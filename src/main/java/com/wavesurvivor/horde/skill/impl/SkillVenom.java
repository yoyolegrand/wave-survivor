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
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * venom : AoE autour du boss qui applique Poison + Nausea + Blindness à tous les joueurs
 * dans un rayon venomRange. Optionnellement joue un son, envoie un message et
 * spawn des particules (défaut squid_ink) sur chaque joueur affecté.
 */
public class SkillVenom extends BossSkill {

    public SkillVenom(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(60, config.venomCooldown);
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        double range = Math.max(2, config.venomRange);
        AABB box = boss.getBoundingBox().inflate(range);
        List<ServerPlayer> victims = level.getEntitiesOfClass(ServerPlayer.class, box,
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator());

        if (victims.isEmpty()) {
            WaveSurvivorMod.LOGGER.debug("[Skill venom] '{}' → aucun joueur dans le rayon {}", boss.getType(), range);
            return;
        }

        ParticleOptions particle = resolveParticle(config.venomParticle, ParticleTypes.SQUID_INK);
        Component msg = (config.venomMessage != null && !config.venomMessage.isBlank())
                ? Component.literal(config.venomMessage) : null;

        for (ServerPlayer p : victims) {
            if (config.venomPoisonDuration > 0) {
                p.addEffect(new MobEffectInstance(MobEffects.POISON,
                        config.venomPoisonDuration, Math.max(0, config.venomPoisonAmplifier), false, true));
            }
            if (config.venomNauseaDuration > 0) {
                p.addEffect(new MobEffectInstance(MobEffects.CONFUSION,
                        config.venomNauseaDuration, Math.max(0, config.venomNauseaAmplifier), false, true));
            }
            if (config.venomBlindnessDuration > 0) {
                p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,
                        config.venomBlindnessDuration, 0, false, true));
            }
            if (particle != null) {
                level.sendParticles(particle,
                        p.getX(), p.getY() + 1.0, p.getZ(),
                        18, 0.4, 0.8, 0.4, 0.02);
            }
            if (msg != null) {
                p.sendSystemMessage(msg);
            }
        }

        playSound(level, boss);
        WaveSurvivorMod.LOGGER.info("[Skill venom] '{}' empoisonne {} joueur(s) dans rayon {}",
                boss.getCustomName() != null ? boss.getCustomName().getString() : boss.getType().toString(),
                victims.size(), (int) range);
    }

    private static ParticleOptions resolveParticle(String id, SimpleParticleType fallback) {
        if (id == null || id.isBlank()) return fallback;
        try {
            Object p = BuiltInRegistries.PARTICLE_TYPE.get(new ResourceLocation(id));
            if (p instanceof ParticleOptions po) return po;
        } catch (Exception ignore) {}
        return fallback;
    }

    private void playSound(ServerLevel level, LivingEntity from) {
        if (config.venomSound == null || config.venomSound.isBlank()) return;
        try {
            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation(config.venomSound));
            if (sound != null) {
                level.playSound(null, from.blockPosition(), sound, SoundSource.HOSTILE, 1.0f, 1.0f);
            }
        } catch (Exception ignore) {}
    }
}
