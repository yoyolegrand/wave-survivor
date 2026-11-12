package com.wavesurvivor.entity;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * GÉODON (monstre de la Faille) — tank mobile, modèle de Claude chat (GeodonModel).
 * États : 0 = MARCHE, 1 = ROULÉ (charge en boule, 2,5 s), 2 = GÉODE (refermé 4 s).
 *  - CHARGE EN BOULE : cible visible entre 6 et 16 blocs, recharge 10 s. Il s'enroule puis roule en ligne droite à ~3× sa vitesse ;
 *    au contact : 10 dégâts + fort recul ; arrêt contre un obstacle ou au bout de la course.
 *  - GÉODE : juste après la charge, refermé 4 s : −75 % de dégâts reçus, sauf explosions et coups portés dans le dos.
 */
public class Geodon extends FailleMonster {

    public static final int WALK = 0, ROLL = 1, GEODE = 2;
    public static final int ROLL_TICKS = 50, GEODE_TICKS = 80, CHARGE_COOLDOWN = 200;
    public static final double CHARGE_MIN = 6, CHARGE_MAX = 16, ROLL_SPEED = 0.38;
    public static final float CHARGE_DAMAGE = 10f;
    private static final DustParticleOptions AMETHYST = new DustParticleOptions(new Vector3f(0.72f, 0.42f, 1f), 1.1f);

    private long chargeReadyAt = 0;
    private Vec3 rollDir = Vec3.ZERO;
    private float geodeYaw;
    private final Set<UUID> rollHits = new HashSet<>();

    public Geodon(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 60.0)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.7);
    }

    @Override
    protected int stateTicks(int s) {
        return s == ROLL ? ROLL_TICKS : s == GEODE ? GEODE_TICKS : 20;
    }

    /** Lance la charge vers cette cible (aussi utilisé par la commande de test, sans condition). */
    public boolean startCharge(LivingEntity target) {
        if (level().isClientSide || target == null || failleState() != WALK) return false;
        Vec3 d = new Vec3(target.getX() - getX(), 0, target.getZ() - getZ());
        if (d.lengthSqr() < 1e-4) d = Vec3.directionFromRotation(0, getYRot());
        rollDir = d.normalize();
        setYRot((float) Math.toDegrees(Math.atan2(-rollDir.x, rollDir.z)));
        yBodyRot = getYRot();
        rollHits.clear();
        chargeReadyAt = level().getGameTime() + CHARGE_COOLDOWN;
        getNavigation().stop();
        setState(ROLL);
        playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.6f, 0.6f);
        return true;
    }

    /** Se referme en géode (aussi utilisé par la commande de test). */
    public void startGeode() {
        if (level().isClientSide) return;
        geodeYaw = getYRot();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        setState(GEODE);
        playSound(SoundEvents.AMETHYST_CLUSTER_PLACE, 1.6f, 0.7f);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel sl)) return;
        int st = failleState();
        long t = stateAge();
        if (st == WALK) {
            LivingEntity tg = getTarget();
            if (tickCount % 5 == 0 && validTarget(tg) && sl.getGameTime() >= chargeReadyAt) {
                double d = distanceTo(tg);
                if (d >= CHARGE_MIN && d <= CHARGE_MAX && hasLineOfSight(tg)) startCharge(tg);
            }
        } else if (st == ROLL) {
            getNavigation().stop();
            if (t < 12) {                          // il s'enroule (premier tiers de l'animation)
                setDeltaMovement(0, getDeltaMovement().y, 0);
                return;
            }
            setDeltaMovement(rollDir.x * ROLL_SPEED, getDeltaMovement().y, rollDir.z * ROLL_SPEED);
            setYRot((float) Math.toDegrees(Math.atan2(-rollDir.x, rollDir.z)));
            yBodyRot = getYRot();
            if (t % 2 == 0) {
                sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OBSIDIAN.defaultBlockState()), getX(), getY() + 0.1, getZ(), 6, 0.5, 0.05, 0.5, 0.1);
                sl.sendParticles(AMETHYST, getX(), getY() + 0.6, getZ(), 3, 0.5, 0.4, 0.5, 0);
            }
            if (t % 6 == 0) playSound(SoundEvents.STONE_STEP, 1.2f, 0.5f);
            // Contact : 10 dégâts + fort recul, une fois par cible
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.4), e -> e != this && e.isAlive() && !(e instanceof Enemy))) {
                if (!rollHits.add(e.getUUID()) || (e instanceof net.minecraft.world.entity.player.Player p && (p.isCreative() || p.isSpectator()))) continue;
                e.hurt(damageSources().mobAttack(this), CHARGE_DAMAGE * diff());
                e.setDeltaMovement(e.getDeltaMovement().add(rollDir.x * 1.6, 0.45, rollDir.z * 1.6));
                e.hurtMarked = true;
                playSound(SoundEvents.ANVIL_LAND, 0.8f, 0.6f);
            }
            if ((t > 16 && horizontalCollision) || t >= ROLL_TICKS) {
                if (horizontalCollision) sl.sendParticles(ParticleTypes.CRIT, getX(), getY() + 0.8, getZ(), 14, 0.6, 0.4, 0.6, 0.2);
                startGeode();
            }
        } else if (st == GEODE) {
            getNavigation().stop();
            setDeltaMovement(0, getDeltaMovement().y, 0);
            setYRot(geodeYaw);
            yBodyRot = geodeYaw;
            if (t % 10 == 0) sl.sendParticles(AMETHYST, getX(), getY() + 0.9, getZ(), 4, 0.6, 0.5, 0.6, 0);
            if (t >= GEODE_TICKS) {
                setState(WALK);
                playSound(SoundEvents.AMETHYST_BLOCK_BREAK, 1.2f, 0.8f);
            }
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (failleState() != WALK) return false;   // ni coup de patte pendant la charge, ni refermé
        return super.doHurtTarget(target);
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (failleState() == GEODE && !src.is(DamageTypeTags.IS_EXPLOSION) && !src.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !fromBehind(src)) {
            amount *= 0.25f;   // géode : −75 % de face
            if (level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.ENCHANTED_HIT, getX(), getY() + 1, getZ(), 6, 0.5, 0.4, 0.5, 0.1);
            playSound(SoundEvents.AMETHYST_BLOCK_HIT, 1f, 1.2f);
        }
        return super.hurt(src, amount);
    }

    /** Coup porté dans le dos (par rapport à son orientation au moment de se refermer). */
    private boolean fromBehind(DamageSource src) {
        Vec3 from = src.getSourcePosition();
        if (from == null) return false;
        Vec3 look = Vec3.directionFromRotation(0, geodeYaw);
        Vec3 to = new Vec3(from.x - getX(), 0, from.z - getZ());
        if (to.lengthSqr() < 1e-4) return false;
        return look.dot(to.normalize()) < -0.35;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() { return SoundEvents.AMETHYST_BLOCK_RESONATE; }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource s) { return SoundEvents.AMETHYST_BLOCK_HIT; }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() { return SoundEvents.AMETHYST_CLUSTER_BREAK; }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState st) {
        playSound(SoundEvents.STONE_STEP, 0.6f, 0.7f);
    }
}
