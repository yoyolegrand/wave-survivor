package com.wavesurvivor.client;

import com.wavesurvivor.entity.SnowSorcerer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EvokerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Rendu du SORCIER DES NEIGES : modèle et animations de l'Évocateur, avec sa propre texture. */
@OnlyIn(Dist.CLIENT)
public class SnowSorcererRenderer extends EvokerRenderer<SnowSorcerer> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("wavesurvivor", "textures/entity/snow_sorcerer.png");

    public SnowSorcererRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public ResourceLocation getTextureLocation(SnowSorcerer sorcerer) {
        return TEXTURE;
    }
}
