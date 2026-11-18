package com.wavesurvivor.client;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Rendu du BRISE-PONT (2.1) : modèle généré + calque lumineux (veines, cristaux, yeux, point faible). */
@OnlyIn(Dist.CLIENT)
public class BrisePontRenderer extends MobRenderer<com.wavesurvivor.entity.BrisePont, BrisePontModel<com.wavesurvivor.entity.BrisePont>> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("wavesurvivor", "textures/entity/rift/brise_pont.png");
    private static final RenderType GLOW = RenderType.eyes(new ResourceLocation("wavesurvivor", "textures/entity/rift/brise_pont_glow.png"));

    public BrisePontRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new BrisePontModel<>(ctx.bakeLayer(BrisePontModel.MODEL_LAYER)), 0.8F);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return GLOW;
            }
        });
    }

    /** Les pattes du modèle s'arrêtent 2,3 px au-dessus du sol en marche (0,3 px en train de ronger) : on le pose au sol. */
    @Override
    protected void setupRotations(com.wavesurvivor.entity.BrisePont e, com.mojang.blaze3d.vertex.PoseStack ps, float age, float yaw, float pt) {
        ps.translate(0, -net.minecraft.util.Mth.lerp(e.gnawAmount(pt), 2.3f, 0.3f) / 16f, 0);
        super.setupRotations(e, ps, age, yaw, pt);
    }

    @Override
    public ResourceLocation getTextureLocation(com.wavesurvivor.entity.BrisePont entity) {
        return TEXTURE;
    }
}
