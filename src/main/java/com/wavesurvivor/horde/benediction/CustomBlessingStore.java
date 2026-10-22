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

    // ─── Packs (export / import) ───

    private static final java.util.regex.Pattern PACK_NAME = java.util.regex.Pattern.compile("[A-Za-z0-9_-]{1,40}");

    public static boolean validPackName(String n) { return n != null && PACK_NAME.matcher(n).matches(); }

    private static Path packsDir() { return FMLPaths.CONFIGDIR.get().resolve("wavesurvivor").resolve("blessing_packs"); }

    /** Noms des packs présents dans le dossier. */
    public static synchronized List<String> listPacks() {
        List<String> out = new ArrayList<>();
        try {
            if (Files.isDirectory(packsDir())) try (var st = Files.list(packsDir())) {
                st.forEach(f -> {
                    String n = f.getFileName().toString();
                    if (n.endsWith(".json") && validPackName(n.substring(0, n.length() - 5))) out.add(n.substring(0, n.length() - 5));
                });
            }
        } catch (Exception ignored) { }
        java.util.Collections.sort(out);
        return out;
    }

    /** Écrit ta couche (catégories créées, modifications, suppressions, réglages) dans un pack. Renvoie le chemin du fichier. */
    public static synchronized boolean settingsModified() { return settingsPatch.size() > 0; }

    /**
     * Écrit ta couche dans un pack et renvoie le fichier ; {@code only} = éléments retenus (identifiants de catégories, « @settings »,
     * « @deleted »), null = tout. Renvoie null s'il n'y a rien à exporter (rien créé ni modifié).
     */
    public static synchronized Path exportPack(String name, Set<String> only) throws java.io.IOException {
        Files.createDirectories(packsDir());
        JsonObject root = new JsonObject();
        root.addProperty("format", 1);
        JsonArray cats = new JsonArray();
        for (Map.Entry<String, JsonObject> o : CUSTOM.entrySet()) if (only == null || containsKey(only, o.getKey())) cats.add(o.getValue());
        root.add("categories", cats);
        JsonObject patches = new JsonObject();
        for (Map.Entry<String, JsonObject> p : PATCHES.entrySet()) if (only == null || containsKey(only, p.getKey())) patches.add(p.getKey(), p.getValue());
        root.add("patches", patches);
        JsonArray del = new JsonArray();
        if (only == null || only.contains("@deleted")) for (String d : DELETED) del.add(d);
        root.add("deleted", del);
        if (settingsPatch.size() > 0 && (only == null || only.contains("@settings"))) root.add("settings", settingsPatch);
        if (cats.size() == 0 && patches.size() == 0 && del.size() == 0 && !root.has("settings")) return null;
        Path f = packsDir().resolve(name + ".json");
        Files.writeString(f, GSON.toJson(root), StandardCharsets.UTF_8);
        return f;
    }

    private static boolean containsKey(Set<String> ids, String k) {
        for (String i : ids) if (key(i).equals(k)) return true;
        return false;
    }

    /** Résultat d'un import : éléments ajoutés, remplacés, et ceux ignorés à cause d'un conflit (identifiant déjà à toi). */
    public record ImportResult(int added, int replaced, List<String> skipped, String error) {}

    /** Fusionne un pack dans ta couche. Sans {@code overwrite}, ce qui existe déjà chez toi est conservé (et listé comme ignoré). */
    public static synchronized ImportResult importPack(String name, boolean overwrite) {
        Path f = packsDir().resolve(name + ".json");
        if (!Files.exists(f)) return new ImportResult(0, 0, List.of(), "introuvable");
        JsonObject root;
        try {
            root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return new ImportResult(0, 0, List.of(), e.getMessage() == null ? "illisible" : e.getMessage());
        }
        load();
        int added = 0, replaced = 0;
        List<String> skipped = new ArrayList<>();
        if (root.has("categories") && root.get("categories").isJsonArray()) for (JsonElement e : root.getAsJsonArray("categories")) {
            if (!e.isJsonObject() || idOf(e).isBlank() || !e.getAsJsonObject().has("tiers")) continue;
            String id = idOf(e).trim().toUpperCase(), k = key(id);
            if (baseOf(id) != null) { skipped.add(id); continue; } // un id livré avec le mod ne peut pas être recréé
            JsonObject c = e.getAsJsonObject().deepCopy();
            c.addProperty("id", id);
            if (CUSTOM.containsKey(k)) {
                if (!overwrite) { skipped.add(id); continue; }
                replaced++;
            } else added++;
            CUSTOM.put(k, c);
            DELETED.remove(k);
        }
        if (root.has("patches") && root.get("patches").isJsonObject()) for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("patches").entrySet()) {
            if (!e.getValue().isJsonObject() || baseOf(e.getKey()) == null) continue;
            String k = key(e.getKey());
            if (PATCHES.containsKey(k)) {
                if (!overwrite) { skipped.add(e.getKey()); continue; }
                replaced++;
            } else added++;
            PATCHES.put(k, e.getValue().getAsJsonObject().deepCopy());
        }
        if (root.has("deleted") && root.get("deleted").isJsonArray()) for (JsonElement e : root.getAsJsonArray("deleted")) {
            if (e.isJsonPrimitive() && baseOf(e.getAsString()) != null && DELETED.add(key(e.getAsString()))) added++;
        }
        if (root.has("settings") && root.get("settings").isJsonObject() && root.getAsJsonObject("settings").size() > 0) {
            if (settingsPatch.size() == 0 || overwrite) {
                settingsPatch = Patch.apply(settingsPatch, root.getAsJsonObject("settings"));
                added++;
            } else skipped.add("settings");
        }
        save();
        return new ImportResult(added, replaced, skipped, null);
    }

    // ─── API éditeur ───

    /** Enregistre les réglages complets envoyés par l'éditeur : seules les différences avec la base du mod sont gardées ("" = tout rétablir). */
    public static synchronized void putSettings(JsonObject full) {
        if (full == null) {
            settingsPatch = new JsonObject();
        } else {
            JsonObject p = Patch.diff(com.wavesurvivor.config.ConfigLayers.builtin().blessingSettings, full);
            settingsPatch = p == null ? new JsonObject() : p;
        }
        save();
    }

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
