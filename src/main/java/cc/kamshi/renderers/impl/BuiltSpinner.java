package cc.kamshi.renderers.impl;

import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;

import cc.kamshi.builders.states.QuadColorState;
import cc.kamshi.builders.states.SizeState;
import cc.kamshi.providers.ResourceProvider;
import cc.kamshi.renderers.IRenderer;

public record BuiltSpinner(
        SizeState size,
        QuadColorState color,
        float startAngle,
        float sweepAngle,
        float radius,
        float thickness
    ) implements IRenderer {

    private static final ShaderProgramKey SPINNER_SHADER_KEY = new ShaderProgramKey(ResourceProvider.getShaderIdentifier("spinner"),
        VertexFormats.POSITION_COLOR, Defines.EMPTY);

    @Override
    public void render(Matrix4f matrix, float x, float y, float z) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        float width = this.size.width(), height = this.size.height();
        ShaderProgram shader = RenderSystem.setShader(SPINNER_SHADER_KEY);
        shader.getUniform("Size").set(width, height);
        shader.getUniform("StartAngle").set(this.startAngle);
        shader.getUniform("SweepAngle").set(this.sweepAngle);
        shader.getUniform("Radius").set(this.radius);
        shader.getUniform("Thickness").set(this.thickness);

        BufferBuilder builder = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        builder.vertex(matrix, x, y, z).color(this.color.color1());
        builder.vertex(matrix, x, y + height, z).color(this.color.color2());
        builder.vertex(matrix, x + width, y + height, z).color(this.color.color3());
        builder.vertex(matrix, x + width, y, z).color(this.color.color4());

        BufferRenderer.drawWithGlobalProgram(builder.end());

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

}
