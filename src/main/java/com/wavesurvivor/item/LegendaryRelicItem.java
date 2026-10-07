package com.wavesurvivor.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * RELIQUE LÉGENDAIRE (1.5) : obtenue uniquement par fusion à la Forge (deux reliques d'une même famille, niveau II).
 * Elle agit comme ses deux reliques d'origine au niveau II (donc compte pour 2 pièces de set) + un effet unique.
 * Ne s'améliore pas (pas de niveau). Nom affiché en or.
 */
public class LegendaryRelicItem extends RelicItem {

    public LegendaryRelicItem(String title, String slot, String quote, String... effects) {
        super(Rarity.EPIC, title, slot, quote, effects);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(this.getDescriptionId(stack)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    }
}
