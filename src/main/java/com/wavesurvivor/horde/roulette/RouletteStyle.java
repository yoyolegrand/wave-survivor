package com.wavesurvivor.horde.roulette;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * STYLE RÉSOLU D'UN COFFRE ROULETTE (1.9) : toutes les couleurs en RGB, les médaillons par face, la clé et les effets de particules, calculés à partir
 * d'un {@link CustomChestData} (les champs absents prennent leur valeur par défaut, donc un ancien coffre garde son aspect).
 * Sans dépendance au jeu : sérialisable en JSON pour être envoyé aux clients avec le bloc, qui s'en servent pour le rendu.
 */
public final class RouletteStyle {

    /** Faces portant un médaillon. */
    public static final List<String> FACES = List.of("front", "back", "left", "right", "top");

    /** Une couche de médaillon : symbole (identifiant de la bibliothèque ou « custom:nom »), taille et couleur résolue. */
    public static final class Layer {
        public String symbol = "dot";
        public boolean small = false;
        public int color;
        public Layer() {}
        public Layer(String symbol, boolean small, int color) { this.symbol = symbol; this.small = small; this.color = color; }
    }

    public int accent, wood, iron, gem;
    /** Couches du fond vers le dessus, par face ; une face absente n'a pas de médaillon. */
    public Map<String, List<Layer>> medallions = new LinkedHashMap<>();
    /** Symboles dessinés dans l'éditeur : nom → 10 lignes de 10 caractères « # » et « . ». */
    public Map<String, List<String>> customSymbols = new LinkedHashMap<>();
    public String keyHead = "ring", keyBit = "simple";
    /** Couleur de la clé : -1 = couleur de sa matière. */
    public int keyColor = -1;
    /** 2.0 — Taille du coffre posé (1 = normale). L'objet (inventaire) garde toujours la taille normale. */
    public double scale = 1.0;
    /** Effets de particules : « idle », « spin », « reveal » (absent = aucun effet propre). */
    public Map<String, CustomChestData.Effect> fx = new LinkedHashMap<>();

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public String toJson() { return GSON.toJson(this); }

    public static RouletteStyle fromJson(String json) {
        try {
            RouletteStyle s = GSON.fromJson(json, RouletteStyle.class);
            return s != null ? s : defaults(0x3DDC84);
        } catch (Exception e) {
            return defaults(0x3DDC84);
        }
    }

    /** Style par défaut (ancien coffre sans réglage) : chêne noir, fer, gemmes de la couleur d'accent, point au centre du cadran avant. */
    public static RouletteStyle defaults(int accent) {
        RouletteStyle s = new RouletteStyle();
        s.accent = accent;
        s.wood = RouletteColors.wood(null);
        s.iron = RouletteColors.iron(null);
        s.gem = accent;
        List<Layer> front = new ArrayList<>();
        front.add(new Layer("dot", false, accent));
        s.medallions.put("front", front);
        return s;
    }

    /**
     * Résout le style d'un coffre.
     * @param accent couleur d'accent déjà calculée (champ « accentColor », sinon l'ancienne « color », sinon déduite du nom)
     */
    public static RouletteStyle resolve(CustomChestData d, int accent) {
        RouletteStyle s = defaults(accent);
        if (d == null) return s;
        s.wood = RouletteColors.wood(d.woodColor);
        s.iron = RouletteColors.iron(d.ironColor);
        s.gem = colorOf(d.gemColor, accent, accent);
        if (d.medallions != null) {
            s.medallions.clear();
            for (String face : FACES) {
                List<CustomChestData.Medallion> in = d.medallions.get(face);
                if (in == null || in.isEmpty()) continue;
                List<Layer> out = new ArrayList<>();
                for (CustomChestData.Medallion m : in) {
                    if (m == null || m.symbol == null || m.symbol.isBlank()) continue;
                    out.add(new Layer(m.symbol.trim().toLowerCase(Locale.ROOT).startsWith("custom:") ? m.symbol.trim() : m.symbol.trim().toLowerCase(Locale.ROOT),
                            "small".equalsIgnoreCase(m.size), colorOf(m.color, accent, accent)));
                }
                if (!out.isEmpty()) s.medallions.put(face, out);
            }
        }
        if (d.customSymbols != null) s.customSymbols.putAll(d.customSymbols);
        if (d.keyHead != null && !d.keyHead.isBlank()) s.keyHead = d.keyHead.trim().toLowerCase(Locale.ROOT);
        if (d.keyBit != null && !d.keyBit.isBlank()) s.keyBit = d.keyBit.trim().toLowerCase(Locale.ROOT);
        s.keyColor = RouletteColors.isHex(d.keyColor) ? RouletteColors.parseHex(d.keyColor, -1) : -1;
        if (d.particles != null && d.particles.fx != null) s.fx.putAll(d.particles.fx);
        if (d.scale != null && d.scale > 0 && Double.isFinite(d.scale)) s.scale = Math.max(0.25, Math.min(4.0, d.scale));
        return s;
    }

    /** « accent » (ou vide) → couleur d'accent, « #RRGGBB » → cette couleur. */
    private static int colorOf(String v, int accent, int def) {
        if (v == null || v.isBlank() || v.equalsIgnoreCase("accent") || v.equalsIgnoreCase("auto")) return def;
        return RouletteColors.parseHex(v, def);
    }
}
