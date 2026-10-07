package com.wavesurvivor.horde.model;

import com.google.gson.annotations.SerializedName;

public class PlayerScaling {
    @SerializedName("countMultiplier")
    public double countMultiplier = 0;

    @SerializedName("healthMultiplier")
    public double healthMultiplier = 0;

    @SerializedName("damageMultiplier")
    public double damageMultiplier = 0;

    public static double factor(double multiplier, int playerCount) {
        if (playerCount <= 1) return 1.0;
        return 1.0 + multiplier * (playerCount - 1);
    }
}
