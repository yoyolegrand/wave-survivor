package com.wavesurvivor.horde.renaissance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.wavesurvivor.WaveSurvivorMod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Raretés de sacrifice réglées EN JEU par les ops (bouton "Configuration" de l'autel de Renaissance).
 * Prioritaires sur les tags / customItems de la config base44, et jamais écrasées par un export.
 * Fichier : config/wavesurvivor/renaissance_rarities.json → { "minecraft:diamond": "Légendaire", "minecraft:dirt": "__none__" }
 */
public class RenaissanceRarityStore {

    /** Valeur spéciale : l'item n'est PAS sacrifiable, même si un tag le rendrait sacrifiable. */
    public static final String EXCLUDED = "__none__";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String FILE_NAME = "renaissance_rarities.json";
    private static final Map<String, String> OVERRIDES = new TreeMap<>();
    private static boolean loaded = false;

    /** Valeur du fichier du joueur : annule une rareté de la base (retour au comportement par défaut). */
    private static final String RESET = "__reset__";

    /** Raretés de la base livrée avec le mod. */
    private static Map<String, String> baseRarities() {
        Map<String, String> out = new TreeMap<>();
        for (Map.Entry<String, com.google.gson.JsonElement> e : com.wavesurvivor.config.ConfigLayers.builtin().rarities.entrySet()) {
            out.put(e.getKey(), e.getValue().getAsString());
        }
        return out;
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        OVERRIDES.clear();
        OVERRIDES.putAll(baseRarities());
        Path file = filePath();
        if (!Files.exists(file)) return;
        try {
            Map<String, String> m = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
                    new TypeToken<Map<String, String>>() {}.getType());
            if (m != null) for (Map.Entry<String, String> e : m.entrySet()) {
                if (RESET.equals(e.getValue())) OVERRIDES.remove(e.getKey()); else OVERRIDES.put(e.getKey(), e.getValue());
            }
            WaveSurvivorMod.LOGGER.info("[Renaissance] {} rareté(s) réglée(s) (base + les vôtres).", OVERRIDES.size());
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[Renaissance] Erreur lecture {} : {}", FILE_NAME, e.getMessage());
        }
    }

    public static void reload() {
        loaded = false;
        ensureLoaded();
    }

    /** @param rarity nom de rareté, EXCLUDED, ou null/"" pour revenir au comportement par défaut. */
    public static void set(String itemId, String rarity) {
        ensureLoaded();
        if (itemId == null || itemId.isBlank()) return;
        if (rarity == null || rarity.isBlank()) OVERRIDES.remove(itemId);
        else OVERRIDES.put(itemId, rarity);
        save();
    }

    /** Copie des réglages (injectée dans la config envoyée aux clients). */
    public static Map<String, String> snapshot() {
        ensureLoaded();
        return new LinkedHashMap<>(OVERRIDES);
    }

    public static void applyTo(RenaissanceConfigData data) {
        data.rarityOverrides = snapshot();
    }

    private static void save() {
        try {
            Path dir = FMLPaths.CONFIGDIR.get().resolve("wavesurvivor");
            if (!Files.exists(dir)) Files.createDirectories(dir);
            // Uniquement ce qui diffère de la base ; une rareté de base retirée est notée « __reset__ »
            Map<String, String> base = baseRarities();
            Map<String, String> mine = new TreeMap<>();
            for (Map.Entry<String, String> e : OVERRIDES.entrySet()) if (!e.getValue().equals(base.get(e.getKey()))) mine.put(e.getKey(), e.getValue());
            for (String k : base.keySet()) if (!OVERRIDES.containsKey(k)) mine.put(k, RESET);
            Files.writeString(filePath(), GSON.toJson(mine), StandardCharsets.UTF_8);
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[Renaissance] Erreur écriture {} : {}", FILE_NAME, e.getMessage());
        }
    }

    private static Path filePath() {
        return FMLPaths.CONFIGDIR.get().resolve("wavesurvivor").resolve(FILE_NAME);
    }
}
