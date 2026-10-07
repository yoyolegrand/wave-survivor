package com.wavesurvivor.horde.roulette;

/** Sons pour un roulette chest. Chaque champ = un sound ID vanilla (peut être null). */
public record RouletteSounds(
        String activation,
        String opening,
        String rolling,
        String rewardCommon,
        String rewardUncommon,
        String rewardRare,
        String rewardEpic,
        String rewardLegendary
) {
    /** Renvoie le son de reward correspondant à la rareté (ou null). */
    public String forRarity(RouletteReward.Rarity r) {
        return switch (r) {
            case LEGENDARY -> rewardLegendary;
            case EPIC      -> rewardEpic;
            case RARE      -> rewardRare;
            case UNCOMMON  -> rewardUncommon;
            case COMMON    -> rewardCommon;
        };
    }
}
