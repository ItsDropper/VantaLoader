package dev.itsdropper.vantaloader.api;

import java.util.Objects;

/** Runtime-derived two-line identity shown at the top of the VantaLoader mod screen. */
public record ModMenuHeader(String title, String subtitle) {
    public ModMenuHeader {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(subtitle, "subtitle");
    }

    public static ModMenuHeader create(String minecraftVersion, int loadedMods) {
        if (loadedMods < 0) throw new IllegalArgumentException("loadedMods cannot be negative");
        String version = minecraftVersion == null || minecraftVersion.isBlank()
                ? "Unknown Minecraft version" : minecraftVersion;
        return new ModMenuHeader("VantaLoader " + version,
                "Loaded " + loadedMods + (loadedMods == 1 ? " mod" : " mods"));
    }
}
