package com.wavesurvivor.horde.kingdom;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * PARTIE DE PORTE DE REMPART (mode Kingdom). Une porte = 9 de ces blocs en cadre 3×3, qui partagent la même
 * réserve de PV (KingdomClaim). Fermée : bloc plein en bois (les monstres doivent la défoncer).
 * Ouverte : invisible et traversable (KingdomDefenses l'ouvre quand un joueur ou un soldat approche).
 * Casser une partie détruit toute la porte. Ne lâche rien.
 */
public class GatePartBlock extends Block {

    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    /** Place dans le cadre 3×3 : colonne 0 = côté ouest / nord, 1 = centre (battants), 2 = côté est / sud ; rangée 0 = bas. */
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty COL =
            net.minecraft.world.level.block.state.properties.IntegerProperty.create("col", 0, 2);
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty ROW =
            net.minecraft.world.level.block.state.properties.IntegerProperty.create("row", 0, 2);
    /** Sens de la porte (axe le long duquel s'étend le cadre). */
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<net.minecraft.core.Direction.Axis> AXIS =
            BlockStateProperties.HORIZONTAL_AXIS;

    public GatePartBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(OPEN, false).setValue(COL, 1).setValue(ROW, 0)
                .setValue(AXIS, net.minecraft.core.Direction.Axis.X));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPEN, COL, ROW, AXIS);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return state.getValue(OPEN);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        // Ouverture / fermeture = même bloc : on ignore. Sinon (cassée, détruite) : toute la porte disparaît.
        if (!state.is(newState.getBlock()) && !level.isClientSide) KingdomDefenses.gateBroken(pos);
        super.onRemove(state, level, pos, newState, moved);
    }

    /** Clic droit sur la porte : l'ouvrir ou la fermer (refusé si un monstre est trop près). */
    @Override
    @SuppressWarnings("deprecation")
    public net.minecraft.world.InteractionResult use(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.player.Player player,
                                                     net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (level.isClientSide) return net.minecraft.world.InteractionResult.SUCCESS;
        if (hand == net.minecraft.world.InteractionHand.MAIN_HAND && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            KingdomDefenses.toggleGate(sp, pos);
        }
        return net.minecraft.world.InteractionResult.CONSUME;
    }
}
