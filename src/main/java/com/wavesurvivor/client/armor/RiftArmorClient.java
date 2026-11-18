package com.wavesurvivor.client.armor;

import com.wavesurvivor.item.RiftArmor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

/**
 * 2.1 — Fournit le modèle de l'Armure de la Faille au rendu, calqué sur SeraphArmorClient : un modèle par
 * (emplacement, palier, branche), créé avec {@link RiftArmorModel#forStage}. Avec Epic Fight, le modèle est aplati APRÈS
 * forStage (les pièces masquées du palier / de la branche ne sont pas fusionnées).
 */
@OnlyIn(Dist.CLIENT)
public final class RiftArmorClient {

    private static final Map<String, HumanoidModel<LivingEntity>> MODELS = new HashMap<>(); // aplatis (Epic Fight)
    private static final Map<String, HumanoidModel<LivingEntity>> PLAIN = new HashMap<>();  // d'origine (rendu vanilla)
    private static EntityModelSet lastSet;

    private RiftArmorClient() {}

    static RiftArmorModel.Branch branch(ItemStack st) {
        return switch (RiftArmor.branch(st)) {
            case BLESSED -> RiftArmorModel.Branch.BLESSED;
            case CORRUPTED -> RiftArmorModel.Branch.CORRUPTED;
            default -> RiftArmorModel.Branch.BASE;
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static HumanoidModel<?> model(EquipmentSlot slot, HumanoidModel<?> original, ItemStack stack) {
        EntityModelSet set = Minecraft.getInstance().getEntityModels();
        if (set != lastSet) { // rechargement des ressources : les anciennes parties ne sont plus valables
            MODELS.clear();
            PLAIN.clear();
            lastSet = set;
        }
        int tier = RiftArmor.tier(stack);
        RiftArmorModel.Branch b = branch(stack);
        String key = slot.name() + "/" + tier + "/" + b.name();
        HumanoidModel<LivingEntity> m = ArmorFlattener.calledByEpicFight()
                ? MODELS.computeIfAbsent(key, k -> ArmorFlattener.flatten(new RiftArmorModel(set.bakeLayer(RiftArmorModel.LAYER)).forStage(slot, tier, b)))
                : PLAIN.computeIfAbsent(key, k -> new RiftArmorModel(set.bakeLayer(RiftArmorModel.LAYER)).forStage(slot, tier, b));
        ((HumanoidModel) original).copyPropertiesTo(m);
        return m;
    }

    /** Texture du modèle selon le palier et la branche (getArmorTexture). */
    public static String texture(ItemStack stack) {
        return RiftArmorModel.texture(RiftArmor.tier(stack), branch(stack)).toString();
    }

    /** Calque lumineux ; null au palier I (cristaux éteints). */
    public static ResourceLocation glow(ItemStack stack) {
        return RiftArmorModel.glowTexture(RiftArmor.tier(stack), branch(stack));
    }

    /** Joueur local (infobulle : bonus d'ensemble porté). */
    public static Player localPlayer() { return Minecraft.getInstance().player; }
}
