package dev.itsdropper.vantaloader.runtime;

import dev.itsdropper.vantaloader.api.ModContext;
import dev.itsdropper.vantaloader.api.VantaMod;

public final class RuntimeTestEntrypoint implements VantaMod {
    public RuntimeTestEntrypoint() { }

    @Override
    public void onInitialize(ModContext context) {
        RuntimeEvents.EVENTS.add("init:" + context.modId());
        RuntimeEvents.lastCapabilities = context.minecraftCapabilities();
        if (context.modId().equals(RuntimeEvents.failInitializeMod)) {
            RuntimeEvents.failInitializeMod = null;
            throw new IllegalStateException("simulated initialization failure");
        }
    }

    @Override
    public void onShutdown() {
        RuntimeEvents.EVENTS.add("stop");
        if (RuntimeEvents.failFatalShutdown) {
            RuntimeEvents.failFatalShutdown = false;
            throw new OutOfMemoryError("simulated fatal shutdown failure");
        }
        if (RuntimeEvents.failShutdown) {
            RuntimeEvents.failShutdown = false;
            throw new AssertionError("simulated shutdown failure");
        }
    }
}
