package com.wavesurvivor.horde.roulette;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Format de sauvegarde d'un roulette chest custom (créé via /ws roulettechest create).
 * Sérialisé dans config/wavesurvivor/custom_roulette_chests.json.
 *
 * Ces chests sont fusionnés avec ceux du config.json (base44) au chargement.
 * Ils ne sont JAMAIS écrasés par base44 puisque dans un fichier séparé.
 */
public class CustomChestData {

    /** Nom d'affichage du coffre (utilisé aussi comme chestKey slugifié). */
    @SerializedName("name")
    public String name;

    /** Preset de particules : "none", "flame", "bubble", "enchant", "portal", "soul_flame", "dust_glow", "crimson_spore". */
    @SerializedName("particles")
    public String particles = "none";

    /** Pattern d'émission : voir ParticlePatterns.ALL. Défaut "circle" (comportement classique). */
    @SerializedName("pattern")
    public String pattern = "circle";

    /** Couleur du coffre (runes, gemme) et de sa clé : "auto" (déduite du nom) ou un colorant Minecraft (red, cyan...). */
    @SerializedName("color")
    public String color = "auto";

    /** Matériau du corps de la clé : or, bronze, fer, cuivre, argent, netherite, os, obsidienne, rouille, or_rose. */
    @SerializedName("keyMaterial")
    public String keyMaterial = "or";

    /** Templates de message par tier. Variables : {player}, {item}, {qty}. */
    @SerializedName("tierMessages")
    public Map<String, String> tierMessages = new LinkedHashMap<>();

    /** Liste des rewards du coffre. */
    @SerializedName("rewards")
    public List<Reward> rewards = new ArrayList<>();

    public static class Reward {
        @SerializedName("item")
        public String item;              // "minecraft:diamond"

        @SerializedName("minQty")
        public int minQty = 1;

        @SerializedName("maxQty")
        public int maxQty = 1;

        @SerializedName("chance")
        public double chance = 10.0;     // en % (le total de tous les rewards doit être ≤ 100)

        @SerializedName("tier")
        public String tier = "COMMON";   // COMMON, UNCOMMON, RARE, EPIC, LEGENDARY

        /** Message custom pour cet item. null = utilise le template du tier. */
        @SerializedName("customMessage")
        public String customMessage;
    }

    /** Retourne les templates par défaut pour un nouveau chest. */
    public static Map<String, String> defaultTierMessages() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("COMMON",    "§7{player} a trouvé §f{qty}× {item}");
        m.put("UNCOMMON",  "§a{player} a trouvé §f{qty}× {item}");
        m.put("RARE",      "§9[RARE] §f{player} a trouvé §f{qty}× {item}");
        m.put("EPIC",      "§5[ÉPIQUE] §f{player} a trouvé §f{qty}× {item}");
        m.put("LEGENDARY", "§6[LÉGENDAIRE] §e{player} a trouvé §6{qty}× {item} §e!");
        return m;
    }
}
