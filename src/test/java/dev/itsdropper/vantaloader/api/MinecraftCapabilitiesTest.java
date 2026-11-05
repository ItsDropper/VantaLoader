package dev.itsdropper.vantaloader.api;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MinecraftCapabilitiesTest {
    @Test
    void emptySnapshotMakesNoUnsupportedClaims() {
        MinecraftCapabilities capabilities = MinecraftCapabilities.none();

        assertTrue(capabilities.isEmpty());
        assertFalse(capabilities.supports(MinecraftCapability.CLIENT_LIFECYCLE_EVENTS));
        assertFalse(capabilities.supportsAny(
                MinecraftCapability.MIXIN_INTEROP,
                MinecraftCapability.RESOURCE_RELOAD));
    }

    @Test
    void capabilityQueriesAreExplicitAndComposable() {
        MinecraftCapabilities capabilities = MinecraftCapabilities.of(
                MinecraftCapability.CLASS_TRANSFORMATIONS,
                MinecraftCapability.OFFICIAL_MOJANG_MAPPINGS);

        assertTrue(capabilities.supports(MinecraftCapability.CLASS_TRANSFORMATIONS));
        assertTrue(capabilities.supportsAll(
                MinecraftCapability.CLASS_TRANSFORMATIONS,
                MinecraftCapability.OFFICIAL_MOJANG_MAPPINGS));
        assertFalse(capabilities.supportsAll(
                MinecraftCapability.CLASS_TRANSFORMATIONS,
                MinecraftCapability.MIXIN_INTEROP));
        assertTrue(capabilities.supportsAny(
                MinecraftCapability.MIXIN_INTEROP,
                MinecraftCapability.OFFICIAL_MOJANG_MAPPINGS));
    }

    @Test
    void exposedSetIsImmutable() {
        MinecraftCapabilities capabilities =
                MinecraftCapabilities.of(MinecraftCapability.CLASS_TRANSFORMATIONS);

        assertEquals(Set.of(MinecraftCapability.CLASS_TRANSFORMATIONS), capabilities.asSet());
        assertThrows(UnsupportedOperationException.class,
                () -> capabilities.asSet().clear());
    }

    @Test
    void nullCapabilityIsRejected() {
        assertThrows(NullPointerException.class,
                () -> MinecraftCapabilities.of(MinecraftCapability.CLASS_TRANSFORMATIONS, null));
        assertThrows(NullPointerException.class,
                () -> MinecraftCapabilities.none().supports(null));
    }
}
