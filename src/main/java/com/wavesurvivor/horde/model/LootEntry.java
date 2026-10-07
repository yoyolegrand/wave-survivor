package com.wavesurvivor.horde.model;

import com.google.gson.annotations.SerializedName;

public class LootEntry {
    public String item;

    @SerializedName("minQty")
    public int minQty = 1;

    @SerializedName("maxQty")
    public int maxQty = 1;

    public int chance = 100;
}
