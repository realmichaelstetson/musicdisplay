package com.haloclient.client.gui.click;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.client.Minecraft;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.labystudio.spotifyapi.SpotifyAPI;
import de.labystudio.spotifyapi.SpotifyAPIFactory;
import de.labystudio.spotifyapi.model.Track;

public final class SpotifyManager {

    private static final int PORT = 8888;
    private static final String REDIRECT_URI = "http://127.0.0.1:8888/callback";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final SpotifyManager INSTANCE = new SpotifyManager();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Halo Spotify Poller");
        thread.setDaemon(true);
        return thread;
    });

    private String clientId = "";
    private String clientSecret = "";
    private String accessToken = "";
    private String refreshToken = "";
    private long tokenExpiresAt = 0L;
    private boolean authorized = false;

    private String tempClientId;
    private String tempClientSecret;

    private HttpServer server;
    private boolean serverRunning = false;
    private volatile MediaStatus currentStatus = MediaStatus.EMPTY;
    private volatile boolean polling = false;
    private volatile NextTrack nextTrack = NextTrack.EMPTY;

    // Local process Spotify API fallback
    private SpotifyAPI localSpotifyAPI;
    private String lastLocalTrackId = "";
    private String lastPolledTrackId = "";
    private boolean lastPolledTrackLiked = false;


    private SpotifyManager() {
        try {
            localSpotifyAPI = SpotifyAPIFactory.createInitialized();
            System.out.println("[Halo/Spotify] Local Spotify API initialized successfully.");
        } catch (Throwable t) {
            System.err.println("[Halo/Spotify] Failed to initialize local Spotify API (os not supported or library missing): " + t.getMessage());
        }
    }

    public void cleanOldArtworkCache() {
        File cacheDir = new File(Minecraft.getInstance().gameDirectory, "config/spotify-search-cache");
        if (cacheDir.exists() && cacheDir.isDirectory()) {
            File[] files = cacheDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().endsWith(".png")) {
                        file.delete();
                    }
                }
            }
        }
        File configDir = new File(Minecraft.getInstance().gameDirectory, "config");
        if (configDir.exists() && configDir.isDirectory()) {
            File[] files = configDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().startsWith("spotify-art")) {
                        file.delete();
                    }
                }
            }
        }
    }

    private static boolean cleanedCache = false;
    public static SpotifyManager getInstance() {
        if (!cleanedCache) {
            cleanedCache = true;
            try {
                INSTANCE.cleanOldArtworkCache();
            } catch (Throwable t) {
                System.err.println("[Halo/Spotify] Failed to clean search artwork cache: " + t.getMessage());
            }
        }
        return INSTANCE;
    }

    public static MediaStatus getStatus() {
        if (MusicDisplayOverlay.getMusicSource() == MusicDisplayOverlay.MusicSource.WINDOWS) {
            return WindowsMediaManager.getStatus();
        }
        return INSTANCE.currentStatus;
    }

    public static NextTrack getNextTrack() {
        if (MusicDisplayOverlay.getMusicSource() == MusicDisplayOverlay.MusicSource.WINDOWS) {
            return NextTrack.EMPTY; // Windows SMTC doesn't expose next track
        }
        return INSTANCE.nextTrack;
    }

    public static boolean isConfigured() {
        if (MusicDisplayOverlay.getMusicSource() == MusicDisplayOverlay.MusicSource.WINDOWS) {
            return WindowsMediaManager.isConfigured();
        }
        return INSTANCE.authorized && !INSTANCE.clientId.isBlank() && !INSTANCE.clientSecret.isBlank();
    }

    public synchronized void load() {
        File file = getConfigFile();
        if (!file.exists()) {
            return;
        }
        try (Reader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (json.has("clientId")) this.clientId = json.get("clientId").getAsString();
            if (json.has("clientSecret")) this.clientSecret = json.get("clientSecret").getAsString();
            if (json.has("accessToken")) this.accessToken = json.get("accessToken").getAsString();
            if (json.has("refreshToken")) this.refreshToken = json.get("refreshToken").getAsString();
            if (json.has("tokenExpiresAt")) this.tokenExpiresAt = json.get("tokenExpiresAt").getAsLong();
            if (json.has("authorized")) this.authorized = json.get("authorized").getAsBoolean();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void save() {
        File file = getConfigFile();
        File dir = file.getParentFile();
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }
        JsonObject json = new JsonObject();
        json.addProperty("clientId", clientId);
        json.addProperty("clientSecret", clientSecret);
        json.addProperty("accessToken", accessToken);
        json.addProperty("refreshToken", refreshToken);
        json.addProperty("tokenExpiresAt", tokenExpiresAt);
        json.addProperty("authorized", authorized);

        try (Writer writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(json, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private File getConfigFile() {
        return new File(Minecraft.getInstance().gameDirectory, "config/spotify-config.json");
    }

    public synchronized void startSetupServer() {
        if (serverRunning) {
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.createContext("/setup", exchange -> {
                String html = getSetupHtml();
                byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            });

            server.createContext("/submit", exchange -> {
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> params = parseFormData(body);
                String cid = params.get("clientId");
                String csec = params.get("clientSecret");

                if (cid == null || cid.isBlank() || csec == null || csec.isBlank()) {
                    byte[] bytes = getErrorHtml("Client ID and Client Secret are required.").getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                    exchange.sendResponseHeaders(400, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }
                    return;
                }

                tempClientId = cid.trim();
                tempClientSecret = csec.trim();

                String authUrl = "https://accounts.spotify.com/authorize" +
                        "?client_id=" + URLEncoder.encode(tempClientId, StandardCharsets.UTF_8) +
                        "&response_type=code" +
                        "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8) +
                        "&scope=" + URLEncoder.encode("user-read-currently-playing user-read-playback-state user-modify-playback-state user-library-modify user-library-read playlist-read-private playlist-read-collaborative", StandardCharsets.UTF_8);

                exchange.getResponseHeaders().set("Location", authUrl);
                exchange.sendResponseHeaders(303, -1);
            });

            server.createContext("/callback", exchange -> {
                Map<String, String> query = parseFormData(exchange.getRequestURI().getQuery());
                String code = query.get("code");
                String error = query.get("error");

                if (error != null) {
                    byte[] bytes = getErrorHtml("Authorization error: " + error).getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                    exchange.sendResponseHeaders(400, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }
                    return;
                }

                if (code == null || code.isBlank()) {
                    byte[] bytes = getErrorHtml("No authorization code received.").getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                    exchange.sendResponseHeaders(400, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }
                    return;
                }

                if (tempClientId == null || tempClientSecret == null) {
                    byte[] bytes = getErrorHtml("Session expired. Please restart the setup in Minecraft.").getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                    exchange.sendResponseHeaders(400, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }
                    return;
                }

                boolean success = exchangeCodeForTokens(code, tempClientId, tempClientSecret);
                if (success) {
                    byte[] bytes = getSuccessHtml().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }

                    new Thread(() -> {
                        try {
                            Thread.sleep(1000);
                        } catch (Exception ignored) {
                        }
                        stopSetupServer();
                    }).start();
                } else {
                    byte[] bytes = getErrorHtml("Failed to exchange code for tokens. Please verify your Client ID and Client Secret.").getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                    exchange.sendResponseHeaders(400, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }
                }
            });

            server.start();
            serverRunning = true;
            System.out.println("[Halo/Spotify] Setup server started on port " + PORT);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void stopSetupServer() {
        if (!serverRunning) {
            return;
        }
        try {
            server.stop(0);
            serverRunning = false;
            System.out.println("[Halo/Spotify] Setup server stopped.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean exchangeCodeForTokens(String code, String clientId, String clientSecret) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            String requestBody = "grant_type=authorization_code" +
                    "&code=" + code +
                    "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8) +
                    "&client_id=" + clientId +
                    "&client_secret=" + clientSecret;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://accounts.spotify.com/api/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                this.accessToken = json.get("access_token").getAsString();
                if (json.has("refresh_token")) {
                    this.refreshToken = json.get("refresh_token").getAsString();
                }
                long expiresIn = json.get("expires_in").getAsLong();
                this.tokenExpiresAt = System.currentTimeMillis() + (expiresIn * 1000L);
                this.clientId = clientId;
                this.clientSecret = clientSecret;
                this.authorized = true;
                save();
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public synchronized void refreshToken() {
        if (refreshToken.isBlank()) {
            authorized = false;
            return;
        }
        try {
            HttpClient client = HttpClient.newHttpClient();
            String requestBody = "grant_type=refresh_token" +
                    "&refresh_token=" + refreshToken +
                    "&client_id=" + clientId +
                    "&client_secret=" + clientSecret;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://accounts.spotify.com/api/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                this.accessToken = json.get("access_token").getAsString();
                if (json.has("refresh_token")) {
                    this.refreshToken = json.get("refresh_token").getAsString();
                }
                long expiresIn = json.get("expires_in").getAsLong();
                this.tokenExpiresAt = System.currentTimeMillis() + (expiresIn * 1000L);
                save();
                System.out.println("[Halo/Spotify] Access token refreshed successfully.");
            } else {
                System.err.println("[Halo/Spotify] Failed to refresh token, status code: " + response.statusCode());
                if (response.statusCode() == 400) {
                    authorized = false;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void startPolling() {
        if (polling) return;
        polling = true;
        EXECUTOR.execute(() -> {
            while (polling) {
                try {
                    poll();
                    Thread.sleep(1500L);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void poll() {
        if (authorized && !clientId.isBlank() && !clientSecret.isBlank()) {
            pollWebAPI();
        } else {
            pollLocalAPI();
        }
    }

    private void pollWebAPI() {
        if (System.currentTimeMillis() + 300000L > tokenExpiresAt) {
            refreshToken();
        }

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.spotify.com/v1/me/player"))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            HttpResponse<String> response = sendRequest(client, request, "Failed to poll player status.");
            int code = response.statusCode();

            if (code == 204) {
                currentStatus = MediaStatus.EMPTY;
            } else if (code == 200) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                
                boolean isPlaying = false;
                if (json.has("is_playing") && !json.get("is_playing").isJsonNull()) {
                    isPlaying = json.get("is_playing").getAsBoolean();
                }

                boolean shuffleState = false;
                if (json.has("shuffle_state") && !json.get("shuffle_state").isJsonNull()) {
                    shuffleState = json.get("shuffle_state").getAsBoolean();
                }

                String repeatState = "off";
                if (json.has("repeat_state") && !json.get("repeat_state").isJsonNull()) {
                    repeatState = json.get("repeat_state").getAsString();
                }

                int volumePercent = 100;
                if (json.has("device") && !json.get("device").isJsonNull()) {
                    JsonObject device = json.getAsJsonObject("device");
                    if (device.has("volume_percent") && !device.get("volume_percent").isJsonNull()) {
                        volumePercent = device.get("volume_percent").getAsInt();
                    }
                }

                if (json.has("item") && !json.get("item").isJsonNull()) {
                    JsonObject item = json.getAsJsonObject("item");
                    String title = item.get("name").getAsString();

                    StringBuilder artists = new StringBuilder();
                    if (item.has("artists")) {
                        var arr = item.getAsJsonArray("artists");
                        for (int i = 0; i < arr.size(); i++) {
                            if (i > 0) artists.append(", ");
                            artists.append(arr.get(i).getAsJsonObject().get("name").getAsString());
                        }
                    }
                    String artist = artists.toString();

                    long durationMs = item.get("duration_ms").getAsLong();
                    long progressMs = json.get("progress_ms").getAsLong();

                    String trackId = "";
                    if (item.has("id") && !item.get("id").isJsonNull()) {
                        trackId = item.get("id").getAsString();
                    }

                    boolean liked = false;
                    if (!trackId.isEmpty()) {
                        if (trackId.equals(lastPolledTrackId)) {
                            liked = lastPolledTrackLiked;
                        } else {
                            try {
                                HttpRequest likedRequest = HttpRequest.newBuilder()
                                        .uri(URI.create("https://api.spotify.com/v1/me/tracks/contains?ids=" + trackId))
                                        .header("Authorization", "Bearer " + accessToken)
                                        .GET()
                                        .build();
                                HttpResponse<String> likedResp = client.send(likedRequest, HttpResponse.BodyHandlers.ofString());
                                if (likedResp.statusCode() == 200) {
                                    var likedArr = JsonParser.parseString(likedResp.body()).getAsJsonArray();
                                    if (likedArr.size() > 0) {
                                        liked = likedArr.get(0).getAsBoolean();
                                    }
                                }
                            } catch (Exception ignored) {}
                            lastPolledTrackId = trackId;
                            lastPolledTrackLiked = liked;
                        }
                    }

                    String artUrl = "";
                    if (item.has("album")) {
                        JsonObject album = item.getAsJsonObject("album");
                        if (album.has("images")) {
                            var images = album.getAsJsonArray("images");
                            if (images.size() > 0) {
                                artUrl = images.get(0).getAsJsonObject().get("url").getAsString();
                            }
                        }
                    }

                    String artPath = "";
                    if (!artUrl.isEmpty()) {
                        artPath = getAndDownloadArt(artUrl);
                    }

                    currentStatus = new MediaStatus(
                            title,
                            artist,
                            (double) progressMs / 1000.0,
                            (double) durationMs / 1000.0,
                            artPath,
                            trackId,
                            isPlaying,
                            shuffleState,
                            volumePercent,
                            liked,
                            repeatState
                    );

                    // Fetch next song from queue
                    try {
                        HttpRequest queueRequest = HttpRequest.newBuilder()
                                .uri(URI.create("https://api.spotify.com/v1/me/player/queue"))
                                .header("Authorization", "Bearer " + accessToken)
                                .GET()
                                .build();
                        HttpResponse<String> queueResponse = client.send(queueRequest, HttpResponse.BodyHandlers.ofString());
                        if (queueResponse.statusCode() == 200) {
                            JsonObject qJson = JsonParser.parseString(queueResponse.body()).getAsJsonObject();
                            if (qJson.has("queue")) {
                                var queueArr = qJson.getAsJsonArray("queue");
                                if (queueArr.size() > 0) {
                                    JsonObject nextItem = queueArr.get(0).getAsJsonObject();
                                    String nextTitle = nextItem.get("name").getAsString();
                                    StringBuilder nextArtists = new StringBuilder();
                                    if (nextItem.has("artists")) {
                                        var arr = nextItem.getAsJsonArray("artists");
                                        for (int aIdx = 0; aIdx < arr.size(); aIdx++) {
                                            if (aIdx > 0) nextArtists.append(", ");
                                            nextArtists.append(arr.get(aIdx).getAsJsonObject().get("name").getAsString());
                                        }
                                    }
                                    String nextArtist = nextArtists.toString();
                                    String nextArtUrl = "";
                                    if (nextItem.has("album")) {
                                        JsonObject album = nextItem.getAsJsonObject("album");
                                        if (album.has("images")) {
                                            var images = album.getAsJsonArray("images");
                                            if (images.size() > 0) {
                                                nextArtUrl = images.get(0).getAsJsonObject().get("url").getAsString();
                                            }
                                        }
                                    }
                                    String nextArtPath = "";
                                    if (!nextArtUrl.isEmpty()) {
                                        nextArtPath = getAndDownloadArt(nextArtUrl);
                                    }
                                    nextTrack = new NextTrack(nextTitle, nextArtist, nextArtPath);
                                } else {
                                    nextTrack = NextTrack.EMPTY;
                                }
                            }
                        } else {
                            nextTrack = NextTrack.EMPTY;
                        }
                    } catch (Exception ignored) {
                        nextTrack = NextTrack.EMPTY;
                    }
                } else {
                    currentStatus = MediaStatus.EMPTY;
                    nextTrack = NextTrack.EMPTY;
                }
            } else if (code == 401) {
                refreshToken();
                nextTrack = NextTrack.EMPTY;
            } else {
                currentStatus = MediaStatus.EMPTY;
                nextTrack = NextTrack.EMPTY;
            }
        } catch (Exception e) {
            e.printStackTrace();
            currentStatus = MediaStatus.EMPTY;
            nextTrack = NextTrack.EMPTY;
        }
    }

    private void pollLocalAPI() {
        if (localSpotifyAPI == null) {
            currentStatus = MediaStatus.EMPTY;
            return;
        }
        try {
            if (localSpotifyAPI.hasTrack()) {
                Track track = localSpotifyAPI.getTrack();
                String title = track.getName();
                String artist = track.getArtist();
                double duration = track.getLength();

                double position = 0.0;
                if (localSpotifyAPI.hasPosition()) {
                    position = localSpotifyAPI.getPosition() / 1000.0;
                }

                String artPath = "";
                String trackId = track.getId();
                if (trackId != null && !trackId.isBlank()) {
                    if (!trackId.equals(lastLocalTrackId)) {
                        lastLocalTrackId = trackId;
                        BufferedImage cover = track.getCoverArt();
                        if (cover != null) {
                            File tempFile = new File(Minecraft.getInstance().gameDirectory, "config/spotify-art-temp.png");
                            File targetFile = new File(Minecraft.getInstance().gameDirectory, "config/spotify-art.png");
                            if (!tempFile.getParentFile().exists()) {
                                tempFile.getParentFile().mkdirs();
                            }
                            ImageIO.write(cover, "png", tempFile);
                            if (tempFile.exists()) {
                                Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            }
                        }
                    }
                    File artFile = new File(Minecraft.getInstance().gameDirectory, "config/spotify-art.png");
                    if (artFile.exists()) {
                        artPath = artFile.getAbsolutePath();
                    }
                }

                currentStatus = new MediaStatus(title, artist, position, duration, artPath, trackId != null ? trackId : "", true, false, 100, false, "off");
            } else {
                currentStatus = MediaStatus.EMPTY;
            }
        } catch (Exception e) {
            e.printStackTrace();
            currentStatus = MediaStatus.EMPTY;
        }
    }

    private String lastArtworkUrl = "";
    private String getAndDownloadArt(String url) {
        if (url == null || url.isBlank()) return "";
        String hash = String.valueOf(Math.abs(url.hashCode()));
        File artFile = new File(Minecraft.getInstance().gameDirectory, "config/spotify-art-" + hash + ".png");
        if (artFile.exists()) {
            return artFile.getAbsolutePath();
        }
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
            File tempFile = new File(Minecraft.getInstance().gameDirectory, "config/spotify-art-temp-" + hash + ".png");
            if (tempFile.exists()) {
                tempFile.delete();
            }
            if (!tempFile.getParentFile().exists()) {
                tempFile.getParentFile().mkdirs();
            }
            client.send(request, HttpResponse.BodyHandlers.ofFile(tempFile.toPath()));
            if (tempFile.exists()) {
                Files.move(tempFile.toPath(), artFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                tempFile.delete();
                return artFile.getAbsolutePath();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    private static Map<String, String> parseFormData(String query) {
        Map<String, String> result = new HashMap<>();
        if (query == null || query.isBlank()) {
            return result;
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            try {
                if (idx > 0 && pair.length() > idx + 1) {
                    String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                    result.put(key, value);
                } else if (idx > 0) {
                    String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                    result.put(key, "");
                }
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private String getSetupHtml() {
        String currentId = clientId != null ? clientId : "";
        String currentSecret = clientSecret != null ? clientSecret : "";
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <title>Spotify API Setup</title>
                    <script src="https://cdn.tailwindcss.com"></script>
                    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
                    <style>
                        body { font-family: 'Inter', sans-serif; }
                    </style>
                </head>
                <body class="bg-zinc-950 text-zinc-50 flex items-center justify-center min-h-screen antialiased px-4">
                    <div class="w-full max-w-md bg-zinc-900 border border-zinc-800 rounded-lg p-6 shadow-2xl relative overflow-hidden">
                        <div class="absolute top-0 left-0 w-full h-[2px] bg-gradient-to-r from-emerald-500 to-green-500"></div>
                        <div class="flex items-center space-x-2 mb-4">
                            <svg class="h-6 w-6 text-green-500 fill-current" viewBox="0 0 24 24">
                                <path d="M12 2C6.477 2 2 6.477 2 12s4.477 10 10 10 10-4.477 10-10S17.523 2 12 2zm4.586 14.424c-.18.295-.565.387-.86.207-2.377-1.454-5.37-1.783-8.893-.982-.336.075-.668-.135-.744-.47-.077-.337.135-.668.47-.745 3.856-.88 7.15-.502 9.82 1.132.296.18.388.565.207.858zm1.224-2.723c-.226.367-.707.487-1.074.26-2.72-1.672-6.87-2.157-10.078-1.182-.413.125-.847-.107-.972-.52-.125-.413.108-.847.52-.972 3.67-1.114 8.243-.574 11.344 1.332.368.228.49.708.26 1.082zm.106-2.842C14.392 8.74 8.57 8.55 5.2 9.572c-.542.164-1.11-.144-1.274-.686-.164-.542.144-1.11.686-1.275 3.864-1.173 10.3-0.95 14.385 1.478.487.29.648.922.358 1.41-.29.486-.922.647-1.41.357z"/>
                            </svg>
                            <h2 class="text-xl font-bold tracking-tight text-white">Spotify Integration</h2>
                        </div>
                        <p class="text-sm text-zinc-400 mb-6 leading-relaxed">
                            To connect Minecraft with the Spotify API, please enter your Spotify Developer Credentials.
                        </p>
                        
                        <div class="bg-zinc-950 border border-zinc-800 rounded-md p-4 mb-6 text-xs text-zinc-400 space-y-2 leading-relaxed">
                            <span class="font-semibold text-zinc-300 block mb-1">Configuration Steps:</span>
                            <ul class="list-decimal list-inside space-y-1">
                                <li>Log in to the <a href="https://developer.spotify.com/dashboard" target="_blank" class="text-green-400 hover:underline">Spotify Developer Dashboard</a>.</li>
                                <li>Create a new application.</li>
                                <li>Edit Settings and add <code class="text-zinc-200">http://127.0.0.1:8888/callback</code> to the <strong>Redirect URIs</strong>.</li>
                                <li>Copy the <strong>Client ID</strong> and <strong>Client Secret</strong> below.</li>
                            </ul>
                        </div>
 
                        <form action="/submit" method="POST" class="space-y-4">
                            <div>
                                <label for="clientId" class="block text-xs font-semibold uppercase tracking-wider text-zinc-400 mb-1">Client ID</label>
                                <input type="text" id="clientId" name="clientId" value="%CLIENT_ID%" required class="w-full px-3 py-2 bg-zinc-950 border border-zinc-800 rounded-md text-zinc-100 placeholder-zinc-700 focus:outline-none focus:ring-2 focus:ring-green-500 focus:border-green-500 transition duration-200" placeholder="Enter Client ID">
                            </div>
                            <div>
                                <label for="clientSecret" class="block text-xs font-semibold uppercase tracking-wider text-zinc-400 mb-1">Client Secret</label>
                                <input type="password" id="clientSecret" name="clientSecret" value="%CLIENT_SECRET%" required class="w-full px-3 py-2 bg-zinc-950 border border-zinc-800 rounded-md text-zinc-100 placeholder-zinc-700 focus:outline-none focus:ring-2 focus:ring-green-500 focus:border-green-500 transition duration-200" placeholder="Enter Client Secret">
                            </div>
                            <button type="submit" class="w-full py-2 bg-white hover:bg-zinc-200 text-zinc-950 font-bold rounded-md transition duration-200 shadow-md">
                                Connect Spotify Account
                            </button>
                        </form>
                    </div>
                </body>
                </html>
                """.replace("%CLIENT_ID%", currentId).replace("%CLIENT_SECRET%", currentSecret);
    }

    private String getSuccessHtml() {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <title>Authorization Successful</title>
                    <script src="https://cdn.tailwindcss.com"></script>
                    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
                    <style>
                        body { font-family: 'Inter', sans-serif; }
                    </style>
                </head>
                <body class="bg-zinc-950 text-zinc-50 flex items-center justify-center min-h-screen antialiased px-4">
                    <div class="w-full max-w-md bg-zinc-900 border border-zinc-800 rounded-lg p-6 shadow-2xl relative overflow-hidden text-center">
                        <div class="absolute top-0 left-0 w-full h-[2px] bg-green-500"></div>
                        <div class="inline-flex items-center justify-center w-12 h-12 rounded-full bg-green-500/10 text-green-500 mb-4">
                            <svg class="h-6 w-6 stroke-current" fill="none" viewBox="0 0 24 24" stroke-width="2">
                                <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
                            </svg>
                        </div>
                        <h2 class="text-xl font-bold tracking-tight text-white mb-2">Setup Completed</h2>
                        <p class="text-sm text-zinc-400 mb-4 leading-relaxed">
                            Spotify API has been successfully authorized and integrated with Minecraft!
                        </p>
                        <p class="text-xs text-zinc-500">
                            You can close this browser tab and return to the game now.
                        </p>
                    </div>
                </body>
                </html>
                """;
    }

    private String getErrorHtml(String reason) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <title>Authorization Failed</title>
                    <script src="https://cdn.tailwindcss.com"></script>
                    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
                    <style>
                        body { font-family: 'Inter', sans-serif; }
                    </style>
                </head>
                <body class="bg-zinc-950 text-zinc-50 flex items-center justify-center min-h-screen antialiased px-4">
                    <div class="w-full max-w-md bg-zinc-900 border border-zinc-800 rounded-lg p-6 shadow-2xl relative overflow-hidden text-center">
                        <div class="absolute top-0 left-0 w-full h-[2px] bg-red-500"></div>
                        <div class="inline-flex items-center justify-center w-12 h-12 rounded-full bg-red-500/10 text-red-500 mb-4">
                            <svg class="h-6 w-6 stroke-current" fill="none" viewBox="0 0 24 24" stroke-width="2">
                                <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
                            </svg>
                        </div>
                        <h2 class="text-xl font-bold tracking-tight text-white mb-2">Authorization Failed</h2>
                        <p class="text-sm text-zinc-400 mb-6 leading-relaxed">
                            """ + reason + """
                        </p>
                        <a href="/setup" class="inline-block w-full py-2 bg-zinc-800 hover:bg-zinc-700 text-zinc-100 font-semibold rounded-md transition duration-200">
                            Try Again
                        </a>
                    </div>
                </body>
                </html>
                """;
    }

    public record SearchResultTrack(String id, String title, String artist, String artworkUrl, String localArtworkPath, boolean liked, boolean isPlaylist) {
        public SearchResultTrack(String id, String title, String artist, String artworkUrl, String localArtworkPath, boolean liked) {
            this(id, title, artist, artworkUrl, localArtworkPath, liked, false);
        }
    }

    public enum SearchFilter {
        ALL("All"),
        SONGS("Songs"),
        PLAYLISTS("Playlists"),
        OWN_PLAYLISTS("Library");

        private final String displayName;
        SearchFilter(String displayName) {
            this.displayName = displayName;
        }
        public String getDisplayName() {
            return displayName;
        }
    }

    public synchronized java.util.List<SearchResultTrack> searchTracks(String query) {
        return search(query, SearchFilter.ALL);
    }

    public synchronized java.util.List<SearchResultTrack> search(String query, SearchFilter filter) {
        java.util.List<SearchResultTrack> results = new java.util.ArrayList<>();
        if (!authorized || accessToken.isBlank()) return results;
        if (System.currentTimeMillis() + 30000L > tokenExpiresAt) {
            refreshToken();
        }
        try {
            HttpClient client = HttpClient.newHttpClient();
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);

            if (filter == SearchFilter.OWN_PLAYLISTS) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.spotify.com/v1/me/playlists?limit=50"))
                        .header("Authorization", "Bearer " + accessToken)
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    if (json.has("items")) {
                        var items = json.getAsJsonArray("items");
                        int count = 0;
                        for (int i = 0; i < items.size() && count < 5; i++) {
                            if (items.get(i) == null || !items.get(i).isJsonObject()) continue;
                            JsonObject playlist = items.get(i).getAsJsonObject();
                            String name = playlist.get("name").getAsString();
                            if (name.toLowerCase().contains(query.toLowerCase())) {
                                String id = playlist.get("id").getAsString();
                                String owner = playlist.getAsJsonObject("owner").get("display_name").getAsString();
                                String artUrl = "";
                                if (playlist.has("images")) {
                                    var images = playlist.getAsJsonArray("images");
                                    if (images.size() > 0) {
                                        artUrl = images.get(0).getAsJsonObject().get("url").getAsString();
                                    }
                                }
                                String localArtPath = "";
                                if (!artUrl.isEmpty()) {
                                    localArtPath = getAndDownloadSearchArt(id, artUrl);
                                }
                                results.add(new SearchResultTrack(id, name, "Playlist by " + owner, artUrl, localArtPath, false, true));
                                count++;
                            }
                        }
                    }
                } else if (response.statusCode() == 401) {
                    refreshToken();
                }
                return results;
            }

            String typeParam = "track";
            int limit = 5;
            if (filter == SearchFilter.SONGS) {
                typeParam = "track";
            } else if (filter == SearchFilter.PLAYLISTS) {
                typeParam = "playlist";
            } else if (filter == SearchFilter.ALL) {
                typeParam = "track,playlist";
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.spotify.com/v1/search?q=" + encodedQuery + "&type=" + typeParam + "&limit=" + limit))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                
                if (json.has("tracks")) {
                    var items = json.getAsJsonObject("tracks").getAsJsonArray("items");
                    int maxTracks = (filter == SearchFilter.ALL) ? 3 : items.size();
                    
                    StringBuilder ids = new StringBuilder();
                    int trackCount = Math.min(items.size(), maxTracks);
                    int validCount = 0;
                    for (int i = 0; i < trackCount; i++) {
                        if (items.get(i) == null || !items.get(i).isJsonObject()) continue;
                        JsonObject track = items.get(i).getAsJsonObject();
                        if (validCount > 0) ids.append(",");
                        ids.append(track.get("id").getAsString());
                        validCount++;
                    }
                    
                    boolean[] likedStates = new boolean[trackCount];
                    if (ids.length() > 0) {
                        HttpRequest likedRequest = HttpRequest.newBuilder()
                                .uri(URI.create("https://api.spotify.com/v1/me/tracks/contains?ids=" + ids.toString()))
                                .header("Authorization", "Bearer " + accessToken)
                                .GET()
                                .build();
                        HttpResponse<String> likedResp = client.send(likedRequest, HttpResponse.BodyHandlers.ofString());
                        if (likedResp.statusCode() == 200) {
                            var likedArr = JsonParser.parseString(likedResp.body()).getAsJsonArray();
                            int likedIdx = 0;
                            for (int i = 0; i < trackCount; i++) {
                                if (items.get(i) == null || !items.get(i).isJsonObject()) continue;
                                if (likedIdx < likedArr.size()) {
                                    likedStates[i] = likedArr.get(likedIdx).getAsBoolean();
                                    likedIdx++;
                                }
                            }
                        }
                    }

                    for (int i = 0; i < trackCount; i++) {
                        if (items.get(i) == null || !items.get(i).isJsonObject()) continue;
                        JsonObject track = items.get(i).getAsJsonObject();
                        String id = track.get("id").getAsString();
                        String title = track.get("name").getAsString();
                        
                        StringBuilder artists = new StringBuilder();
                        if (track.has("artists")) {
                            var arr = track.getAsJsonArray("artists");
                            for (int j = 0; j < arr.size(); j++) {
                                if (j > 0) artists.append(", ");
                                artists.append(arr.get(j).getAsJsonObject().get("name").getAsString());
                            }
                        }
                        String artist = artists.toString();
                        
                        String artUrl = "";
                        if (track.has("album")) {
                            JsonObject album = track.getAsJsonObject("album");
                            if (album.has("images")) {
                                var images = album.getAsJsonArray("images");
                                if (images.size() > 0) {
                                    artUrl = images.get(0).getAsJsonObject().get("url").getAsString();
                                }
                            }
                        }

                        String localArtPath = "";
                        if (!artUrl.isEmpty()) {
                            localArtPath = getAndDownloadSearchArt(id, artUrl);
                        }

                        results.add(new SearchResultTrack(id, title, artist, artUrl, localArtPath, likedStates[i], false));
                    }
                }
                
                if (json.has("playlists")) {
                    var items = json.getAsJsonObject("playlists").getAsJsonArray("items");
                    int maxPlaylists = (filter == SearchFilter.ALL) ? (5 - results.size()) : items.size();
                    int playlistCount = Math.min(items.size(), maxPlaylists);
                    for (int i = 0; i < playlistCount; i++) {
                        if (items.get(i) == null || !items.get(i).isJsonObject()) continue;
                        JsonObject playlist = items.get(i).getAsJsonObject();
                        String id = playlist.get("id").getAsString();
                        String name = playlist.get("name").getAsString();
                        String owner = playlist.getAsJsonObject("owner").get("display_name").getAsString();
                        String artUrl = "";
                        if (playlist.has("images")) {
                            var images = playlist.getAsJsonArray("images");
                            if (images.size() > 0) {
                                artUrl = images.get(0).getAsJsonObject().get("url").getAsString();
                            }
                        }
                        String localArtPath = "";
                        if (!artUrl.isEmpty()) {
                            localArtPath = getAndDownloadSearchArt(id, artUrl);
                        }
                        results.add(new SearchResultTrack(id, name, "Playlist by " + owner, artUrl, localArtPath, false, true));
                    }
                }
            } else if (response.statusCode() == 401) {
                refreshToken();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return results;
    }

    private final Map<String, String> searchArtCache = new HashMap<>();
    private String getAndDownloadSearchArt(String trackId, String url) {
        synchronized (searchArtCache) {
            if (searchArtCache.containsKey(trackId)) {
                File cachedFile = new File(searchArtCache.get(trackId));
                if (cachedFile.exists() && cachedFile.length() > 0) {
                    cachedFile.setLastModified(System.currentTimeMillis());
                    return cachedFile.getAbsolutePath();
                } else if (cachedFile.exists()) {
                    cachedFile.delete();
                }
            }
        }
        File cacheDir = new File(Minecraft.getInstance().gameDirectory, "config/spotify-search-cache");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        File artFile = new File(cacheDir, trackId + ".png");
        if (artFile.exists() && artFile.length() > 0) {
            artFile.setLastModified(System.currentTimeMillis());
            synchronized (searchArtCache) {
                searchArtCache.put(trackId, artFile.getAbsolutePath());
            }
            return artFile.getAbsolutePath();
        } else if (artFile.exists()) {
            artFile.delete();
        }
        try {
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200) {
                byte[] bytes = response.body();
                if (bytes != null && bytes.length > 0) {
                    java.nio.file.Files.write(artFile.toPath(), bytes);
                    artFile.setLastModified(System.currentTimeMillis());
                    synchronized (searchArtCache) {
                        searchArtCache.put(trackId, artFile.getAbsolutePath());
                    }
                    return artFile.getAbsolutePath();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    public synchronized void playTrack(String trackId) {
        if (!authorized || accessToken.isBlank()) return;
        if (System.currentTimeMillis() + 30000L > tokenExpiresAt) {
            refreshToken();
        }
        try {
            HttpClient client = HttpClient.newHttpClient();
            String jsonBody = "{\"uris\":[\"spotify:track:" + trackId + "\"]}";
            HttpRequest request = HttpRequest.newBuilder()
                     .uri(URI.create("https://api.spotify.com/v1/me/player/play"))
                     .header("Authorization", "Bearer " + accessToken)
                     .header("Content-Type", "application/json")
                     .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                     .build();
            HttpResponse<String> response = sendRequest(client, request, "Failed to play track.");
            System.out.println("[SpotifyManager] playTrack HTTP " + response.statusCode() + ": " + response.body());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void playPlaylist(String playlistId) {
        if (!authorized || accessToken.isBlank()) return;
        if (System.currentTimeMillis() + 30000L > tokenExpiresAt) {
            refreshToken();
        }
        try {
            HttpClient client = HttpClient.newHttpClient();
            String jsonBody = "{\"context_uri\":\"spotify:playlist:" + playlistId + "\"}";
            HttpRequest request = HttpRequest.newBuilder()
                     .uri(URI.create("https://api.spotify.com/v1/me/player/play"))
                     .header("Authorization", "Bearer " + accessToken)
                     .header("Content-Type", "application/json")
                     .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                     .build();
            HttpResponse<String> response = sendRequest(client, request, "Failed to play playlist.");
            System.out.println("[SpotifyManager] playPlaylist HTTP " + response.statusCode() + ": " + response.body());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void likeTrack(String trackId, boolean like) {
        if (!authorized || accessToken.isBlank()) return;
        if (System.currentTimeMillis() + 30000L > tokenExpiresAt) {
            refreshToken();
        }
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                     .uri(URI.create("https://api.spotify.com/v1/me/tracks?ids=" + trackId))
                     .header("Authorization", "Bearer " + accessToken);
            
            if (like) {
                builder.PUT(HttpRequest.BodyPublishers.noBody());
            } else {
                builder.DELETE();
            }
            HttpResponse<String> response = sendRequest(client, builder.build(), like ? "Failed to save track." : "Failed to remove track.");
            System.out.println("[SpotifyManager] likeTrack (" + (like ? "LIKE" : "UNLIKE") + ") HTTP " + response.statusCode() + ": " + response.body());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private long lastVolumeApiCall = 0L;
    private int pendingVolumeTarget = -1;

    public synchronized void setVolume(int percent) {
        if (MusicDisplayOverlay.getMusicSource() == MusicDisplayOverlay.MusicSource.WINDOWS) {
            WindowsMediaManager.getInstance().setVolume(percent);
            return;
        }
        if (!authorized || accessToken.isBlank()) return;
        
        // Immediately update local status state to ensure rendering is fluid/lag-free
        if (currentStatus != MediaStatus.EMPTY) {
            currentStatus = new MediaStatus(
                    currentStatus.title(), currentStatus.artist(),
                    currentStatus.positionSeconds(), currentStatus.durationSeconds(),
                    currentStatus.artworkPath(), currentStatus.trackId(),
                    currentStatus.isPlaying(), currentStatus.shuffleState(),
                    percent, currentStatus.liked(),
                    currentStatus.repeatState()
            );
        }

        pendingVolumeTarget = percent;
        long now = System.currentTimeMillis();
        if (now - lastVolumeApiCall > 150L) {
            lastVolumeApiCall = now;
            int volToSend = percent;
            CompletableFuture.runAsync(() -> {
                if (System.currentTimeMillis() + 30000L > tokenExpiresAt) {
                    refreshToken();
                }
                try {
                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("https://api.spotify.com/v1/me/player/volume?volume_percent=" + volToSend))
                            .header("Authorization", "Bearer " + accessToken)
                            .PUT(HttpRequest.BodyPublishers.noBody())
                            .build();
                    sendRequest(client, request, "Failed to set volume.");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }

    public synchronized void togglePlayPause() {
        if (MusicDisplayOverlay.getMusicSource() == MusicDisplayOverlay.MusicSource.WINDOWS) {
            WindowsMediaManager.getInstance().togglePlayPause();
            return;
        }
        if (!authorized || accessToken.isBlank()) return;
        if (System.currentTimeMillis() + 30000L > tokenExpiresAt) {
            refreshToken();
        }
        try {
            boolean play = currentStatus == MediaStatus.EMPTY || !currentStatus.isPlaying();
            String endpoint = play ? "play" : "pause";
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.spotify.com/v1/me/player/" + endpoint))
                    .header("Authorization", "Bearer " + accessToken)
                    .PUT(HttpRequest.BodyPublishers.noBody())
                    .build();
            sendRequest(client, request, play ? "Failed to resume playback." : "Failed to pause playback.");
            if (currentStatus != MediaStatus.EMPTY) {
                currentStatus = new MediaStatus(
                        currentStatus.title(), currentStatus.artist(),
                        currentStatus.positionSeconds(), currentStatus.durationSeconds(),
                        currentStatus.artworkPath(), currentStatus.trackId(),
                        play, currentStatus.shuffleState(),
                        currentStatus.volumePercent(), currentStatus.liked(),
                        currentStatus.repeatState()
                );
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void next() {
        if (MusicDisplayOverlay.getMusicSource() == MusicDisplayOverlay.MusicSource.WINDOWS) {
            WindowsMediaManager.getInstance().next();
            return;
        }
        if (!authorized || accessToken.isBlank()) return;
        if (System.currentTimeMillis() + 30000L > tokenExpiresAt) {
            refreshToken();
        }
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.spotify.com/v1/me/player/next"))
                    .header("Authorization", "Bearer " + accessToken)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            sendRequest(client, request, "Failed to skip to next track.");
            poll();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void previous() {
        if (MusicDisplayOverlay.getMusicSource() == MusicDisplayOverlay.MusicSource.WINDOWS) {
            WindowsMediaManager.getInstance().previous();
            return;
        }
        if (!authorized || accessToken.isBlank()) return;
        if (System.currentTimeMillis() + 30000L > tokenExpiresAt) {
            refreshToken();
        }
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.spotify.com/v1/me/player/previous"))
                    .header("Authorization", "Bearer " + accessToken)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            sendRequest(client, request, "Failed to skip to previous track.");
            poll();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void toggleShuffle(boolean state) {
        if (MusicDisplayOverlay.getMusicSource() == MusicDisplayOverlay.MusicSource.WINDOWS) {
            return; // Not supported by Windows SMTC
        }
        if (!authorized || accessToken.isBlank()) return;
        if (System.currentTimeMillis() + 30000L > tokenExpiresAt) {
            refreshToken();
        }
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.spotify.com/v1/me/player/shuffle?state=" + state))
                    .header("Authorization", "Bearer " + accessToken)
                    .PUT(HttpRequest.BodyPublishers.noBody())
                    .build();
            sendRequest(client, request, "Failed to toggle shuffle.");
            if (currentStatus != MediaStatus.EMPTY) {
                currentStatus = new MediaStatus(
                        currentStatus.title(), currentStatus.artist(),
                        currentStatus.positionSeconds(), currentStatus.durationSeconds(),
                        currentStatus.artworkPath(), currentStatus.trackId(),
                        currentStatus.isPlaying(), state,
                        currentStatus.volumePercent(), currentStatus.liked(),
                        currentStatus.repeatState()
                );
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void toggleLike() {
        if (MusicDisplayOverlay.getMusicSource() == MusicDisplayOverlay.MusicSource.WINDOWS) {
            return; // Not supported by Windows SMTC
        }
        if (currentStatus == MediaStatus.EMPTY) return;
        String trackId = currentStatus.trackId();
        if (trackId == null || trackId.isEmpty()) return;
        boolean newLiked = !currentStatus.liked();
        likeTrack(trackId, newLiked);
        currentStatus = new MediaStatus(
                currentStatus.title(), currentStatus.artist(),
                currentStatus.positionSeconds(), currentStatus.durationSeconds(),
                currentStatus.artworkPath(), currentStatus.trackId(),
                currentStatus.isPlaying(), currentStatus.shuffleState(),
                currentStatus.volumePercent(), newLiked,
                currentStatus.repeatState()
        );
        lastPolledTrackLiked = newLiked;
    }

    public synchronized void toggleRepeat() {
        if (MusicDisplayOverlay.getMusicSource() == MusicDisplayOverlay.MusicSource.WINDOWS) {
            return; // Not supported by Windows SMTC
        }
        if (!authorized || accessToken.isBlank()) return;
        if (System.currentTimeMillis() + 30000L > tokenExpiresAt) {
            refreshToken();
        }
        try {
            String current = currentStatus != MediaStatus.EMPTY ? currentStatus.repeatState() : "off";
            String nextState;
            if ("off".equals(current)) {
                nextState = "context";
            } else if ("context".equals(current)) {
                nextState = "track";
            } else {
                nextState = "off";
            }
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.spotify.com/v1/me/player/repeat?state=" + nextState))
                    .header("Authorization", "Bearer " + accessToken)
                    .PUT(HttpRequest.BodyPublishers.noBody())
                    .build();
            sendRequest(client, request, "Failed to toggle repeat mode.");
            if (currentStatus != MediaStatus.EMPTY) {
                currentStatus = new MediaStatus(
                        currentStatus.title(), currentStatus.artist(),
                        currentStatus.positionSeconds(), currentStatus.durationSeconds(),
                        currentStatus.artworkPath(), currentStatus.trackId(),
                        currentStatus.isPlaying(), currentStatus.shuffleState(),
                        currentStatus.volumePercent(), currentStatus.liked(),
                        nextState
                );
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private HttpResponse<String> sendRequest(HttpClient client, HttpRequest request, String defaultErrorMessage) throws Exception {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response != null && response.statusCode() >= 400 && response.statusCode() != 401) {
            int code = response.statusCode();
            String msg = defaultErrorMessage;
            try {
                String body = response.body();
                if (body != null && !body.isBlank()) {
                    JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                    if (json.has("error")) {
                        JsonObject err = json.getAsJsonObject("error");
                        if (err.has("message")) {
                            msg = err.get("message").getAsString();
                        }
                    }
                }
            } catch (Exception ignored) {}
            HttpNotificationManager.show(code, msg);
        }
        return response;
    }

    public record MediaStatus(String title, String artist, double positionSeconds, double durationSeconds, String artworkPath, String trackId, boolean isPlaying, boolean shuffleState, int volumePercent, boolean liked, String repeatState) {
        public static final MediaStatus EMPTY = new MediaStatus("", "", 0.0, 0.0, "", "", false, false, 100, false, "off");

        public boolean hasMedia() {
            return !title.isBlank() || !artist.isBlank();
        }

        public float progress() {
            if (durationSeconds <= 0.0) {
                return 0.0f;
            }
            return (float) Math.max(0.0, Math.min(1.0, positionSeconds / durationSeconds));
        }
    }

    public record NextTrack(String title, String artist, String artworkPath) {
        public static final NextTrack EMPTY = new NextTrack("", "", "");
        public boolean hasMedia() {
            return !title.isBlank() || !artist.isBlank();
        }
    }
}
