package com.wavesurvivor.horde.kingdom;

import com.wavesurvivor.i18n.WSLang;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;

/**
 * COR DE RALLIEMENT (Commandant) : clic droit → une bannière est plantée là où il vise (64 blocs), les soldats des
 * baraquements s'y rendent pendant 60 s. Sneak + clic droit : rappel des soldats. Recharge 20 s.
 */
public class CommanderHornItem extends Item {

    public CommanderHornItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack st = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(st);
        if (!(player instanceof ServerPlayer p) || !(level instanceof ServerLevel sl)) return InteractionResultHolder.pass(st);
        if (!KingdomManager.isActive() || sl != KingdomManager.level()) {
            p.displayClientMessage(WSLang.c("kingdom.role.only_kingdom"), true);
            return InteractionResultHolder.fail(st);
        }
        if (!KingdomRoles.has(p, KingdomRoles.Role.COMMANDER)) {
            p.displayClientMessage(WSLang.c("kingdom.rally.commander_only"), true);
            return InteractionResultHolder.fail(st);
        }
        if (p.isShiftKeyDown()) {
            KingdomRoleExtras.endRally(sl);
            Component msg = WSLang.c("kingdom.rally.recalled", p.getGameProfile().getName());
            for (ServerPlayer o : sl.getServer().getPlayerList().getPlayers()) o.sendSystemMessage(msg);
            return InteractionResultHolder.consume(st);
        }
        HitResult hit = p.pick(64, 0f, false);
        if (!(hit instanceof BlockHitResult bh) || hit.getType() != HitResult.Type.BLOCK) {
            p.displayClientMessage(WSLang.c("kingdom.rally.no_target"), true);
            return InteractionResultHolder.fail(st);
        }
        BlockPos at = bh.getBlockPos().relative(bh.getDirection());
        KingdomRoleExtras.setRally(sl, at, p);
        p.getCooldowns().addCooldown(this, 400);
        return InteractionResultHolder.consume(st);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(WSLang.c("kingdom.rally.tooltip"));
    }
}
