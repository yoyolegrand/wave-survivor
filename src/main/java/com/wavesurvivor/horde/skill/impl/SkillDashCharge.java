package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.skill.BossSkill;
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

        // Damage bonus si le boss touche la cible dans les 20 ticks (via TargetHit tick check)
        // Simplification : on applique immédiatement un damage bonus proportionnel
        double bonusDamage = getBossAttackDamage(boss) * config.dashDamageMultiplier;
        // Le damage n'est appliqué que si assez proche à l'arrivée (dans 20 ticks)
        // Pour rester simple : on schedule via delta d'impact — ici on applique direct si dist < 6
        if (dist < 8) {
            target.hurt(boss.damageSources().mobAttack(boss), (float) bonusDamage);
        }

        playSound(level, boss, config.dashSound);
        WaveSurvivorMod.LOGGER.debug("[Skill dash] '{}' fonce sur {} (dist {}, bonus dmg {})",
                boss.getCustomName() != null ? boss.getCustomName().getString() : boss.getType(),
                target.getName().getString(), (int) dist, (int) bonusDamage);
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
