package com.wavesurvivor.config.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Configuration d'un marchand qui spawn au start d'une horde.
 *
 * Position : soit absolue (spawnPos), soit relative au spawnpoint de la horde (offset).
 * Priorité à offset si les deux sont renseignés.
 *
 * Le marchand est spawné avec :
 *   - CustomName "§6§l[MARCHAND] §e<name>"
 *   - setInvulnerable(true), setNoAi(true), setPersistenceRequired()
 *   - Profession appliquée (pour minecraft:villager)
 *   - Trades custom remplacent les trades vanilla
 */
public class MerchantData {

    @SerializedName("name")
    public String name;

    /** Type d'entité (défaut "minecraft:villager"). */
    @SerializedName("entityType")
    public String entityType = "minecraft:villager";

    /** Profession du villageois (leatherworker, librarian, cleric, farmer, weaponsmith, etc.). */
    @SerializedName("profession")
    public String profession;

    /** Position ABSOLUE (utilisée si offset absent). Format {x, y, z}. */
    @SerializedName("spawnPos")
    public HordeConfigMultiData.Coords spawnPos;

    /** Position RELATIVE au spawnpoint de la horde (prioritaire sur spawnPos si présente). */
    @SerializedName("offset")
    public Offset offset;

    /** Chance [0..1] que le marchand spawn effectivement à chaque horde. Défaut 1.0. */
    @SerializedName("spawnChance")
    public double spawnChance = 1.0;

    /** Liste des trades proposés (remplace les trades vanilla). */
    @SerializedName("trades")
    public List<TradeData> trades;

    /**
     * Durée en SECONDES avant despawn auto (avec particules poof + son villager.no).
     * 0 ou négatif = marchand permanent (jusqu'à fin de horde).
     * Utilisé pour les marchands éphémères style "Marchand Mystère" (chaos events).
     */
    @SerializedName("duration")
    public int duration = 0;

    public static class Offset {
        public int x;
        public int y;
        public int z;
    }
}
