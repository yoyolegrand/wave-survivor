package com.wavesurvivor.client;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** 2.0 — Rendu du COLOSSE DE LA FAILLE (Titan) : modèle sur mesure + calque lumineux (veines, cœur, yeux, cristaux). */
@OnlyIn(Dist.CLIENT)
public class ColosseRenderer<T extends Mob> extends MobRenderer<T, ColosseModel<T>> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("wavesurvivor", "textures/entity/rift/colosse.png");
    private static final RenderType GLOW = RenderType.eyes(new ResourceLocation("wavesurvivor", "textures/entity/rift/colosse_glow.png"));

    public ColosseRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new ColosseModel<>(ctx.bakeLayer(ColosseModel.MODEL_LAYER)), 0f);
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
