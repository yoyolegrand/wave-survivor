package com.wavesurvivor.client;

import com.wavesurvivor.entity.StoneBrute;
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
 * MODÈLE DU STONE BRUTE (horde des Corrompus, conçu sur mesure, HAUTE RÉSOLUTION : dessiné à l'échelle 2 puis réduit
 * de moitié). Colosse de rochers moussus (~3,3 blocs) : grande tête-idole aux yeux roses, épaules en rochers, bras
 * massifs aux poings énormes, réseau de fissures roses rayonnant du cœur. Texture 256x256 (+ calque lumineux).
 * Animations : marche lourde (torse qui tangue, bras qui balancent), respiration, FRAPPE ÉCRASANTE (le poing droit se
 * lève très haut puis s'abat de tout son poids), tête qui suit sa cible.
 */
@OnlyIn(Dist.CLIENT)
public class StoneBruteModel<T extends StoneBrute> extends HierarchicalModel<T> {

    public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(new ResourceLocation("wavesurvivor", "stone_brute"), "main");

    private final ModelPart root, torso, head, armL, armR, foreL, foreR, legL, legR, shinL, shinR;

    public StoneBruteModel(ModelPart root) {
        this.root = root;
        this.torso = root.getChild("torso");
        this.head = torso.getChild("head");
        this.armL = torso.getChild("arm_l");
        this.armR = torso.getChild("arm_r");
        this.foreL = armL.getChild("arm_l_fore");
        this.foreR = armR.getChild("arm_r_fore");
        this.legL = root.getChild("leg_l");
        this.legR = root.getChild("leg_r");
        this.shinL = legL.getChild("leg_l_shin");
        this.shinR = legR.getChild("leg_r_shin");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition torso = root.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 0).mirror(false).addBox(-20, -46, -11, 40, 30, 22).texOffs(124, 0).mirror(false).addBox(-15, -16, -10, 30, 12, 20).texOffs(0, 52).mirror(false).addBox(-34, -52, -11, 20, 20, 22).texOffs(0, 52).mirror(true).addBox(14, -52, -11, 20, 20, 22), PartPose.ZERO);
        torso.addOrReplaceChild("head", CubeListBuilder.create().texOffs(84, 52).mirror(false).addBox(-11, -24, -10, 22, 24, 20).texOffs(0, 96).mirror(false).addBox(-12, -26, -11, 24, 4, 22), PartPose.offset(0, -46, -2));
        PartDefinition arm_l = torso.addOrReplaceChild("arm_l", CubeListBuilder.create().texOffs(92, 96).mirror(false).addBox(-8, -2, -8, 16, 24, 16), PartPose.offsetAndRotation(-28, -42, 0, 0.05f, 0, -0.12f));
        PartDefinition arm_l_fore = arm_l.addOrReplaceChild("arm_l_fore", CubeListBuilder.create().texOffs(156, 96).mirror(false).addBox(-9, 0, -9, 18, 24, 18), PartPose.offsetAndRotation(0, 21, 0, -0.12f, 0, 0));
        arm_l_fore.addOrReplaceChild("arm_l_fist", CubeListBuilder.create().texOffs(0, 138).mirror(false).addBox(-10, 0, -10, 20, 16, 20), PartPose.offset(0, 22, 0));
        PartDefinition arm_r = torso.addOrReplaceChild("arm_r", CubeListBuilder.create().texOffs(92, 96).mirror(true).addBox(-8, -2, -8, 16, 24, 16), PartPose.offsetAndRotation(28, -42, 0, 0.05f, 0, 0.12f));
        PartDefinition arm_r_fore = arm_r.addOrReplaceChild("arm_r_fore", CubeListBuilder.create().texOffs(156, 96).mirror(true).addBox(-9, 0, -9, 18, 24, 18), PartPose.offsetAndRotation(0, 21, 0, -0.12f, 0, 0));
        arm_r_fore.addOrReplaceChild("arm_r_fist", CubeListBuilder.create().texOffs(0, 138).mirror(true).addBox(-10, 0, -10, 20, 16, 20), PartPose.offset(0, 22, 0));
        root.addOrReplaceChild("pelvis", CubeListBuilder.create().texOffs(80, 138).mirror(false).addBox(-16, -4, -9, 32, 12, 18), PartPose.ZERO);
        PartDefinition leg_l = root.addOrReplaceChild("leg_l", CubeListBuilder.create().texOffs(180, 138).mirror(false).addBox(-8, 0, -8, 16, 20, 16), PartPose.offset(-10, 8, 0));
        leg_l.addOrReplaceChild("leg_l_shin", CubeListBuilder.create().texOffs(0, 174).mirror(false).addBox(-9, 0, -9, 18, 20, 18), PartPose.offset(0, 19, 0));
        PartDefinition leg_r = root.addOrReplaceChild("leg_r", CubeListBuilder.create().texOffs(180, 138).mirror(true).addBox(-8, 0, -8, 16, 20, 16), PartPose.offset(10, 8, 0));
        leg_r.addOrReplaceChild("leg_r_shin", CubeListBuilder.create().texOffs(0, 174).mirror(true).addBox(-9, 0, -9, 18, 20, 18), PartPose.offset(0, 19, 0));
        return LayerDefinition.create(mesh, 256, 256);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(T brute, float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        root.xScale = root.yScale = root.zScale = 0.5f;  // modèle dessiné à l'échelle 2 (haute résolution)
        float walk = limbSwing * 0.42f, amount = Math.min(1f, limbSwingAmount * 1.4f);

        // MARCHE LOURDE ET IMPOSANTE
        // Grandes enjambées, genoux qui plient à chaque lever de jambe
        legL.xRot += Mth.cos(walk) * 0.85f * amount;
        legR.xRot += Mth.cos(walk + Mth.PI) * 0.85f * amount;
        shinL.xRot += Math.max(0f, -Mth.sin(walk)) * 0.7f * amount;
        shinR.xRot += Math.max(0f, Mth.sin(walk)) * 0.7f * amount;
        // Le corps s'enfonce à chaque pas (impact), puis remonte
        float impact = 1f - Math.abs(Mth.sin(walk));
        torso.y += impact * impact * 3.0f * amount;
        // Balancement prononcé d'un côté à l'autre, épaules qui roulent, léger penché en avant
        torso.zRot += Mth.sin(walk) * 0.13f * amount;
        torso.yRot += Mth.cos(walk) * 0.12f * amount;
        torso.xRot += 0.08f * amount;
        // Respiration lente
        torso.xRot += Mth.sin(age * 0.05f) * 0.02f;
        // Tête : reste stable (compense le balancement) et suit sa cible
        head.zRot -= Mth.sin(walk) * 0.1f * amount;
        head.yRot += netHeadYaw * Mth.DEG_TO_RAD * 0.6f - Mth.cos(walk) * 0.08f * amount;
        head.xRot += headPitch * Mth.DEG_TO_RAD * 0.4f;

        int atk = brute.getAttackAnimationTick();
        if (atk > 0) {
            // FRAPPE ÉCRASANTE : le poing droit se lève très haut puis s'abat, le torse accompagne le coup
            float t = Mth.triangleWave(atk, 10.0f);
            armR.xRot += -2.7f + 2.6f * t;
            foreR.xRot += 0.3f;
            torso.xRot += 0.25f * (1f - t);
            armL.xRot += 0.2f;
        } else {
            // Bras massifs qui balancent en opposition des jambes, avant-bras en retard (effet de masse)
            armL.xRot += Mth.cos(walk + Mth.PI) * 0.7f * amount;
            armR.xRot += Mth.cos(walk) * 0.7f * amount;
            foreL.xRot -= Math.max(0f, Mth.sin(walk + Mth.PI - 0.6f)) * 0.35f * amount;
            foreR.xRot -= Math.max(0f, Mth.sin(walk - 0.6f)) * 0.35f * amount;
            armL.zRot -= (0.06f * amount) + Mth.sin(age * 0.04f) * 0.02f;
            armR.zRot += (0.06f * amount) + Mth.sin(age * 0.04f) * 0.02f;
        }
    }
}
