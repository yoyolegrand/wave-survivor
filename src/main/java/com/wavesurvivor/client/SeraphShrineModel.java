package com.wavesurvivor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * MODÈLE DU SANCTUAIRE DU SÉRAPHIN (bloc, conçu sur mesure, HAUTE RÉSOLUTION ×2 : dessiné à l'échelle 2 puis réduit).
 * Socle de marbre, déesse encapuchonnée (visage caché dans un voile d'ombre, seuls les yeux luisent) tenant un orbe, TROIS PAIRES D'AILES (haute, moyenne, basse), halo de 20 segments.
 * Texture 1024x512, une par étape de corruption (0 = pure … 4 = corrompue) + calque lumineux.
 * Animations : halo qui tourne, ailes qui respirent, orbe qui flotte. La corruption fait retomber les ailes,
 * arrache des plumes et brise le halo.
 */
@OnlyIn(Dist.CLIENT)
public class SeraphShrineModel extends Model {

    public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(new ResourceLocation("wavesurvivor", "seraph_shrine"), "main");
    private static final String[] WING_ROOTS = {"upLr", "midLr", "lowLr", "upRr", "midRr", "lowRr"};
    private static final int[] HALO_BROKEN_3 = {3, 12};
    private static final int[] HALO_BROKEN_4 = {2, 3, 7, 11, 12, 16};

    private final ModelPart root, halo, orb;
    private final ModelPart[] wings = new ModelPart[WING_ROOTS.length];
    private final ModelPart[] haloSeg = new ModelPart[20];
    private final List<ModelPart> feathers = new ArrayList<>();

    public SeraphShrineModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
        this.halo = root.getChild("halo");
        this.orb = root.getChild("statue").getChild("orb");
        for (int i = 0; i < WING_ROOTS.length; i++) {
            wings[i] = root.getChild(WING_ROOTS[i]);
        }
        // Plumes (uniquement les plumes, pas les segments d'ailes qui les portent)
        feathers.add(root.getChild("upLr").getChild("upLr_f0"));
        feathers.add(root.getChild("upLr").getChild("upLr_f1"));
        feathers.add(root.getChild("upLr").getChild("upLr_f2"));
        feathers.add(root.getChild("upLr").getChild("upLr_f3"));
        feathers.add(root.getChild("upLr").getChild("upLr_f4"));
        feathers.add(root.getChild("upLr").getChild("upLm").getChild("upLm_f0"));
        feathers.add(root.getChild("upLr").getChild("upLm").getChild("upLm_f1"));
        feathers.add(root.getChild("upLr").getChild("upLm").getChild("upLm_f2"));
        feathers.add(root.getChild("upLr").getChild("upLm").getChild("upLm_f3"));
        feathers.add(root.getChild("upLr").getChild("upLm").getChild("upLm_f4"));
        feathers.add(root.getChild("upLr").getChild("upLm").getChild("upLm_f5"));
        feathers.add(root.getChild("upLr").getChild("upLm").getChild("upLm_f6"));
        feathers.add(root.getChild("upLr").getChild("upLm").getChild("upLm_f7"));
        feathers.add(root.getChild("upLr").getChild("upLm").getChild("upLm_f8"));
        feathers.add(root.getChild("upLr").getChild("upLm").getChild("upLt").getChild("upLt_f0"));
        feathers.add(root.getChild("upLr").getChild("upLm").getChild("upLt").getChild("upLt_f1"));
        feathers.add(root.getChild("upLr").getChild("upLm").getChild("upLt").getChild("upLt_f2"));
        feathers.add(root.getChild("upLr").getChild("upLm").getChild("upLt").getChild("upLt_f3"));
        feathers.add(root.getChild("upLr").getChild("upLm").getChild("upLt").getChild("upLt_f4"));
        feathers.add(root.getChild("upLr").getChild("upLm").getChild("upLt").getChild("upLt_f5"));
        feathers.add(root.getChild("upLr").getChild("upLm").getChild("upLt").getChild("upLt_f6"));
        feathers.add(root.getChild("upLr").getChild("upLm").getChild("upLt").getChild("upLt_f7"));
        feathers.add(root.getChild("midLr").getChild("midLr_f0"));
        feathers.add(root.getChild("midLr").getChild("midLr_f1"));
        feathers.add(root.getChild("midLr").getChild("midLr_f2"));
        feathers.add(root.getChild("midLr").getChild("midLr_f3"));
        feathers.add(root.getChild("midLr").getChild("midLr_f4"));
        feathers.add(root.getChild("midLr").getChild("midLm").getChild("midLm_f0"));
        feathers.add(root.getChild("midLr").getChild("midLm").getChild("midLm_f1"));
        feathers.add(root.getChild("midLr").getChild("midLm").getChild("midLm_f2"));
        feathers.add(root.getChild("midLr").getChild("midLm").getChild("midLm_f3"));
        feathers.add(root.getChild("midLr").getChild("midLm").getChild("midLm_f4"));
        feathers.add(root.getChild("midLr").getChild("midLm").getChild("midLm_f5"));
        feathers.add(root.getChild("midLr").getChild("midLm").getChild("midLm_f6"));
        feathers.add(root.getChild("midLr").getChild("midLm").getChild("midLm_f7"));
        feathers.add(root.getChild("midLr").getChild("midLm").getChild("midLm_f8"));
        feathers.add(root.getChild("midLr").getChild("midLm").getChild("midLt").getChild("midLt_f0"));
        feathers.add(root.getChild("midLr").getChild("midLm").getChild("midLt").getChild("midLt_f1"));
        feathers.add(root.getChild("midLr").getChild("midLm").getChild("midLt").getChild("midLt_f2"));
        feathers.add(root.getChild("midLr").getChild("midLm").getChild("midLt").getChild("midLt_f3"));
        feathers.add(root.getChild("midLr").getChild("midLm").getChild("midLt").getChild("midLt_f4"));
        feathers.add(root.getChild("midLr").getChild("midLm").getChild("midLt").getChild("midLt_f5"));
        feathers.add(root.getChild("midLr").getChild("midLm").getChild("midLt").getChild("midLt_f6"));
        feathers.add(root.getChild("midLr").getChild("midLm").getChild("midLt").getChild("midLt_f7"));
        feathers.add(root.getChild("lowLr").getChild("lowLr_f0"));
        feathers.add(root.getChild("lowLr").getChild("lowLr_f1"));
        feathers.add(root.getChild("lowLr").getChild("lowLr_f2"));
        feathers.add(root.getChild("lowLr").getChild("lowLr_f3"));
        feathers.add(root.getChild("lowLr").getChild("lowLr_f4"));
        feathers.add(root.getChild("lowLr").getChild("lowLm").getChild("lowLm_f0"));
        feathers.add(root.getChild("lowLr").getChild("lowLm").getChild("lowLm_f1"));
        feathers.add(root.getChild("lowLr").getChild("lowLm").getChild("lowLm_f2"));
        feathers.add(root.getChild("lowLr").getChild("lowLm").getChild("lowLm_f3"));
        feathers.add(root.getChild("lowLr").getChild("lowLm").getChild("lowLm_f4"));
        feathers.add(root.getChild("lowLr").getChild("lowLm").getChild("lowLm_f5"));
        feathers.add(root.getChild("lowLr").getChild("lowLm").getChild("lowLm_f6"));
        feathers.add(root.getChild("lowLr").getChild("lowLm").getChild("lowLm_f7"));
        feathers.add(root.getChild("lowLr").getChild("lowLm").getChild("lowLm_f8"));
        feathers.add(root.getChild("lowLr").getChild("lowLm").getChild("lowLt").getChild("lowLt_f0"));
        feathers.add(root.getChild("lowLr").getChild("lowLm").getChild("lowLt").getChild("lowLt_f1"));
        feathers.add(root.getChild("lowLr").getChild("lowLm").getChild("lowLt").getChild("lowLt_f2"));
        feathers.add(root.getChild("lowLr").getChild("lowLm").getChild("lowLt").getChild("lowLt_f3"));
        feathers.add(root.getChild("lowLr").getChild("lowLm").getChild("lowLt").getChild("lowLt_f4"));
        feathers.add(root.getChild("lowLr").getChild("lowLm").getChild("lowLt").getChild("lowLt_f5"));
        feathers.add(root.getChild("lowLr").getChild("lowLm").getChild("lowLt").getChild("lowLt_f6"));
        feathers.add(root.getChild("lowLr").getChild("lowLm").getChild("lowLt").getChild("lowLt_f7"));
        feathers.add(root.getChild("upRr").getChild("upRr_f0"));
        feathers.add(root.getChild("upRr").getChild("upRr_f1"));
        feathers.add(root.getChild("upRr").getChild("upRr_f2"));
        feathers.add(root.getChild("upRr").getChild("upRr_f3"));
        feathers.add(root.getChild("upRr").getChild("upRr_f4"));
        feathers.add(root.getChild("upRr").getChild("upRm").getChild("upRm_f0"));
        feathers.add(root.getChild("upRr").getChild("upRm").getChild("upRm_f1"));
        feathers.add(root.getChild("upRr").getChild("upRm").getChild("upRm_f2"));
        feathers.add(root.getChild("upRr").getChild("upRm").getChild("upRm_f3"));
        feathers.add(root.getChild("upRr").getChild("upRm").getChild("upRm_f4"));
        feathers.add(root.getChild("upRr").getChild("upRm").getChild("upRm_f5"));
        feathers.add(root.getChild("upRr").getChild("upRm").getChild("upRm_f6"));
        feathers.add(root.getChild("upRr").getChild("upRm").getChild("upRm_f7"));
        feathers.add(root.getChild("upRr").getChild("upRm").getChild("upRm_f8"));
        feathers.add(root.getChild("upRr").getChild("upRm").getChild("upRt").getChild("upRt_f0"));
        feathers.add(root.getChild("upRr").getChild("upRm").getChild("upRt").getChild("upRt_f1"));
        feathers.add(root.getChild("upRr").getChild("upRm").getChild("upRt").getChild("upRt_f2"));
        feathers.add(root.getChild("upRr").getChild("upRm").getChild("upRt").getChild("upRt_f3"));
        feathers.add(root.getChild("upRr").getChild("upRm").getChild("upRt").getChild("upRt_f4"));
        feathers.add(root.getChild("upRr").getChild("upRm").getChild("upRt").getChild("upRt_f5"));
        feathers.add(root.getChild("upRr").getChild("upRm").getChild("upRt").getChild("upRt_f6"));
        feathers.add(root.getChild("upRr").getChild("upRm").getChild("upRt").getChild("upRt_f7"));
        feathers.add(root.getChild("midRr").getChild("midRr_f0"));
        feathers.add(root.getChild("midRr").getChild("midRr_f1"));
        feathers.add(root.getChild("midRr").getChild("midRr_f2"));
        feathers.add(root.getChild("midRr").getChild("midRr_f3"));
        feathers.add(root.getChild("midRr").getChild("midRr_f4"));
        feathers.add(root.getChild("midRr").getChild("midRm").getChild("midRm_f0"));
        feathers.add(root.getChild("midRr").getChild("midRm").getChild("midRm_f1"));
        feathers.add(root.getChild("midRr").getChild("midRm").getChild("midRm_f2"));
        feathers.add(root.getChild("midRr").getChild("midRm").getChild("midRm_f3"));
        feathers.add(root.getChild("midRr").getChild("midRm").getChild("midRm_f4"));
        feathers.add(root.getChild("midRr").getChild("midRm").getChild("midRm_f5"));
        feathers.add(root.getChild("midRr").getChild("midRm").getChild("midRm_f6"));
        feathers.add(root.getChild("midRr").getChild("midRm").getChild("midRm_f7"));
        feathers.add(root.getChild("midRr").getChild("midRm").getChild("midRm_f8"));
        feathers.add(root.getChild("midRr").getChild("midRm").getChild("midRt").getChild("midRt_f0"));
        feathers.add(root.getChild("midRr").getChild("midRm").getChild("midRt").getChild("midRt_f1"));
        feathers.add(root.getChild("midRr").getChild("midRm").getChild("midRt").getChild("midRt_f2"));
        feathers.add(root.getChild("midRr").getChild("midRm").getChild("midRt").getChild("midRt_f3"));
        feathers.add(root.getChild("midRr").getChild("midRm").getChild("midRt").getChild("midRt_f4"));
        feathers.add(root.getChild("midRr").getChild("midRm").getChild("midRt").getChild("midRt_f5"));
        feathers.add(root.getChild("midRr").getChild("midRm").getChild("midRt").getChild("midRt_f6"));
        feathers.add(root.getChild("midRr").getChild("midRm").getChild("midRt").getChild("midRt_f7"));
        feathers.add(root.getChild("lowRr").getChild("lowRr_f0"));
        feathers.add(root.getChild("lowRr").getChild("lowRr_f1"));
        feathers.add(root.getChild("lowRr").getChild("lowRr_f2"));
        feathers.add(root.getChild("lowRr").getChild("lowRr_f3"));
        feathers.add(root.getChild("lowRr").getChild("lowRr_f4"));
        feathers.add(root.getChild("lowRr").getChild("lowRm").getChild("lowRm_f0"));
        feathers.add(root.getChild("lowRr").getChild("lowRm").getChild("lowRm_f1"));
        feathers.add(root.getChild("lowRr").getChild("lowRm").getChild("lowRm_f2"));
        feathers.add(root.getChild("lowRr").getChild("lowRm").getChild("lowRm_f3"));
        feathers.add(root.getChild("lowRr").getChild("lowRm").getChild("lowRm_f4"));
        feathers.add(root.getChild("lowRr").getChild("lowRm").getChild("lowRm_f5"));
        feathers.add(root.getChild("lowRr").getChild("lowRm").getChild("lowRm_f6"));
        feathers.add(root.getChild("lowRr").getChild("lowRm").getChild("lowRm_f7"));
        feathers.add(root.getChild("lowRr").getChild("lowRm").getChild("lowRm_f8"));
        feathers.add(root.getChild("lowRr").getChild("lowRm").getChild("lowRt").getChild("lowRt_f0"));
        feathers.add(root.getChild("lowRr").getChild("lowRm").getChild("lowRt").getChild("lowRt_f1"));
        feathers.add(root.getChild("lowRr").getChild("lowRm").getChild("lowRt").getChild("lowRt_f2"));
        feathers.add(root.getChild("lowRr").getChild("lowRm").getChild("lowRt").getChild("lowRt_f3"));
        feathers.add(root.getChild("lowRr").getChild("lowRm").getChild("lowRt").getChild("lowRt_f4"));
        feathers.add(root.getChild("lowRr").getChild("lowRm").getChild("lowRt").getChild("lowRt_f5"));
        feathers.add(root.getChild("lowRr").getChild("lowRm").getChild("lowRt").getChild("lowRt_f6"));
        feathers.add(root.getChild("lowRr").getChild("lowRm").getChild("lowRt").getChild("lowRt_f7"));
        for (int i = 0; i < 20; i++) haloSeg[i] = halo.getChild("halo" + i);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 0).mirror(false).addBox(-48, 36, -48, 96, 12, 96).texOffs(384, 0).mirror(false).addBox(-36, 24, -36, 72, 12, 72).texOffs(672, 0).mirror(false).addBox(-20, -8, -20, 40, 32, 40).texOffs(832, 0).mirror(false).addBox(-22, -12, -22, 44, 4, 44), PartPose.ZERO);
        PartDefinition statue = root.addOrReplaceChild("statue", CubeListBuilder.create().texOffs(0, 108).mirror(false).addBox(-18, -34, -14, 36, 22, 28).texOffs(128, 108).mirror(false).addBox(-15, -60, -11, 30, 26, 22).texOffs(232, 108).mirror(false).addBox(-12, -82, -9, 24, 22, 18).texOffs(316, 108).mirror(false).addBox(-17, -86, -9, 34, 8, 18).texOffs(420, 108).mirror(false).addBox(-4, -90, -4, 8, 4, 8).texOffs(452, 108).mirror(false).addBox(-7, -106, -5, 14, 16, 12).texOffs(504, 108).mirror(false).addBox(-11, -114, -11, 22, 5, 22).texOffs(592, 108).mirror(false).addBox(-11, -109, -11, 3.2f, 22, 18).texOffs(634, 108).mirror(false).addBox(7.8f, -109, -11, 3.2f, 22, 18).texOffs(676, 108).mirror(false).addBox(-11, -109, 6, 22, 24, 5).texOffs(730, 108).mirror(false).addBox(-10, -110, -12.4f, 20, 3.2f, 2).texOffs(774, 108).mirror(false).addBox(-13, -91, -10, 26, 6, 20), PartPose.ZERO);
        statue.addOrReplaceChild("arm_l", CubeListBuilder.create().texOffs(866, 108).mirror(false).addBox(-3.5f, 0, -3.5f, 7, 20, 7), PartPose.offsetAndRotation(-14, -84, 0, -0.95f, 0, 0.2f));
        statue.addOrReplaceChild("arm_r", CubeListBuilder.create().texOffs(894, 108).mirror(false).addBox(-3.5f, 0, -3.5f, 7, 20, 7), PartPose.offsetAndRotation(14, -84, 0, -0.95f, 0, -0.2f));
        statue.addOrReplaceChild("orb", CubeListBuilder.create().texOffs(922, 108).mirror(false).addBox(-6, -6, -6, 12, 12, 12), PartPose.offset(0, -74, -22));
        statue.addOrReplaceChild("flower", CubeListBuilder.create().texOffs(970, 108).mirror(false).addBox(-4, -4, -4, 8, 4, 8).texOffs(0, 158).mirror(false).addBox(-2, -6, -6, 4, 2, 12).texOffs(32, 158).mirror(false).addBox(-6, -6, -2, 12, 2, 4), PartPose.offsetAndRotation(-12, -88, -2, 0.2f, 0.4f, 0.3f));
        PartDefinition upLr = root.addOrReplaceChild("upLr", CubeListBuilder.create().texOffs(64, 158).mirror(false).addBox(-17, -2.6f, -2.6f, 17, 5.2f, 5.2f), PartPose.offsetAndRotation(-6, -88, 17, 0, 0, 1.35f));
        upLr.addOrReplaceChild("upLr_f0", CubeListBuilder.create().texOffs(108, 158).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 13.6f, 2), PartPose.offsetAndRotation(-5.1f, 1.6f, 2.2f, 0.04f, 0, -1.5f));
        upLr.addOrReplaceChild("upLr_f1", CubeListBuilder.create().texOffs(122, 158).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 15.3f, 2), PartPose.offsetAndRotation(-11.05f, 1.6f, 2.2f, 0.04f, 0, -1.43f));
        upLr.addOrReplaceChild("upLr_f2", CubeListBuilder.create().texOffs(136, 158).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 17, 2), PartPose.offsetAndRotation(-16.15f, 1.6f, 2.2f, 0.04f, 0, -1.35f));
        upLr.addOrReplaceChild("upLr_f3", CubeListBuilder.create().texOffs(150, 158).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 6.8f, 2), PartPose.offsetAndRotation(-6.8f, 1.6f, 1, 0, 0, -1.45f));
        upLr.addOrReplaceChild("upLr_f4", CubeListBuilder.create().texOffs(164, 158).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 6.8f, 2), PartPose.offsetAndRotation(-13.6f, 1.6f, 1, 0, 0, -1.35f));
        PartDefinition upLm = upLr.addOrReplaceChild("upLm", CubeListBuilder.create().texOffs(178, 158).mirror(false).addBox(-23.8f, -2.6f, -2.6f, 23.8f, 5.2f, 5.2f), PartPose.offsetAndRotation(-17, 0, 0, 0, 0, -0.25f));
        upLm.addOrReplaceChild("upLm_f0", CubeListBuilder.create().texOffs(236, 158).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 22.1f, 2), PartPose.offsetAndRotation(-1.7f, 1.6f, 3.4f, 0.08f, 0, -1.05f));
        upLm.addOrReplaceChild("upLm_f1", CubeListBuilder.create().texOffs(250, 158).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 23.8f, 2), PartPose.offsetAndRotation(-7.65f, 1.6f, 3.4f, 0.08f, 0, -0.98f));
        upLm.addOrReplaceChild("upLm_f2", CubeListBuilder.create().texOffs(264, 158).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 25.5f, 2), PartPose.offsetAndRotation(-13.6f, 1.6f, 3.4f, 0.08f, 0, -0.9f));
        upLm.addOrReplaceChild("upLm_f3", CubeListBuilder.create().texOffs(278, 158).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 27.2f, 2), PartPose.offsetAndRotation(-19.55f, 1.6f, 3.4f, 0.08f, 0, -0.8f));
        upLm.addOrReplaceChild("upLm_f4", CubeListBuilder.create().texOffs(292, 158).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 13.6f, 2), PartPose.offsetAndRotation(-3.4f, 1.6f, 2.2f, 0.04f, 0, -1.05f));
        upLm.addOrReplaceChild("upLm_f5", CubeListBuilder.create().texOffs(306, 158).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 13.6f, 2), PartPose.offsetAndRotation(-10.2f, 1.6f, 2.2f, 0.04f, 0, -0.98f));
        upLm.addOrReplaceChild("upLm_f6", CubeListBuilder.create().texOffs(320, 158).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 15.3f, 2), PartPose.offsetAndRotation(-17, 1.6f, 2.2f, 0.04f, 0, -0.9f));
        upLm.addOrReplaceChild("upLm_f7", CubeListBuilder.create().texOffs(334, 158).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 6.8f, 2), PartPose.offsetAndRotation(-5.1f, 1.6f, 1, 0, 0, -1.1f));
        upLm.addOrReplaceChild("upLm_f8", CubeListBuilder.create().texOffs(348, 158).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 6.8f, 2), PartPose.offsetAndRotation(-13.6f, 1.6f, 1, 0, 0, -1));
        PartDefinition upLt = upLm.addOrReplaceChild("upLt", CubeListBuilder.create().texOffs(362, 158).mirror(false).addBox(-22.1f, -2.6f, -2.6f, 22.1f, 5.2f, 5.2f), PartPose.offsetAndRotation(-23.8f, 0, 0, 0, 0, -0.15f));
        upLt.addOrReplaceChild("upLt_f0", CubeListBuilder.create().texOffs(416, 158).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 30.6f, 2), PartPose.offsetAndRotation(-1.7f, 1.6f, 3.4f, 0.08f, 0, -0.5f));
        upLt.addOrReplaceChild("upLt_f1", CubeListBuilder.create().texOffs(430, 158).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 35.7f, 2), PartPose.offsetAndRotation(-6.8f, 1.6f, 3.4f, 0.08f, 0, -0.35f));
        upLt.addOrReplaceChild("upLt_f2", CubeListBuilder.create().texOffs(444, 158).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 39.1f, 2), PartPose.offsetAndRotation(-11.9f, 1.6f, 3.4f, 0.08f, 0, -0.2f));
        upLt.addOrReplaceChild("upLt_f3", CubeListBuilder.create().texOffs(458, 158).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 37.4f, 2), PartPose.offsetAndRotation(-17, 1.6f, 3.4f, 0.08f, 0, -0.05f));
        upLt.addOrReplaceChild("upLt_f4", CubeListBuilder.create().texOffs(472, 158).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 32.3f, 2), PartPose.offsetAndRotation(-21.25f, 1.6f, 3.4f, 0.08f, 0, 0.1f));
        upLt.addOrReplaceChild("upLt_f5", CubeListBuilder.create().texOffs(486, 158).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 17, 2), PartPose.offsetAndRotation(-3.4f, 1.6f, 2.2f, 0.04f, 0, -0.55f));
        upLt.addOrReplaceChild("upLt_f6", CubeListBuilder.create().texOffs(500, 158).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 18.7f, 2), PartPose.offsetAndRotation(-10.2f, 1.6f, 2.2f, 0.04f, 0, -0.4f));
        upLt.addOrReplaceChild("upLt_f7", CubeListBuilder.create().texOffs(514, 158).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 17, 2), PartPose.offsetAndRotation(-17, 1.6f, 2.2f, 0.04f, 0, -0.25f));
        PartDefinition midLr = root.addOrReplaceChild("midLr", CubeListBuilder.create().texOffs(528, 158).mirror(false).addBox(-20, -2.6f, -2.6f, 20, 5.2f, 5.2f), PartPose.offsetAndRotation(-6, -80, 14, 0, 0, 0.75f));
        midLr.addOrReplaceChild("midLr_f0", CubeListBuilder.create().texOffs(578, 158).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 16, 2), PartPose.offsetAndRotation(-6, 1.6f, 2.2f, 0.04f, 0, -0.9f));
        midLr.addOrReplaceChild("midLr_f1", CubeListBuilder.create().texOffs(594, 158).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 18, 2), PartPose.offsetAndRotation(-13, 1.6f, 2.2f, 0.04f, 0, -0.83f));
        midLr.addOrReplaceChild("midLr_f2", CubeListBuilder.create().texOffs(610, 158).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 20, 2), PartPose.offsetAndRotation(-19, 1.6f, 2.2f, 0.04f, 0, -0.75f));
        midLr.addOrReplaceChild("midLr_f3", CubeListBuilder.create().texOffs(626, 158).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 8, 2), PartPose.offsetAndRotation(-8, 1.6f, 1, 0, 0, -0.85f));
        midLr.addOrReplaceChild("midLr_f4", CubeListBuilder.create().texOffs(642, 158).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 8, 2), PartPose.offsetAndRotation(-16, 1.6f, 1, 0, 0, -0.75f));
        PartDefinition midLm = midLr.addOrReplaceChild("midLm", CubeListBuilder.create().texOffs(658, 158).mirror(false).addBox(-28, -2.6f, -2.6f, 28, 5.2f, 5.2f), PartPose.offsetAndRotation(-20, 0, 0, 0, 0, -0.35f));
        midLm.addOrReplaceChild("midLm_f0", CubeListBuilder.create().texOffs(724, 158).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 26, 2), PartPose.offsetAndRotation(-2, 1.6f, 3.4f, 0.08f, 0, -0.35f));
        midLm.addOrReplaceChild("midLm_f1", CubeListBuilder.create().texOffs(740, 158).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 28, 2), PartPose.offsetAndRotation(-9, 1.6f, 3.4f, 0.08f, 0, -0.28f));
        midLm.addOrReplaceChild("midLm_f2", CubeListBuilder.create().texOffs(756, 158).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 30, 2), PartPose.offsetAndRotation(-16, 1.6f, 3.4f, 0.08f, 0, -0.2f));
        midLm.addOrReplaceChild("midLm_f3", CubeListBuilder.create().texOffs(772, 158).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 32, 2), PartPose.offsetAndRotation(-23, 1.6f, 3.4f, 0.08f, 0, -0.1f));
        midLm.addOrReplaceChild("midLm_f4", CubeListBuilder.create().texOffs(788, 158).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 16, 2), PartPose.offsetAndRotation(-4, 1.6f, 2.2f, 0.04f, 0, -0.35f));
        midLm.addOrReplaceChild("midLm_f5", CubeListBuilder.create().texOffs(804, 158).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 16, 2), PartPose.offsetAndRotation(-12, 1.6f, 2.2f, 0.04f, 0, -0.28f));
        midLm.addOrReplaceChild("midLm_f6", CubeListBuilder.create().texOffs(820, 158).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 18, 2), PartPose.offsetAndRotation(-20, 1.6f, 2.2f, 0.04f, 0, -0.2f));
        midLm.addOrReplaceChild("midLm_f7", CubeListBuilder.create().texOffs(836, 158).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 8, 2), PartPose.offsetAndRotation(-6, 1.6f, 1, 0, 0, -0.4f));
        midLm.addOrReplaceChild("midLm_f8", CubeListBuilder.create().texOffs(852, 158).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 8, 2), PartPose.offsetAndRotation(-16, 1.6f, 1, 0, 0, -0.3f));
        PartDefinition midLt = midLm.addOrReplaceChild("midLt", CubeListBuilder.create().texOffs(868, 158).mirror(false).addBox(-26, -2.6f, -2.6f, 26, 5.2f, 5.2f), PartPose.offsetAndRotation(-28, 0, 0, 0, 0, -0.3f));
        midLt.addOrReplaceChild("midLt_f0", CubeListBuilder.create().texOffs(930, 158).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 36, 2), PartPose.offsetAndRotation(-2, 1.6f, 3.4f, 0.08f, 0, 0.35f));
        midLt.addOrReplaceChild("midLt_f1", CubeListBuilder.create().texOffs(946, 158).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 42, 2), PartPose.offsetAndRotation(-8, 1.6f, 3.4f, 0.08f, 0, 0.5f));
        midLt.addOrReplaceChild("midLt_f2", CubeListBuilder.create().texOffs(962, 158).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 46, 2), PartPose.offsetAndRotation(-14, 1.6f, 3.4f, 0.08f, 0, 0.65f));
        midLt.addOrReplaceChild("midLt_f3", CubeListBuilder.create().texOffs(978, 158).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 44, 2), PartPose.offsetAndRotation(-20, 1.6f, 3.4f, 0.08f, 0, 0.8f));
        midLt.addOrReplaceChild("midLt_f4", CubeListBuilder.create().texOffs(994, 158).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 38, 2), PartPose.offsetAndRotation(-25, 1.6f, 3.4f, 0.08f, 0, 0.95f));
        midLt.addOrReplaceChild("midLt_f5", CubeListBuilder.create().texOffs(0, 206).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 20, 2), PartPose.offsetAndRotation(-4, 1.6f, 2.2f, 0.04f, 0, 0.3f));
        midLt.addOrReplaceChild("midLt_f6", CubeListBuilder.create().texOffs(16, 206).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 22, 2), PartPose.offsetAndRotation(-12, 1.6f, 2.2f, 0.04f, 0, 0.45f));
        midLt.addOrReplaceChild("midLt_f7", CubeListBuilder.create().texOffs(32, 206).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 20, 2), PartPose.offsetAndRotation(-20, 1.6f, 2.2f, 0.04f, 0, 0.6f));
        PartDefinition lowLr = root.addOrReplaceChild("lowLr", CubeListBuilder.create().texOffs(48, 206).mirror(false).addBox(-17, -2.6f, -2.6f, 17, 5.2f, 5.2f), PartPose.offsetAndRotation(-6, -68, 11, 0, 0, -0.35f));
        lowLr.addOrReplaceChild("lowLr_f0", CubeListBuilder.create().texOffs(92, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 13.6f, 2), PartPose.offsetAndRotation(-5.1f, 1.6f, 2.2f, 0.04f, 0, 0.2f));
        lowLr.addOrReplaceChild("lowLr_f1", CubeListBuilder.create().texOffs(106, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 15.3f, 2), PartPose.offsetAndRotation(-11.05f, 1.6f, 2.2f, 0.04f, 0, 0.27f));
        lowLr.addOrReplaceChild("lowLr_f2", CubeListBuilder.create().texOffs(120, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 17, 2), PartPose.offsetAndRotation(-16.15f, 1.6f, 2.2f, 0.04f, 0, 0.35f));
        lowLr.addOrReplaceChild("lowLr_f3", CubeListBuilder.create().texOffs(134, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 6.8f, 2), PartPose.offsetAndRotation(-6.8f, 1.6f, 1, 0, 0, 0.25f));
        lowLr.addOrReplaceChild("lowLr_f4", CubeListBuilder.create().texOffs(148, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 6.8f, 2), PartPose.offsetAndRotation(-13.6f, 1.6f, 1, 0, 0, 0.35f));
        PartDefinition lowLm = lowLr.addOrReplaceChild("lowLm", CubeListBuilder.create().texOffs(162, 206).mirror(false).addBox(-23.8f, -2.6f, -2.6f, 23.8f, 5.2f, 5.2f), PartPose.offsetAndRotation(-17, 0, 0, 0, 0, -0.4f));
        lowLm.addOrReplaceChild("lowLm_f0", CubeListBuilder.create().texOffs(220, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 22.1f, 2), PartPose.offsetAndRotation(-1.7f, 1.6f, 3.4f, 0.08f, 0, 0.8f));
        lowLm.addOrReplaceChild("lowLm_f1", CubeListBuilder.create().texOffs(234, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 23.8f, 2), PartPose.offsetAndRotation(-7.65f, 1.6f, 3.4f, 0.08f, 0, 0.87f));
        lowLm.addOrReplaceChild("lowLm_f2", CubeListBuilder.create().texOffs(248, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 25.5f, 2), PartPose.offsetAndRotation(-13.6f, 1.6f, 3.4f, 0.08f, 0, 0.95f));
        lowLm.addOrReplaceChild("lowLm_f3", CubeListBuilder.create().texOffs(262, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 27.2f, 2), PartPose.offsetAndRotation(-19.55f, 1.6f, 3.4f, 0.08f, 0, 1.05f));
        lowLm.addOrReplaceChild("lowLm_f4", CubeListBuilder.create().texOffs(276, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 13.6f, 2), PartPose.offsetAndRotation(-3.4f, 1.6f, 2.2f, 0.04f, 0, 0.8f));
        lowLm.addOrReplaceChild("lowLm_f5", CubeListBuilder.create().texOffs(290, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 13.6f, 2), PartPose.offsetAndRotation(-10.2f, 1.6f, 2.2f, 0.04f, 0, 0.87f));
        lowLm.addOrReplaceChild("lowLm_f6", CubeListBuilder.create().texOffs(304, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 15.3f, 2), PartPose.offsetAndRotation(-17, 1.6f, 2.2f, 0.04f, 0, 0.95f));
        lowLm.addOrReplaceChild("lowLm_f7", CubeListBuilder.create().texOffs(318, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 6.8f, 2), PartPose.offsetAndRotation(-5.1f, 1.6f, 1, 0, 0, 0.75f));
        lowLm.addOrReplaceChild("lowLm_f8", CubeListBuilder.create().texOffs(332, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 6.8f, 2), PartPose.offsetAndRotation(-13.6f, 1.6f, 1, 0, 0, 0.85f));
        PartDefinition lowLt = lowLm.addOrReplaceChild("lowLt", CubeListBuilder.create().texOffs(346, 206).mirror(false).addBox(-22.1f, -2.6f, -2.6f, 22.1f, 5.2f, 5.2f), PartPose.offsetAndRotation(-23.8f, 0, 0, 0, 0, -0.35f));
        lowLt.addOrReplaceChild("lowLt_f0", CubeListBuilder.create().texOffs(400, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 30.6f, 2), PartPose.offsetAndRotation(-1.7f, 1.6f, 3.4f, 0.08f, 0, 1.55f));
        lowLt.addOrReplaceChild("lowLt_f1", CubeListBuilder.create().texOffs(414, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 35.7f, 2), PartPose.offsetAndRotation(-6.8f, 1.6f, 3.4f, 0.08f, 0, 1.7f));
        lowLt.addOrReplaceChild("lowLt_f2", CubeListBuilder.create().texOffs(428, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 39.1f, 2), PartPose.offsetAndRotation(-11.9f, 1.6f, 3.4f, 0.08f, 0, 1.85f));
        lowLt.addOrReplaceChild("lowLt_f3", CubeListBuilder.create().texOffs(442, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 37.4f, 2), PartPose.offsetAndRotation(-17, 1.6f, 3.4f, 0.08f, 0, 2));
        lowLt.addOrReplaceChild("lowLt_f4", CubeListBuilder.create().texOffs(456, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 32.3f, 2), PartPose.offsetAndRotation(-21.25f, 1.6f, 3.4f, 0.08f, 0, 2.15f));
        lowLt.addOrReplaceChild("lowLt_f5", CubeListBuilder.create().texOffs(470, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 17, 2), PartPose.offsetAndRotation(-3.4f, 1.6f, 2.2f, 0.04f, 0, 1.5f));
        lowLt.addOrReplaceChild("lowLt_f6", CubeListBuilder.create().texOffs(484, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 18.7f, 2), PartPose.offsetAndRotation(-10.2f, 1.6f, 2.2f, 0.04f, 0, 1.65f));
        lowLt.addOrReplaceChild("lowLt_f7", CubeListBuilder.create().texOffs(498, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 17, 2), PartPose.offsetAndRotation(-17, 1.6f, 2.2f, 0.04f, 0, 1.8f));
        PartDefinition upRr = root.addOrReplaceChild("upRr", CubeListBuilder.create().texOffs(512, 206).mirror(false).addBox(0, -2.6f, -2.6f, 17, 5.2f, 5.2f), PartPose.offsetAndRotation(6, -88, 17, 0, 0, -1.35f));
        upRr.addOrReplaceChild("upRr_f0", CubeListBuilder.create().texOffs(556, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 13.6f, 2), PartPose.offsetAndRotation(5.1f, 1.6f, 2.2f, 0.04f, 0, 1.5f));
        upRr.addOrReplaceChild("upRr_f1", CubeListBuilder.create().texOffs(570, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 15.3f, 2), PartPose.offsetAndRotation(11.05f, 1.6f, 2.2f, 0.04f, 0, 1.43f));
        upRr.addOrReplaceChild("upRr_f2", CubeListBuilder.create().texOffs(584, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 17, 2), PartPose.offsetAndRotation(16.15f, 1.6f, 2.2f, 0.04f, 0, 1.35f));
        upRr.addOrReplaceChild("upRr_f3", CubeListBuilder.create().texOffs(598, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 6.8f, 2), PartPose.offsetAndRotation(6.8f, 1.6f, 1, 0, 0, 1.45f));
        upRr.addOrReplaceChild("upRr_f4", CubeListBuilder.create().texOffs(612, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 6.8f, 2), PartPose.offsetAndRotation(13.6f, 1.6f, 1, 0, 0, 1.35f));
        PartDefinition upRm = upRr.addOrReplaceChild("upRm", CubeListBuilder.create().texOffs(626, 206).mirror(false).addBox(0, -2.6f, -2.6f, 23.8f, 5.2f, 5.2f), PartPose.offsetAndRotation(17, 0, 0, 0, 0, 0.25f));
        upRm.addOrReplaceChild("upRm_f0", CubeListBuilder.create().texOffs(684, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 22.1f, 2), PartPose.offsetAndRotation(1.7f, 1.6f, 3.4f, 0.08f, 0, 1.05f));
        upRm.addOrReplaceChild("upRm_f1", CubeListBuilder.create().texOffs(698, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 23.8f, 2), PartPose.offsetAndRotation(7.65f, 1.6f, 3.4f, 0.08f, 0, 0.98f));
        upRm.addOrReplaceChild("upRm_f2", CubeListBuilder.create().texOffs(712, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 25.5f, 2), PartPose.offsetAndRotation(13.6f, 1.6f, 3.4f, 0.08f, 0, 0.9f));
        upRm.addOrReplaceChild("upRm_f3", CubeListBuilder.create().texOffs(726, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 27.2f, 2), PartPose.offsetAndRotation(19.55f, 1.6f, 3.4f, 0.08f, 0, 0.8f));
        upRm.addOrReplaceChild("upRm_f4", CubeListBuilder.create().texOffs(740, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 13.6f, 2), PartPose.offsetAndRotation(3.4f, 1.6f, 2.2f, 0.04f, 0, 1.05f));
        upRm.addOrReplaceChild("upRm_f5", CubeListBuilder.create().texOffs(754, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 13.6f, 2), PartPose.offsetAndRotation(10.2f, 1.6f, 2.2f, 0.04f, 0, 0.98f));
        upRm.addOrReplaceChild("upRm_f6", CubeListBuilder.create().texOffs(768, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 15.3f, 2), PartPose.offsetAndRotation(17, 1.6f, 2.2f, 0.04f, 0, 0.9f));
        upRm.addOrReplaceChild("upRm_f7", CubeListBuilder.create().texOffs(782, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 6.8f, 2), PartPose.offsetAndRotation(5.1f, 1.6f, 1, 0, 0, 1.1f));
        upRm.addOrReplaceChild("upRm_f8", CubeListBuilder.create().texOffs(796, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 6.8f, 2), PartPose.offsetAndRotation(13.6f, 1.6f, 1, 0, 0, 1));
        PartDefinition upRt = upRm.addOrReplaceChild("upRt", CubeListBuilder.create().texOffs(810, 206).mirror(false).addBox(0, -2.6f, -2.6f, 22.1f, 5.2f, 5.2f), PartPose.offsetAndRotation(23.8f, 0, 0, 0, 0, 0.15f));
        upRt.addOrReplaceChild("upRt_f0", CubeListBuilder.create().texOffs(864, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 30.6f, 2), PartPose.offsetAndRotation(1.7f, 1.6f, 3.4f, 0.08f, 0, 0.5f));
        upRt.addOrReplaceChild("upRt_f1", CubeListBuilder.create().texOffs(878, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 35.7f, 2), PartPose.offsetAndRotation(6.8f, 1.6f, 3.4f, 0.08f, 0, 0.35f));
        upRt.addOrReplaceChild("upRt_f2", CubeListBuilder.create().texOffs(892, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 39.1f, 2), PartPose.offsetAndRotation(11.9f, 1.6f, 3.4f, 0.08f, 0, 0.2f));
        upRt.addOrReplaceChild("upRt_f3", CubeListBuilder.create().texOffs(906, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 37.4f, 2), PartPose.offsetAndRotation(17, 1.6f, 3.4f, 0.08f, 0, 0.05f));
        upRt.addOrReplaceChild("upRt_f4", CubeListBuilder.create().texOffs(920, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 32.3f, 2), PartPose.offsetAndRotation(21.25f, 1.6f, 3.4f, 0.08f, 0, -0.1f));
        upRt.addOrReplaceChild("upRt_f5", CubeListBuilder.create().texOffs(934, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 17, 2), PartPose.offsetAndRotation(3.4f, 1.6f, 2.2f, 0.04f, 0, 0.55f));
        upRt.addOrReplaceChild("upRt_f6", CubeListBuilder.create().texOffs(948, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 18.7f, 2), PartPose.offsetAndRotation(10.2f, 1.6f, 2.2f, 0.04f, 0, 0.4f));
        upRt.addOrReplaceChild("upRt_f7", CubeListBuilder.create().texOffs(962, 206).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 17, 2), PartPose.offsetAndRotation(17, 1.6f, 2.2f, 0.04f, 0, 0.25f));
        PartDefinition midRr = root.addOrReplaceChild("midRr", CubeListBuilder.create().texOffs(0, 247).mirror(false).addBox(0, -2.6f, -2.6f, 20, 5.2f, 5.2f), PartPose.offsetAndRotation(6, -80, 14, 0, 0, -0.75f));
        midRr.addOrReplaceChild("midRr_f0", CubeListBuilder.create().texOffs(50, 247).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 16, 2), PartPose.offsetAndRotation(6, 1.6f, 2.2f, 0.04f, 0, 0.9f));
        midRr.addOrReplaceChild("midRr_f1", CubeListBuilder.create().texOffs(66, 247).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 18, 2), PartPose.offsetAndRotation(13, 1.6f, 2.2f, 0.04f, 0, 0.83f));
        midRr.addOrReplaceChild("midRr_f2", CubeListBuilder.create().texOffs(82, 247).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 20, 2), PartPose.offsetAndRotation(19, 1.6f, 2.2f, 0.04f, 0, 0.75f));
        midRr.addOrReplaceChild("midRr_f3", CubeListBuilder.create().texOffs(98, 247).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 8, 2), PartPose.offsetAndRotation(8, 1.6f, 1, 0, 0, 0.85f));
        midRr.addOrReplaceChild("midRr_f4", CubeListBuilder.create().texOffs(114, 247).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 8, 2), PartPose.offsetAndRotation(16, 1.6f, 1, 0, 0, 0.75f));
        PartDefinition midRm = midRr.addOrReplaceChild("midRm", CubeListBuilder.create().texOffs(130, 247).mirror(false).addBox(0, -2.6f, -2.6f, 28, 5.2f, 5.2f), PartPose.offsetAndRotation(20, 0, 0, 0, 0, 0.35f));
        midRm.addOrReplaceChild("midRm_f0", CubeListBuilder.create().texOffs(196, 247).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 26, 2), PartPose.offsetAndRotation(2, 1.6f, 3.4f, 0.08f, 0, 0.35f));
        midRm.addOrReplaceChild("midRm_f1", CubeListBuilder.create().texOffs(212, 247).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 28, 2), PartPose.offsetAndRotation(9, 1.6f, 3.4f, 0.08f, 0, 0.28f));
        midRm.addOrReplaceChild("midRm_f2", CubeListBuilder.create().texOffs(228, 247).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 30, 2), PartPose.offsetAndRotation(16, 1.6f, 3.4f, 0.08f, 0, 0.2f));
        midRm.addOrReplaceChild("midRm_f3", CubeListBuilder.create().texOffs(244, 247).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 32, 2), PartPose.offsetAndRotation(23, 1.6f, 3.4f, 0.08f, 0, 0.1f));
        midRm.addOrReplaceChild("midRm_f4", CubeListBuilder.create().texOffs(260, 247).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 16, 2), PartPose.offsetAndRotation(4, 1.6f, 2.2f, 0.04f, 0, 0.35f));
        midRm.addOrReplaceChild("midRm_f5", CubeListBuilder.create().texOffs(276, 247).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 16, 2), PartPose.offsetAndRotation(12, 1.6f, 2.2f, 0.04f, 0, 0.28f));
        midRm.addOrReplaceChild("midRm_f6", CubeListBuilder.create().texOffs(292, 247).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 18, 2), PartPose.offsetAndRotation(20, 1.6f, 2.2f, 0.04f, 0, 0.2f));
        midRm.addOrReplaceChild("midRm_f7", CubeListBuilder.create().texOffs(308, 247).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 8, 2), PartPose.offsetAndRotation(6, 1.6f, 1, 0, 0, 0.4f));
        midRm.addOrReplaceChild("midRm_f8", CubeListBuilder.create().texOffs(324, 247).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 8, 2), PartPose.offsetAndRotation(16, 1.6f, 1, 0, 0, 0.3f));
        PartDefinition midRt = midRm.addOrReplaceChild("midRt", CubeListBuilder.create().texOffs(340, 247).mirror(false).addBox(0, -2.6f, -2.6f, 26, 5.2f, 5.2f), PartPose.offsetAndRotation(28, 0, 0, 0, 0, 0.3f));
        midRt.addOrReplaceChild("midRt_f0", CubeListBuilder.create().texOffs(402, 247).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 36, 2), PartPose.offsetAndRotation(2, 1.6f, 3.4f, 0.08f, 0, -0.35f));
        midRt.addOrReplaceChild("midRt_f1", CubeListBuilder.create().texOffs(418, 247).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 42, 2), PartPose.offsetAndRotation(8, 1.6f, 3.4f, 0.08f, 0, -0.5f));
        midRt.addOrReplaceChild("midRt_f2", CubeListBuilder.create().texOffs(434, 247).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 46, 2), PartPose.offsetAndRotation(14, 1.6f, 3.4f, 0.08f, 0, -0.65f));
        midRt.addOrReplaceChild("midRt_f3", CubeListBuilder.create().texOffs(450, 247).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 44, 2), PartPose.offsetAndRotation(20, 1.6f, 3.4f, 0.08f, 0, -0.8f));
        midRt.addOrReplaceChild("midRt_f4", CubeListBuilder.create().texOffs(466, 247).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 38, 2), PartPose.offsetAndRotation(25, 1.6f, 3.4f, 0.08f, 0, -0.95f));
        midRt.addOrReplaceChild("midRt_f5", CubeListBuilder.create().texOffs(482, 247).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 20, 2), PartPose.offsetAndRotation(4, 1.6f, 2.2f, 0.04f, 0, -0.3f));
        midRt.addOrReplaceChild("midRt_f6", CubeListBuilder.create().texOffs(498, 247).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 22, 2), PartPose.offsetAndRotation(12, 1.6f, 2.2f, 0.04f, 0, -0.45f));
        midRt.addOrReplaceChild("midRt_f7", CubeListBuilder.create().texOffs(514, 247).mirror(false).addBox(-2.8f, 0, -1, 5.6f, 20, 2), PartPose.offsetAndRotation(20, 1.6f, 2.2f, 0.04f, 0, -0.6f));
        PartDefinition lowRr = root.addOrReplaceChild("lowRr", CubeListBuilder.create().texOffs(530, 247).mirror(false).addBox(0, -2.6f, -2.6f, 17, 5.2f, 5.2f), PartPose.offsetAndRotation(6, -68, 11, 0, 0, 0.35f));
        lowRr.addOrReplaceChild("lowRr_f0", CubeListBuilder.create().texOffs(574, 247).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 13.6f, 2), PartPose.offsetAndRotation(5.1f, 1.6f, 2.2f, 0.04f, 0, -0.2f));
        lowRr.addOrReplaceChild("lowRr_f1", CubeListBuilder.create().texOffs(588, 247).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 15.3f, 2), PartPose.offsetAndRotation(11.05f, 1.6f, 2.2f, 0.04f, 0, -0.27f));
        lowRr.addOrReplaceChild("lowRr_f2", CubeListBuilder.create().texOffs(602, 247).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 17, 2), PartPose.offsetAndRotation(16.15f, 1.6f, 2.2f, 0.04f, 0, -0.35f));
        lowRr.addOrReplaceChild("lowRr_f3", CubeListBuilder.create().texOffs(616, 247).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 6.8f, 2), PartPose.offsetAndRotation(6.8f, 1.6f, 1, 0, 0, -0.25f));
        lowRr.addOrReplaceChild("lowRr_f4", CubeListBuilder.create().texOffs(630, 247).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 6.8f, 2), PartPose.offsetAndRotation(13.6f, 1.6f, 1, 0, 0, -0.35f));
        PartDefinition lowRm = lowRr.addOrReplaceChild("lowRm", CubeListBuilder.create().texOffs(644, 247).mirror(false).addBox(0, -2.6f, -2.6f, 23.8f, 5.2f, 5.2f), PartPose.offsetAndRotation(17, 0, 0, 0, 0, 0.4f));
        lowRm.addOrReplaceChild("lowRm_f0", CubeListBuilder.create().texOffs(702, 247).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 22.1f, 2), PartPose.offsetAndRotation(1.7f, 1.6f, 3.4f, 0.08f, 0, -0.8f));
        lowRm.addOrReplaceChild("lowRm_f1", CubeListBuilder.create().texOffs(716, 247).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 23.8f, 2), PartPose.offsetAndRotation(7.65f, 1.6f, 3.4f, 0.08f, 0, -0.87f));
        lowRm.addOrReplaceChild("lowRm_f2", CubeListBuilder.create().texOffs(730, 247).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 25.5f, 2), PartPose.offsetAndRotation(13.6f, 1.6f, 3.4f, 0.08f, 0, -0.95f));
        lowRm.addOrReplaceChild("lowRm_f3", CubeListBuilder.create().texOffs(744, 247).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 27.2f, 2), PartPose.offsetAndRotation(19.55f, 1.6f, 3.4f, 0.08f, 0, -1.05f));
        lowRm.addOrReplaceChild("lowRm_f4", CubeListBuilder.create().texOffs(758, 247).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 13.6f, 2), PartPose.offsetAndRotation(3.4f, 1.6f, 2.2f, 0.04f, 0, -0.8f));
        lowRm.addOrReplaceChild("lowRm_f5", CubeListBuilder.create().texOffs(772, 247).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 13.6f, 2), PartPose.offsetAndRotation(10.2f, 1.6f, 2.2f, 0.04f, 0, -0.87f));
        lowRm.addOrReplaceChild("lowRm_f6", CubeListBuilder.create().texOffs(786, 247).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 15.3f, 2), PartPose.offsetAndRotation(17, 1.6f, 2.2f, 0.04f, 0, -0.95f));
        lowRm.addOrReplaceChild("lowRm_f7", CubeListBuilder.create().texOffs(800, 247).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 6.8f, 2), PartPose.offsetAndRotation(5.1f, 1.6f, 1, 0, 0, -0.75f));
        lowRm.addOrReplaceChild("lowRm_f8", CubeListBuilder.create().texOffs(814, 247).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 6.8f, 2), PartPose.offsetAndRotation(13.6f, 1.6f, 1, 0, 0, -0.85f));
        PartDefinition lowRt = lowRm.addOrReplaceChild("lowRt", CubeListBuilder.create().texOffs(828, 247).mirror(false).addBox(0, -2.6f, -2.6f, 22.1f, 5.2f, 5.2f), PartPose.offsetAndRotation(23.8f, 0, 0, 0, 0, 0.35f));
        lowRt.addOrReplaceChild("lowRt_f0", CubeListBuilder.create().texOffs(882, 247).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 30.6f, 2), PartPose.offsetAndRotation(1.7f, 1.6f, 3.4f, 0.08f, 0, -1.55f));
        lowRt.addOrReplaceChild("lowRt_f1", CubeListBuilder.create().texOffs(896, 247).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 35.7f, 2), PartPose.offsetAndRotation(6.8f, 1.6f, 3.4f, 0.08f, 0, -1.7f));
        lowRt.addOrReplaceChild("lowRt_f2", CubeListBuilder.create().texOffs(910, 247).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 39.1f, 2), PartPose.offsetAndRotation(11.9f, 1.6f, 3.4f, 0.08f, 0, -1.85f));
        lowRt.addOrReplaceChild("lowRt_f3", CubeListBuilder.create().texOffs(924, 247).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 37.4f, 2), PartPose.offsetAndRotation(17, 1.6f, 3.4f, 0.08f, 0, -2));
        lowRt.addOrReplaceChild("lowRt_f4", CubeListBuilder.create().texOffs(938, 247).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 32.3f, 2), PartPose.offsetAndRotation(21.25f, 1.6f, 3.4f, 0.08f, 0, -2.15f));
        lowRt.addOrReplaceChild("lowRt_f5", CubeListBuilder.create().texOffs(952, 247).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 17, 2), PartPose.offsetAndRotation(3.4f, 1.6f, 2.2f, 0.04f, 0, -1.5f));
        lowRt.addOrReplaceChild("lowRt_f6", CubeListBuilder.create().texOffs(966, 247).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 18.7f, 2), PartPose.offsetAndRotation(10.2f, 1.6f, 2.2f, 0.04f, 0, -1.65f));
        lowRt.addOrReplaceChild("lowRt_f7", CubeListBuilder.create().texOffs(980, 247).mirror(false).addBox(-2.38f, 0, -1, 4.76f, 17, 2), PartPose.offsetAndRotation(17, 1.6f, 2.2f, 0.04f, 0, -1.8f));
        PartDefinition halo = root.addOrReplaceChild("halo", CubeListBuilder.create(), PartPose.offset(0, -100, 12));
        halo.addOrReplaceChild("halo0", CubeListBuilder.create().texOffs(994, 247).mirror(false).addBox(-4, -2, -1.5f, 8, 4, 3), PartPose.offsetAndRotation(22, 0, 0, 0, 0, 1.5708f));
        halo.addOrReplaceChild("halo1", CubeListBuilder.create().texOffs(0, 295).mirror(false).addBox(-4, -2, -1.5f, 8, 4, 3), PartPose.offsetAndRotation(20.9232f, 6.79837f, 0, 0, 0, 1.88496f));
        halo.addOrReplaceChild("halo2", CubeListBuilder.create().texOffs(22, 295).mirror(false).addBox(-4, -2, -1.5f, 8, 4, 3), PartPose.offsetAndRotation(17.7984f, 12.9313f, 0, 0, 0, 2.19911f));
        halo.addOrReplaceChild("halo3", CubeListBuilder.create().texOffs(44, 295).mirror(false).addBox(-4, -2, -1.5f, 8, 4, 3), PartPose.offsetAndRotation(12.9313f, 17.7984f, 0, 0, 0, 2.51327f));
        halo.addOrReplaceChild("halo4", CubeListBuilder.create().texOffs(66, 295).mirror(false).addBox(-4, -2, -1.5f, 8, 4, 3), PartPose.offsetAndRotation(6.79837f, 20.9232f, 0, 0, 0, 2.82743f));
        halo.addOrReplaceChild("halo5", CubeListBuilder.create().texOffs(88, 295).mirror(false).addBox(-4, -2, -1.5f, 8, 4, 3), PartPose.offsetAndRotation(1.34711e-15f, 22, 0, 0, 0, 3.14159f));
        halo.addOrReplaceChild("halo6", CubeListBuilder.create().texOffs(110, 295).mirror(false).addBox(-4, -2, -1.5f, 8, 4, 3), PartPose.offsetAndRotation(-6.79837f, 20.9232f, 0, 0, 0, 3.45575f));
        halo.addOrReplaceChild("halo7", CubeListBuilder.create().texOffs(132, 295).mirror(false).addBox(-4, -2, -1.5f, 8, 4, 3), PartPose.offsetAndRotation(-12.9313f, 17.7984f, 0, 0, 0, 3.76991f));
        halo.addOrReplaceChild("halo8", CubeListBuilder.create().texOffs(154, 295).mirror(false).addBox(-4, -2, -1.5f, 8, 4, 3), PartPose.offsetAndRotation(-17.7984f, 12.9313f, 0, 0, 0, 4.08407f));
        halo.addOrReplaceChild("halo9", CubeListBuilder.create().texOffs(176, 295).mirror(false).addBox(-4, -2, -1.5f, 8, 4, 3), PartPose.offsetAndRotation(-20.9232f, 6.79837f, 0, 0, 0, 4.39823f));
        halo.addOrReplaceChild("halo10", CubeListBuilder.create().texOffs(198, 295).mirror(false).addBox(-4, -2, -1.5f, 8, 4, 3), PartPose.offsetAndRotation(-22, 2.69422e-15f, 0, 0, 0, 4.71239f));
        halo.addOrReplaceChild("halo11", CubeListBuilder.create().texOffs(220, 295).mirror(false).addBox(-4, -2, -1.5f, 8, 4, 3), PartPose.offsetAndRotation(-20.9232f, -6.79837f, 0, 0, 0, 5.02655f));
        halo.addOrReplaceChild("halo12", CubeListBuilder.create().texOffs(242, 295).mirror(false).addBox(-4, -2, -1.5f, 8, 4, 3), PartPose.offsetAndRotation(-17.7984f, -12.9313f, 0, 0, 0, 5.34071f));
        halo.addOrReplaceChild("halo13", CubeListBuilder.create().texOffs(264, 295).mirror(false).addBox(-4, -2, -1.5f, 8, 4, 3), PartPose.offsetAndRotation(-12.9313f, -17.7984f, 0, 0, 0, 5.65487f));
        halo.addOrReplaceChild("halo14", CubeListBuilder.create().texOffs(286, 295).mirror(false).addBox(-4, -2, -1.5f, 8, 4, 3), PartPose.offsetAndRotation(-6.79837f, -20.9232f, 0, 0, 0, 5.96903f));
        halo.addOrReplaceChild("halo15", CubeListBuilder.create().texOffs(308, 295).mirror(false).addBox(-4, -2, -1.5f, 8, 4, 3), PartPose.offsetAndRotation(-4.04133e-15f, -22, 0, 0, 0, 6.28319f));
        halo.addOrReplaceChild("halo16", CubeListBuilder.create().texOffs(330, 295).mirror(false).addBox(-4, -2, -1.5f, 8, 4, 3), PartPose.offsetAndRotation(6.79837f, -20.9232f, 0, 0, 0, 6.59734f));
        halo.addOrReplaceChild("halo17", CubeListBuilder.create().texOffs(352, 295).mirror(false).addBox(-4, -2, -1.5f, 8, 4, 3), PartPose.offsetAndRotation(12.9313f, -17.7984f, 0, 0, 0, 6.9115f));
        halo.addOrReplaceChild("halo18", CubeListBuilder.create().texOffs(374, 295).mirror(false).addBox(-4, -2, -1.5f, 8, 4, 3), PartPose.offsetAndRotation(17.7984f, -12.9313f, 0, 0, 0, 7.22566f));
        halo.addOrReplaceChild("halo19", CubeListBuilder.create().texOffs(396, 295).mirror(false).addBox(-4, -2, -1.5f, 8, 4, 3), PartPose.offsetAndRotation(20.9232f, -6.79837f, 0, 0, 0, 7.53982f));
        PartDefinition moss = root.addOrReplaceChild("moss", CubeListBuilder.create(), PartPose.ZERO);
        moss.addOrReplaceChild("moss_a0", CubeListBuilder.create().texOffs(418, 295).mirror(false).addBox(-2.67464f, -1.2f, -2.72678f, 5.34928f, 1.2f, 5.45357f), PartPose.offset(-40.7004f, 36, -5.83918f));
        moss.addOrReplaceChild("moss_a1", CubeListBuilder.create().texOffs(438, 295).mirror(false).addBox(-3.94276f, -1.2f, -3.4917f, 7.88551f, 1.2f, 6.9834f), PartPose.offset(11.2141f, 36, 39.3984f));
        moss.addOrReplaceChild("moss_a2", CubeListBuilder.create().texOffs(468, 295).mirror(false).addBox(-4.64617f, -1.2f, -3.22402f, 9.29234f, 1.2f, 6.44805f), PartPose.offset(41.9104f, 36, -39.9007f));
        moss.addOrReplaceChild("moss_a3", CubeListBuilder.create().texOffs(498, 295).mirror(false).addBox(-3.54531f, -1.2f, -4.39285f, 7.09061f, 1.2f, 8.7857f), PartPose.offset(42.2554f, 36, -33.6102f));
        moss.addOrReplaceChild("moss_a4", CubeListBuilder.create().texOffs(530, 295).mirror(false).addBox(-4.41143f, -1.2f, -3.93256f, 8.82285f, 1.2f, 7.86513f), PartPose.offset(-40.5498f, 36, 14.803f));
        moss.addOrReplaceChild("moss_a5", CubeListBuilder.create().texOffs(564, 295).mirror(false).addBox(-3.68525f, -1.2f, -4.16038f, 7.37049f, 1.2f, 8.32076f), PartPose.offset(29.9172f, 36, 39.1319f));
        moss.addOrReplaceChild("moss_b0", CubeListBuilder.create().texOffs(594, 295).mirror(false).addBox(-3.29426f, -1.2f, -3.98619f, 6.58852f, 1.2f, 7.97238f), PartPose.offset(-28.1172f, 24, 12.8955f));
        moss.addOrReplaceChild("moss_b1", CubeListBuilder.create().texOffs(624, 295).mirror(false).addBox(-2.3361f, -1.2f, -2.23419f, 4.67219f, 1.2f, 4.46838f), PartPose.offset(-30.556f, 24, -2.4515f));
        moss.addOrReplaceChild("moss_b2", CubeListBuilder.create().texOffs(642, 295).mirror(false).addBox(-2.25868f, -1.2f, -2.49523f, 4.51736f, 1.2f, 4.99046f), PartPose.offset(-28.2269f, 24, 17.1669f));
        moss.addOrReplaceChild("moss_b3", CubeListBuilder.create().texOffs(662, 295).mirror(false).addBox(-2.16116f, -1.2f, -2.89837f, 4.32233f, 1.2f, 5.79675f), PartPose.offset(-6.97922f, 24, 23.771f));
        moss.addOrReplaceChild("moss_c0", CubeListBuilder.create().texOffs(682, 295).mirror(false).addBox(-3.22892f, -1.2f, -3.22892f, 6.45784f, 1.2f, 6.45784f), PartPose.offset(1.58208f, -12, 12.2683f));
        moss.addOrReplaceChild("moss_c1", CubeListBuilder.create().texOffs(706, 295).mirror(false).addBox(-2.62294f, -1.2f, -2.62294f, 5.24589f, 1.2f, 5.24589f), PartPose.offset(11.6475f, -12, -7.09053f));
        moss.addOrReplaceChild("moss_c2", CubeListBuilder.create().texOffs(726, 295).mirror(false).addBox(-3.4366f, -1.2f, -3.4366f, 6.87319f, 1.2f, 6.87319f), PartPose.offset(-4.51932f, -12, 12.2942f));
        moss.addOrReplaceChild("vine0", CubeListBuilder.create().texOffs(754, 295).mirror(false).addBox(-1, 0, -0.6f, 2, 18, 1.2f), PartPose.offset(-14, -11, -20.6f));
        moss.addOrReplaceChild("vine1", CubeListBuilder.create().texOffs(760, 295).mirror(false).addBox(-1, 0, -0.6f, 2, 12, 1.2f), PartPose.offset(12, -11, -20.6f));
        moss.addOrReplaceChild("vine2", CubeListBuilder.create().texOffs(766, 295).mirror(false).addBox(-1, 0, -0.6f, 2, 16, 1.2f), PartPose.offset(-20.6f, -11, 8));
        moss.addOrReplaceChild("vine3", CubeListBuilder.create().texOffs(772, 295).mirror(false).addBox(-1, 0, -0.6f, 2, 14, 1.2f), PartPose.offset(20.6f, -11, -6));
        return LayerDefinition.create(mesh, 1024, 512);
    }

    /** Pose et animation selon le temps (en ticks) et l'étape de corruption (0 à 4). */
    public void setupAnim(float time, int stage) {
        root.getAllParts().forEach(ModelPart::resetPose);
        root.getAllParts().forEach(p -> p.visible = true);
        root.xScale = root.yScale = root.zScale = 0.5f;              // haute résolution
        float droop = stage * 0.09f;                                 // les ailes retombent avec la corruption
        float breathe = Mth.sin(time * 0.05f) * (stage >= 3 ? 0.02f : 0.05f);
        for (int i = 0; i < wings.length; i++) {
            float side = WING_ROOTS[i].contains("L") ? -1f : 1f;
            wings[i].zRot += side * (droop - breathe);
        }
        // Halo : tourne lentement (ralentit avec la corruption), se brise aux étapes 3 et 4
        halo.zRot = time * (0.012f - stage * 0.0022f);
        if (stage >= 3) for (int i : (stage == 3 ? HALO_BROKEN_3 : HALO_BROKEN_4)) haloSeg[i].visible = false;
        // Orbe : flotte doucement
        orb.y += Mth.sin(time * 0.08f) * 1.2f;
        // Plumes arrachées (toujours les mêmes, pour que la statue ne « clignote » pas)
        if (stage >= 3) {
            float lost = stage == 3 ? 0.15f : 0.35f;
            for (int i = 0; i < feathers.size(); i++) {
                if (((i * 37 + 11) % 100) / 100f < lost) feathers.get(i).visible = false;
            }
        }
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, float r, float g, float b, float a) {
        root.render(pose, buffer, light, overlay, r, g, b, a);
    }
}
