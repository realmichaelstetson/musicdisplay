package com.haloclient.client.render.font;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.haloclient.client.render.HaloVertexConsumer;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.render.TextureSetup;
import org.joml.Matrix3x2fc;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * MSDF (Multi-channel Signed Distance Field) font renderer.
 * Loads pre-generated MSDF atlas (PNG) and glyph metrics (JSON) produced by msdf-atlas-gen.
 * Renders text that stays crisp and sharp at any scale/resolution.
 */
public class MsdfFont {

    private final GpuTexture texture;
    private final GpuTextureView view;
    private final TextureSetup textureSetup;

    private final int atlasWidth;
    private final int atlasHeight;
    private final float distanceRange; // pxRange from atlas generation
    private final float fontSize;      // nominal font size in the atlas (em → px mapping)

    // Font metrics (in em units, multiply by fontSize to get pixels)
    private final float lineHeight;
    private final float ascender;
    private final float descender;

    // Glyph data indexed by unicode codepoint
    private final Map<Integer, GlyphInfo> glyphs = new HashMap<>();

    /**
     * Per-glyph metrics and atlas coordinates.
     */
    public static class GlyphInfo {
        public final int unicode;
        public final float advance; // in em units

        // Plane bounds (em units) — the quad position relative to the cursor
        public final float planeLeft, planeBottom, planeRight, planeTop;

        // Atlas bounds (pixel coordinates in atlas texture)
        public final float atlasLeft, atlasBottom, atlasRight, atlasTop;

        public GlyphInfo(int unicode, float advance,
                         float planeLeft, float planeBottom, float planeRight, float planeTop,
                         float atlasLeft, float atlasBottom, float atlasRight, float atlasTop) {
            this.unicode = unicode;
            this.advance = advance;
            this.planeLeft = planeLeft;
            this.planeBottom = planeBottom;
            this.planeRight = planeRight;
            this.planeTop = planeTop;
            this.atlasLeft = atlasLeft;
            this.atlasBottom = atlasBottom;
            this.atlasRight = atlasRight;
            this.atlasTop = atlasTop;
        }
    }

    /**
     * Creates an MsdfFont from an atlas image stream and a JSON metrics stream.
     *
     * @param imageStream  PNG atlas image (MSDF, 3+ channels)
     * @param jsonStream   JSON glyph metrics from msdf-atlas-gen
     * @param renderSize   the desired rendering size in pixels (used for scaling em→px)
     */
    public MsdfFont(InputStream imageStream, InputStream jsonStream, float renderSize) throws Exception {
        // Parse JSON metrics
        JsonObject root = JsonParser.parseReader(new InputStreamReader(jsonStream, StandardCharsets.UTF_8)).getAsJsonObject();

        JsonObject atlas = root.getAsJsonObject("atlas");
        this.atlasWidth = atlas.get("width").getAsInt();
        this.atlasHeight = atlas.get("height").getAsInt();
        this.distanceRange = atlas.get("distanceRange").getAsFloat();
        this.fontSize = renderSize;

        // yOrigin: "bottom" means atlas Y=0 is at bottom (OpenGL convention)
        // We need to handle this when computing UV coordinates
        String yOrigin = atlas.has("yOrigin") ? atlas.get("yOrigin").getAsString() : "bottom";
        boolean flipY = "bottom".equals(yOrigin);

        JsonObject metrics = root.getAsJsonObject("metrics");
        float emSize = metrics.has("emSize") ? metrics.get("emSize").getAsFloat() : 1.0f;
        this.lineHeight = metrics.has("lineHeight") ? metrics.get("lineHeight").getAsFloat() / emSize : 1.2f;
        this.ascender = metrics.has("ascender") ? metrics.get("ascender").getAsFloat() / emSize : 0.8f;
        this.descender = metrics.has("descender") ? metrics.get("descender").getAsFloat() / emSize : -0.2f;

        // Parse glyphs
        JsonArray glyphArray = root.getAsJsonArray("glyphs");
        for (JsonElement elem : glyphArray) {
            JsonObject g = elem.getAsJsonObject();
            if (!g.has("unicode") || g.get("unicode").isJsonNull()) {
                continue;
            }
            int unicode = g.get("unicode").getAsInt();
            float advance = g.get("advance").getAsFloat() / emSize;

            float planeLeft = 0, planeBottom = 0, planeRight = 0, planeTop = 0;
            float aLeft = 0, aBottom = 0, aRight = 0, aTop = 0;

            if (g.has("planeBounds")) {
                JsonObject pb = g.getAsJsonObject("planeBounds");
                planeLeft = pb.get("left").getAsFloat() / emSize;
                planeBottom = pb.get("bottom").getAsFloat() / emSize;
                planeRight = pb.get("right").getAsFloat() / emSize;
                planeTop = pb.get("top").getAsFloat() / emSize;
            }

            if (g.has("atlasBounds")) {
                JsonObject ab = g.getAsJsonObject("atlasBounds");
                aLeft = ab.get("left").getAsFloat();
                aBottom = ab.get("bottom").getAsFloat();
                aRight = ab.get("right").getAsFloat();
                aTop = ab.get("top").getAsFloat();
            }

            glyphs.put(unicode, new GlyphInfo(unicode, advance,
                    planeLeft, planeBottom, planeRight, planeTop,
                    aLeft, aBottom, aRight, aTop));
        }

        // Load the atlas PNG image
        NativeImage image = NativeImage.read(imageStream);
        int imgW = image.getWidth();
        int imgH = image.getHeight();

        this.texture = RenderSystem.getDevice().createTexture(
                "MSDF Font Atlas",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_TEXTURE_BINDING,
                GpuFormat.RGBA8_UNORM,
                imgW, imgH, 1, 1
        );
        this.view = RenderSystem.getDevice().createTextureView(texture);

        // Upload image data to GPU
        ByteBuffer pixels = ByteBuffer.allocateDirect(imgW * imgH * 4);
        for (int py = 0; py < imgH; py++) {
            for (int px = 0; px < imgW; px++) {
                int rgba = image.getPixel(px, py);
                // NativeImage.getPixel returns ABGR format
                int a = (rgba >> 24) & 0xFF;
                int b = (rgba >> 16) & 0xFF;
                int g = (rgba >> 8) & 0xFF;
                int r = rgba & 0xFF;
                pixels.put((byte) r);
                pixels.put((byte) g);
                pixels.put((byte) b);
                pixels.put((byte) a);
            }
        }
        pixels.flip();

        var encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToTexture(texture, pixels, 0, 0, 0, 0, imgW, imgH);
        image.close();

        GpuSampler sampler = RenderSystem.getDevice().createSampler(
                com.mojang.blaze3d.textures.AddressMode.CLAMP_TO_EDGE,
                com.mojang.blaze3d.textures.AddressMode.CLAMP_TO_EDGE,
                com.mojang.blaze3d.textures.FilterMode.LINEAR,
                com.mojang.blaze3d.textures.FilterMode.LINEAR,
                1, java.util.OptionalDouble.empty()
        );

        this.textureSetup = TextureSetup.singleTexture(view, sampler);
    }

    /**
     * Draws a string using MSDF quads. Each glyph emits a quad with UV from the atlas
     * and pxRange passed via CustomData.x for the shader.
     */
    public void drawString(VertexConsumer buffer, HaloVertexConsumer hv, Matrix3x2fc pose,
                           String text, float x, float y, float size, int color) {
        float scale = size / fontSize;
        float cursorX = x;
        // Baseline: y represents the top of the text line; offset by ascender
        float baseline = y + ascender * size;

        for (int i = 0; i < text.length(); i++) {
            int codepoint = text.codePointAt(i);
            GlyphInfo glyph = glyphs.get(codepoint);
            if (glyph == null) {
                // Try space fallback
                GlyphInfo space = glyphs.get(32);
                if (space != null) cursorX += space.advance * size;
                continue;
            }

            // Only draw if the glyph has atlas bounds (not a whitespace-only glyph)
            if (glyph.atlasRight > glyph.atlasLeft && glyph.atlasTop > glyph.atlasBottom) {
                // Screen-space quad position
                float qx0 = cursorX + glyph.planeLeft * size;
                float qy0 = baseline - glyph.planeTop * size;   // Y is flipped (screen coords: top-down)
                float qx1 = cursorX + glyph.planeRight * size;
                float qy1 = baseline - glyph.planeBottom * size;

                // UV coordinates (atlas pixels → normalized)
                // yOrigin=bottom: atlas Y=0 is at bottom of image, UV V=0 is at top → flip V
                float u0 = glyph.atlasLeft / atlasWidth;
                float v0 = 1.0f - glyph.atlasTop / atlasHeight;
                float u1 = glyph.atlasRight / atlasWidth;
                float v1 = 1.0f - glyph.atlasBottom / atlasHeight;

                // Pass raw distanceRange — shader computes screen-space pxRange via fwidth()
                float pxRange = distanceRange;

                // Emit quad (4 vertices, QUADS mode)
                addVertex(buffer, hv, pose, qx0, qy0, u0, v0, color, pxRange);
                addVertex(buffer, hv, pose, qx0, qy1, u0, v1, color, pxRange);
                addVertex(buffer, hv, pose, qx1, qy1, u1, v1, color, pxRange);
                addVertex(buffer, hv, pose, qx1, qy0, u1, v0, color, pxRange);
            }

            cursorX += glyph.advance * size;
        }
    }

    private void addVertex(VertexConsumer v, HaloVertexConsumer hv, Matrix3x2fc pose,
                           float vx, float vy, float u, float vCoord, int color, float pxRange) {
        v.addVertexWith2DPose(pose, vx, vy).setColor(color).setUv(u, vCoord);
        hv.setCustomData(pxRange, 0, 0, 0); // pxRange passed to shader via CustomData.x
        hv.setColor2(0);
        hv.setShadowProps(0, 0, 0, 0);
    }

    /**
     * Computes the width of a string in pixels at the given font size.
     */
    public float getWidth(String text, float size) {
        float width = 0;
        for (int i = 0; i < text.length(); i++) {
            int codepoint = text.codePointAt(i);
            GlyphInfo glyph = glyphs.get(codepoint);
            if (glyph != null) {
                width += glyph.advance * size;
            } else {
                GlyphInfo space = glyphs.get(32);
                if (space != null) width += space.advance * size;
            }
        }
        return width;
    }

    /**
     * Returns the line height in pixels for the given font size.
     */
    public float getHeight(float size) {
        return lineHeight * size;
    }

    /**
     * Returns the ascender height in pixels for the given font size.
     */
    public float getAscender(float size) {
        return ascender * size;
    }

    public TextureSetup getTextureSetup() {
        return textureSetup;
    }

    public void free() {
        view.close();
        texture.close();
    }
}
