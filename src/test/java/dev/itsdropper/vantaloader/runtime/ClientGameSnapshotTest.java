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

import static org.junit.jupiter.api.Assertions.*;

class ClientGameSnapshotTest {
    @TempDir Path temporaryDirectory;

    @Test
    void publishesImmutablePlayerAndWorldSnapshotsAtBothTickPhases() throws Exception {
        Path mappingsFile = temporaryDirectory.resolve("client.txt");
        Files.writeString(mappingsFile, """
                net.minecraft.client.Minecraft -> a:
                net.minecraft.client.player.LocalPlayer -> b:
                net.minecraft.client.multiplayer.ClientLevel -> c:
                net.minecraft.world.entity.Entity -> d:
                net.minecraft.world.entity.LivingEntity -> e:
                net.minecraft.world.entity.player.Player -> f:
                net.minecraft.world.level.Level -> g:
                net.minecraft.world.food.FoodData -> h:
                net.minecraft.resources.ResourceKey -> i:
                net.minecraft.resources.ResourceLocation -> j:
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
            FakeClient client = new FakeClient(new FakePlayer(), new FakeLevel());
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
                net.minecraft.client.Minecraft -> a:
                net.minecraft.client.player.LocalPlayer -> b:
                net.minecraft.client.multiplayer.ClientLevel -> c:
                net.minecraft.world.entity.Entity -> d:
                net.minecraft.world.entity.LivingEntity -> e:
                net.minecraft.world.entity.player.Player -> f:
                net.minecraft.world.level.Level -> g:
                net.minecraft.world.food.FoodData -> h:
                net.minecraft.resources.ResourceKey -> i:
                net.minecraft.resources.ResourceLocation -> j:
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

    public static final class FakeClient {
        public final FakePlayer player;
        public final FakeLevel level;
        public FakeClient(FakePlayer player, FakeLevel level) {
            this.player = player;
            this.level = level;
        }
    }

    public static final class FakePlayer {
        public boolean onGround = true;
        public double getX() { return 12.5; }
        public double getY() { return 64.0; }
        public double getZ() { return -3.25; }
        public float getYRot() { return 90.0f; }
        public float getXRot() { return -15.0f; }
        public float getHealth() { return 17.0f; }
        public float getMaxHealth() { return 20.0f; }
        public FakeFoodData getFoodData() { return new FakeFoodData(); }
    }

    public static final class FakeFoodData {
        public int getFoodLevel() { return 18; }
    }

    public static final class FakeLevel {
        public FakeResourceKey dimension() { return new FakeResourceKey(); }
        public long getGameTime() { return 123456L; }
    }

    public static final class FakeResourceKey {
        public FakeResourceLocation location() { return new FakeResourceLocation(); }
    }

    public static final class FakeResourceLocation {
        @Override public String toString() { return "minecraft:overworld"; }
    }
}
