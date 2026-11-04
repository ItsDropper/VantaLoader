package dev.itsdropper.vantaloader.transformation;

import dev.itsdropper.vantaloader.api.ClassTransformContext;
import org.junit.jupiter.api.Test;

import java.lang.instrument.IllegalClassFormatException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransformationServiceTest {
    @Test
    void appliesExactTargetTransformationsInStablePriorityOrder() throws Exception {
        TransformationService service = new TransformationService();
        List<String> order = new ArrayList<>();

        service.forMod("zeta").register("append-z", "example.Target", 10, (context, bytes) -> {
            order.add(context.modId() + ":" + context.transformationId());
            return append(bytes, 3);
        });
        service.forMod("alpha").register("append-a", "example.Target", 10, (context, bytes) -> {
            order.add(context.modId() + ":" + context.transformationId());
            return append(bytes, 2);
        });
        service.forMod("alpha").register("append-first", "example.Target", -5, (context, bytes) -> {
            order.add(context.modId() + ":" + context.transformationId());
            assertEquals("example.Target", context.targetClassName());
            return append(bytes, 1);
        });

        byte[] result = service.transform(null, null, "example/Target", null, null, new byte[]{0});

        assertArrayEquals(new byte[]{0, 1, 2, 3}, result);
        assertEquals(List.of("alpha:append-first", "alpha:append-a", "zeta:append-z"), order);
    }

    @Test
    void ignoresClassesWithoutAnExactRegisteredTarget() throws Exception {
        TransformationService service = new TransformationService();
        service.forMod("example").register("target", "example.Target", 0, (context, bytes) -> append(bytes, 1));

        assertNull(service.transform(null, null, "example/Other", null, null, new byte[]{0}));
        assertNull(service.transform(null, null, "example/Target$Nested", null, null, new byte[]{0}));
    }

    @Test
    void rejectsDuplicateIdsWithinOneModButAllowsOtherModsToReuseIds() {
        TransformationService service = new TransformationService();
        var registry = service.forMod("first");
        registry.register("patch", "example.Target", 0, (context, bytes) -> bytes);
        assertThrows(IllegalArgumentException.class,
                () -> registry.register("patch", "example.Other", 0, (context, bytes) -> bytes));
        assertDoesNotThrow(() -> service.forMod("second")
                .register("patch", "example.Other", 0, (context, bytes) -> bytes));
    }

    @Test
    void wrapsTransformerFailuresWithTargetAndContributionDetails() {
        TransformationService service = new TransformationService();
        service.forMod("broken").register("explode", "example.Target", 0,
                (ClassTransformContext context, byte[] bytes) -> {
                    throw new IllegalStateException("fixture failure");
                });

        IllegalClassFormatException failure = assertThrows(IllegalClassFormatException.class,
                () -> service.transform(null, null, "example/Target", null, null, new byte[]{0}));
        assertTrue(failure.getMessage().contains("broken:explode"));
        assertTrue(failure.getMessage().contains("example.Target"));
        assertEquals("fixture failure", failure.getCause().getMessage());
    }

    private static byte[] append(byte[] source, int value) {
        byte[] result = java.util.Arrays.copyOf(source, source.length + 1);
        result[result.length - 1] = (byte) value;
        return result;
    }
}
