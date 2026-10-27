package com.wavesurvivor.arena;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.horde.editor.HordeJson;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * RÉGLAGES DES ARÈNES (config/wavesurvivor/arenas.json, facultatif ; rechargé par /ws reload) :
 * <pre>
 * { "generation": { "enabled": true, "maxSlope": 4, "randomRotation": true, "avoidNeighbors": true, "disabled": ["necropole_rois_dechus"] } }
 * </pre>
 * enabled : génération naturelle des arènes ; maxSlope : écart de hauteur maximal toléré sur le cercle ; randomRotation : orientation au hasard ;
 * disabled : arènes à ne pas générer ; graft : part des portails en ruine remplacés par chaque arène greffée, ex. {"breche_infernale": 0.25}.
 * Affiliation à la horde (clé « affinity ») : cryptWeight / graveWeight / entranceWeight (poids d'apparition), gateOpens (la grille de la crypte
 * s'ouvre), braziersFade (les braseros s'éteignent au fil des vagues), rewardChest (coffre à la première victoire) et rewardChestName (nom du
 * coffre roulette ; ou rewardChests {"<arène>": "<nom>"} par arène : « Reliquaire du Boss » pour la Nécropole, « Reliquaire du Nether » pour la Brèche),
 * firesRekindle (les feux du plan reprennent à chaque vague) et magmaCools (une partie du magma refroidit à la victoire). L'espacement (rareté) et les biomes se règlent dans les données du mod
 * (worldgen/structure_set/arena_*.json et tags/worldgen/biome/has_structure/arena_*.json), modifiables par datapack.
 */
public final class ArenaSettings {

    private static volatile JsonObject gen = null, affinity = null, graft = null;

    private ArenaSettings() {}

    public static synchronized void load() {
        JsonObject g = new JsonObject(), a = new JsonObject(), gr = new JsonObject();
        Path f = FMLPaths.CONFIGDIR.get().resolve("wavesurvivor").resolve("arenas.json");
        if (Files.exists(f)) {
            try {
                JsonObject root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                if (root.has("generation") && root.get("generation").isJsonObject()) g = root.getAsJsonObject("generation");
                if (root.has("affinity") && root.get("affinity").isJsonObject()) a = root.getAsJsonObject("affinity");
                if (root.has("graft") && root.get("graft").isJsonObject()) gr = root.getAsJsonObject("graft");
            } catch (Exception e) {
                WaveSurvivorMod.LOGGER.error("[Arènes] {} illisible : {}", f.getFileName(), e.getMessage());
            }
        }
        gen = g;
        affinity = a;
        graft = gr;
        ArenaBlueprint.clearCache();
    }

    private static JsonObject g() {
        if (gen == null) load();
        return gen;
    }

    public static boolean enabled() { return HordeJson.bool(g(), "enabled", true); }

    public static int maxSlope() { return Math.max(0, HordeJson.num(g(), "maxSlope", 4)); }

    public static boolean randomRotation() { return HordeJson.bool(g(), "randomRotation", true); }

    /** Les grands terrains (Val de la Couronne) restent à 4 chunks de toute autre structure ; false = pas de vérification (test de /locate). */
    public static boolean avoidNeighbors() { return HordeJson.bool(g(), "avoidNeighbors", true); }

    private static JsonObject aff() {
        if (gen == null) load();
        return affinity == null ? new JsonObject() : affinity;
    }

    public static int cryptWeight() { return Math.max(0, HordeJson.num(aff(), "cryptWeight", 5)); }

    public static int graveWeight() { return Math.max(0, HordeJson.num(aff(), "graveWeight", 4)); }

    public static int entranceWeight() { return Math.max(0, HordeJson.num(aff(), "entranceWeight", 1)); }

    public static boolean gateOpens() { return HordeJson.bool(aff(), "gateOpens", true); }

    public static boolean braziersFade() { return HordeJson.bool(aff(), "braziersFade", true); }

    public static boolean rewardChest() { return HordeJson.bool(aff(), "rewardChest", true); }

    /** Nom du coffre roulette de récompense de cette arène (affinity.rewardChests.<arène>, sinon rewardChestName, sinon une valeur par défaut). */
    public static String rewardChestName(String arena) {
        JsonObject per = aff().has("rewardChests") && aff().get("rewardChests").isJsonObject() ? aff().getAsJsonObject("rewardChests") : new JsonObject();
        if (per.has(arena)) return HordeJson.str(per, arena, "Reliquaire du Boss");
        if (aff().has("rewardChestName")) return HordeJson.str(aff(), "rewardChestName", "Reliquaire du Boss");
        return switch (arena) {
            case "breche_infernale" -> "Reliquaire du Nether";
            case "ilot_du_neant" -> "Reliquaire de l'End";
            case "recif_englouti" -> "Reliquaire des Abysses";
            case "temple_de_re", "bastion_de_givre" -> "Reliquaire des Trésors";
            case "cercle_des_arcanes" -> "Reliquaire Arcanique";
            default -> "Reliquaire du Boss";
        };
    }

    /** Les feux du plan reprennent à chaque vague. */
    public static boolean firesRekindle() { return HordeJson.bool(aff(), "firesRekindle", true); }

    /** Une partie du magma refroidit en pierre noire à la victoire. */
    public static boolean magmaCools() { return HordeJson.bool(aff(), "magmaCools", true); }

    /** Part (0 à 1) des portails en ruine éligibles remplacés par cette arène (clé « graft » de arenas.json ; 0,25 par défaut, 0 = désactivé). */
    public static double graftChance(String arena) {
        if (gen == null) load();
        JsonObject g = graft == null ? new JsonObject() : graft;
        return Math.max(0, Math.min(1, HordeJson.dbl(g, arena, 0.25)));
    }

    public static boolean isDisabled(String arena) {
        Set<String> out = new HashSet<>();
        if (g().has("disabled") && g().get("disabled").isJsonArray()) for (JsonElement e : g().getAsJsonArray("disabled")) out.add(e.getAsString());
        return out.contains(arena);
    }
}
