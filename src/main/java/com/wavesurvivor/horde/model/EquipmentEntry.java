package com.wavesurvivor.horde.model;

import com.google.gson.annotations.SerializedName;

public class EquipmentEntry {
    public String item;

    @SerializedName("dropChance")
    public int dropChance = 10;
}
