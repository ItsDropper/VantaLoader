package dev.itsdropper.vantaloader.bootstrap.minecraft;

import dev.itsdropper.vantaloader.transformation.TransformationService;

/** Installs one exact Minecraft-version integration into the native transformation pipeline. */
public interface MinecraftAdapter {
    String minecraftVersion();
    void install(TransformationService transformations);
}
