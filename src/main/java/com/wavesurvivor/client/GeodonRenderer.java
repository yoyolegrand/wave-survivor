package com.wavesurvivor.client;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Rendu du GÉODON (monstre de la Faille) : modèle sur mesure + calque lumineux. */
@OnlyIn(Dist.CLIENT)
public class GeodonRenderer<T extends Mob> extends MobRenderer<T, GeodonModel<T>> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("wavesurvivor", "textures/entity/rift/geodon.png");
    private static final RenderType GLOW = RenderType.eyes(new ResourceLocation("wavesurvivor", "textures/entity/rift/geodon_glow.png"));

    public GeodonRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new GeodonModel<>(ctx.bakeLayer(GeodonModel.MODEL_LAYER)), 1.0F);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return GLOW;
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return TEXTURE;
    }
}
