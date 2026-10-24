package com.wavesurvivor.client;

import com.wavesurvivor.entity.Rootling;
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
 * MODÈLE DU ROOTLING (horde des Corrompus, conçu sur mesure, HAUTE RÉSOLUTION) : tout le modèle est dessiné à
 * l'échelle 2 (4× plus de détails), puis réduit de moitié (root.xScale/yScale/zScale = 0,5). Texture 256×128
 * (+ calque lumineux). Petit être de racines voûté, masque-totem en pierre, bois de cerf en racines, longs bras à griffes.
 * Animations : course effrénée (bras rejetés en arrière), tête qui tressaille par à-coups, bois et racines qui oscillent,
 * double coup de griffes croisé.
 */
@OnlyIn(Dist.CLIENT)
public class RootlingModel<T extends Rootling> extends HierarchicalModel<T> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation("wavesurvivor", "rootling"), "main");

    private final ModelPart root, body, head, antlerL, antlerR, armL, armR, foreL, foreR, legL, legR, root1, root2;

    public RootlingModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.antlerL = head.getChild("antler_l");
        this.antlerR = head.getChild("antler_r");
        this.armL = body.getChild("arm_l");
        this.armR = body.getChild("arm_r");
        this.foreL = armL.getChild("arm_l_fore");
        this.foreR = armR.getChild("arm_r_fore");
        this.legL = root.getChild("leg_l");
        this.legR = root.getChild("leg_r");
        this.root1 = body.getChild("root1");
        this.root2 = body.getChild("root2");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 20).mirror(false).addBox(-5, -16, -3, 10, 16, 6).texOffs(32, 20).mirror(false).addBox(-1, -10, -3.4f, 2, 2, 2), PartPose.offsetAndRotation(0, 32, 0, 0.45f, 0, 0));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).mirror(false).addBox(-4, -8, -4, 8, 8, 8), PartPose.offsetAndRotation(0, -16, -1, -0.35f, 0, 0));
        head.addOrReplaceChild("mask", CubeListBuilder.create().texOffs(128, 0).mirror(false).addBox(-6, -8, 0, 12, 16, 2).texOffs(156, 0).mirror(false).addBox(-6.5f, -5, -1.5f, 13, 2, 2).texOffs(156, 4).mirror(false).addBox(-1, -3, -2, 2, 6, 2).texOffs(164, 4).mirror(false).addBox(-4, 7, -1, 8, 2, 2).texOffs(164, 8).mirror(false).addBox(-6, 1, -1, 3, 2, 1).texOffs(164, 8).mirror(true).addBox(3, 1, -1, 3, 2, 1), PartPose.offset(0, -5, -6.4f));
        PartDefinition antler_l = head.addOrReplaceChild("antler_l", CubeListBuilder.create().texOffs(40, 80).mirror(false).addBox(-1, -10, -1, 2, 10, 2), PartPose.offsetAndRotation(-3.2f, -8.4f, 1, -0.2f, 0, -0.45f));
        antler_l.addOrReplaceChild("antler_l_t1", CubeListBuilder.create().texOffs(48, 80).mirror(false).addBox(-1, -6, -1, 2, 6, 2), PartPose.offsetAndRotation(0, -5.2f, 0, 0, 0, 0.9f));
        antler_l.addOrReplaceChild("antler_l_t2", CubeListBuilder.create().texOffs(56, 80).mirror(false).addBox(-1, -4, -1, 2, 4, 2), PartPose.offsetAndRotation(0, -9.2f, 0, 0, 0, -0.7f));
        PartDefinition antler_r = head.addOrReplaceChild("antler_r", CubeListBuilder.create().texOffs(40, 80).mirror(true).addBox(-1, -10, -1, 2, 10, 2), PartPose.offsetAndRotation(3.2f, -8.4f, 1, -0.2f, 0, 0.45f));
        antler_r.addOrReplaceChild("antler_r_t1", CubeListBuilder.create().texOffs(48, 80).mirror(true).addBox(-1, -6, -1, 2, 6, 2), PartPose.offsetAndRotation(0, -5.2f, 0, 0, 0, -0.9f));
        antler_r.addOrReplaceChild("antler_r_t2", CubeListBuilder.create().texOffs(56, 80).mirror(true).addBox(-1, -4, -1, 2, 4, 2), PartPose.offsetAndRotation(0, -9.2f, 0, 0, 0, 0.7f));
        PartDefinition arm_l = body.addOrReplaceChild("arm_l", CubeListBuilder.create().texOffs(0, 80).mirror(false).addBox(-2, -1, -2, 4, 12, 4), PartPose.offsetAndRotation(-6.6f, -14, 0, 0.25f, 0, -0.12f));
        PartDefinition arm_l_fore = arm_l.addOrReplaceChild("arm_l_fore", CubeListBuilder.create().texOffs(64, 80).mirror(false).addBox(-1, 0, -1, 2, 10, 2), PartPose.offsetAndRotation(0, 10.6f, 0, -0.5f, 0, 0));
        arm_l_fore.addOrReplaceChild("arm_l_claw", CubeListBuilder.create().texOffs(80, 80).mirror(false).addBox(-2, 0, -1, 2, 6, 2).texOffs(80, 80).mirror(false).addBox(0.4f, 0, -1, 2, 6, 2).texOffs(88, 80).mirror(false).addBox(-0.8f, 0, -2.8f, 2, 4, 2), PartPose.offsetAndRotation(0, 9.2f, 0, -0.3f, 0, 0));
        PartDefinition arm_r = body.addOrReplaceChild("arm_r", CubeListBuilder.create().texOffs(0, 80).mirror(true).addBox(-2, -1, -2, 4, 12, 4), PartPose.offsetAndRotation(6.6f, -14, 0, 0.25f, 0, 0.12f));
        PartDefinition arm_r_fore = arm_r.addOrReplaceChild("arm_r_fore", CubeListBuilder.create().texOffs(64, 80).mirror(true).addBox(-1, 0, -1, 2, 10, 2), PartPose.offsetAndRotation(0, 10.6f, 0, -0.5f, 0, 0));
        arm_r_fore.addOrReplaceChild("arm_r_claw", CubeListBuilder.create().texOffs(80, 80).mirror(true).addBox(-2, 0, -1, 2, 6, 2).texOffs(80, 80).mirror(true).addBox(0.4f, 0, -1, 2, 6, 2).texOffs(88, 80).mirror(true).addBox(-0.8f, 0, -2.8f, 2, 4, 2), PartPose.offsetAndRotation(0, 9.2f, 0, -0.3f, 0, 0));
        body.addOrReplaceChild("wrap1", CubeListBuilder.create().texOffs(48, 116).mirror(false).addBox(-6, -1, -1, 12, 2, 2), PartPose.offsetAndRotation(0, -10, -3.4f, 0, 0, 0.55f));
        body.addOrReplaceChild("wrap2", CubeListBuilder.create().texOffs(48, 116).mirror(false).addBox(-6, -1, -1, 12, 2, 2), PartPose.offsetAndRotation(0, -6, -3.4f, 0, 0, -0.5f));
        body.addOrReplaceChild("spike_l", CubeListBuilder.create().texOffs(96, 100).mirror(false).addBox(-1, -6, -1, 2, 6, 2), PartPose.offsetAndRotation(-4.8f, -15.6f, 0, 0.3f, 0, -0.6f));
        body.addOrReplaceChild("spike_r", CubeListBuilder.create().texOffs(96, 100).mirror(false).addBox(-1, -6, -1, 2, 6, 2), PartPose.offsetAndRotation(4.8f, -15.6f, 0, 0.3f, 0, 0.6f));
        body.addOrReplaceChild("root1", CubeListBuilder.create().texOffs(96, 80).mirror(false).addBox(-1, 0, -1, 2, 12, 2), PartPose.offsetAndRotation(-2, -2, 2.8f, 0.5f, 0, 0.2f));
        body.addOrReplaceChild("root2", CubeListBuilder.create().texOffs(96, 80).mirror(false).addBox(-1, 0, -1, 2, 12, 2), PartPose.offsetAndRotation(2.4f, -6, 2.8f, 0.7f, 0, -0.25f));
        PartDefinition leg_l = root.addOrReplaceChild("leg_l", CubeListBuilder.create().texOffs(0, 100).mirror(false).addBox(-2, 0, -2, 4, 10, 4), PartPose.offsetAndRotation(-3, 32, 1, -0.4f, 0, -0.05f));
        leg_l.addOrReplaceChild("leg_l_lo", CubeListBuilder.create().texOffs(16, 100).mirror(false).addBox(-1.5f, 0, -1.5f, 3, 9, 3).texOffs(28, 100).mirror(false).addBox(-2, 8, -4.4f, 4, 1.6f, 6), PartPose.offsetAndRotation(0, 9.2f, 0, 0.55f, 0, 0));
        PartDefinition leg_r = root.addOrReplaceChild("leg_r", CubeListBuilder.create().texOffs(0, 100).mirror(true).addBox(-2, 0, -2, 4, 10, 4), PartPose.offsetAndRotation(3, 32, 1, -0.4f, 0, 0.05f));
        leg_r.addOrReplaceChild("leg_r_lo", CubeListBuilder.create().texOffs(16, 100).mirror(true).addBox(-1.5f, 0, -1.5f, 3, 9, 3).texOffs(28, 100).mirror(true).addBox(-2, 8, -4.4f, 4, 1.6f, 6), PartPose.offsetAndRotation(0, 9.2f, 0, 0.55f, 0, 0));
        return LayerDefinition.create(mesh, 256, 128);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(T rootling, float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        root.xScale = root.yScale = root.zScale = 0.5f;  // modèle dessiné à l'échelle 2 (haute résolution)
        root.y = -1.0f;                                  // genoux fléchis : les pieds reposent au sol
        float run = limbSwing * 1.6f, amount = Math.min(1f, limbSwingAmount * 1.3f);

        // Course effrénée : petites jambes rapides, corps qui tressaute et se penche encore plus
        legL.xRot += Mth.cos(run) * 1.0f * amount;
        legR.xRot += Mth.cos(run + Mth.PI) * 1.0f * amount;
        body.y += Math.abs(Mth.sin(run)) * -1.8f * amount;
        body.xRot += 0.2f * amount;
        body.zRot += Mth.sin(run) * 0.08f * amount;

        // Bras rejetés en arrière qui battent l'air en courant
        armL.xRot += amount * 0.9f + Mth.cos(run) * 0.45f * amount;
        armR.xRot += amount * 0.9f + Mth.cos(run + Mth.PI) * 0.45f * amount;
        foreL.xRot -= amount * 0.3f;
        foreR.xRot -= amount * 0.3f;
        armL.zRot -= Mth.sin(age * 0.09f) * 0.05f;
        armR.zRot += Mth.sin(age * 0.09f + 1f) * 0.05f;

        // Tête : suit sa cible, et TRESSAILLE par à-coups nerveux (comme un pantin)
        head.yRot += netHeadYaw * Mth.DEG_TO_RAD * 0.7f;
        head.xRot += headPitch * Mth.DEG_TO_RAD * 0.5f;
        float pulse = ((int) (age / 6f + rootling.getId() * 3) % 5 == 0) ? 1f : 0f;
        head.zRot += pulse * Mth.sin(age * 3.1f) * 0.22f + Mth.sin(age * 0.6f) * 0.03f;
        head.yRot += pulse * Mth.cos(age * 2.7f) * 0.18f;

        // Bois de cerf et racines qui oscillent
        antlerL.zRot += Mth.sin(age * 0.12f) * 0.06f - amount * 0.1f;
        antlerR.zRot -= Mth.sin(age * 0.12f + 0.8f) * 0.06f - amount * 0.1f;
        root1.xRot += Mth.sin(age * 0.15f) * 0.15f + amount * 0.4f;
        root2.xRot += Mth.sin(age * 0.15f + 1.5f) * 0.15f + amount * 0.4f;

        // Attaque : double coup de griffes croisé vers l'avant
        float strike = Mth.sin(Mth.sqrt(attackTime) * Mth.PI);
        if (strike > 0f) {
            armL.xRot -= strike * 1.9f;
            armR.xRot -= strike * 1.9f;
            armL.yRot += strike * 0.6f;
            armR.yRot -= strike * 0.6f;
            foreL.xRot -= strike * 0.5f;
            foreR.xRot -= strike * 0.5f;
            body.xRot += strike * 0.25f;
        }
    }
}
