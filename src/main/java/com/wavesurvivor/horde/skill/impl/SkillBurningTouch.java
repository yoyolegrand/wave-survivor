package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.skill.BossSkill;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * burning_touch : aura PASSIVE. Toutes les burningTouchInterval ticks, le lanceur est entouré de flammes et
 * enflamme (burningTouchFireSeconds) + blesse (burningTouchDamage) les joueurs à moins de burningTouchRadius.
 * Pas besoin de cible : l'aura tourne en permanence (Diablotin, créatures de lave...).
 */
public class SkillBurningTouch extends BossSkill {

    public SkillBurningTouch(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(10, config.burningTouchInterval);
    }

    @Override
    public boolean requiresTarget() {
        return false;
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        double r = Math.max(0.8, config.burningTouchRadius);
        level.sendParticles(ParticleTypes.FLAME, boss.getX(), boss.getY() + boss.getBbHeight() * 0.5, boss.getZ(),
                6, boss.getBbWidth() * 0.6, boss.getBbHeight() * 0.4, boss.getBbWidth() * 0.6, 0.01);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, boss.getBoundingBox().inflate(r),
                pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
            if (p.distanceToSqr(boss) > (r + boss.getBbWidth()) * (r + boss.getBbWidth())) continue;
            p.setSecondsOnFire(Math.max(1, config.burningTouchFireSeconds));
            if (config.burningTouchDamage > 0) p.hurt(level.damageSources().mobAttack(boss), (float) config.burningTouchDamage);
            level.sendParticles(ParticleTypes.SMALL_FLAME, p.getX(), p.getY() + 1, p.getZ(), 6, 0.3, 0.5, 0.3, 0.02);
        }
    }
}
