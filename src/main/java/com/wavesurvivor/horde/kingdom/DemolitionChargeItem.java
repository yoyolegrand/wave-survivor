package com.wavesurvivor.horde.kingdom;

import com.wavesurvivor.i18n.WSLang;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * CHARGE DE DÉMOLITION (Bâtisseur uniquement) : clic droit au sol près d'une Porte ébréchée dont les gardiens sont
 * morts. Refusée (et non consommée) sinon. Voir {@link KingdomDemolition}.
 */
public class DemolitionChargeItem extends Item {

    public DemolitionChargeItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(ctx.getPlayer() instanceof ServerPlayer p) || !(level instanceof ServerLevel sl)) return InteractionResult.PASS;
        if (!KingdomManager.isActive() || sl != KingdomManager.level()) {
            p.displayClientMessage(WSLang.c("kingdom.role.only_kingdom"), true);
            return InteractionResult.FAIL;
        }
        if (!KingdomRoles.has(p, KingdomRoles.Role.BUILDER)) {
            p.displayClientMessage(WSLang.c("kingdom.charge.builder_only"), true);
            return InteractionResult.FAIL;
        }
        BlockPos at = ctx.getClickedPos().relative(ctx.getClickedFace());
        int gate = KingdomManager.demolitionTarget(at, KingdomDemolition.RANGE);
        if (gate == KingdomManager.NO_GATE) {
            p.displayClientMessage(WSLang.c("kingdom.charge.no_gate"), true);
            return InteractionResult.FAIL;
        }
        if (gate == KingdomManager.GUARDED) {
            p.displayClientMessage(WSLang.c("kingdom.charge.guarded"), true);
            return InteractionResult.FAIL;
        }
        if (gate == KingdomManager.NOT_BREACHED) {
            p.displayClientMessage(WSLang.c("kingdom.charge.not_breached"), true);
            return InteractionResult.FAIL;
        }
        if (!KingdomDemolition.arm(sl, at, gate)) return InteractionResult.FAIL;
        if (!p.isCreative()) ctx.getItemInHand().shrink(1);
        p.displayClientMessage(WSLang.c("kingdom.charge.armed"), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(WSLang.c("kingdom.charge.tooltip"));
    }
}
