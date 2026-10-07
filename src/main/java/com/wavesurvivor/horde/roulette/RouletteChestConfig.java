package com.wavesurvivor.horde.roulette;

import java.util.List;

/**
 * Configuration complète d'un type de roulette chest.
 * Le chestName est le nom EXACT du CustomName qu'un coffre doit avoir pour être matché.
 */
public record RouletteChestConfig(
        String configKey,               // identifiant registry ex "coffre_mysterieux"
        String chestType,               // "minecraft:chest" ou autre block ID
        String chestName,               // "Coffre Mystérieux" (match par CustomName)
        String keyName,                 // "Clé Mystérieuse" (nom de la clé, affiché dans messages)
        boolean consumeKey,
        List<String> allowedChests,     // autres chestNames qu'une clé de ce type peut ouvrir
        boolean showAnimation,
        int animationDurationSeconds,
        String particleIdle,            // particule spawn en continu (nom court, ex "happy_villager")
        String particleActive,          // particule spawn pendant l'animation
        RouletteSounds sounds,
        RouletteDynamicChances dynamicChances,
        List<RouletteReward> rewards,
        RouletteMessages messages
) {}
