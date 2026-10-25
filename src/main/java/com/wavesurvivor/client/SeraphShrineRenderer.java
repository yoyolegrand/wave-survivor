package com.wavesurvivor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.wavesurvivor.shrine.SeraphShrineBlock;
import com.wavesurvivor.shrine.SeraphShrineBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Rendu du SANCTUAIRE DU SÉRAPHIN : modèle 3D haute résolution posé sur le bloc, texture selon l'étape de corruption
 * (0 à 4), orienté comme le bloc, animé (halo, ailes, orbe), + calque lumineux (halo, orbe, yeux, fissures).
 */
@OnlyIn(Dist.CLIENT)
public class SeraphShrineRenderer implements BlockEntityRenderer<SeraphShrineBlockEntity> {

    private static final ResourceLocation[] TEX = new ResourceLocation[5];
    private static final ResourceLocation[] GLOW = new ResourceLocation[5];
    static {
        for (int i = 0; i < 5; i++) {
            TEX[i] = new ResourceLocation("wavesurvivor", "textures/block/seraph_shrine/stage" + i + ".png");
            GLOW[i] = new ResourceLocation("wavesurvivor", "textures/block/seraph_shrine/stage" + i + "_glow.png");
        }
    }

    private final SeraphShrineModel model;

    public SeraphShrineRenderer(BlockEntityRendererProvider.Context ctx) {
        this.model = new SeraphShrineModel(ctx.bakeLayer(SeraphShrineModel.MODEL_LAYER));
    }

    @Override
    public void render(SeraphShrineBlockEntity shrine, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        int stage = shrine.getStage();
        float time = (shrine.getLevel() != null ? shrine.getLevel().getGameTime() : 0) + partialTick;
        model.setupAnim(time, stage);

        pose.pushPose();
        pose.translate(0.5, 1.5, 0.5);
        float yaw = shrine.getBlockState().getValue(SeraphShrineBlock.FACING).toYRot();
        pose.mulPose(Axis.YP.rotationDegrees(180.0f - yaw));   // même formule que le rendu des entités : la statue regarde vers FACING
        pose.scale(-1.0f, -1.0f, 1.0f);   // repère des modèles d'entité (y vers le bas)

        model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEX[stage])), light, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(GLOW[stage])), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(SeraphShrineBlockEntity shrine) {
        return true;   // reste visible même si le bloc lui-même sort de l'écran (ailes très larges)
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
