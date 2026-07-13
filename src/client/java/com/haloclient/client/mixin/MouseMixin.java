package com.haloclient.client.mixin;

import com.haloclient.client.gui.click.ClickGUI;
import com.haloclient.client.gui.click.MusicDisplayOverlay;
import com.haloclient.client.gui.click.DetachedNextElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonInfo;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseMixin {

    @Shadow @Final private Minecraft minecraft;
    @Shadow private MouseButtonInfo activeButton;
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;
    @Shadow private double mousePressedTime;

    @Shadow public abstract double getScaledXPos(Window window);
    @Shadow public abstract double getScaledYPos(Window window);
    @Shadow public static double getScaledXPos(Window window, double x) { return 0; }
    @Shadow public static double getScaledYPos(Window window, double y) { return 0; }

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void halo$onButton(long window, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
        if (this.minecraft.screen instanceof ChatScreen || this.minecraft.screen instanceof ClickGUI) {
            Window win = this.minecraft.getWindow();
            double xm = this.getScaledXPos(win);
            double ym = this.getScaledYPos(win);
            if (action == 1) { // GLFW_PRESS
                if (MusicDisplayOverlay.onMouseClicked(xm, ym, buttonInfo.button()) || DetachedNextElement.onMouseClicked(xm, ym, buttonInfo.button())) {
                    this.activeButton = buttonInfo;
                    this.mousePressedTime = com.mojang.blaze3d.Blaze3D.getTime();
                    ci.cancel();
                }
            } else if (action == 0) { // GLFW_RELEASE
                boolean releasedOverlay = MusicDisplayOverlay.onMouseReleased(xm, ym, buttonInfo.button());
                boolean releasedDetached = DetachedNextElement.onMouseReleased(xm, ym, buttonInfo.button());
                if (releasedOverlay || releasedDetached) {
                    this.activeButton = null;
                    ci.cancel();
                }
            }
        }
    }

    @Inject(method = "handleAccumulatedMovement", at = @At("HEAD"))
    private void halo$onMove(CallbackInfo ci) {
        if ((this.minecraft.screen instanceof ChatScreen || this.minecraft.screen instanceof ClickGUI) && this.activeButton != null) {
            Window win = this.minecraft.getWindow();
            double xm = this.getScaledXPos(win);
            double ym = this.getScaledYPos(win);
            double dx = getScaledXPos(win, this.accumulatedDX);
            double dy = getScaledYPos(win, this.accumulatedDY);
            boolean draggedOverlay = MusicDisplayOverlay.onMouseDragged(xm, ym, this.activeButton.button(), dx, dy);
            boolean draggedDetached = DetachedNextElement.onMouseDragged(xm, ym, this.activeButton.button(), dx, dy);
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void halo$onScroll(long handle, double xoffset, double yoffset, CallbackInfo ci) {
        if (this.minecraft.screen instanceof ChatScreen || this.minecraft.screen instanceof ClickGUI) {
            Window win = this.minecraft.getWindow();
            double xm = this.getScaledXPos(win);
            double ym = this.getScaledYPos(win);
            if (MusicDisplayOverlay.onMouseScrolled(xm, ym, xoffset, yoffset) || DetachedNextElement.onMouseScrolled(xm, ym, xoffset, yoffset)) {
                ci.cancel();
            }
        }
    }
}
