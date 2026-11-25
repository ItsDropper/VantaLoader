package dev.itsdropper.vantaloader.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable value snapshot of one entity visible to the local client. */
public record ClientEntitySnapshot(
        UUID uuid,
        String typeDescriptionId,
        String displayName,
        double x, double y, double z,
        float yaw, float pitch,
        boolean alive,
        Optional<Float> health
) {
    public ClientEntitySnapshot {
        Objects.requireNonNull(uuid, "uuid");
        if (typeDescriptionId == null || typeDescriptionId.isBlank()) throw new IllegalArgumentException("typeDescriptionId must not be blank");
        Objects.requireNonNull(displayName, "displayName");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
            throw new IllegalArgumentException("Entity position must be finite");
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch))
            throw new IllegalArgumentException("Entity rotation must be finite");
        health = Objects.requireNonNull(health, "health");
        health.ifPresent(value -> {
            if (!Float.isFinite(value) || value < 0)
                throw new IllegalArgumentException("Entity health must be finite and non-negative");
        });
    }

    public double distanceSquaredTo(double px, double py, double pz) {
        if (!Double.isFinite(px) || !Double.isFinite(py) || !Double.isFinite(pz))
            throw new IllegalArgumentException("Query position must be finite");
        double dx = x - px, dy = y - py, dz = z - pz;
        return dx * dx + dy * dy + dz * dz;
    }
}
