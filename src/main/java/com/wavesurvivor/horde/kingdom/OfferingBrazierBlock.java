package com.wavesurvivor.horde.kingdom;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;

/**
 * BRASIER DES OFFRANDES (Kingdom) — bloc posé au coin nord-ouest d'une emprise 2×2 sur 2 blocs de haut
 * (les 7 autres cases sont des blocs de collision invisibles). Clic droit : ouvre la grille d'offrandes.
 */
public class OfferingBrazierBlock extends Block implements EntityBlock {

    public OfferingBrazierBlock(Properties props) {
        super(props);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OfferingBrazierBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel sl)) return;
        String why = null;
        if (!KingdomManager.isActive() || KingdomManager.level() != sl) why = "kingdom.defense_only_kingdom";
        else if (!OfferingBrazier.hasRoom(sl, pos)) why = "kingdom.brazier.space";
        if (why != null) {
            sl.removeBlock(pos, false);
            if (placer instanceof ServerPlayer sp) {
                sp.displayClientMessage(com.wavesurvivor.i18n.WSLang.c(why), true);
                if (!sp.isCreative()) {
                    ItemStack back = stack.copy();
                    back.setCount(1);
                    if (!sp.getInventory().add(back)) sp.drop(back, false);
                }
            }
            return;
        }
        OfferingBrazier.register(sl, pos);
        sl.playSound(null, pos, net.minecraft.sounds.SoundEvents.SOUL_ESCAPE, net.minecraft.sounds.SoundSource.BLOCKS, 1f, 1f);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp
                && level.getBlockEntity(pos) instanceof OfferingBrazierBlockEntity be) {
            net.minecraftforge.network.NetworkHooks.openScreen(sp, be, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.CONSUME;
    }

    /** Démontage au pic, comme les autres défenses. */
    @Override
    @SuppressWarnings("deprecation")
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return ColliderBlock.pickaxeProgress(player, super.getDestroyProgress(state, player, level, pos));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl) {
            // La grille d'offrandes tombe au sol (rien n'est perdu), puis les blocs de collision disparaissent
            if (level.getBlockEntity(pos) instanceof OfferingBrazierBlockEntity be) {
                net.minecraft.world.Containers.dropContents(level, pos, be.getStorage());
            }
            OfferingBrazier.unregister(sl, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(com.wavesurvivor.i18n.WSLang.c("kingdom.defense.desc.brazier"));
        tooltip.add(com.wavesurvivor.i18n.WSLang.c("kingdom.brazier.hint"));
    }
}
