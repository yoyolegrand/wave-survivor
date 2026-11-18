package com.wavesurvivor.horde.rift;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * 2.1 — Cristal de l'île-autel : bloc d'affichage uniquement (jamais posé dans le monde), même modèle facetté que le cristal de la
 * Mairie, en blanc. Montré par des blocs d'affichage qui flottent et tournent ; « lit » = allumé (autel capturé / pilier allumé).
 */
public class AltarCrystalBlock extends Block {

    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public AltarCrystalBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(LIT);
    }
}
