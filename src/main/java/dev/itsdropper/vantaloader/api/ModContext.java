package dev.itsdropper.vantaloader.api;

import java.nio.file.Path;
import java.util.logging.Logger;

/** Small initial API exposed to native mods. */
public interface ModContext {
    String modId();
    String modVersion();
    String loaderVersion();
    String minecraftVersion();

    /**
     * Returns a parsed Minecraft version identity while preserving the original
     * string accessor for source and binary compatibility with existing mods.
     */
    default MinecraftVersion minecraftVersionInfo() {
        return MinecraftVersion.parse(minecraftVersion());
    }

    /** Returns verified facilities exposed by the active Minecraft integration. */
    default MinecraftCapabilities minecraftCapabilities() {
        return MinecraftCapabilities.none();
    }

    String environment();
    Path gameDirectory();
    Path configDirectory();
    Logger logger();
    TransformationRegistry transformations();
    EventBus events();

    /** Typed services exported by and discovered between native mods. */
    default ModServices services() {
        throw new UnsupportedOperationException("Inter-mod services are not available in this runtime");
    }
    default ModTasks tasks() { throw new UnsupportedOperationException("Mod task scheduling is not available in this runtime"); }
    default ModConfig config() { throw new UnsupportedOperationException("Persistent VMA config is not available in this runtime"); }

    /** Mod-owned file resources that can be transactionally reloaded without redefining classes. */
    default ResourceReloadRegistry resources() {
        return (id, directory, listener) -> {
            throw new UnsupportedOperationException("Resource reload is not available in this runtime");
        };
    }
}
