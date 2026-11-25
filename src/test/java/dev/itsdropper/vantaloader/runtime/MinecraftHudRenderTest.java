package dev.itsdropper.vantaloader.runtime;

import dev.itsdropper.vantaloader.api.HudRenderContext;
import dev.itsdropper.vantaloader.api.HudRenderEvent;
import dev.itsdropper.vantaloader.bootstrap.MinecraftHooks;
import dev.itsdropper.vantaloader.mapping.MinecraftMappings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MinecraftHudRenderTest {
    @TempDir Path temporaryDirectory;

    @Test
    void exposesCallbackScopedDrawingOperationsUsingMappedMinecraftMethods() throws Exception {
        Path mappingsFile = temporaryDirectory.resolve("hud-mappings.txt");
        Files.writeString(mappingsFile, """
                net.minecraft.client.gui.Gui -> dev.itsdropper.vantaloader.runtime.MinecraftHudRenderTest$FakeGui:
                net.minecraft.client.Minecraft -> dev.itsdropper.vantaloader.runtime.MinecraftHudRenderTest$FakeMinecraft:
                net.minecraft.client.gui.Font -> dev.itsdropper.vantaloader.runtime.MinecraftHudRenderTest$FakeFont:
                net.minecraft.client.gui.GuiGraphics -> dev.itsdropper.vantaloader.runtime.MinecraftHudRenderTest$FakeGraphics:
                """);
        MinecraftHooks.configureMappings(MinecraftMappings.read(mappingsFile));

        ModEventBus bus = new ModEventBus();
        AtomicReference<HudRenderContext> retained = new AtomicReference<>();
        bus.subscribe(HudRenderEvent.class, 0, event -> {
            HudRenderContext context = event.context();
            retained.set(context);
            assertEquals(854, context.width());
            assertEquals(480, context.height());
            context.fillRect(2, 3, 22, 13, 0xFF112233);
            context.drawText("VMA", 4, 5, 0xFFFFFFFF);
        });
        MinecraftHooks.bind(bus);
        try {
            FakeGraphics graphics = new FakeGraphics();
            MinecraftHooks.onHudRender(new FakeGui(new FakeMinecraft(new FakeFont())), graphics);
            assertEquals("VMA", graphics.text);
            assertEquals(4, graphics.textX);
            assertEquals(5, graphics.textY);
            assertEquals(0xFFFFFFFF, graphics.textColor);
            assertArrayEquals(new int[]{2, 3, 22, 13, 0xFF112233}, graphics.rectangle);
            assertThrows(IllegalStateException.class, () -> retained.get().drawText("late", 0, 0, 0));
        } finally {
            MinecraftHooks.unbind(bus);
        }
    }

    public static final class FakeGui {
        public final FakeMinecraft minecraft;
        public FakeGui(FakeMinecraft minecraft) { this.minecraft = minecraft; }
    }
    public static final class FakeMinecraft {
        public final FakeFont font;
        public FakeMinecraft(FakeFont font) { this.font = font; }
    }
    public static final class FakeFont { }
    public static final class FakeGraphics {
        public String text;
        public int textX;
        public int textY;
        public int textColor;
        public int[] rectangle;
        public int guiWidth() { return 854; }
        public int guiHeight() { return 480; }
        public void drawString(FakeFont font, String text, int x, int y, int color) {
            this.text = text; this.textX = x; this.textY = y; this.textColor = color;
        }
        public void fill(int left, int top, int right, int bottom, int color) {
            rectangle = new int[]{left, top, right, bottom, color};
        }
    }
}
