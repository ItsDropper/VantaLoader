package dev.itsdropper.vantaloader.api;

/** Lifecycle-owned handle for one client command. */
public interface ClientCommandRegistration extends AutoCloseable {
    ClientCommand command();
    String modId();
    boolean isClosed();
    @Override void close();
}
