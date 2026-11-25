package dev.itsdropper.vantaloader.runtime;

import dev.itsdropper.vantaloader.api.ClientScreenEvent;
import dev.itsdropper.vantaloader.api.ClientWorldLifecycleEvent;
import dev.itsdropper.vantaloader.bootstrap.MinecraftHooks;
import dev.itsdropper.vantaloader.mapping.MinecraftMappings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClientLifecycleEventsTest {
    @TempDir Path temporaryDirectory;

    @Test
    void publishesWorldAndScreenTransitionsWithoutLeakingMinecraftObjects() throws Exception {
        Path mappingsFile = temporaryDirectory.resolve("lifecycle.txt");
        Files.writeString(mappingsFile, """
                net.minecraft.client.Minecraft -> dev.itsdropper.vantaloader.runtime.ClientLifecycleEventsTest$FakeClient:
                net.minecraft.client.multiplayer.ClientLevel -> dev.itsdropper.vantaloader.runtime.ClientLifecycleEventsTest$FakeClientLevel:
                net.minecraft.client.gui.screens.Screen -> dev.itsdropper.vantaloader.runtime.ClientLifecycleEventsTest$FakeScreen:
                net.minecraft.world.level.Level -> dev.itsdropper.vantaloader.runtime.ClientLifecycleEventsTest$FakeLevel:
                net.minecraft.resources.ResourceKey -> dev.itsdropper.vantaloader.runtime.ClientLifecycleEventsTest$FakeResourceKey:
                net.minecraft.resources.Identifier -> dev.itsdropper.vantaloader.runtime.ClientLifecycleEventsTest$FakeIdentifier:
                """);
        MinecraftHooks.configureMappings(MinecraftMappings.read(mappingsFile));

        ModEventBus bus = new ModEventBus();
        List<ClientWorldLifecycleEvent> worlds = new ArrayList<>();
        List<ClientScreenEvent> screens = new ArrayList<>();
        bus.subscribe(ClientWorldLifecycleEvent.class, 0, worlds::add);
        bus.subscribe(ClientScreenEvent.class, 0, screens::add);
        MinecraftHooks.bind(bus);
        try {
            Method publish = MinecraftHooks.class.getDeclaredMethod("publishClientLifecycle", Object.class);
            publish.setAccessible(true);
            FakeClientLevel world = new FakeClientLevel("minecraft:overworld");
            publish.invoke(null, new FakeClient(world, new FakeTitleScreen()));
            publish.invoke(null, new FakeClient(world, new FakePauseScreen()));
            publish.invoke(null, new FakeClient(null, null));

            assertEquals(List.of(
                    ClientWorldLifecycleEvent.Transition.JOINED,
                    ClientWorldLifecycleEvent.Transition.LEFT),
                    worlds.stream().map(ClientWorldLifecycleEvent::transition).toList());
            assertEquals("minecraft:overworld", worlds.get(0).currentDimensionId().orElseThrow());
            assertEquals("minecraft:overworld", worlds.get(1).previousDimensionId().orElseThrow());

            assertEquals(List.of(ClientScreenEvent.Transition.OPENED,
                    ClientScreenEvent.Transition.CHANGED, ClientScreenEvent.Transition.CLOSED),
                    screens.stream().map(ClientScreenEvent::transition).toList());
            assertTrue(screens.get(0).previousScreenClass().isEmpty());
            assertTrue(screens.get(0).currentScreenClass().orElseThrow().endsWith("FakeTitleScreen"));
            assertTrue(screens.get(1).previousScreenClass().orElseThrow().endsWith("FakeTitleScreen"));
            assertTrue(screens.get(1).currentScreenClass().orElseThrow().endsWith("FakePauseScreen"));
            assertTrue(screens.get(2).currentScreenClass().isEmpty());
        } finally {
            MinecraftHooks.unbind(bus);
        }
    }

    public static final class FakeClient {
        public final FakeClientLevel level;
        public final FakeScreen screen;
        public FakeClient(FakeClientLevel level, FakeScreen screen) {
            this.level = level;
            this.screen = screen;
        }
    }

    public static class FakeScreen { }
    public static final class FakeTitleScreen extends FakeScreen { }
    public static final class FakePauseScreen extends FakeScreen { }

    public static class FakeLevel {
        private final String dimension;
        FakeLevel(String dimension) { this.dimension = dimension; }
        public FakeResourceKey dimension() { return new FakeResourceKey(dimension); }
    }

    public static final class FakeClientLevel extends FakeLevel {
        FakeClientLevel(String dimension) { super(dimension); }
    }

    public static final class FakeResourceKey {
        private final String dimension;
        FakeResourceKey(String dimension) { this.dimension = dimension; }
        public FakeIdentifier identifier() { return new FakeIdentifier(dimension); }
    }

    public static final class FakeIdentifier {
        private final String value;
        FakeIdentifier(String value) { this.value = value; }
        @Override public String toString() { return value; }
    }
}
