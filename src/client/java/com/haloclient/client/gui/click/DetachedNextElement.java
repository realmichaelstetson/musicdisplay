package com.haloclient.client.gui.click;

import com.haloclient.client.render.*;
import com.haloclient.client.render.animation.Animation;
import com.haloclient.client.render.animation.Easing;
import com.haloclient.client.render.font.HaloFontRenderState;
import com.haloclient.client.render.font.MsdfFont;
import com.haloclient.client.render.font.MsdfFontManager;
import com.haloclient.client.render.renderstates.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.util.ARGB;
import org.joml.Matrix3x2f;

public final class DetachedNextElement {

    public static final float WIDTH = 175.0f;
    public static final float HEIGHT = 22.0f;
    private static final float PADDING = 6.0f;

    public static float relativeX = Float.NaN;
    public static float relativeY = Float.NaN;
    private static float targetUserScale = 1.0f;
    private static boolean dragging = false;
    private static float dragOffsetX = 0.0f;
    private static float dragOffsetY = 0.0f;

    private static final Animation userScaleAnimation = new Animation(Easing.EASE_OUT_CUBIC, 150);
    static final Animation posXAnimation = new Animation(Easing.EASE_OUT_CUBIC, 180);
    static final Animation posYAnimation = new Animation(Easing.EASE_OUT_CUBIC, 180);
    private static final Animation scaleAnimation = new Animation(Easing.EASE_OUT_CUBIC, 200);

    static {
        userScaleAnimation.setStartValue(1.0f);
        scaleAnimation.setStartValue(0.0f);
    }

    private DetachedNextElement() {
    }

    public static void render(GuiGraphicsExtractor graphics) {
        if (!MusicDisplayOverlay.isVisible() || !MusicDisplayOverlay.independentNextSong) {
            scaleAnimation.run(0.0f);
            return;
        }

        SpotifyManager.NextTrack nextTrack = SpotifyManager.getNextTrack();
        boolean hasNextSong = SpotifyManager.isConfigured() && MusicDisplayOverlay.isShowNextSong() && nextTrack != null && nextTrack.hasMedia();
        scaleAnimation.run(hasNextSong ? 1.0f : 0.0f);
        float scale = scaleAnimation.getValue();

        if (scale <= 0.001f) {
            return;
        }

        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();

        float x = resolveX(screenWidth);
        float y = resolveY(screenHeight);

        float centerX = x + WIDTH / 2.0f;
        float centerY = y + HEIGHT / 2.0f;

        var textureSetup = CaptureManager.getCaptureTextureSetup();
        userScaleAnimation.run(dragging ? targetUserScale - 0.01f : targetUserScale);
        float totalScale = scale * userScaleAnimation.getValue();

        var pose = new Matrix3x2f(graphics.pose());
        pose.translate(centerX, centerY)
            .scale(totalScale, totalScale);

        float localX = -WIDTH / 2.0f;
        float localY = -HEIGHT / 2.0f;
        var parentScissor = graphics.scissorStack.peek();

        int r = (MusicDisplayOverlay.backgroundColor >> 16) & 0xFF;
        int g = (MusicDisplayOverlay.backgroundColor >> 8) & 0xFF;
        int b = MusicDisplayOverlay.backgroundColor & 0xFF;
        int maxAlpha = (MusicDisplayOverlay.backgroundColor >> 24) & 0xFF;
        int dynamicAlpha = (int) (maxAlpha * scale);

        if (MusicDisplayOverlay.getBackgroundType() == MusicDisplayOverlay.BackgroundType.LIQUID_GLASS) {
            graphics.guiRenderState.addGuiElement(new BlurredLiquidGlassRoundedRectangleRenderState(
                    HaloRenderPipelines.LIQUID_GLASS,
                    textureSetup,
                    pose,
                    localX, localY, WIDTH, HEIGHT,
                    ARGB.color(dynamicAlpha, r, g, b),
                    6.0f,
                    MusicDisplayOverlay.getBlurStrength(),
                    MusicDisplayOverlay.getBloomStrength(),
                    parentScissor
            ));
        } else {
            graphics.guiRenderState.addGuiElement(new BlurredRoundedRectangleRenderState(
                    HaloRenderPipelines.ROUNDED_BLUR,
                    textureSetup,
                    pose,
                    localX, localY, WIDTH, HEIGHT,
                    ARGB.color(dynamicAlpha, r, g, b),
                    6.0f,
                    MusicDisplayOverlay.getBlurStrength(),
                    MusicDisplayOverlay.getBloomStrength(),
                    parentScissor
            ));
        }

        ScreenRectangle cardBounds = (new ScreenRectangle((int) localX, (int) localY, (int) WIDTH, (int) HEIGHT)).transformMaxBounds(pose);
        ScreenRectangle scissor = parentScissor != null ? parentScissor.intersection(cardBounds) : cardBounds;

        float nextArtSize = 16.0f;
        float nextArtX = localX + PADDING;
        float nextArtY = localY + (HEIGHT - nextArtSize) / 2.0f;

        ImageManager.CachedImage nextAlbumArt = MusicDisplayOverlay.getAlbumArt(nextTrack.artworkPath());
        if (nextAlbumArt != null) {
            graphics.guiRenderState.addGuiElement(new ImageRenderState(
                    HaloRenderPipelines.IMAGE,
                    nextAlbumArt.textureSetup(),
                    pose,
                    nextArtX, nextArtY, nextArtSize, nextArtSize,
                    ARGB.color((int) (255 * scale), 255, 255, 255),
                    2.0f,
                    ImageRenderState.ScaleMode.FILL,
                    nextAlbumArt.width(),
                    nextAlbumArt.height(),
                    scissor
            ));
        } else {
            graphics.guiRenderState.addGuiElement(new RoundedRectangleRenderState(
                    HaloRenderPipelines.ROUNDED_RECT,
                    pose,
                    nextArtX, nextArtY, nextArtSize, nextArtSize,
                    ARGB.color((int) (55 * scale), 255, 255, 255),
                    2.0f,
                    scissor
            ));
        }

        String nextText = nextTrack.artist() + " - " + nextTrack.title();
        MsdfFont msdfArtistFont = MsdfFontManager.getFont("inter-semibold", 9.0f);
        if (msdfArtistFont != null) {
            String trimmedNext = MusicDisplayOverlay.trimToWidthMsdf(msdfArtistFont, nextText, 142.0f, 6.0f);
            int textAlpha = (int) (((MusicDisplayOverlay.artistColor >> 24) & 0xFF) * scale);
            graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                    msdfArtistFont,
                    trimmedNext,
                    pose,
                    nextArtX + nextArtSize + 5.0f,
                    localY + HEIGHT / 2.0f - msdfArtistFont.getHeight(6.0f) / 2.0f,
                    6.0f,
                    ARGB.color(textAlpha, (MusicDisplayOverlay.artistColor >> 16) & 0xFF, (MusicDisplayOverlay.artistColor >> 8) & 0xFF, MusicDisplayOverlay.artistColor & 0xFF),
                    scissor
            ));
        }

        if (dragging) {
            float screenCenterX = screenWidth / 2.0f;
            float screenCenterY = screenHeight / 2.0f;
            var screenPose = new Matrix3x2f(graphics.pose());

            boolean isSnappedX = (relativeX == 0.5f);
            boolean isSnappedY = (relativeY == 0.5f);
            int vertLineCol = isSnappedX ? ARGB.color(180, 29, 185, 84) : ARGB.color(100, 255, 255, 255);
            int horizLineCol = isSnappedY ? ARGB.color(180, 29, 185, 84) : ARGB.color(100, 255, 255, 255);

            // Vertical center line
            graphics.guiRenderState.addGuiElement(new RoundedRectangleRenderState(
                    HaloRenderPipelines.ROUNDED_RECT,
                    screenPose,
                    screenCenterX - 0.5f, 0.0f, 1.0f, screenHeight,
                    vertLineCol,
                    0.0f,
                    parentScissor
            ));

            // Horizontal center line
            graphics.guiRenderState.addGuiElement(new RoundedRectangleRenderState(
                    HaloRenderPipelines.ROUNDED_RECT,
                    screenPose,
                    0.0f, screenCenterY - 0.5f, screenWidth, 1.0f,
                    horizLineCol,
                    0.0f,
                    parentScissor
            ));
        }
    }

    public static float resolveX(int screenWidth) {
        float target = Float.isNaN(relativeX) ? (screenWidth - WIDTH) / 2.0f : relativeX * (screenWidth - WIDTH);
        if (dragging) {
            posXAnimation.setValue(target);
            posXAnimation.setStartValue(target);
            return target;
        }
        if (posXAnimation.getValue() == 0.0f && posXAnimation.getStartValue() == 0.0f) {
            posXAnimation.setStartValue(target);
        }
        posXAnimation.run(target);
        return posXAnimation.getValue();
    }

    public static float resolveY(int screenHeight) {
        float target = Float.isNaN(relativeY) ? 20.0f + 44.0f + 4.0f : relativeY * (screenHeight - HEIGHT);
        if (dragging) {
            posYAnimation.setValue(target);
            posYAnimation.setStartValue(target);
            return target;
        }
        if (posYAnimation.getValue() == 0.0f && posYAnimation.getStartValue() == 0.0f) {
            posYAnimation.setStartValue(target);
        }
        posYAnimation.run(target);
        return posYAnimation.getValue();
    }

    public static boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (!MusicDisplayOverlay.isVisible() || !MusicDisplayOverlay.independentNextSong) return false;

        boolean chatOpen = Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.ChatScreen || Minecraft.getInstance().screen instanceof ClickGUI;
        if (!chatOpen) return false;

        float currentHeight = HEIGHT;
        float x = resolveX(Minecraft.getInstance().getWindow().getGuiScaledWidth());
        float y = resolveY(Minecraft.getInstance().getWindow().getGuiScaledHeight());

        float scale = scaleAnimation.getValue();
        float currentScale = userScaleAnimation.getValue();
        float totalScale = scale * currentScale;

        float pivotX = x + WIDTH / 2.0f;
        float pivotY = y + currentHeight / 2.0f;
        float localMX = (float) ((mouseX - pivotX) / totalScale + WIDTH / 2.0f);
        float localMY = (float) ((mouseY - pivotY) / totalScale + currentHeight / 2.0f);

        SpotifyManager.NextTrack nextTrack = SpotifyManager.getNextTrack();
        boolean hasNextSong = SpotifyManager.isConfigured() && MusicDisplayOverlay.isShowNextSong() && nextTrack != null && nextTrack.hasMedia();
        boolean hitNextSongCard = hasNextSong && scaleAnimation.getValue() > 0.001f &&
                localMX >= 0 && localMX <= WIDTH && localMY >= 0 && localMY <= HEIGHT;

        if (hitNextSongCard) {
            if (button == 0) {
                dragging = true;
                dragOffsetX = (float) (mouseX - x);
                dragOffsetY = (float) (mouseY - y);
                return true;
            }
        }
        return false;
    }

    public static boolean onMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!MusicDisplayOverlay.isVisible() || !MusicDisplayOverlay.independentNextSong) return false;

        if (dragging && button == 0) {
            int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
            int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();

            float targetX = (float) (mouseX - dragOffsetX);
            float targetY = (float) (mouseY - dragOffsetY);

            float currentScale = userScaleAnimation.getValue();
            float currentW = WIDTH * currentScale;
            float currentH = HEIGHT * currentScale;

            float minX = (currentW - WIDTH) / 2.0f;
            float maxX = screenWidth - (WIDTH + currentW) / 2.0f;
            float minY = (currentH - HEIGHT) / 2.0f;
            float maxY = screenHeight - (HEIGHT + currentH) / 2.0f;

            if (minX > maxX) {
                targetX = (screenWidth - WIDTH) / 2.0f;
            } else {
                targetX = Math.max(minX, Math.min(maxX, targetX));
            }

            if (minY > maxY) {
                targetY = (screenHeight - HEIGHT) / 2.0f;
            } else {
                targetY = Math.max(minY, Math.min(maxY, targetY));
            }

            float snapThreshold = 10.0f;
            float centerX = screenWidth / 2.0f;
            float centerY = screenHeight / 2.0f;
            float overlayCenterX = targetX + WIDTH / 2.0f;
            float overlayCenterY = targetY + HEIGHT / 2.0f;

            boolean snappedX = false;
            if (Math.abs(overlayCenterX - centerX) < snapThreshold) {
                targetX = centerX - WIDTH / 2.0f;
                snappedX = true;
            }

            boolean snappedY = false;
            if (Math.abs(overlayCenterY - centerY) < snapThreshold) {
                targetY = centerY - HEIGHT / 2.0f;
                snappedY = true;
            }

            relativeX = snappedX ? 0.5f : (screenWidth > WIDTH ? targetX / (screenWidth - WIDTH) : 0.5f);
            relativeY = snappedY ? 0.5f : (screenHeight > HEIGHT ? targetY / (screenHeight - HEIGHT) : 0.05f);
            return true;
        }
        return false;
    }

    public static boolean onMouseReleased(double mouseX, double mouseY, int button) {
        if (dragging && button == 0) {
            dragging = false;
            return true;
        }
        return false;
    }

    public static boolean onMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!MusicDisplayOverlay.isVisible() || !MusicDisplayOverlay.independentNextSong) return false;

        float x = resolveX(Minecraft.getInstance().getWindow().getGuiScaledWidth());
        float y = resolveY(Minecraft.getInstance().getWindow().getGuiScaledHeight());

        float nextScale = scaleAnimation.getValue() * userScaleAnimation.getValue();
        float nextW = WIDTH * nextScale;
        float nextH = HEIGHT * nextScale;
        float nextStartX = x + (WIDTH - nextW) / 2.0f;
        float nextStartY = y + (HEIGHT - nextH) / 2.0f;

        if (mouseX >= nextStartX && mouseX <= nextStartX + nextW && mouseY >= nextStartY && mouseY <= nextStartY + nextH) {
            targetUserScale = Math.max(0.4f, Math.min(2.5f, targetUserScale + (float) scrollY * 0.05f));
            return true;
        }
        return false;
    }
}
