package com.wavesurvivor.client;

import com.wavesurvivor.entity.Neantide;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Rendu du NÉANTIDE (modèle sur mesure, haute résolution) + calque lumineux (yeux, lames, membrane, fissures). */
@OnlyIn(Dist.CLIENT)
public class NeantideRenderer extends MobRenderer<Neantide, NeantideModel<Neantide>> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("wavesurvivor", "textures/entity/void/neantide.png");
    private static final RenderType GLOW = RenderType.eyes(new ResourceLocation("wavesurvivor", "textures/entity/void/neantide_glow.png"));

    public NeantideRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new NeantideModel<>(ctx.bakeLayer(NeantideModel.MODEL_LAYER)), 0.5f);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return GLOW;
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(Neantide neantide) {
        return TEXTURE;
    }
}
