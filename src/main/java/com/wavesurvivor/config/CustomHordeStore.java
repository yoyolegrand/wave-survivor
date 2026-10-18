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
 * COUCHE JOUEUR DES HORDES (1.7) : ce que le joueur a ajouté ou modifié en jeu (Éditeur de Hordes), par-dessus la base
 * livrée avec le mod (ConfigLayers). Un export base44 ne l'écrase jamais.
 * Fichier : config/wavesurvivor/custom_hordes.json
 *   { "hordes": [ {horde complète créée par le joueur}... ],
 *     "patches": { "nom d'une horde de base": { champs modifiés uniquement } },
 *     "deleted": ["nom"...] }
 *
 *  • horde créée par le joueur → entrée complète ;
 *  • horde de base modifiée → PATCH (champs changés seulement) appliqué sur la version actuelle de la base : une mise à jour
 *    corrige tout ce que le joueur n'a pas touché ;
 *  • horde de base supprimée → son nom dans « deleted ».
 * (Une entrée complète portant le nom d'une horde de base — ancien format — reste prioritaire et est convertie à la
 * première sauvegarde depuis l'éditeur.)
 * Fusion au niveau JSON (avant Gson) : aucun champ n'est perdu, même inconnu du modèle Java.
 */
public final class CustomHordeStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String FILE_NAME = "custom_hordes.json";

    public enum Source { BASE44, CUSTOM, OVERRIDE }

    /** Hordes complètes du joueur (fichier), clé = nom en minuscules. */
    private static final Map<String, JsonObject> CUSTOM = new LinkedHashMap<>();
    /** Modifications des hordes de base : nom en minuscules → champs modifiés. */
    private static final Map<String, JsonObject> PATCHES = new LinkedHashMap<>();
    private static final Set<String> DELETED = new LinkedHashSet<>();
    /** Résultat de la dernière fusion : nom → JSON brut complet (pour l'éditeur). */
    private static final Map<String, JsonObject> MERGED = new LinkedHashMap<>();
    private static final Map<String, Source> SOURCES = new LinkedHashMap<>();

    private CustomHordeStore() {}

    private static String key(String name) { return name == null ? "" : name.trim().toLowerCase(); }

    public static synchronized void load() {
        CUSTOM.clear();
        PATCHES.clear();
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
            if (root.has("patches") && root.get("patches").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("patches").entrySet()) {
                    if (e.getValue().isJsonObject()) PATCHES.put(key(e.getKey()), e.getValue().getAsJsonObject());
                }
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
            JsonObject patches = new JsonObject();
            for (Map.Entry<String, JsonObject> p : PATCHES.entrySet()) patches.add(p.getKey(), p.getValue());
            root.add("patches", patches);
            JsonArray del = new JsonArray();
            for (String d : DELETED) del.add(d);
            root.add("deleted", del);
            Files.writeString(filePath(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[CustomHordes] Écriture {} échouée : {}", FILE_NAME, e.getMessage());
        }
    }

    /** Appelé par ConfigLoader : applique la couche du joueur sur payload.HordeConfigMulti (la base). */
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
            } else if (PATCHES.containsKey(k)) {
                JsonObject patched = Patch.apply(h, PATCHES.get(k));
                out.add(patched);
                track(patched, Source.OVERRIDE);
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
        if (!CUSTOM.isEmpty() || !PATCHES.isEmpty() || !DELETED.isEmpty()) {
            WaveSurvivorMod.LOGGER.info("[CustomHordes] {} horde(s) à vous, {} horde(s) de base modifiée(s), {} masquée(s).",
                    CUSTOM.size(), PATCHES.size(), DELETED.size());
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

    /** Nombre de champs que le joueur a modifiés dans cette horde de base (0 = suit entièrement la base). */
    public static synchronized int modifiedFields(String name) {
        JsonObject p = PATCHES.get(key(name));
        return p == null ? 0 : Patch.count(p);
    }

    /** Enregistre une horde (création, modification, renommage). Pour une horde de base, seuls les champs modifiés sont gardés. */
    public static synchronized void put(JsonObject horde, String oldName) {
        String newName = str(horde, "hordeName");
        String nk = key(newName);
        if (oldName != null && !oldName.isBlank() && !key(oldName).equals(nk)) {
            String ok = key(oldName);
            CUSTOM.remove(ok);
            PATCHES.remove(ok);
            if (SOURCES.get(oldName) != Source.CUSTOM) DELETED.add(ok); // renommer une horde de base = la masquer
        }
        JsonObject base = ConfigLayers.builtinHorde(newName);
        if (base != null) {
            CUSTOM.remove(nk);
            JsonObject p = Patch.diff(base, horde);
            if (p == null) PATCHES.remove(nk); else PATCHES.put(nk, p);
        } else {
            CUSTOM.put(nk, horde);
            PATCHES.remove(nk);
        }
        DELETED.remove(nk);
        save();
    }

    /** Supprime une horde : retire la version du joueur et masque la version de base éventuelle. */
    public static synchronized void delete(String name) {
        String k = key(name);
        Source s = sourceOf(name);
        CUSTOM.remove(k);
        PATCHES.remove(k);
        if (s != Source.CUSTOM) DELETED.add(k);
        save();
    }

    /** Hordes du mod que le joueur a supprimées (noms de la base). */
    public static synchronized List<String> deletedBaseNames() {
        List<String> out = new ArrayList<>();
        for (String d : DELETED) {
            JsonObject b = ConfigLayers.builtinHorde(d);
            if (b != null && !CUSTOM.containsKey(d)) out.add(str(b, "hordeName"));
        }
        return out;
    }

    /** Rétablit la version de base : retire les modifications (et la suppression éventuelle). */
    public static synchronized void revert(String name) {
        String k = key(name);
        CUSTOM.remove(k);
        PATCHES.remove(k);
        DELETED.remove(k);
        save();
    }

    private static String str(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "";
    }

    private static Path filePath() {
        return FMLPaths.CONFIGDIR.get().resolve("wavesurvivor").resolve(FILE_NAME);
    }
}
