package com.wavesurvivor.client;

import com.wavesurvivor.entity.KingdomSoldier;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Modèle du Soldat du royaume : animations VANILLA du joueur (marche, coup d'épée en arc).
 * La garde personnalisée a été retirée : elle entrait en conflit avec le coup (surtout contre les archers).
 * Le coup s'anime correctement depuis que KingdomSoldier fait avancer le balancement du bras (updateSwingTime).
 */
@OnlyIn(Dist.CLIENT)
public class KingdomSoldierModel extends HumanoidModel<KingdomSoldier> {

    public KingdomSoldierModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(KingdomSoldier soldier, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        // Épée tenue en main droite (pose « objet ») : le coup d'arc vanilla s'applique dessus
        this.rightArmPose = ArmPose.ITEM;
        this.leftArmPose = ArmPose.ITEM;
        super.setupAnim(soldier, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
    }
}
