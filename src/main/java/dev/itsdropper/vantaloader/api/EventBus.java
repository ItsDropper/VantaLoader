package dev.itsdropper.vantaloader.api;

import java.util.function.Consumer;

/**
 * Synchronous typed event channel for communication between native mods.
 * Lower listener priorities run first; equal priorities preserve registration order.
 */
public interface EventBus {
    <E> EventSubscription subscribe(Class<E> eventType, int priority, Consumer<? super E> listener);

    /** Delivers an event to listeners registered for its concrete type or a supertype. */
    void publish(Object event);
}
