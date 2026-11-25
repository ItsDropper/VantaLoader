package dev.itsdropper.vantaloader.api;

import java.util.Objects;

/** Immutable declaration of a client-local command. */
public record ClientCommand(String name, String description, String usage, ClientCommandCallback callback) {
    public ClientCommand {
        if (name == null || !name.matches("[a-z][a-z0-9_-]{0,31}"))
            throw new IllegalArgumentException("Invalid client command name: " + name);
        if (description == null || description.isBlank())
            throw new IllegalArgumentException("Command description must not be blank");
        if (usage == null || usage.isBlank())
            throw new IllegalArgumentException("Command usage must not be blank");
        Objects.requireNonNull(callback, "callback");
    }
}
