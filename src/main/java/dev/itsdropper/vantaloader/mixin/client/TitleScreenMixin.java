package dev.itsdropper.vantaloader.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "gsd")
abstract class TitleScreenMixin {
    @Inject(method = "bg_", at = @At("TAIL"))
    private void vantaloader$addMenuButton(CallbackInfo callback) {
        VantaMenuHooks.addMenuButton(this);
    }

    /**
     * Replaces the argument passed to Minecraft's own bottom-left version draw call.
     * No additional button or text widget is created.
     */
    @ModifyArg(
            method = "a(Lgir;IIF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lgir;b(Lgio;Ljava/lang/String;III)I",
                    remap = false
            ),
            index = 1,
            remap = false
    )
    private String vantaloader$replaceVanillaVersionLabel(String vanillaVersion) {
        return "VantaLoader (" + System.getProperty("vanta.minecraft.version", "unknown") + ")";
    }
}
