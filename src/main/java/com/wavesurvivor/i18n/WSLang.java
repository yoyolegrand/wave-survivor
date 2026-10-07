package com.wavesurvivor.i18n;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.wavesurvivor.WaveSurvivorMod;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LANGUE DU MOD (indépendante de la langue du jeu) : anglais par défaut, français via /ws lang fr.
 *   - textes dans assets/wavesurvivor/wslang/<en|fr>.json (dans le jar) ;
 *   - t("clé", args...) : placeholders {0}, {1}... ; clé absente → anglais → la clé elle-même ;
 *   - choix sauvegardé dans config/wavesurvivor/settings.json et synchronisé aux joueurs (LangSyncPacket).
 */
public final class WSLang {

    public static final String[] SUPPORTED = {"en", "fr"};
    private static volatile String current = "en";
    private static final Map<String, Map<String, String>> TABLES = new ConcurrentHashMap<>();
    private static final Gson GSON = new Gson();

    private WSLang() {}

    public static String get() { return current; }

    public static boolean isSupported(String l) {
        for (String s : SUPPORTED) if (s.equalsIgnoreCase(l)) return true;
        return false;
    }

    public static void set(String l) {
        current = "fr".equalsIgnoreCase(l) ? "fr" : "en";
    }

    private static Map<String, String> table(String l) {
        return TABLES.computeIfAbsent(l, k -> {
            try (InputStream in = WSLang.class.getResourceAsStream("/assets/wavesurvivor/wslang/" + k + ".json")) {
                if (in == null) return new HashMap<>();
                Map<String, String> m = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8),
                        new TypeToken<Map<String, String>>() {}.getType());
                return m != null ? m : new HashMap<>();
            } catch (Exception e) {
                WaveSurvivorMod.LOGGER.error("[Lang] Lecture de {}.json impossible : {}", k, e.getMessage());
                return new HashMap<>();
            }
        });
    }

    /** Texte traduit, placeholders {0}, {1}... remplacés.
     *  En français, un texte absent du dictionnaire s'affiche tel quel (les configs sont écrites en français) ;
     *  dans une autre langue, il bascule sur l'anglais, puis sur le texte lui-même. */
    public static String t(String key, Object... args) {
        String s = table(current).get(key);
        if (s == null && !"fr".equals(current)) s = table("en").get(key);
        if (s == null) s = key;
        for (int i = 0; i < args.length; i++) s = s.replace("{" + i + "}", String.valueOf(args[i]));
        return s;
    }

    /** Idem, en Component. */
    public static Component c(String key, Object... args) {
        return Component.literal(t(key, args));
    }

    // ─── Réglage persistant ───

    private static Path settingsFile() {
        return FMLPaths.CONFIGDIR.get().resolve("wavesurvivor").resolve("settings.json");
    }

    public static void loadSettings() {
        try {
            Path f = settingsFile();
            if (!Files.exists(f)) { saveSettings(); return; }
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                JsonObject o = GSON.fromJson(r, JsonObject.class);
                if (o != null && o.has("language")) set(o.get("language").getAsString());
            }
            WaveSurvivorMod.LOGGER.info("[Lang] Langue du mod : {}", current);
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.warn("[Lang] settings.json illisible, anglais par défaut : {}", e.getMessage());
        }
    }

    public static void saveSettings() {
        try {
            Path f = settingsFile();
            Files.createDirectories(f.getParent());
            JsonObject o = new JsonObject();
            o.addProperty("language", current);
            Files.writeString(f, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(o), StandardCharsets.UTF_8);
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.warn("[Lang] Sauvegarde de settings.json impossible : {}", e.getMessage());
        }
    }
}
