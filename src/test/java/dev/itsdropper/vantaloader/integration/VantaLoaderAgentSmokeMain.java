package dev.itsdropper.vantaloader.integration;

import java.nio.file.Files;
import java.nio.file.Path;

/** Child-process target proving the agent runs before the application's main method. */
public final class VantaLoaderAgentSmokeMain {
    private VantaLoaderAgentSmokeMain() { }

    public static void main(String[] args) throws Exception {
        Path gameDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path initialized = gameDirectory.resolve("config/vantaloader/agentfixture/initialized.txt");
        if (!Files.isRegularFile(initialized)) {
            throw new IllegalStateException("VantaLoader did not initialize the fixture before main");
        }
        Files.writeString(gameDirectory.resolve("main-started.txt"), "main-started");
    }
}
