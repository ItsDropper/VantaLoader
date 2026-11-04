package dev.itsdropper.vantaloader.integration;

import dev.itsdropper.vantaloader.api.VantaMod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Executes the packaged agent in a real child JVM with an independent mod JAR. */
class VantaLoaderAgentIntegrationTest {
    @TempDir Path temporaryDirectory;

    @Test
    void initializesModsBeforeTheMinecraftMainClassAndShutsThemDown() throws Exception {
        Path gameDirectory = Files.createDirectory(temporaryDirectory.resolve("game"));
        Path modsDirectory = Files.createDirectory(gameDirectory.resolve("mods"));
        Path fixtureClasses = compileFixture();
        createFixtureJar(modsDirectory.resolve("agent-fixture.jar"), fixtureClasses);

        Path agentJar = Path.of("build/agent/VantaLoader-agent.jar").toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(agentJar),
                "The agentJar Gradle task must run before this integration test");

        String classpath = codeSource(VantaLoaderAgentSmokeMain.class)
                + System.getProperty("path.separator")
                + codeSource(VantaMod.class);
        String javaName = System.getProperty("os.name", "").toLowerCase().contains("win")
                ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", javaName);

        Process process = new ProcessBuilder(
                java.toString(),
                "-javaagent:" + agentJar,
                "-cp", classpath,
                VantaLoaderAgentSmokeMain.class.getName()
        ).directory(gameDirectory.toFile()).redirectErrorStream(true).start();

        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("VantaLoader agent child JVM did not exit within 30 seconds");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), "Agent child JVM failed:\n" + output);
        assertTrue(Files.isRegularFile(
                gameDirectory.resolve("config/vantaloader/agentfixture/initialized.txt")),
                "The fixture mod must initialize before the application's main method");
        assertTrue(Files.isRegularFile(
                gameDirectory.resolve("config/vantaloader/agentfixture/shutdown.txt")),
                "The agent shutdown hook must stop initialized mods");
        assertTrue(Files.isRegularFile(gameDirectory.resolve("main-started.txt")),
                "The application main method must run after successful agent initialization");
    }

    private Path compileFixture() throws Exception {
        Path sourceRoot = Files.createDirectory(temporaryDirectory.resolve("fixture-source"));
        Path classes = Files.createDirectory(temporaryDirectory.resolve("fixture-classes"));
        Path source = sourceRoot.resolve("fixture/AgentFixtureMod.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package fixture;
                import dev.itsdropper.vantaloader.api.ModContext;
                import dev.itsdropper.vantaloader.api.VantaMod;
                import java.nio.file.Files;
                public final class AgentFixtureMod implements VantaMod {
                    public AgentFixtureMod() { }
                    public void onInitialize(ModContext context) throws Exception {
                        Files.createDirectories(context.configDirectory());
                        Files.writeString(context.configDirectory().resolve("initialized.txt"), "ready");
                    }
                    public void onShutdown() throws Exception {
                        Files.writeString(
                                PathSupport.shutdownPath(),
                                "stopped");
                    }
                }
                final class PathSupport {
                    private PathSupport() { }
                    static java.nio.file.Path shutdownPath() {
                        return java.nio.file.Path.of(System.getProperty("user.dir"))
                                .resolve("config/vantaloader/agentfixture/shutdown.txt");
                    }
                }
                """, StandardCharsets.UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertTrue(compiler != null, "Agent integration test requires a full JDK");
        int result = compiler.run(null, null, null,
                "--release", "21",
                "-classpath", codeSource(VantaMod.class),
                "-d", classes.toString(),
                source.toString());
        assertEquals(0, result, "Could not compile the independent agent fixture mod");
        return classes;
    }

    private static String codeSource(Class<?> type) throws Exception {
        return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
    }

    private static void createFixtureJar(Path jarPath, Path classes) throws IOException {
        String descriptor = """
                {"schemaVersion":1,"id":"agentfixture","name":"Agent Fixture","version":"1.0.0",
                 "entrypoints":{"main":"fixture.AgentFixtureMod"}}
                """;
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath))) {
            jar.putNextEntry(new JarEntry("vanta.mod.json"));
            jar.write(descriptor.getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
            try (var paths = Files.walk(classes)) {
                for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
                    String entry = classes.relativize(file).toString().replace('\\', '/');
                    jar.putNextEntry(new JarEntry(entry));
                    jar.write(Files.readAllBytes(file));
                    jar.closeEntry();
                }
            }
        }
    }
}
