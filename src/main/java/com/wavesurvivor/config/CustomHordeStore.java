package com.wavesurvivor.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wavesurvivor.WaveSurvivorMod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hordes créées / modifiées EN JEU (Éditeur de Hordes), dans un fichier séparé PRIORITAIRE sur config.json
 * → un export base44 ne les écrase jamais.
 * Fichier : config/wavesurvivor/custom_hordes.json → { "hordes": [ {horde JSON complet}... ], "deleted": ["nom"...] }
 *
 * Fusion au niveau JSON (avant Gson) : aucun champ n'est perdu, même inconnu du modèle Java.
 */
public final class CustomHordeStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String FILE_NAME = "custom_hordes.json";

    public enum Source { BASE44, CUSTOM, OVERRIDE }

    /** Hordes custom (fichier), clé = nom en minuscules. */
    private static final Map<String, JsonObject> CUSTOM = new LinkedHashMap<>();
    private static final Set<String> DELETED = new LinkedHashSet<>();
    /** Résultat de la dernière fusion : nom → JSON brut complet (pour l'éditeur). */
    private static final Map<String, JsonObject> MERGED = new LinkedHashMap<>();
    private static final Map<String, Source> SOURCES = new LinkedHashMap<>();

    private CustomHordeStore() {}

    private static String key(String name) { return name == null ? "" : name.trim().toLowerCase(); }

    public static synchronized void load() {
        CUSTOM.clear();
        DELETED.clear();
        Path f = filePath();
        if (!Files.exists(f)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.has("hordes")) for (JsonElement e : root.getAsJsonArray("hordes")) {
                if (!e.isJsonObject()) continue;
                JsonObject h = e.getAsJsonObject();
                String n = str(h, "hordeName");
                if (!n.isBlank()) CUSTOM.put(key(n), h);
            }
            if (root.has("deleted")) for (JsonElement e : root.getAsJsonArray("deleted")) DELETED.add(key(e.getAsString()));
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[CustomHordes] Lecture {} échouée : {}", FILE_NAME, e.getMessage());
        }
    }

    private static synchronized void save() {
        try {
            Path dir = FMLPaths.CONFIGDIR.get().resolve("wavesurvivor");
            if (!Files.exists(dir)) Files.createDirectories(dir);
            JsonObject root = new JsonObject();
            JsonArray arr = new JsonArray();
            for (JsonObject h : CUSTOM.values()) arr.add(h);
            root.add("hordes", arr);
            JsonArray del = new JsonArray();
            for (String d : DELETED) del.add(d);
            root.add("deleted", del);
            Files.writeString(filePath(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[CustomHordes] Écriture {} échouée : {}", FILE_NAME, e.getMessage());
        }
    }

    /** Appelé par ConfigLoader : fusionne les hordes custom dans payload.HordeConfigMulti. */
    public static synchronized void mergeInto(JsonObject payload) {
        load();
        MERGED.clear();
        SOURCES.clear();
        JsonArray base = payload.has("HordeConfigMulti") && payload.get("HordeConfigMulti").isJsonArray()
                ? payload.getAsJsonArray("HordeConfigMulti") : new JsonArray();
        JsonArray out = new JsonArray();
        Set<String> used = new LinkedHashSet<>();
        for (JsonElement e : base) {
            if (!e.isJsonObject()) continue;
            JsonObject h = e.getAsJsonObject();
            String k = key(str(h, "hordeName"));
            if (DELETED.contains(k) && !CUSTOM.containsKey(k)) continue;
            if (CUSTOM.containsKey(k)) {
                out.add(CUSTOM.get(k));
                track(CUSTOM.get(k), Source.OVERRIDE);
            } else {
                out.add(h);
                track(h, Source.BASE44);
            }
            used.add(k);
        }
        for (Map.Entry<String, JsonObject> c : CUSTOM.entrySet()) {
            if (used.contains(c.getKey())) continue;
            out.add(c.getValue());
            track(c.getValue(), Source.CUSTOM);
        }
        payload.add("HordeConfigMulti", out);
        if (!CUSTOM.isEmpty() || !DELETED.isEmpty()) {
            WaveSurvivorMod.LOGGER.info("[CustomHordes] {} horde(s) éditée(s) en jeu, {} masquée(s).", CUSTOM.size(), DELETED.size());
        }
    }

    private static void track(JsonObject h, Source s) {
        String n = str(h, "hordeName");
        MERGED.put(n, h);
        SOURCES.put(n, s);
    }

    // ─── API éditeur ───

    public static synchronized List<String> names() { return new ArrayList<>(MERGED.keySet()); }

    public static synchronized Source sourceOf(String name) { return SOURCES.getOrDefault(name, Source.BASE44); }

    public static synchronized JsonObject get(String name) {
        for (Map.Entry<String, JsonObject> e : MERGED.entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) return e.getValue().deepCopy();
        }
        return null;
    }

    public static synchronized boolean exists(String name) { return get(name) != null; }

    /** Enregistre une horde (création, modification, renommage). */
    public static synchronized void put(JsonObject horde, String oldName) {
        String newName = str(horde, "hordeName");
        String nk = key(newName);
        if (oldName != null && !oldName.isBlank() && !key(oldName).equals(nk)) {
            String ok = key(oldName);
            CUSTOM.remove(ok);
            if (SOURCES.get(oldName) != Source.CUSTOM) DELETED.add(ok); // renommer une horde base44 = la masquer
        }
        CUSTOM.put(nk, horde);
        DELETED.remove(nk);
        save();
    }

    /** Supprime une horde : retire la version custom et masque la version base44 éventuelle. */
    public static synchronized void delete(String name) {
        String k = key(name);
        Source s = sourceOf(name);
        CUSTOM.remove(k);
        if (s != Source.CUSTOM) DELETED.add(k);
        save();
    }

    private static String str(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "";
    }

    private static Path filePath() {
        return FMLPaths.CONFIGDIR.get().resolve("wavesurvivor").resolve(FILE_NAME);
    }
}
