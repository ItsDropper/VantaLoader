package dev.itsdropper.vantaloader.api;

/** Executes a registered client-local command on Minecraft's client thread. */
@FunctionalInterface
public interface ClientCommandCallback {
    void execute(ClientCommandContext context) throws Exception;
}
