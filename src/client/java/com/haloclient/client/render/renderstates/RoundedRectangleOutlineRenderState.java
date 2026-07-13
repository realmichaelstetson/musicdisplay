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
public record RoundedRectangleOutlineRenderState(
    RenderPipeline pipeline,
    TextureSetup textureSetup,
    Matrix3x2fc pose,
    float x, float y, float width, float height,
    int color, int color2,
    float radius, float borderThickness,
    float shadowBlur, float shadowOffsetX, float shadowOffsetY,
    float gradientAngle,
    @Nullable ScreenRectangle scissorArea,
    @Nullable ScreenRectangle bounds
) implements GuiElementRenderState {

    // Main convenience constructor for single color outline
    public RoundedRectangleOutlineRenderState(
        final RenderPipeline pipeline,
        final Matrix3x2fc pose,
        final float x, final float y, final float width, final float height,
        final int color,
        final float radius, final float borderThickness,
        @Nullable final ScreenRectangle scissorArea
    ) {
        this(pipeline, TextureSetup.noTexture(), pose, x, y, width, height, color, color, radius, borderThickness, 0f, 0f, 0f, 0f, scissorArea, getBounds(x, y, width, height, pose, scissorArea));
    }

    // Full constructor for advanced features (Gradients, Borders)
    public RoundedRectangleOutlineRenderState(
        final RenderPipeline pipeline,
        final Matrix3x2fc pose,
        final float x, final float y, final float width, final float height,
        final int color, final int color2,
        final float radius, final float borderThickness,
        final float gradientAngle,
        @Nullable final ScreenRectangle scissorArea
    ) {
        this(pipeline, TextureSetup.noTexture(), pose, x, y, width, height, color, color2, radius, borderThickness, 0f, 0f, 0f, gradientAngle, scissorArea, getBounds(x, y, width, height, pose, scissorArea));
    }

    @Override
    public void buildVertices(final VertexConsumer vertexConsumer) {
        HaloVertexConsumer hv = (HaloVertexConsumer) vertexConsumer;
        float bx = x - shadowBlur;
        float by = y - shadowBlur;
        float bw = width + shadowBlur * 2;
        float bh = height + shadowBlur * 2;
        float x1 = bx + bw;
        float y1 = by + bh;

        addVertex(vertexConsumer, hv, bx, by, 0.0f, 0.0f); // TL
        addVertex(vertexConsumer, hv, bx, y1, 0.0f, 1.0f); // BL
        addVertex(vertexConsumer, hv, x1, y1, 1.0f, 1.0f); // BR
        addVertex(vertexConsumer, hv, x1, by, 1.0f, 0.0f); // TR
    }

    private void addVertex(VertexConsumer v, HaloVertexConsumer hv, float vx, float vy, float u, float vCoord) {
        v.addVertexWith2DPose(pose, vx, vy).setColor(color).setUv(u, vCoord);
        hv.setCustomData(width, height, radius, borderThickness);
        hv.setColor2(color2);
        hv.setShadowProps(shadowBlur, shadowOffsetX, shadowOffsetY, gradientAngle);
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
