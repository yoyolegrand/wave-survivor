package com.wavesurvivor.config.model;

import com.google.gson.annotations.SerializedName;
import com.wavesurvivor.horde.model.LootEntry;

import java.util.List;
import java.util.Map;

/**
 * CustomEntity au format base44.
 *
 * Phase 2a : stats + equipment + name + loot.
 * Phase 2b : skills (liste de skillName référençant CustomSkill).
 */
public class CustomEntityData {

    @SerializedName("entityName")
    public String entityName;

    @SerializedName("displayName")
    public String displayName;

    @SerializedName("baseEntityType")
    public String baseEntityType = "minecraft:zombie";

    @SerializedName("stats")
    public Stats stats;

    @SerializedName("equipment")
    public Map<String, String> equipment;

    @SerializedName("lootTable")
    public List<LootEntry> lootTable;

    @SerializedName("mountEntity")
    public MountEntity mountEntity;

    @SerializedName("isBoss")
    public boolean isBoss;

    @SerializedName("bossConfig")
    public Map<String, Object> bossConfig;

    /** Profanateur : ignore les joueurs et va frapper le monolithe (horde avec Défense du Monolithe). */
    @SerializedName("targetsAltar")
    public boolean targetsAltar;

    /** Lanceur de sorts : distance idéale à garder avec sa cible (0 = comportement normal, mêlée). */
    @SerializedName("keepDistance")
    public double keepDistance;

    /**
     * Liste des skillName référençant CustomSkill.
     * Ex: ["mortier", "dash", "tire", "totem"]
     * Le SkillManager instancie chaque skill au spawn de l'entité si elle est boss.
     */
    @SerializedName("skills")
    public List<String> skills;

    /**
     * Réglages PROPRES à cette entité, par compétence : nom de la compétence → { champ : valeur }.
     * Appliqués par-dessus la compétence (qui reste inchangée pour les autres entités).
     * Ex : { "Éruption" : { "eruptionCooldown" : 60, "eruptionDamage" : 8 } }
     */
    @SerializedName("skillOverrides")
    public Map<String, com.google.gson.JsonObject> skillOverrides;

    public static class Stats {
        @SerializedName("maxHealth")
        public double maxHealth = 20;

        @SerializedName("attackDamage")
        public double attackDamage = 2;

        @SerializedName("movementSpeed")
        public double movementSpeed = 0.25;

        @SerializedName("followRange")
        public double followRange = 40;

        @SerializedName("knockbackResistance")
        public double knockbackResistance = 0;
    }

    public static class MountEntity {
        @SerializedName("enabled")
        public boolean enabled;

        @SerializedName("entityType")
        public String entityType;

        @SerializedName("customName")
        public String customName;

        @SerializedName("stats")
        public Stats stats;

        @SerializedName("equipment")
        public Map<String, String> equipment;

        @SerializedName("lootTable")
        public List<LootEntry> lootTable;
    }
}
