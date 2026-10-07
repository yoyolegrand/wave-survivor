package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.skill.BossSkill;
import com.wavesurvivor.horde.skill.DelayedActionScheduler;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ShulkerBullet;

import java.util.List;
import java.util.Random;

/**
 * homing_orbs — ORBES TRAQUEURS (signature du Gardien Shulker / Golem de l'End).
 * Lance homingOrbsCount balles de shulker téléguidées vers les joueurs proches (lévitation au contact).
 * Les orbes peuvent être détruites d'un coup (épée ou flèche) : il faut les abattre ou les esquiver.
 */
public class SkillHomingOrbs extends BossSkill {

    private static final Random RNG = new Random();

    public SkillHomingOrbs(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(40, config.homingOrbsCooldown);
    }

    @Override
    public boolean requiresTarget() {
        return false;
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        double range = Math.max(6, config.homingOrbsRange);
        List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, boss.getBoundingBox().inflate(range),
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator());
        if (players.isEmpty()) return;
        int n = Math.max(1, Math.min(10, config.homingOrbsCount));
        for (int i = 0; i < n; i++) {
            ServerPlayer p = players.get(RNG.nextInt(players.size()));
            // Tirs légèrement échelonnés (rafale)
            DelayedActionScheduler.schedule(level.getServer(), i * 6, () -> {
                if (!boss.isAlive() || !p.isAlive()) return;
                Direction.Axis axis = Direction.Axis.values()[RNG.nextInt(3)];
                ShulkerBullet orb = new ShulkerBullet(level, boss, p, axis);
                orb.setPos(boss.getX(), boss.getY() + boss.getBbHeight() * 0.7, boss.getZ());
                level.addFreshEntity(orb);
                level.sendParticles(ParticleTypes.END_ROD, orb.getX(), orb.getY(), orb.getZ(), 6, 0.2, 0.2, 0.2, 0.02);
                level.playSound(null, boss.blockPosition(), SoundEvents.SHULKER_SHOOT, SoundSource.HOSTILE, 1.5f, 0.9f + RNG.nextFloat() * 0.2f);
            }, "homing orbs");
        }
        if (config.homingOrbsMessage != null && !config.homingOrbsMessage.isBlank()) {
            Component msg = Component.literal(config.homingOrbsMessage);
            for (ServerPlayer p : players) p.displayClientMessage(msg, true);
        }
    }
}
