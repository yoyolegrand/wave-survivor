package com.wavesurvivor.horde.quake;

import com.wavesurvivor.horde.skill.DelayedActionScheduler;
import com.wavesurvivor.horde.skill.IceFx;
import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.network.ShakePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * SÉISME (1.7) — événement de chaos réutilisable : le sol gronde (avertissement), puis pendant quelques secondes
 * la zone tremble : secousses qui font vaciller les joueurs, secousse de caméra, fissures qui courent sur le sol
 * (dégâts au passage) et éboulis qui tombent du ciel (dégâts à l'impact). Tout est visuel (blocs « display », jamais
 * de modification du terrain). Aucun abri ni relique ne protège : il faut regarder où l'on met les pieds.
 * Réglé dans l'éditeur (type de chaos « seisme »).
 */
public class QuakeManager {

    /** Réglages d'un séisme. */
    public static final class Params {
        public double radius = 40;
        public int warningTicks = 80;
        public int durationTicks = 160;
        /** Dégâts d'une fissure (60 %) ou d'un éboulis (100 %). */
        public double damage = 3.0;
        /** Force des secousses (vitesse ajoutée toutes les 4 ticks). */
        public double jolt = 0.35;
        /** Nombre de fissures sur la durée, et éboulis par seconde. */
        public int cracks = 5;
        public int rubble = 6;
        /** Intensité de la secousse de caméra (0 = aucune). */
        public double shake = 1.0;
        public String crackBlock = "minecraft:black_concrete";
        public String rubbleBlock = "minecraft:cobblestone";
    }

    private static final class Quake {
        ServerLevel level;
        Vec3 center;
        Params p;
        long startAt, endAt, nextCrack;
        boolean begun;
        BlockState crack, rubble;
    }

    private static final List<Quake> QUAKES = new ArrayList<>();
    private static final Random RNG = new Random();

    /** Lance un séisme centré sur {@code center}. */
    public static void start(ServerLevel level, Vec3 center, Params p) {
        if (level == null || center == null || p == null) return;
        Quake q = new Quake();
        q.level = level;
        q.center = center;
        q.p = p;
        long now = level.getServer().getTickCount();
        q.startAt = now + Math.max(0, p.warningTicks);
        q.endAt = q.startAt + Math.max(20, p.durationTicks);
        q.crack = state(p.crackBlock, Blocks.BLACK_CONCRETE);
        q.rubble = state(p.rubbleBlock, Blocks.COBBLESTONE);
        QUAKES.add(q);
    }

    public static boolean active() { return !QUAKES.isEmpty(); }

    /** Fin de horde : plus aucun séisme. */
    public static void clearAll() { QUAKES.clear(); }

    private static BlockState state(String id, Block fallback) {
        try {
            Block b = BuiltInRegistries.BLOCK.get(new ResourceLocation(id == null ? "" : id.trim()));
            if (b != null && b != Blocks.AIR) return b.defaultBlockState();
        } catch (Exception ignored) {}
        return fallback.defaultBlockState();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || QUAKES.isEmpty()) return;
        long now = e.getServer().getTickCount();
        Iterator<Quake> it = QUAKES.iterator();
        while (it.hasNext()) if (!tick(it.next(), now)) it.remove();
    }

    private static List<ServerPlayer> inZone(Quake q) {
        double r2 = q.p.radius * q.p.radius;
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : q.level.players()) {
            if (!p.isAlive() || p.isSpectator()) continue;
            double dx = p.getX() - q.center.x, dz = p.getZ() - q.center.z;
            if (dx * dx + dz * dz <= r2) out.add(p);
        }
        return out;
    }

    private static boolean tick(Quake q, long now) {
        ServerLevel level = q.level;
        MinecraftServer srv = level.getServer();

        // ─── Avertissement : le sol gronde ───
        if (now < q.startAt) {
            if ((q.startAt - now) % 20 == 0) {
                int sec = (int) ((q.startAt - now) / 20);
                for (ServerPlayer p : inZone(q)) {
                    p.displayClientMessage(Component.literal(WSLang.t("quake.warning", sec)), true);
                    level.playSound(null, p.blockPosition(), SoundEvents.GRAVEL_BREAK, SoundSource.AMBIENT, 1.0f, 0.5f);
                }
            }
            return true;
        }
        List<ServerPlayer> players = inZone(q);
        if (!q.begun) {
            q.begun = true;
            q.nextCrack = now;
            for (ServerPlayer p : players) {
                p.sendSystemMessage(Component.literal(WSLang.t("quake.start")));
                if (q.p.shake > 0) ShakePacket.send(p, Math.max(20, q.p.durationTicks), (float) q.p.shake);
            }
        }
        if (now >= q.endAt) {
            for (ServerPlayer p : players) p.sendSystemMessage(Component.literal(WSLang.t("quake.end")));
            return false;
        }

        // ─── Secousses : les joueurs au sol vacillent ───
        if (now % 4 == 0) {
            for (ServerPlayer p : players) {
                if (!p.onGround() || p.isCreative()) continue;
                double a = RNG.nextDouble() * Math.PI * 2, j = q.p.jolt * (0.5 + RNG.nextDouble() * 0.5);
                p.setDeltaMovement(p.getDeltaMovement().add(Math.cos(a) * j, 0.10 + 0.1 * RNG.nextDouble(), Math.sin(a) * j));
                p.hurtMarked = true;
            }
        }
        if (now % 6 == 0) {
            for (ServerPlayer p : players) {
                BlockState below = level.getBlockState(p.blockPosition().below());
                if (below.isAir()) below = Blocks.STONE.defaultBlockState();
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), p.getX(), p.getY() + 0.1, p.getZ(), 16, 1.2, 0.1, 1.2, 0.2);
                level.playSound(null, p.blockPosition(), SoundEvents.GRAVEL_BREAK, SoundSource.AMBIENT, 1.0f, 0.5f + RNG.nextFloat() * 0.3f);
            }
        }

        // ─── Éboulis (par seconde) et fissures (réparties sur la durée) ───
        if (!players.isEmpty()) {
            if (now % 20 == 0) {
                for (int i = 0; i < Math.max(0, q.p.rubble); i++) rubble(level, srv, players.get(RNG.nextInt(players.size())), q);
            }
            if (now >= q.nextCrack) {
                crack(level, srv, players.get(RNG.nextInt(players.size())), q);
                q.nextCrack = now + Math.max(10, q.p.durationTicks / Math.max(1, q.p.cracks));
            }
        }
        return true;
    }

    private static int groundY(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    /** Un rocher tombe du ciel près d'un joueur : annoncé par de la poussière, dégâts à l'impact. */
    private static void rubble(ServerLevel level, MinecraftServer srv, ServerPlayer near, Quake q) {
        int x = (int) Math.floor(near.getX() + (RNG.nextDouble() - 0.5) * 14);
        int z = (int) Math.floor(near.getZ() + (RNG.nextDouble() - 0.5) * 14);
        int gy = groundY(level, x, z);
        double cx = x + 0.5, cz = z + 0.5;
        Display.BlockDisplay d = IceFx.spawn(level, cx, gy, cz, q.rubble, new Quaternionf(), 0.8f, 0.8f, 0.8f, 9f);
        if (d == null) return;
        IceFx.animate(d, new Quaternionf(), 0.8f, 0.8f, 0.8f, 0f, 9);
        level.sendParticles(ParticleTypes.SMOKE, cx, gy + 0.2, cz, 6, 0.4, 0.05, 0.4, 0.01);
        DelayedActionScheduler.schedule(srv, 9, () -> {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, q.rubble), cx, gy + 0.4, cz, 30, 0.5, 0.3, 0.5, 0.15);
            level.playSound(null, new BlockPos(x, gy, z), SoundEvents.GENERIC_EXPLODE, SoundSource.AMBIENT, 0.5f, 1.4f);
            for (ServerPlayer p : level.players()) {
                if (!p.isAlive() || p.isSpectator() || Math.abs(p.getY() - gy) > 2.5) continue;
                double dx = p.getX() - cx, dz = p.getZ() - cz;
                if (dx * dx + dz * dz <= 1.6 * 1.6) p.hurt(level.damageSources().generic(), (float) q.p.damage);
            }
            DelayedActionScheduler.schedule(srv, 4, () -> IceFx.remove(d), "quake rubble clean");
        }, "quake rubble");
    }

    /** Une fissure court sur le sol vers le joueur (séisme). */
    private static void crack(ServerLevel level, MinecraftServer srv, ServerPlayer target, Quake q) {
        double a = RNG.nextDouble() * Math.PI * 2, dist = 4 + RNG.nextDouble() * 6;
        double sx = target.getX() + Math.cos(a) * dist, sz = target.getZ() + Math.sin(a) * dist;
        double dir = Math.atan2(target.getZ() - sz, target.getX() - sx) + (RNG.nextDouble() - 0.5) * 0.7;
        crackLine(level, srv, sx, sz, dir, 12, q.p.damage * 0.6, q.crack);
    }

    /**
     * Une fissure qui se FAUFILE dans le sol depuis (sx, sz), globalement dans la direction {@code dir} (radians) :
     * tracé en zigzag qui s'affine vers le bout, avec des branches qui partent sur les côtés (et des sous-branches).
     * Elle s'ouvre segment après segment (un tous les 2 ticks) ; blesse et soulève une seule fois chaque joueur qu'elle
     * traverse. Visuelle seulement (blocs « display », le terrain n'est jamais modifié).
     * @param length portée approximative de la fissure principale, en blocs
     */
    public static void crackLine(ServerLevel level, MinecraftServer srv, double sx, double sz, double dir, int length,
                                 double damage, BlockState crackState) {
        List<double[]> segs = new ArrayList<>();
        buildCrack(segs, sx, sz, dir, Math.max(4, (int) Math.round(length * 0.8)), 0.8, 0, 0);
        java.util.Set<java.util.UUID> hit = new java.util.HashSet<>();
        for (double[] s : segs) {
            final double x1 = s[0], z1 = s[1], x2 = s[2], z2 = s[3], width = s[4];
            DelayedActionScheduler.schedule(srv, (int) s[5], () -> {
                double mx = (x1 + x2) / 2, mz = (z1 + z2) / 2;
                double ang = Math.atan2(z2 - z1, x2 - x1);
                float segLen = (float) Math.hypot(x2 - x1, z2 - z1);
                int gy = groundY(level, (int) Math.floor(mx), (int) Math.floor(mz));
                Quaternionf rot = new Quaternionf().rotateY(-(float) ang);
                // La fissure : dalle sombre fine, un peu plus longue que le segment pour que les morceaux se recouvrent
                Display.BlockDisplay d = IceFx.spawn(level, mx, gy + 0.02, mz, crackState, rot, segLen + 0.45f, 0.05f, (float) width, 0f);
                // Éclats posés de travers sur les bords
                Display.BlockDisplay chip = null;
                if (RNG.nextDouble() < 0.4) {
                    double off = (RNG.nextDouble() - 0.5) * (width + 0.7);
                    double cx = mx - Math.sin(ang) * off, cz = mz + Math.cos(ang) * off;
                    float size = 0.18f + RNG.nextFloat() * 0.2f;
                    Quaternionf tilt = new Quaternionf().rotateY(RNG.nextFloat() * 6.28f).rotateX((RNG.nextFloat() - 0.5f) * 0.9f);
                    chip = IceFx.spawn(level, cx, groundY(level, (int) Math.floor(cx), (int) Math.floor(cz)) + 0.02, cz,
                            crackState, tilt, size, size, size, 0f);
                }
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), mx, gy + 0.2, mz, 6, 0.3, 0.1, 0.3, 0.1);
                for (ServerPlayer p : level.players()) {
                    if (!p.isAlive() || p.isSpectator() || p.getY() < gy - 1 || p.getY() > gy + 2 || hit.contains(p.getUUID())) continue;
                    if (distToSegment(p.getX(), p.getZ(), x1, z1, x2, z2) > width / 2 + 0.9) continue;
                    hit.add(p.getUUID());
                    p.hurt(level.damageSources().generic(), (float) damage);
                    p.setDeltaMovement(p.getDeltaMovement().add(0, 0.45, 0));
                    p.hurtMarked = true;
                }
                final Display.BlockDisplay d1 = d, d2 = chip;
                DelayedActionScheduler.schedule(srv, 50, () -> { IceFx.remove(d1); IceFx.remove(d2); }, "quake crack clean");
            }, "quake crack");
        }
    }

    /**
     * Génère les segments d'une fissure : {x1, z1, x2, z2, largeur, délai (ticks), profondeur de branche}.
     * Le cap dérive au hasard autour de la direction de départ (zigzag), la largeur diminue vers le bout, et des branches
     * (plus courtes, plus fines) partent sur les côtés — jusqu'à 2 niveaux.
     */
    private static void buildCrack(List<double[]> segs, double x, double z, double baseHeading, int steps, double width0,
                                   int startDelay, int depth) {
        double wander = 0;
        for (int i = 0; i < steps; i++) {
            wander = wander * 0.8 + (RNG.nextDouble() - 0.5) * 1.0; // dérive qui revient vers la direction de départ
            double heading = baseHeading + Math.max(-0.7, Math.min(0.7, wander));
            double len = 0.9 + RNG.nextDouble() * 0.9;
            double nx = x + Math.cos(heading) * len, nz = z + Math.sin(heading) * len;
            double width = width0 * (1.0 - 0.7 * i / steps) * (0.8 + RNG.nextDouble() * 0.4);
            int delay = startDelay + i * 2;
            segs.add(new double[]{x, z, nx, nz, Math.max(0.12, width), delay, depth});
            if (depth < 2 && i >= 2 && i < steps - 2 && RNG.nextDouble() < 0.3) {
                double side = (RNG.nextBoolean() ? 1 : -1) * (0.55 + RNG.nextDouble() * 0.6);
                buildCrack(segs, nx, nz, heading + side, Math.max(3, (int) Math.round((steps - i) * 0.55)), width * 0.65, delay + 2, depth + 1);
            }
            x = nx;
            z = nz;
        }
    }

    /** Distance du point (px, pz) au segment [(x1, z1), (x2, z2)]. */
    private static double distToSegment(double px, double pz, double x1, double z1, double x2, double z2) {
        double dx = x2 - x1, dz = z2 - z1;
        double l2 = dx * dx + dz * dz;
        double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - x1) * dx + (pz - z1) * dz) / l2));
        double cx = x1 + t * dx, cz = z1 + t * dz;
        return Math.hypot(px - cx, pz - cz);
    }

    /** Hauteur du sol (pour les compétences qui posent des effets). */
    public static int ground(ServerLevel level, double x, double z) {
        return groundY(level, (int) Math.floor(x), (int) Math.floor(z));
    }
}
