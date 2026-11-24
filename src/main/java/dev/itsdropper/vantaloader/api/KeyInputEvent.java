package dev.itsdropper.vantaloader.api;

import java.util.Objects;

/**
 * Raw keyboard input delivered by an exact-version client adapter.
 * Action values follow GLFW: RELEASE=0, PRESS=1, REPEAT=2.
 * The event is delivered on Minecraft's input thread; do not block it.
 */
public record KeyInputEvent(
        Object keyboardHandler,
        long windowHandle,
        int keyCode,
        int scanCode,
        int action,
        int modifiers
) {
    public static final int RELEASE = 0;
    public static final int PRESS = 1;
    public static final int REPEAT = 2;

    public KeyInputEvent {
        Objects.requireNonNull(keyboardHandler, "keyboardHandler");
    }

    public boolean pressed() { return action == PRESS; }
    public boolean released() { return action == RELEASE; }
    public boolean repeated() { return action == REPEAT; }
}
