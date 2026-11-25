package dev.itsdropper.vantaloader.runtime;

import dev.itsdropper.vantaloader.api.KeyBinding;
import dev.itsdropper.vantaloader.api.KeyBindingAction;
import dev.itsdropper.vantaloader.api.KeyInputEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModKeyBindingRegistryTest {
    private static final Object HANDLER = new Object();

    @Test
    void dispatchesPressRepeatAndReleaseAndTracksPressedState() {
        ModEventBus events = new ModEventBus();
        ModKeyBindingRegistry registry = new ModKeyBindingRegistry(events, true);
        List<KeyBindingAction> actions = new ArrayList<>();
        var registration = registry.register(new KeyBinding(
                "toggle", "Toggle feature", 65, 0, actions::add));

        events.publish(input(65, KeyInputEvent.PRESS, 0));
        assertTrue(registration.isPressed());
        events.publish(input(65, KeyInputEvent.REPEAT, 0));
        assertTrue(registration.isPressed());
        events.publish(input(65, KeyInputEvent.RELEASE, 0));

        assertFalse(registration.isPressed());
        assertEquals(List.of(KeyBindingAction.PRESSED, KeyBindingAction.REPEATED,
                KeyBindingAction.RELEASED), actions);
        registry.close();
    }

    @Test
    void requiresExactModifiersAndIgnoresUnboundBindings() {
        ModEventBus events = new ModEventBus();
        ModKeyBindingRegistry registry = new ModKeyBindingRegistry(events, true);
        List<KeyBindingAction> actions = new ArrayList<>();
        registry.register(new KeyBinding("modified", "Modified", 65,
                KeyBinding.MOD_CONTROL, actions::add));
        registry.register(new KeyBinding("unbound", "Unbound", KeyBinding.UNBOUND,
                0, actions::add));

        events.publish(input(65, KeyInputEvent.PRESS, 0));
        events.publish(input(65, KeyInputEvent.PRESS, KeyBinding.MOD_CONTROL));
        events.publish(input(65, KeyInputEvent.RELEASE, 0));

        assertEquals(List.of(KeyBindingAction.PRESSED, KeyBindingAction.RELEASED), actions);
        registry.close();
    }

    @Test
    void registrationsAreUniqueAndCanBeRemovedIndividually() {
        ModEventBus events = new ModEventBus();
        ModKeyBindingRegistry registry = new ModKeyBindingRegistry(events, true);
        KeyBinding binding = new KeyBinding("toggle", "Toggle", 65, 0, action -> { });
        var registration = registry.register(binding);

        assertSame(registration, registry.find("toggle").orElseThrow());
        assertThrows(IllegalStateException.class, () -> registry.register(binding));

        registration.close();
        registration.close();
        assertTrue(registration.isClosed());
        assertFalse(registration.isPressed());
        assertTrue(registry.find("toggle").isEmpty());
        registry.close();
    }

    @Test
    void callbackFailuresDoNotPreventOtherBindingsFromReceivingInput() {
        ModEventBus events = new ModEventBus();
        ModKeyBindingRegistry registry = new ModKeyBindingRegistry(events, true);
        List<String> received = new ArrayList<>();
        registry.register(new KeyBinding("broken", "Broken", 65, 0, action -> {
            throw new IllegalArgumentException("callback failed");
        }));
        registry.register(new KeyBinding("healthy", "Healthy", 65, 0,
                action -> received.add(action.name())));

        assertThrows(IllegalStateException.class,
                () -> events.publish(input(65, KeyInputEvent.PRESS, 0)));
        assertEquals(List.of("PRESSED"), received);
        registry.close();
    }

    @Test
    void refusesRegistrationWhenTheActiveAdapterHasNoKeyboardSupport() {
        ModKeyBindingRegistry registry = new ModKeyBindingRegistry(new ModEventBus(), false);

        assertFalse(registry.isAvailable());
        assertTrue(registry.find("missing").isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> registry.register(new KeyBinding("toggle", "Toggle", 65, 0, action -> { })));
        registry.close();
    }

    private static KeyInputEvent input(int keyCode, int action, int modifiers) {
        return new KeyInputEvent(HANDLER, 1L, keyCode, 30, action, modifiers);
    }
}
