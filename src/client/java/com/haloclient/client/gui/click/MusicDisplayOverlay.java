package com.haloclient.client.gui.click;

import com.haloclient.client.gui.click.elements.SliderElement;
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
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.joml.Matrix3x2f;

/**
 * Samodzielny widget HUD (music display) — odseparowany od ekranu ClickGUI.
 */
public final class MusicDisplayOverlay {

    public static final float WIDTH = 175;
    public static final float HEIGHT = 44;

    private static final float PADDING = 6.0f;
    private static final float COVER_SIZE = 32.0f;
    private static final float TEXT_SIZE = 9.0f;
    private static final float ARTISTTEXT_SIZE = 7.0f;
    private static final float TITLE_SIZE = 9.0f;
    private static final float TIME_SIZE = 7.0f;
    private static final float PROGRESS_HEIGHT = 3.0f;
    private static final float DEFAULT_BLUR = 15.0f;
    private static final float DEFAULT_BLOOM = 3.0f;

    private static float relativeX = Float.NaN;
    private static float relativeY = Float.NaN;
    private static boolean visible;
    public static int backgroundColor = ARGB.color(100, 12, 12, 12);
    public static int titleColor = ARGB.color(255, 255, 255, 255);
    public static int artistColor = ARGB.color(145, 255, 255, 255);
    public static int timeColor = ARGB.color(145, 255, 255, 255);
    public static int progressColor = ARGB.color(220, 255, 255, 255);
    private static float targetUserScale = 1.0f;
    private static final Animation userScaleAnimation = new Animation(Easing.EASE_OUT_CUBIC, 150);
    private static final Animation posXAnimation = new Animation(Easing.EASE_OUT_CUBIC, 180);
    private static final Animation posYAnimation = new Animation(Easing.EASE_OUT_CUBIC, 180);
    private static boolean showControls = false;
    private static boolean showNextSong = true;
    public enum BackgroundType {
        GAUSSIAN("Gaussian"),
        LIQUID_GLASS("Liquid Glass");

        private final String name;
        BackgroundType(String name) { this.name = name; }
        public String getName() { return name; }
    }

    private static BackgroundType backgroundType = BackgroundType.GAUSSIAN;
    private static float blurStrength = DEFAULT_BLUR;
    private static float bloomStrength = DEFAULT_BLOOM;

    private static final Animation scaleAnimation = new Animation(Easing.EASE_OUT_CUBIC, 250);
    private static final Animation progressAnimation = new Animation(Easing.EASE_OUT_CUBIC, 200);
    private static final Animation heightAnimation = new Animation(Easing.EASE_OUT_CUBIC, 200);
    private static final Animation nextSongScaleAnimation = new Animation(Easing.EASE_OUT_CUBIC, 200);
    private static boolean animatingOut;
    private static boolean dragging = false;
    private static boolean draggingVolume = false;
    private static float dragOffsetX = 0.0f;
    private static float dragOffsetY = 0.0f;

    private static ImageManager.CachedImage spotifyLogoImage;
    private static NVGImageRenderer settingsIcon;
    private static final java.util.Map<String, ImageManager.CachedImage> artCache = new java.util.HashMap<>();
    private static final java.util.Map<String, Long> artLastModified = new java.util.HashMap<>();
    private static MsdfFont msdfTitleFont;
    private static MsdfFont msdfArtistFont;
    private static MsdfFont msdfTimeFont;
    private static MsdfFont fluidFont;
    private static boolean showPaintbrushButton = false;
    private static ClickGUI colorPickerGuiInstance = null;
    private static MsdfFont materialIconsFont;
    private static final Animation paintbrushBtnAnimation = new Animation(Easing.EASE_OUT_CUBIC, 150);
    private static final Animation colorPickerPopupAnimation = new Animation(Easing.EASE_OUT_CUBIC, 150);
    private static boolean settingsPopupOpen = false;
    private static final Animation settingsPopupAnimation = new Animation(Easing.EASE_OUT_CUBIC, 150);
    private static boolean draggingBlur = false;
    private static boolean draggingBloom = false;
    public static boolean independentNextSong = false;
    private static final Animation independentNextSongCheckboxAnimation = new Animation(Easing.EASE_OUT_CUBIC, 150);

    static {
        scaleAnimation.setStartValue(0.0f);
        userScaleAnimation.setStartValue(1.0f);
        heightAnimation.setStartValue(44.0f);
        nextSongScaleAnimation.setStartValue(0.0f);
        paintbrushBtnAnimation.setStartValue(0.0f);
        colorPickerPopupAnimation.setStartValue(0.0f);
        settingsPopupAnimation.setStartValue(0.0f);
        independentNextSongCheckboxAnimation.setStartValue(0.0f);
    }

    private MusicDisplayOverlay() {
    }

    public static boolean isVisible() {
        return visible;
    }

    public static boolean isOpened() {
        return visible && !animatingOut;
    }

    public static void setVisible(boolean value) {
        if (value && !visible) {
            // Opening: scale in
            visible = true;
            animatingOut = false;
            scaleAnimation.setStartValue(scaleAnimation.getValue());
            scaleAnimation.reset();
        } else if (!value && visible) {
            // Closing: start scale out animation
            animatingOut = true;
            scaleAnimation.setStartValue(scaleAnimation.getValue());
            scaleAnimation.reset();
        }
    }

    public static boolean isShowControls() {
        return showControls;
    }

    public static void setShowControls(boolean value) {
        showControls = value;
    }

    public static boolean isShowNextSong() {
        return showNextSong;
    }

    public static void setShowNextSong(boolean value) {
        showNextSong = value;
    }

    public static float getBlurStrength() {
        return blurStrength;
    }

    public static void setBlurStrength(float value) {
        blurStrength = clamp(value, 0.0f, 30.0f);
    }

    public static float getBloomStrength() {
        return bloomStrength;
    }

    public static void setBloomStrength(float value) {
        bloomStrength = clamp(value, 0.0f, 20.0f);
    }

    public static BackgroundType getBackgroundType() {
        return backgroundType;
    }

    public static void setBackgroundType(BackgroundType value) {
        backgroundType = value;
    }

    public static void render(GuiGraphicsExtractor graphics) {
        if (!visible) {
            return;
        }

        // Run scale animation
        scaleAnimation.run(animatingOut ? 0.0f : 1.0f);
        float scale = scaleAnimation.getValue();

        // If scale out animation finished, hide fully
        if (animatingOut && scale <= 0.001f) {
            visible = false;
            animatingOut = false;
            return;
        }
        if (scale <= 0.001f) {
            scale = 0.001f;
        }

        // Run height animation
        heightAnimation.run(showControls ? 58.0f : 44.0f);
        float currentHeight = heightAnimation.getValue();

        boolean chatOpen = Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.ChatScreen || Minecraft.getInstance().screen instanceof com.haloclient.client.gui.click.ClickGUI;
        if (!chatOpen) {
            showPaintbrushButton = false;
            if (colorPickerGuiInstance != null) {
                colorPickerGuiInstance.colorPickerOpen = false;
            }
            settingsPopupOpen = false;
        }
        paintbrushBtnAnimation.run((chatOpen && showPaintbrushButton) ? 1.0f : 0.0f);
        float btnScaleVal = paintbrushBtnAnimation.getValue();
        settingsPopupAnimation.run((chatOpen && showPaintbrushButton && settingsPopupOpen) ? 1.0f : 0.0f);
        float settingsPopupScaleVal = settingsPopupAnimation.getValue();

        float x = resolveX(Minecraft.getInstance().getWindow().getGuiScaledWidth());
        float y = resolveY(Minecraft.getInstance().getWindow().getGuiScaledHeight());
        float nextX = x;
        float nextY = y + currentHeight + 4.0f;

        // Center of the overlay for scale pivot
        float centerX = x + WIDTH / 2.0f;
        float centerY = y + currentHeight / 2.0f;

        var textureSetup = CaptureManager.getCaptureTextureSetup();
        userScaleAnimation.run(dragging ? targetUserScale - 0.01f : targetUserScale);
        float totalScale = scale * userScaleAnimation.getValue();
        // Apply scale transform around the overlay center
        var pose = new Matrix3x2f(graphics.pose());
        pose.translate(centerX, centerY)
            .scale(totalScale, totalScale);

        float localX = -WIDTH / 2.0f;
        float localY = -currentHeight / 2.0f;
        var parentScissor = graphics.scissorStack.peek();

        int r = (backgroundColor >> 16) & 0xFF;
        int g = (backgroundColor >> 8) & 0xFF;
        int b = backgroundColor & 0xFF;
        int maxAlpha = (backgroundColor >> 24) & 0xFF;
        int dynamicAlpha = (int) (maxAlpha * scale);

        if (backgroundType == BackgroundType.LIQUID_GLASS) {
            graphics.guiRenderState.addGuiElement(new BlurredLiquidGlassRoundedRectangleRenderState(
                    HaloRenderPipelines.LIQUID_GLASS,
                    textureSetup,
                    pose,
                    localX, localY, WIDTH, currentHeight,
                    ARGB.color(dynamicAlpha, r, g, b),
                    9.0f,
                    blurStrength,
                    bloomStrength,
                    parentScissor
            ));
        } else {
            graphics.guiRenderState.addGuiElement(new BlurredRoundedRectangleRenderState(
                    HaloRenderPipelines.ROUNDED_BLUR,
                    textureSetup,
                    pose,
                    localX, localY, WIDTH, currentHeight,
                    ARGB.color(dynamicAlpha, r, g, b),
                    9.0f,
                    blurStrength,
                    bloomStrength,
                    parentScissor
            ));
        }

        ScreenRectangle cardBounds = (new ScreenRectangle((int) localX, (int) localY, (int) WIDTH, (int) currentHeight)).transformMaxBounds(pose);
        ScreenRectangle scissor = parentScissor != null ? parentScissor.intersection(cardBounds) : cardBounds;

        loadAssets();
        SpotifyManager.MediaStatus status = SpotifyManager.getStatus();
        boolean isConfigured = SpotifyManager.isConfigured();
        String title = isConfigured ? (status.hasMedia() ? status.title() : "No media playing") : "Spotify not connected";
        String artist = isConfigured ? (status.hasMedia() && !status.artist().isBlank() ? status.artist() : "Spotify API") : "Setup in ClickGUI";
        ImageManager.CachedImage currentAlbumArt = isConfigured ? getAlbumArt(status.artworkPath()) : null;
        float progress = isConfigured ? status.progress() : 0.0f;
        float textX = localX + PADDING + COVER_SIZE + 7.0f;
        float textWidth = WIDTH - textX + localX - PADDING;
        final float drawScale = scale;

        // --- MSDF title rendering (GPU pipeline — always crisp) ---
        if (msdfTitleFont != null) {
            int titleAlpha = (int) (255 * drawScale);
            String trimmedTitle = trimToWidthMsdf(msdfTitleFont, title, textWidth, TITLE_SIZE);
            graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                    msdfTitleFont,
                    trimmedTitle,
                    pose,
                    textX,
                    localY + 7.0f,
                    TITLE_SIZE,
                    ARGB.color((int) (((titleColor >> 24) & 0xFF) * drawScale), (titleColor >> 16) & 0xFF, (titleColor >> 8) & 0xFF, titleColor & 0xFF),
                    scissor
            ));
        }

        // --- MSDF artist rendering (GPU pipeline — always crisp) ---
        if (msdfArtistFont != null) {
            int artA = (artistColor >> 24) & 0xFF;
            int artR = (artistColor >> 16) & 0xFF;
            int artG = (artistColor >> 8) & 0xFF;
            int artB = artistColor & 0xFF;
            int artistAlpha = (int) (artA * drawScale);
            float artistMaxWidth = textWidth;
            if (isConfigured && status.hasMedia()) {
                int posMin = (int) status.positionSeconds() / 60;
                int posSec = (int) status.positionSeconds() % 60;
                int durMin = (int) status.durationSeconds() / 60;
                int durSec = (int) status.durationSeconds() % 60;
                String timeText = String.format("%d:%02d / %d:%02d", posMin, posSec, durMin, durSec);
                float timeWidth = msdfArtistFont.getWidth(timeText, TIME_SIZE);
                artistMaxWidth = textWidth - timeWidth - 8.0f;
            }
            String trimmedArtist = trimToWidthMsdf(msdfArtistFont, artist, artistMaxWidth, ARTISTTEXT_SIZE);
            graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                    msdfArtistFont,
                    trimmedArtist,
                    pose,
                    textX,
                    localY + 18.0f,
                    ARTISTTEXT_SIZE,
                    ARGB.color(artistAlpha, artR, artG, artB),
                    scissor
            ));
        }

        // --- MSDF time rendering (GPU pipeline — always crisp) ---
        if (msdfArtistFont != null && isConfigured && status.hasMedia()) {
            int posMin = (int) status.positionSeconds() / 60;
            int posSec = (int) status.positionSeconds() % 60;
            int durMin = (int) status.durationSeconds() / 60;
            int durSec = (int) status.durationSeconds() % 60;
            String timeText = String.format("%d:%02d / %d:%02d", posMin, posSec, durMin, durSec);
            
            int tA = (timeColor >> 24) & 0xFF;
            int tR = (timeColor >> 16) & 0xFF;
            int tG = (timeColor >> 8) & 0xFF;
            int tB = timeColor & 0xFF;
            int timeAlpha = (int) (tA * drawScale);
            float timeWidth = msdfArtistFont.getWidth(timeText, TIME_SIZE);
            float timeX = textX + textWidth - timeWidth;
            float timeY = localY + 44.0f - PADDING - PROGRESS_HEIGHT - TIME_SIZE - 10.0f;
            
            graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                    msdfArtistFont,
                    timeText,
                    pose,
                    timeX,
                    timeY,
                    TIME_SIZE,
                    ARGB.color(timeAlpha, tR, tG, tB),
                    scissor
            ));
        }

        // --- Progress bar (GPU pipeline with smooth animation) ---
        float progressX = textX;
        float progressY = localY + 44.0f - PADDING - PROGRESS_HEIGHT - 5.0f;

        // Animate progress for smooth expansion
        progressAnimation.run(progress);
        float animatedProgress = progressAnimation.getValue();
        float filledWidth = Math.max(PROGRESS_HEIGHT, textWidth * animatedProgress);

        int bgAlpha = (int) (65 * drawScale);

        // Progress bar background
        graphics.guiRenderState.addGuiElement(new RoundedRectangleRenderState(
                HaloRenderPipelines.ROUNDED_RECT,
                pose,
                progressX, progressY, textWidth, PROGRESS_HEIGHT,
                ARGB.color(bgAlpha, 255, 255, 255),
                1.5f,
                scissor
        ));

        // Progress bar fill
        int pA = (progressColor >> 24) & 0xFF;
        int pR = (progressColor >> 16) & 0xFF;
        int pG = (progressColor >> 8) & 0xFF;
        int pB = progressColor & 0xFF;
        int fillAlpha = (int) (pA * drawScale);

        graphics.guiRenderState.addGuiElement(new RoundedRectangleRenderState(
                HaloRenderPipelines.ROUNDED_RECT,
                pose,
                progressX, progressY, filledWidth, PROGRESS_HEIGHT,
                ARGB.color(fillAlpha, pR, pG, pB),
                1.5f,
                scissor
        ));

        // --- Album art / Spotify logo rendering (GPU pipeline — always crisp) ---
        
        // Background placeholder
        graphics.guiRenderState.addGuiElement(new RoundedRectangleRenderState(
                HaloRenderPipelines.ROUNDED_RECT,
                pose,
                localX + PADDING,
                localY + PADDING,
                COVER_SIZE,
                COVER_SIZE,
                ARGB.color((int) (55 * drawScale), 255, 255, 255),
                6.0f,
                scissor
        ));

        if (currentAlbumArt != null) {
            graphics.guiRenderState.addGuiElement(new ImageRenderState(
                    HaloRenderPipelines.IMAGE,
                    currentAlbumArt.textureSetup(),
                    pose,
                    localX + PADDING,
                    localY + PADDING,
                    COVER_SIZE,
                    COVER_SIZE,
                    ARGB.color((int) (255 * drawScale), 255, 255, 255),
                    6.0f,
                    ImageRenderState.ScaleMode.FILL,
                    currentAlbumArt.width(),
                    currentAlbumArt.height(),
                    scissor
            ));
        } else if (spotifyLogoImage != null) {
            float iconSize = 19.0f;
            float iconX = localX + PADDING + (COVER_SIZE - iconSize) / 2.0f;
            float iconY = localY + PADDING + (COVER_SIZE - iconSize) / 2.0f;
            graphics.guiRenderState.addGuiElement(new ImageRenderState(
                    HaloRenderPipelines.IMAGE,
                    spotifyLogoImage.textureSetup(),
                    pose,
                    iconX,
                    iconY,
                    iconSize,
                    iconSize,
                    ARGB.color((int) (210 * drawScale), 255, 255, 255),
                    0.0f,
                    ImageRenderState.ScaleMode.FIT,
                    spotifyLogoImage.width(),
                    spotifyLogoImage.height(),
                    scissor
            ));
        }

        // --- Render Spotify Controls (GPU MSDF Pipeline) ---
        float controlsAlphaProgress = (heightAnimation.getValue() - 44.0f) / (58.0f - 44.0f);
        if (controlsAlphaProgress > 0.01f && fluidFont != null) {
            int controlAlpha = (int) (255 * drawScale * controlsAlphaProgress);
            
            double rawMouseX = Minecraft.getInstance().mouseHandler.xpos();
            double rawMouseY = Minecraft.getInstance().mouseHandler.ypos();
            double winScale = Minecraft.getInstance().getWindow().getGuiScale();
            double currentGuiMX = rawMouseX / winScale;
            double currentGuiMY = rawMouseY / winScale;
            
            float pivotX = centerX;
            float pivotY = centerY;
            float localMX = (float) ((currentGuiMX - pivotX) / totalScale + WIDTH / 2.0f);
            float localMY = (float) ((currentGuiMY - pivotY) / totalScale + heightAnimation.getValue() / 2.0f);

            // 1. Speaker Icon (E)
            boolean speakerHovered = chatOpen && localMX >= 6.0f && localMX <= 17.0f && localMY >= 40.0f && localMY <= 58.0f;
            int speakerColor = speakerHovered ? ARGB.color(controlAlpha, 255, 255, 255) : ARGB.color((int)(controlAlpha * 0.7f), 255, 255, 255);
            graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                    fluidFont,
                    "E",
                    pose,
                    localX + 8.0f,
                    localY + 40.0f,
                    20.0f,
                    speakerColor,
                    scissor
            ));

            // 2. Volume Slider (starts at x+18, width=30)
            float sliderX = localX + 18.0f;
            float sliderY = localY + 46.5f;
            float sliderW = 30.0f;
            float sliderH = 2.0f;
            float volPct = isConfigured ? (status.volumePercent() / 100.0f) : 0.5f;

            // Slider bg
            graphics.guiRenderState.addGuiElement(new RoundedRectangleRenderState(
                    HaloRenderPipelines.ROUNDED_RECT,
                    pose,
                    sliderX, sliderY, sliderW, sliderH,
                    ARGB.color((int) (65 * drawScale * controlsAlphaProgress), 255, 255, 255),
                    1.0f,
                    scissor
            ));

            // Slider fill
            float fillW = sliderW * volPct;
            graphics.guiRenderState.addGuiElement(new RoundedRectangleRenderState(
                    HaloRenderPipelines.ROUNDED_RECT,
                    pose,
                    sliderX, sliderY, fillW, sliderH,
                    ARGB.color(controlAlpha, 255, 255, 255),
                    1.0f,
                    scissor
            ));

            // Slider knob
            boolean sliderHovered = chatOpen && localMX >= 18.0f && localMX <= 48.0f && localMY >= 40.0f && localMY <= 58.0f;
            float knobRadius = (sliderHovered || draggingVolume) ? 2.5f : 1.5f;
            graphics.guiRenderState.addGuiElement(new RoundedRectangleRenderState(
                    HaloRenderPipelines.ROUNDED_RECT,
                    pose,
                    sliderX + fillW - knobRadius, sliderY + sliderH / 2.0f - knobRadius, knobRadius * 2.0f, knobRadius * 2.0f,
                    ARGB.color(controlAlpha, 255, 255, 255),
                    knobRadius,
                    scissor
            ));

            // 3. Middle playback: Prev (H), Play/Pause (B/A), Next (G)
            float midX = localX + WIDTH / 2.0f + 2;
            
            boolean prevHovered = chatOpen && localMX >= 73.0f - 5 && localMX <= 87.0f- 5 && localMY >= 40.0f && localMY <= 58.0f;
            int prevColor = prevHovered ? ARGB.color(controlAlpha, 255, 255, 255) : ARGB.color((int)(controlAlpha * 0.7f), 255, 255, 255);
            graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                    fluidFont,
                    "H",
                    pose,
                    midX - 16.0f - 1.5f,
                    localY + 41.5f,
                    16.0f,
                    prevColor,
                    scissor
            ));

            boolean playHovered = chatOpen && localMX >= 88.0f- 5 && localMX <= 101.0f- 5 && localMY >= 40.0f && localMY <= 58.0f;
            int playColor = playHovered ? ARGB.color(controlAlpha, 255, 255, 255) : ARGB.color((int)(controlAlpha * 0.7f), 255, 255, 255);
            String playChar = (isConfigured && status.isPlaying()) ? "A" : "B";
            graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                    fluidFont,
                    playChar,
                    pose,
                    midX - 4.5f,
                    localY + 41.5f,
                    16.0f,
                    playColor,
                    scissor
            ));

            boolean nextHovered = chatOpen && localMX >= 102.0f- 5 && localMX <= 116.0f- 5 && localMY >= 40.0f && localMY <= 58.0f;
            int nextColor = nextHovered ? ARGB.color(controlAlpha, 255, 255, 255) : ARGB.color((int)(controlAlpha * 0.7f), 255, 255, 255);
            graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                    fluidFont,
                    "G",
                    pose,
                    midX + 16.0f - 8.5f,
                    localY + 41.5f,
                    16.0f,
                    nextColor,
                    scissor
            ));

            // 4. Right: Loop (I), Shuffle (C) and Like (D)
            boolean loopHovered = chatOpen && localMX >= 134.0f && localMX <= 146.0f && localMY >= 40.0f && localMY <= 58.0f;
            boolean loopActive = isConfigured && status.repeatState() != null && !status.repeatState().equals("off");
            int loopColor = loopActive ? ARGB.color(controlAlpha, 29, 185, 84) : (loopHovered ? ARGB.color(controlAlpha, 255, 255, 255) : ARGB.color((int)(controlAlpha * 0.7f), 255, 255, 255));
            graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                    fluidFont,
                    "I",
                    pose,
                    localX + WIDTH - PADDING - 29.0f,
                    localY + 42f,
                    14.0f,
                    loopColor,
                    scissor
            ));

            boolean shuffleHovered = chatOpen && localMX >= 147.0f && localMX <= 159.0f && localMY >= 40.0f && localMY <= 58.0f;
            boolean shuffleActive = isConfigured && status.shuffleState();
            int shuffleColor = shuffleActive ? ARGB.color(controlAlpha, 29, 185, 84) : (shuffleHovered ? ARGB.color(controlAlpha, 255, 255, 255) : ARGB.color((int)(controlAlpha * 0.7f), 255, 255, 255));
            graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                    fluidFont,
                    "C",
                    pose,
                    localX + WIDTH - PADDING - 18.0f ,
                    localY + 41.5f,
                    16.0f,
                    shuffleColor,
                    scissor
            ));

            boolean likeHovered = chatOpen && localMX >= 160.0f && localMX <= 172.0f && localMY >= 40.0f && localMY <= 58.0f;
            boolean likeActive = isConfigured && status.liked();
            int likeColor = likeActive ? ARGB.color(controlAlpha, 29, 185, 84) : (likeHovered ? ARGB.color(controlAlpha, 255, 255, 255) : ARGB.color((int)(controlAlpha * 0.7f), 255, 255, 255));
            graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                    fluidFont,
                    "D",
                    pose,
                    localX + WIDTH - PADDING - 7f,
                    localY + 41.5f,
                    16.0f,
                    likeColor,
                    scissor
            ));
        }

        // --- Next Song Card Rendering ---
        SpotifyManager.NextTrack nextTrack = SpotifyManager.getNextTrack();
        boolean hasNextSong = isConfigured && showNextSong && nextTrack != null && nextTrack.hasMedia();
        nextSongScaleAnimation.run(hasNextSong ? 1.0f : 0.0f);
        float nextSongScale = nextSongScaleAnimation.getValue();

        if (!independentNextSong && nextSongScale > 0.001f) {
            float nextHeight = 22.0f;
            float nextCenterX = nextX + WIDTH / 2.0f;
            float nextCenterY = nextY + nextHeight / 2.0f;

            var nextPose = new Matrix3x2f(graphics.pose());
            nextPose.translate(centerX, centerY)
                    .scale(totalScale, totalScale)
                    .translate(-centerX, -centerY)
                    .translate(nextCenterX, nextCenterY)
                    .scale(nextSongScale, nextSongScale);

            float nextLocalX = -WIDTH / 2.0f;
            float nextLocalY = -nextHeight / 2.0f;

            if (backgroundType == BackgroundType.LIQUID_GLASS) {
                graphics.guiRenderState.addGuiElement(new BlurredLiquidGlassRoundedRectangleRenderState(
                        HaloRenderPipelines.LIQUID_GLASS,
                        textureSetup,
                        nextPose,
                        nextLocalX, nextLocalY, WIDTH, nextHeight,
                        ARGB.color((int) (maxAlpha * scale * nextSongScale), r, g, b),
                        6.0f,
                        blurStrength,
                        bloomStrength,
                        parentScissor
                ));
            } else {
                graphics.guiRenderState.addGuiElement(new BlurredRoundedRectangleRenderState(
                        HaloRenderPipelines.ROUNDED_BLUR,
                        textureSetup,
                        nextPose,
                        nextLocalX, nextLocalY, WIDTH, nextHeight,
                        ARGB.color((int) (maxAlpha * scale * nextSongScale), r, g, b),
                        6.0f,
                        blurStrength,
                        bloomStrength,
                        parentScissor
                ));
            }



            ScreenRectangle nextBounds = (new ScreenRectangle((int) nextLocalX, (int) nextLocalY, (int) WIDTH, (int) nextHeight)).transformMaxBounds(nextPose);
            ScreenRectangle nextScissor = parentScissor != null ? parentScissor.intersection(nextBounds) : nextBounds;

            float nextArtSize = 16.0f;
            float nextArtX = nextLocalX + PADDING;
            float nextArtY = nextLocalY + (nextHeight - nextArtSize) / 2.0f;

            ImageManager.CachedImage nextAlbumArt = getAlbumArt(nextTrack.artworkPath());
            if (nextAlbumArt != null) {
                graphics.guiRenderState.addGuiElement(new ImageRenderState(
                        HaloRenderPipelines.IMAGE,
                        nextAlbumArt.textureSetup(),
                        nextPose,
                        nextArtX, nextArtY, nextArtSize, nextArtSize,
                        ARGB.color((int) (255 * scale), 255, 255, 255),
                        2.0f,
                        ImageRenderState.ScaleMode.FILL,
                        nextAlbumArt.width(),
                        nextAlbumArt.height(),
                        nextScissor
                ));
            } else {
                graphics.guiRenderState.addGuiElement(new RoundedRectangleRenderState(
                        HaloRenderPipelines.ROUNDED_RECT,
                        nextPose,
                        nextArtX, nextArtY, nextArtSize, nextArtSize,
                        ARGB.color((int) (55 * scale), 255, 255, 255),
                        2.0f,
                        nextScissor
                ));
            }

            String nextText = nextTrack.artist() + " - " + nextTrack.title();
            if (msdfArtistFont != null) {
                String trimmedNext = trimToWidthMsdf(msdfArtistFont, nextText, 142.0f, 6.0f);
                int textAlpha = (int) (((artistColor >> 24) & 0xFF) * scale);
                graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                        msdfArtistFont,
                        trimmedNext,
                        nextPose,
                        nextArtX + nextArtSize + 5.0f,
                        nextLocalY + nextHeight / 2.0f - msdfArtistFont.getHeight(6.0f) / 2.0f,
                        6.0f,
                        ARGB.color(textAlpha, (artistColor >> 16) & 0xFF, (artistColor >> 8) & 0xFF, artistColor & 0xFF),
                        nextScissor
                ));
            }
        }

        if (dragging) {
            int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
            int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
            float screenCenterX = screenWidth / 2.0f;
            float screenCenterY = screenHeight / 2.0f;

            var screenPose = new Matrix3x2f(graphics.pose());

            float overlayCenterX = x + WIDTH / 2.0f;
            float overlayCenterY = y + getTotalHeight() / 2.0f;

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

        ensureColorPickerInitialized();
        colorPickerPopupAnimation.run((chatOpen && showPaintbrushButton && colorPickerGuiInstance.colorPickerOpen) ? 1.0f : 0.0f);
        float popupScaleVal = colorPickerPopupAnimation.getValue();

        int bgR = (backgroundColor >> 16) & 0xFF;
        int bgG = (backgroundColor >> 8) & 0xFF;
        int bgB = backgroundColor & 0xFF;
        int bgA = (backgroundColor >> 24) & 0xFF;

        if (btnScaleVal > 0.001f) {
            float btnX = localX + WIDTH + 6.0f;
            float brushBtnY = localY + (currentHeight - 17.0f) / 2.0f - 11.0f;
            float settingsBtnY = localY + (currentHeight - 17.0f) / 2.0f + 11.0f;
            float btnSize = 17.0f;
            float brushBtnCenterX = btnX + btnSize / 2.0f;
            float brushBtnCenterY = brushBtnY + btnSize / 2.0f;
            float settingsBtnCenterX = btnX + btnSize / 2.0f;
            float settingsBtnCenterY = settingsBtnY + btnSize / 2.0f;
            
            double rawMouseX = Minecraft.getInstance().mouseHandler.xpos();
            double rawMouseY = Minecraft.getInstance().mouseHandler.ypos();
            double winScale = Minecraft.getInstance().getWindow().getGuiScale();
            double currentGuiMX = rawMouseX / winScale;
            double currentGuiMY = rawMouseY / winScale;
            float localMX = (float) ((currentGuiMX - centerX) / totalScale);
            float localMY = (float) ((currentGuiMY - centerY) / totalScale);

            // Paintbrush button drawing
            boolean brushBtnHovered = localMX >= btnX && localMX <= btnX + btnSize && localMY >= brushBtnY && localMY <= brushBtnY + btnSize;
            int brushBtnAlpha = brushBtnHovered ? Math.min(255, bgA + 40) : bgA;
            int brushBtnColor = ARGB.color((int)(brushBtnAlpha * btnScaleVal), bgR, bgG, bgB);

            Matrix3x2f brushBtnPose = new Matrix3x2f(pose);
            brushBtnPose.translate(brushBtnCenterX, brushBtnCenterY)
                       .scale(btnScaleVal, btnScaleVal)
                       .translate(-brushBtnCenterX, -brushBtnCenterY);

            if (backgroundType == BackgroundType.LIQUID_GLASS) {
                graphics.guiRenderState.addGuiElement(new BlurredLiquidGlassRoundedRectangleRenderState(
                        HaloRenderPipelines.LIQUID_GLASS,
                        textureSetup,
                        brushBtnPose,
                        btnX, brushBtnY, btnSize, btnSize,
                        brushBtnColor,
                        4.0f,
                        blurStrength,
                        bloomStrength,
                        parentScissor
                ));
            } else {
                graphics.guiRenderState.addGuiElement(new BlurredRoundedRectangleRenderState(
                        HaloRenderPipelines.ROUNDED_BLUR,
                        textureSetup,
                        brushBtnPose,
                        btnX, brushBtnY, btnSize, btnSize,
                        brushBtnColor,
                        4.0f,
                        blurStrength,
                        bloomStrength,
                        parentScissor
                ));
            }

            if (fluidFont != null) {
                float iconSize = 20.0f;
                float brushW = fluidFont.getWidth("J", iconSize);
                float brushH = fluidFont.getHeight(iconSize);
                graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                        fluidFont,
                        "J",
                        brushBtnPose,
                        btnX + (btnSize - brushW) / 2.0f,
                        brushBtnY + (btnSize - brushH) / 2.0f,
                        iconSize,
                        ARGB.color((int)(255 * btnScaleVal), 255, 255, 255),
                        parentScissor
                ));
            }

            // Settings button drawing
            boolean settingsBtnHovered = localMX >= btnX && localMX <= btnX + btnSize && localMY >= settingsBtnY && localMY <= settingsBtnY + btnSize;
            int settingsBtnAlpha = settingsBtnHovered ? Math.min(255, bgA + 40) : bgA;
            int settingsBtnColor = ARGB.color((int)(settingsBtnAlpha * btnScaleVal), bgR, bgG, bgB);

            Matrix3x2f settingsBtnPose = new Matrix3x2f(pose);
            settingsBtnPose.translate(settingsBtnCenterX, settingsBtnCenterY)
                           .scale(btnScaleVal, btnScaleVal)
                           .translate(-settingsBtnCenterX, -settingsBtnCenterY);

            if (backgroundType == BackgroundType.LIQUID_GLASS) {
                graphics.guiRenderState.addGuiElement(new BlurredLiquidGlassRoundedRectangleRenderState(
                        HaloRenderPipelines.LIQUID_GLASS,
                        textureSetup,
                        settingsBtnPose,
                        btnX, settingsBtnY, btnSize, btnSize,
                        settingsBtnColor,
                        4.0f,
                        blurStrength,
                        bloomStrength,
                        parentScissor
                ));
            } else {
                graphics.guiRenderState.addGuiElement(new BlurredRoundedRectangleRenderState(
                        HaloRenderPipelines.ROUNDED_BLUR,
                        textureSetup,
                        settingsBtnPose,
                        btnX, settingsBtnY, btnSize, btnSize,
                        settingsBtnColor,
                        4.0f,
                        blurStrength,
                        bloomStrength,
                        parentScissor
                ));
            }

            if (fluidFont != null) {
                float iconSize = 18.0f;
                float cogW = fluidFont.getWidth("K", iconSize);
                float cogH = fluidFont.getHeight(iconSize);
                graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                        fluidFont,
                        "K",
                        settingsBtnPose,
                        btnX + (btnSize - cogW) / 2.0f,
                        settingsBtnY + (btnSize - cogH) / 2.0f,
                        iconSize,
                        ARGB.color((int)(255 * btnScaleVal), 255, 255, 255),
                        parentScissor
                ));
            }
        }

        if (popupScaleVal > 0.001f) {
            float popupX = localX + WIDTH + 32.0f;
            float popupY = localY + (currentHeight - 128.0f) / 2.0f;
            float popupCenterX = popupX + 120.0f / 2.0f;
            float popupCenterY = popupY + 128.0f / 2.0f;

            double rawMouseX = Minecraft.getInstance().mouseHandler.xpos();
            double rawMouseY = Minecraft.getInstance().mouseHandler.ypos();
            double winScale = Minecraft.getInstance().getWindow().getGuiScale();
            double currentGuiMX = rawMouseX / winScale;
            double currentGuiMY = rawMouseY / winScale;
            float localMX = (float) ((currentGuiMX - centerX) / totalScale);
            float localMY = (float) ((currentGuiMY - centerY) / totalScale);

            Matrix3x2f popupPose = new Matrix3x2f(pose);
            popupPose.translate(popupCenterX, popupCenterY)
                     .scale(popupScaleVal, popupScaleVal)
                     .translate(-popupCenterX, -popupCenterY);

            int bgCol = ARGB.color((int) (bgA * popupScaleVal), bgR, bgG, bgB);

            if (backgroundType == BackgroundType.LIQUID_GLASS) {
                graphics.guiRenderState.addGuiElement(new BlurredLiquidGlassRoundedRectangleRenderState(
                        HaloRenderPipelines.LIQUID_GLASS,
                        textureSetup,
                        popupPose,
                        popupX, popupY, 120.0f, 128.0f,
                        bgCol,
                        6.0f,
                        blurStrength,
                        bloomStrength,
                        parentScissor
                ));
            } else {
                graphics.guiRenderState.addGuiElement(new BlurredRoundedRectangleRenderState(
                        HaloRenderPipelines.ROUNDED_BLUR,
                        textureSetup,
                        popupPose,
                        popupX, popupY, 120.0f, 128.0f,
                        bgCol,
                        6.0f,
                        blurStrength,
                        bloomStrength,
                        parentScissor
                ));
            }

            colorPickerGuiInstance.selectorCircleAnimation.run(1.0f);
            colorPickerGuiInstance.comboboxAnimation.run(colorPickerGuiInstance.comboboxOpen ? 1.0f : 0.0f);
            
            com.haloclient.client.gui.click.elements.ColorPickerElement.drawColorPickerPopup(
                    graphics,
                    popupPose,
                    textureSetup,
                    parentScissor,
                    popupX,
                    popupY,
                    (int)(255 * scale * popupScaleVal),
                    localMX,
                    localMY,
                    true,
                    colorPickerGuiInstance
            );
        }

        if (settingsPopupScaleVal > 0.001f) {
            boolean showBloom = (backgroundType != BackgroundType.LIQUID_GLASS);
            float popupHeight = showBloom ? 51.0f : 35.0f;
            float popupX = localX + WIDTH + 32.0f;
            float popupY = localY + (currentHeight - popupHeight) / 2.0f;
            float popupCenterX = popupX + 120.0f / 2.0f;
            float popupCenterY = popupY + popupHeight / 2.0f;

            Matrix3x2f popupPose = new Matrix3x2f(pose);
            popupPose.translate(popupCenterX, popupCenterY)
                     .scale(settingsPopupScaleVal, settingsPopupScaleVal)
                     .translate(-popupCenterX, -popupCenterY);

            int bgCol = ARGB.color((int) (bgA * settingsPopupScaleVal), bgR, bgG, bgB);

            if (backgroundType == BackgroundType.LIQUID_GLASS) {
                graphics.guiRenderState.addGuiElement(new BlurredLiquidGlassRoundedRectangleRenderState(
                        HaloRenderPipelines.LIQUID_GLASS,
                        textureSetup,
                        popupPose,
                        popupX, popupY, 110.0f, popupHeight,
                        bgCol,
                        6.0f,
                        blurStrength,
                        bloomStrength,
                        parentScissor
                ));
            } else {
                graphics.guiRenderState.addGuiElement(new BlurredRoundedRectangleRenderState(
                        HaloRenderPipelines.ROUNDED_BLUR,
                        textureSetup,
                        popupPose,
                        popupX, popupY, 110.0f, popupHeight,
                        bgCol,
                        6.0f,
                        blurStrength,
                        bloomStrength,
                        parentScissor
                ));
            }

            float pad = 8.0f;
            float contentX = popupX + pad - 5;
            float contentW = 120.0f - 2 * pad;
            int textAlpha = (int)(255 * scale * settingsPopupScaleVal);

            double rawMouseX = Minecraft.getInstance().mouseHandler.xpos();
            double rawMouseY = Minecraft.getInstance().mouseHandler.ypos();
            double winScale = Minecraft.getInstance().getWindow().getGuiScale();
            double currentGuiMX = rawMouseX / winScale;
            double currentGuiMY = rawMouseY / winScale;
            float localMX = (float) ((currentGuiMX - centerX) / totalScale);
            float localMY = (float) ((currentGuiMY - centerY) / totalScale);

            // Draw Blur Slider
            SliderElement.drawSliderV2(
                    graphics,
                    popupPose,
                    textureSetup,
                    parentScissor,
                    "Blur",
                    contentX,
                    popupY + 10.0f,
                    contentW,
                    blurStrength,
                    0.0f,
                    30.0f,
                    0,
                    textAlpha,
                    localMX,
                    localMY,
                    true
            );

            if (showBloom) {
                // Draw Bloom Slider
                SliderElement.drawSliderV2(
                        graphics,
                        popupPose,
                        textureSetup,
                        parentScissor,
                        "Bloom",
                        contentX,
                        popupY + 25.0f,
                        contentW,
                        bloomStrength,
                        0.0f,
                        20.0f,
                        0,
                        textAlpha,
                        localMX,
                        localMY,
                        true
                );
            }

            // Draw Separate Next Checkbox
            independentNextSongCheckboxAnimation.run(independentNextSong ? 1.0f : 0.0f);
            float checkboxY = popupY + (showBloom ? 40.0f : 25.0f);
            com.haloclient.client.gui.click.elements.CheckboxElement.drawCheckboxV2(
                    graphics,
                    popupPose,
                    textureSetup,
                    parentScissor,
                    "Detach Next",
                    contentX,
                    checkboxY,
                    contentW,
                    independentNextSongCheckboxAnimation.getValue(),
                    textAlpha,
                    false,
                    localMX,
                    localMY,
                    true
            );
        }
    }



    /**
     * Trims text to fit within the given width using MSDF font metrics.
     */
    static String trimToWidthMsdf(MsdfFont font, String text, float width, float size) {
        if (font.getWidth(text, size) <= width) {
            return text;
        }
        String suffix = "...";
        float suffixWidth = font.getWidth(suffix, size);
        float maxWidth = Math.max(0.0f, width - suffixWidth);
        StringBuilder sb = new StringBuilder();
        float currentWidth = 0;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            float charWidth = font.getWidth(ch, size);
            if (currentWidth + charWidth > maxWidth) break;
            currentWidth += charWidth;
            sb.append(text.charAt(i));
        }
        return sb + suffix;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    static ImageManager.CachedImage getAlbumArt(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        java.io.File file = new java.io.File(path);
        long lastMod = file.exists() ? file.lastModified() : 0L;
        
        ImageManager.CachedImage cached = artCache.get(path);
        Long cachedLastMod = artLastModified.get(path);
        
        if (cached != null && cachedLastMod != null && lastMod == cachedLastMod) {
            return cached;
        }
        try {
            if (file.exists()) {
                byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
                ImageManager.evict("native:" + path);
                ImageManager.CachedImage loaded = ImageManager.fromBytes(path, bytes);
                if (loaded != null) {
                    if (artCache.size() > 10) {
                        artCache.clear();
                        artLastModified.clear();
                    }
                    artCache.put(path, loaded);
                    artLastModified.put(path, lastMod);
                    return loaded;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static float resolveX(int screenWidth) {
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

    private static float getTotalHeight() {
        float currentHeight = heightAnimation.getValue();
        if (independentNextSong) {
            return currentHeight;
        }
        SpotifyManager.NextTrack nextTrack = SpotifyManager.getNextTrack();
        boolean hasNextSong = SpotifyManager.isConfigured() && showNextSong && nextTrack != null && nextTrack.hasMedia();
        float nextSongScale = nextSongScaleAnimation.getValue();
        return currentHeight + (22.0f + 4.0f) * nextSongScale;
    }

    private static float resolveY(int screenHeight) {
        float totalHeight = getTotalHeight();
        float target = Float.isNaN(relativeY) ? 20.0f : relativeY * (screenHeight - totalHeight);
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



    private static void loadAssets() {
        if (spotifyLogoImage == null) {
            try {
                Identifier id = Identifier.fromNamespaceAndPath("halo", "spotify-white-icon.png");
                spotifyLogoImage = ImageManager.fromIdentifier(id);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (settingsIcon == null) {
            try {
                Identifier id = Identifier.fromNamespaceAndPath("halo", "icons8-settings-20.png");
                settingsIcon = new NVGImageRenderer(
                        Minecraft.getInstance().getResourceManager().open(id)
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
        }



        // Load MSDF fonts for crisp text rendering
        if (msdfTitleFont == null) {
            try {
                msdfTitleFont = MsdfFontManager.getFont("inter-bold", TITLE_SIZE);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (msdfTimeFont == null) {
            try {
                msdfTimeFont = MsdfFontManager.getFont("inter-regular", TIME_SIZE);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (msdfArtistFont == null) {
            try {
                msdfArtistFont = MsdfFontManager.getFont("inter-semibold", TEXT_SIZE);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (fluidFont == null) {
            try {
                fluidFont = MsdfFontManager.getFont("fluid-regular", 9.0f);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (materialIconsFont == null) {
            try {
                materialIconsFont = MsdfFontManager.getFont("materialicons-regular", 10.0f);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        boolean chatOpen = Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.ChatScreen || Minecraft.getInstance().screen instanceof com.haloclient.client.gui.click.ClickGUI;
        float currentHeight = heightAnimation.getValue();
        float x = resolveX(Minecraft.getInstance().getWindow().getGuiScaledWidth());
        float y = resolveY(Minecraft.getInstance().getWindow().getGuiScaledHeight());

        float scale = scaleAnimation.getValue();
        float currentScale = userScaleAnimation.getValue();
        float totalScale = scale * currentScale;

        float pivotX = x + WIDTH / 2.0f;
        float pivotY = y + currentHeight / 2.0f;
        float localMX = (float) ((mouseX - pivotX) / totalScale + WIDTH / 2.0f);
        float localMY = (float) ((mouseY - pivotY) / totalScale + currentHeight / 2.0f);

        boolean hitMainCard = localMX >= 0 && localMX <= WIDTH && localMY >= 0 && localMY <= currentHeight;
        SpotifyManager.NextTrack nextTrack = SpotifyManager.getNextTrack();
        boolean hasNextSong = SpotifyManager.isConfigured() && showNextSong && nextTrack != null && nextTrack.hasMedia();
        boolean hitNextSongCard = !independentNextSong && hasNextSong && nextSongScaleAnimation.getValue() > 0.001f &&
                localMX >= 0 && localMX <= WIDTH && localMY >= currentHeight + 4.0f && localMY <= currentHeight + 4.0f + 22.0f;

        if (chatOpen && paintbrushBtnAnimation.getValue() > 0.5f) {
            float btnX = WIDTH + 6.0f;
            float brushBtnY = (currentHeight - 17.0f) / 2.0f - 11.0f;
            float settingsBtnY = (currentHeight - 17.0f) / 2.0f + 11.0f;
            float btnSize = 17.0f;

            float popupX = WIDTH + 32.0f;
            float popupY = (currentHeight - 128.0f) / 2.0f;

            ensureColorPickerInitialized();

            boolean clickedSomething = false;

            if (colorPickerGuiInstance.colorPickerOpen && colorPickerPopupAnimation.getValue() > 0.5f) {
                if (localMX >= popupX && localMX <= popupX + 120.0f && localMY >= popupY && localMY <= popupY + 128.0f) {
                    clickedSomething = true;
                }
                if (handleColorPickerPopupClick(localMX, localMY, popupX, popupY, button)) {
                    return true;
                }
            }

            if (settingsPopupOpen && settingsPopupAnimation.getValue() > 0.5f) {
                boolean showBloom = (backgroundType != BackgroundType.LIQUID_GLASS);
                float settingsPopupHeight = showBloom ? 51.0f : 35.0f;
                float settingsPopupY = (currentHeight - settingsPopupHeight) / 2.0f;
                float pad = 8.0f;
                float contentX = popupX + pad - 5;
                float contentW = 120.0f - 2 * pad;

                if (localMX >= popupX && localMX <= popupX + 110.0f && localMY >= settingsPopupY && localMY <= settingsPopupY + settingsPopupHeight) {
                    clickedSomething = true;
                }

                float blurSy = settingsPopupY + 10.0f;
                if (button == 0 && localMX >= contentX && localMX <= contentX + contentW && localMY >= blurSy - 6.5f && localMY <= blurSy + 6.5f) {
                    draggingBlur = true;
                    float labelWidth = 25.0f;
                    float valueWidth = 15.0f;
                    float trackX = contentX + labelWidth + 4.0f;
                    float trackWidth = contentW - labelWidth - valueWidth - 12.0f;
                    float normalized = Math.max(0.0f, Math.min(1.0f, (localMX - trackX) / trackWidth));
                    blurStrength = normalized * 30.0f;
                    return true;
                }

                float bloomSy = settingsPopupY + 25.0f;
                if (showBloom && button == 0 && localMX >= contentX && localMX <= contentX + contentW && localMY >= bloomSy - 6.5f && localMY <= bloomSy + 6.5f) {
                    draggingBloom = true;
                    float labelWidth = 25.0f;
                    float valueWidth = 15.0f;
                    float trackX = contentX + labelWidth + 4.0f;
                    float trackWidth = contentW - labelWidth - valueWidth - 12.0f;
                    float normalized = Math.max(0.0f, Math.min(1.0f, (localMX - trackX) / trackWidth));
                    bloomStrength = normalized * 20.0f;
                    return true;
                }

                float checkboxY = settingsPopupY + (showBloom ? 40.0f : 25.0f);
                if (button == 0 && localMX >= contentX && localMX <= contentX + contentW && localMY >= checkboxY - 6.5f && localMY <= checkboxY + 6.5f) {
                    independentNextSong = !independentNextSong;
                    if (independentNextSong) {
                        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
                        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
                        float mainX = resolveX(screenWidth);
                        float mainY = resolveY(screenHeight);
                        DetachedNextElement.relativeX = Float.isNaN(relativeX) ? 0.5f : relativeX;
                        float nextY = mainY + currentHeight + 4.0f;
                        DetachedNextElement.relativeY = nextY / (screenHeight - DetachedNextElement.HEIGHT);
                        
                        DetachedNextElement.posXAnimation.setValue(mainX);
                        DetachedNextElement.posXAnimation.setStartValue(mainX);
                        DetachedNextElement.posYAnimation.setValue(nextY);
                        DetachedNextElement.posYAnimation.setStartValue(nextY);
                    }
                    return true;
                }

                if (localMX >= popupX && localMX <= popupX + 120.0f && localMY >= settingsPopupY && localMY <= settingsPopupY + settingsPopupHeight) {
                    return true;
                }
            }

            if (localMX >= btnX && localMX <= btnX + btnSize && ((localMY >= brushBtnY && localMY <= brushBtnY + btnSize) || (localMY >= settingsBtnY && localMY <= settingsBtnY + btnSize))) {
                clickedSomething = true;
            }

            if (button == 0 && localMX >= btnX && localMX <= btnX + btnSize && localMY >= brushBtnY && localMY <= brushBtnY + btnSize) {
                colorPickerGuiInstance.colorPickerOpen = !colorPickerGuiInstance.colorPickerOpen;
                if (colorPickerGuiInstance.colorPickerOpen) {
                    settingsPopupOpen = false;
                    updateGuiHSB(colorPickerGuiInstance, getTargetColor(colorPickerGuiInstance.selectedTarget));
                }
                return true;
            }

            if (button == 0 && localMX >= btnX && localMX <= btnX + btnSize && localMY >= settingsBtnY && localMY <= settingsBtnY + btnSize) {
                settingsPopupOpen = !settingsPopupOpen;
                if (settingsPopupOpen) {
                    if (colorPickerGuiInstance != null) {
                        colorPickerGuiInstance.colorPickerOpen = false;
                    }
                }
                return true;
            }

            if (hitMainCard || hitNextSongCard) {
                clickedSomething = true;
            }

            if (!clickedSomething) {
                showPaintbrushButton = false;
                settingsPopupOpen = false;
                if (colorPickerGuiInstance != null) {
                    colorPickerGuiInstance.colorPickerOpen = false;
                }
            }
        }

        if (hitMainCard || hitNextSongCard) {
            if (chatOpen && button == 1) {
                showPaintbrushButton = !showPaintbrushButton;
                if (!showPaintbrushButton && colorPickerGuiInstance != null) {
                    colorPickerGuiInstance.colorPickerOpen = false;
                }
                return true;
            }

            if (button == 0) {


                // If controls are shown, check if clicking controls row (localMY >= 40.0)
                if (showControls && heightAnimation.getValue() > 44.0f && localMY >= 40.0f) {
                    SpotifyManager.MediaStatus status = SpotifyManager.getStatus();
                    boolean isConfigured = SpotifyManager.isConfigured();

                    // Left controls: Speaker icon (E) / Volume Slider
                    if (isConfigured && localMX >= 18.0f && localMX <= 48.0f && localMY >= 40.0f && localMY <= 58.0f) {
                        draggingVolume = true;
                        float pct = clamp((localMX - 18.0f) / 30.0f, 0.0f, 1.0f);
                        SpotifyManager.getInstance().setVolume((int) (pct * 100));
                        return true;
                    }
                    if (isConfigured && localMX >= 6.0f && localMX <= 17.0f && localMY >= 40.0f && localMY <= 58.0f) {
                        SpotifyManager.getInstance().setVolume(status.volumePercent() > 0 ? 0 : 50);
                        return true;
                    }

                    // Middle playback controls
                    if (isConfigured) {
                        if (localMX >= 88.0f - 5 && localMX <= 101.0f - 5 && localMY >= 40.0f && localMY <= 58.0f) {
                            SpotifyManager.getInstance().togglePlayPause();
                            return true;
                        }
                        if (localMX >= 73.0f - 5 && localMX <= 87.0f - 5 && localMY >= 40.0f && localMY <= 58.0f) {
                            SpotifyManager.getInstance().previous();
                            return true;
                        }
                        if (localMX >= 102.0f - 5 && localMX <= 116.0f - 5 && localMY >= 40.0f && localMY <= 58.0f) {
                            SpotifyManager.getInstance().next();
                            return true;
                        }
                    }

                    // Right controls: Shuffle / Like / Loop
                    if (isConfigured) {
                        if (localMX >= 160.0f && localMX <= 172.0f && localMY >= 40.0f && localMY <= 58.0f) {
                            SpotifyManager.getInstance().toggleLike();
                            return true;
                        }
                        if (localMX >= 147.0f && localMX <= 159.0f && localMY >= 40.0f && localMY <= 58.0f) {
                            SpotifyManager.getInstance().toggleShuffle(!status.shuffleState());
                            return true;
                        }
                        if (localMX >= 134.0f && localMX <= 146.0f && localMY >= 40.0f && localMY <= 58.0f) {
                            SpotifyManager.getInstance().toggleRepeat();
                            return true;
                        }
                    }

                    // Clicked empty space in the controls area, drag instead
                }

                dragging = true;
                dragOffsetX = (float) (mouseX - x);
                dragOffsetY = (float) (mouseY - y);
                return true;
            }
        }
        return false;
    }

    public static boolean onMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        boolean chatOpen = Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.ChatScreen || Minecraft.getInstance().screen instanceof com.haloclient.client.gui.click.ClickGUI;
        if (chatOpen && colorPickerGuiInstance != null && colorPickerGuiInstance.colorPickerOpen && colorPickerPopupAnimation.getValue() > 0.5f) {
            float currentHeight = heightAnimation.getValue();
            float x = resolveX(Minecraft.getInstance().getWindow().getGuiScaledWidth());
            float y = resolveY(Minecraft.getInstance().getWindow().getGuiScaledHeight());
            float scale = scaleAnimation.getValue();
            float currentScale = userScaleAnimation.getValue();
            float totalScale = scale * currentScale;

            float pivotX = x + WIDTH / 2.0f;
            float pivotY = y + currentHeight / 2.0f;
            float localMX = (float) ((mouseX - pivotX) / totalScale + WIDTH / 2.0f);
            float localMY = (float) ((mouseY - pivotY) / totalScale + currentHeight / 2.0f);

            float popupX = WIDTH + 32.0f;
            float popupY = (currentHeight - 128.0f) / 2.0f;

            if (handleColorPickerPopupDrag(localMX, localMY, popupX, popupY, button)) {
                return true;
            }
        }

        if (chatOpen && settingsPopupOpen && settingsPopupAnimation.getValue() > 0.5f) {
            float currentHeight = heightAnimation.getValue();
            float x = resolveX(Minecraft.getInstance().getWindow().getGuiScaledWidth());
            float y = resolveY(Minecraft.getInstance().getWindow().getGuiScaledHeight());
            float scale = scaleAnimation.getValue();
            float currentScale = userScaleAnimation.getValue();
            float totalScale = scale * currentScale;

            float pivotX = x + WIDTH / 2.0f;
            float pivotY = y + currentHeight / 2.0f;
            float localMX = (float) ((mouseX - pivotX) / totalScale + WIDTH / 2.0f);
            float localMY = (float) ((mouseY - pivotY) / totalScale + currentHeight / 2.0f);

            boolean showBloom = (backgroundType != BackgroundType.LIQUID_GLASS);
            float settingsPopupHeight = showBloom ? 51.0f : 35.0f;
            float popupX = WIDTH + 32.0f;
            float popupY = (currentHeight - settingsPopupHeight) / 2.0f;
            float pad = 8.0f;
            float contentX = popupX + pad - 5;
            float contentW = 120.0f - 2 * pad;

            if (draggingBlur && button == 0) {
                float labelWidth = 25.0f;
                float valueWidth = 15.0f;
                float trackX = contentX + labelWidth + 4.0f;
                float trackWidth = contentW - labelWidth - valueWidth - 12.0f;
                float normalized = Math.max(0.0f, Math.min(1.0f, (localMX - trackX) / trackWidth));
                blurStrength = normalized * 30.0f;
                return true;
            }

            if (showBloom && draggingBloom && button == 0) {
                float labelWidth = 25.0f;
                float valueWidth = 15.0f;
                float trackX = contentX + labelWidth + 4.0f;
                float trackWidth = contentW - labelWidth - valueWidth - 12.0f;
                float normalized = Math.max(0.0f, Math.min(1.0f, (localMX - trackX) / trackWidth));
                bloomStrength = normalized * 20.0f;
                return true;
            }
        }



        if (draggingVolume && button == 0) {
            float x = resolveX(Minecraft.getInstance().getWindow().getGuiScaledWidth());
            float totalScale = scaleAnimation.getValue() * userScaleAnimation.getValue();
            float pivotX = x + WIDTH / 2.0f;
            float localMX = (float) ((mouseX - pivotX) / totalScale + WIDTH / 2.0f);
            float pct = clamp((localMX - 18.0f) / 30.0f, 0.0f, 1.0f);
            SpotifyManager.getInstance().setVolume((int) (pct * 100));
            return true;
        }
        if (dragging && button == 0) {
            int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
            int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();

            float targetX = (float) (mouseX - dragOffsetX);
            float targetY = (float) (mouseY - dragOffsetY);

            float totalHeight = getTotalHeight();

            float currentScale = userScaleAnimation.getValue();
            float currentW = WIDTH * currentScale;
            float currentH = totalHeight * currentScale;

            float minX = (currentW - WIDTH) / 2.0f;
            float maxX = screenWidth - (WIDTH + currentW) / 2.0f;
            float minY = (currentH - totalHeight) / 2.0f;
            float maxY = screenHeight - (totalHeight + currentH) / 2.0f;

            if (minX > maxX) {
                targetX = (screenWidth - WIDTH) / 2.0f;
            } else {
                targetX = clamp(targetX, minX, maxX);
            }

            if (minY > maxY) {
                targetY = (screenHeight - totalHeight) / 2.0f;
            } else {
                targetY = clamp(targetY, minY, maxY);
            }

            float centerX = screenWidth / 2.0f;
            float centerY = screenHeight / 2.0f;
            float overlayCenterX = targetX + WIDTH / 2.0f;
            float overlayCenterY = targetY + totalHeight / 2.0f;

            float snapThreshold = 10.0f;

            boolean snappedX = false;
            if (Math.abs(overlayCenterX - centerX) < snapThreshold) {
                targetX = centerX - WIDTH / 2.0f;
                snappedX = true;
            }
            boolean snappedY = false;
            if (Math.abs(overlayCenterY - centerY) < snapThreshold) {
                targetY = centerY - totalHeight / 2.0f;
                snappedY = true;
            }

            relativeX = snappedX ? 0.5f : (screenWidth > WIDTH ? targetX / (screenWidth - WIDTH) : 0.5f);
            relativeY = snappedY ? 0.5f : (screenHeight > totalHeight ? targetY / (screenHeight - totalHeight) : 0.05f);
            return true;
        }
        return false;
    }

    public static boolean onMouseReleased(double mouseX, double mouseY, int button) {
        if (colorPickerGuiInstance != null) {
            colorPickerGuiInstance.draggingSB = false;
            colorPickerGuiInstance.draggingHue = false;
            colorPickerGuiInstance.draggingCpAlpha = false;
        }

        if (draggingVolume && button == 0) {
            draggingVolume = false;
            return true;
        }
        if (draggingBlur && button == 0) {
            draggingBlur = false;
            return true;
        }
        if (draggingBloom && button == 0) {
            draggingBloom = false;
            return true;
        }
        if (dragging && button == 0) {
            dragging = false;
            return true;
        }
        return false;
    }

    public static boolean onMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!visible) return false;
        
        float x = resolveX(Minecraft.getInstance().getWindow().getGuiScaledWidth());
        float y = resolveY(Minecraft.getInstance().getWindow().getGuiScaledHeight());

        float currentScale = userScaleAnimation.getValue();
        float currentW = WIDTH * currentScale;
        float currentH = heightAnimation.getValue() * currentScale;
        float currentX = x + (WIDTH - currentW) / 2.0f;
        float currentY = y + (heightAnimation.getValue() - currentH) / 2.0f;



        if (mouseX >= currentX && mouseX <= currentX + currentW && mouseY >= currentY && mouseY <= currentY + currentH) {
            targetUserScale = clamp(targetUserScale + (float) scrollY * 0.05f, 0.4f, 2.5f);
            return true;
        }
        return false;
    }

    public static boolean onKeyEvent(int keyCode, int action, long window) {
        if (!visible || colorPickerGuiInstance == null || !colorPickerGuiInstance.colorPickerOpen || !colorPickerGuiInstance.hexInputActive) {
            return false;
        }
        return colorPickerGuiInstance.onKeyEvent(keyCode, action, window);
    }

    private static void ensureColorPickerInitialized() {
        if (colorPickerGuiInstance == null) {
            colorPickerGuiInstance = new ClickGUI();
            updateGuiHSB(colorPickerGuiInstance, backgroundColor);
        }
    }

    private static void updateGuiHSB(ClickGUI gui, int color) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        float[] hsb = java.awt.Color.RGBtoHSB(r, g, b, null);
        gui.cpHue = hsb[0];
        gui.cpSat = hsb[1];
        gui.cpBri = hsb[2];
        gui.cpAlpha = a / 255.0f;
        gui.hexInputText = String.format("%02X%02X%02X%02X", r, g, b, a);
        gui.hexCursor = gui.hexInputText.length();
        gui.animatedCpSat = gui.cpSat;
        gui.animatedCpBri = gui.cpBri;
    }

    private static int getTargetColor(ClickGUI.ColorTarget target) {
        if (target == ClickGUI.ColorTarget.BACKGROUND) return backgroundColor;
        if (target == ClickGUI.ColorTarget.TITLE) return titleColor;
        if (target == ClickGUI.ColorTarget.ARTIST) return artistColor;
        if (target == ClickGUI.ColorTarget.TIME) return timeColor;
        if (target == ClickGUI.ColorTarget.PROGRESS_BAR) return progressColor;
        return 0xFFFFFFFF;
    }

    private static void applyTargetColor(ClickGUI.ColorTarget target, int color) {
        if (target == ClickGUI.ColorTarget.BACKGROUND) backgroundColor = color;
        else if (target == ClickGUI.ColorTarget.TITLE) titleColor = color;
        else if (target == ClickGUI.ColorTarget.ARTIST) artistColor = color;
        else if (target == ClickGUI.ColorTarget.TIME) timeColor = color;
        else if (target == ClickGUI.ColorTarget.PROGRESS_BAR) progressColor = color;
    }

    private static void updateSB(float mouseX, float mouseY, float sbX, float sbY, float sbW, float sbH) {
        colorPickerGuiInstance.cpSat = clamp((mouseX - sbX) / sbW, 0.0f, 1.0f);
        colorPickerGuiInstance.cpBri = clamp(1.0f - (mouseY - sbY) / sbH, 0.0f, 1.0f);
        updateColorFromHSB();
    }

    private static void updateHueFromMouse(float mouseY, float hueY, float hueH) {
        colorPickerGuiInstance.cpHue = clamp((mouseY - hueY) / hueH, 0.0f, 1.0f);
        updateColorFromHSB();
    }

    private static void updateAlphaFromMouse(float mouseX, float alphaX, float alphaW) {
        colorPickerGuiInstance.cpAlpha = clamp((mouseX - alphaX) / alphaW, 0.0f, 1.0f);
        updateColorFromHSB();
    }

    private static void updateColorFromHSB() {
        int rgb = java.awt.Color.HSBtoRGB(colorPickerGuiInstance.cpHue, colorPickerGuiInstance.cpSat, colorPickerGuiInstance.cpBri);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int a = (int)(colorPickerGuiInstance.cpAlpha * 255);
        colorPickerGuiInstance.colorPickerColor = ARGB.color(a, r, g, b);
        applyTargetColor(colorPickerGuiInstance.selectedTarget, colorPickerGuiInstance.colorPickerColor);
        colorPickerGuiInstance.hexInputText = String.format("%02X%02X%02X%02X", r, g, b, a);
        colorPickerGuiInstance.hexCursor = Math.min(colorPickerGuiInstance.hexCursor, colorPickerGuiInstance.hexInputText.length());
        
        colorPickerGuiInstance.animatedCpSat = colorPickerGuiInstance.cpSat;
        colorPickerGuiInstance.animatedCpBri = colorPickerGuiInstance.cpBri;
    }

    private static boolean handleColorPickerPopupClick(float mx, float my, float px, float py, int button) {
        if (button != 0) return false;
        float pad = 6.0f;
        float innerW = 120.0f - 2 * pad;
        float hueBarW = 8.0f;
        float gap = 6.0f;
        float sbW = innerW - hueBarW - gap;
        float sbH = 65.0f;
        float alphaBarH = 8.0f;
        float hexH = 14.0f;

        float comboX = px + pad;
        float comboY = py + pad;
        float comboW = 80.0f;
        float comboH = 13.0f;
        float gapBetweenComboAndSB = 5.0f;

        float closeBtnX = px + 120.0f - pad - 13.0f;
        float closeBtnY = comboY;
        float closeBtnW = 13.0f;
        float closeBtnH = 13.0f;

        float sbX = px + pad;
        float sbY = comboY + comboH + gapBetweenComboAndSB;
        float hueX = sbX + sbW + gap;
        float hueBarY = sbY;
        float alphaSliderX = sbX;
        float alphaSliderY = sbY + sbH + 5.0f;
        float hexX = sbX;
        float hexY = alphaSliderY + alphaBarH + 5.0f;

        float inputW = 72.0f;
        float btnW = 14.0f;
        float copyBtnX = hexX + inputW + 4.0f;
        float pasteBtnX = copyBtnX + btnW + 4.0f;

        if (mx >= closeBtnX && mx <= closeBtnX + closeBtnW && my >= closeBtnY && my <= closeBtnY + closeBtnH) {
            colorPickerGuiInstance.colorPickerOpen = false;
            colorPickerGuiInstance.hexInputActive = false;
            colorPickerGuiInstance.comboboxOpen = false;
            return true;
        }

        if (colorPickerGuiInstance.comboboxOpen) {
            float listY = comboY + comboH + 1.0f;
            float optionH = 12.0f;
            for (int i = 0; i < ClickGUI.ColorTarget.values().length; i++) {
                float optY = listY + i * optionH;
                if (mx >= comboX && mx <= comboX + comboW && my >= optY && my <= optY + optionH) {
                    colorPickerGuiInstance.selectedTarget = ClickGUI.ColorTarget.values()[i];
                    colorPickerGuiInstance.comboboxOpen = false;
                    
                    int currentTargetColor = getTargetColor(colorPickerGuiInstance.selectedTarget);
                    colorPickerGuiInstance.colorPickerColor = currentTargetColor;
                    updateGuiHSB(colorPickerGuiInstance, currentTargetColor);
                    return true;
                }
            }
            colorPickerGuiInstance.comboboxOpen = false;
            return true;
        }

        if (mx >= comboX && mx <= comboX + comboW && my >= comboY && my <= comboY + comboH) {
            colorPickerGuiInstance.comboboxOpen = !colorPickerGuiInstance.comboboxOpen;
            colorPickerGuiInstance.hexInputActive = false;
            return true;
        }

        if (mx >= sbX && mx <= sbX + sbW && my >= sbY && my <= sbY + sbH) {
            colorPickerGuiInstance.draggingSB = true;
            colorPickerGuiInstance.hexInputActive = false;
            updateSB(mx, my, sbX, sbY, sbW, sbH);
            return true;
        }

        if (mx >= hueX && mx <= hueX + hueBarW && my >= hueBarY && my <= hueBarY + sbH) {
            colorPickerGuiInstance.draggingHue = true;
            colorPickerGuiInstance.hexInputActive = false;
            updateHueFromMouse(my, hueBarY, sbH);
            return true;
        }

        if (mx >= alphaSliderX && mx <= alphaSliderX + innerW && my >= alphaSliderY && my <= alphaSliderY + alphaBarH) {
            colorPickerGuiInstance.draggingCpAlpha = true;
            colorPickerGuiInstance.hexInputActive = false;
            updateAlphaFromMouse(mx, alphaSliderX, innerW);
            return true;
        }

        if (mx >= copyBtnX && mx <= copyBtnX + btnW && my >= hexY && my <= hexY + hexH) {
            Minecraft.getInstance().keyboardHandler.setClipboard(colorPickerGuiInstance.hexInputText);
            return true;
        }
        if (mx >= pasteBtnX && mx <= pasteBtnX + btnW && my >= hexY && my <= hexY + hexH) {
            String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clip != null && !clip.isBlank()) {
                clip = clip.trim().replace("#", "");
                if (clip.length() == 6) clip = clip + "FF";
                if (clip.length() == 8) {
                    try {
                        long longVal = Long.parseLong(clip, 16);
                        int newColor = (int) longVal;
                        colorPickerGuiInstance.colorPickerColor = newColor;
                        updateGuiHSB(colorPickerGuiInstance, newColor);
                        applyTargetColor(colorPickerGuiInstance.selectedTarget, newColor);
                    } catch (NumberFormatException ignored) {}
                }
            }
            return true;
        }

        if (mx >= hexX && mx <= hexX + inputW && my >= hexY && my <= hexY + hexH) {
            colorPickerGuiInstance.hexInputActive = true;
            colorPickerGuiInstance.lastBlinkTime = System.currentTimeMillis();
            colorPickerGuiInstance.cursorVisible = true;
            colorPickerGuiInstance.hexSelStart = -1;
            colorPickerGuiInstance.hexSelEnd = -1;
            colorPickerGuiInstance.hexCursor = colorPickerGuiInstance.hexInputText.length();
            return true;
        }

        if (mx >= px && mx <= px + 120.0f && my >= py && my <= py + 128.0f) {
            colorPickerGuiInstance.hexInputActive = false;
            return true;
        }

        return false;
    }

    private static boolean handleColorPickerPopupDrag(float mx, float my, float px, float py, int button) {
        if (button != 0) return false;
        float pad = 6.0f;
        float innerW = 120.0f - 2 * pad;
        float hueBarW = 8.0f;
        float gap = 6.0f;
        float sbW = innerW - hueBarW - gap;
        float sbH = 65.0f;
        float alphaBarH = 8.0f;

        float comboH = 13.0f;
        float gapBetweenComboAndSB = 5.0f;

        float sbX = px + pad;
        float sbY = py + pad + comboH + gapBetweenComboAndSB;
        float hueX = sbX + sbW + gap;
        float hueBarY = sbY;
        float alphaSliderX = sbX;
        float alphaSliderY = sbY + sbH + 5.0f;

        if (colorPickerGuiInstance.draggingSB) {
            updateSB(mx, my, sbX, sbY, sbW, sbH);
            return true;
        }
        if (colorPickerGuiInstance.draggingHue) {
            updateHueFromMouse(my, hueBarY, sbH);
            return true;
        }
        if (colorPickerGuiInstance.draggingCpAlpha) {
            updateAlphaFromMouse(mx, alphaSliderX, innerW);
            return true;
        }
        return false;
    }
}

