package com.wavesurvivor.horde.roulette;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Préréglages de couleur des coffres roulette (1.9) : bois et ferrures, par identifiant ; toute couleur libre s'écrit « #RRGGBB ».
 * Les textures du coffre sont en niveaux de gris : ces couleurs sont multipliées dessus par le rendu.
 */
public final class RouletteColors {

    /** Bois : identifiant → RGB. Le premier est la valeur par défaut. */
    public static final Map<String, Integer> WOOD = new LinkedHashMap<>();
    /** Ferrures : identifiant → RGB. Le premier est la valeur par défaut. */
    public static final Map<String, Integer> IRON = new LinkedHashMap<>();

    static {
        WOOD.put("dark_oak", 0x462E1A);
        WOOD.put("spruce", 0x745432);
        WOOD.put("birch", 0xC8B47C);
        WOOD.put("acacia", 0xAC5C34);
        WOOD.put("cherry", 0xE2B0AA);
        WOOD.put("mangrove", 0x783630);
        WOOD.put("crimson", 0x6C324A);
        WOOD.put("warped", 0x2E6E68);
        WOOD.put("ebony", 0x2C2428);
        IRON.put("iron", 0xC4C6CC);
        IRON.put("gold", 0xFACE50);
        IRON.put("copper", 0xDA8052);
        IRON.put("oxidized_copper", 0x54A688);
        IRON.put("netherite", 0x544E54);
        IRON.put("amethyst", 0xA060CC);
        IRON.put("quartz", 0xEEE8E0);
        IRON.put("obsidian", 0x362850);
    }

    private RouletteColors() {}

    /** « #RRGGBB » (ou « RRGGBB ») → RGB ; {@code def} si le texte n'est pas une couleur. */
    public static int parseHex(String s, int def) {
        if (s == null) return def;
        String t = s.trim();
        if (t.startsWith("#")) t = t.substring(1);
        if (t.length() != 6) return def;
        try {
            return Integer.parseInt(t, 16);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static boolean isHex(String s) {
        return parseHex(s, -1) != -1;
    }

    /** Bois : identifiant de préréglage ou couleur libre ; absent / inconnu = chêne noir. */
    public static int wood(String v) {
        return pick(WOOD, v);
    }

    /** Ferrures : identifiant de préréglage ou couleur libre ; absent / inconnu = fer. */
    public static int iron(String v) {
        return pick(IRON, v);
    }

    private static int pick(Map<String, Integer> presets, String v) {
        int def = presets.values().iterator().next();
        if (v == null || v.isBlank()) return def;
        Integer p = presets.get(v.trim().toLowerCase(Locale.ROOT));
        return p != null ? p : parseHex(v, def);
    }
}
