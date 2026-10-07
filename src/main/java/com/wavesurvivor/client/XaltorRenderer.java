package com.wavesurvivor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wavesurvivor.entity.XaltorEntity;
import net.minecraft.client.renderer.entity.EndermanRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Rendu de Xâl'Tor : l'enderman vanilla (yeux violets lumineux compris), agrandi ×1,6. */
@OnlyIn(Dist.CLIENT)
public class XaltorRenderer extends EndermanRenderer {

    public XaltorRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.5f * XaltorEntity.SCALE;
    }

    @Override
    protected void scale(EnderMan e, PoseStack pose, float partialTick) {
        pose.scale(XaltorEntity.SCALE, XaltorEntity.SCALE, XaltorEntity.SCALE);
    }
}
