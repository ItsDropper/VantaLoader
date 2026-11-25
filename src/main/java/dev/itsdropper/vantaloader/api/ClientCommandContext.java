package dev.itsdropper.vantaloader.api;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Parsed command invocation; contains no Minecraft implementation classes. */
public record ClientCommandContext(String modId, ClientCommand command, String rawInput,
                                   List<String> arguments, Consumer<String> feedback) {
    public ClientCommandContext {
        Objects.requireNonNull(modId, "modId");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(rawInput, "rawInput");
        arguments = List.copyOf(arguments);
        Objects.requireNonNull(feedback, "feedback");
    }
    public void reply(String message) {
        if (message == null || message.isBlank()) throw new IllegalArgumentException("Feedback must not be blank");
        feedback.accept(message);
    }
    public void error(String message) { reply("§c" + Objects.requireNonNull(message, "message")); }
}
