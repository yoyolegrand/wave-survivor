package com.wavesurvivor.client;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 2.1 — BRISE-PONT (créature de la Faille) : thorax + abdomen en 3 plaques de carapace séparées par des veines cyan, tête à 2 grands yeux
 * cyan et 2 petits violets, mandibles de cristal (s'ouvrent pour ronger), 5 épines de cristal sur le dos, point faible cyan, 6 pattes.
 * Généré par script depuis docs/faille/v2_1_ilots/sources/brise_pont/brisepont_model.py (poses « walk » et « gnaw ») : ne pas retoucher
 * à la main. Texture 128x64 + calque lumineux.
 */
@OnlyIn(Dist.CLIENT)
public class BrisePontModel<T extends com.wavesurvivor.entity.BrisePont> extends HierarchicalModel<T> {

    public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(new ResourceLocation("wavesurvivor", "brise_pont"), "main");

    private final ModelPart root;
    private final ModelPart abdomen, spine0, spine1, spine2, spine3, spine4, thorax, head, mandible_l, mandible_r, leg0, shin0, claw0, leg1, shin1, claw1, leg2, shin2, claw2, leg3, shin3, claw3, leg4, shin4, claw4, leg5, shin5, claw5;
    private final ModelPart[] legs = new ModelPart[6];

    public BrisePontModel(ModelPart root) {
        this.root = root;
        this.abdomen = root.getChild("abdomen");
        this.spine0 = abdomen.getChild("spine0");
        this.spine1 = abdomen.getChild("spine1");
        this.spine2 = abdomen.getChild("spine2");
        this.spine3 = abdomen.getChild("spine3");
        this.spine4 = abdomen.getChild("spine4");
        this.thorax = root.getChild("thorax");
        this.head = thorax.getChild("head");
        this.mandible_l = head.getChild("mandible_l");
        this.mandible_r = head.getChild("mandible_r");
        this.leg0 = root.getChild("leg0");
        this.shin0 = leg0.getChild("shin0");
        this.claw0 = shin0.getChild("claw0");
        this.leg1 = root.getChild("leg1");
        this.shin1 = leg1.getChild("shin1");
        this.claw1 = shin1.getChild("claw1");
        this.leg2 = root.getChild("leg2");
        this.shin2 = leg2.getChild("shin2");
        this.claw2 = shin2.getChild("claw2");
        this.leg3 = root.getChild("leg3");
        this.shin3 = leg3.getChild("shin3");
        this.claw3 = shin3.getChild("claw3");
        this.leg4 = root.getChild("leg4");
        this.shin4 = leg4.getChild("shin4");
        this.claw4 = shin4.getChild("claw4");
        this.leg5 = root.getChild("leg5");
        this.shin5 = leg5.getChild("shin5");
        this.claw5 = shin5.getChild("claw5");
        legs[0] = leg0; legs[1] = leg1; legs[2] = leg2; legs[3] = leg3; legs[4] = leg4; legs[5] = leg5;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_abdomen = root.addOrReplaceChild("abdomen", CubeListBuilder.create().texOffs(0, 17).addBox(-8F, -8F, 0F, 16F, 8F, 5F).texOffs(50, 30).addBox(-7F, -7F, 5F, 14F, 6F, 1F).texOffs(44, 0).addBox(-8.5F, -9F, 6F, 17F, 8.5F, 5F).texOffs(50, 30).addBox(-7F, -7F, 11F, 14F, 6F, 1F).texOffs(42, 17).addBox(-7.5F, -7.5F, 12F, 15F, 7.5F, 5F).texOffs(88, 30).addBox(-1.5F, -11.5F, 6.5F, 3F, 3F, 3F), PartPose.offsetAndRotation(0F, 16F, 4F, 0.1047F, 0F, 0F));
        PartDefinition p_spine0 = p_abdomen.addOrReplaceChild("spine0", CubeListBuilder.create().texOffs(26, 30).addBox(-1F, -6F, -1F, 2F, 6F, 2F), PartPose.offsetAndRotation(-4F, -8F, 2F, 0.4363F, 0F, 0.3142F));
        PartDefinition p_spine1 = p_abdomen.addOrReplaceChild("spine1", CubeListBuilder.create().texOffs(18, 30).addBox(-1F, -7F, -1F, 2F, 7F, 2F), PartPose.offsetAndRotation(4F, -8F, 3F, 0.5236F, 0F, -0.3491F));
        PartDefinition p_spine2 = p_abdomen.addOrReplaceChild("spine2", CubeListBuilder.create().texOffs(80, 30).addBox(-1F, -5F, -1F, 2F, 5F, 2F), PartPose.offsetAndRotation(-3F, -8F, 11F, 0.6981F, 0F, 0.2094F));
        PartDefinition p_spine3 = p_abdomen.addOrReplaceChild("spine3", CubeListBuilder.create().texOffs(26, 30).addBox(-1F, -6F, -1F, 2F, 6F, 2F), PartPose.offsetAndRotation(3F, -8F, 12F, 0.7854F, 0F, -0.2443F));
        PartDefinition p_spine4 = p_abdomen.addOrReplaceChild("spine4", CubeListBuilder.create().texOffs(80, 30).addBox(-1F, -5F, -1F, 2F, 5F, 2F), PartPose.offsetAndRotation(0F, -7.5F, 15F, 1.0472F, 0F, 0F));
        PartDefinition p_thorax = root.addOrReplaceChild("thorax", CubeListBuilder.create().texOffs(0, 0).addBox(-6F, -6F, -9F, 12F, 7F, 10F).texOffs(0, 30).addBox(-6.2F, -4F, -8F, 0.5F, 3F, 8F).texOffs(0, 30).addBox(5.7F, -4F, -8F, 0.5F, 3F, 8F), PartPose.offsetAndRotation(0F, 16F, 0F, 0F, 0F, 0F));
        PartDefinition p_head = p_thorax.addOrReplaceChild("head", CubeListBuilder.create().texOffs(82, 17).addBox(-4.5F, -4F, -6F, 9F, 6F, 6F).texOffs(38, 41).addBox(-3.5F, -3.5F, -6.5F, 2F, 2F, 1F).texOffs(38, 41).addBox(1.5F, -3.5F, -6.5F, 2F, 2F, 1F).texOffs(44, 41).addBox(-4.6F, -1F, -5F, 1F, 1F, 1F).texOffs(44, 41).addBox(3.6F, -1F, -5F, 1F, 1F, 1F), PartPose.offsetAndRotation(0F, -2F, -9F, -0.1745F, 0F, 0F));
        PartDefinition p_mandible_l = p_head.addOrReplaceChild("mandible_l", CubeListBuilder.create().texOffs(34, 30).addBox(-1F, -1F, -6F, 2F, 2F, 6F).texOffs(48, 41).addBox(-1F, 0F, -7F, 2F, 1F, 1F), PartPose.offsetAndRotation(-2.5F, 1.5F, -6F, -0.0873F, -0.2094F, 0F));
        PartDefinition p_mandible_r = p_head.addOrReplaceChild("mandible_r", CubeListBuilder.create().texOffs(34, 30).addBox(-1F, -1F, -6F, 2F, 2F, 6F).texOffs(48, 41).addBox(-1F, 0F, -7F, 2F, 1F, 1F), PartPose.offsetAndRotation(2.5F, 1.5F, -6F, -0.0873F, 0.2094F, 0F));
        PartDefinition p_leg0 = root.addOrReplaceChild("leg0", CubeListBuilder.create().texOffs(100, 30).addBox(-10F, -1.5F, -1.5F, 10F, 3F, 3F), PartPose.offsetAndRotation(-6F, 14F, -7F, 0F, 0.4363F, 0.6981F));
        PartDefinition p_shin0 = p_leg0.addOrReplaceChild("shin0", CubeListBuilder.create().texOffs(0, 41).addBox(-11F, -1.5F, -1F, 11F, 2.5F, 2.5F), PartPose.offsetAndRotation(-10F, 0F, 0F, 0F, 0F, -2.0071F));
        PartDefinition p_claw0 = p_shin0.addOrReplaceChild("claw0", CubeListBuilder.create().texOffs(28, 41).addBox(-3F, -1F, -1F, 3F, 2F, 2F), PartPose.offsetAndRotation(-11F, 0F, 0F, 0F, 0F, -0.1745F));
        PartDefinition p_leg1 = root.addOrReplaceChild("leg1", CubeListBuilder.create().texOffs(100, 30).addBox(-10F, -1.5F, -1.5F, 10F, 3F, 3F), PartPose.offsetAndRotation(-6F, 14F, -2F, 0F, 0F, 0.6981F));
        PartDefinition p_shin1 = p_leg1.addOrReplaceChild("shin1", CubeListBuilder.create().texOffs(0, 41).addBox(-11F, -1.5F, -1F, 11F, 2.5F, 2.5F), PartPose.offsetAndRotation(-10F, 0F, 0F, 0F, 0F, -2.0071F));
        PartDefinition p_claw1 = p_shin1.addOrReplaceChild("claw1", CubeListBuilder.create().texOffs(28, 41).addBox(-3F, -1F, -1F, 3F, 2F, 2F), PartPose.offsetAndRotation(-11F, 0F, 0F, 0F, 0F, -0.1745F));
        PartDefinition p_leg2 = root.addOrReplaceChild("leg2", CubeListBuilder.create().texOffs(100, 30).addBox(-10F, -1.5F, -1.5F, 10F, 3F, 3F), PartPose.offsetAndRotation(-6F, 14F, 4F, 0F, -0.4363F, 0.6981F));
        PartDefinition p_shin2 = p_leg2.addOrReplaceChild("shin2", CubeListBuilder.create().texOffs(0, 41).addBox(-11F, -1.5F, -1F, 11F, 2.5F, 2.5F), PartPose.offsetAndRotation(-10F, 0F, 0F, 0F, 0F, -2.0071F));
        PartDefinition p_claw2 = p_shin2.addOrReplaceChild("claw2", CubeListBuilder.create().texOffs(28, 41).addBox(-3F, -1F, -1F, 3F, 2F, 2F), PartPose.offsetAndRotation(-11F, 0F, 0F, 0F, 0F, -0.1745F));
        PartDefinition p_leg3 = root.addOrReplaceChild("leg3", CubeListBuilder.create().texOffs(100, 30).addBox(0F, -1.5F, -1.5F, 10F, 3F, 3F), PartPose.offsetAndRotation(6F, 14F, -7F, 0F, -0.4363F, -0.6981F));
        PartDefinition p_shin3 = p_leg3.addOrReplaceChild("shin3", CubeListBuilder.create().texOffs(0, 41).addBox(0F, -1.5F, -1F, 11F, 2.5F, 2.5F), PartPose.offsetAndRotation(10F, 0F, 0F, 0F, 0F, 2.0071F));
        PartDefinition p_claw3 = p_shin3.addOrReplaceChild("claw3", CubeListBuilder.create().texOffs(28, 41).addBox(0F, -1F, -1F, 3F, 2F, 2F), PartPose.offsetAndRotation(11F, 0F, 0F, 0F, 0F, 0.1745F));
        PartDefinition p_leg4 = root.addOrReplaceChild("leg4", CubeListBuilder.create().texOffs(100, 30).addBox(0F, -1.5F, -1.5F, 10F, 3F, 3F), PartPose.offsetAndRotation(6F, 14F, -2F, 0F, 0F, -0.6981F));
        PartDefinition p_shin4 = p_leg4.addOrReplaceChild("shin4", CubeListBuilder.create().texOffs(0, 41).addBox(0F, -1.5F, -1F, 11F, 2.5F, 2.5F), PartPose.offsetAndRotation(10F, 0F, 0F, 0F, 0F, 2.0071F));
        PartDefinition p_claw4 = p_shin4.addOrReplaceChild("claw4", CubeListBuilder.create().texOffs(28, 41).addBox(0F, -1F, -1F, 3F, 2F, 2F), PartPose.offsetAndRotation(11F, 0F, 0F, 0F, 0F, 0.1745F));
        PartDefinition p_leg5 = root.addOrReplaceChild("leg5", CubeListBuilder.create().texOffs(100, 30).addBox(0F, -1.5F, -1.5F, 10F, 3F, 3F), PartPose.offsetAndRotation(6F, 14F, 4F, 0F, 0.4363F, -0.6981F));
        PartDefinition p_shin5 = p_leg5.addOrReplaceChild("shin5", CubeListBuilder.create().texOffs(0, 41).addBox(0F, -1.5F, -1F, 11F, 2.5F, 2.5F), PartPose.offsetAndRotation(10F, 0F, 0F, 0F, 0F, 2.0071F));
        PartDefinition p_claw5 = p_shin5.addOrReplaceChild("claw5", CubeListBuilder.create().texOffs(28, 41).addBox(0F, -1F, -1F, 3F, 2F, 2F), PartPose.offsetAndRotation(11F, 0F, 0F, 0F, 0F, 0.1745F));
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(T e, float limbSwing, float limbSwingAmount, float t, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        // pose « ronge » (0 → 1) : corps plaqué, tête plongée, mandibles ouvertes
        float g = e.gnawAmount(t - (int) t);
        abdomen.y = Mth.lerp(g, 16F, 18F);
        abdomen.xRot = Mth.lerp(g, 0.1047F, -0.1396F);
        thorax.y = Mth.lerp(g, 16F, 18F);
        thorax.xRot = Mth.lerp(g, 0F, -0.2443F);
        head.xRot = Mth.lerp(g, -0.1745F, -0.5236F);
        mandible_l.xRot = Mth.lerp(g, -0.0873F, -0.3491F);
        mandible_l.yRot = Mth.lerp(g, -0.2094F, -0.4363F);
        mandible_r.xRot = Mth.lerp(g, -0.0873F, -0.3491F);
        mandible_r.yRot = Mth.lerp(g, 0.2094F, 0.4363F);
        leg0.y = Mth.lerp(g, 14F, 16F);
        shin0.zRot = Mth.lerp(g, -2.0071F, -1.3963F);
        leg1.y = Mth.lerp(g, 14F, 16F);
        leg2.y = Mth.lerp(g, 14F, 16F);
        leg3.y = Mth.lerp(g, 14F, 16F);
        shin3.zRot = Mth.lerp(g, 2.0071F, 1.3963F);
        leg4.y = Mth.lerp(g, 14F, 16F);
        leg5.y = Mth.lerp(g, 14F, 16F);
        // ronge : les mandibles claquent, la tête tressaille
        if (g > 0.01f) {
            float c = Mth.sin(t * 1.4f) * 0.25f * g;
            mandible_l.yRot -= c;
            mandible_r.yRot += c;
            head.xRot += Mth.sin(t * 0.7f) * 0.06f * g;
        }
        // marche en trépied : pattes 0, 4, 2 contre 3, 1, 5 (0-2 à gauche, 3-5 à droite)
        float w = limbSwing * 0.9f, a = Math.min(1f, limbSwingAmount * 1.6f) * (1f - g);
        for (int k = 0; k < 6; k++) {
            boolean grpA = k == 0 || k == 2 || k == 4;
            float ph = grpA ? 0f : (float) Math.PI;
            float side = k < 3 ? 1f : -1f;
            legs[k].yRot += Mth.sin(w + ph) * 0.45f * a * side;
            legs[k].zRot += Math.max(0f, Mth.cos(w + ph)) * 0.35f * a * side;
        }
        head.yRot += netHeadYaw * Mth.DEG_TO_RAD * 0.4f * (1f - g);
        // morsure (attackTime fourni par le rendu)
        if (attackTime > 0f) {
            float s = Mth.sin(attackTime * (float) Math.PI);
            head.xRot += 0.4f * s;
            mandible_l.yRot -= 0.5f * s;
            mandible_r.yRot += 0.5f * s;
        }
    }
}
