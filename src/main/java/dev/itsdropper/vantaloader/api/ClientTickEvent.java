package dev.itsdropper.vantaloader.api;

import java.util.Objects;

/** Client tick boundary event emitted by a verified Minecraft-version adapter. */
public record ClientTickEvent(Object client, Phase phase) {
    public ClientTickEvent {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(phase, "phase");
    }
    public enum Phase { START, END }
}
