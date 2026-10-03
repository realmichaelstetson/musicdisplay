package com.haloclient.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryStack;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static com.haloclient.client.render.Constants.mc;
import java.util.ArrayList;
import java.util.List;
import static org.lwjgl.nanovg.NanoVG.*;
import static org.lwjgl.nanovg.NanoVGGL3.*;

public final class NVGRenderer {

    private static long VG = 0;
    private static boolean initAttempted = false;
    private static final List<Runnable> deferredTasks = new ArrayList<>();

    public static void addDeferredTask(final Runnable task) {
        deferredTasks.add(task);
    }

    public static void executeDeferredTasks() {
        if (deferredTasks.isEmpty()) return;
        
        if (beginFrame()) {
            try {
                for (final Runnable task : deferredTasks) {
                    try {
                        task.run();
                    } catch (final Throwable e) {
                        e.printStackTrace();
                    }
                }
            } finally {
                endFrame(true);
            }
        }
        deferredTasks.clear();
    }

    private static void ensureInitialized() {
        if (VG != 0) return;
        if (initAttempted) return;
        initAttempted = true;
        try {
            VG = nvgCreate(NVG_ANTIALIAS | NVG_STENCIL_STROKES);
            if (VG == 0) {
                System.err.println("[Halo/NVG] CRITICAL: nvgCreate returned 0 — NanoVG failed to initialize!");
                System.err.println("[Halo/NVG] Retrying without stencil strokes...");
                VG = nvgCreate(NVG_ANTIALIAS);
                if (VG == 0) {
                    System.err.println("[Halo/NVG] CRITICAL: nvgCreate still returned 0!");
                }
            }
            // Update Constants.VG so that code using `import static Constants.VG` gets the correct value
            Constants.VG = VG;
            System.out.println("[Halo/NVG] NanoVG context created: " + VG);
        } catch (Throwable t) {
            System.err.println("[Halo/NVG] Exception during nvgCreate:");
            t.printStackTrace();
            VG = 0;
        }
    }

    // ── NVG alignment constants (re-exported for consumer code) ──
    public static final int NVG_ALIGN_LEFT = org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT;
    public static final int NVG_ALIGN_CENTER = org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER;
    public static final int NVG_ALIGN_RIGHT = org.lwjgl.nanovg.NanoVG.NVG_ALIGN_RIGHT;
    public static final int NVG_ALIGN_TOP = org.lwjgl.nanovg.NanoVG.NVG_ALIGN_TOP;
    public static final int NVG_ALIGN_MIDDLE = org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE;
    public static final int NVG_ALIGN_BOTTOM = org.lwjgl.nanovg.NanoVG.NVG_ALIGN_BOTTOM;
    public static final int NVG_ALIGN_BASELINE = org.lwjgl.nanovg.NanoVG.NVG_ALIGN_BASELINE;

    // ── NVG image flags (re-exported for consumer code) ──
    public static final int NVG_IMAGE_GENERATE_MIPMAPS = org.lwjgl.nanovg.NanoVG.NVG_IMAGE_GENERATE_MIPMAPS;
    public static final int NVG_IMAGE_FLIPY = org.lwjgl.nanovg.NanoVG.NVG_IMAGE_FLIPY;
    public static final int NVG_IMAGE_PREMULTIPLIED = org.lwjgl.nanovg.NanoVG.NVG_IMAGE_PREMULTIPLIED;
    public static final int NVG_IMAGE_NODELETE = 1 << 16; // not in lwjgl NanoVG, define manually

    public static final NVGPaint NVG_PAINT = NVGPaint.create();
    public static final NVGPaint BLUR_PAINT = NVGPaint.create();
    public static final NVGPaint GLOW_PAINT = NVGPaint.create();

    public static final NVGColor NVG_COLOR_1 = NVGColor.create();
    public static final NVGColor NVG_COLOR_2 = NVGColor.create();

    private static boolean frameStarted;
    public static float globalAlpha = 1;

    public static boolean beginFrame() {
        ensureInitialized();
        if (VG == 0) {
            return false;
        }
        final com.mojang.blaze3d.platform.Window window = mc.getWindow();
        final float scaleFactor = (float) window.getGuiScale();

        if (!frameStarted) {
            GLUtility.setup();
            GLUtility.push();

            nvgBeginFrame(VG, window.getWidth() / scaleFactor, window.getHeight() / scaleFactor,
                    scaleFactor);
            if (!scissors.isEmpty()) {
                useCurrentScissors();
            }
            frameStarted = true;

            return true;
        }

        return false;
    }

    public static void endFrameAndReset(final boolean createRenderPass) {
        endFrame(createRenderPass);
        clearScissors();
    }

    /** Cached FBO id for the main render target — rebuilt when the color texture changes. */
    private static int haloFbo = 0;
    private static int haloFboColorTexId = 0;

    public static void endFrame(final boolean createRenderPass) {
        if (frameStarted) {
            try {
                if (createRenderPass) {
                    // Bind the main render target FBO directly via raw GL.
                    // We intentionally avoid MC's RenderPass / setPipeline because they
                    // overwrite GL state (active shader, texture bindings, blend mode)
                    // that NanoVG manages internally during nvgEndFrame().  Using MC's
                    // abstraction was causing font-atlas texture binds to be stomped,
                    // making all nvgText() calls invisible while shape fills still worked.
                    final RenderTarget rt = mc.gameRenderer.mainRenderTarget();
                    final int colorTexId = ((com.mojang.blaze3d.opengl.GlTexture) rt.getColorTexture()).glId();
                    if (haloFbo == 0 || haloFboColorTexId != colorTexId) {
                        if (haloFbo != 0) GL33C.glDeleteFramebuffers(haloFbo);
                        haloFbo = GL33C.glGenFramebuffers();
                        GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, haloFbo);
                        GL33C.glFramebufferTexture2D(GL33C.GL_FRAMEBUFFER, GL33C.GL_COLOR_ATTACHMENT0,
                                GL33C.GL_TEXTURE_2D, colorTexId, 0);
                        haloFboColorTexId = colorTexId;
                    } else {
                        GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, haloFbo);
                    }
                    GL33C.glViewport(0, 0, rt.width, rt.height);

                    nvgEndFrame(VG);

                    GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, 0);
                } else {
                    nvgEndFrame(VG);
                }
            } catch (Throwable t) {
                System.err.println("[Halo/NVG] Error in endFrame:");
                t.printStackTrace();
            } finally {
                try {
                    GLUtility.pop();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
                try {
                    // founded by unc (trol1337)
                    GL33C.glViewport(0, 0, mc.getWindow().getWidth(), mc.getWindow().getHeight());
                } catch (Throwable t) {
                    t.printStackTrace();
                }
                frameStarted = false;
            }
        }
    }

    public static boolean isFrameStarted() {
        return frameStarted;
    }

    public static void clearScissors() {
        scissors.clear();
    }

    public static void globalAlpha(final float alpha) {
        globalAlpha = alpha;
        nvgGlobalAlpha(VG, alpha);
    }

    public static void rect(final float x, final float y, final float width, final float height, final int color) {
        applyColor(color, NVG_COLOR_1);

        nvgBeginPath(VG);
        nvgFillColor(VG, NVG_COLOR_1);
        nvgRect(VG, x, y, width, height);
        nvgFill(VG);
        nvgClosePath(VG);
    }

    public static void circleOutline(final float x, final float y, final float radius, final float thickness, final int color) {
        applyColor(color, NVG_COLOR_1);
        nvgBeginPath(VG);
        nvgStrokeColor(VG, NVG_COLOR_1);
        nvgStrokeWidth(VG, thickness);
        nvgCircle(VG, x, y, radius);
        nvgStroke(VG);
        nvgClosePath(VG);
    }

    public static void rect(final float x, final float y, final float width, final float height, final int color,
            final float blur) {
        if (blur > 0) {
            rectGlow(x, y, width, height, blur, color);
        }
        rect(x, y, width, height, color);
    }

    public static void rect(final float x, final float y, final float width, final float height,
            final NVGPaint nvgPaint) {
        nvgBeginPath(VG);
        nvgFillPaint(VG, nvgPaint);
        nvgRect(VG, x, y, width, height);
        nvgFill(VG);

        nvgClosePath(VG);
    }

    public static void scale(final float factor, final float x, final float y, final float width, final float height,
            final Runnable content) {
        final float translateX = x + width / 2F;
        final float translateY = y + height / 2F;

        nvgSave(VG);
        nvgTranslate(VG, translateX, translateY);
        nvgScale(VG, factor, factor);
        nvgTranslate(VG, -translateX, -translateY);

        content.run();

        nvgRestore(VG);
    }

    public static void rectStroke(final float x, final float y, final float width, final float height,
            final float strokeThickness, final int color, final int strokeColor) {
        rect(x - strokeThickness, y - strokeThickness, width + (strokeThickness * 2), height + (strokeThickness * 2),
                strokeColor);
        rect(x, y, width, height, color);
    }

    public static void rectGlow(final float x, final float y, final float width, final float height, final float blur,
            final int color) {
        roundedRectGlow(x, y, width, height, 0, blur, color);
    }

    public static void rectGradientGlow(final float x, final float y, final float width, final float height,
            final float blur, final int color1, final int color2) {
        roundedRectGradientGlow(x, y, width, height, 0, blur, color1, color2);
    }

    public static void rotate(final double degrees, final float x, final float y, final float width, final float height,
            final Runnable content) {
        final float translateX = x + width / 2f;
        final float translateY = y + height / 2f;

        nvgSave(VG);
        nvgTranslate(VG, translateX, translateY);
        nvgRotate(VG, (float) Math.toRadians(degrees));

        content.run();

        nvgRestore(VG);
    }

    public static void rectOutlineStroke(final float x, final float y, final float width, final float height,
            final float outlineThickness, final float strokeThickness, final int outlineColor, final int strokeColor) {
        rectOutline(x - outlineThickness, y - outlineThickness, width + (outlineThickness * 2),
                height + (outlineThickness * 2), strokeThickness, strokeColor);
        rectOutline(x, y, width, height, outlineThickness, outlineColor);
    }

    public static void rectOutline(final float x, final float y, final float width, final float height,
            final float thickness, final int color) {
        applyColor(color, NVG_COLOR_1);

        nvgBeginPath(VG);
        nvgStrokeColor(VG, NVG_COLOR_1);
        nvgStrokeWidth(VG, thickness);
        float ht = thickness * 0.5f;
        nvgRect(VG, x + ht, y + ht, width - thickness, height - thickness);
        nvgStroke(VG);
        nvgClosePath(VG);
    }

    public static void rainbowRect(final float x, final float y, final float width, final float height) {
        rainbowRect(x, y, width, height, 0.0f);
    }

    public static void rainbowRect(final float x, final float y, final float width, final float height, final float radius) {
        final int SEGMENTS = 12;
        final float segHeight = height / SEGMENTS;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            NVGColor c1 = NVGColor.malloc(stack);
            NVGColor c2 = NVGColor.malloc(stack);
            NVGPaint paint = NVGPaint.malloc(stack);

            for (int i = 0; i < SEGMENTS; i++) {
                float hue1 = (float) i / SEGMENTS;
                float hue2 = (float) (i + 1) / SEGMENTS;
                int rgb1 = Color.HSBtoRGB(hue1, 1, 1);
                int rgb2 = Color.HSBtoRGB(hue2, 1, 1);

                applyColor(rgb1 | 0xFF000000, c1);
                applyColor(rgb2 | 0xFF000000, c2);

                float sy = y + i * segHeight;
                nvgLinearGradient(VG, x, sy, x, sy + segHeight, c1, c2, paint);

                nvgBeginPath(VG);
                nvgFillPaint(VG, paint);
                if (radius > 0.0f) {
                    float rtl = (i == 0) ? radius : 0.0f;
                    float rtr = (i == 0) ? radius : 0.0f;
                    float rbr = (i == SEGMENTS - 1) ? radius : 0.0f;
                    float rbl = (i == SEGMENTS - 1) ? radius : 0.0f;
                    nvgRoundedRectVarying(VG, x, sy, width, segHeight + 0.5f, rtl, rtr, rbr, rbl);
                } else {
                    nvgRect(VG, x, sy, width, segHeight + 0.5f);
                }
                nvgFill(VG);
                nvgClosePath(VG);
            }
        }
    }

    public static void roundedRectOutline(final float x, final float y, final float width, final float height,
            final float radius, final float thickness, final int color) {
        applyColor(color, NVG_COLOR_1);

        nvgBeginPath(VG);
        nvgStrokeColor(VG, NVG_COLOR_1);
        nvgStrokeWidth(VG, thickness);
        nvgRoundedRect(VG, x, y, width, height, radius);
        nvgStroke(VG);
        nvgClosePath(VG);
    }

    public static void roundedRectOutlineGradient(final float x, final float y, final float width, final float height,
            final float radius, final float thickness, final int color1, final int color2, final float angleDegrees) {
        applyColor(color1, NVG_COLOR_1);
        applyColor(color2, NVG_COLOR_2);

        final float angleRadians = (float) Math.toRadians(angleDegrees);
        final float dx = (float) Math.cos(angleRadians);
        final float dy = (float) Math.sin(angleRadians);

        final float centerX = x + width * 0.5f;
        final float centerY = y + height * 0.5f;

        float endX = centerX + dx * width * 0.5f;
        float endY = centerY + dy * height * 0.5f;

        if (((color2 >> 24) & 0xFF) == 0) {
            endX = centerX;
            endY = centerY;
        }

        nvgLinearGradient(
                VG,
                centerX - dx * width * 0.5f,
                centerY - dy * height * 0.5f,
                endX,
                endY,
                NVG_COLOR_1,
                NVG_COLOR_2,
                NVG_PAINT);

        nvgBeginPath(VG);
        nvgStrokePaint(VG, NVG_PAINT);
        nvgStrokeWidth(VG, thickness);
        nvgRoundedRect(VG, x, y, width, height, radius);
        nvgStroke(VG);
        nvgClosePath(VG);
    }

    private static final List<ScreenPosition> scissors = new ArrayList<>();

    public static void scissor(final float x, final float y, final float width, final float height,
            final Runnable content) {
        ScreenPosition scissor = new ScreenPosition(x, y, width, height);
        scissors.add(scissor);

        nvgIntersectScissor(VG, x, y, width, height);
        content.run();
        nvgResetScissor(VG);

        scissors.remove(scissor);
        useCurrentScissors();
    }

    private static void useCurrentScissors() {
        for (ScreenPosition scissor : scissors) {
            nvgIntersectScissor(VG, scissor.getX(), scissor.getY(), scissor.getWidth(), scissor.getHeight());
        }
    }

    public static void roundedRect(final float x, final float y, final float width, final float height,
            final float radius, final int color) {
        applyColor(color, NVG_COLOR_1);

        nvgBeginPath(VG);
        nvgFillColor(VG, NVG_COLOR_1);
        nvgRoundedRect(VG, x, y, width, height, radius);
        nvgFill(VG);
        nvgClosePath(VG);
    }

    public static void roundedRect(final float x, final float y, final float width, final float height,
            final float radius, final int color, final float blur) {
        if (blur > 0) {
            roundedRectGlow(x, y, width, height, radius, blur, color);
        }
        roundedRect(x, y, width, height, radius, color);
    }

    public static void roundedRectGlow(final float x, final float y, final float width, final float height,
            final float radius, final float blur, final int color) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            NVGColor color1 = NVGColor.malloc(stack);
            NVGColor color2 = NVGColor.malloc(stack);
            NVGPaint paint = NVGPaint.malloc(stack);

            applyColor(color, color1);
            applyColor(ColorUtility.applyOpacity(color, 0), color2);

            nvgBoxGradient(VG, x, y, width, height, radius, blur, color1, color2, paint);

            nvgBeginPath(VG);
            nvgFillPaint(VG, paint);
            nvgRoundedRect(VG, x - blur, y - blur, width + blur * 2, height + blur * 2, radius + blur);
            nvgFill(VG);
            nvgClosePath(VG);
        }
    }

    public static void roundedRectGradientGlow(final float x, final float y, final float width, final float height,
            final float radius, final float blur, final int color1, final int color2) {
        int midColor = ColorUtility.interpolateColors(color1, color2, 0.5f);
        roundedRectGlow(x, y, width, height, radius, blur, midColor);
    }

    public static void roundedRectGradient(final float x, final float y, final float width, final float height,
            final float radius, final int color1, final int color2, final float angleDegrees) {
        applyColor(color1, NVG_COLOR_1);
        applyColor(color2, NVG_COLOR_2);

        final float angleRadians = (float) Math.toRadians(angleDegrees);
        final float dx = (float) Math.cos(angleRadians);
        final float dy = (float) Math.sin(angleRadians);

        nvgLinearGradient(
                VG,
                x + width * 0.5f - dx * width * 0.5f,
                y + height * 0.5f - dy * height * 0.5f,
                x + width * 0.5f + dx * width * 0.5f,
                y + height * 0.5f + dy * height * 0.5f,
                NVG_COLOR_1,
                NVG_COLOR_2,
                NVG_PAINT);

        nvgBeginPath(VG);
        nvgFillPaint(VG, NVG_PAINT);
        nvgRoundedRect(VG, x, y, width, height, radius);
        nvgFill(VG);
        nvgClosePath(VG);
    }

    public static void roundedRectGradient(final float x, final float y, final float width, final float height,
            final float radius, final int color1, final int color2, final float angleDegrees, final float blur) {
        if (blur > 0) {
            roundedRectGradientGlow(x, y, width, height, radius, blur, color1, color2);
        }
        roundedRectGradient(x, y, width, height, radius, color1, color2, angleDegrees);
    }

    public static void rectGradient(final float x, final float y, final float width, final float height,
            final int color1, final int color2, final float angleDegrees) {
        applyColor(color1, NVG_COLOR_1);
        applyColor(color2, NVG_COLOR_2);

        final float angleRadians = (float) Math.toRadians(angleDegrees);
        final float dx = (float) Math.cos(angleRadians);
        final float dy = (float) Math.sin(angleRadians);

        nvgLinearGradient(
                VG,
                x + width * 0.5f - dx * width * 0.5f,
                y + height * 0.5f - dy * height * 0.5f,
                x + width * 0.5f + dx * width * 0.5f,
                y + height * 0.5f + dy * height * 0.5f,
                NVG_COLOR_1,
                NVG_COLOR_2,
                NVG_PAINT);

        nvgBeginPath(VG);
        nvgFillPaint(VG, NVG_PAINT);
        nvgRect(VG, x, y, width, height);
        nvgFill(VG);
        nvgClosePath(VG);
    }

    public static void rectGradient(final float x, final float y, final float width, final float height,
            final int color1, final int color2, final float angleDegrees, final float blur) {
        if (blur > 0) {
            rectGradientGlow(x, y, width, height, blur, color1, color2);
        }
        rectGradient(x, y, width, height, color1, color2, angleDegrees);
    }

    public static void roundedRect(final float x, final float y, final float width, final float height,
            final float radius, final NVGPaint nvgPaint) {
        nvgBeginPath(VG);
        nvgFillPaint(VG, nvgPaint);
        nvgRoundedRect(VG, x, y, width, height, radius);
        nvgFill(VG);
        nvgClosePath(VG);
    }

    public static void roundedRectVarying(final float x, final float y, final float width, final float height,
            final float radiusTopLeft, final float radiusTopRight, final float radiusBottomRight,
            final float radiusBottomLeft, final int color) {
        applyColor(color, NVG_COLOR_1);

        nvgBeginPath(VG);
        nvgFillColor(VG, NVG_COLOR_1);
        nvgRoundedRectVarying(VG, x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft);
        nvgFill(VG);
        nvgClosePath(VG);
    }

    public static void roundedRectVaryingGradient(final float x, final float y, final float width, final float height,
            final float radiusTopLeft, final float radiusTopRight, final float radiusBottomRight,
            final float radiusBottomLeft, final int color1, final int color2, final float angleDegrees) {
        applyColor(color1, NVG_COLOR_1);
        applyColor(color2, NVG_COLOR_2);

        final float angleRadians = (float) Math.toRadians(angleDegrees);
        final float dx = (float) Math.cos(angleRadians);
        final float dy = (float) Math.sin(angleRadians);

        nvgLinearGradient(
                VG,
                x + width * 0.5f - dx * width * 0.5f,
                y + height * 0.5f - dy * height * 0.5f,
                x + width * 0.5f + dx * width * 0.5f,
                y + height * 0.5f + dy * height * 0.5f,
                NVG_COLOR_1,
                NVG_COLOR_2,
                NVG_PAINT);

        nvgBeginPath(VG);
        nvgFillPaint(VG, NVG_PAINT);
        nvgRoundedRectVarying(VG, x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft);
        nvgFill(VG);
        nvgClosePath(VG);
    }

    public static void roundedRectVarying(final float x, final float y, final float width, final float height,
            final float radiusTopLeft, final float radiusTopRight, final float radiusBottomRight,
            final float radiusBottomLeft, final NVGPaint nvgPaint) {
        nvgBeginPath(VG);
        nvgFillPaint(VG, nvgPaint);
        nvgRoundedRectVarying(VG, x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft);
        nvgFill(VG);
        nvgClosePath(VG);
    }

    public static void applyColor(final int color, final NVGColor nvgColor) {
        nvgRGBAf(
                ((color >> 16) & 0xFF) / 255f,
                ((color >> 8) & 0xFF) / 255f,
                (color & 0xFF) / 255f,
                ((color >> 24) & 0xFF) / 255f,
                nvgColor);
    }

    /** Legacy no-op — consumer code that was ported from NVGColor to int incorrectly. */
    public static void applyColor(final int color, final int ignored) {
        // no-op: kept for backward compatibility with code that passes int as second arg
    }

    /** Legacy overload for consumer code that passes Object-typed variables (was NVGColor). */
    public static void applyColor(final int color, final Object target) {
        if (target instanceof NVGColor nvgColor) {
            applyColor(color, nvgColor);
        }
        // else no-op for int/Object compat
    }

    public static void createNVGPaintFromTex(final int width, final int height, final int glTex,
            final NVGPaint nvgPaint) {
        final int imageHandle = nvglCreateImageFromHandle(VG, glTex, width, height,
                NVG_IMAGE_GENERATE_MIPMAPS | NVG_IMAGE_FLIPY);

        nvgImagePattern(VG, 0, 0, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), 0, imageHandle,
                NVG_IMAGE_GENERATE_MIPMAPS, nvgPaint);
    }

    public static long getContext() {
        ensureInitialized();
        return VG;
    }

    // ═══════════════════════════════════════════════════════════════
    //  NanoVG pass-through wrappers for consumer code compatibility
    //  (consumer files that imported these via `static NVGRenderer.*`)
    // ═══════════════════════════════════════════════════════════════

    public static final int NVG_ROUND = 1;
    public static final int NVG_BUTT = 0;
    public static final int NVG_MITER = 0;
    public static final int NVG_BEVEL = 3;
    public static final int NVG_SQUARE = 2;
    public static final int NVG_CW = org.lwjgl.nanovg.NanoVG.NVG_CW;
    public static final int NVG_CCW = org.lwjgl.nanovg.NanoVG.NVG_CCW;

    // Transform helpers
    public static void nvgSave(long vg) { org.lwjgl.nanovg.NanoVG.nvgSave(vg); }
    public static void nvgRestore(long vg) { org.lwjgl.nanovg.NanoVG.nvgRestore(vg); }
    public static void nvgTranslate(long vg, float x, float y) { org.lwjgl.nanovg.NanoVG.nvgTranslate(vg, x, y); }
    public static void nvgScale(long vg, float sx, float sy) { org.lwjgl.nanovg.NanoVG.nvgScale(vg, sx, sy); }
    public static void nvgRotate(long vg, float angle) { org.lwjgl.nanovg.NanoVG.nvgRotate(vg, angle); }
    public static void nvgGlobalAlpha(long vg, float a) { org.lwjgl.nanovg.NanoVG.nvgGlobalAlpha(vg, a); }

    // Path drawing
    public static void nvgBeginPath(long vg) { org.lwjgl.nanovg.NanoVG.nvgBeginPath(vg); }
    public static void nvgClosePath(long vg) { org.lwjgl.nanovg.NanoVG.nvgClosePath(vg); }
    public static void nvgMoveTo(long vg, float x, float y) { org.lwjgl.nanovg.NanoVG.nvgMoveTo(vg, x, y); }
    public static void nvgLineTo(long vg, float x, float y) { org.lwjgl.nanovg.NanoVG.nvgLineTo(vg, x, y); }
    public static void nvgCircle(long vg, float cx, float cy, float r) { org.lwjgl.nanovg.NanoVG.nvgCircle(vg, cx, cy, r); }
    public static void nvgRect(long vg, float x, float y, float w, float h) { org.lwjgl.nanovg.NanoVG.nvgRect(vg, x, y, w, h); }
    public static void nvgRoundedRect(long vg, float x, float y, float w, float h, float r) { org.lwjgl.nanovg.NanoVG.nvgRoundedRect(vg, x, y, w, h, r); }
    public static void nvgArc(long vg, float cx, float cy, float r, float a0, float a1, int dir) { org.lwjgl.nanovg.NanoVG.nvgArc(vg, cx, cy, r, a0, a1, dir); }

    // Stroke/Fill
    public static void nvgStrokeWidth(long vg, float w) { org.lwjgl.nanovg.NanoVG.nvgStrokeWidth(vg, w); }
    public static void nvgStrokeColor(long vg, NVGColor color) { org.lwjgl.nanovg.NanoVG.nvgStrokeColor(vg, color); }
    public static void nvgFillColor(long vg, NVGColor color) { org.lwjgl.nanovg.NanoVG.nvgFillColor(vg, color); }
    /** Legacy overload — accepts packed ARGB int instead of NVGColor. */
    public static void nvgFillColor(long vg, int colorArgb) {
        applyColor(colorArgb, NVG_COLOR_1);
        org.lwjgl.nanovg.NanoVG.nvgFillColor(vg, NVG_COLOR_1);
    }
    /** Legacy overload — accepts packed ARGB int instead of NVGColor. */
    public static void nvgStrokeColor(long vg, int colorArgb) {
        applyColor(colorArgb, NVG_COLOR_1);
        org.lwjgl.nanovg.NanoVG.nvgStrokeColor(vg, NVG_COLOR_1);
    }
    public static void nvgStroke(long vg) { org.lwjgl.nanovg.NanoVG.nvgStroke(vg); }
    public static void nvgFill(long vg) { org.lwjgl.nanovg.NanoVG.nvgFill(vg); }
    public static void nvgLineJoin(long vg, int join) { org.lwjgl.nanovg.NanoVG.nvgLineJoin(vg, join); }
    public static void nvgLineCap(long vg, int cap) { org.lwjgl.nanovg.NanoVG.nvgLineCap(vg, cap); }
    public static void nvgStrokePaint(long vg, NVGPaint paint) { org.lwjgl.nanovg.NanoVG.nvgStrokePaint(vg, paint); }
    public static void nvgFillPaint(long vg, NVGPaint paint) { org.lwjgl.nanovg.NanoVG.nvgFillPaint(vg, paint); }
    /** Legacy overload — accepts Object for backward compat with code that stored NVGPaint as Object. */
    public static void nvgStrokePaint(long vg, Object paint) {
        if (paint instanceof NVGPaint p) org.lwjgl.nanovg.NanoVG.nvgStrokePaint(vg, p);
    }
    /** Legacy overload — accepts Object for backward compat with code that stored NVGPaint as Object. */
    public static void nvgFillPaint(long vg, Object paint) {
        if (paint instanceof NVGPaint p) org.lwjgl.nanovg.NanoVG.nvgFillPaint(vg, p);
    }
    /** Legacy overload — accepts int color for nvgFillPaint (draws solid fill). */
    public static void nvgFillPaint(long vg, int colorArgb) {
        applyColor(colorArgb, NVG_COLOR_1);
        org.lwjgl.nanovg.NanoVG.nvgFillColor(vg, NVG_COLOR_1);
    }

    // Gradient
    public static NVGPaint nvgLinearGradient(long vg, float sx, float sy, float ex, float ey, NVGColor startCol, NVGColor endCol, NVGPaint paint) {
        return org.lwjgl.nanovg.NanoVG.nvgLinearGradient(vg, sx, sy, ex, ey, startCol, endCol, paint);
    }
    /** Legacy overload — accepts int colors instead of NVGColor. Returns the paint. */
    public static NVGPaint nvgLinearGradient(long vg, float sx, float sy, float ex, float ey, int startColor, int endColor, NVGPaint paint) {
        applyColor(startColor, NVG_COLOR_1);
        applyColor(endColor, NVG_COLOR_2);
        if (paint == null) paint = NVG_PAINT;
        return org.lwjgl.nanovg.NanoVG.nvgLinearGradient(vg, sx, sy, ex, ey, NVG_COLOR_1, NVG_COLOR_2, paint);
    }

    // fill(int) — legacy convenience
    public static void fill(int color) {
        applyColor(color, NVG_COLOR_1);
        org.lwjgl.nanovg.NanoVG.nvgFillColor(VG, NVG_COLOR_1);
        org.lwjgl.nanovg.NanoVG.nvgFill(VG);
    }

    // Text
    public static void nvgFontFace(long vg, String name) { org.lwjgl.nanovg.NanoVG.nvgFontFace(vg, name); }
    public static void nvgFontSize(long vg, float size) { org.lwjgl.nanovg.NanoVG.nvgFontSize(vg, size); }
    public static void nvgTextAlign(long vg, int align) { org.lwjgl.nanovg.NanoVG.nvgTextAlign(vg, align); }
    public static void nvgFontBlur(long vg, float blur) { org.lwjgl.nanovg.NanoVG.nvgFontBlur(vg, blur); }
    public static void nvgText(long vg, float x, float y, String text) { org.lwjgl.nanovg.NanoVG.nvgText(vg, x, y, text); }
    public static void nvgTextLetterSpacing(long vg, float spacing) { org.lwjgl.nanovg.NanoVG.nvgTextLetterSpacing(vg, spacing); }
    public static void nvgTextBounds(long vg, float x, float y, String text, float[] bounds) {
        if (bounds != null && bounds.length >= 4) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                java.nio.FloatBuffer buf = stack.mallocFloat(4);
                org.lwjgl.nanovg.NanoVG.nvgTextBounds(vg, x, y, text, buf);
                bounds[0] = buf.get(0);
                bounds[1] = buf.get(1);
                bounds[2] = buf.get(2);
                bounds[3] = buf.get(3);
            }
        }
    }

    // Image
    public static void nvgImagePattern(long vg, float x, float y, float w, float h, float angle, int image, float alpha, NVGPaint paint) {
        org.lwjgl.nanovg.NanoVG.nvgImagePattern(vg, x, y, w, h, angle, image, alpha, paint);
    }
}

