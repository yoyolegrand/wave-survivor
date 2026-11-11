package com.wavesurvivor.client;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** 2.0 — Rendu du DÉVOREUR (Titan de la Faille) : modèle sur mesure + calque lumineux (anneaux, yeux, cristaux, gueule). */
@OnlyIn(Dist.CLIENT)
public class DevoreurRenderer<T extends Mob> extends MobRenderer<T, DevoreurModel<T>> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("wavesurvivor", "textures/entity/rift/devoreur.png");
    private static final RenderType GLOW = RenderType.eyes(new ResourceLocation("wavesurvivor", "textures/entity/rift/devoreur_glow.png"));

    public DevoreurRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DevoreurModel<>(ctx.bakeLayer(DevoreurModel.MODEL_LAYER)), 0f);   // pas d'ombre : il flotte au-dessus du vide
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
