package dev.itsdropper.vantaloader.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.screens.TitleScreen")
abstract class TitleScreenMixin {
    @Inject(method = "init", at = @At("TAIL"))
    private void vantaloader$addMenuButton(CallbackInfo callback) {
        VantaMenuHooks.addMenuButton(this);
    }
}
