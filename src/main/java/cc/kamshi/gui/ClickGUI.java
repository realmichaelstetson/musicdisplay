package cc.kamshi.gui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;
import cc.kamshi.builders.Builder;
import cc.kamshi.builders.states.QuadColorState;
import cc.kamshi.builders.states.QuadRadiusState;
import cc.kamshi.builders.states.SizeState;
import cc.kamshi.renderers.impl.BuiltBlur;
import cc.kamshi.renderers.impl.BuiltTexture;
import cc.kamshi.renderers.impl.BuiltRectangle;
import cc.kamshi.msdf.MsdfFont;
import org.joml.Matrix4f;
import com.google.common.base.Suppliers;
import java.awt.Color;
import java.util.function.Supplier;

import cc.kamshi.gui.animation.Animation;
import cc.kamshi.gui.animation.Easing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;

public class ClickGUI extends Screen {

    private static final int WIDTH = 230;
    private static final int HEIGHT = 140;

    private static final Identifier SPOTIFY_ICON = Identifier.of("mre", "spotify-white-icon.png");

    private static final Supplier<MsdfFont> PRODUCT_SANS_BOLD = Suppliers.memoize(() -> 
        MsdfFont.builder()
            .name("productsans-bold")
            .atlas("msdf/productsans-bold")
            .data("msdf/productsans-bold")
            .build()
    );

    private static final Supplier<MsdfFont> PRODUCT_SANS_REGULAR = Suppliers.memoize(() -> 
        MsdfFont.builder()
            .name("productsans-regular")
            .atlas("msdf/productsans-regular")
            .data("msdf/productsans-regular")
            .build()
    );

    private static final Supplier<MsdfFont> PRODUCT_SANS_MEDIUM = Suppliers.memoize(() -> 
        MsdfFont.builder()
            .name("productsans-medium")
            .atlas("msdf/productsans-medium")
            .data("msdf/productsans-medium")
            .build()
    );

    private static final Supplier<MsdfFont> PRODUCT_SANS_SEMIBOLD = Suppliers.memoize(() -> 
        MsdfFont.builder()
            .name("productsans-semibold")
            .atlas("msdf/productsans-semibold")
            .data("msdf/productsans-semibold")
            .build()
    );

    private static final Supplier<MsdfFont> MATERIALICONS_FONT = Suppliers.memoize(() ->
        MsdfFont.builder()
            .name("materialicons-regular")
            .atlas("msdf/materialicons-regular")
            .data("msdf/materialicons-regular")
            .build()
    );

    private static final Supplier<MsdfFont> FLUID_FONT = Suppliers.memoize(() -> 
        MsdfFont.builder()
            .name("fluid-regular")
            .atlas("msdf/fluid-regular")
            .data("msdf/fluid-regular")
            .build()
    );

    // GUI State Variables
    private static boolean enabled = true;
    private static boolean showControls = true;
    private static boolean showNextSong = true;

    private static float blurValue = 15.0f;
    private static float bloomValue = 3.0f;
    private static float guiSizeValue = 1.2f;

    private boolean draggingBlur = false;
    private boolean draggingBloom = false;
    private boolean draggingGuiSize = false;

    // Custom Color configurations for SpotifyOverlay
    public static int overlayBgColor = new Color(12, 12, 12, 100).getRGB();
    public static int overlayTitleColor = 0xFFFFFFFF;
    public static int overlayArtistColor = 0x90FFFFFF;
    public static int overlayTimeColor = 0x80FFFFFF;
    public static int overlayProgressBarColor = 0xFFFFFFFF;

    // Color Picker State
    private static boolean colorPickerOpen = false;
    private static int activeColorTargetIndex = 0;
    private static final String[] COLOR_TARGETS = {
        "Background Color",
        "Title Text Color",
        "Artist Text Color",
        "Time Text Color",
        "Progress Bar Color"
    };

    private boolean colorTargetDropdownOpen = false;

    // Active color parts in HSV
    private static float hsvHue = 0.0f;
    private static float hsvSat = 1.0f;
    private static float hsvVal = 1.0f;
    private static int hsvAlpha = 255;

    // Color picker drag tracking
    private boolean draggingColorMap = false;
    private boolean draggingHue = false;
    private boolean draggingAlpha = false;

    // Hex text field focus
    private boolean hexFocused = false;
    private static String hexInputText = "";

    private static String glassStyle = "Gaussian";
    private boolean dropdownOpen = false;
    private static final String[] GLASS_STYLES = {"Gaussian", "Liquid"};

    private String searchText = "";
    private boolean searchFocused = false;
    private boolean searchSelectAll = false;

    private static SpotifyManager.SearchFilter activeSearchFilter = SpotifyManager.SearchFilter.ALL;
    private boolean searchLoading = false;
    private java.util.List<SpotifyManager.SearchResultTrack> searchResults = new java.util.ArrayList<>();
    private String lastSearchQuery = "";
    private SpotifyManager.SearchFilter lastSearchFilter = null;

    // Animations
    private final Animation scaleAnimation = new Animation(Easing.EASE_OUT_BACK, 220L);
    private final Animation enabledAnimation = new Animation(Easing.EASE_IN_OUT_QUAD, 150L);
    private final Animation showControlsAnimation = new Animation(Easing.EASE_IN_OUT_QUAD, 150L);
    private final Animation showNextSongAnimation = new Animation(Easing.EASE_IN_OUT_QUAD, 150L);
    private final Animation blurAnimation = new Animation(Easing.EASE_OUT_CUBIC, 150L);
    private final Animation bloomAnimation = new Animation(Easing.EASE_OUT_CUBIC, 150L);
    private final Animation guiSizeAnimation = new Animation(Easing.EASE_OUT_CUBIC, 150L);
    private final Animation dropdownAnimation = new Animation(Easing.EASE_IN_OUT_CUBIC, 200L);
    private final Animation colorTargetDropdownAnimation = new Animation(Easing.EASE_IN_OUT_CUBIC, 200L);
    private final Animation colorPickerScaleAnimation = new Animation(Easing.EASE_OUT_BACK, 200L);
    private boolean closing = false;
    private float currentAlpha = 1.0f;

    public ClickGUI() {
        super(Text.literal("ClickGUI"));
    }

    @Override
    protected void init() {
        super.init();
        closing = false;
        scaleAnimation.setStartValue(0.0f);
        scaleAnimation.setValue(0.0f);
        scaleAnimation.reset();

        enabledAnimation.setStartValue(enabled ? 1.0f : 0.0f);
        showControlsAnimation.setStartValue(showControls ? 1.0f : 0.0f);
        showNextSongAnimation.setStartValue(showNextSong ? 1.0f : 0.0f);
        
        blurAnimation.setStartValue(blurValue / 30.0f);
        bloomAnimation.setStartValue(bloomValue / 10.0f);
        guiSizeAnimation.setStartValue((guiSizeValue - 0.5f) / 2.0f);
        
        dropdownAnimation.setStartValue(dropdownOpen ? 1.0f : 0.0f);
        colorTargetDropdownAnimation.setStartValue(colorTargetDropdownOpen ? 1.0f : 0.0f);
        colorPickerScaleAnimation.setStartValue(colorPickerOpen ? 1.0f : 0.0f);
    }

    @Override
    public void close() {
        if (!closing) {
            closing = true;
            scaleAnimation.reset();
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Empty to disable rendering of the default background gradient or dirt texture
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        
        scaleAnimation.run(closing ? 0.0f : 1.0f);
        float scale = scaleAnimation.getValue() * guiSizeValue;
        
        if (closing && scaleAnimation.isFinished() && scale <= 0.0f) {
            super.close();
            return;
        }

        currentAlpha = Math.max(0.0f, Math.min(1.0f, scale));

        context.getMatrices().push();
        float centerX = this.width / 2.0f;
        float centerY = this.height / 2.0f;
        context.getMatrices().translate(centerX, centerY, 0.0f);
        context.getMatrices().scale(scale, scale, 1.0f);
        context.getMatrices().translate(-centerX, -centerY, 0.0f);
        
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();

        float smouseX = mouseX;
        float smouseY = mouseY;
        if (scale > 0.0f) {
            smouseX = centerX + (mouseX - centerX) / scale;
            smouseY = centerY + (mouseY - centerY) / scale;
        }
        
        // Calculate center coordinates dynamically based on screen dimensions
        float x = (this.width - WIDTH) / 2.0f;
        float y = (this.height - HEIGHT) / 2.0f;
        
        // 1. Background Glass Panel
        BuiltBlur blurRect = Builder.blur()
            .size(new SizeState(WIDTH, HEIGHT))
            .radius(new QuadRadiusState(8f))
            .blurRadius(15f)
            .smoothness(1f)
            .color(new QuadColorState(new Color(23, 23, 23, (int) (165 * currentAlpha))))
            .build();
            
        blurRect.render(matrix, x, y);

        // 2. Spotify Icon
        AbstractTexture texture = this.client.getTextureManager().getTexture(SPOTIFY_ICON);
        if (texture != null) {
            texture.setFilter(true, false);
        }
        int textureId = texture != null ? texture.getGlId() : 0;

        float iconSize = 13f;
        float iconX = x + 8;
        float iconY = y + 14 - iconSize/2f;

        BuiltTexture spotifyIcon = Builder.texture()
            .size(new SizeState(iconSize, iconSize))
            .texture(0f, 0f, 1f, 1f, textureId)
            .radius(0f)
            .smoothness(0f)
            .color(new QuadColorState(new Color(255, 255, 255, (int) (255 * currentAlpha))))
            .build();
            
        spotifyIcon.render(matrix, iconX, iconY);

        // 3. Fonts Setup
        MsdfFont fontBold = PRODUCT_SANS_BOLD.get();
        MsdfFont fontRegular = PRODUCT_SANS_REGULAR.get();
        MsdfFont fontMedium = PRODUCT_SANS_MEDIUM.get();
        MsdfFont fontSemibold = PRODUCT_SANS_SEMIBOLD.get();
        MsdfFont fontMaterial = MATERIALICONS_FONT.get();

        // Title Text
        float fontSize = 10f;
        float textX = x + 25f;
        float textY = y + 14f - fontBold.getHeight(10.0f) / 2.0f;

        drawText(matrix, fontBold, "Music Display v2.4", fontSize, 0xFFFFFFFF, textX, textY);

        // 4. Search Bar (Top Right)
        float searchX = x + 125.0f;
        float searchY = y + 7.5f;
        float searchW = 97.0f;
        float searchH = 14.0f;

        BuiltBlur searchRect = Builder.blur()
            .size(new SizeState(searchW, searchH))
            .radius(new QuadRadiusState(4f))
            .blurRadius(15f)
            .smoothness(1f)
            .color(new QuadColorState(new Color(0, 0, 0, (int) (85 * currentAlpha))))
            .build();

        searchRect.render(matrix, searchX, searchY);

        // Search icon (Magnifying glass)
        drawText(matrix, fontMaterial, "\uE8B6", 8.0f, 0x90FFFFFF, searchX + 5.0f, searchY + searchH / 2.0f - fontMaterial.getHeight(8.0f) / 2.0f);

        // Search text / Placeholder
        String displaySearchText = searchText.isEmpty() ? (searchFocused ? "" : "Search...") : searchText;
        int searchTextColor = searchText.isEmpty() ? 0x60FFFFFF : 0xFFFFFFFF;
        
        // Show cursor if focused
        if (searchFocused && !searchSelectAll && (System.currentTimeMillis() / 500) % 2 == 0) {
            displaySearchText += "|";
        }

        float searchTextX = searchX + 16.0f;
        float searchTextY = searchY + searchH / 2.0f - fontMedium.getHeight(7.0f) / 2.0f;

        // Draw select-all highlight
        if (searchSelectAll && !searchText.isEmpty()) {
            float textW = fontMedium.getWidth(searchText, 7.0f);
            BuiltRectangle selectBg = Builder.rectangle()
                .size(new SizeState(textW + 2.0f, fontMedium.getHeight(7.0f) + 2.0f))
                .radius(new QuadRadiusState(1f))
                .color(new QuadColorState(new Color(60, 120, 215, (int) (180 * currentAlpha))))
                .build();
            selectBg.render(matrix, searchTextX - 1.0f, searchTextY - 1.0f);
        }

        drawText(matrix, fontMedium, displaySearchText, 7.0f, searchTextColor, searchTextX, searchTextY);

        if (!searchText.isEmpty()) {
            boolean hoverClear = smouseX >= searchX + searchW - 14f && smouseX <= searchX + searchW - 2f && smouseY >= searchY && smouseY <= searchY + searchH;
            int clearColor = hoverClear ? 0xFFFFFFFF : 0x80FFFFFF;
            drawText(matrix, fontMaterial, "\uE5CD", 7.0f, clearColor, searchX + searchW - 10.0f, searchY + searchH / 2.0f - fontMaterial.getHeight(7.0f) / 2.0f);
        }

        if (searchText.isEmpty()) {
            // 5. Left Sub-Panel: "Main"
            BuiltBlur mainPanel = Builder.blur()
                .size(new SizeState(103, 52))
                .radius(new QuadRadiusState(3f))
                .blurRadius(15f)
                .smoothness(1f)
                .color(new QuadColorState(new Color(0, 0, 0, (int) (85 * currentAlpha))))
                .build();
            mainPanel.render(matrix, x + 8.0f, y + 27.5f);

            // Main Title
            drawText(matrix, fontMedium, "Main", 6.0f, 0x90FFFFFF, x + 12.0f, y + 34.5f - fontMedium.getHeight(6.0f) / 2.0f);

            // Main - Enabled
            enabledAnimation.run(enabled ? 1.0f : 0.0f);
            drawText(matrix, fontSemibold, "Enabled", 6.5f, 0xFFFFFFFF, x + 12.0f, y + 45.0f - fontRegular.getHeight(6.5f) / 2.0f);
            drawToggle(matrix, x + 91.0f, y + 42.0f, enabledAnimation.getValue());

            // Main - Show Controls
            showControlsAnimation.run(showControls ? 1.0f : 0.0f);
            drawText(matrix, fontSemibold, "Show Controls", 6.5f, 0xFFFFFFFF, x + 12.0f, y + 58.0f - fontRegular.getHeight(6.5f) / 2.0f);
            drawToggle(matrix, x + 91.0f, y + 55.0f, showControlsAnimation.getValue());

            // Main - Show Next Song
            showNextSongAnimation.run(showNextSong ? 1.0f : 0.0f);
            drawText(matrix, fontSemibold, "Show Next Song", 6.5f, 0xFFFFFFFF, x + 12.0f, y + 71.0f - fontRegular.getHeight(6.5f) / 2.0f);
            drawToggle(matrix, x + 91.0f, y + 68.0f, showNextSongAnimation.getValue());
            // Main - Setup Spotify button (drawn at the bottom center of the GUI if not configured)
            if (!SpotifyManager.isConfigured()) {
                BuiltBlur setupBtn = Builder.blur()
                        .size(new SizeState(80f, 15f))
                        .radius(new QuadRadiusState(8f))
                        .blurRadius(15f)
                        .smoothness(1f)
                        .color(new QuadColorState(new Color(46, 178, 103, (int) (180 * currentAlpha))))
                        .build();

// Pozycja przycisku
                float btnX = x + 63.5f + 20;
                float btnY = y + 120.0f;
                float btnWidth = 80f;
                float btnHeight = 15f;

                setupBtn.render(matrix, btnX, btnY);

// Wycentrowany tekst
                String text = "Setup Spotify";
                float textX1 = btnX + (btnWidth - fontSemibold.getWidth(text, 7.0f)) / 2f;
                float textY1 = btnY + (btnHeight - fontSemibold.getHeight(7.0f)) / 2f;

                drawText(matrix, fontSemibold, text, 7.0f, 0xFFFFFFFF, textX1, textY1);
            }

            // 6. Right Sub-Panel: "Settings"
            BuiltBlur settingsPanel = Builder.blur()
                .size(new SizeState(103, 78))
                .radius(new QuadRadiusState(3f))
                .blurRadius(15f)
                .smoothness(1f)
                .color(new QuadColorState(new Color(0, 0, 0, (int) (85 * currentAlpha))))
                .build();
            settingsPanel.render(matrix, x + 119.0f, y + 27.5f);

            // Settings Title
            drawText(matrix, fontMedium, "Settings", 6.0f, 0x90FFFFFF, x + 123.0f, y + 34.5f - fontMedium.getHeight(6.0f) / 2.0f);

            // Blur Slider
            drawText(matrix, fontSemibold, "Blur", 6.5f, 0xFFFFFFFF, x + 124.0f, y + 47.0f - fontRegular.getHeight(6.5f) / 2.0f);
            float blurPct = blurValue / 30.0f;
            blurAnimation.run(blurPct);
            drawSlider(matrix, x + 154.0f, y + 46.0f, blurAnimation.getValue());
            drawText(matrix, fontRegular, String.format("%.1f", blurValue), 6.0f, 0x80FFFFFF, x + 207.0f, y + 47.0f - fontRegular.getHeight(6.0f) / 2.0f);

            // Bloom Slider
            drawText(matrix, fontSemibold, "Bloom", 6.5f, 0xFFFFFFFF, x + 124.0f, y + 60.0f - fontRegular.getHeight(6.5f) / 2.0f);
            float bloomPct = bloomValue / 10.0f;
            bloomAnimation.run(bloomPct);
            String glassStyle = ClickGUI.getGlassStyle();
            if("Liquid".equalsIgnoreCase(glassStyle) || "LiquidGlass".equalsIgnoreCase(glassStyle)){
                drawText(matrix, fontSemibold, "Unavailable with Liquid Glass.", 4.5f, 0xFF474747, x + 154.0f, y + 60.5f - fontRegular.getHeight(4.5f) / 2.0f);
            }else {
                drawSlider(matrix, x + 154.0f, y + 59.0f, bloomAnimation.getValue());
                drawText(matrix, fontRegular, String.format("%.1f", bloomValue), 6.0f, 0x80FFFFFF, x + 207.0f, y + 60.0f - fontRegular.getHeight(6.0f) / 2.0f);
            }
            // Gui Size Slider
            drawText(matrix, fontSemibold, "Gui Size", 6.5f, 0xFFFFFFFF, x + 124.0f, y + 73.0f - fontRegular.getHeight(6.5f) / 2.0f);
            float sizePct = (guiSizeValue - 1.0f) / 0.4f;
            guiSizeAnimation.run(sizePct);
            drawSlider(matrix, x + 154.0f, y + 72.0f, guiSizeAnimation.getValue());
            drawText(matrix, fontRegular, String.format("%.1f", guiSizeValue), 6.0f, 0x80FFFFFF, x + 207.0f, y + 73.0f - fontRegular.getHeight(6.0f) / 2.0f);

            // Colors Selector
            drawText(matrix, fontSemibold, "Colors", 6.5f, 0xFFFFFFFF, x + 124.0f, y + 86.0f - fontRegular.getHeight(6.5f) / 2.0f);
            BuiltRectangle colorBox = Builder.rectangle()
                .size(new SizeState(12, 8))
                .radius(new QuadRadiusState(2f))
                .color(new QuadColorState(adjustColorAlpha(new Color(getTargetColor(activeColorTargetIndex), true), currentAlpha)))
                .build();
            colorBox.render(matrix, x + 205.0f, y + 82.0f);

            // Glass Style Dropdown
            drawText(matrix, fontSemibold, "Glass Style", 6.5f, 0xFFFFFFFF, x + 124.0f, y + 99.0f - fontRegular.getHeight(6.5f) / 2.0f);
            
            BuiltRectangle dropdownBg = Builder.rectangle()
                .size(new SizeState(40, 10))
                .radius(new QuadRadiusState(2f))
                .color(new QuadColorState(new Color(0, 0, 0, (int) (85 * currentAlpha))))
                .build();
            dropdownBg.render(matrix, x + 180.0f, y + 94.0f);

            drawText(matrix, fontRegular, glassStyle, 5.5f, 0xFFFFFFFF, x + 183.0f, y + 98.5f - fontRegular.getHeight(5.5f) / 2.0f);
            drawArrow(matrix, x + 213.0f, y + 97.0f, 5.0f, dropdownOpen, 0x80FFFFFF);

            // Render Open Dropdown Menu options at the very end
            dropdownAnimation.run(dropdownOpen ? 1.0f : 0.0f);
            float dropdownProgress = dropdownAnimation.getValue();

            if (dropdownProgress > 0.0f) {
                float dropY = y + 104.0f;
                float optionHeight = 10.0f;
                float fullHeight = optionHeight * GLASS_STYLES.length;
                float currentHeight = fullHeight * dropdownProgress;

                context.enableScissor(
                    (int) Math.floor(x + 180.0f),
                    (int) Math.floor(dropY),
                    (int) Math.ceil(x + 220.0f),
                    (int) Math.ceil(dropY + currentHeight)
                );

                BuiltBlur menuBg = Builder.blur()
                    .size(new SizeState(40, fullHeight))
                    .radius(new QuadRadiusState(2f))
                    .blurRadius(blurValue)
                    .smoothness(bloomValue)
                    .color(new QuadColorState(new Color(15, 15, 15, (int) (230 * currentAlpha))))
                    .build();
                menuBg.render(matrix, x + 180.0f, dropY);

                for (int i = 0; i < GLASS_STYLES.length; i++) {
                    boolean isSelected = GLASS_STYLES[i].equals(glassStyle);
                    if (isSelected) {
                        BuiltRectangle dot = Builder.rectangle()
                            .size(new SizeState(3f, 3f))
                            .radius(new QuadRadiusState(1.5f))
                            .color(new QuadColorState(new Color(255, 255, 255, (int) (255 * currentAlpha))))
                            .build();
                        dot.render(matrix, x + 212.0f, dropY + 3.5f + i * optionHeight);
                    }
                    drawText(matrix, fontRegular, GLASS_STYLES[i], 5.5f, 0xFFFFFFFF, x + 183.0f, dropY + 2.5f + i * optionHeight);
                }

                context.disableScissor();
            }
        } else {
            // Trigger asynchronous Spotify search update
            triggerSearch(searchText, activeSearchFilter);

            // 1. Draw Filters Section
            float fy = y + 27.5f;
            drawText(matrix, fontMedium, "Filters:", 6.0f, 0x60FFFFFF, x + 10.0f, fy + 3.5f - fontMedium.getHeight(6.0f) / 2.0f);

            float gap = 4.0f;
            float totalWidth = 0.0f;
            for (var filter : SpotifyManager.SearchFilter.values()) {
                String label = filter == SpotifyManager.SearchFilter.OWN_PLAYLISTS ? "Library" : filter.getDisplayName();
                float labelW = fontMedium.getWidth(label, 6.0f);
                totalWidth += (labelW + 6.0f);
            }
            totalWidth += gap * (SpotifyManager.SearchFilter.values().length - 1);

            float fx = x + 222.0f - totalWidth;

            // Background rect behind filter buttons (same style as search input)
            float filterPadX = 2.0f;
            float filterPadY = 1.0f;
            BuiltBlur filtersBg = Builder.blur()
                .size(new SizeState(totalWidth + filterPadX * 2, 9.0f + filterPadY * 2))
                .radius(new QuadRadiusState(4f))
                .blurRadius(15f)
                .smoothness(1f)
                .color(new QuadColorState(new Color(0, 0, 0, (int) (85 * currentAlpha))))
                .build();
            filtersBg.render(matrix, fx - filterPadX, fy - filterPadY);

            for (var filter : SpotifyManager.SearchFilter.values()) {
                String label = filter == SpotifyManager.SearchFilter.OWN_PLAYLISTS ? "Library" : filter.getDisplayName();
                float labelW = fontMedium.getWidth(label, 6.0f);
                float boxW = labelW + 6.0f;
                float boxH = 9.0f;
                boolean isSelected = activeSearchFilter == filter;
                if (isSelected) {
                    BuiltRectangle filterBox = Builder.rectangle()
                        .size(new SizeState(boxW, boxH))
                        .radius(new QuadRadiusState(2f))
                        .color(new QuadColorState(new Color(46, 178, 103, (int) (255 * currentAlpha))))
                        .build();
                    filterBox.render(matrix, fx, fy);
                }
                int color = isSelected ? 0xFFFFFFFF : 0x90FFFFFF;
                drawText(matrix, fontMedium, label, 6.0f, color, fx + 3.0f, fy + 4.5f - fontMedium.getHeight(6.0f) / 2.0f);
                fx += boxW + gap;
            }

            // 2. Draw Main Search Body (Loading or Results)
            if (searchLoading) {
                float loadingY = y + 68.0f;
                drawText(matrix, fontSemibold, "Loading...", 7.0f, 0x90FFFFFF, 
                         x + WIDTH / 2.0f - fontSemibold.getWidth("Loading...", 7.0f) / 2.0f, 
                         loadingY);
                drawSpinner(matrix, x + WIDTH / 2.0f, loadingY + 18.0f, 6.0f, 1.5f, currentAlpha);
            } else if (searchResults.isEmpty()) {
                String msg = "No results found";
                drawText(matrix, fontRegular, msg, 7.0f, 0x50FFFFFF,
                         x + WIDTH / 2.0f - fontRegular.getWidth(msg, 7.0f) / 2.0f,
                         y + 75.0f);
            } else {
                for (int i = 0; i < Math.min(4, searchResults.size()); i++) {
                    var item = searchResults.get(i);
                    float ry = y + 38.0f + i * 24.0f;

                    boolean isHovered = smouseX >= x + 8.0f && smouseX <= x + 222.0f && smouseY >= ry && smouseY <= ry + 22.0f;
                    if (isHovered) {
                        BuiltRectangle hoverBg = Builder.rectangle()
                            .size(new SizeState(214.0f, 22.0f))
                            .radius(new QuadRadiusState(3f))
                            .color(new QuadColorState(new Color(255, 255, 255, (int) (15 * currentAlpha))))
                            .build();
                        hoverBg.render(matrix, x + 8.0f, ry);
                    }

                    int artTexId = 0;
                    if (item.localArtworkPath() != null && !item.localArtworkPath().isEmpty()) {
                        Identifier artId = SpotifyOverlay.getOrCreateArtworkTexture(item.localArtworkPath());
                        if (artId != null) {
                            var texObj = this.client.getTextureManager().getTexture(artId);
                            if (texObj != null) {
                                artTexId = texObj.getGlId();
                            }
                        }
                    }
                    if (artTexId == 0) {
                        var texObj = this.client.getTextureManager().getTexture(SPOTIFY_ICON);
                        if (texObj != null) {
                            artTexId = texObj.getGlId();
                        }
                    }
                    BuiltTexture artwork = Builder.texture()
                        .size(new SizeState(18f, 18f))
                        .texture(0f, 0f, 1f, 1f, artTexId)
                        .radius(2f)
                        .smoothness(0.5f)
                        .color(QuadColorState.WHITE)
                        .build();
                    artwork.render(matrix, x + 10.0f, ry + 2.0f);

                    String title = item.title();
                    if (fontSemibold.getWidth(title, 6.5f) > 130.0f) {
                        while (!title.isEmpty() && fontSemibold.getWidth(title + "...", 6.5f) > 130.0f) {
                            title = title.substring(0, title.length() - 1);
                        }
                        title += "...";
                    }
                    drawText(matrix, fontSemibold, title, 6.5f, 0xFFFFFFFF, x + 32.0f, ry + 2.5f);

                    String artist = item.artist();
                    if (fontRegular.getWidth(artist, 5.5f) > 130.0f) {
                        while (!artist.isEmpty() && fontRegular.getWidth(artist + "...", 5.5f) > 130.0f) {
                            artist = artist.substring(0, artist.length() - 1);
                        }
                        artist += "...";
                    }
                    drawText(matrix, fontRegular, artist, 5.5f, 0x90FFFFFF, x + 32.0f, ry + 11.5f);

                    MsdfFont fontFluid = FLUID_FONT.get();

                    boolean hoverPlay = smouseX >= x + 204.0f && smouseX <= x + 218.0f && smouseY >= ry && smouseY <= ry + 22.0f;
                    int playColor = hoverPlay ? 0xFFFFFFFF : 0x90FFFFFF;
                    drawText(matrix, fontFluid, "B", 16.0f, playColor, x + 208.0f, ry + 10.0f - fontFluid.getHeight(16.0f) / 2.0f);

                    if (!item.isPlaylist()) {
                        boolean hoverHeart = smouseX >= x + 188.0f && smouseX <= x + 202.0f && smouseY >= ry && smouseY <= ry + 22.0f;
                        int heartColor;
                        if (item.liked()) {
                            heartColor = 0xFF2EB267; // Spotify Green for liked
                        } else {
                            heartColor = hoverHeart ? 0x90FFFFFF : 0x40FFFFFF;
                        }
                        drawText(matrix, fontFluid, "D", 16.0f, heartColor, x + 192.0f, ry + 10.0f - fontFluid.getHeight(16.0f) / 2.0f);
                    }
                }
            }
        }

        colorPickerScaleAnimation.run(colorPickerOpen ? 1.0f : 0.0f);
        float colorPickerScale = colorPickerScaleAnimation.getValue();
        if (colorPickerScale > 0.0f) {
            float mouseXScaled = mouseX;
            float mouseYScaled = mouseY;
            if (scale > 0.0f) {
                mouseXScaled = centerX + (mouseX - centerX) / scale;
                mouseYScaled = centerY + (mouseY - centerY) / scale;
            }

            float px = x + WIDTH + 6.0f;
            float py = y;
            float pw = 125.0f;
            float ph = 140.0f;
            float cpCenterX = px + pw / 2.0f;
            float cpCenterY = py + ph / 2.0f;

            context.getMatrices().push();
            context.getMatrices().translate(cpCenterX, cpCenterY, 0.0f);
            context.getMatrices().scale(colorPickerScale, colorPickerScale, 1.0f);
            context.getMatrices().translate(-cpCenterX, -cpCenterY, 0.0f);

            Matrix4f cpMatrix = context.getMatrices().peek().getPositionMatrix();
            drawColorPicker(context, cpMatrix, px, py, mouseXScaled, mouseYScaled);
            context.getMatrices().pop();
        }

        context.getMatrices().pop();
    }

    private void drawCopyIcon(Matrix4f matrix, float cx, float cy, int color) {
        MsdfFont fontMaterial = MATERIALICONS_FONT.get();
        drawText(matrix, fontMaterial, "\uE14D", 8.0f, color, cx - 0.5f, cy - 0.5f);
    }

    private void drawPasteIcon(Matrix4f matrix, float cx, float cy, int color) {
        MsdfFont fontMaterial = MATERIALICONS_FONT.get();
        drawText(matrix, fontMaterial, "\uE14F", 8.0f, color, cx, cy);
    }


    private void drawColorPicker(DrawContext context, Matrix4f matrix, float px, float py, float mouseX, float mouseY) {
        MsdfFont fontRegular = PRODUCT_SANS_REGULAR.get();
        MsdfFont fontMedium = PRODUCT_SANS_MEDIUM.get();
        MsdfFont fontBold = PRODUCT_SANS_BOLD.get();
        float w = 125f;
        float h = 140f;
        
        BuiltBlur blur = Builder.blur()
            .size(new SizeState(w, h))
            .radius(new QuadRadiusState(6f))
            .blurRadius(blurValue)
            .smoothness(bloomValue)
            .color(new QuadColorState(new Color(23, 23, 23, (int) (165 * currentAlpha))))
            .build();
        blur.render(matrix, px, py);

        float selectX = px + 8f;
        float selectY = py + 7f;
        float selectW = 95f;
        float selectH = 14f;

        BuiltRectangle selectBg = Builder.rectangle()
            .size(new SizeState(selectW, selectH))
            .radius(new QuadRadiusState(2f))
            .color(new QuadColorState(new Color(0, 0, 0, (int) (85 * currentAlpha))))
            .build();
        selectBg.render(matrix, selectX, selectY);

        drawText(matrix, fontBold, COLOR_TARGETS[activeColorTargetIndex], 6.0f, 0xFFFFFFFF, selectX + 5f, selectY + selectH / 2f - fontBold.getHeight(6.0f) / 2f);
        drawArrow(matrix, selectX + selectW - 9f, selectY + selectH / 2f - 4f, 9.0f, colorTargetDropdownOpen, 0x80FFFFFF);

        // Render close "X" button made of two rects rotated by 45 degrees
        float closeCX = px + 113f;
        float closeCY = py + 14f;
        float closeSize = 9.0f;
        float closeThickness = 2.0f;

        boolean hoverClose = mouseX >= px + 104f && mouseX <= px + 122f && mouseY >= py + 5f && mouseY <= py + 23f;
        Color closeColor = hoverClose ? new Color(255, 255, 255, (int) (255 * currentAlpha)) : new Color(255, 255, 255, (int) (140 * currentAlpha));

        Matrix4f closeMatrix = new Matrix4f(matrix);
        closeMatrix.translate(closeCX, closeCY, 0f);
        closeMatrix.rotateZ((float) (Math.PI / 4.0));

        BuiltRectangle closeBar1 = Builder.rectangle()
            .size(new SizeState(closeThickness, closeSize))
            .radius(new QuadRadiusState(closeThickness / 2f))
            .color(new QuadColorState(closeColor))
            .build();
        closeBar1.render(closeMatrix, -closeThickness / 2f, -closeSize / 2f);

        BuiltRectangle closeBar2 = Builder.rectangle()
            .size(new SizeState(closeSize, closeThickness))
            .radius(new QuadRadiusState(closeThickness / 2f))
            .color(new QuadColorState(closeColor))
            .build();
        closeBar2.render(closeMatrix, -closeSize / 2f, -closeThickness / 2f);

        float mapX = px + 8f;
        float mapY = py + 24f;
        float mapW = 93f;
        float mapH = 68f;

        Color pureHue = Color.getHSBColor(hsvHue, 1f, 1f);

        // Render smooth bilinear HSV gradient using custom single-batch POSITION_COLOR shader (zero gaps, zero seam artifacts)
        drawSVGradient(matrix, mapX, mapY, mapW, mapH, pureHue);

        // Render 4 corner overlays to give the SV gradient box visually rounded corners (radius ~2f)
        int maskColor = new Color(12, 12, 12, (int) (255 * currentAlpha)).getRGB();
        drawCornerMask(matrix, mapX - 0.2f, mapY - 0.2f, maskColor);
        drawCornerMask(matrix, mapX - 0.2f, mapY + mapH - 1.3f, maskColor);
        drawCornerMask(matrix, mapX + mapW - 1.3f, mapY + mapH - 1.3f, maskColor);
        drawCornerMask(matrix, mapX + mapW - 1.3f, mapY - 0.2f, maskColor);

        float dotX = mapX + hsvSat * mapW;
        float dotY = mapY + (1.0f - hsvVal) * mapH;
        BuiltRectangle pickerDot = Builder.rectangle()
            .size(new SizeState(6f, 6f))
            .radius(new QuadRadiusState(2f))
            .color(new QuadColorState(new Color(255, 255, 255, (int) (255 * currentAlpha))))
            .build();
        pickerDot.render(matrix, dotX - 2f, dotY - 2f);

        float hueX = px + 109f;
        float hueY = py + 24f;
        float hueW = 8f;
        float hueH = 68f;

        // Render Hue bar seamlessly in a single draw call with shared vertices (zero gaps)
        drawHueBar(matrix, hueX, hueY, hueW, hueH);

        // Apply 4 corner masks to round the top and bottom of the Hue bar
        drawCornerMask(matrix, hueX - 0.2f, hueY - 0.2f, maskColor);
        drawCornerMask(matrix, hueX + hueW - 1.3f, hueY - 0.2f, maskColor);
        drawCornerMask(matrix, hueX - 0.2f, hueY + hueH - 1.3f, maskColor);
        drawCornerMask(matrix, hueX + hueW - 1.3f, hueY + hueH - 1.3f, maskColor);

        float thumbY = hueY + hsvHue * hueH;
        BuiltRectangle hueThumb = Builder.rectangle()
            .size(new SizeState(hueW + 2f, 2f))
            .radius(new QuadRadiusState(0f))
            .color(new QuadColorState(new Color(255, 255, 255, (int) (255 * currentAlpha))))
            .build();
        hueThumb.render(matrix, hueX - 1f, thumbY - 1f);

        float alphaX = px + 8f;
        float alphaY = py + 98f;
        float alphaW = 109f;
        float alphaH = 6f;

        Color solidColor = Color.getHSBColor(hsvHue, hsvSat, hsvVal);
        Color startColor = new Color(solidColor.getRed(), solidColor.getGreen(), solidColor.getBlue(), 0);
        Color endColor = new Color(solidColor.getRed(), solidColor.getGreen(), solidColor.getBlue(), 255);

        BuiltRectangle opacityBar = Builder.rectangle()
            .size(new SizeState(alphaW, alphaH))
            .radius(new QuadRadiusState(0f))
            .color(new QuadColorState(
                startColor.getRGB(),
                startColor.getRGB(),
                endColor.getRGB(),
                endColor.getRGB()
            ))
            .build();
        opacityBar.render(matrix, alphaX, alphaY);

        float alphaThumbX = alphaX + (hsvAlpha / 255f) * alphaW;
        BuiltRectangle alphaThumb = Builder.rectangle()
            .size(new SizeState(2f, alphaH + 2f))
            .radius(new QuadRadiusState(0f))
            .color(new QuadColorState(new Color(255, 255, 255, (int) (255 * currentAlpha))))
            .build();
        alphaThumb.render(matrix, alphaThumbX - 1f, alphaY - 1f);

        float hexX = px + 8f;
        float hexY = py + 112f;
        float hexW = 70f;
        float hexH = 16f;

        BuiltRectangle hexBg = Builder.rectangle()
            .size(new SizeState(hexW, hexH))
            .radius(new QuadRadiusState(2f))
            .color(new QuadColorState(new Color(0, 0, 0, (int) (85 * currentAlpha))))
            .build();
        hexBg.render(matrix, hexX, hexY);

        String displayHex = hexInputText;
        if (hexFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
            displayHex += "|";
        }
        drawText(matrix, fontRegular, displayHex, 6.0f, hexFocused ? 0xFFFFFFFF : 0x90FFFFFF, hexX + 5f, hexY + hexH / 2f - fontRegular.getHeight(6.0f) / 2f);

        float copyX = px + 83f;
        float copyY = py + 112f;
        float copyW = 15f;
        float copyH = 16f;

        BuiltRectangle copyBg = Builder.rectangle()
            .size(new SizeState(copyW, copyH))
            .radius(new QuadRadiusState(2f))
            .color(new QuadColorState(new Color(0, 0, 0, (int) (85 * currentAlpha))))
            .build();
        copyBg.render(matrix, copyX, copyY);
        drawCopyIcon(matrix, copyX + 4f, copyY + 4.5f, 0xFFFFFFFF);

        float pasteX = px + 101f;
        float pasteY = py + 112f;
        float pasteW = 15f;
        float pasteH = 16f;

        BuiltRectangle pasteBg = Builder.rectangle()
            .size(new SizeState(pasteW, pasteH))
            .radius(new QuadRadiusState(2f))
            .color(new QuadColorState(new Color(0, 0, 0, (int) (85 * currentAlpha))))
            .build();
        pasteBg.render(matrix, pasteX, pasteY);
        drawPasteIcon(matrix, pasteX + 3.5f, pasteY + 4f, 0xFFFFFFFF);

        colorTargetDropdownAnimation.run(colorTargetDropdownOpen ? 1.0f : 0.0f);
        float colorDropdownProgress = colorTargetDropdownAnimation.getValue();

        if (colorDropdownProgress > 0.0f) {
            float listY = selectY + selectH + 1f;
            float optionH = 10f;
            float fullHeight = optionH * COLOR_TARGETS.length;
            float currentHeight = fullHeight * colorDropdownProgress;

            float scale = scaleAnimation.getValue() * guiSizeValue;
            float centerX = this.width / 2.0f;
            float centerY = this.height / 2.0f;

            context.enableScissor(
                (int) Math.floor(selectX),
                (int) Math.floor(listY),
                (int) Math.ceil(selectX + selectW),
                (int) Math.ceil(listY + currentHeight)
            );

            BuiltBlur listBg = Builder.blur()
                .size(new SizeState(selectW, fullHeight))
                .radius(new QuadRadiusState(2f))
                .blurRadius(blurValue)
                .smoothness(bloomValue)
                .color(new QuadColorState(new Color(22, 22, 22, (int) (220 * currentAlpha))))
                .build();
            listBg.render(matrix, selectX, listY);

            for (int i = 0; i < COLOR_TARGETS.length; i++) {
                boolean isSelected = (i == activeColorTargetIndex);
                if (isSelected) {
                    BuiltRectangle dot = Builder.rectangle()
                        .size(new SizeState(3f, 3f))
                        .radius(new QuadRadiusState(1.5f))
                        .color(new QuadColorState(new Color(255, 255, 255, (int) (255 * currentAlpha))))
                        .build();
                    dot.render(matrix, selectX + selectW - 8f, listY + 3.5f + i * optionH);
                }
                drawText(matrix, fontRegular, COLOR_TARGETS[i], 5.5f, 0xFFFFFFFF, selectX + 5f, listY + 2.5f + i * optionH);
            }

            context.disableScissor();
        }
    }

    private void drawText(Matrix4f matrix, MsdfFont font, String text, float size, int color, float tx, float ty) {
        if (text == null || text.isEmpty()) return;
        Builder.text()
            .font(font)
            .text(text)
            .size(size)
            .color(adjustAlpha(color, currentAlpha))
            .build()
            .render(matrix, tx, ty);
    }

    private void drawToggle(Matrix4f matrix, float tx, float ty, float animValue) {
        Color inactiveColor = new Color(255, 255, 255, (int) (40 * currentAlpha));
        Color activeColor = new Color(46, 178, 103, (int) (255 * currentAlpha));
        
        int r = (int) (inactiveColor.getRed() + (activeColor.getRed() - inactiveColor.getRed()) * animValue);
        int g = (int) (inactiveColor.getGreen() + (activeColor.getGreen() - inactiveColor.getGreen()) * animValue);
        int b = (int) (inactiveColor.getBlue() + (activeColor.getBlue() - inactiveColor.getBlue()) * animValue);
        int a = (int) (inactiveColor.getAlpha() + (activeColor.getAlpha() - inactiveColor.getAlpha()) * animValue);
        Color trackColor = new Color(r, g, b, a);
        
        BuiltRectangle track = Builder.rectangle()
            .size(new SizeState(15, 8))
            .radius(new QuadRadiusState(3f))
            .color(new QuadColorState(trackColor))
            .build();
        track.render(matrix, tx, ty);

        float thumbX = tx + 1f + animValue * 7.0f;
        BuiltRectangle thumb = Builder.rectangle()
            .size(new SizeState(6, 6))
            .radius(new QuadRadiusState(2f))
            .color(new QuadColorState(new Color(255, 255, 255, (int) (255 * currentAlpha))))
            .build();
        thumb.render(matrix, thumbX, ty + 1f);
    }

    private void drawSlider(Matrix4f matrix, float tx, float ty, float percent) {
        // Track
        BuiltRectangle track = Builder.rectangle()
            .size(new SizeState(45, 3))
            .radius(new QuadRadiusState(1f))
            .color(new QuadColorState(new Color(255, 255, 255, (int) (50 * currentAlpha))))
            .build();
        track.render(matrix, tx, ty);

        // Active Track (drawn up to thumb position)
        float activeWidth = percent * 45f;
        if (activeWidth > 0) {
            BuiltRectangle activeTrack = Builder.rectangle()
                .size(new SizeState(activeWidth, 3))
                .radius(new QuadRadiusState(1f))
                .color(new QuadColorState(new Color(255, 255, 255, (int) (255 * currentAlpha))))
                .build();
            activeTrack.render(matrix, tx, ty);
        }

        // Thumb
        float thumbX = tx + percent * 45f - 2.5f;
        BuiltRectangle thumb = Builder.rectangle()
            .size(new SizeState(6, 6))
            .radius(new QuadRadiusState(2f))
            .color(new QuadColorState(new Color(255, 255, 255, (int) (255 * currentAlpha))))
            .build();
        thumb.render(matrix, thumbX, ty - 1.5f);
    }

    private int adjustAlpha(int color, float alpha) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int newA = (int) (a * alpha);
        return (newA << 24) | (r << 16) | (g << 8) | b;
    }

    private Color adjustColorAlpha(Color color, float alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (color.getAlpha() * alpha));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float x = (this.width - WIDTH) / 2.0f;
        float y = (this.height - HEIGHT) / 2.0f;

        float scale = scaleAnimation.getValue() * guiSizeValue;
        float centerX = this.width / 2.0f;
        float centerY = this.height / 2.0f;
        if (scale > 0.0f) {
            mouseX = centerX + (mouseX - centerX) / scale;
            mouseY = centerY + (mouseY - centerY) / scale;
        }

        // Clear search "X" button check (must be before search focus check)
        if (!searchText.isEmpty()) {
            float searchX = x + 125.0f;
            float searchY = y + 7.5f;
            float searchW = 97.0f;
            float searchH = 14.0f;
            if (mouseX >= searchX + searchW - 14.0f && mouseX <= searchX + searchW - 2.0f && mouseY >= searchY && mouseY <= searchY + searchH) {
                searchText = "";
                searchFocused = false;
                searchSelectAll = false;
                searchResults.clear();
                lastSearchQuery = "";
                lastSearchFilter = null;
                return true;
            }
        }

        // Search Bar Focus check
        if (mouseX >= x + 125.0f && mouseX <= x + 222.0f && mouseY >= y + 7.5f && mouseY <= y + 21.5f) {
            searchFocused = true;
            hexFocused = false;
            return true;
        } else {
            searchFocused = false;
        }

        // Color Picker Click Checks
        if (colorPickerOpen) {
            float px = x + WIDTH + 6.0f;
            float py = y;

            // Close button click check (X button right of combobox)
            if (mouseX >= px + 104.0f && mouseX <= px + 122.0f && mouseY >= py + 5.0f && mouseY <= py + 23.0f) {
                colorPickerOpen = false;
                return true;
            }

            // Target dropdown click check
            if (colorTargetDropdownOpen) {
                if (mouseX >= px + 8.0f && mouseX <= px + 103.0f) {
                    float listY = py + 21.0f;
                    for (int i = 0; i < COLOR_TARGETS.length; i++) {
                        if (mouseY >= listY + i * 10.0f && mouseY <= listY + (i + 1) * 10.0f) {
                            activeColorTargetIndex = i;
                            updateHSVFromActiveTarget();
                            colorTargetDropdownOpen = false;
                            return true;
                        }
                    }
                }
                colorTargetDropdownOpen = false;
                return true;
            }

            if (mouseX >= px + 8.0f && mouseX <= px + 103.0f && mouseY >= py + 8.0f && mouseY <= py + 20.0f) {
                colorTargetDropdownOpen = true;
                return true;
            }

            // Color Map drag
            if (mouseX >= px + 8.0f && mouseX <= px + 101.0f && mouseY >= py + 24.0f && mouseY <= py + 92.0f) {
                draggingColorMap = true;
                updateColorMap(mouseX, mouseY, px, py);
                hexFocused = false;
                return true;
            }

            // Hue Bar drag
            if (mouseX >= px + 109.0f && mouseX <= px + 117.0f && mouseY >= py + 24.0f && mouseY <= py + 92.0f) {
                draggingHue = true;
                updateHue(mouseY, py);
                hexFocused = false;
                return true;
            }

            // Opacity Slider drag
            if (mouseX >= px + 8.0f && mouseX <= px + 117.0f && mouseY >= py + 98.0f && mouseY <= py + 104.0f) {
                draggingAlpha = true;
                updateAlpha(mouseX, px);
                hexFocused = false;
                return true;
            }

            // Hex focused check
            if (mouseX >= px + 8.0f && mouseX <= px + 78.0f && mouseY >= py + 112.0f && mouseY <= py + 128.0f) {
                hexFocused = true;
                searchFocused = false;
                return true;
            }

            // Copy button
            if (mouseX >= px + 83.0f && mouseX <= px + 98.0f && mouseY >= py + 112.0f && mouseY <= py + 128.0f) {
                MinecraftClient.getInstance().keyboard.setClipboard(hexInputText);
                return true;
            }

            // Paste button
            if (mouseX >= px + 101.0f && mouseX <= px + 116.0f && mouseY >= py + 112.0f && mouseY <= py + 128.0f) {
                String cb = MinecraftClient.getInstance().keyboard.getClipboard();
                if (cb != null && cb.startsWith("#") && (cb.length() == 7 || cb.length() == 9)) {
                    try {
                        long parsed = Long.parseLong(cb.substring(1), 16);
                        int argb = (int) parsed;
                        if (cb.length() == 7) argb = 0xFF000000 | argb;
                        setTargetColor(activeColorTargetIndex, argb);
                        updateHSVFromActiveTarget();
                    } catch (Exception e) {}
                }
                return true;
            }

            hexFocused = false;
        }

        if (searchText.isEmpty()) {
            // Handle dropdown toggle / selection
            if (dropdownOpen) {
                if (mouseX >= x + 180.0f && mouseX <= x + 220.0f) {
                    float dropY = y + 104.0f;
                    for (int i = 0; i < GLASS_STYLES.length; i++) {
                        if (mouseY >= dropY + i * 10.0f && mouseY <= dropY + (i + 1) * 10.0f) {
                            glassStyle = GLASS_STYLES[i];
                            dropdownOpen = false;
                            return true;
                        }
                    }
                }
                dropdownOpen = false;
                return true;
            }

            // Dropdown open check
            if (mouseX >= x + 180.0f && mouseX <= x + 220.0f && mouseY >= y + 94.0f && mouseY <= y + 104.0f) {
                dropdownOpen = true;
                return true;
            }

            // Toggle Main - Enabled
            if (mouseX >= x + 91.0f && mouseX <= x + 106.0f && mouseY >= y + 42.0f && mouseY <= y + 50.0f) {
                enabled = !enabled;
                return true;
            }

            // Toggle Main - Show Controls
            if (mouseX >= x + 91.0f && mouseX <= x + 106.0f && mouseY >= y + 55.0f && mouseY <= y + 63.0f) {
                showControls = !showControls;
                return true;
            }

            // Toggle Main - Show Next Song
            if (mouseX >= x + 91.0f && mouseX <= x + 106.0f && mouseY >= y + 68.0f && mouseY <= y + 76.0f) {
                showNextSong = !showNextSong;
                return true;
            }

            // Slider Blur
            if (mouseX >= x + 154.0f && mouseX <= x + 203.0f && mouseY >= y + 44.0f && mouseY <= y + 51.0f) {
                draggingBlur = true;
                updateBlur(mouseX);
                return true;
            }

            // Slider Bloom
            if (mouseX >= x + 154.0f && mouseX <= x + 203.0f && mouseY >= y + 57.0f && mouseY <= y + 64.0f) {
                draggingBloom = true;
                updateBloom(mouseX);
                return true;
            }

            // Slider Gui Size
            if (mouseX >= x + 154.0f && mouseX <= x + 203.0f && mouseY >= y + 70.0f && mouseY <= y + 77.0f) {
                draggingGuiSize = true;
                updateGuiSize(mouseX);
                return true;
            }

            // Colors box (toggles right-side Color Picker popup)
            if (mouseX >= x + 205.0f && mouseX <= x + 217.0f && mouseY >= y + 82.0f && mouseY <= y + 90.0f) {
                colorPickerOpen = !colorPickerOpen;
                if (colorPickerOpen) {
                    updateHSVFromActiveTarget();
                }
                return true;
            }

            // Dropdown open check (Glass Style box)
            if (mouseX >= x + 180.0f && mouseX <= x + 220.0f && mouseY >= y + 94.0f && mouseY <= y + 104.0f) {
                dropdownOpen = true;
                return true;
            }

            // Setup Spotify button click check
            if (!SpotifyManager.isConfigured()) {
                if (mouseX >= x + 63.5f && mouseX <= x + 166.5f && mouseY >= y + 110.0f && mouseY <= y + 132.0f) {
                    SpotifyManager.getInstance().startSetupServer();
                    net.minecraft.util.Util.getOperatingSystem().open("http://127.0.0.1:8888/setup");
                    return true;
                }
            }
        } else {
            // Search Mode Click Checks
            float searchX = x + 125.0f;
            float searchY = y + 7.5f;
            float searchW = 97.0f;
            float searchH = 14.0f;

            // 1. Clear search "X" button check
            if (mouseX >= searchX + searchW - 14.0f && mouseX <= searchX + searchW - 2.0f && mouseY >= searchY && mouseY <= searchY + searchH) {
                searchText = "";
                searchFocused = false;
                searchSelectAll = false;
                searchResults.clear();
                lastSearchQuery = "";
                lastSearchFilter = null;
                return true;
            }

            // 2. Search filters buttons check
            if (mouseY >= y + 27.5f && mouseY <= y + 36.5f) {
                float gap = 4.0f;
                float totalWidth = 0.0f;
                MsdfFont fontMedium = PRODUCT_SANS_MEDIUM.get();
                for (var filter : SpotifyManager.SearchFilter.values()) {
                    String label = filter == SpotifyManager.SearchFilter.OWN_PLAYLISTS ? "Library" : filter.getDisplayName();
                    float labelW = fontMedium.getWidth(label, 6.0f);
                    totalWidth += (labelW + 6.0f);
                }
                totalWidth += gap * (SpotifyManager.SearchFilter.values().length - 1);

                float fx = x + 222.0f - totalWidth;
                for (var filter : SpotifyManager.SearchFilter.values()) {
                    String label = filter == SpotifyManager.SearchFilter.OWN_PLAYLISTS ? "Library" : filter.getDisplayName();
                    float labelW = fontMedium.getWidth(label, 6.0f);
                    float boxW = labelW + 6.0f;
                    if (mouseX >= fx && mouseX <= fx + boxW) {
                        activeSearchFilter = filter;
                        return true;
                    }
                    fx += boxW + gap;
                }
            }

            // 3. Search result list clicks (Play and Heart)
            if (!searchLoading && !searchResults.isEmpty()) {
                for (int i = 0; i < Math.min(4, searchResults.size()); i++) {
                    var item = searchResults.get(i);
                    float ry = y + 38.0f + i * 24.0f;

                    if (mouseX >= x + 8.0f && mouseX <= x + 222.0f && mouseY >= ry && mouseY <= ry + 22.0f) {
                        // Play button clicked
                        if (mouseX >= x + 204.0f && mouseX <= x + 218.0f) {
                            if (item.isPlaylist()) {
                                SpotifyManager.getInstance().playPlaylist(item.id());
                            } else {
                                SpotifyManager.getInstance().playTrack(item.id());
                            }
                            return true;
                        }

                        // Heart button clicked
                        if (!item.isPlaylist() && mouseX >= x + 188.0f && mouseX <= x + 202.0f) {
                            boolean newLiked = !item.liked();
                            SpotifyManager.getInstance().likeTrack(item.id(), newLiked);
                            searchResults.set(i, new SpotifyManager.SearchResultTrack(
                                item.id(), item.title(), item.artist(), item.artworkUrl(), item.localArtworkPath(), newLiked, item.isPlaylist()
                            ));
                            return true;
                        }
                    }
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingBlur = false;
        draggingBloom = false;
        draggingGuiSize = false;
        draggingColorMap = false;
        draggingHue = false;
        draggingAlpha = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        float scale = scaleAnimation.getValue() * guiSizeValue;
        float centerX = this.width / 2.0f;
        float centerY = this.height / 2.0f;
        if (scale > 0.0f) {
            mouseX = centerX + (mouseX - centerX) / scale;
            mouseY = centerY + (mouseY - centerY) / scale;
        }

        if (draggingBlur) {
            updateBlur(mouseX);
            return true;
        }
        if (draggingBloom) {
            updateBloom(mouseX);
            return true;
        }
        if (draggingGuiSize) {
            updateGuiSize(mouseX);
            return true;
        }

        if (colorPickerOpen) {
            float x = (this.width - WIDTH) / 2.0f;
            float y = (this.height - HEIGHT) / 2.0f;
            float px = x + WIDTH + 6.0f;
            float py = y;

            if (draggingColorMap) {
                updateColorMap(mouseX, mouseY, px, py);
                return true;
            }
            if (draggingHue) {
                updateHue(mouseY, py);
                return true;
            }
            if (draggingAlpha) {
                updateAlpha(mouseX, px);
                return true;
            }
        }

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searchFocused) {
            if (searchSelectAll) {
                searchText = String.valueOf(chr);
                searchSelectAll = false;
            } else {
                searchText += chr;
            }
            return true;
        }
        if (hexFocused) {
            if (hexInputText.length() < 9 && ((chr >= '0' && chr <= '9') || (chr >= 'a' && chr <= 'f') || (chr >= 'A' && chr <= 'F') || chr == '#')) {
                hexInputText += chr;
                parseHexInput();
            }
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchFocused) {
            // Ctrl+A - Select All
            if (Screen.isSelectAll(keyCode)) {
                searchSelectAll = true;
                return true;
            }
            // Ctrl+C - Copy
            if (Screen.isCopy(keyCode)) {
                if (!searchText.isEmpty()) {
                    MinecraftClient.getInstance().keyboard.setClipboard(searchText);
                }
                return true;
            }
            // Ctrl+X - Cut
            if (Screen.isCut(keyCode)) {
                if (!searchText.isEmpty()) {
                    MinecraftClient.getInstance().keyboard.setClipboard(searchText);
                    searchText = "";
                    searchSelectAll = false;
                }
                return true;
            }
            // Ctrl+V - Paste
            if (Screen.isPaste(keyCode)) {
                String cb = MinecraftClient.getInstance().keyboard.getClipboard();
                if (cb != null) {
                    if (searchSelectAll) {
                        searchText = cb;
                        searchSelectAll = false;
                    } else {
                        searchText += cb;
                    }
                }
                return true;
            }
            // Backspace
            if (keyCode == 259) {
                if (searchSelectAll) {
                    searchText = "";
                    searchSelectAll = false;
                } else if (!searchText.isEmpty()) {
                    searchText = searchText.substring(0, searchText.length() - 1);
                }
                return true;
            }
            // Delete
            if (keyCode == 261) {
                if (searchSelectAll) {
                    searchText = "";
                    searchSelectAll = false;
                }
                return true;
            }
            // Escape
            if (keyCode == 256) {
                searchFocused = false;
                searchSelectAll = false;
                return true;
            }
            // Enter
            if (keyCode == 257) {
                searchFocused = false;
                searchSelectAll = false;
                return true;
            }
        }
        if (hexFocused) {
            if (keyCode == 259) { // GLFW_KEY_BACKSPACE
                if (!hexInputText.isEmpty()) {
                    hexInputText = hexInputText.substring(0, hexInputText.length() - 1);
                    parseHexInput();
                }
                return true;
            }
            if (keyCode == 256 || keyCode == 257) { // ESCAPE or ENTER
                hexFocused = false;
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void updateColorMap(double smouseX, double smouseY, float px, float py) {
        float mapX = px + 8f;
        float mapY = py + 24f;
        float mapW = 93f;
        float mapH = 68f;
        
        float pctX = (float) ((smouseX - mapX) / mapW);
        float pctY = (float) ((smouseY - mapY) / mapH);
        
        hsvSat = Math.max(0.0f, Math.min(1.0f, pctX));
        hsvVal = 1.0f - Math.max(0.0f, Math.min(1.0f, pctY));
        updateActiveTargetFromHSV();
    }

    private void updateHue(double smouseY, float py) {
        float hueY = py + 24f;
        float hueH = 68f;
        
        float pctY = (float) ((smouseY - hueY) / hueH);
        hsvHue = Math.max(0.0f, Math.min(1.0f, pctY));
        updateActiveTargetFromHSV();
    }

    private void updateAlpha(double smouseX, float px) {
        float alphaX = px + 8f;
        float alphaW = 109f;
        
        float pctX = (float) ((smouseX - alphaX) / alphaW);
        hsvAlpha = Math.max(0, Math.min(255, (int) (pctX * 255)));
        updateActiveTargetFromHSV();
    }

    private void parseHexInput() {
        String clean = hexInputText.replace("#", "");
        if (clean.length() == 6 || clean.length() == 8) {
            try {
                long parsed = Long.parseLong(clean, 16);
                int argb = (int) parsed;
                if (clean.length() == 6) {
                    argb = 0xFF000000 | argb;
                }
                setTargetColor(activeColorTargetIndex, argb);
                hsvAlpha = (argb >> 24) & 0xFF;
                float[] hsv = new float[3];
                Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, hsv);
                hsvHue = hsv[0];
                hsvSat = hsv[1];
                hsvVal = hsv[2];
            } catch (Exception e) {}
        }
    }

    private void updateBlur(double mouseX) {
        float x = (this.width - WIDTH) / 2.0f;
        float pct = (float) ((mouseX - (x + 154.0f)) / 45.0f);
        pct = Math.max(0.0f, Math.min(1.0f, pct));
        blurValue = Math.round((pct * 30.0f) * 10.0f) / 10.0f;
    }

    private void updateBloom(double mouseX) {
        float x = (this.width - WIDTH) / 2.0f;
        float pct = (float) ((mouseX - (x + 154.0f)) / 45.0f);
        pct = Math.max(0.0f, Math.min(1.0f, pct));
        bloomValue = Math.round((pct * 10.0f) * 10.0f) / 10.0f;
    }

    private void updateGuiSize(double mouseX) {
        float x = (this.width - WIDTH) / 2.0f;
        float pct = (float) ((mouseX - (x + 154.0f)) / 45.0f);
        pct = Math.max(0.0f, Math.min(1.0f, pct));
        guiSizeValue = Math.round((1.0f + pct * 0.4f) * 10.0f) / 10.0f;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // Static configuration getters for SpotifyOverlay
    public static boolean isOverlayEnabled() {
        return enabled;
    }

    public static boolean isShowControls() {
        return showControls;
    }

    public static boolean isShowNextSong() {
        return showNextSong;
    }

    public static float getBlurValue() {
        return blurValue;
    }

    public static float getBloomValue() {
        return bloomValue;
    }

    public static float getGuiSizeValue() {
        return guiSizeValue;
    }

    public static String getGlassStyle() {
        return glassStyle;
    }

    private void drawArrow(Matrix4f matrix, float tx, float ty, float size, boolean open, int color) {
        MsdfFont fluidFont = FLUID_FONT.get();
        
        // Exact mathematical center of the drawn MSDF quad for glyph 'B' relative to origin (tx, ty):
        // width = (right - left) = 0.3333 => ox = width / 2 = 0.16665
        // y_center = baselineHeight - (top + bottom)/2 = 0.756 - 0.291625 = 0.464375
        float ox = 0.16665f * size;
        float oy = 0.464375f * size;
        
        float cx = tx + ox;
        float cy = ty + oy;
        
        float angle = open ? (float) (-Math.PI / 2f) : (float) (Math.PI / 2f);
        
        Matrix4f rotMatrix = new Matrix4f(matrix);
        rotMatrix.translate(cx, cy, 0f);
        rotMatrix.rotateZ(angle);
        rotMatrix.translate(-ox, -oy, 0f);
        
        drawText(rotMatrix, fluidFont, "B", size, color, 0f, 0f);
    }

    // Static configuration getters for SpotifyOverlay custom colors
    public static int getOverlayBgColor() { return overlayBgColor; }
    public static int getOverlayTitleColor() { return overlayTitleColor; }
    public static int getOverlayArtistColor() { return overlayArtistColor; }
    public static int getOverlayTimeColor() { return overlayTimeColor; }
    public static int getOverlayProgressBarColor() { return overlayProgressBarColor; }

    private static int getTargetColor(int index) {
        switch (index) {
            case 0: return overlayBgColor;
            case 1: return overlayTitleColor;
            case 2: return overlayArtistColor;
            case 3: return overlayTimeColor;
            case 4: return overlayProgressBarColor;
            default: return 0xFFFFFFFF;
        }
    }

    private static void setTargetColor(int index, int argb) {
        switch (index) {
            case 0: overlayBgColor = argb; break;
            case 1: overlayTitleColor = argb; break;
            case 2: overlayArtistColor = argb; break;
            case 3: overlayTimeColor = argb; break;
            case 4: overlayProgressBarColor = argb; break;
        }
    }

    private static void updateHSVFromActiveTarget() {
        int rgb = getTargetColor(activeColorTargetIndex);
        hsvAlpha = (rgb >> 24) & 0xFF;
        float[] hsv = new float[3];
        Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, hsv);
        hsvHue = hsv[0];
        hsvSat = hsv[1];
        hsvVal = hsv[2];
        hexInputText = String.format("#%08X", rgb);
    }

    private static void updateActiveTargetFromHSV() {
        Color color = Color.getHSBColor(hsvHue, hsvSat, hsvVal);
        int argb = (hsvAlpha << 24) | (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();
        setTargetColor(activeColorTargetIndex, argb);
        hexInputText = String.format("#%08X", argb);
    }

    private void drawSVGradient(Matrix4f matrix, float x, float y, float w, float h, Color pureHue) {
        net.minecraft.client.render.Tessellator tessellator = net.minecraft.client.render.Tessellator.getInstance();
        net.minecraft.client.render.BufferBuilder builder = tessellator.begin(
            net.minecraft.client.render.VertexFormat.DrawMode.QUADS, 
            net.minecraft.client.render.VertexFormats.POSITION_COLOR
        );

        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.disableCull();
        com.mojang.blaze3d.systems.RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        int steps = (int) w;
        for (int i = 0; i < steps; i++) {
            float s = (float) i / (steps - 1);
            float nextS = (float) (i + 1) / (steps - 1);
            if (i == steps - 1) nextS = s;

            int r1 = (int) (255 - s * (255 - pureHue.getRed()));
            int g1 = (int) (255 - s * (255 - pureHue.getGreen()));
            int b1 = (int) (255 - s * (255 - pureHue.getBlue()));
            
            int r2 = (int) (255 - nextS * (255 - pureHue.getRed()));
            int g2 = (int) (255 - nextS * (255 - pureHue.getGreen()));
            int b2 = (int) (255 - nextS * (255 - pureHue.getBlue()));

            int cTopLeft = 0xFF000000 | (r1 << 16) | (g1 << 8) | b1;
            int cTopRight = 0xFF000000 | (r2 << 16) | (g2 << 8) | b2;
            int cBottom = 0xFF000000;

            float x1 = x + i;
            float x2 = x + i + 1.2f;

            builder.vertex(matrix, x1, y, 0).color(cTopLeft);
            builder.vertex(matrix, x1, y + h, 0).color(cBottom);
            builder.vertex(matrix, x2, y + h, 0).color(cBottom);
            builder.vertex(matrix, x2, y, 0).color(cTopRight);
        }

        net.minecraft.client.render.BufferRenderer.drawWithGlobalProgram(builder.end());
        com.mojang.blaze3d.systems.RenderSystem.enableCull();
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    private void drawCornerMask(Matrix4f matrix, float x, float y, int color) {
        BuiltRectangle mask = Builder.rectangle()
            .size(new SizeState(1.5f, 1.5f))
            .radius(new QuadRadiusState(0f))
            .color(new QuadColorState(color))
            .build();
        mask.render(matrix, x, y);
    }

    private void drawHueBar(Matrix4f matrix, float x, float y, float w, float h) {
        net.minecraft.client.render.Tessellator tessellator = net.minecraft.client.render.Tessellator.getInstance();
        net.minecraft.client.render.BufferBuilder builder = tessellator.begin(
            net.minecraft.client.render.VertexFormat.DrawMode.QUADS, 
            net.minecraft.client.render.VertexFormats.POSITION_COLOR
        );

        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.disableCull();
        com.mojang.blaze3d.systems.RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        Color[] rainbowColors = { Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED };
        float segH = h / 6.0f;
        for (int i = 0; i < 6; i++) {
            float y1 = y + i * segH;
            float y2 = y + (i + 1) * segH;
            if (i == 5) y2 = y + h;

            int cTop = rainbowColors[i].getRGB();
            int cBottom = rainbowColors[i+1].getRGB();

            builder.vertex(matrix, x, y1, 0).color(cTop);
            builder.vertex(matrix, x, y2, 0).color(cBottom);
            builder.vertex(matrix, x + w, y2, 0).color(cBottom);
            builder.vertex(matrix, x + w, y1, 0).color(cTop);
        }

        net.minecraft.client.render.BufferRenderer.drawWithGlobalProgram(builder.end());
        com.mojang.blaze3d.systems.RenderSystem.enableCull();
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    private void triggerSearch(String query, SpotifyManager.SearchFilter filter) {
        if (query.equals(lastSearchQuery) && filter == lastSearchFilter) {
            return;
        }
        lastSearchQuery = query;
        lastSearchFilter = filter;
        if (query.isEmpty()) {
            searchResults.clear();
            searchLoading = false;
            return;
        }
        searchLoading = true;
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                var results = SpotifyManager.getInstance().search(query, filter);
                MinecraftClient.getInstance().execute(() -> {
                    if (query.equals(lastSearchQuery) && filter == lastSearchFilter) {
                        this.searchResults = results;
                        this.searchLoading = false;
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                MinecraftClient.getInstance().execute(() -> {
                    if (query.equals(lastSearchQuery) && filter == lastSearchFilter) {
                        this.searchLoading = false;
                    }
                });
            }
        });
    }

    private void drawSpinner(Matrix4f matrix, float cx, float cy, float radius, float thickness, float alpha) {
        net.minecraft.client.render.Tessellator tessellator = net.minecraft.client.render.Tessellator.getInstance();
        net.minecraft.client.render.BufferBuilder builder = tessellator.begin(
            net.minecraft.client.render.VertexFormat.DrawMode.QUADS,
            net.minecraft.client.render.VertexFormats.POSITION_COLOR
        );

        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.disableCull();
        com.mojang.blaze3d.systems.RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        float angle = (float) ((System.currentTimeMillis() % 1000) / 1000.0 * Math.PI * 2.0);
        int color = new Color(255, 255, 255, (int) (alpha * 255)).getRGB();

        int segments = 40;
        int drawSegments = (int) (segments * 0.75f);
        float rInner = radius - thickness / 2.0f;
        float rOuter = radius + thickness / 2.0f;

        for (int i = 0; i < drawSegments; i++) {
            float a1 = angle + (float) i / segments * (float) Math.PI * 2.0f;
            float a2 = angle + (float) (i + 1) / segments * (float) Math.PI * 2.0f;

            float cos1 = (float) Math.cos(a1);
            float sin1 = (float) Math.sin(a1);
            float cos2 = (float) Math.cos(a2);
            float sin2 = (float) Math.sin(a2);

            builder.vertex(matrix, cx + rInner * cos1, cy + rInner * sin1, 0).color(color);
            builder.vertex(matrix, cx + rInner * cos2, cy + rInner * sin2, 0).color(color);
            builder.vertex(matrix, cx + rOuter * cos2, cy + rOuter * sin2, 0).color(color);
            builder.vertex(matrix, cx + rOuter * cos1, cy + rOuter * sin1, 0).color(color);
        }

        net.minecraft.client.render.BufferRenderer.drawWithGlobalProgram(builder.end());
        com.mojang.blaze3d.systems.RenderSystem.enableCull();
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }
}
