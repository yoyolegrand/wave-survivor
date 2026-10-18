package com.wavesurvivor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wavesurvivor.entity.Djinn;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Rendu du DJINN : modèle sur mesure (turban, queue de fumée) + yeux lumineux. */
@OnlyIn(Dist.CLIENT)
public class DjinnRenderer extends MobRenderer<Djinn, DjinnModel<Djinn>> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("wavesurvivor", "textures/entity/djinn/djinn.png");
    private static final RenderType EYES = RenderType.eyes(new ResourceLocation("wavesurvivor", "textures/entity/djinn/djinn_eyes.png"));

    public DjinnRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DjinnModel<>(ctx.bakeLayer(DjinnModel.LAYER)), 0.45f);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return EYES;
            }
        });
    }

    @Override
    protected void scale(Djinn djinn, PoseStack pose, float partialTick) {
        pose.scale(0.9375f, 0.9375f, 0.9375f); // même échelle que les illageois
    }

    @Override
    public ResourceLocation getTextureLocation(Djinn djinn) {
        return TEXTURE;
    }
}
