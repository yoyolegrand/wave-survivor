package com.wavesurvivor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.IronGolemModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Map;

/**
 * Rendu du GOLEM DE GIVRE : modèle et animations du Golem de fer, avec sa propre texture
 * et ses propres fissures (selon les PV restants). Pas de fleur.
 */
@OnlyIn(Dist.CLIENT)
public class FrostGolemRenderer extends IronGolemRenderer {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("wavesurvivor", "textures/entity/frost_golem/frost_golem.png");

    public FrostGolemRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.layers.clear();                       // retire les fissures « fer » et la fleur du Golem de fer
        this.addLayer(new FrostCrackLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(IronGolem golem) {
        return TEXTURE;
    }

    /** Fissures de glace : apparaissent quand le golem perd des PV. */
    private static class FrostCrackLayer extends RenderLayer<IronGolem, IronGolemModel<IronGolem>> {
        private static final Map<IronGolem.Crackiness, ResourceLocation> CRACKS = Map.of(
                IronGolem.Crackiness.LOW, new ResourceLocation("wavesurvivor", "textures/entity/frost_golem/frost_golem_crackiness_low.png"),
                IronGolem.Crackiness.MEDIUM, new ResourceLocation("wavesurvivor", "textures/entity/frost_golem/frost_golem_crackiness_medium.png"),
                IronGolem.Crackiness.HIGH, new ResourceLocation("wavesurvivor", "textures/entity/frost_golem/frost_golem_crackiness_high.png"));

        FrostCrackLayer(RenderLayerParent<IronGolem, IronGolemModel<IronGolem>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, IronGolem golem,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (golem.isInvisible()) return;
            IronGolem.Crackiness c = golem.getCrackiness();
            if (c == IronGolem.Crackiness.NONE) return;
            renderColoredCutoutModel(this.getParentModel(), CRACKS.get(c), pose, buffers, light, golem, 1.0f, 1.0f, 1.0f);
        }
    }
}
