package dev.itsdropper.vantaloader.api;

/**
 * Opt-in contract for mods that expose a configuration screen to VantaLoader's
 * in-game Mod Menu. Implement this on the class declared by entrypoints.main.
 *
 * <p>The API deliberately avoids a compile-time Minecraft dependency. The
 * parent and returned objects are the active game's Screen type at runtime.
 * Implementations should return a new screen and must not retain the parent.</p>
 */
public interface ModMenuApi {
    Object createConfigScreen(Object parentScreen) throws Exception;
}
