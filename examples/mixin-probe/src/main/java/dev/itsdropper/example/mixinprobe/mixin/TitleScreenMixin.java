package dev.itsdropper.example.mixinprobe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Targets the exact 1.21.11 runtime name and title-screen init method used by the game adapter. */
@Mixin(targets = "gsd")
abstract class TitleScreenMixin {
    @Inject(method = "bg_", at = @At("TAIL"))
    private void vantaloader$verifyThirdPartyMixin(CallbackInfo callbackInfo) {
        MixinProbeHooks.onTitleScreenInitialized();
    }
}
