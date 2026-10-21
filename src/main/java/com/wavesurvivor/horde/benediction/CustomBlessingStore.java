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
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[Bénédictions] Lecture de {} impossible : {}", FILE_NAME, e.getMessage());
        }
    }

    /** Applique la couche du joueur sur la base : renvoie les catégories effectives. */
    public static synchronized JsonArray mergeInto(JsonArray base) {
        load();
        JsonArray out = new JsonArray();
        Set<String> used = new HashSet<>();
        for (JsonElement e : base) {
            if (!e.isJsonObject()) continue;
            String k = key(idOf(e));
            if (DELETED.contains(k) && !CUSTOM.containsKey(k)) continue;
            if (CUSTOM.containsKey(k)) out.add(CUSTOM.get(k).deepCopy());
            else if (PATCHES.containsKey(k)) out.add(Patch.apply(e.getAsJsonObject(), PATCHES.get(k)));
            else out.add(e);
            used.add(k);
        }
        for (Map.Entry<String, JsonObject> c : CUSTOM.entrySet()) if (!used.contains(c.getKey())) out.add(c.getValue().deepCopy());
        if (!CUSTOM.isEmpty() || !PATCHES.isEmpty() || !DELETED.isEmpty()) {
            WaveSurvivorMod.LOGGER.info("[Bénédictions] {} catégorie(s) à vous, {} de base modifiée(s), {} masquée(s).",
                    CUSTOM.size(), PATCHES.size(), DELETED.size());
        }
        return out;
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
