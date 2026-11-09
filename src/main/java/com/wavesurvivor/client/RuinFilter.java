package com.wavesurvivor.client;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.AbstractGlassBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Mode « vestiges » (Faille 2.0) : rejoue le dessin normal du bâtiment en l'abîmant (murs déchiquetés, matériaux fissurés, lumières et tissus supprimés). */
final class RuinFilter {

    static boolean active;
    static float cut;
    static long seed;
    static int idx;

    private RuinFilter() {}

    /** height = hauteur du bâtiment (ARCHER 9,6, MAGE 4,3, …). */
    static void begin(BlockPos pos, float height) {
        active = true;
        seed = pos.asLong();
        idx = 0;
        cut = height * 0.4f;
    }

    static void end() { active = false; }

    /** Nombre pseudo-aléatoire stable (0..1) : mêmes ruines à chaque image. */
    static float rnd(int i) {
        long h = seed * 31L + i * 0x9E3779B97F4A7C15L;
        h ^= (h >>> 29);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 32);
        return (h & 0xFFFF) / 65535f;
    }

    /** Hauteur maximale gardée pour la pièce n° idx (murs déchiquetés). */
    static float limit() {
        idx++;
        return cut * (0.7f + 0.6f * rnd(idx));
    }

    /** Matériau abîmé ; null = pièce supprimée (lumières, verre, bannières…). */
    static BlockState ruined(BlockState st) {
        Block b = st.getBlock();
        if (b == Blocks.STONE_BRICKS || b == Blocks.MOSSY_STONE_BRICKS) return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        if (b == Blocks.DEEPSLATE_BRICKS) return Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
        if (b == Blocks.POLISHED_BLACKSTONE_BRICKS) return Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        if (b == Blocks.NETHER_BRICKS) return Blocks.CRACKED_NETHER_BRICKS.defaultBlockState();
        if (st.is(BlockTags.PLANKS)) return Blocks.DARK_OAK_PLANKS.defaultBlockState();
        if (st.is(BlockTags.LOGS)) return Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
        if (b == Blocks.STONE || b == Blocks.SMOOTH_STONE) return Blocks.COBBLESTONE.defaultBlockState();
        if (st.is(BlockTags.WOOL) || st.is(BlockTags.BANNERS) || b instanceof AbstractGlassBlock
                || b == Blocks.LANTERN || b == Blocks.SOUL_LANTERN || b == Blocks.GLOWSTONE || b == Blocks.SHROOMLIGHT) return null;
        return st;
    }
}
