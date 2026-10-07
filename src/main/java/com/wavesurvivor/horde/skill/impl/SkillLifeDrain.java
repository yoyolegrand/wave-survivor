package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.skill.BossSkill;
import com.wavesurvivor.horde.skill.DelayedActionScheduler;
import com.wavesurvivor.horde.skill.NecroTracker;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * life_drain : canal de lifeDrainDuration ticks. Toutes les 10 ticks, un rayon de particules d'âmes
 * relie le lanceur à la cible : la cible subit lifeDrainDamage, le lanceur est soigné (× healRatio).
 * Le canal s'interrompt si la cible sort de portée, meurt, ou si le lanceur meurt.
 */
public class SkillLifeDrain extends BossSkill {

    private static final int PULSE = 10;

    public SkillLifeDrain(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(60, config.lifeDrainCooldown);
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        double range = Math.max(3, config.lifeDrainRange);
        if (target == null || boss.distanceToSqr(target) > range * range) return;

        if (target instanceof ServerPlayer p && config.lifeDrainMessage != null && !config.lifeDrainMessage.isBlank()) {
            p.sendSystemMessage(Component.literal(config.lifeDrainMessage));
        }
        NecroTracker.playSound(level, boss.blockPosition(), config.lifeDrainSound, 0.7f);

        int pulses = Math.max(1, config.lifeDrainDuration / PULSE);
        for (int i = 0; i < pulses; i++) {
            DelayedActionScheduler.schedule(level.getServer(), i * PULSE, () -> pulse(boss, target, level, range),
                    "life_drain pulse");
        }
    }

    private void pulse(LivingEntity boss, LivingEntity target, ServerLevel level, double range) {
        if (!boss.isAlive() || !target.isAlive() || boss.level() != target.level()) return;
        if (boss.distanceToSqr(target) > range * range) return;

        // Rayon de particules du lanceur vers la cible
        Vec3 from = boss.getEyePosition();
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.6, 0);
        Vec3 d = to.subtract(from);
        int steps = (int) Math.max(4, d.length() * 3);
        for (int i = 0; i <= steps; i++) {
            Vec3 pt = from.add(d.scale((double) i / steps));
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pt.x, pt.y, pt.z, 1, 0.02, 0.02, 0.02, 0);
        }
        level.sendParticles(ParticleTypes.SOUL, to.x, to.y, to.z, 4, 0.2, 0.2, 0.2, 0.01);

        float dmg = (float) Math.max(0.5, config.lifeDrainDamage);
        target.hurt(level.damageSources().indirectMagic(boss, boss), dmg);
        boss.heal((float) (dmg * Math.max(0, config.lifeDrainHealRatio)));
        level.sendParticles(ParticleTypes.HEART, boss.getX(), boss.getY() + boss.getBbHeight() + 0.3, boss.getZ(),
                1, 0.2, 0.1, 0.2, 0);
    }
}
