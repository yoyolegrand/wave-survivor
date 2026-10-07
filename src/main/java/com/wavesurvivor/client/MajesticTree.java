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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ARBRE FANTASTIQUE (événement du chaos « Arbre ») :
 *  - racines noueuses en deux segments, tronc torsadé qui s'affine et se cambre ;
 *  - deux étages de branches qui partent en éventail et portent chacune une touffe de feuillage ;
 *  - couronne en parasol (anneau large, cœur massif, étages resserrés, cime) ;
 *  - rideaux de feuillage qui pendent du bord, fruits lumineux qui brillent dans le noir ;
 *  - tout le houppier ondule doucement au vent.
 * Config « tronc1,tronc2|feuille1,feuille2|fruit1 » : chaque partie peut mélanger plusieurs blocs (répartition fixe).
 */
@OnlyIn(Dist.CLIENT)
public final class MajesticTree {

    private MajesticTree() {}

    private static final int TRUNK = 0, LEAF = 1, FRUIT = 2;

    /** Pièce : position, échelles X/Y/Z, angles Y/X/Z, type ; {@code sway} = part du vent subie (0 à 1). */
    private record P(float x, float y, float z, float sx, float sy, float sz, float ry, float rx, float rz, int kind, float sway) {}

    private static final List<P> BASE = new ArrayList<>();
    /** En plus pour les tailles moyenne et grande. */
    private static final List<P> EXTRA = new ArrayList<>();
    /** Hauteur totale (pour le placement des repères et la boîte de collision). */
    public static final float HEIGHT = 6.0f;

    static {
        // ── Racines noueuses : 6 racines en 2 segments, qui plongent vers l'extérieur ──
        for (int k = 0; k < 6; k++) {
            double a = Math.toRadians(k * 60 + 15);
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            BASE.add(new P(c * 0.42f, -0.05f, s * 0.42f, 0.46f, 0.55f, 0.46f, k * 23f, -s * 30f, c * 30f, TRUNK, 0f));
            BASE.add(new P(c * 0.86f, -0.18f, s * 0.86f, 0.30f, 0.45f, 0.30f, k * 31f, -s * 58f, c * 58f, TRUNK, 0f));
        }
        // ── Tronc torsadé : 7 segments qui s'affinent, légère cambrure ──
        float[] seg = {0.92f, 0.84f, 0.76f, 0.68f, 0.60f, 0.54f, 0.48f};
        float y = 0f;
        for (int i = 0; i < seg.length; i++) {
            float lean = (float) Math.sin(i * 0.55) * 0.10f;
            BASE.add(new P(lean, y, lean * 0.4f, seg[i], seg[i] * 1.05f, seg[i], i * 14f, 0f, 0f, TRUNK, i / 7f * 0.35f));
            y += seg[i] * 0.92f;
        }
        // ── Branches basses : 5 en éventail, 2 segments, touffe au bout ──
        for (int k = 0; k < 5; k++) {
            double a = Math.toRadians(k * 72);
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            BASE.add(new P(c * 0.30f, 2.55f, s * 0.30f, 0.32f, 0.80f, 0.32f, 0f, s * 58f, -c * 58f, TRUNK, 0.4f));
            BASE.add(new P(c * 0.92f, 3.00f, s * 0.92f, 0.25f, 0.70f, 0.25f, 0f, s * 74f, -c * 74f, TRUNK, 0.55f));
            BASE.add(new P(c * 1.62f, 3.05f, s * 1.62f, 0.95f, 0.80f, 0.95f, k * 17f, 0f, 0f, LEAF, 0.8f));
        }
        // ── Branches hautes : 5, décalées, plus courtes ──
        for (int k = 0; k < 5; k++) {
            double a = Math.toRadians(36 + k * 72);
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            BASE.add(new P(c * 0.26f, 3.55f, s * 0.26f, 0.27f, 0.70f, 0.27f, 0f, s * 48f, -c * 48f, TRUNK, 0.6f));
            BASE.add(new P(c * 1.05f, 4.00f, s * 1.05f, 0.90f, 0.78f, 0.90f, k * 29f, 0f, 0f, LEAF, 0.9f));
        }
        // ── Couronne en parasol ──
        ring(BASE, 3.25f, 1.30f, 10, 0.82f, 0.62f, 18f, LEAF, 0.85f);   // anneau large
        BASE.add(new P(0f, 3.70f, 0f, 1.55f, 1.20f, 1.55f, 20f, 0f, 0f, LEAF, 0.8f)); // cœur
        ring(BASE, 4.45f, 0.72f, 6, 1.00f, 0.85f, 0f, LEAF, 0.95f);
        ring(BASE, 5.05f, 0.38f, 4, 0.80f, 0.70f, 45f, LEAF, 1f);
        BASE.add(new P(0f, 5.45f, 0f, 0.62f, 0.55f, 0.62f, 45f, 0f, 0f, LEAF, 1f)); // cime
        // ── Rideaux de feuillage qui pendent du bord ──
        for (int k = 0; k < 10; k++) {
            double a = Math.toRadians(9 + k * 36);
            float len = 0.8f + (k % 3) * 0.25f;
            BASE.add(new P((float) Math.cos(a) * 1.62f, 3.05f - len, (float) Math.sin(a) * 1.62f,
                    0.20f, len, 0.20f, k * 11f, 0f, 0f, LEAF, 1f));
        }
        // ── Fruits lumineux répartis dans la couronne ──
        float[][] fruits = {{1.45f, 2.85f, 20f}, {1.20f, 3.30f, 95f}, {1.50f, 2.90f, 165f}, {1.10f, 3.70f, 230f},
                {1.40f, 2.95f, 300f}, {0.80f, 4.35f, 60f}, {0.75f, 4.40f, 200f}, {0.45f, 5.10f, 330f}};
        for (float[] f : fruits) {
            double a = Math.toRadians(f[2]);
            BASE.add(new P((float) Math.cos(a) * f[0], f[1], (float) Math.sin(a) * f[0], 0.20f, 0.20f, 0.20f, 45f, 35f, 0f, FRUIT, 0.9f));
        }
        // ── Moyen / grand : anneau extérieur, rideaux plus longs, fruits en plus ──
        ring(EXTRA, 3.55f, 1.85f, 12, 0.74f, 0.58f, 0f, LEAF, 0.95f);
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(22.5 + k * 45);
            float len = 1.1f + (k % 2) * 0.35f;
            EXTRA.add(new P((float) Math.cos(a) * 1.95f, 3.45f - len, (float) Math.sin(a) * 1.95f,
                    0.18f, len, 0.18f, k * 13f, 0f, 0f, LEAF, 1f));
        }
        for (int k = 0; k < 6; k++) {
            double a = Math.toRadians(k * 60 + 30);
            EXTRA.add(new P((float) Math.cos(a) * 1.80f, 3.25f, (float) Math.sin(a) * 1.80f, 0.20f, 0.20f, 0.20f, 45f, 35f, 0f, FRUIT, 1f));
        }
    }

    private static void ring(List<P> out, float y, float r, int n, float w, float h, float offsetDeg, int kind, float sway) {
        for (int k = 0; k < n; k++) {
            double a = Math.toRadians(offsetDeg + k * 360.0 / n);
            out.add(new P((float) Math.cos(a) * r, y, (float) Math.sin(a) * r, w, h, w, k * 27f, 0f, 0f, kind, sway));
        }
    }

    private static final Map<String, List<BlockState>> CACHE = new HashMap<>();

    private static List<BlockState> states(String csv, BlockState fallback) {
        if (csv == null || csv.isBlank()) return fallback == null ? List.of() : List.of(fallback);
        return CACHE.computeIfAbsent(csv + "#" + (fallback == null ? "" : fallback.toString()), k -> {
            List<BlockState> l = new ArrayList<>();
            for (String s : csv.split(",")) {
                try {
                    Block b = BuiltInRegistries.BLOCK.get(new ResourceLocation(s.trim()));
                    if (b != Blocks.AIR) l.add(b.defaultBlockState());
                } catch (Exception ignored) {}
            }
            if (l.isEmpty() && fallback != null) l.add(fallback);
            return l;
        });
    }

    public static void render(GisementEntity e, float pt, PoseStack pose, MultiBufferSource buffers, int light,
                              BlockRenderDispatcher blocks, float hp, float death) {
        // « tronc|feuillage|fruits » (ancien format « tronc,feuilles » accepté)
        String csv = e.getBlocksCsv();
        String[] parts = csv.split("\\|", -1);
        List<BlockState> trunk, leaves, fruits;
        if (parts.length >= 2) {
            trunk = states(parts[0], Blocks.OAK_LOG.defaultBlockState());
            leaves = states(parts[1], Blocks.OAK_LEAVES.defaultBlockState());
            fruits = parts.length >= 3 ? states(parts[2], null) : List.of(Blocks.SHROOMLIGHT.defaultBlockState());
        } else {
            String[] old = csv.split(",");
            trunk = states(old[0], Blocks.OAK_LOG.defaultBlockState());
            leaves = states(old.length > 1 ? old[1] : "", Blocks.OAK_LEAVES.defaultBlockState());
            fruits = List.of(Blocks.SHROOMLIGHT.defaultBlockState());
        }

        float s = (0.82f + 0.18f * hp) * death * e.scale();
        pose.scale(s, s, s);
        pose.mulPose(Axis.ZP.rotationDegrees((1f - hp) * 10f)); // penche à chaque coup

        float t = e.tickCount + pt;
        int seed = Math.abs(e.getUUID().hashCode());
        float phase = (seed % 628) / 100f;
        // Vent : léger balancement, plus fort quand l'arbre est touché
        float gust = 1f + (e.hurtTime > 0 ? 2.5f : 0f);
        float windX = (Mth.sin(t * 0.045f + phase) * 0.06f + Mth.sin(t * 0.13f + phase) * 0.015f) * gust;
        float windZ = (Mth.cos(t * 0.038f + phase) * 0.045f) * gust;
        // Les fruits pulsent doucement
        float pulse = 1f + Mth.sin(t * 0.12f + phase) * 0.12f;

        int i = 0;
        for (P p : BASE) part(pose, buffers, light, blocks, p, trunk, leaves, fruits, seed, i++, windX, windZ, pulse);
        if (e.getSize() > 0) for (P p : EXTRA) part(pose, buffers, light, blocks, p, trunk, leaves, fruits, seed, i++, windX, windZ, pulse);
    }

    private static void part(PoseStack pose, MultiBufferSource buffers, int light, BlockRenderDispatcher blocks, P p,
                             List<BlockState> trunk, List<BlockState> leaves, List<BlockState> fruits,
                             int seed, int i, float windX, float windZ, float pulse) {
        List<BlockState> from = p.kind() == TRUNK ? trunk : p.kind() == LEAF ? leaves : fruits;
        if (from.isEmpty()) return; // fruits désactivés
        BlockState st = from.get(Math.floorMod(seed + i * 7 + (i * i) % 5, from.size()));
        float k = p.kind() == FRUIT ? pulse : 1f;
        pose.pushPose();
        pose.translate(p.x() + windX * p.sway() * (p.y() / 4f), p.y(), p.z() + windZ * p.sway() * (p.y() / 4f));
        // Inclinaison d'abord (direction monde), puis rotation du bloc sur lui-même
        if (p.rx() != 0f) pose.mulPose(Axis.XP.rotationDegrees(p.rx()));
        if (p.rz() != 0f) pose.mulPose(Axis.ZP.rotationDegrees(p.rz()));
        if (p.ry() != 0f) pose.mulPose(Axis.YP.rotationDegrees(p.ry()));
        pose.scale(p.sx() * k, p.sy() * k, p.sz() * k);
        pose.translate(-0.5, 0, -0.5);
        blocks.renderSingleBlock(st, pose, buffers, p.kind() == FRUIT ? LightTexture.FULL_BRIGHT : light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
