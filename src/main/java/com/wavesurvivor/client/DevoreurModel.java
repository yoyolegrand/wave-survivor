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
 * 2.0 — MODÈLE DU DÉVOREUR, Titan de la première Faille (conçu sur mesure par Claude chat, NE PAS régénérer à la main :
 * le modèle, la texture et les aperçus viennent d'un même script, voir docs/faille/titans/devoreur/).
 * Serpent colossal : queue qui pend dans le vide (sous le sol de l'entité), 16 segments en S (chacun ENFANT du précédent :
 * les rotations s'additionnent, d'où l'ondulation), écailles d'obsidienne, ventre en plaques, anneau de veine turquoise
 * à chaque segment, crêtes de cristal sur le dos, tête à cornes et collerette, mâchoire articulée.
 * Texture 512x256 + calque lumineux (anneaux, yeux, cristaux, gueule). Taille naturelle ~10 blocs au-dessus du sol
 * (agrandi ensuite par Pehkui). Avant = -Z, comme toutes les entités.
 * Animations : ondulation permanente (vague qui remonte le corps), respiration de la mâchoire, et 8 POSES de compétence
 * lues sur l'entité si elle implémente {@link TitanAnimated} (purement visuelles : aucune compétence n'en dépend).
 */
@OnlyIn(Dist.CLIENT)
public class DevoreurModel<T extends Mob> extends HierarchicalModel<T> {

    public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(new ResourceLocation("wavesurvivor", "devoreur"), "main");

    private static final float[] REST_X = {0.25F, 0.18F, 0.1F, -0.06F, -0.1F, -0.12F, -0.1F, -0.04F, 0.02F, 0.08F, 0.13F, 0.17F, 0.19F, 0.21F, 0.22F, 0.25F};
    private static final float[] REST_Z = {0F, 0F, -0.01F, 0.08F, 0.1F, 0.08F, 0.02F, -0.1F, -0.16F, -0.16F, -0.1F, 0.06F, 0.1F, 0.08F, 0.04F, -0.02F};
    private static final float HEAD_X = -0.78F;
    private static final float JAW_X = 0.12F;

    private final ModelPart root, head, jaw;
    private final ModelPart[] segs = new ModelPart[16];

    public DevoreurModel(ModelPart root) {
        this.root = root;
        segs[0] = root.getChild("tail0");
        segs[1] = segs[0].getChild("tail1");
        segs[2] = segs[1].getChild("tail2");
        segs[3] = segs[2].getChild("seg0");
        segs[4] = segs[3].getChild("seg1");
        segs[5] = segs[4].getChild("seg2");
        segs[6] = segs[5].getChild("seg3");
        segs[7] = segs[6].getChild("seg4");
        segs[8] = segs[7].getChild("seg5");
        segs[9] = segs[8].getChild("seg6");
        segs[10] = segs[9].getChild("seg7");
        segs[11] = segs[10].getChild("seg8");
        segs[12] = segs[11].getChild("seg9");
        segs[13] = segs[12].getChild("seg10");
        segs[14] = segs[13].getChild("seg11");
        segs[15] = segs[14].getChild("neck");
        this.head = segs[15].getChild("head");
        this.jaw = head.getChild("jaw");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_tail0 = root.addOrReplaceChild("tail0", CubeListBuilder.create().texOffs(154, 164).addBox(-5F, -12F, -5F, 10F, 12F, 10F), PartPose.offsetAndRotation(0F, 90F, 60F, 0.25F, 0F, 0F));
        PartDefinition p_tail1 = p_tail0.addOrReplaceChild("tail1", CubeListBuilder.create().texOffs(294, 129).addBox(-8.5F, -12F, -8F, 17F, 12F, 16F), PartPose.offsetAndRotation(0F, -12F, 0F, 0.18F, 0F, 0F));
        PartDefinition p_tail2 = p_tail1.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(110, 129).addBox(-12F, -13F, -11F, 24F, 13F, 22F), PartPose.offsetAndRotation(0F, -12F, 0F, 0.1F, 0F, -0.01F));
        PartDefinition p_seg0 = p_tail2.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(348, 46).addBox(-15F, -14F, -13.5F, 30F, 14F, 27F), PartPose.offsetAndRotation(0F, -13F, 0F, -0.06F, 0F, 0.08F));
        p_seg0.addOrReplaceChild("seg0_crest", CubeListBuilder.create().texOffs(286, 164).addBox(-3.5F, -11F, -3.5F, 7F, 11F, 7F).texOffs(402, 164).addBox(-2F, -21F, -2F, 4F, 10F, 4F).texOffs(76, 188).addBox(-1F, -28F, -1F, 2F, 7F, 2F), PartPose.offsetAndRotation(0F, -7.7F, 12.5F, -0.75F, 0F, 0F));
        PartDefinition p_seg1 = p_seg0.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(268, 0).addBox(-17F, -14F, -15F, 34F, 14F, 30F), PartPose.offsetAndRotation(0F, -14F, 0F, -0.1F, 0F, 0.1F));
        PartDefinition p_seg2 = p_seg1.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(0, 0).addBox(-18F, -14F, -16F, 36F, 14F, 32F), PartPose.offsetAndRotation(0F, -14F, 0F, -0.12F, 0F, 0.08F));
        p_seg2.addOrReplaceChild("seg2_crest", CubeListBuilder.create().texOffs(226, 164).addBox(-4F, -13F, -4F, 8F, 13F, 8F).texOffs(358, 164).addBox(-2.5F, -24F, -2.5F, 5F, 11F, 5F).texOffs(44, 188).addBox(-1F, -32F, -1F, 2F, 8F, 2F), PartPose.offsetAndRotation(0F, -7.7F, 15F, -0.75F, 0F, 0F));
        PartDefinition p_seg3 = p_seg2.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(0, 0).addBox(-18F, -14F, -16F, 36F, 14F, 32F), PartPose.offsetAndRotation(0F, -14F, 0F, -0.1F, 0F, 0.02F));
        PartDefinition p_seg4 = p_seg3.addOrReplaceChild("seg4", CubeListBuilder.create().texOffs(136, 0).addBox(-17.5F, -14F, -15.5F, 35F, 14F, 31F), PartPose.offsetAndRotation(0F, -14F, 0F, -0.04F, 0F, -0.1F));
        p_seg4.addOrReplaceChild("seg4_crest", CubeListBuilder.create().texOffs(226, 164).addBox(-4F, -13F, -4F, 8F, 13F, 8F).texOffs(358, 164).addBox(-2.5F, -24F, -2.5F, 5F, 11F, 5F).texOffs(44, 188).addBox(-1F, -32F, -1F, 2F, 8F, 2F), PartPose.offsetAndRotation(0F, -7.7F, 14.5F, -0.75F, 0F, 0F));
        PartDefinition p_seg5 = p_seg4.addOrReplaceChild("seg5", CubeListBuilder.create().texOffs(268, 0).addBox(-17F, -14F, -15F, 34F, 14F, 30F), PartPose.offsetAndRotation(0F, -14F, 0F, 0.02F, 0F, -0.16F));
        PartDefinition p_seg6 = p_seg5.addOrReplaceChild("seg6", CubeListBuilder.create().texOffs(0, 46).addBox(-16F, -14F, -14.5F, 32F, 14F, 29F), PartPose.offsetAndRotation(0F, -14F, 0F, 0.08F, 0F, -0.16F));
        p_seg6.addOrReplaceChild("seg6_crest", CubeListBuilder.create().texOffs(258, 164).addBox(-3.5F, -12F, -3.5F, 7F, 12F, 7F).texOffs(402, 164).addBox(-2F, -22F, -2F, 4F, 10F, 4F).texOffs(76, 188).addBox(-1F, -29F, -1F, 2F, 7F, 2F), PartPose.offsetAndRotation(0F, -7.7F, 13.5F, -0.75F, 0F, 0F));
        PartDefinition p_seg7 = p_seg6.addOrReplaceChild("seg7", CubeListBuilder.create().texOffs(230, 46).addBox(-15.5F, -14F, -14F, 31F, 14F, 28F), PartPose.offsetAndRotation(0F, -14F, 0F, 0.13F, 0F, -0.1F));
        PartDefinition p_seg8 = p_seg7.addOrReplaceChild("seg8", CubeListBuilder.create().texOffs(0, 89).addBox(-14.5F, -14F, -13F, 29F, 14F, 26F), PartPose.offsetAndRotation(0F, -14F, 0F, 0.17F, 0F, 0.06F));
        p_seg8.addOrReplaceChild("seg8_crest", CubeListBuilder.create().texOffs(286, 164).addBox(-3.5F, -11F, -3.5F, 7F, 11F, 7F).texOffs(402, 164).addBox(-2F, -21F, -2F, 4F, 10F, 4F).texOffs(76, 188).addBox(-1F, -28F, -1F, 2F, 7F, 2F), PartPose.offsetAndRotation(0F, -7.7F, 12F, -0.75F, 0F, 0F));
        PartDefinition p_seg9 = p_seg8.addOrReplaceChild("seg9", CubeListBuilder.create().texOffs(110, 89).addBox(-13.5F, -14F, -12.5F, 27F, 14F, 25F), PartPose.offsetAndRotation(0F, -14F, 0F, 0.19F, 0F, 0.1F));
        PartDefinition p_seg10 = p_seg9.addOrReplaceChild("seg10", CubeListBuilder.create().texOffs(214, 89).addBox(-13F, -13F, -12F, 26F, 13F, 24F), PartPose.offsetAndRotation(0F, -14F, 0F, 0.21F, 0F, 0.08F));
        p_seg10.addOrReplaceChild("seg10_crest", CubeListBuilder.create().texOffs(286, 164).addBox(-3.5F, -11F, -3.5F, 7F, 11F, 7F).texOffs(0, 188).addBox(-2F, -20F, -2F, 4F, 9F, 4F).texOffs(76, 188).addBox(-1F, -27F, -1F, 2F, 7F, 2F), PartPose.offsetAndRotation(0F, -7.15F, 11F, -0.75F, 0F, 0F));
        PartDefinition p_seg11 = p_seg10.addOrReplaceChild("seg11", CubeListBuilder.create().texOffs(406, 89).addBox(-12.5F, -13F, -11.5F, 25F, 13F, 23F), PartPose.offsetAndRotation(0F, -13F, 0F, 0.22F, 0F, 0.04F));
        PartDefinition p_neck = p_seg11.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(202, 129).addBox(-12F, -12F, -11F, 24F, 12F, 22F), PartPose.offsetAndRotation(0F, -13F, 0F, 0.25F, 0F, -0.02F));
        p_neck.addOrReplaceChild("neck_crest", CubeListBuilder.create().texOffs(334, 164).addBox(-3F, -10F, -3F, 6F, 10F, 6F).texOffs(0, 188).addBox(-2F, -19F, -2F, 4F, 9F, 4F).texOffs(96, 188).addBox(-1F, -25F, -1F, 2F, 6F, 2F), PartPose.offsetAndRotation(0F, -6.6F, 10F, -0.75F, 0F, 0F));
        PartDefinition p_head = p_neck.addOrReplaceChild("head", CubeListBuilder.create().texOffs(122, 46).addBox(-16F, -18F, -13F, 31F, 20F, 23F).texOffs(314, 89).addBox(-12F, -13F, -36F, 23F, 14F, 23F).texOffs(82, 164).addBox(-9F, -17F, -34F, 18F, 4F, 18F).texOffs(418, 164).addBox(-17F, -21F, -20F, 34F, 4F, 9F).texOffs(52, 188).addBox(-17F, -14F, -17F, 1F, 4F, 5F).texOffs(104, 188).addBox(-17F, -9F, -9F, 1F, 3F, 4F).texOffs(114, 188).addBox(-12F, -14F, -35F, 1F, 3F, 3F).texOffs(84, 188).addBox(-11F, 1F, -34F, 3F, 5F, 3F).texOffs(84, 188).addBox(-11F, 1F, -29F, 3F, 5F, 3F).texOffs(84, 188).addBox(-11F, 1F, -24F, 3F, 5F, 3F).texOffs(84, 188).addBox(-11F, 1F, -20F, 3F, 5F, 3F).texOffs(84, 188).addBox(-11F, 1F, -15F, 3F, 5F, 3F).texOffs(52, 188).addBox(16F, -14F, -17F, 1F, 4F, 5F).texOffs(104, 188).addBox(16F, -9F, -9F, 1F, 3F, 4F).texOffs(114, 188).addBox(11F, -14F, -35F, 1F, 3F, 3F).texOffs(84, 188).addBox(8F, 1F, -34F, 3F, 5F, 3F).texOffs(84, 188).addBox(8F, 1F, -29F, 3F, 5F, 3F).texOffs(84, 188).addBox(8F, 1F, -24F, 3F, 5F, 3F).texOffs(84, 188).addBox(8F, 1F, -20F, 3F, 5F, 3F).texOffs(84, 188).addBox(8F, 1F, -15F, 3F, 5F, 3F).texOffs(360, 129).addBox(-9F, 0F, -34F, 18F, 1F, 23F), PartPose.offsetAndRotation(0F, -12F, 0F, -0.78F, 0F, 0F));
        p_head.addOrReplaceChild("horn_l", CubeListBuilder.create().texOffs(194, 164).addBox(-4F, -14F, -4F, 8F, 14F, 8F).texOffs(314, 164).addBox(-2.5F, -26F, -2.5F, 5F, 12F, 5F).texOffs(44, 188).addBox(-1F, -34F, -1F, 2F, 8F, 2F), PartPose.offsetAndRotation(-10F, -18F, 3F, -1.05F, 0F, -0.42F));
        p_head.addOrReplaceChild("horn_r", CubeListBuilder.create().texOffs(194, 164).addBox(-4F, -14F, -4F, 8F, 14F, 8F).texOffs(314, 164).addBox(-2.5F, -26F, -2.5F, 5F, 12F, 5F).texOffs(44, 188).addBox(-1F, -34F, -1F, 2F, 8F, 2F), PartPose.offsetAndRotation(10F, -18F, 3F, -1.05F, 0F, 0.42F));
        p_head.addOrReplaceChild("frill0", CubeListBuilder.create().texOffs(16, 188).addBox(-2F, -7F, -2F, 4F, 7F, 4F).texOffs(64, 188).addBox(-1.5F, -13F, -1.5F, 3F, 6F, 3F).texOffs(122, 188).addBox(-1F, -17F, -1F, 2F, 4F, 2F), PartPose.offsetAndRotation(-10F, -15F, 9F, -1.25F, 0F, -0.64F));
        p_head.addOrReplaceChild("frill1", CubeListBuilder.create().texOffs(378, 164).addBox(-3F, -9F, -3F, 6F, 9F, 6F).texOffs(32, 188).addBox(-1.5F, -17F, -1.5F, 3F, 8F, 3F).texOffs(96, 188).addBox(-1F, -23F, -1F, 2F, 6F, 2F), PartPose.offsetAndRotation(-5F, -15F, 9F, -1.25F, 0F, -0.32F));
        p_head.addOrReplaceChild("frill2", CubeListBuilder.create().texOffs(334, 164).addBox(-3F, -10F, -3F, 6F, 10F, 6F).texOffs(0, 188).addBox(-2F, -19F, -2F, 4F, 9F, 4F).texOffs(96, 188).addBox(-1F, -25F, -1F, 2F, 6F, 2F), PartPose.offsetAndRotation(0F, -15F, 9F, -1.25F, 0F, 0F));
        p_head.addOrReplaceChild("frill3", CubeListBuilder.create().texOffs(378, 164).addBox(-3F, -9F, -3F, 6F, 9F, 6F).texOffs(32, 188).addBox(-1.5F, -17F, -1.5F, 3F, 8F, 3F).texOffs(96, 188).addBox(-1F, -23F, -1F, 2F, 6F, 2F), PartPose.offsetAndRotation(5F, -15F, 9F, -1.25F, 0F, 0.32F));
        p_head.addOrReplaceChild("frill4", CubeListBuilder.create().texOffs(16, 188).addBox(-2F, -7F, -2F, 4F, 7F, 4F).texOffs(64, 188).addBox(-1.5F, -13F, -1.5F, 3F, 6F, 3F).texOffs(122, 188).addBox(-1F, -17F, -1F, 2F, 4F, 2F), PartPose.offsetAndRotation(10F, -15F, 9F, -1.25F, 0F, 0.64F));
        p_head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(0, 129).addBox(-13F, 0F, -26F, 26F, 6F, 29F).texOffs(0, 164).addBox(-9F, -1F, -25F, 18F, 1F, 23F).texOffs(84, 188).addBox(-11F, -5F, -23F, 3F, 5F, 3F).texOffs(84, 188).addBox(-11F, -5F, -19F, 3F, 5F, 3F).texOffs(84, 188).addBox(-11F, -5F, -14F, 3F, 5F, 3F).texOffs(84, 188).addBox(-11F, -5F, -9F, 3F, 5F, 3F).texOffs(84, 188).addBox(-11F, -5F, -5F, 3F, 5F, 3F).texOffs(84, 188).addBox(8F, -5F, -23F, 3F, 5F, 3F).texOffs(84, 188).addBox(8F, -5F, -19F, 3F, 5F, 3F).texOffs(84, 188).addBox(8F, -5F, -14F, 3F, 5F, 3F).texOffs(84, 188).addBox(8F, -5F, -9F, 3F, 5F, 3F).texOffs(84, 188).addBox(8F, -5F, -5F, 3F, 5F, 3F), PartPose.offsetAndRotation(0F, 1F, -10F, 0.12F, 0F, 0F));
        return LayerDefinition.create(mesh, 512, 256);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    private static float env(float p) {
        return Mth.sin((float) Math.PI * Mth.clamp(p, 0f, 1f));
    }

    /** Fenêtre [a, b] de l'avancement p : 0 hors fenêtre, cloche de 0 à 1 à 0 dedans. */
    private static float win(float p, float a, float b) {
        return p < a || p > b ? 0f : env((p - a) / (b - a));
    }

    @Override
    public void setupAnim(T e, float limbSwing, float limbSwingAmount, float t, float netHeadYaw, float headPitch) {
        int n = segs.length;
        // ── Ondulation permanente : une vague lente qui remonte le corps (plus ample vers la tête) ──
        for (int i = 0; i < n; i++) {
            float k = (float) i / (n - 1);
            segs[i].xRot = REST_X[i] + 0.035f * Mth.sin(t * 0.06f - i * 0.55f) * (0.4f + k);
            segs[i].zRot = REST_Z[i] + 0.05f * Mth.sin(t * 0.045f - i * 0.5f) * (0.5f + k);
            segs[i].yRot = 0.03f * Mth.sin(t * 0.03f - i * 0.4f);
        }
        head.xRot = HEAD_X + 0.05f * Mth.sin(t * 0.07f);
        head.yRot = 0f;
        head.zRot = 0f;
        jaw.xRot = JAW_X + 0.06f * Mth.sin(t * 0.09f);
        if (!(e instanceof TitanAnimated ta)) return;
        TitanAnimated.Pose pose = ta.titanPose();
        if (pose == null || pose == TitanAnimated.Pose.IDLE) return;
        float p = Mth.clamp(ta.titanPoseProgress(t), 0f, 1f);
        float en = env(p);
        int up = (int) (n * 0.6f);
        switch (pose) {
            case ROAR -> {        // se dresse, tête levée, gueule grande ouverte, tremblement
                for (int i = up; i < n; i++) {
                    segs[i].xRot -= 0.09f * en;
                    segs[i].zRot += 0.015f * Mth.sin(t * 1.7f + i) * en;
                }
                head.xRot -= 0.55f * en;
                jaw.xRot += 0.75f * en;
            }
            case BEAM -> {        // rayon : tête baissée, gueule ouverte, balayage de gauche à droite
                float sweep = Mth.clamp(p * 2.4f - 1.2f, -1f, 1f);
                for (int i = n - 6; i < n; i++) segs[i].zRot += 0.06f * sweep * en;
                head.xRot += 0.18f * en;
                head.yRot += 0.35f * sweep * en;
                jaw.xRot += 0.55f * en;
            }
            case SLAM -> {        // onde de choc : se cabre, puis frappe vers l'avant
                float c = -0.10f * win(p, 0f, 0.55f) + 0.20f * win(p, 0.5f, 1f);
                for (int i = n / 2; i < n; i++) segs[i].xRot += c;
                head.xRot += 2f * c;
                jaw.xRot += 0.4f * win(p, 0.5f, 1f);
            }
            case THROW -> {       // météores : recul de la tête, puis crache vers l'avant
                float c = -0.40f * win(p, 0f, 0.6f) + 0.50f * win(p, 0.6f, 1f);
                for (int i = n - 4; i < n; i++) segs[i].xRot += 0.15f * c;
                head.xRot += c;
                jaw.xRot += 0.7f * win(p, 0.55f, 1f);
            }
            case SUMMON -> {      // avatars : se redresse, gueule entrouverte
                for (int i = 0; i < n; i++) segs[i].xRot -= REST_X[i] * 0.5f * en;
                head.xRot -= 0.3f * en;
                jaw.xRot += 0.5f * en;
            }
            case CHARGE -> {      // charge : se ramasse en arrière (annonce), puis se jette en avant gueule ouverte
                float coil = win(p, 0f, 0.7f), lunge = win(p, 0.55f, 1f);
                for (int i = n / 2; i < n; i++) segs[i].xRot += -0.12f * coil + 0.22f * lunge;
                head.xRot += -0.30f * coil + 0.35f * lunge;
                jaw.xRot += 0.15f * coil + 0.8f * win(p, 0.6f, 1f);
            }
            case STUNNED -> {     // étourdi : le corps s'affaisse, la tête pend, la mâchoire est molle
                for (int i = n / 2; i < n; i++) {
                    segs[i].xRot += 0.06f * en;
                    segs[i].zRot += (0.04f + 0.02f * Mth.sin(t * 0.3f + i * 0.4f)) * en;
                }
                head.xRot += 0.5f * en;
                jaw.xRot += 0.35f * en;
            }
            case HURT -> {        // sursaut
                for (int i = up; i < n; i++) segs[i].zRot += 0.05f * Mth.sin(p * 20f) * en;
                head.xRot -= 0.15f * en;
                jaw.xRot += 0.3f * en;
            }
            default -> { }
        }
    }
}
