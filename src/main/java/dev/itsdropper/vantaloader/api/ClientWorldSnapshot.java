package dev.itsdropper.vantaloader.api;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Immutable, game-thread snapshot of the active client world. */
public record ClientWorldSnapshot(String dimensionId, long gameTime,
                                  List<ClientEntitySnapshot> entities, boolean entitiesTruncated) {
    public ClientWorldSnapshot {
        if (dimensionId == null || dimensionId.isBlank())
            throw new IllegalArgumentException("dimensionId must not be blank");
        entities = List.copyOf(Objects.requireNonNull(entities, "entities"));
    }

    /** Source-compatible constructor for adapters that do not yet expose entity snapshots. */
    public ClientWorldSnapshot(String dimensionId, long gameTime) {
        this(dimensionId, gameTime, List.of(), false);
    }

    /** Returns nearby entities ordered by distance, bounded by the supplied radius. */
    public List<ClientEntitySnapshot> entitiesWithin(double x, double y, double z, double radius) {
        if (!Double.isFinite(radius) || radius < 0)
            throw new IllegalArgumentException("radius must be finite and non-negative");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
            throw new IllegalArgumentException("Query position must be finite");
        double radiusSquared = radius * radius;
        if (!Double.isFinite(radiusSquared))
            throw new IllegalArgumentException("radius is too large");
        return entities.stream()
                .filter(entity -> entity.distanceSquaredTo(x, y, z) <= radiusSquared)
                .sorted(Comparator.comparingDouble(entity -> entity.distanceSquaredTo(x, y, z)))
                .toList();
    }
}
