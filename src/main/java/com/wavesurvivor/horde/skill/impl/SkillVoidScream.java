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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * void_scream : sonic boom AoE. Damage + knockback (repousse loin du boss) tous les joueurs
 * dans voidScreamRange. Spawn particule sonic_boom sur chaque victime.
 */
public class SkillVoidScream extends BossSkill {

    public SkillVoidScream(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(60, config.voidScreamCooldown);
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        double range = Math.max(2, config.voidScreamRange);
        AABB box = boss.getBoundingBox().inflate(range);
        List<ServerPlayer> victims = level.getEntitiesOfClass(ServerPlayer.class, box,
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator());

        if (victims.isEmpty()) return;

        Vec3 bossPos = boss.position();
        ParticleOptions particle = resolveParticle(config.voidScreamParticle, ParticleTypes.SONIC_BOOM);
        Component msg = (config.voidScreamMessage != null && !config.voidScreamMessage.isBlank())
                ? Component.literal(config.voidScreamMessage) : null;

        for (ServerPlayer p : victims) {
            if (config.voidScreamDamage > 0) {
                p.hurt(level.damageSources().mobAttack(boss), (float) config.voidScreamDamage);
            }
            if (config.voidScreamKnockback > 0) {
                Vec3 dir = p.position().subtract(bossPos);
                double len = dir.length();
                if (len > 0.01) {
                    Vec3 push = dir.scale(1.0 / len);
                    p.push(push.x * config.voidScreamKnockback, 0.4, push.z * config.voidScreamKnockback);
                    p.hurtMarked = true;
                }
            }
            if (particle != null) {
                level.sendParticles(particle, p.getX(), p.getY() + 1.0, p.getZ(),
                        3, 0.2, 0.4, 0.2, 0.01);
            }
            if (msg != null) p.sendSystemMessage(msg);
        }

        playSound(level, boss);
        WaveSurvivorMod.LOGGER.info("[Skill void_scream] '{}' hurle sur {} joueur(s) dans rayon {}",
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
        if (config.voidScreamSound == null || config.voidScreamSound.isBlank()) return;
        try {
            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation(config.voidScreamSound));
            if (sound != null) {
                level.playSound(null, from.blockPosition(), sound, SoundSource.HOSTILE, 1.5f, 1.0f);
            }
        } catch (Exception ignore) {}
    }
}
