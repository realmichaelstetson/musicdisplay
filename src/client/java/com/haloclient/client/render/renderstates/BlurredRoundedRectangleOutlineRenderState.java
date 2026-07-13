package com.haloclient.client.render.renderstates;

import com.haloclient.client.render.HaloVertexConsumer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record BlurredRoundedRectangleOutlineRenderState(
    RenderPipeline pipeline,
    TextureSetup textureSetup,
    Matrix3x2fc pose,
    float x, float y, float width, float height,
    int color, int color2,
    float radius, float borderThickness,
    float blurStrength, float bloom,
    float gradientAngle,
    float cornerMask,
    @Nullable ScreenRectangle scissorArea,
    @Nullable ScreenRectangle bounds
) implements GuiElementRenderState {

    // Convenience constructor for single color outline
    public BlurredRoundedRectangleOutlineRenderState(
        final RenderPipeline pipeline,
        final TextureSetup textureSetup,
        final Matrix3x2fc pose,
        final float x, final float y, final float width, final float height,
        final int color,
        final float radius, final float borderThickness,
        final float blurStrength,
        final float bloom,
        @Nullable final ScreenRectangle scissorArea
    ) {
        this(pipeline, textureSetup, pose, x, y, width, height,
            color, color,
            radius, borderThickness,
            blurStrength, bloom, 0.0f, 0.0f,
            scissorArea,
            getBounds(x, y, width, height, pose, scissorArea));
    }

    @Override
    public void buildVertices(final VertexConsumer vertexConsumer) {
        HaloVertexConsumer hv = (HaloVertexConsumer) vertexConsumer;
        float renderX = x - bloom;
        float renderY = y - bloom;
        float renderX1 = x + width + bloom;
        float renderY1 = y + height + bloom;

        addVertex(vertexConsumer, hv, renderX, renderY, 0.0f, 0.0f); // TL
        addVertex(vertexConsumer, hv, renderX, renderY1, 0.0f, 1.0f); // BL
        addVertex(vertexConsumer, hv, renderX1, renderY1, 1.0f, 1.0f); // BR
        addVertex(vertexConsumer, hv, renderX1, renderY, 1.0f, 0.0f); // TR
    }

    private void addVertex(VertexConsumer v, HaloVertexConsumer hv, float vx, float vy, float u, float vCoord) {
        v.addVertexWith2DPose(pose, vx, vy).setColor(color).setUv(u, vCoord);
        hv.setCustomData(width, height, radius, borderThickness);
        hv.setColor2(color2);
        hv.setShadowProps(blurStrength, bloom, gradientAngle, cornerMask);
    }

    @Nullable
    private static ScreenRectangle getBounds(final float x, final float y, final float width, final float height, final Matrix3x2fc pose, @Nullable final ScreenRectangle scissorArea) {
        ScreenRectangle bounds = (new ScreenRectangle((int)x, (int)y, (int)width, (int)height)).transformMaxBounds(pose);
        return scissorArea != null ? scissorArea.intersection(bounds) : bounds;
    }

    @Override public RenderPipeline pipeline() { return pipeline; }
    @Override public TextureSetup textureSetup() { return textureSetup; }
    @Override public Matrix3x2fc pose() { return pose; }
    @Nullable @Override public ScreenRectangle scissorArea() { return scissorArea; }
    @Nullable @Override public ScreenRectangle bounds() { return bounds; }
}
