package com.wavesurvivor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.wavesurvivor.entity.DjinnFangs;
import net.minecraft.client.model.EvokerFangsModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Rendu des CROCS DU DJINN : même modèle et même animation que les crocs vanilla, avec la texture turquoise et or.
 * (Le rendu vanilla des crocs utilise sa texture écrite en dur : il faut donc refaire le dessin ici.)
 */
@OnlyIn(Dist.CLIENT)
public class DjinnFangsRenderer extends EntityRenderer<DjinnFangs> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("wavesurvivor", "textures/entity/djinn/djinn_fangs.png");
    private final EvokerFangsModel<DjinnFangs> model;

    public DjinnFangsRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.model = new EvokerFangsModel<>(ctx.bakeLayer(ModelLayers.EVOKER_FANGS));
    }

    @Override
    public void render(DjinnFangs fangs, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float progress = fangs.getAnimationProgress(partialTick);
        if (progress != 0.0f) {
            float size = 2.0f;
            if (progress > 0.9f) size *= (1.0f - progress) / 0.1f;
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(90.0f - fangs.getYRot()));
            pose.scale(-size, -size, size);
            pose.translate(0.0, -0.626, 0.0);
            pose.scale(0.5f, 0.5f, 0.5f);
            model.setupAnim(fangs, progress, 0.0f, 0.0f, fangs.getYRot(), fangs.getXRot());
            VertexConsumer vc = buffers.getBuffer(model.renderType(TEXTURE));
            model.renderToBuffer(pose, vc, light, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
            pose.popPose();
        }
        super.render(fangs, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(DjinnFangs fangs) {
        return TEXTURE;
    }
}
