package com.wavesurvivor.horde.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

/**
 * Vague spéciale — remplace les hordeEntities normales quand tirée au sort.
 * Note : format différent d'HordeEntity (count fixe au lieu de base+increment).
 */
public class SpecialWave {

    @SerializedName("name")
    public String name = "Vague spéciale";

    /** Probabilité relative de cette vague (0-100). */
    @SerializedName("chance")
    public int chance = 50;

    @SerializedName("entities")
    public List<SpecialWaveEntity> entities;

    public static class SpecialWaveEntity {
        @SerializedName("entity_type")
        public String entityType = "minecraft:zombie";

        @SerializedName("custom_name")
        public String customName;

        /** Attention : count FIXE, pas base_count + count_increment. */
        @SerializedName("count")
        public int count = 1;

        /** Mode Kingdom : Portes d'où sort cette unité (« » = toutes, sinon numéros « 1,3 »). */
        @SerializedName("gates")
        public String gates = "";

        /** Taille de l'unité (1 = normale) — nécessite le mod Pehkui, ignorée sinon. */
        @SerializedName("scale")
        public double scale = 1.0;

        /** Frappe les bâtiments à portée (Monolithe, murs, tours). */
        @SerializedName("attack_buildings")
        public boolean attackBuildings = false;

        /** « Briseur de Monolithe » : ignore les joueurs et ne vise que le Monolithe. */
        @SerializedName("targets_altar")
        public boolean targetsAltar = false;

        /** Dégâts aux bâtiments par coup / par explosion (0 = son attaque normale ; 40 pour une explosion). */
        @SerializedName("building_damage")
        public double buildingDamage = 0;

        /** Compétences ajoutées à cette unité. */
        @SerializedName("skills")
        public java.util.List<String> skills;

        /** Réglages propres à cette unité : nom de compétence → { paramètre : valeur }. */
        @SerializedName("skillOverrides")
        public java.util.Map<String, com.google.gson.JsonObject> skillOverrides;

        @SerializedName("max_health")
        public double maxHealth = 20;

        @SerializedName("attack_damage")
        public double attackDamage = 2;

        @SerializedName("movement_speed")
        public double movementSpeed = 0.25;

        @SerializedName("follow_range")
        public double followRange = 40;

        @SerializedName("knockback_resistance")
        public double knockbackResistance = 0;

        @SerializedName("equipment")
        public Map<String, EquipmentEntry> equipment;

        @SerializedName("loot_table")
        public List<LootEntry> lootTable;

        /** Conversion vers HordeEntity pour réutiliser HordeSpawner. */
        public HordeEntity toHordeEntity() {
            HordeEntity h = new HordeEntity();
            h.entityType = this.entityType;
            h.customName = this.customName;
            h.gates = this.gates == null ? "" : this.gates;
            h.scale = this.scale <= 0 ? 1.0 : this.scale;
            h.attackBuildings = this.attackBuildings;
            h.targetsAltar = this.targetsAltar;
            h.buildingDamage = Math.max(0, this.buildingDamage);
            h.skills = this.skills;
            h.skillOverrides = this.skillOverrides;
            h.baseCount = this.count; // count fixe
            h.countIncrement = 0;
            h.maxHealth = this.maxHealth;
            h.attackDamage = this.attackDamage;
            h.movementSpeed = this.movementSpeed;
            h.followRange = this.followRange;
            h.knockbackResistance = this.knockbackResistance;
            h.equipment = this.equipment;
            h.lootTable = this.lootTable;
            return h;
        }
    }
}
