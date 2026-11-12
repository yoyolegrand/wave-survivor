package com.wavesurvivor.client;

import com.wavesurvivor.entity.FailleAnimated;
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
import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * BOLIDE (monstre de la Faille) : tombe du ciel en boule de roche (pièce « ball »), se relève en golem de cristal en fusion, refroidit (textures changées par BolideRenderer).
 * Généré par script (Claude chat, docs/faille/monstres/sources/) : ne pas retoucher à la main.
 * Dessiné à l'échelle 2 puis réduit de moitié (haute résolution). Texture 256x256 + calque lumineux.
 */
@OnlyIn(Dist.CLIENT)
public class BolideModel<T extends Mob> extends HierarchicalModel<T> {

    public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(new ResourceLocation("wavesurvivor", "bolide"), "main");

    private final ModelPart root;
    private final ModelPart ball, pelvis, torso, head, armL, armR, foreL, foreR, legL, legR, shinL, shinR;

    public BolideModel(ModelPart root) {
        this.root = root;
        this.ball = root.getChild("ball");
        this.pelvis = root.getChild("pelvis");
        this.torso = pelvis.getChild("torso");
        this.head = torso.getChild("head");
        this.armL = torso.getChild("arm_1");
        this.armR = torso.getChild("arm_-1");
        this.foreL = armL.getChild("fore_1");
        this.foreR = armR.getChild("fore_-1");
        this.legL = pelvis.getChild("leg_1");
        this.legR = pelvis.getChild("leg_-1");
        this.shinL = legL.getChild("shin_1");
        this.shinR = legR.getChild("shin_-1");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_pelvis = root.addOrReplaceChild("pelvis", CubeListBuilder.create().texOffs(132, 114).addBox(-10F, -4F, -7F, 20F, 10F, 14F), PartPose.offsetAndRotation(0F, 8F, 0F, 0F, 0F, 0F));
        PartDefinition p_leg_m1 = p_pelvis.addOrReplaceChild("leg_-1", CubeListBuilder.create().texOffs(76, 86).addBox(-5F, 0F, -5F, 10F, 18F, 10F), PartPose.offsetAndRotation(-7F, 4F, 0F, 0F, 0F, 0F));
        p_leg_m1.addOrReplaceChild("shin_-1", CubeListBuilder.create().texOffs(88, 114).addBox(-5.5F, 0F, -5.5F, 11F, 14F, 11F).texOffs(130, 140).addBox(-6F, 12F, -8F, 12F, 5F, 14F), PartPose.offsetAndRotation(0F, 17F, 0F, 0F, 0F, 0F));
        PartDefinition p_leg_1 = p_pelvis.addOrReplaceChild("leg_1", CubeListBuilder.create().texOffs(76, 86).addBox(-5F, 0F, -5F, 10F, 18F, 10F), PartPose.offsetAndRotation(7F, 4F, 0F, 0F, 0F, 0F));
        p_leg_1.addOrReplaceChild("shin_1", CubeListBuilder.create().texOffs(88, 114).addBox(-5.5F, 0F, -5.5F, 11F, 14F, 11F).texOffs(130, 140).addBox(-6F, 12F, -8F, 12F, 5F, 14F), PartPose.offsetAndRotation(0F, 17F, 0F, 0F, 0F, 0F));
        PartDefinition p_torso = p_pelvis.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 86).addBox(-11F, -12F, -8F, 22F, 12F, 16F).texOffs(0, 0).addBox(-15F, -32F, -11F, 30F, 22F, 22F).texOffs(0, 140).addBox(-12F, -36F, -9F, 24F, 5F, 18F).texOffs(112, 163).addBox(-4F, -24F, -11.6F, 8F, 8F, 1F), PartPose.offsetAndRotation(0F, -3F, 0F, 0.2F, 0F, 0F));
        p_torso.addOrReplaceChild("head", CubeListBuilder.create().texOffs(84, 140).addBox(-6F, -9F, -6F, 12F, 10F, 11F).texOffs(178, 163).addBox(-4F, -5F, -6.6F, 8F, 2F, 1F), PartPose.offsetAndRotation(0F, -33F, -7F, -0.2F, 0F, 0F));
        p_torso.addOrReplaceChild("back_c0", CubeListBuilder.create().texOffs(214, 140).addBox(-3F, -9F, -3F, 6F, 9F, 6F).texOffs(64, 163).addBox(-2F, -17F, -2F, 4F, 8F, 4F).texOffs(162, 163).addBox(-1F, -22F, -1F, 2F, 5F, 2F), PartPose.offsetAndRotation(-9F, -34F, 4F, 0.5F, 0F, -0.5F));
        p_torso.addOrReplaceChild("back_c1", CubeListBuilder.create().texOffs(182, 140).addBox(-4F, -11F, -4F, 8F, 11F, 8F).texOffs(0, 163).addBox(-2.5F, -21F, -2.5F, 5F, 10F, 5F).texOffs(154, 163).addBox(-1F, -27F, -1F, 2F, 6F, 2F), PartPose.offsetAndRotation(-2F, -34F, 7F, 0.7F, 0F, 0.1F));
        p_torso.addOrReplaceChild("back_c2", CubeListBuilder.create().texOffs(20, 163).addBox(-3F, -8F, -3F, 6F, 8F, 6F).texOffs(80, 163).addBox(-2F, -15F, -2F, 4F, 7F, 4F).texOffs(162, 163).addBox(-1F, -20F, -1F, 2F, 5F, 2F), PartPose.offsetAndRotation(6F, -34F, 5F, 0.5F, 0F, 0.4F));
        p_torso.addOrReplaceChild("back_c3", CubeListBuilder.create().texOffs(96, 163).addBox(-2F, -6F, -2F, 4F, 6F, 4F).texOffs(142, 163).addBox(-1.5F, -11F, -1.5F, 3F, 5F, 3F).texOffs(170, 163).addBox(-1F, -15F, -1F, 2F, 4F, 2F), PartPose.offsetAndRotation(12F, -34F, -2F, 0.1F, 0F, 0.9F));
        PartDefinition p_arm_m1 = p_torso.addOrReplaceChild("arm_-1", CubeListBuilder.create().texOffs(176, 86).addBox(-7F, -6F, -7F, 14F, 12F, 14F).texOffs(48, 114).addBox(-5F, 4F, -5F, 10F, 16F, 10F), PartPose.offsetAndRotation(-18F, -28F, 0F, 0F, 0F, 0.15F));
        p_arm_m1.addOrReplaceChild("fore_-1", CubeListBuilder.create().texOffs(0, 114).addBox(-6F, 0F, -6F, 12F, 14F, 12F).texOffs(116, 86).addBox(-7.5F, 12F, -7.5F, 15F, 11F, 15F), PartPose.offsetAndRotation(0F, 18F, 0F, -0.3F, 0F, 0F));
        PartDefinition p_arm_1 = p_torso.addOrReplaceChild("arm_1", CubeListBuilder.create().texOffs(176, 86).addBox(-7F, -6F, -7F, 14F, 12F, 14F).texOffs(48, 114).addBox(-5F, 4F, -5F, 10F, 16F, 10F), PartPose.offsetAndRotation(18F, -28F, 0F, 0F, 0F, -0.15F));
        p_arm_1.addOrReplaceChild("fore_1", CubeListBuilder.create().texOffs(0, 114).addBox(-6F, 0F, -6F, 12F, 14F, 12F).texOffs(116, 86).addBox(-7.5F, 12F, -7.5F, 15F, 11F, 15F), PartPose.offsetAndRotation(0F, 18F, 0F, -0.3F, 0F, 0F));
        PartDefinition p_ball = root.addOrReplaceChild("ball", CubeListBuilder.create().texOffs(104, 0).addBox(-11F, -11F, -11F, 22F, 22F, 22F).texOffs(148, 44).addBox(-13F, -8F, -8F, 26F, 16F, 16F).texOffs(84, 44).addBox(-8F, -13F, -8F, 16F, 26F, 16F).texOffs(0, 44).addBox(-8F, -8F, -13F, 16F, 16F, 26F), PartPose.offsetAndRotation(0F, 26F, 0F, 0F, 0F, 0.4F));
        p_ball.addOrReplaceChild("bc0", CubeListBuilder.create().texOffs(44, 163).addBox(-2.5F, -7F, -2.5F, 5F, 7F, 5F).texOffs(130, 163).addBox(-1.5F, -13F, -1.5F, 3F, 6F, 3F).texOffs(170, 163).addBox(-1F, -17F, -1F, 2F, 4F, 2F), PartPose.offsetAndRotation(0F, -13F, 0F, 0F, 0F, 0F));
        p_ball.addOrReplaceChild("bc1", CubeListBuilder.create().texOffs(44, 163).addBox(-2.5F, -7F, -2.5F, 5F, 7F, 5F).texOffs(130, 163).addBox(-1.5F, -13F, -1.5F, 3F, 6F, 3F).texOffs(170, 163).addBox(-1F, -17F, -1F, 2F, 4F, 2F), PartPose.offsetAndRotation(12F, -4F, 3F, 0F, 0F, -1.4F));
        p_ball.addOrReplaceChild("bc2", CubeListBuilder.create().texOffs(44, 163).addBox(-2.5F, -7F, -2.5F, 5F, 7F, 5F).texOffs(130, 163).addBox(-1.5F, -13F, -1.5F, 3F, 6F, 3F).texOffs(170, 163).addBox(-1F, -17F, -1F, 2F, 4F, 2F), PartPose.offsetAndRotation(-11F, 2F, -4F, 0F, 0F, 1.6F));
        p_ball.addOrReplaceChild("bc3", CubeListBuilder.create().texOffs(44, 163).addBox(-2.5F, -7F, -2.5F, 5F, 7F, 5F).texOffs(130, 163).addBox(-1.5F, -13F, -1.5F, 3F, 6F, 3F).texOffs(170, 163).addBox(-1F, -17F, -1F, 2F, 4F, 2F), PartPose.offsetAndRotation(3F, 12F, 6F, 3.1F, 0F, 0F));
        p_ball.addOrReplaceChild("bc4", CubeListBuilder.create().texOffs(44, 163).addBox(-2.5F, -7F, -2.5F, 5F, 7F, 5F).texOffs(130, 163).addBox(-1.5F, -13F, -1.5F, 3F, 6F, 3F).texOffs(170, 163).addBox(-1F, -17F, -1F, 2F, 4F, 2F), PartPose.offsetAndRotation(-4F, -8F, 11F, 1.4F, 0F, 0F));
        return LayerDefinition.create(mesh, 256, 256);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }

    private static void scale(ModelPart p, float s) {
        p.xScale = s;
        p.yScale = s;
        p.zScale = s;
    }

    @Override
    public void setupAnim(T e, float limbSwing, float limbSwingAmount, float t, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        scale(root, 0.5f);
        int st = e instanceof FailleAnimated fa ? fa.failleState() : 0;
        float p = e instanceof FailleAnimated fa2 ? Mth.clamp(fa2.failleProgress(t), 0f, 1f) : 0f;
        float w = limbSwing * 0.6f, a = Math.min(1f, limbSwingAmount * 1.5f);
        // États : 0 = CHUTE (boule de roche qui tourne, le golem est caché), 1 = IMPACT (30 ticks : accroupi dans le cratère,
        //         poing au sol, puis se relève), 2 = ACTIF (marche), 3 = COUP (12 ticks : direct du poing droit).
        boolean falling = st == 0;
        ball.visible = falling;
        pelvis.visible = !falling;
        if (falling) {
            ball.xRot = t * 0.5f;
            ball.yRot = t * 0.3f;
            return;
        }
        // accroupi (k = 1) → debout (k = 0)
        float k = st == 1 ? 1f - smooth((p - 0.35f) / 0.65f) : 0f;
        pelvis.y = 8f + 12f * k;
        legL.xRot = -1.3f * k;  shinL.xRot = 2.2f * k;
        legR.xRot = 0.4f * k;   shinR.xRot = 1.3f * k;
        torso.xRot = Mth.lerp(k, 0.2f, 0.75f);
        head.xRot = Mth.lerp(k, -0.2f, -0.6f);
        armR.xRot = -0.6f * k;  armR.zRot += 0.25f * k;  foreR.xRot = -0.3f - 0.2f * k;
        armL.xRot = 0.3f * k;   armL.zRot += -0.6f * k;
        // marche lourde
        float m = a * (1f - k);
        legL.xRot += Mth.sin(w) * 0.6f * m;
        legR.xRot -= Mth.sin(w) * 0.6f * m;
        armL.xRot -= Mth.sin(w) * 0.45f * m;
        armR.xRot += Mth.sin(w) * 0.45f * m;
        torso.yRot = Mth.sin(w) * 0.1f * m;
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD * 0.5f;
        if (st == 3) {                                           // COUP : arme le poing puis frappe droit devant
            float wind = smooth(p / 0.4f), hit = smooth((p - 0.4f) / 0.2f), back = smooth((p - 0.7f) / 0.3f);
            armR.xRot += -1.0f * wind - 0.7f * hit + 1.7f * back;
            torso.yRot += 0.35f * wind - 0.6f * hit + 0.25f * back;
        }
    }
}
