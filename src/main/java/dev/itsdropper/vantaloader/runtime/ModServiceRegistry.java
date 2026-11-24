package dev.itsdropper.vantaloader.runtime;

import dev.itsdropper.vantaloader.api.ModServices;
import dev.itsdropper.vantaloader.api.ServiceKey;
import dev.itsdropper.vantaloader.api.ServiceRegistration;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runtime-wide registry with registrations scoped to their owning mod. */
final class ModServiceRegistry {
    private final ConcurrentMap<String, Registration<?>> services = new ConcurrentHashMap<>();

    Scope createScope(String modId) {
        return new Scope(Objects.requireNonNull(modId, "modId"));
    }

    final class Scope implements ModServices, AutoCloseable {
        private final String ownerId;
        private final Set<Registration<?>> owned = ConcurrentHashMap.newKeySet();
        private final AtomicBoolean closed = new AtomicBoolean();

        private Scope(String ownerId) {
            this.ownerId = ownerId;
        }

        @Override
        public <T> ServiceRegistration register(ServiceKey<T> key, T service) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(service, "service");
            if (!key.serviceType().isInstance(service)) {
                throw new IllegalArgumentException("Service for '" + key.id()
                        + "' is not an instance of " + key.serviceType().getName());
            }
            ensureOpen();
            Registration<T> registration = new Registration<>(this, key, service);
            Registration<?> existing = services.putIfAbsent(key.id(), registration);
            if (existing != null) {
                throw new IllegalStateException("VMA service '" + key.id()
                        + "' is already registered by another mod");
            }
            owned.add(registration);
            if (closed.get()) {
                registration.close();
                throw new IllegalStateException("Service scope for mod '" + ownerId + "' is closed");
            }
            return registration;
        }

        @Override
        public <T> Optional<T> find(ServiceKey<T> key) {
            Objects.requireNonNull(key, "key");
            ensureOpen();
            Registration<?> registration = services.get(key.id());
            if (registration == null) return Optional.empty();
            if (!registration.key.equals(key)) {
                throw new IllegalStateException("VMA service '" + key.id()
                        + "' was registered with incompatible type "
                        + registration.key.serviceType().getName());
            }
            return Optional.of(key.serviceType().cast(registration.service));
        }

        private void ensureOpen() {
            if (closed.get()) throw new IllegalStateException(
                    "Service scope for mod '" + ownerId + "' is closed");
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            for (Registration<?> registration : Set.copyOf(owned)) registration.close();
        }
    }

    private final class Registration<T> implements ServiceRegistration {
        private final Scope owner;
        private final ServiceKey<T> key;
        private final T service;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Registration(Scope owner, ServiceKey<T> key, T service) {
            this.owner = owner;
            this.key = key;
            this.service = service;
        }

        @Override public ServiceKey<T> key() { return key; }
        @Override public boolean isClosed() { return closed.get(); }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            services.remove(key.id(), this);
            owner.owned.remove(this);
        }
    }
}
