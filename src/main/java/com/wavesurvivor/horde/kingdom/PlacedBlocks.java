package com.wavesurvivor.horde.kingdom;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * ANTI-FARM du trésor Kingdom : positions des blocs « à ressource » (pierre, bois, minerai de fer) POSÉS par un joueur
 * ou créés par un fluide, par dimension, enregistrées avec le monde. Recasser un de ces blocs ne rapporte rien.
 * Suivi permanent (pas seulement pendant une partie) : bâtir une tour de pierre avant la horde pour la miner ensuite
 * ne rapporte donc rien non plus. Une position est oubliée dès que le bloc est cassé.
 */
public class PlacedBlocks extends SavedData {

    private static final String NAME = "wavesurvivor_placed";

    private final LongOpenHashSet set = new LongOpenHashSet();

    public static PlacedBlocks get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(PlacedBlocks::load, PlacedBlocks::new, NAME);
    }

    public void add(BlockPos pos) {
        if (set.add(pos.asLong())) setDirty();
    }

    /** @return vrai si la position était retenue (bloc posé). */
    public boolean remove(BlockPos pos) {
        boolean r = set.remove(pos.asLong());
        if (r) setDirty();
        return r;
    }

    private static PlacedBlocks load(CompoundTag t) {
        PlacedBlocks p = new PlacedBlocks();
        for (long l : t.getLongArray("p")) p.set.add(l);
        return p;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        t.putLongArray("p", set.toLongArray());
        return t;
    }
}
