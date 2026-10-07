package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.skill.BossSkill;
import com.wavesurvivor.horde.skill.NecroTracker;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * wither_curse : une traînée de fumée noire relie le lanceur à sa cible, puis Wither (witherCurseAmplifier,
 * witherCurseDuration ticks) sur la cible — ou sur tous les joueurs dans witherCurseRadius autour d'elle.
 */
public class SkillWitherCurse extends BossSkill {

    public SkillWitherCurse(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(40, config.witherCurseCooldown);
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        double range = Math.max(3, config.witherCurseRange);
        if (target == null || !target.isAlive() || boss.distanceToSqr(target) > range * range) return;

        List<LivingEntity> victims = new ArrayList<>();
        if (config.witherCurseRadius > 0) {
            victims.addAll(level.getEntitiesOfClass(ServerPlayer.class, target.getBoundingBox().inflate(config.witherCurseRadius),
                    p -> p.isAlive() && !p.isCreative() && !p.isSpectator()));
        }
        if (!victims.contains(target)) victims.add(target);

        // Traînée de fumée noire lanceur → cible
        Vec3 from = boss.getEyePosition(), to = target.position().add(0, target.getBbHeight() * 0.6, 0);
        Vec3 d = to.subtract(from);
        int steps = (int) Math.max(4, d.length() * 3);
        for (int i = 0; i <= steps; i++) {
            Vec3 pt = from.add(d.scale((double) i / steps));
            level.sendParticles(ParticleTypes.SMOKE, pt.x, pt.y, pt.z, 1, 0.03, 0.03, 0.03, 0);
        }
        int dur = Math.max(20, config.witherCurseDuration);
        int amp = Math.max(0, config.witherCurseAmplifier);
        for (LivingEntity v : victims) {
            v.addEffect(new MobEffectInstance(MobEffects.WITHER, dur, amp));
            level.sendParticles(ParticleTypes.SQUID_INK, v.getX(), v.getY() + 1, v.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
            if (v instanceof ServerPlayer p && config.witherCurseMessage != null && !config.witherCurseMessage.isBlank()) {
                p.displayClientMessage(Component.literal(config.witherCurseMessage), true);
            }
        }
        NecroTracker.playSound(level, boss.blockPosition(), config.witherCurseSound, 0.8f);
    }
}
