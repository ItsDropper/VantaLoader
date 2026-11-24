package dev.itsdropper.example.mixinprobe;

import dev.itsdropper.vantaloader.api.ModContext;
import dev.itsdropper.vantaloader.api.VantaMod;

/** Independently packaged mod used to verify third-party SpongePowered Mixin interoperability. */
public final class MixinProbeMod implements VantaMod {
    public MixinProbeMod() { }

    @Override
    public void onInitialize(ModContext context) {
        context.logger().info("Third-party Mixin probe mod initialized");
    }

    @Override
    public void onShutdown() { }
}
