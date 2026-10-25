package com.wavesurvivor.shrine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * SANCTUAIRE DU SÉRAPHIN (bloc incassable, modèle 3D sur mesure affiché par SeraphShrineRenderer). Le clic droit ouvre l'écran de la statue ;
 * la jauge de foi et l'étape sont gardées par SeraphShrineBlockEntity.
 */
public class SeraphShrineBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public SeraphShrineBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;   // le bloc lui-même est invisible : tout est dessiné par le rendu 3D
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SeraphShrineBlockEntity(pos, state);
    }

    // ─── Corps physique (blocs de collision invisibles) ───

    /** Hauteur de la colonne de collision au-dessus du Sanctuaire (la statue fait environ 5 blocs de haut avec son socle). */
    public static final int COLLIDER_HEIGHT = 4;

    /** Pose le corps physique : socle autour du Sanctuaire et colonne au-dessus, seulement là où c'est libre (air, herbes). */
    public static void placeColliders(net.minecraft.world.level.LevelAccessor level, BlockPos pos) {
        BlockState ring = com.wavesurvivor.registry.ModBlocks.SHRINE_COLLIDER.get().defaultBlockState().setValue(ShrineColliderBlock.RING, true);
        BlockState column = com.wavesurvivor.registry.ModBlocks.SHRINE_COLLIDER.get().defaultBlockState().setValue(ShrineColliderBlock.RING, false);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            put(level, pos.offset(dx, 0, dz), ring);
        }
        for (int dy = 1; dy <= COLLIDER_HEIGHT; dy++) put(level, pos.above(dy), column);
    }

    private static void put(net.minecraft.world.level.LevelAccessor level, BlockPos p, BlockState st) {
        BlockState cur = level.getBlockState(p);
        if ((cur.isAir() || cur.canBeReplaced()) && !cur.is(st.getBlock())) level.setBlock(p, st, 2);
    }

    /** Retire le corps physique (le Sanctuaire disparaît). */
    public static void removeColliders(net.minecraft.world.level.LevelAccessor level, BlockPos pos) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            for (int dy = 0; dy <= COLLIDER_HEIGHT; dy++) {
                BlockPos p = pos.offset(dx, dy, dz);
                if (level.getBlockState(p).getBlock() instanceof ShrineColliderBlock) level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    /** Sanctuaire auquel appartient un bloc de collision (au plus 1 bloc autour, 4 en dessous), ou null. */
    @Nullable
    public static BlockPos findShrine(Level level, BlockPos pos) {
        for (int dy = 0; dy <= COLLIDER_HEIGHT; dy++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos p = pos.offset(dx, -dy, dz);
            if (level.getBlockState(p).getBlock() instanceof SeraphShrineBlock) return p;
        }
        return null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!level.isClientSide && !old.is(state.getBlock())) placeColliders(level, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) removeColliders(level, pos);
        super.onRemove(state, level, pos, newState, moved);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state,
            net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, com.wavesurvivor.registry.ModBlockEntities.SERAPH_SHRINE.get(), SeraphShrineBlockEntity::serverTick);
    }

    /** Clic droit : ouvre l'écran du Sanctuaire (offrandes, récolte, fusion, forge). */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof net.minecraft.server.level.ServerPlayer sp && level.getBlockEntity(pos) instanceof SeraphShrineBlockEntity be) {
            net.minecraftforge.network.NetworkHooks.openScreen(sp, be, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.CONSUME;
    }
}
