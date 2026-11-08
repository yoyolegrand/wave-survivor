package com.wavesurvivor.horde.rift;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.HordeConfigMultiData;
import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.network.ShakePacket;
import com.wavesurvivor.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 2.0 (jalon 1b) — PORTAIL DE LA FAILLE EN BLOCS RÉELS ET SON ANIMATION D'OUVERTURE.
 * Un « Antre » colossal (≈ 28 de large × 17 de haut) : demi-dôme de roche noire (pierre noire, basalte, deepslate, obsidienne),
 * ovale en retrait fermé par de la pierre, veines d'obsidienne pleureuse autour de l'ovale, vrilles turquoise / violettes
 * qui rampent sur la roche, cristaux d'améthyste. Les blocs sont provisoires (le style définitif viendra avec les modèles du Chat).
 * <p>
 * Première ouverture (130 « temps » = 6,5 s à vitesse 1) : 1 grondement, 2 le rocher surgit (couche par couche),
 * 3 le silence (fissures), 4 l'explosion (l'ovale s'ouvre), 5 la Faille s'ouvre (surface du portail + veines), 6 la Faille vit (vrilles).
 * Ouvertures suivantes : le rocher reste en place, courte réouverture (~1,5 s). Géométrie déterministe (graine = pied du rocher) :
 * on peut la recalculer à tout moment depuis {@link RiftPortals.Rec}.
 */
final class RiftGate {

    // ─── Géométrie (repère local : lx = gauche/droite, y = hauteur, lz = profondeur depuis la façade, vers l'intérieur du rocher) ───
    private static final int PLANE = 2;                      // profondeur de la surface du portail (ovale en retrait)
    private static final double RX = 4.6, RY = 6.2, CY = 7.5; // ovale
    private static final int TOP = 17;                       // couches 0..17
    private static final int DIST = 18;                      // distance autel → façade
    private static final int END_FRESH = 130, END_REOPEN = 112;

    private final ServerLevel level;
    final BlockPos origin;
    final int fx, fz;
    private final long seed;
    private final BlockPos center;

    private final List<List<int[]>> byY = new ArrayList<>();      // par couche : {lx, y, lz, type}  (type 9 = pierre de scellé)
    private final List<int[]> plane = new ArrayList<>();
    private final List<int[]> sealCells = new ArrayList<>();      // ovale creusé + surface (pierre de scellé)
    private final List<double[]> rim = new ArrayList<>();         // {lx, y, lz, ring}, triés du plus proche au plus loin
    private final List<List<int[]>> tendrils = new ArrayList<>();
    private final List<int[]> clusters = new ArrayList<>();

    // ─── Séquence ───
    private boolean running, fresh, open;
    private double vt;
    private int lastStep;
    private int revealed = -1, rimDone, tendrilsDone;
    private double speed = 1.0, shake = 1.0;
    private boolean title = true;

    private RiftGate(ServerLevel level, BlockPos origin, int fx, int fz) {
        this.level = level;
        this.origin = origin;
        this.fx = fx;
        this.fz = fz;
        this.seed = origin.asLong();
        this.center = w(0, 6, -2);
        buildGeometry();
    }

    /** Portail déjà invoqué pour cet autel, sinon un nouvel emplacement (la direction au terrain le plus plat, façade tournée vers l'autel). */
    static RiftGate plan(ServerLevel level, BlockPos altar, boolean[] freshOut) {
        RiftPortals.Rec rec = RiftPortals.find(level.getServer(), level, altar);
        if (rec != null) {
            freshOut[0] = false;
            return new RiftGate(level, rec.origin(), rec.fx(), rec.fz());
        }
        freshOut[0] = true;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int[] best = dirs[0];
        double bestScore = Double.MAX_VALUE;
        for (int[] d : dirs) {
            int ox = altar.getX() - d[0] * DIST, oz = altar.getZ() - d[1] * DIST;
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, wet = 0;
            for (int lx = -12; lx <= 12; lx += 4) {
                for (int lz = -2; lz <= 12; lz += 4) {
                    int x = ox + (-d[1]) * lx - d[0] * lz, z = oz + d[0] * lx - d[1] * lz;
                    int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    min = Math.min(min, h);
                    max = Math.max(max, h);
                    if (!level.getBlockState(new BlockPos(x, h - 1, z)).getFluidState().isEmpty()) wet++;
                }
            }
            double score = (max - min) + wet * 6.0;
            if (score < bestScore) {
                bestScore = score;
                best = d;
            }
        }
        int ox = altar.getX() - best[0] * DIST, oz = altar.getZ() - best[1] * DIST;
        int oy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ox, oz) - 1;
        return new RiftGate(level, new BlockPos(ox, oy, oz), best[0], best[1]);
    }

    /** Reconstruit un portail mémorisé (fermeture au démarrage du serveur). */
    static RiftGate of(ServerLevel level, RiftPortals.Rec rec) {
        return new RiftGate(level, rec.origin(), rec.fx(), rec.fz());
    }

    BlockPos w(int lx, int y, int lz) {
        return new BlockPos(origin.getX() - fz * lx - fx * lz, origin.getY() + y, origin.getZ() + fx * lx - fz * lz);
    }

    boolean isOpen() { return open; }

    boolean isRunning() { return running; }

    // ─── Géométrie ───

    private static double sq(double v) { return v * v; }

    private static double noise(long seed, int a, int b, int c) {
        long h = seed ^ (a * 73856093L) ^ (b * 19349663L) ^ (c * 83492791L);
        h = (h ^ (h >>> 13)) * 0x5bd1e995L;
        h ^= h >>> 15;
        return (h & 0xFFFFFF) / (double) 0x1000000;
    }

    private static long key(int lx, int y, int lz) { return ((long) (lx + 64) << 20) | ((long) (y + 64) << 10) | (lz + 64); }

    private void buildGeometry() {
        for (int i = 0; i <= TOP; i++) byY.add(new ArrayList<>());
        Map<Long, int[]> rockMap = new LinkedHashMap<>();
        Set<Long> carvedSet = new HashSet<>();
        for (int lx = -14; lx <= 14; lx++) {
            for (int y = 0; y <= TOP; y++) {
                for (int lz = 0; lz <= 13; lz++) {
                    double ring = sq(lx / RX) + sq((y - CY) / RY);
                    if (y >= 1 && ring < 1 && lz <= PLANE) {
                        int[] c = {lx, y, lz, 9};
                        sealCells.add(c);
                        byY.get(y).add(c);
                        carvedSet.add(key(lx, y, lz));
                        if (lz == PLANE) plane.add(c);
                        continue;
                    }
                    double e = sq(lx / 13.5) + sq(y / 17.0) + sq(lz / 12.5);
                    if (e > 1 + (noise(seed, lx, y, lz) - 0.5) * 0.35) continue;
                    double n = noise(seed, lx, y, lz + 99);
                    int type = n < 0.40 ? 0 : n < 0.68 ? 1 : n < 0.84 ? 2 : n < 0.95 ? 3 : 4;
                    int[] c = {lx, y, lz, type};
                    rockMap.put(key(lx, y, lz), c);
                    byY.get(y).add(c);
                    if (lz <= 1 && ring >= 1 && ring < 1.75) rim.add(new double[]{lx, y, lz, ring});
                }
            }
        }
        rim.sort(Comparator.comparingDouble(a -> a[3]));
        // Surface de la roche (au moins une face à l'air), pour les vrilles et les cristaux
        int[][] nb = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        Set<Long> surface = new HashSet<>();
        List<int[]> tops = new ArrayList<>();
        for (int[] c : rockMap.values()) {
            for (int[] d : nb) {
                long k = key(c[0] + d[0], c[1] + d[1], c[2] + d[2]);
                if (c[1] + d[1] >= 0 && !rockMap.containsKey(k)) {
                    surface.add(key(c[0], c[1], c[2]));
                    break;
                }
            }
            if (c[1] >= 4 && !rockMap.containsKey(key(c[0], c[1] + 1, c[2])) && !carvedSet.contains(key(c[0], c[1] + 1, c[2]))) tops.add(c);
        }
        Random rnd = new Random(seed);
        // 12 vrilles : elles partent du bord de l'ovale et s'éloignent en rampant sur la surface
        List<double[]> starts = new ArrayList<>();
        for (double[] r : rim) if (r[3] < 1.3 && surface.contains(key((int) r[0], (int) r[1], (int) r[2]))) starts.add(r);
        for (int t = 0; t < 14 && !starts.isEmpty(); t++) {
            double[] s = starts.get(rnd.nextInt(starts.size()));
            int[] cur = rockMap.get(key((int) s[0], (int) s[1], (int) s[2]));
            List<int[]> path = new ArrayList<>();
            Set<Long> seen = new HashSet<>();
            for (int step = 0; step < 26 && cur != null; step++) {
                path.add(cur);
                seen.add(key(cur[0], cur[1], cur[2]));
                int[] next = null;
                int off = rnd.nextInt(6);
                for (int i = 0; i < 6 && next == null; i++) {
                    int[] d = nb[(i + off) % 6];
                    int[] c = rockMap.get(key(cur[0] + d[0], cur[1] + d[1], cur[2] + d[2]));
                    if (c == null || seen.contains(key(c[0], c[1], c[2])) || !surface.contains(key(c[0], c[1], c[2]))) continue;
                    if (sq(c[0]) + sq(c[1] - CY) >= sq(cur[0]) + sq(cur[1] - CY)) next = c;
                }
                cur = next;
            }
            tendrils.add(path);
        }
        // Cristaux d'améthyste sur le dessus
        for (int i = 0; i < 16 && !tops.isEmpty(); i++) {
            int[] c = tops.get(rnd.nextInt(tops.size()));
            clusters.add(new int[]{c[0], c[1] + 1, c[2]});
        }
    }

    private static BlockState rock(int type) {
        return switch (type) {
            case 0 -> Blocks.BLACKSTONE.defaultBlockState();
            case 1 -> Blocks.BASALT.defaultBlockState();
            case 2 -> Blocks.SMOOTH_BASALT.defaultBlockState();
            case 3 -> Blocks.DEEPSLATE.defaultBlockState();
            case 4 -> Blocks.OBSIDIAN.defaultBlockState();
            default -> Blocks.STONE.defaultBlockState();   // 9 : pierre de scellé (l'ovale fermé), claire pour se distinguer du rocher
        };
    }

    private void set(int lx, int y, int lz, BlockState st) {
        level.setBlock(w(lx, y, lz), st, 2);
    }

    // ─── Séquence d'ouverture ───

    /** Démarre l'animation : complète la première fois, courte réouverture ensuite. */
    void begin(boolean freshOpen, HordeConfigMultiData.RiftSettings rs) {
        fresh = freshOpen;
        speed = Math.max(0.2, Math.min(3.0, rs.openingSpeed));
        shake = Math.max(0.0, Math.min(3.0, rs.shakeIntensity));
        title = rs.showTitle;
        open = false;
        running = true;
        if (fresh) {
            vt = 0;
            lastStep = -1;
            revealed = -1;
            rimDone = 0;
            tendrilsDone = 0;
            RiftPortals.add(level.getServer(), new RiftPortals.Rec(level.dimension().location().toString(), RiftManager.altarOf(), origin, fx, fz));
        } else {
            revealed = TOP;
            rimDone = rim.size();
            tendrilsDone = tendrils.size();
            vt = 80;
            lastStep = 79;
        }
    }

    /** À appeler chaque tick serveur ; faux tant que la séquence tourne. */
    boolean tick() {
        if (!running) return true;
        vt += 1.0 / speed;
        int target = (int) Math.floor(vt);
        while (lastStep < target && running) {
            lastStep++;
            step(lastStep);
        }
        if (running && fresh) pushPlayers();
        return !running;
    }

    private void step(int s) {
        if (s == 0) {
            snd(SoundEvents.AMBIENT_CAVE.value(), 4f, 0.6f);
            snd(SoundEvents.WARDEN_DIG, 3f, 0.5f);
            shake(34, 0.5);
        }
        if (s < 30 && s % 2 == 0 && fresh) {
            for (int i = 0; i < 4; i++) {
                BlockPos p = w(-12 + (int) (noise(seed, s, i, 1) * 24), 0, -1 + (int) (noise(seed, s, i, 2) * 14));
                level.sendParticles(ParticleTypes.WITCH, p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5, 5, 0.4, 0.2, 0.4, 0.04);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5, 2, 0.4, 0.5, 0.4, 0.02);
            }
        }
        if (s == 16 && fresh) shake(18, 0.9);
        if (s == 30 && fresh) shake(42, 1.4);
        if (s >= 30 && s < 70 && fresh) {
            int target = (int) Math.round(TOP * (1 - sq(1 - (s - 30) / 40.0)));
            reveal(Math.min(TOP, target));
            if (s % 3 == 0) snd(s % 6 == 0 ? SoundEvents.STONE_BREAK : SoundEvents.DEEPSLATE_BREAK, 2.5f, 0.6f);
        }
        if (s == 70 && fresh) {
            reveal(TOP);
            snd(SoundEvents.WARDEN_HEARTBEAT, 3f, 0.8f);
        }
        if (s == 74 && fresh) { // l'ovale de fissures violettes sur la façade
            for (int[] c : sealCells) {
                if (c[2] != 0) continue;
                double ring = sq(c[0] / RX) + sq((c[1] - CY) / RY);
                if (ring >= 0.72) set(c[0], c[1], c[2], Blocks.AMETHYST_BLOCK.defaultBlockState());
            }
            snd(SoundEvents.AMETHYST_BLOCK_CHIME, 3f, 0.5f);
        }
        if (s == 80) { // l'explosion : l'ovale vole en éclats
            for (int[] c : sealCells) set(c[0], c[1], c[2], Blocks.AIR.defaultBlockState());
            level.sendParticles(ParticleTypes.FLASH, center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5, 3, 1.5, 3, 1.5, 0);
            level.sendParticles(ParticleTypes.EXPLOSION, center.getX() + 0.5, center.getY() + 2.0, center.getZ() + 0.5, 6, 2.5, 3, 2.5, 0);
            level.sendParticles(ParticleTypes.WITCH, center.getX() + 0.5, center.getY() + 2.0, center.getZ() + 0.5, 60, 2, 3, 2, 0.3);
            snd(SoundEvents.GENERIC_EXPLODE, 5f, 0.7f);
            shake(10, 1.8);
        }
        if (s == 86) snd(SoundEvents.PORTAL_TRIGGER, 3f, 0.9f);
        if (s >= 86 && s <= 110) fillPortal((s - 85) / 25.0);
        if (s == 110) {
            fillPortal(1.0);
            snd(SoundEvents.PORTAL_AMBIENT, 3f, 0.6f);
            if (fresh) for (int[] c : clusters) {
                if (level.getBlockState(w(c[0], c[1], c[2])).isAir()) set(c[0], c[1], c[2], Blocks.AMETHYST_CLUSTER.defaultBlockState());
            }
            if (title && fresh) announce();
        }
        if (s > 110 && s < END_FRESH && fresh) { // les vrilles jaillissent une à une
            int due = (int) Math.ceil((s - 110) / 19.0 * tendrils.size());
            while (tendrilsDone < Math.min(due, tendrils.size())) {
                placeTendril(tendrils.get(tendrilsDone), tendrilsDone % 2 == 0);
                tendrilsDone++;
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.getX() + 0.5, center.getY() + 3.0, center.getZ() + 0.5, 20, 3, 3, 3, 0.1);
            }
        }
        if (s >= (fresh ? END_FRESH : END_REOPEN)) {
            fillPortal(1.0);
            open = true;
            running = false;
            WaveSurvivorMod.LOGGER.info("[Faille] Portail ouvert en {} ({}).", origin, fresh ? "première ouverture" : "réouverture");
        }
    }

    private static final int[][] LOCAL_DIRS = {{0, 0, -1}, {0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, -1, 0}, {0, 0, 1}};

    private Direction worldDir(int[] d) {
        return Direction.getNearest(-fz * d[0] - fx * d[2], d[1], fx * d[0] - fz * d[2]);
    }

    /** Une face de la cellule {@code c} est-elle libre (air) du côté {@code d} ? */
    private boolean exposed(int[] c, int[] d) {
        return level.getBlockState(w(c[0] + d[0], c[1] + d[1], c[2] + d[2])).isAir();
    }

    /** Pose une veine plaquée contre la roche (face tournée vers la cellule {@code c}), dans la cellule d'air voisine du côté {@code d}. */
    private void placeVein(int[] c, int[] d, boolean teal) {
        BlockPos pos = w(c[0] + d[0], c[1] + d[1], c[2] + d[2]);
        Block vb = (teal ? ModBlocks.RIFT_VEIN_TEAL : ModBlocks.RIFT_VEIN_VIOLET).get();
        BooleanProperty prop = MultifaceBlock.getFaceProperty(worldDir(new int[]{-d[0], -d[1], -d[2]}));
        BlockState cur = level.getBlockState(pos);
        if (cur.is(vb)) level.setBlock(pos, cur.setValue(prop, true), 2);
        else if (cur.isAir()) level.setBlock(pos, vb.defaultBlockState().setValue(prop, true), 2);
    }

    /** Une vrille = un chemin continu de veines qui rampe sur la roche ; la face reste la même d'une cellule à l'autre, et contourne les arêtes. */
    private void placeTendril(List<int[]> path, boolean teal) {
        int[] prev = null;
        for (int[] c : path) {
            int[] face = null;
            if (prev != null && exposed(c, prev)) face = prev;
            if (face == null) for (int[] d : LOCAL_DIRS) if (exposed(c, d)) { face = d; break; }
            if (face == null) continue;
            placeVein(c, face, teal);
            if (prev != null && prev != face && exposed(c, prev)) placeVein(c, prev, teal);   // l'arête : le filament s'enroule
            prev = face;
        }
    }

    // ─── Faille vivante (portail ouvert) : contour, aspiration, étincelles, lueur qui respire ───

    private static final DustParticleOptions TEAL = new DustParticleOptions(new org.joml.Vector3f(0.10f, 0.95f, 0.85f), 1.1f);
    private int glow = 4;

    /** Position monde d'un point du repère local (fractions autorisées), au centre des cellules. */
    private double[] wd(double lx, double y, double lz) {
        return new double[]{origin.getX() + 0.5 - fz * lx - fx * lz, origin.getY() + y + 0.5, origin.getZ() + 0.5 + fx * lx - fz * lz};
    }

    /** À appeler à chaque tick serveur tant que le portail est ouvert. */
    void tickOpen(long time) {
        if (!open) return;
        boolean near = false;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(center.getX() + 0.5, center.getY(), center.getZ() + 0.5) <= 80.0 * 80.0) { near = true; break; }
        }
        if (!near) return;
        Random r = new Random();
        // Liseré turquoise continu le long du bord de l'ovale (un peu en avant de la surface)
        for (int i = 0; i < 10; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double[] q = wd(RX * Math.cos(a), CY - 0.5 + RY * Math.sin(a), PLANE - 0.35);
            level.sendParticles(TEAL, q[0], q[1], q[2], 1, 0.04, 0.04, 0.04, 0);
        }
        // Particules aspirées vers le centre de l'ovale (comme le portail du Nether, mais vers l'intérieur)
        if (time % 2 == 0) {
            for (int i = 0; i < 5; i++) {
                double a = r.nextDouble() * Math.PI * 2, rad = 0.4 + r.nextDouble() * 0.6;
                double[] start = wd(RX * rad * Math.cos(a), CY - 0.5 + RY * rad * Math.sin(a), PLANE - 0.2);
                double[] off = wd(r.nextGaussian() * 1.2, CY - 0.5 + r.nextGaussian() * 1.8, PLANE - 2.5 - r.nextDouble() * 2.5);
                level.sendParticles(ParticleTypes.PORTAL, start[0], start[1], start[2], 0, off[0] - start[0], off[1] - start[1], off[2] - start[2], 1.0);
            }
        }
        // Quelques étincelles qui s'échappent vers l'extérieur
        if (time % 5 == 0) {
            for (int i = 0; i < 2; i++) {
                double[] from = wd((r.nextDouble() - 0.5) * 2 * RX * 0.8, CY - 0.5 + (r.nextDouble() - 0.5) * 2 * RY * 0.8, PLANE - 0.3);
                level.sendParticles(ParticleTypes.END_ROD, from[0], from[1], from[2], 0, -fx * 0.06 + (r.nextDouble() - 0.5) * 0.04, 0.03 + r.nextDouble() * 0.04, -fz * 0.06 + (r.nextDouble() - 0.5) * 0.04, 1.0);
            }
        }
        // La lueur « respire » : lumière lentement variable (une marche par demi-seconde)
        if (time % 10 == 0) {
            int g = (int) Math.round(3.5 + 3.5 * Math.sin(time * 0.045));
            if (g != glow) {
                glow = g;
                for (int[] c : plane) {
                    BlockPos pos = w(c[0], c[1], c[2]);
                    BlockState st = level.getBlockState(pos);
                    if (st.is(ModBlocks.RIFT_PORTAL.get())) level.setBlock(pos, st.setValue(RiftPortalBlock.GLOW, g), 2);
                }
            }
        }
    }

    /** Fait surgir les couches du rocher jusqu'à {@code upTo} (de bas en haut) : fondations, débris, joueurs repoussés. */
    private void reveal(int upTo) {
        while (revealed < upTo) {
            revealed++;
            for (int[] c : byY.get(revealed)) {
                set(c[0], c[1], c[2], rock(c[3]));
                if (revealed == 0 && c[3] != 9) foundation(c[0], c[2], c[3]);
            }
            if (revealed % 2 == 0) {
                BlockPos p = w(0, revealed, 2);
                level.sendParticles(ParticleTypes.POOF, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 14, 9, 0.5, 5, 0.05);
            }
        }
    }

    private void foundation(int lx, int lz, int type) {
        for (int k = 1; k <= 12; k++) {
            BlockPos p = w(lx, -k, lz);
            BlockState st = level.getBlockState(p);
            if (st.blocksMotion() && st.getFluidState().isEmpty()) break;
            level.setBlock(p, rock(type == 4 ? 3 : type), 2);
        }
    }

    /** La surface du portail s'étend du centre vers les bords (fente → ovale) ; les veines gagnent la roche autour. */
    private void fillPortal(double wFrac) {
        BlockState portal = ModBlocks.RIFT_PORTAL.get().defaultBlockState();
        for (int[] c : plane) {
            if (Math.abs(c[0]) <= wFrac * RX + 0.6) set(c[0], c[1], c[2], portal);
        }
        double limit = 1.05 + wFrac * 0.7;
        while (rimDone < rim.size() && rim.get(rimDone)[3] <= limit) {
            double[] r = rim.get(rimDone++);
            int lx = (int) r[0], y = (int) r[1], lz = (int) r[2];
            // Veines d'obsidienne pleureuse qui s'estompent en s'éloignant (le contour turquoise est fait de particules)
            if (r[3] < 1.4 || ((lx + y * 3 + lz) & 1) == 0) set(lx, y, lz, Blocks.CRYING_OBSIDIAN.defaultBlockState());
        }
    }

    /** Referme le portail : la surface et l'ovale redeviennent de la pierre (le rocher, les veines et les vrilles restent). */
    void seal() {
        for (int[] c : sealCells) set(c[0], c[1], c[2], rock(9));
        open = false;
        running = false;
    }

    // ─── Aides ───

    private void snd(SoundEvent e, float vol, float pitch) {
        level.playSound(null, center, e, SoundSource.BLOCKS, vol, pitch);
    }

    private void shake(int ticks, double intensity) {
        if (shake <= 0) return;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(center.getX() + 0.5, center.getY(), center.getZ() + 0.5) <= 48.0 * 48.0) ShakePacket.send(p, ticks, (float) (intensity * shake));
        }
    }

    private void announce() {
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(center.getX() + 0.5, center.getY(), center.getZ() + 0.5) > 48.0 * 48.0) continue;
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(WSLang.t("rift.opened_sub"))));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(WSLang.t("rift.opened_title"))));
        }
    }

    /** Pendant l'ouverture : on sort les joueurs de l'emprise du rocher et on repousse en douceur ceux qui sont devant la façade (sans dégâts). */
    private void pushPlayers() {
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            double dx = p.getX() - origin.getX() - 0.5, dz = p.getZ() - origin.getZ() - 0.5, dy = p.getY() - origin.getY();
            if (dy < -4 || dy > 24) continue;
            double lz = -(dx * fx + dz * fz), lx = dx * (-fz) + dz * fx;
            if (lz > -0.5 && lz < 14.5 && Math.abs(lx) < 15.5) {
                BlockPos to = w((int) Math.round(lx), 0, -5);
                int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, to.getX(), to.getZ());
                p.teleportTo(to.getX() + 0.5, gy, to.getZ() + 0.5);
                p.fallDistance = 0;
            } else if (lz <= -0.5 && lz > -7 && Math.abs(lx) < 16) {
                p.setDeltaMovement(p.getDeltaMovement().add(fx * 0.12, 0.02, fz * 0.12));
                p.hurtMarked = true;
            }
        }
    }
}
