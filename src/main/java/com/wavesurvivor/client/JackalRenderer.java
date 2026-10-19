package com.wavesurvivor.client;

import com.wavesurvivor.entity.Jackal;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Rendu du CHACAL MAUDIT (modèle sur mesure) + yeux rouge braise lumineux. */
@OnlyIn(Dist.CLIENT)
public class JackalRenderer extends MobRenderer<Jackal, JackalModel<Jackal>> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("wavesurvivor", "textures/entity/desert/jackal.png");
    private static final RenderType EYES = RenderType.eyes(new ResourceLocation("wavesurvivor", "textures/entity/desert/jackal_eyes.png"));

    public JackalRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new JackalModel<>(ctx.bakeLayer(JackalModel.LAYER)), 0.5f);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return EYES;
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(Jackal jackal) {
        return TEXTURE;
    }
}
