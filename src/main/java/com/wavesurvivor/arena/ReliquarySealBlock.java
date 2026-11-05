package com.wavesurvivor.arena;

import com.wavesurvivor.horde.roulette.KeyPouch;
import com.wavesurvivor.horde.roulette.SealedKey;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SCEAU DU RELIQUAIRE SCELLÉ (1.9) : bloc incassable qui ferme la porte de la ruine « Reliquaire scellé » (12 blocs, dont la serrure au centre).
 * Clic droit avec une Clé scellée (en main, ou prise dans une Bourse à clés) : la clé est consommée et tous les blocs du sceau voisins disparaissent
 * du centre vers l'extérieur (environ une demi-seconde) avec des particules dorées et roses. Sans clé : un message.
 * L'ouverture passe par des ticks programmés (propriété {@code opening}) : elle survit à un déchargement de chunk ou à un redémarrage.
 */
public class ReliquarySealBlock extends Block {

    /** Serrure : le bloc central, d'aspect propre. */
    public static final BooleanProperty LOCK = BooleanProperty.create("lock");
    /** Bloc en train de disparaître (tick programmé). */
    public static final BooleanProperty OPENING = BooleanProperty.create("opening");

    private static final DustParticleOptions GOLD = new DustParticleOptions(new org.joml.Vector3f(1.0f, 0.78f, 0.2f), 1.2f);
    private static final DustParticleOptions PINK = new DustParticleOptions(new org.joml.Vector3f(1.0f, 0.3f, 0.78f), 1.2f);

    public ReliquarySealBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(LOCK, false).setValue(OPENING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(LOCK, OPENING); }

    /** Même un Wither ou un Dragon ne le casse pas. */
    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) { return false; }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (state.getValue(OPENING)) return InteractionResult.CONSUME;
        ItemStack held = p.getItemInHand(hand);
        boolean used = false;
        if (SealedKey.is(held)) {
            if (!p.getAbilities().instabuild) held.shrink(1);
            used = true;
        } else used = KeyPouch.takeSealed(p);
        if (!used) {
            p.displayClientMessage(Component.literal(WSLang.t("reliquary_seal.locked")), true);
            level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.7f, 0.6f);
            return InteractionResult.CONSUME;
        }
        open((ServerLevel) level, pos);
        if (p instanceof net.minecraft.server.level.ServerPlayer sp) com.wavesurvivor.vestige.VestigeRegistry.claimNear(sp, com.wavesurvivor.vestige.VestigeRegistry.RUIN, pos, 30, "reliquaire_scelle"); // Boussole des Vestiges
        p.displayClientMessage(Component.literal(WSLang.t("reliquary_seal.opened")), true);
        return InteractionResult.CONSUME;
    }

    /** Lance l'ouverture : tous les blocs du sceau reliés à celui-ci disparaîtront, la serrure d'abord. */
    private void open(ServerLevel level, BlockPos start) {
        List<BlockPos> all = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        todo.add(start);
        seen.add(start);
        while (!todo.isEmpty() && all.size() < 96) {
            BlockPos c = todo.poll();
            all.add(c);
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                BlockPos n = c.relative(d);
                if (seen.add(n) && level.getBlockState(n).is(this)) todo.add(n);
            }
        }
        BlockPos center = start;
        for (BlockPos c : all) if (level.getBlockState(c).getValue(LOCK)) center = c;
        for (BlockPos c : all) {
            double dist = Math.sqrt(c.distSqr(center));
            level.setBlock(c, level.getBlockState(c).setValue(OPENING, true), 2);
            level.scheduleTick(c, this, 2 + (int) Math.round(dist * 4)); // du centre vers l'extérieur, ~0,5 s au total
        }
        level.playSound(null, center, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.2f, 0.6f);
        level.playSound(null, center, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.5f, 0.8f);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource rnd) {
        if (!state.getValue(OPENING)) return;
        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        level.sendParticles(GOLD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 14, 0.4, 0.4, 0.4, 0.03);
        level.sendParticles(PINK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 14, 0.4, 0.4, 0.4, 0.03);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 0.8f, 1.2f);
    }
}
