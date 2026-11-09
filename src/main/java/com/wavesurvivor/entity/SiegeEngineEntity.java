package com.wavesurvivor.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkHooks;

/**
 * 2.0 — Engin de siège de la Faille (catapulte ou baliste céleste) : une entité rendue par un modèle JSON (voir SiegeEngineRenderer).
 * L'état de jeu (niveau, variante, munitions, rechargement) est porté ici ; les animations du client se calculent à partir des
 * dates de tir et de rechargement synchronisées. L'engin regarde toujours vers le SUD ; son entité est au centre de son emprise, au sol
 * (catapulte 2 × 6, baliste 3 × 3, 3 de haut).
 */
public class SiegeEngineEntity extends Entity {

    public static final int CATAPULT = 0, BALLISTA = 1;

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(SiegeEngineEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LEVEL = SynchedEntityData.defineId(SiegeEngineEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> AMMO = SynchedEntityData.defineId(SiegeEngineEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> RELOAD_TICKS = SynchedEntityData.defineId(SiegeEngineEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> FIRE_TICK = SynchedEntityData.defineId(SiegeEngineEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> RELOAD_START = SynchedEntityData.defineId(SiegeEngineEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> AIM_YAW = SynchedEntityData.defineId(SiegeEngineEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> AIM_PITCH = SynchedEntityData.defineId(SiegeEngineEntity.class, EntityDataSerializers.FLOAT);

    /** Copie locale du type (la boîte de collision est calculée avant que les données synchronisées n'existent). */
    private int kindCache = CATAPULT;

    /** État de jeu côté serveur : variante de niveau 3 (0 = aucune), prochain tir possible, fin de rechargement, rechargement en cours. */
    public int variant = 0;
    public long readyAt = 0, nextShot = 0;
    public boolean reloading = false;

    public SiegeEngineEntity(EntityType<? extends SiegeEngineEntity> type, Level level) {
        super(type, level);
        this.noPhysics = false;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(KIND, CATAPULT);
        entityData.define(LEVEL, 1);
        entityData.define(AMMO, 0);
        entityData.define(RELOAD_TICKS, 600);
        entityData.define(FIRE_TICK, -1_000_000L);
        entityData.define(RELOAD_START, -1_000_000L);
        entityData.define(AIM_YAW, 0f);
        entityData.define(AIM_PITCH, -18f);
    }

    // ─── Accès ───

    public int kind() { return entityData.get(KIND); }
    public boolean isBallista() { return kind() == BALLISTA; }
    public int engineLevel() { return entityData.get(LEVEL); }
    public int ammo() { return entityData.get(AMMO); }
    public int reloadTicks() { return entityData.get(RELOAD_TICKS); }
    public long fireTick() { return entityData.get(FIRE_TICK); }
    public long reloadStart() { return entityData.get(RELOAD_START); }
    public float aimYaw() { return entityData.get(AIM_YAW); }
    public float aimPitch() { return entityData.get(AIM_PITCH); }

    public void setKind(int k) { entityData.set(KIND, k); kindCache = k; setBoundingBox(makeBoundingBox()); }
    public void setEngineLevel(int l) { entityData.set(LEVEL, l); }
    public void setAmmo(int a) { entityData.set(AMMO, a); }
    public void setReloadTicks(int t) { entityData.set(RELOAD_TICKS, t); }
    public void markFired(long gameTime) { entityData.set(FIRE_TICK, gameTime); }
    public void markReload(long gameTime, int ticks) { entityData.set(RELOAD_START, gameTime); entityData.set(RELOAD_TICKS, ticks); }
    public void setAim(float yaw, float pitch) { entityData.set(AIM_YAW, yaw); entityData.set(AIM_PITCH, pitch); }

    /** Dimensions de l'emprise (en blocs) : {largeur en X, hauteur, longueur en Z}. */
    public static double[] footprint(int kind) {
        return kind == BALLISTA ? new double[]{3, 3, 3} : new double[]{2, 3, 6};
    }

    // ─── Boîte de collision rectangulaire (2 × 6 ou 3 × 3) ───

    @Override
    protected AABB makeBoundingBox() {
        double[] f = footprint(kindCache);
        return new AABB(getX() - f[0] / 2, getY(), getZ() - f[2] / 2, getX() + f[0] / 2, getY() + f[1], getZ() + f[2] / 2);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (KIND.equals(key)) {
            kindCache = entityData.get(KIND);
            setBoundingBox(makeBoundingBox());
        }
    }

    // ─── Comportement : solide, invulnérable, pilotable au clic droit ───

    @Override
    public boolean isPickable() { return !isRemoved(); }

    @Override
    public boolean canBeCollidedWith() { return true; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean isInvulnerableTo(DamageSource source) { return true; }

    @Override
    public boolean hurt(DamageSource source, float amount) { return false; }

    @Override
    public boolean skipAttackInteraction(Entity attacker) { return true; }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp) {
            com.wavesurvivor.horde.rift.RiftManager.engineUse(sp, this);
        }
        return InteractionResult.CONSUME;
    }

    // ─── Sauvegarde ───

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        setKind(t.getInt("kind"));
        entityData.set(LEVEL, Math.max(1, t.getInt("level")));
        entityData.set(AMMO, t.getInt("ammo"));
        variant = t.getInt("variant");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        t.putInt("kind", kind());
        t.putInt("level", engineLevel());
        t.putInt("ammo", ammo());
        t.putInt("variant", variant);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
