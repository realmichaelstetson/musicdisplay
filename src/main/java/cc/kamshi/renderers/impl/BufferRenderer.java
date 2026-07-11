package cc.kamshi.renderers.impl;

import java.util.OptionalInt;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.BuiltBuffer;

public final class BufferRenderer {

    public static RenderPass uploadBuffer(BuiltBuffer buffer) {
        VertexFormat vertexFormat = buffer.getDrawParameters().format();
       
        GpuBuffer vertexBuffer = vertexFormat.uploadImmediateVertexBuffer(buffer.getBuffer());
        RenderSystem.ShapeIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(buffer.getDrawParameters().mode());
        GpuBuffer indexBuffer = shapeIndexBuffer.getIndexBuffer(buffer.getDrawParameters().indexCount());
        VertexFormat.IndexType indexType = shapeIndexBuffer.getIndexType();

        Framebuffer fbo = MinecraftClient.getInstance().getFramebuffer();
        RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder()
            .createRenderPass(fbo.getColorAttachment(), OptionalInt.empty());
        
        renderPass.setVertexBuffer(0, vertexBuffer);
        renderPass.setIndexBuffer(indexBuffer, indexType);

        return renderPass;
    }

    public static void renderBuffer(BuiltBuffer buffer, RenderPass renderPass) {
        renderPass.drawIndexed(0, buffer.getDrawParameters().indexCount());

        renderPass.close();
        buffer.close();
    }

    public static void draw(BuiltBuffer buffer, com.mojang.blaze3d.pipeline.RenderPipeline pipeline) {
        RenderPass renderPass = uploadBuffer(buffer);
        renderPass.setPipeline(pipeline);
        renderBuffer(buffer, renderPass);
    }

}
