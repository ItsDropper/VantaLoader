package dev.itsdropper.example.mixinprobe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.logging.Logger;

/** Targets the exact 1.21.11 runtime name and title-screen init method used by the game adapter. */
@Mixin(targets = "gsd")
abstract class TitleScreenMixin {
    @Inject(method = "bg_", at = @At("TAIL"))
    private void vantaloader$verifyThirdPartyMixin(CallbackInfo callbackInfo) {
        // Keep the proof inside the merged handler. Minecraft's application
        // classloader cannot directly resolve arbitrary classes in a mod loader.
        Logger.getLogger("VantaLoader.MixinProbe")
                .info("VantaLoader third-party Mixin probe: title screen injection executed");
    }
}
