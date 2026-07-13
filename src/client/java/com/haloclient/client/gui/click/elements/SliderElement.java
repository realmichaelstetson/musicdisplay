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

public class SliderElement {

    public static void drawSlider(
            @Nullable GuiGraphicsExtractor graphics,
            @Nullable Matrix3x2f pose,
            @Nullable TextureSetup textureSetup,
            @Nullable ScreenRectangle scissor,
            String label, float sx, float sy, float width, float value, float min, float max, int alphaScale, int alpha, float mouseX, float mouseY,
            boolean extractPass
    ) {
        float cardH = 13.0f;
        float cardY = sy - cardH / 2.0f;
        boolean hovered = ClickGUI.isHovered(mouseX, mouseY, sx, cardY, width, cardH);

        float labelWidth = 25.0f;
        float valueWidth = 15.0f;
        float trackX = sx + labelWidth + 4.0f;
        float trackWidth = width - labelWidth - valueWidth - 12.0f;
        float trackY = sy;
        float normalized = Math.max(0.0f, Math.min(1.0f, (value - min) / (max - min)));
        float fillWidth = trackWidth * normalized;
        float knobX = trackX + fillWidth;
        float knobY = trackY;

        String valueText = max > 100.0f ? String.valueOf(Math.round(value)) : String.format("%.1f", value);

        if (extractPass) {
            if (graphics != null && pose != null && textureSetup != null) {
                // Card background
                graphics.guiRenderState.addGuiElement(new BlurredRoundedRectangleRenderState(
                        HaloRenderPipelines.ROUNDED_BLUR,
                        textureSetup,
                        pose,
                        sx, cardY, width, cardH,
                        ARGB.color(hovered ? 185 : 165, 0, 0, 0),
                        3.0f,
                        200.0f,
                        0.0f,
                        scissor
                ));

                // Draw track (Zinc 800)
                graphics.guiRenderState.addGuiElement(new BlurredRoundedRectangleRenderState(
                        HaloRenderPipelines.ROUNDED_BLUR,
                        textureSetup,
                        pose,
                        trackX, trackY - 1.0f, trackWidth, 2.0f,
                        ARGB.color(255, 40, 40, 40),
                        1.0f,
                        200.0f,
                        0.0f,
                        scissor
                ));

                // Draw fill (White)
                if (fillWidth > 0) {
                    graphics.guiRenderState.addGuiElement(new BlurredRoundedRectangleRenderState(
                            HaloRenderPipelines.ROUNDED_BLUR,
                            textureSetup,
                            pose,
                            trackX, trackY - 1.0f, fillWidth, 2.0f,
                            ARGB.color(255, 244, 244, 245),
                            1.0f,
                            200.0f,
                            0.0f,
                            scissor
                    ));
                }

                // Draw knob (White circle)
                graphics.guiRenderState.addGuiElement(new BlurredRoundedRectangleRenderState(
                        HaloRenderPipelines.ROUNDED_BLUR,
                        textureSetup,
                        pose,
                        knobX - 2.5f, knobY - 2.5f, 5.0f, 5.0f,
                        ARGB.color(255, 255, 255, 255),
                        2.5f,
                        200.0f,
                        0.0f,
                        scissor
                ));

                // Label Text
                var font = MsdfFontManager.getFont("productsans-semibold", 6f);
                if (font != null) {
                    graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                            font,
                            label,
                            new Matrix3x2f(pose),
                            sx + 4.0f,
                            sy - font.getHeight(6f) / 2f,
                            6f,
                            ARGB.color(255, 244, 244, 245),
                            scissor
                    ));
                }

                // Value Text
                var valueFont = MsdfFontManager.getFont("productsans-semibold", 5.5f);
                if (valueFont != null) {
                    graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                            valueFont,
                            valueText,
                            new Matrix3x2f(pose),
                            sx + width - 4.0f - valueFont.getWidth(valueText, 5.5f),
                            sy - valueFont.getHeight(5.5f) / 2f,
                            5.5f,
                            ARGB.color(255, 161, 161, 170),
                            scissor
                    ));
                }
            }
        }
    }

    public static void drawSliderV2(
            @Nullable GuiGraphicsExtractor graphics,
            @Nullable Matrix3x2f pose,
            @Nullable TextureSetup textureSetup,
            @Nullable ScreenRectangle scissor,
            String label, float sx, float sy, float width, float value, float min, float max, int alphaScale, int alpha, float mouseX, float mouseY,
            boolean extractPass
    ) {
        float labelWidth = 25.0f;
        float valueWidth = 15.0f;
        float trackX = sx + labelWidth + 4.0f;
        float trackWidth = width - labelWidth - valueWidth - 12.0f;
        float trackY = sy;
        float normalized = Math.max(0.0f, Math.min(1.0f, (value - min) / (max - min)));
        float fillWidth = trackWidth * normalized;
        float knobX = trackX + fillWidth;
        float knobY = trackY;

        String valueText = max > 100.0f ? String.valueOf(Math.round(value)) : String.format("%.1f", value);

        if (extractPass) {
            if (graphics != null && pose != null && textureSetup != null) {
                // Draw track (Zinc 800)
                graphics.guiRenderState.addGuiElement(new BlurredRoundedRectangleRenderState(
                        HaloRenderPipelines.ROUNDED_BLUR,
                        textureSetup,
                        pose,
                        trackX, trackY - 1.0f, trackWidth, 2.0f,
                        ARGB.color(255, 40, 40, 40),
                        1.0f,
                        200.0f,
                        0.0f,
                        scissor
                ));

                // Draw fill (White)
                if (fillWidth > 0) {
                    graphics.guiRenderState.addGuiElement(new BlurredRoundedRectangleRenderState(
                            HaloRenderPipelines.ROUNDED_BLUR,
                            textureSetup,
                            pose,
                            trackX, trackY - 1.0f, fillWidth, 2.0f,
                            ARGB.color(255, 244, 244, 245),
                            1.0f,
                            200.0f,
                            0.0f,
                            scissor
                    ));
                }

                // Draw knob (White circle)
                graphics.guiRenderState.addGuiElement(new BlurredRoundedRectangleRenderState(
                        HaloRenderPipelines.ROUNDED_BLUR,
                        textureSetup,
                        pose,
                        knobX - 2.5f, knobY - 2.5f, 5.0f, 5.0f,
                        ARGB.color(255, 255, 255, 255),
                        2.5f,
                        200.0f,
                        0.0f,
                        scissor
                ));

                // Label Text
                var font = MsdfFontManager.getFont("productsans-semibold", 6f);
                if (font != null) {
                    graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                            font,
                            label,
                            new Matrix3x2f(pose),
                            sx + 4.0f,
                            sy - font.getHeight(6f) / 2f,
                            6f,
                            ARGB.color(255, 244, 244, 245),
                            scissor
                    ));
                }

                // Value Text
                var valueFont = MsdfFontManager.getFont("productsans-semibold", 5.5f);
                if (valueFont != null) {
                    graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                            valueFont,
                            valueText,
                            new Matrix3x2f(pose),
                            sx + width - 4.0f - valueFont.getWidth(valueText, 5.5f),
                            sy - valueFont.getHeight(5.5f) / 2f,
                            5.5f,
                            ARGB.color(255, 244, 244, 245),
                            scissor
                    ));
                }
            }
        }
    }
}
