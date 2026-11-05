package dev.itsdropper.vantaloader.runtime;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModEventBusTest {
    @Test
    void dispatchesAssignableTypesInPriorityThenRegistrationOrder() {
        ModEventBus bus = new ModEventBus();
        List<String> received = new ArrayList<>();
        bus.subscribe(Object.class, 0, event -> received.add("object"));
        bus.subscribe(Message.class, 10, event -> received.add("late"));
        bus.subscribe(Message.class, -10, event -> received.add("early"));
        bus.subscribe(Message.class, 10, event -> received.add("same-priority"));

        bus.publish(new Message());

        assertEquals(List.of("object", "early", "late", "same-priority"), received);
    }

    @Test
    void subscriptionCanBeRemovedAndScopeClosesAllOwnedListeners() {
        ModEventBus bus = new ModEventBus();
        List<String> received = new ArrayList<>();
        ModEventBus.Scope scope = new ModEventBus.Scope(bus);
        var removable = scope.subscribe(Message.class, 0, event -> received.add("removable"));
        scope.subscribe(Message.class, 0, event -> received.add("scoped"));

        removable.close();
        bus.publish(new Message());
        assertEquals(List.of("scoped"), received);

        scope.close();
        bus.publish(new Message());
        assertEquals(List.of("scoped"), received);
        assertThrows(IllegalStateException.class,
                () -> scope.subscribe(Message.class, 0, event -> { }));
        assertThrows(IllegalStateException.class, () -> scope.publish(new Message()));
    }

    @Test
    void continuesDispatchAndReportsAllListenerFailures() {
        ModEventBus bus = new ModEventBus();
        List<String> received = new ArrayList<>();
        bus.subscribe(Message.class, 0, event -> {
            throw new IllegalArgumentException("first");
        });
        bus.subscribe(Message.class, 1, event -> received.add("continued"));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> bus.publish(new Message()));
        assertEquals(1, failure.getSuppressed().length);
        assertEquals(List.of("continued"), received);
    }

    private record Message() { }
}
