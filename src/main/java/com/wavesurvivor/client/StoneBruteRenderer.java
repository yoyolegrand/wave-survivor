package com.wavesurvivor.client;

import com.wavesurvivor.entity.StoneBrute;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Rendu du STONE BRUTE (modèle sur mesure, haute résolution) + calque lumineux (yeux, fissures roses, cœur). */
@OnlyIn(Dist.CLIENT)
public class StoneBruteRenderer extends MobRenderer<StoneBrute, StoneBruteModel<StoneBrute>> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("wavesurvivor", "textures/entity/corrupted/stone_brute.png");
    private static final RenderType GLOW = RenderType.eyes(new ResourceLocation("wavesurvivor", "textures/entity/corrupted/stone_brute_glow.png"));

    public StoneBruteRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new StoneBruteModel<>(ctx.bakeLayer(StoneBruteModel.MODEL_LAYER)), 1.1f);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return GLOW;
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(StoneBrute brute) {
        return TEXTURE;
    }
}
