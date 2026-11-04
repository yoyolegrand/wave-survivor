package com.wavesurvivor.craft;

import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * BLOCS UTILITAIRES de l'Artisanat sacré et profane (fabriqués dans l'onglet Artisanat du Sanctuaire).
 * Grâce : Lanterne de grâce, Fontaine sacrée, Bougie votive. Souillure : Totem d'appât, Obélisque profane, Autel de sang.
 * Les effets sont dans {@link CraftUtilityBlockEntity} (zones à tick) et {@link CraftUtilityEvents} (lanterne, bougie) ;
 * les valeurs sont dans crafting_fragments.json (« blocks »).
 */
public class CraftUtilityBlock extends Block implements EntityBlock {

    public enum Kind {
        LANTERN("grace_lantern"), FOUNTAIN("holy_fountain"), CANDLE("votive_candle"),
        BAIT("bait_totem"), OBELISK("profane_obelisk"), ALTAR("blood_altar");
        public final String id;
        Kind(String id) { this.id = id; }
    }

    /** Bougie votive : allumée ou non (propriété partagée par tous les blocs pour garder un seul état). */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** Lanterne de grâce suspendue au plafond (inutilisée par les autres blocs : toujours faux). */
    public static final BooleanProperty HANGING = BlockStateProperties.HANGING;
    /** Nombre de bougies votives sur le bloc (1 à 4, comme les bougies vanilla ; 1 pour les autres blocs). */
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty CANDLES = BlockStateProperties.CANDLES;

    private static final VoxelShape LANTERN_SHAPE = Block.box(4, 0, 4, 12, 10, 12);
    private static final VoxelShape LANTERN_HANGING_SHAPE = Block.box(4, 1, 4, 12, 16, 12);
    private static final VoxelShape[] CANDLE_SHAPES = {Block.box(7, 0, 7, 9, 6, 9), Block.box(5, 0, 6, 11, 6, 9), Block.box(5, 0, 6, 10, 6, 11), Block.box(5, 0, 5, 11, 6, 10)};
    /** Position des flammes (fractions de bloc) selon le nombre de bougies, comme les bougies vanilla. */
    private static final double[][][] CANDLE_FLAMES = {
            {{0.5, 0.5, 0.5}},
            {{0.375, 0.44, 0.5}, {0.625, 0.5, 0.44}},
            {{0.5, 0.313, 0.625}, {0.375, 0.44, 0.5}, {0.56, 0.5, 0.44}},
            {{0.44, 0.313, 0.56}, {0.625, 0.44, 0.56}, {0.375, 0.44, 0.375}, {0.56, 0.5, 0.375}}};
    private static final VoxelShape FOUNTAIN_SHAPE = Block.box(0, 0, 0, 16, 15, 16);
    private static final VoxelShape TOTEM_SHAPE = Block.box(2, 0, 2, 14, 16, 14);
    /** Autel de sang : socle (0 à 2,5), corps étroit (jusqu'à 10) et plateau pleine largeur (10 à 13) ; les chandeliers au-dessus n'ont pas de collision. */
    private static final VoxelShape ALTAR_SHAPE = net.minecraft.world.phys.shapes.Shapes.or(
            Block.box(0, 0, 0, 16, 1, 16), Block.box(1, 1, 1, 15, 2.5, 15), Block.box(2, 2.5, 2, 14, 10, 14), Block.box(0, 10, 0, 16, 13, 16));

    public final Kind kind;

    public CraftUtilityBlock(Properties props, Kind kind) {
        super(props);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(LIT, kind != Kind.CANDLE).setValue(HANGING, false).setValue(CANDLES, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(LIT, HANGING, CANDLES); }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return switch (kind) {
            case LANTERN -> s.getValue(HANGING) ? LANTERN_HANGING_SHAPE : LANTERN_SHAPE;
            case CANDLE -> CANDLE_SHAPES[s.getValue(CANDLES) - 1];
            case FOUNTAIN -> FOUNTAIN_SHAPE;
            case BAIT, OBELISK -> TOTEM_SHAPE;
            case ALTAR -> ALTAR_SHAPE;
            default -> super.getShape(s, l, p, c);
        };
    }

    @Override
    public boolean canSurvive(BlockState s, net.minecraft.world.level.LevelReader l, BlockPos p) {
        if (kind != Kind.LANTERN && kind != Kind.CANDLE) return true;
        if (kind == Kind.LANTERN && s.getValue(HANGING)) return l.getBlockState(p.above()).isFaceSturdy(l, p.above(), net.minecraft.core.Direction.DOWN);
        return l.getBlockState(p.below()).isFaceSturdy(l, p.below(), net.minecraft.core.Direction.UP);
    }

    /** Lanterne : suspendue si on vise le dessous d'un bloc (comme la lanterne vanilla), posée sinon. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext ctx) {
        BlockState def = defaultBlockState();
        if (kind == Kind.CANDLE) { // une bougie de plus sur la même case (jusqu'à 4), comme les bougies vanilla
            BlockState here = ctx.getLevel().getBlockState(ctx.getClickedPos());
            if (here.is(this)) return here.setValue(CANDLES, Math.min(4, here.getValue(CANDLES) + 1));
        }
        if (kind != Kind.LANTERN) return def.canSurvive(ctx.getLevel(), ctx.getClickedPos()) ? def : null;
        for (net.minecraft.core.Direction d : ctx.getNearestLookingDirections()) {
            if (d.getAxis() != net.minecraft.core.Direction.Axis.Y) continue;
            BlockState st = def.setValue(HANGING, d == net.minecraft.core.Direction.UP);
            if (st.canSurvive(ctx.getLevel(), ctx.getClickedPos())) return st;
        }
        return null;
    }

    @Override
    public boolean canBeReplaced(BlockState s, net.minecraft.world.item.context.BlockPlaceContext ctx) {
        if (kind == Kind.CANDLE && !ctx.isSecondaryUseActive() && ctx.getItemInHand().is(this.asItem()) && s.getValue(CANDLES) < 4) return true;
        return super.canBeReplaced(s, ctx);
    }

    /** Bougie allumée : flammes dorées au bout de chaque mèche, un peu de fumée et de crépitement. */
    @Override
    public void animateTick(BlockState s, Level level, BlockPos p, net.minecraft.util.RandomSource r) {
        if (kind != Kind.CANDLE || !s.getValue(LIT)) return;
        for (double[] o : CANDLE_FLAMES[s.getValue(CANDLES) - 1]) {
            double x = p.getX() + o[0], y = p.getY() + o[1], z = p.getZ() + o[2];
            float f = r.nextFloat();
            if (f < 0.2f) {
                level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE, x, y, z, 0, 0, 0);
                if (f < 0.1f) level.playLocalSound(x, y, z, SoundEvents.CANDLE_AMBIENT, SoundSource.BLOCKS, 1f + r.nextFloat(), r.nextFloat() * 0.7f + 0.3f, false);
            }
            level.addParticle(com.wavesurvivor.registry.ModParticles.GOLD_FLAME.get(), x, y, z, 0, 0, 0);
        }
    }

    @Override
    public BlockState updateShape(BlockState s, net.minecraft.core.Direction d, BlockState n, net.minecraft.world.level.LevelAccessor l, BlockPos p, BlockPos np) {
        net.minecraft.core.Direction support = kind == Kind.LANTERN && s.getValue(HANGING) ? net.minecraft.core.Direction.UP : net.minecraft.core.Direction.DOWN;
        return d == support && !s.canSurvive(l, p) ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                : super.updateShape(s, d, n, l, p, np);
    }

    // ─── Bloc entité ───

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CraftUtilityBlockEntity(pos, state); }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.CRAFT_UTILITY.get()) return null;
        if (kind != Kind.FOUNTAIN && kind != Kind.OBELISK && kind != Kind.BAIT) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<CraftUtilityBlockEntity>) CraftUtilityBlockEntity::serverTick;
    }

    // ─── Clic droit ───

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
        if (kind != Kind.CANDLE && kind != Kind.BAIT && kind != Kind.ALTAR) return InteractionResult.PASS;
        if (kind == Kind.CANDLE && p.getItemInHand(hand).is(this.asItem()) && state.getValue(CANDLES) < 4) return InteractionResult.PASS; // on empile au lieu d'allumer
        if (level.isClientSide) return InteractionResult.SUCCESS;
        long now = level.getGameTime();
        CraftUtilityBlockEntity be = level.getBlockEntity(pos) instanceof CraftUtilityBlockEntity e ? e : null;
        switch (kind) {
            case CANDLE -> {
                boolean lit = !state.getValue(LIT);
                level.setBlock(pos, state.setValue(LIT, lit), 3);
                level.playSound(null, pos, lit ? SoundEvents.FLINTANDSTEEL_USE : SoundEvents.CANDLE_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 1f);
            }
            case BAIT -> {
                if (be == null) return InteractionResult.PASS;
                if (now < be.readyAt) {
                    p.displayClientMessage(Component.literal(WSLang.t("craft.block.cooldown", (be.readyAt - now + 19) / 20)), true);
                    return InteractionResult.CONSUME;
                }
                long active = (long) CraftConfig.blockNum("bait_totem", "activeSeconds", 30) * 20;
                long cool = (long) CraftConfig.blockNum("bait_totem", "cooldownSeconds", 60) * 20;
                be.activeUntil = now + active;
                be.readyAt = now + active + cool;
                be.setChanged();
                level.playSound(null, pos, SoundEvents.WITHER_AMBIENT, SoundSource.BLOCKS, 0.6f, 0.6f);
                p.displayClientMessage(Component.literal(WSLang.t("craft.block.bait_on", active / 20)), true);
            }
            case ALTAR -> {
                if (be == null) return InteractionResult.PASS;
                if (now < be.readyAt) {
                    p.displayClientMessage(Component.literal(WSLang.t("craft.block.cooldown", (be.readyAt - now + 19) / 20)), true);
                    return InteractionResult.CONSUME;
                }
                float cost = (float) CraftConfig.blockNum("blood_altar", "healthCost", 8);
                if (p.getHealth() <= cost) {
                    p.displayClientMessage(Component.literal(WSLang.t("craft.block.altar_weak")), true);
                    return InteractionResult.CONSUME;
                }
                p.hurt(level.damageSources().magic(), cost);
                p.giveExperiencePoints((int) CraftConfig.blockNum("blood_altar", "xp", 120));
                be.readyAt = now + (long) CraftConfig.blockNum("blood_altar", "cooldownSeconds", 30) * 20;
                be.setChanged();
                level.playSound(null, pos, SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, SoundSource.BLOCKS, 1f, 0.7f);
            }
            default -> { }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tip, TooltipFlag flag) {
        for (String line : WSLang.t("craft.block." + kind.id).split("\n")) tip.add(Component.literal(line));
    }
}
