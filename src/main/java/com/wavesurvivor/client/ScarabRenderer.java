package com.wavesurvivor.client;

import com.wavesurvivor.entity.Scarab;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Rendu du SCARABÉE (modèle sur mesure). */
@OnlyIn(Dist.CLIENT)
public class ScarabRenderer extends MobRenderer<Scarab, ScarabModel<Scarab>> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("wavesurvivor", "textures/entity/desert/scarab.png");

    public ScarabRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new ScarabModel<>(ctx.bakeLayer(ScarabModel.LAYER)), 0.3f);
    }

    @Override
    protected float getFlipDegrees(Scarab scarab) {
        return 180.0f; // se retourne sur le dos en mourant
    }

    @Override
    public ResourceLocation getTextureLocation(Scarab scarab) {
        return TEXTURE;
    }
}
