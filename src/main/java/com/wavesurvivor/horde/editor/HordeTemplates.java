package com.wavesurvivor.horde.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wavesurvivor.i18n.WSLang;

/**
 * Modèles de départ de l'éditeur de hordes (bouton « Nouvelle horde ») :
 * Classique courte, Classique longue, Kingdom, Vide. Monstres vanilla uniquement.
 */
public final class HordeTemplates {

    private HordeTemplates() {}

    public static final String[] IDS = {"short", "long", "kingdom", "empty"};

    /** Construit la horde du modèle {@code id} avec le nom donné. */
    public static JsonObject build(String id, String name) {
        return switch (id) {
            case "short" -> classicShort(name);
            case "long" -> classicLong(name);
            case "kingdom" -> kingdom(name);
            default -> HordeJson.newHorde(name);
        };
    }

    // ─── Briques ───

    private static String n(String key) { return WSLang.t("tpl.mob." + key); }

    private static JsonObject mob(String type, String name, int base, int inc, int hp, int dmg, double speed,
                                  int minWave, int maxWave, String mainhand, int emeraldChance) {
        JsonObject m = HordeJson.newMob();
        m.addProperty("entity_type", type);
        m.addProperty("custom_name", name);
        m.addProperty("base_count", base);
        m.addProperty("count_increment", inc);
        m.addProperty("max_health", hp);
        m.addProperty("attack_damage", dmg);
        m.addProperty("movement_speed", speed);
        m.addProperty("min_wave", minWave);
        m.addProperty("max_wave", maxWave);
        if (mainhand != null) {
            JsonObject eq = new JsonObject();
            JsonObject mh = new JsonObject();
            mh.addProperty("item", mainhand);
            mh.addProperty("dropChance", 5);
            eq.add("mainhand", mh);
            m.add("equipment", eq);
        }
        if (emeraldChance > 0) m.add("loot_table", loot("minecraft:emerald", 1, 2, emeraldChance));
        return m;
    }

    private static JsonArray loot(String item, int min, int max, int chance) {
        JsonArray a = new JsonArray();
        a.add(lootLine(item, min, max, chance));
        return a;
    }

    private static JsonObject lootLine(String item, int min, int max, int chance) {
        JsonObject l = new JsonObject();
        l.addProperty("item", item);
        l.addProperty("minQty", min);
        l.addProperty("maxQty", max);
        l.addProperty("chance", chance);
        return l;
    }

    private static JsonObject boss(int wave, String name, String type, int hp, int dmg, double speed,
                                   String minionType, int minionCount, int emeralds) {
        JsonObject b = new JsonObject();
        b.addProperty("waveNumber", wave);
        b.addProperty("bossName", name);
        b.addProperty("entityType", type);
        b.addProperty("useCustomEntity", false);
        b.addProperty("customEntityName", "");
        b.addProperty("maxHealth", hp);
        b.addProperty("attackDamage", dmg);
        b.addProperty("movementSpeed", speed);
        b.addProperty("knockbackResistance", 0.8);
        b.add("equipment", new JsonObject());
        boolean minions = minionType != null && minionCount > 0;
        b.addProperty("useMinionSummon", minions);
        JsonObject mc = new JsonObject();
        if (minions) {
            mc.addProperty("triggerType", "interval");
            mc.addProperty("triggerValue", 30);
            mc.addProperty("minionType", minionType);
            mc.addProperty("minionCount", minionCount);
        }
        b.add("minionConfig", mc);
        JsonArray lt = new JsonArray();
        lt.add(lootLine("minecraft:emerald", emeralds, emeralds + 8, 100));
        lt.add(lootLine("minecraft:diamond", 1, 2, 50));
        b.add("lootTable", lt);
        b.addProperty("spawnMessage", WSLang.t("ui.boss_name_apparait"));
        b.addProperty("deathMessage", WSLang.t("ui.victoire_name_a_ete_vaincu"));
        return b;
    }

    private static JsonObject base(String name, String descKey, int waves, int delay) {
        JsonObject h = HordeJson.newHorde(name);
        h.addProperty("hordeDescription", WSLang.t(descKey));
        JsonObject cd = HordeJson.obj(h, "configData");
        cd.addProperty("totalWaves", waves);
        cd.addProperty("delayBetweenWaves", delay);
        return h;
    }

    // ─── Classique courte : 10 vagues, 1 boss ───

    private static JsonObject classicShort(String name) {
        JsonObject h = base(name, "tpl.short.hdesc", 10, 30);
        JsonObject cd = HordeJson.obj(h, "configData");
        JsonArray mobs = HordeJson.arr(cd, "hordeEntities");
        mobs.add(mob("minecraft:zombie", n("zombie"), 3, 1, 20, 3, 0.23, 0, 0, null, 25));
        mobs.add(mob("minecraft:skeleton", n("skeleton"), 2, 1, 20, 3, 0.25, 2, 0, "minecraft:bow", 25));
        mobs.add(mob("minecraft:spider", n("spider"), 1, 1, 16, 3, 0.3, 4, 0, null, 25));
        mobs.add(mob("minecraft:creeper", n("creeper"), 1, 0, 20, 0, 0.25, 6, 0, null, 30));
        cd.addProperty("useBossWaves", true);
        HordeJson.arr(cd, "bossWaves").add(boss(10, n("boss_short"), "minecraft:zombie", 250, 9, 0.26,
                "minecraft:zombie", 2, 10));
        return h;
    }

    // ─── Classique longue : 30 vagues, 3 boss, vagues spéciales, marchand ───

    private static JsonObject classicLong(String name) {
        JsonObject h = base(name, "tpl.long.hdesc", 30, 25);
        JsonObject cd = HordeJson.obj(h, "configData");
        JsonArray mobs = HordeJson.arr(cd, "hordeEntities");
        mobs.add(mob("minecraft:zombie", n("zombie"), 3, 1, 20, 3, 0.23, 0, 15, null, 20));
        mobs.add(mob("minecraft:skeleton", n("skeleton"), 2, 1, 20, 3, 0.25, 2, 20, "minecraft:bow", 20));
        mobs.add(mob("minecraft:spider", n("spider"), 1, 1, 16, 3, 0.3, 4, 0, null, 20));
        mobs.add(mob("minecraft:creeper", n("creeper"), 1, 0, 20, 0, 0.25, 6, 0, null, 25));
        mobs.add(mob("minecraft:husk", n("husk"), 2, 1, 30, 5, 0.24, 11, 0, "minecraft:iron_sword", 25));
        mobs.add(mob("minecraft:stray", n("stray"), 2, 1, 26, 4, 0.25, 14, 0, "minecraft:bow", 25));
        mobs.add(mob("minecraft:witch", n("witch"), 1, 0, 30, 0, 0.25, 16, 0, null, 35));
        mobs.add(mob("minecraft:vindicator", n("vindicator"), 1, 1, 36, 8, 0.31, 21, 0, "minecraft:iron_axe", 40));
        mobs.add(mob("minecraft:wither_skeleton", n("wither_skeleton"), 1, 1, 40, 8, 0.27, 25, 0, "minecraft:stone_sword", 45));

        // Vagues spéciales
        cd.addProperty("useSpecialWaves", true);
        cd.addProperty("specialWaveChance", 20);
        JsonArray specials = HordeJson.arr(cd, "specialWaves");
        specials.add(special(n("special_spiders"), "minecraft:spider", n("spider"), 12, 16, 3, 0.32));
        specials.add(special(n("special_creepers"), "minecraft:creeper", n("creeper"), 8, 20, 0, 0.27));

        // Boss
        cd.addProperty("useBossWaves", true);
        JsonArray bosses = HordeJson.arr(cd, "bossWaves");
        bosses.add(boss(10, n("boss_short"), "minecraft:zombie", 300, 10, 0.26, "minecraft:zombie", 2, 10));
        bosses.add(boss(20, n("boss_long_2"), "minecraft:wither_skeleton", 600, 14, 0.28, "minecraft:skeleton", 3, 16));
        bosses.add(boss(30, n("boss_long_3"), "minecraft:ravager", 1200, 18, 0.27, "minecraft:vindicator", 3, 24));

        // Marchand ambulant
        cd.addProperty("useMerchants", true);
        HordeJson.arr(cd, "merchants").add(merchant(n("merchant"), 0.3));
        return h;
    }

    private static JsonObject special(String waveName, String type, String mobName, int count, int hp, int dmg, double speed) {
        JsonObject s = new JsonObject();
        s.addProperty("name", waveName);
        s.addProperty("chance", 50);
        JsonArray ents = new JsonArray();
        JsonObject e = new JsonObject();
        e.addProperty("entity_type", type);
        e.addProperty("custom_name", mobName);
        e.addProperty("count", count);
        e.addProperty("max_health", hp);
        e.addProperty("attack_damage", dmg);
        e.addProperty("movement_speed", speed);
        e.addProperty("follow_range", 64);
        e.add("equipment", new JsonObject());
        e.add("loot_table", loot("minecraft:emerald", 1, 2, 30));
        ents.add(e);
        s.add("entities", ents);
        return s;
    }

    private static JsonObject merchant(String name, double chance) {
        JsonObject m = new JsonObject();
        m.addProperty("name", name);
        m.addProperty("entityType", "minecraft:villager");
        m.addProperty("profession", "armorer");
        JsonObject off = new JsonObject();
        off.addProperty("x", 3);
        off.addProperty("y", 0);
        off.addProperty("z", 2);
        m.add("offset", off);
        m.addProperty("spawnChance", chance);
        JsonArray trades = new JsonArray();
        trades.add(trade(2, "minecraft:cooked_beef", 8));
        trades.add(trade(4, "minecraft:arrow", 32));
        trades.add(trade(6, "minecraft:golden_apple", 1));
        trades.add(trade(10, "minecraft:iron_chestplate", 1));
        m.add("trades", trades);
        return m;
    }

    private static JsonObject trade(int emeralds, String out, int count) {
        JsonObject t = new JsonObject();
        t.addProperty("input1", "minecraft:emerald");
        t.addProperty("input1Count", emeralds);
        t.addProperty("input2", "");
        t.addProperty("input2Count", 0);
        t.addProperty("output", out);
        t.addProperty("outputCount", count);
        t.addProperty("maxUses", 16);
        return t;
    }

    // ─── Kingdom : 4 Portes, 10 Assauts, unités de siège, boss d'assaut + boss final ───

    private static JsonObject kingdom(String name) {
        JsonObject h = base(name, "tpl.kingdom.hdesc", 10, 45);
        JsonObject cd = HordeJson.obj(h, "configData");
        cd.addProperty("mode", "kingdom");
        cd.addProperty("blockNaturalSpawns", true);
        JsonArray mobs = HordeJson.arr(cd, "hordeEntities");
        mobs.add(mob("minecraft:zombie", n("zombie"), 4, 1, 22, 3, 0.24, 0, 0, null, 40));
        mobs.add(mob("minecraft:pillager", n("pillager"), 3, 1, 24, 4, 0.3, 0, 0, "minecraft:crossbow", 45));
        mobs.add(mob("minecraft:vindicator", n("vindicator"), 2, 1, 28, 7, 0.31, 3, 0, "minecraft:iron_axe", 45));
        mobs.add(mob("minecraft:skeleton", n("skeleton"), 2, 1, 22, 3, 0.25, 2, 0, "minecraft:bow", 40));
        // Unités de siège (mêmes réglages que le bouton « Ajouter les unités de siège »)
        mobs.add(siege("minecraft:ravager", "§6§l🐏 " + n("ram"), 2, 90, 8, 0.24, 1.0, "ram"));
        mobs.add(siege("minecraft:creeper", "§c§l💣 " + n("sapper"), 3, 24, 0, 0.3, 0, "sapper"));
        mobs.add(siege("minecraft:spider", "§a§l🕷 " + n("climber"), 3, 24, 4, 0.33, 0, "climber"));

        cd.addProperty("useBossWaves", true);
        JsonArray bosses = HordeJson.arr(cd, "bossWaves");
        bosses.add(boss(5, n("boss_kingdom_1"), "minecraft:vindicator", 300, 12, 0.3, "minecraft:vindicator", 2, 12));
        bosses.add(boss(10, n("boss_kingdom_final"), "minecraft:ravager", 900, 16, 0.27, "minecraft:pillager", 3, 24));

        cd.addProperty("useMerchants", true);
        HordeJson.arr(cd, "merchants").add(merchant(n("merchant"), 1.0));

        JsonObject k = HordeJson.obj(cd, "kingdom");
        k.addProperty("gateShape", "arch");
        k.addProperty("denBackground", "auto");
        k.addProperty("portalTheme", "auto");
        k.addProperty("gateStyle", "blocks");
        k.addProperty("gatePlacement", "auto");
        k.addProperty("gateCount", 4);
        k.addProperty("gateFacing", "monolith");
        k.addProperty("ringMin", 64);
        k.addProperty("ringMax", 72);
        k.addProperty("portalHealth", 800);
        k.addProperty("assaultBudget", 60);
        k.addProperty("assaultGrowth", 0.15);
        k.addProperty("aliveCap", 80);
        k.addProperty("calmSeconds", 300);
        k.addProperty("calmPercent", 5);
        k.addProperty("smallBreachSeconds", 45);
        k.addProperty("smallBreachUnits", 4);
        JsonArray calm = HordeJson.arr(k, "calmBreachUnits");
        calm.add(calmUnit("minecraft:zombie", n("zombie"), 3, 22, 3, 0.24, null));
        calm.add(calmUnit("minecraft:pillager", n("pillager"), 2, 24, 4, 0.3, "minecraft:crossbow"));
        k.addProperty("maxCycles", 10);
        k.addProperty("squadSize", 8);
        k.addProperty("engageRadius", 16);
        k.addProperty("claimRadius", 16);
        k.addProperty("buildOnlyInClaim", true);
        k.addProperty("claimMarkers", true);
        k.addProperty("wallDurability", true);
        k.addProperty("wallHpMultiplier", 1.0);
        k.addProperty("guardiansPerGate", 2);
        k.addProperty("guardianHpMult", 3.0);
        k.addProperty("catalystHealth", 150);
        k.addProperty("catalystEscort", 4);
        k.addProperty("breachPercent", 35);
        k.addProperty("finalBoss", true);
        return h;
    }

    private static JsonObject siege(String type, String name, int base, int hp, int dmg, double speed, double kb, String role) {
        JsonObject m = mob(type, name, base, 1, hp, dmg, speed, 2, 0, null, 40);
        m.addProperty("knockback_resistance", kb);
        m.addProperty("override_stats", true);
        m.addProperty("siege_role", role);
        return m;
    }

    private static JsonObject calmUnit(String type, String name, int count, int hp, int dmg, double speed, String mainhand) {
        JsonObject u = new JsonObject();
        u.addProperty("entity_type", type);
        u.addProperty("custom_name", name);
        u.addProperty("count", count);
        u.addProperty("max_health", hp);
        u.addProperty("attack_damage", dmg);
        u.addProperty("movement_speed", speed);
        u.addProperty("follow_range", 64);
        JsonObject eq = new JsonObject();
        if (mainhand != null) {
            JsonObject mh = new JsonObject();
            mh.addProperty("item", mainhand);
            mh.addProperty("dropChance", 5);
            eq.add("mainhand", mh);
        }
        u.add("equipment", eq);
        u.add("loot_table", loot("minecraft:emerald", 1, 2, 45));
        return u;
    }
}
