package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.skill.BossSkill;
import com.wavesurvivor.horde.skill.NecroTracker;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * resurrection : aura PASSIVE du lanceur (Nécromancien). Tant qu'il est en vie, chaque mort-vivant
 * tué dans resurrectionRadius a resurrectionChance% de se relever 2s plus tard en Revenant
 * (resurrectionEntity, resurrectionHealth PV, sans loot, ne peut pas se relever à son tour).
 * S'active une seule fois, dès le spawn (pas besoin de cible).
 */
public class SkillResurrection extends BossSkill {

    public SkillResurrection(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return 20;
    }

    @Override
    public boolean requiresTarget() {
        return false;
    }

    @Override
    public boolean isOneShot() {
        return true;
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        NecroTracker.registerResurrector(boss,
                Math.max(2, config.resurrectionRadius),
                Math.max(0, Math.min(100, config.resurrectionChance)),
                Math.max(1, config.resurrectionHealth),
                config.resurrectionEntity != null ? config.resurrectionEntity : "minecraft:skeleton",
                config.resurrectionMessage);
        level.sendParticles(ParticleTypes.SCULK_SOUL, boss.getX(), boss.getY() + 1, boss.getZ(), 25, 0.5, 0.8, 0.5, 0.02);
    }
}
