package com.wavesurvivor.shrine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.Patch;
import com.wavesurvivor.horde.editor.HordeJson;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AMÉLIORATIONS DE LA FORGE DU SANCTUAIRE (12 bénédictions « Consacré », 12 corruptions « Profané »), décrites par des DONNÉES :
 * defaults/wavesurvivor/shrine_upgrades.json (base du mod) + config/wavesurvivor/shrine_upgrades.json (couche du joueur, seulement les champs
 * à changer). Rechargé par /ws reload.
 */
public final class ShrineUpgrades {

    /** Un effet : clé, valeur à chaque niveau (I, II, III) et malus ou non. */
    public record Fx(String key, double[] tiers, boolean bad) {
        public double at(int tier) { return tiers.length == 0 ? 0 : tiers[Math.max(0, Math.min(tiers.length - 1, tier - 1))]; }
    }

    /** Une amélioration : identifiant, camp (consecrated / profaned), types d'équipement acceptés, effets. */
    public record Def(String id, String align, Set<String> slots, List<Fx> effects) {}

    private static volatile JsonObject root = null;
    private static volatile Map<String, Def> defs = new LinkedHashMap<>();

    private ShrineUpgrades() {}

    private static JsonObject base() {
        try (InputStream in = ShrineUpgrades.class.getResourceAsStream("/defaults/wavesurvivor/shrine_upgrades.json")) {
            if (in != null) return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[Sanctuaire] Lecture des améliorations impossible : {}", e.getMessage());
        }
        return new JsonObject();
    }

    public static synchronized void load() {
        JsonObject r = base();
        Path f = FMLPaths.CONFIGDIR.get().resolve("wavesurvivor").resolve("shrine_upgrades.json");
        if (Files.exists(f)) {
            try {
                r = Patch.apply(r, JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (Exception e) {
                WaveSurvivorMod.LOGGER.error("[Sanctuaire] {} illisible : {}", f.getFileName(), e.getMessage());
            }
        }
        Map<String, Def> map = new LinkedHashMap<>();
        if (r.has("upgrades") && r.get("upgrades").isJsonArray()) {
            for (JsonElement e : r.getAsJsonArray("upgrades")) {
                if (!e.isJsonObject()) continue;
                JsonObject o = e.getAsJsonObject();
                String id = HordeJson.str(o, "id", "");
                String align = HordeJson.str(o, "align", "");
                if (id.isBlank() || (!align.equals("consecrated") && !align.equals("profaned"))) continue;
                Set<String> slots = new HashSet<>();
                if (o.has("slots") && o.get("slots").isJsonArray()) for (JsonElement s : o.getAsJsonArray("slots")) slots.add(s.getAsString());
                List<Fx> fx = new ArrayList<>();
                if (o.has("effects") && o.get("effects").isJsonArray()) for (JsonElement fe : o.getAsJsonArray("effects")) {
                    if (!fe.isJsonObject()) continue;
                    JsonObject fo = fe.getAsJsonObject();
                    JsonArray t = fo.has("tiers") && fo.get("tiers").isJsonArray() ? fo.getAsJsonArray("tiers") : new JsonArray();
                    double[] tiers = new double[t.size()];
                    for (int i = 0; i < tiers.length; i++) tiers[i] = t.get(i).getAsDouble();
                    fx.add(new Fx(HordeJson.str(fo, "key", ""), tiers, HordeJson.bool(fo, "bad", false)));
                }
                if (!slots.isEmpty() && !fx.isEmpty()) map.put(id, new Def(id, align, slots, fx));
            }
        }
        defs = map;
        root = r;
        WaveSurvivorMod.LOGGER.info("[Sanctuaire] {} améliorations chargées.", map.size());
    }

    private static JsonObject cfg() {
        if (root == null) load();
        return root;
    }

    public static Def get(String id) {
        cfg();
        return defs.get(id);
    }

    public static Collection<Def> all() {
        cfg();
        return defs.values();
    }

    public static int maxSlots() { return Math.max(1, HordeJson.num(cfg(), "maxSlots", 3)); }

    /** Coût (en fragments) de l'amélioration qui occupera l'emplacement n (0 = le premier). */
    public static int slotCost(int n) {
        JsonObject c = cfg();
        if (c.has("slotCosts") && c.get("slotCosts").isJsonArray() && c.getAsJsonArray("slotCosts").size() > 0) {
            JsonArray a = c.getAsJsonArray("slotCosts");
            return Math.max(1, a.get(Math.min(n, a.size() - 1)).getAsInt());
        }
        return n + 1;
    }

    public static int removeCost() { return Math.max(1, HordeJson.num(cfg(), "removeCost", 1)); }

    public static double cap() { return HordeJson.dbl(cfg(), "cap", 0.30); }

    public static boolean isCapped(String key) {
        JsonObject c = cfg();
        if (!c.has("cappedKeys") || !c.get("cappedKeys").isJsonArray()) return false;
        for (JsonElement e : c.getAsJsonArray("cappedKeys")) if (e.getAsString().equals(key)) return true;
        return false;
    }

    private static JsonObject gear() {
        JsonObject c = cfg();
        return c.has("gear") && c.get("gear").isJsonObject() ? c.getAsJsonObject("gear") : new JsonObject();
    }

    /** Intervalle de régénération (ticks) pour le niveau 1-3 de « Souffle de vie ». */
    public static int regenInterval(int tier) {
        JsonObject g = gear();
        if (g.has("regenIntervalTicks") && g.get("regenIntervalTicks").isJsonArray() && g.getAsJsonArray("regenIntervalTicks").size() > 0) {
            JsonArray a = g.getAsJsonArray("regenIntervalTicks");
            return Math.max(10, a.get(Math.max(0, Math.min(a.size() - 1, tier - 1))).getAsInt());
        }
        return 60;
    }

    public static int outOfCombatTicks() { return Math.max(20, HordeJson.num(gear(), "outOfCombatTicks", 200)); }

    public static double executionThreshold() { return HordeJson.dbl(gear(), "executionThreshold", 0.30); }
}
