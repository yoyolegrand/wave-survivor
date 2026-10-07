package com.wavesurvivor.altar;

import com.wavesurvivor.horde.roulette.RouletteChestConfig;
import com.wavesurvivor.horde.roulette.RouletteChestRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Disposition des roulette chests et des marchands dans la zone (claim) d'un autel.
 *
 *   - Slots manuels (relatifs à l'autel) d'abord : s'il y a des slots "chest", ils remplacent l'auto des coffres ;
 *     les slots "merchant" sont attribués aux premiers marchands, les suivants passent en auto.
 *   - Auto : coffres en cercle à ~70% du rayon (un par config, max N, exclusions), marchands à ~45%,
 *     décalés angulairement pour ne pas s'aligner sur les coffres.
 *   - Anti-chevauchement : chaque position réserve sa colonne + un anneau de 1 bloc autour ;
 *     si la place visée est prise ou invalide, recherche en spirale d'un spot libre dans la zone.
 */
public class AltarLayout {

    public record ChestSpot(BlockPos pos, RouletteChestConfig config) {}

    public record Result(List<ChestSpot> chests, List<BlockPos> merchants) {}

    private static final int SCAN_UP = 3;
    private static final int SCAN_DOWN = 5;
    private static final int SEARCH_RADIUS = 4;

    /** Coffres choisis en auto : toutes les configs sauf exclues, dans l'ordre du registre, max N. */
    public static List<RouletteChestConfig> autoChestConfigs(AltarRecipes.Zone zone) {
        List<RouletteChestConfig> out = new ArrayList<>();
        int max = zone != null ? Math.max(0, zone.maxChests) : 8;
        for (String key : RouletteChestRegistry.allKeys()) {
            if (out.size() >= max) break;
            if (zone != null && zone.excludedChests != null && zone.excludedChests.contains(key)) continue;
            RouletteChestConfig c = RouletteChestRegistry.get(key);
            if (c != null) out.add(c);
        }
        return out;
    }

    /** Ancienne signature (nombre de marchands sans noms) : emplacements « marchand » pris dans l'ordre. */
    public static Result compute(ServerLevel level, BlockPos altar, int radius, AltarRecipes.Zone zone, int merchantCount) {
        List<String> blank = new ArrayList<>();
        for (int i = 0; i < merchantCount; i++) blank.add("");
        return compute(level, altar, radius, zone, blank);
    }

    public static Result compute(ServerLevel level, BlockPos altar, int radius, AltarRecipes.Zone zone, List<String> merchantNames) {
        Set<Long> reserved = new HashSet<>();
        reserve(reserved, altar.getX(), altar.getZ()); // l'autel et son pourtour

        List<AltarRecipes.Slot> slots = zone != null && zone.slots != null ? zone.slots : List.of();
        // « Placer les autres automatiquement » : ce qui n'est pas posé à la main est disposé en cercle (sinon absent)
        boolean auto = zone == null || zone.autoLayout;

        // ── Coffres : posés à la main d'abord, puis les autres en auto ──
        List<ChestSpot> chests = new ArrayList<>();
        Set<String> placed = new HashSet<>();
        for (AltarRecipes.Slot s : slots) {
            if (!"chest".equals(s.type) || s.configKey == null) continue;
            RouletteChestConfig cfg = RouletteChestRegistry.get(s.configKey);
            if (cfg == null || !placed.add(s.configKey.toLowerCase())) continue;
            BlockPos p = findNear(level, altar, radius, altar.getX() + s.dx, altar.getZ() + s.dz,
                    altar.getY() + s.dy, 1, reserved);
            if (p != null) chests.add(new ChestSpot(p, cfg));
        }
        if (auto) {
            List<RouletteChestConfig> cfgs = new ArrayList<>();
            int max = zone != null ? Math.max(0, zone.maxChests) : 8;
            for (RouletteChestConfig c : autoChestConfigs(zone)) {
                if (chests.size() + cfgs.size() >= Math.max(max, chests.size())) break;
                boolean dup = false;
                for (String k : placed) if (RouletteChestRegistry.get(k) == c) dup = true;
                if (!dup) cfgs.add(c);
            }
            int n = cfgs.size();
            int ring = Math.max(2, (int) Math.round(radius * 0.7));
            for (int i = 0; i < n; i++) {
                double a = 2 * Math.PI * i / n + Math.PI / 8;
                int x = altar.getX() + (int) Math.round(Math.cos(a) * ring);
                int z = altar.getZ() + (int) Math.round(Math.sin(a) * ring);
                BlockPos p = findNear(level, altar, radius, x, z, altar.getY(), 1, reserved);
                if (p != null) chests.add(new ChestSpot(p, cfgs.get(i)));
            }
        }

        // ── Marchands : une position PAR marchand (même ordre que la liste), null = pas de place ──
        int merchantCount = merchantNames == null ? 0 : merchantNames.size();
        List<BlockPos> merchants = new ArrayList<>();
        for (int i = 0; i < merchantCount; i++) merchants.add(null);
        boolean[] taken = new boolean[merchantCount];
        for (AltarRecipes.Slot s : slots) {
            if (!"merchant".equals(s.type)) continue;
            int idx = -1;
            if (s.name != null && !s.name.isBlank()) {
                for (int i = 0; i < merchantCount; i++) {
                    if (!taken[i] && s.name.equalsIgnoreCase(merchantNames.get(i))) { idx = i; break; }
                }
                if (idx < 0) continue; // marchand renommé ou supprimé : emplacement ignoré
            } else {
                for (int i = 0; i < merchantCount; i++) if (!taken[i]) { idx = i; break; }
                if (idx < 0) continue;
            }
            BlockPos p = findNear(level, altar, radius, altar.getX() + s.dx, altar.getZ() + s.dz,
                    altar.getY() + s.dy, 2, reserved);
            if (p != null) {
                merchants.set(idx, p);
                taken[idx] = true;
            }
        }
        if (auto) {
            List<Integer> rest = new ArrayList<>();
            for (int i = 0; i < merchantCount; i++) if (!taken[i]) rest.add(i);
            int remaining = rest.size();
            int ring = Math.max(2, (int) Math.round(radius * 0.45));
            for (int k = 0; k < remaining; k++) {
                double a = 2 * Math.PI * k / remaining + Math.PI / 4 + Math.PI / Math.max(1, remaining);
                int x = altar.getX() + (int) Math.round(Math.cos(a) * ring);
                int z = altar.getZ() + (int) Math.round(Math.sin(a) * ring);
                BlockPos p = findNear(level, altar, radius, x, z, altar.getY(), 2, reserved);
                if (p != null) merchants.set(rest.get(k), p);
            }
        }
        return new Result(chests, merchants);
    }

    // ─── Recherche de position libre ───

    /**
     * Cherche, en spirale autour de (x,z), une colonne libre (non réservée) dans la zone
     * dont la surface accepte un objet de `height` blocs de haut. Réserve la colonne trouvée.
     */
    private static BlockPos findNear(ServerLevel level, BlockPos altar, int radius, int x, int z, int refY,
                                     int height, Set<Long> reserved) {
        double max2 = (radius + 0.5) * (radius + 0.5);
        for (int r = 0; r <= SEARCH_RADIUS; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue; // anneau de la spirale
                    int cx = x + dx, cz = z + dz;
                    double ax = cx - altar.getX(), az = cz - altar.getZ();
                    if (ax * ax + az * az > max2) continue;
                    if (reserved.contains(key(cx, cz))) continue;
                    BlockPos p = surface(level, cx, cz, refY, height);
                    if (p == null) continue;
                    reserve(reserved, cx, cz);
                    return p;
                }
            }
        }
        return null;
    }

    /** Premier emplacement (bloc d'air posé sur un bloc plein, avec `height` blocs libres) autour de refY. */
    private static BlockPos surface(ServerLevel level, int x, int z, int refY, int height) {
        for (int y = refY + SCAN_UP; y >= refY - SCAN_DOWN; y--) {
            BlockPos p = new BlockPos(x, y, z);
            BlockState below = level.getBlockState(p.below());
            if (!below.isCollisionShapeFullBlock(level, p.below())) continue;
            if (!below.getFluidState().isEmpty()) continue;
            boolean free = true;
            for (int h = 0; h < height; h++) {
                BlockState s = level.getBlockState(p.above(h));
                if (!(s.isAir() || s.canBeReplaced()) || !s.getFluidState().isEmpty()) { free = false; break; }
            }
            if (free) return p;
        }
        return null;
    }

    /** Réserve la colonne + l'anneau d'1 bloc autour (espacement minimum de 2 entre deux objets). */
    private static void reserve(Set<Long> reserved, int x, int z) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                reserved.add(key(x + dx, z + dz));
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }
}
