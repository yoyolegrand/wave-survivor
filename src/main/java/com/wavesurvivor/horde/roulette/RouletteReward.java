package com.wavesurvivor.horde.roulette;

import java.util.List;

/** Une récompense dans le roulette chest. */
public record RouletteReward(
        String item,          // "minecraft:diamond"
        int minQty,
        int maxQty,
        double chance,        // pondération (base 100)
        Rarity rarity,
        String displayName
) {
    public enum Rarity { COMMON, UNCOMMON, RARE, EPIC, LEGENDARY }
}
