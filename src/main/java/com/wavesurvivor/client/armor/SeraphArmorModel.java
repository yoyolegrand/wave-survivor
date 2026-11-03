package com.wavesurvivor.client.armor;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/**
 * Armure du Séraphin — modèle 3D ORIGINAL (créé par Claude chat pour Wave Survivor).
 * Généré automatiquement depuis la géométrie de référence : ne pas éditer à la main les boîtes.
 * Texture : textures/models/armor/seraph_armor.png (512 x 128, 2 px/unité ; UV déclarées en 256 x 64).
 * Pièces : tête (heaume, ailes, halo), corps (plastron, tabard, écharpe, ailes dorsales),
 * bras (épaulières en plumes), jambes = "leggings" (cuissards, genouillères ailées) + "boots" (solerets).
 * Les tailles non entières sont obtenues par des boîtes entières + CubeDeformation, pour que le patron UV
 * tombe exactement sur la texture.
 */
public class SeraphArmorModel extends HumanoidModel<LivingEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation("wavesurvivor", "seraph_armor"), "main");
    public static final ResourceLocation TEXTURE = new ResourceLocation("wavesurvivor", "textures/models/armor/seraph_armor.png");

    private final ModelPart rightLeggings, leftLeggings, rightBoots, leftBoots;

    public SeraphArmorModel(ModelPart root) {
        super(root);
        this.rightLeggings = this.rightLeg.getChild("leggings");
        this.leftLeggings = this.leftLeg.getChild("leggings");
        this.rightBoots = this.rightLeg.getChild("boots");
        this.leftBoots = this.leftLeg.getChild("boots");
    }

    /** Affiche seulement les pièces de l'emplacement porté. */
    public SeraphArmorModel forSlot(EquipmentSlot slot) {
        this.setAllVisible(false);
        switch (slot) {
            case HEAD -> this.head.visible = true;
            case CHEST -> { this.body.visible = true; this.rightArm.visible = true; this.leftArm.visible = true; }
            case LEGS -> { this.rightLeg.visible = true; this.leftLeg.visible = true; rightLeggings.visible = true; leftLeggings.visible = true; rightBoots.visible = false; leftBoots.visible = false; }
            case FEET -> { this.rightLeg.visible = true; this.leftLeg.visible = true; rightLeggings.visible = false; leftLeggings.visible = false; rightBoots.visible = true; leftBoots.visible = true; }
            default -> {}
        }
        return this;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition armor_head = head.addOrReplaceChild("armor", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.5F, -8.5F, -4.5F, 9, 9, 9, new CubeDeformation(0F, 0F, 0F))
                .texOffs(36, 0).addBox(-1F, -10F, -4.5F, 2, 2, 9, new CubeDeformation(-0.25F, -0.4F, 0F))
                .texOffs(58, 0).addBox(-4.5F, -0.8F, -4F, 9, 2, 8, new CubeDeformation(-0.1F, -0.2F, 0F)), PartPose.ZERO);
        PartDefinition p_hwing_l = armor_head.addOrReplaceChild("hwing_l", CubeListBuilder.create()
                .texOffs(92, 0).addBox(-0.5F, -4.75F, -0.4F, 1, 5, 2, new CubeDeformation(-0.1F, -0.25F, -0.4F))
                .texOffs(98, 0).addBox(-0.5F, -6F, 0.8F, 1, 5, 2, new CubeDeformation(-0.1F, 0F, -0.4F))
                .texOffs(104, 0).addBox(-0.5F, -5.25F, 2F, 1, 4, 2, new CubeDeformation(-0.1F, -0.25F, -0.4F)),
                PartPose.offsetAndRotation(-4.8F, -4.5F, -1F, -0.5F, 0F, 0.25F));
        PartDefinition p_hwing_r = armor_head.addOrReplaceChild("hwing_r", CubeListBuilder.create()
                .texOffs(92, 0).mirror().addBox(-0.5F, -4.75F, -0.4F, 1, 5, 2, new CubeDeformation(-0.1F, -0.25F, -0.4F)).mirror(false)
                .texOffs(98, 0).mirror().addBox(-0.5F, -6F, 0.8F, 1, 5, 2, new CubeDeformation(-0.1F, 0F, -0.4F)).mirror(false)
                .texOffs(104, 0).mirror().addBox(-0.5F, -5.25F, 2F, 1, 4, 2, new CubeDeformation(-0.1F, -0.25F, -0.4F)).mirror(false),
                PartPose.offsetAndRotation(4.8F, -4.5F, -1F, -0.5F, 0F, -0.25F));
        PartDefinition p_halo = armor_head.addOrReplaceChild("halo", CubeListBuilder.create(),
                PartPose.offset(0F, -6F, 6.2F));
        PartDefinition p_hs0 = p_halo.addOrReplaceChild("hs0", CubeListBuilder.create()
                .texOffs(110, 0).addBox(-1.5F, -0.5F, -0.5F, 3, 1, 1, new CubeDeformation(-0.4F, 0F, -0.1F)),
                PartPose.offsetAndRotation(6.2F, 0F, 0F, 0F, 0F, 1.5708F));
        PartDefinition p_hs1 = p_halo.addOrReplaceChild("hs1", CubeListBuilder.create()
                .texOffs(110, 0).addBox(-1.5F, -0.5F, -0.5F, 3, 1, 1, new CubeDeformation(-0.4F, 0F, -0.1F)),
                PartPose.offsetAndRotation(5.586F, 2.69F, 0F, 0F, 0F, 2.0196F));
        PartDefinition p_hs2 = p_halo.addOrReplaceChild("hs2", CubeListBuilder.create()
                .texOffs(110, 0).addBox(-1.5F, -0.5F, -0.5F, 3, 1, 1, new CubeDeformation(-0.4F, 0F, -0.1F)),
                PartPose.offsetAndRotation(3.8658F, 4.8473F, 0F, 0F, 0F, 2.4684F));
        PartDefinition p_hs3 = p_halo.addOrReplaceChild("hs3", CubeListBuilder.create()
                .texOffs(110, 0).addBox(-1.5F, -0.5F, -0.5F, 3, 1, 1, new CubeDeformation(-0.4F, 0F, -0.1F)),
                PartPose.offsetAndRotation(1.3799F, 6.0445F, 0F, 0F, 0F, 2.9172F));
        PartDefinition p_hs4 = p_halo.addOrReplaceChild("hs4", CubeListBuilder.create()
                .texOffs(110, 0).addBox(-1.5F, -0.5F, -0.5F, 3, 1, 1, new CubeDeformation(-0.4F, 0F, -0.1F)),
                PartPose.offsetAndRotation(-1.3793F, 6.0446F, 0F, 0F, 0F, 3.3659F));
        PartDefinition p_hs5 = p_halo.addOrReplaceChild("hs5", CubeListBuilder.create()
                .texOffs(110, 0).addBox(-1.5F, -0.5F, -0.5F, 3, 1, 1, new CubeDeformation(-0.4F, 0F, -0.1F)),
                PartPose.offsetAndRotation(-3.8653F, 4.8476F, 0F, 0F, 0F, 3.8147F));
        PartDefinition p_hs6 = p_halo.addOrReplaceChild("hs6", CubeListBuilder.create()
                .texOffs(110, 0).addBox(-1.5F, -0.5F, -0.5F, 3, 1, 1, new CubeDeformation(-0.4F, 0F, -0.1F)),
                PartPose.offsetAndRotation(-5.5858F, 2.6905F, 0F, 0F, 0F, 4.2635F));
        PartDefinition p_hs7 = p_halo.addOrReplaceChild("hs7", CubeListBuilder.create()
                .texOffs(110, 0).addBox(-1.5F, -0.5F, -0.5F, 3, 1, 1, new CubeDeformation(-0.4F, 0F, -0.1F)),
                PartPose.offsetAndRotation(-6.2F, 0.0006F, 0F, 0F, 0F, 4.7123F));
        PartDefinition p_hs8 = p_halo.addOrReplaceChild("hs8", CubeListBuilder.create()
                .texOffs(110, 0).addBox(-1.5F, -0.5F, -0.5F, 3, 1, 1, new CubeDeformation(-0.4F, 0F, -0.1F)),
                PartPose.offsetAndRotation(-5.5863F, -2.6895F, 0F, 0F, 0F, 5.1611F));
        PartDefinition p_hs9 = p_halo.addOrReplaceChild("hs9", CubeListBuilder.create()
                .texOffs(110, 0).addBox(-1.5F, -0.5F, -0.5F, 3, 1, 1, new CubeDeformation(-0.4F, 0F, -0.1F)),
                PartPose.offsetAndRotation(-3.8662F, -4.8469F, 0F, 0F, 0F, 5.6099F));
        PartDefinition p_hs10 = p_halo.addOrReplaceChild("hs10", CubeListBuilder.create()
                .texOffs(110, 0).addBox(-1.5F, -0.5F, -0.5F, 3, 1, 1, new CubeDeformation(-0.4F, 0F, -0.1F)),
                PartPose.offsetAndRotation(-1.3804F, -6.0444F, 0F, 0F, 0F, 6.0587F));
        PartDefinition p_hs11 = p_halo.addOrReplaceChild("hs11", CubeListBuilder.create()
                .texOffs(110, 0).addBox(-1.5F, -0.5F, -0.5F, 3, 1, 1, new CubeDeformation(-0.4F, 0F, -0.1F)),
                PartPose.offsetAndRotation(1.3787F, -6.0448F, 0F, 0F, 0F, 6.5074F));
        PartDefinition p_hs12 = p_halo.addOrReplaceChild("hs12", CubeListBuilder.create()
                .texOffs(110, 0).addBox(-1.5F, -0.5F, -0.5F, 3, 1, 1, new CubeDeformation(-0.4F, 0F, -0.1F)),
                PartPose.offsetAndRotation(3.8649F, -4.848F, 0F, 0F, 0F, 6.9562F));
        PartDefinition p_hs13 = p_halo.addOrReplaceChild("hs13", CubeListBuilder.create()
                .texOffs(110, 0).addBox(-1.5F, -0.5F, -0.5F, 3, 1, 1, new CubeDeformation(-0.4F, 0F, -0.1F)),
                PartPose.offsetAndRotation(5.5855F, -2.691F, 0F, 0F, 0F, 7.405F));
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition armor_body = body.addOrReplaceChild("armor", CubeListBuilder.create()
                .texOffs(118, 0).addBox(-5F, -0.7F, -3F, 10, 8, 6, new CubeDeformation(-0.3F, -0.4F, -0.1F))
                .texOffs(150, 0).addBox(-5F, 6.55F, -3F, 10, 2, 6, new CubeDeformation(-0.25F, -0.25F, -0.15F))
                .texOffs(182, 0).addBox(-5F, 7.85F, -3F, 10, 2, 6, new CubeDeformation(-0.15F, -0.25F, -0.05F))
                .texOffs(214, 0).addBox(-5F, 9.15F, -3.5F, 10, 2, 7, new CubeDeformation(-0.05F, -0.35F, -0.45F))
                .texOffs(0, 18).addBox(-3F, 10.35F, -3.35F, 6, 9, 1, new CubeDeformation(0F, -0.25F, -0.25F))
                .texOffs(14, 18).addBox(-3.5F, 10.6F, 2.35F, 7, 8, 1, new CubeDeformation(0F, 0F, -0.25F))
                .texOffs(30, 18).addBox(-5.1F, -0.75F, -3.5F, 2, 11, 1, new CubeDeformation(-0.2F, -0.25F, -0.25F)), PartPose.ZERO);
        PartDefinition p_bwing_l = armor_body.addOrReplaceChild("bwing_l", CubeListBuilder.create()
                .texOffs(36, 18).addBox(-0.5F, 0F, -1F, 1, 9, 2, new CubeDeformation(0F, 0F, -0.4F))
                .texOffs(42, 18).addBox(-0.5F, 1F, 0.2F, 1, 7, 2, new CubeDeformation(-0.05F, 0F, -0.4F))
                .texOffs(48, 18).addBox(-0.5F, 2.15F, 0.2F, 1, 7, 2, new CubeDeformation(-0.05F, -0.45F, -0.4F))
                .texOffs(54, 18).addBox(-0.5F, 3.8F, 0.2F, 1, 6, 2, new CubeDeformation(-0.05F, -0.4F, -0.4F))
                .texOffs(60, 18).addBox(-0.5F, 5.45F, 0.2F, 1, 5, 2, new CubeDeformation(-0.05F, -0.35F, -0.4F))
                .texOffs(66, 18).addBox(-0.5F, 7.1F, 0.2F, 1, 4, 2, new CubeDeformation(-0.05F, -0.3F, -0.4F)),
                PartPose.offsetAndRotation(-2.5F, 1F, 3.2F, 0.25F, 0.35F, 0.35F));
        PartDefinition p_bwing_r = armor_body.addOrReplaceChild("bwing_r", CubeListBuilder.create()
                .texOffs(36, 18).mirror().addBox(-0.5F, 0F, -1F, 1, 9, 2, new CubeDeformation(0F, 0F, -0.4F)).mirror(false)
                .texOffs(42, 18).mirror().addBox(-0.5F, 1F, 0.2F, 1, 7, 2, new CubeDeformation(-0.05F, 0F, -0.4F)).mirror(false)
                .texOffs(48, 18).mirror().addBox(-0.5F, 2.15F, 0.2F, 1, 7, 2, new CubeDeformation(-0.05F, -0.45F, -0.4F)).mirror(false)
                .texOffs(54, 18).mirror().addBox(-0.5F, 3.8F, 0.2F, 1, 6, 2, new CubeDeformation(-0.05F, -0.4F, -0.4F)).mirror(false)
                .texOffs(60, 18).mirror().addBox(-0.5F, 5.45F, 0.2F, 1, 5, 2, new CubeDeformation(-0.05F, -0.35F, -0.4F)).mirror(false)
                .texOffs(66, 18).mirror().addBox(-0.5F, 7.1F, 0.2F, 1, 4, 2, new CubeDeformation(-0.05F, -0.3F, -0.4F)).mirror(false),
                PartPose.offsetAndRotation(2.5F, 1F, 3.2F, 0.25F, -0.35F, -0.35F));
        PartDefinition right_arm = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5.0F, 2.0F, 0.0F));
        PartDefinition armor_right_arm = right_arm.addOrReplaceChild("armor", CubeListBuilder.create()
                .texOffs(72, 18).addBox(-3.5F, 2F, -2.5F, 5, 4, 5, new CubeDeformation(-0.2F, 0F, -0.1F))
                .texOffs(92, 18).addBox(-4F, 5.4F, -3F, 6, 2, 6, new CubeDeformation(-0.4F, 0F, -0.3F))
                .texOffs(116, 18).addBox(-3.5F, 7.2F, -2.5F, 5, 3, 5, new CubeDeformation(-0.1F, 0F, -0.1F))
                .texOffs(136, 18).addBox(-4F, 8.95F, -3F, 6, 1, 6, new CubeDeformation(-0.3F, -0.05F, -0.3F))
                .texOffs(160, 18).addBox(-4F, 9.5F, -3F, 6, 3, 6, new CubeDeformation(-0.3F, -0.4F, -0.3F)), PartPose.ZERO);
        PartDefinition p_arm_r_p1 = armor_right_arm.addOrReplaceChild("arm_r_p1", CubeListBuilder.create()
                .texOffs(184, 18).addBox(-3.5F, -1.8F, -3.5F, 7, 3, 7, new CubeDeformation(-0.1F, 0F, -0.2F)),
                PartPose.offsetAndRotation(-1F, -0.8F, 0F, 0F, 0F, 0.2F));
        PartDefinition p_arm_r_p2 = armor_right_arm.addOrReplaceChild("arm_r_p2", CubeListBuilder.create()
                .texOffs(212, 18).addBox(-3.5F, -0.2F, -3.5F, 7, 2, 7, new CubeDeformation(-0.3F, -0.2F, -0.4F)),
                PartPose.offsetAndRotation(-1.3F, 1F, 0F, 0F, 0F, 0.32F));
        PartDefinition p_arm_r_p3 = armor_right_arm.addOrReplaceChild("arm_r_p3", CubeListBuilder.create()
                .texOffs(0, 30).addBox(-3F, -0.25F, -3F, 6, 2, 6, new CubeDeformation(0F, -0.25F, -0.1F)),
                PartPose.offsetAndRotation(-1.6F, 2.3F, 0F, 0F, 0F, 0.42F));
        PartDefinition left_arm = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5.0F, 2.0F, 0.0F));
        PartDefinition armor_left_arm = left_arm.addOrReplaceChild("armor", CubeListBuilder.create()
                .texOffs(72, 18).mirror().addBox(-1.5F, 2F, -2.5F, 5, 4, 5, new CubeDeformation(-0.2F, 0F, -0.1F)).mirror(false)
                .texOffs(92, 18).mirror().addBox(-2F, 5.4F, -3F, 6, 2, 6, new CubeDeformation(-0.4F, 0F, -0.3F)).mirror(false)
                .texOffs(116, 18).mirror().addBox(-1.5F, 7.2F, -2.5F, 5, 3, 5, new CubeDeformation(-0.1F, 0F, -0.1F)).mirror(false)
                .texOffs(136, 18).mirror().addBox(-2F, 8.95F, -3F, 6, 1, 6, new CubeDeformation(-0.3F, -0.05F, -0.3F)).mirror(false)
                .texOffs(160, 18).mirror().addBox(-2F, 9.5F, -3F, 6, 3, 6, new CubeDeformation(-0.3F, -0.4F, -0.3F)).mirror(false), PartPose.ZERO);
        PartDefinition p_arm_l_p1 = armor_left_arm.addOrReplaceChild("arm_l_p1", CubeListBuilder.create()
                .texOffs(184, 18).mirror().addBox(-3.5F, -1.8F, -3.5F, 7, 3, 7, new CubeDeformation(-0.1F, 0F, -0.2F)).mirror(false),
                PartPose.offsetAndRotation(1F, -0.8F, 0F, 0F, 0F, -0.2F));
        PartDefinition p_arm_l_p2 = armor_left_arm.addOrReplaceChild("arm_l_p2", CubeListBuilder.create()
                .texOffs(212, 18).mirror().addBox(-3.5F, -0.2F, -3.5F, 7, 2, 7, new CubeDeformation(-0.3F, -0.2F, -0.4F)).mirror(false),
                PartPose.offsetAndRotation(1.3F, 1F, 0F, 0F, 0F, -0.32F));
        PartDefinition p_arm_l_p3 = armor_left_arm.addOrReplaceChild("arm_l_p3", CubeListBuilder.create()
                .texOffs(0, 30).mirror().addBox(-3F, -0.25F, -3F, 6, 2, 6, new CubeDeformation(0F, -0.25F, -0.1F)).mirror(false),
                PartPose.offsetAndRotation(1.6F, 2.3F, 0F, 0F, 0F, -0.42F));
        PartDefinition right_leg = root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F));
        PartDefinition leggings_r = right_leg.addOrReplaceChild("leggings", CubeListBuilder.create()
                .texOffs(24, 30).addBox(-2.5F, -0.4F, -2.5F, 5, 6, 5, new CubeDeformation(0F, -0.4F, 0F))
                .texOffs(44, 30).addBox(-3F, 4.65F, -3.15F, 6, 3, 6, new CubeDeformation(-0.35F, -0.35F, -0.2F))
                .texOffs(68, 30).addBox(-2.5F, 6.8F, -2.5F, 5, 4, 5, new CubeDeformation(-0.1F, -0.4F, -0.1F)), PartPose.ZERO);
        PartDefinition p_leg_r_kw = leggings_r.addOrReplaceChild("leg_r_kw", CubeListBuilder.create()
                .texOffs(140, 30).addBox(-0.5F, -2.4F, -0.5F, 1, 3, 2, new CubeDeformation(-0.2F, -0.2F, -0.2F))
                .texOffs(146, 30).addBox(-0.5F, -1.6F, 0.8F, 1, 2, 2, new CubeDeformation(-0.2F, 0F, -0.3F)),
                PartPose.offsetAndRotation(-2.65F, 6.1F, -2.2F, -0.6F, 0F, -0.35F));
        right_leg.addOrReplaceChild("boots", CubeListBuilder.create()
                .texOffs(88, 30).addBox(-3F, 9.45F, -3F, 6, 3, 6, new CubeDeformation(-0.4F, -0.35F, -0.3F))
                .texOffs(112, 30).addBox(-2.5F, 10.35F, -4.2F, 5, 2, 2, new CubeDeformation(0F, -0.25F, -0.3F))
                .texOffs(126, 30).addBox(-2.5F, 10.55F, -5.35F, 5, 2, 2, new CubeDeformation(-0.2F, -0.45F, -0.45F)), PartPose.ZERO);
        PartDefinition left_leg = root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F));
        PartDefinition leggings_l = left_leg.addOrReplaceChild("leggings", CubeListBuilder.create()
                .texOffs(24, 30).mirror().addBox(-2.5F, -0.4F, -2.5F, 5, 6, 5, new CubeDeformation(0F, -0.4F, 0F)).mirror(false)
                .texOffs(44, 30).mirror().addBox(-3F, 4.65F, -3.15F, 6, 3, 6, new CubeDeformation(-0.35F, -0.35F, -0.2F)).mirror(false)
                .texOffs(68, 30).mirror().addBox(-2.5F, 6.8F, -2.5F, 5, 4, 5, new CubeDeformation(-0.1F, -0.4F, -0.1F)).mirror(false), PartPose.ZERO);
        PartDefinition p_leg_l_kw = leggings_l.addOrReplaceChild("leg_l_kw", CubeListBuilder.create()
                .texOffs(140, 30).mirror().addBox(-0.5F, -2.4F, -0.5F, 1, 3, 2, new CubeDeformation(-0.2F, -0.2F, -0.2F)).mirror(false)
                .texOffs(146, 30).mirror().addBox(-0.5F, -1.6F, 0.8F, 1, 2, 2, new CubeDeformation(-0.2F, 0F, -0.3F)).mirror(false),
                PartPose.offsetAndRotation(2.65F, 6.1F, -2.2F, -0.6F, 0F, 0.35F));
        left_leg.addOrReplaceChild("boots", CubeListBuilder.create()
                .texOffs(88, 30).mirror().addBox(-3F, 9.45F, -3F, 6, 3, 6, new CubeDeformation(-0.4F, -0.35F, -0.3F)).mirror(false)
                .texOffs(112, 30).mirror().addBox(-2.5F, 10.35F, -4.2F, 5, 2, 2, new CubeDeformation(0F, -0.25F, -0.3F)).mirror(false)
                .texOffs(126, 30).mirror().addBox(-2.5F, 10.55F, -5.35F, 5, 2, 2, new CubeDeformation(-0.2F, -0.45F, -0.45F)).mirror(false), PartPose.ZERO);
        return LayerDefinition.create(mesh, 256, 64);
    }
}
