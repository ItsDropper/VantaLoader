package dev.itsdropper.vantaloader.api;

/** Mod-owned live key binding registration. */
public interface KeyBindingRegistration extends AutoCloseable {
    KeyBinding binding();
    boolean isPressed();
    boolean isClosed();
    @Override void close();
}
