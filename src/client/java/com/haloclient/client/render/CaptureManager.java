package com.haloclient.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.GpuFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;

public class CaptureManager {
    private static GpuTexture captureTexture;
    private static GpuTextureView captureView;
    private static TextureSetup textureSetup;

    public static TextureSetup getCaptureTextureSetup() {
        return textureSetup != null ? textureSetup : TextureSetup.noTexture();
    }

    public static void updateCapture(Minecraft mc) {
        if (!RenderSystem.isOnRenderThread() || mc == null || mc.gameRenderer.mainRenderTarget() == null) return;

        var screenTex = mc.gameRenderer.mainRenderTarget().getColorTexture();
        if (screenTex == null || screenTex.isClosed()) return;

        int w = screenTex.getWidth(0);
        int h = screenTex.getHeight(0);
        if (w <= 0 || h <= 0) return;

        if (captureTexture == null || captureTexture.getWidth(0) != w || captureTexture.getHeight(0) != h) {
            cleanup();
            // Inicjalizacja bufora dokładnie na wymiary klatki
            captureTexture = RenderSystem.getDevice().createTexture("Halo Blur Buffer", 15, screenTex.getFormat(), w, h, 1, 1);
            captureView = RenderSystem.getDevice().createTextureView(captureTexture);
            textureSetup = TextureSetup.singleTexture(captureView, RenderSystem.getDevice().createSampler(
                com.mojang.blaze3d.textures.AddressMode.CLAMP_TO_EDGE,
                com.mojang.blaze3d.textures.AddressMode.CLAMP_TO_EDGE,
                com.mojang.blaze3d.textures.FilterMode.LINEAR,
                com.mojang.blaze3d.textures.FilterMode.LINEAR,
                1, java.util.OptionalDouble.empty()
            ));
        }

        try {
            // Natychmiastowe synchroniczne pobranie klatki (jesteśmy na wątku generowania!)
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                screenTex, captureTexture, 0, 0, 0, 0, 0, w, h
            );
        } catch (Exception ignored) {}
    }

    public static void cleanup() {
        if (captureTexture != null) {
            captureTexture.close();
            captureTexture = null;
        }
        if (captureView != null) {
            captureView.close();
            captureView = null;
        }
        textureSetup = null;
    }
}
