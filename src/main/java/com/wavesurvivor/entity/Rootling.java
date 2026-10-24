package com.wavesurvivor.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/**
 * ROOTLING (horde des Corrompus) — petit être de racines au masque-totem, modèle sur mesure (RootlingModel).
 * Essaim : rapide, fragile, arrive en grands groupes, lacère de ses griffes et éclate en copeaux de bois à sa mort.
 */
public class Rootling extends Monster {

    private static final BlockParticleOption WOOD = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DARK_OAK_LOG.defaultBlockState());
    private static final BlockParticleOption MOSS = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MOSS_BLOCK.defaultBlockState());
    private static final DustParticleOptions CORRUPTION = new DustParticleOptions(new Vector3f(1.0f, 0.25f, 0.8f), 1.0f);

    public Rootling(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 3;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.MOVEMENT_SPEED, 0.36)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(3, new LeapAtTargetGoal(this, 0.35f));
        goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.2, false));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.9));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        // Fine poussière de corruption qui s'échappe de lui
        if (level() instanceof ServerLevel sl && tickCount % 12 == 0) {
            sl.sendParticles(CORRUPTION, getX(), getY() + 0.8, getZ(), 1, 0.2, 0.3, 0.2, 0.0);
        }
    }

    /** Mort : il éclate en copeaux de bois, mousse et lueur magenta. */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(WOOD, getX(), getY() + 0.6, getZ(), 30, 0.3, 0.4, 0.3, 0.15);
            sl.sendParticles(MOSS, getX(), getY() + 0.5, getZ(), 10, 0.3, 0.3, 0.3, 0.1);
            sl.sendParticles(CORRUPTION, getX(), getY() + 0.8, getZ(), 15, 0.3, 0.4, 0.3, 0.0);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() { return SoundEvents.ENDERMITE_AMBIENT; }
    @Override
    protected SoundEvent getHurtSound(DamageSource src) { return SoundEvents.WOOD_HIT; }
    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.WOOD_BREAK; }
    @Override
    protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.AZALEA_LEAVES_STEP, 0.4f, 1.2f); }
    @Override
    public float getVoicePitch() { return super.getVoicePitch() * 0.7f; }
}
