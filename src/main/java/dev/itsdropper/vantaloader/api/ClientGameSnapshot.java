package dev.itsdropper.vantaloader.api;

import java.util.Optional;

/** Immutable snapshot of the local client game state at one tick boundary. */
public record ClientGameSnapshot(
        Optional<ClientPlayerSnapshot> player,
        Optional<ClientWorldSnapshot> world
) {
    public ClientGameSnapshot {
        player = java.util.Objects.requireNonNull(player, "player");
        world = java.util.Objects.requireNonNull(world, "world");
    }

    public boolean inWorld() { return player.isPresent() && world.isPresent(); }
}
