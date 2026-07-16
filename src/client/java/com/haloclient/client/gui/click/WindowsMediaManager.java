package com.haloclient.client.gui.click;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manager for Windows System Media Transport Controls (SMTC).
 * Launches a sidecar .exe (SMTCBridge.exe) compiled from C#,
 * reads JSON status lines from its stdout, and sends commands via stdin.
 * Provides album art, volume, play/pause, next, previous controls
 * for any media player running on Windows.
 */
public final class WindowsMediaManager {

    private static final WindowsMediaManager INSTANCE = new WindowsMediaManager();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Halo Windows Media Reader");
        thread.setDaemon(true);
        return thread;
    });

    private volatile SpotifyManager.MediaStatus currentStatus = SpotifyManager.MediaStatus.EMPTY;
    private volatile boolean running = false;
    private volatile boolean bridgeReady = false;
    private Process bridgeProcess;
    private OutputStream bridgeStdin;
    private Path bridgeExePath;

    private WindowsMediaManager() {
    }

    public static WindowsMediaManager getInstance() {
        return INSTANCE;
    }

    public static SpotifyManager.MediaStatus getStatus() {
        return INSTANCE.currentStatus;
    }

    public static boolean isConfigured() {
        return INSTANCE.bridgeReady;
    }

    /**
     * Extract the sidecar exe from the mod resources to the config directory,
     * then launch the process and begin reading status lines.
     */
    public void start() {
        if (running) return;
        running = true;

        EXECUTOR.execute(() -> {
            try {
                extractBridge();
                launchBridge();
            } catch (Exception e) {
                System.err.println("[Halo/WindowsMedia] Failed to start bridge: " + e.getMessage());
                e.printStackTrace();
                running = false;
            }
        });
    }

    public void stop() {
        running = false;
        bridgeReady = false;
        if (bridgeProcess != null) {
            try {
                bridgeProcess.destroyForcibly();
            } catch (Exception ignored) {
            }
            bridgeProcess = null;
        }
    }

    private void extractBridge() throws IOException {
        File configDir = new File(Minecraft.getInstance().gameDirectory, "config");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        bridgeExePath = new File(configDir, "SMTCBridge.exe").toPath();

        // Always extract fresh on startup to ensure latest version
        try (InputStream is = WindowsMediaManager.class.getClassLoader()
                .getResourceAsStream("assets/halo/SMTCBridge.exe")) {
            if (is == null) {
                throw new IOException("SMTCBridge.exe not found in mod resources");
            }
            Files.copy(is, bridgeExePath, StandardCopyOption.REPLACE_EXISTING);
        }
        System.out.println("[Halo/WindowsMedia] Extracted SMTCBridge.exe to " + bridgeExePath);
    }

    private void launchBridge() {
        try {
            String artworkPath = new File(Minecraft.getInstance().gameDirectory, "config/windows-media-art.png").getAbsolutePath();
            ProcessBuilder pb = new ProcessBuilder(bridgeExePath.toString(), artworkPath);
            pb.redirectErrorStream(false);
            bridgeProcess = pb.start();
            bridgeStdin = bridgeProcess.getOutputStream();
            bridgeReady = true;

            System.out.println("[Halo/WindowsMedia] Bridge process started (PID " + bridgeProcess.pid() + ")");

            // Read stderr in a separate thread for diagnostics
            Thread stderrThread = new Thread(() -> {
                try (BufferedReader err = new BufferedReader(
                        new InputStreamReader(bridgeProcess.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = err.readLine()) != null) {
                        System.err.println("[SMTCBridge/err] " + line);
                    }
                } catch (Exception ignored) {
                }
            }, "Halo Windows Media Stderr");
            stderrThread.setDaemon(true);
            stderrThread.start();

            // Read stdout JSON lines
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(bridgeProcess.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while (running && (line = reader.readLine()) != null) {
                    try {
                        parseStatusLine(line);
                    } catch (Exception e) {
                        System.err.println("[Halo/WindowsMedia] Failed to parse status line: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Halo/WindowsMedia] Bridge process error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            bridgeReady = false;
            if (running) {
                // Restart after short delay
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                }
                if (running) {
                    System.out.println("[Halo/WindowsMedia] Restarting bridge...");
                    launchBridge();
                }
            }
        }
    }

    private void parseStatusLine(String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        String title = obj.has("Title") && !obj.get("Title").isJsonNull() ? obj.get("Title").getAsString() : "";
        String artist = obj.has("Artist") && !obj.get("Artist").isJsonNull() ? obj.get("Artist").getAsString() : "";
        boolean isPlaying = obj.has("IsPlaying") && obj.get("IsPlaying").getAsBoolean();
        double positionSeconds = obj.has("PositionSeconds") ? obj.get("PositionSeconds").getAsDouble() : 0.0;
        double durationSeconds = obj.has("DurationSeconds") ? obj.get("DurationSeconds").getAsDouble() : 0.0;
        int volumePercent = obj.has("VolumePercent") ? obj.get("VolumePercent").getAsInt() : 100;
        String artworkPath = obj.has("ArtworkPath") && !obj.get("ArtworkPath").isJsonNull() ? obj.get("ArtworkPath").getAsString() : "";


        currentStatus = new SpotifyManager.MediaStatus(
                title, artist, positionSeconds, durationSeconds,
                artworkPath, title + "|" + artist, // trackId
                isPlaying, false, volumePercent, false, "off"
        );
    }

    // --- Control methods (send commands to bridge stdin) ---

    private void sendCommand(String command) {
        if (!bridgeReady || bridgeStdin == null) return;
        try {
            bridgeStdin.write((command + "\n").getBytes(StandardCharsets.UTF_8));
            bridgeStdin.flush();
        } catch (Exception e) {
            System.err.println("[Halo/WindowsMedia] Failed to send command '" + command + "': " + e.getMessage());
        }
    }

    public void togglePlayPause() {
        sendCommand("playpause");
        // Optimistically toggle local state for responsive UI
        if (currentStatus != SpotifyManager.MediaStatus.EMPTY) {
            currentStatus = new SpotifyManager.MediaStatus(
                    currentStatus.title(), currentStatus.artist(),
                    currentStatus.positionSeconds(), currentStatus.durationSeconds(),
                    currentStatus.artworkPath(), currentStatus.trackId(),
                    !currentStatus.isPlaying(), currentStatus.shuffleState(),
                    currentStatus.volumePercent(), currentStatus.liked(),
                    currentStatus.repeatState()
            );
        }
    }

    public void next() {
        sendCommand("next");
    }

    public void previous() {
        sendCommand("previous");
    }

    private long lastVolumeCmd = 0L;

    public void setVolume(int percent) {
        // Immediately update local status for fluid rendering
        if (currentStatus != SpotifyManager.MediaStatus.EMPTY) {
            currentStatus = new SpotifyManager.MediaStatus(
                    currentStatus.title(), currentStatus.artist(),
                    currentStatus.positionSeconds(), currentStatus.durationSeconds(),
                    currentStatus.artworkPath(), currentStatus.trackId(),
                    currentStatus.isPlaying(), currentStatus.shuffleState(),
                    percent, currentStatus.liked(),
                    currentStatus.repeatState()
            );
        }
        // Throttle: max once per 100ms
        long now = System.currentTimeMillis();
        if (now - lastVolumeCmd > 100L) {
            lastVolumeCmd = now;
            sendCommand("volume " + Math.max(0, Math.min(100, percent)));
        }
    }

    // Unsupported on Windows SMTC – no-ops
    public void toggleLike() { }
    public void toggleShuffle(boolean state) { }
    public void toggleRepeat() { }
}
