package dev.itsdropper.vantaloader.api;

import java.nio.file.Path;
import java.util.logging.Logger;

/** Small initial API exposed to native mods. */
public interface ModContext {
    String modId();
    String modVersion();
    String loaderVersion();
    String minecraftVersion();
    String environment();
    Path gameDirectory();
    Path configDirectory();
    Logger logger();
    TransformationRegistry transformations();
    EventBus events();
}
