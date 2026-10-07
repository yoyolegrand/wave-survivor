package com.wavesurvivor.horde.ai;

import com.wavesurvivor.WaveSurvivorMod;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.util.EnumSet;

/**
 * IA de LANCEUR DE SORTS : garde une distance idéale avec sa cible au lieu d'aller au corps à corps.
 *   distance < idéal - 3 → recule ; > idéal + 3 → se rapproche ; entre les deux → s'arrête et fixe la cible.
 * Prioritaire (0) sur l'attaque de mêlée : tant qu'il a une cible, il ne frappe plus au contact.
 * Activée par CustomEntity.keepDistance > 0 (ex : Nécromancien = 10).
 */
public class KeepDistanceGoal extends Goal {

    private final PathfinderMob mob;
    private final double min, max;
    private int repath = 0;

    public KeepDistanceGoal(PathfinderMob mob, double idealDistance) {
        this.mob = mob;
        this.min = Math.max(2.0, idealDistance - 3.0);
        this.max = idealDistance + 3.0;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity t = mob.getTarget();
        // Avec un arc / une arbalète, l'IA de tir vanilla gère déjà la distance (et tire) : on la laisse faire
        if (mob.getMainHandItem().getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem) return false;
        return t != null && t.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        repath = 0;
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity t = mob.getTarget();
        if (t == null) return;
        mob.getLookControl().setLookAt(t, 30f, 30f);
        if (--repath > 0) return;
        repath = 10;
        double d = mob.distanceTo(t);
        if (d < min) {
            Vec3 away = DefaultRandomPos.getPosAway(mob, 10, 7, t.position());
            if (away != null) mob.getNavigation().moveTo(away.x, away.y, away.z, 1.25);
        } else if (d > max) {
            mob.getNavigation().moveTo(t, 1.0);
        } else {
            mob.getNavigation().stop();
        }
    }

    /** Ajoute le comportement à un mob (goalSelector est protégé : accès par réflexion, noms SRG). */
    public static void apply(Mob mob, double idealDistance) {
        if (!(mob instanceof PathfinderMob pm) || idealDistance <= 0) return;
        try {
            GoalSelector goals = ObfuscationReflectionHelper.getPrivateValue(Mob.class, mob, "f_21345_");
            if (goals != null) goals.addGoal(0, new KeepDistanceGoal(pm, idealDistance));
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.warn("[KeepDistance] Impossible d'ajouter l'IA de lanceur : {}", e.getMessage());
        }
    }
}
