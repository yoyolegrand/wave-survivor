package com.wavesurvivor.horde.skill;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.CustomEntityData;
import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.spawn.CustomSkillRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.*;

/**
 * Gestionnaire central des skills de boss.
 * Chaque boss ayant des skills est enregistré ici avec sa liste de BossSkill instanciés.
 * Le tick() itère tous les boss trackés et déclenche les skills prêts.
 */
public class SkillManager {

    private static final Map<UUID, List<BossSkill>> ACTIVE = new HashMap<>();

    /**
     * Enregistre un boss avec ses skills.
     * @param bossUuid   UUID du boss
     * @param ce         CustomEntityData du boss (contient la liste skills)
     */
    public static void register(UUID bossUuid, CustomEntityData ce) {
        WaveSurvivorMod.LOGGER.info("[SkillManager] register() appelé : bossUuid={}, ce.entityName={}, ce.skills={}",
                bossUuid,
                ce != null ? ce.entityName : "null",
                ce != null ? (ce.skills != null ? ce.skills.toString() : "NULL") : "CE=NULL");

        if (ce == null) { WaveSurvivorMod.LOGGER.warn("[SkillManager] register skip : ce == null"); return; }
        if (ce.skills == null) { WaveSurvivorMod.LOGGER.warn("[SkillManager] register skip : ce.skills == null (Gson n'a pas trouvé le champ 'skills' pour '{}')", ce.entityName); return; }
        if (ce.skills.isEmpty()) { WaveSurvivorMod.LOGGER.warn("[SkillManager] register skip : ce.skills vide pour '{}'", ce.entityName); return; }

        List<BossSkill> instances = new ArrayList<>();
        for (String skillName : ce.skills) {
            if (skillName == null || skillName.isBlank()) continue;
            CustomSkillData cfg = CustomSkillRegistry.getSkill(skillName);
            if (cfg == null) {
                WaveSurvivorMod.LOGGER.warn("[SkillManager] Skill '{}' introuvable pour boss '{}'",
                        skillName, ce.entityName);
                continue;
            }
            cfg = withOverrides(cfg, ce, skillName); // réglages propres à cette entité
            cfg = translateMessages(cfg);           // messages de la compétence dans la langue du mod
            BossSkill sk = SkillFactory.create(cfg);
            if (sk != null) {
                instances.add(sk);
                WaveSurvivorMod.LOGGER.info("[SkillManager] Boss '{}' → skill '{}' (type={}, cooldown={}t)",
                        ce.entityName, cfg.skillName, cfg.skillType, sk.getCooldownTicks());
            }
        }

        if (!instances.isEmpty()) {
            // Ajout (et non remplacement) : une entité custom peut aussi recevoir des compétences d'unité de horde
            ACTIVE.computeIfAbsent(bossUuid, k -> new ArrayList<>()).addAll(instances);
        }
    }

    /**
     * Compétences ajoutées à une unité de horde (onglet Mobs du Horde Editor), avec ses réglages propres.
     * S'ajoutent à celles d'une éventuelle entité custom.
     */
    public static void registerUnitSkills(UUID uuid, String name, List<String> skills, Map<String, com.google.gson.JsonObject> overrides) {
        if (uuid == null || skills == null || skills.isEmpty()) return;
        CustomEntityData ce = new CustomEntityData();
        ce.entityName = name != null && !name.isBlank() ? name : "unité";
        ce.skills = new ArrayList<>(skills);
        ce.skillOverrides = overrides;
        register(uuid, ce);
    }

    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();

    /** Copie de la compétence avec les réglages propres à l'entité (skillOverrides) ; la compétence d'origine n'est pas modifiée. */
    private static CustomSkillData withOverrides(CustomSkillData cfg, CustomEntityData ce, String skillName) {
        if (ce.skillOverrides == null) return cfg;
        com.google.gson.JsonObject ov = ce.skillOverrides.get(skillName);
        if (ov == null || ov.size() == 0) return cfg;
        try {
            com.google.gson.JsonObject merged = GSON.toJsonTree(cfg).getAsJsonObject();
            for (var en : ov.entrySet()) merged.add(en.getKey(), en.getValue());
            return GSON.fromJson(merged, CustomSkillData.class);
        } catch (Exception ex) {
            WaveSurvivorMod.LOGGER.warn("[SkillManager] Réglages propres invalides pour '{}' sur '{}' : {}", skillName, ce.entityName, ex.getMessage());
            return cfg;
        }
    }

    /** Copie de la compétence dont les champs « …Message » sont traduits (langue du mod) ; inchangée si rien à traduire. */
    private static CustomSkillData translateMessages(CustomSkillData cfg) {
        try {
            com.google.gson.JsonObject o = GSON.toJsonTree(cfg).getAsJsonObject();
            boolean changed = false;
            for (String k : new java.util.ArrayList<>(o.keySet())) {
                if (!k.endsWith("Message") || !o.get(k).isJsonPrimitive() || !o.get(k).getAsJsonPrimitive().isString()) continue;
                String v = o.get(k).getAsString();
                String t = com.wavesurvivor.i18n.WSLang.t(v);
                if (!t.equals(v)) { o.addProperty(k, t); changed = true; }
            }
            return changed ? GSON.fromJson(o, CustomSkillData.class) : cfg;
        } catch (Exception ex) {
            return cfg;
        }
    }

    public static void unregister(UUID bossUuid) {
        if (ACTIVE.remove(bossUuid) != null) {
            WaveSurvivorMod.LOGGER.debug("[SkillManager] Unregister boss {}", bossUuid);
        }
    }

    public static void clearAll() {
        ACTIVE.clear();
        ProjectileTrailTracker.clearAll();
        GatlingScheduler.clearAll();
        WebTrapTracker.clearAll();
        DelayedActionScheduler.clearAll();
    }

    public static int activeBossCount() {
        return ACTIVE.size();
    }

    public static Map<UUID, List<BossSkill>> snapshot() {
        return new HashMap<>(ACTIVE);
    }

    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        long now = server.getTickCount();

        // Heartbeat toutes les 5s pour confirmer que le tick tourne
        if (now % 100 == 0) {
            WaveSurvivorMod.LOGGER.info("[SkillManager] tick heartbeat : {} boss actif(s)", ACTIVE.size());
        }

        var it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            LivingEntity boss = findLiving(server, e.getKey());
            if (boss == null || !boss.isAlive()) {
                WaveSurvivorMod.LOGGER.info("[SkillManager] Boss {} introuvable/mort → retiré", e.getKey());
                it.remove();
                continue;
            }
            ServerLevel level = (ServerLevel) boss.level();
            for (BossSkill sk : e.getValue()) {
                try {
                    boolean fired = sk.tickIfReady(now, boss, level);
                    if (fired) {
                        WaveSurvivorMod.LOGGER.info("[SkillManager] Boss '{}' → skill '{}' déclenché",
                                boss.getCustomName() != null ? boss.getCustomName().getString() : boss.getType(),
                                sk.getName());
                    }
                } catch (Exception ex) {
                    WaveSurvivorMod.LOGGER.error("[SkillManager] Erreur exec skill {} sur boss {}: {}",
                            sk.getName(), boss.getUUID(), ex.getMessage(), ex);
                }
            }
        }
    }

    private static LivingEntity findLiving(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(uuid);
            if (e instanceof LivingEntity living) return living;
        }
        return null;
    }
}
