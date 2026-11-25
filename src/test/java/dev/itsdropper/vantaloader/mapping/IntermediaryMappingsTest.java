package dev.itsdropper.vantaloader.mapping;

import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.Mixin;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class IntermediaryMappingsTest {
    @Mixin(targets = "xyz")
    static final class LivingEntityAccessorMixin { }

    @Test
    void remapsIntermediaryMixinReferencesDirectlyToObfuscatedRuntimeNames() throws Exception {
        String tiny = """
                tiny\t2\t0\tofficial\tintermediary
                c\tabc\tnet/minecraft/class_442
                \tm\t()V\ta\tmethod_25426
                \tm\t(Ljava/lang/String;)V\tb\tmethod_90000
                c\tconn\tnet/minecraft/class_2535
                \tf\tLjava/net/SocketAddress;\tb\tfield_11645
                c\txyz\tnet/minecraft/class_1309
                \tf\tF\tbD\tfield_6220
                \tf\tF\tbF\tfield_6259
                """;
        IntermediaryMappings intermediary = IntermediaryMappings.read(
                new ByteArrayInputStream(tiny.getBytes(StandardCharsets.UTF_8)));

        String refmap = """
                {
                  "mappings": {
                    "example/TitleMixin": {
                      "init": "Lnet/minecraft/class_442;method_25426()V",
                      "probe": "Lnet/minecraft/class_442;method_90000(Ljava/lang/String;)V"
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
                refmap.getBytes(StandardCharsets.UTF_8), getClass().getClassLoader()), StandardCharsets.UTF_8);

        assertTrue(remapped.contains("Labc;a()V"), remapped);
        assertTrue(remapped.contains("Labc;b(Ljava/lang/String;)V"), remapped);
        assertTrue(remapped.contains("Lconn;b:Ljava/net/SocketAddress;"), remapped);
        assertTrue(remapped.contains("bF:F"), remapped);
    }
}
