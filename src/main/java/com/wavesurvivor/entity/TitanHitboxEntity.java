package com.wavesurvivor.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * 2.0 — Boîte de contact invisible du Titan de la Faille. Minecraft ne retrouve une entité que dans la section (16 × 16 × 16) de sa position et
 * suppose qu'aucune entité ne dépasse 2 blocs de rayon : un Titan géant ne pouvait donc être touché (flèches, boulets, mêlée) que près de son centre.
 * Quelques boîtes de taille normale posées sur sa face avant (voir RiftTitan) relaient chaque coup reçu au Titan.
 * Invisible, sans collision, jamais sauvegardée ; disparaît avec le Titan.
 */
public class TitanHitboxEntity extends Entity {

    public TitanHitboxEntity(EntityType<? extends TitanHitboxEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    public boolean isPickable() { return !isRemoved(); }

    @Override
    public boolean canBeCollidedWith() { return false; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    public boolean isInvulnerableTo(DamageSource source) { return false; }

    /** Un coup reçu ici est un coup porté au Titan. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return false;
        return com.wavesurvivor.horde.rift.RiftManager.hurtTitan(source, amount);
    }

    @Override
    public void tick() {
        super.tick();
        // Plus de Titan (mort, Faille fermée) : la boîte disparaît
        if (!level().isClientSide && tickCount % 20 == 0 && !com.wavesurvivor.horde.rift.RiftManager.titanAlive()) discard();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
