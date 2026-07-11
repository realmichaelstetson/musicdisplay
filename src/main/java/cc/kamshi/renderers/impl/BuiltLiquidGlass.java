package cc.kamshi.renderers.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;

import java.util.OptionalInt;
import org.joml.Matrix4f;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;

import cc.kamshi.builders.states.QuadColorState;
import cc.kamshi.builders.states.QuadRadiusState;
import cc.kamshi.builders.states.SizeState;
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

    private static GpuTexture TEMP_TEXTURE = null;

    private static void prepareTempTexture() {
        Framebuffer fbo = MinecraftClient.getInstance().getFramebuffer();
        if (TEMP_TEXTURE == null
                || TEMP_TEXTURE.getWidth(0) != fbo.textureWidth || TEMP_TEXTURE.getHeight(0) != fbo.textureHeight) {
            if (TEMP_TEXTURE != null) {
                TEMP_TEXTURE.close();
            }
            
            TEMP_TEXTURE = RenderSystem.getDevice().createTexture((String) null, 
                TextureFormat.RGBA8, fbo.textureWidth, fbo.textureHeight, 1);
        }

        RenderSystem.ShapeIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(DrawMode.QUADS);
        GpuBuffer indexBuffer = shapeIndexBuffer.getIndexBuffer(6);
        GpuBuffer vertexBuffer = RenderSystem.getQuadVertexBuffer();
        RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(TEMP_TEXTURE, OptionalInt.empty());
        
        renderPass.setPipeline(CRenderPipelines.BLIT_PIPLINE);
        renderPass.setVertexBuffer(0, vertexBuffer);
        renderPass.setIndexBuffer(indexBuffer, shapeIndexBuffer.getIndexType());
        renderPass.bindSampler("InSampler", fbo.getColorAttachment());
        renderPass.drawIndexed(0, 6);
        renderPass.close();
    }

    @Override
    public void render(Matrix4f matrix, float x, float y, float z) {
        prepareTempTexture();

        float width = this.size.width(), height = this.size.height();
        
        float renderX = x - this.bloom;
        float renderY = y - this.bloom;
        float renderWidth = width + this.bloom * 2.0f;
        float renderHeight = height + this.bloom * 2.0f;

        BufferBuilder builder = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        builder.vertex(matrix, renderX, renderY, z).color(this.color.color1());
        builder.vertex(matrix, renderX, renderY + renderHeight, z).color(this.color.color2());
        builder.vertex(matrix, renderX + renderWidth, renderY + renderHeight, z).color(this.color.color3());
        builder.vertex(matrix, renderX + renderWidth, renderY, z).color(this.color.color4());

        BuiltBuffer buffer = builder.end();
        RenderPass renderPass = BufferRenderer.uploadBuffer(buffer);

        renderPass.setPipeline(CRenderPipelines.LIQUID_GLASS_PIPLINE);

        renderPass.setUniform("Size", width, height);
        renderPass.setUniform("Radius", this.radius.radius1(), this.radius.radius2(), 
            this.radius.radius3(), this.radius.radius4());
        renderPass.setUniform("Smoothness", this.smoothness);
        renderPass.setUniform("BlurRadius", this.blurRadius);
        
        int argb = this.color2 != null ? this.color2.getRGB() : this.color.color1();
        float a = ((argb >> 24) & 0xFF) / 255.0f;
        float r = ((argb >> 16) & 0xFF) / 255.0f;
        float g = ((argb >> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;
        renderPass.setUniform("Color2", r, g, b, a);
        renderPass.setUniform("GradientAngle", this.gradientAngle);
        renderPass.setUniform("Bloom", this.bloom);
        renderPass.setUniform("CornerMask", this.cornerMask);
        
        renderPass.bindSampler("Sampler0", TEMP_TEXTURE);

        BufferRenderer.renderBuffer(buffer, renderPass);
    }

}
