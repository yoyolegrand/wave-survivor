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
 * SANCTUAIRE DU SÉRAPHIN (bloc posable, modèle 3D sur mesure affiché par SeraphShrineRenderer).
 * Pour l'instant (aperçu), on change son état à la main :
 *  - offrandes PURES (lingot d'or, fleurs, miel, perle de l'Ender) : clic droit = purifie d'une étape ;
 *  - sacrifices IMPURS (chair putréfiée, œil d'araignée, os) : clic droit = corrompt d'une étape ;
 *  - accroupi, main vide : fait défiler les 5 étapes.
 */
public class SeraphShrineBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);
    private static final String[] STAGE_NAMES = {"§e✦ Pure", "§f✧ Apaisée", "§7◇ Neutre", "§d✕ Souillée", "§5☠ Corrompue"};

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

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SeraphShrineBlockEntity shrine)) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        int delta;
        if (held.isEmpty() && player.isShiftKeyDown()) delta = 99;                       // défilement des étapes
        else if (isPure(held)) delta = -1;
        else if (isImpure(held)) delta = 1;
        else return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;

        int before = shrine.getStage();
        int after = delta == 99 ? (before + 1) % (SeraphShrineBlockEntity.MAX_STAGE + 1) : before + delta;
        shrine.setStage(after);
        if (delta != 99 && shrine.getStage() != before && !player.getAbilities().instabuild) held.shrink(1);

        ServerLevel sl = (ServerLevel) level;
        boolean purer = shrine.getStage() < before || (delta == 99 && after == 0);
        double cx = pos.getX() + 0.5, cy = pos.getY() + 3.0, cz = pos.getZ() + 0.5;
        if (purer) {
            sl.sendParticles(new DustParticleOptions(new Vector3f(1f, 0.88f, 0.45f), 1.4f), cx, cy, cz, 40, 1.2, 1.4, 1.2, 0);
            sl.sendParticles(ParticleTypes.END_ROD, cx, cy, cz, 15, 1.0, 1.5, 1.0, 0.02);
            sl.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.5f, 1.4f);
        } else {
            sl.sendParticles(new DustParticleOptions(new Vector3f(1f, 0.27f, 0.78f), 1.4f), cx, cy, cz, 40, 1.2, 1.4, 1.2, 0);
            sl.sendParticles(ParticleTypes.SMOKE, cx, cy, cz, 20, 1.0, 1.5, 1.0, 0.02);
            sl.playSound(null, pos, SoundEvents.WITHER_AMBIENT, SoundSource.BLOCKS, 0.6f, 1.6f);
        }
        player.displayClientMessage(Component.literal("Sanctuaire du Séraphin : " + STAGE_NAMES[shrine.getStage()]), true);
        return InteractionResult.CONSUME;
    }

    private static boolean isPure(ItemStack s) {
        return s.is(Items.GOLD_INGOT) || s.is(Items.HONEY_BOTTLE) || s.is(Items.ENDER_PEARL)
                || s.is(net.minecraft.tags.ItemTags.FLOWERS);
    }

    private static boolean isImpure(ItemStack s) {
        return s.is(Items.ROTTEN_FLESH) || s.is(Items.SPIDER_EYE) || s.is(Items.BONE);
    }
}
