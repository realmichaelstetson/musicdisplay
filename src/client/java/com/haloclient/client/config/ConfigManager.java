package com.haloclient.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.haloclient.client.gui.click.ClickGUI;
import com.haloclient.client.gui.click.DetachedNextElement;
import com.haloclient.client.gui.click.MusicDisplayOverlay;
import com.haloclient.client.module.Module;
import com.haloclient.client.module.ModuleManager;
import com.haloclient.client.module.impl.render.ClickGUIModule;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int AUTOSAVE_INTERVAL_TICKS = 40;

    private static String lastSaved = "";
    private static int tickCounter = 0;

    private ConfigManager() {}

    private static File getConfigFile() {
        return new File(Minecraft.getInstance().gameDirectory, "config/musicdisplay.json");
    }

    public static void load(ModuleManager moduleManager) {
        File file = getConfigFile();
        if (!file.exists()) return;
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            readSection(root, "overlay", MusicDisplayOverlay::readConfig);
            readSection(root, "nextSong", DetachedNextElement::readConfig);
            readSection(root, "clickGui", ClickGUI::readConfig);
            readSection(root, "modules", modules -> readModules(modules, moduleManager));
        } catch (Exception e) {
            System.err.println("[MusicDisplay] Failed to load config: " + e);
        }
        lastSaved = serialize(moduleManager);
    }

    public static void tick(ModuleManager moduleManager) {
        if (++tickCounter < AUTOSAVE_INTERVAL_TICKS) return;
        tickCounter = 0;
        save(moduleManager);
    }

    public static void save(ModuleManager moduleManager) {
        String json = serialize(moduleManager);
        if (json.equals(lastSaved)) return;

        File file = getConfigFile();
        try {
            File dir = file.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            //Write to a temp file first so a crash mid-write can't corrupt the config
            File temp = new File(file.getPath() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp.toPath(), StandardCharsets.UTF_8)) {
                writer.write(json);
            }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            lastSaved = json;
        } catch (Exception e) {
            System.err.println("[MusicDisplay] Failed to save config: " + e);
        }
    }

    private static String serialize(ModuleManager moduleManager) {
        JsonObject root = new JsonObject();

        JsonObject overlay = new JsonObject();
        MusicDisplayOverlay.writeConfig(overlay);
        root.add("overlay", overlay);

        JsonObject nextSong = new JsonObject();
        DetachedNextElement.writeConfig(nextSong);
        root.add("nextSong", nextSong);

        JsonObject clickGui = new JsonObject();
        ClickGUI.writeConfig(clickGui);
        root.add("clickGui", clickGui);

        JsonObject modules = new JsonObject();
        for (Module module : moduleManager.getModules()) {
            JsonObject m = new JsonObject();
            //ClickGUIModule only opens the screen, its "enabled" state is transient
            if (!(module instanceof ClickGUIModule)) m.addProperty("enabled", module.isEnabled());
            m.addProperty("key", module.getKey());
            modules.add(module.getName(), m);
        }
        root.add("modules", modules);

        return GSON.toJson(root);
    }

    private static void readModules(JsonObject modules, ModuleManager moduleManager) {
        for (Module module : moduleManager.getModules()) {
            JsonObject m = getObject(modules, module.getName());
            if (m == null) continue;
            if (m.has("key")) module.setKey(m.get("key").getAsInt());
            if (m.has("enabled") && !(module instanceof ClickGUIModule)) {
                boolean enabled = m.get("enabled").getAsBoolean();
                if (enabled != module.isEnabled()) module.setEnabled(enabled);
            }
        }
    }

    private static void readSection(JsonObject root, String name, Consumer<JsonObject> reader) {
        JsonObject section = getObject(root, name);
        if (section == null) return;
        try {
            reader.accept(section);
        } catch (Exception e) {
            System.err.println("[MusicDisplay] Failed to read config section '" + name + "': " + e);
        }
    }

    private static JsonObject getObject(JsonObject parent, String name) {
        JsonElement element = parent.get(name);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    public static void putFloat(JsonObject json, String key, float value) {
        if (!Float.isNaN(value)) json.addProperty(key, value);
    }

    public static float getFloat(JsonObject json, String key, float fallback) {
        JsonElement e = json.get(key);
        if (e == null || !e.isJsonPrimitive()) return fallback;
        float v = e.getAsFloat();
        return Float.isFinite(v) ? v : fallback;
    }

    public static int getInt(JsonObject json, String key, int fallback) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsInt() : fallback;
    }

    public static boolean getBoolean(JsonObject json, String key, boolean fallback) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsBoolean() : fallback;
    }

    public static <E extends Enum<E>> E getEnum(JsonObject json, String key, Class<E> type, E fallback) {
        JsonElement e = json.get(key);
        if (e == null || !e.isJsonPrimitive()) return fallback;
        try {
            return Enum.valueOf(type, e.getAsString());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
