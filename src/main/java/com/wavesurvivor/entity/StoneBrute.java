package com.wavesurvivor.entity;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsTargetGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3f;

/**
 * STONE BRUTE (horde des Corrompus) — colosse de rochers moussus, modèle sur mesure (StoneBruteModel, haute résolution).
 * Tank de première ligne : lent mais implacable, insensible au recul.
 *  - PEAU DE PIERRE : au-delà de 6 dégâts, le reste d'un coup est réduit de 40 %.
 *  - FRAPPE ÉCRASANTE : coup lent et très puissant (animation du poing levé), qui projette en l'air (Golem de fer).
 */
public class StoneBrute extends IronGolem implements Enemy {

    private static final BlockParticleOption STONE = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
    private static final DustParticleOptions CORRUPTION = new DustParticleOptions(new Vector3f(1.0f, 0.28f, 0.8f), 1.2f);

    public StoneBrute(EntityType<? extends IronGolem> type, Level level) {
        super(type, level);
        this.xpReward = 20;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return IronGolem.createAttributes()
                .add(Attributes.MAX_HEALTH, 80.0)
                .add(Attributes.ATTACK_DAMAGE, 12.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.addGoal(3, new MoveTowardsTargetGoal(this, 0.9, 32.0f));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** PEAU DE PIERRE : les gros coups sont amortis. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (amount > 6.0f) amount = 6.0f + (amount - 6.0f) * 0.6f;
        boolean hit = super.hurt(source, amount);
        if (hit && level() instanceof ServerLevel sl) {
            sl.sendParticles(STONE, getX(), getY() + getBbHeight() * 0.6, getZ(), 10, 0.4, 0.5, 0.4, 0.1);
        }
        return hit;
    }

    /** FRAPPE ÉCRASANTE : éclats de pierre et lueur rose à l'impact. */
    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && level() instanceof ServerLevel sl) {
            sl.sendParticles(STONE, target.getX(), target.getY() + 0.3, target.getZ(), 25, 0.5, 0.2, 0.5, 0.15);
            sl.sendParticles(CORRUPTION, target.getX(), target.getY() + 0.5, target.getZ(), 12, 0.5, 0.3, 0.5, 0.0);
        }
        return hit;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        // Poussière de corruption qui s'échappe des fissures
        if (level() instanceof ServerLevel sl && tickCount % 10 == 0) {
            sl.sendParticles(CORRUPTION, getX(), getY() + getBbHeight() * 0.65, getZ(), 1, 0.4, 0.4, 0.4, 0.0);
        }
    }

    /** Pas lourds : grondement grave et poussière à chaque pas. */
    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        playSound(net.minecraft.sounds.SoundEvents.IRON_GOLEM_STEP, 1.6f, 0.55f);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state.isAir() ? Blocks.DIRT.defaultBlockState() : state),
                    getX(), getY() + 0.1, getZ(), 14, 0.7, 0.05, 0.7, 0.12);
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.6f; // grondement grave
    }
}
