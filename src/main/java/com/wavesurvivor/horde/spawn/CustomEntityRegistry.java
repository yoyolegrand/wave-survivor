package com.wavesurvivor.horde.spawn;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.ModConfig;
import com.wavesurvivor.config.model.CustomEntityData;

import java.util.HashMap;
import java.util.Map;

/**
 * Registre des CustomEntity indexé par nom (case-insensitive).
 * Init au démarrage du mod après le chargement de la config.
 *
 * Un nom est considéré comme CustomEntity si :
 *  - il existe une CustomEntityData avec ce entityName (case-insensitive)
 *  - OU l'id ne contient pas de ':' (les entities vanilla / modées sont
 *    toujours "namespace:name")
 */
public class CustomEntityRegistry {

    private static final Map<String, CustomEntityData> BY_NAME = new HashMap<>();
    private static boolean initialized = false;

    public static void init(ModConfig config) {
        BY_NAME.clear();
        if (config != null && config.customEntity != null) {
            for (CustomEntityData ce : config.customEntity) {
                if (ce != null && ce.entityName != null && !ce.entityName.isBlank()) {
                    BY_NAME.put(ce.entityName.toLowerCase(), ce);
                }
            }
        }
        initialized = true;
        WaveSurvivorMod.LOGGER.info("[CustomEntityRegistry] {} entités enregistrées.", BY_NAME.size());
        for (String key : BY_NAME.keySet()) {
            CustomEntityData ce = BY_NAME.get(key);
            int nSkills = ce.skills != null ? ce.skills.size() : -1;
            WaveSurvivorMod.LOGGER.info("  • {} → base={} (isBoss={}, skills={})",
                    key, ce.baseEntityType, ce.isBoss,
                    nSkills < 0 ? "NULL" : (nSkills == 0 ? "[]" : ce.skills.toString()));
        }
    }

    /** Retourne la CustomEntity portant ce nom, ou null. */
    public static CustomEntityData get(String name) {
        if (name == null || BY_NAME.isEmpty()) return null;
        return BY_NAME.get(name.toLowerCase());
    }

    /**
     * True si le nom référence une CustomEntity connue.
     * Ne vérifie PAS que l'id est valide en vanilla — juste la présence dans le registre.
     */
    public static boolean isCustom(String name) {
        return name != null && !name.isBlank() && BY_NAME.containsKey(name.toLowerCase());
    }

    /**
     * Heuristique : "nom simple sans ':' " → probablement une référence CustomEntity
     * même si on ne l'a pas dans le registre (permet d'afficher un warning clair).
     */
    public static boolean looksLikeCustomRef(String name) {
        return name != null && !name.isBlank() && !name.contains(":");
    }

    public static boolean isReady() {
        return initialized;
    }

    public static int size() {
        return BY_NAME.size();
    }
}
