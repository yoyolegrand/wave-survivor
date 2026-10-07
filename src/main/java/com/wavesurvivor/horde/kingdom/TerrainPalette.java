package com.wavesurvivor.horde.kingdom;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.Map;

/**
 * Thème « Terrain » des Portes : analyse du sol autour d'un point (13×13) et versions « travaillées » des matériaux
 * (pierre → briques de pierre, grès → grès taillé, terre → briques de boue…). Utilisé côté serveur (Porte en vrais blocs)
 * ET côté client (Porte dessinée, mode « Visuel seul »). Retourne null si le terrain n'est pas encore chargé.
 */
public final class TerrainPalette {

    private TerrainPalette() {}

    /** Variantes d'un matériau : taillé, fissuré, sculpté, poli. */
    public record Dressed(BlockState cut, BlockState cracked, BlockState chiseled, BlockState polished) {}

    /** Matériau de surface (plateforme) et de profondeur (piliers, arche). */
    public record Sample(Dressed top, Dressed low) {}

    /**
     * @param hm type de carte de hauteur : MOTION_BLOCKING_NO_LEAVES côté serveur, MOTION_BLOCKING côté client
     *           (le client ne reçoit pas l'autre) ; les feuillages sont ignorés dans tous les cas.
     */
    public static Sample sample(LevelReader level, BlockPos origin, Heightmap.Types hm) {
        Map<BlockState, Integer> surf = new HashMap<>(), deep = new HashMap<>();
        for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
            int x = origin.getX() + dx, z = origin.getZ() + dz;
            int top = level.getHeight(hm, x, z) - 1;
            int d = 0;
            for (int y = top; y >= top - 10 && d <= 5; y--) {
                BlockPos p = new BlockPos(x, y, z);
                BlockState s = level.getBlockState(p);
                if (s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS)) continue;   // arbres : on descend jusqu'au sol
                if (s.isAir() || s.hasBlockEntity() || !s.getFluidState().isEmpty() || !s.isCollisionShapeFullBlock(level, p)) { d++; continue; }
                if (d <= 1) surf.merge(s.getBlock().defaultBlockState(), d == 0 ? 2 : 1, Integer::sum);
                else deep.merge(s.getBlock().defaultBlockState(), 1, Integer::sum);
                d++;
            }
        }
        if (surf.isEmpty() && deep.isEmpty()) return null;   // terrain pas encore chargé
        BlockState surface = mostCommon(surf, Blocks.STONE.defaultBlockState());
        return new Sample(dressed(surface), dressed(mostCommon(deep, surface)));
    }

    private static BlockState mostCommon(Map<BlockState, Integer> counts, BlockState fallback) {
        BlockState best = fallback;
        int n = 0;
        for (var e : counts.entrySet()) if (e.getValue() > n) { n = e.getValue(); best = e.getKey(); }
        return best;
    }

    public static Dressed dressed(BlockState raw) {
        Block b = raw.getBlock();
        if (b == Blocks.STONE || b == Blocks.COBBLESTONE || b == Blocks.SMOOTH_STONE || b == Blocks.GRAVEL)
            return new Dressed(Blocks.STONE_BRICKS.defaultBlockState(), Blocks.CRACKED_STONE_BRICKS.defaultBlockState(),
                    Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), Blocks.SMOOTH_STONE.defaultBlockState());
        if (b == Blocks.MOSSY_COBBLESTONE || b == Blocks.MOSS_BLOCK)
            return new Dressed(Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), Blocks.MOSSY_COBBLESTONE.defaultBlockState(),
                    Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState());
        if (b == Blocks.DEEPSLATE || b == Blocks.COBBLED_DEEPSLATE || b == Blocks.TUFF)
            return new Dressed(Blocks.DEEPSLATE_BRICKS.defaultBlockState(), Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState(),
                    Blocks.CHISELED_DEEPSLATE.defaultBlockState(), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        if (b == Blocks.SAND || b == Blocks.SANDSTONE)
            return new Dressed(Blocks.CUT_SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(),
                    Blocks.CHISELED_SANDSTONE.defaultBlockState(), Blocks.SMOOTH_SANDSTONE.defaultBlockState());
        if (b == Blocks.RED_SAND || b == Blocks.RED_SANDSTONE)
            return new Dressed(Blocks.CUT_RED_SANDSTONE.defaultBlockState(), Blocks.RED_SANDSTONE.defaultBlockState(),
                    Blocks.CHISELED_RED_SANDSTONE.defaultBlockState(), Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState());
        if (b == Blocks.GRANITE) return new Dressed(Blocks.POLISHED_GRANITE.defaultBlockState(), raw, Blocks.POLISHED_GRANITE.defaultBlockState(), Blocks.POLISHED_GRANITE.defaultBlockState());
        if (b == Blocks.DIORITE) return new Dressed(Blocks.POLISHED_DIORITE.defaultBlockState(), raw, Blocks.POLISHED_DIORITE.defaultBlockState(), Blocks.POLISHED_DIORITE.defaultBlockState());
        if (b == Blocks.ANDESITE) return new Dressed(Blocks.POLISHED_ANDESITE.defaultBlockState(), raw, Blocks.POLISHED_ANDESITE.defaultBlockState(), Blocks.POLISHED_ANDESITE.defaultBlockState());
        if (b == Blocks.DIRT || b == Blocks.GRASS_BLOCK || b == Blocks.COARSE_DIRT || b == Blocks.PODZOL || b == Blocks.ROOTED_DIRT
                || b == Blocks.MUD || b == Blocks.MYCELIUM || b == Blocks.CLAY || b == Blocks.DIRT_PATH)
            return new Dressed(Blocks.MUD_BRICKS.defaultBlockState(), Blocks.PACKED_MUD.defaultBlockState(),
                    Blocks.MUD_BRICKS.defaultBlockState(), Blocks.PACKED_MUD.defaultBlockState());
        if (b == Blocks.SNOW_BLOCK || b == Blocks.POWDER_SNOW || b == Blocks.ICE || b == Blocks.PACKED_ICE)
            return new Dressed(Blocks.PACKED_ICE.defaultBlockState(), Blocks.SNOW_BLOCK.defaultBlockState(),
                    Blocks.BLUE_ICE.defaultBlockState(), Blocks.PACKED_ICE.defaultBlockState());
        if (b == Blocks.NETHERRACK)
            return new Dressed(Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.CRACKED_NETHER_BRICKS.defaultBlockState(),
                    Blocks.CHISELED_NETHER_BRICKS.defaultBlockState(), Blocks.RED_NETHER_BRICKS.defaultBlockState());
        if (b == Blocks.BLACKSTONE || b == Blocks.BASALT)
            return new Dressed(Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
                    Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        if (b == Blocks.END_STONE)
            return new Dressed(Blocks.END_STONE_BRICKS.defaultBlockState(), Blocks.END_STONE.defaultBlockState(),
                    Blocks.PURPUR_PILLAR.defaultBlockState(), Blocks.PURPUR_BLOCK.defaultBlockState());
        return new Dressed(raw, raw, raw, raw); // matériau inconnu : utilisé tel quel
    }
}
