package com.wavesurvivor.horde.kingdom;

import com.wavesurvivor.i18n.WSLang;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * FIOLE D'ALCHIMISTE : potion jetable EMPILABLE (par 16). Les fioles de même type et de même niveau s'empilent.
 * Le lancer est celui d'une potion jetable vanilla ; l'effet à l'impact est géré par {@link KingdomAlchemy}.
 * Info-bulle : effet de la fiole à la place de « Aucun effet ».
 */
public class AlchemyFlaskItem extends SplashPotionItem {

    public AlchemyFlaskItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
        if (!stack.hasTag()) return;
        String kind = stack.getTag().getString(KingdomAlchemy.FLASK_TAG);
        if (kind.isEmpty()) return;
        String[] lines = WSLang.t("kingdom.alchemy.desc." + kind).split("\n");
        // Ligne 1 : effet général ; puis la ligne du niveau de cette fiole
        if (lines.length > 1) tip.add(Component.literal(lines[1]));
        int lvl = Math.max(1, stack.getTag().getInt(KingdomAlchemy.LEVEL_TAG));
        if (lines.length > 1 + lvl) tip.add(Component.literal(lines[1 + lvl].replace("§8", "§e")));
        tip.add(WSLang.c("kingdom.alchemy.throw_hint"));
    }
}
