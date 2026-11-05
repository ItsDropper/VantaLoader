package dev.itsdropper.vantaloader.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModMenuHeaderTest {
    @Test
    void formatsRuntimeMinecraftVersionAndPluralizedModCount() {
        assertEquals(new ModMenuHeader("VantaLoader 1.21.11", "Loaded 12 mods"),
                ModMenuHeader.create("1.21.11", 12));
        assertEquals(new ModMenuHeader("VantaLoader 1.21.11", "Loaded 1 mod"),
                ModMenuHeader.create("1.21.11", 1));
    }

    @Test
    void handlesUnknownVersionAndRejectsNegativeCounts() {
        assertEquals("VantaLoader Unknown Minecraft version",
                ModMenuHeader.create(null, 0).title());
        assertThrows(IllegalArgumentException.class, () -> ModMenuHeader.create("1.21.11", -1));
    }
}
