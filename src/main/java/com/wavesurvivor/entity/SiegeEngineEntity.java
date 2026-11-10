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
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(SiegeEngineEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> AMMO = SynchedEntityData.defineId(SiegeEngineEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> RELOAD_TICKS = SynchedEntityData.defineId(SiegeEngineEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> FIRE_TICK = SynchedEntityData.defineId(SiegeEngineEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> RELOAD_START = SynchedEntityData.defineId(SiegeEngineEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> AIM_YAW = SynchedEntityData.defineId(SiegeEngineEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> AIM_PITCH = SynchedEntityData.defineId(SiegeEngineEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HP = SynchedEntityData.defineId(SiegeEngineEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> MAX_HP = SynchedEntityData.defineId(SiegeEngineEntity.class, EntityDataSerializers.FLOAT);

    /** Copie locale du type (la boîte de collision est calculée avant que les données synchronisées n'existent). */
    private int kindCache = CATAPULT;

    /** État de jeu côté serveur : variante de niveau 3 (0 = aucune), prochain tir possible, fin de rechargement, rechargement en cours. */
    public long readyAt = 0, nextShot = 0;
    public boolean reloading = false;
    /** Tour-catapulte : cible actuelle et prochaine recherche de cible (non sauvegardées). */
    public java.util.UUID targetId;
    public long nextAcquire = 0;

    public SiegeEngineEntity(EntityType<? extends SiegeEngineEntity> type, Level level) {
        super(type, level);
        this.noPhysics = false;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(KIND, CATAPULT);
        entityData.define(LEVEL, 1);
        entityData.define(VARIANT, 0);
        entityData.define(AMMO, 0);
        entityData.define(RELOAD_TICKS, 600);
        entityData.define(FIRE_TICK, -1_000_000L);
        entityData.define(RELOAD_START, -1_000_000L);
        entityData.define(AIM_YAW, 0f);
        entityData.define(AIM_PITCH, -18f);
        entityData.define(HP, 0f);
        entityData.define(MAX_HP, 0f);
    }

    // ─── Accès ───

    public int kind() { return entityData.get(KIND); }
    public boolean isBallista() { return kind() == BALLISTA; }
    public int engineLevel() { return entityData.get(LEVEL); }
    public int ammo() { return entityData.get(AMMO); }
    /** Variante de niveau 3 (0 = aucune) : synchronisée, le client choisit le modèle d'après elle. */
    public int variant() { return entityData.get(VARIANT); }
    public void setVariant(int v) { entityData.set(VARIANT, v); }
    public int reloadTicks() { return entityData.get(RELOAD_TICKS); }
    public long fireTick() { return entityData.get(FIRE_TICK); }
    public long reloadStart() { return entityData.get(RELOAD_START); }
    public float aimYaw() { return entityData.get(AIM_YAW); }
    public float aimPitch() { return entityData.get(AIM_PITCH); }

    public void setKind(int k) { entityData.set(KIND, k); kindCache = k; setBoundingBox(makeBoundingBox()); }
    public void setEngineLevel(int l) { entityData.set(LEVEL, l); applyMaxHp(); }

    // ─── Points de vie : les monstres, leurs explosions et les attaques du Titan abîment l'engin ; à 0, il est détruit ───

    public float hp() { return entityData.get(HP); }
    public float maxHp() { return entityData.get(MAX_HP); }

    /** PV max selon le type et le niveau (tour-catapulte 800, baliste 400 ; ×1,3 au niveau 2, ×1,6 au niveau 3). */
    public float baseMaxHp() {
        float b = isBallista() ? 400f : 800f;
        int lv = engineLevel();
        // mêmes bonus que les bâtiments du royaume : Bâtisseur, Pierre de Fondation, réglage de PV de la horde
        float bonus = level().isClientSide ? 1f : (float) com.wavesurvivor.horde.kingdom.KingdomClaim.buildingHpMult();
        return b * (lv >= 3 ? 1.6f : lv == 2 ? 1.3f : 1f) * bonus;
    }

    /** Recalcule les PV max (pose, amélioration) en gardant la même proportion de PV. */
    public void applyMaxHp() {
        float old = maxHp(), m = baseMaxHp();
        float ratio = old > 0 ? Math.max(0f, Math.min(1f, hp() / old)) : 1f;
        entityData.set(MAX_HP, m);
        entityData.set(HP, m * ratio);
        updateLabel();
    }

    public void setHpRatio(float r) {
        entityData.set(HP, maxHp() * Math.max(0.01f, Math.min(1f, r)));
        updateLabel();
    }

    /** Réparation complète. */
    public void repair() {
        entityData.set(HP, maxHp());
        updateLabel();
    }

    /** Dégâts reçus (monstres, explosions, Titan). À 0 PV, l'engin est détruit. */
    public void damage(float amount) {
        if (level().isClientSide || isRemoved() || amount <= 0 || maxHp() <= 0) return;
        float hp = Math.max(0f, hp() - amount);
        entityData.set(HP, hp);
        updateLabel();
        if (level() instanceof net.minecraft.server.level.ServerLevel sl) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, getX(), getY() + footprint(kindCache)[1] * 0.6, getZ(), 6, 0.6, 0.6, 0.6, 0.1);
        }
        if (hp <= 0) com.wavesurvivor.horde.rift.RiftManager.engineDestroyed(this);
    }

    /** Étiquette de PV au-dessus de l'engin (visible dès qu'il est abîmé). */
    private void updateLabel() {
        if (level().isClientSide) return;
        float m = maxHp(), h = hp();
        if (m <= 0) return;
        float f = h / m;
        String col = f > 0.6f ? "§a" : f > 0.3f ? "§e" : "§c";
        setCustomName(net.minecraft.network.chat.Component.literal(col + "♥ " + (int) Math.ceil(h) + " §7/ " + (int) m));
        setCustomNameVisible(h < m);
    }
    public void setAmmo(int a) { entityData.set(AMMO, a); }
    public void setReloadTicks(int t) { entityData.set(RELOAD_TICKS, t); }
    public void markFired(long gameTime) { entityData.set(FIRE_TICK, gameTime); }
    public void markReload(long gameTime, int ticks) { entityData.set(RELOAD_START, gameTime); entityData.set(RELOAD_TICKS, ticks); }
    public void setAim(float yaw, float pitch) { entityData.set(AIM_YAW, yaw); entityData.set(AIM_PITCH, pitch); }

    /** Dimensions de l'emprise (en blocs) : {largeur en X, hauteur, longueur en Z}. */
    public static double[] footprint(int kind) {
        return kind == BALLISTA ? new double[]{3, 3, 3} : new double[]{6, 6, 6};   // la catapulte est une tour automatique de 6 × 6 × 6
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

    // ─── Pilotage de la baliste : le joueur s'assoit derrière l'arme (caméra légèrement au-dessus) ───

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return isBallista() && getPassengers().isEmpty() && passenger instanceof Player;
    }

    @Override
    public boolean shouldRiderSit() { return true; }

    /** Le pilote est assis derrière l'arme, dans l'axe de sa visée, un peu au-dessus (vue dégagée au-dessus du fût). */
    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction cb) {
        if (!hasPassenger(passenger)) return;
        double yaw = Math.toRadians(passenger.getYRot());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        cb.accept(passenger, getX() - fx * 0.95, getY() + 0.95, getZ() - fz * 0.95);
    }

    @Override
    public void tick() {
        super.tick();
        // Serveur : la tourelle et l'arme suivent la visée du pilote
        if (!level().isClientSide && isBallista() && getFirstPassenger() instanceof ServerPlayer sp) {
            setAim(sp.getYRot(), Math.max(-60f, Math.min(40f, sp.getXRot())));
        }
    }

    // ─── Comportement : solide, invulnérable, pilotable au clic droit ───

    @Override
    public boolean isPickable() { return !isRemoved(); }

    @Override
    public boolean canBeCollidedWith() { return true; }

    @Override
    public boolean isPushable() { return false; }

    /** Seuls les ennemis (coups, flèches, explosions des monstres) l'abîment : jamais les joueurs ni les défenses alliées. */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !(source.getEntity() instanceof net.minecraft.world.entity.monster.Enemy);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isInvulnerableTo(source)) return false;
        damage(amount);
        return true;
    }

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
        setVariant(t.getInt("variant"));
        applyMaxHp();
        if (t.contains("hp")) setHpRatio(t.getFloat("hp"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        t.putInt("kind", kind());
        t.putInt("level", engineLevel());
        t.putInt("ammo", ammo());
        t.putInt("variant", variant());
        if (maxHp() > 0) t.putFloat("hp", hp() / maxHp());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
