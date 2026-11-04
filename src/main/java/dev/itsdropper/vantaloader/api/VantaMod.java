package dev.itsdropper.vantaloader.api;

/** Main entrypoint implemented by a native VantaLoader mod. */
public interface VantaMod {
    void onInitialize(ModContext context) throws Exception;

    default void onShutdown() throws Exception {
    }
}
