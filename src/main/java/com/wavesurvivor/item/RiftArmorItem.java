package com.wavesurvivor.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.wavesurvivor.craft.CraftConfig;
import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.item.RiftArmor.Branch;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 2.1 — Pièce de l'ARMURE DE LA FAILLE (évolutive). L'état vient du NBT (voir {@link RiftArmor}) : protection, robustesse,
 * recul et bonus de branche sont calculés ici ; bonus d'ensemble dans CraftEquipEvents et RiftEffect.
 * Modèle 3D : client/armor/RiftArmorModel (via RiftArmorClient), une apparence par palier / branche.
 */
public class RiftArmorItem extends ArmorItem {

    public RiftArmorItem(ArmorItem.Type type, Properties props) {
        super(RiftArmorMaterial.RIFT, type, props);
    }

    /** UUID stable par emplacement et par bonus (vie, dégâts, sorts…). */
    private UUID uuid(String key) {
        return UUID.nameUUIDFromBytes(("wavesurvivor:rift_armor/" + key + "/" + this.type.getName()).getBytes(StandardCharsets.UTF_8));
    }

    private static Attribute irons(String path) {
        return ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation("irons_spellbooks", path));
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> base = super.getAttributeModifiers(slot, stack);
        if (slot != this.type.getSlot()) return base;
        // UUID d'armure vanilla de l'emplacement (repris du modificateur d'origine) : l'infobulle fusionne proprement
        UUID id = null;
        for (AttributeModifier m : base.get(Attributes.ARMOR)) id = m.getId();
        if (id == null) return base;
        int tier = RiftArmor.tier(stack), lv = RiftArmor.level(stack);
        Branch br = RiftArmor.branch(stack);
        var b = ImmutableMultimap.<Attribute, AttributeModifier>builder();
        base.forEach((a, m) -> { if (a != Attributes.ARMOR && a != Attributes.ARMOR_TOUGHNESS && a != Attributes.KNOCKBACK_RESISTANCE) b.put(a, m); });
        double armor = RiftArmor.tierArmor(tier, this.type) + RiftArmor.byLevel(br, "armorPerPiece", lv, 0);
        double tough = RiftArmor.byTier("tierToughness", tier, tier == 1 ? 0 : tier == 2 ? 2 : 3);
        double kb = RiftArmor.byTier("tierKnockback", tier, tier >= 3 ? 0.1 : 0);
        AttributeModifier.Operation add = AttributeModifier.Operation.ADDITION, mul = AttributeModifier.Operation.MULTIPLY_BASE;
        b.put(Attributes.ARMOR, new AttributeModifier(id, "Armor modifier", armor, add));
        if (tough != 0) b.put(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(id, "Armor toughness", tough, add));
        if (kb != 0) b.put(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(id, "Armor knockback resistance", kb, add));
        // Branches : vie, dégâts, vitesse d'attaque (par pièce)
        double hp = RiftArmor.byLevel(br, "healthPerPiece", lv, 0);
        double dmg = RiftArmor.byLevel(br, "damagePercentPerPiece", lv, 0);
        double spd = RiftArmor.byLevel(br, "attackSpeedPercentPerPiece", lv, 0);
        if (hp != 0) b.put(Attributes.MAX_HEALTH, new AttributeModifier(uuid("hp"), "wavesurvivor_rift_hp", hp, add));
        if (dmg != 0) b.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(uuid("dmg"), "wavesurvivor_rift_dmg", dmg / 100.0, mul));
        if (spd != 0) b.put(Attributes.ATTACK_SPEED, new AttributeModifier(uuid("spd"), "wavesurvivor_rift_spd", spd / 100.0, mul));
        // Iron's Spells (optionnel) : sorts d'Ender dès le palier III ; Sacrés / de Sang + mana selon la branche
        if (tier >= 3) {
            Attribute ender = irons("ender_spell_power");
            double pe = RiftArmor.num("enderSpellPowerPercent", 6);
            if (ender != null && pe != 0) b.put(ender, new AttributeModifier(uuid("ender"), "wavesurvivor_rift_ender", pe / 100.0, mul));
        }
        if (br != Branch.BASE) {
            boolean blessed = br == Branch.BLESSED;
            Attribute school = irons(blessed ? "holy_spell_power" : "blood_spell_power");
            double ps = RiftArmor.branchNum(br, blessed ? "holySpellPowerPercent" : "bloodSpellPowerPercent", 6);
            if (school != null && ps != 0) b.put(school, new AttributeModifier(uuid("school"), "wavesurvivor_rift_school", ps / 100.0, mul));
            Attribute mana = irons("max_mana");
            double mp = RiftArmor.branchNum(br, "maxMana", 25);
            if (mana != null && mp != 0) b.put(mana, new AttributeModifier(uuid("mana"), "wavesurvivor_rift_mana", mp, add));
        }
        return b.build();
    }

    @Override
    public Component getName(ItemStack stack) {
        Branch br = RiftArmor.branch(stack);
        return br == Branch.BASE ? super.getName(stack) : Component.translatable(this.getDescriptionId(stack) + "." + br.id);
    }

    @Override
    public Rarity getRarity(ItemStack stack) {
        if (RiftArmor.branch(stack) != Branch.BASE || RiftArmor.tier(stack) >= 3) return Rarity.EPIC;
        return RiftArmor.tier(stack) == 2 ? Rarity.RARE : Rarity.UNCOMMON;
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return com.wavesurvivor.client.armor.RiftArmorClient.texture(stack);   // appelé seulement côté client (rendu)
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
                return com.wavesurvivor.client.armor.RiftArmorClient.model(slot, original, stack);
            }
        });
    }

    // ─── Infobulle ───

    private static void lines(List<Component> tip, String text) {
        for (String l : text.split("\n")) tip.add(Component.literal(l));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        Branch br = RiftArmor.branch(stack);
        int tier = RiftArmor.tier(stack), lv = RiftArmor.level(stack);
        tip.add(Component.literal(RiftArmor.tierName(stack)));
        // Prochaine amélioration (lieu + coût)
        if (br == Branch.BASE && tier < 3) {
            List<CraftConfig.Cost> c = RiftArmor.tierCost(stack);
            if (c != null) tip.add(Component.literal(WSLang.t("rift_armor.next", WSLang.t("rift_armor.next.forge"), RiftArmor.costText(c))));
        } else if (br != Branch.BASE && lv < 3) {
            tip.add(Component.literal(WSLang.t("rift_armor.next", WSLang.t("rift_armor.next.shrine"), RiftArmor.costText(RiftArmor.branchCost(br, lv + 1)))));
        } else if (br != Branch.BASE) {
            tip.add(Component.literal(WSLang.t("rift_armor.max")));
        }
        if (br == Branch.BASE && tier >= 3) lines(tip, WSLang.t("rift_armor.choice"));
        // Bonus d'ensemble : celui du joueur s'il porte les 4 pièces, sinon celui que donnerait cette pièce (grisé)
        Player p = net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient() ? com.wavesurvivor.client.armor.RiftArmorClient.localPlayer() : null;
        RiftArmor.SetInfo s = RiftArmor.set(p);
        boolean worn = s.full() && p != null && p.getInventory().armor.contains(stack);
        int sTier = worn ? s.tier() : tier;
        Branch sBr = worn ? s.branch() : br;
        int sLv = worn ? s.level() : lv;
        String dim = worn ? "" : "§8";
        tip.add(Component.literal(dim + WSLang.t("rift_armor.set.anchor") + (worn ? "" : " §8(" + (p == null ? 0 : s.count()) + "/4)")));
        for (String l : WSLang.t("rift_armor.set.anchor." + sTier).split("\n")) tip.add(Component.literal(worn ? l : "§8" + l.replaceAll("§.", "")));
        if (sBr == Branch.BLESSED) {
            String t = WSLang.t("rift_armor.set.grace", fmt(RiftArmor.byLevel(Branch.BLESSED, "healingReceivedPercent", sLv, 5 * sLv)));
            for (String l : t.split("\n")) tip.add(Component.literal(worn ? l : "§8" + l.replaceAll("§.", "")));
        } else if (sBr == Branch.CORRUPTED) {
            String t = WSLang.t("rift_armor.set.hunger", fmt(RiftArmor.byLevel(Branch.CORRUPTED, "lifestealPercent", sLv, 0)));
            for (String l : t.split("\n")) tip.add(Component.literal(worn ? l : "§8" + l.replaceAll("§.", "")));
        }
        // Iron's Spells
        if (net.minecraftforge.fml.ModList.get().isLoaded("irons_spellbooks")) {
            if (br == Branch.BLESSED) tip.add(Component.literal(WSLang.t("rift_armor.irons.blessed")));
            else if (br == Branch.CORRUPTED) tip.add(Component.literal(WSLang.t("rift_armor.irons.corrupted")));
            if (tier >= 3) tip.add(Component.literal(WSLang.t("rift_armor.irons")));
        }
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
