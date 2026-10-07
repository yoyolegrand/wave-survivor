package com.wavesurvivor.config;

import com.google.gson.annotations.SerializedName;
import com.wavesurvivor.config.model.CustomEntityData;
import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.config.model.HordeConfigMultiData;
import com.wavesurvivor.config.model.TotemConfigData;
import java.util.List;
import java.util.Map;

public class ModConfig {

    @SerializedName("HordeConfig")
    public List<Map<String, Object>> hordeConfig;

    @SerializedName("HordeConfigMulti")
    public List<HordeConfigMultiData> hordeConfigMulti;

    @SerializedName("CustomEntity")
    public List<CustomEntityData> customEntity;

    @SerializedName("CustomSkill")
    public List<CustomSkillData> customSkill;

    @SerializedName("CustomItem")
    public List<Map<String, Object>> customItem;

    @SerializedName("AltarConfig")
    public List<Map<String, Object>> altarConfig;

    @SerializedName("BenedictionConfig")
    public List<Map<String, Object>> benedictionConfig;

    @SerializedName("BloodPactConfig")
    public List<Map<String, Object>> bloodPactConfig;

    @SerializedName("RenaissanceConfig")
    public List<Map<String, Object>> renaissanceConfig;

    @SerializedName("RouletteChestConfig")
    public List<Map<String, Object>> rouletteChestConfig;

    @SerializedName("TotemConfig")
    public List<TotemConfigData> totemConfig;

    @SerializedName("Script")
    public List<Map<String, Object>> script;

    public HordeConfigMultiData findHorde(String name) {
        if (hordeConfigMulti == null || name == null) return null;
        for (HordeConfigMultiData h : hordeConfigMulti) {
            if (h.hordeName != null && h.hordeName.equalsIgnoreCase(name)) return h;
        }
        return null;
    }

    public CustomEntityData findCustomEntity(String name) {
        if (customEntity == null || name == null) return null;
        for (CustomEntityData ce : customEntity) {
            if (ce.entityName != null && ce.entityName.equalsIgnoreCase(name)) return ce;
        }
        return null;
    }

    public CustomSkillData findCustomSkill(String name) {
        if (customSkill == null || name == null) return null;
        for (CustomSkillData sk : customSkill) {
            if (sk.skillName != null && sk.skillName.equalsIgnoreCase(name)) return sk;
        }
        return null;
    }

    public TotemConfigData findTotemConfig(String name) {
        if (totemConfig == null || name == null) return null;
        for (TotemConfigData t : totemConfig) {
            if (t.totemName != null && t.totemName.equalsIgnoreCase(name)) return t;
        }
        return null;
    }
}
