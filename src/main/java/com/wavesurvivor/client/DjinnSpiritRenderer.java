package com.wavesurvivor.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.VexRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Vex;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Rendu des ESPRITS DU DJINN : modèle du Vex, texture turquoise et or (et sa variante « en charge »). */
@OnlyIn(Dist.CLIENT)
public class DjinnSpiritRenderer extends VexRenderer {

    private static final ResourceLocation NORMAL = new ResourceLocation("wavesurvivor", "textures/entity/djinn/djinn_spirit.png");
    private static final ResourceLocation CHARGING = new ResourceLocation("wavesurvivor", "textures/entity/djinn/djinn_spirit_charging.png");

    public DjinnSpiritRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public ResourceLocation getTextureLocation(Vex vex) {
        return vex.isCharging() ? CHARGING : NORMAL;
    }
}
