package com.haloclient.client.gui;

import com.haloclient.client.render.renderstates.BlurredRoundedRectangleRenderState;
import com.haloclient.client.render.CaptureManager;
import com.haloclient.client.render.HaloRenderPipelines;
import com.haloclient.client.render.animation.Animation;
import com.haloclient.client.render.animation.Easing;
import com.haloclient.client.util.FrameClock;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import org.joml.Matrix3x2f;

public class HaloScreen extends Screen {

    private final Animation scaleAnimation = new Animation(Easing.EASE_OUT_QUART, 250L);
    private boolean closing = false;

    public HaloScreen() {
        super(Component.literal("Halo Client GUI"));
        scaleAnimation.setValue(0.0f);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        FrameClock.update();
        scaleAnimation.run(closing ? 0.0f : 1.0f);
        
        if (closing && scaleAnimation.getValue() <= 0.001f) {
            this.minecraft.gui.setScreen(null);
            return;
        }

        float scale = scaleAnimation.getValue();
        if (scale <= 0.0001f) return;

        // Draw centered rectangle
        float width = 400.0f;
        float height = 250.0f;
        float x = (graphics.guiWidth() - width) / 2.0f;
        float y = (graphics.guiHeight() - height) / 2.0f;
        float radius = 10.0f;
        float blur = 30.0f;
        float bloom = 0.0f;

        var textureSetup = CaptureManager.getCaptureTextureSetup();
        int color = ARGB.color(100, 0, 0, 0);

        Matrix3x2f pose = new Matrix3x2f(graphics.pose());
        if (scale < 0.999f) {
            float centerX = x + width / 2.0f;
            float centerY = y + height / 2.0f;
            pose.translate(centerX, centerY);
            pose.scale(scale);
            pose.translate(-centerX, -centerY);
        }

        graphics.guiRenderState.addGuiElement(new BlurredRoundedRectangleRenderState(
                HaloRenderPipelines.ROUNDED_BLUR,
                textureSetup,
                pose,
                x, y, width, height,
                color,
                radius,
                blur,
                bloom,
                graphics.scissorStack.peek()
        ));

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        this.closing = true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {

    }

    @Override
    public boolean isInGameUi() {
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
