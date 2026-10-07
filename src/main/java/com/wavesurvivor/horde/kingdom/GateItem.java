package com.wavesurvivor.horde.kingdom;

import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * PORTE DE REMPART (objet) : pose un cadre 3 de large × 3 de haut de GatePartBlock, perpendiculaire au regard
 * du joueur, centré sur le bloc visé. Uniquement dans le claim, pendant une partie Kingdom, sur un espace dégagé.
 */
public class GateItem extends Item {

    public GateItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(ctx.getPlayer() instanceof ServerPlayer p) || !(level instanceof ServerLevel sl)) return InteractionResult.PASS;

        BlockState clicked = level.getBlockState(ctx.getClickedPos());
        BlockPos base = clicked.canBeReplaced() ? ctx.getClickedPos() : ctx.getClickedPos().relative(ctx.getClickedFace());
        Direction side = p.getDirection().getClockWise(); // la porte s'étend sur les côtés du joueur
        List<BlockPos> parts = new ArrayList<>();
        for (int i = -1; i <= 1; i++) for (int y = 0; y <= 2; y++) parts.add(base.relative(side, i).above(y));

        if (!KingdomManager.isActive() || !KingdomClaim.isActive()) {
            p.displayClientMessage(WSLang.c("kingdom.defense_only_kingdom"), true);
            return InteractionResult.FAIL;
        }
        for (BlockPos q : parts) {
            // Même règle que les tours : « Claim obligatoire : NON » → posable partout (créatif toujours exempté)
            if (!p.isCreative() && !KingdomClaim.buildAllowed(q)) {
                p.displayClientMessage(WSLang.c("kingdom.claim_outside"), true);
                return InteractionResult.FAIL;
            }
            BlockState s = level.getBlockState(q);
            if (!s.isAir() && !s.canBeReplaced()) {
                p.displayClientMessage(WSLang.c("kingdom.gate_space"), true);
                return InteractionResult.FAIL;
            }
        }
        // Chaque partie connaît sa place (colonne ouest/nord → est/sud, rangée) et le sens de la porte : visuel dédié
        boolean positive = side.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        var gate = ModBlocks.KINGDOM_GATE_PART.get().defaultBlockState().setValue(GatePartBlock.AXIS, side.getAxis());
        for (int i = -1; i <= 1; i++) for (int y = 0; y <= 2; y++) {
            int col = (positive ? i : -i) + 1;
            level.setBlock(base.relative(side, i).above(y), gate.setValue(GatePartBlock.COL, col).setValue(GatePartBlock.ROW, y), 3);
        }
        KingdomDefenses.registerGate(sl, base, parts);
        if (!p.isCreative()) ctx.getItemInHand().shrink(1);
        level.playSound(null, base, SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.BLOCKS, 1.2f, 0.7f);
        level.playSound(null, base, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.5f, 1.2f);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(WSLang.c("kingdom.gate.desc"));
    }
}
