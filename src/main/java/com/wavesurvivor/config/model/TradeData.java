package com.wavesurvivor.config.model;

import com.google.gson.annotations.SerializedName;

/**
 * Un trade proposé par un marchand.
 * Format compatible avec le JS d'origine :
 *   { input1, input1Count, output, outputCount, maxUses }
 * Support optionnel d'un second input (input2, input2Count) pour trades à 2 items.
 *
 * Le champ output supporte les IDs spéciaux "roulettechest:key_*" — dans ce cas,
 * MerchantSpawner convertit en name_tag avec NBT rouletteKey/rouletteChestName.
 */
public class TradeData {

    @SerializedName("input1")
    public String input1;

    @SerializedName("input1Count")
    public int input1Count = 1;

    /** Optionnel : deuxième item d'input pour trades à 2 items. */
    @SerializedName("input2")
    public String input2;

    @SerializedName("input2Count")
    public int input2Count = 0;

    @SerializedName("output")
    public String output;

    @SerializedName("outputCount")
    public int outputCount = 1;

    /** Nombre max de fois que ce trade peut être utilisé avant de se vider. Défaut 12. */
    @SerializedName("maxUses")
    public int maxUses = 12;
}
