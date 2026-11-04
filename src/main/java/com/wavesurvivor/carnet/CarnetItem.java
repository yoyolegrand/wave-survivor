package com.wavesurvivor.carnet;

import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.network.CarnetPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Carnet du Survivant : clic droit pour l'ouvrir (démarrage, objectifs, ressources, recherche dans le Codex). */
public class CarnetItem extends Item {

    public CarnetItem(Properties props) { super(props); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack st = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) CarnetPacket.openFor(sp);
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        for (String line : WSLang.t("carnet.item.tip").split("\n")) tip.add(Component.literal(line));
    }
}
