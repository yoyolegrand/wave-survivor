package com.wavesurvivor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wavesurvivor.entity.FailleAnimated;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Rendu du BOLIDE : deux jeux de textures sur les mêmes UV, « brûlant » (fissures roses et blanches) et « refroidi »
 * (fissures turquoise). On passe de l'un à l'autre quand {@code failleHeat()} descend sous 0,5 ; le calque lumineux
 * s'atténue avec la chaleur.
 */
@OnlyIn(Dist.CLIENT)
public class BolideRenderer<T extends Mob> extends MobRenderer<T, BolideModel<T>> {

    private static final ResourceLocation HOT = new ResourceLocation("wavesurvivor", "textures/entity/rift/bolide_hot.png");
    private static final ResourceLocation COOL = new ResourceLocation("wavesurvivor", "textures/entity/rift/bolide_cool.png");
    private static final ResourceLocation HOT_GLOW = new ResourceLocation("wavesurvivor", "textures/entity/rift/bolide_hot_glow.png");
    private static final ResourceLocation COOL_GLOW = new ResourceLocation("wavesurvivor", "textures/entity/rift/bolide_cool_glow.png");

    public BolideRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new BolideModel<>(ctx.bakeLayer(BolideModel.MODEL_LAYER)), 1.0F);
        addLayer(new RenderLayer<>(this) {
            @Override
            public void render(PoseStack ps, MultiBufferSource buf, int light, T e, float limb, float limbAmt, float partial,
                               float age, float yaw, float pitch) {
                float heat = e instanceof FailleAnimated fa ? fa.failleHeat() : 0f;
                RenderType rt = RenderType.eyes(heat >= 0.5f ? HOT_GLOW : COOL_GLOW);
                float k = 0.55f + 0.45f * heat;
                getParentModel().renderToBuffer(ps, buf.getBuffer(rt), 0xF00000, OverlayTexture.NO_OVERLAY, k, k, k, 1f);
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        float heat = entity instanceof FailleAnimated fa ? fa.failleHeat() : 0f;
        return heat >= 0.5f ? HOT : COOL;
    }
}
