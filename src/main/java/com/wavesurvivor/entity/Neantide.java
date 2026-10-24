package com.wavesurvivor.entity;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * NÉANTIDE — créature du néant insectoïde et humanoïde, modèle sur mesure (NeantideModel, haute résolution).
 * Compétences intégrées :
 *  - BOND DU NÉANT : toutes les ~6 s, s'il est à 4-12 blocs de sa cible, il bondit sur elle (gerbe de particules du néant).
 *  - FAUX DE L'ABÎME : chaque coup flétrit la cible (Wither I, 3 s).
 *  - REPLI DANS LE NÉANT : blessé sous 60 % de sa vie, 1 chance sur 3 de se téléporter à quelques blocs (5 s de recharge).
 */
public class Neantide extends Monster {

    private static final DustParticleOptions VOID = new DustParticleOptions(new Vector3f(0.73f, 0.36f, 1.0f), 1.2f);
    private int leapCd = 80, blinkCd = 0;
    /** Animation de fauchage (16 ticks = 0,8 s), déclenchée par l'événement 4 envoyé à chaque coup. */
    public static final int ATTACK_ANIM_TICKS = 16;
    private int attackAnim = 0;

    /** Progression du fauchage, de 0 (repos) à 1 (fin du coup). */
    public float getAttackProgress(float partialTick) {
        if (attackAnim <= 0) return 0f;
        return Math.min(1f, (ATTACK_ANIM_TICKS - attackAnim + partialTick) / ATTACK_ANIM_TICKS);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 4) attackAnim = ATTACK_ANIM_TICKS;
        else super.handleEntityEvent(id);
    }

    @Override
    public void tick() {
        super.tick();
        if (attackAnim > 0) attackAnim--;
    }

    public Neantide(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 15;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 34.0)
                .add(Attributes.ATTACK_DAMAGE, 7.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.15, true));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.85));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel sl)) return;
        if (tickCount % 8 == 0) sl.sendParticles(VOID, getX(), getY() + 1.4, getZ() + 0.0, 1, 0.3, 0.4, 0.3, 0.0);
        if (leapCd > 0) leapCd--;
        if (blinkCd > 0) blinkCd--;
        // BOND DU NÉANT
        LivingEntity t = getTarget();
        if (t != null && t.isAlive() && leapCd == 0 && onGround()) {
            double d = distanceTo(t);
            if (d >= 4 && d <= 12 && hasLineOfSight(t)) {
                leapCd = 120;
                Vec3 dir = t.position().subtract(position()).normalize();
                setDeltaMovement(dir.x * (0.45 + d * 0.06), 0.55, dir.z * (0.45 + d * 0.06));
                hasImpulse = true;
                sl.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 0.5, getZ(), 30, 0.4, 0.3, 0.4, 0.3);
                sl.sendParticles(VOID, getX(), getY() + 0.8, getZ(), 15, 0.4, 0.4, 0.4, 0.0);
                sl.playSound(null, blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.HOSTILE, 1.2f, 0.6f);
            }
        }
    }

    /** FAUX DE L'ABÎME : la cible est flétrie. */
    @Override
    public boolean doHurtTarget(Entity target) {
        level().broadcastEntityEvent(this, (byte) 4);   // lance l'animation de fauchage chez les joueurs
        attackAnim = ATTACK_ANIM_TICKS;
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity le) {
            le.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0), this);
            if (level() instanceof ServerLevel sl) {
                sl.sendParticles(VOID, le.getX(), le.getY() + 1.0, le.getZ(), 10, 0.3, 0.4, 0.3, 0.0);
            }
        }
        return hit;
    }

    /** REPLI DANS LE NÉANT : se téléporte quand il est blessé. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (hit && isAlive() && blinkCd == 0 && getHealth() < getMaxHealth() * 0.6f && random.nextFloat() < 0.33f
                && level() instanceof ServerLevel sl) {
            double ox = getX(), oy = getY(), oz = getZ();
            for (int i = 0; i < 10; i++) {
                if (randomTeleport(ox + (random.nextDouble() - 0.5) * 12, oy + random.nextInt(4) - 1, oz + (random.nextDouble() - 0.5) * 12, true)) {
                    blinkCd = 100;
                    sl.sendParticles(ParticleTypes.PORTAL, ox, oy + 1, oz, 40, 0.4, 0.8, 0.4, 0.4);
                    sl.sendParticles(VOID, getX(), getY() + 1, getZ(), 20, 0.4, 0.6, 0.4, 0.0);
                    sl.playSound(null, blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0f, 0.7f);
                    break;
                }
            }
        }
        return hit;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false; // retombe sans dégâts après ses bonds
    }

    @Override
    public MobType getMobType() {
        return MobType.ARTHROPOD;
    }

    @Override
    protected SoundEvent getAmbientSound() { return SoundEvents.SILVERFISH_AMBIENT; }
    @Override
    protected SoundEvent getHurtSound(DamageSource src) { return SoundEvents.ENDERMITE_HURT; }
    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.ENDERMITE_DEATH; }
    @Override
    public float getVoicePitch() { return super.getVoicePitch() * 0.5f; } // cliquetis grave et inquiétant
}
