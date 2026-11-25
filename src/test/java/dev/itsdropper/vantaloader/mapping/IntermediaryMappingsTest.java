package dev.itsdropper.vantaloader.mapping;

import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.Mixin;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class IntermediaryMappingsTest {
    @TempDir Path temporaryDirectory;

    @Mixin(targets = "xyz")
    static final class LivingEntityAccessorMixin { }

    @Test
    void remapsIntermediaryMixinReferencesToObfuscatedRuntimeNames() throws Exception {
        String tiny = """
                tiny\t2\t0\tofficial\tintermediary
                c\tnet/minecraft/client/gui/screens/TitleScreen\tnet/minecraft/class_442
                \tm\t()V\tinit\tmethod_25426
                \tm\t(Lnet/minecraft/util/profiling/jfr/event/ChunkGenerationEvent;)V\tprobe\tmethod_90000
                c\tnet/minecraft/network/Connection\tnet/minecraft/class_2535
                \tf\tLjava/net/SocketAddress;\taddress\tfield_11645
                c\tnet/minecraft/world/entity/LivingEntity\tnet/minecraft/class_1309
                \tf\tF\tyBodyRotO\tfield_6220
                \tf\tF\tyHeadRotO\tfield_6259
                """;
        IntermediaryMappings intermediary = IntermediaryMappings.read(
                new ByteArrayInputStream(tiny.getBytes(StandardCharsets.UTF_8)));

        Path officialMappings = temporaryDirectory.resolve("client.txt");
        Files.writeString(officialMappings, """
                net.minecraft.client.gui.screens.TitleScreen -> abc:
                    1:1:void init() -> a
                net.minecraft.network.Connection -> xyz:
                    java.net.SocketAddress address -> b
                net.minecraft.util.profiling.jfr.event.ChunkGenerationEvent -> jfr:
                net.minecraft.world.entity.LivingEntity -> xyz:
                    float yBodyRotO -> bD
                    float yHeadRotO -> bF
                """);
        MinecraftMappings runtime = MinecraftMappings.read(officialMappings);

        String refmap = """
                {
                  "mappings": {
                    "example/TitleMixin": {
                      "init": "Lnet/minecraft/class_442;method_25426()V",
                      "probe": "Lnet/minecraft/class_442;method_90000(Lnet/minecraft/util/profiling/jfr/event/ChunkGenerationEvent;)V"
                    },
                    "example/ConnectionAccessor": {
                      "address": "Lnet/minecraft/class_2535;field_11645:Ljava/net/SocketAddress;"
                  },
                  "dev/itsdropper/vantaloader/mapping/IntermediaryMappingsTest$LivingEntityAccessorMixin": {
                    "headYaw": "field_6259:F"
                    }
                  }
                }
                """;
        String remapped = new String(intermediary.remapRefmap(
                refmap.getBytes(StandardCharsets.UTF_8), runtime, getClass().getClassLoader()), StandardCharsets.UTF_8);

        assertTrue(remapped.contains("Labc;a()V"), remapped);
        assertTrue(remapped.contains("Ljfr;"), remapped);
        assertTrue(remapped.contains("bF:F"), remapped);
        assertTrue(remapped.contains("Lxyz;b:Ljava/net/SocketAddress;"), remapped);
    }
}
