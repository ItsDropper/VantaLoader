package dev.itsdropper.vantaloader.api;

import java.util.Objects;

/** Immutable, game-thread snapshot of the active client world. */
public record ClientWorldSnapshot(String dimensionId, long gameTime) {
    public ClientWorldSnapshot {
        if (dimensionId == null || dimensionId.isBlank()) {
            throw new IllegalArgumentException("dimensionId must not be blank");
        }
    }
}
