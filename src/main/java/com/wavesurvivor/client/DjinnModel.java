package com.wavesurvivor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wavesurvivor.entity.Djinn;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * MODÈLE DU DJINN : tête d'illageois coiffée d'un grand turban (pierre, plume), manteau à ceinture,
 * bras croisés (levés pendant les sorts), et une QUEUE DE FUMÉE (5 segments) à la place des jambes.
 * Texture 128×64 : moitié gauche = patron de l'Évocateur, moitié droite = turban, ceinture, queue.
 * Animations : flotte (haut / bas), la queue ondule, bras levés pendant les sorts.
 */
@OnlyIn(Dist.CLIENT)
public class DjinnModel<T extends Djinn> extends HierarchicalModel<T> implements ArmedModel, HeadedModel {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation("wavesurvivor", "djinn"), "main");

    private final ModelPart root, main, head, arms, rightArm, leftArm;
    private final ModelPart[] tail = new ModelPart[5];

    public DjinnModel(ModelPart root) {
        this.root = root;
        this.main = root.getChild("main");
        this.head = main.getChild("head");
        this.arms = main.getChild("arms");
        this.rightArm = main.getChild("right_arm");
        this.leftArm = main.getChild("left_arm");
        ModelPart t = main.getChild("tail1");
        tail[0] = t;
        for (int i = 1; i < 5; i++) {
            t = t.getChild("tail" + (i + 1));
            tail[i] = t;
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition rootDef = mesh.getRoot();
        PartDefinition main = rootDef.addOrReplaceChild("main", CubeListBuilder.create(), PartPose.offset(0, 0, 0));

        // Tête, nez et turban
        PartDefinition head = main.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -10, -4, 8, 10, 8), PartPose.ZERO);
        head.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(24, 0).addBox(-1, -1, -6, 2, 4, 2), PartPose.offset(0, -2, 0));
        PartDefinition turban = head.addOrReplaceChild("turban", CubeListBuilder.create()
                .texOffs(64, 0).addBox(-6, -15, -6, 12, 6, 12)       // dôme du turban (plus large que la tête)
                .texOffs(64, 18).addBox(-4, -17, -4, 8, 2, 8)         // 2e étage
                .texOffs(96, 18).addBox(-2, -19, -2, 4, 2, 4)         // bouton
                .texOffs(112, 0).addBox(-1.5f, -14, -6.5f, 3, 3, 1),  // pierre
                PartPose.ZERO);
        turban.addOrReplaceChild("plume", CubeListBuilder.create().texOffs(112, 6).addBox(-0.5f, -6, -1, 1, 6, 2),
                PartPose.offsetAndRotation(0, -18.5f, 0, -0.3f, 0, 0));

        // Corps : tunique + manteau + ceinture
        main.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(16, 20).addBox(-4, 0, -3, 8, 12, 6)
                .texOffs(0, 38).addBox(-4, 0, -3, 8, 12, 6, new CubeDeformation(0.5f))
                .texOffs(64, 28).addBox(-4.5f, 10, -3.5f, 9, 2, 7), PartPose.ZERO);

        // Bras croisés (au repos) et bras séparés (pendant les sorts) — même patron que l'Évocateur
        PartDefinition arms = main.addOrReplaceChild("arms", CubeListBuilder.create()
                .texOffs(44, 22).addBox(-8, -2, -2, 4, 8, 4)
                .texOffs(40, 38).addBox(-4, 2, -2, 8, 4, 4), PartPose.offsetAndRotation(0, 3, -1, -0.75f, 0, 0));
        arms.addOrReplaceChild("left_shoulder", CubeListBuilder.create().texOffs(44, 22).mirror().addBox(4, -2, -2, 4, 8, 4), PartPose.ZERO);
        // Bras séparés (sorts) avec un manchon évasé au poignet
        main.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 46).addBox(-3, -2, -2, 4, 12, 4)
                .texOffs(96, 28).addBox(-3.5f, 5, -2.5f, 5, 3, 5), PartPose.offset(-5, 2, 0));
        main.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(40, 46).mirror().addBox(-1, -2, -2, 4, 12, 4)
                .texOffs(96, 28).mirror().addBox(-1.5f, 5, -2.5f, 5, 3, 5), PartPose.offset(5, 2, 0));

        // Queue de fumée : 5 segments de plus en plus fins, chacun accroché au précédent
        PartDefinition t1 = main.addOrReplaceChild("tail1", CubeListBuilder.create().texOffs(64, 38).addBox(-3.5f, 0, -2.5f, 7, 5, 5), PartPose.offset(0, 12, 0));
        PartDefinition t2 = t1.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(88, 38).addBox(-2.5f, 0, -2, 5, 4, 4), PartPose.offset(0, 4.5f, 0));
        PartDefinition t3 = t2.addOrReplaceChild("tail3", CubeListBuilder.create().texOffs(64, 50).addBox(-1.5f, 0, -1.5f, 3, 4, 3), PartPose.offset(0, 3.5f, 0));
        PartDefinition t4 = t3.addOrReplaceChild("tail4", CubeListBuilder.create().texOffs(80, 50).addBox(-1, 0, -1, 2, 3, 2), PartPose.offset(0, 3.5f, 0));
        t4.addOrReplaceChild("tail5", CubeListBuilder.create().texOffs(92, 50).addBox(-0.5f, 0, -0.5f, 1, 2, 1), PartPose.offset(0, 2.5f, 0));

        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(T djinn, float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD;
        // Flotte doucement (et se tient un peu au-dessus du sol)
        main.y = -1.5f + Mth.sin(age * 0.08f) * 1.2f;
        // La queue ondule, de plus en plus fort vers le bout, et se replie vers l'arrière
        for (int i = 0; i < tail.length; i++) {
            tail[i].xRot = 0.18f + 0.06f * i + Mth.sin(age * 0.12f - i * 0.6f) * (0.08f + 0.04f * i);
            tail[i].zRot = Mth.sin(age * 0.09f - i * 0.5f) * (0.05f + 0.035f * i);
        }
        // Bras : croisés au repos, levés pendant les sorts, tendus en attaque
        AbstractIllager.IllagerArmPose pose = djinn.getArmPose();
        boolean crossed = pose == AbstractIllager.IllagerArmPose.CROSSED;
        arms.visible = crossed;
        rightArm.visible = !crossed;
        leftArm.visible = !crossed;
        if (pose == AbstractIllager.IllagerArmPose.SPELLCASTING) {
            rightArm.xRot = Mth.cos(age * 0.6662f) * 0.25f;
            leftArm.xRot = Mth.cos(age * 0.6662f) * 0.25f;
            rightArm.zRot = 2.3561945f;
            leftArm.zRot = -2.3561945f;
        } else if (!crossed) {
            rightArm.xRot = -1.3f + Mth.cos(age * 0.3f) * 0.1f;
            leftArm.xRot = -1.3f - Mth.cos(age * 0.3f) * 0.1f;
        }
    }

    @Override
    public ModelPart getHead() {
        return head;
    }

    @Override
    public void translateToHand(HumanoidArm side, PoseStack pose) {
        main.translateAndRotate(pose);
        (side == HumanoidArm.LEFT ? leftArm : rightArm).translateAndRotate(pose);
    }
}
