package com.wavesurvivor.horde.spawn;

import com.wavesurvivor.WaveSurvivorMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ZONE DE SPAWN SÛRE.
 *   - SCAN (au lancement de la horde, ou au premier spawn d'une zone) : chaque colonne du cercle de spawn est testée
 *     de +8 à -8 blocs autour de la hauteur du point de horde ; on garde la première position qui a
 *       · un sol SOLIDE (pas de feuilles, magma, cactus, feu de camp, neige poudreuse…),
 *       · 2 blocs LIBRES au-dessus (pieds + tête), sans liquide ni bloc dangereux (lave, eau, feu, rose du Wither,
 *         buisson à baies, toile, cactus…),
 *     ainsi que sa HAUTEUR LIBRE (jusqu'à 5 blocs) pour les grands monstres.
 *     Les 2 blocs autour du point de horde (Monolithe) sont exclus.
 *   - SPAWN : tirage parmi les positions valides assez hautes pour le monstre, puis RE-VÉRIFICATION en direct
 *     (sol toujours solide, boîte de collision du monstre libre) au cas où un joueur aurait construit depuis.
 *   - SECOURS : si rien n'est valide, position sûre la plus proche du centre (sommet du terrain) + alerte aux admins.
 */
public final class SpawnZone {

    private SpawnZone() {}

    private static final Random RNG = new Random();
    private static final int SCAN_UP = 8, SCAN_DOWN = 8, MAX_CLEAR = 5, EXCLUDE_CENTER = 2;

    /** Position valide + hauteur libre au-dessus. */
    public record Spot(BlockPos pos, int clearance) {}

    private record Zone(List<Spot> spots, int columns) {}

    private static final Map<String, Zone> CACHE = new ConcurrentHashMap<>();
    private static final java.util.Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private static String key(Level level, BlockPos c, int r) {
        return level.dimension().location() + "|" + c.asLong() + "|" + r;
    }

    /** Oublie toutes les zones (fin / arrêt de horde) : le prochain spawn rescanne. */
    public static void clear() {
        CACHE.clear();
        WARNED.clear();
    }

    /** Scanne (ou rescanne) la zone et la garde en mémoire. Renvoie {positions valides, colonnes testées}. */
    public static int[] prepare(Level level, BlockPos center, int radius) {
        Zone z = scan(level, center, radius);
        CACHE.put(key(level, center, radius), z);
        WaveSurvivorMod.LOGGER.info("[SpawnZone] {} positions valides sur {} colonnes (rayon {}, centre {})",
                z.spots().size(), z.columns(), radius, center.toShortString());
        return new int[]{z.spots().size(), z.columns()};
    }

    /** Positions valides de la zone (scan si besoin). */
    public static List<Spot> spots(Level level, BlockPos center, int radius) {
        return CACHE.computeIfAbsent(key(level, center, radius), k -> scan(level, center, radius)).spots();
    }

    // ─── Scan ───

    private static Zone scan(Level level, BlockPos center, int radius) {
        int r = Math.max(1, radius);
        List<Spot> out = new ArrayList<>();
        int columns = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d2 = dx * dx + dz * dz;
                if (d2 > (double) r * r) continue;
                if (r > 4 && d2 < EXCLUDE_CENTER * EXCLUDE_CENTER) continue; // pas sur le Monolithe (arènes seulement)
                columns++;
                int x = center.getX() + dx, z = center.getZ() + dz;
                for (int y = center.getY() + SCAN_UP; y >= center.getY() - SCAN_DOWN; y--) {
                    p.set(x, y, z);
                    if (isSafe(level, p)) {
                        out.add(new Spot(p.immutable(), clearance(level, p)));
                        break;
                    }
                }
            }
        }
        return new Zone(out, columns);
    }

    /** Sol solide et sûr + pieds et tête libres et sans danger. */
    public static boolean isSafe(Level level, BlockPos feet) {
        BlockPos below = feet.below();
        BlockState floor = level.getBlockState(below);
        if (!floor.isFaceSturdy(level, below, Direction.UP) || dangerousFloor(floor)) return false;
        return passable(level, feet) && passable(level, feet.above());
    }

    private static boolean passable(Level level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (!s.getFluidState().isEmpty()) return false;            // eau, lave…
        if (!s.getCollisionShape(level, p).isEmpty()) return false; // bloc plein / partiel
        return !dangerous(s);
    }

    private static int clearance(Level level, BlockPos feet) {
        int c = 0;
        BlockPos.MutableBlockPos q = feet.mutable();
        while (c < MAX_CLEAR && passable(level, q)) { c++; q.move(Direction.UP); }
        return c;
    }

    private static boolean dangerous(BlockState s) {
        return s.is(BlockTags.FIRE) || s.is(Blocks.WITHER_ROSE) || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.COBWEB)
                || s.is(Blocks.CACTUS) || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.LAVA) || s.is(Blocks.NETHER_PORTAL)
                || s.is(Blocks.END_PORTAL);
    }

    private static boolean dangerousFloor(BlockState s) {
        return s.is(BlockTags.LEAVES) || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.CACTUS) || s.is(BlockTags.CAMPFIRES)
                || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.WITHER_ROSE);
    }

    // ─── Tirage d'une position ───

    /**
     * Sol le plus proche d'une hauteur de référence (pieds posés sur un bloc solide, 2 blocs libres au-dessus), cherché
     * de refY+3 à refY-8. Contrairement à la carte des hauteurs, ignore les toits, arbres, barrières et piliers au-dessus :
     * un totem ou un sbire apparaît à la hauteur du boss (grotte, arène flottante…). Rien trouvé : carte des hauteurs.
     */
    public static BlockPos groundNear(Level level, int x, int z, int refY) {
        for (int dy = 3; dy >= -8; dy--) {
            BlockPos feet = new BlockPos(x, refY + dy, z);
            var below = level.getBlockState(feet.below());
            if (below.is(net.minecraft.world.level.block.Blocks.BARRIER) || below.getCollisionShape(level, feet.below()).isEmpty()) continue;
            if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) continue;
            if (!level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) continue;
            if (!level.getFluidState(feet).isEmpty()) continue;
            return feet;
        }
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    /**
     * Position de spawn sûre dans le cercle (center, radius) pour ce type de monstre (null = taille humaine).
     * Tient compte de la hauteur / largeur du monstre et revérifie la position en direct.
     */
    public static BlockPos pick(Level level, BlockPos center, int radius, EntityType<?> type) {
        EntityDimensions dim = type != null ? type.getDimensions() : EntityDimensions.scalable(0.6f, 1.95f);
        int needH = Math.max(2, (int) Math.ceil(dim.height));
        List<Spot> all = spots(level, center, radius);

        // Positions assez hautes pour ce monstre
        List<Spot> fit = new ArrayList<>();
        for (Spot s : all) if (s.clearance() >= Math.min(needH, MAX_CLEAR)) fit.add(s);
        if (fit.isEmpty()) fit = all;

        for (int tries = 0; tries < 16 && !fit.isEmpty(); tries++) {
            Spot s = fit.get(RNG.nextInt(fit.size()));
            if (stillValid(level, s.pos(), dim)) return s.pos();
        }
        return fallback(level, center, radius);
    }

    /** Re-vérification en direct : sol toujours solide et boîte de collision du monstre libre. */
    private static boolean stillValid(Level level, BlockPos feet, EntityDimensions dim) {
        if (!isSafe(level, feet)) return false;
        AABB box = dim.makeBoundingBox(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5).deflate(0.05);
        return level.noCollision(box) && !level.containsAnyLiquid(box);
    }

    /** Aucune position valide : sommet du terrain près du centre + alerte (une fois par zone). */
    private static BlockPos fallback(Level level, BlockPos center, int radius) {
        String k = key(level, center, radius);
        if (WARNED.add(k) && level instanceof ServerLevel sl) {
            WaveSurvivorMod.LOGGER.warn("[SpawnZone] Aucune position de spawn sûre (centre {}, rayon {})", center.toShortString(), radius);
            Component msg = com.wavesurvivor.i18n.WSLang.c("spawnzone.none");
            for (ServerPlayer op : sl.getServer().getPlayerList().getPlayers()) if (op.hasPermissions(2)) op.sendSystemMessage(msg);
        }
        int x = center.getX() + EXCLUDE_CENTER + 1, z = center.getZ();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new BlockPos(x, y, z);
    }

    // ─── Commande de débogage /ws spawnzone ───

    /**
     * Scanne la zone autour du joueur et affiche les positions valides (particules vertes, 10 s, visibles par lui seul).
     * radius ≤ 0 → rayon de la horde en cours, sinon 20.
     */
    public static int debugShow(ServerPlayer player, int radius) {
        ServerLevel level = player.serverLevel();
        int r = radius;
        if (r <= 0) {
            var hm = com.wavesurvivor.horde.HordeManager.get();
            r = hm.getActiveHorde() != null && hm.getActiveHorde().configData != null ? hm.getActiveHorde().configData.spawnRadius : 20;
        }
        BlockPos c = player.blockPosition();
        int[] res = prepare(level, c, r);
        List<Spot> found = spots(level, c, r);
        for (int i = 0; i < 10; i++) {
            com.wavesurvivor.horde.skill.DelayedActionScheduler.schedule(level.getServer(), i * 20, () -> {
                for (Spot s : found) {
                    level.sendParticles(player, net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, true,
                            s.pos().getX() + 0.5, s.pos().getY() + 0.2, s.pos().getZ() + 0.5, 1, 0.15, 0.05, 0.15, 0);
                }
            }, "spawnzone debug");
        }
        int pct = res[1] > 0 ? Math.round(100f * res[0] / res[1]) : 0;
        player.sendSystemMessage(com.wavesurvivor.i18n.WSLang.c("spawnzone.result", res[0], res[1], r, pct));
        return 1;
    }
}
