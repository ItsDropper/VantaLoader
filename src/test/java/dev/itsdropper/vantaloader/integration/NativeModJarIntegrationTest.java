package dev.itsdropper.vantaloader.integration;

import dev.itsdropper.vantaloader.api.VantaMod;
import dev.itsdropper.vantaloader.api.ModMenuApi;
import dev.itsdropper.vantaloader.dependency.ModResolver;
import dev.itsdropper.vantaloader.discovery.ModDiscovery;
import dev.itsdropper.vantaloader.runtime.ModRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end proof that an independently compiled mod JAR is discovered,
 * loaded through the mod class loader, initialized, and shut down.
 */
class NativeModJarIntegrationTest {
    @TempDir Path temporaryDirectory;

    @Test
    void loadsAnIndependentlyCompiledModJarAndRunsItsLifecycle() throws Exception {
        Path modsDirectory = Files.createDirectory(temporaryDirectory.resolve("mods"));
        Path gameDirectory = temporaryDirectory.resolve("game");
        Path classesDirectory = Files.createDirectory(temporaryDirectory.resolve("fixture-classes"));
        Path sourceFile = temporaryDirectory.resolve("ExampleFixtureMod.java");
        Path jarFile = modsDirectory.resolve("example-fixture.jar");

        String source = """
                package fixture;
                import dev.itsdropper.vantaloader.api.ModContext;
                import dev.itsdropper.vantaloader.api.VantaMod;
                import dev.itsdropper.vantaloader.api.ModMenuApi;
                import java.nio.file.Files;
                import java.nio.file.Path;

                public final class ExampleFixtureMod implements VantaMod, ModMenuApi {
                    private Path marker;
                    public ExampleFixtureMod() {
                        if (Thread.currentThread().getContextClassLoader()
                                != ExampleFixtureMod.class.getClassLoader()) {
                            throw new IllegalStateException("Constructor context class loader mismatch");
                        }
                    }
                    @Override
                    public void onInitialize(ModContext context) throws Exception {
                        Files.createDirectories(context.configDirectory());
                        marker = context.configDirectory().resolve("initialized.txt");
                        String source = ExampleFixtureMod.class.getProtectionDomain()
                                .getCodeSource().getLocation().toExternalForm();
                        boolean contextLoaderMatches = Thread.currentThread().getContextClassLoader()
                                == ExampleFixtureMod.class.getClassLoader();
                        Files.writeString(marker, context.modId() + ":" + context.modVersion()
                                + System.lineSeparator() + source
                                + System.lineSeparator() + contextLoaderMatches);
                    }
                    @Override
                    public Object createConfigScreen(Object parentScreen) {
                        return "settings:" + parentScreen;
                    }
                    @Override
                    public void onShutdown() throws Exception {
                        Files.writeString(marker.resolveSibling("shutdown.txt"), "shutdown");
                    }
                }
                """;
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Integration tests require a full JDK, not a JRE");
        String apiLocation = Path.of(VantaMod.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).toString();
        String classpath = System.getProperty("java.class.path")
                + System.getProperty("path.separator") + apiLocation;
        int compileResult = compiler.run(null, null, null,
                "--release", "21",
                "-classpath", classpath,
                "-d", classesDirectory.toString(),
                sourceFile.toString());
        assertEquals(0, compileResult, "Fixture mod compilation failed");

        String descriptor = """
                {
                  "schemaVersion": 1,
                  "id": "example_fixture",
                  "name": "Example Fixture Mod",
                  "version": "1.2.3",
                  "description": "Integration fixture mod",
                  "icon": "assets/example/icon.png",
                  "license": "MIT",
                  "website": "https://example.com",
                  "sources": "https://github.com/example/fixture",
                  "entrypoints": { "main": "fixture.ExampleFixtureMod" }
                }
                """;
        Path compiledClass = classesDirectory.resolve("fixture/ExampleFixtureMod.class");
        assertTrue(Files.isRegularFile(compiledClass), "Fixture class was not generated");

        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarFile))) {
            jar.putNextEntry(new JarEntry("vanta.mod.json"));
            jar.write(descriptor.getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
            jar.putNextEntry(new JarEntry("fixture/ExampleFixtureMod.class"));
            jar.write(Files.readAllBytes(compiledClass));
            jar.closeEntry();
            jar.putNextEntry(new JarEntry("assets/example/icon.png"));
            jar.write(new byte[]{(byte) 0x89, 'P', 'N', 'G'});
            jar.closeEntry();
        }

        var discovered = new ModDiscovery().discover(modsDirectory);
        assertEquals(1, discovered.size());
        assertEquals("example_fixture", discovered.getFirst().descriptor().id());

        var ordered = new ModResolver().resolve(discovered);
        try (ModRuntime runtime = new ModRuntime(gameDirectory)) {
            ClassLoader originalContextLoader = Thread.currentThread().getContextClassLoader();
            runtime.start(ordered);
            var menuEntries = runtime.modMenuEntries();
            assertEquals(1, menuEntries.size());
            assertEquals("Integration fixture mod", menuEntries.getFirst().description());
            assertEquals("MIT", menuEntries.getFirst().license());
            assertTrue(menuEntries.getFirst().hasConfigScreen());
            assertNotNull(menuEntries.getFirst().iconResource());
            assertEquals("settings:parent", runtime.createModConfigScreen("example_fixture", "parent"));
            assertSame(originalContextLoader, Thread.currentThread().getContextClassLoader(),
                    "Runtime startup must restore the caller's context class loader");
            Path modConfig = gameDirectory.resolve("config/vantaloader/example_fixture");
            String[] initialization = Files.readString(modConfig.resolve("initialized.txt"))
                    .split(System.lineSeparator());
            assertEquals("example_fixture:1.2.3", initialization[0]);
            assertTrue(initialization[1].startsWith("file:"),
                    "URLClassLoader should report the JAR file URL, not the test classpath");
            assertTrue(initialization[1].contains("example-fixture.jar"),
                    "Entrypoint must originate from the independently packaged fixture JAR");
            assertEquals("true", initialization[2],
                    "Mod callbacks must run with the mod class loader as context class loader");
            assertFalse(Files.exists(modConfig.resolve("shutdown.txt")),
                    "Shutdown must not run before the runtime is closed");
        }

        Path shutdownMarker = gameDirectory.resolve(
                "config/vantaloader/example_fixture/shutdown.txt");
        assertEquals("shutdown", Files.readString(shutdownMarker));
    }
}
