package com.wavesurvivor.client;

import com.wavesurvivor.entity.Rootling;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Rendu du ROOTLING (modèle sur mesure) + calque lumineux (yeux, cœur, fissure du masque). */
@OnlyIn(Dist.CLIENT)
public class RootlingRenderer extends MobRenderer<Rootling, RootlingModel<Rootling>> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("wavesurvivor", "textures/entity/corrupted/rootling.png");
    private static final RenderType GLOW = RenderType.eyes(new ResourceLocation("wavesurvivor", "textures/entity/corrupted/rootling_glow.png"));

    public RootlingRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new RootlingModel<>(ctx.bakeLayer(RootlingModel.LAYER)), 0.35f);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return GLOW;
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(Rootling rootling) {
        return TEXTURE;
    }
}
