package com.wavesurvivor.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import com.wavesurvivor.i18n.WSLang;
import java.util.List;

/**
 * RELIQUE LÉGENDAIRE (1.5) : obtenue uniquement par fusion à la Forge (deux reliques d'une même famille, niveau II).
 * Elle agit comme ses deux reliques d'origine au niveau II (donc compte pour 2 pièces de set) + un effet unique.
 * S'améliore comme les autres à la Forge (doublon identique) : ses effets uniques gagnent en puissance et, dès le niveau II,
 * ses deux reliques d'origine agissent au niveau III. Nom affiché en or.
 */
public class LegendaryRelicItem extends RelicItem {

    public LegendaryRelicItem(String title, String slot, String quote, String... effects) {
        super(Rarity.EPIC, title, slot, quote, effects);
    }

    /** Effets traduits : chiffres du niveau, et niveau auquel agissent les deux reliques d'origine ({P}). */
    @Override
    public List<String> effectTexts(int lvl) {
        String roman = RelicFusions.parentLevel(lvl) >= 3 ? "III" : "II";
        return super.effectTexts(lvl).stream().map(t -> t.replace("{P}", roman)).toList();
    }

    /** Maj : effets réels des deux reliques d'origine, au niveau où la légendaire les fait agir. Sinon un rappel. */
    @Override
    protected void appendDetails(ItemStack stack, List<Component> tooltip) {
        RelicFusions.Fusion f = RelicFusions.ofResult(this);
        if (f == null) return;
        if (!shiftDown()) {
            tooltip.add(Component.literal(WSLang.t("relic.shift_hint")).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            return;
        }
        for (Item it : List.of(f.a().get(), f.b().get())) {
            if (!(it instanceof RelicItem r)) continue;
            tooltip.add(Component.literal(WSLang.t("relic.parent_line", new ItemStack(it).getHoverName().getString())).withStyle(ChatFormatting.GOLD));
            for (String t : r.effectTexts(RelicFusions.parentLevel(RelicEffects.levelOf(stack)))) tooltip.add(Component.literal("   " + t).withStyle(ChatFormatting.YELLOW));
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(this.getDescriptionId(stack)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    }
}
