package com.wavesurvivor.client;

import com.wavesurvivor.entity.TitanAnimated;
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
 * 2.0 — MODÈLE DE L'ARCHONTE DU NÉANT, Titan (conçu sur mesure par Claude chat, NE PAS régénérer à la main :
 * le modèle, la texture et les aperçus viennent d'un même script, voir docs/faille/titans/archonte/).
 * Silhouette immense et maigre qui flotte : robe déchirée en 4 étages et 10 lambeaux qui ondulent, cage thoracique
 * ouverte sur le vide, masque d'os à mâchoire articulée, couronne de 6 cornes, auréole de 28 blocs qui tourne,
 * longs bras d'os aux doigts articulés, 6 rochers en orbite (certains portent un cristal).
 * Texture 512x128 + calque lumineux. Taille naturelle ~11 blocs (agrandi par Pehkui). Pas de jambes : pas de marche.
 * Animations : flottement, robe et lambeaux, auréole et rochers qui tournent, et 8 POSES de compétence lues sur l'entité
 * si elle implémente {@link TitanAnimated} (purement visuelles).
 */
@OnlyIn(Dist.CLIENT)
public class ArchonteModel<T extends Mob> extends HierarchicalModel<T> {

    public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(new ResourceLocation("wavesurvivor", "archonte"), "main");

    private static final float BODY_Y = -56F;
    private static final int TATTERS = 10;
    private static final float[] ROCK_Y = {-96F, -46F, -126F, -71F, -111F, -31F};
    private static final float[] SLAM_RISE = {0F, 0F, 0.45F, -14F, 0.58F, 6F, 0.85F, 6F, 1F, 0F};
    private static final float[] SLAM_ARM = {0F, 0F, 0.45F, -2.6F, 0.58F, -0.7F, 0.85F, -0.7F, 1F, 0F};
    private static final float[] THROW_ARM = {0F, 0F, 0.45F, -2.7F, 0.6F, -2.7F, 0.72F, -1.35F, 0.9F, -1.35F, 1F, 0F};
    private static final float[] THROW_LEAN = {0F, 0F, 0.45F, -0.15F, 0.6F, -0.15F, 0.72F, 0.25F, 0.9F, 0.25F, 1F, 0F};
    private static final float[] CHARGE_LEAN = {0F, 0F, 0.5F, -0.15F, 0.62F, 0.55F, 0.95F, 0.55F, 1F, 0F};
    private static final int[] SIDE = {-1, 1};

    private final ModelPart root, body, torso, head, jaw, halo, orbit;
    private final ModelPart[] arm = new ModelPart[2], elbow = new ModelPart[2], hand = new ModelPart[2];
    private final ModelPart[] tatter = new ModelPart[TATTERS];
    private final ModelPart[] rock = new ModelPart[6];

    public ArchonteModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.torso = body.getChild("torso");
        this.head = torso.getChild("head");
        this.jaw = head.getChild("jaw");
        this.halo = head.getChild("halo");
        this.orbit = root.getChild("orbit");
        ModelPart robe = body.getChild("robe");
        for (int k = 0; k < TATTERS; k++) tatter[k] = robe.getChild("tatter" + k);
        for (int k = 0; k < rock.length; k++) rock[k] = orbit.getChild("rock" + k);
        String[] a = {"arm_r", "arm_l"};
        for (int i = 0; i < 2; i++) {
            arm[i] = torso.getChild(a[i]);
            elbow[i] = arm[i].getChild(a[i] + "_elbow");
            hand[i] = elbow[i].getChild(a[i] + "_hand");
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, -56F, 0F, 0F, 0F, 0F));
        PartDefinition p_robe = p_body.addOrReplaceChild("robe", CubeListBuilder.create().texOffs(238, 62).addBox(-11F, -2F, -8F, 22F, 12F, 16F).texOffs(0, 62).addBox(-14F, 10F, -10F, 28F, 16F, 20F).texOffs(152, 0).addBox(-18F, 26F, -13F, 36F, 16F, 26F).texOffs(0, 0).addBox(-22F, 42F, -16F, 44F, 30F, 32F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        p_robe.addOrReplaceChild("tatter0", CubeListBuilder.create().texOffs(376, 98).addBox(-2.5F, 0F, -0.5F, 5F, 9F, 1F), PartPose.offsetAndRotation(4F, 71F, 14F, 0F, 0F, 0F));
        p_robe.addOrReplaceChild("tatter1", CubeListBuilder.create().texOffs(352, 98).addBox(-2.5F, 0F, -0.5F, 5F, 10F, 1F), PartPose.offsetAndRotation(14F, 71F, 9F, 0F, 0F, 0F));
        p_robe.addOrReplaceChild("tatter2", CubeListBuilder.create().texOffs(332, 98).addBox(-2.5F, 0F, -0.5F, 5F, 11F, 1F), PartPose.offsetAndRotation(19F, 71F, 2F, 0F, 0F, 0F));
        p_robe.addOrReplaceChild("tatter3", CubeListBuilder.create().texOffs(304, 98).addBox(-2.5F, 0F, -0.5F, 5F, 12F, 1F), PartPose.offsetAndRotation(17F, 71F, -7F, 0F, 0F, 0F));
        p_robe.addOrReplaceChild("tatter4", CubeListBuilder.create().texOffs(292, 98).addBox(-2.5F, 0F, -0.5F, 5F, 13F, 1F), PartPose.offsetAndRotation(8F, 71F, -13F, 0F, 0F, 0F));
        p_robe.addOrReplaceChild("tatter5", CubeListBuilder.create().texOffs(212, 98).addBox(-2.5F, 0F, -0.5F, 5F, 14F, 1F), PartPose.offsetAndRotation(-4F, 71F, -14F, 0F, 0F, 0F));
        p_robe.addOrReplaceChild("tatter6", CubeListBuilder.create().texOffs(376, 98).addBox(-2.5F, 0F, -0.5F, 5F, 9F, 1F), PartPose.offsetAndRotation(-14F, 71F, -9F, 0F, 0F, 0F));
        p_robe.addOrReplaceChild("tatter7", CubeListBuilder.create().texOffs(352, 98).addBox(-2.5F, 0F, -0.5F, 5F, 10F, 1F), PartPose.offsetAndRotation(-19F, 71F, -2F, 0F, 0F, 0F));
        p_robe.addOrReplaceChild("tatter8", CubeListBuilder.create().texOffs(332, 98).addBox(-2.5F, 0F, -0.5F, 5F, 11F, 1F), PartPose.offsetAndRotation(-17F, 71F, 7F, 0F, 0F, 0F));
        p_robe.addOrReplaceChild("tatter9", CubeListBuilder.create().texOffs(304, 98).addBox(-2.5F, 0F, -0.5F, 5F, 12F, 1F), PartPose.offsetAndRotation(-8F, 71F, 13F, 0F, 0F, 0F));
        PartDefinition p_torso = p_body.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(354, 0).addBox(-12F, -24F, -7F, 24F, 24F, 14F).texOffs(124, 62).addBox(-18F, -30F, -10F, 36F, 8F, 21F).texOffs(410, 62).addBox(-15F, -22F, 7F, 30F, 18F, 4F), PartPose.offsetAndRotation(0F, -2F, 0F, 0F, 0F, 0F));
        PartDefinition p_head = p_torso.addOrReplaceChild("head", CubeListBuilder.create().texOffs(276, 0).addBox(-10F, -21F, -9F, 20F, 22F, 19F).texOffs(224, 98).addBox(-7F, -17F, -10F, 14F, 12F, 2F).texOffs(490, 98).addBox(-6F, -13F, -10.5F, 4F, 2F, 1F).texOffs(490, 98).addBox(2F, -13F, -10.5F, 4F, 2F, 1F).texOffs(484, 98).addBox(-1F, -10F, -10.5F, 2F, 3F, 1F), PartPose.offsetAndRotation(0F, -30F, -1F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(420, 98).addBox(-5F, 0F, -1.5F, 10F, 6F, 2F), PartPose.offsetAndRotation(0F, -5F, -9F, 0F, 0F, 0F));
        p_head.addOrReplaceChild("horn0", CubeListBuilder.create().texOffs(256, 98).addBox(-2.5F, -9F, -2.5F, 5F, 9F, 5F).texOffs(364, 98).addBox(-1.5F, -17F, -1.5F, 3F, 8F, 3F).texOffs(408, 98).addBox(-1F, -24F, -1F, 2F, 7F, 2F), PartPose.offsetAndRotation(-8F, -21F, 0F, -0.3F, 0F, 1.1606F));
        p_head.addOrReplaceChild("horn1", CubeListBuilder.create().texOffs(132, 98).addBox(-3F, -11F, -3F, 6F, 11F, 6F).texOffs(276, 98).addBox(-2F, -21F, -2F, 4F, 10F, 4F).texOffs(400, 98).addBox(-1F, -29F, -1F, 2F, 8F, 2F), PartPose.offsetAndRotation(-5F, -21F, 2F, -0.3F, 0F, 0.6964F));
        p_head.addOrReplaceChild("horn2", CubeListBuilder.create().texOffs(48, 98).addBox(-3.5F, -13F, -3.5F, 7F, 13F, 7F).texOffs(192, 98).addBox(-2.5F, -24F, -2.5F, 5F, 11F, 5F).texOffs(344, 98).addBox(-1F, -34F, -1F, 2F, 10F, 2F), PartPose.offsetAndRotation(-2F, -21F, 3F, -0.3F, 0F, 0.2321F));
        p_head.addOrReplaceChild("horn3", CubeListBuilder.create().texOffs(48, 98).addBox(-3.5F, -13F, -3.5F, 7F, 13F, 7F).texOffs(192, 98).addBox(-2.5F, -24F, -2.5F, 5F, 11F, 5F).texOffs(344, 98).addBox(-1F, -34F, -1F, 2F, 10F, 2F), PartPose.offsetAndRotation(2F, -21F, 3F, -0.3F, 0F, -0.2321F));
        p_head.addOrReplaceChild("horn4", CubeListBuilder.create().texOffs(132, 98).addBox(-3F, -11F, -3F, 6F, 11F, 6F).texOffs(276, 98).addBox(-2F, -21F, -2F, 4F, 10F, 4F).texOffs(400, 98).addBox(-1F, -29F, -1F, 2F, 8F, 2F), PartPose.offsetAndRotation(5F, -21F, 2F, -0.3F, 0F, -0.6964F));
        p_head.addOrReplaceChild("horn5", CubeListBuilder.create().texOffs(256, 98).addBox(-2.5F, -9F, -2.5F, 5F, 9F, 5F).texOffs(364, 98).addBox(-1.5F, -17F, -1.5F, 3F, 8F, 3F).texOffs(408, 98).addBox(-1F, -24F, -1F, 2F, 7F, 2F), PartPose.offsetAndRotation(8F, -21F, 0F, -0.3F, 0F, -1.1606F));
        p_head.addOrReplaceChild("halo", CubeListBuilder.create().texOffs(476, 98).addBox(19.5F, -1.5F, -0.5F, 3F, 3F, 1F).texOffs(476, 98).addBox(18.5F, 3.5F, -0.5F, 3F, 3F, 1F).texOffs(476, 98).addBox(17.5F, 7.5F, -0.5F, 3F, 3F, 1F).texOffs(476, 98).addBox(14.5F, 11.5F, -0.5F, 3F, 3F, 1F).texOffs(476, 98).addBox(11.5F, 14.5F, -0.5F, 3F, 3F, 1F).texOffs(476, 98).addBox(7.5F, 17.5F, -0.5F, 3F, 3F, 1F).texOffs(476, 98).addBox(3.5F, 18.5F, -0.5F, 3F, 3F, 1F).texOffs(476, 98).addBox(-1.5F, 19.5F, -0.5F, 3F, 3F, 1F).texOffs(476, 98).addBox(-6.5F, 18.5F, -0.5F, 3F, 3F, 1F).texOffs(476, 98).addBox(-10.5F, 17.5F, -0.5F, 3F, 3F, 1F).texOffs(476, 98).addBox(-14.5F, 14.5F, -0.5F, 3F, 3F, 1F).texOffs(476, 98).addBox(-17.5F, 11.5F, -0.5F, 3F, 3F, 1F).texOffs(476, 98).addBox(-20.5F, 7.5F, -0.5F, 3F, 3F, 1F).texOffs(476, 98).addBox(-21.5F, 3.5F, -0.5F, 3F, 3F, 1F).texOffs(476, 98).addBox(-22.5F, -1.5F, -0.5F, 3F, 3F, 1F).texOffs(476, 98).addBox(-21.5F, -6.5F, -0.5F, 3F, 3F, 1F).texOffs(476, 98).addBox(-20.5F, -10.5F, -0.5F, 3F, 3F, 1F).texOffs(476, 98).addBox(-17.5F, -14.5F, -0.5F, 3F, 3F, 1F).texOffs(476, 98).addBox(-14.5F, -17.5F, -0.5F, 3F, 3F, 1F).texOffs(476, 98).addBox(-10.5F, -20.5F, -0.5F, 3F, 3F, 1F).texOffs(476, 98).addBox(-6.5F, -21.5F, -0.5F, 3F, 3F, 1F).texOffs(476, 98).addBox(-1.5F, -22.5F, -0.5F, 3F, 3F, 1F).texOffs(476, 98).addBox(3.5F, -21.5F, -0.5F, 3F, 3F, 1F).texOffs(476, 98).addBox(7.5F, -20.5F, -0.5F, 3F, 3F, 1F).texOffs(476, 98).addBox(11.5F, -17.5F, -0.5F, 3F, 3F, 1F).texOffs(476, 98).addBox(14.5F, -14.5F, -0.5F, 3F, 3F, 1F).texOffs(476, 98).addBox(17.5F, -10.5F, -0.5F, 3F, 3F, 1F).texOffs(476, 98).addBox(18.5F, -6.5F, -0.5F, 3F, 3F, 1F), PartPose.offsetAndRotation(0F, -14F, 13F, 0F, 0F, 0F));
        PartDefinition p_arm_r = p_torso.addOrReplaceChild("arm_r", CubeListBuilder.create().texOffs(96, 62).addBox(-3.5F, -2F, -3.5F, 7F, 24F, 7F), PartPose.offsetAndRotation(-16F, -26F, 0F, -0.1F, 0F, 0.22F));
        PartDefinition p_arm_r_elbow = p_arm_r.addOrReplaceChild("arm_r_elbow", CubeListBuilder.create().texOffs(314, 62).addBox(-5.5F, 0F, -5.5F, 11F, 13F, 11F).texOffs(116, 98).addBox(-2F, 8F, -2F, 4F, 14F, 4F), PartPose.offsetAndRotation(0F, 21F, 0F, -0.45F, 0F, 0F));
        PartDefinition p_arm_r_hand = p_arm_r_elbow.addOrReplaceChild("arm_r_hand", CubeListBuilder.create().texOffs(444, 98).addBox(-3F, 0F, -2F, 6F, 4F, 4F), PartPose.offsetAndRotation(0F, 22F, 0F, 0F, 0F, 0F));
        p_arm_r_hand.addOrReplaceChild("arm_r_f0", CubeListBuilder.create().texOffs(472, 98).addBox(-0.5F, 0F, -0.5F, 1F, 7.05F, 1F), PartPose.offsetAndRotation(-2.25F, 4F, 0F, 0F, 0F, -0.18F));
        p_arm_r_hand.addOrReplaceChild("arm_r_f1", CubeListBuilder.create().texOffs(416, 98).addBox(-0.5F, 0F, -0.5F, 1F, 8.35F, 1F), PartPose.offsetAndRotation(-0.75F, 4F, 0F, 0F, 0F, -0.06F));
        p_arm_r_hand.addOrReplaceChild("arm_r_f2", CubeListBuilder.create().texOffs(416, 98).addBox(-0.5F, 0F, -0.5F, 1F, 8.35F, 1F), PartPose.offsetAndRotation(0.75F, 4F, 0F, 0F, 0F, 0.06F));
        p_arm_r_hand.addOrReplaceChild("arm_r_f3", CubeListBuilder.create().texOffs(472, 98).addBox(-0.5F, 0F, -0.5F, 1F, 7.05F, 1F), PartPose.offsetAndRotation(2.25F, 4F, 0F, 0F, 0F, 0.18F));
        PartDefinition p_arm_l = p_torso.addOrReplaceChild("arm_l", CubeListBuilder.create().texOffs(96, 62).addBox(-3.5F, -2F, -3.5F, 7F, 24F, 7F), PartPose.offsetAndRotation(16F, -26F, 0F, -0.1F, 0F, -0.22F));
        PartDefinition p_arm_l_elbow = p_arm_l.addOrReplaceChild("arm_l_elbow", CubeListBuilder.create().texOffs(314, 62).addBox(-5.5F, 0F, -5.5F, 11F, 13F, 11F).texOffs(116, 98).addBox(-2F, 8F, -2F, 4F, 14F, 4F), PartPose.offsetAndRotation(0F, 21F, 0F, -0.45F, 0F, 0F));
        PartDefinition p_arm_l_hand = p_arm_l_elbow.addOrReplaceChild("arm_l_hand", CubeListBuilder.create().texOffs(444, 98).addBox(-3F, 0F, -2F, 6F, 4F, 4F), PartPose.offsetAndRotation(0F, 22F, 0F, 0F, 0F, 0F));
        p_arm_l_hand.addOrReplaceChild("arm_l_f0", CubeListBuilder.create().texOffs(472, 98).addBox(-0.5F, 0F, -0.5F, 1F, 7.05F, 1F), PartPose.offsetAndRotation(-2.25F, 4F, 0F, 0F, 0F, -0.18F));
        p_arm_l_hand.addOrReplaceChild("arm_l_f1", CubeListBuilder.create().texOffs(416, 98).addBox(-0.5F, 0F, -0.5F, 1F, 8.35F, 1F), PartPose.offsetAndRotation(-0.75F, 4F, 0F, 0F, 0F, -0.06F));
        p_arm_l_hand.addOrReplaceChild("arm_l_f2", CubeListBuilder.create().texOffs(416, 98).addBox(-0.5F, 0F, -0.5F, 1F, 8.35F, 1F), PartPose.offsetAndRotation(0.75F, 4F, 0F, 0F, 0F, 0.06F));
        p_arm_l_hand.addOrReplaceChild("arm_l_f3", CubeListBuilder.create().texOffs(472, 98).addBox(-0.5F, 0F, -0.5F, 1F, 7.05F, 1F), PartPose.offsetAndRotation(2.25F, 4F, 0F, 0F, 0F, 0.18F));
        PartDefinition p_orbit = root.addOrReplaceChild("orbit", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_rock0 = p_orbit.addOrReplaceChild("rock0", CubeListBuilder.create().texOffs(358, 62).addBox(-6.5F, -6.5F, -6.5F, 13F, 10F, 13F), PartPose.offsetAndRotation(41F, -96F, 7F, 0F, 0F, 0F));
        p_rock0.addOrReplaceChild("rock0_crystal", CubeListBuilder.create().texOffs(316, 98).addBox(-2F, -8F, -2F, 4F, 8F, 4F).texOffs(388, 98).addBox(-1.5F, -15F, -1.5F, 3F, 7F, 3F).texOffs(464, 98).addBox(-1F, -21F, -1F, 2F, 6F, 2F), PartPose.offsetAndRotation(0F, -6.5F, 0F, 0.2F, 0F, 0.3F));
        p_orbit.addOrReplaceChild("rock1", CubeListBuilder.create().texOffs(76, 98).addBox(-5F, -5F, -5F, 10F, 8F, 10F), PartPose.offsetAndRotation(12F, -46F, 44F, 0.7F, 0.9F, 0.4F));
        p_orbit.addOrReplaceChild("rock2", CubeListBuilder.create().texOffs(156, 98).addBox(-4.5F, -4.5F, -4.5F, 9F, 7F, 9F), PartPose.offsetAndRotation(-31F, -126F, 26F, 0.4F, 1.8F, 0F));
        PartDefinition p_rock3 = p_orbit.addOrReplaceChild("rock3", CubeListBuilder.create().texOffs(358, 62).addBox(-6.5F, -6.5F, -6.5F, 13F, 10F, 13F), PartPose.offsetAndRotation(-41F, -71F, -15F, 0.1F, 2.7F, 0.4F));
        p_rock3.addOrReplaceChild("rock3_crystal", CubeListBuilder.create().texOffs(316, 98).addBox(-2F, -8F, -2F, 4F, 8F, 4F).texOffs(388, 98).addBox(-1.5F, -15F, -1.5F, 3F, 7F, 3F).texOffs(464, 98).addBox(-1F, -21F, -1F, 2F, 6F, 2F), PartPose.offsetAndRotation(0F, -6.5F, 0F, 0.2F, 0F, 0.3F));
        p_orbit.addOrReplaceChild("rock4", CubeListBuilder.create().texOffs(76, 98).addBox(-5F, -5F, -5F, 10F, 8F, 10F), PartPose.offsetAndRotation(-7F, -111F, -41F, 0.8F, 3.6F, 0F));
        PartDefinition p_rock5 = p_orbit.addOrReplaceChild("rock5", CubeListBuilder.create().texOffs(0, 98).addBox(-6F, -6F, -6F, 12F, 10F, 12F), PartPose.offsetAndRotation(35F, -31F, -30F, 0.5F, 4.5F, 0.4F));
        p_rock5.addOrReplaceChild("rock5_crystal", CubeListBuilder.create().texOffs(316, 98).addBox(-2F, -8F, -2F, 4F, 8F, 4F).texOffs(388, 98).addBox(-1.5F, -15F, -1.5F, 3F, 7F, 3F).texOffs(464, 98).addBox(-1F, -21F, -1F, 2F, 6F, 2F), PartPose.offsetAndRotation(0F, -6F, 0F, 0.2F, 0F, 0.3F));
        return LayerDefinition.create(mesh, 512, 128);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    private static float env(float p) {
        return Mth.sin((float) Math.PI * Mth.clamp(p, 0f, 1f));
    }

    private static float win(float p, float a, float b) {
        return p < a || p > b ? 0f : env((p - a) / (b - a));
    }

    /** Images clés {p0, v0, p1, v1, ...} avec transition douce (smoothstep) ; constante aux extrémités. */
    private static float kf(float p, float[] k) {
        if (p <= k[0]) return k[1];
        for (int i = 0; i + 3 < k.length; i += 2) {
            float a = k[i], va = k[i + 1], b = k[i + 2], vb = k[i + 3];
            if (p <= b) {
                float u = b > a ? (p - a) / (b - a) : 1f;
                u = u * u * (3f - 2f * u);
                return va + (vb - va) * u;
            }
        }
        return k[k.length - 1];
    }

    private static void scale(ModelPart p, float s) {
        p.xScale = s;
        p.yScale = s;
        p.zScale = s;
    }

    @Override
    public void setupAnim(T e, float limbSwing, float limbSwingAmount, float t, float netHeadYaw, float headPitch) {
        // ── Repos : il flotte, la robe ondule, l'auréole et les rochers tournent ──
        body.y = BODY_Y + 2.5f * Mth.sin(t * 0.05f);
        body.xRot = 0f;
        body.yRot = 0f;
        body.zRot = 0f;
        torso.xRot = 0.04f * Mth.sin(t * 0.04f);
        head.xRot = 0.05f * Mth.sin(t * 0.03f);
        head.yRot = 0.15f * Mth.sin(t * 0.017f);
        jaw.xRot = 0.03f + 0.03f * Mth.sin(t * 0.06f);
        halo.xRot = 0f;
        halo.zRot = t * 0.01f;
        scale(halo, 1f);
        orbit.yRot = t * 0.012f;
        for (int k = 0; k < rock.length; k++) rock[k].y = ROCK_Y[k] + 3f * Mth.sin(t * 0.04f + k * 1.3f);
        for (int k = 0; k < TATTERS; k++) {
            tatter[k].xRot = 0.12f * Mth.sin(t * 0.07f + k * 0.9f);
            tatter[k].zRot = 0.08f * Mth.sin(t * 0.05f + k * 1.7f);
        }
        for (int i = 0; i < 2; i++) {
            int sx = SIDE[i];
            arm[i].xRot = -0.1f + 0.05f * Mth.sin(t * 0.045f + sx);
            arm[i].yRot = 0f;
            arm[i].zRot = -sx * 0.22f;
            elbow[i].xRot = -0.45f + 0.05f * Mth.sin(t * 0.05f + sx);
            hand[i].xRot = 0.1f * Mth.sin(t * 0.06f + sx);
        }
        if (!(e instanceof TitanAnimated ta)) return;
        TitanAnimated.Pose pose = ta.titanPose();
        if (pose == null || pose == TitanAnimated.Pose.IDLE) return;
        float p = Mth.clamp(ta.titanPoseProgress(t), 0f, 1f);
        float en = env(p);
        switch (pose) {
            case ROAR -> {        // se redresse, bras écartés vers le ciel, le masque s'ouvre, l'auréole s'emballe
                head.xRot -= 0.4f * en;
                jaw.xRot += 0.5f * en;
                torso.xRot -= 0.2f * en;
                for (int i = 0; i < 2; i++) {
                    arm[i].xRot -= 0.6f * en;
                    arm[i].zRot -= SIDE[i] * 1.0f * en;
                    elbow[i].xRot += 0.3f * en;
                }
                halo.zRot += 3.0f * p;
                scale(halo, 1f + 0.15f * en);
            }
            case BEAM -> {        // les deux mains jointes en avant : le rayon en sort et balaie
                float sweep = Mth.clamp(p * 2.4f - 1.2f, -1f, 1f);
                body.yRot = 0.45f * sweep * en;
                for (int i = 0; i < 2; i++) {
                    arm[i].xRot -= 1.35f * en;
                    arm[i].zRot += SIDE[i] * 0.32f * en;
                    elbow[i].xRot += 0.4f * en;
                }
                head.xRot += 0.1f * en;
            }
            case SLAM -> {        // s'élève, puis abat les deux mains vers le sol
                body.y += kf(p, SLAM_RISE);
                float a = kf(p, SLAM_ARM);
                for (int i = 0; i < 2; i++) {
                    arm[i].xRot += a;
                    elbow[i].xRot += 0.3f * en;
                }
                torso.xRot += 0.3f * win(p, 0.5f, 1f);
                jaw.xRot += 0.35f * en;
            }
            case THROW -> {       // lève une main (les rochers s'y rassemblent), puis la jette en avant
                arm[0].xRot += kf(p, THROW_ARM);
                arm[0].zRot -= 0.25f * en;
                arm[1].xRot -= 0.6f * en;
                arm[1].zRot -= 0.4f * en;
                torso.xRot += kf(p, THROW_LEAN);
                body.yRot = -0.25f * en;
                orbit.yRot += 4.0f * p * p;
            }
            case SUMMON -> {      // bras grands ouverts, paumes vers le ciel, l'auréole grandit
                for (int i = 0; i < 2; i++) {
                    arm[i].xRot -= 0.35f * en;
                    arm[i].zRot -= SIDE[i] * 1.35f * en;
                    hand[i].xRot -= 0.6f * en;
                }
                head.xRot -= 0.3f * en;
                scale(halo, 1f + 0.45f * en);
                halo.zRot += 1.5f * p;
            }
            case CHARGE -> {      // se ramasse, puis fond sur l'île, penché en avant, bras rejetés en arrière
                float lean = kf(p, CHARGE_LEAN);
                float fw = Math.max(0f, lean);
                body.xRot = lean;
                for (int i = 0; i < 2; i++) {
                    arm[i].xRot += 1.1f * fw;
                    arm[i].zRot -= SIDE[i] * 0.25f * fw;
                }
                for (int k = 0; k < TATTERS; k++) tatter[k].xRot += 0.7f * fw;
                head.xRot -= 0.5f * fw;
            }
            case STUNNED -> {     // s'affaisse : tête basse, bras ballants, il descend, l'auréole penche
                body.y += 10f * en;
                torso.xRot += 0.35f * en;
                head.xRot += 0.45f * en;
                for (int i = 0; i < 2; i++) {
                    arm[i].xRot += 0.15f * en;
                    arm[i].zRot += SIDE[i] * 0.12f * en;
                    elbow[i].xRot += 0.4f * en;
                }
                halo.xRot = 0.5f * en;
                body.zRot = 0.08f * Mth.sin(t * 0.2f) * en;
            }
            case HURT -> {        // recule
                torso.xRot -= 0.25f * en;
                head.xRot -= 0.3f * en;
                jaw.xRot += 0.3f * en;
            }
            default -> { }
        }
    }
}
