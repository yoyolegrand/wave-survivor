package com.wavesurvivor.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RavagerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Rendu du RAVAGEUR DE GIVRE (Aurvang) : modèle et animations du Ravageur, avec sa texture glacée. */
@OnlyIn(Dist.CLIENT)
public class FrostRavagerRenderer extends RavagerRenderer {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("wavesurvivor", "textures/entity/frost_ravager.png");

    public FrostRavagerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public ResourceLocation getTextureLocation(Ravager ravager) {
        return TEXTURE;
    }
}
