package com.wavesurvivor.horde.kingdom;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * BLOC DE COLLISION des défenses du mode Kingdom : invisible (on ne voit que la structure 3D), mais SOLIDE
 * (joueurs, monstres et flèches s'y cognent). Il appartient à une défense (KingdomDefenses) :
 *  - il partage les PV de sa défense : un monstre qui le frappe abîme la défense (comme un mur) ;
 *  - un clic droit dessus agit comme sur la défense (améliorer, réparer, ouvrir le Glaneur) ;
 *  - incassable à la main ; il disparaît avec sa défense (destruction, fin de partie).
 */
public class ColliderBlock extends Block {

    public ColliderBlock(Properties props) {
        super(props);
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0f;
    }

    /** Clic droit : transmis à la défense propriétaire. */
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        BlockPos parent = KingdomDefenses.parentOf(pos);
        if (parent == null) return InteractionResult.PASS;
        BlockState ps = level.getBlockState(parent);
        return ps.use(level, player, hand, hit.withPosition(parent));
    }

    /** Détruit par un monstre (PV de la défense à 0) : toute la défense tombe. */
    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) KingdomDefenses.colliderBroken(pos);
        super.onRemove(state, level, pos, newState, moved);
    }

    /**
     * Démontage au pic : un joueur armé d'une pioche (n'importe quel matériau) casse une tour ou un rempart en 3 s.
     * Sans pioche, la valeur d'origine du bloc s'applique ({@code fallback}).
     */
    public static float pickaxeProgress(Player player, float fallback) {
        if (player != null && player.getMainHandItem().is(net.minecraft.tags.ItemTags.PICKAXES)) return 1f / 60f; // 60 ticks = 3 s
        return fallback;
    }

    @Override
    @SuppressWarnings("deprecation")
    public float getDestroyProgress(BlockState state, Player player, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        // Partie invisible d'une tour : incassable à la main, 3 s à la pioche
        return pickaxeProgress(player, 0f);
    }
}
