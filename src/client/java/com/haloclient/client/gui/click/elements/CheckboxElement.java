package com.haloclient.client.gui.click.elements;

import com.haloclient.client.gui.click.ClickGUI;
import com.haloclient.client.render.HaloRenderPipelines;
import com.haloclient.client.render.font.HaloFontRenderState;
import com.haloclient.client.render.font.MsdfFontManager;
import com.haloclient.client.render.renderstates.BlurredRoundedRectangleRenderState;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

public class CheckboxElement {

    public static void drawCheckbox(
            @Nullable GuiGraphicsExtractor graphics,
            @Nullable Matrix3x2f pose,
            @Nullable TextureSetup textureSetup,
            @Nullable ScreenRectangle scissor,
            String label, float lx, float ly, float width, float animProgress, int alpha, boolean disabled, float mouseX, float mouseY,
            boolean extractPass
    ) {
        float cardH = 13.0f;
        float cardY = ly - cardH / 2.0f;
        boolean hovered = ClickGUI.isHovered(mouseX, mouseY, lx, cardY, width, cardH);

        float trackW = 16.0f;
        float trackH = 8.0f;
        float rx = lx + width - trackW - 4.0f;
        float ry = ly - trackH / 2.0f;

        int textColor = disabled ? ARGB.color(255, 255, 255, 255) : ARGB.color(255, 244, 244, 245); // Zinc 500 or 100

        if (extractPass) {
            if (graphics != null && pose != null && textureSetup != null) {
                // Card background
                graphics.guiRenderState.addGuiElement(new BlurredRoundedRectangleRenderState(
                        HaloRenderPipelines.ROUNDED_BLUR,
                        textureSetup,
                        pose,
                        lx, cardY, width, cardH,
                        ARGB.color(hovered ? 185 : 165, 0, 0, 0),
                        3.0f,
                        200.0f,
                        0.0f,
                        scissor
                ));

                // Switch track
                int trackColor;
                if (disabled) {
                    trackColor = ARGB.color((int) (alpha * 0.35f), 30, 30, 30);
                } else {
                    int cFrom = ARGB.color((int) (alpha * 0.9f), 0, 0, 0); // Zinc 800
                    int cTo = ARGB.color((int) (alpha * 0.9f), 29, 185, 84); // Spotify green
                    trackColor = lerpColor(cFrom, cTo, animProgress);
                }
                
                graphics.guiRenderState.addGuiElement(new BlurredRoundedRectangleRenderState(
                        HaloRenderPipelines.ROUNDED_BLUR,
                        textureSetup,
                        pose,
                        rx, ry, trackW, trackH,
                        trackColor,
                        4.0f,
                        200.0f,
                        0.0f,
                        scissor
                ));

                // Text
                var font = MsdfFontManager.getFont("productsans-semibold", 6f);
                if (font != null) {
                    graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                            font,
                            label,
                            new Matrix3x2f(pose),
                            lx + 4.0f,
                            ly - font.getHeight(6f) / 2f,
                            6f,
                            textColor,
                            scissor
                    ));
                }
                float knobSize = 6.0f;
                float knobX = rx + 1.0f + animProgress * (trackW - knobSize - 2.0f);
                float knobY = ry + 1.0f;
                int knobColor = disabled ? ARGB.color(255, 80, 80, 80) : ARGB.color(255, 255, 255, 255);
                graphics.guiRenderState.addGuiElement(new BlurredRoundedRectangleRenderState(
                        HaloRenderPipelines.ROUNDED_BLUR,
                        textureSetup,
                        pose,
                        knobX, knobY, knobSize, knobSize,
                        knobColor,
                        3.0f,
                        0.0f,
                        0.0f,
                        scissor
                ));

            }
        }
    }

    public static void drawCheckboxV2(
            @Nullable GuiGraphicsExtractor graphics,
            @Nullable Matrix3x2f pose,
            @Nullable TextureSetup textureSetup,
            @Nullable ScreenRectangle scissor,
            String label, float lx, float ly, float width, float animProgress, int alpha, boolean disabled, float mouseX, float mouseY,
            boolean extractPass
    ) {
        float trackW = 16.0f;
        float trackH = 8.0f;
        float rx = lx + width - trackW - 4.0f;
        float ry = ly - trackH / 2.0f;

        int textColor = disabled ? ARGB.color(255, 255, 255, 255) : ARGB.color(255, 244, 244, 245); // Zinc 500 or 100

        if (extractPass) {
            if (graphics != null && pose != null && textureSetup != null) {
                // Switch track
                int trackColor;
                if (disabled) {
                    trackColor = ARGB.color((int) (alpha * 0.35f), 30, 30, 30);
                } else {
                    int cFrom = ARGB.color((int) (alpha * 0.9f), 0, 0, 0); // Zinc 800
                    int cTo = ARGB.color((int) (alpha * 0.9f), 29, 185, 84); // Spotify green
                    trackColor = lerpColor(cFrom, cTo, animProgress);
                }
                
                graphics.guiRenderState.addGuiElement(new BlurredRoundedRectangleRenderState(
                        HaloRenderPipelines.ROUNDED_BLUR,
                        textureSetup,
                        pose,
                        rx, ry, trackW, trackH,
                        trackColor,
                        4.0f,
                        200.0f,
                        0.0f,
                        scissor
                ));

                // Text
                var font = MsdfFontManager.getFont("productsans-semibold", 6f);
                if (font != null) {
                    graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                            font,
                            label,
                            new Matrix3x2f(pose),
                            lx + 4.0f,
                            ly - font.getHeight(6f) / 2f,
                            6f,
                            textColor,
                            scissor
                    ));
                }
                float knobSize = 6.0f;
                float knobX = rx + 1.0f + animProgress * (trackW - knobSize - 2.0f);
                float knobY = ry + 1.0f;
                int knobColor = disabled ? ARGB.color(255, 80, 80, 80) : ARGB.color(255, 255, 255, 255);
                graphics.guiRenderState.addGuiElement(new BlurredRoundedRectangleRenderState(
                        HaloRenderPipelines.ROUNDED_BLUR,
                        textureSetup,
                        pose,
                        knobX, knobY, knobSize, knobSize,
                        knobColor,
                        3.0f,
                        0.0f,
                        0.0f,
                        scissor
                ));

            }
        }
    }

    private static int lerpColor(int from, int to, float factor) {
        int a1 = (from >> 24) & 0xFF;
        int r1 = (from >> 16) & 0xFF;
        int g1 = (from >> 8) & 0xFF;
        int b1 = from & 0xFF;

        int a2 = (to >> 24) & 0xFF;
        int r2 = (to >> 16) & 0xFF;
        int g2 = (to >> 8) & 0xFF;
        int b2 = to & 0xFF;

        int a = (int) (a1 + (a2 - a1) * factor);
        int r = (int) (r1 + (r2 - r1) * factor);
        int g = (int) (g1 + (g2 - g1) * factor);
        int b = (int) (b1 + (b2 - b1) * factor);

        return ARGB.color(a, r, g, b);
    }
}
