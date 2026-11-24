package dev.itsdropper.vantaloader.api;

import java.util.function.Consumer;

/**
 * Synchronous typed event channel for communication between native mods.
 * Lower numeric priorities run first; equal priorities preserve registration order.
 * Subscriptions should be closed when their owning feature is disabled.
 */
public interface EventBus {
    /** Registers a listener at normal priority. */
    default <E> EventSubscription subscribe(Class<E> eventType, Consumer<? super E> listener) {
        return subscribe(eventType, EventPriority.NORMAL, listener);
    }

    /** Registers a listener using a named priority. */
    default <E> EventSubscription subscribe(Class<E> eventType, EventPriority priority,
                                            Consumer<? super E> listener) {
        return subscribe(eventType, java.util.Objects.requireNonNull(priority, "priority").value(), listener);
    }

    /** Registers a listener using an explicit numeric priority. */
    <E> EventSubscription subscribe(Class<E> eventType, int priority, Consumer<? super E> listener);

    /** Delivers an event to listeners registered for its concrete type or a supertype. */
    void publish(Object event);
}
