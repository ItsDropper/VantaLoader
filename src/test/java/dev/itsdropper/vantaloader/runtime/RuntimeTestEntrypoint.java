package dev.itsdropper.vantaloader.runtime;

import dev.itsdropper.vantaloader.api.ModContext;
import dev.itsdropper.vantaloader.api.VantaMod;

public final class RuntimeTestEntrypoint implements VantaMod {
    public RuntimeTestEntrypoint() { }

    @Override
    public void onInitialize(ModContext context) {
        RuntimeEvents.EVENTS.add("init:" + context.modId());
    }

    @Override
    public void onShutdown() {
        RuntimeEvents.EVENTS.add("stop");
    }
}
