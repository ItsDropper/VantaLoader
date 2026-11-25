package dev.itsdropper.vantaloader.bootstrap;

import dev.itsdropper.vantaloader.api.ClientGameSnapshot;
import dev.itsdropper.vantaloader.api.ClientGameTickEvent;
import dev.itsdropper.vantaloader.api.ClientPlayerSnapshot;
import dev.itsdropper.vantaloader.api.ClientTickEvent;
import dev.itsdropper.vantaloader.api.ClientWorldSnapshot;
import dev.itsdropper.vantaloader.api.EventBus;
import dev.itsdropper.vantaloader.api.KeyInputEvent;
import dev.itsdropper.vantaloader.mapping.MinecraftMappings;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Static bridge called by bytecode injected into the Minecraft client. */
public final class MinecraftHooks {
    private static final Logger LOGGER = Logger.getLogger("VantaLoader.GameHooks");
    private static final AtomicReference<EventBus> EVENT_BUS = new AtomicReference<>();
    private static final AtomicReference<Object> ACTIVE_CLIENT = new AtomicReference<>();
    private static final AtomicReference<MinecraftMappings> MAPPINGS = new AtomicReference<>();
    private static final java.util.concurrent.atomic.AtomicBoolean KEYBOARD_INPUT_OBSERVED =
            new java.util.concurrent.atomic.AtomicBoolean();
    private static final java.util.concurrent.atomic.AtomicBoolean PLAYER_TICK_OBSERVED =
            new java.util.concurrent.atomic.AtomicBoolean();
    private static final java.util.concurrent.atomic.AtomicBoolean GAME_STATE_FAILURE_LOGGED =
            new java.util.concurrent.atomic.AtomicBoolean();
    private static final java.util.concurrent.atomic.AtomicBoolean KEY_INPUT_FAILURE_LOGGED =
            new java.util.concurrent.atomic.AtomicBoolean();

    private MinecraftHooks() { }

    public static void configureMappings(MinecraftMappings mappings) {
        MAPPINGS.set(Objects.requireNonNull(mappings, "mappings"));
    }

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

    /** Called by the exact-version adapter with Minecraft's structured KeyEvent input. */
    public static void onKeyInput(Object keyboardHandler, long windowHandle, int action, Object keyEvent) {
        if (KEYBOARD_INPUT_OBSERVED.compareAndSet(false, true)) {
            LOGGER.info("VantaLoader integration probe: keyboard input observed");
        }
        EventBus eventBus = EVENT_BUS.get();
        if (eventBus == null) return;
        final int keyCode;
        final int scanCode;
        final int modifiers;
        try {
            MinecraftMappings mappings = MAPPINGS.get();
            if (mappings == null) throw new IllegalStateException("Official Minecraft mappings are not configured");
            keyCode = ((Number) invoke(keyEvent, mappings,
                    "net.minecraft.client.input.KeyEvent", "key", "()I")).intValue();
            scanCode = ((Number) invoke(keyEvent, mappings,
                    "net.minecraft.client.input.KeyEvent", "scancode", "()I")).intValue();
            modifiers = ((Number) invoke(keyEvent, mappings,
                    "net.minecraft.client.input.KeyEvent", "modifiers", "()I")).intValue();
        } catch (Throwable failure) {
            rethrowFatal(failure);
            if (KEY_INPUT_FAILURE_LOGGED.compareAndSet(false, true)) {
                LOGGER.log(Level.SEVERE, "VantaLoader could not decode Minecraft's mapped KeyEvent", failure);
            }
            return;
        }
        try {
            eventBus.publish(new KeyInputEvent(
                    keyboardHandler, windowHandle, keyCode, scanCode, action, modifiers));
        } catch (Throwable failure) {
            rethrowFatal(failure);
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

        try {
            eventBus.publish(new ClientTickEvent(client, phase));
        } catch (Throwable failure) {
            rethrowFatal(failure);
            LOGGER.log(Level.SEVERE, "One or more VantaLoader client tick listeners failed", failure);
        }

        try {
            ClientGameSnapshot snapshot = snapshot(client);
            eventBus.publish(new ClientGameTickEvent(snapshot, phase));
        } catch (Throwable failure) {
            rethrowFatal(failure);
            if (GAME_STATE_FAILURE_LOGGED.compareAndSet(false, true)) {
                LOGGER.log(Level.SEVERE, "VantaLoader could not read the mapped Minecraft client game state; "
                        + "ClientGameTickEvent delivery is disabled for this process", failure);
            }
        }
    }

    private static ClientGameSnapshot snapshot(Object client) throws ReflectiveOperationException {
        MinecraftMappings mappings = MAPPINGS.get();
        if (mappings == null) throw new IllegalStateException("Official Minecraft mappings are not configured");

        Object player = readField(client, mappings, "net.minecraft.client.Minecraft", "player",
                "Lnet/minecraft/client/player/LocalPlayer;");
        Object level = readField(client, mappings, "net.minecraft.client.Minecraft", "level",
                "Lnet/minecraft/client/multiplayer/ClientLevel;");

        Optional<ClientPlayerSnapshot> playerSnapshot = player == null
                ? Optional.empty() : Optional.of(snapshotPlayer(player, mappings));
        Optional<ClientWorldSnapshot> worldSnapshot = level == null
                ? Optional.empty() : Optional.of(snapshotWorld(level, mappings));
        return new ClientGameSnapshot(playerSnapshot, worldSnapshot);
    }

    private static ClientPlayerSnapshot snapshotPlayer(Object player, MinecraftMappings mappings)
            throws ReflectiveOperationException {
        double x = ((Number) invoke(player, mappings, "net.minecraft.world.entity.Entity", "getX", "()D")).doubleValue();
        double y = ((Number) invoke(player, mappings, "net.minecraft.world.entity.Entity", "getY", "()D")).doubleValue();
        double z = ((Number) invoke(player, mappings, "net.minecraft.world.entity.Entity", "getZ", "()D")).doubleValue();
        float yaw = ((Number) invoke(player, mappings, "net.minecraft.world.entity.Entity", "getYRot", "()F")).floatValue();
        float pitch = ((Number) invoke(player, mappings, "net.minecraft.world.entity.Entity", "getXRot", "()F")).floatValue();
        float health = ((Number) invoke(player, mappings, "net.minecraft.world.entity.LivingEntity",
                "getHealth", "()F")).floatValue();
        float maxHealth = ((Number) invoke(player, mappings, "net.minecraft.world.entity.LivingEntity",
                "getMaxHealth", "()F")).floatValue();
        Object foodData = invoke(player, mappings, "net.minecraft.world.entity.player.Player",
                "getFoodData", "()Lnet/minecraft/world/food/FoodData;");
        int foodLevel = foodData == null ? -1 : ((Number) invoke(foodData, mappings,
                "net.minecraft.world.food.FoodData", "getFoodLevel", "()I")).intValue();
        boolean onGround = (Boolean) readField(player, mappings, "net.minecraft.world.entity.Entity",
                "onGround", "Z");
        return new ClientPlayerSnapshot(x, y, z, yaw, pitch, health, maxHealth, foodLevel, onGround);
    }

    private static ClientWorldSnapshot snapshotWorld(Object level, MinecraftMappings mappings)
            throws ReflectiveOperationException {
        Object dimension = invoke(level, mappings, "net.minecraft.world.level.Level",
                "dimension", "()Lnet/minecraft/resources/ResourceKey;");
        Object location = invoke(dimension, mappings, "net.minecraft.resources.ResourceKey",
                "identifier", "()Lnet/minecraft.resources.Identifier;");
        String dimensionId = String.valueOf(location);
        long gameTime = ((Number) invoke(level, mappings, "net.minecraft.world.level.LevelAccessor",
                "getGameTime", "()J")).longValue();
        return new ClientWorldSnapshot(dimensionId, gameTime);
    }

    private static Object readField(Object target, MinecraftMappings mappings, String owner,
                                    String name, String descriptor) throws ReflectiveOperationException {
        Objects.requireNonNull(target, "target");
        String runtimeName = mappings.runtimeFieldName(owner, name, descriptor);
        Class<?> declaringType = Class.forName(mappings.runtimeClassName(owner), false,
                target.getClass().getClassLoader());
        if (!declaringType.isAssignableFrom(target.getClass())) {
            throw new IllegalArgumentException("Mapped field owner " + declaringType.getName()
                    + " is not a supertype of " + target.getClass().getName());
        }
        Field field = declaringType.getDeclaredField(runtimeName);
        if (!field.trySetAccessible()) {
            throw new IllegalAccessException("Cannot access mapped field "
                    + declaringType.getName() + "." + runtimeName);
        }
        return field.get(target);
    }

    private static Object invoke(Object target, MinecraftMappings mappings, String owner,
                                  String name, String descriptor) throws ReflectiveOperationException {
        Objects.requireNonNull(target, "target");
        String runtimeName = mappings.runtimeMethodName(owner, name, descriptor);
        Class<?> declaringType = Class.forName(mappings.runtimeClassName(owner), false,
                target.getClass().getClassLoader());
        if (!declaringType.isAssignableFrom(target.getClass())) {
            throw new IllegalArgumentException("Mapped method owner " + declaringType.getName()
                    + " is not a supertype of " + target.getClass().getName());
        }
        Method method = declaringType.getDeclaredMethod(runtimeName);
        if (!method.trySetAccessible()) {
            throw new IllegalAccessException("Cannot access mapped method "
                    + declaringType.getName() + "." + runtimeName);
        }
        return method.invoke(target);
    }

    private static void rethrowFatal(Throwable failure) {
        if (failure instanceof VirtualMachineError fatal) throw fatal;
        if (failure instanceof ThreadDeath death) throw death;
    }
}
