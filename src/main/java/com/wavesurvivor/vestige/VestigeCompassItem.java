package com.wavesurvivor.vestige;

import com.wavesurvivor.config.RulesConfig;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * BOUSSOLE DES VESTIGES (1.9) : l'aiguille pointe vers la structure du mod non réclamée la plus proche, sans dire laquelle (voir {@link VestigeRegistry}).
 * Clic droit : distance. Accroupi + clic droit : filtre (Tout → Hordes → Sanctuaires → Ruines), mémorisé dans l'objet.
 * L'aiguille est animée côté client (propriété « angle », cible reçue du serveur) ; sans cible, elle tourne au hasard.
 */
public class VestigeCompassItem extends Item {

    public static final String TAG_FILTER = "VestigeFilter";

    public VestigeCompassItem(Properties props) { super(props.stacksTo(1).rarity(Rarity.UNCOMMON)); }

    public static int filterOf(ItemStack s) {
        return s.hasTag() ? Math.max(0, Math.min(3, s.getTag().getInt(TAG_FILTER))) : 0;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        if (player.isShiftKeyDown() && RulesConfig.vestigeFilter()) {
            int f = (filterOf(stack) + 1) % 4;
            stack.getOrCreateTag().putInt(TAG_FILTER, f);
            sp.displayClientMessage(Component.literal(WSLang.t("vestige.filter_set", WSLang.t("vestige.filter." + f))), true);
            VestigeRegistry.sync(sp, true);
            return InteractionResultHolder.success(stack);
        }
        VestigeRegistry.Entry e = VestigeRegistry.nearest(sp, filterOf(stack));
        if (e == null) sp.displayClientMessage(Component.literal(WSLang.t(VestigeRegistry.scanning(sp) ? "vestige.scanning" : "vestige.none")), true);
        else sp.displayClientMessage(Component.literal(WSLang.t("vestige.unknown", Math.round(VestigeRegistry.distance(sp, e)))), true);
        VestigeRegistry.sync(sp, true);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("§7" + WSLang.t("vestige.tip")));
        if (RulesConfig.vestigeFilter()) {
            tip.add(Component.literal("§8" + WSLang.t("vestige.tip_filter")));
            tip.add(Component.literal("§8" + WSLang.t("vestige.filter_set", WSLang.t("vestige.filter." + filterOf(stack)))));
        }
    }
}
