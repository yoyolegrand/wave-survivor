package com.wavesurvivor.horde.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

public class HordeEntity {

    @SerializedName("entity_type")
    public String entityType = "minecraft:zombie";

    @SerializedName("custom_name")
    public String customName;

    @SerializedName("base_count")
    public int baseCount = 1;

    @SerializedName("count_increment")
    public int countIncrement = 0;

    @SerializedName("max_health")
    public double maxHealth = 20;

    /** Mode Kingdom : ce monstre sert de Gardien aux Portes (sinon, le plus robuste de la horde). */
    @SerializedName("kingdom_guardian")
    public boolean kingdomGuardian = false;

    /** Mode Kingdom : rôle de siège de ce monstre — "" (aucun), "ram" (Bélier), "sapper" (Sapeur), "climber" (Grimpeur). */
    @SerializedName("siege_role")
    public String siegeRole = "";

    /** Mode Kingdom : Portes d'où sort cette unité (« » = toutes, sinon numéros « 1,3 »). */
    @SerializedName("gates")
    public String gates = "";

    /** Taille de l'unité (1 = normale, 0,5 = moitié, 2 = double) — nécessite le mod Pehkui, ignorée sinon. */
    @SerializedName("scale")
    public double scale = 1.0;

    /** Frappe les bâtiments à portée (Monolithe, murs, tours) en plus de ses cibles habituelles. */
    @SerializedName("attack_buildings")
    public boolean attackBuildings = false;

    /** « Briseur de Monolithe » : ignore les joueurs et ne vise que le Monolithe (ex-rôle « Profanateur »). */
    @SerializedName("targets_altar")
    public boolean targetsAltar = false;

    /** Dégâts aux bâtiments par coup / par explosion (0 = son attaque normale ; 40 pour une explosion). */
    @SerializedName("building_damage")
    public double buildingDamage = 0;

    /** Compétences ajoutées à cette unité (noms des compétences de l'onglet Compétences), en plus de celles d'une entité custom. */
    @SerializedName("skills")
    public java.util.List<String> skills;

    /** Réglages propres à cette unité : nom de compétence → { paramètre : valeur }. */
    @SerializedName("skillOverrides")
    public java.util.Map<String, com.google.gson.JsonObject> skillOverrides;

    /** Vrai si cette unité peut sortir de la Porte n° {@code num} (num ≤ 0 = n'importe laquelle). */
    public boolean allowedAtGate(int num) {
        if (num <= 0 || gates == null || gates.isBlank()) return true;
        for (String s : gates.split(",")) {
            try { if (Integer.parseInt(s.trim()) == num) return true; } catch (NumberFormatException ignored) {}
        }
        return false;
    }

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

    @SerializedName("playerScaling")
    public PlayerScaling playerScaling;

    @SerializedName("loot_table")
    public List<LootEntry> lootTable;

    /** Première vague où ce mob apparaît (0 ou 1 = dès la vague 1). */
    @SerializedName("min_wave")
    public int minWave = 0;

    /** Dernière vague où ce mob apparaît (0 = jusqu'à la fin). */
    @SerializedName("max_wave")
    public int maxWave = 0;

    /** Entité custom : true = les stats de la horde remplacent celles de l'entité. */
    @SerializedName("override_stats")
    public boolean overrideStats = false;

    /** true = ne lâche jamais ses armes (main + main secondaire). */
    @SerializedName("no_weapon_drop")
    public boolean noWeaponDrop = false;

    /** true = ne lâche jamais son armure (tête, torse, jambes, pieds). */
    @SerializedName("no_armor_drop")
    public boolean noArmorDrop = false;

    /** Applique les interdictions de drop d'équipement (à appeler après avoir équipé le mob). */
    public void applyDropFlags(net.minecraft.world.entity.Mob mob) {
        if (mob == null) return;
        if (noWeaponDrop) {
            mob.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0f);
            mob.setDropChance(net.minecraft.world.entity.EquipmentSlot.OFFHAND, 0f);
        }
        if (noArmorDrop) {
            mob.setDropChance(net.minecraft.world.entity.EquipmentSlot.HEAD, 0f);
            mob.setDropChance(net.minecraft.world.entity.EquipmentSlot.CHEST, 0f);
            mob.setDropChance(net.minecraft.world.entity.EquipmentSlot.LEGS, 0f);
            mob.setDropChance(net.minecraft.world.entity.EquipmentSlot.FEET, 0f);
        }
    }

    public int getCountForWave(int wave, int playerCount) {
        if (minWave > 0 && wave < minWave) return 0;
        if (maxWave > 0 && wave > maxWave) return 0;
        int first = Math.max(1, minWave);
        int base = baseCount + countIncrement * Math.max(0, wave - first);
        if (playerScaling != null && playerScaling.countMultiplier > 0) {
            base = (int) Math.round(base * PlayerScaling.factor(playerScaling.countMultiplier, playerCount));
        }
        // Mutateur « Nuée » : 50 % de monstres en plus ; niveau de difficulté : nombre de monstres
        base = (int) Math.round(base * com.wavesurvivor.horde.mutator.MutatorEffects.countMultiplier()
                * com.wavesurvivor.horde.difficulty.HordeDifficulty.countMultiplier());
        return Math.max(0, base);
    }

    public double effectiveMaxHealth(int playerCount) {
        if (playerScaling == null || playerScaling.healthMultiplier <= 0) return maxHealth;
        return maxHealth * PlayerScaling.factor(playerScaling.healthMultiplier, playerCount);
    }

    public double effectiveAttackDamage(int playerCount) {
        if (playerScaling == null || playerScaling.damageMultiplier <= 0) return attackDamage;
        return attackDamage * PlayerScaling.factor(playerScaling.damageMultiplier, playerCount);
    }
}
