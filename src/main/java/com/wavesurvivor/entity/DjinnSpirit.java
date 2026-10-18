package com.wavesurvivor.entity;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/** ESPRIT DU DJINN — Vex invoqué par le Djinn : même comportement, recoloré turquoise et or, avec une traînée de paillettes. */
public class DjinnSpirit extends Vex {

    private static final DustParticleOptions TRAIL = new DustParticleOptions(new Vector3f(0.35f, 0.95f, 0.9f), 0.7f);

    public DjinnSpirit(EntityType<? extends Vex> type, Level level) {
        super(type, level);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level() instanceof ServerLevel sl && tickCount % 3 == 0) {
            sl.sendParticles(TRAIL, getX(), getY() + 0.35, getZ(), 1, 0.15, 0.15, 0.15, 0.0);
        }
    }
}
