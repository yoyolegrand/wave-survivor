package com.wavesurvivor.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.level.Level;

/**
 * ÉCLAT DU NÉANT — cousin violet du Diablotin (horde du Néant Éternel).
 * Même vol au ras du sol et mêmes collisions ; en plus, quand il est touché, 1 chance sur 4 de se
 * TÉLÉPORTER à quelques blocs (comme un enderman). Pas immunisé au feu.
 */
public class EclatNeant extends Diablotin {

    public EclatNeant(EntityType<? extends Vex> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 14.0)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (hit && isAlive() && level() instanceof ServerLevel sl && getRandom().nextFloat() < 0.25f) {
            double ox = getX(), oy = getY(), oz = getZ();
            for (int tries = 0; tries < 8; tries++) {
                double x = ox + (getRandom().nextDouble() - 0.5) * 8;
                double z = oz + (getRandom().nextDouble() - 0.5) * 8;
                if (randomTeleport(x, oy, z, false)) {
                    sl.sendParticles(ParticleTypes.PORTAL, ox, oy + 0.4, oz, 20, 0.2, 0.3, 0.2, 0.3);
                    sl.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 0.4, getZ(), 20, 0.2, 0.3, 0.2, 0.3);
                    sl.playSound(null, blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.7f, 1.6f);
                    break;
                }
            }
        }
        return hit;
    }
}
