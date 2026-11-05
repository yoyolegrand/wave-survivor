package com.wavesurvivor.horde.roulette;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Format de sauvegarde d'un roulette chest custom (créé via /ws roulettechest create).
 * Sérialisé dans config/wavesurvivor/custom_roulette_chests.json.
 *
 * Ces chests sont fusionnés avec ceux du config.json (base44) au chargement.
 * Ils ne sont JAMAIS écrasés par base44 puisque dans un fichier séparé.
 */
public class CustomChestData {

    /** Nom d'affichage du coffre (utilisé aussi comme chestKey slugifié). */
    @SerializedName("name")
    public String name;

    /**
     * Particules : soit l'ancien texte (preset « none », « flame », « bubble »…, lu tel quel), soit (1.9) un objet
     * { "idle": Effet, "spin": Effet, "reveal": Effet } décrivant les trois moments. Voir {@link Particles}.
     */
    @SerializedName("particles")
    @JsonAdapter(Particles.Adapter.class)
    public Particles particles = Particles.legacy("none");

    /** Pattern d'émission : voir ParticlePatterns.ALL. Défaut "circle" (comportement classique). */
    @SerializedName("pattern")
    public String pattern = "circle";

    /** Couleur du coffre (runes, gemme) et de sa clé : "auto" (déduite du nom) ou un colorant Minecraft (red, cyan...). */
    @SerializedName("color")
    public String color = "auto";

    // ─── Nouveaux réglages (1.9), tous optionnels : absents = aspect d'avant ───

    /** Couleur d'accent (cadran, liseré, runes) : « #RRGGBB » ou « auto » (alors l'ancienne « color » puis le nom du coffre). */
    @SerializedName("accentColor")
    public String accentColor;

    /** Bois : identifiant de préréglage (dark_oak, spruce, birch, acacia, cherry, mangrove, crimson, warped, ebony) ou « #RRGGBB ». Défaut : dark_oak. */
    @SerializedName("woodColor")
    public String woodColor;

    /** Ferrures : préréglage (iron, gold, copper, oxidized_copper, netherite, amethyst, quartz, obsidian) ou « #RRGGBB ». Défaut : iron. */
    @SerializedName("ironColor")
    public String ironColor;

    /** Gemmes : « accent » (défaut) ou « #RRGGBB ». */
    @SerializedName("gemColor")
    public String gemColor;

    /** Médaillons par face (front, back, left, right, top) : couches du fond vers le dessus. Absent : un point sur la face avant. */
    @SerializedName("medallions")
    public Map<String, List<Medallion>> medallions;

    /** Symboles dessinés dans l'éditeur : nom → 10 lignes de 10 caractères « # » (plein) et « . » (vide). */
    @SerializedName("customSymbols")
    public Map<String, List<String>> customSymbols;

    /** Forme de la clé : tête (ring, clover, crown, skull, heart, star, diamond, winged) et panneton (simple, double, stairs, hook) ; couleur « material » ou « #RRGGBB ». */
    @SerializedName("keyHead")
    public String keyHead;
    @SerializedName("keyBit")
    public String keyBit;
    @SerializedName("keyColor")
    public String keyColor;

    /** Une couche de médaillon. */
    public static class Medallion {
        /** Identifiant de la bibliothèque (dot, star, skull…) ou « custom:<nom> ». */
        @SerializedName("symbol") public String symbol = "dot";
        /** « large » (fond) ou « small » (couche du dessus). */
        @SerializedName("size") public String size = "large";
        /** « accent » ou « #RRGGBB ». */
        @SerializedName("color") public String color = "accent";
        public Medallion() {}
        public Medallion(String symbol, String size, String color) { this.symbol = symbol; this.size = size; this.color = color; }
    }

    /** Un effet de particules : type A, type B optionnel (émis en alternance), forme d'émission et réglages. */
    public static class Effect {
        @SerializedName("a") public String a;
        @SerializedName("b") public String b;
        @SerializedName("pattern") public String pattern = "circle";
        @SerializedName("density") public int density = 5;
        @SerializedName("radius") public double radius = 1.0;
        @SerializedName("height") public double height = 1.0;
        @SerializedName("speed") public int speed = 5;
        @SerializedName("color") public String color;
        @SerializedName("color2") public String color2;
        @SerializedName("size") public double size = 1.0;
    }

    /**
     * Particules d'un coffre : l'ancien préréglage (texte) ou les effets des trois moments. Lu et écrit par {@link Adapter} :
     * un coffre sans effets s'écrit comme avant (un simple texte), donc les anciens fichiers ne changent pas.
     */
    public static class Particles {
        /** Ancien préréglage : « none », « flame »… */
        public String legacy = "none";
        /** Effets 1.9 : « idle », « spin », « reveal » ; vide pour un ancien coffre. */
        public Map<String, Effect> fx = new LinkedHashMap<>();

        public static Particles legacy(String preset) {
            Particles p = new Particles();
            p.legacy = preset == null || preset.isBlank() ? "none" : preset;
            return p;
        }

        public boolean hasEffects() { return fx != null && !fx.isEmpty(); }

        public static class Adapter implements JsonSerializer<Particles>, JsonDeserializer<Particles> {
            @Override
            public JsonElement serialize(Particles p, java.lang.reflect.Type t, JsonSerializationContext ctx) {
                if (p == null || !p.hasEffects()) return new com.google.gson.JsonPrimitive(p == null ? "none" : p.legacy);
                JsonObject o = new JsonObject();
                for (Map.Entry<String, Effect> e : p.fx.entrySet()) o.add(e.getKey(), ctx.serialize(e.getValue(), Effect.class));
                return o;
            }

            @Override
            public Particles deserialize(JsonElement j, java.lang.reflect.Type t, JsonDeserializationContext ctx) throws JsonParseException {
                if (j.isJsonPrimitive()) return legacy(j.getAsString());
                Particles p = new Particles();
                if (j.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> e : j.getAsJsonObject().entrySet()) {
                        if (e.getValue().isJsonObject()) p.fx.put(e.getKey(), ctx.deserialize(e.getValue(), Effect.class));
                    }
                }
                return p;
            }
        }
    }

    /** Pattern d'émission au repos : celui de l'effet « idle » s'il existe, sinon l'ancien champ « pattern ». */
    public String idlePattern() {
        Effect e = particles != null && particles.fx != null ? particles.fx.get("idle") : null;
        return e != null && e.pattern != null && !e.pattern.isBlank() ? e.pattern : pattern;
    }

    /** Reprend les réglages d'apparence 1.9 d'un coffre existant (l'éditeur actuel ne les modifie pas encore : il ne doit pas les perdre). */
    public void copyStyleFrom(CustomChestData o) {
        if (o == null) return;
        accentColor = o.accentColor;
        woodColor = o.woodColor;
        ironColor = o.ironColor;
        gemColor = o.gemColor;
        medallions = o.medallions;
        customSymbols = o.customSymbols;
        keyHead = o.keyHead;
        keyBit = o.keyBit;
        keyColor = o.keyColor;
        if (o.particles != null && o.particles.hasEffects()) particles = o.particles;
    }

    /** Matériau du corps de la clé : or, bronze, fer, cuivre, argent, netherite, os, obsidienne, rouille, or_rose. */
    @SerializedName("keyMaterial")
    public String keyMaterial = "or";

    /** Templates de message par tier. Variables : {player}, {item}, {qty}. */
    @SerializedName("tierMessages")
    public Map<String, String> tierMessages = new LinkedHashMap<>();

    /** Liste des rewards du coffre. */
    @SerializedName("rewards")
    public List<Reward> rewards = new ArrayList<>();

    public static class Reward {
        @SerializedName("item")
        public String item;              // "minecraft:diamond"

        @SerializedName("minQty")
        public int minQty = 1;

        @SerializedName("maxQty")
        public int maxQty = 1;

        @SerializedName("chance")
        public double chance = 10.0;     // en % (le total de tous les rewards doit être ≤ 100)

        @SerializedName("tier")
        public String tier = "COMMON";   // COMMON, UNCOMMON, RARE, EPIC, LEGENDARY

        /** Message custom pour cet item. null = utilise le template du tier. */
        @SerializedName("customMessage")
        public String customMessage;
    }

    /** Retourne les templates par défaut pour un nouveau chest. */
    public static Map<String, String> defaultTierMessages() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("COMMON",    "§7{player} a trouvé §f{qty}× {item}");
        m.put("UNCOMMON",  "§a{player} a trouvé §f{qty}× {item}");
        m.put("RARE",      "§9[RARE] §f{player} a trouvé §f{qty}× {item}");
        m.put("EPIC",      "§5[ÉPIQUE] §f{player} a trouvé §f{qty}× {item}");
        m.put("LEGENDARY", "§6[LÉGENDAIRE] §e{player} a trouvé §6{qty}× {item} §e!");
        return m;
    }
}
