package dev.itsdropper.vantaloader.runtime;

import dev.itsdropper.vantaloader.api.ModContext;
import dev.itsdropper.vantaloader.metadata.LoaderVersion;
import dev.itsdropper.vantaloader.metadata.ModDescriptor;

import java.nio.file.Path;
import java.util.logging.Logger;

final class DefaultModContext implements ModContext {
    private final ModDescriptor descriptor;
    private final Path gameDirectory;
    private final Path configDirectory;
    private final Logger logger;

    DefaultModContext(ModDescriptor descriptor, Path gameDirectory, Path configDirectory) {
        this.descriptor = descriptor;
        this.gameDirectory = gameDirectory;
        this.configDirectory = configDirectory;
        this.logger = Logger.getLogger("VantaLoader.Mod." + descriptor.id());
    }
    @Override public String modId() { return descriptor.id(); }
    @Override public String modVersion() { return descriptor.version().toString(); }
    @Override public String loaderVersion() { return LoaderVersion.VERSION; }
    @Override public Path gameDirectory() { return gameDirectory; }
    @Override public Path configDirectory() { return configDirectory; }
    @Override public Logger logger() { return logger; }
}
