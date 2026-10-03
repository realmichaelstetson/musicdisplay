package com.haloclient.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.resources.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Menedżer obrazów obsługujący ładowanie tekstur z trzech źródeł:
 * 1. Identifier (zasoby Minecraft, np. "halo:icon.png")
 * 2. URL (obrazy z internetu, pobierane asynchronicznie)
 * 3. NativeImage (obrazy załadowane bezpośrednio z pamięci)
 *
 * Wszystkie tekstury są cachowane i automatycznie uploadowane na GPU
 * z filtrowaniem LINEAR dla najlepszej jakości skalowania.
 */
public class ImageManager {

    private static final Map<String, CachedImage> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> FAILED_CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService DOWNLOAD_POOL = Executors.newFixedThreadPool(2);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static GpuSampler sharedSampler;

    /**
     * Cached image data zawierająca TextureSetup gotowy do użycia w renderowaniu
     * oraz oryginalne wymiary obrazu potrzebne do poprawnego skalowania.
     */
    public record CachedImage(
        TextureSetup textureSetup,
        int width,
        int height,
        GpuTexture gpuTexture,
        GpuTextureView gpuTextureView
    ) {
        public float aspectRatio() {
            return (float) width / (float) height;
        }
    }

    /**
     * Tworzy lub pobiera z cache współdzielony sampler z filtrowaniem LINEAR
     * (bilinear filtering) dla najlepszej jakości skalowania obrazów.
     */
    private static GpuSampler getSharedSampler() {
        if (sharedSampler == null) {
            sharedSampler = RenderSystem.getDevice().createSampler(
                AddressMode.CLAMP_TO_EDGE,
                AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR,
                FilterMode.LINEAR,
                8, OptionalDouble.empty()
            );
        }
        return sharedSampler;
    }

    // ==========================================
    // === ŁADOWANIE Z IDENTIFIER (ZASOBY MC) ===
    // ==========================================

    /**
     * Ładuje obraz z zasobów Minecraft na podstawie Identifier.
     * Np. fromIdentifier(Identifier.fromNamespaceAndPath("halo", "icon.png"))
     * szuka pliku w assets/halo/icon.png
     *
     * @param id Identifier zasobu (np. "halo:icon.png")
     * @return CachedImage lub null jeśli nie znaleziono
     */
    public static CachedImage fromIdentifier(Identifier id) {
        String key = "id:" + id.toString();
        CachedImage cached = CACHE.get(key);
        if (cached != null) return cached;

        try {
            // Pełna ścieżka do zasobu w assets/<namespace>/textures/<path>
            Identifier texturePath = Identifier.fromNamespaceAndPath(id.getNamespace(), "textures/" + id.getPath());
            var resourceOpt = Minecraft.getInstance().getResourceManager().getResource(texturePath);

            if (resourceOpt.isEmpty()) {
                // Próbuj bez prefiksu textures/
                resourceOpt = Minecraft.getInstance().getResourceManager().getResource(id);
            }

            if (resourceOpt.isEmpty()) return null;

            try (InputStream is = resourceOpt.get().open()) {
                byte[] bytes = is.readAllBytes();
                CachedImage result = uploadFromBytes(id.toString(), bytes);
                if (result != null) CACHE.put(key, result);
                return result;
            }
        } catch (IOException e) {
            System.err.println("[ImageManager] Nie udało się załadować zasobu: " + id + " - " + e.getMessage());
            return null;
        }
    }

    // ================================
    // === ŁADOWANIE Z URL (ASYNC) ===
    // ================================

    /**
     * Pobiera obraz z URL asynchronicznie.
     * Przy pierwszym wywołaniu rozpoczyna pobieranie i zwraca null.
     * Kolejne wywołania zwracają CachedImage po zakończeniu pobierania.
     *
     * @param url URL obrazu do pobrania
     * @return CachedImage lub null jeśli jeszcze nie pobrano
     */
    private static String normalizeUrl(String url) {
        if (url == null) return "";
        if (url.contains("spotifycdn.com/image/")) {
            return url.replaceAll("https?://image-cdn-[a-zA-Z0-9-]+\\.spotifycdn\\.com/image/", "https://i.scdn.co/image/")
                      .replaceAll("https?://image-cdn\\.spotifycdn\\.com/image/", "https://i.scdn.co/image/");
        }
        return url;
    }

    public static CachedImage fromUrl(String url) {
        String normalized = normalizeUrl(url);
        String key = "url:" + normalized;
        CachedImage cached = CACHE.get(key);
        if (cached != null) return cached;

        // Sprawdź czy pobieranie jest w toku
        if (!PENDING_DOWNLOADS.containsKey(key)) {
            PENDING_DOWNLOADS.put(key, true);
            downloadAsync(normalized, key);
        }

        return null; // Jeszcze nie gotowe
    }

    private static final Map<String, Boolean> PENDING_DOWNLOADS = new ConcurrentHashMap<>();

    private static void downloadAsync(String url, String cacheKey) {
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(15))
                        .header("Accept", "image/jpeg, image/png")
                        .GET()
                        .build();

                HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());

                if (response.statusCode() == 200) {
                    byte[] data = response.body();

                    // Planujemy upload na renderThread
                    Minecraft.getInstance().execute(() -> {
                        try {
                            CachedImage result = uploadFromBytes(url, data);
                            if (result != null) {
                                CACHE.put(cacheKey, result);
                            } else {
                                FAILED_CACHE.put(cacheKey, true);
                            }
                            PENDING_DOWNLOADS.remove(cacheKey);
                        } catch (Exception e) {
                            System.err.println("[ImageManager] Błąd parsowania obrazu z URL: " + url + " - " + e.getMessage());
                            FAILED_CACHE.put(cacheKey, true);
                            PENDING_DOWNLOADS.remove(cacheKey);
                        }
                    });
                } else {
                    System.err.println("[ImageManager] HTTP error " + response.statusCode() + " dla URL: " + url);
                    PENDING_DOWNLOADS.remove(cacheKey);
                }
            } catch (Exception e) {
                System.err.println("[ImageManager] Błąd pobierania URL: " + url + " - " + e.getMessage());
                PENDING_DOWNLOADS.remove(cacheKey);
            }
        }, DOWNLOAD_POOL);
    }

    // =====================================
    // === ŁADOWANIE Z NATIVEIMAGE (RAM) ===
    // =====================================

    /**
     * Tworzy CachedImage z surowych bajtów obrazu (PNG/JPG/BMP/GIF).
     *
     * @param name Unikalna nazwa do cachowania
     * @param imageBytes Surowe bajty pliku obrazu
     * @return CachedImage gotowy do renderowania
     */
    public static CachedImage fromBytes(String name, byte[] imageBytes) {
        String key = "native:" + name;
        if (FAILED_CACHE.containsKey(key)) return null;
        CachedImage cached = CACHE.get(key);
        if (cached != null) return cached;

        CachedImage result = uploadFromBytes(name, imageBytes);
        if (result != null) {
            CACHE.put(key, result);
        } else {
            FAILED_CACHE.put(key, true);
        }
        return result;
    }

    // ==============================
    // === UPLOAD NA GPU (RDZEŃ) ===
    // ==============================

    /**
     * Dekoduje obraz z surowych bajtów przez STBImage i uploaduje na GPU
     * z pełnym łańcuchem mipmap dla ostrego renderowania przy każdej skali.
     */
    private static CachedImage uploadFromBytes(String context, byte[] data) {
        // Dekoduj obraz przez STBImage
        ByteBuffer inputBuffer = BufferUtils.createByteBuffer(data.length);
        inputBuffer.put(data);
        inputBuffer.flip();

        int[] wArr = new int[1], hArr = new int[1], channelsArr = new int[1];
        ByteBuffer pixels = STBImage.stbi_load_from_memory(inputBuffer, wArr, hArr, channelsArr, 4);

        if (pixels == null) {
            System.err.println("[ImageManager] STBImage nie mógł zdekodować obrazu [" + context + "]: " + STBImage.stbi_failure_reason());
            return null;
        }

        int w = wArr[0];
        int h = hArr[0];

        if (w < 16 || h < 16) {
            STBImage.stbi_image_free(pixels);
            System.err.println("[ImageManager] Za małe wymiary obrazu (placeholder/błąd): " + w + "x" + h);
            return null;
        }

        // Oblicz liczbę mip levels (pełny łańcuch aż do 1x1)
        int mipLevels = calculateMipLevels(w, h);

        // Tworzymy GPU texture Z MIPMAPAMI
        GpuTexture gpuTexture = RenderSystem.getDevice().createTexture(
                "Halo Image",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_TEXTURE_BINDING,
                GpuFormat.RGBA8_UNORM,
                w, h, 1, mipLevels
        );

        GpuTextureView gpuView = RenderSystem.getDevice().createTextureView(gpuTexture);
        var encoder = RenderSystem.getDevice().createCommandEncoder();

        // Upload mip level 0 (pełna rozdzielczość)
        encoder.writeToTexture(gpuTexture, pixels, 0, 0, 0, 0, w, h);

        // Generuj i uploaduj kolejne mip levels (box filter na CPU)
        ByteBuffer currentMip = pixels;
        int mipW = w, mipH = h;

        for (int level = 1; level < mipLevels; level++) {
            int newW = Math.max(1, mipW / 2);
            int newH = Math.max(1, mipH / 2);

            ByteBuffer nextMip = downsampleMip(currentMip, mipW, mipH, newW, newH);
            encoder.writeToTexture(gpuTexture, nextMip, level, 0, 0, 0, newW, newH);

            // Zwolnij poprzedni mip (ale nie oryginalne pixels - to zwalniamy na końcu)
            if (level > 1) {
                org.lwjgl.system.MemoryUtil.memFree(currentMip);
            }
            currentMip = nextMip;
            mipW = newW;
            mipH = newH;
        }

        // Zwolnij ostatni wygenerowany mip i oryginalne pixels
        if (mipLevels > 1) {
            org.lwjgl.system.MemoryUtil.memFree(currentMip);
        }
        STBImage.stbi_image_free(pixels);

        TextureSetup textureSetup = TextureSetup.singleTexture(gpuView, getSharedSampler());

        return new CachedImage(textureSetup, w, h, gpuTexture, gpuView);
    }

    /**
     * Oblicza liczbę mip levels dla danego rozmiaru tekstury.
     */
    private static int calculateMipLevels(int w, int h) {
        return (int) (Math.floor(Math.log(Math.min(w, h)) / Math.log(2))) + 1;
    }

    /**
     * Downscale o połowę za pomocą box filtra (średnia 2x2 pikseli).
     * Daje najlepszą jakość mipmapowania dla ostrych krawędzi (np. loga).
     */
    private static ByteBuffer downsampleMip(ByteBuffer src, int srcW, int srcH, int dstW, int dstH) {
        ByteBuffer dst = org.lwjgl.system.MemoryUtil.memAlloc(dstW * dstH * 4);

        for (int y = 0; y < dstH; y++) {
            for (int x = 0; x < dstW; x++) {
                int sx = x * 2;
                int sy = y * 2;

                for (int c = 0; c < 4; c++) {
                    int sum = 0;
                    int count = 0;

                    for (int oy = 0; oy < 2 && sy + oy < srcH; oy++) {
                        for (int ox = 0; ox < 2 && sx + ox < srcW; ox++) {
                            sum += Byte.toUnsignedInt(src.get(((sy + oy) * srcW + (sx + ox)) * 4 + c));
                            count++;
                        }
                    }

                    dst.put((byte) (sum / count));
                }
            }
        }

        dst.flip();
        return dst;
    }

    // ==============================
    // === ZARZĄDZANIE PAMIĘCIĄ ===
    // ==============================

    /**
     * Usuwa pojedynczy obraz z cache i zwalnia zasoby GPU.
     */
    public static void evict(String key) {
        CachedImage removed = CACHE.remove(key);
        if (removed != null) {
            removed.gpuTextureView().close();
            removed.gpuTexture().close();
        }
    }

    /**
     * Czyści cały cache i zwalnia wszystkie zasoby GPU.
     */
    public static void cleanup() {
        for (CachedImage img : CACHE.values()) {
            img.gpuTextureView().close();
            img.gpuTexture().close();
        }
        CACHE.clear();
        PENDING_DOWNLOADS.clear();
    }

    /**
     * Sprawdza czy obraz jest załadowany i gotowy do renderowania.
     */
    public static boolean isLoaded(String url) {
        return CACHE.containsKey("url:" + url);
    }
}
