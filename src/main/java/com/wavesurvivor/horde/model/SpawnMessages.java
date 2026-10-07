package com.wavesurvivor.horde.model;

import com.google.gson.annotations.SerializedName;

/**
 * Messages custom affichés en chat au démarrage des vagues.
 * Placeholders supportés : {wave}, {total}, {name}.
 */
public class SpawnMessages {

    @SerializedName("normalWave")
    public String normalWave;

    @SerializedName("specialWave")
    public String specialWave;

    @SerializedName("useSound")
    public boolean useSound;

    @SerializedName("normalSound")
    public String normalSound;

    @SerializedName("specialSound")
    public String specialSound;
}
