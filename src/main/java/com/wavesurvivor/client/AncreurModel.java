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
 * ANCREUR (monstre de la Faille) : colosse voûté enchaîné, ancre d'obsidienne et d'améthyste sur le dos ; la plante devant lui pour enchaîner un joueur.
 * Généré par script (Claude chat, docs/faille/monstres/sources/) : ne pas retoucher à la main.
 * Dessiné à l'échelle 2 puis réduit de moitié (haute résolution). Texture 256x256 + calque lumineux.
 */
@OnlyIn(Dist.CLIENT)
public class AncreurModel<T extends Mob> extends HierarchicalModel<T> {

    public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(new ResourceLocation("wavesurvivor", "ancreur"), "main");

    private final ModelPart root;
    private final ModelPart pelvis, torso, head, armL, armR, foreL, foreR, legL, legR, anchorBack, anchorPlanted;
    private static final float TORSO = 0.35F;

    public AncreurModel(ModelPart root) {
        this.root = root;
        this.pelvis = root.getChild("pelvis");
        this.torso = pelvis.getChild("torso");
        this.head = torso.getChild("head");
        this.armL = torso.getChild("arm_l");
        this.armR = torso.getChild("arm_r");
        this.foreL = armL.getChild("fore");
        this.foreR = armR.getChild("fore");
        this.legL = pelvis.getChild("leg_l");
        this.legR = pelvis.getChild("leg_r");
        this.anchorBack = torso.getChild("anchor_back");
        this.anchorPlanted = root.getChild("anchor_planted");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_pelvis = root.addOrReplaceChild("pelvis", CubeListBuilder.create().texOffs(120, 76).addBox(-12F, -4F, -8F, 24F, 10F, 16F), PartPose.offsetAndRotation(0F, 6F, 0F, 0F, 0F, 0F));
        p_pelvis.addOrReplaceChild("leg_r", CubeListBuilder.create().texOffs(144, 46).addBox(-6F, 0F, -6F, 12F, 18F, 12F).texOffs(60, 102).addBox(-7F, 16F, -9F, 14F, 7F, 16F).texOffs(192, 102).addBox(-6.5F, 6F, -6.5F, 13F, 3F, 13F), PartPose.offsetAndRotation(-8F, 4F, 0F, 0F, 0F, 0F));
        p_pelvis.addOrReplaceChild("leg_l", CubeListBuilder.create().texOffs(144, 46).addBox(-6F, 0F, -6F, 12F, 18F, 12F).texOffs(60, 102).addBox(-7F, 16F, -9F, 14F, 7F, 16F).texOffs(192, 102).addBox(-6.5F, 6F, -6.5F, 13F, 3F, 13F), PartPose.offsetAndRotation(8F, 4F, 0F, 0F, 0F, 0F));
        PartDefinition p_torso = p_pelvis.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 46).addBox(-12F, -14F, -8F, 24F, 14F, 16F).texOffs(0, 0).addBox(-18F, -36F, -11F, 36F, 24F, 22F).texOffs(0, 76).addBox(-18.5F, -26F, -11.5F, 37F, 3F, 23F).texOffs(0, 76).addBox(-18.5F, -32F, -11.5F, 37F, 3F, 23F).texOffs(52, 127).addBox(-2F, -30F, -11.6F, 4F, 12F, 1F), PartPose.offsetAndRotation(0F, -4F, 0F, 0.35F, 0F, 0F));
        PartDefinition p_head = p_torso.addOrReplaceChild("head", CubeListBuilder.create().texOffs(200, 76).addBox(-7F, -12F, -8F, 14F, 13F, 13F).texOffs(172, 127).addBox(-8F, -9F, -10F, 16F, 3F, 4F).texOffs(228, 127).addBox(-5F, -6F, -8.6F, 3F, 2F, 1F).texOffs(228, 127).addBox(2F, -6F, -8.6F, 3F, 2F, 1F), PartPose.offsetAndRotation(0F, -34F, -9F, -0.25F, 0F, 0F));
        p_head.addOrReplaceChild("head_c", CubeListBuilder.create().texOffs(62, 127).addBox(-2F, -6F, -2F, 4F, 6F, 4F).texOffs(160, 127).addBox(-1.5F, -11F, -1.5F, 3F, 5F, 3F).texOffs(212, 127).addBox(-1F, -15F, -1F, 2F, 4F, 2F), PartPose.offsetAndRotation(0F, -12F, 1F, -0.4F, 0F, 0F));
        PartDefinition p_arm_r = p_torso.addOrReplaceChild("arm_r", CubeListBuilder.create().texOffs(80, 46).addBox(-8F, -8F, -8F, 16F, 14F, 16F).texOffs(136, 0).addBox(-6F, 4F, -6F, 12F, 20F, 12F), PartPose.offsetAndRotation(-20F, -30F, 0F, 0.2F, 0F, 0.15F));
        p_arm_r.addOrReplaceChild("fore", CubeListBuilder.create().texOffs(184, 0).addBox(-6.5F, 0F, -6.5F, 13F, 18F, 13F).texOffs(0, 102).addBox(-7.5F, 16F, -7.5F, 15F, 10F, 15F).texOffs(136, 102).addBox(-7F, 4F, -7F, 14F, 3F, 14F), PartPose.offsetAndRotation(0F, 22F, 0F, -0.3F, 0F, 0F));
        PartDefinition p_arm_l = p_torso.addOrReplaceChild("arm_l", CubeListBuilder.create().texOffs(80, 46).addBox(-8F, -8F, -8F, 16F, 14F, 16F).texOffs(136, 0).addBox(-6F, 4F, -6F, 12F, 20F, 12F), PartPose.offsetAndRotation(20F, -30F, 0F, -0.4F, 0F, -0.2F));
        p_arm_l.addOrReplaceChild("fore", CubeListBuilder.create().texOffs(184, 0).addBox(-6.5F, 0F, -6.5F, 13F, 18F, 13F).texOffs(0, 102).addBox(-7.5F, 16F, -7.5F, 15F, 10F, 15F).texOffs(136, 102).addBox(-7F, 4F, -7F, 14F, 3F, 14F), PartPose.offsetAndRotation(0F, 22F, 0F, -0.6F, 0F, 0F));
        p_torso.addOrReplaceChild("back_c0", CubeListBuilder.create().texOffs(62, 127).addBox(-2F, -6F, -2F, 4F, 6F, 4F).texOffs(160, 127).addBox(-1.5F, -11F, -1.5F, 3F, 5F, 3F).texOffs(220, 127).addBox(-1F, -14F, -1F, 2F, 3F, 2F), PartPose.offsetAndRotation(-9F, -36F, 6F, -0.5F, 0F, -0.3F));
        p_torso.addOrReplaceChild("back_c1", CubeListBuilder.create().texOffs(0, 127).addBox(-3F, -8F, -3F, 6F, 8F, 6F).texOffs(78, 127).addBox(-1.5F, -15F, -1.5F, 3F, 7F, 3F).texOffs(212, 127).addBox(-1F, -19F, -1F, 2F, 4F, 2F), PartPose.offsetAndRotation(0F, -36F, 6F, -0.5F, 0F, 0F));
        p_torso.addOrReplaceChild("back_c2", CubeListBuilder.create().texOffs(62, 127).addBox(-2F, -6F, -2F, 4F, 6F, 4F).texOffs(160, 127).addBox(-1.5F, -11F, -1.5F, 3F, 5F, 3F).texOffs(220, 127).addBox(-1F, -14F, -1F, 2F, 3F, 2F), PartPose.offsetAndRotation(9F, -36F, 6F, -0.5F, 0F, 0.3F));
        PartDefinition p_anchor_back = p_torso.addOrReplaceChild("anchor_back", CubeListBuilder.create().texOffs(116, 0).addBox(-2.5F, -36F, -2.5F, 5F, 40F, 5F).texOffs(112, 127).addBox(-10F, -32F, -2F, 20F, 4F, 4F).texOffs(90, 127).addBox(-4F, -42F, -1.5F, 8F, 6F, 3F).texOffs(24, 127).addBox(-3.5F, 2F, -3.5F, 7F, 6F, 7F), PartPose.offsetAndRotation(4F, -30F, 13F, 0.25F, 0F, -0.75F));
        PartDefinition p_anchor_back_fluke_m1 = p_anchor_back.addOrReplaceChild("anchor_back_fluke_-1", CubeListBuilder.create().texOffs(120, 102).addBox(-2F, -2F, -2F, 4F, 14F, 4F), PartPose.offsetAndRotation(-2F, 6F, 0F, 0F, 0F, -2.3F));
        p_anchor_back_fluke_m1.addOrReplaceChild("anchor_back_fluke_c-1", CubeListBuilder.create().texOffs(62, 127).addBox(-2F, -6F, -2F, 4F, 6F, 4F).texOffs(160, 127).addBox(-1.5F, -11F, -1.5F, 3F, 5F, 3F).texOffs(212, 127).addBox(-1F, -15F, -1F, 2F, 4F, 2F), PartPose.offsetAndRotation(0F, 13F, 0F, 3.1416F, 0F, 0F));
        PartDefinition p_anchor_back_fluke_1 = p_anchor_back.addOrReplaceChild("anchor_back_fluke_1", CubeListBuilder.create().texOffs(120, 102).addBox(-2F, -2F, -2F, 4F, 14F, 4F), PartPose.offsetAndRotation(2F, 6F, 0F, 0F, 0F, 2.3F));
        p_anchor_back_fluke_1.addOrReplaceChild("anchor_back_fluke_c1", CubeListBuilder.create().texOffs(62, 127).addBox(-2F, -6F, -2F, 4F, 6F, 4F).texOffs(160, 127).addBox(-1.5F, -11F, -1.5F, 3F, 5F, 3F).texOffs(212, 127).addBox(-1F, -15F, -1F, 2F, 4F, 2F), PartPose.offsetAndRotation(0F, 13F, 0F, 3.1416F, 0F, 0F));
        PartDefinition p_anchor_planted = root.addOrReplaceChild("anchor_planted", CubeListBuilder.create().texOffs(116, 0).addBox(-2.5F, -36F, -2.5F, 5F, 40F, 5F).texOffs(112, 127).addBox(-10F, -32F, -2F, 20F, 4F, 4F).texOffs(90, 127).addBox(-4F, -42F, -1.5F, 8F, 6F, 3F).texOffs(24, 127).addBox(-3.5F, 2F, -3.5F, 7F, 6F, 7F), PartPose.offsetAndRotation(0F, 38F, -36F, 0.12F, 0F, 0F));
        PartDefinition p_anchor_planted_fluke_m1 = p_anchor_planted.addOrReplaceChild("anchor_planted_fluke_-1", CubeListBuilder.create().texOffs(120, 102).addBox(-2F, -2F, -2F, 4F, 14F, 4F), PartPose.offsetAndRotation(-2F, 6F, 0F, 0F, 0F, -2.3F));
        p_anchor_planted_fluke_m1.addOrReplaceChild("anchor_planted_fluke_c-1", CubeListBuilder.create().texOffs(62, 127).addBox(-2F, -6F, -2F, 4F, 6F, 4F).texOffs(160, 127).addBox(-1.5F, -11F, -1.5F, 3F, 5F, 3F).texOffs(212, 127).addBox(-1F, -15F, -1F, 2F, 4F, 2F), PartPose.offsetAndRotation(0F, 13F, 0F, 3.1416F, 0F, 0F));
        PartDefinition p_anchor_planted_fluke_1 = p_anchor_planted.addOrReplaceChild("anchor_planted_fluke_1", CubeListBuilder.create().texOffs(120, 102).addBox(-2F, -2F, -2F, 4F, 14F, 4F), PartPose.offsetAndRotation(2F, 6F, 0F, 0F, 0F, 2.3F));
        p_anchor_planted_fluke_1.addOrReplaceChild("anchor_planted_fluke_c1", CubeListBuilder.create().texOffs(62, 127).addBox(-2F, -6F, -2F, 4F, 6F, 4F).texOffs(160, 127).addBox(-1.5F, -11F, -1.5F, 3F, 5F, 3F).texOffs(212, 127).addBox(-1F, -15F, -1F, 2F, 4F, 2F), PartPose.offsetAndRotation(0F, 13F, 0F, 3.1416F, 0F, 0F));
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
        // États : 0 = PORTE l'ancre sur le dos (marche), 1 = PLANTE (20 ticks : lève l'ancre puis l'abat devant lui),
        //         2 = TIENT (ancre plantée, chaîne tendue vers le joueur enchaîné ; la chaîne est dessinée par le rendu).
        legL.xRot = Mth.sin(w) * 0.55f * a;
        legR.xRot = -Mth.sin(w) * 0.55f * a;
        torso.zRot = Mth.sin(w) * 0.06f * a;
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD * 0.5f;
        armL.xRot += -Mth.sin(w) * 0.4f * a;
        // COUP NORMAL (attaque au corps à corps, attackTime fourni par le rendu) : grand revers du poing droit
        float atk = this.attackTime;
        if (atk > 0f && st == 0) {
            float s = Mth.sin(atk * (float) Math.PI);
            armR.xRot += -1.9f * s;
            armR.zRot += 0.35f * s;
            foreR.xRot += -0.5f * s;
            torso.yRot = -0.35f * s;
            torso.xRot += 0.15f * s;
        }
        boolean planted = st == 2 || (st == 1 && p > 0.55f);
        anchorBack.visible = !planted;
        anchorPlanted.visible = planted;
        if (st == 1) {
            float up = smooth(p / 0.45f), down = smooth((p - 0.45f) / 0.15f);
            float arm = -2.6f * up + 1.4f * down;                 // lève au-dessus de la tête puis abat
            armL.xRot = arm; armR.xRot = arm;
            armL.zRot = -0.25f; armR.zRot = 0.25f;
            foreL.xRot = -0.2f; foreR.xRot = -0.2f;
            torso.xRot = TORSO - 0.2f * up + 0.6f * down;
        } else if (st == 2) {
            torso.xRot = 0.75f;
            armL.xRot = -1.2f; armR.xRot = -1.2f;
            armL.zRot = -0.25f; armR.zRot = 0.25f;
            foreL.xRot = -0.2f; foreR.xRot = -0.2f;
            torso.xRot += 0.03f * Mth.sin(t * 0.5f);              // tire sur la chaîne
        }
    }
}
