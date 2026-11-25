package dev.itsdropper.vantaloader.api;

import java.util.Optional;

/** Mod-scoped client-local command registry. Commands never reach the remote server. */
public interface ClientCommandRegistry extends AutoCloseable {
    boolean isAvailable();
    ClientCommandRegistration register(ClientCommand command);
    Optional<ClientCommandRegistration> find(String name);
    @Override void close();
}
