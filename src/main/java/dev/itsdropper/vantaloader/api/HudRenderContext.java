package dev.itsdropper.vantaloader.api;

/**
 * Drawing operations for one HUD render callback. The context expires as soon as
 * the callback dispatch finishes and must not be retained or used from another thread.
 */
public interface HudRenderContext {
    int width();
    int height();
    void drawText(String text, int x, int y, int color);
    void fillRect(int left, int top, int right, int bottom, int color);
}
