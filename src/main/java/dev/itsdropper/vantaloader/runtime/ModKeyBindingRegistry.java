package dev.itsdropper.vantaloader.runtime;

import dev.itsdropper.vantaloader.api.EventPriority;
import dev.itsdropper.vantaloader.api.EventSubscription;
import dev.itsdropper.vantaloader.api.KeyBinding;
import dev.itsdropper.vantaloader.api.KeyBindingAction;
import dev.itsdropper.vantaloader.api.KeyBindingRegistry;
import dev.itsdropper.vantaloader.api.KeyBindingRegistration;
import dev.itsdropper.vantaloader.api.KeyInputEvent;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Input-thread key dispatch scoped to one mod's lifecycle. */
final class ModKeyBindingRegistry implements KeyBindingRegistry {
    private final ConcurrentMap<String, Registration> registrations = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final EventSubscription inputSubscription;

    ModKeyBindingRegistry(dev.itsdropper.vantaloader.api.EventBus events) {
        inputSubscription = Objects.requireNonNull(events, "events")
                .subscribe(KeyInputEvent.class, EventPriority.NORMAL, this::onInput);
    }

    @Override
    public KeyBindingRegistration register(KeyBinding binding) {
        Objects.requireNonNull(binding, "binding");
        ensureOpen();
        Registration registration = new Registration(binding);
        Registration existing = registrations.putIfAbsent(binding.id(), registration);
        if (existing != null) {
            throw new IllegalStateException("Duplicate key binding ID '" + binding.id() + "'");
        }
        if (closed.get()) {
            registration.close();
            throw new IllegalStateException("Key binding registry is closed");
        }
        return registration;
    }

    @Override
    public Optional<KeyBindingRegistration> find(String id) {
        Objects.requireNonNull(id, "id");
        ensureOpen();
        return Optional.ofNullable(registrations.get(id));
    }

    private void onInput(KeyInputEvent event) {
        if (closed.get()) return;
        List<Throwable> failures = new java.util.ArrayList<>();
        for (Registration registration : List.copyOf(registrations.values())) {
            try {
                registration.accept(event);
            } catch (Throwable failure) {
                if (failure instanceof VirtualMachineError fatal) throw fatal;
                if (failure instanceof ThreadDeath death) throw death;
                failures.add(failure);
            }
        }
        if (!failures.isEmpty()) {
            IllegalStateException aggregate = new IllegalStateException(
                    failures.size() + " VMA key binding callback(s) failed");
            failures.forEach(aggregate::addSuppressed);
            throw aggregate;
        }
    }

    private void ensureOpen() {
        if (closed.get()) throw new IllegalStateException("Key binding registry is closed");
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        inputSubscription.close();
        for (Registration registration : List.copyOf(registrations.values())) registration.close();
    }

    private final class Registration implements KeyBindingRegistration {
        private final KeyBinding binding;
        private boolean pressed;
        private boolean registrationClosed;

        private Registration(KeyBinding binding) {
            this.binding = binding;
        }

        @Override public KeyBinding binding() { return binding; }

        @Override
        public synchronized boolean isPressed() {
            return !registrationClosed && pressed;
        }

        @Override
        public synchronized boolean isClosed() {
            return registrationClosed;
        }

        private synchronized void accept(KeyInputEvent event) {
            if (registrationClosed || binding.defaultKeyCode() == KeyBinding.UNBOUND
                    || binding.defaultKeyCode() != event.keyCode()) return;

            if (event.action() == KeyInputEvent.PRESS) {
                if (event.modifiers() == binding.modifiers() && !pressed) {
                    pressed = true;
                    binding.callback().onAction(KeyBindingAction.PRESSED);
                }
            } else if (event.action() == KeyInputEvent.REPEAT) {
                if (pressed) binding.callback().onAction(KeyBindingAction.REPEATED);
            } else if (event.action() == KeyInputEvent.RELEASE && pressed) {
                pressed = false;
                binding.callback().onAction(KeyBindingAction.RELEASED);
            }
        }

        @Override
        public synchronized void close() {
            if (registrationClosed) return;
            registrationClosed = true;
            pressed = false;
            registrations.remove(binding.id(), this);
        }
    }
}
