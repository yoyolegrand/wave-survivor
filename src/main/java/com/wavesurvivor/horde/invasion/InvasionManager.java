package com.wavesurvivor.horde.invasion;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.HordeConfigMultiData;
import com.wavesurvivor.horde.HordeManager;
import com.wavesurvivor.horde.boss.BossManager;
import com.wavesurvivor.horde.model.BossWave;
import com.wavesurvivor.horde.model.HordeEntity;
import com.wavesurvivor.horde.model.SpecialWave;
import com.wavesurvivor.horde.skill.DelayedActionScheduler;
import com.wavesurvivor.horde.spawn.HordeSpawner;
import com.wavesurvivor.horde.spawn.MobRegistry;
import com.wavesurvivor.horde.stats.PlayerStats;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * INVASIONS DE VILLAGE (1.7) — une horde de mode « invasion » attaque un vrai village (hors autel).
 * Déroulé : alerte (la cloche sonne, les villageois se cachent) → préparation → vagues (nombre selon la difficulté
 * du monde et le niveau de Mauvais présage, comme le raid vanilla) → pauses entre les vagues → victoire (Héros du
 * village, butin, points de Renaissance, statistiques, succès) ou défaite (trop de villageois perdus).
 * Les envahisseurs marchent vers la cloche, ouvrent et brisent les portes, et s'en prennent aux villageois.
 * Aucun bloc n'est détruit à part les portes. Une seule invasion à la fois, jamais pendant une horde.
 */
public class InvasionManager {

    private enum Phase { PREP, FIGHT, BREAK, END }

    private static final class Inv {
        ServerLevel level;
        BlockPos center;
        Vec3 target;
        HordeConfigMultiData horde;
        HordeConfigMultiData.ConfigDataInner cfg;
        HordeConfigMultiData.InvasionSettings s;
        boolean vanilla;
        int omen, waves, wave, villagersStart, villagersAlive, waveSize;
        Phase phase = Phase.PREP;
        long phaseStart, phaseEnd, waveStart, lastSpawn, nextChaos, absentSince = -1, nextHide;
        boolean leaderGiven, won;
        BlockPos[] points;
        ServerBossEvent bar;
        final Set<UUID> invaders = new HashSet<>();
        final Set<UUID> adopted = new HashSet<>();
    }

    /** Types d'envahisseurs du raid vanilla : dans l'onglet Envahisseurs, ils remplacent les statistiques / compétences de leur type. */
    private static final Set<String> RAIDER_TYPES = Set.of("minecraft:pillager", "minecraft:vindicator", "minecraft:evoker",
            "minecraft:witch", "minecraft:ravager");

    private static Inv cur = null;
    private static long cooldownUntil = 0;
    private static final Random RNG = new Random();

    public static boolean active() { return cur != null; }

    public static boolean inCooldown(MinecraftServer srv) { return srv != null && srv.getTickCount() < cooldownUntil; }

    /** Ticks restants avant la fin du délai entre deux invasions (0 = aucun). */
    public static long cooldownRemaining(MinecraftServer srv) { return srv == null ? 0 : Math.max(0, cooldownUntil - srv.getTickCount()); }

    /** Hordes de mode « invasion » activées (celles de la roulette du Mauvais présage). */
    public static List<HordeConfigMultiData> pool() {
        List<HordeConfigMultiData> out = new ArrayList<>();
        var cfg = WaveSurvivorMod.getConfig();
        if (cfg == null || cfg.hordeConfigMulti == null) return out;
        for (HordeConfigMultiData h : cfg.hordeConfigMulti) {
            if (h != null && h.configData != null && h.configData.isInvasion() && h.configData.invasion().enabled) out.add(h);
        }
        return out;
    }

    // ─── Démarrage ───

    /** Lance une invasion près de {@code near}. Renvoie null si c'est parti, sinon la clé de texte de l'erreur. */
    public static String start(ServerLevel level, BlockPos near, HordeConfigMultiData horde, int omen) {
        MinecraftServer srv = level.getServer();
        if (!InvasionGlobal.get().enabled) return "invasion.err.disabled";
        if (cur != null) return "invasion.err.busy";
        if (HordeManager.get().isRunning()) return "invasion.err.horde";
        if (horde == null || horde.configData == null || !horde.configData.isInvasion()) return "invasion.err.no_horde";

        Inv v = new Inv();
        v.level = level;
        v.horde = horde;
        v.cfg = horde.configData;
        v.s = horde.configData.invasion();
        v.vanilla = "vanilla".equalsIgnoreCase(v.s.composition);
        v.omen = Math.max(1, Math.min(5, omen));
        v.center = findCenter(level, near);
        v.target = new Vec3(v.center.getX() + 0.5, v.center.getY(), v.center.getZ() + 0.5);
        v.villagersStart = countVillagers(level, v.center, v.s.villagerRadius);
        if (v.villagersStart < 1) return "invasion.err.no_villagers";

        int base = v.vanilla ? Composition.baseWaves(level.getDifficulty()) : Math.max(1, v.cfg.totalWaves);
        v.waves = base + (v.s.bonusWaveOmen > 0 && v.omen >= v.s.bonusWaveOmen ? 1 : 0);

        // Quatre points d'apparition autour du village, à intervalle régulier
        v.points = new BlockPos[4];
        double a0 = RNG.nextDouble() * Math.PI * 2;
        int dist = Math.max(24, v.s.spawnDistance);
        for (int i = 0; i < 4; i++) {
            double a = a0 + i * Math.PI / 2;
            int x = v.center.getX() + (int) Math.round(Math.cos(a) * dist);
            int z = v.center.getZ() + (int) Math.round(Math.sin(a) * dist);
            v.points[i] = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
        }

        long now = srv.getTickCount();
        v.phase = Phase.PREP;
        v.phaseStart = now;
        v.phaseEnd = now + Math.max(5, v.s.prepSeconds) * 20L;
        v.bar = new ServerBossEvent(Component.literal(""), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
        cur = v;

        for (ServerPlayer p : level.players()) {
            if (near(p, v)) p.sendSystemMessage(Component.literal(WSLang.t("invasion.start", WSLang.t(horde.hordeName), v.s.prepSeconds, v.waves)));
        }
        ringBell(v);
        hideVillagers(v);
        WaveSurvivorMod.LOGGER.info("[Invasion] '{}' démarrée en {} ({} villageois, {} vagues, présage {}).", horde.hordeName,
                v.center.toShortString(), v.villagersStart, v.waves, v.omen);
        return null;
    }

    /** Arrêt manuel (commande) : les envahisseurs restants disparaissent, sans récompense. */
    public static boolean stop(MinecraftServer srv) {
        if (cur == null) return false;
        cleanup(srv, false);
        return true;
    }

    // ─── Village ───

    /** Cloche du village la plus proche (point d'intérêt), sinon la position donnée. */
    private static BlockPos findCenter(ServerLevel level, BlockPos near) {
        try {
            Optional<BlockPos> bell = level.getPoiManager().findClosest(h -> h.is(PoiTypes.MEETING), near, 64, PoiManager.Occupancy.ANY);
            if (bell.isPresent()) return bell.get();
        } catch (Exception ignored) {}
        return near;
    }

    private static int countVillagers(ServerLevel level, BlockPos c, int r) {
        return level.getEntitiesOfClass(Villager.class, new AABB(c).inflate(r, 48, r), Villager::isAlive).size();
    }

    private static boolean near(ServerPlayer p, Inv v) {
        double dx = p.getX() - v.target.x, dz = p.getZ() - v.target.z;
        return dx * dx + dz * dz <= 128.0 * 128.0;
    }

    /** La cloche sonne trois fois ; les villageois du coin l'entendent (ils rentrent se cacher dans leur maison). */
    private static void ringBell(Inv v) {
        MinecraftServer srv = v.level.getServer();
        for (int i = 0; i < 3; i++) {
            DelayedActionScheduler.schedule(srv, i * 14, () -> v.level.playSound(null, v.center, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 3.0f, 1.0f), "invasion bell");
        }
    }

    private static void hideVillagers(Inv v) {
        long t = v.level.getGameTime();
        for (Villager vil : v.level.getEntitiesOfClass(Villager.class, new AABB(v.center).inflate(v.s.villagerRadius, 48, v.s.villagerRadius), Villager::isAlive)) {
            vil.getBrain().setMemory(MemoryModuleType.HEARD_BELL_TIME, t);
        }
    }

    // ─── Boucle principale (toutes les 10 ticks) ───

    public static void tick(MinecraftServer srv) {
        Inv v = cur;
        if (v == null) return;
        long now = srv.getTickCount();
        if (now % 10 != 0) return;
        if (HordeManager.get().isRunning()) { cleanup(srv, false); return; }

        adoptInvaders(v);
        v.villagersAlive = countVillagers(v.level, v.center, v.s.villagerRadius);

        // Personne près du village depuis une minute : l'invasion est abandonnée
        boolean anyone = false;
        for (ServerPlayer p : v.level.players()) if (!p.isSpectator() && Math.abs(p.getX() - v.target.x) < 200 && Math.abs(p.getZ() - v.target.z) < 200) { anyone = true; break; }
        if (anyone) v.absentSince = -1;
        else if (v.absentSince < 0) v.absentSince = now;
        else if (now - v.absentSince > 1200 && v.phase != Phase.END) {
            for (ServerPlayer p : v.level.players()) p.sendSystemMessage(Component.literal(WSLang.t("invasion.abandoned")));
            cleanup(srv, false);
            return;
        }

        if (v.phase != Phase.END) {
            if (now >= v.nextHide) { hideVillagers(v); v.nextHide = now + 100; }
            // Défaite : trop de villageois perdus (ou tous)
            int lost = v.villagersStart - v.villagersAlive;
            boolean defeat = v.villagersAlive <= 0
                    || (v.s.lossPercent < 100 && (long) lost * 100 > (long) v.s.lossPercent * v.villagersStart);
            if (defeat) { defeat(v, now); updateBar(v, now); return; }
        }

        switch (v.phase) {
            case PREP -> { if (now >= v.phaseEnd) startWave(v, 1, now); }
            case FIGHT -> {
                int alive = aliveInvaders(v);
                boolean spawned = now >= v.lastSpawn;
                boolean cleared = spawned && alive == 0 && BossManager.activeBossCount() == 0;
                boolean timeout = v.s.waveTimeoutSeconds > 0 && now - v.waveStart > v.s.waveTimeoutSeconds * 20L;
                if (cleared || timeout) {
                    if (v.wave >= v.waves) victory(v, now);
                    else {
                        v.phase = Phase.BREAK;
                        v.phaseStart = now;
                        v.phaseEnd = now + Math.max(5, v.s.waveDelaySeconds) * 20L;
                        for (ServerPlayer p : v.level.players()) if (near(p, v)) p.sendSystemMessage(Component.literal(WSLang.t("invasion.wave_done", v.wave, v.waves)));
                    }
                } else if (v.cfg.chaosEnabled && v.cfg.chaosEvents != null && !v.cfg.chaosEvents.isEmpty() && now >= v.nextChaos) {
                    var ev = v.cfg.chaosEvents.get(RNG.nextInt(v.cfg.chaosEvents.size()));
                    try { com.wavesurvivor.horde.chaos.ChaosApplier.apply(ev, v.level, v.center, srv); } catch (Exception e) {
                        WaveSurvivorMod.LOGGER.warn("[Invasion] Événement de chaos impossible : {}", e.getMessage());
                    }
                    v.nextChaos = now + chaosDelay(v);
                }
            }
            case BREAK -> { if (now >= v.phaseEnd) startWave(v, v.wave + 1, now); }
            case END -> { if (now >= v.phaseEnd) { cleanup(srv, v.won); return; } }
        }
        updateBar(v, now);
    }

    private static long chaosDelay(Inv v) {
        int min = Math.max(5, v.cfg.chaosMinInterval), max = Math.max(min, v.cfg.chaosMaxInterval);
        return (min + RNG.nextInt(max - min + 1)) * 20L;
    }

    // ─── Vagues ───

    private static void startWave(Inv v, int n, long now) {
        v.wave = n;
        v.phase = Phase.FIGHT;
        v.waveStart = now;
        v.leaderGiven = false;
        v.nextChaos = now + chaosDelay(v);
        int players = 0;
        for (ServerPlayer p : v.level.players()) if (near(p, v)) players++;
        players = Math.max(1, players);

        int[] maxShare = {0};
        int total;
        if (v.vanilla) {
            int base = v.waves - (v.s.bonusWaveOmen > 0 && v.omen >= v.s.bonusWaveOmen ? 1 : 0);
            boolean bonus = n > base;
            total = 0;
            // Un monstre d'un type du raid (pillard, vindicateur, évocateur, sorcière, ravageur) dans l'onglet Envahisseurs
            // REMPLACE les statistiques, compétences et équipement de ce type ; son nombre reste celui du raid vanilla.
            java.util.Map<String, HordeEntity> overrides = new java.util.HashMap<>();
            if (v.cfg.hordeEntities != null) {
                for (HordeEntity t : v.cfg.hordeEntities) if (t != null && RAIDER_TYPES.contains(t.entityType)) overrides.put(t.entityType, t);
            }
            for (Composition.Group g : Composition.vanilla(n, base, bonus, v.level.getDifficulty(), RNG)) {
                HordeEntity t = overrides.get(g.entityType());
                total += spawnSplit(v, t != null ? t : vanillaTemplate(g.entityType(), v.s), g.count(), players, maxShare);
            }
            // …et les autres monstres, les vagues spéciales et le Chef de la horde s'ajoutent au raid.
            total += spawnHorde(v, n, players, maxShare, true);
        } else {
            total = spawnHorde(v, n, players, maxShare, false);
        }
        v.waveSize = Math.max(1, total);
        v.lastSpawn = now + 40 + (maxShare[0] > 25 ? 60L * ((maxShare[0] + 24) / 25) : 0);

        for (ServerPlayer p : v.level.players()) {
            if (!near(p, v)) continue;
            p.sendSystemMessage(Component.literal(WSLang.t("invasion.wave", n, v.waves)));
            v.level.playSound(null, p.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.6f, 0.8f);
        }
    }

    /** Vague de la horde : monstres, vague spéciale (tirage) et boss de cette vague. */
    private static int spawnHorde(Inv v, int wave, int players, int[] maxShare, boolean skipRaiders) {
        int total = 0;
        if (v.cfg.hordeEntities != null) {
            for (HordeEntity t : v.cfg.hordeEntities) {
                // Composition vanilla : les types du raid sont des remplacements, pas des ajouts
                if (skipRaiders && RAIDER_TYPES.contains(t.entityType)) continue;
                total += spawnSplit(v, t, t.getCountForWave(wave, players), players, maxShare);
            }
        }
        SpecialWave sw = pickSpecial(v.cfg, wave);
        if (sw != null && sw.entities != null) {
            for (SpecialWave.SpecialWaveEntity swe : sw.entities) {
                HordeEntity h = swe.toHordeEntity();
                total += spawnSplit(v, h, h.getCountForWave(wave, players), players, maxShare);
            }
            for (ServerPlayer p : v.level.players()) if (near(p, v)) p.sendSystemMessage(Component.literal(WSLang.t("invasion.special", WSLang.t(sw.name))));
        }
        if (v.cfg.useBossWaves && v.cfg.bossWaves != null) {
            for (BossWave b : v.cfg.bossWaves) {
                if (b.waveNumber != wave) continue;
                if (BossManager.spawnBoss(v.level, b, v.points[0], 8, v.level.getServer())) {
                    total++;
                    for (ServerPlayer p : v.level.players()) if (near(p, v)) p.sendSystemMessage(Component.literal(WSLang.t("invasion.boss", WSLang.t(b.bossName))));
                }
            }
        }
        return total;
    }

    private static SpecialWave pickSpecial(HordeConfigMultiData.ConfigDataInner cfg, int wave) {
        if (!cfg.useSpecialWaves || cfg.specialWaves == null || cfg.specialWaves.isEmpty()) return null;
        List<SpecialWave> pool = new ArrayList<>();
        for (SpecialWave sw : cfg.specialWaves) if (sw.chance > 0 && sw.allowedAt(wave)) pool.add(sw);
        if (pool.isEmpty()) return null;
        if (cfg.specialWaveChance > 0 && RNG.nextInt(100) >= cfg.specialWaveChance) return null;
        int total = 0;
        for (SpecialWave sw : pool) total += sw.chance;
        if (total <= 0) return null;
        int r = RNG.nextInt(total), cum = 0;
        for (SpecialWave sw : pool) { cum += sw.chance; if (r < cum) return sw; }
        return null;
    }

    /** Répartit {@code n} unités sur les quatre points d'apparition. */
    private static int spawnSplit(Inv v, HordeEntity t, int n, int players, int[] maxShare) {
        if (n <= 0 || t == null) return 0;
        int pts = v.points.length;
        for (int i = 0; i < pts; i++) {
            int share = n / pts + (i < n % pts ? 1 : 0);
            if (share <= 0) continue;
            maxShare[0] = Math.max(maxShare[0], share);
            HordeSpawner.spawnMobs(v.level, t, v.points[i], 8, share, players);
        }
        return n;
    }

    /** Modèle d'unité pour un envahisseur vanilla (statistiques vanilla ; équipement vanilla laissé tel quel). */
    private static HordeEntity vanillaTemplate(String type, HordeConfigMultiData.InvasionSettings s) {
        HordeEntity t = new HordeEntity();
        t.entityType = type;
        t.lootTable = new ArrayList<>();
        switch (type) {
            case "minecraft:ravager" -> { t.maxHealth = 100; t.attackDamage = 12; t.movementSpeed = 0.3; t.knockbackResistance = 0.75; }
            case "minecraft:evoker" -> { t.maxHealth = 24; t.attackDamage = 6; t.movementSpeed = 0.5; }
            case "minecraft:witch" -> { t.maxHealth = 26; t.attackDamage = 2; t.movementSpeed = 0.25; }
            default -> { t.maxHealth = 24; t.attackDamage = 5; t.movementSpeed = 0.35; } // pillard, vindicateur
        }
        // Multiplicateurs réglés dans l'éditeur (1 = valeurs vanilla)
        t.maxHealth *= Math.max(0.1, s.vanillaHealthMult);
        t.attackDamage *= Math.max(0.1, s.vanillaDamageMult);
        t.followRange = 64;
        return t;
    }

    // ─── Envahisseurs ───

    /** Toute unité de horde apparue pendant l'invasion en fait partie : buts (cloche, portes, villageois) et suivi. */
    private static void adoptInvaders(Inv v) {
        List<UUID> ids = new ArrayList<>();
        for (var e : MobRegistry.entries()) ids.add(e.getKey());
        for (UUID id : ids) {
            if (!v.adopted.add(id)) continue;
            Entity e = v.level.getEntity(id);
            if (e == null) { v.adopted.remove(id); continue; }
            v.invaders.add(id);
            e.getPersistentData().putBoolean("ws_invasion", true);
            if (e instanceof Mob mob) {
                addGoals(mob, v.target, v.s.openDoors, v.s.breakDoors, true);
                // Vanilla : le premier chef de la vague porte la bannière du Mauvais présage (qu'il peut lâcher)
                if (v.vanilla && !v.leaderGiven && mob instanceof AbstractIllager) {
                    v.leaderGiven = true;
                    mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, Raid.getLeaderBannerInstance());
                    mob.setDropChance(net.minecraft.world.entity.EquipmentSlot.HEAD, 2.0f);
                }
            }
        }
    }

    /**
     * Buts d'un envahisseur : marche vers la cloche et vise les villageois ({@code toVillage}), ouvre / brise les portes.
     * Les deux groupes ont chacun leur marque, pour ne jamais être ajoutés deux fois.
     */
    public static void addGoals(Mob mob, Vec3 target, boolean open, boolean brk, boolean toVillage) {
        var tag = mob.getPersistentData();
        try {
            if (toVillage && target != null && !tag.getBoolean("ws_inv_village")) {
                tag.putBoolean("ws_inv_village", true);
                mob.goalSelector.addGoal(6, new MoveToVillageGoal(mob, target, 1.0));
                mob.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(mob, AbstractVillager.class, true));
            }
            if ((open || brk) && !tag.getBoolean("ws_inv_doors") && mob.getNavigation() instanceof GroundPathNavigation g) {
                tag.putBoolean("ws_inv_doors", true);
                if (open) {
                    g.setCanOpenDoors(true);
                    mob.goalSelector.addGoal(2, new OpenDoorGoal(mob, false));
                }
                if (brk) mob.goalSelector.addGoal(3, new BreakDoorGoal(mob, d -> true));
            }
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.warn("[Invasion] Buts d'envahisseur impossibles : {}", e.getMessage());
        }
    }

    private static int aliveInvaders(Inv v) {
        int n = 0;
        for (UUID id : v.invaders) {
            Entity e = v.level.getEntity(id);
            if (e != null && e.isAlive()) n++;
        }
        return n;
    }

    // ─── Fin ───

    private static void victory(Inv v, long now) {
        v.phase = Phase.END;
        v.won = true;
        v.phaseEnd = now + 200;
        v.level.playSound(null, v.center, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 3.0f, 1.0f);
        boolean flawless = v.villagersAlive >= v.villagersStart;
        for (ServerPlayer p : v.level.players()) {
            if (!near(p, v) || p.isSpectator()) continue;
            if (v.s.heroSeconds > 0) {
                p.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, v.s.heroSeconds * 20, Math.max(0, v.omen - 1), false, false, true));
            }
            p.sendSystemMessage(Component.literal(WSLang.t("invasion.victory", v.s.heroSeconds / 60, Math.max(1, v.omen))));
            giveRewards(p, v.omen, v.s.rewardEmeralds, v.s.rewardKeys, v.s.rewardPr, v.s.lootCsv);
            PlayerStats.add(p, "invasions_won", 1);
            if (flawless) PlayerStats.add(p, "invasions_flawless", 1);
        }
        cooldownUntil = now + Math.max(0, InvasionGlobal.get().cooldownSeconds) * 20L;
    }

    private static void defeat(Inv v, long now) {
        v.phase = Phase.END;
        v.won = false;
        v.phaseEnd = now + 100;
        for (ServerPlayer p : v.level.players()) if (near(p, v)) p.sendSystemMessage(Component.literal(WSLang.t("invasion.defeat")));
        cooldownUntil = now + Math.max(0, InvasionGlobal.get().cooldownSeconds) * 20L;
    }

    /** Retire les envahisseurs restants, la barre et les restes d'événements. */
    private static void cleanup(MinecraftServer srv, boolean won) {
        Inv v = cur;
        if (v == null) return;
        cur = null;
        try {
            for (UUID id : v.invaders) {
                Entity e = v.level.getEntity(id);
                if (e != null && e.isAlive()) e.discard();
                MobRegistry.remove(id);
            }
            BossManager.clearAll(srv);
            com.wavesurvivor.horde.frost.StormManager.clearAll();
            com.wavesurvivor.horde.quake.QuakeManager.clearAll();
            com.wavesurvivor.horde.chaos.ChaosTracker.clearAll(srv);
            com.wavesurvivor.horde.spawn.SpawnZone.clear();
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.warn("[Invasion] Nettoyage incomplet : {}", e.getMessage());
        }
        if (v.bar != null) v.bar.removeAllPlayers();
        WaveSurvivorMod.LOGGER.info("[Invasion] Terminée ({}).", won ? "victoire" : "fin");
    }

    /** Arrêt du serveur : plus de barre, plus d'envahisseurs. */
    public static void shutdown(MinecraftServer srv) {
        if (cur != null) cleanup(srv, false);
    }

    // ─── Barre d'état ───

    private static void updateBar(Inv v, long now) {
        if (v.bar == null) return;
        String name = WSLang.t(v.horde.hordeName);
        String title;
        float progress;
        int shown = Math.max(1, v.wave);
        switch (v.phase) {
            case PREP -> {
                int sec = (int) Math.max(0, (v.phaseEnd - now) / 20);
                title = WSLang.t("invasion.bar_prep", name, v.villagersAlive, v.villagersStart, sec);
                progress = 1f - (float) (now - v.phaseStart) / Math.max(1, v.phaseEnd - v.phaseStart);
            }
            case BREAK -> {
                int sec = (int) Math.max(0, (v.phaseEnd - now) / 20);
                title = WSLang.t("invasion.bar_break", name, v.villagersAlive, v.villagersStart, v.wave, v.waves, sec);
                progress = 1f - (float) (now - v.phaseStart) / Math.max(1, v.phaseEnd - v.phaseStart);
            }
            case END -> {
                title = WSLang.t(v.won ? "invasion.bar_won" : "invasion.bar_lost", name);
                progress = v.won ? 1f : 0f;
            }
            default -> {
                title = WSLang.t("invasion.bar", name, v.villagersAlive, v.villagersStart, shown, v.waves);
                progress = Math.min(1f, (float) aliveInvaders(v) / v.waveSize);
            }
        }
        v.bar.setName(Component.literal(title));
        v.bar.setProgress(Math.max(0f, Math.min(1f, progress)));
        v.bar.setColor(v.phase == Phase.END && v.won ? BossEvent.BossBarColor.GREEN : BossEvent.BossBarColor.RED);
        for (ServerPlayer p : v.level.players()) {
            if (near(p, v)) v.bar.addPlayer(p);
            else v.bar.removePlayer(p);
        }
    }

    // ─── Récompenses (invasion du mod et raid vanilla) ───

    /** Donne émeraudes (× présage), clés de coffre mystère, points de Renaissance et butin supplémentaire. */
    public static void giveRewards(ServerPlayer p, int omen, int emeralds, int keys, int pr, String lootCsv) {
        List<String> got = new ArrayList<>();
        int em = Math.max(0, emeralds) * Math.max(1, omen);
        if (em > 0) { give(p, com.wavesurvivor.horde.loot.LootItems.resolve("minecraft:emerald", em)); got.add(em + "× " + WSLang.t("invasion.loot.emerald")); }
        if (keys > 0) { give(p, com.wavesurvivor.horde.loot.LootItems.resolve("roulettechest:key_coffre_mysterieux", keys)); got.add(keys + "× " + WSLang.t("invasion.loot.key")); }
        if (lootCsv != null && !lootCsv.isBlank()) {
            for (String tok : lootCsv.split(",")) {
                String t = tok.trim();
                if (t.isEmpty()) continue;
                int chance = 100, min = 1, max = 1;
                int at = t.indexOf('@');
                if (at >= 0) { try { chance = Integer.parseInt(t.substring(at + 1).trim()); } catch (NumberFormatException ignored) {} t = t.substring(0, at); }
                int star = t.indexOf('*');
                if (star >= 0) {
                    String q = t.substring(star + 1).trim();
                    t = t.substring(0, star);
                    try {
                        int dash = q.indexOf('-');
                        if (dash > 0) { min = Integer.parseInt(q.substring(0, dash).trim()); max = Integer.parseInt(q.substring(dash + 1).trim()); }
                        else min = max = Integer.parseInt(q);
                    } catch (NumberFormatException ignored) {}
                }
                if (RNG.nextInt(100) >= chance) continue;
                int qty = min + (max > min ? RNG.nextInt(max - min + 1) : 0);
                ItemStack st = com.wavesurvivor.horde.loot.LootItems.resolve(t.trim(), qty);
                if (st.isEmpty()) continue;
                give(p, st);
                got.add(st.getCount() + "× " + st.getHoverName().getString());
            }
        }
        if (pr > 0) {
            com.wavesurvivor.horde.renaissance.RenaissanceStore.addPoints(p, pr);
            got.add(pr + " " + WSLang.t("invasion.loot.pr"));
        }
        if (!got.isEmpty()) p.sendSystemMessage(Component.literal(WSLang.t("invasion.reward", String.join(", ", got))));
    }

    private static void give(ServerPlayer p, ItemStack st) {
        if (st == null || st.isEmpty()) return;
        if (!p.getInventory().add(st)) p.drop(st, false);
    }

    // ─── Commande /ws invasion ───

    /** Lance une invasion sur le village où se trouve le joueur. Renvoie null si c'est parti, sinon la clé de l'erreur. */
    public static String startForPlayer(ServerPlayer p, HordeConfigMultiData horde, int omen) {
        ServerLevel level = p.serverLevel();
        if (!level.isVillage(p.blockPosition())) return "invasion.err.not_village";
        return start(level, p.blockPosition(), horde, omen);
    }
}
