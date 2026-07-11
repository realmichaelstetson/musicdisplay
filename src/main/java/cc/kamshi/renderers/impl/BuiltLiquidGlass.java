package cc.kamshi.renderers.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;

import org.joml.Matrix4f;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.mojang.blaze3d.systems.RenderSystem;

import cc.kamshi.builders.states.QuadColorState;
import cc.kamshi.builders.states.QuadRadiusState;
import cc.kamshi.builders.states.SizeState;
import cc.kamshi.providers.ResourceProvider;
import cc.kamshi.renderers.IRenderer;

public record BuiltLiquidGlass(
        SizeState size,
        QuadRadiusState radius,
        QuadColorState color,
        java.awt.Color color2,
        float smoothness,
        float blurRadius,
        float gradientAngle,
        float bloom,
        float cornerMask
    ) implements IRenderer {

	private static final ShaderProgramKey LIQUID_GLASS_SHADER_KEY = new ShaderProgramKey(ResourceProvider.getShaderIdentifier("liquid_glass"), 
		VertexFormats.POSITION_COLOR, Defines.EMPTY);
    private static final Supplier<SimpleFramebuffer> TEMP_FBO_SUPPLIER = Suppliers
        .memoize(() -> new SimpleFramebuffer(1920, 1024, false));
    private static final Framebuffer MAIN_FBO = MinecraftClient.getInstance().getFramebuffer();

    @Override
    public void render(Matrix4f matrix, float x, float y, float z) {
        SimpleFramebuffer fbo = TEMP_FBO_SUPPLIER.get();
        if (fbo.textureWidth != MAIN_FBO.textureWidth || fbo.textureHeight != MAIN_FBO.textureHeight) {
            fbo.resize(MAIN_FBO.textureWidth, MAIN_FBO.textureHeight);
        }
        fbo.setTexFilter(9729); // GL_LINEAR (smooth sampling)

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        fbo.beginWrite(false);
        MAIN_FBO.draw(fbo.textureWidth, fbo.textureHeight);
        MAIN_FBO.beginWrite(false);

        RenderSystem.setShaderTexture(0, fbo.getColorAttachment());

        float width = this.size.width(), height = this.size.height();
		ShaderProgram shader = RenderSystem.setShader(LIQUID_GLASS_SHADER_KEY);
        if (shader.getUniform("Size") != null) {
            shader.getUniform("Size").set(width, height);
        }
        if (shader.getUniform("Radius") != null) {
            shader.getUniform("Radius").set(this.radius.radius1(), this.radius.radius2(), 
                this.radius.radius3(), this.radius.radius4());
        }
        if (shader.getUniform("Smoothness") != null) {
            shader.getUniform("Smoothness").set(this.smoothness);
        }
        if (shader.getUniform("BlurRadius") != null) {
            shader.getUniform("BlurRadius").set(this.blurRadius);
        }
        
        int argb = this.color2 != null ? this.color2.getRGB() : this.color.color1();
        float a = ((argb >> 24) & 0xFF) / 255.0f;
        float r = ((argb >> 16) & 0xFF) / 255.0f;
        float g = ((argb >> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;
        if (shader.getUniform("Color2") != null) {
            shader.getUniform("Color2").set(r, g, b, a);
        }
        if (shader.getUniform("GradientAngle") != null) {
            shader.getUniform("GradientAngle").set(this.gradientAngle);
        }
        if (shader.getUniform("Bloom") != null) {
            shader.getUniform("Bloom").set(this.bloom);
        }
        if (shader.getUniform("CornerMask") != null) {
            shader.getUniform("CornerMask").set(this.cornerMask);
        }
		
        float renderX = x - this.bloom;
        float renderY = y - this.bloom;
        float renderWidth = width + this.bloom * 2.0f;
        float renderHeight = height + this.bloom * 2.0f;

		BufferBuilder builder = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        builder.vertex(matrix, renderX, renderY, z).color(this.color.color1());
        builder.vertex(matrix, renderX, renderY + renderHeight, z).color(this.color.color2());
        builder.vertex(matrix, renderX + renderWidth, renderY + renderHeight, z).color(this.color.color3());
        builder.vertex(matrix, renderX + renderWidth, renderY, z).color(this.color.color4());

        BufferRenderer.drawWithGlobalProgram(builder.end());

        RenderSystem.setShaderTexture(0, 0);

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

}
