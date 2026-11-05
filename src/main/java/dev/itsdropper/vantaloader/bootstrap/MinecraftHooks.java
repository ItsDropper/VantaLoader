package dev.itsdropper.vantaloader.bootstrap;

import dev.itsdropper.vantaloader.api.ClientTickEvent;
import dev.itsdropper.vantaloader.api.EventBus;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Static bridge called by bytecode injected into the Minecraft client. */
public final class MinecraftHooks {
    private static final Logger LOGGER = Logger.getLogger("VantaLoader.GameHooks");
    private static final AtomicReference<EventBus> EVENT_BUS = new AtomicReference<>();
    private MinecraftHooks() { }

    public static void bind(EventBus eventBus) {
        Objects.requireNonNull(eventBus, "eventBus");
        EventBus current = EVENT_BUS.get();
        if (current == eventBus) return;
        if (!EVENT_BUS.compareAndSet(null, eventBus)) {
            throw new IllegalStateException("A VantaLoader Minecraft event bus is already bound");
        }
    }

    public static void unbind(EventBus eventBus) {
        if (eventBus != null) EVENT_BUS.compareAndSet(eventBus, null);
    }

    /** Listener failures are logged after all listeners run; they do not abort Minecraft's tick. */
    public static void onClientTick(Object client, boolean starting) {
        EventBus eventBus = EVENT_BUS.get();
        if (eventBus == null) return;
        ClientTickEvent event = new ClientTickEvent(client,
                starting ? ClientTickEvent.Phase.START : ClientTickEvent.Phase.END);
        try {
            eventBus.publish(event);
        } catch (Throwable failure) {
            if (failure instanceof VirtualMachineError fatal) throw fatal;
            if (failure instanceof ThreadDeath death) throw death;
            LOGGER.log(Level.SEVERE, "One or more VantaLoader client tick listeners failed", failure);
        }
    }
}
