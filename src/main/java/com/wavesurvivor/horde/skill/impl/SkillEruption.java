package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.skill.BossSkill;
import com.wavesurvivor.horde.skill.DelayedActionScheduler;
import com.wavesurvivor.horde.skill.NecroTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/**
 * eruption : sous les eruptionTargets joueurs les plus proches (dans eruptionRange), un cercle de flammes
 * sert d'avertissement pendant eruptionDelay ticks, puis un GEYSER jaillit à cet endroit :
 * dégâts (armure appliquée), feu, projection vers le haut. Sortir du cercle à temps = esquive.
 */
public class SkillEruption extends BossSkill {

    public SkillEruption(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(60, config.eruptionCooldown);
    }

    @Override
    public boolean requiresTarget() {
        return false;
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        double range = Math.max(4, config.eruptionRange);
        List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, boss.getBoundingBox().inflate(range),
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator());
        if (players.isEmpty()) return;
        players.sort(Comparator.comparingDouble(p -> p.distanceToSqr(boss)));
        int n = Math.min(players.size(), Math.max(1, config.eruptionTargets));
        int delay = Math.max(10, config.eruptionDelay);
        double radius = Math.max(0.8, config.eruptionRadius);

        for (int i = 0; i < n; i++) {
            ServerPlayer p = players.get(i);
            Vec3 spot = p.position();
            if (config.eruptionMessage != null && !config.eruptionMessage.isBlank()) {
                p.displayClientMessage(Component.literal(config.eruptionMessage), true);
            }
            // Avertissement : cercle de flammes au sol
            for (int t = 0; t < delay; t += 5) {
                DelayedActionScheduler.schedule(level.getServer(), t, () -> {
                    for (int k = 0; k < 16; k++) {
                        double a = Math.PI * 2 * k / 16;
                        level.sendParticles(ParticleTypes.FLAME, spot.x + Math.cos(a) * radius, spot.y + 0.1,
                                spot.z + Math.sin(a) * radius, 1, 0, 0.02, 0, 0);
                    }
                    level.sendParticles(ParticleTypes.LAVA, spot.x, spot.y + 0.1, spot.z, 1, radius * 0.4, 0, radius * 0.4, 0);
                }, "eruption warning");
            }
            // Geyser
            DelayedActionScheduler.schedule(level.getServer(), delay, () -> geyser(boss, level, spot, radius), "eruption geyser");
        }
        NecroTracker.playSound(level, boss.blockPosition(), config.eruptionSound, 0.7f);
    }

    private void geyser(LivingEntity boss, ServerLevel level, Vec3 spot, double radius) {
        BlockPos bp = BlockPos.containing(spot);
        level.playSound(null, bp, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.2f, 0.6f);
        level.playSound(null, bp, SoundEvents.LAVA_POP, SoundSource.HOSTILE, 1.0f, 0.8f);
        for (int h = 0; h < 12; h++) {
            level.sendParticles(ParticleTypes.FLAME, spot.x, spot.y + h * 0.35, spot.z, 6, radius * 0.35, 0.1, radius * 0.35, 0.03);
        }
        level.sendParticles(ParticleTypes.LAVA, spot.x, spot.y + 0.5, spot.z, 12, radius * 0.5, 0.3, radius * 0.5, 0.1);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, spot.x, spot.y + 2.5, spot.z, 10, 0.4, 0.8, 0.4, 0.02);

        AABB box = new AABB(spot.x - radius, spot.y - 1, spot.z - radius, spot.x + radius, spot.y + 3, spot.z + radius);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, box,
                pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
            if (p.position().distanceToSqr(spot.x, p.getY(), spot.z) > radius * radius) continue;
            p.hurt(boss.isAlive() ? level.damageSources().mobAttack(boss) : level.damageSources().inFire(),
                    (float) Math.max(0, config.eruptionDamage));
            p.setSecondsOnFire(Math.max(0, config.eruptionFireSeconds));
            if (com.wavesurvivor.item.RelicEffects.immovable(p)) continue;
            Vec3 v = p.getDeltaMovement();
            p.setDeltaMovement(v.x, Math.max(v.y, config.eruptionKnockup), v.z);
            p.hurtMarked = true;
        }
    }
}
