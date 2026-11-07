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
        List<BlockState> fruits = parts.length > 2 ? states(parts[2], null) : List.of(Blocks.BROWN_TERRACOTTA.defaultBlockState());

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

        int h = 6 + rng.nextInt(4) + (e.getSize() > 0 ? 0 : 0);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int[] lean = dirs[rng.nextInt(4)];
        int cx = 0, cz = 0;
        int k = 0;
        for (int i = 0; i < h; i++) {
            if (i >= h / 2 && i % 2 == 0) { cx += lean[0]; cz += lean[1]; }
            BlockState base = trunk.get(Math.floorMod(seed + i, trunk.size()));
            block(pose, buffers, light, blocks, i % 3 == 1 ? stripped(base) : base, cx, i, cz, 1f, windX, windZ, h, false);
        }
        int ty = h;
        BlockState leaf = leaves.get(0);
        block(pose, buffers, light, blocks, leaf, cx, ty, cz, 1f, windX, windZ, h, false);
        int[][] fronds = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
        for (int[] d : fronds) {
            for (int st = 1; st <= 4; st++) {
                int py = ty + (st == 1 ? 1 : 0) - (st >= 3 ? 1 : 0) - (st == 4 ? 1 : 0);
                block(pose, buffers, light, blocks, leaves.get(Math.floorMod(seed + k++, leaves.size())), cx + d[0] * st, py, cz + d[1] * st, 0.6f, windX, windZ, h, true);
            }
        }
        if (!fruits.isEmpty()) {
            int[][] nuts = {{1, 0}, {0, 1}, {-1, 0}};
            for (int[] d : nuts) {
                if (rng.nextFloat() < 0.6f) block(pose, buffers, light, blocks, fruits.get(Math.floorMod(seed + k++, fruits.size())),
                        cx + d[0], ty - 1, cz + d[1], 0.7f, windX, windZ, h, true);
            }
        }
    }

    /** Un bloc du palmier à la position (x, y, z) en blocs ; {@code thick} réduit la hauteur (palmes fines) ; {@code sway} : subit le vent. */
    private static void block(PoseStack pose, MultiBufferSource buffers, int light, BlockRenderDispatcher blocks, BlockState st,
                              int x, int y, int z, float thick, float windX, float windZ, int h, boolean sway) {
        float w = sway ? (float) y / h : 0f;
        pose.pushPose();
        pose.translate(x * U + windX * w, y * U, z * U + windZ * w);
        pose.scale(U, U * thick, U);
        pose.translate(-0.5, 0, -0.5);
        blocks.renderSingleBlock(st, pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
