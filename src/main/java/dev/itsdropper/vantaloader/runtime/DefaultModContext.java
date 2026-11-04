package dev.itsdropper.vantaloader.runtime;

import dev.itsdropper.vantaloader.api.ModContext;
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

    DefaultModContext(ModDescriptor descriptor, Path gameDirectory, Path configDirectory,
                      TransformationService transformationService) {
        this.descriptor = descriptor;
        this.gameDirectory = gameDirectory;
        this.configDirectory = configDirectory;
        this.logger = Logger.getLogger("VantaLoader.Mod." + descriptor.id());
        this.transformations = transformationService.forMod(descriptor.id());
    }
    @Override public String modId() { return descriptor.id(); }
    @Override public String modVersion() { return descriptor.version().toString(); }
    @Override public String loaderVersion() { return LoaderVersion.VERSION; }
    @Override public Path gameDirectory() { return gameDirectory; }
    @Override public Path configDirectory() { return configDirectory; }
    @Override public Logger logger() { return logger; }
    @Override public TransformationRegistry transformations() { return transformations; }
}
