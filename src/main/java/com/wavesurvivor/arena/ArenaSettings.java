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
 * { "generation": { "enabled": true, "maxSlope": 4, "randomRotation": true, "disabled": ["necropole_rois_dechus"] } }
 * </pre>
 * enabled : génération naturelle des arènes ; maxSlope : écart de hauteur maximal toléré sur le cercle ; randomRotation : orientation au hasard ;
 * disabled : arènes à ne pas générer. L'espacement (rareté) et les biomes se règlent dans les données du mod
 * (worldgen/structure_set/arena_*.json et tags/worldgen/biome/has_structure/arena_*.json), modifiables par datapack.
 */
public final class ArenaSettings {

    private static volatile JsonObject gen = null;

    private ArenaSettings() {}

    public static synchronized void load() {
        JsonObject g = new JsonObject();
        Path f = FMLPaths.CONFIGDIR.get().resolve("wavesurvivor").resolve("arenas.json");
        if (Files.exists(f)) {
            try {
                JsonObject root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                if (root.has("generation") && root.get("generation").isJsonObject()) g = root.getAsJsonObject("generation");
            } catch (Exception e) {
                WaveSurvivorMod.LOGGER.error("[Arènes] {} illisible : {}", f.getFileName(), e.getMessage());
            }
        }
        gen = g;
        ArenaBlueprint.clearCache();
    }

    private static JsonObject g() {
        if (gen == null) load();
        return gen;
    }

    public static boolean enabled() { return HordeJson.bool(g(), "enabled", true); }

    public static int maxSlope() { return Math.max(0, HordeJson.num(g(), "maxSlope", 4)); }

    public static boolean randomRotation() { return HordeJson.bool(g(), "randomRotation", true); }

    public static boolean isDisabled(String arena) {
        Set<String> out = new HashSet<>();
        if (g().has("disabled") && g().get("disabled").isJsonArray()) for (JsonElement e : g().getAsJsonArray("disabled")) out.add(e.getAsString());
        return out.contains(arena);
    }
}
