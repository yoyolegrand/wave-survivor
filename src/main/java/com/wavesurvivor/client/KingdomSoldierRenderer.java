package com.wavesurvivor.client;

import com.wavesurvivor.entity.KingdomSoldier;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Rendu du Soldat du royaume : corps humain (modèle joueur), armure et arme visibles,
 * légèrement plus grand qu'un joueur (×1,08).
 */
@OnlyIn(Dist.CLIENT)
public class KingdomSoldierRenderer extends HumanoidMobRenderer<KingdomSoldier, HumanoidModel<KingdomSoldier>> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("minecraft", "textures/entity/player/wide/steve.png");

    public KingdomSoldierRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new KingdomSoldierModel(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f, 1.08f, 1.08f, 1.08f);
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                ctx.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(KingdomSoldier soldier) {
        return TEXTURE;
    }
}
