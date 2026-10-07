package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.skill.BossSkill;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * uppercut : le boss propulse la cible en l'air (knock-up).
 * Ne s'active que si la cible est en corps-à-corps (distance ≤ uppercutRange).
 * Applique un boost vertical + Slowness (et Nausea si uppercutAddNausea).
 * Spawn des particules et joue un son au point d'impact.
 */
public class SkillUppercut extends BossSkill {

    public SkillUppercut(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(20, config.uppercutCooldown);
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        double dist = boss.distanceTo(target);
        if (dist > config.uppercutRange) {
            WaveSurvivorMod.LOGGER.debug("[Skill uppercut] cible trop loin ({} > {})", (int) dist, config.uppercutRange);
            return;
        }

        // Boost vertical
        double power = Math.max(0.1, config.uppercutPower);
        Vec3 currentVel = target.getDeltaMovement();
        target.setDeltaMovement(currentVel.x, power, currentVel.z);
        target.hurtMarked = true; // force la synchro client de la vélocité

        // Slowness
        if (config.uppercutSlownessDuration > 0) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    config.uppercutSlownessDuration, Math.max(0, config.uppercutSlownessAmplifier), false, true));
        }

        // Nausea (optionnelle)
        if (config.uppercutAddNausea && config.uppercutNauseaDuration > 0) {
            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION,
                    config.uppercutNauseaDuration, 0, false, true));
        }

        // Particules au point d'impact (pieds de la cible)
        ParticleOptions particle = resolveParticle(config.uppercutParticle, ParticleTypes.EXPLOSION);
        if (particle != null) {
            level.sendParticles(particle,
                    target.getX(), target.getY(), target.getZ(),
                    12, 0.5, 0.2, 0.5, 0.05);
        }

        // Son
        if (config.uppercutImpactSound != null && !config.uppercutImpactSound.isBlank()) {
            try {
                SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation(config.uppercutImpactSound));
                if (sound != null) {
                    level.playSound(null, target.blockPosition(), sound, SoundSource.HOSTILE, 1.0f, 1.0f);
                }
            } catch (Exception ignore) {}
        }

        // Message
        if (config.uppercutMessage != null && !config.uppercutMessage.isBlank() && target instanceof ServerPlayer sp) {
            sp.sendSystemMessage(Component.literal(config.uppercutMessage));
        }

        WaveSurvivorMod.LOGGER.info("[Skill uppercut] '{}' knock-up '{}' (power {})",
                boss.getCustomName() != null ? boss.getCustomName().getString() : boss.getType().toString(),
                target.getName().getString(), power);
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
