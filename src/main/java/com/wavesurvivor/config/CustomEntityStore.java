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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Entités custom et compétences créées / modifiées EN JEU (Éditeur d'entités), dans un fichier séparé
 * PRIORITAIRE sur config.json → jamais écrasé par un export base44.
 * Fichier : config/wavesurvivor/custom_entities.json
 *   { "entities": [...], "skills": [...], "deletedEntities": [...], "deletedSkills": [...] }
 * Fusion au niveau JSON (aucun champ perdu).
 */
public final class CustomEntityStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String FILE_NAME = "custom_entities.json";

    public enum Kind {
        ENTITY("CustomEntity", "entityName", "entities", "deletedEntities"),
        SKILL("CustomSkill", "skillName", "skills", "deletedSkills");

        final String payloadKey, nameKey, fileKey, deletedKey;
        Kind(String payloadKey, String nameKey, String fileKey, String deletedKey) {
            this.payloadKey = payloadKey; this.nameKey = nameKey; this.fileKey = fileKey; this.deletedKey = deletedKey;
        }
        public String nameKey() { return nameKey; }
    }

    private static final Map<Kind, Map<String, JsonObject>> CUSTOM = new LinkedHashMap<>();
    private static final Map<Kind, Set<String>> DELETED = new LinkedHashMap<>();
    private static final Map<Kind, Map<String, JsonObject>> MERGED = new LinkedHashMap<>();
    private static final Map<Kind, Map<String, String>> SOURCES = new LinkedHashMap<>();

    static {
        for (Kind k : Kind.values()) {
            CUSTOM.put(k, new LinkedHashMap<>());
            DELETED.put(k, new LinkedHashSet<>());
            MERGED.put(k, new LinkedHashMap<>());
            SOURCES.put(k, new LinkedHashMap<>());
        }
    }

    private CustomEntityStore() {}

    private static String key(String n) { return n == null ? "" : n.trim().toLowerCase(); }

    private static String name(Kind k, JsonObject o) {
        return o != null && o.has(k.nameKey) && o.get(k.nameKey).isJsonPrimitive() ? o.get(k.nameKey).getAsString() : "";
    }

    public static synchronized void load() {
        for (Kind k : Kind.values()) { CUSTOM.get(k).clear(); DELETED.get(k).clear(); }
        Path f = filePath();
        if (!Files.exists(f)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Kind k : Kind.values()) {
                if (root.has(k.fileKey)) for (JsonElement e : root.getAsJsonArray(k.fileKey)) {
                    if (!e.isJsonObject()) continue;
                    String n = name(k, e.getAsJsonObject());
                    if (!n.isBlank()) CUSTOM.get(k).put(key(n), e.getAsJsonObject());
                }
                if (root.has(k.deletedKey)) for (JsonElement e : root.getAsJsonArray(k.deletedKey)) DELETED.get(k).add(key(e.getAsString()));
            }
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[CustomEntities] Lecture {} échouée : {}", FILE_NAME, e.getMessage());
        }
    }

    private static synchronized void save() {
        try {
            Path dir = FMLPaths.CONFIGDIR.get().resolve("wavesurvivor");
            if (!Files.exists(dir)) Files.createDirectories(dir);
            JsonObject root = new JsonObject();
            for (Kind k : Kind.values()) {
                JsonArray arr = new JsonArray();
                for (JsonObject o : CUSTOM.get(k).values()) arr.add(o);
                root.add(k.fileKey, arr);
                JsonArray del = new JsonArray();
                for (String d : DELETED.get(k)) del.add(d);
                root.add(k.deletedKey, del);
            }
            Files.writeString(filePath(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[CustomEntities] Écriture {} échouée : {}", FILE_NAME, e.getMessage());
        }
    }

    /** Appelé par ConfigLoader : fusionne dans payload.CustomEntity / payload.CustomSkill. */
    public static synchronized void mergeInto(JsonObject payload) {
        load();
        for (Kind k : Kind.values()) {
            MERGED.get(k).clear();
            SOURCES.get(k).clear();
            JsonArray base = payload.has(k.payloadKey) && payload.get(k.payloadKey).isJsonArray()
                    ? payload.getAsJsonArray(k.payloadKey) : new JsonArray();
            JsonArray out = new JsonArray();
            Set<String> used = new LinkedHashSet<>();
            for (JsonElement e : base) {
                if (!e.isJsonObject()) continue;
                JsonObject o = e.getAsJsonObject();
                String n = name(k, o), kk = key(n);
                if (n.isBlank()) { out.add(o); continue; }
                if (DELETED.get(k).contains(kk) && !CUSTOM.get(k).containsKey(kk)) continue;
                JsonObject chosen = CUSTOM.get(k).getOrDefault(kk, o);
                out.add(chosen);
                MERGED.get(k).put(n, chosen);
                SOURCES.get(k).put(n, CUSTOM.get(k).containsKey(kk) ? "OVERRIDE" : "BASE44");
                used.add(kk);
            }
            for (Map.Entry<String, JsonObject> c : CUSTOM.get(k).entrySet()) {
                if (used.contains(c.getKey())) continue;
                out.add(c.getValue());
                String n = name(k, c.getValue());
                MERGED.get(k).put(n, c.getValue());
                SOURCES.get(k).put(n, "CUSTOM");
            }
            payload.add(k.payloadKey, out);
        }
    }

    // ─── API éditeur ───

    public static synchronized Map<String, JsonObject> all(Kind k) {
        Map<String, JsonObject> m = new LinkedHashMap<>();
        for (Map.Entry<String, JsonObject> e : MERGED.get(k).entrySet()) m.put(e.getKey(), e.getValue().deepCopy());
        return m;
    }

    public static synchronized String sourceOf(Kind k, String n) { return SOURCES.get(k).getOrDefault(n, "BASE44"); }

    public static synchronized boolean exists(Kind k, String n) {
        for (String s : MERGED.get(k).keySet()) if (s.equalsIgnoreCase(n)) return true;
        return false;
    }

    public static synchronized void put(Kind k, JsonObject o, String oldName) {
        String n = name(k, o), nk = key(n);
        if (oldName != null && !oldName.isBlank() && !key(oldName).equals(nk)) {
            CUSTOM.get(k).remove(key(oldName));
            if (!"CUSTOM".equals(sourceOf(k, oldName))) DELETED.get(k).add(key(oldName));
        }
        CUSTOM.get(k).put(nk, o);
        DELETED.get(k).remove(nk);
        save();
    }

    public static synchronized void delete(Kind k, String n) {
        String s = sourceOf(k, n);
        CUSTOM.get(k).remove(key(n));
        if (!"CUSTOM".equals(s)) DELETED.get(k).add(key(n));
        save();
    }

    private static Path filePath() {
        return FMLPaths.CONFIGDIR.get().resolve("wavesurvivor").resolve(FILE_NAME);
    }
}
