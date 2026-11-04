package dev.itsdropper.vantaloader.bootstrap;

import dev.itsdropper.vantaloader.dependency.ModResolver;
import dev.itsdropper.vantaloader.discovery.ModArtifact;
import dev.itsdropper.vantaloader.discovery.ModDiscovery;
import dev.itsdropper.vantaloader.metadata.LoaderVersion;
import dev.itsdropper.vantaloader.runtime.ModRuntime;
import dev.itsdropper.vantaloader.transformation.TransformationService;

import java.lang.instrument.Instrumentation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

/** Coordinates discovery, validation, dependency resolution, and mod startup. */
public final class VantaLoader {
    private static final Logger LOGGER = Logger.getLogger("VantaLoader");
    private final Path gameDirectory;
    private final Path modsDirectory;
    private final Instrumentation instrumentation;

    public VantaLoader(Path gameDirectory, Path modsDirectory) {
        this(gameDirectory, modsDirectory, null);
    }

    public VantaLoader(Path gameDirectory, Path modsDirectory, Instrumentation instrumentation) {
        this.gameDirectory = gameDirectory.toAbsolutePath().normalize();
        this.modsDirectory = modsDirectory.toAbsolutePath().normalize();
        this.instrumentation = instrumentation;
    }

    public void load() throws Exception {
        Files.createDirectories(gameDirectory);
        LOGGER.info("VantaLoader " + LoaderVersion.VERSION + " starting");
        LOGGER.info("Game directory: " + gameDirectory);
        LOGGER.info("Mods directory: " + modsDirectory);
        List<ModArtifact> discovered = new ModDiscovery().discover(modsDirectory);
        LOGGER.info("Discovered " + discovered.size() + " mod JAR(s)");
        List<ModArtifact> ordered = new ModResolver().resolve(discovered);
        for (int i = 0; i < ordered.size(); i++) {
            var descriptor = ordered.get(i).descriptor();
            LOGGER.info("Load order " + (i + 1) + ": " + descriptor.id() + " " + descriptor.version());
        }
        TransformationService transformations = new TransformationService();
        if (instrumentation != null) {
            instrumentation.addTransformer(transformations, false);
            LOGGER.info("Native class transformation service installed");
        } else {
            LOGGER.warning("No JVM instrumentation supplied; registered class transformations will not run");
        }
        ModRuntime runtime = new ModRuntime(gameDirectory, transformations);
        try {
            runtime.start(ordered);
            LOGGER.info("All VantaLoader mods initialized successfully");
        } catch (Exception failure) {
            try { runtime.close(); }
            catch (Exception shutdownFailure) { failure.addSuppressed(shutdownFailure); }
            throw failure;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { runtime.close(); }
            catch (Exception failure) { LOGGER.severe("Mod shutdown failed: " + failure.getMessage()); }
        }, "VantaLoader-Shutdown"));
    }
}
