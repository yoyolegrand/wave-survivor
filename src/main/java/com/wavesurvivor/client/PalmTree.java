package com.wavesurvivor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.wavesurvivor.entity.GisementEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * PALMIER D'OASIS (1.9) : forme « Oasis » des arbres du chaos. Même algorithme que docs/arenes/oasis_des_pharaons/palm.py : tronc courbé de
 * 6 à 9 blocs (anneaux de bois écorcé tous les 3 blocs, il s'incline à partir de la moitié), couronne de 8 palmes qui montent puis retombent,
 * noix de coco sous la couronne. Blocs : ceux de l'événement (tronc, feuillage, fruits) ; par défaut bois de jungle, feuilles de jungle, terre cuite brune.
 * Rendu seulement : la taille et les PV viennent de l'entité, comme pour {@link MajesticTree}.
 */
@OnlyIn(Dist.CLIENT)
public final class PalmTree {

    private PalmTree() {}

    /** Taille d'un bloc du palmier (le palmier fait au plus ~6 blocs de haut, comme l'arbre classique). */
    private static final float U = 0.62f;

    private static List<BlockState> states(String csv, BlockState fallback) {
        List<BlockState> l = new ArrayList<>();
        if (csv != null) for (String s : csv.split(",")) {
            if (s.isBlank()) continue;
            try {
                Block b = BuiltInRegistries.BLOCK.get(new ResourceLocation(s.trim()));
                if (b != Blocks.AIR) l.add(b.defaultBlockState());
            } catch (Exception ignored) {}
        }
        if (l.isEmpty() && fallback != null) l.add(fallback);
        return l;
    }

    /** Feuilles posées « persistantes » (comme dans le plan de l'arène) : des feuilles naturelles tomberaient, les palmes étant loin du tronc. */
    private static List<BlockState> persistent(List<BlockState> l) {
        List<BlockState> out = new ArrayList<>();
        for (BlockState st : l) out.add(st.hasProperty(net.minecraft.world.level.block.LeavesBlock.PERSISTENT)
                ? st.setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true) : st);
        return out;
    }

    /** Version « écorcée » d'un tronc (jungle_log → stripped_jungle_log) ; le bloc lui-même s'il n'en a pas. */
    private static BlockState stripped(BlockState st) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(st.getBlock());
        if (id.getPath().endsWith("_log") || id.getPath().endsWith("_wood")) {
            Block b = BuiltInRegistries.BLOCK.get(new ResourceLocation(id.getNamespace(), "stripped_" + id.getPath()));
            if (b != Blocks.AIR) return b.defaultBlockState();
        }
        return st;
    }

    public static void render(GisementEntity e, float pt, PoseStack pose, MultiBufferSource buffers, int light,
                              BlockRenderDispatcher blocks, float hp, float death) {
        String[] parts = e.getBlocksCsv().split("\\|", -1);
        List<BlockState> trunk = states(parts[0], Blocks.JUNGLE_LOG.defaultBlockState());
        List<BlockState> leaves = persistent(states(parts.length > 1 ? parts[1] : "", Blocks.JUNGLE_LEAVES.defaultBlockState()));
        List<BlockState> fruits = parts.length > 2 ? states(parts[2], null) : List.of(Blocks.COCOA.defaultBlockState());

        float s = (0.82f + 0.18f * hp) * death * e.scale();
        pose.scale(s, s, s);
        pose.mulPose(Axis.ZP.rotationDegrees((1f - hp) * 10f)); // penche à chaque coup

        float t = e.tickCount + pt;
        int seed = Math.abs(e.getUUID().hashCode());
        Random rng = new Random(seed);
        float phase = (seed % 628) / 100f;
        float gust = 1f + (e.hurtTime > 0 ? 2.5f : 0f);
        float windX = (Mth.sin(t * 0.05f + phase) * 0.07f + Mth.sin(t * 0.14f + phase) * 0.02f) * gust;
        float windZ = Mth.cos(t * 0.042f + phase) * 0.05f * gust;

        // ── Palmier v2 : même algorithme que docs/arenes/oasis_des_pharaons/palm.py ──
        // Toutes les positions sont RELATIVES à la base du tronc ; la couronne se calcule depuis le haut du tronc courbé (cx, ty, cz),
        // jamais depuis une hauteur absolue. Les feuilles ne sont posées que sur une case vide (jamais sur le tronc).
        java.util.Map<P, BlockState> grid = new java.util.LinkedHashMap<>();
        int h = 7 + rng.nextInt(4);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int[] lean = dirs[rng.nextInt(4)];
        int cx = 0, cz = 0;
        for (int i = 0; i < h; i++) {
            if (i >= h / 2 && (i - h / 2) % 2 == 0 && i > 0) { cx += lean[0]; cz += lean[1]; }
            BlockState base = trunk.get(Math.floorMod(seed + i, trunk.size()));
            put(grid, cx, i, cz, i % 3 == 2 ? stripped(base) : base, true);
        }
        int ty = h; // niveau de la couronne
        int[] k = {0};
        // cœur de la couronne : 3 x 3 + sommet
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) put(grid, cx + dx, ty, cz + dz, leaf(leaves, seed, k), false);
        put(grid, cx, ty + 1, cz, leaf(leaves, seed, k), false);
        // 4 palmes droites (longueur 4), qui retombent ; « marche » quand elles descendent (contact par une face)
        int[] profS = {0, 0, 0, 0, -1};
        for (int[] d : dirs) {
            for (int st = 2; st <= 4; st++) {
                int px = cx + d[0] * st, pz = cz + d[1] * st, py = ty + profS[st];
                put(grid, px, py, pz, leaf(leaves, seed, k), false);
                if (profS[st] < profS[st - 1]) put(grid, px, py + 1, pz, leaf(leaves, seed, k), false);
            }
            put(grid, cx + d[0] * 4, ty - 2, cz + d[1] * 4, leaf(leaves, seed, k), false); // pointe qui pend
        }
        // 4 palmes diagonales (longueur 3) avec bloc de liaison (sinon contact par le coin)
        int[] profD = {0, 0, 0, -1};
        int[][] diags = {{1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
        for (int[] d : diags) {
            for (int st = 2; st <= 3; st++) {
                int px = cx + d[0] * st, pz = cz + d[1] * st, py = ty + profD[st];
                put(grid, px, py, pz, leaf(leaves, seed, k), false);
                put(grid, px - d[0], py, pz, leaf(leaves, seed, k), false);
                if (profD[st] < profD[st - 1]) put(grid, px, py + 1, pz, leaf(leaves, seed, k), false);
            }
            put(grid, cx + d[0] * 3, ty - 2, cz + d[1] * 3, leaf(leaves, seed, k), false);
        }
        // noix de coco : cabosses de cacao mûres collées au dernier bloc du tronc, facing = direction du tronc vu depuis la cabosse
        Object[][] nuts = {{0, 1, net.minecraft.core.Direction.NORTH}, {1, 0, net.minecraft.core.Direction.WEST},
                {0, -1, net.minecraft.core.Direction.SOUTH}, {-1, 0, net.minecraft.core.Direction.EAST}};
        for (Object[] n : nuts) {
            if (rng.nextFloat() >= 0.7f || fruits.isEmpty()) continue;
            BlockState f = fruits.get(Math.floorMod(seed + k[0]++, fruits.size()));
            if (f.is(Blocks.COCOA)) f = f.setValue(net.minecraft.world.level.block.CocoaBlock.AGE, 2)
                    .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, (net.minecraft.core.Direction) n[2]);
            put(grid, cx + (Integer) n[0], ty - 1, cz + (Integer) n[1], f, false);
        }
        for (java.util.Map.Entry<P, BlockState> en : grid.entrySet()) {
            P q = en.getKey();
            block(pose, buffers, light, blocks, en.getValue(), q.x(), q.y(), q.z(), windX, windZ, h);
        }
    }

    private record P(int x, int y, int z) {}

    /** Pose un bloc dans la grille ; {@code force} : remplace ce qui s'y trouve (tronc), sinon seulement si la case est vide. */
    private static void put(java.util.Map<P, BlockState> grid, int x, int y, int z, BlockState st, boolean force) {
        P key = new P(x, y, z);
        if (force || !grid.containsKey(key)) grid.put(key, st);
    }

    private static BlockState leaf(List<BlockState> leaves, int seed, int[] k) { return leaves.get(Math.floorMod(seed + k[0]++, leaves.size())); }

    /** Un bloc du palmier à la position (x, y, z) en blocs ; le vent déplace un peu plus le haut que le pied (même poids pour tout le palmier : aucun bloc ne se détache). */
    private static void block(PoseStack pose, MultiBufferSource buffers, int light, BlockRenderDispatcher blocks, BlockState st,
                              int x, int y, int z, float windX, float windZ, int h) {
        float w = Math.max(0f, Math.min(1f, (float) y / (h + 2)));
        pose.pushPose();
        pose.translate(x * U + windX * w, y * U, z * U + windZ * w);
        pose.scale(U, U, U);
        pose.translate(-0.5, 0, -0.5);
        blocks.renderSingleBlock(st, pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
