package dev.itsdropper.vantaloader.runtime;

import dev.itsdropper.vantaloader.api.ClientCommand;
import dev.itsdropper.vantaloader.api.ClientCommandContext;
import dev.itsdropper.vantaloader.api.ClientCommandDispatcher;
import dev.itsdropper.vantaloader.api.ClientCommandRegistration;
import dev.itsdropper.vantaloader.api.ClientCommandRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Global command router with per-mod ownership and deterministic cleanup. */
public final class ModClientCommandService implements ClientCommandDispatcher, AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger("VantaLoader.Commands");
    private static final int MAX_INPUT_LENGTH = 32_767;
    private final ConcurrentMap<String, Registration> commands = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final boolean available;

    public ModClientCommandService(boolean available) { this.available = available; }

    public Scope createScope(String modId) {
        if (closed.get()) throw new IllegalStateException("Client command service is closed");
        return new Scope(modId);
    }

    @Override
    public boolean dispatch(String input, Consumer<String> feedback) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(feedback, "feedback");
        if (!available || closed.get() || input.length() > MAX_INPUT_LENGTH) return false;
        String commandLine = input.startsWith("/") ? input.substring(1) : input;
        int first = 0;
        while (first < commandLine.length() && Character.isWhitespace(commandLine.charAt(first))) first++;
        int end = first;
        while (end < commandLine.length() && !Character.isWhitespace(commandLine.charAt(end))) end++;
        if (first == end) return false;
        String name = commandLine.substring(first, end).toLowerCase(Locale.ROOT);
        Registration registration = commands.get(name);
        if (registration == null || registration.isClosed()) return false;

        final List<String> tokens;
        try {
            tokens = tokenize(commandLine.substring(end));
        } catch (IllegalArgumentException malformed) {
            feedback.accept("§c" + malformed.getMessage());
            feedback.accept("§7Usage: /" + registration.command.usage());
            return true;
        }
        try {
            registration.command.callback().execute(new ClientCommandContext(
                    registration.modId, registration.command, input,
                    tokens, feedback));
        } catch (Throwable failure) {
            if (failure instanceof VirtualMachineError fatal) throw fatal;
            if (failure instanceof ThreadDeath death) throw death;
            LOGGER.log(Level.SEVERE, "Client command /" + name + " from mod '"
                    + registration.modId + "' failed", failure);
            feedback.accept("§cCommand /" + name + " failed: "
                    + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()));
        }
        return true;
    }

    /** Tokenizes whitespace-separated arguments with single/double quotes and backslash escapes. */
    static List<String> tokenize(String input) {
        List<String> result = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        char quote = 0;
        boolean escaped = false;
        boolean tokenStarted = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (escaped) {
                token.append(c);
                escaped = false;
                tokenStarted = true;
            } else if (c == '\\') {
                escaped = true;
                tokenStarted = true;
            } else if (quote != 0) {
                if (c == quote) quote = 0;
                else token.append(c);
                tokenStarted = true;
            } else if (c == '"' || c == '\'') {
                quote = c;
                tokenStarted = true;
            } else if (Character.isWhitespace(c)) {
                if (tokenStarted) {
                    result.add(token.toString());
                    token.setLength(0);
                    tokenStarted = false;
                }
            } else {
                token.append(c);
                tokenStarted = true;
            }
        }
        if (escaped) throw new IllegalArgumentException("Command ends with an incomplete escape");
        if (quote != 0) throw new IllegalArgumentException("Command contains an unterminated quote");
        if (tokenStarted) result.add(token.toString());
        return List.copyOf(result);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        for (Registration registration : List.copyOf(commands.values())) registration.close();
    }

    public final class Scope implements ClientCommandRegistry {
        private final String modId;
        private final AtomicBoolean scopeClosed = new AtomicBoolean();

        private Scope(String modId) {
            if (modId == null || !modId.matches("[a-z][a-z0-9_-]{1,63}"))
                throw new IllegalArgumentException("Invalid mod ID for command scope: " + modId);
            this.modId = modId;
        }

        @Override public boolean isAvailable() { return available && !closed.get() && !scopeClosed.get(); }

        @Override
        public ClientCommandRegistration register(ClientCommand command) {
            Objects.requireNonNull(command, "command");
            ensureOpen();
            if (!available) throw new UnsupportedOperationException("The active Minecraft adapter does not support client commands");
            Registration registration = new Registration(modId, command, this);
            if (commands.putIfAbsent(command.name(), registration) != null)
                throw new IllegalStateException("Client command '/" + command.name() + "' is already registered");
            if (!isAvailable()) {
                registration.close();
                throw new IllegalStateException("Client command scope closed during registration");
            }
            LOGGER.info("Registered client command /" + command.name() + " for mod " + modId);
            return registration;
        }

        @Override public Optional<ClientCommandRegistration> find(String name) {
            Objects.requireNonNull(name, "name");
            ensureOpen();
            Registration registration = commands.get(name);
            return registration != null && registration.scope == this
                    ? Optional.of(registration) : Optional.empty();
        }

        private void ensureOpen() {
            if (!isAvailable()) throw new IllegalStateException("Client command scope is closed");
        }

        @Override public void close() {
            if (!scopeClosed.compareAndSet(false, true)) return;
            for (Registration registration : List.copyOf(commands.values())) {
                if (registration.scope == this) registration.close();
            }
        }
    }

    private final class Registration implements ClientCommandRegistration {
        private final String modId;
        private final ClientCommand command;
        private final Scope scope;
        private final AtomicBoolean registrationClosed = new AtomicBoolean();

        private Registration(String modId, ClientCommand command, Scope scope) {
            this.modId = modId;
            this.command = command;
            this.scope = scope;
        }
        @Override public ClientCommand command() { return command; }
        @Override public String modId() { return modId; }
        @Override public boolean isClosed() { return registrationClosed.get(); }
        @Override public void close() {
            if (registrationClosed.compareAndSet(false, true)) commands.remove(command.name(), this);
        }
    }
}
