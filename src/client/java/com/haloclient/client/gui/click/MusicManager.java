package com.haloclient.client.gui.click;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * Central music-source facade. The overlay and ClickGUI talk to this instead of a
 * concrete provider, so the active source (Spotify or Subsonic) can be switched at
 * runtime while reusing the same {@link SpotifyManager.MediaStatus} record type.
 *
 * Reads (status / next track / configured) are routed to the active source.
 * Playback controls only apply to Spotify; Subsonic is a display-only source, so its
 * control calls are no-ops.
 */
public final class MusicManager {

    public enum Source {
        SPOTIFY("Spotify"),
        SUBSONIC("Subsonic");

        private final String displayName;

        Source(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile Source activeSource = Source.SPOTIFY;

    private MusicManager() {
    }

    // ------------------------------------------------------------------
    // Source selection
    // ------------------------------------------------------------------

    public static Source getActiveSource() {
        return activeSource;
    }

    public static void setActiveSource(Source source) {
        if (source == null || source == activeSource) {
            return;
        }
        activeSource = source;
        save();
    }

    public static void toggleSource() {
        setActiveSource(activeSource == Source.SPOTIFY ? Source.SUBSONIC : Source.SPOTIFY);
    }

    public static String sourceDisplayName() {
        return activeSource.getDisplayName();
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    public static void load() {
        loadSourcePreference();
        SpotifyManager.getInstance().load();
        SubsonicManager.getInstance().load();
        MusicDisplayOverlay.loadSettings();
    }

    public static void startPolling() {
        SpotifyManager.getInstance().startPolling();
        SubsonicManager.getInstance().startPolling();
    }

    public static void cleanup() {
        try {
            SubsonicManager.getInstance().internalStop();
        } catch (Throwable ignored) {
        }
        try {
            SpotifyManager.getInstance().cleanOldArtworkCache();
        } catch (Throwable ignored) {
        }
        try {
            SubsonicManager.getInstance().cleanArtworkCache();
        } catch (Throwable ignored) {
        }
    }

    private static File getConfigFile() {
        return new File(Minecraft.getInstance().gameDirectory, "config/music-source.json");
    }

    private static void loadSourcePreference() {
        File file = getConfigFile();
        if (!file.exists()) {
            return;
        }
        try (Reader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (json.has("source")) {
                try {
                    activeSource = Source.valueOf(json.get("source").getAsString());
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void save() {
        File file = getConfigFile();
        File dir = file.getParentFile();
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }
        JsonObject json = new JsonObject();
        json.addProperty("source", activeSource.name());
        try (Writer writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(json, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ------------------------------------------------------------------
    // Reads (routed to active source)
    // ------------------------------------------------------------------

    public static SpotifyManager.MediaStatus getStatus() {
        if (activeSource == Source.SUBSONIC) {
            return SubsonicManager.getInstance().getStatus();
        }
        return SpotifyManager.getStatus();
    }

    public static SpotifyManager.NextTrack getNextTrack() {
        if (activeSource == Source.SUBSONIC) {
            // Subsonic's "now playing" has no reliable up-next queue.
            return SpotifyManager.NextTrack.EMPTY;
        }
        return SpotifyManager.getNextTrack();
    }

    public static boolean isConfigured() {
        if (activeSource == Source.SUBSONIC) {
            return SubsonicManager.getInstance().isConfigured();
        }
        return SpotifyManager.isConfigured();
    }

    /**
     * Whether the active source supports interactive playback controls.
     * Spotify always does; Subsonic only when a local MPRIS player is driving playback.
     */
    public static boolean supportsControls() {
        if (activeSource == Source.SUBSONIC) {
            SubsonicManager sm = SubsonicManager.getInstance();
            return sm.isInternalActive() || sm.isMprisControllable();
        }
        return activeSource == Source.SPOTIFY;
    }

    // ------------------------------------------------------------------
    // Controls (Spotify; Subsonic via the internal player or a local MPRIS player)
    // ------------------------------------------------------------------

    public static void togglePlayPause() {
        if (activeSource == Source.SUBSONIC) {
            SubsonicManager sm = SubsonicManager.getInstance();
            if (sm.isInternalActive()) {
                sm.internalTogglePause();
            } else if (sm.isMprisControllable()) {
                sm.mprisTogglePlayPause();
            }
        } else if (activeSource == Source.SPOTIFY) {
            SpotifyManager.getInstance().togglePlayPause();
        }
    }

    public static void next() {
        if (activeSource == Source.SUBSONIC) {
            SubsonicManager sm = SubsonicManager.getInstance();
            if (!sm.isInternalActive() && sm.isMprisControllable()) {
                sm.mprisNext();
            }
        } else if (activeSource == Source.SPOTIFY) {
            SpotifyManager.getInstance().next();
        }
    }

    public static void previous() {
        if (activeSource == Source.SUBSONIC) {
            SubsonicManager sm = SubsonicManager.getInstance();
            if (!sm.isInternalActive() && sm.isMprisControllable()) {
                sm.mprisPrevious();
            }
        } else if (activeSource == Source.SPOTIFY) {
            SpotifyManager.getInstance().previous();
        }
    }

    public static void setVolume(int percent) {
        if (activeSource == Source.SUBSONIC) {
            SubsonicManager sm = SubsonicManager.getInstance();
            if (sm.isInternalActive()) {
                sm.setInternalVolume(percent);
            } else if (sm.isMprisControllable()) {
                sm.mprisSetVolume(percent);
            }
        } else if (activeSource == Source.SPOTIFY) {
            SpotifyManager.getInstance().setVolume(percent);
        }
    }

    // Shuffle / like / repeat are Spotify-only (not exposed via this integration path).
    public static void toggleShuffle(boolean state) {
        if (activeSource == Source.SPOTIFY) SpotifyManager.getInstance().toggleShuffle(state);
    }

    public static void toggleLike() {
        if (activeSource == Source.SPOTIFY) SpotifyManager.getInstance().toggleLike();
    }

    public static void toggleRepeat() {
        if (activeSource == Source.SPOTIFY) SpotifyManager.getInstance().toggleRepeat();
    }
}
