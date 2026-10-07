package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.WaveSurvivorMod;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Tracker qui gère toute la phase de charge du Supernova :
 *   - Particules "aura" tournantes au-dessus de la tête (indicateur visuel)
 *   - Particules ambient autour du corps
 *   - Sons progressifs (ding qui accélère + monte en pitch, façon countdown de bombe)
 *   - Explosion finale à la fin du délai
 *
 * Un seul boss peut être trackable à la fois (Map par UUID).
 * Register via MinecraftForge.EVENT_BUS.register(new SupernovaTracker()) dans WaveSurvivorMod.
 */
public class SupernovaTracker {

    private static final Map<UUID, ChargeState> ACTIVE = new HashMap<>();

    public static class ChargeState {
        public final ServerLevel level;
        public final LivingEntity mob;
        public final long startTick;
        public final long endTick;
        public final ParticleOptions chargeParticle;
        public final double explosionPower;
        public final Level.ExplosionInteraction explosionMode;
        public long lastSoundTick;

        public ChargeState(ServerLevel level, LivingEntity mob, long startTick, long endTick,
                            ParticleOptions chargeParticle, double explosionPower,
                            Level.ExplosionInteraction explosionMode) {
            this.level = level;
            this.mob = mob;
            this.startTick = startTick;
            this.endTick = endTick;
            this.chargeParticle = chargeParticle;
            this.explosionPower = explosionPower;
            this.explosionMode = explosionMode;
            this.lastSoundTick = startTick;
        }
    }

    /** Kicks off the charge. Called from SkillSupernova.execute() */
    public static void register(ServerLevel level, LivingEntity mob, long delayTicks,
                                 ParticleOptions particle, double explosionPower,
                                 Level.ExplosionInteraction explosionMode) {
        long start = level.getGameTime();
        long end = start + delayTicks;
        ACTIVE.put(mob.getUUID(), new ChargeState(level, mob, start, end, particle, explosionPower, explosionMode));

        // Son initial d'alerte : beacon activate (fort, remarquable)
        level.playSound(null, mob.blockPosition(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 2.5f, 0.6f);
        WaveSurvivorMod.LOGGER.info("[Supernova] '{}' entre en charge — explosion dans {}t (power {})",
                mob.getCustomName() != null ? mob.getCustomName().getString() : mob.getType().toString(),
                delayTicks, explosionPower);
    }

    public static void cancel(UUID mobId) {
        ACTIVE.remove(mobId);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (ACTIVE.isEmpty()) return;

        Iterator<Map.Entry<UUID, ChargeState>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ChargeState> entry = it.next();
            ChargeState cs = entry.getValue();

            // Mob mort → cleanup sans explosion
            if (cs.mob == null || cs.mob.isRemoved() || !cs.mob.isAlive()) {
                it.remove();
                continue;
            }

            long now = cs.level.getGameTime();

            // Explosion à la fin de la charge
            if (now >= cs.endTick) {
                cs.level.explode(cs.mob, cs.mob.getX(), cs.mob.getY(), cs.mob.getZ(),
                        (float) cs.explosionPower, cs.explosionMode);
                // Son d'explosion additionnel (plus grave, doom)
                cs.level.playSound(null, cs.mob.blockPosition(),
                        SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 2.0f, 0.5f);
                // Le boss meurt de son explosion
                cs.mob.hurt(cs.level.damageSources().generic(), cs.mob.getMaxHealth() * 10);
                it.remove();
                WaveSurvivorMod.LOGGER.info("[Supernova] '{}' EXPLOSE (power {})",
                        cs.mob.getName().getString(), cs.explosionPower);
                continue;
            }

            // ---- Particules ----
            spawnCrownParticles(cs, now);
            spawnBodyParticles(cs);

            // ---- Sons progressifs (tic-tac qui accélère + monte en pitch) ----
            long remaining = cs.endTick - now;
            long soundInterval;
            if (remaining > 40) soundInterval = 15;       // ~1.3 sons/s au début
            else if (remaining > 20) soundInterval = 8;   // ~2.5 sons/s au milieu
            else soundInterval = 4;                        // 5 sons/s à la fin (urgent)

            if (now - cs.lastSoundTick >= soundInterval) {
                float progress = 1.0f - (float) remaining / Math.max(1, cs.endTick - cs.startTick);
                float pitch = 0.7f + progress * 1.5f; // 0.7 → 2.2
                cs.level.playSound(null, cs.mob.blockPosition(),
                        SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.HOSTILE, 1.5f, pitch);
                cs.lastSoundTick = now;
            }
        }
    }

    /** Particules "couronne" tournantes au-dessus de la tête — indicateur visuel principal. */
    private static void spawnCrownParticles(ChargeState cs, long now) {
        Vec3 pos = cs.mob.position();
        double crownY = pos.y + cs.mob.getBbHeight() + 0.8;
        // Cercle tournant : 6 particules par tick, ring qui pivote
        double angleOffset = (now % 60) * (Math.PI * 2 / 60);
        for (int i = 0; i < 6; i++) {
            double angle = (Math.PI * 2 / 6) * i + angleOffset;
            double dx = Math.cos(angle) * 1.2;
            double dz = Math.sin(angle) * 1.2;
            cs.level.sendParticles(cs.chargeParticle,
                    pos.x + dx, crownY, pos.z + dz,
                    1, 0, 0, 0, 0.02);
        }
        // Une flamme centrale plus haute pour l'attention
        cs.level.sendParticles(ParticleTypes.FLAME,
                pos.x, crownY + 0.5, pos.z,
                2, 0.1, 0.1, 0.1, 0.03);
    }

    /** Particules ambient autour du corps (charge visible sur tout le mob). */
    private static void spawnBodyParticles(ChargeState cs) {
        Vec3 pos = cs.mob.position();
        double bodyH = cs.mob.getBbHeight();
        cs.level.sendParticles(cs.chargeParticle,
                pos.x, pos.y + bodyH * 0.5, pos.z,
                4, 0.4, bodyH * 0.4, 0.4, 0.05);
    }
}
