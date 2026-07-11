package cc.kamshi.gui;

import com.google.common.base.Suppliers;
import cc.kamshi.builders.Builder;
import cc.kamshi.builders.states.QuadColorState;
import cc.kamshi.builders.states.QuadRadiusState;
import cc.kamshi.builders.states.SizeState;
import cc.kamshi.msdf.MsdfFont;
import cc.kamshi.renderers.impl.BuiltBlur;
import cc.kamshi.renderers.impl.BuiltLiquidGlass;
import cc.kamshi.renderers.impl.BuiltRectangle;
import cc.kamshi.renderers.impl.BuiltTexture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import cc.kamshi.gui.animation.Animation;
import cc.kamshi.gui.animation.Easing;

public final class SpotifyOverlay {

    public static final float WIDTH = 175;
    public static final float HEIGHT = 44f;

    private static final Identifier SPOTIFY_ICON = Identifier.of("mre", "spotify-white-icon.png");

    // Position & scaling target states (instantly updated by drag/scroll)
    private static float targetX = 15f;
    private static float targetY = 15f;
    private static float targetScale = 1.2f;

    // Animations for positions and scale to ensure smooth transitions
    private static final Animation xAnimation = new Animation(Easing.EASE_OUT_CUBIC, 200L);
    private static final Animation yAnimation = new Animation(Easing.EASE_OUT_CUBIC, 200L);
    private static final Animation scaleAnimation = new Animation(Easing.EASE_OUT_CUBIC, 200L);
    private static boolean firstRender = true;

    // Draggable states
    private static boolean dragging = false;
    private static float dragOffsetX = 0f;
    private static float dragOffsetY = 0f;

    // Snap alignment states
    private static boolean showSnapLines = false;
    private static boolean snapXActive = false;
    private static boolean snapYActive = false;
    private static float snappedXLine = 0f;
    private static float snappedYLine = 0f;

    // Progress Bar Animation
    private static final Animation progressAnimation = new Animation(Easing.EASE_IN_OUT_QUAD, 150L);

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

    private static final Map<String, Identifier> LOADED_ARTWORKS = new HashMap<>();

    public static Identifier getOrCreateArtworkTexture(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        if (LOADED_ARTWORKS.containsKey(path)) {
            Identifier id = LOADED_ARTWORKS.get(path);
            if (id == null) {
                return null;
            }
            if (MinecraftClient.getInstance().getTextureManager().getTexture(id) != null) {
                return id;
            }
        }
        File file = new File(path);
        if (!file.exists() || file.length() == 0) {
            return null;
        }
        try (InputStream is = new FileInputStream(file)) {
            NativeImage nativeImage = NativeImage.read(is);
            NativeImageBackedTexture texture = new NativeImageBackedTexture(nativeImage);
            String safePath = "spotify_art_" + Math.abs(path.hashCode());
            Identifier id = Identifier.of("mre", safePath);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
            LOADED_ARTWORKS.put(path, id);
            return id;
        } catch (Exception e) {
            LOADED_ARTWORKS.put(path, null);
            return null;
        }
    }

    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        // Only render if overlay is enabled in ClickGUI settings
        if (!ClickGUI.isOverlayEnabled()) {
            return;
        }

        SpotifyManager.MediaStatus status = SpotifyManager.getStatus();
        if (status == null || !status.hasMedia()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden) {
            return;
        }

        float blurVal = ClickGUI.getBlurValue();
        float bloomVal = ClickGUI.getBloomValue();
        String glassStyle = ClickGUI.getGlassStyle();

        // Ensure the overlay target position is clamped to the screen boundaries
        float sw = client.getWindow().getScaledWidth();
        float sh = client.getWindow().getScaledHeight();
        float targetW = WIDTH * targetScale;
        float targetH = HEIGHT * targetScale;
        targetX = Math.max(0f, Math.min(sw - targetW, targetX));
        targetY = Math.max(0f, Math.min(sh - targetH, targetY));

        // Initialize animations on first render to avoid startup fly-in
        if (firstRender) {
            xAnimation.setValue(targetX);
            yAnimation.setValue(targetY);
            scaleAnimation.setValue(targetScale);
            firstRender = false;
        }

        // Tick animations
        xAnimation.run(targetX);
        yAnimation.run(targetY);
        scaleAnimation.run(targetScale);

        float currentX = xAnimation.getValue();
        float currentY = yAnimation.getValue();
        float currentScale = scaleAnimation.getValue();

        // Render Snap Lines & Guidelines when dragging (1:1 thickness, 255 alpha)
        if (dragging) {
            // Vertical guidelines: Left (15f), Center (sw / 2f), Right (sw - 15f)
            float[] vLines = { 15f, sw / 2f, sw - 15f };
            for (float vx : vLines) {
                boolean isSnapped = snapXActive && Math.abs(snappedXLine - vx) < 0.1f;
                int color = isSnapped ? 0xFF2EB267 : 0xFF646464;
                context.fill((int) vx, 0, (int) (vx + 1f), (int) sh, color);
            }

            // Horizontal guidelines: Top (15f), Center (sh / 2f), Bottom (sh - 15f)
            float[] hLines = { 15f, sh / 2f, sh - 15f };
            for (float vy : hLines) {
                boolean isSnapped = snapYActive && Math.abs(snappedYLine - vy) < 0.1f;
                int color = isSnapped ? 0xFF2EB267 : 0xFF646464;
                context.fill(0, (int) vy, (int) sw, (int) (vy + 1f), color);
            }
        }

        context.getMatrices().push();
        
        // Scale and translate the overlay using animated values
        context.getMatrices().translate(currentX, currentY, 0.0f);
        context.getMatrices().scale(currentScale, currentScale, 1.0f);
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();

        // Render Background Panel at scaled origin (0f, 0f)
        renderBackground(matrix, 0f, 0f, WIDTH, HEIGHT, glassStyle, blurVal, bloomVal);

        // Render Album Artwork
        int textureId = 0;
        if (!status.artworkPath().isEmpty()) {
            Identifier artId = getOrCreateArtworkTexture(status.artworkPath());
            if (artId != null) {
                var textureObj = client.getTextureManager().getTexture(artId);
                if (textureObj != null) {
                    textureId = textureObj.getGlId();
                }
            }
        }
        if (textureId == 0) {
            var textureObj = client.getTextureManager().getTexture(SPOTIFY_ICON);
            if (textureObj != null) {
                textureId = textureObj.getGlId();
            }
        }

        BuiltTexture artwork = Builder.texture()
            .size(new SizeState(32f, 32f))
            .texture(0f, 0f, 1f, 1f, textureId)
            .radius(4f)
            .smoothness(1f)
            .color(QuadColorState.WHITE)
            .build();
        artwork.render(matrix, 8f, 6f);

        // Render Track Title & Artist
        MsdfFont fontBold = PRODUCT_SANS_BOLD.get();
        MsdfFont fontRegular = PRODUCT_SANS_REGULAR.get();

        String title = status.title();
        if (title.length() > 25) {
            title = title.substring(0, 21) + "...";
        }
        drawText(matrix, fontBold, title, 8f, ClickGUI.getOverlayTitleColor(), 45f, 6f);

        String artist = status.artist();
        if (artist.length() > 28) {
            artist = artist.substring(0, 25) + "...";
        }
        drawText(matrix, fontBold, artist, 7f, ClickGUI.getOverlayArtistColor(), 45f, 16f);

        // Render Time (e.g. 0:21 / 2:15)
        String timeStr = formatTime(status.positionSeconds()) + " / " + formatTime(status.durationSeconds());
        float rightX = 165f - fontRegular.getWidth(timeStr, 7f);
        drawText(matrix, fontRegular, timeStr, 7f, ClickGUI.getOverlayTimeColor(), rightX, 16f);

        // Progress Bar
        float progressX = 44f;
        float progressY = 29f;
        float progressW = 125f;
        float progressH = 4f;

        BuiltRectangle trackBg = Builder.rectangle()
            .size(new SizeState(progressW, progressH))
            .radius(new QuadRadiusState(1.2f))
            .smoothness(1f)
            .color(new QuadColorState(new Color(255, 255, 255, 45)))
            .build();
        trackBg.render(matrix, progressX, progressY);

        progressAnimation.run(status.progress());
        float activeWidth = progressW * progressAnimation.getValue();
        if (activeWidth > 0) {
            BuiltRectangle trackActive = Builder.rectangle()
                .size(new SizeState(activeWidth, progressH))
                .radius(new QuadRadiusState(1.2f))
                .smoothness(1f)
                .color(new QuadColorState(new Color(ClickGUI.getOverlayProgressBarColor(), true)))
                .build();
            trackActive.render(matrix, progressX, progressY);
        }

        context.getMatrices().pop();
    }

    public static boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // Left Mouse Click
            float currentScale = scaleAnimation.getValue();
            float currentX = xAnimation.getValue();
            float currentY = yAnimation.getValue();
            float w = WIDTH * currentScale;
            float h = HEIGHT * currentScale;
            if (mouseX >= currentX && mouseX <= currentX + w && mouseY >= currentY && mouseY <= currentY + h) {
                dragging = true;
                // Compute offset using target positions to avoid double-transition errors
                dragOffsetX = (float) (mouseX - targetX);
                dragOffsetY = (float) (mouseY - targetY);
                return false; // Consume mouse click
            }
        }
        return true;
    }

    public static void onMouseDragged(double mouseX, double mouseY, int button) {
        if (dragging && button == 0) {
            float w = WIDTH * targetScale;
            float h = HEIGHT * targetScale;
            
            MinecraftClient client = MinecraftClient.getInstance();
            float sw = client.getWindow().getScaledWidth();
            float sh = client.getWindow().getScaledHeight();

            float tX = (float) (mouseX - dragOffsetX);
            float tY = (float) (mouseY - dragOffsetY);

            // Snapping constraints check (threshold: 5.0 pixels)
            snapXActive = false;
            snapYActive = false;
            showSnapLines = true;

            float threshold = 5.0f;
            
            // Snap to Left border
            if (Math.abs(tX - 15f) < threshold) {
                tX = 15f;
                snapXActive = true;
                snappedXLine = 15f;
            }
            // Snap to Horizontal Center of screen
            else if (Math.abs((tX + w / 2f) - (sw / 2f)) < threshold) {
                tX = sw / 2f - w / 2f;
                snapXActive = true;
                snappedXLine = sw / 2f;
            }
            // Snap to Right border
            else if (Math.abs(tX - (sw - 15f - w)) < threshold) {
                tX = sw - 15f - w;
                snapXActive = true;
                snappedXLine = sw - 15f;
            }

            // Snap to Top border
            if (Math.abs(tY - 15f) < threshold) {
                tY = 15f;
                snapYActive = true;
                snappedYLine = 15f;
            }
            // Snap to Vertical Center of screen
            else if (Math.abs((tY + h / 2f) - (sh / 2f)) < threshold) {
                tY = sh / 2f - h / 2f;
                snapYActive = true;
                snappedYLine = sh / 2f;
            }
            // Snap to Bottom border
            else if (Math.abs(tY - (sh - 15f - h)) < threshold) {
                tY = sh - 15f - h;
                snapYActive = true;
                snappedYLine = sh - 15f;
            }

            // Clamp target coords so it remains inside the screen boundaries
            targetX = Math.max(0f, Math.min(sw - w, tX));
            targetY = Math.max(0f, Math.min(sh - h, tY));

            if (dragging) {
                xAnimation.setValue(targetX);
                yAnimation.setValue(targetY);
            }
        }
    }

    public static void onMouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            dragging = false;
            showSnapLines = false;
            snapXActive = false;
            snapYActive = false;
        }
    }

    public static boolean onMouseScrolled(double mouseX, double mouseY, double amount) {
        float currentScale = scaleAnimation.getValue();
        float currentX = xAnimation.getValue();
        float currentY = yAnimation.getValue();
        float w = WIDTH * currentScale;
        float h = HEIGHT * currentScale;
        if (mouseX >= currentX && mouseX <= currentX + w && mouseY >= currentY && mouseY <= currentY + h) {
            float oldScale = targetScale;
            targetScale += (float) (amount * 0.08f);
            targetScale = Math.max(0.5f, Math.min(2.0f, targetScale));
            float newScale = targetScale;

            // Calculate center point using target values
            float centerX = targetX + (WIDTH * oldScale) / 2f;
            float centerY = targetY + (HEIGHT * oldScale) / 2f;

            // Adjust target top-left position to keep the center stationary
            targetX = centerX - (WIDTH * newScale) / 2f;
            targetY = centerY - (HEIGHT * newScale) / 2f;
            
            MinecraftClient client = MinecraftClient.getInstance();
            float sw = client.getWindow().getScaledWidth();
            float sh = client.getWindow().getScaledHeight();
            targetX = Math.max(0f, Math.min(sw - WIDTH * targetScale, targetX));
            targetY = Math.max(0f, Math.min(sh - HEIGHT * targetScale, targetY));
            return false; // Consume mouse scroll
        }
        return true;
    }

    private static void renderBackground(Matrix4f matrix, float x, float y, float w, float h, String glassStyle, float blurVal, float bloomVal) {
        Color bgColor = new Color(ClickGUI.getOverlayBgColor(), true);
        if ("Liquid".equalsIgnoreCase(glassStyle) || "LiquidGlass".equalsIgnoreCase(glassStyle)) {
            BuiltLiquidGlass liquid = Builder.liquidGlass()
                .size(new SizeState(w, h))
                .radius(new QuadRadiusState(8f))
                .blurRadius(blurVal)
                .smoothness(bloomVal)
                .color(new QuadColorState(bgColor))
                .build();
            liquid.render(matrix, x, y);
        } else {
            BuiltBlur blur = Builder.blur()
                .size(new SizeState(w, h))
                .radius(new QuadRadiusState(8f))
                .blurRadius(blurVal)
                .smoothness(bloomVal)
                .color(new QuadColorState(bgColor))
                .build();
            blur.render(matrix, x, y);
        }
    }

    private static void drawText(Matrix4f matrix, MsdfFont font, String text, float size, int color, float tx, float ty) {
        if (text == null || text.isEmpty()) return;
        Builder.text()
            .font(font)
            .text(text)
            .size(size)
            .color(color)
            .build()
            .render(matrix, tx, ty);
    }

    private static String formatTime(double seconds) {
        if (Double.isNaN(seconds) || Double.isInfinite(seconds) || seconds < 0) {
            seconds = 0;
        }
        int m = (int) (seconds / 60);
        int s = (int) (seconds % 60);
        return String.format("%d:%02d", m, s);
    }

}
