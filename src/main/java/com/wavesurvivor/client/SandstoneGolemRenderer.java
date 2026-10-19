package com.wavesurvivor.client;

import com.wavesurvivor.entity.SandstoneGolem;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Rendu du COLOSSE DE GRÈS (modèle sur mesure) + calque lumineux (fissures, hiéroglyphes, yeux, cœur-soleil). */
@OnlyIn(Dist.CLIENT)
public class SandstoneGolemRenderer extends MobRenderer<SandstoneGolem, SandstoneGolemModel<SandstoneGolem>> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("wavesurvivor", "textures/entity/desert/sandstone_golem.png");
    private static final RenderType GLOW = RenderType.eyes(new ResourceLocation("wavesurvivor", "textures/entity/desert/sandstone_golem_glow.png"));

    public SandstoneGolemRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new SandstoneGolemModel<>(ctx.bakeLayer(SandstoneGolemModel.LAYER)), 1.3f);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return GLOW;
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(SandstoneGolem golem) {
        return TEXTURE;
    }
}
