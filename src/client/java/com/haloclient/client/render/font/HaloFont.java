package com.haloclient.client.render.font;

import com.haloclient.client.render.HaloVertexConsumer;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fc;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTTPackedchar;
import org.lwjgl.stb.STBTTPackContext;
import org.lwjgl.stb.STBTruetype;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.stb.STBTruetype.*;

public class HaloFont {
    private final GpuTexture texture;
    private final GpuTextureView view;
    private final float size;
    private final STBTTPackedchar.Buffer charData;
    private final int width = 2048;
    private final int height = 2048;
    private final TextureSetup textureSetup;

    public HaloFont(InputStream fontStream, float size) throws IOException {
        this.size = size;
        byte[] bytes = fontStream.readAllBytes();
        ByteBuffer fontBuffer = BufferUtils.createByteBuffer(bytes.length);
        fontBuffer.put(bytes);
        fontBuffer.flip();

        STBTTFontinfo info = STBTTFontinfo.create();
        if (!stbtt_InitFont(info, fontBuffer)) {
            throw new IOException("Failed to initialize font");
        }

        this.charData = STBTTPackedchar.malloc(256);
        ByteBuffer bitmap = BufferUtils.createByteBuffer(width * height);

        STBTTPackContext pc = STBTTPackContext.malloc();
        stbtt_PackBegin(pc, bitmap, width, height, 0, 1, 0L);
        stbtt_PackSetOversampling(pc, 4, 4); // 4x oversampling for HD text
        stbtt_PackSetSkipMissingCodepoints(pc, false);
        stbtt_PackFontRange(pc, fontBuffer, 0, size, 32, charData);
        stbtt_PackEnd(pc);
        
        // Free temporal buffers
        pc.free();

        this.texture = RenderSystem.getDevice().createTexture("Halo Font Texture", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_TEXTURE_BINDING, GpuFormat.R8_UNORM, width, height, 1, 1);
        this.view = RenderSystem.getDevice().createTextureView(texture);
        
        // Upload data to GPU using the correct method from CommandEncoder
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToTexture(texture, bitmap, 0, 0, 0, 0, width, height);

        GpuSampler sampler = RenderSystem.getDevice().createSampler(
            com.mojang.blaze3d.textures.AddressMode.CLAMP_TO_EDGE,
            com.mojang.blaze3d.textures.AddressMode.CLAMP_TO_EDGE,
            com.mojang.blaze3d.textures.FilterMode.LINEAR,
            com.mojang.blaze3d.textures.FilterMode.LINEAR,
            1, java.util.OptionalDouble.empty()
        );

        this.textureSetup = TextureSetup.singleTexture(view, sampler);
    }

    public void drawString(VertexConsumer buffer, HaloVertexConsumer hv, Matrix3x2fc pose, String text, float x, float y, int color) {
        float currentX = x;

        for (char c : text.toCharArray()) {
            if (c < 32 || c > 127) continue;
            
            STBTTPackedchar pc = charData.get(c - 32);
            
            float x0 = currentX + pc.xoff();
            float y0 = y + pc.yoff();
            
            // Width/height must be divided by oversampling factor (4x)
            float x1 = x0 + (pc.x1() - pc.x0()) / 4.0f;
            float y1 = y0 + (pc.y1() - pc.y0()) / 4.0f;

            float u0 = pc.x0() / (float) width;
            float v0 = pc.y0() / (float) height;
            float u1 = pc.x1() / (float) width;
            float v1 = pc.y1() / (float) height;

            addVertex(buffer, hv, pose, x0, y0, u0, v0, color);
            addVertex(buffer, hv, pose, x0, y1, u0, v1, color);
            addVertex(buffer, hv, pose, x1, y1, u1, v1, color);
            addVertex(buffer, hv, pose, x1, y0, u1, v0, color);

            currentX += pc.xadvance();
        }
    }

    private void addVertex(VertexConsumer v, HaloVertexConsumer hv, Matrix3x2fc pose, float vx, float vy, float u, float vCoord, int color) {
        v.addVertexWith2DPose(pose, vx, vy).setColor(color).setUv(u, vCoord);
        hv.setCustomData(0, 0, 0, 0); // No special rect params
        hv.setColor2(0);
        hv.setShadowProps(0, 0, 0, 0);
    }

    public float getWidth(String text) {
        float currentX = 0;
        for (char c : text.toCharArray()) {
            if (c < 32 || c > 127) continue;
            currentX += charData.get(c - 32).xadvance();
        }
        return currentX;
    }

    public float getHeight() {
        return size;
    }

    public TextureSetup getTextureSetup() {
        return textureSetup;
    }

    public void free() {
        charData.free();
        view.close();
        texture.close();
    }
}
