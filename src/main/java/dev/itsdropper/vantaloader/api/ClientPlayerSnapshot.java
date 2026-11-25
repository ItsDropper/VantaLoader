package dev.itsdropper.vantaloader.api;

/** Immutable, game-thread snapshot of the local player; contains no live Minecraft objects. */
public record ClientPlayerSnapshot(
        double x, double y, double z,
        float yaw, float pitch,
        float health, float maxHealth,
        int foodLevel,
        boolean onGround
) {
    public ClientPlayerSnapshot {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Player position must be finite");
        }
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("Player rotation must be finite");
        }
        if (!Float.isFinite(health) || !Float.isFinite(maxHealth) || maxHealth < 0) {
            throw new IllegalArgumentException("Player health values must be finite and maxHealth non-negative");
        }
        if (foodLevel < -1 || foodLevel > 20) {
            throw new IllegalArgumentException("foodLevel must be -1 (unavailable) or in [0, 20]");
        }
    }

    public boolean hasFoodLevel() { return foodLevel >= 0; }
}
