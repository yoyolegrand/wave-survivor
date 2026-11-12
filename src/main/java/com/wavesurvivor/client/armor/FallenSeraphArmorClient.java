package com.wavesurvivor.client.armor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.EnumMap;
import java.util.Map;

/**
 * 2.0 — Modèle de l'Armure du Séraphin déchu (FallenSeraphArmorModel de Claude chat, même couche SeraphArmorModel.LAYER) :
 * un modèle par emplacement, recréé si les modèles sont rechargés, pose recopiée depuis le modèle d'armure d'origine (comme SeraphArmorClient).
 */
@OnlyIn(Dist.CLIENT)
public final class FallenSeraphArmorClient {

    private static final Map<EquipmentSlot, SeraphArmorModel> MODELS = new EnumMap<>(EquipmentSlot.class);
    private static EntityModelSet lastSet;

    private FallenSeraphArmorClient() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static HumanoidModel<?> model(EquipmentSlot slot, HumanoidModel<?> original) {
        EntityModelSet set = Minecraft.getInstance().getEntityModels();
        if (set != lastSet) {
            MODELS.clear();
            lastSet = set;
        }
        SeraphArmorModel m = MODELS.computeIfAbsent(slot, s -> new FallenSeraphArmorModel(set.bakeLayer(SeraphArmorModel.LAYER)).forSlot(s));
        ((HumanoidModel) original).copyPropertiesTo(m);
        return m;
    }
}
