package com.wavesurvivor.entity;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsTargetGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

/**
 * GOLEM DE GIVRE (horde des Pics Gelés) — Golem de fer corrompu par le froid, HOSTILE.
 * Même modèle et mêmes animations que le Golem de fer (texture à lui). Élite lourde : lent, très résistant.
 *  - Poing glacé : projette comme le Golem de fer + Lenteur II 3 s.
 *  - Onde de gel (toutes les ~10 s, cible à 5 blocs) : cercle de givre, immobilise 1 s puis ralentit.
 *  - Armure de glace : −25 % de dégâts des projectiles tant qu'il a plus de 50 % de PV.
 *  - Faiblesse au feu : +50 % de dégâts de feu.
 *  - Sous 50 % de PV : sa glace se fissure, il devient plus rapide (rage).
 *  - À sa mort : éclatement en éclats de glace qui ralentissent les alentours (sans dégâts).
 * Implémente Enemy : ciblé par les tours, pièges et soldats comme les autres monstres.
 */
public class FrostGolem extends IronGolem implements Enemy {

    private static final UUID RAGE_SPEED = UUID.fromString("6c1f7a2e-3b8d-4e51-9a0c-1f2e3d4c5b61");
    private int slamCooldown = 120;
    private boolean enraged = false;

    public FrostGolem(EntityType<? extends IronGolem> type, Level level) {
        super(type, level);
        this.xpReward = 20;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return IronGolem.createAttributes()
                .add(Attributes.MAX_HEALTH, 120.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25 * 0.85)
                .add(Attributes.ATTACK_DAMAGE, 12.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        // Pas de super : on remplace l'IA « protecteur du village » par une IA hostile
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.addGoal(2, new MoveTowardsTargetGoal(this, 0.9, 32.0f));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.6));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, FrostGolem.class));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, KingdomSoldier.class, true));
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, false));
        this.targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, IronGolem.class, true,
                e -> !(e instanceof FrostGolem)));
    }

    /** Ne s'en prend jamais à un autre Golem de Givre. */
    @Override
    public boolean canAttack(LivingEntity target) {
        return !(target instanceof FrostGolem) && super.canAttack(target);
    }

    @Override
    public boolean isPlayerCreated() {
        return false;
    }

    /** Poing glacé : l'attaque du Golem de fer (projection, animation) + Lenteur II 3 s. */
    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity le) {
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            le.setTicksFrozen(Math.max(le.getTicksFrozen(), 100));
            if (level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.SNOWFLAKE, le.getX(), le.getY() + 1, le.getZ(), 15, 0.3, 0.5, 0.3, 0.05);
            }
        }
        return hit;
    }

    /** Armure de glace (projectiles −25 % au-dessus de 50 % de PV) et faiblesse au feu (+50 %). */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_FIRE)) amount *= 1.5f;
        else if (source.is(DamageTypeTags.IS_PROJECTILE) && getHealth() > getMaxHealth() * 0.5f) amount *= 0.75f;
        return super.hurt(source, amount);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        ServerLevel sl = (ServerLevel) level();
        // Rage : sous 50 % de PV, la glace se fissure et il accélère
        if (!enraged && getHealth() < getMaxHealth() * 0.5f) {
            enraged = true;
            var spd = getAttribute(Attributes.MOVEMENT_SPEED);
            if (spd != null && spd.getModifier(RAGE_SPEED) == null) {
                spd.addTransientModifier(new AttributeModifier(RAGE_SPEED, "frost_golem_rage", 0.35,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
            sl.playSound(null, blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.2f, 0.6f);
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()),
                    getX(), getY() + 1.5, getZ(), 40, 0.6, 0.8, 0.6, 0.1);
        }
        if (enraged && tickCount % 10 == 0) {
            sl.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + 2.2, getZ(), 3, 0.4, 0.3, 0.4, 0.01);
        }
        // Onde de gel : toutes les ~10 s si une cible est à 5 blocs
        if (slamCooldown > 0) slamCooldown--;
        LivingEntity target = getTarget();
        if (slamCooldown <= 0 && target != null && target.isAlive() && distanceToSqr(target) < 5 * 5) {
            slamCooldown = 200;
            frostSlam(sl);
        }
    }

    /** Onde de gel : cercle de givre de 5 blocs — immobilise 1 s, puis Lenteur II 3 s. */
    private void frostSlam(ServerLevel sl) {
        sl.broadcastEntityEvent(this, (byte) 4); // animation de frappe du Golem de fer
        sl.playSound(null, blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.5f, 0.5f);
        sl.playSound(null, blockPosition(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 1.0f, 0.7f);
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI * 2 / 48;
            for (double r = 1.5; r <= 5; r += 1.75) {
                sl.sendParticles(ParticleTypes.SNOWFLAKE, getX() + Math.cos(a) * r, getY() + 0.2, getZ() + Math.sin(a) * r,
                        1, 0.05, 0.05, 0.05, 0.01);
            }
        }
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()),
                getX(), getY() + 0.3, getZ(), 60, 2.0, 0.2, 2.0, 0.15);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(blockPosition()).inflate(5, 2, 5),
                e -> e != this && e.isAlive() && !(e instanceof Enemy))) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 9));
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
            e.setTicksFrozen(Math.max(e.getTicksFrozen(), 160));
        }
    }

    /** Éclatement : des éclats de glace qui ralentissent les alentours (sans dégâts). */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel sl) {
            sl.playSound(null, blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.5f, 0.8f);
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()),
                    getX(), getY() + 1.2, getZ(), 80, 0.8, 1.0, 0.8, 0.2);
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(4),
                    e -> e != this && !(e instanceof Enemy))) {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            }
        }
    }
}
