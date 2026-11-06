package dev.itsdropper.vantaloader.bootstrap;

import dev.itsdropper.vantaloader.dependency.ModResolver;
import dev.itsdropper.vantaloader.api.MinecraftCapabilities;
import dev.itsdropper.vantaloader.api.MinecraftCapability;
import dev.itsdropper.vantaloader.bootstrap.minecraft.MinecraftAdapter;
import dev.itsdropper.vantaloader.bootstrap.minecraft.MinecraftAdapterRegistry;
import dev.itsdropper.vantaloader.discovery.ModArtifact;
import dev.itsdropper.vantaloader.discovery.ModDiscovery;
import dev.itsdropper.vantaloader.metadata.LoaderVersion;
import dev.itsdropper.vantaloader.mapping.MinecraftMappings;
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

    private static MinecraftMappings loadMinecraftMappings() throws Exception {
        String configuredPath = System.getProperty("vanta.minecraft.mappings");
        if (configuredPath == null || configuredPath.isBlank()) {
            LOGGER.warning("No official Minecraft mappings path supplied; named Minecraft class targets cannot be resolved");
            return null;
        }
        MinecraftMappings mappings = MinecraftMappings.read(Path.of(configuredPath));
        LOGGER.info("Loaded " + mappings.classCount() + " official Minecraft class mappings");
        return mappings;
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

        String minecraftVersion = System.getProperty("vanta.minecraft.version", "unknown");
        // Minecraft 1.21.11 still ships obfuscated runtime names. Use Mojang's
        // official client mappings for both native adapters and reflection-backed UI.
        MinecraftMappings mappings = loadMinecraftMappings();
        TransformationService transformations = new TransformationService(mappings);

        List<MinecraftCapability> verifiedCapabilities = new java.util.ArrayList<>();
        if (mappings != null) {
            verifiedCapabilities.add(MinecraftCapability.OFFICIAL_MOJANG_MAPPINGS);
        }
        if (instrumentation != null) {
            MinecraftAdapter adapter = MinecraftAdapterRegistry.find(minecraftVersion).orElse(null);
            if (adapter != null) {
                adapter.install(transformations);
                verifiedCapabilities.add(MinecraftCapability.CLIENT_LIFECYCLE_EVENTS);
                LOGGER.info("Installed exact Minecraft adapter: " + adapter.minecraftVersion());
            } else {
                LOGGER.info("No exact Minecraft adapter is available for " + minecraftVersion);
            }
            instrumentation.addTransformer(transformations, false);
            verifiedCapabilities.add(MinecraftCapability.CLASS_TRANSFORMATIONS);
            LOGGER.info("Native class transformation service installed");
        } else {
            LOGGER.warning("No JVM instrumentation supplied; registered class transformations will not run");
        }
        MinecraftCapabilities capabilities = MinecraftCapabilities.of(
                verifiedCapabilities.toArray(MinecraftCapability[]::new));
        LOGGER.info("Verified Minecraft capabilities: " + capabilities.asSet());

        ModRuntime runtime = new ModRuntime(gameDirectory, transformations, capabilities);
        try {
            runtime.start(ordered);
            dev.itsdropper.vantaloader.mixin.client.VantaMenuHooks.bind(runtime, mappings);
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
