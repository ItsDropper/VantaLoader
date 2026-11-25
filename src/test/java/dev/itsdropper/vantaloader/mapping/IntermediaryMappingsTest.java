package dev.itsdropper.vantaloader.mapping;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class IntermediaryMappingsTest {
    @TempDir Path temporaryDirectory;

    @Test
    void remapsIntermediaryMixinReferencesToObfuscatedRuntimeNames() throws Exception {
        String tiny = """
                tiny\t2\t0\tofficial\tintermediary
                c\tnet/minecraft/client/gui/screens/TitleScreen\tnet/minecraft/class_442
                \tm\t()V\tinit\tmethod_25426
                c\tnet/minecraft/network/Connection\tnet/minecraft/class_2535
                \tf\tLjava/net/SocketAddress;\taddress\tfield_11645
                """;
        IntermediaryMappings intermediary = IntermediaryMappings.read(
                new ByteArrayInputStream(tiny.getBytes(StandardCharsets.UTF_8)));

        Path officialMappings = temporaryDirectory.resolve("client.txt");
        Files.writeString(officialMappings, """
                net.minecraft.client.gui.screens.TitleScreen -> abc:
                    1:1:void init() -> a
                net.minecraft.network.Connection -> xyz:
                    java.net.SocketAddress address -> b
                """);
        MinecraftMappings runtime = MinecraftMappings.read(officialMappings);

        String refmap = """
                {
                  "mappings": {
                    "example/TitleMixin": {
                      "init": "Lnet/minecraft/class_442;method_25426()V"
                    },
                    "example/ConnectionAccessor": {
                      "address": "Lnet/minecraft/class_2535;field_11645:Ljava/net/SocketAddress;"
                    }
                  }
                }
                """;
        String remapped = new String(intermediary.remapRefmap(
                refmap.getBytes(StandardCharsets.UTF_8), runtime), StandardCharsets.UTF_8);

        assertTrue(remapped.contains("Labc;a()V"), remapped);
        assertTrue(remapped.contains("Lxyz;b:Ljava/net/SocketAddress;"), remapped);
    }
}
