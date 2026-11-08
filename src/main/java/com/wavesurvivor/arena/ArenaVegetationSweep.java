package com.wavesurvivor.arena;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Quand un chunk NEUF d'une arène à terrain complet devient entièrement généré, on rejoue le balayage de végétation sur ses colonnes : les arbres des
 * chunks voisins, décorés après l'arène, ont pu pousser par-dessus le plan (étals du marché, rempart…). Fait au tick suivant, quelques chunks par tick.
 */
public class ArenaVegetationSweep {

    private record Job(ServerLevel level, ChunkPos pos) {}

    private final Deque<Job> queue = new ArrayDeque<>();

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load e) {
        if (!e.isNewChunk() || !(e.getLevel() instanceof ServerLevel sl)) return;
        queue.add(new Job(sl, e.getChunk().getPos()));
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || queue.isEmpty()) return;
        for (int i = 0; i < 3 && !queue.isEmpty(); i++) {
            Job j = queue.poll();
            try { sweep(j); } catch (Exception ex) { com.wavesurvivor.WaveSurvivorMod.LOGGER.warn("[Arènes] balayage de végétation impossible en {} : {}", j.pos(), ex.getMessage()); }
        }
    }

    /** Commande de test « /ws arena sweep » : balaie tous les chunks chargés dans {@code radius} chunks autour de {@code center}. @return nombre de chunks traités */
    public static int sweepAround(ServerLevel level, ChunkPos center, int radius) {
        int n = 0;
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            ChunkPos cp = new ChunkPos(center.x + dx, center.z + dz);
            if (level.getChunkSource().getChunkNow(cp.x, cp.z) == null) continue;
            sweep(new Job(level, cp));
            n++;
        }
        return n;
    }

    private static void sweep(Job j) {
        ServerLevel level = j.level();
        LevelChunk chunk = level.getChunkSource().getChunkNow(j.pos().x, j.pos().z);
        if (chunk == null) return;
        for (StructureStart st : level.structureManager().startsForStructure(j.pos(), s -> s instanceof ArenaStructure)) {
            for (var piece : st.getPieces()) {
                if (!(piece instanceof ArenaPiece ap)) continue;
                ArenaBlueprint bp = ArenaBlueprint.load(ap.arena());
                if (bp == null) continue;
                BoundingBox clip = new BoundingBox(j.pos().getMinBlockX(), level.getMinBuildHeight(), j.pos().getMinBlockZ(),
                        j.pos().getMaxBlockX(), level.getMaxBuildHeight(), j.pos().getMaxBlockZ());
                bp.sweepVegetation(level, ap.ox(), ap.oz(), ap.y0(), ap.rotation(), clip);
            }
        }
    }
}
