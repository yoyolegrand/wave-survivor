package com.wavesurvivor.horde.skill;

import com.wavesurvivor.config.model.CustomSkillData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Skill de boss abstrait. Chaque type de skill hérite de cette classe et implémente execute().
 * Le SkillManager tick tous les skills des boss actifs et déclenche ceux qui sont prêts.
 */
public abstract class BossSkill {

    protected final CustomSkillData config;
    protected long lastActivationTick = -1L;

    protected BossSkill(CustomSkillData config) {
        this.config = config;
    }

    /** Cooldown en ticks entre 2 activations. */
    public abstract long getCooldownTicks();

    /**
     * True si le skill doit être testé même sans target
     * (ex: totem_protection qui s'active dès le spawn).
     */
    public boolean requiresTarget() {
        return true;
    }

    /**
     * True si le skill ne s'active qu'une seule fois par vie du boss.
     */
    public boolean isOneShot() {
        return false;
    }

    public abstract void execute(LivingEntity boss, LivingEntity target, ServerLevel level);

    public String getName() {
        return config.skillName != null ? config.skillName : config.skillType;
    }

    public boolean tickIfReady(long now, LivingEntity boss, ServerLevel level) {
        if (isOneShot() && lastActivationTick >= 0) return false;
        // Compétence réservée à une phase avancée du boss (Boss 2.0)
        if (config.unlockPhase > 1 && com.wavesurvivor.horde.boss.BossDirector.phase(boss.getUUID()) < config.unlockPhase) return false;
        // Temps de recharge raccourci à chaque phase
        long cooldown = Math.max(1, Math.round(getCooldownTicks() * com.wavesurvivor.horde.boss.BossDirector.cooldownMultiplier(boss.getUUID())));
        if (lastActivationTick >= 0 && (now - lastActivationTick) < cooldown) return false;

        LivingEntity target = null;
        if (boss instanceof Mob mob) target = mob.getTarget();
        if (requiresTarget() && (target == null || !target.isAlive())) return false;

        execute(boss, target, level);
        lastActivationTick = now;
        return true;
    }
}
