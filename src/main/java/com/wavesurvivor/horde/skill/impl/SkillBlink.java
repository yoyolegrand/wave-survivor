package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.skill.BossSkill;
import com.wavesurvivor.horde.skill.DelayedActionScheduler;
import com.wavesurvivor.horde.skill.NecroTracker;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * blink (Clignement) : le lanceur disparaît et RÉAPPARAÎT DANS LE DOS de sa cible (blinkDistance blocs derrière elle),
 * se tourne vers elle puis FRAPPE (attaque + blinkBonusDamage, armure appliquée). Jamais dans un mur :
 * si l'emplacement est bloqué, essaie sur les côtés, sinon renonce.
 */
public class SkillBlink extends BossSkill {

    public SkillBlink(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(40, config.blinkCooldown);
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        if (target == null || !target.isAlive()) return;
        double range = Math.max(4, config.blinkRange);
        if (boss.distanceToSqr(target) > range * range) return;

        // Derrière la cible (direction de son regard, à plat), puis côtés si bloqué
        Vec3 look = target.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1e-4) flat = new Vec3(0, 0, 1);
        flat = flat.normalize();
        double d = Math.max(1.0, config.blinkDistance);
        Vec3[] candidates = {
                target.position().subtract(flat.scale(d)),
                target.position().add(new Vec3(-flat.z, 0, flat.x).scale(d)),
                target.position().add(new Vec3(flat.z, 0, -flat.x).scale(d))
        };
        Vec3 from = boss.position();
        Vec3 dest = null;
        for (Vec3 c : candidates) {
            if (level.noCollision(boss, boss.getBoundingBox().move(c.subtract(from)))) { dest = c; break; }
        }
        if (dest == null) return;

        level.sendParticles(ParticleTypes.PORTAL, from.x, from.y + boss.getBbHeight() * 0.5, from.z, 30, 0.3, 0.6, 0.3, 0.4);
        boss.teleportTo(dest.x, dest.y, dest.z);
        // Face à la cible
        Vec3 dir = target.position().subtract(dest);
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * (180 / Math.PI)) - 90f;
        boss.setYRot(yaw);
        boss.setYHeadRot(yaw);
        boss.yBodyRot = yaw;
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, dest.x, dest.y + boss.getBbHeight() * 0.5, dest.z, 30, 0.3, 0.6, 0.3, 0.1);
        NecroTracker.playSound(level, boss.blockPosition(), config.blinkSound, 1.0f);
        if (target instanceof ServerPlayer p && config.blinkMessage != null && !config.blinkMessage.isBlank()) {
            p.displayClientMessage(Component.literal(config.blinkMessage), true);
        }

        if (config.blinkStrike) {
            DelayedActionScheduler.schedule(level.getServer(), 5, () -> {
                if (!boss.isAlive() || !target.isAlive() || boss.distanceToSqr(target) > 16) return;
                AttributeInstance atk = boss.getAttribute(Attributes.ATTACK_DAMAGE);
                float dmg = (float) ((atk != null ? atk.getValue() : 4) + Math.max(0, config.blinkBonusDamage));
                target.hurt(level.damageSources().mobAttack(boss), dmg);
                boss.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1, target.getZ(), 10, 0.3, 0.4, 0.3, 0.2);
            }, "blink strike");
        }
    }
}
