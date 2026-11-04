package dev.itsdropper.vantaloader.integration;

import dev.itsdropper.vantaloader.api.VantaMod;
import dev.itsdropper.vantaloader.dependency.ModResolver;
import dev.itsdropper.vantaloader.discovery.ModArtifact;
import dev.itsdropper.vantaloader.metadata.DescriptorReader;
import dev.itsdropper.vantaloader.runtime.ModRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies declared dependency visibility across independently compiled mod JARs. */
class DependencyClassLoaderIntegrationTest {
    @TempDir Path temporaryDirectory;

    @Test
    void exposesDeclaredDependenciesButNotUnrelatedMods() throws Exception {
        Path mods = Files.createDirectory(temporaryDirectory.resolve("mods"));
        Path game = temporaryDirectory.resolve("game");
        Path coreClasses = compile("core-classes", List.of(
                source("fixture/shared/Library.java", """
                        package fixture.shared;
                        public final class Library {
                            private Library() { }
                            public static String message() { return "dependency-visible"; }
                        }
                        """),
                source("fixture/core/CoreMod.java", """
                        package fixture.core;
                        import dev.itsdropper.vantaloader.api.ModContext;
                        import dev.itsdropper.vantaloader.api.VantaMod;
                        public final class CoreMod implements VantaMod {
                            public CoreMod() { }
                            public void onInitialize(ModContext context) { }
                            public void onShutdown() { }
                        }
                        """)), null);
        Path dependentClasses = compile("dependent-classes", List.of(
                source("fixture/dependent/DependentMod.java", """
                        package fixture.dependent;
                        import dev.itsdropper.vantaloader.api.ModContext;
                        import dev.itsdropper.vantaloader.api.VantaMod;
                        import fixture.shared.Library;
                        import java.nio.file.Files;
                        public final class DependentMod implements VantaMod {
                            public DependentMod() { }
                            public void onInitialize(ModContext context) throws Exception {
                                Files.writeString(context.configDirectory().resolve("visibility.txt"),
                                        Library.message());
                            }
                            public void onShutdown() { }
                        }
                        """)), coreClasses);
        Path isolatedClasses = compile("isolated-classes", List.of(
                source("fixture/isolated/IsolatedMod.java", """
                        package fixture.isolated;
                        import dev.itsdropper.vantaloader.api.ModContext;
                        import dev.itsdropper.vantaloader.api.VantaMod;
                        import java.nio.file.Files;
                        public final class IsolatedMod implements VantaMod {
                            public IsolatedMod() { }
                            public void onInitialize(ModContext context) throws Exception {
                                String result;
                                try {
                                    Class.forName("fixture.shared.Library");
                                    result = "leaked";
                                } catch (ClassNotFoundException expected) {
                                    result = "isolated";
                                }
                                Files.writeString(context.configDirectory().resolve("visibility.txt"), result);
                            }
                            public void onShutdown() { }
                        }
                        """)), null);

        Path coreJar = packageMod(mods.resolve("core.jar"), coreClasses, """
                {"schemaVersion":1,"id":"core","name":"Core","version":"1.0.0",
                 "entrypoints":{"main":"fixture.core.CoreMod"}}
                """);
        Path dependentJar = packageMod(mods.resolve("dependent.jar"), dependentClasses, """
                {"schemaVersion":1,"id":"dependent","name":"Dependent","version":"1.0.0",
                 "entrypoints":{"main":"fixture.dependent.DependentMod"},
                 "depends":{"core":">=1.0.0 <2.0.0"}}
                """);
        Path isolatedJar = packageMod(mods.resolve("isolated.jar"), isolatedClasses, """
                {"schemaVersion":1,"id":"isolated","name":"Isolated","version":"1.0.0",
                 "entrypoints":{"main":"fixture.isolated.IsolatedMod"}}
                """);

        List<ModArtifact> artifacts = List.of(
                artifact(coreJar), artifact(dependentJar), artifact(isolatedJar));
        List<ModArtifact> ordered = new ModResolver().resolve(artifacts);
        assertEquals(List.of("core", "dependent", "isolated"),
                ordered.stream().map(item -> item.descriptor().id()).toList());

        try (ModRuntime runtime = new ModRuntime(game)) {
            runtime.start(ordered);
            assertEquals("dependency-visible", Files.readString(
                    game.resolve("config/vantaloader/dependent/visibility.txt")));
            assertEquals("isolated", Files.readString(
                    game.resolve("config/vantaloader/isolated/visibility.txt")));
        }
    }

    private Path compile(String directoryName, List<Source> sources, Path dependencyClasses) throws Exception {
        Path classes = Files.createDirectory(temporaryDirectory.resolve(directoryName));
        Path sourceRoot = Files.createDirectory(temporaryDirectory.resolve(directoryName + "-sources"));
        List<Path> sourceFiles = sources.stream().map(source -> {
            try {
                Path file = sourceRoot.resolve(source.relativePath());
                Files.createDirectories(file.getParent());
                Files.writeString(file, source.contents(), StandardCharsets.UTF_8);
                return file;
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }).toList();

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertTrue(compiler != null, "Integration tests require a full JDK");
        String apiLocation = Path.of(VantaMod.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).toString();
        String classpath = apiLocation;
        if (dependencyClasses != null) {
            classpath += System.getProperty("path.separator") + dependencyClasses;
        }
        List<String> arguments = new java.util.ArrayList<>(List.of(
                "--release", "21", "-classpath", classpath, "-d", classes.toString()));
        sourceFiles.forEach(file -> arguments.add(file.toString()));
        int result = compiler.run(null, null, null, arguments.toArray(String[]::new));
        assertEquals(0, result, "Fixture source compilation failed for " + directoryName);
        return classes;
    }

    private Path packageMod(Path jarPath, Path classes, String descriptor) throws Exception {
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath))) {
            jar.putNextEntry(new JarEntry("vanta.mod.json"));
            jar.write(descriptor.getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
            try (var files = Files.walk(classes)) {
                for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                    String entryName = classes.relativize(file).toString().replace('\\\\', '/');
                    jar.putNextEntry(new JarEntry(entryName));
                    jar.write(Files.readAllBytes(file));
                    jar.closeEntry();
                }
            }
        }
        return jarPath;
    }

    private ModArtifact artifact(Path jar) throws Exception {
        try (var input = new java.util.jar.JarFile(jar.toFile())) {
            var descriptor = input.getInputStream(input.getJarEntry("vanta.mod.json"));
            try (var reader = new java.io.InputStreamReader(descriptor, StandardCharsets.UTF_8)) {
                return new ModArtifact(jar, new DescriptorReader().read(reader));
            }
        }
    }

    private static Source source(String relativePath, String contents) {
        return new Source(relativePath, contents);
    }

    private record Source(String relativePath, String contents) { }
}
