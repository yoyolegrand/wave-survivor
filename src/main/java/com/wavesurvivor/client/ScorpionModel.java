package com.wavesurvivor.client;

import com.wavesurvivor.entity.Scorpion;
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
 * MODÈLE DU SCORPION (conçu sur mesure) : carapace ambre en anneaux, QUEUE de 5 segments dressée et recourbée
 * au-dessus du dos (vésicule + dard), 2 PINCES articulées (bras, avant-bras, pince, doigt mobile), 8 pattes, yeux.
 * Texture 128×64. Animations : pattes en alternance, queue qui ondule, pinces qui claquent, COUP DE DARD en attaque.
 */
@OnlyIn(Dist.CLIENT)
public class ScorpionModel<T extends Scorpion> extends HierarchicalModel<T> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation("wavesurvivor", "scorpion"), "main");

    private final ModelPart root, body;
    private final ModelPart[] legs = new ModelPart[8];
    private final ModelPart[] tail = new ModelPart[5];
    private final ModelPart clawR, clawL, fingerR, fingerL;
    private static final String[] LEGS = {"leg_r1", "leg_r2", "leg_r3", "leg_r4", "leg_l1", "leg_l2", "leg_l3", "leg_l4"};

    public ScorpionModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        for (int i = 0; i < 8; i++) legs[i] = body.getChild(LEGS[i]);
        ModelPart t = body.getChild("tail1");
        tail[0] = t;
        for (int i = 1; i < 5; i++) { t = t.getChild("tail" + (i + 1)); tail[i] = t; }
        this.clawR = body.getChild("claw_r");
        this.clawL = body.getChild("claw_l");
        this.fingerR = clawR.getChild("claw_r_fore").getChild("claw_r_hand").getChild("claw_r_finger");
        this.fingerL = clawL.getChild("claw_l_fore").getChild("claw_l_hand").getChild("claw_l_finger");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0, 24, 0));
        PartDefinition cephalo = body.addOrReplaceChild("cephalo", CubeListBuilder.create().texOffs(0, 0).mirror(false).addBox(-4, -6, -8, 8, 4, 7), PartPose.ZERO);
        cephalo.addOrReplaceChild("eyes", CubeListBuilder.create().texOffs(0, 36).mirror(false).addBox(-1.5f, -6.6f, -7, 1, 1, 1).texOffs(0, 36).mirror(false).addBox(0.5f, -6.6f, -7, 1, 1, 1), PartPose.ZERO);
        body.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(30, 0).mirror(false).addBox(-4.5f, -6.5f, -1, 9, 4, 5), PartPose.ZERO);
        body.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(58, 0).mirror(false).addBox(-4, -6, 4, 8, 4, 4), PartPose.ZERO);
        body.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(82, 0).mirror(false).addBox(-3.5f, -5.5f, 8, 7, 3, 4), PartPose.ZERO);
        PartDefinition tail1 = body.addOrReplaceChild("tail1", CubeListBuilder.create().texOffs(0, 12).mirror(false).addBox(-1.5f, -1.5f, 0, 3, 3, 4), PartPose.offsetAndRotation(0, -4.5f, 11.5f, 0.9f, 0, 0));
        PartDefinition tail2 = tail1.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(0, 12).mirror(false).addBox(-1.5f, -1.5f, 0, 3, 3, 4), PartPose.offsetAndRotation(0, 0, 3.6f, 0.7f, 0, 0));
        PartDefinition tail3 = tail2.addOrReplaceChild("tail3", CubeListBuilder.create().texOffs(0, 12).mirror(false).addBox(-1.5f, -1.5f, 0, 3, 3, 4), PartPose.offsetAndRotation(0, 0, 3.6f, 0.7f, 0, 0));
        PartDefinition tail4 = tail3.addOrReplaceChild("tail4", CubeListBuilder.create().texOffs(14, 12).mirror(false).addBox(-1, -1, 0, 2, 2, 4), PartPose.offsetAndRotation(0, 0, 3.6f, 0.6f, 0, 0));
        PartDefinition tail5 = tail4.addOrReplaceChild("tail5", CubeListBuilder.create().texOffs(26, 12).mirror(false).addBox(-1, -1, 0, 2, 2, 3), PartPose.offsetAndRotation(0, 0, 3.6f, 0.6f, 0, 0));
        PartDefinition bulb = tail5.addOrReplaceChild("bulb", CubeListBuilder.create().texOffs(36, 12).mirror(false).addBox(-1.5f, -1.5f, 0, 3, 3, 3), PartPose.offsetAndRotation(0, 0, 2.6f, 0.7f, 0, 0));
        bulb.addOrReplaceChild("needle", CubeListBuilder.create().texOffs(48, 12).mirror(false).addBox(-0.5f, -0.5f, 0, 1, 1, 3), PartPose.offsetAndRotation(0, -0.6f, 2.6f, 1.2f, 0, 0));
        PartDefinition claw_r = body.addOrReplaceChild("claw_r", CubeListBuilder.create().texOffs(0, 20).mirror(false).addBox(-1, -1, -5, 2, 2, 5), PartPose.offsetAndRotation(3.5f, -4, -7, 0.15f, -0.55f, 0));
        PartDefinition claw_r_fore = claw_r.addOrReplaceChild("claw_r_fore", CubeListBuilder.create().texOffs(14, 20).mirror(false).addBox(-1, -1, -4, 2, 2, 4), PartPose.offsetAndRotation(0, 0, -4.5f, 0, 0.95f, 0));
        PartDefinition claw_r_hand = claw_r_fore.addOrReplaceChild("claw_r_hand", CubeListBuilder.create().texOffs(26, 20).mirror(false).addBox(-2, -1.5f, -4, 4, 3, 4).texOffs(42, 20).mirror(false).addBox(-2, -1, -8, 1, 2, 4), PartPose.offset(0, 0, -3.5f));
        claw_r_hand.addOrReplaceChild("claw_r_finger", CubeListBuilder.create().texOffs(42, 20).mirror(false).addBox(-0.5f, -1, -4, 1, 2, 4), PartPose.offsetAndRotation(1.5f, 0, -3.5f, 0, 0.25f, 0));
        PartDefinition claw_l = body.addOrReplaceChild("claw_l", CubeListBuilder.create().texOffs(0, 20).mirror(true).addBox(-1, -1, -5, 2, 2, 5), PartPose.offsetAndRotation(-3.5f, -4, -7, 0.15f, 0.55f, 0));
        PartDefinition claw_l_fore = claw_l.addOrReplaceChild("claw_l_fore", CubeListBuilder.create().texOffs(14, 20).mirror(true).addBox(-1, -1, -4, 2, 2, 4), PartPose.offsetAndRotation(0, 0, -4.5f, 0, -0.95f, 0));
        PartDefinition claw_l_hand = claw_l_fore.addOrReplaceChild("claw_l_hand", CubeListBuilder.create().texOffs(26, 20).mirror(true).addBox(-2, -1.5f, -4, 4, 3, 4).texOffs(42, 20).mirror(true).addBox(1, -1, -8, 1, 2, 4), PartPose.offset(0, 0, -3.5f));
        claw_l_hand.addOrReplaceChild("claw_l_finger", CubeListBuilder.create().texOffs(42, 20).mirror(true).addBox(-0.5f, -1, -4, 1, 2, 4), PartPose.offsetAndRotation(-1.5f, 0, -3.5f, 0, -0.25f, 0));
        PartDefinition leg_r1 = body.addOrReplaceChild("leg_r1", CubeListBuilder.create().texOffs(0, 30).mirror(false).addBox(0, -1, -1, 6, 2, 2), PartPose.offsetAndRotation(3.8f, -4, -6, 0, 0.6f, -0.55f));
        leg_r1.addOrReplaceChild("leg_r1_lo", CubeListBuilder.create().texOffs(16, 30).mirror(false).addBox(0, -0.5f, -0.5f, 6, 1, 1), PartPose.offsetAndRotation(5.6f, 0, 0, 0, 0, 1.35f));
        PartDefinition leg_r2 = body.addOrReplaceChild("leg_r2", CubeListBuilder.create().texOffs(0, 30).mirror(false).addBox(0, -1, -1, 6, 2, 2), PartPose.offsetAndRotation(3.8f, -4, -4, 0, 0.2f, -0.55f));
        leg_r2.addOrReplaceChild("leg_r2_lo", CubeListBuilder.create().texOffs(16, 30).mirror(false).addBox(0, -0.5f, -0.5f, 6, 1, 1), PartPose.offsetAndRotation(5.6f, 0, 0, 0, 0, 1.35f));
        PartDefinition leg_r3 = body.addOrReplaceChild("leg_r3", CubeListBuilder.create().texOffs(0, 30).mirror(false).addBox(0, -1, -1, 6, 2, 2), PartPose.offsetAndRotation(3.8f, -4, -2, 0, -0.2f, -0.55f));
        leg_r3.addOrReplaceChild("leg_r3_lo", CubeListBuilder.create().texOffs(16, 30).mirror(false).addBox(0, -0.5f, -0.5f, 6, 1, 1), PartPose.offsetAndRotation(5.6f, 0, 0, 0, 0, 1.35f));
        PartDefinition leg_r4 = body.addOrReplaceChild("leg_r4", CubeListBuilder.create().texOffs(0, 30).mirror(false).addBox(0, -1, -1, 6, 2, 2), PartPose.offsetAndRotation(3.8f, -4, 0, 0, -0.6f, -0.55f));
        leg_r4.addOrReplaceChild("leg_r4_lo", CubeListBuilder.create().texOffs(16, 30).mirror(false).addBox(0, -0.5f, -0.5f, 6, 1, 1), PartPose.offsetAndRotation(5.6f, 0, 0, 0, 0, 1.35f));
        PartDefinition leg_l1 = body.addOrReplaceChild("leg_l1", CubeListBuilder.create().texOffs(0, 30).mirror(true).addBox(-6, -1, -1, 6, 2, 2), PartPose.offsetAndRotation(-3.8f, -4, -6, 0, -0.6f, 0.55f));
        leg_l1.addOrReplaceChild("leg_l1_lo", CubeListBuilder.create().texOffs(16, 30).mirror(true).addBox(-6, -0.5f, -0.5f, 6, 1, 1), PartPose.offsetAndRotation(-5.6f, 0, 0, 0, 0, -1.35f));
        PartDefinition leg_l2 = body.addOrReplaceChild("leg_l2", CubeListBuilder.create().texOffs(0, 30).mirror(true).addBox(-6, -1, -1, 6, 2, 2), PartPose.offsetAndRotation(-3.8f, -4, -4, 0, -0.2f, 0.55f));
        leg_l2.addOrReplaceChild("leg_l2_lo", CubeListBuilder.create().texOffs(16, 30).mirror(true).addBox(-6, -0.5f, -0.5f, 6, 1, 1), PartPose.offsetAndRotation(-5.6f, 0, 0, 0, 0, -1.35f));
        PartDefinition leg_l3 = body.addOrReplaceChild("leg_l3", CubeListBuilder.create().texOffs(0, 30).mirror(true).addBox(-6, -1, -1, 6, 2, 2), PartPose.offsetAndRotation(-3.8f, -4, -2, 0, 0.2f, 0.55f));
        leg_l3.addOrReplaceChild("leg_l3_lo", CubeListBuilder.create().texOffs(16, 30).mirror(true).addBox(-6, -0.5f, -0.5f, 6, 1, 1), PartPose.offsetAndRotation(-5.6f, 0, 0, 0, 0, -1.35f));
        PartDefinition leg_l4 = body.addOrReplaceChild("leg_l4", CubeListBuilder.create().texOffs(0, 30).mirror(true).addBox(-6, -1, -1, 6, 2, 2), PartPose.offsetAndRotation(-3.8f, -4, 0, 0, 0.6f, 0.55f));
        leg_l4.addOrReplaceChild("leg_l4_lo", CubeListBuilder.create().texOffs(16, 30).mirror(true).addBox(-6, -0.5f, -0.5f, 6, 1, 1), PartPose.offsetAndRotation(-5.6f, 0, 0, 0, 0, -1.35f));
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(T scorpion, float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        float speed = limbSwing * 1.3f, amount = Math.min(1f, limbSwingAmount * 1.5f);
        // Pattes : chaque paire décalée, côtés opposés en alternance
        for (int i = 0; i < 8; i++) {
            float side = i < 4 ? 1f : -1f;
            float phase = (i % 4) * (Mth.PI / 2f) + (side > 0 ? 0f : Mth.PI);
            legs[i].yRot += Mth.cos(speed + phase) * 0.4f * amount * side;
            legs[i].zRot += -side * Math.max(0f, Mth.sin(speed + phase)) * 0.3f * amount;
        }
        // Queue : ondule en permanence ; COUP DE DARD pendant l'attaque (la queue se recourbe vers l'avant)
        float strike = Mth.sin(Mth.sqrt(attackTime) * Mth.PI);
        for (int i = 0; i < 5; i++) {
            tail[i].xRot += Mth.sin(age * 0.1f - i * 0.5f) * 0.05f + strike * (i < 3 ? 0.32f : 0.15f);
            tail[i].zRot += Mth.sin(age * 0.07f - i * 0.4f) * 0.04f;
        }
        // Pinces : claquent doucement, s'avancent pendant l'attaque
        float pinch = 0.15f + Math.abs(Mth.sin(age * 0.18f)) * 0.3f;
        fingerR.yRot += pinch;
        fingerL.yRot -= pinch;
        clawR.yRot += Mth.sin(age * 0.05f) * 0.06f - strike * 0.2f;
        clawL.yRot -= Mth.sin(age * 0.05f + 1f) * 0.06f - strike * 0.2f;
        clawR.xRot -= strike * 0.25f;
        clawL.xRot -= strike * 0.25f;
    }
}
