package dev.itsdropper.vantaloader.mapping;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MinecraftMappingsTest {
    @TempDir Path temporaryDirectory;

    @Test
    void parsesNamedToRuntimeAndRuntimeToNamedClassNames() throws Exception {
        Path file = temporaryDirectory.resolve("client_mappings.txt");
        Files.writeString(file, """
                # example official mapping
                net.minecraft.client.Minecraft -> abc:
                    1:1:void run() -> a
                net.minecraft.world.level.Level -> def:
                    1:1:void tick() -> b
                """);

        MinecraftMappings mappings = MinecraftMappings.read(file);
        assertEquals(2, mappings.classCount());
        assertEquals("abc", mappings.toRuntimeClassName("net.minecraft.client.Minecraft"));
        assertEquals("net.minecraft.client.Minecraft", mappings.toNamedClassName("abc"));
        assertEquals("com.example.Mod", mappings.toRuntimeClassName("com.example.Mod"));
        assertTrue(mappings.containsNamedClass("net.minecraft.world.level.Level"));
    }


    @Test
    void resolvesMethodsAndFieldsUsingExactDescriptors() throws Exception {
        Path file = temporaryDirectory.resolve("members.txt");
        Files.writeString(file, """
                net.minecraft.client.Minecraft -> a:
                    1:1:void tick(net.minecraft.world.level.Level,int) -> b
                    net.minecraft.world.level.Level currentLevel -> c
                net.minecraft.world.level.Level -> d:
                """);

        MinecraftMappings mappings = MinecraftMappings.read(file);
        assertEquals("b", mappings.toRuntimeMethodName(
                "net.minecraft.client.Minecraft", "tick",
                "(Lnet/minecraft/world/level/Level;I)V"));
        assertEquals("c", mappings.toRuntimeFieldName(
                "net.minecraft.client.Minecraft", "currentLevel",
                "Lnet/minecraft/world/level/Level;"));
        assertEquals("unknown", mappings.toRuntimeMethodName(
                "net.minecraft.client.Minecraft", "unknown", "()V"));
        assertEquals(1, mappings.methodCount());
        assertEquals(1, mappings.fieldCount());
        assertEquals("(Ld;I)V", mappings.runtimeDescriptor(
                "(Lnet/minecraft/world/level/Level;I)V"));
    }

    @Test
    void distinguishesOverloadedMethodsByDescriptor() throws Exception {
        Path file = temporaryDirectory.resolve("overloads.txt");
        Files.writeString(file, """
                net.minecraft.client.Minecraft -> a:
                    void tick(int) -> b
                    void tick(long) -> c
                """);

        MinecraftMappings mappings = MinecraftMappings.read(file);
        assertEquals("b", mappings.toRuntimeMethodName(
                "net.minecraft.client.Minecraft", "tick", "(I)V"));
        assertEquals("c", mappings.toRuntimeMethodName(
                "net.minecraft.client.Minecraft", "tick", "(J)V"));
    }

    @Test
    void rejectsConflictingOrMalformedClassMappings() throws Exception {
        Path file = temporaryDirectory.resolve("invalid.txt");
        Files.writeString(file, """
                a.A -> x:
                b.B -> x:
                """);
        assertThrows(IOException.class, () -> MinecraftMappings.read(file));

        Files.writeString(file, "not-a-valid-mapping\\n");
        assertThrows(IOException.class, () -> MinecraftMappings.read(file));
    }

    @Test
    void rejectsFilesWithoutClassEntries() throws Exception {
        Path file = temporaryDirectory.resolve("empty.txt");
        Files.writeString(file, "# only a comment\\n");
        assertThrows(IOException.class, () -> MinecraftMappings.read(file));
    }
}
