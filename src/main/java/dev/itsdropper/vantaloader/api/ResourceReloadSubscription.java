package dev.itsdropper.vantaloader.api;

/** Removes a mod-owned resource reload registration. */
@FunctionalInterface
public interface ResourceReloadSubscription extends AutoCloseable {
    @Override void close();
}
