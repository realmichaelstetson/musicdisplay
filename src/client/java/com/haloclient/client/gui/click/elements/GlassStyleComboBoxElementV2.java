package com.haloclient.client.gui.click.elements;

import com.haloclient.client.gui.click.ClickGUI;
import com.haloclient.client.gui.click.MusicDisplayOverlay;
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

public class GlassStyleComboBoxElementV2 {

    public static void drawMusicSourceComboBox(
            @Nullable GuiGraphicsExtractor graphics,
            @Nullable Matrix3x2f pose,
            @Nullable TextureSetup textureSetup,
            @Nullable ScreenRectangle scissor,
            float leftColX, float colWidth, float y, int alpha, float mouseX, float mouseY,
            boolean extractPass,
            ClickGUI gui
    ) {
        float styleLy = y + 86.0f;
        float styleCardH = 10.0f;
        float styleCardY = styleLy - styleCardH / 2.0f;
        float styleRectW = 45.0f;
        float styleRectX = leftColX + colWidth - styleRectW - 3.0f;
        boolean styleHovered = ClickGUI.isHovered(mouseX, mouseY, styleRectX, styleCardY, styleRectW, styleCardH);
        int styleRectBg = styleHovered ? ARGB.color(200, 0, 0, 0) : ARGB.color(185, 0, 0, 0);

        if (extractPass) {
            if (graphics != null && pose != null && textureSetup != null) {
                // Combobox background
                graphics.guiRenderState.addGuiElement(new BlurredRoundedRectangleRenderState(
                        HaloRenderPipelines.ROUNDED_BLUR,
                        textureSetup,
                        pose,
                        styleRectX, styleCardY, styleRectW, styleCardH,
                        styleRectBg,
                        3.0f,
                        200.0f,
                        0.0f,
                        scissor
                ));

                // Label Text
                var font = MsdfFontManager.getFont("productsans-semibold", 6f);
                if (font != null) {
                    graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                            font,
                            "Music Source",
                            new Matrix3x2f(pose),
                            leftColX + 4.0f,
                            styleLy - font.getHeight(6f) / 2f,
                            6f,
                            ARGB.color(255, 244, 244, 245),
                            scissor
                    ));

                    // Selected value text
                    graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                            font,
                            MusicDisplayOverlay.getMusicSource().getName(),
                            new Matrix3x2f(pose),
                            styleRectX + 4.0f,
                            styleLy - font.getHeight(5.5f) / 2f,
                            5.5f,
                            ARGB.color(255, 228, 228, 231),
                            scissor
                    ));
                }

                // Dropdown arrow — rotate based on animation progress
                var iconFont = MsdfFontManager.getFont("fluid-regular", 8f);
                if (iconFont != null) {
                    float arrowCenterX = styleRectX + styleRectW - 6.0f;
                    float arrowCenterY = styleLy;
                    String arrowChar = "F";
                    float arrowW = iconFont.getWidth(arrowChar, 8f);
                    float arrowH = iconFont.getHeight(8f);
                    float progress = gui.musicSourceComboAnimation.getValue();

                    // Build a pose that rotates around the arrow center
                    Matrix3x2f arrowPose = new Matrix3x2f(pose);
                    arrowPose.translate(arrowCenterX, arrowCenterY);
                    arrowPose.rotate((float) (Math.PI / 2.0 + (1.0 - progress) * Math.PI)); // closed=270° (down), open=90° (up)
                    arrowPose.translate(-arrowCenterX, -arrowCenterY);

                    graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                            iconFont,
                            arrowChar,
                            arrowPose,
                            arrowCenterX - arrowW / 2f,
                            arrowCenterY - arrowH / 2f,
                            8f,
                            ARGB.color(255, 161, 161, 170),
                            scissor
                    ));
                }

                // Music Source Dropdown rendering
                float comboProgress = gui.musicSourceComboAnimation.getValue();
                if (comboProgress > 0.001f) {
                    float styleListY = styleCardY + styleCardH + 1.0f;
                    float styleOptionH = 12.0f;
                    float styleListH = MusicDisplayOverlay.MusicSource.values().length * styleOptionH;
                    float styleAnimListH = styleListH * comboProgress;

                    // Compute a scissor rectangle for the animated dropdown area
                    ScreenRectangle dropdownBounds = (new ScreenRectangle(
                            (int) styleRectX, (int) styleListY,
                            (int) styleRectW, (int) Math.ceil(styleAnimListH)
                    )).transformMaxBounds(pose);
                    ScreenRectangle dropdownScissor = scissor != null ? scissor.intersection(dropdownBounds) : dropdownBounds;

                    // List Background — uses the dropdown scissor
                    graphics.guiRenderState.addGuiElement(new BlurredRoundedRectangleRenderState(
                            HaloRenderPipelines.ROUNDED_BLUR,
                            textureSetup,
                            pose,
                            styleRectX, styleListY, styleRectW, styleAnimListH,
                            ARGB.color(185, 0, 0, 0),
                            3.0f,
                            200.0f,
                            0.0f,
                            dropdownScissor
                    ));

                    for (int i = 0; i < MusicDisplayOverlay.MusicSource.values().length; i++) {
                        MusicDisplayOverlay.MusicSource source = MusicDisplayOverlay.MusicSource.values()[i];
                        float optY = styleListY + i * styleOptionH;
                        boolean optHovered = ClickGUI.isHovered(mouseX, mouseY, styleRectX, optY, styleRectW, styleOptionH) && mouseY <= styleListY + styleAnimListH;

                        if (optHovered && gui.musicSourceComboOpen) {
                            graphics.guiRenderState.addGuiElement(new BlurredRoundedRectangleRenderState(
                                    HaloRenderPipelines.ROUNDED_BLUR,
                                    textureSetup,
                                    pose,
                                    styleRectX + 1.0f, optY + 1.0f, styleRectW - 2.0f, styleOptionH - 2.0f,
                                    ARGB.color(200, 0, 0, 0),
                                    2.0f,
                                    200.0f,
                                    0.0f,
                                    dropdownScissor
                            ));
                        }

                        int col = (source == MusicDisplayOverlay.getMusicSource()) ? ARGB.color(255, 255, 255, 255) : ARGB.color(255, 161, 161, 170);
                        if (source == MusicDisplayOverlay.getMusicSource()) {
                            graphics.guiRenderState.addGuiElement(new BlurredRoundedRectangleRenderState(
                                    HaloRenderPipelines.ROUNDED_BLUR,
                                    textureSetup,
                                    pose,
                                    styleRectX + styleRectW - 8.0f, optY + styleOptionH / 2.0f - 1.0f, 2.0f, 2.0f,
                                    ARGB.color(255, 255, 255, 255),
                                    1.0f,
                                    200.0f,
                                    0.0f,
                                    dropdownScissor
                            ));
                        }

                        var itemFont = MsdfFontManager.getFont("productsans-semibold", 5.5f);
                        if (itemFont != null) {
                            graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                                    itemFont,
                                    source.getName(),
                                    new Matrix3x2f(pose),
                                    styleRectX + 4.0f,
                                    optY + styleOptionH / 2.0f + 0.2f - itemFont.getHeight(5.5f) / 2f,
                                    5.5f,
                                    col,
                                    dropdownScissor
                            ));
                        }
                    }
                }
            }
        }
    }
}
