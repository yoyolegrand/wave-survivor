package com.wavesurvivor.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * DIABLOTIN — premier monstre propre au mod (Terres Brûlées).
 * Un vex rouge braise (rendu teinté côté client) qui :
 *   - NE TRAVERSE PAS les blocs : le vex vanilla désactive ses collisions (noPhysics) à chaque tick,
 *     on les réactive pendant le déplacement ;
 *   - vole au RAS DU SOL : jamais plus de MAX_HEIGHT blocs au-dessus du sol (ramené vers le bas sinon) ;
 *   - est immunisé au feu (défini sur l'EntityType).
 * Garde l'IA d'attaque du vex (piqué vers la cible), mais sans limite de durée de vie.
 */
public class Diablotin extends Vex {

    public static final double MAX_HEIGHT = 2.0;

    public Diablotin(EntityType<? extends Vex> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 16.0)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    /** Collisions actives : le Diablotin se cogne aux murs au lieu de les traverser. */
    @Override
    public void move(MoverType type, Vec3 delta) {
        boolean saved = this.noPhysics;
        this.noPhysics = false;
        super.move(type, delta);
        this.noPhysics = saved;
    }

    /**
     * AGRESSIVITÉ PERMANENTE : le vex vanilla n'attaque qu'en « charge » à plus de 2 blocs (et le plafond de vol
     * coupe ses charges). On ajoute une cible joueur sans besoin de ligne de vue et une poursuite au corps à corps
     * prioritaire : il fonce sur le joueur et frappe dès qu'il est à portée.
     */
    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(2, new ChaseAndStrikeGoal(this));
        this.targetSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(
                this, net.minecraft.world.entity.player.Player.class, false));
    }

    /** Poursuite au vol + frappe au contact (1 coup par seconde). */
    static class ChaseAndStrikeGoal extends net.minecraft.world.entity.ai.goal.Goal {
        private final Diablotin mob;
        private int cooldown;

        ChaseAndStrikeGoal(Diablotin mob) {
            this.mob = mob;
            this.setFlags(java.util.EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            net.minecraft.world.entity.LivingEntity t = mob.getTarget();
            return t != null && t.isAlive() && mob.distanceToSqr(t) < 48 * 48;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            mob.setIsCharging(true);
        }

        @Override
        public void stop() {
            mob.setIsCharging(false);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            net.minecraft.world.entity.LivingEntity t = mob.getTarget();
            if (t == null) return;
            mob.getLookControl().setLookAt(t, 30f, 30f);
            // Vise le torse du joueur
            Vec3 aim = t.position().add(0, t.getBbHeight() * 0.55, 0);
            mob.getMoveControl().setWantedPosition(aim.x, aim.y, aim.z, 1.0);
            if (cooldown > 0) cooldown--;
            double reach = Math.max(1.6, mob.getBbWidth() * 2.0 + t.getBbWidth()) ;
            if (cooldown <= 0 && mob.distanceToSqr(t) <= reach * reach) {
                mob.doHurtTarget(t);
                cooldown = 20;
            }
        }
    }

    /** Plafond de vol : ramené vers le sol s'il dépasse MAX_HEIGHT blocs au-dessus du terrain. */
    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        double ground = groundBelow();
        if (getY() > ground + MAX_HEIGHT) {
            Vec3 v = getDeltaMovement();
            setDeltaMovement(v.x, Math.min(v.y, -0.18), v.z);
        } else if (getY() < ground + 0.3) {
            // ne s'écrase pas au sol : garde un petit vol stationnaire
            Vec3 v = getDeltaMovement();
            if (v.y < 0) setDeltaMovement(v.x, 0.04, v.z);
        }
    }

    /** Hauteur du sol juste sous le Diablotin (6 blocs de recherche). */
    private double groundBelow() {
        BlockPos.MutableBlockPos p = blockPosition().mutable();
        for (int i = 0; i < 6; i++) {
            if (!level().getBlockState(p).getCollisionShape(level(), p).isEmpty()) {
                return p.getY() + level().getBlockState(p).getCollisionShape(level(), p).max(net.minecraft.core.Direction.Axis.Y);
            }
            p.move(0, -1, 0);
        }
        return getY() - MAX_HEIGHT; // rien en dessous (vide) : pas de contrainte supplémentaire
    }
}
