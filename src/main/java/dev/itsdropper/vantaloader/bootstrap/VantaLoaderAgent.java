package dev.itsdropper.vantaloader.bootstrap;

import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Minecraft JVM entry point. The launcher sets the instance directory as
 * user.dir, so the agent uses that directory's mods/ folder without adding
 * launcher-specific arguments to Minecraft's game argument list.
 */
public final class VantaLoaderAgent {
    private static final Logger LOGGER = Logger.getLogger("VantaLoader.Agent");

    private VantaLoaderAgent() { }

    public static void premain(String agentArguments, Instrumentation instrumentation) throws Exception {
        Path gameDirectory = Path.of(System.getProperty("user.dir", "."))
                .toAbsolutePath().normalize();
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
}
