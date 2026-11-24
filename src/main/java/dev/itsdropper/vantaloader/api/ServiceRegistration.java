package dev.itsdropper.vantaloader.api;

/** A mod-owned service registration that is removed when closed or when its owner stops. */
public interface ServiceRegistration extends AutoCloseable {
    ServiceKey<?> key();
    boolean isClosed();
    @Override void close();
}
