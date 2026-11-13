package com.wavesurvivor.client.armor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.EnumMap;
import java.util.Map;

/**
 * Fournit le modèle de l'Armure du Séraphin au rendu : un modèle par emplacement (créé une fois depuis SeraphArmorModel.LAYER,
 * recréé si les modèles sont rechargés), avec la pose recopiée depuis le modèle d'armure d'origine.
 */
@OnlyIn(Dist.CLIENT)
public final class SeraphArmorClient {

    private static final Map<EquipmentSlot, HumanoidModel<LivingEntity>> MODELS = new EnumMap<>(EquipmentSlot.class);
    private static EntityModelSet lastSet;

    private SeraphArmorClient() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static HumanoidModel<?> model(EquipmentSlot slot, HumanoidModel<?> original) {
        EntityModelSet set = Minecraft.getInstance().getEntityModels();
        if (set != lastSet) { // rechargement des ressources : les anciennes parties ne sont plus valables
            MODELS.clear();
            lastSet = set;
        }
        // aplati pour Epic Fight (voir ArmorFlattener) : même rendu en vanilla, plus de casque décalé en mode combat
        HumanoidModel<LivingEntity> m = MODELS.computeIfAbsent(slot, s -> ArmorFlattener.flatten(new SeraphArmorModel(set.bakeLayer(SeraphArmorModel.LAYER)).forSlot(s)));
        ((HumanoidModel) original).copyPropertiesTo(m);
        return m;
    }
}
