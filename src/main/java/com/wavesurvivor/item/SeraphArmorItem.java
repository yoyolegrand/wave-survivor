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
 * Pièce de l'ARMURE DU SÉRAPHIN (casque, plastron, jambières, bottes) : modèle 3D sur mesure (client/armor/SeraphArmorModel),
 * alignement Consacré imposé pour la Forge du Sanctuaire, bonus d'ensemble (voir CraftEquipEvents).
 */
public class SeraphArmorItem extends ArmorItem implements CraftGearItems.FixedAlign {

    /** Texture complète du modèle (écrite en texte ici pour ne pas charger les classes du client côté serveur). */
    public static final String TEXTURE = "wavesurvivor:textures/models/armor/seraph_armor.png";

    public SeraphArmorItem(ArmorItem.Type type, Properties props) {
        super(SeraphArmorMaterial.SERAPH, type, props.rarity(Rarity.EPIC));
    }

    private static final java.util.UUID[] UUIDS = {
            java.util.UUID.fromString("7c1a5f20-5a0e-4d51-9a11-0e5a1f000001"), java.util.UUID.fromString("7c1a5f20-5a0e-4d51-9a11-0e5a1f000002"),
            java.util.UUID.fromString("7c1a5f20-5a0e-4d51-9a11-0e5a1f000003"), java.util.UUID.fromString("7c1a5f20-5a0e-4d51-9a11-0e5a1f000004")};

    /**
     * Si Iron's Spells est installé : +6 % de puissance des sorts sacrés et +25 de mana max par pièce (valeurs dans crafting_fragments.json,
     * « seraph_set » : holySpellPowerPercent, maxMana). Sans le mod, les attributs n'existent pas et rien n'est ajouté.
     */
    @Override
    public com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute, net.minecraft.world.entity.ai.attributes.AttributeModifier>
            getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        var base = super.getAttributeModifiers(slot, stack);
        if (slot != this.type.getSlot()) return base;
        var b = com.google.common.collect.ImmutableMultimap.<net.minecraft.world.entity.ai.attributes.Attribute, net.minecraft.world.entity.ai.attributes.AttributeModifier>builder();
        b.putAll(base);
        var reg = net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES;
        var power = reg.getValue(new net.minecraft.resources.ResourceLocation("irons_spellbooks", "holy_spell_power"));
        var mana = reg.getValue(new net.minecraft.resources.ResourceLocation("irons_spellbooks", "max_mana"));
        java.util.UUID id = UUIDS[this.type.ordinal()];
        double pct = com.wavesurvivor.craft.CraftConfig.equipNum("seraph_set", "holySpellPowerPercent", 6);
        double mp = com.wavesurvivor.craft.CraftConfig.equipNum("seraph_set", "maxMana", 25);
        if (power != null && pct != 0) b.put(power, new net.minecraft.world.entity.ai.attributes.AttributeModifier(id, "wavesurvivor_seraph_holy", pct / 100.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE));
        if (mana != null && mp != 0) b.put(mana, new net.minecraft.world.entity.ai.attributes.AttributeModifier(UUIDS[(this.type.ordinal() + 1) % 4], "wavesurvivor_seraph_mana_" + this.type.getName(), mp, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
        return b.build();
    }

    @Override
    public String align() { return ShrineGear.CONSECRATED; }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) { return TEXTURE; }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
                return com.wavesurvivor.client.armor.SeraphArmorClient.model(slot, original);
            }
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        for (String line : WSLang.t("craft.item.seraph_armor").split("\n")) tip.add(Component.literal(line));
    }
}
