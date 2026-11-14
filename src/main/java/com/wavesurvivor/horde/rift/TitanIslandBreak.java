package com.wavesurvivor.horde.rift;

import com.wavesurvivor.horde.kingdom.KingdomClaim;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 2.1 — CASSURE ORGANIQUE de l'île quand le Titan charge (fiche docs/faille/v2_fente_cassure/FICHE_FAILLE_V2_ET_CASSURE.md).
 * Préparée à l'annonce de la charge (contour et fissures tirés une fois, avec une graine propre à la charge), appliquée à l'impact :
 * <ul>
 *   <li>contour en « morsure » : taper(a) = max(0, 1 − (|a|/h)²) ; reach = depth × (0,7 + 1,1 × bruit1D(a)) × taper^0,45 + (bruit2D − 0,5) × 4 ;
 *       arrachées : taper &gt; 0 et distance ≥ R − reach (jamais à moins de 12 blocs du centre) ;</li>
 *   <li>bande qui s'effrite (4 blocs avant le nouveau bord) : trous, blocs de surface abîmés, affaissements, dessous déchiqueté ;</li>
 *   <li>3 à 5 fissures de magma qui rampent du nouveau bord vers le centre (suivies aussi par les particules de l'annonce) ;</li>
 *   <li>4 à 6 petits débris suspendus au-delà du nouveau bord.</li>
 * </ul>
 * Les blocs à entité (Monolithe, bâtiments) et les blocs posés par les joueurs ne sont jamais modifiés par la bande ni les fissures.
 */
final class TitanIslandBreak {

    private static final int SAFE = 12;          // rayon épargné autour du centre
    private static final int NOISE_CELLS = 30;   // cellules du bruit 1D sur 360°
    private static final Random RNG = new Random();
    private static final DustParticleOptions ORANGE = new DustParticleOptions(new Vector3f(1.0f, 0.55f, 0.1f), 1.5f);

    private final BlockPos c;
    private final int sr, depth;
    private final double axis, h;
    private final long seed;
    private final double[] n1 = new double[NOISE_CELLS];
    /** Fissures précalculées : trajets de colonnes (x, z), du nouveau bord vers le centre. */
    private final List<List<int[]>> cracks = new ArrayList<>();

    /**
     * @param impactX / impactZ point du bord visé par la charge (axe de la morsure)
     * @param halfWidth demi-largeur (blocs) de la zone de charge ; h = son angle vu du centre × 1,2
     */
    TitanIslandBreak(BlockPos center, int siegeRadius, int depth, double impactX, double impactZ, double halfWidth) {
        this.c = center;
        this.sr = siegeRadius;
        this.depth = depth;
        this.axis = Math.atan2(impactX - (center.getX() + 0.5), impactZ - (center.getZ() + 0.5));
        this.h = 1.2 * Math.asin(Math.min(1.0, halfWidth / Math.max(1, siegeRadius)));
        this.seed = RNG.nextLong();
        Random r = new Random(seed);
        for (int i = 0; i < NOISE_CELLS; i++) n1[i] = r.nextDouble();
        planCracks(r);
    }

    // ─── Contour ───

    /** Angle de la colonne autour du centre, relatif à l'axe de la charge, dans [−π, π]. */
    private double angle(double x, double z) {
        double a = Math.atan2(x - c.getX(), z - c.getZ()) - axis;
        while (a > Math.PI) a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }

    private double taper(double a) {
        double k = Math.abs(a) / h;
        return Math.max(0, 1 - k * k);
    }

    private static double smooth(double t) { return t * t * (3 - 2 * t); }

    /** Bruit de valeur lissé à 30 cellules sur 360°, dans [0, 1]. */
    private double noise1(double a) {
        double t = (a + Math.PI) / (2 * Math.PI) * NOISE_CELLS;
        int i = (int) Math.floor(t);
        double f = smooth(t - i);
        double v0 = n1[Math.floorMod(i, NOISE_CELLS)], v1 = n1[Math.floorMod(i + 1, NOISE_CELLS)];
        return v0 + (v1 - v0) * f;
    }

    /** Valeur pseudo-aléatoire [0, 1) d'un nœud de la grille (graine de la charge). */
    private double hash(int gx, int gz) {
        long k = seed ^ (gx * 0x9E3779B97F4A7C15L) ^ (gz * 0xC2B2AE3D27D4EB4FL);
        k = (k ^ (k >>> 30)) * 0xBF58476D1CE4E5B9L;
        k = (k ^ (k >>> 27)) * 0x94D049BB133111EBL;
        k ^= k >>> 31;
        return (k >>> 11) * 0x1.0p-53;
    }

    /** Bruit de valeur 2D à cellules de 4 blocs, dans [0, 1]. */
    private double noise2(double x, double z) {
        double gx = x / 4.0, gz = z / 4.0;
        int ix = (int) Math.floor(gx), iz = (int) Math.floor(gz);
        double fx = smooth(gx - ix), fz = smooth(gz - iz);
        double a = hash(ix, iz), b = hash(ix + 1, iz), d = hash(ix, iz + 1), e = hash(ix + 1, iz + 1);
        double top = a + (b - a) * fx, bot = d + (e - d) * fx;
        return top + (bot - top) * fz;
    }

    private double reach(double a, double x, double z) {
        double tp = taper(a);
        if (tp <= 0) return -1e9;
        return depth * (0.7 + 1.1 * noise1(a)) * Math.pow(tp, 0.45) + (noise2(x, z) - 0.5) * 4;
    }

    /** Distance au centre du nouveau bord dans la direction a (le bruit 2D est relu au point estimé). */
    private double edgeAt(double a) {
        double dir = axis + a, d = sr - depth;
        for (int k = 0; k < 2; k++) d = sr - reach(a, c.getX() + Math.sin(dir) * d, c.getZ() + Math.cos(dir) * d);
        return d;
    }

    // ─── Fissures : marche aléatoire du nouveau bord vers le centre ───

    private void planCracks(Random r) {
        int n = 3 + r.nextInt(3);
        for (int i = 0; i < n; i++) {
            double a0 = (r.nextDouble() * 2 - 1) * h * 0.7;
            double start = edgeAt(a0) - 1;
            if (start <= SAFE + 2) continue;
            double dir = axis + a0;
            double px = c.getX() + 0.5 + Math.sin(dir) * start, pz = c.getZ() + 0.5 + Math.cos(dir) * start;
            double heading = Math.atan2(c.getX() + 0.5 - px, c.getZ() + 0.5 - pz);   // cap initial vers le centre
            int len = 10 + r.nextInt(13);
            List<int[]> path = new ArrayList<>();
            Set<Long> seen = new HashSet<>();
            for (int s = 0; s < len; s++) {
                int bx = (int) Math.floor(px), bz = (int) Math.floor(pz);
                if (Math.hypot(bx - c.getX(), bz - c.getZ()) < SAFE) break;
                if (seen.add(BlockPos.asLong(bx, 0, bz))) path.add(new int[]{bx, bz});
                heading += r.nextGaussian() * 0.35;
                px += Math.sin(heading);
                pz += Math.cos(heading);
                // la fissure ne repart jamais dans la partie arrachée
                double d = Math.hypot(px - c.getX() - 0.5, pz - c.getZ() - 0.5);
                if (taper(angle(px, pz)) > 0 && d >= edgeAt(angle(px, pz))) break;
            }
            if (path.size() >= 3) cracks.add(path);
        }
    }

    /** Annonce de la charge : les fissures se dessinent progressivement (progress 0 → 1) en particules orange. */
    void telegraph(ServerLevel l, double progress) {
        for (List<int[]> path : cracks) {
            int shown = (int) Math.ceil(path.size() * Math.max(0.15, Math.min(1, progress)));
            for (int i = 0; i < shown; i++) {
                int[] p = path.get(i);
                int top = surface(l, p[0], p[1]);
                if (top == Integer.MIN_VALUE) continue;
                l.sendParticles(ORANGE, p[0] + 0.5, top + 1.05, p[1] + 0.5, 1, 0.25, 0, 0.25, 0);
            }
        }
        // et quelques éclats le long du futur bord
        for (int i = 0; i < 8; i++) {
            double a = (RNG.nextDouble() * 2 - 1) * h * 0.9, e = edgeAt(a), dir = axis + a;
            if (e < SAFE) continue;
            int x = (int) Math.floor(c.getX() + 0.5 + Math.sin(dir) * e), z = (int) Math.floor(c.getZ() + 0.5 + Math.cos(dir) * e);
            int top = surface(l, x, z);
            if (top == Integer.MIN_VALUE) continue;
            BlockState st = l.getBlockState(new BlockPos(x, top, z));
            l.sendParticles(ORANGE, x + 0.5, top + 1.05, z + 0.5, 2, 0.4, 0, 0.4, 0);
            l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), x + 0.5, top + 1.1, z + 0.5, 3, 0.3, 0.1, 0.3, 0.05);
        }
    }

    // ─── Application à l'impact ───

    /**
     * Arrache la morsure (du bord vers l'intérieur), puis effrite la bande, trace les fissures et laisse les débris.
     * @return emprise {x0, x1, z0, z1} des colonnes arrachées (pour les dégâts aux bâtiments), ou null si rien n'est arraché
     */
    double[] execute(ServerLevel l, int[] falling) {
        List<double[]> cut = new ArrayList<>();
        List<double[]> band = new ArrayList<>();
        int r = sr + 4;
        for (int x = c.getX() - r; x <= c.getX() + r; x++) {
            for (int z = c.getZ() - r; z <= c.getZ() + r; z++) {
                double d = Math.hypot(x - c.getX(), z - c.getZ());
                if (d < SAFE || d > r) continue;
                double a = angle(x + 0.5, z + 0.5), tp = taper(a);
                if (tp <= 0) continue;
                double edge = sr - reach(a, x, z);
                if (d >= edge) cut.add(new double[]{x, z, d});
                else if (d >= edge - 4 && tp > 0.05) {
                    if (RNG.nextDouble() < 0.22) cut.add(new double[]{x, z, d});   // trous dans la bande
                    else band.add(new double[]{x, z, d});
                }
            }
        }
        if (cut.isEmpty()) return null;
        double span = 1;
        for (double[] col : cut) span = Math.max(span, r - col[2]);
        double[] box = {Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE};
        int last = 0;
        for (double[] col : cut) {
            int x = (int) col[0], z = (int) col[1];
            int delay = 4 + (int) Math.round((r - col[2]) / (span + 1) * 40) + RNG.nextInt(4);   // du bord vers l'intérieur
            last = Math.max(last, delay);
            RiftManager.later(l.getServer(), delay, () -> RiftTitan.collapseColumn(l, x, z, falling));
            box[0] = Math.min(box[0], x);
            box[1] = Math.max(box[1], x + 1);
            box[2] = Math.min(box[2], z);
            box[3] = Math.max(box[3], z + 1);
        }
        for (double[] col : band) {
            int x = (int) col[0], z = (int) col[1];
            int delay = 10 + (int) Math.round((r - col[2]) / (span + 1) * 40) + RNG.nextInt(6);
            last = Math.max(last, delay);
            RiftManager.later(l.getServer(), delay, () -> crumble(l, x, z));
        }
        RiftManager.later(l.getServer(), last + 6, () -> applyCracks(l));
        RiftManager.later(l.getServer(), last + 12, () -> debris(l));
        return box;
    }

    /** Colonne de la bande : bloc de surface abîmé, parfois affaissée, dessous déchiqueté (surplombs et pointes). */
    private void crumble(ServerLevel l, int x, int z) {
        int top = surface(l, x, z);
        if (top == Integer.MIN_VALUE) return;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, top, z);
        if (blocked(l, m)) return;
        if (RNG.nextDouble() < 0.30) {   // affaissement de 1 à 3 blocs
            int k = 1 + RNG.nextInt(3);
            for (int i = 0; i < k && top > c.getY() - 12; i++, top--) {
                m.setY(top);
                if (blocked(l, m)) break;
                l.setBlock(m, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        m.setY(top);
        if (!l.getBlockState(m).isAir() && !blocked(l, m)) {
            double p = RNG.nextDouble();
            BlockState st = p < 0.40 ? Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState()
                    : p < 0.70 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState()
                    : p < 0.85 ? Blocks.GRAVEL.defaultBlockState()
                    : Blocks.BLACKSTONE.defaultBlockState();
            l.setBlock(m, st, 2);
        }
        // dessous déchiqueté : 2 à 10 blocs retirés au bas du cône, sans jamais approcher la surface (3 blocs gardés)
        int bottom = Integer.MIN_VALUE;
        for (int y = c.getY() - 45; y < top - 3; y++) {
            m.setY(y);
            if (!l.getBlockState(m).isAir()) { bottom = y; break; }
        }
        if (bottom != Integer.MIN_VALUE) {
            int k = 2 + RNG.nextInt(9);
            for (int y = bottom; y < bottom + k && y < top - 3; y++) {
                m.setY(y);
                if (!blocked(l, m)) l.setBlock(m, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        if (RNG.nextInt(4) == 0) {
            BlockState st = l.getBlockState(new BlockPos(x, top, z));
            if (!st.isAir()) l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), x + 0.5, top + 1, z + 0.5, 6, 0.3, 0.2, 0.3, 0.05);
        }
    }

    /** Les fissures deviennent des veines de magma (et le bloc dessous dans 30 % des cas). */
    private void applyCracks(ServerLevel l) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (List<int[]> path : cracks) {
            for (int[] p : path) {
                if (Math.hypot(p[0] - c.getX(), p[1] - c.getZ()) < SAFE) continue;
                int top = surface(l, p[0], p[1]);
                if (top == Integer.MIN_VALUE) continue;
                m.set(p[0], top, p[1]);
                if (blocked(l, m)) continue;
                l.setBlock(m, Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
                if (RNG.nextDouble() < 0.30) {
                    m.setY(top - 1);
                    if (!l.getBlockState(m).isAir() && !blocked(l, m)) l.setBlock(m, Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
                }
                if (RNG.nextInt(3) == 0) l.sendParticles(ParticleTypes.LAVA, p[0] + 0.5, top + 1.1, p[1] + 0.5, 1, 0.2, 0, 0.2, 0);
            }
        }
        if (!cracks.isEmpty()) {
            int[] p = cracks.get(0).get(0);
            l.playSound(null, new BlockPos(p[0], c.getY(), p[1]), SoundEvents.BASALT_BREAK, SoundSource.BLOCKS, 3f, 0.5f);
        }
    }

    /** 4 à 6 petits rochers (2 à 6 blocs) suspendus 3 à 10 blocs au-delà du nouveau bord, entre −2 et +1 par rapport à la surface. */
    private void debris(ServerLevel l) {
        BlockState[] mats = {Blocks.DEEPSLATE.defaultBlockState(), Blocks.COBBLED_DEEPSLATE.defaultBlockState(), Blocks.TUFF.defaultBlockState()};
        Direction[] dirs = Direction.values();
        int n = 4 + RNG.nextInt(3);
        for (int i = 0; i < n; i++) {
            double a = (RNG.nextDouble() * 2 - 1) * h * 0.8;
            double d = edgeAt(a) + 3 + RNG.nextInt(8), dir = axis + a;
            BlockPos start = BlockPos.containing(c.getX() + 0.5 + Math.sin(dir) * d, c.getY() - 2 + RNG.nextInt(4), c.getZ() + 0.5 + Math.cos(dir) * d);
            List<BlockPos> rock = new ArrayList<>();
            int size = 2 + RNG.nextInt(5);
            BlockState mat = mats[RNG.nextInt(mats.length)];
            if (!l.getBlockState(start).isAir()) continue;
            l.setBlock(start, mat, 2);
            rock.add(start);
            for (int tries = 0; rock.size() < size && tries < 20; tries++) {
                BlockPos p = rock.get(RNG.nextInt(rock.size())).relative(dirs[RNG.nextInt(dirs.length)]);
                if (!l.getBlockState(p).isAir()) continue;
                l.setBlock(p, RNG.nextInt(3) == 0 ? mats[RNG.nextInt(mats.length)] : mat, 2);
                rock.add(p);
            }
        }
    }

    // ─── Outils ───

    /**
     * Bloc de surface naturel de la colonne (premier bloc solide en descendant), ou MIN_VALUE s'il n'y en a pas ou si quelque chose
     * est construit dessus (le premier bloc trouvé est trop haut : mur, tour…).
     */
    private int surface(ServerLevel l, int x, int z) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = c.getY() + 16; y >= c.getY() - 12; y--) {
            m.set(x, y, z);
            BlockState st = l.getBlockState(m);
            if (st.isAir() || st.canBeReplaced() || !st.getFluidState().isEmpty()) continue;
            return y > c.getY() + 4 ? Integer.MIN_VALUE : y;
        }
        return Integer.MIN_VALUE;
    }

    /** Blocs jamais modifiés : blocs à entité (Monolithe, bâtiments), blocs posés par les joueurs, blocs incassables. */
    private static boolean blocked(ServerLevel l, BlockPos p) {
        BlockState st = l.getBlockState(p);
        return st.getBlock() instanceof EntityBlock || l.getBlockEntity(p) != null || KingdomClaim.isPlaced(p) || st.getDestroySpeed(l, p) < 0;
    }
}
