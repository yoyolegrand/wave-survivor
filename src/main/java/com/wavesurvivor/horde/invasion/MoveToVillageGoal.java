package com.wavesurvivor.horde.invasion;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Un envahisseur sans cible marche vers le centre du village (la cloche), comme les pillards d'un raid vanilla.
 * S'efface dès qu'il a une cible (villageois, golem, joueur) ou qu'il est arrivé.
 */
public class MoveToVillageGoal extends Goal {

    private final Mob mob;
    private final Vec3 target;
    private final double speed;

    public MoveToVillageGoal(Mob mob, Vec3 target, double speed) {
        this.mob = mob;
        this.target = target;
        this.speed = speed;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return mob.isAlive() && mob.getTarget() == null && mob.distanceToSqr(target) > 14 * 14;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse() && !mob.getNavigation().isDone();
    }

    @Override
    public void start() {
        mob.getNavigation().moveTo(target.x, target.y, target.z, speed);
    }

    @Override
    public void tick() {
        if (mob.tickCount % 40 == 0 && mob.getNavigation().isDone()) {
            mob.getNavigation().moveTo(target.x, target.y, target.z, speed);
        }
    }

    @Override
    public void stop() {
        // la navigation se termine d'elle-même
    }
}
