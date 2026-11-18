package com.wavesurvivor.horde.rift;

/**
 * 2.1 — Fiches par défaut des gardiens de l'île au coffre (format d'un monstre de horde : onglet Monstres de l'éditeur).
 * Utilisées par le serveur quand la horde n'a pas encore ses propres fiches, et par l'éditeur pour les créer.
 */
public final class ChestIsletDefaults {

    private ChestIsletDefaults() {}

    /** Gardien du trésor (le « boss » de l'île, barre de vie jaune). */
    public static final String GUARDIAN = "{\"entity_type\":\"minecraft:ravager\",\"custom_name\":\"§6§lGardien du trésor\","
            + "\"max_health\":220,\"attack_damage\":12,\"movement_speed\":0.3,\"follow_range\":32,\"knockback_resistance\":1.0,"
            + "\"equipment\":{},\"loot_table\":["
            + "{\"item\":\"minecraft:emerald\",\"minQty\":4,\"maxQty\":8,\"chance\":100},"
            + "{\"item\":\"minecraft:amethyst_shard\",\"minQty\":3,\"maxQty\":6,\"chance\":100},"
            + "{\"item\":\"wavesurvivor:poussiere_faille\",\"minQty\":2,\"maxQty\":4,\"chance\":50}]}";

    /** Gardiens d'élite (un au hasard par place de garde). */
    public static final String ELITES = "["
            + "{\"entity_type\":\"minecraft:wither_skeleton\",\"custom_name\":\"§6Gardien des ruines\",\"max_health\":60,\"attack_damage\":8,"
            + "\"movement_speed\":0.27,\"follow_range\":32,\"equipment\":{\"mainhand\":{\"item\":\"minecraft:golden_sword\"},\"head\":{\"item\":\"minecraft:golden_helmet\"}},"
            + "\"no_weapon_drop\":true,\"no_armor_drop\":true,\"loot_table\":[{\"item\":\"minecraft:amethyst_shard\",\"minQty\":1,\"maxQty\":3,\"chance\":60}]},"
            + "{\"entity_type\":\"minecraft:vindicator\",\"custom_name\":\"§6Lame des ruines\",\"max_health\":50,\"attack_damage\":9,"
            + "\"movement_speed\":0.33,\"follow_range\":32,\"equipment\":{\"mainhand\":{\"item\":\"minecraft:golden_axe\"}},"
            + "\"no_weapon_drop\":true,\"no_armor_drop\":true,\"loot_table\":[{\"item\":\"minecraft:emerald\",\"minQty\":1,\"maxQty\":2,\"chance\":50}]},"
            + "{\"entity_type\":\"minecraft:stray\",\"custom_name\":\"§6Archer des ruines\",\"max_health\":40,\"attack_damage\":5,"
            + "\"movement_speed\":0.27,\"follow_range\":32,\"equipment\":{\"head\":{\"item\":\"minecraft:golden_helmet\"}},"
            + "\"no_weapon_drop\":true,\"no_armor_drop\":true,\"loot_table\":[{\"item\":\"minecraft:arrow\",\"minQty\":2,\"maxQty\":6,\"chance\":60}]}"
            + "]";
}
