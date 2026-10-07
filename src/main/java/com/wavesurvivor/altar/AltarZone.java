package com.wavesurvivor.altar;

import com.wavesurvivor.horde.skill.DelayedActionScheduler;
import com.wavesurvivor.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Transforme le sol de la zone (claim) d'un autel en onde progressive :
 * anneau par anneau depuis l'autel, chaque colonne du disque voit son bloc de surface
 * remplacé par un bloc tiré au sort dans le sol de la horde.
 *
 * Sont ignorés : air/liquides, blocs avec BlockEntity (coffres...), blocs incassables, l'autel lui-même.
 * La transformation est PERMANENTE (pas de restauration au délien / à la casse).
 */
public class AltarZone {

    /** Ticks entre deux anneaux (2 = rayon 8 transformé en ~0.8s). */
    private static final int TICKS_PER_RING = 2;
    /** Fenêtre verticale de recherche de la surface, relative à l'autel. */
    private static final int SCAN_UP = 2;
    private static final int SCAN_DOWN = 4;

    public static void transform(ServerLevel level, BlockPos center, AltarRecipes.Zone zone) {
        if (zone == null || !zone.isActive()) return;
        MinecraftServer server = level.getServer();
        int radius = Math.min(zone.radius, 48);
        // Le dessin (carré) peut dépasser le cercle du rayon : on couvre les deux
        int reach = Math.max(radius, Math.min(zone.patternHalf(), 48));
        int maxRing = (int) Math.ceil(reach * 1.4143) + 1;

        // Colonnes regroupées par anneau (distance arrondie au centre)
        List<List<int[]>> rings = new ArrayList<>();
        for (int r = 0; r <= maxRing; r++) rings.add(new ArrayList<>());
        double max = (radius + 0.5) * (radius + 0.5);
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                double d2 = dx * dx + dz * dz;
                if (d2 > max && zone.patternAt(dx, dz) == null) continue;
                int ring = Math.min(maxRing, (int) Math.round(Math.sqrt(d2)));
                rings.get(ring).add(new int[]{dx, dz});
            }
        }
        radius = maxRing;

        for (int r = 0; r <= radius; r++) {
            final List<int[]> cols = rings.get(r);
            final int ringIndex = r;
            DelayedActionScheduler.schedule(server, r * TICKS_PER_RING, () -> {
                int changed = 0;
                for (int[] c : cols) {
                    if (convertColumn(level, center.offset(c[0], 0, c[1]), center, zone)) changed++;
                }
                if (changed > 0 && ringIndex % 2 == 0) {
                    float pitch = 0.6f + ringIndex * 0.05f;
                    level.playSound(null, center, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.BLOCKS, 0.8f, pitch);
                }
            }, "altar zone ring " + r);
        }
        // Fin de l'onde
        DelayedActionScheduler.schedule(server, (radius + 1) * TICKS_PER_RING, () ->
                level.playSound(null, center, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0f, 0.6f),
                "altar zone end");
    }

    /** Sable / sable rouge / gravier → version "sans gravité" (même rendu, ne tombe jamais). */
    private static BlockState stabilize(BlockState s) {
        if (s == null) return null;
        if (s.is(Blocks.SAND)) return ModBlocks.STABLE_SAND.get().defaultBlockState();
        if (s.is(Blocks.RED_SAND)) return ModBlocks.STABLE_RED_SAND.get().defaultBlockState();
        if (s.is(Blocks.GRAVEL)) return ModBlocks.STABLE_GRAVEL.get().defaultBlockState();
        return s;
    }

    /** Remplace le bloc de surface d'une colonne. @return true si modifié. */
    private static boolean convertColumn(ServerLevel level, BlockPos column, BlockPos altar, AltarRecipes.Zone zone) {
        for (int dy = SCAN_UP; dy >= -SCAN_DOWN; dy--) {
            BlockPos p = new BlockPos(column.getX(), altar.getY() + dy, column.getZ());
            BlockState st = level.getBlockState(p);
            if (st.isAir() || !st.getFluidState().isEmpty()) continue;
            if (st.is(ModBlocks.ALTAR_RUNIC.get())) continue;          // l'autel : on regarde en dessous
            if (st.canBeReplaced()) continue;                           // herbes, fleurs, neige fine...
            if (!st.isCollisionShapeFullBlock(level, p)) return false;  // dalle, clôture... : on ne touche pas
            if (st.hasBlockEntity() || st.getDestroySpeed(level, p) < 0) return false;

            BlockState target = stabilize(zone.targetAt(column.getX() - altar.getX(), column.getZ() - altar.getZ(), level.getRandom()));
            if (target == null || st.is(target.getBlock())) return false;

            // Nettoie la végétation au-dessus (sinon elle casse et drop sur le nouveau sol)
            BlockPos above = p.above();
            BlockState aboveSt = level.getBlockState(above);
            if (!aboveSt.isAir() && aboveSt.canBeReplaced() && aboveSt.getFluidState().isEmpty()
                    && !aboveSt.is(ModBlocks.ALTAR_RUNIC.get())) {
                level.setBlock(above, Blocks.AIR.defaultBlockState(), 3);
            }

            level.setBlock(p, target, 3);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, target),
                    p.getX() + 0.5, p.getY() + 1.05, p.getZ() + 0.5, 4, 0.3, 0.05, 0.3, 0.05);
            return true;
        }
        return false;
    }
}
