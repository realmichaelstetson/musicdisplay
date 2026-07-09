package com.haloclient.client.gui.click;

import net.minecraft.client.Minecraft;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads (and controls) the currently playing track from a local MPRIS media player
 * on Linux (e.g. Feishin, the Subsonic client) by shelling out to {@code gdbus}.
 *
 * Unlike the raw Subsonic {@code getNowPlaying} API, MPRIS exposes a live playback
 * position, the play/pause state and playback controls, which is what makes the
 * overlay progress accurate and the buttons functional.
 *
 * Requires (inside a Flatpak sandbox) the session-bus permission:
 *   flatpak override --user --talk-name='org.mpris.MediaPlayer2.*' org.prismlauncher.PrismLauncher
 */
final class LinuxMediaProvider {

    private static final String MPRIS_PREFIX = "org.mpris.MediaPlayer2.";
    private static final String OBJ = "/org/mpris/MediaPlayer2";
    private static final String IFACE = "org.mpris.MediaPlayer2.Player";

    private static final Pattern INT64 = Pattern.compile("int64\\s+(\\d+)");
    private static final Pattern STATUS = Pattern.compile("<'([A-Za-z]+)'>");
    private static final Pattern LENGTH = Pattern.compile("'mpris:length':\\s*<int64\\s+(\\d+)>");
    private static final Pattern ART_URL = Pattern.compile("'mpris:artUrl':\\s*<'([^']*)'>");
    private static final Pattern DOUBLE = Pattern.compile("<(\\d+(?:\\.\\d+)?)>");
    private static final Pattern MPRIS_NAME = Pattern.compile("org\\.mpris\\.MediaPlayer2\\.[A-Za-z0-9._-]+");

    private static Boolean available;
    private volatile String lastPlayer;
    private volatile long positionSampleMs;

    /** Wall-clock time (ms) at which the last Position value was sampled — used as extrapolation base. */
    long positionSampleMs() {
        return positionSampleMs;
    }

    boolean isAvailable() {
        if (available == null) {
            available = System.getProperty("os.name", "").toLowerCase().contains("linux") && commandExists();
            System.out.println("[Halo/MPRIS] gdbus available: " + available);
        }
        return available;
    }

    String lastPlayer() {
        return lastPlayer;
    }

    private static boolean commandExists() {
        try {
            Process p = new ProcessBuilder("gdbus", "--version").redirectErrorStream(true).start();
            p.waitFor(3, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Lists real MPRIS players (excluding the playerctld proxy). */
    private List<String> listPlayers() {
        List<String> players = new ArrayList<>();
        String names = run("gdbus", "call", "--session", "--dest", "org.freedesktop.DBus",
                "--object-path", "/org/freedesktop/DBus",
                "--method", "org.freedesktop.DBus.ListNames");
        if (names.isBlank()) return players;
        Matcher m = MPRIS_NAME.matcher(names);
        while (m.find()) {
            String name = m.group();
            if (name.endsWith(".playerctld")) continue;
            if (!players.contains(name)) players.add(name);
        }
        return players;
    }

    /** Picks the most relevant player: a real MPRIS app (not playerctld), preferring one that is Playing. */
    private String pickPlayer() {
        String firstPaused = null;
        for (String name : listPlayers()) {
            String status = playbackStatus(name);
            if ("Playing".equals(status)) return name;
            if (firstPaused == null && "Paused".equals(status)) firstPaused = name;
        }
        List<String> players = listPlayers();
        return firstPaused != null ? firstPaused : (players.isEmpty() ? null : players.get(0));
    }

    /** Returns a player currently in the Playing state, or null. */
    String playingPlayer() {
        for (String name : listPlayers()) {
            if ("Playing".equals(playbackStatus(name))) return name;
        }
        return null;
    }

    void pause(String player) {
        if (player != null) callMethod(player, "Pause");
    }

    /** Pauses whichever player is currently playing (used to avoid double audio). */
    void pauseActive() {
        pause(playingPlayer());
    }

    private String playbackStatus(String player) {
        String out = getProp(player, "PlaybackStatus");
        Matcher m = STATUS.matcher(out);
        return m.find() ? m.group(1) : "";
    }

    /** Builds a MediaStatus from the active MPRIS player, or null if none is active. */
    SpotifyManager.MediaStatus poll() {
        if (!isAvailable()) return null;
        String player = pickPlayer();
        if (!java.util.Objects.equals(player, lastPlayer)) {
            System.out.println("[Halo/MPRIS] active player: " + (player == null ? "none" : player));
        }
        lastPlayer = player;
        if (player == null) return null;

        String status = playbackStatus(player);
        if (status.isBlank() || "Stopped".equals(status)) return null;
        boolean playing = "Playing".equals(status);

        String meta = getProp(player, "Metadata");
        if (meta.isBlank()) return null;

        String title = parseVariantString(meta, "xesam:title");
        String artist = parseVariantString(meta, "xesam:artist");
        if (title.isBlank() && artist.isBlank()) return null;

        double duration = 0.0;
        Matcher lm = LENGTH.matcher(meta);
        if (lm.find()) duration = Long.parseLong(lm.group(1)) / 1_000_000.0;

        String posOut = getProp(player, "Position");
        positionSampleMs = System.currentTimeMillis();
        double position = 0.0;
        Matcher pm = INT64.matcher(posOut);
        if (pm.find()) position = Long.parseLong(pm.group(1)) / 1_000_000.0;

        int volume = 100;
        Matcher vm = DOUBLE.matcher(getProp(player, "Volume"));
        if (vm.find()) {
            try {
                volume = (int) Math.round(Double.parseDouble(vm.group(1)) * 100.0);
                volume = Math.max(0, Math.min(100, volume));
            } catch (NumberFormatException ignored) {
            }
        }

        String artPath = "";
        Matcher am = ART_URL.matcher(meta);
        if (am.find()) {
            artPath = downloadArt(am.group(1));
        }

        return new SpotifyManager.MediaStatus(
                title, artist, position, duration, artPath, player,
                playing, false, volume, false, "off");
    }

    // ------------------------------------------------------------------
    // Controls
    // ------------------------------------------------------------------

    void playPause(String player) {
        if (player != null) callMethod(player, "PlayPause");
    }

    void next(String player) {
        if (player != null) callMethod(player, "Next");
    }

    void previous(String player) {
        if (player != null) callMethod(player, "Previous");
    }

    void openUri(String player, String uri) {
        if (player == null || uri == null) return;
        run("gdbus", "call", "--session", "--dest", player, "--object-path", OBJ,
                "--method", IFACE + ".OpenUri", uri);
    }

    void setVolume(String player, int percent) {
        if (player == null) return;
        double vol = Math.max(0.0, Math.min(1.0, percent / 100.0));
        run("gdbus", "call", "--session", "--dest", player, "--object-path", OBJ,
                "--method", "org.freedesktop.DBus.Properties.Set", IFACE, "Volume",
                "<" + vol + ">");
    }

    private void callMethod(String player, String method) {
        run("gdbus", "call", "--session", "--dest", player, "--object-path", OBJ,
                "--method", IFACE + "." + method);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String getProp(String player, String prop) {
        return run("gdbus", "call", "--session", "--dest", player, "--object-path", OBJ,
                "--method", "org.freedesktop.DBus.Properties.Get", IFACE, prop);
    }

    private static String run(String... args) {
        try {
            Process process = new ProcessBuilder(args).redirectErrorStream(true).start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "";
            }
            String s = out.toString();
            // gdbus prints errors starting with "Error:" to stdout (merged); treat as no data.
            if (s.startsWith("Error:")) return "";
            return s;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Extracts a GVariant string value for the given metadata key, handling both
     * single- and double-quoted forms and backslash escapes, and skipping a leading
     * '[' for array-typed values (e.g. xesam:artist).
     */
    private static String parseVariantString(String meta, String key) {
        int k = meta.indexOf("'" + key + "'");
        if (k < 0) return "";
        int lt = meta.indexOf('<', k);
        if (lt < 0) return "";
        int i = lt + 1;
        while (i < meta.length()) {
            char c = meta.charAt(i);
            if (c == '\'' || c == '"') break;
            if (c == '>') return "";
            i++;
        }
        if (i >= meta.length()) return "";
        char quote = meta.charAt(i);
        StringBuilder sb = new StringBuilder();
        for (int j = i + 1; j < meta.length(); j++) {
            char c = meta.charAt(j);
            if (c == '\\' && j + 1 < meta.length()) {
                sb.append(meta.charAt(j + 1));
                j++;
            } else if (c == quote) {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private final java.util.Map<String, String> artCache = new java.util.HashMap<>();

    /** Downloads cover art from an MPRIS artUrl, stripping any resize param that yields WebP. */
    private String downloadArt(String url) {
        if (url == null || url.isBlank() || !url.startsWith("http")) return "";
        String clean = url.replaceAll("[&?]size=\\d+", "");
        String hash = String.valueOf(Math.abs(clean.hashCode()));
        synchronized (artCache) {
            String cached = artCache.get(hash);
            if (cached != null && new File(cached).exists()) return cached;
        }
        File artFile = new File(Minecraft.getInstance().gameDirectory, "config/subsonic-art-" + hash + ".png");
        if (artFile.exists() && artFile.length() > 0) {
            synchronized (artCache) {
                artCache.put(hash, artFile.getAbsolutePath());
            }
            return artFile.getAbsolutePath();
        }
        try {
            // Force HTTP/1.1: Java's default HTTP/2 fails ("header parser received no bytes")
            // against a plain-HTTP Subsonic/Navidrome server.
            HttpClient client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(clean)).build();
            File temp = new File(Minecraft.getInstance().gameDirectory, "config/subsonic-art-temp-" + hash + ".png");
            if (!temp.getParentFile().exists()) temp.getParentFile().mkdirs();
            HttpResponse<byte[]> resp = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() == 200 && resp.body() != null && resp.body().length > 0) {
                Files.write(temp.toPath(), resp.body());
                Files.move(temp.toPath(), artFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                synchronized (artCache) {
                    artCache.put(hash, artFile.getAbsolutePath());
                }
                return artFile.getAbsolutePath();
            }
        } catch (Exception ignored) {
        }
        return "";
    }
}
