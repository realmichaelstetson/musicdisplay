package com.haloclient.client.gui.click;

import com.haloclient.client.render.CaptureManager;
import com.haloclient.client.render.HaloRenderPipelines;
import com.haloclient.client.render.font.HaloFontRenderState;
import com.haloclient.client.render.font.MsdfFont;
import com.haloclient.client.render.font.MsdfFontManager;
import com.haloclient.client.render.renderstates.BlurredRoundedRectangleRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import org.joml.Matrix3x2f;

public class HttpNotificationManager {
    private static volatile String statusText = "";
    private static volatile String messageText = "";
    private static volatile long showUntil = 0L;
    private static volatile float currentYOffset = 100.0f; // starts off-screen at the bottom

    public static void show(int statusCode, String message) {
        statusText = "Error " + statusCode;
        if (message != null) {
            message = message.replace("Player command failed:", "").trim();
        }
        messageText = message;
        showUntil = System.currentTimeMillis() + 4500L; // show for 4.5 seconds
    }

    public static void render(GuiGraphicsExtractor graphics) {
        long now = System.currentTimeMillis();
        if (now > showUntil && currentYOffset >= 100.0f) {
            return;
        }

        // Simple animation logic
        float targetY = now <= showUntil ? 0.0f : 100.0f;
        currentYOffset += (targetY - currentYOffset) * 0.12f;

        if (currentYOffset > 99.0f && now > showUntil) {
            return;
        }

        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();

        // Dimensions of the notification toast
        float width = 180.0f;
        float height = 20.0f;
        float margin = 10.0f;

        float x = screenWidth - width - margin;
        float y = screenHeight - height - margin + currentYOffset;

        var textureSetup = CaptureManager.getCaptureTextureSetup();
        var pose = new Matrix3x2f(graphics.pose());
        var parentScissor = graphics.scissorStack.peek();

        // 1. Blurred background card
        // We use a dark semi-transparent red/grey to make it look premium
        int cardColor = ARGB.color(180, 24, 12, 12);

        graphics.guiRenderState.addGuiElement(new BlurredRoundedRectangleRenderState(
                HaloRenderPipelines.ROUNDED_BLUR,
                textureSetup,
                pose,
                x, y, width, height,
                cardColor,
                7.0f, // radius
                15.0f, // blur strength
                0.0f, // bloom
                parentScissor
        ));

        // Load MSDF fonts
        MsdfFont titleFont = MsdfFontManager.getFont("inter-bold", 7.0f);
        MsdfFont msgFont = MsdfFontManager.getFont("inter-regular", 6.0f);

        // 2. Render exclamation mark icon on the left
        if (titleFont != null) {
            float iconSize = 10.0f;
            float iconX = x + 10.0f;
            float iconY = y + (height - iconSize) / 2.0f - 1;
            
            // Draw a subtle red exclamation mark
            graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                    titleFont,
                    "!",
                    pose,
                    iconX,
                    iconY,
                    iconSize,
                    ARGB.color(255, 239, 68, 68),
                    parentScissor
            ));
        }

        // 3. Render Status (Error Code) & Message
        float textX = x + 24.0f;
        float maxTextWidth = width - 30.0f;

        if (titleFont != null) {
            graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                    titleFont,
                    statusText,
                    pose,
                    textX,
                    y + 2.0f,
                    7.0f,
                    ARGB.color(255, 255, 255, 255),
                    parentScissor
            ));
        }

        if (msgFont != null) {
            String trimmedMsg = MusicDisplayOverlay.trimToWidthMsdf(msgFont, messageText, maxTextWidth, 7.0f);
            graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                    msgFont,
                    trimmedMsg,
                    pose,
                    textX,
                    y + 11.0f,
                    6.0f,
                    ARGB.color(200, 220, 220, 220),
                    parentScissor
            ));
        }
    }
}
