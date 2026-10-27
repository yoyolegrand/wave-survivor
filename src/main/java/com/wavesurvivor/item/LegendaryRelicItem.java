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
 * Ne s'améliore pas (pas de niveau). Nom affiché en or.
 */
public class LegendaryRelicItem extends RelicItem {

    public LegendaryRelicItem(String title, String slot, String quote, String... effects) {
        super(Rarity.EPIC, title, slot, quote, effects);
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
            for (String t : r.effectTexts(RelicFusions.PARENT_LEVEL)) tooltip.add(Component.literal("   " + t).withStyle(ChatFormatting.YELLOW));
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(this.getDescriptionId(stack)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    }
}
