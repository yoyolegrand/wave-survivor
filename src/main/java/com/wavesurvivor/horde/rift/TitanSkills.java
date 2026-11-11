package com.wavesurvivor.horde.rift;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.config.model.HordeConfigMultiData;
import com.wavesurvivor.horde.skill.BossSkill;
import com.wavesurvivor.horde.spawn.CustomSkillRegistry;
import com.wavesurvivor.horde.skill.SkillFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

/**
 * 2.0 — COMPÉTENCES DU TITAN. Le Titan tire ses attaques dans une liste pondérée (réglage « titanSkills » de la Faille, éditeur de horde ›
 * Faille › Compétences) : chaque entrée = une compétence du catalogue (Entités & Compétences), un poids et la phase où elle se débloque (1 à 4).
 *   • Compétences « Titan » (types titan_*) : celles codées dans RiftTitan. Leurs réglages (catalogue + réglages propres à ce Titan) remplacent
 *     ceux de la Faille au moment où le Titan apparaît ({@link #effective}).
 *   • Compétences ordinaires (Éruption, Gatling…) : lancées depuis un lanceur invisible au bord de l'île, côté Titan (RiftTitan.castRegular).
 * Sans liste enregistrée : la liste par défaut ({@link #defaults()}) reproduit le comportement d'avant.
 */
public final class TitanSkills {

    private TitanSkills() {}

    private static final Gson GSON = new Gson();
    private static final Random RNG = new Random();

    public static final String METEORS = "titan_meteors", TRACKING = "titan_tracking", LAVA = "titan_lava", GIANT = "titan_giant",
            STOMP = "titan_stomp", ROCK = "titan_rock", BEAM = "titan_beam", ROAR = "titan_roar", CHARGE = "titan_charge",
            FIST = "titan_fist", BUILDING = "titan_building", AVATARS = "titan_avatars";
    public static final String[] TYPES = {METEORS, TRACKING, LAVA, GIANT, STOMP, ROCK, BEAM, ROAR, CHARGE, FIST, BUILDING, AVATARS};

    public static boolean isTitanType(String type) {
        return type != null && type.startsWith("titan_");
    }

    /** Nom de la compétence livrée avec le mod pour un type Titan (catalogue). */
    public static String defaultName(String type) {
        return type == null ? "" : "titan_" + type.substring("titan_".length());
    }

    /** Liste par défaut : mêmes attaques, mêmes poids et mêmes phases qu'avant la 2.0 (les doublons ajoutent du poids à une phase plus haute). */
    public static List<HordeConfigMultiData.TitanSkillEntry> defaults() {
        List<HordeConfigMultiData.TitanSkillEntry> l = new ArrayList<>();
        l.add(e(METEORS, 2, 1));
        l.add(e(STOMP, 1, 1));
        l.add(e(AVATARS, 1, 1));
        l.add(e(ROCK, 2, 2));
        l.add(e(TRACKING, 1, 2));
        l.add(e(BUILDING, 1, 2));
        l.add(e(FIST, 1, 2));
        l.add(e(CHARGE, 1, 1));
        l.add(e(BEAM, 1, 3));
        l.add(e(LAVA, 1, 3));
        l.add(e(BUILDING, 1, 3));
        l.add(e(BEAM, 1, 4));
        l.add(e(ROAR, 1, 4));
        l.add(e(GIANT, 1, 4));
        l.add(e(FIST, 1, 4));
        return l;
    }

    private static HordeConfigMultiData.TitanSkillEntry e(String type, int w, int ph) {
        HordeConfigMultiData.TitanSkillEntry x = new HordeConfigMultiData.TitanSkillEntry();
        x.skill = defaultName(type);
        x.weight = w;
        x.phase = ph;
        return x;
    }

    /** Liste du Titan : celle de la horde, sinon la liste par défaut. */
    public static List<HordeConfigMultiData.TitanSkillEntry> entries(HordeConfigMultiData.RiftSettings rs) {
        return rs == null || rs.titanSkills == null ? defaults() : rs.titanSkills;
    }

    /** Compétence du catalogue avec les réglages propres à ce Titan, ou null si elle n'existe plus. */
    public static CustomSkillData resolve(HordeConfigMultiData.RiftSettings rs, String name) {
        if (name == null || name.isBlank()) return null;
        CustomSkillData base = CustomSkillRegistry.getSkill(name);
        if (base == null) {
            // Compétence Titan livrée par le mod mais absente du catalogue (supprimée) : ses valeurs par défaut
            for (String t : TYPES) if (defaultName(t).equalsIgnoreCase(name)) { base = new CustomSkillData(); base.skillName = name; base.skillType = t; }
            if (base == null) return null;
        }
        JsonObject ov = rs != null && rs.skillOverrides != null ? rs.skillOverrides.get(name) : null;
        if (ov == null || ov.size() == 0) return base;
        try {
            JsonObject merged = GSON.toJsonTree(base).getAsJsonObject();
            for (var en : ov.entrySet()) merged.add(en.getKey(), en.getValue());
            return GSON.fromJson(merged, CustomSkillData.class);
        } catch (Exception ex) {
            WaveSurvivorMod.LOGGER.warn("[Titan] Réglages propres invalides pour '{}' : {}", name, ex.getMessage());
            return base;
        }
    }

    /** Type d'une entrée (compétence Titan ou ordinaire), "" si introuvable. */
    public static String typeOf(HordeConfigMultiData.RiftSettings rs, String name) {
        CustomSkillData d = resolve(rs, name);
        return d == null || d.skillType == null ? "" : d.skillType;
    }

    /**
     * Réglages effectifs du Titan : une copie de ceux de la Faille où chaque compétence Titan de la liste impose ses valeurs (la première de
     * chaque type). Une compétence absente de la liste est désactivée (charge, poing, avatars).
     */
    public static HordeConfigMultiData.RiftSettings effective(HordeConfigMultiData.RiftSettings base) {
        HordeConfigMultiData.RiftSettings o = GSON.fromJson(GSON.toJsonTree(base), HordeConfigMultiData.RiftSettings.class);
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (HordeConfigMultiData.TitanSkillEntry en : entries(base)) {
            CustomSkillData d = resolve(base, en.skill);
            if (d == null || !isTitanType(d.skillType) || !seen.add(d.skillType)) continue;
            apply(d, o);
        }
        // Charge et poing n'existent que s'ils sont dans la liste (sans liste enregistrée, la charge suit l'ancien réglage « titanCharge »)
        o.titanCharge = seen.contains(CHARGE) && (base.titanSkills != null || base.titanCharge);
        if (!seen.contains(FIST)) o.titanMonolithDamage = 0;
        if (!seen.contains(AVATARS)) o.titanAvatarMax = 0;
        return o;
    }

    private static void apply(CustomSkillData d, HordeConfigMultiData.RiftSettings o) {
        switch (d.skillType) {
            case METEORS -> {
                o.meteorBase = d.tMeteorsBase;
                o.meteorPerPlayer = d.tMeteorsPerPlayer;
                o.meteorDamage = d.tMeteorsDamage;
                o.meteorRadius = d.tMeteorsRadius;
                o.meteorSize = d.tMeteorsSize;
                o.meteorDelay = d.tMeteorsDelay;
                o.meteorFall = d.tMeteorsFall;
                if (d.tMeteorsBlocks != null && !d.tMeteorsBlocks.isBlank()) o.meteorBlocks = d.tMeteorsBlocks;
                if (d.tMeteorsTrail != null && !d.tMeteorsTrail.isBlank()) o.meteorTrail = d.tMeteorsTrail;
                o.meteorCrater = d.tMeteorsCrater;
            }
            case TRACKING -> o.titanTrackingDamage = d.tTrackingDamage;
            case LAVA -> o.titanLavaDamage = d.tLavaDamage;
            case GIANT -> { o.titanGiantDamage = d.tGiantDamage; o.titanGiantRadius = d.tGiantRadius; }
            case STOMP -> o.titanStompDamage = d.tStompDamage;
            case ROCK -> { o.titanRockHits = d.tRockHits; o.titanRockDamage = d.tRockDamage; }
            case BEAM -> o.titanBeamDamage = d.tBeamDamage;
            case CHARGE -> {
                o.titanChargeInterval = d.tChargeInterval;
                o.titanChargeStun = d.tChargeStun;
                o.titanChargeMelee = d.tChargeMelee;
                o.titanChargeDamage = d.tChargeDamage;
                o.titanChargeReach = d.tChargeReach;
                o.titanMeleeDamage = d.tChargeMeleeDamage;
                o.titanChargeCapPercent = d.tChargeCapPercent;
                o.titanIslandBreak = d.tChargeIslandBreak;
                o.titanBreakDepth = d.tChargeBreakDepth;
            }
            case FIST -> o.titanMonolithDamage = d.tFistDamage;
            case BUILDING -> { o.titanBuildDamage = d.tBuildingDamage; o.titanBuildRadius = d.tBuildingRadius; }
            case AVATARS -> {
                if (d.tAvatarsEntity != null && !d.tAvatarsEntity.isBlank()) o.titanAvatarEntity = d.tAvatarsEntity.trim();
                o.titanAvatarMax = d.tAvatarsMax;
                o.titanAvatarHp = d.tAvatarsHp;
                o.titanAvatarPercent = d.tAvatarsPercent;
            }
            default -> { }
        }
    }

    /**
     * Tire une attaque : parmi les entrées débloquées à cette phase ({@code phase} = 0 à 3) et utilisables maintenant, au poids.
     * La charge n'est jamais tirée ici (elle a son propre minuteur). null si rien n'est possible.
     */
    public static HordeConfigMultiData.TitanSkillEntry pick(HordeConfigMultiData.RiftSettings rs, int phase, Predicate<String> usable) {
        List<HordeConfigMultiData.TitanSkillEntry> pool = new ArrayList<>();
        int total = 0;
        for (HordeConfigMultiData.TitanSkillEntry en : entries(rs)) {
            if (en == null || en.weight <= 0 || Math.max(1, en.phase) - 1 > phase) continue;
            String type = typeOf(rs, en.skill);
            if (type.isEmpty() || CHARGE.equals(type) || !usable.test(type)) continue;
            pool.add(en);
            total += en.weight;
        }
        if (pool.isEmpty() || total <= 0) return null;
        int r = RNG.nextInt(total);
        for (HordeConfigMultiData.TitanSkillEntry en : pool) {
            r -= en.weight;
            if (r < 0) return en;
        }
        return pool.get(pool.size() - 1);
    }

    // ─── Compétences ordinaires (lancées par le lanceur invisible du Titan) ───

    private static final Map<String, BossSkill> REGULAR = new HashMap<>();

    /** Instance de la compétence ordinaire (réglages du catalogue + réglages propres au Titan), gardée pour toute la bataille. */
    public static BossSkill regular(HordeConfigMultiData.RiftSettings rs, String name) {
        return REGULAR.computeIfAbsent(name.toLowerCase(java.util.Locale.ROOT), k -> {
            CustomSkillData d = resolve(rs, name);
            return d == null || isTitanType(d.skillType) ? null : SkillFactory.create(d);
        });
    }

    /** Nouvelle bataille : les compétences ordinaires sont recréées (réglages à jour). */
    public static void reset() { REGULAR.clear(); }
}
