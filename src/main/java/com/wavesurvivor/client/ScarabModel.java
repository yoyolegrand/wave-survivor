package com.wavesurvivor.client;

import com.wavesurvivor.entity.Scarab;
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
 * MODÈLE DU SCARABÉE (conçu sur mesure) : carapace bombée en deux élytres bleu lapis bordées d'or, thorax doré,
 * tête en râteau avec antennes, 6 pattes en deux segments. Texture 64×64.
 * Animations : marche en trépied (3 pattes avancent pendant que les 3 autres poussent), antennes qui frémissent.
 */
@OnlyIn(Dist.CLIENT)
public class ScarabModel<T extends Scarab> extends HierarchicalModel<T> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation("wavesurvivor", "scarab"), "main");

    private final ModelPart root, body, antL, antR;
    private final ModelPart[] legs = new ModelPart[6];
    private static final String[] LEGS = {"leg_r1", "leg_l2", "leg_r3", "leg_l1", "leg_r2", "leg_l3"}; // 2 trépieds

    public ScarabModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        ModelPart head = body.getChild("head");
        this.antL = head.getChild("ant_l");
        this.antR = head.getChild("ant_r");
        for (int i = 0; i < 6; i++) legs[i] = body.getChild(LEGS[i]);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0, 24, 0));
        body.addOrReplaceChild("abdomen", CubeListBuilder.create().texOffs(0, 0).mirror(false).addBox(-3.5f, -4, -1, 7, 2, 8), PartPose.ZERO);
        body.addOrReplaceChild("shell_l", CubeListBuilder.create().texOffs(0, 10).mirror(false).addBox(-5, -6.5f, -2, 5, 3, 10).texOffs(0, 23).mirror(false).addBox(-4, -7.5f, -1, 4, 1, 8), PartPose.ZERO);
        body.addOrReplaceChild("shell_r", CubeListBuilder.create().texOffs(30, 10).mirror(false).addBox(0, -6.5f, -2, 5, 3, 10).texOffs(24, 23).mirror(false).addBox(0, -7.5f, -1, 4, 1, 8), PartPose.ZERO);
        body.addOrReplaceChild("thorax", CubeListBuilder.create().texOffs(30, 0).mirror(false).addBox(-3, -5.5f, -5, 6, 3, 4), PartPose.ZERO);
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(50, 0).mirror(false).addBox(-2, -4.5f, -8, 4, 2, 3).texOffs(50, 6).mirror(false).addBox(-2.5f, -3.5f, -9, 5, 1, 1), PartPose.ZERO);
        head.addOrReplaceChild("ant_l", CubeListBuilder.create().texOffs(0, 40).mirror(false).addBox(0, -0.5f, -2, 1, 1, 2), PartPose.offsetAndRotation(-2, -4.6f, -8, -0.5f, 0.4f, 0));
        head.addOrReplaceChild("ant_r", CubeListBuilder.create().texOffs(0, 40).mirror(true).addBox(-1, -0.5f, -2, 1, 1, 2), PartPose.offsetAndRotation(2, -4.6f, -8, -0.5f, -0.4f, 0));
        PartDefinition leg_r1 = body.addOrReplaceChild("leg_r1", CubeListBuilder.create().texOffs(0, 44).mirror(false).addBox(0, -0.5f, -0.5f, 3, 1, 1), PartPose.offsetAndRotation(3.6f, -3.6f, -3.5f, 0, 0.5f, -0.35f));
        leg_r1.addOrReplaceChild("leg_r1_lo", CubeListBuilder.create().texOffs(10, 44).mirror(false).addBox(0, -0.5f, -0.5f, 4, 1, 1), PartPose.offsetAndRotation(3, 0, 0, 0, 0, 1.15f));
        PartDefinition leg_r2 = body.addOrReplaceChild("leg_r2", CubeListBuilder.create().texOffs(0, 44).mirror(false).addBox(0, -0.5f, -0.5f, 3, 1, 1), PartPose.offsetAndRotation(3.6f, -3.6f, -0.8f, 0, 0, -0.35f));
        leg_r2.addOrReplaceChild("leg_r2_lo", CubeListBuilder.create().texOffs(10, 44).mirror(false).addBox(0, -0.5f, -0.5f, 4, 1, 1), PartPose.offsetAndRotation(3, 0, 0, 0, 0, 1.15f));
        PartDefinition leg_r3 = body.addOrReplaceChild("leg_r3", CubeListBuilder.create().texOffs(0, 44).mirror(false).addBox(0, -0.5f, -0.5f, 3, 1, 1), PartPose.offsetAndRotation(3.6f, -3.6f, 2.2f, 0, -0.5f, -0.35f));
        leg_r3.addOrReplaceChild("leg_r3_lo", CubeListBuilder.create().texOffs(10, 44).mirror(false).addBox(0, -0.5f, -0.5f, 4, 1, 1), PartPose.offsetAndRotation(3, 0, 0, 0, 0, 1.15f));
        PartDefinition leg_l1 = body.addOrReplaceChild("leg_l1", CubeListBuilder.create().texOffs(0, 44).mirror(true).addBox(-3, -0.5f, -0.5f, 3, 1, 1), PartPose.offsetAndRotation(-3.6f, -3.6f, -3.5f, 0, -0.5f, 0.35f));
        leg_l1.addOrReplaceChild("leg_l1_lo", CubeListBuilder.create().texOffs(10, 44).mirror(true).addBox(-4, -0.5f, -0.5f, 4, 1, 1), PartPose.offsetAndRotation(-3, 0, 0, 0, 0, -1.15f));
        PartDefinition leg_l2 = body.addOrReplaceChild("leg_l2", CubeListBuilder.create().texOffs(0, 44).mirror(true).addBox(-3, -0.5f, -0.5f, 3, 1, 1), PartPose.offsetAndRotation(-3.6f, -3.6f, -0.8f, 0, 0, 0.35f));
        leg_l2.addOrReplaceChild("leg_l2_lo", CubeListBuilder.create().texOffs(10, 44).mirror(true).addBox(-4, -0.5f, -0.5f, 4, 1, 1), PartPose.offsetAndRotation(-3, 0, 0, 0, 0, -1.15f));
        PartDefinition leg_l3 = body.addOrReplaceChild("leg_l3", CubeListBuilder.create().texOffs(0, 44).mirror(true).addBox(-3, -0.5f, -0.5f, 3, 1, 1), PartPose.offsetAndRotation(-3.6f, -3.6f, 2.2f, 0, 0.5f, 0.35f));
        leg_l3.addOrReplaceChild("leg_l3_lo", CubeListBuilder.create().texOffs(10, 44).mirror(true).addBox(-4, -0.5f, -0.5f, 4, 1, 1), PartPose.offsetAndRotation(-3, 0, 0, 0, 0, -1.15f));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(T scarab, float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        float speed = limbSwing * 1.6f, amount = Math.min(1f, limbSwingAmount * 1.6f);
        for (int i = 0; i < 6; i++) {
            float phase = i < 3 ? 0f : Mth.PI;           // trépied A puis B
            float side = LEGS[i].contains("_r") ? 1f : -1f;
            legs[i].yRot += Mth.cos(speed + phase) * 0.55f * amount * side;
            legs[i].zRot += -side * Math.max(0f, Mth.sin(speed + phase)) * 0.35f * amount;   // la patte se lève en avançant
        }
        body.y += Mth.sin(speed * 2f) * 0.25f * amount;   // petit balancement en courant
        antL.xRot += Mth.sin(age * 0.35f) * 0.12f;
        antR.xRot += Mth.sin(age * 0.35f + 1.3f) * 0.12f;
        antL.yRot += Mth.sin(age * 0.21f) * 0.08f;
        antR.yRot -= Mth.sin(age * 0.21f + 0.7f) * 0.08f;
    }
}
