package com.wavesurvivor.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 2.1 — BRISE-PONT (fiche docs/faille/v2_1_ilots/FICHE_2_1_0.md § 8) : créature de la Faille, taille d'une araignée.
 * Pendant un Assaut du Siège, il grimpe depuis le vide sous un pont, s'agrippe au tablier et ronge le segment (fissures orange,
 * effondrement au bout de ~15 s), puis passe au segment suivant. Toute la logique de pont est dans RiftIslets ; ici : déplacement
 * scripté (montée), pose « ronge », point faible du dos (dégâts ×2), recul renforcé et mort immédiate s'il tombe dans le vide.
 */
public class BrisePont extends Monster {

    private static final EntityDataAccessor<Boolean> GNAW = SynchedEntityData.defineId(BrisePont.class, EntityDataSerializers.BOOLEAN);
    private static final DustParticleOptions ORANGE = new DustParticleOptions(new Vector3f(1f, 0.45f, 0.1f), 1.2f);
    private static final DustParticleOptions CYAN = new DustParticleOptions(new Vector3f(0.27f, 0.95f, 1f), 1.4f);

    // ── état serveur (non sauvegardé : les Brise-pont ne survivent pas à une reprise) ──
    public int isletKind = -1, segment = -1;
    private Vec3 climbFrom, climbTo;
    private int climbTicks = -1, climbLen = 30;
    private int deckY = Integer.MIN_VALUE;
    private int biteCd, unhooked;
    /** Endroit du tablier à ronger (milieu de son segment) ; null = pas de pont attribué → il chasse les joueurs. */
    private BlockPos gnawAt;
    private int seekCd, seekTicks;

    // ── animation (client) ──
    private float gnawO, gnawV;

    public BrisePont(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 8;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 16.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(GNAW, false);
    }

    /**
     * IA : s'il y a un pont de la Faille à portée, il y va et le ronge (RiftIslets.claimBridge) ; sinon il chasse les joueurs
     * comme une araignée. Les objectifs de combat et de promenade se taisent pendant qu'il ronge, grimpe ou marche vers son pont.
     */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new net.minecraft.world.entity.ai.goal.MeleeAttackGoal(this, 1.15, false) {
            @Override public boolean canUse() { return !busy() && super.canUse(); }
            @Override public boolean canContinueToUse() { return !busy() && super.canContinueToUse(); }
        });
        goalSelector.addGoal(5, new net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override public boolean canUse() { return !busy() && super.canUse(); }
            @Override public boolean canContinueToUse() { return !busy() && super.canContinueToUse(); }
        });
        goalSelector.addGoal(6, new net.minecraft.world.entity.ai.goal.LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(7, new net.minecraft.world.entity.ai.goal.RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal(this) {
            @Override public boolean canUse() { return !busy() && super.canUse(); }
        });
        targetSelector.addGoal(2, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(this, Player.class, true) {
            @Override public boolean canUse() { return !busy() && super.canUse(); }
        });
    }

    /** Occupé par son pont (il ronge, grimpe ou y marche) : pas de combat ni de promenade. */
    public boolean busy() { return gnawing() || climbing() || gnawAt != null; }

    /** Pont attribué : il marche jusqu'à {@code at} (milieu du segment) puis ronge. */
    public void assignBridge(int kind, int seg, BlockPos at) {
        isletKind = kind;
        segment = seg;
        gnawAt = at;
        deckY = at.getY();
        seekTicks = 0;
        setTarget(null);
    }

    /** Plus de pont à ronger : il redevient un chasseur. */
    public void releaseBridge() {
        isletKind = -1;
        segment = -1;
        gnawAt = null;
        deckY = Integer.MIN_VALUE;
        setGnawing(false);
        seekCd = 100;
    }

    public boolean gnawing() { return entityData.get(GNAW); }

    public void setGnawing(boolean b) { entityData.set(GNAW, b); }

    /** Pose « ronge » interpolée (0 → 1), pour le modèle. */
    public float gnawAmount(float pt) { return Mth.lerp(pt, gnawO, gnawV); }

    /** Monte depuis le vide (de {@code from} à {@code to}, en traversant le tablier), puis ronge. */
    public void startClimb(Vec3 from, Vec3 to, int deck) {
        climbFrom = from;
        climbTo = to;
        gnawAt = BlockPos.containing(to.x, deck, to.z);
        climbTicks = 0;
        deckY = deck;
        setNoGravity(true);
        noPhysics = true;
        moveTo(from.x, from.y, from.z, getYRot(), 0f);
    }

    /** Saute sur un autre segment (le sien va s'effondrer). */
    public void hopTo(Vec3 to) {
        startClimb(position(), to, deckY);
        climbLen = 12;
        setGnawing(false);
    }

    public boolean climbing() { return climbTicks >= 0; }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            gnawO = gnawV;
            gnawV = Mth.approach(gnawV, gnawing() ? 1f : 0f, 0.15f);
            return;
        }
        ServerLevel l = (ServerLevel) level();
        if (climbTicks >= 0 && climbFrom != null && climbTo != null) {
            climbTicks++;
            float t = Math.min(1f, climbTicks / (float) climbLen);
            // monte d'abord à la verticale, puis se hisse sur le tablier
            double up = Math.min(1.0, t / 0.7), side = Math.max(0.0, (t - 0.6) / 0.4);
            double x = Mth.lerp(side, climbFrom.x, climbTo.x), z = Mth.lerp(side, climbFrom.z, climbTo.z);
            double y = Mth.lerp(up, climbFrom.y, climbTo.y);
            setPos(x, y, z);
            setDeltaMovement(Vec3.ZERO);
            if (climbTicks % 4 == 0) l.playSound(null, blockPosition(), SoundEvents.SPIDER_STEP, SoundSource.HOSTILE, 0.6f, 0.7f);
            if (t >= 1f) {
                climbTicks = -1;
                setNoGravity(false);
                noPhysics = false;
                setGnawing(true);
            }
            return;
        }
        // tombé du pont : le vide l'avale (mort immédiate)
        if (deckY != Integer.MIN_VALUE && getY() < deckY - 4) {
            l.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY(), getZ(), 30, 0.4, 0.4, 0.4, 0.2);
            hurt(damageSources().fellOutOfWorld(), Float.MAX_VALUE);
            return;
        }
        // cherche un pont (toutes les 2 s) ; marche vers son segment ; s'il n'y arrive pas, il chasse les joueurs un moment
        if (!gnawing() && tickCount % 20 == 0) {
            if (gnawAt == null) {
                if (seekCd > 0) seekCd -= 20;
                else com.wavesurvivor.horde.rift.RiftIslets.claimBridge(this);
            } else {
                double d = Math.hypot(getX() - (gnawAt.getX() + 0.5), getZ() - (gnawAt.getZ() + 0.5));
                if (d > 1.6 && unhooked <= 0) {
                    setTarget(null);
                    getNavigation().moveTo(gnawAt.getX() + 0.5, gnawAt.getY() + 1, gnawAt.getZ() + 0.5, 1.1);
                    seekTicks += 20;
                    if (seekTicks > 400) releaseBridge();   // pont inaccessible : il abandonne
                }
            }
        }
        if (gnawing()) {
            setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0);
            getNavigation().stop();
            if (tickCount % 5 == 0) l.sendParticles(ORANGE, getX(), getY() + 0.1, getZ(), 3, 0.5, 0.05, 0.5, 0.01);
            if (tickCount % 12 == 0) l.playSound(null, blockPosition(), SoundEvents.GRINDSTONE_USE, SoundSource.HOSTILE, 0.5f, 0.6f);
            // morsure : les joueurs tout près
            if (biteCd > 0) biteCd--;
            else {
                Player p = l.getNearestPlayer(this, 2.3);
                if (p != null && !p.isSpectator() && !p.isCreative()) {
                    swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                    doHurtTarget(p);
                    biteCd = 30;
                }
            }
        }
    }

    /** Point faible du dos : coup porté de derrière ou d'au-dessus → dégâts ×2. */
    @Override
    public boolean hurt(DamageSource src, float amount) {
        Entity att = src.getEntity();
        if (att != null && !level().isClientSide && amount < Float.MAX_VALUE / 2) {
            Vec3 facing = Vec3.directionFromRotation(0, getYRot());
            Vec3 to = att.position().subtract(position()).multiply(1, 0, 1);
            boolean behind = to.lengthSqr() > 1e-4 && facing.dot(to.normalize()) < -0.3;
            boolean above = att.getY() > getY() + 1.6 || (src.getDirectEntity() != null && src.getDirectEntity() != att && src.getDirectEntity().getDeltaMovement().y < -0.3);
            if (behind || above) {
                amount *= 2f;
                ((ServerLevel) level()).sendParticles(CYAN, getX(), getY() + 0.8, getZ(), 12, 0.3, 0.2, 0.3, 0.05);
                level().playSound(null, blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 1f, 1.4f);
            }
        }
        return super.hurt(src, amount);
    }

    /** Recul renforcé : un bon coup le jette dans le vide. */
    @Override
    public void knockback(double strength, double x, double z) {
        if (climbing()) return;
        if (gnawing()) setGnawing(false);   // décroché, il se remet à ronger quand il retouche le tablier
        unhooked = 12;
        super.knockback(strength * 1.8, x, z);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        // décroché par un coup mais toujours sur le tablier : il se raccroche
        if (level().isClientSide) return;
        if (unhooked > 0) unhooked--;
        else if (!gnawing() && !climbing() && onGround() && segment >= 0 && gnawAt != null
                && Math.hypot(getX() - (gnawAt.getX() + 0.5), getZ() - (gnawAt.getZ() + 0.5)) <= 1.6
                && Math.abs(getY() - (gnawAt.getY() + 1)) < 1.2) {
            getNavigation().stop();
            seekTicks = 0;
            setGnawing(true);
        }
    }

    @Override
    public boolean removeWhenFarAway(double d) { return false; }

    @Override
    public boolean causeFallDamage(float dist, float mult, DamageSource src) { return false; }

    @Override
    protected SoundEvent getAmbientSound() { return SoundEvents.SPIDER_AMBIENT; }

    @Override
    protected SoundEvent getHurtSound(DamageSource src) { return SoundEvents.AMETHYST_BLOCK_HIT; }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.AMETHYST_CLUSTER_BREAK; }

    @Override
    protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState st) {
        playSound(SoundEvents.SPIDER_STEP, 0.15f, 1.2f);
    }

}
