package dev.itsdropper.vantaloader.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KeyBindingTest {
    @Test
    void validatesBindingIdentityAndModifierMask() {
        assertDoesNotThrow(() -> new KeyBinding("toggle", "Toggle", 65,
                KeyBinding.MOD_CONTROL | KeyBinding.MOD_SHIFT, action -> { }));
        assertThrows(IllegalArgumentException.class,
                () -> new KeyBinding("Bad ID", "Toggle", 65, 0, action -> { }));
        assertThrows(IllegalArgumentException.class,
                () -> new KeyBinding("toggle", " ", 65, 0, action -> { }));
        assertThrows(IllegalArgumentException.class,
                () -> new KeyBinding("toggle", "Toggle", -2, 0, action -> { }));
        assertThrows(IllegalArgumentException.class,
                () -> new KeyBinding("toggle", "Toggle", 65, 0x40, action -> { }));
        assertThrows(NullPointerException.class,
                () -> new KeyBinding("toggle", "Toggle", 65, 0, null));
    }
}
