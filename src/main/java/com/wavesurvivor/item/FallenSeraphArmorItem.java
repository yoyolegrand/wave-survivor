package com.wavesurvivor.item;

import com.wavesurvivor.craft.CraftGearItems;
import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.shrine.ShrineGear;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * 2.0 — Pièce de l'ARMURE DU SÉRAPHIN DÉCHU (variante corrompue du Séraphin) : même modèle 3D et même couche, texture et pièces déplacées
 * de Claude chat (client/armor/FallenSeraphArmorModel). Alignement Profané imposé pour la Forge du Sanctuaire ; bonus d'ensemble
 * « Pacte du Déchu » dans CraftEquipEvents. Classe distincte de SeraphArmorItem : mélanger les deux ne donne aucun des deux ensembles.
 */
public class FallenSeraphArmorItem extends ArmorItem implements CraftGearItems.FixedAlign {

    /** Texture complète du modèle (écrite en texte ici pour ne pas charger les classes du client côté serveur). */
    public static final String TEXTURE = "wavesurvivor:textures/models/armor/fallen_seraph_armor.png";

    public FallenSeraphArmorItem(ArmorItem.Type type, Properties props) {
        super(FallenSeraphArmorMaterial.FALLEN_SERAPH, type, props.rarity(Rarity.EPIC));
    }

    private static final java.util.UUID[] UUIDS = {
            java.util.UUID.fromString("7c1a5f20-5a0e-4d51-9a11-0e5a1f0000d1"), java.util.UUID.fromString("7c1a5f20-5a0e-4d51-9a11-0e5a1f0000d2"),
            java.util.UUID.fromString("7c1a5f20-5a0e-4d51-9a11-0e5a1f0000d3"), java.util.UUID.fromString("7c1a5f20-5a0e-4d51-9a11-0e5a1f0000d4")};

    /**
     * Si Iron's Spells est installé : puissance des sorts de SANG (au lieu de sacrés) et mana max par pièce, mêmes valeurs que le Séraphin
     * (crafting_fragments.json, « fallen_seraph_set » : bloodSpellPowerPercent, maxMana). Sans le mod, rien n'est ajouté.
     */
    @Override
    public com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute, net.minecraft.world.entity.ai.attributes.AttributeModifier>
            getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        var base = super.getAttributeModifiers(slot, stack);
        if (slot != this.type.getSlot()) return base;
        var b = com.google.common.collect.ImmutableMultimap.<net.minecraft.world.entity.ai.attributes.Attribute, net.minecraft.world.entity.ai.attributes.AttributeModifier>builder();
        b.putAll(base);
        var reg = net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES;
        var power = reg.getValue(new net.minecraft.resources.ResourceLocation("irons_spellbooks", "blood_spell_power"));
        var mana = reg.getValue(new net.minecraft.resources.ResourceLocation("irons_spellbooks", "max_mana"));
        java.util.UUID id = UUIDS[this.type.ordinal()];
        double pct = com.wavesurvivor.craft.CraftConfig.equipNum("fallen_seraph_set", "bloodSpellPowerPercent", 6);
        double mp = com.wavesurvivor.craft.CraftConfig.equipNum("fallen_seraph_set", "maxMana", 25);
        if (power != null && pct != 0) b.put(power, new net.minecraft.world.entity.ai.attributes.AttributeModifier(id, "wavesurvivor_fallen_seraph_blood", pct / 100.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE));
        if (mana != null && mp != 0) b.put(mana, new net.minecraft.world.entity.ai.attributes.AttributeModifier(UUIDS[(this.type.ordinal() + 1) % 4], "wavesurvivor_fallen_seraph_mana_" + this.type.getName(), mp, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
        return b.build();
    }

    @Override
    public String align() { return ShrineGear.PROFANED; }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) { return TEXTURE; }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
                return com.wavesurvivor.client.armor.FallenSeraphArmorClient.model(slot, original);
            }
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        for (String line : WSLang.t("craft.item.fallen_seraph_armor").split("\n")) tip.add(Component.literal(line));
    }
}
