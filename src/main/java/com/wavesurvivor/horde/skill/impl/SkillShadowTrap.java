package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.skill.BossSkill;
import com.wavesurvivor.horde.skill.DelayedActionScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.Random;

/**
 * shadow_trap : pose un piège visible au sol à la position du joueur.
 * Pendant shadowTrapDelay ticks : particules pulsées régulièrement (colonne + spirale au sol),
 * puis téléporte le joueur dans un rayon et déclenche une explosion optionnelle.
 * Le joueur peut fuir (s'éloigner du trapPos ne fait rien, le TP est basé sur la position DU PIÈGE).
 */
public class SkillShadowTrap extends BossSkill {

    private static final Random RNG = new Random();

    public SkillShadowTrap(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(40, config.shadowTrapCooldown);
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        if (!(target instanceof ServerPlayer sp)) return;
        final BlockPos trapPos = sp.blockPosition();
        final ParticleOptions particle = resolveParticle(config.shadowTrapParticle, ParticleTypes.SCULK_SOUL);
        final int delay = Math.max(1, config.shadowTrapDelay);

        // Son initial fort d'alerte
        level.playSound(null, trapPos, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2.0f, 0.6f);
        if (config.shadowTrapSound != null && !config.shadowTrapSound.isBlank()) {
            try {
                SoundEvent snd = BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation(config.shadowTrapSound));
                if (snd != null) {
                    level.playSound(null, trapPos, snd, SoundSource.HOSTILE, 1.5f, 1.0f);
                }
            } catch (Exception ignore) {}
        }

        // Message chat au joueur
        if (config.shadowTrapMessage != null && !config.shadowTrapMessage.isBlank()) {
            sp.sendSystemMessage(Component.literal(config.shadowTrapMessage));
        }

        // Burst initial visible : cercle de particules au sol
        spawnRingParticles(level, trapPos, particle, 2.0, 30);
        spawnColumnParticles(level, trapPos, particle, 20);

        // Bursts pulsés toutes les 5 ticks pendant le delay pour visibilité continue
        final ServerLevel finalLevel = level;
        int interval = 5;
        int nBursts = delay / interval;
        for (int i = 1; i <= nBursts; i++) {
            final int stepIndex = i;
            DelayedActionScheduler.schedule(level.getServer(), i * interval, () -> {
                // Progression : rayon grandit + count augmente + tick sound
                double radius = 1.5 + (stepIndex / (double) nBursts) * 1.5;
                spawnRingParticles(finalLevel, trapPos, particle, radius, 20);
                spawnColumnParticles(finalLevel, trapPos, particle, 8);
                // Secondary particle plus visible (portal blanc qui monte)
                finalLevel.sendParticles(ParticleTypes.REVERSE_PORTAL,
                        trapPos.getX() + 0.5, trapPos.getY() + 0.3, trapPos.getZ() + 0.5,
                        8, 0.8, 0.5, 0.8, 0.05);
                // Tick sound qui accélère
                float pitch = 0.5f + (float) stepIndex / nBursts * 1.5f;
                finalLevel.playSound(null, trapPos, SoundEvents.SCULK_CLICKING,
                        SoundSource.HOSTILE, 1.0f, pitch);
            }, "shadow_trap pulse " + i);
        }

        // Schedule trigger TP à la fin
        final ServerPlayer finalSp = sp;
        final LivingEntity finalBoss = boss;

        DelayedActionScheduler.schedule(level.getServer(), delay, () -> {
            if (!finalSp.isAlive()) return;

            // Effet visuel + son de TP
            finalLevel.sendParticles(ParticleTypes.PORTAL,
                    finalSp.getX(), finalSp.getY() + 1.0, finalSp.getZ(),
                    40, 0.5, 1.0, 0.5, 0.5);
            finalLevel.playSound(null, finalSp.blockPosition(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.5f, 0.7f);

            // Téléportation dans le rayon
            double r = Math.max(2, config.shadowTrapTeleportRadius);
            for (int i = 0; i < 10; i++) {
                double angle = RNG.nextDouble() * Math.PI * 2;
                double dist = r * (0.3 + RNG.nextDouble() * 0.7);
                double x = trapPos.getX() + Math.cos(angle) * dist + 0.5;
                double z = trapPos.getZ() + Math.sin(angle) * dist + 0.5;
                double y = trapPos.getY();
                if (finalSp.randomTeleport(x, y, z, true)) break;
            }

            // Explosion optionnelle
            String mode = config.shadowTrapExplosionMode == null ? "none" : config.shadowTrapExplosionMode.toLowerCase();
            if (!"none".equals(mode)) {
                Level.ExplosionInteraction interaction = switch (mode) {
                    case "break" -> Level.ExplosionInteraction.MOB;
                    case "destroy" -> Level.ExplosionInteraction.TNT;
                    default -> Level.ExplosionInteraction.NONE;
                };
                finalLevel.explode(finalBoss.isAlive() ? finalBoss : null,
                        trapPos.getX() + 0.5, trapPos.getY(), trapPos.getZ() + 0.5,
                        (float) config.shadowTrapExplosionPower, interaction);
            }
        }, "shadow_trap trigger @ " + trapPos);

        WaveSurvivorMod.LOGGER.info("[Skill shadow_trap] '{}' pose piège sur '{}' — trigger dans {}t",
                boss.getCustomName() != null ? boss.getCustomName().getString() : boss.getType().toString(),
                sp.getName().getString(), delay);
    }

    /** Cercle de particules au sol autour du trap. */
    private static void spawnRingParticles(ServerLevel level, BlockPos center, ParticleOptions p, double radius, int count) {
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2 / count) * i;
            double x = center.getX() + 0.5 + Math.cos(angle) * radius;
            double z = center.getZ() + 0.5 + Math.sin(angle) * radius;
            level.sendParticles(p, x, center.getY() + 0.1, z, 1, 0, 0, 0, 0);
        }
    }

    /** Colonne verticale de particules du sol vers le haut. */
    private static void spawnColumnParticles(ServerLevel level, BlockPos center, ParticleOptions p, int count) {
        for (int i = 0; i < count; i++) {
            double y = center.getY() + RNG.nextDouble() * 3.0;
            double xOff = (RNG.nextDouble() - 0.5) * 0.6;
            double zOff = (RNG.nextDouble() - 0.5) * 0.6;
            level.sendParticles(p,
                    center.getX() + 0.5 + xOff, y, center.getZ() + 0.5 + zOff,
                    1, 0, 0.05, 0, 0.02);
        }
    }

    private static ParticleOptions resolveParticle(String id, SimpleParticleType fallback) {
        if (id == null || id.isBlank()) return fallback;
        try {
            Object p = BuiltInRegistries.PARTICLE_TYPE.get(new ResourceLocation(id));
            if (p instanceof ParticleOptions po) return po;
        } catch (Exception ignore) {}
        return fallback;
    }
}
