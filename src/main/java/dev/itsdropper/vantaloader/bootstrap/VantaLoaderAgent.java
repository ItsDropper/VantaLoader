package dev.itsdropper.vantaloader.bootstrap;

import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Minecraft JVM entry point.
 *
 * Vanta Launcher supplies the instance directory through the documented
 * vanta.instance.directory system property. user.dir remains a backwards-
 * compatible fallback for other launchers and direct agent users.
 */
public final class VantaLoaderAgent {
    static final String INSTANCE_DIRECTORY_PROPERTY = "vanta.instance.directory";
    private static final Logger LOGGER = Logger.getLogger("VantaLoader.Agent");

    private VantaLoaderAgent() { }

    public static void premain(String agentArguments, Instrumentation instrumentation) throws Exception {
        Path gameDirectory = resolveGameDirectory(
                System.getProperty(INSTANCE_DIRECTORY_PROPERTY),
                System.getProperty("user.dir", "."));
        Path modsDirectory = gameDirectory.resolve("mods");

        LOGGER.info("Attaching to Minecraft before its main class starts");
        LOGGER.info("Instance directory: " + gameDirectory);
        LOGGER.info("Native mod directory: " + modsDirectory);

        try {
            new VantaLoader(gameDirectory, modsDirectory, instrumentation).load();
            LOGGER.info("VantaLoader agent initialized");
        } catch (Exception failure) {
            LOGGER.log(Level.SEVERE, "VantaLoader could not initialize; aborting Minecraft startup", failure);
            throw failure;
        }
    }

    static Path resolveGameDirectory(String configuredDirectory, String workingDirectory) {
        String selected = configuredDirectory == null || configuredDirectory.isBlank()
                ? workingDirectory : configuredDirectory;
        if (selected == null || selected.isBlank()) {
            throw new IllegalArgumentException("VantaLoader requires an instance directory.");
        }
        return Path.of(selected).toAbsolutePath().normalize();
    }
}
