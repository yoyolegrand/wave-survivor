package com.wavesurvivor.horde.model;

import com.google.gson.annotations.SerializedName;

public class MultiSpawnEntry {
    public int x;
    public int y;
    public int z;

    @SerializedName("percentage")
    public int percentage = 100;
}
