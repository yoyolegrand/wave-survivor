package com.wavesurvivor.item;

import com.wavesurvivor.i18n.WSLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 2.0 — ÉCLAT DE FAILLE : gagné en battant le Titan de la Faille. Sert à la Forge de l'autel de Renaissance pour monter une relique
 * du niveau III au niveau IV (1 éclat pour une relique, 2 pour une légendaire).
 */
public class RiftShardItem extends Item {

    public RiftShardItem(Properties props) {
        super(props.rarity(Rarity.EPIC));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        for (String line : WSLang.t("item.rift_shard.desc").split("\n")) tooltip.add(Component.literal(line).withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
