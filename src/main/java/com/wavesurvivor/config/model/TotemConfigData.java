package com.wavesurvivor.config.model;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

/**
 * TotemConfig au format base44.
 * Utilisé par le skill totem_protection pour l'apparence des totems posés.
 * Phase 2b : baseHP + armorSet + handItems suffisent.
 * Phase 4 : implémentation complète des totems custom en tant que blocs.
 */
public class TotemConfigData {

    @SerializedName("totemName")
    public String totemName;

    @SerializedName("displayName")
    public String displayName;

    @SerializedName("baseHP")
    public double baseHP = 10;

    @SerializedName("headItem")
    public String headItem = "minecraft:player_head";

    @SerializedName("useCustomHead")
    public boolean useCustomHead;

    @SerializedName("skullOwner")
    public String skullOwner;

    /** Map slot → item id (chest, legs, feet). */
    @SerializedName("armorSet")
    public Map<String, String> armorSet;

    /** Map slot → item id (mainhand, offhand). */
    @SerializedName("handItems")
    public Map<String, String> handItems;

    @SerializedName("particles")
    public Map<String, String> particles;
}
