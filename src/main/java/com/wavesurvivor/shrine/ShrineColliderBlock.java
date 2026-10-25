package com.wavesurvivor.shrine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Corps physique INVISIBLE du Sanctuaire du Séraphin : la statue est un rendu 3D, pas des blocs, on passerait donc à travers. Ces blocs
 * (colonne au-dessus du Sanctuaire, socle autour de lui) la rendent solide pour joueurs, monstres et flèches, sans rien afficher. Incassables
 * en Survie ; un clic droit dessus agit comme sur le Sanctuaire.
 */
public class ShrineColliderBlock extends Block {

    /** Vrai pour le socle (bas, 0,75 bloc), faux pour la colonne du corps de la statue (bloc plein). */
    public static final BooleanProperty RING = BooleanProperty.create("ring");
    private static final VoxelShape BASE = Block.box(0, 0, 0, 16, 12, 16);
    private static final VoxelShape FULL = Block.box(0, 0, 0, 16, 16, 16);

    public ShrineColliderBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(RING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RING);
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(RING) ? BASE : FULL;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(RING) ? BASE : FULL;
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

    /** Clic droit : transmis au Sanctuaire proche (ouvre son écran). */
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos shrine = SeraphShrineBlock.findShrine(level, pos);
        if (shrine == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        return level.getBlockState(shrine).use(level, player, hand, hit.withPosition(shrine));
    }
}
