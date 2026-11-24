package dev.itsdropper.vantaloader.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KeyInputEventTest {
    private static final Object HANDLER = new Object();

    @Test
    void classifiesGlfwCompatiblePressReleaseAndRepeatActions() {
        KeyInputEvent press = event(KeyInputEvent.PRESS);
        KeyInputEvent release = event(KeyInputEvent.RELEASE);
        KeyInputEvent repeat = event(KeyInputEvent.REPEAT);

        assertTrue(press.pressed());
        assertFalse(press.released());
        assertFalse(press.repeated());

        assertFalse(release.pressed());
        assertTrue(release.released());
        assertFalse(release.repeated());

        assertFalse(repeat.pressed());
        assertFalse(repeat.released());
        assertTrue(repeat.repeated());
    }

    @Test
    void requiresAnOwningKeyboardHandler() {
        assertThrows(NullPointerException.class,
                () -> new KeyInputEvent(null, 0L, 65, 30, KeyInputEvent.PRESS, 0));
    }

    private static KeyInputEvent event(int action) {
        return new KeyInputEvent(HANDLER, 42L, 65, 30, action, 0);
    }
}
