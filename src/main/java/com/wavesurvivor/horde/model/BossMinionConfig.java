package com.wavesurvivor.horde.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

/**
 * Config des minions qu'un boss invoque.
 * triggerType : "interval" (toutes les X sec) | "health" (à X% HP restant)
 */
public class BossMinionConfig {

    @SerializedName("triggerType")
    public String triggerType = "interval";

    /** Pour "interval" = secondes entre chaque summon. Pour "health" = seuil en % HP (0-100). */
    @SerializedName("triggerValue")
    public double triggerValue = 10;

    @SerializedName("minionType")
    public String minionType = "minecraft:zombie";

    @SerializedName("minionCount")
    public int minionCount = 3;

    @SerializedName("minionStats")
    public MinionStats minionStats;

    @SerializedName("minionEquipment")
    public Map<String, EquipmentEntry> minionEquipment;

    /** Butin des sbires invoqués (même format que le butin des monstres). */
    @SerializedName("minionLoot")
    public List<LootEntry> minionLoot;

    public static class MinionStats {
        @SerializedName("maxHealth")
        public double maxHealth = 20;
        @SerializedName("attackDamage")
        public double attackDamage = 3;
        @SerializedName("movementSpeed")
        public double movementSpeed = 0.25;
        /** Taille des sbires (1 = normale) — nécessite le mod Pehkui, ignorée sinon. */
        @SerializedName("scale")
        public double scale = 1.0;
    }
}
