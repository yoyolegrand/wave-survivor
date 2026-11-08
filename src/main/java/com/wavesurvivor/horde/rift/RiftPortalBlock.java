package com.wavesurvivor.horde.rift;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * 2.0 — Bloc de la surface de la Faille (l'ovale tourbillonnant du portail). Sans collision : un joueur qui entre dedans traverse
 * le portail (à condition qu'une Faille soit ouverte). Posé et retiré par {@link RiftGate}, jamais cassable ni obtenable.
 */
public class RiftPortalBlock extends Block {

    /** Intensité de la lueur (lumière = 8 + glow) : RiftGate la fait varier lentement pour que la Faille « respire ». */
    public static final IntegerProperty GLOW = IntegerProperty.create("glow", 0, 7);

    public RiftPortalBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(GLOW, 4));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(GLOW);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || !(entity instanceof ServerPlayer p)) return;
        if (!RiftManager.isActive() || p.isSpectator() || !p.isAlive()) return;
        RiftManager.enter(p);
    }
}
