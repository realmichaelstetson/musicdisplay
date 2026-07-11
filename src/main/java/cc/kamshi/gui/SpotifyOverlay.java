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
import net.minecraft.client.texture.AbstractTexture;
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

    private static final Identifier SPOTIFY_ICON = Identifier.of("kamshi", "spotify-white-icon.png");

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

    // Controls & Next Song Animations
    private static final Animation controlsAnimation = new Animation(Easing.EASE_OUT_CUBIC, 200L);
    private static final Animation nextSongAnimation = new Animation(Easing.EASE_OUT_CUBIC, 200L);
    private static boolean draggingVolume = false;

    public static float getCurrentHeight() {
        float controlsProgress = controlsAnimation.getValue();
        float expandedH = HEIGHT + 14f * controlsProgress;
        float nextSongProgress = nextSongAnimation.getValue();
        float totalLocalH = expandedH;
        if (nextSongProgress > 0.0f) {
            totalLocalH += 4f + 20f * nextSongProgress;
        }
        return totalLocalH;
    }

    private static final Supplier<MsdfFont> FLUID_FONT = Suppliers.memoize(() -> 
        MsdfFont.builder()
            .name("fluid-regular")
            .atlas("msdf/fluid-regular")
            .data("msdf/fluid-regular")
            .build()
    );

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

    private static final Supplier<MsdfFont> INTER_BOLD = Suppliers.memoize(() -> 
        MsdfFont.builder()
            .name("inter-bold")
            .atlas("msdf/inter-bold")
            .data("msdf/inter-bold")
            .build()
    );

    private static final Supplier<MsdfFont> INTER_REGULAR = Suppliers.memoize(() -> 
        MsdfFont.builder()
            .name("inter-regular")
            .atlas("msdf/inter-regular")
            .data("msdf/inter-regular")
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
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "spotify_art", nativeImage);
            String safePath = "spotify_art_" + Math.abs(path.hashCode());
            Identifier id = Identifier.of("kamshi", safePath);
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

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden) {
            return;
        }

        float blurVal = ClickGUI.getBlurValue();
        float bloomVal = ClickGUI.getBloomValue();
        String glassStyle = ClickGUI.getGlassStyle();

        SpotifyManager.MediaStatus status = SpotifyManager.getStatus();
        boolean hasMedia = status != null && status.hasMedia();
        boolean isConnected = SpotifyManager.isConfigured();

        String titleStr;
        String artistStr;
        String timeStr;
        float progressPct = 0.0f;
        String artworkPath = "";

        if (!isConnected) {
            titleStr = "Spotify is not connected";
            artistStr = "Setup inside ClickGUI";
            timeStr = "0:00 / 0:00";
            progressPct = 0.0f;
        } else if (!hasMedia) {
            titleStr = "Music is not playing";
            artistStr = "Play a track on Spotify";
            timeStr = "0:00 / 0:00";
            progressPct = 0.0f;
        } else {
            titleStr = status.title();
            artistStr = status.artist();
            timeStr = formatTime(status.positionSeconds()) + " / " + formatTime(status.durationSeconds());
            progressPct = status.progress();
            artworkPath = status.artworkPath();
        }

        float sw = client.getWindow().getScaledWidth();
        float sh = client.getWindow().getScaledHeight();

        // Target height calculation based on enabled panels
        controlsAnimation.run(ClickGUI.isShowControls() ? 1.0f : 0.0f);
        float controlsProgress = controlsAnimation.getValue();
        float expandedH = HEIGHT + 14f * controlsProgress;

        var nextTrack = SpotifyManager.getNextTrack();
        boolean shouldShowNext = ClickGUI.isShowNextSong() && nextTrack != null && nextTrack.hasMedia();
        nextSongAnimation.run(shouldShowNext ? 1.0f : 0.0f);
        float nextSongProgress = nextSongAnimation.getValue();

        float targetW = WIDTH * targetScale;
        float totalLocalH = expandedH;
        if (nextSongProgress > 0.0f) {
            totalLocalH += 4f + 20f * nextSongProgress;
        }
        float targetH = totalLocalH * targetScale;

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

            // Horizontal guidelines: Snap bounds
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
        renderBackground(matrix, 0f, 0f, WIDTH, expandedH, glassStyle, blurVal, bloomVal,8);

        // Render Album Artwork
        AbstractTexture artworkTexture = null;
        if (!artworkPath.isEmpty()) {
            Identifier artId = getOrCreateArtworkTexture(artworkPath);
            if (artId != null) {
                artworkTexture = client.getTextureManager().getTexture(artId);
            }
        }
        if (artworkTexture == null) {
            artworkTexture = client.getTextureManager().getTexture(SPOTIFY_ICON);
        }

        BuiltTexture artwork = Builder.texture()
            .size(new SizeState(32f, 32f))
            .texture(0f, 0f, 1f, 1f, artworkTexture)
            .radius(4f)
            .smoothness(1f)
            .color(QuadColorState.WHITE)
            .build();
        artwork.render(matrix, 8f, 6f);

        // Render Track Title & Artist
        MsdfFont fontBold = PRODUCT_SANS_BOLD.get();
        MsdfFont fontRegular = PRODUCT_SANS_REGULAR.get();

        // Dynamically shorten title if too long for the panel
        String displayTitle = titleStr;
        float maxTitleW = 165f - 45f;
        if (fontBold.getWidth(displayTitle, 8f) > maxTitleW) {
            while (!displayTitle.isEmpty() && fontBold.getWidth(displayTitle + "...", 8f) > maxTitleW) {
                displayTitle = displayTitle.substring(0, displayTitle.length() - 1);
            }
            displayTitle += "...";
        }
        drawText(matrix, context, fontBold, displayTitle, 8f, ClickGUI.getOverlayTitleColor(), 45f, 6f);

        // Render Time (e.g. 0:21 / 2:15)
        float rightX = 165f - fontRegular.getWidth(timeStr, 7f);
        drawText(matrix, context, fontRegular, timeStr, 7f, ClickGUI.getOverlayTimeColor(), rightX, 16f);

        // Dynamically shorten artist to prevent overlap with the time string on the same row
        String displayArtist = artistStr;
        float maxArtistW = rightX - 45f - 4f; // 4f gap
        if (fontBold.getWidth(displayArtist, 7f) > maxArtistW) {
            while (!displayArtist.isEmpty() && fontBold.getWidth(displayArtist + "...", 7f) > maxArtistW) {
                displayArtist = displayArtist.substring(0, displayArtist.length() - 1);
            }
            displayArtist += "...";
        }
        drawText(matrix, context, fontBold, displayArtist, 7f, ClickGUI.getOverlayArtistColor(), 45f, 16f);

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

        progressAnimation.run(progressPct);
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

        // Render controls bar (scissored for height expansion animation)
        if (controlsProgress > 0.0f) {
            context.enableScissor(
                (int) 0,
                (int) HEIGHT,
                (int) WIDTH,
                (int) (HEIGHT + 15f * controlsProgress)
            );

            MsdfFont fontFluid = FLUID_FONT.get();
            float controlY = HEIGHT -2f;

            // Volume bar background
            float volX = 18f;
            float volY = controlY + 3f;
            float volW = 32f;
            float volH = 3f;
            BuiltRectangle volBg = Builder.rectangle()
                .size(new SizeState(volW, volH))
                .radius(new QuadRadiusState(0.5f))
                .smoothness(0.5f)
                .color(new QuadColorState(new Color(255, 255, 255, 45)))
                .build();
            volBg.render(matrix, volX, volY);

            int volumePercent = status != null ? status.volumePercent() : 50;
            float activeVolW = volW * (volumePercent / 100.0f);
            if (activeVolW > 0.0f) {
                BuiltRectangle volActive = Builder.rectangle()
                    .size(new SizeState(activeVolW, volH))
                    .radius(new QuadRadiusState(0.5f))
                    .smoothness(0.5f)
                    .color(new QuadColorState(new Color(ClickGUI.getOverlayProgressBarColor(), true)))
                    .build();
                volActive.render(matrix, volX, volY);
            }

            // Volume Icon "E"
            drawText(matrix, context, fontFluid, "E", 14f, 0xFFFFFFFF, 10f, controlY - 2f);

            // Center controls: Prev ("H"), Play/Pause ("B"/"A"), Next ("G")
            drawText(matrix, context, fontFluid, "H", 13f, 0xFFFFFFFF, 73f, controlY - 2f);
            String playPauseGlyph = (status != null && status.isPlaying()) ? "A" : "B";
            drawText(matrix, context, fontFluid, playPauseGlyph, 13f, 0xFFFFFFFF, 85.8f, controlY - 2f);
            drawText(matrix, context, fontFluid, "G", 13f, 0xFFFFFFFF, 97.8f, controlY -2f);

            // Right controls: Repeat ("I"), Shuffle ("C"), Like ("D")
            int repeatColor = (status != null && !"off".equals(status.repeatState())) ? 0xFF2EB267 : 0xFFFFFFFF;
            drawText(matrix, context, fontFluid, "I", 13f, repeatColor, 139f, controlY - 2f);

            int shuffleColor = (status != null && status.shuffleState()) ? 0xFF2EB267 : 0xFFFFFFFF;
            drawText(matrix, context, fontFluid, "C", 14f, shuffleColor, 151f, controlY -2.5f);

            int likeColor = (status != null && status.liked()) ? 0xFF2EB267 : 0xFFFFFFFF;
            drawText(matrix, context, fontFluid, "D", 13f, likeColor, 163f, controlY - 2f);

            context.disableScissor();
        }

        // Show Next Song panel
        if (nextSongProgress > 0.0f) {
            float nextY = expandedH + 4f;

            context.getMatrices().push();
            float centerX = WIDTH / 2f;
            float centerY = nextY + 10f;
            context.getMatrices().translate(centerX, centerY, 0.0f);
            context.getMatrices().scale(nextSongProgress, nextSongProgress, 1.0f);
            context.getMatrices().translate(-centerX, -centerY, 0.0f);
            Matrix4f nextMatrix = context.getMatrices().peek().getPositionMatrix();

            renderBackground(nextMatrix, 0f, nextY, WIDTH, 20f, glassStyle, blurVal, bloomVal,6);

            AbstractTexture nextArtworkTexture = null;
            if (nextTrack != null && !nextTrack.artworkPath().isEmpty()) {
                Identifier nextArtId = getOrCreateArtworkTexture(nextTrack.artworkPath());
                if (nextArtId != null) {
                    nextArtworkTexture = client.getTextureManager().getTexture(nextArtId);
                }
            }
            if (nextArtworkTexture == null) {
                nextArtworkTexture = client.getTextureManager().getTexture(SPOTIFY_ICON);
            }

            BuiltTexture nextArtwork = Builder.texture()
                .size(new SizeState(14f, 14f))
                .texture(0f, 0f, 1f, 1f, nextArtworkTexture)
                .radius(2f)
                .smoothness(1f)
                .color(QuadColorState.WHITE)
                .build();
            nextArtwork.render(nextMatrix, 8f, nextY + 3f);

            String nextText = nextTrack.artist() + " - " + nextTrack.title();
            if (nextText.length() > 38) {
                nextText = nextText.substring(0, 35) + "...";
            }
            Builder.text()
                .font(fontRegular)
                .text(nextText)
                .size(6.5f)
                .color(0xFFFFFFFF)
                .build()
                .render(nextMatrix, 28f, nextY + 6f);

            context.getMatrices().pop();
        }

        context.getMatrices().pop();
    }

    public static boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // Left Mouse Click
            float currentScale = scaleAnimation.getValue();
            float currentX = xAnimation.getValue();
            float currentY = yAnimation.getValue();

            SpotifyManager.MediaStatus status = SpotifyManager.getStatus();
            float controlsVal = controlsAnimation.getValue();
            float nextSongVal = nextSongAnimation.getValue();
            float expandedH = HEIGHT + 14f * controlsVal;
            float totalH = expandedH;
            if (nextSongVal > 0.0f) {
                totalH += 4f + 20f * nextSongVal;
            }

            float w = WIDTH * currentScale;
            float h = totalH * currentScale;

            if (mouseX >= currentX && mouseX <= currentX + w && mouseY >= currentY && mouseY <= currentY + h) {
                double localX = (mouseX - currentX) / currentScale;
                double localY = (mouseY - currentY) / currentScale;

                if (ClickGUI.isShowControls() && localY >= HEIGHT && localY <= HEIGHT + 18f) {
                    // Check volume slider hit
                    if (localX >= 18f && localX <= 50f) {
                        float pct = (float) ((localX - 18f) / 32f);
                        pct = Math.max(0f, Math.min(1f, pct));
                        SpotifyManager.getInstance().setVolume((int) (pct * 100f));
                        draggingVolume = true;
                        return false;
                    }
                    // Shuffle: WIDTH / 2f - 40f => 154f in design or local coords WIDTH/2 - 40? 
                    // Let's use exact layout positions
                    // Prev (75f), Play/Pause (90f), Next (105f)
                    if (localX >= 71f && localX <= 79f) {
                        SpotifyManager.getInstance().previous();
                        return true;
                    }
                    if (localX >= 83f && localX <= 94f) {
                        SpotifyManager.getInstance().togglePlayPause();
                        return true;
                    }
                    if (localX >= 95f && localX <= 103f) {
                        SpotifyManager.getInstance().next();
                        return true;
                    }
                    // Repeat (142f)
                    if (localX >= 137f && localX <= 146f) {
                        SpotifyManager.getInstance().toggleRepeat();
                        return true;
                    }
                    // Shuffle (154f)
                    if (localX >= 149f && localX <= 157f) {
                        if (status != null) {
                            SpotifyManager.getInstance().toggleShuffle(!status.shuffleState());
                        }
                        return true;
                    }
                    // Like (166f)
                    if (localX >= 161f && localX <= 169f) {
                        SpotifyManager.getInstance().toggleLike();
                        return true;
                    }
                }

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
            float h = getCurrentHeight() * targetScale;
            
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
        } else if (draggingVolume && button == 0) {
            float currentScale = scaleAnimation.getValue();
            float currentX = xAnimation.getValue();
            double localX = (mouseX - currentX) / currentScale;
            float pct = (float) ((localX - 18f) / 32f);
            pct = Math.max(0f, Math.min(1f, pct));
            SpotifyManager.getInstance().setVolume((int) (pct * 100f));
        }
    }

    public static void onMouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            dragging = false;
            draggingVolume = false;
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
        float h = getCurrentHeight() * currentScale;
        if (mouseX >= currentX && mouseX <= currentX + w && mouseY >= currentY && mouseY <= currentY + h) {
            float oldScale = targetScale;
            targetScale += (float) (amount * 0.08f);
            targetScale = Math.max(0.5f, Math.min(2.0f, targetScale));
            float newScale = targetScale;

            // Calculate center point using target values
            float centerX = targetX + (WIDTH * oldScale) / 2f;
            float centerY = targetY + (getCurrentHeight() * oldScale) / 2f;

            // Adjust target top-left position to keep the center stationary
            targetX = centerX - (WIDTH * newScale) / 2f;
            targetY = centerY - (getCurrentHeight() * newScale) / 2f;
            
            MinecraftClient client = MinecraftClient.getInstance();
            float sw = client.getWindow().getScaledWidth();
            float sh = client.getWindow().getScaledHeight();
            targetX = Math.max(0f, Math.min(sw - WIDTH * targetScale, targetX));
            targetY = Math.max(0f, Math.min(sh - getCurrentHeight() * targetScale, targetY));
            return false; // Consume mouse scroll
        }
        return true;
    }

    private static void renderBackground(Matrix4f matrix, float x, float y, float w, float h, String glassStyle, float blurVal, float bloomVal, float rounding) {
        Color bgColor = new Color(ClickGUI.getOverlayBgColor(), true);
        if ("Liquid".equalsIgnoreCase(glassStyle) || "LiquidGlass".equalsIgnoreCase(glassStyle)) {
            BuiltLiquidGlass liquid = Builder.liquidGlass()
                .size(new SizeState(w, h))
                .radius(new QuadRadiusState(rounding))
                .blurRadius(blurVal)
                .smoothness(bloomVal)
                .color(new QuadColorState(bgColor))
                .build();
            liquid.render(matrix, x, y);
        } else {
            BuiltBlur blur = Builder.blur()
                .size(new SizeState(w, h))
                .radius(new QuadRadiusState(rounding))
                .blurRadius(blurVal)
                .smoothness(bloomVal)
                .color(new QuadColorState(bgColor))
                .build();
            blur.render(matrix, x, y);
        }
    }

    private static void drawText(Matrix4f matrix, DrawContext context, MsdfFont font, String text, float size, int color, float tx, float ty) {
        if (text == null || text.isEmpty()) return;

        MsdfFont fallbackFont = font.getName().contains("bold") ? INTER_BOLD.get() : INTER_REGULAR.get();
        float currentX = tx;

        int runStart = 0;
        MsdfFont runFont = null;

        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int count = Character.charCount(cp);
            
            MsdfFont activeFont = font.hasGlyph(cp) ? font : fallbackFont;
            if (runFont == null) {
                runFont = activeFont;
                runStart = i;
            } else if (activeFont != runFont) {
                String runText = text.substring(runStart, i);
                Builder.text()
                    .font(runFont)
                    .text(runText)
                    .size(size)
                    .color(color)
                    .build()
                    .render(matrix, currentX, ty);
                currentX += runFont.getWidth(runText, size);
                
                runFont = activeFont;
                runStart = i;
            }
            i += count;
        }

        if (runFont != null && runStart < text.length()) {
            String runText = text.substring(runStart);
            Builder.text()
                .font(runFont)
                .text(runText)
                .size(size)
                .color(color)
                .build()
                .render(matrix, currentX, ty);
        }
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
