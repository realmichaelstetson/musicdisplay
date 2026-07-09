package com.haloclient.client.gui.click;

import com.haloclient.client.render.renderstates.BlurredRoundedRectangleRenderState;
import com.haloclient.client.render.CaptureManager;
import com.haloclient.client.render.HaloRenderPipelines;
import com.haloclient.client.render.FontRepository;
import com.haloclient.client.render.NVGRenderer;
import com.haloclient.client.render.NVGTextRenderer;
import com.haloclient.client.render.NVGImageRenderer;
import com.haloclient.client.render.animation.Animation;
import com.haloclient.client.render.animation.Easing;
import com.haloclient.client.render.renderstates.RoundedRectangleRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.joml.Matrix3x2f;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import com.haloclient.client.render.ImageManager;
import com.haloclient.client.render.renderstates.ImageRenderState;
import com.haloclient.client.render.font.FontManager;
import com.haloclient.client.render.font.HaloFontRenderState;
import com.haloclient.client.render.font.MsdfFont;
import com.haloclient.client.render.font.MsdfFontManager;
import org.jspecify.annotations.Nullable;
import java.awt.Color;
import java.util.Map;

public class ClickGUI extends Screen {

    private static final float WIDTH = 230.0f;
    private static final float HEIGHT = 140.0f;
    private static final float PADDING = 8.0f;
    private static final float TAB_SWITCHER_WIDTH = 20.0f;
    private static final float TAB_SWITCHER_HEIGHT = 13.0f;
    private static final float TAB_SWITCHER_SPACING = 2.0f;
    private static final float TAB_SWITCHER_BG_PADDING = 2.0f;

    // Color picker popup constants
    private static final float POPUP_WIDTH = 120.0f;
    private static final float POPUP_HEIGHT = 128.0f;

    private static float panelX = Float.NaN;
    private static float panelY = Float.NaN;

    private static final String HOME_ICON = "\uE88A";
    private static final String SETTINGS_ICON = "\uE8B8";

    private static NVGTextRenderer headerFont;
    private static NVGTextRenderer checkboxFont;
    private static NVGTextRenderer mediumFont;
    private static NVGTextRenderer iconFont;
    private static NVGTextRenderer fluidFont;
    private static NVGImageRenderer spotifyLogo;
    private static ImageManager.CachedImage spotifyLogoImage;

    private boolean dragging;
    private boolean draggingBlurSlider;
    private boolean draggingBloomSlider;
    private boolean draggingScaleSlider;
    private float dragStartGuiScale = 1.0f;
    private float dragOffsetX;
    private float dragOffsetY;

    public static float guiScale = 1.0f;

    // Color target selection
    public enum ColorTarget {
        BACKGROUND("Background Color"),
        TITLE("Title Text Color"),
        ARTIST("Artist Text Color"),
        TIME("Time Text Color"),
        PROGRESS_BAR("Progress Bar Color");

        private final String name;
        ColorTarget(String name) { this.name = name; }
        public String getName() { return name; }
    }

    public ColorTarget selectedTarget = ColorTarget.BACKGROUND;
    public boolean comboboxOpen = false;

    // Color picker state
    public static int colorPickerColor = 0xFFFF0000;
    public boolean colorPickerOpen = false;
    public float cpHue = 0.0f;
    public float cpSat = 1.0f;
    public float cpBri = 1.0f;
    public float cpAlpha = 1.0f;
    public boolean draggingSB = false;
    public boolean draggingHue = false;
    public boolean draggingCpAlpha = false;
    public boolean hexInputActive = false;
    public String hexInputText = "FF0000FF";
    public int hexCursor = 8;
    public int hexSelStart = -1;
    public int hexSelEnd = -1;
    public long lastBlinkTime = 0;
    public boolean cursorVisible = true;

    // Search fields
    private boolean searchInputActive = false;
    private String searchInputText = "";
    private int searchCursor = 0;
    private int searchSelStart = -1;
    private int searchSelEnd = -1;
    private long searchLastBlinkTime = 0;
    private boolean searchCursorVisible = true;
    private boolean searchLoading = false;
    private java.util.List<SpotifyManager.SearchResultTrack> searchResults = new java.util.ArrayList<>();
    private SpotifyManager.SearchFilter currentSearchFilter = SpotifyManager.SearchFilter.ALL;

    private void setAndTriggerSearchFilter(SpotifyManager.SearchFilter filter) {
        if (this.currentSearchFilter == filter) return;
        this.currentSearchFilter = filter;
        triggerSearch(searchInputText);
    }
    private final Animation filterIndicatorX = new Animation(Easing.EASE_OUT_CUBIC, 200);
    private final Animation filterIndicatorWidth = new Animation(Easing.EASE_OUT_CUBIC, 200);
    private final Map<String, NVGImageRenderer> searchArtRenderers = new java.util.HashMap<>();
    private final Animation scaleAnimation = new Animation(Easing.EASE_OUT_CUBIC, 250);
    private final Animation popupScaleAnimation = new Animation(Easing.EASE_OUT_CUBIC, 200);
    public final Animation comboboxAnimation = new Animation(Easing.EASE_OUT_CUBIC, 150);
    public final Animation selectorCircleAnimation = new Animation(Easing.EASE_OUT_CUBIC, 150);
    private final Animation enabledToggleAnimation = new Animation(Easing.EASE_OUT_CUBIC, 150);
    private final Animation controlsToggleAnimation = new Animation(Easing.EASE_OUT_CUBIC, 150);
    private final Animation nextSongToggleAnimation = new Animation(Easing.EASE_OUT_CUBIC, 150);
    public boolean bgStyleComboOpen = false;
    public final Animation bgStyleComboAnimation = new Animation(Easing.EASE_OUT_CUBIC, 150);
    public float animatedCpSat = 0.0f;
    public float animatedCpBri = 1.0f;
    private boolean closing;

    public ClickGUI() {
        super(Component.literal("ClickGUI"));
        scaleAnimation.setStartValue(0.0f);
        popupScaleAnimation.setStartValue(0.0f);
        comboboxAnimation.setStartValue(0.0f);
        selectorCircleAnimation.setStartValue(0.0f);
        enabledToggleAnimation.setStartValue(0.0f);
        controlsToggleAnimation.setStartValue(0.0f);
        nextSongToggleAnimation.setStartValue(0.0f);
        bgStyleComboAnimation.setStartValue(0.0f);
        filterIndicatorX.setStartValue(0.0f);
        filterIndicatorWidth.setStartValue(0.0f);
    }

    @Override
    protected void init() {
        super.init();
        searchInputActive = false;
        searchInputText = "";
        searchCursor = 0;
        searchSelStart = -1;
        searchSelEnd = -1;
        searchResults.clear();
        searchArtRenderers.clear();
        searchLoading = false;
        currentSearchFilter = SpotifyManager.SearchFilter.ALL;
        filterIndicatorX.setStartValue(0.0f);
        filterIndicatorWidth.setStartValue(0.0f);
        closing = false;
        scaleAnimation.setStartValue(scaleAnimation.getValue());
        scaleAnimation.reset();
        
        selectedTarget = ColorTarget.BACKGROUND;
        comboboxOpen = false;
        bgStyleComboOpen = false;
        
        // Sync with overlay background color
        colorPickerColor = MusicDisplayOverlay.backgroundColor;
        updateHSBFromColor();
        animatedCpSat = cpSat;
        animatedCpBri = cpBri;
        popupScaleAnimation.setStartValue(0.0f);
        popupScaleAnimation.reset();
        comboboxAnimation.setStartValue(0.0f);
        comboboxAnimation.reset();
        bgStyleComboAnimation.setStartValue(0.0f);
        bgStyleComboAnimation.reset();
        selectorCircleAnimation.setStartValue(0.0f);
        selectorCircleAnimation.reset();
        enabledToggleAnimation.setStartValue(MusicDisplayOverlay.isOpened() ? 1.0f : 0.0f);
        enabledToggleAnimation.reset();
        controlsToggleAnimation.setStartValue(MusicDisplayOverlay.isShowControls() ? 1.0f : 0.0f);
        controlsToggleAnimation.reset();
        nextSongToggleAnimation.setStartValue(MusicDisplayOverlay.isShowNextSong() ? 1.0f : 0.0f);
        nextSongToggleAnimation.reset();
    }

    private float resolvePopupX(float pX) {
        return pX + WIDTH + 4.0f;
    }

    private float resolvePopupY(float pY) {
        return pY + (HEIGHT - POPUP_HEIGHT) / 2.0f;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (dragging) {
            panelX = mouseX - dragOffsetX;
            panelY = mouseY - dragOffsetY;
        }

        if (MusicDisplayOverlay.isVisible()) {
            MusicDisplayOverlay.render(graphics);
        }

        // Run scale animation
        scaleAnimation.run(closing ? 0.0f : 1.0f);
        float scale = scaleAnimation.getValue();
        if (scale <= 0.001f) {
            if (closing) {
                super.extractRenderState(graphics, mouseX, mouseY, delta);
                return;
            }
            scale = 0.001f;
        }

        // Run popup scale animation
        popupScaleAnimation.run(colorPickerOpen ? 1.0f : 0.0f);
        float popupScale = popupScaleAnimation.getValue();

        // Run combobox expand/collapse animation
        comboboxAnimation.run(comboboxOpen ? 1.0f : 0.0f);
        bgStyleComboAnimation.run(bgStyleComboOpen ? 1.0f : 0.0f);

        // Run selector circle dynamic scale animation
        selectorCircleAnimation.run(draggingSB ? 1.0f : 0.0f);

        // Run toggle switch animations
        enabledToggleAnimation.run(MusicDisplayOverlay.isOpened() ? 1.0f : 0.0f);
        controlsToggleAnimation.run(MusicDisplayOverlay.isShowControls() ? 1.0f : 0.0f);
        nextSongToggleAnimation.run(MusicDisplayOverlay.isShowNextSong() ? 1.0f : 0.0f);

        // Smoothly follow color picker values
        float lerpFactor = 0.2f;
        animatedCpSat += (cpSat - animatedCpSat) * lerpFactor;
        animatedCpBri += (cpBri - animatedCpBri) * lerpFactor;

        float x = resolvePanelX(this.width);
        float y = resolvePanelY(this.height);

        // Center of the panel for scale pivot
        float centerX = x + WIDTH / 2.0f;
        float centerY = y + HEIGHT / 2.0f;

        // Apply scale pivot to mouse coordinates
        mouseX = (int) (centerX + (mouseX - centerX) / guiScale);
        mouseY = (int) (centerY + (mouseY - centerY) / guiScale);

        var textureSetup = CaptureManager.getCaptureTextureSetup();
        // Apply scale transform around the panel center
        var pose = new Matrix3x2f(graphics.pose());
        pose.translate(centerX, centerY)
            .scale(scale * guiScale, scale * guiScale)
            .translate(-centerX, -centerY);
        var scissor = graphics.scissorStack.peek();

        int alphaScale = (int)(165 * scale);

        // Draw blurred background rounded rect (Shadcn zinc-950 background)
        graphics.guiRenderState.addGuiElement(new BlurredRoundedRectangleRenderState(
                HaloRenderPipelines.ROUNDED_BLUR,
                textureSetup,
                pose,
                x, y, WIDTH, HEIGHT,
                ARGB.color(alphaScale, 23, 23, 23),
                8.0f,
                200.0f,
                0.0f,
                scissor
            ));

        // Extract Header Spotify Logo
        if (spotifyLogoImage != null) {
            float logoSize = 13.0f;
            float logoX = x + 8.0f;
            float logoY = y + 14.0f - logoSize / 2.0f;
            graphics.guiRenderState.addGuiElement(new ImageRenderState(
                    HaloRenderPipelines.IMAGE,
                    spotifyLogoImage.textureSetup(),
                    pose,
                    logoX,
                    logoY,
                    logoSize,
                    logoSize,
                    ARGB.color((int) (255 * scale), 255, 255, 255),
                    0.0f,
                    ImageRenderState.ScaleMode.FIT,
                    spotifyLogoImage.width(),
                    spotifyLogoImage.height(),
                    scissor
            ));
        }

        // Extract Header Title
        var headerFontM = MsdfFontManager.getFont("productsans-bold", 10.0f);
        if (headerFontM != null) {
            graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                    headerFontM,
                    "Music Display v2.4",
                    new Matrix3x2f(pose),
                    x + 25.0f,
                    y + 14.5f - headerFontM.getHeight(10.0f) / 2.0f,
                    10.0f,
                    ARGB.color((int) (255 * scale), 255, 255, 255),
                    scissor
            ));
        }

        // Draw blurred background rounded rect for popup (Modal style) with popup scale
        if (popupScale > 0.001f) {
            float popupX = resolvePopupX(x);
            float popupY = resolvePopupY(y);
            float popupCenterX = popupX + POPUP_WIDTH / 2.0f;
            float popupCenterY = popupY + POPUP_HEIGHT / 2.0f;

            Matrix3x2f popupPose = new Matrix3x2f(pose);
            popupPose.translate(popupCenterX, popupCenterY)
                     .scale(popupScale, popupScale)
                     .translate(-popupCenterX, -popupCenterY);

            int popupAlphaScale = (int)(165 * scale * popupScale);

            graphics.guiRenderState.addGuiElement(new BlurredRoundedRectangleRenderState(
                    HaloRenderPipelines.ROUNDED_BLUR,
                    textureSetup,
                    popupPose,
                    popupX, popupY, POPUP_WIDTH, POPUP_HEIGHT,
                    ARGB.color(popupAlphaScale, 20, 20, 22),
                    6.0f,
                    200.0f,
                    0.0f,
                    scissor
            ));

            // Extract combobox background render state inside drawColorPickerPopup
            com.haloclient.client.gui.click.elements.ColorPickerElement.drawColorPickerPopup(graphics, popupPose, textureSetup, scissor, popupX, popupY, (int)(255 * scale * popupScale), mouseX, mouseY, true, this);
        }

        // Extract backgrounds and hover states for left and right columns
        if (searchInputText.isEmpty()) {
            float cW = 103.0f;
            float lCX = x + PADDING;
            
            // 1. Extract left section card ("Main" + checkbox + show controls)
            graphics.guiRenderState.addGuiElement(new BlurredRoundedRectangleRenderState(
                    HaloRenderPipelines.ROUNDED_BLUR,
                    textureSetup,
                    pose,
                    lCX, y + 27.5f, cW, 52.0f,
                    ARGB.color(165, 0, 0, 0),
                    3.0f,
                    200.0f,
                    0.0f,
                    scissor
            ));

            // Extract checkbox hover state (must be drawn behind the switch track)


            // Extract checkbox track
            com.haloclient.client.gui.click.elements.CheckboxElement.drawCheckbox(graphics, pose, textureSetup, scissor, "Enabled", lCX, y + 47.0f, cW, enabledToggleAnimation.getValue(), alphaScale, false, mouseX, mouseY, true);
            com.haloclient.client.gui.click.elements.CheckboxElement.drawCheckbox(graphics, pose, textureSetup, scissor, "Show Controls", lCX, y + 60.0f, cW, controlsToggleAnimation.getValue(), alphaScale, false, mouseX, mouseY, true);
            com.haloclient.client.gui.click.elements.CheckboxElement.drawCheckbox(graphics, pose, textureSetup, scissor, "Show Next Song", lCX, y + 73.0f, cW, nextSongToggleAnimation.getValue(), alphaScale, false, mouseX, mouseY, true);

            // 2. Extract right section card ("Settings" + 3 sliders + color picker)
            float colWidth = 103.0f;
            float rightColX = x + WIDTH - PADDING - colWidth;
            graphics.guiRenderState.addGuiElement(new BlurredRoundedRectangleRenderState(
                    HaloRenderPipelines.ROUNDED_BLUR,
                    textureSetup,
                    pose,
                    rightColX, y + 27.5f, colWidth, 78.0f,
                    ARGB.color(165, 0, 0, 0),
                    3.0f,
                    200.0f,
                    0.0f,
                    scissor
            ));

            // Extract hover highlights for individual controls inside the unified settings card
             if (isHovered(mouseX, mouseY, rightColX, y + 99.0f - 6.5f, colWidth, 13.0f)) {
                graphics.guiRenderState.addGuiElement(new BlurredRoundedRectangleRenderState(
                        HaloRenderPipelines.ROUNDED_BLUR,
                        textureSetup,
                        pose,
                        rightColX, y + 99.0f - 6.5f, colWidth, 13.0f,
                        ARGB.color(175, 0, 0, 0),
                        3.0f,
                        200.0f,
                        0.0f,
                        scissor
                ));
            }

            // Extract sliders
            com.haloclient.client.gui.click.elements.SliderElement.drawSlider(graphics, pose, textureSetup, scissor, "Blur", rightColX, y + 47.0f, colWidth, MusicDisplayOverlay.getBlurStrength(), 0.0f, 30.0f, 0, alphaScale, mouseX, mouseY, true);
            
            boolean bloomDisabled = MusicDisplayOverlay.getBackgroundType() == MusicDisplayOverlay.BackgroundType.LIQUID_GLASS;
            if (bloomDisabled) {
                float bloomY = y + 60.0f;
                var font = MsdfFontManager.getFont("productsans-semibold", 6f);
                if (font != null) {
                    graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                            font,
                            "Bloom",
                            new Matrix3x2f(pose),
                            rightColX + 4.0f,
                            bloomY - font.getHeight(6f) / 2f,
                            6f,
                            ARGB.color(alphaScale / 3, 244, 244, 245),
                            scissor
                    ));
                    
                    graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                            font,
                            "Unavailable with Liquid Glass.",
                            new Matrix3x2f(pose),
                            rightColX + colWidth - 4.0f - font.getWidth("Unavailable with Liquid Glass.", 5f),
                            bloomY - font.getHeight(5f) / 2f,
                            5f,
                            ARGB.color(alphaScale / 3, 161, 161, 170),
                            scissor
                    ));
                }
            } else {
                com.haloclient.client.gui.click.elements.SliderElement.drawSlider(graphics, pose, textureSetup, scissor, "Bloom", rightColX, y + 60.0f, colWidth, MusicDisplayOverlay.getBloomStrength(), 0.0f, 20.0f, 0, alphaScale, mouseX, mouseY, true);
            }
            
            com.haloclient.client.gui.click.elements.SliderElement.drawSlider(graphics, pose, textureSetup, scissor, "Gui Size", rightColX, y + 73.0f, colWidth, guiScale, 0.5f, 1.5f, 0, alphaScale, mouseX, mouseY, true);

            com.haloclient.client.gui.click.elements.ColorPickerElement.drawColorPicker(graphics, pose, textureSetup, scissor, "Colors", rightColX, y + 86.0f, colWidth, colorPickerColor, 0, alphaScale, mouseX, mouseY, true);

            // Extract Glass Style ComboBox
            com.haloclient.client.gui.click.elements.GlassStyleComboBoxElement.drawGlassStyleComboBox(graphics, pose, textureSetup, scissor, rightColX, colWidth, y, alphaScale, mouseX, mouseY, true, this);
        }

        // Extract Search background render state
        float searchX = x + 125.0f;
        float searchY = y + 7.5f;
        float searchW = 97.0f;
        float searchH = 14.0f;
        graphics.guiRenderState.addGuiElement(new BlurredRoundedRectangleRenderState(
                HaloRenderPipelines.ROUNDED_BLUR,
                textureSetup,
                pose,
                searchX, searchY, searchW, searchH,
                searchInputActive ? ARGB.color(185, 0, 0, 0) : ARGB.color(165, 00, 00, 00),
                4.0f,
                200.0f,
                0.0f,
                scissor
        ));

        // Extract Search Input content
        var mediumFontM = MsdfFontManager.getFont("productsans-medium", 7f);
        var materialIconFont = MsdfFontManager.getFont("materialicons-regular", 8f);
        if (mediumFontM != null && materialIconFont != null) {
            int alphaInt = (int)(255 * scale);
            // Search icon (magnifying glass)
            graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                    materialIconFont,
                    "\uE8B6",
                    new Matrix3x2f(pose),
                    searchX + 5.0f,
                    searchY + searchH / 2.0f - materialIconFont.getHeight(8.0f) / 2.0f,
                    8.0f,
                    ARGB.color(alphaInt, 161, 161, 170),
                    scissor
            ));

            // Search text input
            String dispText = searchInputText;
            if (dispText.isEmpty() && !searchInputActive) {
                dispText = "Search...";
            }
            int txtColor = searchInputText.isEmpty() ? ARGB.color(alphaInt, 113, 113, 122) : ARGB.color(alphaInt, 255, 255, 255);

            float textLeft = searchX + 16.0f;
            float textRight = searchX + searchW - (!searchInputText.isEmpty() ? 15.0f : 5.0f);
            float textWidthLimit = textRight - textLeft;

            // Scissor search text
            ScreenRectangle searchBounds = (new ScreenRectangle(
                    (int) textLeft, (int) searchY,
                    (int) textWidthLimit, (int) searchH
            )).transformMaxBounds(pose);
            ScreenRectangle searchScissor = scissor != null ? scissor.intersection(searchBounds) : searchBounds;

            // Draw selection highlight if active
            if (searchInputActive && hasSearchSelection()) {
                int selMin = Math.min(searchSelStart, searchSelEnd);
                int selMax = Math.max(searchSelStart, searchSelEnd);
                float beforeSelW = selMin > 0 ? mediumFontM.getWidth(searchInputText.substring(0, selMin), 7.0f) : 0;
                float selW = mediumFontM.getWidth(searchInputText.substring(selMin, selMax), 7.0f);
                float selHighlightX = textLeft + beforeSelW;

                graphics.guiRenderState.addGuiElement(new RoundedRectangleRenderState(
                        HaloRenderPipelines.ROUNDED_RECT,
                        pose,
                        selHighlightX, searchY + 3.0f, selW, searchH - 6.0f,
                        ARGB.color((int)(alphaInt * 0.3f), 100, 150, 255),
                        0.0f,
                        searchScissor
                ));
            }

            // Draw search text
            graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                    mediumFontM,
                    dispText,
                    new Matrix3x2f(pose),
                    textLeft,
                    searchY + searchH / 2.0f - mediumFontM.getHeight(7.0f) / 2.0f,
                    7.0f,
                    txtColor,
                    searchScissor
            ));

            // Cursor blink
            if (searchInputActive && searchCursorVisible) {
                float textW = mediumFontM.getWidth(searchInputText.substring(0, searchCursor), 7.0f);
                graphics.guiRenderState.addGuiElement(new RoundedRectangleRenderState(
                        HaloRenderPipelines.ROUNDED_RECT,
                        pose,
                        textLeft + textW, searchY + 3.0f, 1.0f, searchH - 6.0f,
                        ARGB.color(alphaInt, 255, 255, 255),
                        0.0f,
                        searchScissor
                ));
            }

            // Clear button (X) on the right
            if (!searchInputText.isEmpty()) {
                float clearBtnW = 8.0f;
                float clearBtnH = 8.0f;
                float clearBtnX = searchX + searchW - 12.0f;
                float clearBtnY = searchY + (searchH - clearBtnH) / 2.0f;
                boolean clearHovered = isHovered(mouseX, mouseY, clearBtnX - 3.0f, clearBtnY - 3.0f, clearBtnW + 6.0f, clearBtnH + 6.0f);
                int clearColor = clearHovered ? ARGB.color(alphaInt, 255, 255, 255) : ARGB.color(alphaInt, 161, 161, 170);

                graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                        materialIconFont,
                        "\uE5CD", // 'close' icon
                        new Matrix3x2f(pose),
                        clearBtnX + clearBtnW / 2.0f - materialIconFont.getWidth("\uE5CD", 7.5f) / 2f,
                        clearBtnY + clearBtnH / 2.0f - materialIconFont.getHeight(7.5f) / 2f,
                        7.5f,
                        clearColor,
                        scissor
                ));
            }
        }

        // Extract Status text (Now Playing / Idle) when search input is empty
        if (searchInputText.isEmpty()) {
            var statusFont = MsdfFontManager.getFont("productsans-semibold", 6.0f);
            if (statusFont != null) {
                SpotifyManager.MediaStatus status = SpotifyManager.getStatus();
                String statusText = (status != null && status.hasMedia()) ? "Now playing" : "Idle";
                int statusCol = ARGB.color((int) (140 * scale), 161, 161, 170);
                float statusW = statusFont.getWidth(statusText, 6.0f);

            }
        }

        // Extract Setup button (source-aware) if search is empty
        if (searchInputText.isEmpty()) {
            if (!MusicManager.isConfigured()) {
                float btnW = 80.0f;
                float btnH = 15.0f;
                float btnX = x + (WIDTH - btnW) / 2.0f;
                float btnY = y + HEIGHT - btnH - PADDING;
                boolean hovered = isHovered(mouseX, mouseY, btnX, btnY, btnW, btnH);

                int btnBg = ARGB.color((int) (alphaScale * 0.7f), 47, 47, 47);
                int btnText = hovered ? ARGB.color(alphaScale, 250, 250, 250) : ARGB.color(alphaScale, 161, 161, 170);

                // Button Background
                graphics.guiRenderState.addGuiElement(new RoundedRectangleRenderState(
                        HaloRenderPipelines.ROUNDED_RECT,
                        pose,
                        btnX, btnY, btnW, btnH,
                        btnBg,
                        8.0f,
                        scissor
                ));
                if (hovered) {
                    graphics.guiRenderState.addGuiElement(new RoundedRectangleRenderState(
                            HaloRenderPipelines.ROUNDED_RECT,
                            pose,
                            btnX, btnY, btnW, btnH,
                            ARGB.color((int) (alphaScale * 0.10f), 255, 255, 255),
                            8.0f,
                            scissor
                    ));
                }

                var statusFont = MsdfFontManager.getFont("productsans-semibold", 7.0f);
                if (statusFont != null) {
                    String setupLabel = "Setup " + MusicManager.sourceDisplayName();
                    float textW = statusFont.getWidth(setupLabel, 7.0f);
                    graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                            statusFont,
                            setupLabel,
                            new Matrix3x2f(pose),
                            btnX + btnW / 2.0f - textW / 2.0f,
                            btnY + btnH / 2.0f - statusFont.getHeight(7.0f) / 2.0f,
                            7.0f,
                            btnText,
                            scissor
                    ));
                }
            }
        } else {
            // Draw filter buttons
            var filterFont = MsdfFontManager.getFont("productsans-semibold", 6.0f);
            if (filterFont != null) {
                float totalFiltersW = 0;
                float gap = 4.0f;
                for (var f : SpotifyManager.SearchFilter.values()) {
                    totalFiltersW += filterFont.getWidth(f.getDisplayName(), 6.0f) + 8.0f + gap;
                }
                totalFiltersW -= gap;
                float filtersStartX = x + WIDTH - PADDING - totalFiltersW - 2.0f;

                // Draw filter buttons background container (only behind the buttons)
                graphics.guiRenderState.addGuiElement(new BlurredRoundedRectangleRenderState(
                        HaloRenderPipelines.ROUNDED_BLUR,
                        textureSetup,
                        pose,
                        filtersStartX - 2.0f, y + 24.0f, totalFiltersW + 4.0f, 12.0f,
                        ARGB.color((int) (165 * scale), 0, 0, 0),
                        3.0f,
                        200.0f,
                        0.0f,
                        scissor
                ));

                var labelFont = MsdfFontManager.getFont("productsans-medium", 6.0f);
                if (labelFont != null) {
                    graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                            labelFont,
                            "Filters:",
                            new Matrix3x2f(pose),
                            x + PADDING + 2.0f,
                            y + 25.0f + 5.0f - labelFont.getHeight(6.0f) / 2.0f,
                            6.0f,
                            ARGB.color((int) (150 * scale), 161, 161, 170),
                            scissor
                    ));
                }

                float activeX = filtersStartX;
                float activeW = 0.0f;
                float currentX = filtersStartX;
                for (var f : SpotifyManager.SearchFilter.values()) {
                    float btnW = filterFont.getWidth(f.getDisplayName(), 6.0f) + 8.0f;
                    if (currentSearchFilter == f) {
                        activeX = currentX;
                        activeW = btnW;
                    }
                    currentX += btnW + gap;
                }

                // Run sliding active indicator animation
                if (filterIndicatorX.getValue() == 0.0f) {
                    filterIndicatorX.setStartValue(activeX);
                    filterIndicatorWidth.setStartValue(activeW);
                }
                filterIndicatorX.run(activeX);
                filterIndicatorWidth.run(activeW);

                // Draw sliding active indicator background (green)
                float indX = filterIndicatorX.getValue();
                float indW = filterIndicatorWidth.getValue();
                graphics.guiRenderState.addGuiElement(new BlurredRoundedRectangleRenderState(
                        HaloRenderPipelines.ROUNDED_BLUR,
                        textureSetup,
                        pose,
                        indX, y + 25.5f, indW, 9.0f,
                        ARGB.color((int)(255 * scale), 29, 185, 84), // Spotify green
                        2.0f,
                        200.0f,
                        0.0f,
                        scissor
                ));

                currentX = filtersStartX;
                for (var f : SpotifyManager.SearchFilter.values()) {
                    float btnW = filterFont.getWidth(f.getDisplayName(), 6.0f) + 8.0f;
                    float btnH = 9.0f;
                    float btnY = y + 25.5f;
                    boolean active = (currentSearchFilter == f);
                    boolean hovered = isHovered(mouseX, mouseY, currentX, btnY, btnW, btnH);
                    
                    if (!active && hovered) {
                        int bgCol = ARGB.color((int)(185 * scale), 0, 0, 0);
                        graphics.guiRenderState.addGuiElement(new BlurredRoundedRectangleRenderState(
                                HaloRenderPipelines.ROUNDED_BLUR,
                                textureSetup,
                                pose,
                                currentX, btnY, btnW, btnH,
                                bgCol,
                                2.0f,
                                200.0f,
                                0.0f,
                                scissor
                        ));
                    }
                    
                    int txtCol = active ? ARGB.color((int)(255 * scale), 255, 255, 255) : ARGB.color((int)(200 * scale), 220, 220, 220);
                    float textW = filterFont.getWidth(f.getDisplayName(), 6.0f);
                    graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                            filterFont,
                            f.getDisplayName(),
                            new Matrix3x2f(pose),
                            currentX + (btnW - textW) / 2.0f,
                            btnY + btnH / 2.0f - filterFont.getHeight(6.0f) / 2.0f,
                            6.0f,
                            txtCol,
                            scissor
                    ));
                    currentX += btnW + gap;
                }
            }

            // Extract Search Loading State or Results
            if (searchLoading && searchResults.isEmpty()) {
                float loadCenterY = y + 38.0f + (HEIGHT - 38.0f - PADDING) / 2.0f;
                float loadCenterX = x + WIDTH / 2.0f;

                var loadFont = MsdfFontManager.getFont("productsans-semibold", 7.0f);
                if (loadFont != null) {
                    float textW = loadFont.getWidth("Loading...", 7.0f);
                    graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                            loadFont,
                            "Loading...",
                            new Matrix3x2f(pose),
                            loadCenterX - textW / 2.0f,
                            loadCenterY - 5.0f - loadFont.getHeight(7.0f) / 2.0f,
                            7.0f,
                            ARGB.color(255, 161, 161, 170),
                            scissor
                    ));
                }

                // Loading spinner animation (YouTube style)
                long ms = com.haloclient.client.util.FrameClock.millis();
                float circleX = loadCenterX;
                float circleY = loadCenterY + 10.0f;
                float circleRadius = 6.0f;
                float dotSize = 1.5f;

                // Use modulo 6000ms to avoid float precision loss (LCM of 2000ms and 1500ms)
                long relativeMs = ms % 6000L;

                // 1. Base rotation (constant speed)
                float rotationCycle = (relativeMs % 2000) / 2000.0f;
                float baseAngle = rotationCycle * 360.0f;

                // 2. Arc expansion/contraction cycle (1500ms)
                float arcCycle = (relativeMs % 1500) / 1500.0f;
                float headAngle, tailAngle;

                if (arcCycle < 0.5f) {
                    float nt = arcCycle / 0.5f;
                    // Ease-in-out using sine
                    float easedHead = (float) (Math.sin(nt * Math.PI - Math.PI / 2.0) + 1.0) / 2.0f;
                    tailAngle = 0.0f;
                    headAngle = easedHead * 270.0f;
                } else {
                    float nt = (arcCycle - 0.5f) / 0.5f;
                    float easedTail = (float) (Math.sin(nt * Math.PI - Math.PI / 2.0) + 1.0) / 2.0f;
                    tailAngle = easedTail * 270.0f;
                    headAngle = 270.0f;
                }

                // Continuous total angle within the 6000ms loop
                float totalAngle = baseAngle + (float) Math.floor(relativeMs / 1500.0) * 270.0f + tailAngle;
                float sweepAngle = headAngle - tailAngle;
                if (sweepAngle < 15.0f) {
                    sweepAngle = 15.0f;
                }

                // Draw the expanding/contracting arc using the custom SPINNER shader (perfectly smooth, anti-aliased, round-capped)
                float quadSize = 16.0f;
                graphics.guiRenderState.addGuiElement(new RoundedRectangleRenderState(
                        HaloRenderPipelines.SPINNER,
                        pose,
                        circleX - quadSize / 2f, circleY - quadSize / 2f, quadSize, quadSize,
                        ARGB.color(255, 255, 255, 255), ARGB.color(255, 255, 255, 255),
                        circleRadius, 1.2f,
                        0.0f, (float) Math.toRadians(totalAngle), (float) Math.toRadians(sweepAngle),
                        0.0f,
                        scissor
                ));
            } else {
                // Render search results!
                float listStartY = y + 38.0f;
                float itemH = 20.0f;
                float listW = WIDTH - PADDING * 2.0f;
                float listX = x + PADDING;

                var resultFont = MsdfFontManager.getFont("productsans-medium", 6.5f);
                var resultIconFont = MsdfFontManager.getFont("fluid-regular", 10.0f);

                for (int i = 0; i < Math.min(searchResults.size(), 4); i++) {
                    SpotifyManager.SearchResultTrack track = searchResults.get(i);
                    float itemY = listStartY + i * (itemH + 2.0f);

                    // Hover highlight
                    boolean itemHovered = isHovered(mouseX, mouseY, listX, itemY, listW, itemH);
                    if (itemHovered) {
                        graphics.guiRenderState.addGuiElement(new RoundedRectangleRenderState(
                                HaloRenderPipelines.ROUNDED_RECT,
                                pose,
                                listX, itemY, listW, itemH,
                                ARGB.color((int) (255 * 0.2f), 0, 0, 0),
                                4.0f,
                                scissor
                        ));
                    }

                    // Album art on the left
                    float artSize = 16.0f;
                    float artX = listX + 2.0f;
                    float artY = itemY + (itemH - artSize) / 2.0f;

                    ImageManager.CachedImage artCached = null;
                    if (!track.artworkUrl().isEmpty()) {
                        artCached = ImageManager.fromUrl(track.artworkUrl());
                    } else if (!track.localArtworkPath().isEmpty()) {
                        artCached = loadLocalArt(track.localArtworkPath());
                    }
                    if (artCached != null) {
                        graphics.guiRenderState.addGuiElement(new ImageRenderState(
                                HaloRenderPipelines.IMAGE,
                                artCached.textureSetup(),
                                pose,
                                artX, artY, artSize, artSize,
                                ARGB.color(255, 255, 255, 255),
                                2.0f,
                                ImageRenderState.ScaleMode.FILL,
                                artCached.width(), artCached.height(),
                                scissor
                        ));
                    } else {
                        graphics.guiRenderState.addGuiElement(new RoundedRectangleRenderState(
                                HaloRenderPipelines.ROUNDED_RECT,
                                pose,
                                artX, artY, artSize, artSize,
                                ARGB.color(255, 40, 40, 40),
                                2.0f,
                                scissor
                        ));
                    }

                    // Artist - Title next to it
                    float textX = artX + artSize + 6.0f;
                    float textMaxW = listW - artSize - 52.0f;

                    // Scissor search text item
                    ScreenRectangle itemBounds = (new ScreenRectangle(
                             (int) textX, (int) itemY,
                            (int) textMaxW, (int) itemH
                    )).transformMaxBounds(pose);
                    ScreenRectangle itemScissor = scissor != null ? scissor.intersection(itemBounds) : itemBounds;

                    if (resultFont != null) {
                        SpotifyManager.MediaStatus status = SpotifyManager.getStatus();
                        boolean isCurrent = status != null && status.hasMedia() && track.id().equals(status.trackId());
                        int titleColor = isCurrent ? ARGB.color(255, 29, 185, 84) : ARGB.color(255, 255, 255, 255);

                        // Title at the top
                        graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                                resultFont,
                                track.title(),
                                new Matrix3x2f(pose),
                                textX,
                                itemY + 7.0f - resultFont.getHeight(6.5f) / 2.0f,
                                6.5f,
                                titleColor,
                                itemScissor
                        ));
                        // Artist at the bottom
                        graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                                resultFont,
                                track.artist(),
                                new Matrix3x2f(pose),
                                textX,
                                itemY + 14.0f - resultFont.getHeight(5.0f) / 2.0f,
                                5.0f,
                                ARGB.color(255, 161, 161, 170),
                                itemScissor
                        ));
                    }

                    // Play icon & Like icon on the far right
                    if (resultIconFont != null) {
                        float playBtnW = 10.0f;
                        float likeBtnW = 10.0f;
                        float playX = listX + listW - 12.0f;
                        float likeX = playX - 16.0f;
                        float iconY = itemY + itemH / 2.0f;

                        boolean playHovered = isHovered(mouseX, mouseY, playX - 5.0f, itemY, playBtnW + 10.0f, itemH);
                        int playColor = playHovered ? ARGB.color(255, 255, 255, 255) : ARGB.color(255, 161, 161, 170);

                        if (!track.isPlaylist()) {
                            boolean likeHovered = isHovered(mouseX, mouseY, likeX - 5.0f, itemY, likeBtnW + 10.0f, itemH);
                            int likeColor = track.liked() ? ARGB.color(255, 29, 185, 84) : (likeHovered ? ARGB.color(255, 255, 255, 255) : ARGB.color(255, 161, 161, 170));

                            graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                                    resultIconFont,
                                    track.liked() ? "D" : "D",
                                    new Matrix3x2f(pose),
                                    likeX - resultIconFont.getWidth(track.liked() ? "D" : "D", 16.0f) / 2.0f,
                                    iconY - resultIconFont.getHeight(16.0f) / 2.0f,
                                    16.0f,
                                    likeColor,
                                    scissor
                            ));
                        }

                        graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                                resultIconFont,
                                "B",
                                new Matrix3x2f(pose),
                                playX - resultIconFont.getWidth("B", 16.0f) / 2.0f,
                                iconY - resultIconFont.getHeight(16.0f) / 2.0f,
                                16.0f,
                                playColor,
                                scissor
                        ));
                    }
                }
            }
        }

        if (searchInputText.isEmpty()) {
            var font = MsdfFontManager.getFont("productsans-medium", 6f);
            if (font != null) {
                graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                        font,
                        "Main",
                        new Matrix3x2f(pose),
                        x + PADDING +4,
                        y + 34.5f - font.getHeight(6f) / 2f,
                        6f,
                        ARGB.color(255, 161, 161, 170),
                        scissor
                ));
            }
            float colWidth = 103.0f;
            if (font != null) {
                graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                        font,
                        "Settings",
                        new Matrix3x2f(pose),
                        x + WIDTH - PADDING - colWidth +4f,
                        y + 34.5f - font.getHeight(6f) / 2f,
                        6f,
                        ARGB.color(255, 161, 161, 170),
                        scissor
                ));
            }
        }
        loadAssets();
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        float x = resolvePanelX(this.width);
        float y = resolvePanelY(this.height);
        float centerX = x + WIDTH / 2.0f;
        float centerY = y + HEIGHT / 2.0f;

        double rawMouseX = mouseX;
        double rawMouseY = mouseY;

        mouseX = centerX + (mouseX - centerX) / guiScale;
        mouseY = centerY + (mouseY - centerY) / guiScale;

        if (button == 0) {
            // Search input click
            float searchX = x + 125.0f;
            float searchY = y + 7.5f;
            float searchW = 97.0f;
            float searchH = 14.0f;

            boolean hasText = !searchInputText.isEmpty();
            float clearBtnW = 8.0f;
            float clearBtnH = 8.0f;
            float clearBtnX = searchX + searchW - 12.0f;
            float clearBtnY = searchY + (searchH - clearBtnH) / 2.0f;
            if (hasText && isHovered(mouseX, mouseY, clearBtnX - 3.0f, clearBtnY - 3.0f, clearBtnW + 6.0f, clearBtnH + 6.0f)) {
                searchInputText = "";
                searchCursor = 0;
                searchInputActive = false;
                clearSearchSelection();
                triggerSearch("");
                return true;
            }

            if (isHovered(mouseX, mouseY, searchX, searchY, searchW, searchH)) {
                searchInputActive = true;
                searchLastBlinkTime = System.currentTimeMillis();
                searchCursorVisible = true;
                searchCursor = searchInputText.length();
                hexInputActive = false;
                return true;
            } else {
                searchInputActive = false;
            }

            // Filter buttons click check
            if (!searchInputText.isEmpty()) {
                var filterFont = MsdfFontManager.getFont("productsans-semibold", 6.0f);
                if (filterFont != null) {
                    float totalFiltersW = 0;
                    float gap = 4.0f;
                    for (var f : SpotifyManager.SearchFilter.values()) {
                        totalFiltersW += filterFont.getWidth(f.getDisplayName(), 6.0f) + 8.0f + gap;
                    }
                    totalFiltersW -= gap;
                    float filtersStartX = x + WIDTH - PADDING - totalFiltersW - 2.0f;
                    float currentX = filtersStartX;
                    for (var f : SpotifyManager.SearchFilter.values()) {
                        float btnW = filterFont.getWidth(f.getDisplayName(), 6.0f) + 8.0f;
                        float btnH = 10.0f;
                        float btnY = y + 25.0f;
                        if (isHovered(mouseX, mouseY, currentX, btnY, btnW, btnH)) {
                            setAndTriggerSearchFilter(f);
                            return true;
                        }
                        currentX += btnW + gap;
                    }
                }
            }

            // Search result item click check (Play / Like)
            if (!searchInputText.isEmpty()) {
                float listStartY = y + 38.0f;
                float itemH = 20.0f;
                float listW = WIDTH - PADDING * 2.0f;
                float listX = x + PADDING;

                for (int i = 0; i < Math.min(searchResults.size(), 4); i++) {
                    SpotifyManager.SearchResultTrack track = searchResults.get(i);
                    float itemY = listStartY + i * (itemH + 2.0f);
                    
                    float playBtnW = 10.0f;
                    float likeBtnW = 10.0f;
                    float playX = listX + listW - 12.0f;
                    float likeX = playX - 16.0f;
                    
                    boolean subsonicSource = MusicManager.getActiveSource() == MusicManager.Source.SUBSONIC;
                    if (isHovered(mouseX, mouseY, playX - 5.0f, itemY, playBtnW + 10.0f, itemH)) {
                        if (subsonicSource) {
                            SubsonicManager.getInstance().playSearchResult(track.id(), track.title(), track.artist(), track.localArtworkPath());
                        } else if (track.isPlaylist()) {
                            SpotifyManager.getInstance().playPlaylist(track.id());
                        } else {
                            SpotifyManager.getInstance().playTrack(track.id());
                        }
                        return true;
                    }

                    if (isHovered(mouseX, mouseY, likeX - 5.0f, itemY, likeBtnW + 10.0f, itemH)) {
                        if (subsonicSource) {
                            SubsonicManager.getInstance().star(track.id(), !track.liked());
                            searchResults.set(i, new SpotifyManager.SearchResultTrack(
                                track.id(), track.title(), track.artist(), track.artworkUrl(), track.localArtworkPath(), !track.liked(), false
                            ));
                        } else if (!track.isPlaylist()) {
                            SpotifyManager.getInstance().likeTrack(track.id(), !track.liked());
                            searchResults.set(i, new SpotifyManager.SearchResultTrack(
                                track.id(), track.title(), track.artist(), track.artworkUrl(), track.localArtworkPath(), !track.liked(), false
                            ));
                        }
                        return true;
                    }
                }
                
                // Block clicks on background widgets if search results are displayed
                if (isHovered(mouseX, mouseY, listX, listStartY, listW, HEIGHT - 38.0f - PADDING)) {
                    return true;
                }
            }

            // Handle color picker popup clicks first (modal)
            if (colorPickerOpen) {
                float popupX = resolvePopupX(x);
                float popupY = resolvePopupY(y);
                float pad = 6.0f;
                float innerW = POPUP_WIDTH - 2 * pad;
                float hueBarW = 8.0f;
                float gap = 6.0f;
                float sbW = innerW - hueBarW - gap;
                float sbH = 65.0f;
                float alphaBarH = 8.0f;
                float hexH = 14.0f;

                // Combobox coordinates
                float comboX = popupX + pad;
                float comboY = popupY + pad;
                float comboW = 80.0f;
                float comboH = 13.0f;
                float gapBetweenComboAndSB = 5.0f;

                // Close button coordinates
                float closeBtnX = popupX + POPUP_WIDTH - pad - 13.0f;
                float closeBtnY = comboY;
                float closeBtnW = 13.0f;
                float closeBtnH = 13.0f;

                float sbX = popupX + pad;
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

                // 0. Close button click
                if (isHovered(mouseX, mouseY, closeBtnX, closeBtnY, closeBtnW, closeBtnH)) {
                    colorPickerOpen = false;
                    hexInputActive = false;
                    comboboxOpen = false;
                    return true;
                }

                // 1. Dropdown options click
                if (comboboxOpen) {
                    float listY = comboY + comboH + 1.0f;
                    float optionH = 12.0f;
                    for (int i = 0; i < ColorTarget.values().length; i++) {
                        float optY = listY + i * optionH;
                        if (isHovered(mouseX, mouseY, comboX, optY, comboW, optionH)) {
                            selectedTarget = ColorTarget.values()[i];
                            comboboxOpen = false;
                            
                            // Load color from target
                            if (selectedTarget == ColorTarget.BACKGROUND) {
                                colorPickerColor = MusicDisplayOverlay.backgroundColor;
                            } else if (selectedTarget == ColorTarget.TITLE) {
                                colorPickerColor = MusicDisplayOverlay.titleColor;
                            } else if (selectedTarget == ColorTarget.ARTIST) {
                                colorPickerColor = MusicDisplayOverlay.artistColor;
                            } else if (selectedTarget == ColorTarget.TIME) {
                                colorPickerColor = MusicDisplayOverlay.timeColor;
                            } else if (selectedTarget == ColorTarget.PROGRESS_BAR) {
                                colorPickerColor = MusicDisplayOverlay.progressColor;
                            }
                            updateHSBFromColor();
                            animatedCpSat = cpSat;
                            animatedCpBri = cpBri;
                            return true;
                        }
                    }
                    // Clicked inside popup while combobox was open, but not on an option -> close dropdown
                    comboboxOpen = false;
                    return true;
                }

                // 2. Combobox box click
                if (isHovered(mouseX, mouseY, comboX, comboY, comboW, comboH)) {
                    comboboxOpen = !comboboxOpen;
                    hexInputActive = false;
                    return true;
                }

                // SB area click
                if (isHovered(mouseX, mouseY, sbX, sbY, sbW, sbH)) {
                    draggingSB = true;
                    hexInputActive = false;
                    updateSB((float)mouseX, (float)mouseY, sbX, sbY, sbW, sbH);
                    return true;
                }
                // Hue bar click
                if (isHovered(mouseX, mouseY, hueX, hueBarY, hueBarW, sbH)) {
                    draggingHue = true;
                    hexInputActive = false;
                    updateHueFromMouse((float)mouseY, hueBarY, sbH);
                    return true;
                }
                // Alpha slider click
                if (isHovered(mouseX, mouseY, alphaSliderX, alphaSliderY, innerW, alphaBarH)) {
                    draggingCpAlpha = true;
                    hexInputActive = false;
                    updateAlphaFromMouse((float)mouseX, alphaSliderX, innerW);
                    return true;
                }
                // Hex input click
                if (isHovered(mouseX, mouseY, hexX, hexY, inputW, hexH)) {
                    hexInputActive = true;
                    resetCursorBlink();
                    clearHexSelection();
                    // Position cursor based on click
                    if (checkboxFont != null) {
                        float hashW = checkboxFont.getStringWidth("#", 6.0f);
                        float clickRelX = (float)mouseX - hexX - 4.0f - hashW;
                        if (clickRelX <= 0) {
                            hexCursor = 0;
                        } else {
                            float textW = checkboxFont.getStringWidth(hexInputText, 6.0f);
                            if (textW > 0) {
                                hexCursor = Math.round(clickRelX / textW * hexInputText.length());
                                hexCursor = Math.max(0, Math.min(hexCursor, hexInputText.length()));
                            }
                        }
                    }
                    return true;
                }
                // Copy button click
                if (isHovered(mouseX, mouseY, copyBtnX, hexY, btnW, hexH)) {
                    copyHexToClipboard(Minecraft.getInstance().getWindow().handle());
                    return true;
                }
                // Paste button click
                if (isHovered(mouseX, mouseY, pasteBtnX, hexY, btnW, hexH)) {
                    pasteHexFromClipboard(Minecraft.getInstance().getWindow().handle());
                    return true;
                }
                // Click inside popup but not on a control
                if (isHovered(mouseX, mouseY, popupX, popupY, POPUP_WIDTH, POPUP_HEIGHT)) {
                    hexInputActive = false;
                    return true;
                }
                // Click outside popup - close it
                colorPickerOpen = false;
                hexInputActive = false;
                return true;
            }

            float colWidth = 103.0f;
            float leftColX = x + PADDING - 10;
            float rightColX = x + WIDTH - PADDING - colWidth;

            // Handle style combo dropdown click first
            if (bgStyleComboOpen) {
                float styleLy = y + 99.0f;
                float styleCardH = 13.0f;
                float styleCardY = styleLy - styleCardH / 2.0f;
                float styleRectW = 55.0f;
                float styleRectX = rightColX + colWidth - styleRectW - 4.0f;
                float styleListY = styleCardY + styleCardH + 1.0f;
                float styleOptionH = 12.0f;

                for (int i = 0; i < MusicDisplayOverlay.BackgroundType.values().length; i++) {
                    float optY = styleListY + i * styleOptionH;
                    if (isHovered(mouseX, mouseY, styleRectX, optY, styleRectW, styleOptionH)) {
                        MusicDisplayOverlay.setBackgroundType(MusicDisplayOverlay.BackgroundType.values()[i]);
                        bgStyleComboOpen = false;
                        return true;
                    }
                }
                // Clicked outside dropdown options - close it
                bgStyleComboOpen = false;
                return true;
            }

            // Glass Style Combo Box click
            float styleLy = y + 99.0f;
            float styleCardH = 13.0f;
            float styleCardY = styleLy - styleCardH / 2.0f;
            float styleRectW = 55.0f;
            float styleRectX = rightColX + colWidth - styleRectW - 4.0f;
            if (isHovered(mouseX, mouseY, styleRectX, styleCardY, styleRectW, styleCardH)) {
                bgStyleComboOpen = !bgStyleComboOpen;
                return true;
            }

            // Enabled checkbox click (clicking anywhere on the checkbox card area)
            if (isHovered(mouseX, mouseY, x + PADDING, y + 47.0f - 6.5f, colWidth, 13.0f)) {
                MusicDisplayOverlay.setVisible(!MusicDisplayOverlay.isOpened());
                return true;
            }

            // Show Controls checkbox click
            if (isHovered(mouseX, mouseY, x + PADDING, y + 60.0f - 6.5f, colWidth, 13.0f)) {
                MusicDisplayOverlay.setShowControls(!MusicDisplayOverlay.isShowControls());
                return true;
            }

            // Show Next Song checkbox click
            if (isHovered(mouseX, mouseY, x + PADDING, y + 73.0f - 6.5f, colWidth, 13.0f)) {
                MusicDisplayOverlay.setShowNextSong(!MusicDisplayOverlay.isShowNextSong());
                return true;
            }

            // Sliders click (clicking anywhere on the slider card area)
            if (isHovered(mouseX, mouseY, rightColX, y + 47.0f - 6.5f, colWidth, 13.0f)) {
                draggingBlurSlider = true;
                updateBlurSlider((float) mouseX, rightColX, colWidth);
                return true;
            }
            if (isHovered(mouseX, mouseY, rightColX, y + 60.0f - 6.5f, colWidth, 13.0f)
                    && MusicDisplayOverlay.getBackgroundType() != MusicDisplayOverlay.BackgroundType.LIQUID_GLASS) {
                draggingBloomSlider = true;
                updateBloomSlider((float) mouseX, rightColX, colWidth);
                return true;
            }
            if (isHovered(mouseX, mouseY, rightColX, y + 73.0f - 6.5f, colWidth, 13.0f)) {
                draggingScaleSlider = true;
                dragStartGuiScale = guiScale;
                updateScaleSlider((float) mouseX, rightColX, colWidth);
                return true;
            }

            // Color picker card click - open popup
            if (isHovered(mouseX, mouseY, rightColX, y + 86.0f - 6.5f, colWidth, 13.0f)) {
                colorPickerOpen = true;
                selectedTarget = ColorTarget.BACKGROUND;
                comboboxOpen = false;
                
                // Initialize popup state from current color
                colorPickerColor = MusicDisplayOverlay.backgroundColor;
                updateHSBFromColor();
                animatedCpSat = cpSat;
                animatedCpBri = cpBri;
                hexInputActive = false;
                return true;
            }

            // Setup button click (source-aware)
            if (!MusicManager.isConfigured()) {
                float btnW = 80.0f;
                float btnH = 15.0f;
                float btnX = x + (WIDTH - btnW) / 2.0f;
                float btnY = y + HEIGHT - btnH - PADDING;
                if (isHovered(mouseX, mouseY, btnX, btnY, btnW, btnH)) {
                    if (MusicManager.getActiveSource() == MusicManager.Source.SUBSONIC) {
                        SubsonicManager.getInstance().startSetupServer();
                        openUrl("http://127.0.0.1:8889/setup");
                    } else {
                        SpotifyManager.getInstance().startSetupServer();
                        openUrl("http://127.0.0.1:8888/setup");
                    }
                    return true;
                }
            }
        }

        // Right-click the header strip toggles the active music source (Spotify <-> Subsonic)
        if (button == 1 && isHovered(rawMouseX, rawMouseY, x, y, WIDTH, 25.0f)) {
            MusicManager.toggleSource();
            return true;
        }

        // Dragging window check (using raw coordinates)
        if (button == 0 && isHovered(rawMouseX, rawMouseY, x, y, WIDTH, 25.0f)) {
            dragging = true;
            dragOffsetX = (float) (rawMouseX - x);
            dragOffsetY = (float) (rawMouseY - y);
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        float x = resolvePanelX(this.width);
        float y = resolvePanelY(this.height);
        float centerX = x + WIDTH / 2.0f;
        float centerY = y + HEIGHT / 2.0f;

        float sMouseX = (float) (centerX + (event.x() - centerX) / guiScale);
        float sMouseY = (float) (centerY + (event.y() - centerY) / guiScale);

        // Color picker popup drags
        if (draggingSB) {
            float popupX = resolvePopupX(x);
            float popupY = resolvePopupY(y);
            float pad = 6.0f;
            float innerW = POPUP_WIDTH - 2 * pad;
            float hueBarW = 8.0f;
            float gap = 6.0f;
            float sbW = innerW - hueBarW - gap;
            float sbH = 65.0f;
            
            float comboH = 13.0f;
            float gapBetweenComboAndSB = 5.0f;
            
            float sbX = popupX + pad;
            float sbY = popupY + pad + comboH + gapBetweenComboAndSB;
            updateSB(sMouseX, sMouseY, sbX, sbY, sbW, sbH);
            return true;
        }
        if (draggingHue) {
            float popupX = resolvePopupX(x);
            float popupY = resolvePopupY(y);
            float pad = 6.0f;
            float sbH = 65.0f;
            
            float comboH = 13.0f;
            float gapBetweenComboAndSB = 5.0f;
            
            float hueBarY = popupY + pad + comboH + gapBetweenComboAndSB;
            updateHueFromMouse(sMouseY, hueBarY, sbH);
            return true;
        }
        if (draggingCpAlpha) {
            float popupX = resolvePopupX(x);
            float popupY = resolvePopupY(y);
            float pad = 6.0f;
            float innerW = POPUP_WIDTH - 2 * pad;
            float alphaSliderX = popupX + pad;
            updateAlphaFromMouse(sMouseX, alphaSliderX, innerW);
            return true;
        }

        // Slider drags
        float colWidth = 103.0f;
        float rightColX = x + WIDTH - PADDING - colWidth;
        if (draggingBlurSlider) {
            updateBlurSlider(sMouseX, rightColX, colWidth);
            return true;
        }
        if (draggingBloomSlider) {
            updateBloomSlider(sMouseX, rightColX, colWidth);
            return true;
        }
        if (draggingScaleSlider) {
            // Use frozen guiScale from drag start to avoid feedback loop
            float fixedMouseX = (float) (centerX + (event.x() - centerX) / dragStartGuiScale);
            updateScaleSlider(fixedMouseX, rightColX, colWidth);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        draggingBlurSlider = false;
        draggingBloomSlider = false;
        draggingScaleSlider = false;
        draggingSB = false;
        draggingHue = false;
        draggingCpAlpha = false;
        return super.mouseReleased(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static float resolvePanelX(int screenWidth) {
        if (Float.isNaN(panelX)) {
            return (screenWidth - WIDTH) / 2.0f;
        }
        return panelX;
    }

    private static float resolvePanelY(int screenHeight) {
        if (Float.isNaN(panelY)) {
            return (screenHeight - HEIGHT) / 2.0f;
        }
        return panelY;
    }

    public static boolean isHovered(double mouseX, double mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private static void updateBlurSlider(float mouseX, float rightColX, float colWidth) {
        float labelWidth = 25.0f;
        float trackX = rightColX + labelWidth + 4.0f;
        float trackWidth = colWidth - labelWidth - 15.0f - 12.0f;
        float normalized = Math.max(0.0f, Math.min(1.0f, (mouseX - trackX) / trackWidth));
        MusicDisplayOverlay.setBlurStrength(normalized * 30.0f);
    }

    private static void updateBloomSlider(float mouseX, float rightColX, float colWidth) {
        float labelWidth = 25.0f;
        float trackX = rightColX + labelWidth + 4.0f;
        float trackWidth = colWidth - labelWidth - 15.0f - 12.0f;
        float normalized = Math.max(0.0f, Math.min(1.0f, (mouseX - trackX) / trackWidth));
        MusicDisplayOverlay.setBloomStrength(normalized * 20.0f);
    }

    private static void updateScaleSlider(float mouseX, float rightColX, float colWidth) {
        float labelWidth = 25.0f;
        float trackX = rightColX + labelWidth + 4.0f;
        float trackWidth = colWidth - labelWidth - 15.0f - 12.0f;
        float normalized = Math.max(0.0f, Math.min(1.0f, (mouseX - trackX) / trackWidth));
        guiScale = 0.5f + normalized * 1.0f;
    }

    // === Color picker update methods ===

    private void updateSB(float mouseX, float mouseY, float sbX, float sbY, float sbW, float sbH) {
        cpSat = Math.max(0.0f, Math.min(1.0f, (mouseX - sbX) / sbW));
        cpBri = Math.max(0.0f, Math.min(1.0f, 1.0f - (mouseY - sbY) / sbH));
        updateColorFromHSB();
    }

    private void updateHueFromMouse(float mouseY, float hueY, float hueH) {
        cpHue = Math.max(0.0f, Math.min(1.0f, (mouseY - hueY) / hueH));
        updateColorFromHSB();
    }

    private void updateAlphaFromMouse(float mouseX, float alphaX, float alphaW) {
        cpAlpha = Math.max(0.0f, Math.min(1.0f, (mouseX - alphaX) / alphaW));
        updateColorFromHSB();
    }

    private void updateColorFromHSB() {
        int rgb = Color.HSBtoRGB(cpHue, cpSat, cpBri);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int a = (int)(cpAlpha * 255);
        colorPickerColor = ARGB.color(a, r, g, b);
        if (selectedTarget == ColorTarget.BACKGROUND) {
            MusicDisplayOverlay.backgroundColor = colorPickerColor;
        } else if (selectedTarget == ColorTarget.TITLE) {
            MusicDisplayOverlay.titleColor = colorPickerColor;
        } else if (selectedTarget == ColorTarget.ARTIST) {
            MusicDisplayOverlay.artistColor = colorPickerColor;
        } else if (selectedTarget == ColorTarget.TIME) {
            MusicDisplayOverlay.timeColor = colorPickerColor;
        } else if (selectedTarget == ColorTarget.PROGRESS_BAR) {
            MusicDisplayOverlay.progressColor = colorPickerColor;
        }
        hexInputText = String.format("%02X%02X%02X%02X", r, g, b, a);
        hexCursor = Math.min(hexCursor, hexInputText.length());
    }

    // === Keyboard event handling ===

    public boolean onKeyEvent(int keyCode, int action, long window) {
        if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_REPEAT) return false;

        // Handle Escape to close popup or deactivate hex/search input
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (searchInputActive) {
                searchInputActive = false;
                return true;
            }
            if (hexInputActive) {
                hexInputActive = false;
                updateHexFromColor();
                return true;
            } else if (colorPickerOpen) {
                colorPickerOpen = false;
                return true;
            }
            return false;
        }

        if (searchInputActive) {
            boolean ctrl = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                        || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
            boolean shift = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                         || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

            if (ctrl) {
                switch (keyCode) {
                    case GLFW.GLFW_KEY_A -> { searchSelStart = 0; searchSelEnd = searchInputText.length(); searchCursor = searchSelEnd; }
                    case GLFW.GLFW_KEY_C -> copySearchToClipboard(window);
                    case GLFW.GLFW_KEY_V -> pasteSearchFromClipboard(window);
                    case GLFW.GLFW_KEY_X -> { copySearchToClipboard(window); deleteSearchSelection(); triggerSearch(searchInputText); }
                }
                searchLastBlinkTime = System.currentTimeMillis();
                searchCursorVisible = true;
                return true;
            }

            // Normal typing (replaces selection if active)
            char ch = keyToChar(keyCode, window);
            if (ch != 0) {
                deleteSearchSelection();
                searchInputText = searchInputText.substring(0, searchCursor) + ch + searchInputText.substring(searchCursor);
                searchCursor++;
                triggerSearch(searchInputText);
                searchLastBlinkTime = System.currentTimeMillis();
                searchCursorVisible = true;
                return true;
            }

            switch (keyCode) {
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (hasSearchSelection()) {
                        deleteSearchSelection();
                    } else if (searchCursor > 0) {
                        searchInputText = searchInputText.substring(0, searchCursor - 1) + searchInputText.substring(searchCursor);
                        searchCursor--;
                    }
                    triggerSearch(searchInputText);
                    searchLastBlinkTime = System.currentTimeMillis();
                    searchCursorVisible = true;
                }
                case GLFW.GLFW_KEY_DELETE -> {
                    if (hasSearchSelection()) {
                        deleteSearchSelection();
                    } else if (searchCursor < searchInputText.length()) {
                        searchInputText = searchInputText.substring(0, searchCursor) + searchInputText.substring(searchCursor + 1);
                    }
                    triggerSearch(searchInputText);
                    searchLastBlinkTime = System.currentTimeMillis();
                    searchCursorVisible = true;
                }
                case GLFW.GLFW_KEY_LEFT -> {
                    if (shift) {
                        if (searchSelStart < 0) { searchSelStart = searchCursor; searchSelEnd = searchCursor; }
                        if (searchCursor > 0) { searchCursor--; searchSelEnd = searchCursor; }
                    } else {
                        if (searchCursor > 0) searchCursor--;
                        clearSearchSelection();
                    }
                    searchLastBlinkTime = System.currentTimeMillis();
                    searchCursorVisible = true;
                }
                case GLFW.GLFW_KEY_RIGHT -> {
                    if (shift) {
                        if (searchSelStart < 0) { searchSelStart = searchCursor; searchSelEnd = searchCursor; }
                        if (searchCursor < searchInputText.length()) { searchCursor++; searchSelEnd = searchCursor; }
                    } else {
                        if (searchCursor < searchInputText.length()) searchCursor++;
                        clearSearchSelection();
                    }
                    searchLastBlinkTime = System.currentTimeMillis();
                    searchCursorVisible = true;
                }
                case GLFW.GLFW_KEY_HOME -> {
                    searchCursor = 0;
                    if (!shift) clearSearchSelection();
                    else { if (searchSelStart < 0) { searchSelStart = searchCursor; } searchSelEnd = 0; }
                    searchLastBlinkTime = System.currentTimeMillis();
                    searchCursorVisible = true;
                }
                case GLFW.GLFW_KEY_END -> {
                    searchCursor = searchInputText.length();
                    if (!shift) clearSearchSelection();
                    else { if (searchSelStart < 0) { searchSelStart = searchCursor; } searchSelEnd = searchInputText.length(); }
                    searchLastBlinkTime = System.currentTimeMillis();
                    searchCursorVisible = true;
                }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    searchInputActive = false;
                }
            }
            return true;
        }

        if (!hexInputActive) return false;

        boolean ctrl = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        boolean shift = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                     || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

        if (ctrl) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_A -> { hexSelStart = 0; hexSelEnd = hexInputText.length(); hexCursor = hexSelEnd; }
                case GLFW.GLFW_KEY_C -> copyHexToClipboard(window);
                case GLFW.GLFW_KEY_V -> pasteHexFromClipboard(window);
                case GLFW.GLFW_KEY_X -> { copyHexToClipboard(window); deleteHexSelection(); applyHexInput(); }
            }
            resetCursorBlink();
            return true;
        }

        // Hex character input
        char ch = keyToHexChar(keyCode);
        if (ch != 0) {
            deleteHexSelection();
            if (hexInputText.length() < 8) {
                hexInputText = hexInputText.substring(0, hexCursor) + ch + hexInputText.substring(hexCursor);
                hexCursor++;
            }
            applyHexInput();
            resetCursorBlink();
            return true;
        }

        // Control keys
        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (hasHexSelection()) {
                    deleteHexSelection();
                } else if (hexCursor > 0) {
                    hexInputText = hexInputText.substring(0, hexCursor - 1) + hexInputText.substring(hexCursor);
                    hexCursor--;
                }
                applyHexInput();
                resetCursorBlink();
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (hasHexSelection()) {
                    deleteHexSelection();
                } else if (hexCursor < hexInputText.length()) {
                    hexInputText = hexInputText.substring(0, hexCursor) + hexInputText.substring(hexCursor + 1);
                }
                applyHexInput();
                resetCursorBlink();
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (shift) {
                    if (hexSelStart < 0) { hexSelStart = hexCursor; hexSelEnd = hexCursor; }
                    if (hexCursor > 0) { hexCursor--; hexSelEnd = hexCursor; }
                } else {
                    if (hexCursor > 0) hexCursor--;
                    clearHexSelection();
                }
                resetCursorBlink();
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (shift) {
                    if (hexSelStart < 0) { hexSelStart = hexCursor; hexSelEnd = hexCursor; }
                    if (hexCursor < hexInputText.length()) { hexCursor++; hexSelEnd = hexCursor; }
                } else {
                    if (hexCursor < hexInputText.length()) hexCursor++;
                    clearHexSelection();
                }
                resetCursorBlink();
            }
            case GLFW.GLFW_KEY_HOME -> {
                hexCursor = 0;
                if (!shift) clearHexSelection();
                else { if (hexSelStart < 0) { hexSelStart = hexCursor; } hexSelEnd = 0; }
                resetCursorBlink();
            }
            case GLFW.GLFW_KEY_END -> {
                hexCursor = hexInputText.length();
                if (!shift) clearHexSelection();
                else { if (hexSelStart < 0) { hexSelStart = hexCursor; } hexSelEnd = hexInputText.length(); }
                resetCursorBlink();
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                hexInputActive = false;
                applyHexInput();
            }
            default -> { return false; }
        }
        return true;
    }

    // === Hex input helper methods ===

    private static char keyToHexChar(int keyCode) {
        if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
            return (char)('0' + (keyCode - GLFW.GLFW_KEY_0));
        }
        if (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_F) {
            return (char)('A' + (keyCode - GLFW.GLFW_KEY_A));
        }
        return 0;
    }

    public boolean hasHexSelection() {
        return hexInputActive && hexSelStart >= 0 && hexSelStart != hexSelEnd;
    }

    private void clearHexSelection() {
        hexSelStart = -1;
        hexSelEnd = -1;
    }

    private void deleteHexSelection() {
        if (!hasHexSelection()) return;
        int selMin = Math.min(hexSelStart, hexSelEnd);
        int selMax = Math.max(hexSelStart, hexSelEnd);
        hexInputText = hexInputText.substring(0, selMin) + hexInputText.substring(selMax);
        hexCursor = selMin;
        clearHexSelection();
    }

    private void copyHexToClipboard(long window) {
        String text;
        if (hasHexSelection()) {
            int selMin = Math.min(hexSelStart, hexSelEnd);
            int selMax = Math.max(hexSelStart, hexSelEnd);
            text = hexInputText.substring(selMin, selMax);
        } else {
            text = hexInputText;
        }
        System.out.println("[ClickGUI] Copying color to clipboard: " + text);
        try {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                new java.awt.datatransfer.StringSelection(text), null
            );
            System.out.println("[ClickGUI] Copied via AWT successfully.");
            return;
        } catch (Throwable t) {
            System.out.println("[ClickGUI] AWT copy failed: " + t.getMessage());
        }
        try {
            Minecraft.getInstance().keyboardHandler.setClipboard(text);
            System.out.println("[ClickGUI] Copied via Minecraft successfully.");
        } catch (Throwable t) {
            System.out.println("[ClickGUI] Minecraft copy failed: " + t.getMessage());
        }
    }

    private void pasteHexFromClipboard(long window) {
        String clipboard = null;
        try {
            clipboard = (String) java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().getData(java.awt.datatransfer.DataFlavor.stringFlavor);
            System.out.println("[ClickGUI] Read via AWT successfully: " + clipboard);
        } catch (Throwable t) {
            System.out.println("[ClickGUI] AWT paste failed: " + t.getMessage());
        }
        if (clipboard == null) {
            try {
                clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
                System.out.println("[ClickGUI] Read via Minecraft successfully: " + clipboard);
            } catch (Throwable t) {
                System.out.println("[ClickGUI] Minecraft paste failed: " + t.getMessage());
            }
        }
        if (clipboard == null) return;
        
        if (clipboard.startsWith("#")) {
            clipboard = clipboard.substring(1);
        }
        String hex = clipboard.replaceAll("[^0-9a-fA-F]", "").toUpperCase();
        System.out.println("[ClickGUI] Cleaned hex for pasting: " + hex);
        if (hex.isEmpty()) return;
        
        if (hasHexSelection()) {
            deleteHexSelection();
            String newText = hexInputText.substring(0, hexCursor) + hex + hexInputText.substring(hexCursor);
            if (newText.length() > 8) {
                newText = newText.substring(0, 8);
            }
            hexInputText = newText;
            hexCursor = Math.min(hexCursor + hex.length(), 8);
        } else {
            if (hex.length() == 6 || hex.length() == 8) {
                hexInputText = hex;
                hexCursor = hex.length();
            } else {
                String newText = hexInputText.substring(0, hexCursor) + hex + hexInputText.substring(hexCursor);
                if (newText.length() > 8) {
                    newText = newText.substring(0, 8);
                }
                hexInputText = newText;
                hexCursor = Math.min(hexCursor + hex.length(), 8);
            }
        }
        System.out.println("[ClickGUI] New input text: " + hexInputText);
        applyHexInput();
    }

    private void applyHexInput() {
        String hex = hexInputText;
        while (hex.length() < 8) hex = hex + "F";
        try {
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            int a = Integer.parseInt(hex.substring(6, 8), 16);
            colorPickerColor = ARGB.color(a, r, g, b);
            if (selectedTarget == ColorTarget.BACKGROUND) {
                MusicDisplayOverlay.backgroundColor = colorPickerColor;
            } else if (selectedTarget == ColorTarget.TITLE) {
                MusicDisplayOverlay.titleColor = colorPickerColor;
            } else if (selectedTarget == ColorTarget.ARTIST) {
                MusicDisplayOverlay.artistColor = colorPickerColor;
            } else if (selectedTarget == ColorTarget.TIME) {
                MusicDisplayOverlay.timeColor = colorPickerColor;
            } else if (selectedTarget == ColorTarget.PROGRESS_BAR) {
                MusicDisplayOverlay.progressColor = colorPickerColor;
            }
            float[] hsb = Color.RGBtoHSB(r, g, b, null);
            cpHue = hsb[0];
            cpSat = hsb[1];
            cpBri = hsb[2];
            cpAlpha = a / 255.0f;
        } catch (NumberFormatException e) {
            // Invalid hex, don't update
        }
    }

    private void updateHexFromColor() {
        int a = (colorPickerColor >> 24) & 0xFF;
        int r = (colorPickerColor >> 16) & 0xFF;
        int g = (colorPickerColor >> 8) & 0xFF;
        int b = colorPickerColor & 0xFF;
        hexInputText = String.format("%02X%02X%02X%02X", r, g, b, a);
        hexCursor = Math.min(hexCursor, hexInputText.length());
    }

    private void updateHSBFromColor() {
        int a = (colorPickerColor >> 24) & 0xFF;
        int r = (colorPickerColor >> 16) & 0xFF;
        int g = (colorPickerColor >> 8) & 0xFF;
        int b = colorPickerColor & 0xFF;
        float[] hsb = Color.RGBtoHSB(r, g, b, null);
        cpHue = hsb[0];
        cpSat = hsb[1];
        cpBri = hsb[2];
        cpAlpha = a / 255.0f;
        hexInputText = String.format("%02X%02X%02X%02X", r, g, b, a);
        hexCursor = hexInputText.length();
    }

    private void resetCursorBlink() {
        lastBlinkTime = System.currentTimeMillis();
        cursorVisible = true;
    }

    private static void loadAssets() {
        if (spotifyLogo == null) {
            try {
                Identifier id = Identifier.fromNamespaceAndPath("halo", "spotify-white-icon.png");
                spotifyLogo = new NVGImageRenderer(
                        Minecraft.getInstance().getResourceManager().open(id)
                );
            } catch (Exception e) {
                System.err.println("[ClickGUI] Failed to load spotifyLogo: " + e.getMessage());
                e.printStackTrace();
            }
        }
        if (spotifyLogoImage == null) {
            try {
                Identifier id = Identifier.fromNamespaceAndPath("halo", "spotify-white-icon.png");
                spotifyLogoImage = ImageManager.fromIdentifier(id);
            } catch (Exception e) {
                System.err.println("[ClickGUI] Failed to load spotifyLogoImage: " + e.getMessage());
                e.printStackTrace();
            }
        }
        if (headerFont == null) {
            try {
                headerFont = FontRepository.getFont("productsans-bold");
            } catch (Exception e) {
                System.err.println("[ClickGUI] Failed to load headerFont: " + e.getMessage());
                e.printStackTrace();
            }
        }
        if (checkboxFont == null) {
            try {
                checkboxFont = FontRepository.getFont("productsans-semibold");
            } catch (Exception e) {
                System.err.println("[ClickGUI] Failed to load checkboxFont: " + e.getMessage());
                e.printStackTrace();
            }
        }
        if (mediumFont == null) {
            try {
                mediumFont = FontRepository.getFont("productsans-medium");
            } catch (Exception e) {
                System.err.println("[ClickGUI] Failed to load mediumFont: " + e.getMessage());
                e.printStackTrace();
            }
        }
        if (iconFont == null) {
            try {
                iconFont = FontRepository.getFont("materialicons-regular");
            } catch (Exception e) {
                System.err.println("[ClickGUI] Failed to load iconFont: " + e.getMessage());
                e.printStackTrace();
            }
        }
        if (fluidFont == null) {
            try {
                fluidFont = FontRepository.getFont("Fluid-Regular");
            } catch (Exception e) {
                System.err.println("[ClickGUI] Failed to load fluidFont: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    @Override
    public void removed() {
        // Persist any settings changed in the GUI (colors, gui size, toggles, sliders).
        MusicDisplayOverlay.scheduleSave();
        super.removed();
    }

    private static final java.util.Map<String, ImageManager.CachedImage> localArtCache = new java.util.HashMap<>();

    /** Loads a search-result cover from a local file (already downloaded by the provider), cached by path. */
    private static ImageManager.CachedImage loadLocalArt(String path) {
        if (path == null || path.isEmpty()) return null;
        ImageManager.CachedImage cached = localArtCache.get(path);
        if (cached != null) return cached;
        try {
            java.io.File f = new java.io.File(path);
            if (f.exists() && f.length() > 0) {
                byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
                ImageManager.CachedImage img = ImageManager.fromBytes("localart:" + path, bytes);
                if (img != null) {
                    localArtCache.put(path, img);
                    return img;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static void openUrl(String url) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("cmd.exe", "/c", "start", url).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", url).start();
            } else {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void triggerSearch(String query) {
        if (query.isEmpty()) {
            searchResults.clear();
            searchArtRenderers.clear();
            searchLoading = false;
            return;
        }
        searchLoading = true;
        SpotifyManager.SearchFilter filter = this.currentSearchFilter;
        boolean subsonic = MusicManager.getActiveSource() == MusicManager.Source.SUBSONIC;
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            var results = subsonic
                    ? SubsonicManager.getInstance().search(query)
                    : SpotifyManager.getInstance().search(query, filter);
            Minecraft.getInstance().execute(() -> {
                if (searchInputText.equals(query) && this.currentSearchFilter == filter) {
                    this.searchResults = results;
                    this.searchLoading = false;
                }
            });
        });
    }

    private static char keyToChar(int keyCode, long window) {
        boolean shift = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                     || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        
        if (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z) {
            char base = (char) ('a' + (keyCode - GLFW.GLFW_KEY_A));
            return shift ? Character.toUpperCase(base) : base;
        }
        if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
            if (shift) {
                switch (keyCode) {
                    case GLFW.GLFW_KEY_1: return '!';
                    case GLFW.GLFW_KEY_2: return '@';
                    case GLFW.GLFW_KEY_3: return '#';
                    case GLFW.GLFW_KEY_4: return '$';
                    case GLFW.GLFW_KEY_5: return '%';
                    case GLFW.GLFW_KEY_6: return '^';
                    case GLFW.GLFW_KEY_7: return '&';
                    case GLFW.GLFW_KEY_8: return '*';
                    case GLFW.GLFW_KEY_9: return '(';
                    case GLFW.GLFW_KEY_0: return ')';
                }
            }
            return (char) ('0' + (keyCode - GLFW.GLFW_KEY_0));
        }
        if (keyCode == GLFW.GLFW_KEY_SPACE) return ' ';
        if (keyCode == GLFW.GLFW_KEY_MINUS) return shift ? '_' : '-';
        if (keyCode == GLFW.GLFW_KEY_EQUAL) return shift ? '+' : '=';
        if (keyCode == GLFW.GLFW_KEY_LEFT_BRACKET) return shift ? '{' : '[';
        if (keyCode == GLFW.GLFW_KEY_RIGHT_BRACKET) return shift ? '}' : ']';
        if (keyCode == GLFW.GLFW_KEY_SEMICOLON) return shift ? ':' : ';';
        if (keyCode == GLFW.GLFW_KEY_APOSTROPHE) return shift ? '"' : '\'';
        if (keyCode == GLFW.GLFW_KEY_COMMA) return shift ? '<' : ',';
        if (keyCode == GLFW.GLFW_KEY_PERIOD) return shift ? '>' : '.';
        if (keyCode == GLFW.GLFW_KEY_SLASH) return shift ? '?' : '/';
        if (keyCode == GLFW.GLFW_KEY_BACKSLASH) return shift ? '|' : '\\';
        
        if (keyCode >= GLFW.GLFW_KEY_KP_0 && keyCode <= GLFW.GLFW_KEY_KP_9) {
            return (char) ('0' + (keyCode - GLFW.GLFW_KEY_KP_0));
        }
        if (keyCode == GLFW.GLFW_KEY_KP_DECIMAL) return '.';
        if (keyCode == GLFW.GLFW_KEY_KP_DIVIDE) return '/';
        if (keyCode == GLFW.GLFW_KEY_KP_MULTIPLY) return '*';
        if (keyCode == GLFW.GLFW_KEY_KP_SUBTRACT) return '-';
        if (keyCode == GLFW.GLFW_KEY_KP_ADD) return '+';

        return 0;
    }

    private boolean hasSearchSelection() {
        return searchInputActive && searchSelStart >= 0 && searchSelStart != searchSelEnd;
    }

    private void clearSearchSelection() {
        searchSelStart = -1;
        searchSelEnd = -1;
    }

    private void deleteSearchSelection() {
        if (!hasSearchSelection()) return;
        int selMin = Math.min(searchSelStart, searchSelEnd);
        int selMax = Math.max(searchSelStart, searchSelEnd);
        searchInputText = searchInputText.substring(0, selMin) + searchInputText.substring(selMax);
        searchCursor = selMin;
        clearSearchSelection();
    }

    private void copySearchToClipboard(long window) {
        String text;
        if (hasSearchSelection()) {
            int selMin = Math.min(searchSelStart, searchSelEnd);
            int selMax = Math.max(searchSelStart, searchSelEnd);
            text = searchInputText.substring(selMin, selMax);
        } else {
            text = searchInputText;
        }
        try {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                new java.awt.datatransfer.StringSelection(text), null
            );
            return;
        } catch (Throwable t) {}
        try {
            Minecraft.getInstance().keyboardHandler.setClipboard(text);
        } catch (Throwable t) {}
    }

    private void pasteSearchFromClipboard(long window) {
        String clipboard = null;
        try {
            clipboard = (String) java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().getData(java.awt.datatransfer.DataFlavor.stringFlavor);
        } catch (Throwable t) {}
        if (clipboard == null) {
            try {
                clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
            } catch (Throwable t) {}
        }
        if (clipboard == null) return;
        
        deleteSearchSelection();
        searchInputText = searchInputText.substring(0, searchCursor) + clipboard + searchInputText.substring(searchCursor);
        searchCursor += clipboard.length();
        triggerSearch(searchInputText);
    }
}
