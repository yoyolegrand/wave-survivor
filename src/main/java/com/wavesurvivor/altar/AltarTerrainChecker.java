package com.wavesurvivor.altar;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Vérifie qu'un terrain est propice au spawn d'une horde autour d'un altar.
 *
 * Critères :
 *   - Rayon d'inspection = spawnRadius de la horde
 *   - Headroom minimum : min 6 blocks vides au-dessus de l'altar (verticalement)
 *   - Blocs solides au sol dans le rayon : min 15 (sinon pas de sol pour spawner les mobs)
 *   - Blocs liquides : max 30% de la zone au sol (sinon trop de lave/eau)
 */
public class AltarTerrainChecker {

    /** Résultat détaillé de la validation avec message d'erreur si KO. */
    public static class Result {
        public final boolean valid;
        public final String errorMessage;
        public final int solidBlocks;
        public final int liquidBlocks;
        public final int headroom;

        public Result(boolean valid, String err, int solid, int liquid, int headroom) {
            this.valid = valid;
            this.errorMessage = err;
            this.solidBlocks = solid;
            this.liquidBlocks = liquid;
            this.headroom = headroom;
        }
    }

    private static final int MIN_HEADROOM = 6;
    private static final int MIN_SOLID_BLOCKS = 15;
    private static final double MAX_LIQUID_RATIO = 0.30;

    public static Result check(ServerLevel level, BlockPos altarPos, int spawnRadius) {
        int radius = Math.max(4, spawnRadius);

        // 1. Check headroom vertical (blocs vides au-dessus de l'altar)
        int headroom = 0;
        for (int dy = 1; dy <= MIN_HEADROOM + 2; dy++) {
            BlockPos p = altarPos.above(dy);
            BlockState state = level.getBlockState(p);
            if (state.isAir() || !state.getFluidState().isEmpty() && state.getFluidState().isEmpty()) {
                headroom++;
            } else if (!state.isSolid() && !state.blocksMotion()) {
                headroom++;
            } else {
                break; // premier block solide → stop
            }
        }
        if (headroom < MIN_HEADROOM) {
            return new Result(false,
                    "§cPas assez d'espace au-dessus de l'autel (§f" + headroom + "§c/§f" + MIN_HEADROOM + " blocs libres§c)",
                    0, 0, headroom);
        }

        // 2. Compte les blocks solides au sol + liquides dans un cylindre autour de l'altar
        int solid = 0;
        int liquid = 0;
        int totalGroundChecks = 0;

        int cx = altarPos.getX();
        int cy = altarPos.getY();
        int cz = altarPos.getZ();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                // Cercle (pas carré)
                if (dx * dx + dz * dz > radius * radius) continue;

                // Cherche le sol dans une plage verticale (±4 blocks autour du niveau altar)
                boolean foundSolid = false;
                for (int dy = -4; dy <= 4; dy++) {
                    BlockPos p = new BlockPos(cx + dx, cy + dy, cz + dz);
                    BlockState state = level.getBlockState(p);
                    if (!state.getFluidState().isEmpty()) {
                        liquid++;
                        break;
                    }
                    if (state.isSolid() && state.blocksMotion()) {
                        // Vérifie qu'il y a du vide au-dessus (spawn possible)
                        BlockState above = level.getBlockState(p.above());
                        if (above.isAir() || !above.blocksMotion()) {
                            solid++;
                            foundSolid = true;
                            break;
                        }
                    }
                }
                totalGroundChecks++;
            }
        }

        double liquidRatio = totalGroundChecks > 0 ? (double) liquid / totalGroundChecks : 0;

        if (solid < MIN_SOLID_BLOCKS) {
            return new Result(false,
                    "§cPas assez de sol solide autour de l'autel (§f" + solid + "§c/§f" + MIN_SOLID_BLOCKS + "§c)",
                    solid, liquid, headroom);
        }
        if (liquidRatio > MAX_LIQUID_RATIO) {
            int pct = (int) Math.round(liquidRatio * 100);
            return new Result(false,
                    "§cTrop de liquide autour de l'autel (§f" + pct + "%§c) — max §f" + (int)(MAX_LIQUID_RATIO * 100) + "%",
                    solid, liquid, headroom);
        }

        return new Result(true, null, solid, liquid, headroom);
    }
}
