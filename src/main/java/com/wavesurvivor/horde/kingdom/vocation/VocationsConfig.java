package com.wavesurvivor.horde.kingdom.vocation;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * VOCATIONS (1.9) : réglages de la Maîtrise des rôles du mode Kingdom et arbres de talents, décrits par des DONNÉES :
 * defaults/wavesurvivor/vocations.json (base du mod) + config/wavesurvivor/vocations.json (couche du joueur, seulement les champs
 * à changer). Rechargé par /ws reload.
 */
public final class VocationsConfig {

    /** Un talent : valeur PAR RANG (unité : pct, flat, sec ou bool). « effect » est la clé lue par les effets des rôles. */
    public record Talent(String id, String effect, int ranks, double value, String unit) {}

    public record Branch(String id, List<Talent> talents) {}

    /** Talent ultime (au choix, un seul par rôle). */
    public record Ultimate(String id, String effect, double value, String unit) {}

    public record RoleTree(String roleId, JsonObject base, List<Branch> branches, List<Ultimate> ultimates) {
        public Talent talent(String id) {
            for (Branch b : branches) for (Talent t : b.talents()) if (t.id().equals(id) || t.effect().equals(id)) return t;
            return null;
        }

        /** Branche qui contient ce talent (null si inconnu). */
        public Branch branchOf(String talentId) {
            for (Branch b : branches) for (Talent t : b.talents()) if (t.id().equals(talentId)) return b;
            return null;
        }

        public Ultimate ultimate(String id) {
            for (Ultimate u : ultimates) if (u.id().equals(id)) return u;
            return null;
        }
    }

    private static volatile JsonObject root = null;
    private static volatile Map<String, RoleTree> trees = new LinkedHashMap<>();

    private VocationsConfig() {}

    private static JsonObject base() {
        try (InputStream in = VocationsConfig.class.getResourceAsStream("/defaults/wavesurvivor/vocations.json")) {
            if (in != null) return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[Vocations] Lecture de la base impossible : {}", e.getMessage());
        }
        return new JsonObject();
    }

    public static synchronized void load() {
        JsonObject r = base();
        Path f = FMLPaths.CONFIGDIR.get().resolve("wavesurvivor").resolve("vocations.json");
        if (Files.exists(f)) {
            try {
                r = Patch.apply(r, JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (Exception e) {
                WaveSurvivorMod.LOGGER.error("[Vocations] {} illisible : {}", f.getFileName(), e.getMessage());
            }
        }
        Map<String, RoleTree> map = new LinkedHashMap<>();
        JsonObject roles = r.has("roles") && r.get("roles").isJsonObject() ? r.getAsJsonObject("roles") : new JsonObject();
        for (Map.Entry<String, JsonElement> e : roles.entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            JsonObject o = e.getValue().getAsJsonObject();
            List<Branch> branches = new ArrayList<>();
            if (o.has("branches") && o.get("branches").isJsonArray()) for (JsonElement be : o.getAsJsonArray("branches")) {
                if (!be.isJsonObject()) continue;
                JsonObject bo = be.getAsJsonObject();
                List<Talent> ts = new ArrayList<>();
                if (bo.has("talents") && bo.get("talents").isJsonArray()) for (JsonElement te : bo.getAsJsonArray("talents")) {
                    if (!te.isJsonObject()) continue;
                    JsonObject to = te.getAsJsonObject();
                    String id = HordeJson.str(to, "id", "");
                    if (id.isBlank()) continue;
                    ts.add(new Talent(id, HordeJson.str(to, "effect", e.getKey() + "." + HordeJson.str(bo, "id", "") + "." + id),
                            Math.max(1, HordeJson.num(to, "ranks", 1)), HordeJson.dbl(to, "value", 1), HordeJson.str(to, "unit", "flat")));
                }
                String bid = HordeJson.str(bo, "id", "");
                if (!bid.isBlank() && !ts.isEmpty()) branches.add(new Branch(bid, ts));
            }
            List<Ultimate> ults = new ArrayList<>();
            if (o.has("ultimates") && o.get("ultimates").isJsonArray()) for (JsonElement ue : o.getAsJsonArray("ultimates")) {
                if (!ue.isJsonObject()) continue;
                JsonObject uo = ue.getAsJsonObject();
                String id = HordeJson.str(uo, "id", "");
                if (id.isBlank()) continue;
                ults.add(new Ultimate(id, HordeJson.str(uo, "effect", e.getKey() + ".ult." + id), HordeJson.dbl(uo, "value", 1), HordeJson.str(uo, "unit", "bool")));
            }
            JsonObject b = o.has("base") && o.get("base").isJsonObject() ? o.getAsJsonObject("base") : new JsonObject();
            map.put(e.getKey(), new RoleTree(e.getKey(), b, branches, ults));
        }
        trees = map;
        root = r;
        WaveSurvivorMod.LOGGER.info("[Vocations] {} arbres chargés.", map.size());
    }

    private static JsonObject cfg() {
        if (root == null) load();
        return root;
    }

    /** Arbre du rôle (id du rôle : « builder », « miner »…) ; null si le rôle n'a pas d'arbre. */
    public static RoleTree tree(String roleId) {
        cfg();
        return trees.get(roleId);
    }

    // ─── Réglages généraux ───

    public static int maxLevel() { return Math.max(1, HordeJson.num(cfg(), "maxLevel", 20)); }

    public static int pointsPerLevel() { return Math.max(0, HordeJson.num(cfg(), "pointsPerLevel", 1)); }

    /** XP à gagner pour passer du niveau n-1 au niveau n : base + pas × (n − 1). */
    public static int xpForLevel(int n) {
        JsonObject c = cfg().has("xpCurve") && cfg().get("xpCurve").isJsonObject() ? cfg().getAsJsonObject("xpCurve") : new JsonObject();
        return Math.max(1, HordeJson.num(c, "base", 100) + HordeJson.num(c, "step", 50) * Math.max(0, n - 1));
    }

    /** XP total cumulée nécessaire pour atteindre le niveau n (0 pour le niveau 0). */
    public static long xpToReach(int n) {
        long s = 0;
        for (int i = 1; i <= n; i++) s += xpForLevel(i);
        return s;
    }

    /** Niveau de Maîtrise (0 à maxLevel) pour une XP totale. */
    public static int levelForXp(long xp) {
        int lvl = 0;
        long acc = 0;
        int max = maxLevel();
        while (lvl < max) {
            acc += xpForLevel(lvl + 1);
            if (xp < acc) break;
            lvl++;
        }
        return lvl;
    }

    /** Points à dépenser dans une branche pour débloquer le talent à la position i (0 à 3). */
    public static int branchTier(int index) {
        JsonObject c = cfg();
        if (c.has("branchTiers") && c.get("branchTiers").isJsonArray() && c.getAsJsonArray("branchTiers").size() > 0) {
            JsonArray a = c.getAsJsonArray("branchTiers");
            return Math.max(0, a.get(Math.min(Math.max(0, index), a.size() - 1)).getAsInt());
        }
        return new int[]{0, 2, 5, 8}[Math.max(0, Math.min(3, index))];
    }

    private static JsonObject ult() {
        return cfg().has("ultimate") && cfg().get("ultimate").isJsonObject() ? cfg().getAsJsonObject("ultimate") : new JsonObject();
    }

    /** Points déjà dépensés dans l'arbre pour pouvoir choisir un talent ultime. */
    public static int ultimatePoints() { return Math.max(0, HordeJson.num(ult(), "pointsRequired", 15)); }

    public static int ultimateCostPR() { return Math.max(0, HordeJson.num(ult(), "costPR", 150)); }

    public static int resetCostPR() { return Math.max(0, HordeJson.num(cfg(), "resetCostPR", 50)); }

    // ─── XP gagnée en partie ───

    private static JsonObject xp() {
        return cfg().has("xp") && cfg().get("xp").isJsonObject() ? cfg().getAsJsonObject("xp") : new JsonObject();
    }

    /** XP par vague ou assaut survécu (base commune à tous les rôles). */
    public static int xpPerWave() { return Math.max(0, HordeJson.num(xp(), "perWave", 10)); }

    public static double victoryMult() { return HordeJson.dbl(xp(), "victoryMult", 2.0); }

    public static double defeatMult() { return HordeJson.dbl(xp(), "defeatMult", 0.5); }

    /** XP d'une action propre au rôle (clé comme « catalyst_closed »), 0 si elle n'existe pas. */
    public static double xpAction(String roleId, String action) {
        JsonObject a = xp().has("actions") && xp().get("actions").isJsonObject() ? xp().getAsJsonObject("actions") : new JsonObject();
        JsonObject r = a.has(roleId) && a.get(roleId).isJsonObject() ? a.getAsJsonObject(roleId) : new JsonObject();
        return HordeJson.dbl(r, action, 0);
    }

    // ─── Effets de base des rôles (valeurs réduites) ───

    public static double baseValue(String roleId, String key, double def) {
        RoleTree t = tree(roleId);
        return t == null ? def : HordeJson.dbl(t.base(), key, def);
    }
}
