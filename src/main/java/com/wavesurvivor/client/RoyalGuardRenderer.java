package com.wavesurvivor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wavesurvivor.entity.RoyalGuard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Rendu du GARDE ROYAL MOMIFIÉ (modèle sur mesure, agrandi ×1,3) + calque lumineux (yeux, cœur, hiéroglyphes). */
@OnlyIn(Dist.CLIENT)
public class RoyalGuardRenderer extends MobRenderer<RoyalGuard, RoyalGuardModel<RoyalGuard>> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("wavesurvivor", "textures/entity/desert/royal_guard.png");
    private static final RenderType GLOW = RenderType.eyes(new ResourceLocation("wavesurvivor", "textures/entity/desert/royal_guard_glow.png"));

    public RoyalGuardRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new RoyalGuardModel<>(ctx.bakeLayer(RoyalGuardModel.LAYER)), 0.6f);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return GLOW;
            }
        });
    }

    @Override
    protected void scale(RoyalGuard guard, PoseStack pose, float partialTick) {
        pose.scale(1.3f, 1.3f, 1.3f);
    }

    @Override
    public ResourceLocation getTextureLocation(RoyalGuard guard) {
        return TEXTURE;
    }
}
