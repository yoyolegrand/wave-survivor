package com.wavesurvivor.client;

import com.wavesurvivor.entity.Scorpion;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Rendu du SCORPION (modèle sur mesure) + yeux rouges lumineux. */
@OnlyIn(Dist.CLIENT)
public class ScorpionRenderer extends MobRenderer<Scorpion, ScorpionModel<Scorpion>> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("wavesurvivor", "textures/entity/desert/scorpion.png");
    private static final RenderType EYES = RenderType.eyes(new ResourceLocation("wavesurvivor", "textures/entity/desert/scorpion_eyes.png"));

    public ScorpionRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new ScorpionModel<>(ctx.bakeLayer(ScorpionModel.LAYER)), 0.8f);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return EYES;
            }
        });
    }

    /** Taille réglée (ex. Sekhmet ×2) : le modèle et l'ombre suivent. */
    @Override
    protected void scale(Scorpion scorpion, com.mojang.blaze3d.vertex.PoseStack poseStack, float partialTick) {
        float s = scorpion.bodyScale();
        poseStack.scale(s, s, s);
        this.shadowRadius = 0.8f * s;
    }

    @Override
    protected float getFlipDegrees(Scorpion scorpion) {
        return 180.0f; // se retourne sur le dos en mourant, comme l'araignée
    }

    @Override
    public ResourceLocation getTextureLocation(Scorpion scorpion) {
        return TEXTURE;
    }
}
