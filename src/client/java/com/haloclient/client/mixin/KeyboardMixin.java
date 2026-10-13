package com.haloclient.client.mixin;

import com.haloclient.client.gui.HaloScreen;
import com.haloclient.client.HaloClient;
import com.haloclient.client.module.Module;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void halo$onKey(long window, int action, KeyEvent event, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof com.haloclient.client.gui.click.ClickGUI gui) {
            if (gui.onKeyEvent(event.key(), action, window)) {
                ci.cancel();
                return;
            }
        }
        if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen) {
            if (com.haloclient.client.gui.click.MusicDisplayOverlay.onKeyEvent(event.key(), action, window)) {
                ci.cancel();
                return;
            }
        }

        if (action != GLFW.GLFW_PRESS) return;
        if (mc.gui.screen() != null) return;

        HaloClient.INSTANCE.getModuleManager().getModules().forEach(module -> {
            if (module.getKey() == event.key()) {
                module.toggle();
            }
        });
    }
}
