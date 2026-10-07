package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.skill.BossSkill;
import com.wavesurvivor.horde.skill.ProjectileTrailTracker;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;

/**
 * projectile_potion : le boss tire une flèche empoisonnée (ou autre effet) sur la cible.
 * L'effet, la durée, l'amplifier viennent du config.
 */
public class SkillProjectilePotion extends BossSkill {

    public SkillProjectilePotion(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(20, config.potionCooldown);
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        Arrow arrow = new Arrow(level, boss);
        Vec3 bossEye = boss.getEyePosition();
        arrow.setPos(bossEye.x, bossEye.y, bossEye.z);

        // Direction vers le centre du corps de la cible
        double dx = target.getX() - bossEye.x;
        double dy = target.getY() + target.getBbHeight() * 0.5 - bossEye.y;
        double dz = target.getZ() - bossEye.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.1) return;

        float speed = (float) Math.max(0.1, config.projectileSpeed);
        arrow.shoot(dx, dy, dz, speed, 1.0f);

        // Applique l'effet de potion à la flèche via addEffect
        MobEffect effect = resolveMobEffect(config.potionEffect);
        if (effect == null) effect = MobEffects.POISON; // fallback

        arrow.addEffect(new MobEffectInstance(effect,
                Math.max(20, config.potionDuration),
                Math.max(0, config.potionAmplifier)));
        arrow.setBaseDamage(1.0);
        arrow.setCritArrow(false);

        // Options modulables : projectile invisible + traînée de particules
        if (config.invisibleProjectile) {
            arrow.setInvisible(true);
        }

        level.addFreshEntity(arrow);

        // Register tracker de traînée après addFreshEntity (sinon UUID pas encore valide dans le monde)
        if (config.trailParticle != null && !config.trailParticle.isBlank()) {
            ProjectileTrailTracker.register(arrow.getUUID(), config.trailParticle, config.trailParticleCount);
        }

        WaveSurvivorMod.LOGGER.debug("[Skill projectile_potion] '{}' tire une flèche {} sur {} (dist {}, invisible={}, trail={})",
                boss.getCustomName() != null ? boss.getCustomName().getString() : boss.getType(),
                config.potionEffect, target.getName().getString(), (int) dist,
                config.invisibleProjectile, config.trailParticle);
    }

    private MobEffect resolveMobEffect(String name) {
        if (name == null || name.isBlank()) return null;
        String id = name.contains(":") ? name : "minecraft:" + name;
        try {
            return BuiltInRegistries.MOB_EFFECT.get(new ResourceLocation(id));
        } catch (Exception e) {
            return null;
        }
    }
}
