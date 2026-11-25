package dev.itsdropper.vantaloader.api;

import java.util.Objects;

/** Typed client tick event carrying immutable player/world state. */
public record ClientGameTickEvent(ClientGameSnapshot snapshot, ClientTickEvent.Phase phase) {
    public ClientGameTickEvent {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(phase, "phase");
    }
}
