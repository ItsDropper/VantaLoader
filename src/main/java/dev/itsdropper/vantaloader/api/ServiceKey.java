package dev.itsdropper.vantaloader.api;

import java.util.Objects;

/** Stable, typed identity for a service shared between native mods. */
public record ServiceKey<T>(String id, Class<T> serviceType) {
    public ServiceKey {
        if (id == null || !id.matches("[a-z][a-z0-9_.-]{1,63}:[a-z][a-z0-9_./-]{0,127}")) {
            throw new IllegalArgumentException("Service IDs must use namespace:path syntax: " + id);
        }
        Objects.requireNonNull(serviceType, "serviceType");
        if (serviceType.isPrimitive() || serviceType == Void.class) {
            throw new IllegalArgumentException("Service type must be a reference type");
        }
    }

    public static <T> ServiceKey<T> of(String id, Class<T> serviceType) {
        return new ServiceKey<>(id, serviceType);
    }
}
