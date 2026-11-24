package dev.itsdropper.vantaloader.api;

/** Callback invoked synchronously on Minecraft's input thread. */
@FunctionalInterface
public interface KeyBindingCallback {
    void onAction(KeyBindingAction action);
}
