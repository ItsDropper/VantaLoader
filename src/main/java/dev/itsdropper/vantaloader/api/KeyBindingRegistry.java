package dev.itsdropper.vantaloader.api;

import java.util.Optional;

/**
 * Mod-scoped registry for keyboard bindings. Default keys are active immediately;
 * user remapping and persistent key-map UI are separate capabilities.
 */
public interface KeyBindingRegistry extends AutoCloseable {
    KeyBindingRegistration register(KeyBinding binding);
    Optional<KeyBindingRegistration> find(String id);
    @Override void close();
}
