package com.wavesurvivor.client;

import com.wavesurvivor.entity.RoyalGuard;
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
 * MODÈLE DU GARDE ROYAL MOMIFIÉ (conçu sur mesure ; base du Garde du Pharaon et d'Anubis) : casque d'Anubis à tête de
 * chacal, mâchoire de squelette béante, torse en V penché en avant, épaulières à pointes, cage thoracique ouverte (cœur
 * maudit), cape déchirée, pagne, DEUX KHÉPESH. Texture 128×64 (+ calque lumineux).
 * Animations : marche en garde, double coup de khépesh à l'attaque, mâchoire qui hurle, cape et bandelettes qui flottent.
 */
@OnlyIn(Dist.CLIENT)
public class RoyalGuardModel<T extends RoyalGuard> extends HierarchicalModel<T> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation("wavesurvivor", "royal_guard"), "main");

    private final ModelPart root, torso, head, jaw, cape, armR, armL, foreR, foreL, legR, legL, strip1, strip2;

    public RoyalGuardModel(ModelPart root) {
        this.root = root;
        this.torso = root.getChild("torso");
        this.head = torso.getChild("head");
        this.jaw = head.getChild("jaw");
        this.cape = torso.getChild("cape");
        this.armR = torso.getChild("arm_r");
        this.armL = torso.getChild("arm_l");
        this.foreR = armR.getChild("arm_r_fore");
        this.foreL = armL.getChild("arm_l_fore");
        this.legR = root.getChild("leg_r");
        this.legL = root.getChild("leg_l");
        ModelPart hips = root.getChild("hips");
        this.strip1 = hips.getChild("strip1");
        this.strip2 = hips.getChild("strip2");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition torso = root.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 18).mirror(false).addBox(-5, -12, -2.5f, 10, 7, 5).texOffs(30, 18).mirror(false).addBox(-3.5f, -5, -2, 7, 5, 4).texOffs(52, 18).mirror(false).addBox(-1, -10, -2.8f, 2, 2, 1).texOffs(58, 18).mirror(false).addBox(-5.5f, -12.6f, -3, 11, 2, 6), PartPose.offsetAndRotation(0, 12, 0, 0.28f, 0, 0));
        PartDefinition head = torso.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).mirror(false).addBox(-4.5f, -9, -4.5f, 9, 9, 9).texOffs(36, 0).mirror(false).addBox(-1.5f, -6, -10.5f, 3, 3, 6).texOffs(36, 9).mirror(false).addBox(-1, -4.5f, -12, 2, 2, 2).texOffs(54, 0).mirror(false).addBox(-3.5f, -15, -1, 2, 6, 1).texOffs(54, 0).mirror(true).addBox(1.5f, -15, -1, 2, 6, 1).texOffs(60, 0).mirror(false).addBox(-3, -17, -1, 1, 2, 1).texOffs(60, 0).mirror(true).addBox(2, -17, -1, 1, 2, 1), PartPose.offsetAndRotation(0, -12, -0.5f, -0.25f, 0, 0));
        head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(64, 0).mirror(false).addBox(-3, 0, -4.5f, 6, 2, 5), PartPose.offsetAndRotation(0, 0, -0.5f, 0.15f, 0, 0)); // légèrement entrouverte
        torso.addOrReplaceChild("cape", CubeListBuilder.create().texOffs(34, 30).mirror(false).addBox(-5, 0, 0, 10, 16, 1), PartPose.offsetAndRotation(0, -12, 3.1f, 0.38f, 0, 0)); // retombe vers l'arrière, dégage le pagne
        torso.addOrReplaceChild("pauldron_r", CubeListBuilder.create().texOffs(56, 30).mirror(false).addBox(-3, -3, -3, 6, 4, 6).texOffs(80, 30).mirror(false).addBox(-0.5f, -6, -0.5f, 1, 3, 1).texOffs(80, 30).mirror(false).addBox(-2.5f, -5, -0.5f, 1, 2, 1), PartPose.offsetAndRotation(-6, -11.5f, 0, 0, 0, 0.3f));
        torso.addOrReplaceChild("pauldron_l", CubeListBuilder.create().texOffs(56, 30).mirror(true).addBox(-3, -3, -3, 6, 4, 6).texOffs(80, 30).mirror(false).addBox(-0.5f, -6, -0.5f, 1, 3, 1).texOffs(80, 30).mirror(false).addBox(1.5f, -5, -0.5f, 1, 2, 1), PartPose.offsetAndRotation(6, -11.5f, 0, 0, 0, -0.3f));
        PartDefinition arm_r = torso.addOrReplaceChild("arm_r", CubeListBuilder.create().texOffs(84, 30).mirror(false).addBox(-1.5f, -1, -1.5f, 3, 7, 3), PartPose.offsetAndRotation(-6.5f, -10, 0, -2.5f, 0.2f, 0.35f));
        PartDefinition arm_r_fore = arm_r.addOrReplaceChild("arm_r_fore", CubeListBuilder.create().texOffs(96, 30).mirror(false).addBox(-1.5f, 0, -1.5f, 3, 7, 3), PartPose.offsetAndRotation(0, 6, 0, -0.7f, 0, 0));
        PartDefinition arm_r_k = arm_r_fore.addOrReplaceChild("arm_r_k", CubeListBuilder.create().texOffs(24, 48).mirror(false).addBox(-0.5f, -0.5f, -1, 1, 1, 3).texOffs(32, 48).mirror(false).addBox(-1.5f, -1, -1.5f, 3, 2, 1), PartPose.offsetAndRotation(0, 6.5f, 0, -0.6f, 0, 0));
        PartDefinition arm_r_k_b1 = arm_r_k.addOrReplaceChild("arm_r_k_b1", CubeListBuilder.create().texOffs(40, 48).mirror(false).addBox(-0.5f, -1.5f, -7, 1, 3, 7), PartPose.offset(0, 0, -1.5f));
        PartDefinition arm_r_k_b2 = arm_r_k_b1.addOrReplaceChild("arm_r_k_b2", CubeListBuilder.create().texOffs(56, 48).mirror(false).addBox(-0.5f, -2, -5, 1, 4, 5), PartPose.offsetAndRotation(0, 0, -6.6f, 0.6f, 0, 0));
        arm_r_k_b2.addOrReplaceChild("arm_r_k_b3", CubeListBuilder.create().texOffs(68, 48).mirror(false).addBox(-0.5f, -1.5f, -4, 1, 3, 4), PartPose.offsetAndRotation(0, 0, -4.6f, 0.85f, 0, 0));
        PartDefinition arm_l = torso.addOrReplaceChild("arm_l", CubeListBuilder.create().texOffs(84, 30).mirror(true).addBox(-1.5f, -1, -1.5f, 3, 7, 3), PartPose.offsetAndRotation(6.5f, -10, 0, -1, -0.15f, -0.25f));
        PartDefinition arm_l_fore = arm_l.addOrReplaceChild("arm_l_fore", CubeListBuilder.create().texOffs(96, 30).mirror(true).addBox(-1.5f, 0, -1.5f, 3, 7, 3), PartPose.offsetAndRotation(0, 6, 0, -0.45f, 0, 0));
        PartDefinition arm_l_k = arm_l_fore.addOrReplaceChild("arm_l_k", CubeListBuilder.create().texOffs(24, 48).mirror(false).addBox(-0.5f, -0.5f, -1, 1, 1, 3).texOffs(32, 48).mirror(false).addBox(-1.5f, -1, -1.5f, 3, 2, 1), PartPose.offsetAndRotation(0, 6.5f, 0, -0.15f, 0, 0));
        PartDefinition arm_l_k_b1 = arm_l_k.addOrReplaceChild("arm_l_k_b1", CubeListBuilder.create().texOffs(40, 48).mirror(false).addBox(-0.5f, -1.5f, -7, 1, 3, 7), PartPose.offset(0, 0, -1.5f));
        PartDefinition arm_l_k_b2 = arm_l_k_b1.addOrReplaceChild("arm_l_k_b2", CubeListBuilder.create().texOffs(56, 48).mirror(false).addBox(-0.5f, -2, -5, 1, 4, 5), PartPose.offsetAndRotation(0, 0, -6.6f, 0.6f, 0, 0));
        arm_l_k_b2.addOrReplaceChild("arm_l_k_b3", CubeListBuilder.create().texOffs(68, 48).mirror(false).addBox(-0.5f, -1.5f, -4, 1, 3, 4), PartPose.offsetAndRotation(0, 0, -4.6f, 0.85f, 0, 0));
        PartDefinition hips = root.addOrReplaceChild("hips", CubeListBuilder.create().texOffs(92, 18).mirror(false).addBox(-4, -1, -2.5f, 8, 2, 5).texOffs(0, 30).mirror(false).addBox(-4, 0.5f, -2.5f, 8, 5, 5).texOffs(26, 30).mirror(false).addBox(-1.5f, 1, -3, 3, 7, 1), PartPose.offset(0, 12, 0));
        hips.addOrReplaceChild("strip1", CubeListBuilder.create().texOffs(78, 48).mirror(false).addBox(-0.5f, 0, -0.5f, 1, 6, 1), PartPose.offsetAndRotation(-3, 4, -2.4f, 0.1f, 0, 0.15f));
        hips.addOrReplaceChild("strip2", CubeListBuilder.create().texOffs(78, 48).mirror(false).addBox(-0.5f, 0, -0.5f, 1, 6, 1), PartPose.offsetAndRotation(3.2f, 4, 2.2f, -0.1f, 0, -0.1f));
        PartDefinition leg_r = root.addOrReplaceChild("leg_r", CubeListBuilder.create().texOffs(0, 48).mirror(false).addBox(-1.5f, 0, -1.5f, 3, 7, 3), PartPose.offsetAndRotation(-2.4f, 12, 0, -0.45f, 0, 0.08f));
        leg_r.addOrReplaceChild("leg_r_lo", CubeListBuilder.create().texOffs(12, 48).mirror(false).addBox(-1.5f, 0, -1.5f, 3, 7, 3), PartPose.offsetAndRotation(0, 6.5f, 0, 0.55f, 0, 0));
        PartDefinition leg_l = root.addOrReplaceChild("leg_l", CubeListBuilder.create().texOffs(0, 48).mirror(true).addBox(-1.5f, 0, -1.5f, 3, 7, 3), PartPose.offsetAndRotation(2.4f, 12, 0, 0.2f, 0, -0.08f));
        leg_l.addOrReplaceChild("leg_l_lo", CubeListBuilder.create().texOffs(12, 48).mirror(true).addBox(-1.5f, 0, -1.5f, 3, 7, 3), PartPose.offsetAndRotation(0, 6.5f, 0, 0.35f, 0, 0));
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(T guard, float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        root.y = -1.3f; // genoux fléchis : les pieds reposent bien au sol
        float walk = limbSwing * 0.66f, amount = Math.min(1f, limbSwingAmount);
        // Marche en garde (jambes fléchies), torse qui tangue
        legR.xRot += Mth.cos(walk) * 0.7f * amount;
        legL.xRot += Mth.cos(walk + Mth.PI) * 0.7f * amount;
        torso.zRot += Mth.sin(walk) * 0.05f * amount;
        torso.xRot += Mth.sin(age * 0.07f) * 0.02f;
        // Tête : suit sa cible ; mâchoire qui hurle
        head.yRot += netHeadYaw * Mth.DEG_TO_RAD * 0.7f;
        head.xRot += headPitch * Mth.DEG_TO_RAD * 0.5f;
        float strike = Mth.sin(Mth.sqrt(attackTime) * Mth.PI);
        jaw.xRot += Math.max(0f, Mth.sin(age * 0.12f)) * 0.07f + strike * 0.22f;
        // Double coup de khépesh : le bras levé s'abat, puis l'autre taille en travers
        float second = Mth.sin(Mth.clamp(attackTime * 1.6f - 0.6f, 0f, 1f) * Mth.PI);
        armR.xRot += strike * 2.3f;
        foreR.xRot += strike * 0.5f;
        armL.yRot += second * 0.9f;
        armL.xRot -= second * 0.4f;
        foreL.xRot += second * 0.3f;
        // Au repos : les lames oscillent, menaçantes
        armR.zRot += Mth.sin(age * 0.06f) * 0.05f;
        armL.xRot += Mth.sin(age * 0.08f + 1f) * 0.06f;
        // Cape et bandelettes qui flottent (la cape s'écarte vers l'arrière en marchant)
        cape.xRot += Math.abs(Mth.sin(walk)) * 0.25f * amount + Mth.sin(age * 0.05f) * 0.04f;
        strip1.zRot += Mth.sin(age * 0.1f) * 0.15f;
        strip2.zRot += Mth.sin(age * 0.1f + 2f) * 0.15f;
        strip1.xRot -= amount * 0.3f;
        strip2.xRot -= amount * 0.3f;
    }
}
