package com.haloclient.client.mixin;

import com.haloclient.client.HaloClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.Minecraft;
import java.io.File;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(Options.class)
public class OptionsMixin {
    @Shadow @Final @Mutable public KeyMapping[] keyMappings;

    @Inject(method = "processOptions", at = @At("HEAD"))
    private void halo$onProcessOptions(@Coerce Object access, CallbackInfo ci) {
        boolean found = false;
        if (this.keyMappings != null) {
            for (KeyMapping km : this.keyMappings) {
                if (km == HaloClient.openClickGuiKey) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                KeyMapping[] original = this.keyMappings;
                KeyMapping[] newMappings = new KeyMapping[original.length + 1];
                System.arraycopy(original, 0, newMappings, 0, original.length);
                newMappings[original.length] = HaloClient.openClickGuiKey;
                this.keyMappings = newMappings;
            }
        }
    }
}
