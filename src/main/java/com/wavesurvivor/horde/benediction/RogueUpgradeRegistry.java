package com.wavesurvivor.horde.benediction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registre des bénédictions rogue-like, rempli par {@link BlessingCatalog} à partir des DONNÉES (base du mod + couche du joueur ;
 * depuis la 1.8 plus rien n'est écrit en dur ici). Mêmes accès qu'avant : get / all / allIds / count.
 *
 * Raretés → couleurs : COMMON blanc, RARE bleu, EPIC violet clair, LEGENDARY jaune.
 */
public class RogueUpgradeRegistry {

    private static volatile Map<String, RogueUpgrade> upgrades = new LinkedHashMap<>();

    /** Remplace tout le contenu (rechargement du catalogue, ou catalogue reçu du serveur). */
    static void replaceAll(Map<String, RogueUpgrade> map) {
        upgrades = map;
    }

    private static Map<String, RogueUpgrade> map() {
        BlessingCatalog.ensureLoaded();
        if (!BlessingCatalog.isBuilt()) BlessingCatalog.buildNow();
        return upgrades;
    }

    public static RogueUpgrade get(String id) {
        return map().get(id);
    }

    public static Collection<RogueUpgrade> all() {
        return map().values();
    }

    public static List<String> allIds() {
        return new ArrayList<>(map().keySet());
    }

    public static int count() {
        return map().size();
    }
}
