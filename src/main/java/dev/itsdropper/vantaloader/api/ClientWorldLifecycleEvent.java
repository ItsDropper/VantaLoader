package dev.itsdropper.vantaloader.api;

import java.util.Objects;
import java.util.Optional;

/** Client-thread notification that the active client world was entered, left, or replaced. */
public record ClientWorldLifecycleEvent(Transition transition,
                                        Optional<String> previousDimensionId,
                                        Optional<String> currentDimensionId) {
    public enum Transition { JOINED, LEFT, CHANGED }

    public ClientWorldLifecycleEvent {
        Objects.requireNonNull(transition, "transition");
        previousDimensionId = Objects.requireNonNull(previousDimensionId, "previousDimensionId");
        currentDimensionId = Objects.requireNonNull(currentDimensionId, "currentDimensionId");
        switch (transition) {
            case JOINED -> {
                if (previousDimensionId.isPresent() || currentDimensionId.isEmpty())
                    throw new IllegalArgumentException("JOINED requires only a current dimension");
            }
            case LEFT -> {
                if (previousDimensionId.isEmpty() || currentDimensionId.isPresent())
                    throw new IllegalArgumentException("LEFT requires only a previous dimension");
            }
            case CHANGED -> {
                if (previousDimensionId.isEmpty() || currentDimensionId.isEmpty())
                    throw new IllegalArgumentException("CHANGED requires both dimensions");
            }
        }
    }
}
