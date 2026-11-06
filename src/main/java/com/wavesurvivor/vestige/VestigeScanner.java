package com.wavesurvivor.vestige;

import com.wavesurvivor.arena.ArenaPiece;
import com.wavesurvivor.config.RulesConfig;
import com.wavesurvivor.shrine.SeraphShrinePiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RECHERCHE DES VESTIGES (1.9) : le registre ne connaissait que les structures dont les chunks étaient déjà générés. Ce scanner calcule, à partir de la
 * graine et des réglages de placement (random_spread) de chaque structure du mod, tous les emplacements possibles dans le rayon de la boussole, puis lit
 * leur « départ de structure » (état STRUCTURE_STARTS, comme /locate) en commençant par les plus proches. Travail étalé : quelques millisecondes par tick,
 * uniquement pour un joueur qui porte la boussole. Les structures trouvées sont enregistrées dans le registre comme à leur génération.
 */
final class VestigeScanner {

    private VestigeScanner() {}

    private record Cand(Holder<Structure> structure, int cx, int cz, long dist) {}

    /** Emplacements déjà examinés (dimension|structure|cx|cz). */
    private static final Set<String> DONE = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, List<Cand>> QUEUE = new HashMap<>();
    private static final Map<UUID, BlockPos> ORIGIN = new HashMap<>();
    /** Budget de temps par passage (nanosecondes). */
    private static final long BUDGET = 4_000_000L;

    /** Vrai s'il reste des emplacements à examiner pour ce joueur. */
    static boolean busy(ServerPlayer p) {
        List<Cand> q = QUEUE.get(p.getUUID());
        return q != null && !q.isEmpty();
    }

    static void forget(UUID id) {
        QUEUE.remove(id);
        ORIGIN.remove(id);
    }

    static void tick(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel level)) return;
        BlockPos origin = ORIGIN.get(p.getUUID());
        List<Cand> q = QUEUE.get(p.getUUID());
        // Nouvelle liste au premier passage, ou quand le joueur s'est beaucoup déplacé
        if (q == null || origin == null || origin.distSqr(p.blockPosition()) > 256.0 * 256.0) {
            q = build(level, p, false);
            QUEUE.put(p.getUUID(), q);
            ORIGIN.put(p.getUUID(), p.blockPosition());
        }
        if (q.isEmpty()) return;
        long end = System.nanoTime() + BUDGET;
        String dim = level.dimension().location().toString();
        while (!q.isEmpty() && System.nanoTime() < end) {
            Cand c = q.remove(0);
            Structure st = c.structure.value();
            String key = dim + "|" + c.structure.unwrapKey().map(k -> k.location().toString()).orElse("?") + "|" + c.cx + "|" + c.cz;
            if (!DONE.add(key)) continue;
            ChunkAccess chunk = level.getChunk(c.cx, c.cz, ChunkStatus.STRUCTURE_STARTS);
            StructureStart start = chunk.getStartForStructure(st);
            if (start == null || !start.isValid() || start.getPieces().isEmpty()) continue;
            var first = start.getPieces().get(0);
            BlockPos center = start.getBoundingBox().getCenter();
            if (first instanceof ArenaPiece ap) VestigeRegistry.recordArena(level, ap.arena(), center);
            else if (first instanceof SeraphShrinePiece) VestigeRegistry.record(level, VestigeRegistry.SHRINE, "seraph_shrine", center);
        }
    }

    /** Tous les emplacements possibles des structures du mod dans le rayon, non encore examinés, du plus proche au plus lointain. */
    private static List<Cand> build(ServerLevel level, ServerPlayer p, boolean ignoreDone) {
        List<Cand> out = new ArrayList<>();
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        Registry<Structure> reg = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        int r = Math.min(220, Math.max(1, RulesConfig.vestigeRadius() / 16 + 1));
        int px = p.blockPosition().getX() >> 4, pz = p.blockPosition().getZ() >> 4;
        String dim = level.dimension().location().toString();
        for (ResourceLocation id : reg.keySet()) {
            if (!id.getNamespace().equals("wavesurvivor")) continue;
            Holder<Structure> h = reg.getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE, id));
            for (StructurePlacement pl : state.getPlacementsForStructure(h)) {
                if (!(pl instanceof RandomSpreadStructurePlacement rs)) continue;
                int sp = Math.max(1, rs.spacing());
                for (int rx = Math.floorDiv(px - r, sp); rx <= Math.floorDiv(px + r, sp); rx++) {
                    for (int rz = Math.floorDiv(pz - r, sp); rz <= Math.floorDiv(pz + r, sp); rz++) {
                        ChunkPos cp = rs.getPotentialStructureChunk(state.getLevelSeed(), rx * sp, rz * sp);
                        long dx = cp.x - px, dz = cp.z - pz, d = dx * dx + dz * dz;
                        if (d > (long) r * r || (!ignoreDone && DONE.contains(dim + "|" + id + "|" + cp.x + "|" + cp.z))) continue;
                        out.add(new Cand(h, cp.x, cp.z, d));
                    }
                }
            }
        }
        out.sort(Comparator.comparingLong(Cand::dist));
        return out;
    }

    /** Diagnostic (/ws vestiges) : structures du mod vues par le générateur et emplacements possibles ; avec {@code scanAll}, examine tout de suite tous les emplacements. */
    static List<String> report(ServerPlayer p, boolean scanAll) {
        List<String> out = new ArrayList<>();
        if (!(p.level() instanceof ServerLevel level)) return out;
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        Registry<Structure> reg = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        int n = 0;
        for (ResourceLocation id : reg.keySet()) {
            if (!id.getNamespace().equals("wavesurvivor")) continue;
            Holder<Structure> h = reg.getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE, id));
            List<StructurePlacement> pls = state.getPlacementsForStructure(h);
            out.add("§7" + id.getPath() + " : " + pls.size() + " placement(s)" + (pls.isEmpty() ? "" : " " + pls.get(0).getClass().getSimpleName()));
            n += pls.size();
        }
        List<Cand> all = build(level, p, true);
        out.add("§eRayon " + RulesConfig.vestigeRadius() + " blocs : " + all.size() + " emplacement(s) possible(s), " + n + " placement(s) au total, "
                + DONE.size() + " déjà examiné(s), file d'attente " + (QUEUE.containsKey(p.getUUID()) ? QUEUE.get(p.getUUID()).size() : -1));
        int found = 0, nulls = 0;
        if (scanAll) {
            for (Cand c : all) {
                ChunkAccess chunk = level.getChunk(c.cx, c.cz, ChunkStatus.STRUCTURE_STARTS);
                StructureStart start = chunk.getStartForStructure(c.structure.value());
                String sid = c.structure.unwrapKey().map(k -> k.location().getPath()).orElse("?");
                DONE.add(level.dimension().location() + "|" + c.structure.unwrapKey().map(k -> k.location().toString()).orElse("?") + "|" + c.cx + "|" + c.cz);
                if (start == null || !start.isValid() || start.getPieces().isEmpty()) { nulls++; continue; }
                found++;
                var first = start.getPieces().get(0);
                BlockPos center = start.getBoundingBox().getCenter();
                String kind = first.getClass().getSimpleName();
                if (first instanceof ArenaPiece ap) VestigeRegistry.recordArena(level, ap.arena(), center);
                else if (first instanceof SeraphShrinePiece) VestigeRegistry.record(level, VestigeRegistry.SHRINE, "seraph_shrine", center);
                out.add("§a" + sid + " (" + kind + ") en " + center.getX() + " " + center.getZ() + " : " + Math.round(Math.sqrt(center.distSqr(p.blockPosition()))) + " blocs");
            }
            out.add("§eExamen terminé : " + found + " structure(s) trouvée(s), " + nulls + " emplacement(s) sans structure.");
        }
        return out;
    }
}
