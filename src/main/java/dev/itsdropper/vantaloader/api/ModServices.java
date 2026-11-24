package dev.itsdropper.vantaloader.api;

import java.util.Optional;

/**
 * Typed inter-mod service discovery.
 *
 * <p>Registrations are owned by the registering mod and are automatically removed
 * when that mod shuts down. A service is visible only after its provider registers it.
 * Mods should declare metadata dependencies on providers whose services they require.</p>
 */
public interface ModServices {
    <T> ServiceRegistration register(ServiceKey<T> key, T service);
    <T> Optional<T> find(ServiceKey<T> key);

    default <T> T require(ServiceKey<T> key) {
        return find(key).orElseThrow(() ->
                new IllegalStateException("Required VMA service is not registered: " + key.id()));
    }

    default boolean isRegistered(ServiceKey<?> key) {
        return find(key).isPresent();
    }
}
