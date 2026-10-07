package com.wavesurvivor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.wavesurvivor.entity.GisementEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
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
 * Rendu du GISEMENT : amas rocheux de 7 blocs de tailles et d'angles différents (blocs de la config en alternance),
 * qui rétrécit à chaque coup de pioche.
 */
@OnlyIn(Dist.CLIENT)
public class GisementRenderer extends EntityRenderer<GisementEntity> {

    /** {x, y, z, taille, angle Y, angle X} de chaque bloc de l'amas. */
    private static final float[][] PARTS = {
            {-0.32f, 0.00f, -0.20f, 0.70f, 12f, 4f},
            {0.30f, 0.00f, 0.15f, 0.64f, -20f, -6f},
            {0.00f, 0.00f, 0.36f, 0.50f, 35f, 8f},
            {-0.08f, 0.42f, -0.02f, 0.60f, -8f, 12f},
            {0.36f, 0.34f, -0.30f, 0.44f, 25f, -10f},
            {-0.40f, 0.30f, 0.30f, 0.40f, -30f, 6f},
            {0.05f, 0.86f, 0.05f, 0.34f, 45f, 15f}};

    /** Blocs en plus pour les tailles moyenne (4 premiers) et grande (tous). */
    private static final float[][] EXTRA = {
            {0.62f, 0.00f, -0.45f, 0.42f, 18f, 5f},
            {-0.66f, 0.00f, -0.40f, 0.38f, -12f, -8f},
            {-0.10f, 0.00f, -0.70f, 0.40f, 30f, 10f},
            {0.55f, 0.00f, 0.62f, 0.36f, -25f, 6f},
            {-0.62f, 0.00f, 0.66f, 0.34f, 40f, -5f},
            {0.30f, 0.62f, 0.40f, 0.32f, 10f, 20f},
            {-0.42f, 0.66f, -0.36f, 0.30f, -35f, 14f}};

    private static final Map<String, List<BlockState>> CACHE = new HashMap<>();
    private final BlockRenderDispatcher blocks;

    public GisementRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.blocks = ctx.getBlockRenderDispatcher();
        this.shadowRadius = 0.7f;
    }

    @Override
    public ResourceLocation getTextureLocation(GisementEntity e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    private static List<BlockState> states(String csv) {
        return CACHE.computeIfAbsent(csv, k -> {
            List<BlockState> l = new ArrayList<>();
            for (String s : k.split(",")) {
                try {
                    Block b = BuiltInRegistries.BLOCK.get(new ResourceLocation(s.trim()));
                    if (b != Blocks.AIR) l.add(b.defaultBlockState());
                } catch (Exception ignored) {}
            }
            if (l.isEmpty()) l.add(Blocks.IRON_ORE.defaultBlockState());
            return l;
        });
    }

    @Override
    public void render(GisementEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        float hp = Mth.clamp(e.getHealth() / e.getMaxHealth(), 0f, 1f);
        float death = e.deathTime > 0 ? Mth.clamp(1f - (e.deathTime + pt) / 12f, 0f, 1f) : 1f;
        float scale = (0.55f + 0.45f * hp) * death * e.scale();
        float shake = e.hurtTime > 0 ? Mth.sin((e.hurtTime - pt) * 2.2f) * 0.04f : 0f;
        List<BlockState> list = states(e.getBlocksCsv());

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
        pose.translate(shake, 0, -shake);
        if (e.isTree()) {
            MajesticTree.render(e, pt, pose, buffers, light, blocks, hp, death);
            pose.popPose();
            super.render(e, yaw, pt, pose, buffers, light); // nom
            return;
        }
        pose.scale(scale, scale, scale);
        int extra = e.getSize() == 0 ? 0 : e.getSize() == 1 ? 4 : EXTRA.length;
        for (int i = 0; i < PARTS.length + extra; i++) {
            float[] p = i < PARTS.length ? PARTS[i] : EXTRA[i - PARTS.length];
            pose.pushPose();
            pose.translate(p[0], p[1], p[2]);
            pose.mulPose(Axis.YP.rotationDegrees(p[4]));
            pose.mulPose(Axis.XP.rotationDegrees(p[5]));
            pose.scale(p[3], p[3], p[3]);
            pose.translate(-0.5, 0, -0.5);
            blocks.renderSingleBlock(list.get(i % list.size()), pose, buffers, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        pose.popPose();
        super.render(e, yaw, pt, pose, buffers, light); // nom
    }
}
