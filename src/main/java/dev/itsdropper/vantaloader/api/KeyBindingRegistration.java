package dev.itsdropper.vantaloader.api;

/** Mod-owned live key binding registration with persistent user remapping. */
public interface KeyBindingRegistration extends AutoCloseable {
    KeyBinding binding();
    int keyCode();
    int modifiers();
    boolean isPressed();
    boolean isClosed();
    /** Changes the effective key and modifiers and persists them in this mod's VMA config. */
    void remap(int keyCode, int modifiers);
    /** Restores the declared default and persists that choice. */
    void resetToDefault();
    @Override void close();
}
