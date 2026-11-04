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

    private static final VoxelShape LANTERN_SHAPE = Block.box(4, 0, 4, 12, 10, 12);
    private static final VoxelShape LANTERN_HANGING_SHAPE = Block.box(4, 1, 4, 12, 16, 12);
    private static final VoxelShape CANDLE_SHAPE = Block.box(6, 0, 6, 10, 7, 10);
    private static final VoxelShape FOUNTAIN_SHAPE = Block.box(0, 0, 0, 16, 15, 16);
    private static final VoxelShape TOTEM_SHAPE = Block.box(2, 0, 2, 14, 16, 14);
    private static final VoxelShape ALTAR_SHAPE = Block.box(0, 0, 0, 16, 12, 16);

    public final Kind kind;

    public CraftUtilityBlock(Properties props, Kind kind) {
        super(props);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(LIT, kind != Kind.CANDLE).setValue(HANGING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(LIT, HANGING); }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return switch (kind) {
            case LANTERN -> s.getValue(HANGING) ? LANTERN_HANGING_SHAPE : LANTERN_SHAPE;
            case CANDLE -> CANDLE_SHAPE;
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
        if (kind != Kind.LANTERN) return def.canSurvive(ctx.getLevel(), ctx.getClickedPos()) ? def : null;
        for (net.minecraft.core.Direction d : ctx.getNearestLookingDirections()) {
            if (d.getAxis() != net.minecraft.core.Direction.Axis.Y) continue;
            BlockState st = def.setValue(HANGING, d == net.minecraft.core.Direction.UP);
            if (st.canSurvive(ctx.getLevel(), ctx.getClickedPos())) return st;
        }
        return null;
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
