package com.wavesurvivor.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * TOTEM — pilier sculpté surmonté d'une tête, anneau de runes lumineuses (rendu client).
 * Rôles : protection (boss invulnérable), fureur, soin, malediction, invocation (auras du chaos, T2).
 * Immobile, sans IA, sans recul, immunisé au feu ; « monstre » pour ne pas être abîmé par la horde.
 */
public class TotemEntity extends Mob implements net.minecraft.world.entity.monster.Enemy {

    private static final EntityDataAccessor<String> ROLE = SynchedEntityData.defineId(TotemEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> HEAD = SynchedEntityData.defineId(TotemEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(TotemEntity.class, EntityDataSerializers.INT);

    public TotemEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.setNoAi(true);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ROLE, "protection");
        this.entityData.define(HEAD, "minecraft:player_head");
        this.entityData.define(COLOR, -1);
    }

    public void setup(String role, String headItem, int color) {
        this.entityData.set(ROLE, role != null ? role : "protection");
        this.entityData.set(HEAD, headItem != null && !headItem.isBlank() ? headItem : "minecraft:player_head");
        this.entityData.set(COLOR, color);
    }

    public String getRole() { return this.entityData.get(ROLE); }
    public String getHeadItem() { return this.entityData.get(HEAD); }

    /** Couleur du rôle (ou couleur imposée). */
    public int getTotemColor() {
        int c = this.entityData.get(COLOR);
        if (c >= 0) return c;
        return roleColor(getRole());
    }

    public static int roleColor(String role) {
        return switch (role == null ? "" : role) {
            case "fureur" -> 0xFF3B30;
            case "soin" -> 0x3DDC84;
            case "malediction" -> 0xA64DFF;
            case "invocation" -> 0xFF9A1F;
            default -> 0xFFC83D; // protection : or
        };
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("TotemRole", getRole());
        tag.putString("TotemHead", getHeadItem());
        tag.putInt("TotemColor", this.entityData.get(COLOR));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("TotemRole")) setup(tag.getString("TotemRole"), tag.getString("TotemHead"), tag.getInt("TotemColor"));
    }

    // ─── Immobile, inébranlable ───

    @Override public boolean isPushable() { return false; }
    @Override protected void doPush(Entity other) {}
    @Override public void push(double x, double y, double z) {}
    @Override public void knockback(double strength, double x, double z) {}
    @Override public boolean removeWhenFarAway(double dist) { return false; }
    @Override public boolean isAffectedByPotions() { return false; }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FALL) || source.is(net.minecraft.tags.DamageTypeTags.IS_DROWNING)) return false;
        return super.hurt(source, amount);
    }

    @Override protected SoundEvent getHurtSound(DamageSource src) { return SoundEvents.STONE_HIT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.TOTEM_USE; }
    @Override protected SoundEvent getAmbientSound() { return null; }
}
