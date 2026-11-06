package dev.itsdropper.vantaloader.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "grx")
abstract class PauseScreenMixin {
    @Inject(method = "bg_", at = @At("TAIL"))
    private void vantaloader$addMenuButton(CallbackInfo callback) {
        VantaMenuHooks.addMenuButton(this);
    }
}
