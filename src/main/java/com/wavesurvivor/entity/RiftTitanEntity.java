package com.wavesurvivor.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * 2.0 — Base des Titans à modèle sur mesure (Dévoreur, Colosse, puis Archonte) : pose de compétence synchronisée (purement visuelle :
 * pose, tick de JEU de départ, durée ; {@link #playPose} côté serveur), géant immobile sans IA, visible de très loin.
 * Le départ est un tick de jeu (identique serveur / client), pas l'âge de l'entité (qui diffère entre les deux).
 */
public abstract class RiftTitanEntity extends Monster implements TitanAnimated {

    private static final EntityDataAccessor<Integer> POSE = SynchedEntityData.defineId(RiftTitanEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> POSE_START = SynchedEntityData.defineId(RiftTitanEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> POSE_LEN = SynchedEntityData.defineId(RiftTitanEntity.class, EntityDataSerializers.INT);

    protected RiftTitanEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.noCulling = true;   // immense : jamais écarté par le tri de rendu
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 3000.0)
                .add(Attributes.ATTACK_DAMAGE, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(POSE, 0);
        entityData.define(POSE_START, 0L);
        entityData.define(POSE_LEN, 1);
    }

    @Override
    protected void registerGoals() {
        // aucune IA : le Titan est piloté par RiftTitan
    }

    // ─── Poses (TitanAnimated) ───

    /** Serveur : joue une pose pendant {@code ticks} ticks (IDLE ensuite). */
    public void playPose(TitanAnimated.Pose pose, int ticks) {
        if (level().isClientSide || pose == null) return;
        entityData.set(POSE, pose.ordinal());
        entityData.set(POSE_START, level().getGameTime());
        entityData.set(POSE_LEN, Math.max(1, ticks));
    }

    @Override
    public Pose titanPose() {
        int i = entityData.get(POSE);
        Pose[] all = Pose.values();
        Pose p = i >= 0 && i < all.length ? all[i] : Pose.IDLE;
        if (p != Pose.IDLE && level().getGameTime() - entityData.get(POSE_START) > entityData.get(POSE_LEN)) return Pose.IDLE;
        return p;
    }

    @Override
    public float titanPoseProgress(float ageInTicks) {
        float partial = ageInTicks - tickCount;   // fraction du tick en cours
        float f = (level().getGameTime() - entityData.get(POSE_START) + partial) / (float) Math.max(1, entityData.get(POSE_LEN));
        return Math.max(0f, Math.min(1f, f));
    }

    // ─── Comportement : géant immobile qui flotte au-dessus du vide ───

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 640.0 * 640.0;   // visible de très loin (il mesure ~50 blocs une fois agrandi)
    }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean causeFallDamage(float dist, float mult, DamageSource src) { return false; }

    @Override
    public boolean removeWhenFarAway(double d) { return false; }

    @Override
    protected SoundEvent getAmbientSound() { return null; }

    @Override
    protected SoundEvent getHurtSound(DamageSource src) { return SoundEvents.RAVAGER_HURT; }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.ENDER_DRAGON_DEATH; }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
    }
}
