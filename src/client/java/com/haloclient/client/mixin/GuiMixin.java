package com.haloclient.client.mixin;

import com.haloclient.client.HaloClient;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class GuiMixin {

 

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void halo$onAfterExtractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        // Hidden with the rest of the HUD (F1)
        if (((Hud) (Object) this).isHidden()) return;
        HaloClient.renderModules(graphics, deltaTracker);
    }

}
