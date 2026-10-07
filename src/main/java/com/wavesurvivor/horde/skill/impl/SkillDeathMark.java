package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.skill.BossSkill;
import com.wavesurvivor.horde.skill.NecroTracker;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * death_mark : marque la cible (joueur) pendant deathMarkDuration ticks.
 * Le marqué brille (Glowing), tous les monstres dans deathMarkRadius le ciblent,
 * et il subit +deathMarkDamageBonus% de dégâts (géré par NecroTracker).
 */
public class SkillDeathMark extends BossSkill {

    public SkillDeathMark(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(100, config.deathMarkCooldown);
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        if (!(target instanceof ServerPlayer p) || p.isCreative() || p.isSpectator()) return;
        if (NecroTracker.isMarked(p.getUUID())) return; // déjà marqué : on n'empile pas

        NecroTracker.mark(p, level.getServer().getTickCount(), Math.max(20, config.deathMarkDuration),
                Math.max(0, config.deathMarkDamageBonus), Math.max(4, config.deathMarkRadius));

        level.sendParticles(ParticleTypes.SCULK_SOUL, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.4, 0.8, 0.4, 0.03);
        NecroTracker.playSound(level, p.blockPosition(), config.deathMarkSound, 0.6f);
        if (config.deathMarkMessage != null && !config.deathMarkMessage.isBlank()) {
            p.sendSystemMessage(Component.literal(config.deathMarkMessage));
        }
    }
}
