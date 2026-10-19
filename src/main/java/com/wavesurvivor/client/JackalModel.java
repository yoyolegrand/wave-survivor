package com.wavesurvivor.client;

import com.wavesurvivor.entity.Jackal;
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
 * MODÈLE DU CHACAL MAUDIT (conçu sur mesure) : posture de chasse (tête basse, oreilles rabattues), gueule ouverte
 * (mâchoire articulée, crocs), avant massif et arrière maigre aux pattes coudées, crête hérissée, queue raide.
 * Texture 64×64 (+ yeux lumineux). Animations : galop en diagonale, mâchoire qui claque (attaque / grognement),
 * tête qui suit sa proie, oreilles qui frémissent, queue qui fouette.
 */
@OnlyIn(Dist.CLIENT)
public class JackalModel<T extends Jackal> extends HierarchicalModel<T> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation("wavesurvivor", "jackal"), "main");

    private final ModelPart root, body, head, jaw, earL, earR, legFL, legFR, legBL, legBR, tail;

    public JackalModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.jaw = head.getChild("jaw");
        this.earL = head.getChild("ear_l");
        this.earR = head.getChild("ear_r");
        this.legFL = body.getChild("leg_fl");
        this.legFR = body.getChild("leg_fr");
        this.legBL = body.getChild("leg_bl");
        this.legBR = body.getChild("leg_br");
        this.tail = body.getChild("tail1");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0, 24, 0));
        body.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(0, 0).mirror(false).addBox(-3.5f, -18, -6, 7, 8, 7), PartPose.ZERO);
        body.addOrReplaceChild("hips", CubeListBuilder.create().texOffs(28, 0).mirror(false).addBox(-2.5f, -16, 1, 5, 5, 6), PartPose.ZERO);
        body.addOrReplaceChild("hackles", CubeListBuilder.create().texOffs(52, 0).mirror(false).addBox(-0.5f, -2, -0.5f, 1, 2, 1).texOffs(52, 0).mirror(false).addBox(-0.5f, -2, 1.5f, 1, 2, 1).texOffs(52, 0).mirror(false).addBox(-0.5f, -2, 3.5f, 1, 2, 1).texOffs(52, 0).mirror(false).addBox(-0.5f, -1.5f, 5.5f, 1, 2, 1), PartPose.offsetAndRotation(0, -18, -4, -0.4f, 0, 0));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 19).mirror(false).addBox(-3, -3, -5, 6, 5, 5).texOffs(22, 19).mirror(false).addBox(-1.5f, -1.5f, -10, 3, 2, 5).texOffs(52, 4).mirror(false).addBox(-1.5f, 0.5f, -9.5f, 1, 1, 1).texOffs(52, 4).mirror(false).addBox(0.5f, 0.5f, -9.5f, 1, 1, 1), PartPose.offsetAndRotation(0, -15, -6, 0.4f, 0, 0));
        head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(22, 27).mirror(false).addBox(-1.5f, 0, -5, 3, 1, 5).texOffs(52, 4).mirror(false).addBox(-1.5f, -1, -4.5f, 1, 1, 1).texOffs(52, 4).mirror(false).addBox(0.5f, -1, -4.5f, 1, 1, 1), PartPose.offsetAndRotation(0, 0.5f, -5, 0.45f, 0, 0));
        head.addOrReplaceChild("ear_l", CubeListBuilder.create().texOffs(38, 19).mirror(false).addBox(-1, -5, -0.5f, 2, 5, 1), PartPose.offsetAndRotation(-2, -3, -1.5f, -0.7f, 0, -0.2f));
        head.addOrReplaceChild("ear_r", CubeListBuilder.create().texOffs(38, 19).mirror(true).addBox(-1, -5, -0.5f, 2, 5, 1), PartPose.offsetAndRotation(2, -3, -1.5f, -0.7f, 0, 0.2f));
        body.addOrReplaceChild("leg_fl", CubeListBuilder.create().texOffs(0, 40).mirror(false).addBox(-1.5f, 0, -1.5f, 3, 10, 3).texOffs(12, 40).mirror(false).addBox(-1.5f, 9.2f, -2.2f, 3, 0.8f, 1), PartPose.offset(-2, -11, -3.5f));
        body.addOrReplaceChild("leg_fr", CubeListBuilder.create().texOffs(0, 40).mirror(true).addBox(-1.5f, 0, -1.5f, 3, 10, 3).texOffs(12, 40).mirror(true).addBox(-1.5f, 9.2f, -2.2f, 3, 0.8f, 1), PartPose.offset(2, -11, -3.5f));
        PartDefinition leg_bl = body.addOrReplaceChild("leg_bl", CubeListBuilder.create().texOffs(24, 40).mirror(false).addBox(-1, 0, -1, 2, 6, 2), PartPose.offsetAndRotation(-1.6f, -10, 5, 0.35f, 0, 0));
        leg_bl.addOrReplaceChild("leg_bl_lo", CubeListBuilder.create().texOffs(32, 40).mirror(false).addBox(-1, 0, -1, 2, 6, 2), PartPose.offsetAndRotation(0, 5.5f, 0, -0.55f, 0, 0));
        PartDefinition leg_br = body.addOrReplaceChild("leg_br", CubeListBuilder.create().texOffs(24, 40).mirror(true).addBox(-1, 0, -1, 2, 6, 2), PartPose.offsetAndRotation(1.6f, -10, 5, 0.35f, 0, 0));
        leg_br.addOrReplaceChild("leg_br_lo", CubeListBuilder.create().texOffs(32, 40).mirror(true).addBox(-1, 0, -1, 2, 6, 2), PartPose.offsetAndRotation(0, 5.5f, 0, -0.55f, 0, 0));
        PartDefinition tail1 = body.addOrReplaceChild("tail1", CubeListBuilder.create().texOffs(40, 40).mirror(false).addBox(-1, -1, 0, 2, 2, 5), PartPose.offsetAndRotation(0, -15, 6.5f, -0.35f, 0, 0));
        tail1.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(40, 48).mirror(false).addBox(-1.5f, -1.5f, 0, 3, 3, 4), PartPose.offsetAndRotation(0, 0, 4.5f, -0.15f, 0, 0));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(T jackal, float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        float run = limbSwing * 0.9f, amount = Math.min(1f, limbSwingAmount);
        // Galop : pattes en diagonale
        legFL.xRot += Mth.cos(run) * 1.1f * amount;
        legBR.xRot += Mth.cos(run) * 0.9f * amount;
        legFR.xRot += Mth.cos(run + Mth.PI) * 1.1f * amount;
        legBL.xRot += Mth.cos(run + Mth.PI) * 0.9f * amount;
        body.y += Math.abs(Mth.sin(run)) * -0.8f * amount;
        // Tête : suit sa proie (débattement limité), reste basse
        head.yRot += netHeadYaw * Mth.DEG_TO_RAD * 0.8f;
        head.xRot += headPitch * Mth.DEG_TO_RAD * 0.5f;
        // Mâchoire : claque pendant l'attaque, grogne au repos
        float bite = Mth.sin(Mth.sqrt(attackTime) * Mth.PI);
        jaw.xRot += bite * 0.6f + Math.max(0f, Mth.sin(age * 0.25f)) * 0.12f;
        head.xRot -= bite * 0.25f;
        // Oreilles qui frémissent, queue raide qui fouette
        earL.xRot += Mth.sin(age * 0.3f) * 0.06f;
        earR.xRot += Mth.sin(age * 0.3f + 1.7f) * 0.06f;
        tail.yRot += Mth.sin(age * 0.15f) * 0.15f + Mth.cos(run) * 0.2f * amount;
    }
}
