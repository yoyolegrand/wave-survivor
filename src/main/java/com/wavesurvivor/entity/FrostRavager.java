package com.wavesurvivor.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * RAVAGEUR DE GIVRE (base d'Aurvang, l'Ancien du Glacier — horde des Pics Gelés).
 * Même modèle, mêmes animations et même comportement que le Ravageur, avec :
 *  - une texture glacée (fourrure de neige, cornes et plaques de cristal, yeux cyan) ;
 *  - une aura : de la neige tombe en permanence autour de lui ;
 *  - un souffle de givre (visuel) vers sa cible toutes les ~7 s ;
 *  - jamais d'attaque contre les Golems de Givre ; insensible au gel.
 */
public class FrostRavager extends Ravager {

    private int breathCooldown = 100;

    public FrostRavager(EntityType<? extends Ravager> type, Level level) {
        super(type, level);
        this.xpReward = 40;
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !(target instanceof FrostGolem) && super.canAttack(target);
    }

    @Override
    public boolean canFreeze() {
        return false;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel sl)) return;
        float w = getBbWidth();
        // Aura : neige qui tombe autour de lui, quelques éclats lumineux
        if (tickCount % 2 == 0) {
            sl.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + getBbHeight() + 0.6, getZ(), 2, w * 0.9, 0.3, w * 0.9, 0.01);
        }
        if (tickCount % 10 == 0) {
            sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + getBbHeight() * 0.6, getZ(), 1, w * 0.6, 0.5, w * 0.6, 0.01);
        }
        // Souffle de givre vers la cible (visuel)
        if (breathCooldown > 0) breathCooldown--;
        LivingEntity t = getTarget();
        if (breathCooldown <= 0 && t != null && t.isAlive() && distanceToSqr(t) < 12 * 12) {
            breathCooldown = 140;
            Vec3 from = position().add(0, getBbHeight() * 0.55, 0);
            Vec3 dir = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(from);
            double len = Math.min(10, dir.length());
            dir = dir.normalize();
            sl.playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.4f, 0.75f);
            for (double s = 1.0; s <= len; s += 0.6) {
                double spread = 0.15 + s * 0.12;
                Vec3 p = from.add(dir.scale(s));
                sl.sendParticles(ParticleTypes.SNOWFLAKE, p.x, p.y, p.z, 6, spread, spread, spread, 0.02);
                sl.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 2, spread * 0.6, spread * 0.6, spread * 0.6, 0.01);
            }
        }
    }
}
