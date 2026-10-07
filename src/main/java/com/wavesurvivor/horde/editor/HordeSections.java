package com.wavesurvivor.horde.editor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashSet;
import java.util.Set;

/**
 * ÉDITION À PLUSIEURS : découpage d'une horde par ONGLET de l'éditeur (mêmes numéros que HordeEditorScreen).
 * Chaque onglet « possède » une partie du JSON ; merge() recopie seulement les parties des onglets demandés,
 * ce qui permet d'enregistrer un onglet sans écraser le travail fait en même temps dans un autre.
 *   Général : tout ce qui n'appartient pas aux autres onglets (nom, description, réglages, Royaume, Portes, Chemins…)
 *   Mobs / Spéciales / Boss / Pauses-Calme / Chaos : leurs listes et interrupteurs
 *   Autel : la config d'autel (fichier séparé, gérée à part) · Aperçu : rien (lecture seule)
 */
public final class HordeSections {

    private HordeSections() {}

    public static final int T_GENERAL = 0, T_MOBS = 1, T_SPECIAL = 2, T_BOSS = 3, T_MERCH = 4, T_CHAOS = 5, T_ALTAR = 6, T_PREVIEW = 7;
    public static final int TAB_COUNT = 8;
    /** Masque « tous les onglets » (enregistrement classique, en solo). */
    public static final int ALL = -1;

    public static int bit(int tab) { return 1 << tab; }

    public static boolean has(int mask, int tab) { return (mask & bit(tab)) != 0; }

    private static final String[] MOBS = {"hordeEntities"};
    private static final String[] SPECIAL = {"useSpecialWaves", "specialWaveChance", "specialWaves"};
    private static final String[] BOSS = {"useBossWaves", "bossWaves"};
    private static final String[] MERCH = {"useMerchants", "merchants", "merchantDebug", "useWavePauses", "wavePauses"};
    private static final String[] MERCH_KINGDOM = {"smallBreachSeconds", "smallBreachUnits", "calmBreachUnits", "calmObjectives"};
    private static final String[] CHAOS = {"chaosEnabled", "chaosMinInterval", "chaosMaxInterval", "chaosEvents"};

    private static final Set<String> NOT_GENERAL = new HashSet<>();
    private static final Set<String> NOT_GENERAL_KINGDOM = new HashSet<>(Set.of(MERCH_KINGDOM));

    static {
        for (String[] a : new String[][]{MOBS, SPECIAL, BOSS, MERCH, CHAOS}) NOT_GENERAL.addAll(Set.of(a));
    }

    private static String[] keysOf(int tab) {
        return switch (tab) {
            case T_MOBS -> MOBS;
            case T_SPECIAL -> SPECIAL;
            case T_BOSS -> BOSS;
            case T_MERCH -> MERCH;
            case T_CHAOS -> CHAOS;
            default -> new String[0];
        };
    }

    /** Recopie dans {@code target} les parties de {@code source} appartenant aux onglets du masque. */
    public static void merge(JsonObject target, JsonObject source, int mask) {
        if (target == null || source == null) return;
        JsonObject tcd = obj(target, "configData"), scd = obj(source, "configData");
        if (tcd == null || scd == null) return;

        if (has(mask, T_GENERAL)) {
            // Champs de premier niveau (nom, description, récompenses…)
            Set<String> top = union(target, source);
            top.remove("configData");
            for (String k : top) copy(target, source, k);
            // configData hors parties des autres onglets
            for (String k : union(tcd, scd)) {
                if (NOT_GENERAL.contains(k) || k.equals("kingdom")) continue;
                copy(tcd, scd, k);
            }
            // Royaume : tout sauf les brèches du Calme (onglet Calme)
            JsonObject tk = obj(tcd, "kingdom"), sk = obj(scd, "kingdom");
            if (sk != null || tk != null) {
                if (tk == null) { tk = new JsonObject(); tcd.add("kingdom", tk); }
                if (sk == null) sk = new JsonObject();
                for (String k : union(tk, sk)) if (!NOT_GENERAL_KINGDOM.contains(k)) copy(tk, sk, k);
            }
        }
        for (int t = 1; t < TAB_COUNT; t++) {
            if (!has(mask, t)) continue;
            for (String k : keysOf(t)) copy(tcd, scd, k);
            if (t == T_MERCH) {
                JsonObject tk = obj(tcd, "kingdom"), sk = obj(scd, "kingdom");
                if (sk == null && tk == null) continue;
                if (tk == null) { tk = new JsonObject(); tcd.add("kingdom", tk); }
                if (sk == null) sk = new JsonObject();
                for (String k : MERCH_KINGDOM) copy(tk, sk, k);
            }
        }
    }

    private static JsonObject obj(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : null;
    }

    private static Set<String> union(JsonObject a, JsonObject b) {
        Set<String> s = new HashSet<>(a.keySet());
        s.addAll(b.keySet());
        return s;
    }

    /** target[k] = copie de source[k] (ou supprimé si absent de source). */
    private static void copy(JsonObject target, JsonObject source, String k) {
        JsonElement v = source.get(k);
        if (v == null) target.remove(k);
        else target.add(k, v.deepCopy());
    }
}
