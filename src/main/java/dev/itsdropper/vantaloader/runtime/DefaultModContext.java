package dev.itsdropper.vantaloader.runtime;

import dev.itsdropper.vantaloader.api.ModContext;
import dev.itsdropper.vantaloader.api.MinecraftCapabilities;
import dev.itsdropper.vantaloader.api.EventBus;
import dev.itsdropper.vantaloader.api.TransformationRegistry;
import dev.itsdropper.vantaloader.metadata.LoaderVersion;
import dev.itsdropper.vantaloader.metadata.ModDescriptor;
import dev.itsdropper.vantaloader.transformation.TransformationService;

import java.nio.file.Path;
import java.util.logging.Logger;

final class DefaultModContext implements ModContext {
    private final ModDescriptor descriptor;
    private final Path gameDirectory;
    private final Path configDirectory;
    private final Logger logger;
    private final TransformationRegistry transformations;
    private final EventBus events;
    private final MinecraftCapabilities minecraftCapabilities;
    private final dev.itsdropper.vantaloader.api.ResourceReloadRegistry resources;
    private final dev.itsdropper.vantaloader.api.ModConfig config;

    DefaultModContext(ModDescriptor descriptor, Path gameDirectory, Path configDirectory,
                      TransformationService transformationService, EventBus events,
                      MinecraftCapabilities minecraftCapabilities,
                      dev.itsdropper.vantaloader.api.ResourceReloadRegistry resources,
                      dev.itsdropper.vantaloader.api.ModConfig config) {
        this.descriptor = descriptor;
        this.gameDirectory = gameDirectory;
        this.configDirectory = configDirectory;
        this.logger = Logger.getLogger("VantaLoader.Mod." + descriptor.id());
        this.transformations = transformationService.forMod(descriptor.id());
        this.events = java.util.Objects.requireNonNull(events, "events");
        this.minecraftCapabilities = java.util.Objects.requireNonNull(minecraftCapabilities, "minecraftCapabilities");
        this.resources = java.util.Objects.requireNonNull(resources, "resources");
        this.config = java.util.Objects.requireNonNull(config, "config");
    }
    @Override public String modId() { return descriptor.id(); }
    @Override public String modVersion() { return descriptor.version().toString(); }
    @Override public String loaderVersion() { return LoaderVersion.VERSION; }
    @Override public String minecraftVersion() {
        return System.getProperty("vanta.minecraft.version", "unknown");
    }
    @Override public MinecraftCapabilities minecraftCapabilities() { return minecraftCapabilities; }
    @Override public String environment() {
        return System.getProperty("vanta.environment", "client");
    }
    @Override public Path gameDirectory() { return gameDirectory; }
    @Override public Path configDirectory() { return configDirectory; }
    @Override public Logger logger() { return logger; }
    @Override public TransformationRegistry transformations() { return transformations; }
    @Override public EventBus events() { return events; }
    @Override public dev.itsdropper.vantaloader.api.ResourceReloadRegistry resources() { return resources; }
    @Override public dev.itsdropper.vantaloader.api.ModConfig config() { return config; }
}
