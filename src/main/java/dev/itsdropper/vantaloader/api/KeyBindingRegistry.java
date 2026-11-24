package dev.itsdropper.vantaloader.api;

import java.util.Optional;

/**
 * Mod-scoped registry for keyboard bindings. Default keys are active immediately;
 * user remapping and persistent key-map UI are separate capabilities.
 */
public interface KeyBindingRegistry extends AutoCloseable {
    /** Whether the active Minecraft adapter supplies keyboard input events. */
    boolean isAvailable();
    KeyBindingRegistration register(KeyBinding binding);
    Optional<KeyBindingRegistration> find(String id);
    @Override void close();
}
