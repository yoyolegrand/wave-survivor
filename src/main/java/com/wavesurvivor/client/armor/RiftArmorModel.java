package com.wavesurvivor.client.armor;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 2.1 — Armure de la Faille — modèle 3D ORIGINAL et ÉVOLUTIF (créé par Claude chat pour Wave Survivor).
 * Généré automatiquement depuis la géométrie de référence (docs/armure_faille/source/rift3d.py) : ne pas éditer à la main les boîtes.
 * Un seul modèle pour toutes les apparences : {@link #forStage} montre les pièces du palier (I à III) et de la branche
 * (base, bénie, corrompue). Textures HD (1024 x 256, 4 px/unité ; UV déclarées en 256 x 64), mêmes UV pour toutes :
 *   palier I  : rift_armor_cracked.png (cristaux éteints, pas de calque lumineux)
 *   palier II/III : rift_armor.png + rift_armor_glow.png
 *   bénie     : rift_armor_blessed.png + rift_armor_blessed_glow.png (couronne d'éclats flottants)
 *   corrompue : rift_armor_corrupted.png + rift_armor_corrupted_glow.png (grandes cornes, épines dorsales)
 * Pièces : tête (heaume à visière fermée en T, crête, cornes), corps (plastron, pectoraux en V, fissure, bandes, ceinture,
 * tassettes, éclats dorsaux), bras (épaulières à pointes, brassards, aileron), jambes = "leggings" (cuissards, genouillères
 * à pointe) + "boots" (solerets, cristal de cheville).
 */
public class RiftArmorModel extends HumanoidModel<LivingEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation("wavesurvivor", "rift_armor"), "main");

    /** Branche de l'armure : BASE (paliers I à III), BLESSED (bénie), CORRUPTED (corrompue). */
    public enum Branch { BASE, BLESSED, CORRUPTED }

    /** Sous-partie qui n'apparaît qu'à partir d'un palier et/ou pour certaines branches (masque : 1 base, 2 bénie, 4 corrompue). */
    private record Staged(String[] path, int minTier, int branchMask) {}
    private static final Staged[] STAGED = {
            new Staged(new String[]{"head", "armor", "crest0"}, 3, 7),
            new Staged(new String[]{"head", "armor", "crest1"}, 2, 7),
            new Staged(new String[]{"head", "armor", "crest2"}, 3, 7),
            new Staged(new String[]{"head", "armor", "horn_r"}, 3, 3),
            new Staged(new String[]{"head", "armor", "horn_l"}, 3, 3),
            new Staged(new String[]{"head", "armor", "crown"}, 1, 2),
            new Staged(new String[]{"head", "armor", "crown", "cr0"}, 1, 2),
            new Staged(new String[]{"head", "armor", "crown", "cr1"}, 1, 2),
            new Staged(new String[]{"head", "armor", "crown", "cr2"}, 1, 2),
            new Staged(new String[]{"head", "armor", "crown", "cr3"}, 1, 2),
            new Staged(new String[]{"head", "armor", "crown", "cr4"}, 1, 2),
            new Staged(new String[]{"head", "armor", "crown", "cr5"}, 1, 2),
            new Staged(new String[]{"head", "armor", "crown", "cr6"}, 1, 2),
            new Staged(new String[]{"head", "armor", "chorn_r"}, 1, 4),
            new Staged(new String[]{"head", "armor", "chorn_r", "chorn_r2"}, 1, 4),
            new Staged(new String[]{"head", "armor", "chorn_l"}, 1, 4),
            new Staged(new String[]{"head", "armor", "chorn_l", "chorn_l2"}, 1, 4),
            new Staged(new String[]{"body", "armor", "pec_r"}, 2, 7),
            new Staged(new String[]{"body", "armor", "pec_l"}, 2, 7),
            new Staged(new String[]{"body", "armor", "shard_r"}, 3, 7),
            new Staged(new String[]{"body", "armor", "shard_l"}, 3, 7),
            new Staged(new String[]{"body", "armor", "spine0"}, 1, 4),
            new Staged(new String[]{"body", "armor", "spine1"}, 1, 4),
            new Staged(new String[]{"body", "armor", "spine2"}, 1, 4),
            new Staged(new String[]{"right_arm", "armor", "pd2_r"}, 2, 7),
            new Staged(new String[]{"right_arm", "armor", "spk1_r"}, 2, 7),
            new Staged(new String[]{"right_arm", "armor", "spk2_r"}, 3, 7),
            new Staged(new String[]{"right_arm", "armor", "spk3_r"}, 3, 7),
            new Staged(new String[]{"right_arm", "armor", "fin_r"}, 3, 7),
            new Staged(new String[]{"left_arm", "armor", "pd2_l"}, 2, 7),
            new Staged(new String[]{"left_arm", "armor", "spk1_l"}, 2, 7),
            new Staged(new String[]{"left_arm", "armor", "spk2_l"}, 3, 7),
            new Staged(new String[]{"left_arm", "armor", "spk3_l"}, 3, 7),
            new Staged(new String[]{"left_arm", "armor", "fin_l"}, 3, 7),
            new Staged(new String[]{"right_leg", "leggings", "knee_r"}, 2, 7),
            new Staged(new String[]{"right_leg", "boots", "ankle_r"}, 3, 7),
            new Staged(new String[]{"left_leg", "leggings", "knee_l"}, 2, 7),
            new Staged(new String[]{"left_leg", "boots", "ankle_l"}, 3, 7)
    };

    private final ModelPart rightLeggings, leftLeggings, rightBoots, leftBoots;
    private final List<ModelPart> stagedParts = new ArrayList<>();

    public RiftArmorModel(ModelPart root) {
        super(root);
        this.rightLeggings = this.rightLeg.getChild("leggings");
        this.leftLeggings = this.leftLeg.getChild("leggings");
        this.rightBoots = this.rightLeg.getChild("boots");
        this.leftBoots = this.leftLeg.getChild("boots");
        for (Staged s : STAGED) {
            ModelPart p = root;
            for (String n : s.path()) p = p.getChild(n);
            stagedParts.add(p);
        }
    }

    public static ResourceLocation texture(int tier, Branch branch) {
        String n = switch (branch) {
            case BLESSED -> "rift_armor_blessed";
            case CORRUPTED -> "rift_armor_corrupted";
            default -> tier <= 1 ? "rift_armor_cracked" : "rift_armor";
        };
        return new ResourceLocation("wavesurvivor", "textures/models/armor/" + n + ".png");
    }

    /** Calque lumineux (RenderType.eyes) ; null au palier I : les cristaux sont encore éteints. */
    public static ResourceLocation glowTexture(int tier, Branch branch) {
        if (branch == Branch.BASE && tier <= 1) return null;
        String n = switch (branch) {
            case BLESSED -> "rift_armor_blessed_glow";
            case CORRUPTED -> "rift_armor_corrupted_glow";
            default -> "rift_armor_glow";
        };
        return new ResourceLocation("wavesurvivor", "textures/models/armor/" + n + ".png");
    }

    /** Affiche seulement les pièces de l'emplacement porté. */
    public RiftArmorModel forSlot(EquipmentSlot slot) {
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

    /** Emplacement + palier (1 à 3 ; une armure bénie ou corrompue est toujours au palier 3) + branche. */
    public RiftArmorModel forStage(EquipmentSlot slot, int tier, Branch branch) {
        forSlot(slot);
        int bit = 1 << branch.ordinal();
        int t = branch == Branch.BASE ? tier : 3;
        for (int i = 0; i < STAGED.length; i++) {
            Staged s = STAGED[i];
            stagedParts.get(i).visible = t >= s.minTier() && (s.branchMask() & bit) != 0;
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
                .texOffs(214, 26).addBox(-4.5F, -6.2F, -5.3F, 9, 1, 1, new CubeDeformation(0F, 0F, 0F))
                .texOffs(54, 26).addBox(-3.5F, -2.6F, -5.1F, 7, 3, 1, new CubeDeformation(0F, -0.1F, 0F))
                .texOffs(88, 0).addBox(-4.5F, -0.8F, -4F, 9, 2, 8, new CubeDeformation(-0.1F, -0.2F, 0F)), PartPose.ZERO);
        PartDefinition p_crest0 = armor_head.addOrReplaceChild("crest0", CubeListBuilder.create()
                .texOffs(8, 26).addBox(-1F, -3F, -1F, 2, 3, 2, new CubeDeformation(-0.2F, 0F, -0.2F)), PartPose.offsetAndRotation(0F, -8.3F, -2.2F, -0.15F, 0F, 0F));
        PartDefinition p_crest1 = armor_head.addOrReplaceChild("crest1", CubeListBuilder.create()
                .texOffs(220, 18).addBox(-1F, -5F, -1F, 2, 5, 2, new CubeDeformation(-0.1F, 0F, -0.1F)), PartPose.offsetAndRotation(0F, -8.3F, 0.3F, -0.5F, 0F, 0F));
        PartDefinition p_crest2 = armor_head.addOrReplaceChild("crest2", CubeListBuilder.create()
                .texOffs(228, 18).addBox(-1F, -4F, -1F, 2, 4, 2, new CubeDeformation(-0.35F, 0F, -0.35F)), PartPose.offsetAndRotation(0F, -7.6F, 3.2F, -1F, 0F, 0F));
        PartDefinition p_horn_r = armor_head.addOrReplaceChild("horn_r", CubeListBuilder.create()
                .texOffs(70, 26).addBox(-1F, -1F, -1F, 2, 2, 2, new CubeDeformation(0F, 0F, 0F))
                .texOffs(16, 26).addBox(-0.5F, -4.5F, -0.5F, 1, 4, 1, new CubeDeformation(0.05F, 0F, 0.05F)), PartPose.offsetAndRotation(-4.4F, -6.4F, -0.3F, -0.35F, 0F, -0.75F));
        PartDefinition p_horn_l = armor_head.addOrReplaceChild("horn_l", CubeListBuilder.create()
                .texOffs(70, 26).mirror().addBox(-1F, -1F, -1F, 2, 2, 2, new CubeDeformation(0F, 0F, 0F)).mirror(false)
                .texOffs(16, 26).mirror().addBox(-0.5F, -4.5F, -0.5F, 1, 4, 1, new CubeDeformation(0.05F, 0F, 0.05F)).mirror(false), PartPose.offsetAndRotation(4.4F, -6.4F, -0.3F, -0.35F, 0F, 0.75F));
        PartDefinition p_crown = armor_head.addOrReplaceChild("crown", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -4.6F, 5F, 0.15F, 0F, 0F));
        PartDefinition p_cr0 = p_crown.addOrReplaceChild("cr0", CubeListBuilder.create()
                .texOffs(176, 26).addBox(-0.5F, -2F, -0.5F, 1, 2, 1, new CubeDeformation(0F, 0F, 0F)), PartPose.offsetAndRotation(-5.884F, -1.955F, 0F, 0F, 0F, -1.25F));
        PartDefinition p_cr1 = p_crown.addOrReplaceChild("cr1", CubeListBuilder.create()
                .texOffs(180, 26).addBox(-0.5F, -2F, -0.5F, 1, 2, 1, new CubeDeformation(0F, 0F, 0F)), PartPose.offsetAndRotation(-4.575F, -4.184F, 0F, 0F, 0F, -0.83F));
        PartDefinition p_cr2 = p_crown.addOrReplaceChild("cr2", CubeListBuilder.create()
                .texOffs(184, 26).addBox(-0.5F, -2F, -0.5F, 1, 2, 1, new CubeDeformation(0F, 0F, 0F)), PartPose.offsetAndRotation(-2.528F, -5.661F, 0F, 0F, 0F, -0.42F));
        PartDefinition p_cr3 = p_crown.addOrReplaceChild("cr3", CubeListBuilder.create()
                .texOffs(78, 26).addBox(-0.5F, -3F, -0.5F, 1, 3, 1, new CubeDeformation(0F, 0F, 0F)), PartPose.offset(0F, -6.2F, 0F));
        PartDefinition p_cr4 = p_crown.addOrReplaceChild("cr4", CubeListBuilder.create()
                .texOffs(188, 26).addBox(-0.5F, -2F, -0.5F, 1, 2, 1, new CubeDeformation(0F, 0F, 0F)), PartPose.offsetAndRotation(2.528F, -5.661F, 0F, 0F, 0F, 0.42F));
        PartDefinition p_cr5 = p_crown.addOrReplaceChild("cr5", CubeListBuilder.create()
                .texOffs(192, 26).addBox(-0.5F, -2F, -0.5F, 1, 2, 1, new CubeDeformation(0F, 0F, 0F)), PartPose.offsetAndRotation(4.575F, -4.184F, 0F, 0F, 0F, 0.83F));
        PartDefinition p_cr6 = p_crown.addOrReplaceChild("cr6", CubeListBuilder.create()
                .texOffs(196, 26).addBox(-0.5F, -2F, -0.5F, 1, 2, 1, new CubeDeformation(0F, 0F, 0F)), PartPose.offsetAndRotation(5.884F, -1.955F, 0F, 0F, 0F, 1.25F));
        PartDefinition p_chorn_r = armor_head.addOrReplaceChild("chorn_r", CubeListBuilder.create()
                .texOffs(82, 26).addBox(-1F, -1F, -1F, 2, 2, 2, new CubeDeformation(0.1F, 0F, 0.1F))
                .texOffs(236, 18).addBox(-1F, -5F, -1F, 2, 4, 2, new CubeDeformation(-0.1F, 0F, -0.1F)), PartPose.offsetAndRotation(-4.2F, -6.6F, 0.2F, -0.25F, 0F, -1.05F));
        PartDefinition p_chorn_r2 = p_chorn_r.addOrReplaceChild("chorn_r2", CubeListBuilder.create()
                .texOffs(244, 18).addBox(-0.5F, -5F, -0.5F, 1, 5, 1, new CubeDeformation(0.1F, 0F, 0.1F)), PartPose.offsetAndRotation(0F, -4.7F, 0F, -0.45F, 0F, 0.85F));
        PartDefinition p_chorn_l = armor_head.addOrReplaceChild("chorn_l", CubeListBuilder.create()
                .texOffs(82, 26).mirror().addBox(-1F, -1F, -1F, 2, 2, 2, new CubeDeformation(0.1F, 0F, 0.1F)).mirror(false)
                .texOffs(236, 18).mirror().addBox(-1F, -5F, -1F, 2, 4, 2, new CubeDeformation(-0.1F, 0F, -0.1F)).mirror(false), PartPose.offsetAndRotation(4.2F, -6.6F, 0.2F, -0.25F, 0F, 1.05F));
        PartDefinition p_chorn_l2 = p_chorn_l.addOrReplaceChild("chorn_l2", CubeListBuilder.create()
                .texOffs(244, 18).mirror().addBox(-0.5F, -5F, -0.5F, 1, 5, 1, new CubeDeformation(0.1F, 0F, 0.1F)).mirror(false), PartPose.offsetAndRotation(0F, -4.7F, 0F, -0.45F, 0F, -0.85F));
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition armor_body = body.addOrReplaceChild("armor", CubeListBuilder.create()
                .texOffs(36, 0).addBox(-5F, -0.7F, -3F, 10, 5, 6, new CubeDeformation(-0.2F, -0.2F, -0.1F))
                .texOffs(20, 26).addBox(-0.5F, 0.2F, -3.45F, 1, 4, 1, new CubeDeformation(-0.05F, 0F, -0.1F))
                .texOffs(90, 26).addBox(-1.2F, 3.9F, -3.45F, 1, 3, 1, new CubeDeformation(-0.05F, 0F, -0.1F))
                .texOffs(94, 26).addBox(-0.2F, 6.6F, -3.5F, 1, 3, 1, new CubeDeformation(-0.1F, 0F, -0.15F))
                .texOffs(0, 18).addBox(-4.5F, 3.9F, -3F, 9, 2, 6, new CubeDeformation(-0.05F, -0.15F, -0.15F))
                .texOffs(30, 18).addBox(-4.5F, 5.5F, -3F, 9, 2, 6, new CubeDeformation(-0.2F, -0.15F, -0.25F))
                .texOffs(60, 18).addBox(-4.5F, 7.1F, -3F, 9, 2, 6, new CubeDeformation(-0.35F, -0.15F, -0.35F))
                .texOffs(90, 18).addBox(-4.5F, 8.8F, -3F, 9, 2, 6, new CubeDeformation(-0.3F, -0.25F, -0.3F))
                .texOffs(200, 26).addBox(-1F, 9F, -3.6F, 2, 2, 1, new CubeDeformation(0F, -0.2F, -0.1F))
                .texOffs(24, 26).addBox(-4.4F, 10.4F, -2.95F, 4, 4, 1, new CubeDeformation(-0.15F, 0F, -0.25F))
                .texOffs(34, 26).addBox(0.4F, 10.4F, -2.95F, 4, 4, 1, new CubeDeformation(-0.15F, 0F, -0.25F))
                .texOffs(98, 26).addBox(-4F, 10.4F, 1.95F, 8, 3, 1, new CubeDeformation(-0.1F, 0F, -0.25F)), PartPose.ZERO);
        PartDefinition p_pec_r = armor_body.addOrReplaceChild("pec_r", CubeListBuilder.create()
                .texOffs(44, 26).addBox(-2.2F, -0.3F, -0.5F, 4, 4, 1, new CubeDeformation(0F, 0F, -0.15F)), PartPose.offsetAndRotation(-2.4F, 0.2F, -3F, 0F, 0.22F, 0F));
        PartDefinition p_pec_l = armor_body.addOrReplaceChild("pec_l", CubeListBuilder.create()
                .texOffs(44, 26).mirror().addBox(-1.8F, -0.3F, -0.5F, 4, 4, 1, new CubeDeformation(0F, 0F, -0.15F)).mirror(false), PartPose.offsetAndRotation(2.4F, 0.2F, -3F, 0F, -0.22F, 0F));
        PartDefinition p_shard_r = armor_body.addOrReplaceChild("shard_r", CubeListBuilder.create()
                .texOffs(116, 26).addBox(-1.5F, -0.5F, -1.5F, 3, 1, 3, new CubeDeformation(-0.2F, 0F, -0.2F))
                .texOffs(120, 18).addBox(-1F, -6F, -1F, 2, 6, 2, new CubeDeformation(-0.15F, 0F, -0.15F)), PartPose.offsetAndRotation(-2.2F, 3.2F, 3F, -0.65F, 0F, -0.35F));
        PartDefinition p_shard_r2 = p_shard_r.addOrReplaceChild("shard_r2", CubeListBuilder.create()
                .texOffs(128, 26).addBox(-0.5F, -3F, -0.5F, 1, 3, 1, new CubeDeformation(0F, 0F, 0F)), PartPose.offsetAndRotation(0.9F, -0.2F, 0.2F, 0F, 0F, 0.45F));
        PartDefinition p_shard_l = armor_body.addOrReplaceChild("shard_l", CubeListBuilder.create()
                .texOffs(116, 26).mirror().addBox(-1.5F, -0.5F, -1.5F, 3, 1, 3, new CubeDeformation(-0.2F, 0F, -0.2F)).mirror(false)
                .texOffs(120, 18).mirror().addBox(-1F, -6F, -1F, 2, 6, 2, new CubeDeformation(-0.15F, 0F, -0.15F)).mirror(false), PartPose.offsetAndRotation(2.2F, 3.2F, 3F, -0.65F, 0F, 0.35F));
        PartDefinition p_shard_l2 = p_shard_l.addOrReplaceChild("shard_l2", CubeListBuilder.create()
                .texOffs(128, 26).mirror().addBox(-0.5F, -3F, -0.5F, 1, 3, 1, new CubeDeformation(0F, 0F, 0F)).mirror(false), PartPose.offsetAndRotation(-0.9F, -0.2F, 0.2F, 0F, 0F, -0.45F));
        PartDefinition p_spine0 = armor_body.addOrReplaceChild("spine0", CubeListBuilder.create()
                .texOffs(132, 26).addBox(-0.5F, -3F, -0.5F, 1, 3, 1, new CubeDeformation(0.1F, 0F, 0.1F)), PartPose.offsetAndRotation(0F, 1.5F, 3F, -0.9F, 0F, 0F));
        PartDefinition p_spine1 = armor_body.addOrReplaceChild("spine1", CubeListBuilder.create()
                .texOffs(206, 26).addBox(-0.5F, -2.5F, -0.5F, 1, 2, 1, new CubeDeformation(0.1F, 0F, 0.1F)), PartPose.offsetAndRotation(0F, 4.1F, 3F, -0.9F, 0F, 0F));
        PartDefinition p_spine2 = armor_body.addOrReplaceChild("spine2", CubeListBuilder.create()
                .texOffs(234, 26).addBox(-0.5F, -2F, -0.5F, 1, 1, 1, new CubeDeformation(0.1F, 0F, 0.1F)), PartPose.offsetAndRotation(0F, 6.7F, 3F, -0.9F, 0F, 0F));
        PartDefinition right_arm = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5F, 2F, 0F));
        PartDefinition armor_right_arm = right_arm.addOrReplaceChild("armor", CubeListBuilder.create()
                .texOffs(150, 0).addBox(-3.5F, 2F, -2.5F, 5, 4, 5, new CubeDeformation(-0.2F, 0F, -0.1F))
                .texOffs(128, 18).addBox(-4F, 5.4F, -3F, 6, 2, 6, new CubeDeformation(-0.4F, 0F, -0.3F))
                .texOffs(170, 0).addBox(-3.5F, 7F, -2.5F, 5, 4, 5, new CubeDeformation(-0.1F, 0F, -0.1F))
                .texOffs(152, 18).addBox(-4F, 10.2F, -3F, 6, 2, 6, new CubeDeformation(-0.3F, -0.2F, -0.3F)), PartPose.ZERO);
        PartDefinition p_pd1_r = armor_right_arm.addOrReplaceChild("pd1_r", CubeListBuilder.create()
                .texOffs(122, 0).addBox(-3.5F, -1.8F, -3.5F, 7, 3, 7, new CubeDeformation(-0.1F, 0F, -0.2F)), PartPose.offsetAndRotation(-1F, -1F, 0F, 0F, 0F, -0.18F));
        PartDefinition p_pd2_r = armor_right_arm.addOrReplaceChild("pd2_r", CubeListBuilder.create()
                .texOffs(176, 18).addBox(-3F, -0.3F, -3F, 6, 2, 6, new CubeDeformation(-0.2F, -0.2F, -0.35F)), PartPose.offsetAndRotation(-1.6F, 1F, 0F, 0F, 0F, -0.32F));
        PartDefinition p_spk1_r = armor_right_arm.addOrReplaceChild("spk1_r", CubeListBuilder.create()
                .texOffs(248, 18).addBox(-1F, -4F, -1F, 2, 4, 2, new CubeDeformation(-0.1F, 0F, -0.1F)), PartPose.offsetAndRotation(-2.6F, -2.4F, 0.2F, 0F, 0F, -0.55F));
        PartDefinition p_spk2_r = armor_right_arm.addOrReplaceChild("spk2_r", CubeListBuilder.create()
                .texOffs(136, 26).addBox(-0.5F, -3F, -0.5F, 1, 3, 1, new CubeDeformation(0F, 0F, 0F)), PartPose.offsetAndRotation(-2F, -2.2F, -2F, 0.45F, 0F, -0.4F));
        PartDefinition p_spk3_r = armor_right_arm.addOrReplaceChild("spk3_r", CubeListBuilder.create()
                .texOffs(210, 26).addBox(-0.5F, -2F, -0.5F, 1, 2, 1, new CubeDeformation(0F, 0F, 0F)), PartPose.offsetAndRotation(-2.4F, -2.2F, 2.2F, -0.5F, 0F, -0.45F));
        PartDefinition p_fin_r = armor_right_arm.addOrReplaceChild("fin_r", CubeListBuilder.create()
                .texOffs(0, 26).addBox(-1F, -1.5F, -1.5F, 1, 3, 3, new CubeDeformation(0F, 0F, -0.2F)), PartPose.offsetAndRotation(-3.4F, 8.5F, 0F, 0F, 0F, 0.25F));
        PartDefinition left_arm = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5F, 2F, 0F));
        PartDefinition armor_left_arm = left_arm.addOrReplaceChild("armor", CubeListBuilder.create()
                .texOffs(150, 0).mirror().addBox(-1.5F, 2F, -2.5F, 5, 4, 5, new CubeDeformation(-0.2F, 0F, -0.1F)).mirror(false)
                .texOffs(128, 18).mirror().addBox(-2F, 5.4F, -3F, 6, 2, 6, new CubeDeformation(-0.4F, 0F, -0.3F)).mirror(false)
                .texOffs(170, 0).mirror().addBox(-1.5F, 7F, -2.5F, 5, 4, 5, new CubeDeformation(-0.1F, 0F, -0.1F)).mirror(false)
                .texOffs(152, 18).mirror().addBox(-2F, 10.2F, -3F, 6, 2, 6, new CubeDeformation(-0.3F, -0.2F, -0.3F)).mirror(false), PartPose.ZERO);
        PartDefinition p_pd1_l = armor_left_arm.addOrReplaceChild("pd1_l", CubeListBuilder.create()
                .texOffs(122, 0).mirror().addBox(-3.5F, -1.8F, -3.5F, 7, 3, 7, new CubeDeformation(-0.1F, 0F, -0.2F)).mirror(false), PartPose.offsetAndRotation(1F, -1F, 0F, 0F, 0F, 0.18F));
        PartDefinition p_pd2_l = armor_left_arm.addOrReplaceChild("pd2_l", CubeListBuilder.create()
                .texOffs(176, 18).mirror().addBox(-3F, -0.3F, -3F, 6, 2, 6, new CubeDeformation(-0.2F, -0.2F, -0.35F)).mirror(false), PartPose.offsetAndRotation(1.6F, 1F, 0F, 0F, 0F, 0.32F));
        PartDefinition p_spk1_l = armor_left_arm.addOrReplaceChild("spk1_l", CubeListBuilder.create()
                .texOffs(248, 18).mirror().addBox(-1F, -4F, -1F, 2, 4, 2, new CubeDeformation(-0.1F, 0F, -0.1F)).mirror(false), PartPose.offsetAndRotation(2.6F, -2.4F, 0.2F, 0F, 0F, 0.55F));
        PartDefinition p_spk2_l = armor_left_arm.addOrReplaceChild("spk2_l", CubeListBuilder.create()
                .texOffs(136, 26).mirror().addBox(-0.5F, -3F, -0.5F, 1, 3, 1, new CubeDeformation(0F, 0F, 0F)).mirror(false), PartPose.offsetAndRotation(2F, -2.2F, -2F, 0.45F, 0F, 0.4F));
        PartDefinition p_spk3_l = armor_left_arm.addOrReplaceChild("spk3_l", CubeListBuilder.create()
                .texOffs(210, 26).mirror().addBox(-0.5F, -2F, -0.5F, 1, 2, 1, new CubeDeformation(0F, 0F, 0F)).mirror(false), PartPose.offsetAndRotation(2.4F, -2.2F, 2.2F, -0.5F, 0F, 0.45F));
        PartDefinition p_fin_l = armor_left_arm.addOrReplaceChild("fin_l", CubeListBuilder.create()
                .texOffs(0, 26).mirror().addBox(0F, -1.5F, -1.5F, 1, 3, 3, new CubeDeformation(0F, 0F, -0.2F)).mirror(false), PartPose.offsetAndRotation(3.4F, 8.5F, 0F, 0F, 0F, -0.25F));
        PartDefinition right_leg = root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12F, 0F));
        PartDefinition leggings_right_leg = right_leg.addOrReplaceChild("leggings", CubeListBuilder.create()
                .texOffs(68, 0).addBox(-2.5F, -0.4F, -2.5F, 5, 6, 5, new CubeDeformation(0F, -0.4F, 0F))
                .texOffs(190, 0).addBox(-3F, 4.65F, -3.15F, 6, 3, 6, new CubeDeformation(-0.35F, -0.35F, -0.2F))
                .texOffs(200, 18).addBox(-2.5F, 6.8F, -2.5F, 5, 3, 5, new CubeDeformation(-0.1F, -0.2F, -0.1F)), PartPose.ZERO);
        PartDefinition p_knee_r = leggings_right_leg.addOrReplaceChild("knee_r", CubeListBuilder.create()
                .texOffs(140, 26).addBox(-0.5F, -2.5F, -0.5F, 1, 3, 1, new CubeDeformation(0.1F, 0F, 0.1F)), PartPose.offsetAndRotation(0F, 5.8F, -3.1F, 0.7F, 0F, 0F));
        PartDefinition boots_right_leg = right_leg.addOrReplaceChild("boots", CubeListBuilder.create()
                .texOffs(214, 0).addBox(-3F, 9.2F, -3F, 6, 3, 6, new CubeDeformation(-0.4F, -0.25F, -0.3F))
                .texOffs(144, 26).addBox(-2.5F, 10.35F, -4.2F, 5, 2, 2, new CubeDeformation(0F, -0.25F, -0.3F))
                .texOffs(158, 26).addBox(-2.5F, 10.55F, -5.35F, 5, 2, 2, new CubeDeformation(-0.2F, -0.45F, -0.45F)), PartPose.ZERO);
        PartDefinition p_ankle_r = boots_right_leg.addOrReplaceChild("ankle_r", CubeListBuilder.create()
                .texOffs(172, 26).addBox(-0.5F, -2.5F, -0.5F, 1, 3, 1, new CubeDeformation(0F, 0F, 0F)), PartPose.offsetAndRotation(-2.6F, 10.2F, 0.3F, -0.3F, 0F, -0.5F));
        PartDefinition left_leg = root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12F, 0F));
        PartDefinition leggings_left_leg = left_leg.addOrReplaceChild("leggings", CubeListBuilder.create()
                .texOffs(68, 0).mirror().addBox(-2.5F, -0.4F, -2.5F, 5, 6, 5, new CubeDeformation(0F, -0.4F, 0F)).mirror(false)
                .texOffs(190, 0).mirror().addBox(-3F, 4.65F, -3.15F, 6, 3, 6, new CubeDeformation(-0.35F, -0.35F, -0.2F)).mirror(false)
                .texOffs(200, 18).mirror().addBox(-2.5F, 6.8F, -2.5F, 5, 3, 5, new CubeDeformation(-0.1F, -0.2F, -0.1F)).mirror(false), PartPose.ZERO);
        PartDefinition p_knee_l = leggings_left_leg.addOrReplaceChild("knee_l", CubeListBuilder.create()
                .texOffs(140, 26).mirror().addBox(-0.5F, -2.5F, -0.5F, 1, 3, 1, new CubeDeformation(0.1F, 0F, 0.1F)).mirror(false), PartPose.offsetAndRotation(0F, 5.8F, -3.1F, 0.7F, 0F, 0F));
        PartDefinition boots_left_leg = left_leg.addOrReplaceChild("boots", CubeListBuilder.create()
                .texOffs(214, 0).mirror().addBox(-3F, 9.2F, -3F, 6, 3, 6, new CubeDeformation(-0.4F, -0.25F, -0.3F)).mirror(false)
                .texOffs(144, 26).mirror().addBox(-2.5F, 10.35F, -4.2F, 5, 2, 2, new CubeDeformation(0F, -0.25F, -0.3F)).mirror(false)
                .texOffs(158, 26).mirror().addBox(-2.5F, 10.55F, -5.35F, 5, 2, 2, new CubeDeformation(-0.2F, -0.45F, -0.45F)).mirror(false), PartPose.ZERO);
        PartDefinition p_ankle_l = boots_left_leg.addOrReplaceChild("ankle_l", CubeListBuilder.create()
                .texOffs(172, 26).mirror().addBox(-0.5F, -2.5F, -0.5F, 1, 3, 1, new CubeDeformation(0F, 0F, 0F)).mirror(false), PartPose.offsetAndRotation(2.6F, 10.2F, 0.3F, -0.3F, 0F, 0.5F));
        return LayerDefinition.create(mesh, 256, 64);
    }
}
