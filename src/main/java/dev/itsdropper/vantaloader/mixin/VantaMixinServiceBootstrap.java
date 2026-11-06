package dev.itsdropper.vantaloader.mixin;

import org.spongepowered.asm.service.IMixinServiceBootstrap;

/** ServiceLoader bootstrap for the VantaLoader Java-agent environment. */
public final class VantaMixinServiceBootstrap implements IMixinServiceBootstrap {
    @Override public String getName() { return "VantaLoader"; }
    @Override public String getServiceClassName() { return VantaMixinService.class.getName(); }
    @Override public void bootstrap() { }
}
