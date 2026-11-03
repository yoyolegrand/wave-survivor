package com.wavesurvivor.craft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * FESTIN DE GRÂCE : bloc posable à 6 parts (modèle 3D sur assiette, 6 stades). Chaque part rend de la faim et des PV (config) ;
 * toute l'équipe peut en manger. Ce n'est PAS une nourriture spéciale : il ne remplace pas l'effet d'une autre.
 */
public class GraceFeastBlock extends Block {

    public static final int PORTIONS = 6;
    public static final IntegerProperty BITES = IntegerProperty.create("bites", 0, PORTIONS - 1);

    /** Forme fixe sur l'assiette (le modèle 3D ne rétrécit pas comme un gâteau). */
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 4, 15);

    public GraceFeastBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(BITES, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(BITES); }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPE; }

    @Override
    public boolean canSurvive(BlockState s, LevelReader l, BlockPos p) { return l.getBlockState(p.below()).isSolid(); }

    @Override
    public BlockState updateShape(BlockState s, Direction d, BlockState n, LevelAccessor l, BlockPos p, BlockPos np) {
        return d == Direction.DOWN && !s.canSurvive(l, p) ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                : super.updateShape(s, d, n, l, p, np);
    }

    @Override
    public boolean isPathfindable(BlockState s, BlockGetter l, BlockPos p, PathComputationType t) { return false; }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
        // Une part n'est mangée que si le joueur a faim ou est blessé
        if (!p.canEat(false) && p.getHealth() >= p.getMaxHealth()) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        p.getFoodData().eat(CraftConfig.feastNutrition(), CraftConfig.feastSaturation());
        p.heal(CraftConfig.feastHeal());
        for (CraftConfig.FoodEffect fe : CraftConfig.feastEffects()) {
            net.minecraft.world.effect.MobEffect eff = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.get(fe.effect());
            if (eff != null) p.addEffect(new net.minecraft.world.effect.MobEffectInstance(eff, fe.seconds() * 20, fe.amplifier(), false, true, true));
        }
        level.playSound(null, pos, SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 1f, 1f);
        int bites = state.getValue(BITES);
        if (bites + 1 >= PORTIONS) level.removeBlock(pos, false);
        else level.setBlock(pos, state.setValue(BITES, bites + 1), 3);
        return InteractionResult.CONSUME;
    }
}
