package com.wavesurvivor.horde.kingdom;

import com.wavesurvivor.i18n.WSLang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * REMPART DU ROYAUME : segment de muraille de 3 blocs de haut (base, corps à meurtrière, sommet crénelé), plein et
 * solide. Ses 3 blocs partagent une réserve de PV (400, × « PV des murs ») : cible des Sapeurs, Béliers et unités
 * « Attaque les bâtiments ». Une seule amélioration, peu chère (clic droit avec la monnaie du royaume) : Rempart
 * renforcé, +400 PV et habillage de fer. Un joueur qui casse un bloc fait tomber tout le segment ; aucun butin.
 */
public class RampartBlock extends Block {

    public enum Part implements StringRepresentable {
        BASE("base"), MIDDLE("middle"), TOP("top");

        private final String name;

        Part(String n) { this.name = n; }

        @Override
        public String getSerializedName() { return name; }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final BooleanProperty REINFORCED = BooleanProperty.create("reinforced");
    /** Sens de la muraille (elle court le long de cet axe ; face avant / arrière de part et d'autre). */
    public static final EnumProperty<net.minecraft.core.Direction.Axis> AXIS =
            net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_AXIS;

    /** PV d'un segment, et gain de l'amélioration (× multiplicateur des PV des murs). */
    public static final float HP = 400f, UPGRADE_HP = 400f;
    /** Coût de l'amélioration (monnaie du royaume) : peu cher, on en pose beaucoup. */
    public static final int UPGRADE_COST = 4;

    public RampartBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(PART, Part.BASE).setValue(REINFORCED, false)
                .setValue(AXIS, net.minecraft.core.Direction.Axis.X));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(PART, REINFORCED, AXIS);
    }

    /** Position de la base du segment dont fait partie ce bloc. */
    public static BlockPos baseOf(BlockPos pos, BlockState st) {
        return pos.below(st.getValue(PART).ordinal());
    }

    // ─── Amélioration (clic droit) ───

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        // Un rempart en main : on construit à côté, on n'améliore pas
        if (player.getItemInHand(hand).getItem() instanceof RampartItem) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp) || hand != InteractionHand.MAIN_HAND) return InteractionResult.CONSUME;
        if (state.getValue(REINFORCED)) {
            sp.displayClientMessage(WSLang.c("kingdom.rampart.already"), true);
            return InteractionResult.CONSUME;
        }
        if (!KingdomManager.isActive()) {
            sp.displayClientMessage(WSLang.c("kingdom.rampart.only_kingdom"), true);
            return InteractionResult.CONSUME;
        }
        KingdomClaim.trackRampartAt(pos); // rempart posé avant la partie / monde rechargé : suivi dès maintenant
        if (!KingdomClaim.isPlaced(pos)) {
            sp.displayClientMessage(WSLang.c("kingdom.rampart.only_kingdom"), true);
            return InteractionResult.CONSUME;
        }
        if (!com.wavesurvivor.altar.AltarDefense.trySpend(sp, UPGRADE_COST)) return InteractionResult.CONSUME;
        BlockPos base = baseOf(pos, state);
        for (int i = 0; i < 3; i++) {
            BlockPos q = base.above(i);
            BlockState s = level.getBlockState(q);
            if (s.is(this)) level.setBlock(q, s.setValue(REINFORCED, true), Block.UPDATE_ALL);
        }
        KingdomClaim.addDefenseMaxHp(base, UPGRADE_HP);
        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.8f, 1.2f);
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.WAX_ON, base.getX() + 0.5, base.getY() + 1.5, base.getZ() + 0.5, 30, 0.4, 1.0, 0.4, 0.05);
        }
        sp.displayClientMessage(WSLang.c("kingdom.rampart.reinforced"), true);
        return InteractionResult.CONSUME;
    }

    // ─── Casse : tout le segment tombe ───

    /** Démontage au pic : un segment de rempart se casse en 3 s avec une pioche. */
    @Override
    @SuppressWarnings("deprecation")
    public float getDestroyProgress(BlockState state, Player player, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return ColliderBlock.pickaxeProgress(player, super.getDestroyProgress(state, player, level, pos));
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockPos base = baseOf(pos, state);
            for (int i = 0; i < 3; i++) {
                BlockPos q = base.above(i);
                if (q.equals(pos) || !level.getBlockState(q).is(this)) continue;
                KingdomClaim.untrack(q);
                level.setBlock(q, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }
}
