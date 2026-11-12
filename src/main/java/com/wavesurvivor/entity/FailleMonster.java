package com.wavesurvivor.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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

/**
 * 2.0 — Base des monstres de la Faille à modèle sur mesure (Géodon, Ancreur, Bolide ; modèles de Claude chat).
 * Synchronise l'état d'animation et le temps de jeu (game time) de son début, lus par le modèle via {@link FailleAnimated}.
 * Durée de chaque état (pour l'avancement 0 → 1) : {@link #stateTicks(int)}.
 */
public abstract class FailleMonster extends Monster implements FailleAnimated {

    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(FailleMonster.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STATE_START = SynchedEntityData.defineId(FailleMonster.class, EntityDataSerializers.INT);

    protected FailleMonster(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(STATE, initialState());
        entityData.define(STATE_START, 0);
    }

    /** État au moment de l'apparition (0 par défaut ; le Bolide commence en CHUTE = 0 aussi). */
    protected int initialState() { return 0; }

    /** Durée (ticks) de l'état pour l'avancement de l'animation. */
    protected abstract int stateTicks(int state);

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public int failleState() { return entityData.get(STATE); }

    @Override
    public float failleProgress(float ageInTicks) {
        float partial = Math.max(0f, Math.min(1f, ageInTicks - tickCount));
        float p = ((int) level().getGameTime() - entityData.get(STATE_START) + partial) / (float) Math.max(1, stateTicks(failleState()));
        return Math.max(0f, Math.min(1f, p));
    }

    /** Change d'état (côté serveur) et note son début. */
    protected void setState(int s) {
        entityData.set(STATE, s);
        entityData.set(STATE_START, (int) level().getGameTime());
    }

    /** Ticks écoulés depuis le début de l'état courant. */
    protected long stateAge() {
        return level().getGameTime() - entityData.get(STATE_START);
    }

    /** Multiplicateur de difficulté de la partie (×1,25 Difficile, ×1,5 Cauchemar). */
    protected static float diff() {
        return (float) com.wavesurvivor.horde.benediction.RogueUpgradeManager.difficultyMult();
    }

    /** Cible valable pour une capacité (vivante, pas un joueur en créatif / spectateur). */
    protected static boolean validTarget(LivingEntity t) {
        if (t == null || !t.isAlive()) return false;
        return !(t instanceof Player p) || (!p.isCreative() && !p.isSpectator());
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }
}
