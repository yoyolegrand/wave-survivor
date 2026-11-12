package com.wavesurvivor.client.armor;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;

/**
 * Armure du SÉRAPHIN DÉCHU — même modèle et même couche que l'armure du Séraphin (SeraphArmorModel.LAYER),
 * avec une autre texture et quelques pièces déplacées : auréole brisée (3 morceaux manquants, 3 fêlés et décalés)
 * et penchée, ailes du heaume et du dos retombées. Conçu par Claude chat, ne pas retoucher à la main.
 * Texture : textures/models/armor/fallen_seraph_armor.png (512 x 128, 2 px/unité ; UV 256 x 64 comme le Séraphin)
 * + calque lumineux facultatif fallen_seraph_armor_glow.png (fissures, yeux, cœur, auréole).
 */
public class FallenSeraphArmorModel extends SeraphArmorModel {
    public static final ResourceLocation TEXTURE = new ResourceLocation("wavesurvivor", "textures/models/armor/fallen_seraph_armor.png");
    public static final ResourceLocation GLOW = new ResourceLocation("wavesurvivor", "textures/models/armor/fallen_seraph_armor_glow.png");

    public FallenSeraphArmorModel(ModelPart root) {
        super(root);
        ModelPart headArmor = this.head.getChild("armor");
        // auréole brisée et penchée
        ModelPart halo = headArmor.getChild("halo");
        halo.setPos(0.5F, -4.6F, 6.4F);
        halo.setRotation(0.3F, 0F, 0.42F);
        for (int k : new int[]{3, 4, 10}) halo.getChild("hs" + k).visible = false;   // morceaux manquants
        nudge(halo.getChild("hs2"), 0.6F, 0.9F, 0.5F);                              // morceaux fêlés, décalés
        nudge(halo.getChild("hs5"), -0.5F, 1.4F, -0.6F);
        nudge(halo.getChild("hs11"), 0.4F, -0.7F, 0.4F);
        // ailes retombées
        headArmor.getChild("hwing_l").setRotation(0.55F, 0F, 0.9F);
        headArmor.getChild("hwing_r").setRotation(0.55F, 0F, -0.9F);
        ModelPart bodyArmor = this.body.getChild("armor");
        bodyArmor.getChild("bwing_l").setRotation(1.0F, 0.25F, 0.95F);
        bodyArmor.getChild("bwing_r").setRotation(1.0F, -0.25F, -0.95F);
    }

    private static void nudge(ModelPart p, float dx, float dy, float dzRot) {
        p.x += dx;
        p.y += dy;
        p.zRot += dzRot;
    }
}
