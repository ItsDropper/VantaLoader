package dev.itsdropper.vantaloader.runtime;

import dev.itsdropper.vantaloader.api.*;
import java.io.IOException;
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
    private final boolean available;
    private final ModConfig config;

    ModKeyBindingRegistry(EventBus events, boolean available) { this(events, available, null); }
    ModKeyBindingRegistry(EventBus events, boolean available, ModConfig config) {
        this.available = available;
        this.config = config;
        this.inputSubscription = available
                ? Objects.requireNonNull(events, "events").subscribe(KeyInputEvent.class, EventPriority.NORMAL, this::onInput)
                : () -> { };
    }

    @Override public boolean isAvailable() { return available && !closed.get(); }

    @Override public KeyBindingRegistration register(KeyBinding binding) {
        Objects.requireNonNull(binding, "binding");
        ensureOpen();
        if (!available) throw new UnsupportedOperationException("The active Minecraft adapter does not provide keyboard input events");
        Registration registration = new Registration(binding);
        if (registrations.putIfAbsent(binding.id(), registration) != null)
            throw new IllegalStateException("Duplicate key binding ID '" + binding.id() + "'");
        if (closed.get()) {
            registration.close();
            throw new IllegalStateException("Key binding registry is closed");
        }
        return registration;
    }

    @Override public Optional<KeyBindingRegistration> find(String id) {
        Objects.requireNonNull(id, "id");
        ensureOpen();
        return available ? Optional.ofNullable(registrations.get(id)) : Optional.empty();
    }

    private void onInput(KeyInputEvent event) {
        if (closed.get()) return;
        List<Throwable> failures = new java.util.ArrayList<>();
        for (Registration registration : List.copyOf(registrations.values())) {
            try { registration.accept(event); }
            catch (Throwable failure) {
                if (failure instanceof VirtualMachineError fatal) throw fatal;
                if (failure instanceof ThreadDeath death) throw death;
                failures.add(failure);
            }
        }
        if (!failures.isEmpty()) {
            IllegalStateException aggregate = new IllegalStateException(failures.size() + " VMA key binding callback(s) failed");
            failures.forEach(aggregate::addSuppressed);
            throw aggregate;
        }
    }

    private void ensureOpen() {
        if (closed.get()) throw new IllegalStateException("Key binding registry is closed");
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        inputSubscription.close();
        for (Registration registration : List.copyOf(registrations.values())) registration.close();
    }

    private final class Registration implements KeyBindingRegistration {
        private final KeyBinding binding;
        private int keyCode;
        private int modifiers;
        private boolean pressed;
        private boolean registrationClosed;

        private Registration(KeyBinding binding) {
            this.binding = binding;
            keyCode = config == null ? binding.defaultKeyCode() : config.get(keyCodeKey(binding));
            modifiers = config == null ? binding.modifiers() : config.get(modifiersKey(binding));
            validateMapping(keyCode, modifiers);
        }

        @Override public KeyBinding binding() { return binding; }
        @Override public synchronized int keyCode() { return keyCode; }
        @Override public synchronized int modifiers() { return modifiers; }
        @Override public synchronized boolean isPressed() { return !registrationClosed && pressed; }
        @Override public synchronized boolean isClosed() { return registrationClosed; }

        @Override public synchronized void remap(int newKeyCode, int newModifiers) {
            ensureRegistrationOpen();
            validateMapping(newKeyCode, newModifiers);
            if (config != null) {
                int previousKey = keyCode, previousModifiers = modifiers;
                config.set(keyCodeKey(binding), newKeyCode);
                config.set(modifiersKey(binding), newModifiers);
                try { config.save(); }
                catch (IOException failure) {
                    config.set(keyCodeKey(binding), previousKey);
                    config.set(modifiersKey(binding), previousModifiers);
                    throw new IllegalStateException("Could not persist key binding remap for '" + binding.id() + "'", failure);
                }
            }
            keyCode = newKeyCode;
            modifiers = newModifiers;
            pressed = false;
        }

        @Override public void resetToDefault() { remap(binding.defaultKeyCode(), binding.modifiers()); }

        private synchronized void accept(KeyInputEvent event) {
            if (registrationClosed || keyCode == KeyBinding.UNBOUND || keyCode != event.keyCode()) return;
            if (event.action() == KeyInputEvent.PRESS) {
                if (event.modifiers() == modifiers && !pressed) {
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

        private void ensureRegistrationOpen() {
            if (registrationClosed || closed.get()) throw new IllegalStateException("Key binding registration is closed");
        }

        @Override public synchronized void close() {
            if (registrationClosed) return;
            registrationClosed = true;
            pressed = false;
            registrations.remove(binding.id(), this);
        }
    }

    private static ConfigKey<Integer> keyCodeKey(KeyBinding binding) {
        return ConfigKey.integer("keybinding." + binding.id() + ".key", binding.defaultKeyCode(),
                KeyBinding.UNBOUND, KeyBinding.MAX_KEY_CODE);
    }

    private static ConfigKey<Integer> modifiersKey(KeyBinding binding) {
        return ConfigKey.integer("keybinding." + binding.id() + ".modifiers", binding.modifiers(),
                0, KeyBinding.MODIFIER_MASK);
    }

    private static void validateMapping(int keyCode, int modifiers) {
        if (keyCode < KeyBinding.UNBOUND || keyCode > KeyBinding.MAX_KEY_CODE)
            throw new IllegalArgumentException("Key code must be GLFW key code or UNBOUND");
        if (modifiers < 0 || (modifiers & ~KeyBinding.MODIFIER_MASK) != 0)
            throw new IllegalArgumentException("Unknown GLFW modifier bits: " + modifiers);
    }
}
