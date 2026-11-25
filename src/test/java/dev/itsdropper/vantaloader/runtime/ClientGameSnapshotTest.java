package dev.itsdropper.vantaloader.runtime;

import dev.itsdropper.vantaloader.api.ClientGameTickEvent;
import dev.itsdropper.vantaloader.api.ClientPlayerSnapshot;
import dev.itsdropper.vantaloader.api.ClientTickEvent;
import dev.itsdropper.vantaloader.api.ClientWorldSnapshot;
import dev.itsdropper.vantaloader.bootstrap.MinecraftHooks;
import dev.itsdropper.vantaloader.mapping.MinecraftMappings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class ClientGameSnapshotTest {
    @TempDir Path temporaryDirectory;

    @Test
    void publishesImmutablePlayerAndWorldSnapshotsAtBothTickPhases() throws Exception {
        Path mappingsFile = temporaryDirectory.resolve("client.txt");
        Files.writeString(mappingsFile, """
                net.minecraft.client.Minecraft -> dev.itsdropper.vantaloader.runtime.ClientGameSnapshotTest$FakeClient:
                net.minecraft.client.player.LocalPlayer -> dev.itsdropper.vantaloader.runtime.ClientGameSnapshotTest$FakeLocalPlayer:
                net.minecraft.client.multiplayer.ClientLevel -> dev.itsdropper.vantaloader.runtime.ClientGameSnapshotTest$FakeClientLevel:
                net.minecraft.world.entity.Entity -> dev.itsdropper.vantaloader.runtime.ClientGameSnapshotTest$FakeEntity:
                    1:1:boolean onGround -> bc
                net.minecraft.world.entity.EntityType -> dev.itsdropper.vantaloader.runtime.ClientGameSnapshotTest$FakeEntityType:
                net.minecraft.network.chat.Component -> dev.itsdropper.vantaloader.runtime.ClientGameSnapshotTest$FakeComponent:
                net.minecraft.world.entity.LivingEntity -> dev.itsdropper.vantaloader.runtime.ClientGameSnapshotTest$FakeLivingEntity:
                net.minecraft.world.entity.player.Player -> dev.itsdropper.vantaloader.runtime.ClientGameSnapshotTest$FakePlayer:
                net.minecraft.world.level.Level -> dev.itsdropper.vantaloader.runtime.ClientGameSnapshotTest$FakeLevel:
                net.minecraft.world.level.LevelAccessor -> dev.itsdropper.vantaloader.runtime.ClientGameSnapshotTest$FakeLevelAccessor:
                net.minecraft.world.food.FoodData -> dev.itsdropper.vantaloader.runtime.ClientGameSnapshotTest$FakeFoodData:
                net.minecraft.resources.ResourceKey -> dev.itsdropper.vantaloader.runtime.ClientGameSnapshotTest$FakeResourceKey:
                net.minecraft.resources.Identifier -> dev.itsdropper.vantaloader.runtime.ClientGameSnapshotTest$FakeIdentifier:
                """);
        MinecraftHooks.configureMappings(MinecraftMappings.read(mappingsFile));

        ModEventBus bus = new ModEventBus();
        List<ClientGameTickEvent> events = new ArrayList<>();
        AtomicReference<ClientGameTickEvent> received = new AtomicReference<>();
        bus.subscribe(ClientGameTickEvent.class, 0, event -> {
            events.add(event);
            received.set(event);
        });
        MinecraftHooks.bind(bus);
        try {
            FakeClient client = new FakeClient(new FakeLocalPlayer(), new FakeClientLevel());
            assertTrue(snapshotDirectly(client).inWorld());
            MinecraftHooks.onClientTick(client, true);

            ClientGameTickEvent start = received.get();
            assertNotNull(start);
            assertEquals(ClientTickEvent.Phase.START, start.phase());
            assertTrue(start.snapshot().inWorld());

            ClientPlayerSnapshot player = start.snapshot().player().orElseThrow();
            assertEquals(12.5, player.x());
            assertEquals(64.0, player.y());
            assertEquals(-3.25, player.z());
            assertEquals(90.0f, player.yaw());
            assertEquals(-15.0f, player.pitch());
            assertEquals(17.0f, player.health());
            assertEquals(20.0f, player.maxHealth());
            assertEquals(18, player.foodLevel());
            assertTrue(player.onGround());

            ClientWorldSnapshot world = start.snapshot().world().orElseThrow();
            assertEquals("minecraft:overworld", world.dimensionId());
            assertEquals(123456L, world.gameTime());
            assertEquals(1, world.entities().size());
            var zombie = world.entities().get(0);
            assertEquals("entity.minecraft.zombie", zombie.typeDescriptionId());
            assertEquals("Zombie", zombie.displayName());
            assertTrue(zombie.alive());
            assertEquals(17.0f, zombie.health().orElseThrow());
            assertEquals(1, world.entitiesWithin(12.5, 64.0, -3.25, 0).size());
            assertTrue(world.entitiesWithin(0, 0, 0, 1).isEmpty());
            assertThrows(IllegalArgumentException.class, () -> world.entitiesWithin(0, 0, 0, -1));

            MinecraftHooks.onClientTickEnd();
            assertEquals(List.of(ClientTickEvent.Phase.START, ClientTickEvent.Phase.END),
                    events.stream().map(ClientGameTickEvent::phase).toList());
            assertEquals(123456L, events.get(1).snapshot().world().orElseThrow().gameTime());
        } finally {
            MinecraftHooks.unbind(bus);
        }
    }

    @Test
    void representsTitleScreenStateWithoutInventingPlayerOrWorld() throws Exception {
        Path mappingsFile = temporaryDirectory.resolve("client.txt");
        Files.writeString(mappingsFile, """
                net.minecraft.client.Minecraft -> dev.itsdropper.vantaloader.runtime.ClientGameSnapshotTest$FakeClient:
                net.minecraft.client.player.LocalPlayer -> dev.itsdropper.vantaloader.runtime.ClientGameSnapshotTest$FakeLocalPlayer:
                net.minecraft.client.multiplayer.ClientLevel -> dev.itsdropper.vantaloader.runtime.ClientGameSnapshotTest$FakeClientLevel:
                net.minecraft.world.entity.Entity -> dev.itsdropper.vantaloader.runtime.ClientGameSnapshotTest$FakeEntity:
                    1:1:boolean onGround -> bc
                net.minecraft.world.entity.LivingEntity -> dev.itsdropper.vantaloader.runtime.ClientGameSnapshotTest$FakeLivingEntity:
                net.minecraft.world.entity.player.Player -> dev.itsdropper.vantaloader.runtime.ClientGameSnapshotTest$FakePlayer:
                net.minecraft.world.level.Level -> dev.itsdropper.vantaloader.runtime.ClientGameSnapshotTest$FakeLevel:
                net.minecraft.world.food.FoodData -> dev.itsdropper.vantaloader.runtime.ClientGameSnapshotTest$FakeFoodData:
                net.minecraft.resources.ResourceKey -> dev.itsdropper.vantaloader.runtime.ClientGameSnapshotTest$FakeResourceKey:
                net.minecraft.resources.Identifier -> dev.itsdropper.vantaloader.runtime.ClientGameSnapshotTest$FakeIdentifier:
                """);
        MinecraftHooks.configureMappings(MinecraftMappings.read(mappingsFile));

        ModEventBus bus = new ModEventBus();
        AtomicReference<ClientGameTickEvent> received = new AtomicReference<>();
        bus.subscribe(ClientGameTickEvent.class, 0, received::set);
        MinecraftHooks.bind(bus);
        try {
            MinecraftHooks.onClientTick(new FakeClient(null, null), true);
            assertNotNull(received.get());
            assertFalse(received.get().snapshot().inWorld());
            assertTrue(received.get().snapshot().player().isEmpty());
            assertTrue(received.get().snapshot().world().isEmpty());
        } finally {
            MinecraftHooks.unbind(bus);
            MinecraftHooks.onClientTickEnd();
        }
    }

    private static dev.itsdropper.vantaloader.api.ClientGameSnapshot snapshotDirectly(Object client)
            throws Exception {
        Method snapshot = MinecraftHooks.class.getDeclaredMethod("snapshot", Object.class);
        snapshot.setAccessible(true);
        try {
            return (dev.itsdropper.vantaloader.api.ClientGameSnapshot) snapshot.invoke(null, client);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception exception) throw exception;
            if (cause instanceof Error error) throw error;
            throw new AssertionError(cause);
        }
    }

    public static final class FakeClient {
        public final FakeLocalPlayer player;
        public final FakeClientLevel level;
        public FakeClient(FakeLocalPlayer player, FakeClientLevel level) {
            this.player = player;
            this.level = level;
        }
    }

    public static final class FakeEntityType {
        public String getDescriptionId() { return "entity.minecraft.zombie"; }
    }

    public static final class FakeComponent {
        public String getString() { return "Zombie"; }
    }

    public static class FakeEntity {
        public java.util.UUID getUUID() { return java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"); }
        public FakeEntityType getType() { return new FakeEntityType(); }
        public FakeComponent getName() { return new FakeComponent(); }
        public boolean isAlive() { return true; }
        public boolean bc = true;
        public double getX() { return 12.5; }
        public double getY() { return 64.0; }
        public double getZ() { return -3.25; }
        public float getYRot() { return 90.0f; }
        public float getXRot() { return -15.0f; }
    }

    public static class FakeLivingEntity extends FakeEntity {
        public float getHealth() { return 17.0f; }
        public float getMaxHealth() { return 20.0f; }
    }

    public static class FakePlayer extends FakeLivingEntity {
        public FakeFoodData getFoodData() { return new FakeFoodData(); }
    }

    public static final class FakeLocalPlayer extends FakePlayer {
        // Simulates a subclass field that shares the mapped name but has another type.
        public int bc = 1;
    }

    public static final class FakeFoodData {
        public int getFoodLevel() { return 18; }
    }

    public interface FakeLevelAccessor {
        long getGameTime();
    }

    public static class FakeLevel implements FakeLevelAccessor {
        public FakeResourceKey dimension() { return new FakeResourceKey(); }
        @Override public long getGameTime() { return 123456L; }
    }

    public static final class FakeClientLevel extends FakeLevel {
        public Iterable<FakeEntity> entitiesForRendering() { return List.of(new FakeLivingEntity()); }
    }

    public static final class FakeResourceKey {
        public FakeIdentifier identifier() { return new FakeIdentifier(); }
    }

    public static final class FakeIdentifier {
        @Override public String toString() { return "minecraft:overworld"; }
    }
}
