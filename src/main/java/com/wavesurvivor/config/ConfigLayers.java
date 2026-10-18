package com.wavesurvivor.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wavesurvivor.WaveSurvivorMod;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * COUCHES DE CONFIGURATION (1.7).
 *
 *  • Couche BASE : livrée dans le jar (/defaults/wavesurvivor/), relue à chaque démarrage. Chaque mise à jour du mod la
 *    corrige donc chez tous les joueurs, sans toucher à rien d'autre.
 *  • Couche JOUEUR : config/wavesurvivor/*.json ne contient QUE les ajouts du joueur (hordes, entités, compétences, coffres…
 *    créés en jeu), ses modifications d'un élément de base (« surcharges ») et ses suppressions.
 *
 * Cette classe fournit la base ; les magasins (CustomHordeStore, CustomEntityStore, AltarRecipes, CustomChestStore,
 * RenaissanceShopStore, RenaissanceRarityStore) appliquent par-dessus le fichier du joueur.
 */
public final class ConfigLayers {

    private ConfigLayers() {}

    private static final String RESOURCE_DIR = "/defaults/wavesurvivor/";

    /**
     * Fichiers d'ajouts, dans l'ordre. Pour livrer un nouvel élément de base : créer le fichier d'ajouts et l'ajouter ici
     * (hordes, entités, compétences, recettes d'autel, déblocages, lots de coffres).
     */
    public static final List<String> ADDITIONS = List.of(
            "additions/kingdom_pillagers.json",  // 1.2.1 : « Le Siège des Pillards » (horde Kingdom prête à jouer)
            "additions/progression_1_4.json",    // 1.4.0 : conditions de déblocage (progression des hordes)
            "additions/relics_1_5.json",         // 1.5.0 : nouvelles reliques dans les Reliquaires
            "additions/frozen_peaks.json",       // 1.6.0 : « Les Pics Gelés »
            "additions/sables_ardents.json",     // 1.7.0 : « Les Sables Ardents »
            "additions/invasions.json"           // 1.7.0 : invasions de village prêtes à jouer
    );

    private static BuiltinBuilder.Result builtin;

    /** La base, reconstruite depuis le jar (une fois par lancement). */
    public static synchronized BuiltinBuilder.Result builtin() {
        if (builtin == null) {
            builtin = BuiltinBuilder.build(ConfigLayers::readResource, ADDITIONS);
            WaveSurvivorMod.LOGGER.info("[Config] Base livrée : {} hordes, {} entités, {} compétences.",
                    builtin.payload.has("HordeConfigMulti") ? builtin.payload.getAsJsonArray("HordeConfigMulti").size() : 0,
                    builtin.payload.has("CustomEntity") ? builtin.payload.getAsJsonArray("CustomEntity").size() : 0,
                    builtin.payload.has("CustomSkill") ? builtin.payload.getAsJsonArray("CustomSkill").size() : 0);
        }
        return builtin;
    }

    /** Copie de la base base44 (hordes, entités, compétences…) sur laquelle les magasins du joueur s'appliquent. */
    public static JsonObject payloadCopy() {
        return builtin().payload.deepCopy();
    }

    public static JsonObject builtinHorde(String name) {
        return BuiltinBuilder.find(array("HordeConfigMulti"), "hordeName", name);
    }

    public static JsonObject builtinEntity(CustomEntityStore.Kind kind, String name) {
        return BuiltinBuilder.find(array(kind.payloadKey()), kind.nameKey(), name);
    }

    private static JsonArray array(String key) {
        JsonObject p = builtin().payload;
        return p.has(key) && p.get(key).isJsonArray() ? p.getAsJsonArray(key) : new JsonArray();
    }

    /** Lit un fichier de la base dans le jar ; null s'il est absent. */
    public static String readResource(String rel) {
        try (InputStream in = ConfigLayers.class.getResourceAsStream(RESOURCE_DIR + rel)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[Config] Lecture de {} impossible : {}", rel, e.getMessage());
            return null;
        }
    }
}
