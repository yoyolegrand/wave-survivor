package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.skill.BossSkill;
import com.wavesurvivor.horde.skill.DelayedActionScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * grave_grip — EMPRISE DE LA TOMBE (signature du Fossoyeur).
 * Un joueur qui reste immobile graveGripStillSeconds dans le rayon est tiré à moitié sous terre : immobilisé
 * (lenteur extrême, saut impossible) et blessé chaque seconde pendant graveGripDuration. Il faut rester en mouvement.
 */
public class SkillGraveGrip extends BossSkill {

    private final Map<UUID, Vec3> lastPos = new HashMap<>();
    private final Map<UUID, Integer> still = new HashMap<>();
    private final Map<UUID, Long> grippedUntil = new HashMap<>();

    public SkillGraveGrip(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(10, config.graveGripCooldown); // vérification régulière (1 fois par seconde par défaut)
    }

    @Override
    public boolean requiresTarget() {
        return false;
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        long now = level.getServer().getTickCount();
        double r = Math.max(4, config.graveGripRadius);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, boss.getBoundingBox().inflate(r))) {
            if (!p.isAlive() || p.isCreative() || p.isSpectator()) continue;
            UUID id = p.getUUID();
            if (grippedUntil.getOrDefault(id, 0L) > now) continue;
            Vec3 pos = p.position();
            Vec3 prev = lastPos.put(id, pos);
            boolean moved = prev == null || prev.distanceToSqr(pos) > 0.09;
            int s = moved ? 0 : still.getOrDefault(id, 0) + 1;
            still.put(id, s);
            if (s >= Math.max(1, config.graveGripStillSeconds) && p.onGround()) grip(p, level, now);
        }
    }

    private void grip(ServerPlayer p, ServerLevel level, long now) {
        UUID id = p.getUUID();
        int dur = Math.max(20, config.graveGripDuration);
        grippedUntil.put(id, now + dur + 60);
        still.put(id, 0);
        BlockPos below = p.blockPosition().below();
        var ground = level.getBlockState(below);
        // À moitié dans le sol (les yeux restent au-dessus : pas d'étouffement)
        p.teleportTo(p.getX(), p.getY() - 0.9, p.getZ());
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, dur, 6, false, true, true));
        p.addEffect(new MobEffectInstance(MobEffects.JUMP, dur, 250, false, false, false));
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), p.getX(), p.getY() + 0.9, p.getZ(), 40, 0.4, 0.3, 0.4, 0.1);
        }
        level.playSound(null, p.blockPosition(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 1.5f, 0.6f);
        if (config.graveGripMessage != null && !config.graveGripMessage.isBlank()) {
            p.displayClientMessage(Component.literal(config.graveGripMessage), true);
        }
        float dmg = (float) Math.max(0, config.graveGripDamage);
        for (int t = 20; t <= dur; t += 20) {
            DelayedActionScheduler.schedule(level.getServer(), t, () -> {
                if (p.isAlive() && dmg > 0) p.hurt(level.damageSources().magic(), dmg);
            }, "grave grip");
        }
    }
}
