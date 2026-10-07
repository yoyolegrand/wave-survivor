package com.wavesurvivor.horde.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/** Lecture/écriture tolérante du JSON d'une horde pour l'éditeur (code commun client/serveur). */
public final class HordeJson {

    private HordeJson() {}

    // ─── Accès génériques ───

    public static String str(JsonObject o, String k, String def) {
        try { return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def; } catch (Exception e) { return def; }
    }

    public static int num(JsonObject o, String k, int def) {
        try { return o != null && o.has(k) && !o.get(k).isJsonNull() ? (int) Math.round(o.get(k).getAsDouble()) : def; } catch (Exception e) { return def; }
    }

    public static double dbl(JsonObject o, String k, double def) {
        try { return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : def; } catch (Exception e) { return def; }
    }

    public static boolean bool(JsonObject o, String k, boolean def) {
        try { return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsBoolean() : def; } catch (Exception e) { return def; }
    }

    public static JsonObject obj(JsonObject o, String k) {
        if (!o.has(k) || !o.get(k).isJsonObject()) o.add(k, new JsonObject());
        return o.getAsJsonObject(k);
    }

    public static JsonArray arr(JsonObject o, String k) {
        if (!o.has(k) || !o.get(k).isJsonArray()) o.add(k, new JsonArray());
        return o.getAsJsonArray(k);
    }

    public static JsonObject parseObject(String s) {
        try {
            JsonElement e = JsonParser.parseString(s == null ? "" : s.trim());
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    // ─── Modèles ───

    public static JsonObject newHorde(String name) {
        JsonObject h = new JsonObject();
        h.addProperty("hordeName", name);
        h.addProperty("hordeDescription", "");
        JsonObject cd = new JsonObject();
        cd.addProperty("totalWaves", 5);
        cd.addProperty("delayBetweenWaves", 30);
        cd.addProperty("spawnType", "fixed");
        cd.addProperty("spawnRadius", 12);
        cd.addProperty("gameOverRadius", 2000);
        JsonObject ps = new JsonObject();
        ps.addProperty("enabled", true);
        ps.addProperty("countMultiplier", 0.5);
        ps.addProperty("statsMultiplier", 0.1);
        cd.add("playerScaling", ps);
        JsonObject msg = new JsonObject();
        msg.addProperty("normalWave", "§6§l[HORDE] §eVague {wave}/{total}");
        msg.addProperty("specialWave", "§c§l[HORDE] §6Vague {wave}/{total} §c- {name} !");
        msg.addProperty("useSound", true);
        cd.add("spawnMessages", msg);
        cd.add("hordeEntities", new JsonArray());
        cd.addProperty("useSpecialWaves", false);
        cd.addProperty("specialWaveChance", 35);
        cd.add("specialWaves", new JsonArray());
        cd.addProperty("useBossWaves", false);
        cd.add("bossWaves", new JsonArray());
        cd.addProperty("useMerchants", false);
        cd.add("merchants", new JsonArray());
        cd.addProperty("useWavePauses", false);
        cd.add("wavePauses", new JsonArray());
        cd.addProperty("chaosEnabled", false);
        cd.add("chaosEvents", new JsonArray());
        h.add("configData", cd);
        return h;
    }

    public static JsonObject newMob() {
        JsonObject m = new JsonObject();
        m.addProperty("entity_type", "minecraft:zombie");
        m.addProperty("custom_name", "Zombie");
        m.addProperty("base_count", 3);
        m.addProperty("count_increment", 1);
        m.addProperty("max_health", 20);
        m.addProperty("attack_damage", 3);
        m.addProperty("movement_speed", 0.23);
        m.addProperty("follow_range", 64);
        m.add("equipment", new JsonObject());
        m.add("loot_table", new JsonArray());
        m.addProperty("min_wave", 0);
        m.addProperty("max_wave", 0);
        return m;
    }

    /** Est-ce un mob de horde (et pas une horde entière) ? */
    public static boolean looksLikeMob(JsonObject o) {
        return o != null && o.has("entity_type") && !o.has("configData");
    }

    public static boolean looksLikeHorde(JsonObject o) {
        return o != null && o.has("configData") && o.get("configData").isJsonObject();
    }

    // ─── Aperçu vague par vague (même formule que HordeEntity.getCountForWave, 1 joueur) ───

    public record WaveLine(int wave, List<String> parts, List<String> bosses) {}

    public static List<WaveLine> preview(JsonObject horde) {
        List<WaveLine> out = new ArrayList<>();
        JsonObject cd = obj(horde, "configData");
        int total = Math.max(0, num(cd, "totalWaves", 0));
        JsonArray mobs = arr(cd, "hordeEntities");
        JsonArray bosses = arr(cd, "bossWaves");
        for (int w = 1; w <= Math.min(total, 100); w++) {
            List<String> parts = new ArrayList<>();
            for (JsonElement el : mobs) {
                if (!el.isJsonObject()) continue;
                JsonObject m = el.getAsJsonObject();
                int n = countFor(m, w);
                if (n > 0) parts.add(n + "× " + displayName(m));
            }
            List<String> b = new ArrayList<>();
            if (bool(cd, "useBossWaves", true)) {
                for (JsonElement el : bosses) {
                    if (el.isJsonObject() && num(el.getAsJsonObject(), "waveNumber", -1) == w) {
                        b.add(str(el.getAsJsonObject(), "bossName", "Boss"));
                    }
                }
            }
            out.add(new WaveLine(w, parts, b));
        }
        return out;
    }

    public static int countFor(JsonObject m, int wave) {
        int min = num(m, "min_wave", 0), max = num(m, "max_wave", 0);
        if (min > 0 && wave < min) return 0;
        if (max > 0 && wave > max) return 0;
        int first = Math.max(1, min);
        return Math.max(0, num(m, "base_count", 0) + num(m, "count_increment", 0) * Math.max(0, wave - first));
    }

    public static String displayName(JsonObject m) {
        String n = str(m, "custom_name", "");
        n = n.replaceAll("§.", "").trim();
        if (n.isEmpty()) n = str(m, "entity_type", "?");
        return n;
    }
}
