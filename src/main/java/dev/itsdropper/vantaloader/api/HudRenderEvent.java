package dev.itsdropper.vantaloader.api;

import java.util.Objects;

/** Fired after vanilla HUD rendering, on the Minecraft render thread. */
public record HudRenderEvent(HudRenderContext context) {
    public HudRenderEvent {
        Objects.requireNonNull(context, "context");
    }
}
