package com.wavesurvivor.client;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** 2.0 — Rendu de L'ARCHONTE DU NÉANT (Titan) : modèle sur mesure + calque lumineux (auréole, liserés, yeux, côtes, cornes, cristaux). */
@OnlyIn(Dist.CLIENT)
public class ArchonteRenderer<T extends Mob> extends MobRenderer<T, ArchonteModel<T>> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("wavesurvivor", "textures/entity/rift/archonte.png");
    private static final RenderType GLOW = RenderType.eyes(new ResourceLocation("wavesurvivor", "textures/entity/rift/archonte_glow.png"));

    public ArchonteRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new ArchonteModel<>(ctx.bakeLayer(ArchonteModel.MODEL_LAYER)), 0f);   // il flotte : pas d'ombre
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
