package com.wavesurvivor.horde.kingdom;

import com.wavesurvivor.altar.AltarRecipes;
import com.wavesurvivor.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CORRUPTION DU SOL autour d'une Porte du mode Kingdom (comme l'arène d'un autel).
 * Se propage par anneaux depuis la Porte (un anneau par tick), selon une zone configurée dans l'éditeur :
 * rayon + sol pondéré + dessin du « Peintre de sol » (mêmes règles que l'autel).
 * Chaque bloc modifié est mémorisé : Porte détruite → son sol est PURIFIÉ ; fin de partie → terrain restauré.
 * Ne touche jamais les blocs de la Porte elle-même, l'eau, la lave ni les arbres.
 */
final class GateCorruption {

    private final ServerLevel level;
    private final BlockPos center;
    private final AltarRecipes.Zone zone;
    private final List<List<int[]>> rings = new ArrayList<>();
    private int ring = 0;
    /** Blocs d'origine (pour purifier / restaurer). */
    private final Map<BlockPos, BlockState> original = new LinkedHashMap<>();

    GateCorruption(ServerLevel level, BlockPos center, AltarRecipes.Zone zone) {
        this.level = level;
        this.center = center;
        this.zone = zone;
        int radius = Math.min(Math.max(0, zone.radius), 48);
        int reach = Math.max(radius, Math.min(zone.patternHalf(), 48));
        int maxRing = (int) Math.ceil(reach * 1.4143) + 1;
        for (int r = 0; r <= maxRing; r++) rings.add(new ArrayList<>());
        double max = (radius + 0.5) * (radius + 0.5);
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                double d2 = dx * dx + dz * dz;
                if (d2 > max && zone.patternAt(dx, dz) == null) continue;
                rings.get(Math.min(maxRing, (int) Math.round(Math.sqrt(d2)))).add(new int[]{dx, dz});
            }
        }
    }

    /** Vrai si une zone de corruption est réglée (rayon > 0 et un sol ou un dessin). */
    static boolean usable(AltarRecipes.Zone z) {
        return z != null && z.isActive();
    }

    /** Propagation : un anneau par appel. */
    void tick() {
        if (ring >= rings.size()) return;
        List<int[]> cols = rings.get(ring++);
        int n = 0;
        for (int[] c : cols) {
            if (convert(c[0], c[1]) && (n++ % 6) == 0) {
                level.sendParticles(ParticleTypes.SCULK_SOUL, center.getX() + c[0] + 0.5,
                        level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX() + c[0], center.getZ() + c[1]) + 0.2,
                        center.getZ() + c[1] + 0.5, 1, 0.2, 0.1, 0.2, 0.01);
            }
        }
    }

    private boolean convert(int dx, int dz) {
        int x = center.getX() + dx, z = center.getZ() + dz;
        BlockPos p = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
        if (KingdomManager.isGateBlock(p)) return false;                 // la Porte elle-même
        BlockState cur = level.getBlockState(p);
        if (cur.isAir() || !cur.getFluidState().isEmpty()) return false;  // eau, lave
        if (cur.is(BlockTags.LOGS) || cur.is(BlockTags.LEAVES)) return false; // arbres
        if (cur.hasBlockEntity()) return false;                          // coffres, autels…
        BlockState target = zone.targetAt(dx, dz, level.getRandom());
        if (target == null) return false;
        target = stabilize(target);
        if (target.equals(cur)) return false;
        original.putIfAbsent(p, cur);
        level.setBlock(p, target, 2);
        return true;
    }

    /** Sable / gravier « sans gravité » (ne tombent pas), comme pour l'arène des autels. */
    private static BlockState stabilize(BlockState s) {
        if (s.is(Blocks.SAND)) return ModBlocks.STABLE_SAND.get().defaultBlockState();
        if (s.is(Blocks.RED_SAND)) return ModBlocks.STABLE_RED_SAND.get().defaultBlockState();
        if (s.is(Blocks.GRAVEL)) return ModBlocks.STABLE_GRAVEL.get().defaultBlockState();
        return s;
    }

    /** Purification : le terrain d'origine revient (Porte détruite ou fin de partie). */
    void restore() {
        List<Map.Entry<BlockPos, BlockState>> entries = new ArrayList<>(original.entrySet());
        for (int i = entries.size() - 1; i >= 0; i--) {
            BlockPos p = entries.get(i).getKey();
            level.setBlock(p, entries.get(i).getValue(), 2);
            if ((i % 8) == 0) level.sendParticles(ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5, 1, 0.2, 0.1, 0.2, 0.01);
        }
        original.clear();
        ring = rings.size(); // plus de propagation
    }
}
