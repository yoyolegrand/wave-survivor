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
 * COUCHE JOUEUR DES ENTITÉS ET COMPÉTENCES (1.7), par-dessus la base livrée avec le mod (ConfigLayers).
 * Fichier : config/wavesurvivor/custom_entities.json
 *   { "entities": [...créées par le joueur], "skills": [...], "entityPatches": {nom: champs modifiés},
 *     "skillPatches": {...}, "deletedEntities": [...], "deletedSkills": [...] }
 * Même principe que les hordes (CustomHordeStore) : élément de base modifié = patch champ par champ appliqué sur la version
 * actuelle de la base ; élément créé par le joueur = entrée complète.
 * Fusion au niveau JSON (aucun champ perdu).
 */
public final class CustomEntityStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String FILE_NAME = "custom_entities.json";

    public enum Kind {
        ENTITY("CustomEntity", "entityName", "entities", "deletedEntities", "entityPatches"),
        SKILL("CustomSkill", "skillName", "skills", "deletedSkills", "skillPatches");

        final String payloadKey, nameKey, fileKey, deletedKey, patchKey;
        Kind(String payloadKey, String nameKey, String fileKey, String deletedKey, String patchKey) {
            this.payloadKey = payloadKey; this.nameKey = nameKey; this.fileKey = fileKey; this.deletedKey = deletedKey; this.patchKey = patchKey;
        }
        public String nameKey() { return nameKey; }
        public String payloadKey() { return payloadKey; }
    }

    private static final Map<Kind, Map<String, JsonObject>> CUSTOM = new LinkedHashMap<>();
    private static final Map<Kind, Map<String, JsonObject>> PATCHES = new LinkedHashMap<>();
    private static final Map<Kind, Set<String>> DELETED = new LinkedHashMap<>();
    private static final Map<Kind, Map<String, JsonObject>> MERGED = new LinkedHashMap<>();
    private static final Map<Kind, Map<String, String>> SOURCES = new LinkedHashMap<>();

    static {
        for (Kind k : Kind.values()) {
            CUSTOM.put(k, new LinkedHashMap<>());
            PATCHES.put(k, new LinkedHashMap<>());
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
        for (Kind k : Kind.values()) { CUSTOM.get(k).clear(); PATCHES.get(k).clear(); DELETED.get(k).clear(); }
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
                if (root.has(k.patchKey) && root.get(k.patchKey).isJsonObject()) {
                    for (Map.Entry<String, JsonElement> e : root.getAsJsonObject(k.patchKey).entrySet()) {
                        if (e.getValue().isJsonObject()) PATCHES.get(k).put(key(e.getKey()), e.getValue().getAsJsonObject());
                    }
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
                JsonObject patches = new JsonObject();
                for (Map.Entry<String, JsonObject> p : PATCHES.get(k).entrySet()) patches.add(p.getKey(), p.getValue());
                root.add(k.patchKey, patches);
                JsonArray del = new JsonArray();
                for (String d : DELETED.get(k)) del.add(d);
                root.add(k.deletedKey, del);
            }
            Files.writeString(filePath(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[CustomEntities] Écriture {} échouée : {}", FILE_NAME, e.getMessage());
        }
    }

    /** Appelé par ConfigLoader : applique la couche du joueur sur payload.CustomEntity / payload.CustomSkill (la base). */
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
                JsonObject chosen;
                String src;
                if (CUSTOM.get(k).containsKey(kk)) { chosen = CUSTOM.get(k).get(kk); src = "OVERRIDE"; }
                else if (PATCHES.get(k).containsKey(kk)) { chosen = Patch.apply(o, PATCHES.get(k).get(kk)); src = "OVERRIDE"; }
                else { chosen = o; src = "BASE44"; }
                out.add(chosen);
                MERGED.get(k).put(n, chosen);
                SOURCES.get(k).put(n, src);
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

    /** Nombre de champs que le joueur a modifiés dans cet élément de base (0 = suit entièrement la base). */
    public static synchronized int modifiedFields(Kind k, String n) {
        JsonObject p = PATCHES.get(k).get(key(n));
        return p == null ? 0 : Patch.count(p);
    }

    /** Enregistre un élément. Pour un élément de base, seuls les champs modifiés sont gardés. */
    public static synchronized void put(Kind k, JsonObject o, String oldName) {
        String n = name(k, o), nk = key(n);
        if (oldName != null && !oldName.isBlank() && !key(oldName).equals(nk)) {
            CUSTOM.get(k).remove(key(oldName));
            PATCHES.get(k).remove(key(oldName));
            if (!"CUSTOM".equals(sourceOf(k, oldName))) DELETED.get(k).add(key(oldName));
        }
        JsonObject base = ConfigLayers.builtinEntity(k, n);
        if (base != null) {
            CUSTOM.get(k).remove(nk);
            JsonObject p = Patch.diff(base, o);
            if (p == null) PATCHES.get(k).remove(nk); else PATCHES.get(k).put(nk, p);
        } else {
            CUSTOM.get(k).put(nk, o);
            PATCHES.get(k).remove(nk);
        }
        DELETED.get(k).remove(nk);
        save();
    }

    public static synchronized void delete(Kind k, String n) {
        String s = sourceOf(k, n);
        CUSTOM.get(k).remove(key(n));
        PATCHES.get(k).remove(key(n));
        if (!"CUSTOM".equals(s)) DELETED.get(k).add(key(n));
        save();
    }

    /** Rétablit la version de base : retire les modifications (et la suppression éventuelle). */
    public static synchronized void revert(Kind k, String n) {
        CUSTOM.get(k).remove(key(n));
        PATCHES.get(k).remove(key(n));
        DELETED.get(k).remove(key(n));
        save();
    }

    private static Path filePath() {
        return FMLPaths.CONFIGDIR.get().resolve("wavesurvivor").resolve(FILE_NAME);
    }
}
