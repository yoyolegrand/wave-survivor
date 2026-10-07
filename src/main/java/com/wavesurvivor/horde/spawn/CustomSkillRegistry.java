package com.wavesurvivor.horde.spawn;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.ModConfig;
import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.config.model.TotemConfigData;

import java.util.HashMap;
import java.util.Map;

/**
 * Registre des CustomSkill + TotemConfig indexés par nom.
 * Init au boot après ConfigLoader.
 */
public class CustomSkillRegistry {

    private static final Map<String, CustomSkillData> SKILLS = new HashMap<>();
    private static final Map<String, TotemConfigData> TOTEMS = new HashMap<>();
    private static boolean initialized = false;

    public static void init(ModConfig config) {
        SKILLS.clear();
        TOTEMS.clear();
        if (config != null) {
            if (config.customSkill != null) {
                for (CustomSkillData sk : config.customSkill) {
                    if (sk != null && sk.skillName != null && !sk.skillName.isBlank()) {
                        SKILLS.put(sk.skillName.toLowerCase(), sk);
                    }
                }
            }
            if (config.totemConfig != null) {
                for (TotemConfigData t : config.totemConfig) {
                    if (t != null && t.totemName != null && !t.totemName.isBlank()) {
                        TOTEMS.put(t.totemName.toLowerCase(), t);
                    }
                }
            }
        }
        initialized = true;
        WaveSurvivorMod.LOGGER.info("[CustomSkillRegistry] {} skills + {} totems enregistrés.", SKILLS.size(), TOTEMS.size());
        for (CustomSkillData sk : SKILLS.values()) {
            WaveSurvivorMod.LOGGER.info("  • {} (type={})", sk.skillName, sk.skillType);
        }
        for (TotemConfigData t : TOTEMS.values()) {
            WaveSurvivorMod.LOGGER.info("  • totem: {} (baseHP={})", t.totemName, t.baseHP);
        }
    }

    public static CustomSkillData getSkill(String name) {
        if (name == null || SKILLS.isEmpty()) return null;
        return SKILLS.get(name.toLowerCase());
    }

    public static TotemConfigData getTotem(String name) {
        if (name == null || TOTEMS.isEmpty()) return null;
        return TOTEMS.get(name.toLowerCase());
    }

    public static boolean isReady() {
        return initialized;
    }

    public static int skillCount() {
        return SKILLS.size();
    }
}
