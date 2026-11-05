package dev.itsdropper.vantaloader.api;

/** A removable event listener registration. */
@FunctionalInterface
public interface EventSubscription extends AutoCloseable {
    @Override
    void close();
}
