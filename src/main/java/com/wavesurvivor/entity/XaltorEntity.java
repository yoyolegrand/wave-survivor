package com.wavesurvivor.entity;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * XÂL'TOR, L'ŒIL DU NÉANT — enderman géant (×1,6), boss final du Néant Éternel.
 * Hitbox à la taille (définie sur l'EntityType), monte les marches d'un bloc, immunisé au feu.
 * Ses mécaniques (clones, cristaux, effondrement) sont gérées par XaltorController.
 */
public class XaltorEntity extends EnderMan {

    public static final float SCALE = 1.6f;

    public XaltorEntity(EntityType<? extends EnderMan> type, Level level) {
        super(type, level);
        this.setMaxUpStep(1.0f);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 500.0)
                .add(Attributes.MOVEMENT_SPEED, 0.33)
                .add(Attributes.ATTACK_DAMAGE, 12.0)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9);
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dims) {
        return dims.height * 0.88f;
    }
}
