package com.wavesurvivor.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * SCARABÉE (horde des Sables Ardents) — petit insecte sacré, très rapide, qui mord et arrive par dizaines.
 * Modèle sur mesure (ScarabModel). Soulève un nuage de sable en apparaissant.
 * Contrairement au poisson d'argent, il ne s'enfouit JAMAIS dans les blocs (sinon les vagues resteraient bloquées).
 */
public class Scarab extends Monster {

    private static final BlockParticleOption SAND = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());

    public Scarab(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 3;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 8.0)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.MOVEMENT_SPEED, 0.36)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, false));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        // Sort du sable en apparaissant
        if (tickCount == 2 && level() instanceof ServerLevel sl) {
            sl.sendParticles(SAND, getX(), getY() + 0.1, getZ(), 18, 0.3, 0.1, 0.3, 0.12);
            playSound(SoundEvents.SAND_BREAK, 0.6f, 1.4f);
        }
    }

    @Override
    public MobType getMobType() {
        return MobType.ARTHROPOD; // sensible au Fléau des arthropodes
    }

    @Override
    protected SoundEvent getAmbientSound() { return SoundEvents.SILVERFISH_AMBIENT; }
    @Override
    protected SoundEvent getHurtSound(DamageSource src) { return SoundEvents.SILVERFISH_HURT; }
    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.SILVERFISH_DEATH; }
    @Override
    protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.SILVERFISH_STEP, 0.15f, 1.3f); }
    @Override
    public float getVoicePitch() { return super.getVoicePitch() * 0.8f; }
}
