package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.skill.BossSkill;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Random;

/**
 * force_field : bulle permanente autour du boss. Tick tous les forceFieldCheckInterval ticks,
 * applique damage + knockback (loin du boss) + fire aux joueurs qui entrent dans forceFieldRadius.
 * Spawn des particules décoratives autour du boss à chaque tick.
 * Skill tick continu (requiresTarget = false) plutôt qu'attaque unique.
 */
public class SkillForceField extends BossSkill {

    private static final Random RNG = new Random();

    public SkillForceField(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(1, config.forceFieldCheckInterval);
    }

    @Override
    public boolean requiresTarget() {
        return false;
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        double r = Math.max(1, config.forceFieldRadius);

        // Particules décoratives autour du boss
        ParticleOptions particle = resolveParticle(config.forceFieldParticle, ParticleTypes.FLAME);
        if (particle != null) {
            for (int i = 0; i < 6; i++) {
                double angle = RNG.nextDouble() * Math.PI * 2;
                double x = boss.getX() + Math.cos(angle) * r;
                double z = boss.getZ() + Math.sin(angle) * r;
                double y = boss.getY() + RNG.nextDouble() * boss.getBbHeight();
                level.sendParticles(particle, x, y, z, 1, 0.02, 0.02, 0.02, 0.01);
            }
        }

        // Damage/knockback/fire aux joueurs à l'intérieur
        AABB box = boss.getBoundingBox().inflate(r);
        List<ServerPlayer> victims = level.getEntitiesOfClass(ServerPlayer.class, box,
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator());
        if (victims.isEmpty()) return;

        Vec3 bossPos = boss.position();
        boolean anyHit = false;

        for (ServerPlayer p : victims) {
            if (config.forceFieldDamage > 0) {
                p.hurt(level.damageSources().mobAttack(boss), (float) config.forceFieldDamage);
                anyHit = true;
            }
            if (config.forceFieldKnockback > 0) {
                Vec3 dir = p.position().subtract(bossPos);
                double len = dir.length();
                if (len > 0.01) {
                    Vec3 push = dir.scale(1.0 / len);
                    p.push(push.x * config.forceFieldKnockback, 0.3, push.z * config.forceFieldKnockback);
                    p.hurtMarked = true;
                }
            }
            if (config.forceFieldFireDuration > 0) {
                p.setSecondsOnFire(config.forceFieldFireDuration);
            }
        }

        // Son seulement quand on hit quelqu'un (évite spam)
        if (anyHit && config.forceFieldSound != null && !config.forceFieldSound.isBlank()) {
            try {
                SoundEvent snd = BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation(config.forceFieldSound));
                if (snd != null) {
                    level.playSound(null, boss.blockPosition(), snd, SoundSource.HOSTILE, 0.8f, 1.0f);
                }
            } catch (Exception ignore) {}
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
