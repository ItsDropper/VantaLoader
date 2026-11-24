package dev.itsdropper.example.mixinprobe.mixin;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/** Runtime marker is emitted only when the injected method actually executes. */
public final class MixinProbeHooks {
    private static final Logger LOGGER = Logger.getLogger("VantaLoader.MixinProbe");
    private static final AtomicBoolean OBSERVED = new AtomicBoolean();

    private MixinProbeHooks() { }

    public static void onTitleScreenInitialized() {
        if (OBSERVED.compareAndSet(false, true)) {
            LOGGER.info("VantaLoader third-party Mixin probe: title screen injection executed");
        }
    }
}
