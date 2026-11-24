package dev.itsdropper.vantaloader.api;

import java.util.Objects;

/**
 * Immutable keyboard binding definition. Key codes and modifier bits use GLFW values.
 * The binding ID is unique within its owning mod.
 */
public record KeyBinding(String id, String name, int defaultKeyCode, int modifiers,
                         KeyBindingCallback callback) {
    public static final int MOD_SHIFT = 0x0001;
    public static final int MOD_CONTROL = 0x0002;
    public static final int MOD_ALT = 0x0004;
    public static final int MOD_SUPER = 0x0008;
    public static final int MOD_CAPS_LOCK = 0x0010;
    public static final int MOD_NUM_LOCK = 0x0020;
    public static final int MODIFIER_MASK = MOD_SHIFT | MOD_CONTROL | MOD_ALT
            | MOD_SUPER | MOD_CAPS_LOCK | MOD_NUM_LOCK;
    public static final int UNBOUND = -1;

    public KeyBinding {
        if (id == null || !id.matches("[a-z][a-z0-9_.-]{0,63}")) {
            throw new IllegalArgumentException("Invalid key binding ID: " + id);
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Key binding name must not be blank");
        }
        if (defaultKeyCode < UNBOUND) {
            throw new IllegalArgumentException("Key code must be GLFW key code or UNBOUND");
        }
        if ((modifiers & ~MODIFIER_MASK) != 0) {
            throw new IllegalArgumentException("Unknown GLFW modifier bits: " + modifiers);
        }
        Objects.requireNonNull(callback, "callback");
    }
}
