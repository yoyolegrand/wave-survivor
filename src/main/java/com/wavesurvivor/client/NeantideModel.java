package com.wavesurvivor.client;

import com.wavesurvivor.entity.Neantide;
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
 * MODÈLE DU NÉANTIDE (créature du néant insectoïde, conçu sur mesure, HAUTE RÉSOLUTION : dessiné à l'échelle 2 puis
 * réduit de moitié). Yeux composés, mandibules en crochet, antennes, chitine lustrée, bras-faux de mante à lames
 * lumineuses, petits bras-pinces, élytres entrouvertes sur une membrane du néant, jambes d'insecte coudées.
 * Texture 256x128 (+ calque lumineux).
 * Animations : démarche saccadée, mandibules qui claquent, antennes qui frémissent, élytres qui vibrent (et s'ouvrent
 * quand il chasse), FAUCHAGE EN CROIX des deux bras-faux à l'attaque.
 */
@OnlyIn(Dist.CLIENT)
public class NeantideModel<T extends Neantide> extends HierarchicalModel<T> {

    public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(new ResourceLocation("wavesurvivor", "neantide"), "main");

    private final ModelPart root, torso, head, mandL, mandR, mandTipL, mandTipR, antL, antR,
            armL, armR, foreL, foreR, bladeL, bladeR, elyL, elyR, legL, legR, shinL, shinR;

    public NeantideModel(ModelPart root) {
        this.root = root;
        this.torso = root.getChild("torso");
        this.head = torso.getChild("head");
        this.mandL = head.getChild("mand_l");
        this.mandR = head.getChild("mand_r");
        this.mandTipL = mandL.getChild("mand_l_tip");
        this.mandTipR = mandR.getChild("mand_r_tip");
        this.antL = head.getChild("ant_l");
        this.antR = head.getChild("ant_r");
        this.armL = torso.getChild("arm_l");
        this.armR = torso.getChild("arm_r");
        this.foreL = armL.getChild("arm_l_fore");
        this.foreR = armR.getChild("arm_r_fore");
        this.bladeL = foreL.getChild("arm_l_blade");
        this.bladeR = foreR.getChild("arm_r_blade");
        this.elyL = torso.getChild("ely_l");
        this.elyR = torso.getChild("ely_r");
        this.legL = root.getChild("leg_l");
        this.legR = root.getChild("leg_r");
        this.shinL = legL.getChild("leg_l_shin");
        this.shinR = legR.getChild("leg_r_shin");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition torso = root.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 0).mirror(false).addBox(-5, -12, -4, 10, 12, 8).texOffs(36, 0).mirror(false).addBox(-9, -30, -6, 18, 18, 12).texOffs(96, 0).mirror(false).addBox(-8, -29, -7.2f, 16, 14, 2).texOffs(132, 0).mirror(false).addBox(-8, -29, 5.2f, 16, 24, 1.2f), PartPose.offsetAndRotation(0, 4, 0, 0.28f, 0, 0));
        PartDefinition head = torso.addOrReplaceChild("head", CubeListBuilder.create().texOffs(166, 0).mirror(false).addBox(-6, -12, -8, 12, 12, 12).texOffs(214, 0).mirror(false).addBox(-7.2f, -10.4f, -8.8f, 5.2f, 6, 4.8f).texOffs(214, 0).mirror(true).addBox(2, -10.4f, -8.8f, 5.2f, 6, 4.8f).texOffs(234, 0).mirror(false).addBox(-4, -4, -9.2f, 8, 4, 2).texOffs(0, 30).mirror(false).addBox(-2, -15, -7, 4, 4, 14).texOffs(36, 30).mirror(false).addBox(-6.8f, -5, -6, 2, 6, 8).texOffs(56, 30).mirror(false).addBox(4.8f, -5, -6, 2, 6, 8), PartPose.offsetAndRotation(0, -30, -3, 0.1f, 0, 0));
        PartDefinition mand_l = head.addOrReplaceChild("mand_l", CubeListBuilder.create().texOffs(76, 30).mirror(false).addBox(-2, -2, -8, 4, 4, 8), PartPose.offsetAndRotation(-3.6f, -2.4f, -7.6f, 0.25f, 0.45f, 0));
        mand_l.addOrReplaceChild("mand_l_tip", CubeListBuilder.create().texOffs(100, 30).mirror(false).addBox(-1.2f, -1.2f, -7, 2.4f, 2.4f, 7).texOffs(118, 30).mirror(false).addBox(-1, -1, -3, 2, 2, 2), PartPose.offsetAndRotation(0, 0, -7.2f, 0, -0.95f, 0));
        PartDefinition mand_r = head.addOrReplaceChild("mand_r", CubeListBuilder.create().texOffs(76, 30).mirror(true).addBox(-2, -2, -8, 4, 4, 8), PartPose.offsetAndRotation(3.6f, -2.4f, -7.6f, 0.25f, -0.45f, 0));
        mand_r.addOrReplaceChild("mand_r_tip", CubeListBuilder.create().texOffs(100, 30).mirror(true).addBox(-1.2f, -1.2f, -7, 2.4f, 2.4f, 7).texOffs(118, 30).mirror(true).addBox(-1, -1, -3, 2, 2, 2), PartPose.offsetAndRotation(0, 0, -7.2f, 0, 0.95f, 0));
        PartDefinition ant_l = head.addOrReplaceChild("ant_l", CubeListBuilder.create().texOffs(126, 30).mirror(false).addBox(-0.8f, -12, -0.8f, 1.6f, 12, 1.6f), PartPose.offsetAndRotation(-3.2f, -12, -5, -0.4f, 0, -0.35f));
        ant_l.addOrReplaceChild("ant_l_2", CubeListBuilder.create().texOffs(134, 30).mirror(false).addBox(-0.8f, -12, -0.8f, 1.6f, 12, 1.6f), PartPose.offsetAndRotation(0, -11.2f, 0, 0.9f, 0, -0.15f));
        PartDefinition ant_r = head.addOrReplaceChild("ant_r", CubeListBuilder.create().texOffs(126, 30).mirror(true).addBox(-0.8f, -12, -0.8f, 1.6f, 12, 1.6f), PartPose.offsetAndRotation(3.2f, -12, -5, -0.4f, 0, 0.35f));
        ant_r.addOrReplaceChild("ant_r_2", CubeListBuilder.create().texOffs(134, 30).mirror(true).addBox(-0.8f, -12, -0.8f, 1.6f, 12, 1.6f), PartPose.offsetAndRotation(0, -11.2f, 0, 0.9f, 0, 0.15f));
        PartDefinition arm_l = torso.addOrReplaceChild("arm_l", CubeListBuilder.create().texOffs(142, 30).mirror(false).addBox(-3, -2, -3, 6, 17, 6).texOffs(166, 30).mirror(false).addBox(-5, -6, -5, 10, 6, 10).texOffs(206, 30).mirror(false).addBox(-1, -12, -1, 2, 6, 2), PartPose.offsetAndRotation(-11, -26, 0, -0.3f, 0, -0.25f));
        PartDefinition arm_l_fore = arm_l.addOrReplaceChild("arm_l_fore", CubeListBuilder.create().texOffs(214, 30).mirror(false).addBox(-2.5f, 0, -2.5f, 5, 16, 5), PartPose.offsetAndRotation(0, 15, 0, -1.5f, 0, 0));
        arm_l_fore.addOrReplaceChild("arm_l_blade", CubeListBuilder.create().texOffs(234, 30).mirror(false).addBox(-1, 0, -2.5f, 2, 22, 5).texOffs(0, 57).mirror(false).addBox(-1, 18, -5, 2, 6, 3), PartPose.offsetAndRotation(0, 15, 0, -2.4f, 0, 0));
        PartDefinition arm_r = torso.addOrReplaceChild("arm_r", CubeListBuilder.create().texOffs(142, 30).mirror(true).addBox(-3, -2, -3, 6, 17, 6).texOffs(166, 30).mirror(true).addBox(-5, -6, -5, 10, 6, 10).texOffs(206, 30).mirror(true).addBox(-1, -12, -1, 2, 6, 2), PartPose.offsetAndRotation(11, -26, 0, -0.3f, 0, 0.25f));
        PartDefinition arm_r_fore = arm_r.addOrReplaceChild("arm_r_fore", CubeListBuilder.create().texOffs(214, 30).mirror(true).addBox(-2.5f, 0, -2.5f, 5, 16, 5), PartPose.offsetAndRotation(0, 15, 0, -1.5f, 0, 0));
        arm_r_fore.addOrReplaceChild("arm_r_blade", CubeListBuilder.create().texOffs(234, 30).mirror(true).addBox(-1, 0, -2.5f, 2, 22, 5).texOffs(0, 57).mirror(true).addBox(-1, 18, -5, 2, 6, 3), PartPose.offsetAndRotation(0, 15, 0, -2.4f, 0, 0));
        PartDefinition sarm_l = torso.addOrReplaceChild("sarm_l", CubeListBuilder.create().texOffs(10, 57).mirror(false).addBox(-2, 0, -2, 4, 12, 4), PartPose.offsetAndRotation(-7.6f, -14, -2, -0.9f, 0, -0.3f));
        sarm_l.addOrReplaceChild("sarm_l_pinch", CubeListBuilder.create().texOffs(26, 57).mirror(false).addBox(-2, 0, -1, 2, 6, 2).texOffs(34, 57).mirror(false).addBox(0.4f, 0, -1, 2, 6, 2), PartPose.offsetAndRotation(0, 11, 0, -0.4f, 0, 0));
        PartDefinition sarm_r = torso.addOrReplaceChild("sarm_r", CubeListBuilder.create().texOffs(10, 57).mirror(true).addBox(-2, 0, -2, 4, 12, 4), PartPose.offsetAndRotation(7.6f, -14, -2, -0.9f, 0, 0.3f));
        sarm_r.addOrReplaceChild("sarm_r_pinch", CubeListBuilder.create().texOffs(26, 57).mirror(true).addBox(-2, 0, -1, 2, 6, 2).texOffs(34, 57).mirror(true).addBox(0.4f, 0, -1, 2, 6, 2), PartPose.offsetAndRotation(0, 11, 0, -0.4f, 0, 0));
        torso.addOrReplaceChild("ely_l", CubeListBuilder.create().texOffs(42, 57).mirror(false).addBox(-5, 0, -1.5f, 10, 28, 3), PartPose.offsetAndRotation(-6, -30, 6.8f, 0.2f, -0.75f, -0.12f));
        torso.addOrReplaceChild("ely_r", CubeListBuilder.create().texOffs(42, 57).mirror(true).addBox(-5, 0, -1.5f, 10, 28, 3), PartPose.offsetAndRotation(6, -30, 6.8f, 0.2f, 0.75f, 0.12f));
        root.addOrReplaceChild("hips", CubeListBuilder.create().texOffs(68, 57).mirror(false).addBox(-7, 0, -5, 14, 12, 10).texOffs(116, 57).mirror(false).addBox(-5, 6, 4, 10, 12, 6), PartPose.ZERO);
        PartDefinition leg_l = root.addOrReplaceChild("leg_l", CubeListBuilder.create().texOffs(148, 57).mirror(false).addBox(-3.5f, 0, -3.5f, 7, 16, 7).texOffs(176, 57).mirror(false).addBox(-4, 2, -4.6f, 8, 6, 2), PartPose.offsetAndRotation(-6, 10, 0, -0.55f, 0, -0.06f));
        PartDefinition leg_l_shin = leg_l.addOrReplaceChild("leg_l_shin", CubeListBuilder.create().texOffs(196, 57).mirror(false).addBox(-2.5f, 0, -2.5f, 5, 18, 5), PartPose.offsetAndRotation(0, 15, 0, 1.05f, 0, 0));
        leg_l_shin.addOrReplaceChild("leg_l_foot", CubeListBuilder.create().texOffs(216, 57).mirror(false).addBox(-3, 0, -7, 6, 3, 10).texOffs(0, 88).mirror(false).addBox(-3, 1, -9, 2, 2, 3).texOffs(10, 88).mirror(false).addBox(1, 1, -9, 2, 2, 3), PartPose.offsetAndRotation(0, 16, 0, -0.5f, 0, 0));
        PartDefinition leg_r = root.addOrReplaceChild("leg_r", CubeListBuilder.create().texOffs(148, 57).mirror(true).addBox(-3.5f, 0, -3.5f, 7, 16, 7).texOffs(176, 57).mirror(true).addBox(-4, 2, -4.6f, 8, 6, 2), PartPose.offsetAndRotation(6, 10, 0, -0.55f, 0, 0.06f));
        PartDefinition leg_r_shin = leg_r.addOrReplaceChild("leg_r_shin", CubeListBuilder.create().texOffs(196, 57).mirror(true).addBox(-2.5f, 0, -2.5f, 5, 18, 5), PartPose.offsetAndRotation(0, 15, 0, 1.05f, 0, 0));
        leg_r_shin.addOrReplaceChild("leg_r_foot", CubeListBuilder.create().texOffs(216, 57).mirror(true).addBox(-3, 0, -7, 6, 3, 10).texOffs(0, 88).mirror(true).addBox(-3, 1, -9, 2, 2, 3).texOffs(10, 88).mirror(true).addBox(1, 1, -9, 2, 2, 3), PartPose.offsetAndRotation(0, 16, 0, -0.5f, 0, 0));
        return LayerDefinition.create(mesh, 256, 128);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(T neantide, float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        root.xScale = root.yScale = root.zScale = 0.5f;  // modèle dessiné à l'échelle 2 (haute résolution)
        root.y = 4.0f;                                   // jambes coudées : abaisse le modèle pour que les pieds touchent le sol
        float walk = limbSwing * 0.9f, amount = Math.min(1f, limbSwingAmount * 1.2f);

        // Démarche saccadée d'insecte : jambes coudées, corps qui tressaute par à-coups
        legL.xRot += Mth.cos(walk) * 0.7f * amount;
        legR.xRot += Mth.cos(walk + Mth.PI) * 0.7f * amount;
        shinL.xRot += Math.max(0f, Mth.sin(walk)) * 0.5f * amount;
        shinR.xRot += Math.max(0f, -Mth.sin(walk)) * 0.5f * amount;
        float jerk = (float) Math.floor(Mth.sin(walk * 2f) * 2f) * 0.5f;   // à-coups (valeurs discrètes)
        torso.zRot += jerk * 0.04f * amount;
        torso.y += Math.abs(jerk) * -0.8f * amount;

        // Tête : suit sa cible, petits mouvements nerveux
        head.yRot += netHeadYaw * Mth.DEG_TO_RAD * 0.7f;
        head.xRot += headPitch * Mth.DEG_TO_RAD * 0.5f;
        head.zRot += Mth.sin(age * 0.9f) * 0.02f;

        // Mandibules qui claquent, antennes qui frémissent
        float click = Math.abs(Mth.sin(age * 0.45f));
        mandTipL.yRot -= click * 0.35f;
        mandTipR.yRot += click * 0.35f;
        mandL.yRot -= click * 0.08f;
        mandR.yRot += click * 0.08f;
        antL.xRot += Mth.sin(age * 0.22f) * 0.1f;
        antR.xRot += Mth.sin(age * 0.22f + 1.1f) * 0.1f;
        antL.zRot -= Mth.sin(age * 0.37f) * 0.05f;
        antR.zRot += Mth.sin(age * 0.37f + 0.6f) * 0.05f;

        // Élytres qui vibrent ; elles s'ouvrent davantage quand il chasse
        float hunt = neantide.isAggressive() ? 1f : 0f;
        float buzz = Mth.sin(age * 2.6f) * (0.03f + 0.04f * hunt);
        elyL.yRot -= 0.3f * hunt + buzz;
        elyR.yRot += 0.3f * hunt + buzz;
        elyL.zRot -= buzz;
        elyR.zRot += buzz;

        // Bras-faux : légère respiration au repos ; FAUCHAGE EN CROIX à l'attaque (les faux se déplient d'un coup)
        armL.xRot += Mth.sin(age * 0.08f) * 0.04f;
        armR.xRot += Mth.sin(age * 0.08f + 1f) * 0.04f;
        // FAUCHAGE EN CROIX (0,8 s, animation propre au Néantide) : armement puis coup
        //   1re moitié : les faux se lèvent et se déplient au-dessus de la tête ; 2e moitié : elles s'abattent en se croisant
        float p = neantide.getAttackProgress(age - (int) age);
        if (p > 0f) {
            float raise = p < 0.45f ? Mth.sin(p / 0.45f * Mth.HALF_PI) : Mth.cos((p - 0.45f) / 0.55f * Mth.HALF_PI);
            float slash = p < 0.45f ? 0f : Mth.sin((p - 0.45f) / 0.55f * Mth.PI);
            armL.xRot -= raise * 1.6f;
            armR.xRot -= raise * 1.6f;
            foreL.xRot += raise * 1.2f;
            foreR.xRot += raise * 1.2f;
            bladeL.xRot += raise * 0.8f + slash * 0.6f;
            bladeR.xRot += raise * 0.8f + slash * 0.6f;
            armL.yRot -= slash * 0.7f;
            armR.yRot += slash * 0.7f;
            torso.xRot += slash * 0.3f - raise * 0.12f;
            mandTipL.yRot -= raise * 0.35f;
            mandTipR.yRot += raise * 0.35f;
        }
    }
}
