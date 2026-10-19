package com.wavesurvivor.client;

import com.wavesurvivor.entity.SandstoneGolem;
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
 * MODÈLE DU COLOSSE DE GRÈS (conçu sur mesure, base de Rê-Atoum) : gardien de tombeau voûté, tête de pharaon enfoncée
 * entre deux épaules en dalles brisées (némès, barbe, cobra), cœur-soleil dans la poitrine, éclats d'obélisque dans
 * le dos, bras démesurés aux poings-blocs, 3 rochers en orbite. Texture 256×128 (+ calque lumineux).
 * Animations : marche lourde, bras qui balancent, coup des deux poings, rochers qui tournent (plus vite en colère).
 */
@OnlyIn(Dist.CLIENT)
public class SandstoneGolemModel<T extends SandstoneGolem> extends HierarchicalModel<T> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation("wavesurvivor", "sandstone_golem"), "main");

    private final ModelPart root, body, torso, head, armL, armR, foreL, foreR, legL, legR;
    private final ModelPart[] rocks = new ModelPart[3];

    public SandstoneGolemModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.torso = body.getChild("torso");
        this.head = torso.getChild("head");
        this.armL = torso.getChild("arm_l");
        this.armR = torso.getChild("arm_r");
        this.foreL = armL.getChild("arm_l_fore");
        this.foreR = armR.getChild("arm_r_fore");
        this.legL = body.getChild("leg_l");
        this.legR = body.getChild("leg_r");
        for (int i = 0; i < 3; i++) rocks[i] = body.getChild("rock" + (i + 1));
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0, 24, 0));
        body.addOrReplaceChild("leg_l", CubeListBuilder.create().texOffs(72, 34).mirror(false).addBox(-4, 0, -4, 8, 12, 8), PartPose.offset(-6, -12, 1));
        body.addOrReplaceChild("leg_r", CubeListBuilder.create().texOffs(72, 34).mirror(true).addBox(-4, 0, -4, 8, 12, 8), PartPose.offset(6, -12, 1));
        body.addOrReplaceChild("pelvis", CubeListBuilder.create().texOffs(70, 0).mirror(false).addBox(-7, -17, -4, 14, 5, 8), PartPose.ZERO);
        PartDefinition torso = body.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 0).mirror(false).addBox(-11, -20, -7, 22, 20, 13).texOffs(222, 0).mirror(false).addBox(-3, -13, -7.6f, 6, 6, 1), PartPose.offsetAndRotation(0, -17, 0, 0.38f, 0, 0));
        torso.addOrReplaceChild("shoulder_l", CubeListBuilder.create().texOffs(146, 0).mirror(false).addBox(-8, -6, -6, 8, 6, 11), PartPose.offsetAndRotation(-8, -18, 0, 0, 0, 0.25f));
        torso.addOrReplaceChild("shoulder_r", CubeListBuilder.create().texOffs(146, 0).mirror(true).addBox(0, -6, -6, 8, 6, 11), PartPose.offsetAndRotation(8, -18, 0, 0, 0, -0.25f));
        torso.addOrReplaceChild("spike1", CubeListBuilder.create().texOffs(120, 34).mirror(false).addBox(-1.5f, -8, -1.5f, 3, 8, 3), PartPose.offsetAndRotation(-4, -18, 4, -0.5f, 0, 0.2f));
        torso.addOrReplaceChild("spike2", CubeListBuilder.create().texOffs(120, 34).mirror(false).addBox(-1.5f, -8, -1.5f, 3, 8, 3), PartPose.offsetAndRotation(3, -17, 5, -0.65f, 0, -0.25f));
        torso.addOrReplaceChild("spike3", CubeListBuilder.create().texOffs(120, 34).mirror(false).addBox(-1.5f, -8, -1.5f, 3, 8, 3), PartPose.offsetAndRotation(0, -10, 6, -0.9f, 0, 0.05f));
        torso.addOrReplaceChild("head", CubeListBuilder.create().texOffs(114, 0).mirror(false).addBox(-4, -8, -6, 8, 9, 8).texOffs(104, 34).mirror(false).addBox(-6, -7, -3, 2, 9, 6).texOffs(104, 34).mirror(true).addBox(4, -7, -3, 2, 9, 6).texOffs(236, 0).mirror(false).addBox(-1, -12, -6.8f, 2, 4, 2).texOffs(236, 8).mirror(false).addBox(-1, 1, -6.5f, 2, 4, 2), PartPose.offsetAndRotation(0, -19, -5, -0.3f, 0, 0));
        PartDefinition arm_l = torso.addOrReplaceChild("arm_l", CubeListBuilder.create().texOffs(0, 34).mirror(true).addBox(-4, -2, -4, 8, 16, 8), PartPose.offsetAndRotation(-14, -18, -1, -0.15f, 0, 0.12f));
        arm_l.addOrReplaceChild("arm_l_fore", CubeListBuilder.create().texOffs(32, 34).mirror(true).addBox(-5, 0, -5, 10, 14, 10), PartPose.offsetAndRotation(0, 13, 0, -0.35f, 0, 0));
        PartDefinition arm_r = torso.addOrReplaceChild("arm_r", CubeListBuilder.create().texOffs(0, 34).mirror(false).addBox(-4, -2, -4, 8, 16, 8), PartPose.offsetAndRotation(14, -18, -1, -0.15f, 0, -0.12f));
        arm_r.addOrReplaceChild("arm_r_fore", CubeListBuilder.create().texOffs(32, 34).mirror(false).addBox(-5, 0, -5, 10, 14, 10), PartPose.offsetAndRotation(0, 13, 0, -0.35f, 0, 0));
        body.addOrReplaceChild("rock1", CubeListBuilder.create().texOffs(132, 34).mirror(false).addBox(-1.5f, -1.5f, -1.5f, 3, 3, 3), PartPose.offset(-17, -44, -4));
        body.addOrReplaceChild("rock2", CubeListBuilder.create().texOffs(132, 34).mirror(false).addBox(-1.5f, -1.5f, -1.5f, 3, 3, 3), PartPose.offset(16, -40, -8));
        body.addOrReplaceChild("rock3", CubeListBuilder.create().texOffs(132, 34).mirror(false).addBox(-1.5f, -1.5f, -1.5f, 3, 3, 3), PartPose.offset(2, -48, 6));
        return LayerDefinition.create(mesh, 256, 128);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(T golem, float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        float walk = limbSwing * 0.55f, amount = Math.min(1f, limbSwingAmount);
        // Marche lourde : jambes, torse qui tangue
        legL.xRot += Mth.cos(walk) * 0.7f * amount;
        legR.xRot += Mth.cos(walk + Mth.PI) * 0.7f * amount;
        torso.zRot += Mth.sin(walk) * 0.06f * amount;
        torso.xRot += Mth.sin(age * 0.06f) * 0.02f;                  // respiration
        // Tête : suit sa cible
        head.yRot += netHeadYaw * Mth.DEG_TO_RAD * 0.6f;
        head.xRot += headPitch * Mth.DEG_TO_RAD * 0.4f;
        // Bras : balancent en marchant ; COUP DES DEUX POINGS (levés puis abattus)
        int atk = golem.getAttackAnimationTick();
        if (atk > 0) {
            float t = Mth.triangleWave(atk, 10.0f);
            armL.xRot += -2.0f + 1.6f * t;
            armR.xRot += -2.0f + 1.6f * t;
            foreL.xRot += 0.3f;
            foreR.xRot += 0.3f;
        } else {
            armL.xRot += Mth.cos(walk + Mth.PI) * 0.55f * amount;
            armR.xRot += Mth.cos(walk) * 0.55f * amount;
            armL.zRot += Mth.sin(age * 0.05f) * 0.03f;
            armR.zRot -= Mth.sin(age * 0.05f) * 0.03f;
        }
        // Rochers en orbite autour de lui (plus vite quand il a une cible)
        float speed = golem.isAggressive() ? 0.09f : 0.035f;
        for (int i = 0; i < 3; i++) {
            float a = age * speed + i * ((float) (Math.PI * 2.0) / 3f);
            float r = 17f + (i == 2 ? -3f : 0f);
            rocks[i].x = Mth.cos(a) * r;
            rocks[i].z = Mth.sin(a) * r;
            rocks[i].y = -42f - i * 2f + Mth.sin(age * 0.08f + i) * 1.5f;
            rocks[i].yRot = a;
            rocks[i].xRot = age * 0.05f + i;
        }
    }
}
