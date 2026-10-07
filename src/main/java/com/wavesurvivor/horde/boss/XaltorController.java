package com.wavesurvivor.horde.boss;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.altar.AltarDefense;
import com.wavesurvivor.altar.AltarRecipes;
import com.wavesurvivor.entity.XaltorCrystal;
import com.wavesurvivor.entity.XaltorEntity;
import com.wavesurvivor.horde.HordeManager;
import com.wavesurvivor.horde.anomaly.AnomalyManager;
import com.wavesurvivor.horde.spawn.NetherSpawnFix;
import com.wavesurvivor.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * XÂL'TOR, L'ŒIL DU NÉANT (boss CustomEntity avec bossConfig.xaltorMechanics = true).
 *
 *   Phase 1 — Le Chasseur (100 → 60 %) : ses compétences (Clignement, Puits, illusions) + toutes les 15 s,
 *             2 CLONES géants (1 PV, disparaissent en un coup ou après 10 s).
 *   Phase 2 — Les Cristaux du Néant (≤ 60 %) : 4 cristaux en l'air autour de l'arène, reliés à lui par un RAYON.
 *             Tant qu'un cristal tient : INVULNÉRABLE et se soigne (1 PV/s par cristal). 3 coups par cristal.
 *   Phase 3 — L'Effondrement (≤ 30 %) : anomalies ×2,5 plus fréquentes (dont une sous chaque joueur),
 *             il ATTIRE les joueurs vers lui toutes les 4 s et SIPHONNE le Monolithe pour se soigner.
 */
public class XaltorController {

    private static final Random RNG = new Random();
    private static final String CLONE_TAG = "ws_xaltor_clone";

    private static class State {
        final UUID boss;
        final BlockPos arena;
        int phase = 1;
        long nextClones, nextPull, nextSiphon;
        final List<UUID> crystals = new ArrayList<>();
        final Map<UUID, Long> clones = new ConcurrentHashMap<>();
        boolean crystalsBroken = false;
        State(UUID boss, BlockPos arena, long now) { this.boss = boss; this.arena = arena; this.nextClones = now + 200; }
    }

    private static final Map<UUID, State> BOSSES = new ConcurrentHashMap<>();

    public static void register(LivingEntity boss, BlockPos spawnCenter) {
        BlockPos altar = AltarDefense.activePos();
        long now = boss.level().getServer() != null ? boss.level().getServer().getTickCount() : 0;
        BOSSES.put(boss.getUUID(), new State(boss.getUUID(), altar != null ? altar : spawnCenter, now));
        WaveSurvivorMod.LOGGER.info("[Xâl'Tor] Mécaniques activées pour {}", boss.getName().getString());
    }

    // ─── Tick ───

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || BOSSES.isEmpty()) return;
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        for (State s : BOSSES.values()) {
            Entity be = find(server, s.boss);
            if (!(be instanceof LivingEntity boss) || !boss.isAlive()) { cleanup(server, s); BOSSES.remove(s.boss); continue; }
            ServerLevel level = (ServerLevel) boss.level();
            float pct = boss.getHealth() / boss.getMaxHealth();

            // Clones : expiration
            s.clones.entrySet().removeIf(e -> {
                Entity c = level.getEntity(e.getKey());
                if (c == null || !c.isAlive()) return true;
                if (now >= e.getValue()) { poof(level, c); c.discard(); return true; }
                return false;
            });

            if (s.phase == 1) {
                if (now >= s.nextClones) { s.nextClones = now + 300; spawnClones(level, boss, s, now); }
                if (pct <= 0.60f) enterPhase2(level, boss, s);
            }
            if (s.phase == 2) {
                tickCrystals(level, boss, s, now);
                if (s.crystalsBroken && pct <= 0.30f) enterPhase3(level, boss, s, now);
            }
            if (s.phase == 3) tickPhase3(level, boss, s, now);
        }
    }

    // ─── Phase 1 : clones ───

    private static void spawnClones(ServerLevel level, LivingEntity boss, State s, long now) {
        for (int i = 0; i < 2; i++) {
            XaltorEntity c = ModEntities.XALTOR.get().create(level);
            if (c == null) continue;
            double a = RNG.nextDouble() * Math.PI * 2;
            double x = boss.getX() + Math.cos(a) * 4, z = boss.getZ() + Math.sin(a) * 4;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
            c.moveTo(x, y, z, RNG.nextFloat() * 360f, 0);
            AttributeInstance hp = c.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null) hp.setBaseValue(1.0);
            c.setHealth(1f);
            AttributeInstance atk = c.getAttribute(Attributes.ATTACK_DAMAGE);
            if (atk != null) atk.setBaseValue(2.0);
            c.setCustomName(boss.getCustomName());
            c.getPersistentData().putBoolean(CLONE_TAG, true);
            c.getPersistentData().putBoolean("ws_revenant", true);
            NetherSpawnFix.apply(c); // agressif, pas de téléportation aléatoire
            if (level.addFreshEntity(c)) {
                s.clones.put(c.getUUID(), now + 200);
                level.sendParticles(ParticleTypes.PORTAL, x, y + 2, z, 40, 0.4, 1.2, 0.4, 0.5);
            }
        }
        level.playSound(null, boss.blockPosition(), SoundEvents.ENDERMAN_SCREAM, SoundSource.HOSTILE, 1.5f, 0.6f);
        broadcast(level, com.wavesurvivor.i18n.WSLang.t("xaltor.clones"));
    }

    // ─── Phase 2 : cristaux ───

    private static void enterPhase2(ServerLevel level, LivingEntity boss, State s) {
        s.phase = 2;
        for (int i = 0; i < 4; i++) {
            XaltorCrystal c = ModEntities.XALTOR_CRYSTAL.get().create(level);
            if (c == null) continue;
            double a = Math.PI / 4 + Math.PI / 2 * i;
            double x = s.arena.getX() + 0.5 + Math.cos(a) * 7, z = s.arena.getZ() + 0.5 + Math.sin(a) * 7;
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
            c.moveTo(x, ground + 3.5, z, 0, 0);
            c.setBeamTarget(boss.blockPosition().above(2));
            if (level.addFreshEntity(c)) {
                s.crystals.add(c.getUUID());
                level.sendParticles(ParticleTypes.END_ROD, x, ground + 4, z, 20, 0.3, 0.3, 0.3, 0.05);
            }
        }
        level.playSound(null, boss.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 1.0f, 1.2f);
        broadcast(level, com.wavesurvivor.i18n.WSLang.t("xaltor.crystals"));
    }

    private static void tickCrystals(ServerLevel level, LivingEntity boss, State s, long now) {
        s.crystals.removeIf(id -> { Entity c = level.getEntity(id); return c == null || !c.isAlive(); });
        if (s.crystals.isEmpty()) {
            if (!s.crystalsBroken) {
                s.crystalsBroken = true;
                level.playSound(null, boss.blockPosition(), SoundEvents.ENDER_DRAGON_HURT, SoundSource.HOSTILE, 1.5f, 0.7f);
                broadcast(level, com.wavesurvivor.i18n.WSLang.t("xaltor.crystals_broken"));
            }
            return;
        }
        if (now % 5 == 0) {
            for (UUID id : s.crystals) {
                if (level.getEntity(id) instanceof XaltorCrystal c) c.setBeamTarget(boss.blockPosition().above(2));
            }
        }
        if (now % 20 == 0) {
            boss.heal(s.crystals.size());
            level.sendParticles(ParticleTypes.END_ROD, boss.getX(), boss.getY() + 3, boss.getZ(), 6, 0.4, 0.8, 0.4, 0.02);
        }
    }

    // ─── Phase 3 : l'Effondrement ───

    private static void enterPhase3(ServerLevel level, LivingEntity boss, State s, long now) {
        s.phase = 3;
        s.nextPull = now + 40;
        s.nextSiphon = now + 40;
        AnomalyManager.setIntensity(2.5);
        String horde = HordeManager.get().getActiveHorde() != null ? HordeManager.get().getActiveHorde().hordeName : null;
        AltarRecipes.Anomalies cfg = horde != null ? AltarRecipes.getAnomalies(horde) : null;
        for (ServerPlayer p : players(level, s.arena, 32)) {
            AnomalyManager.spawnAt(level, new Vec3(p.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    p.getBlockX(), p.getBlockZ()), p.getZ()), cfg);
        }
        level.playSound(null, boss.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 1.5f, 0.6f);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, boss.getX(), boss.getY() + 2, boss.getZ(), 120, 3, 2, 3, 0.3);
        broadcast(level, com.wavesurvivor.i18n.WSLang.t("xaltor.collapse"));
    }

    private static void tickPhase3(ServerLevel level, LivingEntity boss, State s, long now) {
        if (now >= s.nextPull) {
            s.nextPull = now + 80;
            for (ServerPlayer p : players(level, boss.blockPosition(), 16)) {
                if (com.wavesurvivor.item.RelicEffects.immovable(p)) continue;
                Vec3 to = boss.position().subtract(p.position());
                double d = to.length();
                if (d < 3) continue;
                Vec3 v = to.normalize().scale(Math.min(1.4, 0.35 + d * 0.06));
                p.setDeltaMovement(v.x, 0.35, v.z);
                p.hurtMarked = true;
                level.sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 12, 0.3, 0.5, 0.3, 0.5);
            }
            level.playSound(null, boss.blockPosition(), SoundEvents.ENDERMAN_STARE, SoundSource.HOSTILE, 1.4f, 0.5f);
        }
        if (now >= s.nextSiphon) {
            s.nextSiphon = now + 40;
            BlockPos altar = AltarDefense.activePos();
            if (altar != null) {
                float taken = AltarDefense.siphon(level, 6f);
                if (taken > 0) {
                    boss.heal(taken);
                    Vec3 from = Vec3.atCenterOf(altar).add(0, 0.6, 0), to = boss.position().add(0, 2.5, 0), d = to.subtract(from);
                    int steps = (int) Math.max(4, d.length() * 2);
                    for (int i = 0; i <= steps; i++) {
                        Vec3 p = from.add(d.scale((double) i / steps));
                        level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                    }
                }
            }
        }
    }

    // ─── Invulnérabilité pendant les cristaux ───

    @SubscribeEvent
    public void onAttack(LivingAttackEvent event) {
        State s = BOSSES.get(event.getEntity().getUUID());
        if (s == null || s.phase != 2 || s.crystals.isEmpty()) return;
        event.setCanceled(true);
        LivingEntity e = event.getEntity();
        if (e.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.END_ROD, e.getX(), e.getY() + 2.5, e.getZ(), 6, 0.5, 0.8, 0.5, 0.02);
            level.playSound(null, e.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 0.8f, 0.6f);
        }
        if (event.getSource().getEntity() instanceof ServerPlayer p) {
            p.displayClientMessage(com.wavesurvivor.i18n.WSLang.c("xaltor.protected"), true);
        }
    }

    // ─── Utils ───

    private static List<ServerPlayer> players(ServerLevel level, BlockPos c, double r) {
        return level.getEntitiesOfClass(ServerPlayer.class, new AABB(c).inflate(r), p -> p.isAlive() && !p.isCreative() && !p.isSpectator());
    }

    private static void poof(ServerLevel level, Entity e) {
        level.sendParticles(ParticleTypes.PORTAL, e.getX(), e.getY() + 1.5, e.getZ(), 30, 0.4, 1.0, 0.4, 0.5);
    }

    private static void cleanup(MinecraftServer server, State s) {
        for (ServerLevel lvl : server.getAllLevels()) {
            for (UUID id : s.crystals) { Entity c = lvl.getEntity(id); if (c != null) c.discard(); }
            for (UUID id : s.clones.keySet()) { Entity c = lvl.getEntity(id); if (c != null) { poof(lvl, c); c.discard(); } }
        }
        s.crystals.clear();
        s.clones.clear();
        AnomalyManager.setIntensity(1.0);
    }

    private static void broadcast(ServerLevel level, String msg) {
        Component c = Component.literal(msg);
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) p.sendSystemMessage(c);
    }

    private static Entity find(MinecraftServer server, UUID id) {
        for (ServerLevel lvl : server.getAllLevels()) {
            Entity e = lvl.getEntity(id);
            if (e != null) return e;
        }
        return null;
    }

    public static void clearAll(MinecraftServer server) {
        if (server != null) for (State s : BOSSES.values()) cleanup(server, s);
        BOSSES.clear();
    }
}
