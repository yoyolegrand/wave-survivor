package com.wavesurvivor.horde.roulette;

/**
 * Système de chances dynamiques par vague.
 * À partir de startWave, chaque rareté voit son poids ajusté par le modifier * (currentWave - startWave + 1).
 * Puis les chances sont re-normalisées à base 100.
 */
public record RouletteDynamicChances(
        int startWave,
        int commonMod,
        int uncommonMod,
        int rareMod,
        int epicMod,
        int legendaryMod
) {
    public int modifierFor(RouletteReward.Rarity r) {
        return switch (r) {
            case COMMON    -> commonMod;
            case UNCOMMON  -> uncommonMod;
            case RARE      -> rareMod;
            case EPIC      -> epicMod;
            case LEGENDARY -> legendaryMod;
        };
    }
}
