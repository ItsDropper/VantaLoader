package dev.itsdropper.vantaloader.bootstrap;

import dev.itsdropper.vantaloader.api.ClientTickEvent;
import dev.itsdropper.vantaloader.api.KeyInputEvent;
import dev.itsdropper.vantaloader.api.EventBus;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Static bridge called by bytecode injected into the Minecraft client. */
public final class MinecraftHooks {
    private static final Logger LOGGER = Logger.getLogger("VantaLoader.GameHooks");
    private static final AtomicReference<EventBus> EVENT_BUS = new AtomicReference<>();
    private static final AtomicReference<Object> ACTIVE_CLIENT = new AtomicReference<>();
    private static final java.util.concurrent.atomic.AtomicBoolean KEYBOARD_INPUT_OBSERVED =
            new java.util.concurrent.atomic.AtomicBoolean();
    private static final java.util.concurrent.atomic.AtomicBoolean PLAYER_TICK_OBSERVED =
            new java.util.concurrent.atomic.AtomicBoolean();
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
        if (starting) ACTIVE_CLIENT.set(Objects.requireNonNull(client, "client"));
        publishClientTick(client, starting ? ClientTickEvent.Phase.START : ClientTickEvent.Phase.END);
    }

    /** Called immediately before Minecraft.tick returns; avoids loading local 0 from a chopped stack-map frame. */
    public static void onClientTickEnd() {
        Object client = ACTIVE_CLIENT.getAndSet(null);
        if (client != null) publishClientTick(client, ClientTickEvent.Phase.END);
    }


    /** Called by the exact-version keyboard adapter before vanilla input processing. */
    public static void onKeyInput(Object keyboardHandler, long windowHandle, int keyCode,
                                 int scanCode, int action, int modifiers) {
        if (KEYBOARD_INPUT_OBSERVED.compareAndSet(false, true)) {
            LOGGER.info("VantaLoader integration probe: keyboard input observed");
        }
        EventBus eventBus = EVENT_BUS.get();
        if (eventBus == null) return;
        try {
            eventBus.publish(new KeyInputEvent(
                    keyboardHandler, windowHandle, keyCode, scanCode, action, modifiers));
        } catch (Throwable failure) {
            if (failure instanceof VirtualMachineError fatal) throw fatal;
            if (failure instanceof ThreadDeath death) throw death;
            LOGGER.log(Level.SEVERE, "One or more VantaLoader keyboard input listeners failed", failure);
        }
    }

    /** Internal CI/runtime probe called from LocalPlayer.aiStep after mod transformations compose. */
    public static void onLocalPlayerTick() {
        if (PLAYER_TICK_OBSERVED.compareAndSet(false, true)) {
            LOGGER.info("VantaLoader gameplay probe: local player tick observed");
        }
    }

    private static void publishClientTick(Object client, ClientTickEvent.Phase phase) {
        EventBus eventBus = EVENT_BUS.get();
        if (eventBus == null) return;
        ClientTickEvent event = new ClientTickEvent(client, phase);
        try {
            eventBus.publish(event);
        } catch (Throwable failure) {
            if (failure instanceof VirtualMachineError fatal) throw fatal;
            if (failure instanceof ThreadDeath death) throw death;
            LOGGER.log(Level.SEVERE, "One or more VantaLoader client tick listeners failed", failure);
        }
    }
}
