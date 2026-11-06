package dev.itsdropper.vantaloader.mixin.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VantaMenuHooksTest {
    @Test
    void visibleRowsAlwaysLeavesRoomForAtLeastOneMod() {
        assertEquals(1, VantaMenuHooks.visibleRows(120));
        assertEquals(5, VantaMenuHooks.visibleRows(360));
    }

    @Test
    void scrollOffsetIsClampedToTheAvailableRange() {
        assertEquals(0, VantaMenuHooks.clampScrollOffset(-4, 20, 6));
        assertEquals(14, VantaMenuHooks.clampScrollOffset(99, 20, 6));
        assertEquals(0, VantaMenuHooks.clampScrollOffset(4, 4, 6));
        assertEquals(0, VantaMenuHooks.clampScrollOffset(4, 0, 6));
    }
}
