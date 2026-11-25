package dev.itsdropper.vantaloader.api;

import java.util.function.Consumer;

/** Loader-to-Minecraft bridge for routing a raw client command before it is sent to a server. */
public interface ClientCommandDispatcher {
    /** Returns true only when a registered VMA command consumed the input. */
    boolean dispatch(String input, Consumer<String> feedback);
}
