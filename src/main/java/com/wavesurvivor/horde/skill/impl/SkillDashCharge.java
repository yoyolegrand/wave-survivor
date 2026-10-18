package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.skill.BossSkill;
import com.wavesurvivor.horde.skill.DelayedActionScheduler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * dash_charge : le boss fonce sur la cible avec une impulsion, damage bonus à l'impact.
 * Aussi appelé "shoulder charge".
 */
public class SkillDashCharge extends BossSkill {

    public SkillDashCharge(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(20, config.dashDelay);
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        double dist = boss.distanceTo(target);
        if (dist < config.dashMinDistance) return;

        Vec3 dir = target.position().subtract(boss.position()).normalize();
        Vec3 push = new Vec3(dir.x * config.dashPower, 0.35, dir.z * config.dashPower);
        boss.setDeltaMovement(push);
        boss.hurtMarked = true;

        // Resistance temporaire pendant le dash
        int resistTicks = Math.max(10, config.dashResistanceDuration);
        boss.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, resistTicks, 3, false, false));

        // 1.7 : le bonus de dégâts n'est plus instantané (il touchait à coup sûr, même sans contact, et pouvait tuer d'un coup) :
        // il n'est infligé que si le boss ARRIVE au contact de la cible pendant la charge, une seule fois. Esquiver = aucun dégât.
        double bonusDamage = getBossAttackDamage(boss) * config.dashDamageMultiplier;
        checkImpact(boss, target, level, (float) bonusDamage, 0);

        playSound(level, boss, config.dashSound);
        WaveSurvivorMod.LOGGER.debug("[Skill dash] '{}' fonce sur {} (dist {}, bonus dmg {})",
                boss.getCustomName() != null ? boss.getCustomName().getString() : boss.getType(),
                target.getName().getString(), (int) dist, (int) bonusDamage);
    }

    /** Durée de la charge pendant laquelle l'impact est testé (ticks) et pas de test (ticks). */
    private static final int CHARGE_TICKS = 16, STEP = 2;

    private void checkImpact(LivingEntity boss, LivingEntity target, ServerLevel level, float damage, int elapsed) {
        DelayedActionScheduler.schedule(level.getServer(), STEP, () -> {
            if (!boss.isAlive() || !target.isAlive()) return;
            // Contact : le boss touche la cible (marge légère pour une charge à pleine vitesse)
            if (boss.getBoundingBox().inflate(0.6).intersects(target.getBoundingBox())) {
                target.hurt(boss.damageSources().mobAttack(boss), damage);
                playSound(level, boss, config.dashImpactSound);
                return;
            }
            if (elapsed + STEP < CHARGE_TICKS) checkImpact(boss, target, level, damage, elapsed + STEP);
        }, "dash_impact");
    }

    private double getBossAttackDamage(LivingEntity boss) {
        var attr = boss.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        return attr != null ? attr.getValue() : 5.0;
    }

    private void playSound(ServerLevel level, LivingEntity from, String soundId) {
        if (soundId == null || soundId.isBlank()) return;
        try {
            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation(soundId));
            if (sound != null) {
                level.playSound(null, from.blockPosition(), sound, SoundSource.HOSTILE, 1.0f, 1.0f);
            }
        } catch (Exception ignore) {}
    }
}
