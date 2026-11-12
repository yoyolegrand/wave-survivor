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
 * GÉODON (monstre de la Faille) : bête à carapace-géode de 5 plaques articulées hérissées d'améthyste ; marche, s'enroule en boule pour charger, se referme en géode.
 * Généré par script (Claude chat, docs/faille/monstres/sources/) : ne pas retoucher à la main.
 * Dessiné à l'échelle 2 puis réduit de moitié (haute résolution). Texture 256x128 + calque lumineux.
 */
@OnlyIn(Dist.CLIENT)
public class GeodonModel<T extends Mob> extends HierarchicalModel<T> {

    public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(new ResourceLocation("wavesurvivor", "geodon"), "main");

    private final ModelPart root;
    private final ModelPart body, belly, head, tail;
    private final ModelPart[] shell = new ModelPart[5], legs = new ModelPart[4], crack = new ModelPart[3];
    /** angles de repos (dôme) et angles « enroulé en boule » de chaque plaque */
    private static final float[] DOME = {0.38F, -0.18F, -0.2F, -0.24F, -0.26F};
    private static final float[] CURL = {-1.0F, 1.05F, 1.05F, 1.05F, 1.05F};

    public GeodonModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.belly = body.getChild("belly");
        this.head = body.getChild("head");
        ModelPart s = body;
        for (int i = 0; i < 5; i++) { s = s.getChild("shell" + i); shell[i] = s; }
        this.tail = shell[4].getChild("tail");
        for (int i = 0; i < 3; i++) crack[i] = shell[i + 1].getChild("crack" + i);
        String[] n = {"leg_lf", "leg_lb", "leg_rf", "leg_rb"};
        for (int i = 0; i < 4; i++) legs[i] = body.getChild(n[i]);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 22F, 0F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("belly", CubeListBuilder.create().texOffs(0, 0).addBox(-11F, -9F, -16F, 22F, 14F, 32F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_shell0 = p_body.addOrReplaceChild("shell0", CubeListBuilder.create().texOffs(0, 46).addBox(-11F, -10F, 0F, 22F, 11F, 9F).texOffs(152, 66).addBox(-12F, -2F, 0F, 24F, 3F, 9F), PartPose.offsetAndRotation(0F, -6F, -14F, 0.38F, 0F, 0F));
        PartDefinition p_shell1 = p_shell0.addOrReplaceChild("shell1", CubeListBuilder.create().texOffs(182, 0).addBox(-13F, -10F, 0F, 26F, 11F, 9F).texOffs(78, 66).addBox(-14F, -2F, 0F, 28F, 3F, 9F), PartPose.offsetAndRotation(0F, 0F, 8F, -0.1463F, 0F, 0F));
        p_shell1.addOrReplaceChild("shell1_c0", CubeListBuilder.create().texOffs(90, 78).addBox(-2F, -6F, -2F, 4F, 6F, 4F).texOffs(118, 78).addBox(-1F, -11F, -1F, 2F, 5F, 2F).texOffs(142, 78).addBox(-1F, -14F, -1F, 2F, 3F, 2F), PartPose.offsetAndRotation(-5F, -10F, 4F, -0.15F, 0F, -0.35F));
        p_shell1.addOrReplaceChild("shell1_c1", CubeListBuilder.create().texOffs(0, 78).addBox(-2.5F, -7F, -2.5F, 5F, 7F, 5F).texOffs(106, 78).addBox(-1.5F, -13F, -1.5F, 3F, 6F, 3F).texOffs(134, 78).addBox(-1F, -17F, -1F, 2F, 4F, 2F), PartPose.offsetAndRotation(0F, -10F, 4F, -0.15F, 0F, 0F));
        p_shell1.addOrReplaceChild("shell1_c2", CubeListBuilder.create().texOffs(90, 78).addBox(-2F, -6F, -2F, 4F, 6F, 4F).texOffs(118, 78).addBox(-1F, -11F, -1F, 2F, 5F, 2F).texOffs(142, 78).addBox(-1F, -14F, -1F, 2F, 3F, 2F), PartPose.offsetAndRotation(5F, -10F, 4F, -0.15F, 0F, 0.35F));
        PartDefinition p_shell2 = p_shell1.addOrReplaceChild("shell2", CubeListBuilder.create().texOffs(108, 0).addBox(-14F, -10F, 0F, 28F, 11F, 9F).texOffs(0, 66).addBox(-15F, -2F, 0F, 30F, 3F, 9F), PartPose.offsetAndRotation(0F, 0F, 8F, -0.1636F, 0F, 0F));
        p_shell2.addOrReplaceChild("shell2_c0", CubeListBuilder.create().texOffs(90, 78).addBox(-2F, -6F, -2F, 4F, 6F, 4F).texOffs(118, 78).addBox(-1F, -11F, -1F, 2F, 5F, 2F).texOffs(142, 78).addBox(-1F, -14F, -1F, 2F, 3F, 2F), PartPose.offsetAndRotation(-5F, -10F, 4F, 0F, 0F, -0.35F));
        p_shell2.addOrReplaceChild("shell2_c1", CubeListBuilder.create().texOffs(0, 78).addBox(-2.5F, -7F, -2.5F, 5F, 7F, 5F).texOffs(106, 78).addBox(-1.5F, -13F, -1.5F, 3F, 6F, 3F).texOffs(134, 78).addBox(-1F, -17F, -1F, 2F, 4F, 2F), PartPose.offsetAndRotation(0F, -10F, 4F, 0F, 0F, 0F));
        p_shell2.addOrReplaceChild("shell2_c2", CubeListBuilder.create().texOffs(90, 78).addBox(-2F, -6F, -2F, 4F, 6F, 4F).texOffs(118, 78).addBox(-1F, -11F, -1F, 2F, 5F, 2F).texOffs(142, 78).addBox(-1F, -14F, -1F, 2F, 3F, 2F), PartPose.offsetAndRotation(5F, -10F, 4F, 0F, 0F, 0.35F));
        PartDefinition p_shell3 = p_shell2.addOrReplaceChild("shell3", CubeListBuilder.create().texOffs(182, 0).addBox(-13F, -10F, 0F, 26F, 11F, 9F).texOffs(78, 66).addBox(-14F, -2F, 0F, 28F, 3F, 9F), PartPose.offsetAndRotation(0F, 0F, 8F, -0.2344F, 0F, 0F));
        p_shell3.addOrReplaceChild("shell3_c0", CubeListBuilder.create().texOffs(90, 78).addBox(-2F, -6F, -2F, 4F, 6F, 4F).texOffs(118, 78).addBox(-1F, -11F, -1F, 2F, 5F, 2F).texOffs(142, 78).addBox(-1F, -14F, -1F, 2F, 3F, 2F), PartPose.offsetAndRotation(-5F, -10F, 4F, 0.15F, 0F, -0.35F));
        p_shell3.addOrReplaceChild("shell3_c1", CubeListBuilder.create().texOffs(0, 78).addBox(-2.5F, -7F, -2.5F, 5F, 7F, 5F).texOffs(106, 78).addBox(-1.5F, -13F, -1.5F, 3F, 6F, 3F).texOffs(134, 78).addBox(-1F, -17F, -1F, 2F, 4F, 2F), PartPose.offsetAndRotation(0F, -10F, 4F, 0.15F, 0F, 0F));
        p_shell3.addOrReplaceChild("shell3_c2", CubeListBuilder.create().texOffs(90, 78).addBox(-2F, -6F, -2F, 4F, 6F, 4F).texOffs(118, 78).addBox(-1F, -11F, -1F, 2F, 5F, 2F).texOffs(142, 78).addBox(-1F, -14F, -1F, 2F, 3F, 2F), PartPose.offsetAndRotation(5F, -10F, 4F, 0.15F, 0F, 0.35F));
        PartDefinition p_shell4 = p_shell3.addOrReplaceChild("shell4", CubeListBuilder.create().texOffs(0, 46).addBox(-11F, -10F, 0F, 22F, 11F, 9F).texOffs(152, 66).addBox(-12F, -2F, 0F, 24F, 3F, 9F), PartPose.offsetAndRotation(0F, 0F, 8F, -0.2903F, 0F, 0F));
        p_shell4.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(134, 46).addBox(-5F, -4F, 0F, 10F, 6F, 10F).texOffs(218, 66).addBox(-3F, -3F, 9F, 6F, 4F, 8F), PartPose.offsetAndRotation(0F, -3F, 8F, -0.35F, 0F, 0F));
        p_shell3.addOrReplaceChild("crack2", CubeListBuilder.create().texOffs(174, 46).addBox(-3F, -8F, -3F, 6F, 8F, 6F).texOffs(52, 78).addBox(-2F, -15F, -2F, 4F, 7F, 4F).texOffs(126, 78).addBox(-1F, -20F, -1F, 2F, 5F, 2F), PartPose.offsetAndRotation(0F, 0F, 8F, -0.8F, 0F, 0.3F));
        p_shell2.addOrReplaceChild("crack1", CubeListBuilder.create().texOffs(174, 46).addBox(-3F, -8F, -3F, 6F, 8F, 6F).texOffs(52, 78).addBox(-2F, -15F, -2F, 4F, 7F, 4F).texOffs(126, 78).addBox(-1F, -20F, -1F, 2F, 5F, 2F), PartPose.offsetAndRotation(0F, 0F, 8F, -0.8F, 0F, 0F));
        p_shell1.addOrReplaceChild("crack0", CubeListBuilder.create().texOffs(174, 46).addBox(-3F, -8F, -3F, 6F, 8F, 6F).texOffs(52, 78).addBox(-2F, -15F, -2F, 4F, 7F, 4F).texOffs(126, 78).addBox(-1F, -20F, -1F, 2F, 5F, 2F), PartPose.offsetAndRotation(0F, 0F, 8F, -0.8F, 0F, -0.3F));
        p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(90, 46).addBox(-6F, -5F, -10F, 12F, 9F, 10F).texOffs(198, 46).addBox(-7F, -7F, -9F, 14F, 4F, 9F).texOffs(68, 78).addBox(-3F, -2F, -15F, 6F, 5F, 5F).texOffs(150, 78).addBox(-6.5F, -4F, -8F, 1F, 2F, 3F).texOffs(150, 78).addBox(5.5F, -4F, -8F, 1F, 2F, 3F), PartPose.offsetAndRotation(0F, -2F, -16F, 0.1F, 0F, 0F));
        p_body.addOrReplaceChild("leg_rf", CubeListBuilder.create().texOffs(62, 46).addBox(-3.5F, 0F, -3.5F, 7F, 13F, 7F).texOffs(20, 78).addBox(-4F, 11F, -5F, 8F, 3F, 8F), PartPose.offsetAndRotation(-8F, 4F, -9F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("leg_rb", CubeListBuilder.create().texOffs(62, 46).addBox(-3.5F, 0F, -3.5F, 7F, 13F, 7F).texOffs(20, 78).addBox(-4F, 11F, -5F, 8F, 3F, 8F), PartPose.offsetAndRotation(-8F, 4F, 9F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("leg_lf", CubeListBuilder.create().texOffs(62, 46).addBox(-3.5F, 0F, -3.5F, 7F, 13F, 7F).texOffs(20, 78).addBox(-4F, 11F, -5F, 8F, 3F, 8F), PartPose.offsetAndRotation(8F, 4F, -9F, 0F, 0F, 0F));
        p_body.addOrReplaceChild("leg_lb", CubeListBuilder.create().texOffs(62, 46).addBox(-3.5F, 0F, -3.5F, 7F, 13F, 7F).texOffs(20, 78).addBox(-4F, 11F, -5F, 8F, 3F, 8F), PartPose.offsetAndRotation(8F, 4F, 9F, 0F, 0F, 0F));
        return LayerDefinition.create(mesh, 256, 128);
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
        // États : 0 = MARCHE, 1 = ROULÉ (charge en boule), 2 = GÉODE (refermé, quasi invulnérable de face).
        // failleProgress : 0 → 1 depuis le début de l'état ; l'enroulement se fait sur le premier tiers.
        float curl = st == 0 ? 0f : smooth(p * 3f);
        // marche : pattes en diagonale, petit balancement de la carapace
        float sw = Mth.sin(w) * 0.6f * a * (1f - curl);
        legs[0].xRot = sw; legs[3].xRot = sw; legs[1].xRot = -sw; legs[2].xRot = -sw;
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD * 0.5f * (1f - curl);
        head.xRot += headPitch * Mth.DEG_TO_RAD * 0.3f * (1f - curl);
        for (int i = 0; i < 5; i++) {
            float breathe = 0.04f * Mth.sin(t * 0.3f + i) * (1f - curl);
            shell[i].xRot = Mth.lerp(curl, DOME[i] + breathe, CURL[i]);
        }
        tail.xRot = Mth.lerp(curl, -0.35f, 1.2f);
        // enroulé : la tête, les pattes et le ventre rentrent dans la boule
        float hide = 1f - 0.99f * curl;
        scale(head, hide);
        scale(belly, hide);
        for (ModelPart l : legs) scale(l, hide);
        body.y = 22f + curl * (st == 2 ? 8f : 4f);
        if (st == 1) body.xRot = t * 0.6f * curl;              // la boule roule
        boolean geode = st == 2 && curl > 0.9f;                  // les cristaux jaillissent des jointures
        for (ModelPart c : crack) c.visible = geode;
    }
}
