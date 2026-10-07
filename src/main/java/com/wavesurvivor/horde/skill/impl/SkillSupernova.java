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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * supernova : quand HP du boss ≤ supernovaHealthThreshold, register le boss dans SupernovaTracker
 * qui gère toute la phase de charge (particules + sons progressifs) puis l'explosion.
 * One-shot par vie du boss.
 */
public class SkillSupernova extends BossSkill {

    private boolean triggered = false;

    public SkillSupernova(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(20, config.supernovaCooldown);
    }

    @Override
    public boolean requiresTarget() {
        return false;
    }

    @Override
    public boolean isOneShot() {
        return true;
    }

    /** Override du tickIfReady pour trigger sur HP threshold plutôt que cooldown. */
    @Override
    public boolean tickIfReady(long now, LivingEntity boss, ServerLevel level) {
        if (triggered) return false;
        if (boss.getMaxHealth() <= 0) return false;
        float pct = boss.getHealth() / boss.getMaxHealth();
        if (pct > config.supernovaHealthThreshold) return false;
        triggered = true;
        execute(boss, null, level);
        lastActivationTick = now;
        return true;
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        ParticleOptions chargePart = resolveParticle(config.supernovaChargeParticle, ParticleTypes.LAVA);
        int delay = Math.max(5, config.supernovaChargeDelay);
        Level.ExplosionInteraction mode = switch (config.supernovaDestroyBlocks == null
                ? "none" : config.supernovaDestroyBlocks.toLowerCase()) {
            case "break" -> Level.ExplosionInteraction.MOB;
            case "destroy" -> Level.ExplosionInteraction.TNT;
            default -> Level.ExplosionInteraction.NONE;
        };

        // Délègue tout au tracker : particules, sons progressifs, explosion finale
        SupernovaTracker.register(level, boss, delay, chargePart, config.supernovaExplosionPower, mode);

        WaveSurvivorMod.LOGGER.info("[Skill supernova] '{}' déclenche (HP {}%) — explosion dans {}t (power {})",
                boss.getCustomName() != null ? boss.getCustomName().getString() : boss.getType().toString(),
                (int) ((boss.getHealth() / boss.getMaxHealth()) * 100), delay, config.supernovaExplosionPower);
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
