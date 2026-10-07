package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.skill.BossSkill;
import com.wavesurvivor.horde.skill.DelayedActionScheduler;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Random;

/**
 * doom_mark — SENTENCE (signature du Roi Mort).
 * Marque un joueur (lueur + cercle d'âmes). Après doomMarkDelay, un énorme coup (doomMarkDamagePercent % de ses PV
 * max) s'abat : il est PARTAGÉ entre tous les joueurs à moins de doomMarkShareRadius blocs de lui.
 * Seul : mortel. En groupe : supportable. Les joueurs doivent se rassembler autour du condamné.
 */
public class SkillDoomMark extends BossSkill {

    private static final Random RNG = new Random();

    public SkillDoomMark(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(100, config.doomMarkCooldown);
    }

    @Override
    public boolean requiresTarget() {
        return true;
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, boss.getBoundingBox().inflate(32),
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator());
        if (players.isEmpty()) return;
        ServerPlayer marked = players.get(RNG.nextInt(players.size()));
        int delay = Math.max(40, config.doomMarkDelay);

        marked.addEffect(new MobEffectInstance(MobEffects.GLOWING, delay, 0, false, false, true));
        level.playSound(null, marked.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.8f, 1.6f);
        if (config.doomMarkMessage != null && !config.doomMarkMessage.isBlank()) {
            Component msg = Component.literal(config.doomMarkMessage.replace("{player}", marked.getGameProfile().getName()));
            for (ServerPlayer p : players) p.displayClientMessage(msg, true);
        }
        // Cercle d'âmes autour du condamné pendant le compte à rebours
        double r = Math.max(2, config.doomMarkShareRadius);
        for (int t = 0; t < delay; t += 5) {
            final int left = delay - t;
            DelayedActionScheduler.schedule(level.getServer(), t, () -> {
                if (!marked.isAlive()) return;
                int pts = 24;
                for (int i = 0; i < pts; i++) {
                    double a = Math.PI * 2 * i / pts;
                    level.sendParticles(left < 30 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SOUL,
                            marked.getX() + Math.cos(a) * r, marked.getY() + 0.1, marked.getZ() + Math.sin(a) * r, 1, 0, 0.02, 0, 0);
                }
                if (left % 20 == 0) level.playSound(null, marked.blockPosition(), SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.HOSTILE, 1.5f, 0.5f);
            }, "doom mark");
        }
        // La sentence
        DelayedActionScheduler.schedule(level.getServer(), delay, () -> {
            if (!marked.isAlive() || !boss.isAlive()) return;
            List<ServerPlayer> share = level.getEntitiesOfClass(ServerPlayer.class, marked.getBoundingBox().inflate(r),
                    p -> p.isAlive() && !p.isCreative() && !p.isSpectator() && p.distanceToSqr(marked) <= r * r);
            if (!share.contains(marked)) share.add(marked);
            float total = (float) (marked.getMaxHealth() * Math.max(0, config.doomMarkDamagePercent) / 100.0);
            float each = total / share.size();
            for (ServerPlayer p : share) {
                p.hurt(level.damageSources().indirectMagic(boss, boss), each);
            }
            level.sendParticles(ParticleTypes.SONIC_BOOM, marked.getX(), marked.getY() + 1, marked.getZ(), 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, marked.getX(), marked.getY() + 0.5, marked.getZ(), 60, r * 0.5, 0.5, r * 0.5, 0.05);
            level.playSound(null, marked.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 2f, 0.6f);
        }, "doom mark strike");
    }
}
