package com.wavesurvivor.horde.benediction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.Patch;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * COUCHE JOUEUR DES BÉNÉDICTIONS (1.8) : ce que le joueur a ajouté ou modifié, par-dessus la base livrée avec le mod.
 * Fichier : config/wavesurvivor/custom_blessings.json
 *   { "categories": [ catégories complètes créées par le joueur ],
 *     "patches": { "ID_DE_CATEGORIE": { champs modifiés uniquement } },
 *     "deleted": [ "ID_DE_CATEGORIE"… ] }
 * Même principe que les hordes (CustomHordeStore) : catégorie de base modifiée = patch champ par champ appliqué sur la version actuelle
 * de la base (les niveaux de rareté sont un objet : un niveau modifié ne gèle pas les autres) ; catégorie créée = entrée complète.
 */
public final class CustomBlessingStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String FILE_NAME = "custom_blessings.json";

    private static final Map<String, JsonObject> CUSTOM = new LinkedHashMap<>();
    private static final Map<String, JsonObject> PATCHES = new LinkedHashMap<>();
    private static final Set<String> DELETED = new HashSet<>();
    /** Réglages du tirage modifiés par le joueur (champs changés seulement). */
    private static JsonObject settingsPatch = new JsonObject();
    /** Origine de chaque catégorie effective (dernière fusion) : BASE44 (livrée), OVERRIDE (livrée et modifiée), CUSTOM (créée). */
    private static final Map<String, String> SOURCES = new HashMap<>();

    private CustomBlessingStore() {}

    private static String key(String id) { return id == null ? "" : id.trim().toUpperCase(); }

    private static String idOf(JsonElement e) {
        return e != null && e.isJsonObject() && e.getAsJsonObject().has("id") && e.getAsJsonObject().get("id").isJsonPrimitive()
                ? e.getAsJsonObject().get("id").getAsString() : "";
    }

    private static synchronized void load() {
        CUSTOM.clear();
        PATCHES.clear();
        DELETED.clear();
        settingsPatch = new JsonObject();
        Path f = filePath();
        if (!Files.exists(f)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.has("categories")) for (JsonElement e : root.getAsJsonArray("categories")) {
                if (e.isJsonObject() && !idOf(e).isBlank()) CUSTOM.put(key(idOf(e)), e.getAsJsonObject());
            }
            if (root.has("patches") && root.get("patches").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("patches").entrySet()) {
                    if (e.getValue().isJsonObject()) PATCHES.put(key(e.getKey()), e.getValue().getAsJsonObject());
                }
            }
            if (root.has("deleted")) for (JsonElement e : root.getAsJsonArray("deleted")) DELETED.add(key(e.getAsString()));
            if (root.has("settings") && root.get("settings").isJsonObject()) settingsPatch = root.getAsJsonObject("settings");
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[Bénédictions] Lecture de {} impossible : {}", FILE_NAME, e.getMessage());
        }
    }

    /** Applique la couche du joueur sur la base : renvoie les catégories effectives. */
    public static synchronized JsonArray mergeInto(JsonArray base) {
        load();
        JsonArray out = new JsonArray();
        Set<String> used = new HashSet<>();
        SOURCES.clear();
        for (JsonElement e : base) {
            if (!e.isJsonObject()) continue;
            String k = key(idOf(e));
            if (DELETED.contains(k) && !CUSTOM.containsKey(k)) continue;
            if (CUSTOM.containsKey(k)) { out.add(CUSTOM.get(k).deepCopy()); SOURCES.put(k, "OVERRIDE"); }
            else if (PATCHES.containsKey(k)) { out.add(Patch.apply(e.getAsJsonObject(), PATCHES.get(k))); SOURCES.put(k, "OVERRIDE"); }
            else { out.add(e); SOURCES.put(k, "BASE44"); }
            used.add(k);
        }
        for (Map.Entry<String, JsonObject> c : CUSTOM.entrySet()) {
            if (used.contains(c.getKey())) continue;
            out.add(c.getValue().deepCopy());
            SOURCES.put(c.getKey(), "CUSTOM");
        }
        if (!CUSTOM.isEmpty() || !PATCHES.isEmpty() || !DELETED.isEmpty()) {
            WaveSurvivorMod.LOGGER.info("[Bénédictions] {} catégorie(s) à vous, {} de base modifiée(s), {} masquée(s).",
                    CUSTOM.size(), PATCHES.size(), DELETED.size());
        }
        return out;
    }

    /** Réglages du tirage : base du mod + modifications du joueur. */
    public static synchronized JsonObject applySettings(JsonObject base) {
        return Patch.apply(base, settingsPatch);
    }

    // ─── API éditeur ───

    /** Origine d'une catégorie : BASE44 (livrée), OVERRIDE (livrée et modifiée), CUSTOM (créée par le joueur). */
    public static synchronized String sourceOf(String id) { return SOURCES.getOrDefault(key(id), "BASE44"); }

    /** Nombre de champs modifiés d'une catégorie livrée (0 = suit entièrement la base). */
    public static synchronized int modifiedFields(String id) {
        JsonObject p = PATCHES.get(key(id));
        return p == null ? 0 : Patch.count(p);
    }

    private static JsonObject baseOf(String id) {
        return com.wavesurvivor.config.BuiltinBuilder.find(com.wavesurvivor.config.ConfigLayers.builtin().blessings, "id", id);
    }

    /** Enregistre une catégorie. Pour une catégorie livrée, seuls les champs modifiés sont gardés ; identique à la base = plus de modification. */
    public static synchronized void put(JsonObject cat) {
        String id = idOf(cat), k = key(id);
        JsonObject base = baseOf(id);
        if (base != null) {
            CUSTOM.remove(k);
            JsonObject p = Patch.diff(base, cat);
            if (p == null) PATCHES.remove(k); else PATCHES.put(k, p);
        } else {
            CUSTOM.put(k, cat);
            PATCHES.remove(k);
        }
        DELETED.remove(k);
        save();
    }

    /** Supprime une catégorie : retire la version du joueur et masque la version livrée éventuelle. */
    public static synchronized void delete(String id) {
        String k = key(id);
        String src = sourceOf(id);
        CUSTOM.remove(k);
        PATCHES.remove(k);
        if (!"CUSTOM".equals(src)) DELETED.add(k);
        save();
    }

    /** Rétablit la version livrée : retire les modifications et la suppression. */
    public static synchronized void revert(String id) {
        String k = key(id);
        CUSTOM.remove(k);
        PATCHES.remove(k);
        DELETED.remove(k);
        save();
    }

    /** Catégories livrées que le joueur a supprimées. */
    public static synchronized List<String> deletedBaseIds() {
        List<String> out = new ArrayList<>();
        for (String d : DELETED) {
            JsonObject b = baseOf(d);
            if (b != null && !CUSTOM.containsKey(d)) out.add(idOf(b));
        }
        return out;
    }

    private static synchronized void save() {
        try {
            Path dir = FMLPaths.CONFIGDIR.get().resolve("wavesurvivor");
            if (!Files.exists(dir)) Files.createDirectories(dir);
            JsonObject root = new JsonObject();
            JsonArray cats = new JsonArray();
            for (JsonObject o : CUSTOM.values()) cats.add(o);
            root.add("categories", cats);
            JsonObject patches = new JsonObject();
            for (Map.Entry<String, JsonObject> p : PATCHES.entrySet()) patches.add(p.getKey(), p.getValue());
            root.add("patches", patches);
            JsonArray del = new JsonArray();
            for (String d : DELETED) del.add(d);
            root.add("deleted", del);
            if (settingsPatch.size() > 0) root.add("settings", settingsPatch);
            Files.writeString(filePath(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[Bénédictions] Écriture de {} impossible : {}", FILE_NAME, e.getMessage());
        }
    }

    /** Identifiants des catégories de la couche du joueur (pour l'éditeur). */
    public static synchronized List<String> customIds() {
        List<String> l = new ArrayList<>();
        for (JsonObject o : CUSTOM.values()) l.add(idOf(o));
        return l;
    }

    private static Path filePath() {
        return FMLPaths.CONFIGDIR.get().resolve("wavesurvivor").resolve(FILE_NAME);
    }
}
