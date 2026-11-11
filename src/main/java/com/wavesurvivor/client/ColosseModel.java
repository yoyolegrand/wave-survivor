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
 * 2.0 — MODÈLE DU COLOSSE DE LA FAILLE, Titan (conçu sur mesure par Claude chat, NE PAS régénérer à la main :
 * le modèle, la texture et les aperçus viennent d'un même script, voir docs/faille/titans/colosse/).
 * Géant d'obsidienne taillée en facettes, veines turquoise lumineuses, grappes de cristaux violets sur les épaules,
 * le dos et la tête, cœur de vide dans la poitrine. Silhouette de gorille : torse penché, bras longs, tête enfoncée.
 * Texture 512x256 + calque lumineux (veines, cœur, yeux, cristaux). Taille naturelle ~10 blocs (agrandi par Pehkui).
 * Animations : respiration, bras qui se balancent, regard qui erre, marche (limbSwing, pendant la charge), et 8 POSES
 * de compétence lues sur l'entité si elle implémente {@link TitanAnimated} (purement visuelles).
 */
@OnlyIn(Dist.CLIENT)
public class ColosseModel<T extends Mob> extends HierarchicalModel<T> {

    public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(new ResourceLocation("wavesurvivor", "colosse"), "main");

    private static final float TORSO_X = 0.32F;
    // Images clés (avancement, angle) des poses
    private static final float[] SLAM_ARM = {0F, 0F, 0.15F, -1.2F, 0.45F, -2.95F, 0.58F, -1.1F, 0.85F, -1.1F, 1F, 0F};
    private static final float[] SLAM_TORSO = {0F, 0F, 0.45F, -0.18F, 0.58F, 0.5F, 0.85F, 0.5F, 1F, 0F};
    private static final float[] SLAM_KNEE = {0F, 0F, 0.45F, 0.1F, 0.58F, 0.55F, 0.85F, 0.55F, 1F, 0F};
    private static final float[] THROW_ARM = {0F, 0F, 0.3F, -0.75F, 0.4F, -0.75F, 0.62F, -2.9F, 0.75F, -2.9F, 0.86F, -1F, 1F, 0F};
    private static final float[] THROW_TORSO = {0F, 0F, 0.3F, 0.55F, 0.4F, 0.55F, 0.62F, -0.2F, 0.75F, -0.2F, 0.86F, 0.35F, 1F, 0F};
    private static final float[] THROW_KNEE = {0F, 0F, 0.3F, 0.6F, 0.4F, 0.6F, 0.62F, 0F, 1F, 0F};
    private static final float[] CHARGE_LEAN = {0F, 0F, 0.5F, 0.3F, 0.62F, 0.45F, 0.95F, 0.45F, 1F, 0F};
    private static final float[] CHARGE_ARM = {0F, 0F, 0.5F, 0.75F, 0.62F, 0.95F, 0.95F, 0.95F, 1F, 0F};

    private final ModelPart root, body, torso, head, jaw;
    private final ModelPart[] arm = new ModelPart[2], elbow = new ModelPart[2], leg = new ModelPart[2], knee = new ModelPart[2];
    /** côté de chaque bras/jambe : -1 = droite (x négatif), +1 = gauche */
    private static final int[] SIDE = {-1, 1};

    public ColosseModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.torso = body.getChild("torso");
        this.head = torso.getChild("head");
        this.jaw = head.getChild("jaw");
        String[] a = {"arm_r", "arm_l"}, l = {"leg_r", "leg_l"};
        for (int i = 0; i < 2; i++) {
            arm[i] = torso.getChild(a[i]);
            elbow[i] = arm[i].getChild(a[i] + "_elbow");
            leg[i] = body.getChild(l[i]);
            knee[i] = leg[i].getChild(l[i] + "_knee");
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 111).addBox(-21F, -12F, -13F, 42F, 15F, 26F), PartPose.offsetAndRotation(0F, -40F, 0F, 0F, 0F, 0F));
        PartDefinition p_leg_r = p_body.addOrReplaceChild("leg_r", CubeListBuilder.create().texOffs(252, 0).addBox(-10F, 0F, -10F, 20F, 31F, 20F), PartPose.offsetAndRotation(-15F, 0F, 0F, 0F, 0F, 0F));
        p_leg_r.addOrReplaceChild("leg_r_knee", CubeListBuilder.create().texOffs(0, 63).addBox(-11F, 0F, -11F, 22F, 26F, 22F).texOffs(136, 111).addBox(-12F, 24F, -15F, 24F, 10F, 26F).texOffs(334, 152).addBox(-8F, -3F, -13F, 16F, 8F, 4F), PartPose.offsetAndRotation(0F, 30F, 0F, 0F, 0F, 0F));
        PartDefinition p_leg_l = p_body.addOrReplaceChild("leg_l", CubeListBuilder.create().texOffs(252, 0).addBox(-10F, 0F, -10F, 20F, 31F, 20F), PartPose.offsetAndRotation(15F, 0F, 0F, 0F, 0F, 0F));
        p_leg_l.addOrReplaceChild("leg_l_knee", CubeListBuilder.create().texOffs(0, 63).addBox(-11F, 0F, -11F, 22F, 26F, 22F).texOffs(136, 111).addBox(-12F, 24F, -15F, 24F, 10F, 26F).texOffs(334, 152).addBox(-8F, -3F, -13F, 16F, 8F, 4F), PartPose.offsetAndRotation(0F, 30F, 0F, 0F, 0F, 0F));
        PartDefinition p_torso = p_body.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(160, 63).addBox(-16F, -22F, -12F, 32F, 23F, 24F).texOffs(0, 0).addBox(-25F, -52F, -16F, 50F, 31F, 32F).texOffs(236, 111).addBox(-20F, -58F, -12F, 40F, 7F, 26F).texOffs(368, 111).addBox(-15F, -48F, 15F, 30F, 22F, 6F).texOffs(92, 152).addBox(-8F, -43F, -18F, 16F, 16F, 1F).texOffs(40, 174).addBox(-11F, -45F, -19F, 22F, 3F, 2F).texOffs(40, 174).addBox(-11F, -27F, -19F, 22F, 3F, 2F).texOffs(146, 152).addBox(-11F, -42F, -19F, 3F, 15F, 2F).texOffs(146, 152).addBox(8F, -42F, -19F, 3F, 15F, 2F), PartPose.offsetAndRotation(0F, -10F, 2F, 0.32F, 0F, 0F));
        p_torso.addOrReplaceChild("back_crystal0", CubeListBuilder.create().texOffs(156, 152).addBox(-3F, -10F, -3F, 6F, 10F, 6F).texOffs(318, 152).addBox(-2F, -19F, -2F, 4F, 9F, 4F).texOffs(0, 174).addBox(-1F, -25F, -1F, 2F, 6F, 2F), PartPose.offsetAndRotation(-9F, -54F, 14F, 0.6F, 0F, -0.3F));
        p_torso.addOrReplaceChild("back_crystal1", CubeListBuilder.create().texOffs(32, 152).addBox(-4F, -13F, -4F, 8F, 13F, 8F).texOffs(180, 152).addBox(-2.5F, -24F, -2.5F, 5F, 11F, 5F).texOffs(414, 152).addBox(-1F, -32F, -1F, 2F, 8F, 2F), PartPose.offsetAndRotation(6F, -54F, 15F, 0.75F, 0F, 0.25F));
        p_torso.addOrReplaceChild("back_crystal2", CubeListBuilder.create().texOffs(200, 152).addBox(-3F, -9F, -3F, 6F, 9F, 6F).texOffs(374, 152).addBox(-1.5F, -17F, -1.5F, 3F, 8F, 3F).texOffs(0, 174).addBox(-1F, -23F, -1F, 2F, 6F, 2F), PartPose.offsetAndRotation(0F, -54F, 10F, 0.4F, 0F, 0.05F));
        PartDefinition p_head = p_torso.addOrReplaceChild("head", CubeListBuilder.create().texOffs(370, 63).addBox(-13F, -19F, -13F, 26F, 20F, 22F).texOffs(422, 152).addBox(-14F, -13F, -16F, 28F, 4F, 5F).texOffs(96, 174).addBox(-10F, -9F, -14F, 6F, 3F, 1F).texOffs(96, 174).addBox(4F, -9F, -14F, 6F, 3F, 1F), PartPose.offsetAndRotation(0F, -55F, -14F, -0.32F, 0F, 0F));
        p_head.addOrReplaceChild("head_crystal0", CubeListBuilder.create().texOffs(386, 152).addBox(-2F, -6F, -2F, 4F, 6F, 4F).texOffs(8, 174).addBox(-1F, -12F, -1F, 2F, 6F, 2F).texOffs(32, 174).addBox(-1F, -16F, -1F, 2F, 4F, 2F), PartPose.offsetAndRotation(-5F, -19F, 2F, 0.3F, 0F, -0.35F));
        p_head.addOrReplaceChild("head_crystal1", CubeListBuilder.create().texOffs(496, 152).addBox(-1.5F, -5F, -1.5F, 3F, 5F, 3F).texOffs(16, 174).addBox(-1F, -10F, -1F, 2F, 5F, 2F).texOffs(88, 174).addBox(-1F, -13F, -1F, 2F, 3F, 2F), PartPose.offsetAndRotation(5F, -19F, 4F, 0.45F, 0F, 0.3F));
        p_head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(440, 111).addBox(-10F, 0F, -12F, 20F, 6F, 16F).texOffs(224, 152).addBox(-8F, -1F, -11F, 16F, 1F, 13F), PartPose.offsetAndRotation(0F, -2F, -2F, 0F, 0F, 0F));
        PartDefinition p_arm_r = p_torso.addOrReplaceChild("arm_r", CubeListBuilder.create().texOffs(332, 0).addBox(-13F, -14F, -13F, 26F, 24F, 26F).texOffs(88, 63).addBox(-9F, 8F, -9F, 18F, 30F, 18F), PartPose.offsetAndRotation(-36F, -46F, -2F, -0.37F, 0F, 0.3F));
        p_arm_r.addOrReplaceChild("arm_r_crystal0", CubeListBuilder.create().texOffs(0, 152).addBox(-4F, -14F, -4F, 8F, 14F, 8F).texOffs(126, 152).addBox(-2.5F, -26F, -2.5F, 5F, 12F, 5F).texOffs(414, 152).addBox(-1F, -34F, -1F, 2F, 8F, 2F), PartPose.offsetAndRotation(0F, -14F, -3F, 0.15F, 0F, -0.55F));
        p_arm_r.addOrReplaceChild("arm_r_crystal1", CubeListBuilder.create().texOffs(156, 152).addBox(-3F, -10F, -3F, 6F, 10F, 6F).texOffs(318, 152).addBox(-2F, -19F, -2F, 4F, 9F, 4F).texOffs(0, 174).addBox(-1F, -25F, -1F, 2F, 6F, 2F), PartPose.offsetAndRotation(-5F, -14F, 6F, 0.5F, 0F, -0.85F));
        p_arm_r.addOrReplaceChild("arm_r_crystal2", CubeListBuilder.create().texOffs(64, 152).addBox(-3.5F, -11F, -3.5F, 7F, 11F, 7F).texOffs(282, 152).addBox(-2F, -21F, -2F, 4F, 10F, 4F).texOffs(488, 152).addBox(-1F, -28F, -1F, 2F, 7F, 2F), PartPose.offsetAndRotation(4F, -14F, 4F, 0.35F, 0F, -0.2F));
        p_arm_r.addOrReplaceChild("arm_r_crystal3", CubeListBuilder.create().texOffs(298, 152).addBox(-2.5F, -8F, -2.5F, 5F, 8F, 5F).texOffs(402, 152).addBox(-1.5F, -15F, -1.5F, 3F, 7F, 3F).texOffs(24, 174).addBox(-1F, -20F, -1F, 2F, 5F, 2F), PartPose.offsetAndRotation(-7F, -14F, -7F, -0.2F, 0F, -1F));
        PartDefinition p_arm_r_elbow = p_arm_r.addOrReplaceChild("arm_r_elbow", CubeListBuilder.create().texOffs(164, 0).addBox(-11F, 0F, -11F, 22F, 32F, 22F).texOffs(272, 63).addBox(-12F, 30F, -13F, 24F, 18F, 25F), PartPose.offsetAndRotation(0F, 36F, 0F, -0.15F, 0F, 0F));
        p_arm_r_elbow.addOrReplaceChild("arm_r_fore_crystal", CubeListBuilder.create().texOffs(298, 152).addBox(-2.5F, -8F, -2.5F, 5F, 8F, 5F).texOffs(402, 152).addBox(-1.5F, -15F, -1.5F, 3F, 7F, 3F).texOffs(24, 174).addBox(-1F, -20F, -1F, 2F, 5F, 2F), PartPose.offsetAndRotation(-3F, 4F, 9F, 1.1F, 0F, -0.3F));
        PartDefinition p_arm_l = p_torso.addOrReplaceChild("arm_l", CubeListBuilder.create().texOffs(332, 0).addBox(-13F, -14F, -13F, 26F, 24F, 26F).texOffs(88, 63).addBox(-9F, 8F, -9F, 18F, 30F, 18F), PartPose.offsetAndRotation(36F, -46F, -2F, -0.37F, 0F, -0.3F));
        p_arm_l.addOrReplaceChild("arm_l_crystal0", CubeListBuilder.create().texOffs(0, 152).addBox(-4F, -14F, -4F, 8F, 14F, 8F).texOffs(126, 152).addBox(-2.5F, -26F, -2.5F, 5F, 12F, 5F).texOffs(414, 152).addBox(-1F, -34F, -1F, 2F, 8F, 2F), PartPose.offsetAndRotation(0F, -14F, -3F, 0.15F, 0F, 0.55F));
        p_arm_l.addOrReplaceChild("arm_l_crystal1", CubeListBuilder.create().texOffs(156, 152).addBox(-3F, -10F, -3F, 6F, 10F, 6F).texOffs(318, 152).addBox(-2F, -19F, -2F, 4F, 9F, 4F).texOffs(0, 174).addBox(-1F, -25F, -1F, 2F, 6F, 2F), PartPose.offsetAndRotation(5F, -14F, 6F, 0.5F, 0F, 0.85F));
        p_arm_l.addOrReplaceChild("arm_l_crystal2", CubeListBuilder.create().texOffs(64, 152).addBox(-3.5F, -11F, -3.5F, 7F, 11F, 7F).texOffs(282, 152).addBox(-2F, -21F, -2F, 4F, 10F, 4F).texOffs(488, 152).addBox(-1F, -28F, -1F, 2F, 7F, 2F), PartPose.offsetAndRotation(-4F, -14F, 4F, 0.35F, 0F, 0.2F));
        p_arm_l.addOrReplaceChild("arm_l_crystal3", CubeListBuilder.create().texOffs(298, 152).addBox(-2.5F, -8F, -2.5F, 5F, 8F, 5F).texOffs(402, 152).addBox(-1.5F, -15F, -1.5F, 3F, 7F, 3F).texOffs(24, 174).addBox(-1F, -20F, -1F, 2F, 5F, 2F), PartPose.offsetAndRotation(7F, -14F, -7F, -0.2F, 0F, 1F));
        PartDefinition p_arm_l_elbow = p_arm_l.addOrReplaceChild("arm_l_elbow", CubeListBuilder.create().texOffs(164, 0).addBox(-11F, 0F, -11F, 22F, 32F, 22F).texOffs(272, 63).addBox(-12F, 30F, -13F, 24F, 18F, 25F), PartPose.offsetAndRotation(0F, 36F, 0F, -0.15F, 0F, 0F));
        p_arm_l_elbow.addOrReplaceChild("arm_l_fore_crystal", CubeListBuilder.create().texOffs(298, 152).addBox(-2.5F, -8F, -2.5F, 5F, 8F, 5F).texOffs(402, 152).addBox(-1.5F, -15F, -1.5F, 3F, 7F, 3F).texOffs(24, 174).addBox(-1F, -20F, -1F, 2F, 5F, 2F), PartPose.offsetAndRotation(3F, 4F, 9F, 1.1F, 0F, 0.3F));
        return LayerDefinition.create(mesh, 512, 256);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    private static float env(float p) {
        return Mth.sin((float) Math.PI * Mth.clamp(p, 0f, 1f));
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

    @Override
    public void setupAnim(T e, float swing, float swingAmt, float t, float netHeadYaw, float headPitch) {
        // ── Repos : respiration, balancement des bras, regard qui erre ──
        float br = Mth.sin(t * 0.08f);
        body.y = -40f;
        torso.xRot = TORSO_X + 0.025f * br;
        torso.yRot = 0f;
        torso.zRot = 0f;
        head.xRot = -TORSO_X + 0.03f * Mth.sin(t * 0.05f);
        head.yRot = 0.12f * Mth.sin(t * 0.021f);
        jaw.xRot = 0.04f + 0.03f * br;
        for (int i = 0; i < 2; i++) {
            int sx = SIDE[i];
            arm[i].xRot = -TORSO_X - 0.05f + 0.04f * Mth.sin(t * 0.06f + sx);
            arm[i].yRot = 0f;
            arm[i].zRot = -sx * (0.3f + 0.02f * br);
            elbow[i].xRot = -0.15f;
            // ── Marche (pendant la charge, si l'entité se déplace) ──
            float w = Mth.cos(swing * 0.4f + (sx > 0 ? (float) Math.PI : 0f)) * 0.55f * swingAmt;
            leg[i].xRot = w;
            knee[i].xRot = Math.max(0f, -w) * 0.8f;
        }
        if (!(e instanceof TitanAnimated ta)) return;
        TitanAnimated.Pose pose = ta.titanPose();
        if (pose == null || pose == TitanAnimated.Pose.IDLE) return;
        float p = Mth.clamp(ta.titanPoseProgress(t), 0f, 1f);
        float en = env(p);
        switch (pose) {
            case ROAR -> {        // se cambre, poings levés, gueule ouverte
                torso.xRot -= 0.3f * en;
                head.xRot -= 0.45f * en;
                jaw.xRot += 0.55f * en;
                for (int i = 0; i < 2; i++) {
                    arm[i].xRot -= 0.45f * en;
                    arm[i].zRot -= SIDE[i] * 0.5f * en;
                    elbow[i].xRot -= 0.95f * en;
                }
                torso.zRot = 0.02f * Mth.sin(t * 1.6f) * en;
            }
            case BEAM -> {        // bombe le torse : le rayon jaillit du cœur et balaie
                float sweep = Mth.clamp(p * 2.4f - 1.2f, -1f, 1f);
                torso.xRot -= 0.22f * en;
                torso.yRot = 0.4f * sweep * en;
                head.xRot -= 0.2f * en;
                for (int i = 0; i < 2; i++) {
                    arm[i].xRot += 0.55f * en;
                    arm[i].zRot -= SIDE[i] * 0.35f * en;
                }
            }
            case SLAM -> {        // lève les deux poings au-dessus de la tête puis frappe le sol
                float a = kf(p, SLAM_ARM);
                torso.xRot += kf(p, SLAM_TORSO);
                for (int i = 0; i < 2; i++) {
                    arm[i].xRot += a;
                    arm[i].zRot += SIDE[i] * 0.12f * en;
                }
                float k = kf(p, SLAM_KNEE);
                for (int i = 0; i < 2; i++) {
                    leg[i].xRot -= k;
                    knee[i].xRot += 2f * k;
                }
                body.y = -40f + 10f * k;
                jaw.xRot += 0.35f * en;
            }
            case THROW -> {       // se baisse, arrache un rocher, le soulève et le lance
                float a = kf(p, THROW_ARM);
                torso.xRot += kf(p, THROW_TORSO);
                for (int i = 0; i < 2; i++) {
                    arm[i].xRot += a;
                    arm[i].zRot += SIDE[i] * 0.15f * en;
                }
                float k = kf(p, THROW_KNEE);
                for (int i = 0; i < 2; i++) {
                    leg[i].xRot -= k;
                    knee[i].xRot += 2f * k;
                }
                body.y = -40f + 10f * k;
            }
            case SUMMON -> {      // bras levés en V, appelle les avatars
                torso.xRot -= 0.25f * en;
                head.xRot -= 0.4f * en;
                jaw.xRot += 0.4f * en;
                for (int i = 0; i < 2; i++) {
                    arm[i].xRot -= 0.25f * en;
                    arm[i].zRot -= SIDE[i] * 2.3f * en;
                    elbow[i].xRot -= 0.3f * en;
                }
            }
            case CHARGE -> {      // se ramasse puis fonce, épaule en avant
                float lean = kf(p, CHARGE_LEAN);
                torso.xRot += lean;
                torso.yRot = -0.3f * lean;
                head.xRot -= 0.6f * lean;
                float a = kf(p, CHARGE_ARM);
                float run = p > 0.5f ? (p - 0.5f) / 0.5f : 0f;
                for (int i = 0; i < 2; i++) {
                    arm[i].xRot += a;
                    float w = Mth.sin(t * 0.9f + (SIDE[i] > 0 ? (float) Math.PI : 0f)) * 0.6f * run;
                    leg[i].xRot += w;
                    knee[i].xRot += Math.max(0f, -w) * 0.9f;
                }
            }
            case STUNNED -> {     // tombe à genoux, bras ballants, tête basse
                float se = (float) Math.sqrt(en);
                for (int i = 0; i < 2; i++) {
                    leg[i].xRot -= 0.25f * se;
                    knee[i].xRot += 1.75f * se;
                    arm[i].xRot -= 0.35f * en;
                    arm[i].zRot += SIDE[i] * 0.05f * en;
                }
                body.y = -40f + 18f * en * en;
                torso.xRot += 0.4f * en;
                head.xRot += 0.35f * en;
                jaw.xRot += 0.2f * en;
                torso.zRot = 0.05f * Mth.sin(t * 0.25f) * en;
            }
            case HURT -> {        // recule sous le choc
                torso.xRot -= 0.22f * en;
                head.xRot -= 0.3f * en;
                jaw.xRot += 0.25f * en;
            }
            default -> { }
        }
    }
}
