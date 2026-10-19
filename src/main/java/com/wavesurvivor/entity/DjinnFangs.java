package com.wavesurvivor.entity;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3f;

/**
 * CROCS DU DJINN — crocs de l'Évocateur invoqués par un Djinn : même comportement (dégâts, délai),
 * mâchoire turquoise et dents en or, annoncés par un petit tourbillon de sable doré pendant leur préparation.
 */
public class DjinnFangs extends EvokerFangs {

    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0f, 0.82f, 0.25f), 0.8f);
    private static final BlockParticleOption SAND = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState());

    public DjinnFangs(EntityType<? extends EvokerFangs> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        super.tick();
        // Préparation : petit tourbillon de sable et d'or à l'endroit où les crocs vont surgir
        if (level() instanceof ServerLevel sl && tickCount < 16 && tickCount % 2 == 0) {
            double a = tickCount * 0.8;
            sl.sendParticles(SAND, getX() + Math.cos(a) * 0.45, getY() + 0.1 + tickCount * 0.03, getZ() + Math.sin(a) * 0.45, 1, 0.02, 0.02, 0.02, 0.0);
            sl.sendParticles(GOLD, getX() + Math.cos(a + Math.PI) * 0.45, getY() + 0.15, getZ() + Math.sin(a + Math.PI) * 0.45, 1, 0, 0, 0, 0);
            if (tickCount == 14) sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.4, getZ(), 3, 0.2, 0.2, 0.2, 0.03);
        }
    }
}
