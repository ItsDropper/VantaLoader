package dev.itsdropper.vantaloader.api;

import java.util.Objects;
import java.util.Optional;

/** Client-thread notification that Minecraft opened, closed, or replaced a screen. */
public record ClientScreenEvent(Transition transition,
                                Optional<String> previousScreenClass,
                                Optional<String> currentScreenClass) {
    public enum Transition { OPENED, CLOSED, CHANGED }

    public ClientScreenEvent {
        Objects.requireNonNull(transition, "transition");
        previousScreenClass = Objects.requireNonNull(previousScreenClass, "previousScreenClass");
        currentScreenClass = Objects.requireNonNull(currentScreenClass, "currentScreenClass");
        switch (transition) {
            case OPENED -> {
                if (previousScreenClass.isPresent() || currentScreenClass.isEmpty())
                    throw new IllegalArgumentException("OPENED requires only a current screen");
            }
            case CLOSED -> {
                if (previousScreenClass.isEmpty() || currentScreenClass.isPresent())
                    throw new IllegalArgumentException("CLOSED requires only a previous screen");
            }
            case CHANGED -> {
                if (previousScreenClass.isEmpty() || currentScreenClass.isEmpty())
                    throw new IllegalArgumentException("CHANGED requires both screens");
            }
        }
    }
}
