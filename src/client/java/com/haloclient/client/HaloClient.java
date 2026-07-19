package com.haloclient.client;

import com.haloclient.client.render.renderstates.BlurredQuadRenderState;
import com.haloclient.client.render.renderstates.BlurredRoundedRectangleRenderState;
import com.haloclient.client.render.CaptureManager;
import com.haloclient.client.render.HaloRenderPipelines;
import com.haloclient.client.render.animation.Animation;
import com.haloclient.client.render.animation.Easing;
import com.haloclient.client.render.font.FontManager;
import com.haloclient.client.render.font.HaloFontRenderState;
import com.haloclient.client.util.FrameClock;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.haloclient.client.module.ModuleManager;
import org.lwjgl.glfw.GLFW;

public class HaloClient implements ClientModInitializer {

    private static final Animation rectAnimation = new Animation(Easing.EASE_IN_OUT_CUBIC, 300L);
    private static float targetValue = 1.0f;
    
    public static HaloClient INSTANCE;
    public ModuleManager moduleManager;

    public static final net.minecraft.client.KeyMapping.Category HALO_CATEGORY = net.minecraft.client.KeyMapping.Category.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("halo", "halo"));

    public static final net.minecraft.client.KeyMapping openClickGuiKey = new net.minecraft.client.KeyMapping(
            "key.halo.clickgui",
            org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT,
            HALO_CATEGORY
    );

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        HaloRenderPipelines.init();
        moduleManager = new ModuleManager();
        com.haloclient.client.gui.click.SpotifyManager.getInstance().load();
        com.haloclient.client.gui.click.SpotifyManager.getInstance().startPolling();
        com.haloclient.client.gui.click.WindowsMediaManager.getInstance().start();

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openClickGuiKey.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new com.haloclient.client.gui.click.ClickGUI());
                }
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            try {
                com.haloclient.client.gui.click.SpotifyManager.getInstance().cleanOldArtworkCache();
            } catch (Throwable ignored) {}
            try {
                com.haloclient.client.gui.click.WindowsMediaManager.getInstance().stop();
            } catch (Throwable ignored) {}
        });
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public static void renderModules(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        FrameClock.update();

        if (INSTANCE != null && INSTANCE.moduleManager != null) {
            INSTANCE.moduleManager.getModules().stream()
                    .filter(com.haloclient.client.module.Module::isEnabled)
                    .forEach(m -> m.onRender(graphics, deltaTracker));
        }

        com.haloclient.client.gui.click.HttpNotificationManager.render(graphics);
    }

    public static void renderRounded(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        renderModules(graphics, deltaTracker);

        // 1. RENDER A BLURRED ROUNDED RECT
        var window = net.minecraft.client.Minecraft.getInstance().getWindow();
        float w = 200.0f, h = 30.0f;

        float x = (graphics.guiWidth() - w) / 2.0f;
        float y = 20;
        float radius = 15.0f;
        float blur = 600.0f;
        float bloom = 7f;

        var textureSetup = CaptureManager.getCaptureTextureSetup();
        int blurTintColor = ARGB.color(100, 9, 9, 9);

        if (GLFW.glfwGetKey(window.handle(), GLFW.GLFW_KEY_H) == GLFW.GLFW_PRESS) targetValue = 0.0f;
        if (GLFW.glfwGetKey(window.handle(), GLFW.GLFW_KEY_J) == GLFW.GLFW_PRESS) targetValue = 1.0f;
        
        rectAnimation.run(targetValue);
        float easedScale = rectAnimation.getValue();
        
        if (easedScale > 0.0001f) {

            Matrix3x2f pose = new Matrix3x2f(graphics.pose());
            float rectCenterX = x + w / 2.0f;
            float rectCenterY = y + h / 2.0f;
            pose.translate(rectCenterX, rectCenterY);
            pose.scale(easedScale);
            pose.translate(-rectCenterX, -rectCenterY);

            graphics.guiRenderState.addGuiElement(new BlurredRoundedRectangleRenderState(
                    HaloRenderPipelines.ROUNDED_BLUR,
                    textureSetup,
                    pose,
                    x, y, w, h,
                    blurTintColor,
                    radius,
                    blur,
                    bloom,
                    graphics.scissorStack.peek()
            ));
        }

        // 3. RENDER TEXT
        var interFont = FontManager.getFont("productsans-bold.ttf", 23f);
        if (interFont != null) {
            String text = "I hate jews";
            float textWidth = interFont.getWidth(text);
            float centerX = (graphics.guiWidth() - textWidth) / 2.0f;

            float textY = (graphics.guiHeight() - interFont.getHeight()) / 2.0f;
            
            graphics.guiRenderState.addGuiElement(new HaloFontRenderState(
                interFont,
                text,
                new Matrix3x2f(graphics.pose()),
                centerX, textY,
                ARGB.color(255, 255, 255, 255),
                graphics.scissorStack.peek()
            ));
        }


        // 3. RENDER 3D BOX UNDER PLAYER
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            double px, py, pz;
            var cameraState = mc.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState;
            
            // METODA PURE-MATRIX: Zero odwołań do 'żywych' pozycji gracza/kamery.
            // Wszystko liczymy na podstawie zamrożonej macierzy klatki.
            Matrix4f viewInv = new Matrix4f(cameraState.viewRotationMatrix).invert();
            
            // W trybie F5, oczy gracza są ZAWSZE na środku ekranu, czyli na osi Z kamery.
            // Wymuszamy 4.0 bloku dystansu, by sprawdzić absolutną płynność (brak jittera).
            float dist = 4.0f;
            if (mc.options.getCameraType().isFirstPerson()) dist = 0.0f;
            
            // Punkt w przestrzeni kamery (wycelowany w gracza)
            Vector4f relPos = new Vector4f(0, 0, -dist, 1.0f);
            
            // Transformacja do przestrzeni świata
            relPos.mul(viewInv);
            
            px = cameraState.pos.x + relPos.x;
            py = cameraState.pos.y + relPos.y - mc.player.getEyeHeight();
            pz = cameraState.pos.z + relPos.z;

            renderWorldBox(graphics, px - 0.5, py , pz - 0.5);
           // renderWorldBox(graphics, 0, 75, 0);
        }
    }

    public static void renderWorldBox(GuiGraphicsExtractor graphics, double ex, double ey, double ez) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.gameRenderer == null) return;

        var cameraState = mc.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState;
        var camPos = cameraState.pos;
        
        Matrix4f modelView = new Matrix4f(cameraState.viewRotationMatrix);
        Matrix4f projection = new Matrix4f(cameraState.projectionMatrix);
        
        var window = mc.getWindow();
        int winW = window.getWidth();
        int winH = window.getHeight();
        float guiScale = (float)window.getGuiScale();

        double sz = 1.0;
        double height = 1.0;
        Vec3[] v = new Vec3[] {
            new Vec3(ex, ey, ez),           // 0
            new Vec3(ex + sz, ey, ez),      // 1
            new Vec3(ex + sz, ey + height, ez), // 2
            new Vec3(ex, ey + height, ez),      // 3
            new Vec3(ex, ey, ez + sz),      // 4
            new Vec3(ex + sz, ey, ez + sz), // 5
            new Vec3(ex + sz, ey + height, ez + sz), // 6
            new Vec3(ex, ey + height, ez + sz)  // 7
        };

        // Ściany boxa - WSZYSTKIE ŚCIANY
        int[][] faces = {
            {3, 2, 6, 7}, // Góra
            {0, 1, 5, 4}, // Dół
            {0, 1, 2, 3}, // Przód
            {4, 5, 6, 7}, // Tył
            {0, 3, 7, 4}, // Lewo
            {1, 2, 6, 5}  // Prawo
        };

        // Sortowanie ścian od najdalszej (dla poprawnego blendingu)
        Integer[] faceIndices = new Integer[faces.length];
        for (int i = 0; i < faces.length; i++) faceIndices[i] = i;
        
        float[] distances = new float[faces.length];
        for (int i = 0; i < faces.length; i++) {
            Vec3 center = new Vec3(0, 0, 0);
            for (int idx : faces[i]) center = center.add(v[idx]);
            center = center.scale(0.25); // Środek ściany
            distances[i] = (float) center.distanceTo(camPos);
        }
        Arrays.sort(faceIndices, (a, b) -> Float.compare(distances[b], distances[a]));

        var textureSetup = CaptureManager.getCaptureTextureSetup();
        int boxColor = ARGB.color(10, 255, 255, 255); // Cyan-Blue Blur
        
        for (int i : faceIndices) {
            int[] face = faces[i];
            
            // 1. Zbieramy wierzchołki ściany
            List<Vector4f> poly = new ArrayList<>();
            for (int vIdx : face) {
                Vector4f p = new Vector4f((float)(v[vIdx].x - camPos.x), (float)(v[vIdx].y - camPos.y), (float)(v[vIdx].z - camPos.z), 1.0f);
                p.mul(modelView);
                p.mul(projection);
                poly.add(p);
            }

            // 2. Sutherland-Hodgman Clipping przeciwko near-plane (w = 0.05)
            float near = 0.05f;
            List<Vector4f> clipped = new ArrayList<>();
            for (int k = 0; k < poly.size(); k++) {
                Vector4f p1 = poly.get(k);
                Vector4f p2 = poly.get((k + 1) % poly.size());
                
                if (p1.w >= near) {
                    if (p2.w >= near) {
                        clipped.add(p2);
                    } else {
                        float t = (near - p1.w) / (p2.w - p1.w);
                        clipped.add(new Vector4f(p1.x + t*(p2.x-p1.x), p1.y + t*(p2.y-p1.y), p1.z + t*(p2.z-p1.z), near));
                    }
                } else if (p2.w >= near) {
                    float t = (near - p1.w) / (p2.w - p1.w);
                    clipped.add(new Vector4f(p1.x + t*(p2.x-p1.x), p1.y + t*(p2.y-p1.y), p1.z + t*(p2.z-p1.z), near));
                    clipped.add(p2);
                }
            }

            // 3. Rzutowanie na ekran i renderowanie (Triangle Fan jako Quady)
            if (clipped.size() >= 3) {
                List<Vector2f> screenPoints = new ArrayList<>();
                for (Vector4f cp : clipped) {
                    screenPoints.add(new Vector2f((cp.x / cp.w * 0.5f + 0.5f) * winW / guiScale, (1.0f - (cp.y / cp.w * 0.5f + 0.5f)) * winH / guiScale));
                }

                // Renderuj wachlarz wielokąta
                Vector2f p0 = screenPoints.get(0);
                for (int k = 1; k < screenPoints.size() - 1; k++) {
                    graphics.guiRenderState.addGuiElement(new BlurredQuadRenderState(
                        HaloRenderPipelines.ROUNDED_BLUR,
                        textureSetup,
                        new Matrix3x2f(graphics.pose()),
                        p0, screenPoints.get(k), screenPoints.get(k+1), p0,
                        boxColor,
                        100.0f, 100.0f, // Rozmiar teraz nie gra roli, bo radius jest ujemny
                        30.0f,  // Blur
                        graphics.scissorStack.peek()
                    ));
                }
            }
        }
    }
}