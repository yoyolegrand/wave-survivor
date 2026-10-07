package com.wavesurvivor.horde.boss;

import com.wavesurvivor.WaveSurvivorMod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parse la Map<String, Object> "bossConfig" d'une CustomEntity en objet typé.
 * Format base44 :
 * {
 *   "playerAuraRadius": 10, "playerAuraInterval": 300,
 *   "playerAuraEffects": [{"effect": "slowness", "amplifier": 0, "duration": 60}, ...],
 *   "selfEffectTrigger": "interval" | "healthThreshold",
 *   "selfEffectInterval": 300, "selfEffectHealthThreshold": 50,
 *   "selfEffectTriggeredOnce": false,
 *   "selfEffects": [{"effect": "resistance", ...}, ...],
 *   "summonEnabled": true, "summonTrigger": "interval",
 *   "summonInterval": 300, "summonHealthThreshold": 50,
 *   "summonTriggeredOnce": false, "summonMaxActive": 5,
 *   "summonEntities": [{"entityType": "minecraft:drowned", "count": 2, "radius": 5}, ...]
 * }
 */
public class BossConfigParsed {

    // playerAura
    public double playerAuraRadius = 0;
    public int playerAuraInterval = 0;
    public List<EffectSpec> playerAuraEffects = new ArrayList<>();

    // selfEffects
    public String selfEffectTrigger = "interval";
    public int selfEffectInterval = 0;
    public int selfEffectHealthThreshold = 0;
    public boolean selfEffectTriggeredOnce = false;
    public List<EffectSpec> selfEffects = new ArrayList<>();

    // summon
    public boolean summonEnabled = false;
    public String summonTrigger = "interval";
    public int summonInterval = 0;
    public int summonHealthThreshold = 0;
    public boolean summonTriggeredOnce = false;
    public int summonMaxActive = 0;
    public List<SummonEntity> summonEntities = new ArrayList<>();

    public static BossConfigParsed from(Map<String, Object> m) {
        if (m == null || m.isEmpty()) return null;
        BossConfigParsed p = new BossConfigParsed();
        try {
            // playerAura
            p.playerAuraRadius = asDouble(m.get("playerAuraRadius"), 0);
            p.playerAuraInterval = asInt(m.get("playerAuraInterval"), 0);
            p.playerAuraEffects = asEffectList(m.get("playerAuraEffects"));

            // selfEffects
            p.selfEffectTrigger = asString(m.get("selfEffectTrigger"), "interval");
            p.selfEffectInterval = asInt(m.get("selfEffectInterval"), 0);
            p.selfEffectHealthThreshold = asInt(m.get("selfEffectHealthThreshold"), 0);
            p.selfEffectTriggeredOnce = asBool(m.get("selfEffectTriggeredOnce"), false);
            p.selfEffects = asEffectList(m.get("selfEffects"));

            // summon
            p.summonEnabled = asBool(m.get("summonEnabled"), false);
            p.summonTrigger = asString(m.get("summonTrigger"), "interval");
            p.summonInterval = asInt(m.get("summonInterval"), 0);
            p.summonHealthThreshold = asInt(m.get("summonHealthThreshold"), 0);
            p.summonTriggeredOnce = asBool(m.get("summonTriggeredOnce"), false);
            p.summonMaxActive = asInt(m.get("summonMaxActive"), 0);
            p.summonEntities = asSummonList(m.get("summonEntities"));
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.warn("[BossConfig] Erreur parsing bossConfig : {}", e.getMessage());
            return null;
        }
        return p;
    }

    public boolean hasPlayerAura() {
        return playerAuraInterval > 0 && playerAuraRadius > 0
                && playerAuraEffects != null && !playerAuraEffects.isEmpty();
    }

    public boolean hasSelfEffects() {
        return selfEffects != null && !selfEffects.isEmpty() && selfEffectInterval > 0;
    }

    public boolean hasSummon() {
        return summonEnabled && summonEntities != null && !summonEntities.isEmpty()
                && summonInterval > 0 && summonMaxActive > 0;
    }

    public boolean isEmpty() {
        return !hasPlayerAura() && !hasSelfEffects() && !hasSummon();
    }

    // ─── Helpers de parsing (Gson désérialise en Double/Boolean/String/Map/List) ───

    private static int asInt(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        return def;
    }

    private static double asDouble(Object o, double def) {
        if (o instanceof Number n) return n.doubleValue();
        return def;
    }

    private static boolean asBool(Object o, boolean def) {
        if (o instanceof Boolean b) return b;
        return def;
    }

    private static String asString(Object o, String def) {
        return o instanceof String s ? s : def;
    }

    @SuppressWarnings("unchecked")
    private static List<EffectSpec> asEffectList(Object o) {
        List<EffectSpec> out = new ArrayList<>();
        if (!(o instanceof List<?> raw)) return out;
        for (Object el : raw) {
            if (!(el instanceof Map<?, ?> m)) continue;
            Map<String, Object> mo = (Map<String, Object>) m;
            EffectSpec fx = new EffectSpec();
            fx.effect = asString(mo.get("effect"), null);
            fx.amplifier = asInt(mo.get("amplifier"), 0);
            fx.duration = asInt(mo.get("duration"), 0);
            if (fx.effect != null && !fx.effect.isBlank() && fx.duration > 0) out.add(fx);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<SummonEntity> asSummonList(Object o) {
        List<SummonEntity> out = new ArrayList<>();
        if (!(o instanceof List<?> raw)) return out;
        for (Object el : raw) {
            if (!(el instanceof Map<?, ?> m)) continue;
            Map<String, Object> mo = (Map<String, Object>) m;
            SummonEntity s = new SummonEntity();
            s.entityType = asString(mo.get("entityType"), null);
            s.count = asInt(mo.get("count"), 1);
            s.radius = asDouble(mo.get("radius"), 3);
            if (s.entityType != null && !s.entityType.isBlank() && s.count > 0) out.add(s);
        }
        return out;
    }

    public static class EffectSpec {
        public String effect;
        public int amplifier;
        public int duration;
    }

    public static class SummonEntity {
        public String entityType;
        public int count;
        public double radius;
    }
}
