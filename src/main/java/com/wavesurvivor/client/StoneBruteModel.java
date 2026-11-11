package com.wavesurvivor.client;

import com.wavesurvivor.entity.StoneBrute;
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
 * MODÈLE DU STONE BRUTE — REFONTE 2.0 (Claude chat ; généré par script, voir docs/corrompus/stone_brute_refonte/ : ne pas
 * retoucher à la main). Dessiné à l'échelle 2 puis réduit de moitié (haute résolution). Colosse de pierres maçonnées inspiré
 * du golem de fer : tête basse et en avant avec arcade unique et LONG NEZ, grand poitrail sur une taille étroite, longs bras
 * aux poings énormes, épaules-rochers moussues, lianes pendantes. Corruption rose : cœur lumineux au centre du poitrail d'où
 * partent les fissures, grappe de cristaux de souillure sur l'épaule gauche, fleur fanée sur l'épaule droite.
 * Texture 256x256 + calque lumineux (mêmes chemins qu'avant).
 * Animations : marche lourde (torse qui tangue), respiration, tête qui suit sa cible, fleur qui oscille,
 * FRAPPE ÉCRASANTE (poing droit levé puis abattu, inchangée) et LANCER DE ROCHER (se baisse et arrache un rocher,
 * le soulève au-dessus de sa tête, le lance) lu sur l'entité via {@code getThrowProgress(ageInTicks)}.
 */
@OnlyIn(Dist.CLIENT)
public class StoneBruteModel<T extends StoneBrute> extends HierarchicalModel<T> {

    public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(new ResourceLocation("wavesurvivor", "stone_brute"), "main");

    private static final float HUNCH = 0.14F;
    private static final float[] THROW_ARM = {0F, 0F, 0.3F, -0.9F, 0.38F, -0.9F, 0.6F, -3F, 0.68F, -3F, 0.8F, -1.2F, 1F, 0F};
    private static final float[] THROW_TORSO = {0F, 0F, 0.3F, 0.5F, 0.38F, 0.5F, 0.6F, -0.25F, 0.68F, -0.25F, 0.8F, 0.35F, 1F, 0F};
    private static final float[] THROW_KNEE = {0F, 0F, 0.3F, 0.55F, 0.38F, 0.55F, 0.6F, 0F, 1F, 0F};

    private final ModelPart root, pelvis, torso, head, armL, armR, foreL, foreR, legL, legR, shinL, shinR, flower;

    public StoneBruteModel(ModelPart root) {
        this.root = root;
        this.pelvis = root.getChild("pelvis");
        this.torso = root.getChild("torso");
        this.head = torso.getChild("head");
        this.armL = torso.getChild("arm_l");
        this.armR = torso.getChild("arm_r");
        this.foreL = armL.getChild("arm_l_fore");
        this.foreR = armR.getChild("arm_r_fore");
        this.flower = armR.getChild("flower");
        this.legL = root.getChild("leg_l");
        this.legR = root.getChild("leg_r");
        this.shinL = legL.getChild("leg_l_shin");
        this.shinR = legR.getChild("leg_r_shin");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("pelvis", CubeListBuilder.create().texOffs(88, 134).addBox(-15F, -4F, -9F, 30F, 12F, 18F), PartPose.offsetAndRotation(0F, 0F, 0F, 0F, 0F, 0F));
        PartDefinition p_leg_r = root.addOrReplaceChild("leg_r", CubeListBuilder.create().texOffs(88, 96).addBox(-8F, 0F, -8F, 16F, 20F, 16F), PartPose.offsetAndRotation(-11F, 8F, 0F, 0F, 0F, 0F));
        p_leg_r.addOrReplaceChild("leg_r_shin", CubeListBuilder.create().texOffs(152, 96).addBox(-9F, 0F, -9F, 18F, 17F, 18F).texOffs(100, 166).addBox(-10F, 15F, -12F, 20F, 6F, 22F), PartPose.offsetAndRotation(0F, 19F, 0F, 0F, 0F, 0F));
        PartDefinition p_leg_l = root.addOrReplaceChild("leg_l", CubeListBuilder.create().texOffs(88, 96).addBox(-8F, 0F, -8F, 16F, 20F, 16F), PartPose.offsetAndRotation(11F, 8F, 0F, 0F, 0F, 0F));
        p_leg_l.addOrReplaceChild("leg_l_shin", CubeListBuilder.create().texOffs(152, 96).addBox(-9F, 0F, -9F, 18F, 17F, 18F).texOffs(100, 166).addBox(-10F, 15F, -12F, 20F, 6F, 22F), PartPose.offsetAndRotation(0F, 19F, 0F, 0F, 0F, 0F));
        PartDefinition p_torso = root.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 134).addBox(-13F, -14F, -9F, 26F, 14F, 18F).texOffs(0, 0).addBox(-23F, -44F, -12F, 46F, 30F, 24F).texOffs(0, 194).addBox(-18F, -50F, -8F, 36F, 7F, 20F).texOffs(110, 221).addBox(-4F, -34F, -13F, 8F, 8F, 1F).texOffs(222, 194).addBox(-19F, -44F, -13F, 4F, 20F, 1F).texOffs(112, 194).addBox(-12F, -44F, -13F, 4F, 26F, 1F).texOffs(28, 221).addBox(9F, -44F, -13F, 4F, 16F, 1F).texOffs(122, 194).addBox(16F, -44F, -13F, 4F, 23F, 1F), PartPose.offsetAndRotation(0F, -4F, 0F, 0.14F, 0F, 0F));
        p_torso.addOrReplaceChild("head", CubeListBuilder.create().texOffs(72, 54).addBox(-10F, -22F, -10F, 20F, 22F, 18F).texOffs(142, 194).addBox(-10.5F, -23F, -10.5F, 21F, 3F, 19F).texOffs(144, 221).addBox(-11F, -17F, -13F, 22F, 4F, 4F).texOffs(26, 238).addBox(-8F, -13F, -10.5F, 5F, 2F, 1F).texOffs(26, 238).addBox(3F, -13F, -10.5F, 5F, 2F, 1F).texOffs(38, 221).addBox(-2.5F, -13F, -15F, 5F, 11F, 5F).texOffs(0, 238).addBox(-6F, -3F, -10.5F, 12F, 2F, 1F), PartPose.offsetAndRotation(0F, -48F, -7F, -0.14F, 0F, 0F));
        PartDefinition p_arm_r = p_torso.addOrReplaceChild("arm_r", CubeListBuilder.create().texOffs(140, 0).addBox(-12F, -12F, -12F, 24F, 20F, 24F).texOffs(0, 166).addBox(-12.5F, -13F, -12.5F, 25F, 3F, 25F).texOffs(132, 194).addBox(-10F, -10F, -13F, 4F, 22F, 1F).texOffs(148, 54).addBox(-8F, 6F, -8F, 16F, 24F, 16F), PartPose.offsetAndRotation(-31F, -42F, 0F, -0.09F, 0F, 0.1F));
        p_arm_r.addOrReplaceChild("arm_r_fore", CubeListBuilder.create().texOffs(0, 54).addBox(-9F, 0F, -9F, 18F, 24F, 18F).texOffs(0, 96).addBox(-11F, 22F, -11F, 22F, 16F, 22F), PartPose.offsetAndRotation(0F, 28F, 0F, -0.12F, 0F, 0F));
        p_arm_r.addOrReplaceChild("flower", CubeListBuilder.create().texOffs(140, 221).addBox(-0.5F, -8F, -0.5F, 1F, 8F, 1F).texOffs(196, 221).addBox(-2.5F, -11F, -2.5F, 5F, 3F, 5F), PartPose.offsetAndRotation(-4F, -13F, -3F, 0.25F, 0F, -0.35F));
        PartDefinition p_arm_l = p_torso.addOrReplaceChild("arm_l", CubeListBuilder.create().texOffs(140, 0).addBox(-12F, -12F, -12F, 24F, 20F, 24F).texOffs(0, 166).addBox(-12.5F, -13F, -12.5F, 25F, 3F, 25F).texOffs(132, 194).addBox(6F, -10F, -13F, 4F, 22F, 1F).texOffs(148, 54).addBox(-8F, 6F, -8F, 16F, 24F, 16F), PartPose.offsetAndRotation(31F, -42F, 0F, -0.09F, 0F, -0.1F));
        p_arm_l.addOrReplaceChild("arm_l_fore", CubeListBuilder.create().texOffs(0, 54).addBox(-9F, 0F, -9F, 18F, 24F, 18F).texOffs(0, 96).addBox(-11F, 22F, -11F, 22F, 16F, 22F), PartPose.offsetAndRotation(0F, 28F, 0F, -0.12F, 0F, 0F));
        p_arm_l.addOrReplaceChild("arm_l_crystal0", CubeListBuilder.create().texOffs(0, 221).addBox(-3.5F, -10F, -3.5F, 7F, 10F, 7F).texOffs(78, 221).addBox(-2F, -18F, -2F, 4F, 8F, 4F).texOffs(216, 221).addBox(-1F, -24F, -1F, 2F, 6F, 2F), PartPose.offsetAndRotation(2F, -13F, 2F, 0.2F, 0F, 0.45F));
        p_arm_l.addOrReplaceChild("arm_l_crystal1", CubeListBuilder.create().texOffs(58, 221).addBox(-2.5F, -7F, -2.5F, 5F, 7F, 5F).texOffs(128, 221).addBox(-1.5F, -13F, -1.5F, 3F, 6F, 3F).texOffs(232, 221).addBox(-1F, -17F, -1F, 2F, 4F, 2F), PartPose.offsetAndRotation(-4F, -13F, 5F, 0.55F, 0F, 0.15F));
        p_arm_l.addOrReplaceChild("arm_l_crystal2", CubeListBuilder.create().texOffs(94, 221).addBox(-2F, -6F, -2F, 4F, 6F, 4F).texOffs(224, 221).addBox(-1F, -11F, -1F, 2F, 5F, 2F).texOffs(240, 221).addBox(-1F, -14F, -1F, 2F, 3F, 2F), PartPose.offsetAndRotation(6F, -13F, -4F, -0.25F, 0F, 0.7F));
        return LayerDefinition.create(mesh, 256, 256);
    }

    @Override
    public ModelPart root() {
        return root;
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
    public void setupAnim(T brute, float limbSwing, float limbSwingAmount, float t, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        root.xScale = root.yScale = root.zScale = 0.5f;  // modèle dessiné à l'échelle 2 (haute résolution)
        float w = limbSwing * 0.42f, a = Math.min(1f, limbSwingAmount * 1.4f);

        // ── MARCHE LOURDE : grandes enjambées, genoux qui plient, le corps s'enfonce à chaque pas ──
        legL.xRot = Mth.cos(w) * 0.85f * a;
        legR.xRot = Mth.cos(w + Mth.PI) * 0.85f * a;
        shinL.xRot = Math.max(0f, -Mth.sin(w)) * 0.7f * a;
        shinR.xRot = Math.max(0f, Mth.sin(w)) * 0.7f * a;
        float impact = 1f - Math.abs(Mth.sin(w));
        torso.y = -4f + impact * impact * 3.0f * a;
        // dos voûté, balancement, respiration lente
        torso.xRot = HUNCH + 0.08f * a + 0.02f * Mth.sin(t * 0.05f);
        torso.yRot = Mth.cos(w) * 0.12f * a;
        torso.zRot = Mth.sin(w) * 0.13f * a;
        // tête : compense le voûtement et le balancement, suit sa cible
        head.xRot = -HUNCH + 0.03f * Mth.sin(t * 0.04f) + headPitch * Mth.DEG_TO_RAD * 0.4f;
        head.yRot = -Mth.cos(w) * 0.08f * a + netHeadYaw * Mth.DEG_TO_RAD * 0.6f;
        head.zRot = -Mth.sin(w) * 0.1f * a;
        flower.xRot = 0.25f + 0.06f * Mth.sin(t * 0.09f);
        flower.zRot = -0.35f + 0.05f * Mth.sin(t * 0.07f);

        // ── Bras : balancent en opposition des jambes, avant-bras en retard (effet de masse) ──
        armR.xRot = -HUNCH + 0.05f + Mth.cos(w) * 0.7f * a;
        armL.xRot = -HUNCH + 0.05f + Mth.cos(w + Mth.PI) * 0.7f * a;
        armR.zRot = 0.1f + 0.06f * a + 0.02f * Mth.sin(t * 0.04f);
        armL.zRot = -(0.1f + 0.06f * a + 0.02f * Mth.sin(t * 0.04f));
        foreR.xRot = -0.12f - Math.max(0f, Mth.sin(w - 0.6f)) * 0.35f * a;
        foreL.xRot = -0.12f - Math.max(0f, Mth.sin(w + Mth.PI - 0.6f)) * 0.35f * a;

        // ── FRAPPE ÉCRASANTE (inchangée) : le poing droit se lève très haut puis s'abat, le torse accompagne ──
        int atk = brute.getAttackAnimationTick();
        if (atk > 0) {
            float tw = Mth.triangleWave(atk, 10.0f);
            armR.xRot += -2.7f + 2.6f * tw;
            foreR.xRot += 0.3f;
            torso.xRot += 0.25f * (1f - tw);
            armL.xRot += 0.2f;
        }

        // ── LANCER DE ROCHER : se baisse et arrache (0-0,35), soulève au-dessus de la tête (0,35-0,62), lance (0,62-0,8) ──
        float p = brute.getThrowProgress(t);
        if (p >= 0f) {
            float ar = kf(p, THROW_ARM);
            armR.xRot += ar;
            armL.xRot += ar;
            torso.xRot += kf(p, THROW_TORSO);
            float k = kf(p, THROW_KNEE);
            legL.xRot -= k;
            legR.xRot -= k;
            shinL.xRot += 2f * k;
            shinR.xRot += 2f * k;
            float dy = 9f * k;   // tout le corps descend quand il plie les genoux
            torso.y += dy;
            pelvis.y += dy;
            legL.y += dy;
            legR.y += dy;
        }
    }
}
