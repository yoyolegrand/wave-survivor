package com.wavesurvivor.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.level.Level;

/**
 * GARDE ROYAL MOMIFIÉ (horde des Sables Ardents ; base du Garde du Pharaon et d'Anubis, Gardien des Tombes) —
 * modèle sur mesure (RoyalGuardModel) : casque d'Anubis, deux khépesh, cœur maudit.
 * Comportement du Wither Skeleton (coup de Wither, immunité au feu), en plus solide.
 */
public class RoyalGuard extends WitherSkeleton {

    public RoyalGuard(EntityType<? extends WitherSkeleton> type, Level level) {
        super(type, level);
        this.xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return WitherSkeleton.createAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }
}
