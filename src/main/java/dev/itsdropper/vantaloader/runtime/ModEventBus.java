package dev.itsdropper.vantaloader.runtime;

import dev.itsdropper.vantaloader.api.EventBus;
import dev.itsdropper.vantaloader.api.EventSubscription;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Thread-safe synchronous event dispatch with mod-owned subscription scopes. */
final class ModEventBus implements EventBus {
    private final CopyOnWriteArrayList<Listener<?>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public <E> EventSubscription subscribe(Class<E> eventType, int priority, Consumer<? super E> listener) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(listener, "listener");
        Listener<E> registration = new Listener<>(eventType, priority, sequence.getAndIncrement(), listener);
        listeners.add(registration);
        return () -> listeners.remove(registration);
    }

    @Override
    public void publish(Object event) {
        Objects.requireNonNull(event, "event");
        List<Listener<?>> matching = listeners.stream()
                .filter(listener -> listener.eventType().isInstance(event))
                .sorted(Comparator.comparingInt((Listener<?> listener) -> listener.priority())
                        .thenComparingLong(listener -> listener.sequence()))
                .toList();
        List<Throwable> failures = new ArrayList<>();
        for (Listener<?> listener : matching) {
            try {
                listener.accept(event);
            } catch (RuntimeException failure) {
                failures.add(failure);
            }
        }
        if (!failures.isEmpty()) {
            IllegalStateException aggregate = new IllegalStateException(
                    failures.size() + " event listener(s) failed for " + event.getClass().getName());
            failures.forEach(aggregate::addSuppressed);
            throw aggregate;
        }
    }

    private record Listener<E>(Class<E> eventType, int priority, long sequence,
                               Consumer<? super E> consumer) {
        private void accept(Object event) {
            consumer.accept(eventType.cast(event));
        }
    }

    static final class Scope implements EventBus, AutoCloseable {
        private final EventBus delegate;
        private final List<EventSubscription> owned = new ArrayList<>();
        private volatile boolean closed;

        Scope(EventBus delegate) {
            this.delegate = delegate;
        }

        @Override
        public synchronized <E> EventSubscription subscribe(Class<E> eventType, int priority,
                                               Consumer<? super E> listener) {
            if (closed) throw new IllegalStateException("Event scope is closed");
            EventSubscription subscription = delegate.subscribe(eventType, priority, listener);
            owned.add(subscription);
            return () -> {
                subscription.close();
                owned.remove(subscription);
            };
        }

        @Override
        public synchronized void publish(Object event) {
            if (closed) throw new IllegalStateException("Event scope is closed");
            delegate.publish(event);
        }

        @Override
        public synchronized void close() {
            if (closed) return;
            closed = true;
            owned.forEach(EventSubscription::close);
            owned.clear();
        }
    }
}
