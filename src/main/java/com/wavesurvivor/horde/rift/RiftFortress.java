package com.wavesurvivor.horde.rift;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * 2.0 (jalons 3-4) — LA FORTERESSE ENNEMIE de la Faille : île agrandie par anneaux, plateforme reliée à la base par un pont,
 * muraille à porte, tours, donjon ; bouclier pendant le Siège ; ressources des calmes ; catalyseurs et cœur de la Contre-offensive.
 * Provisoire côté visuel (blocs vanilla) : plusieurs formes de forteresse viendront avec les visuels du Chat.
 */
final class RiftFortress {

    /** Rayon de la plateforme de la forteresse (×3 par rapport à la première version). */
    static final int R = 39;
    /** Longueur du pont entre le bord de l'île et la plateforme. */
    static final int GAP = 33;
    private static final Random RNG = new Random();
    private static final DustParticleOptions VIOLET = new DustParticleOptions(new org.joml.Vector3f(0.65f, 0.2f, 0.95f), 1.3f);
    private static final DustParticleOptions TEAL = new DustParticleOptions(new org.joml.Vector3f(0.1f, 0.95f, 0.85f), 1.3f);

    /** Cibles à PV (catalyseurs, cœur) : cristaux invulnérables dont les dégâts sont gérés ici. {PV, PV max}. */
    static final Map<UUID, float[]> TARGETS = new HashMap<>();
    static final Map<UUID, String> LABELS = new HashMap<>();

    private RiftFortress() {}

    static BlockPos center(int siegeRadius) {
        BlockPos c = RiftManager.CENTER;
        return new BlockPos(c.getX() + siegeRadius + GAP + R, c.getY(), c.getZ());
    }

    /** Devant la porte de la forteresse : les ennemis y apparaissent. */
    static BlockPos spawnPoint(int siegeRadius) {
        BlockPos c = center(siegeRadius);
        return new BlockPos(c.getX() - 24, c.getY() + 1, c.getZ());
    }

    /** Début du pont, côté forteresse : les joueurs refoulés par le bouclier sont replacés ici. */
    static BlockPos bridgeHead(int siegeRadius) {
        BlockPos c = center(siegeRadius);
        return new BlockPos(c.getX() - R - 4, c.getY() + 1, c.getZ());
    }

    // ─── Île ───

    /** Anneau d'île entre {@code from} (exclu, la bordure précédente est refaite) et {@code to} : l'île grandit. */
    static void islandRing(ServerLevel l, int from, int to) {
        BlockState tileA = Blocks.POLISHED_DEEPSLATE.defaultBlockState(), tileB = Blocks.DEEPSLATE_TILES.defaultBlockState();
        BlockState rock = Blocks.DEEPSLATE.defaultBlockState(), edge = Blocks.OBSIDIAN.defaultBlockState();
        BlockPos c = RiftManager.CENTER;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -to; dx <= to; dx++) {
            for (int dz = -to; dz <= to; dz++) {
                double d = Math.hypot(dx, dz);
                if (d <= from - 2 || d > to + 0.5) continue;
                int depth = 3 + (int) Math.round((1 - d / to) * 6);
                for (int k = 0; k < depth; k++) {
                    BlockState st = k == 0 ? (d > to - 1.5 ? edge : ((dx + dz) & 1) == 0 ? tileA : tileB) : rock;
                    l.setBlock(m.set(c.getX() + dx, c.getY() - k, c.getZ() + dz), st, 2);
                }
            }
        }
    }

    // ─── Forteresse ───

    private static void set(ServerLevel l, int x, int y, int z, BlockState st) {
        l.setBlock(new BlockPos(x, y, z), st, 2);
    }

    /** Pont (largeur 15), plateforme de rayon 39, muraille à porte côté base (-X), 4 grosses tours et donjon 21×21 (tout ×3). */
    static void build(ServerLevel l, int sr) {
        BlockPos c = center(sr);
        int cx = c.getX(), cz = c.getZ(), y = c.getY();
        BlockState bricks = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), cracked = Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState rock = Blocks.BLACKSTONE.defaultBlockState(), edge = Blocks.OBSIDIAN.defaultBlockState();
        BlockState wall = Blocks.BLACKSTONE_WALL.defaultBlockState();
        BlockState cry = Blocks.CRYING_OBSIDIAN.defaultBlockState(), gild = Blocks.GILDED_BLACKSTONE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        // Pont (15 de large) de l'île à la plateforme, avec parapets et piliers
        for (int x = RiftManager.CENTER.getX() + sr - 1; x <= cx - R + 1; x++) {
            for (int dz = -7; dz <= 7; dz++) {
                set(l, x, y, cz + dz, ((x + dz) & 1) == 0 ? bricks : cracked);
                set(l, x, y - 1, cz + dz, rock);
                set(l, x, y - 2, cz + dz, rock);
                if (Math.abs(dz) == 7) {
                    set(l, x, y + 1, cz + dz, wall);
                    if ((x & 7) == 0) { set(l, x, y + 2, cz + dz, wall); set(l, x, y + 3, cz + dz, cry); }
                }
            }
        }
        // Plateforme
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > R + 0.5) continue;
                int depth = 3 + (int) Math.round((1 - d / R) * 12);
                for (int k = 0; k < depth; k++) {
                    BlockState st = k == 0 ? (d > R - 1.5 ? edge : ((dx + dz) & 1) == 0 ? bricks : cracked) : (RNG.nextInt(14) == 0 ? gild : rock);
                    set(l, cx + dx, y - k, cz + dz, st);
                }
            }
        }
        // Muraille (rayon ~31, hauteur 15 + créneaux) avec une porte de 13 de large côté base (-X)
        for (int dx = -34; dx <= 34; dx++) {
            for (int dz = -34; dz <= 34; dz++) {
                double d = Math.hypot(dx, dz);
                if (d < 29.4 || d > 33.6) continue;
                if (dx < -20 && Math.abs(dz) <= 6) continue; // la porte
                for (int h = 1; h <= 15; h++) set(l, cx + dx, y + h, cz + dz, (h == 8 || h == 9) && ((dx + dz) & 3) == 0 ? cry : bricks);
                if (((dx + dz) & 1) == 0) set(l, cx + dx, y + 16, cz + dz, bricks);
            }
        }
        // 4 grosses tours (9×9, haut de 27)
        for (int sx : new int[]{-1, 1}) for (int sz : new int[]{-1, 1}) {
            for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
                for (int h = 1; h <= 27; h++) set(l, cx + sx * 21 + dx, y + h, cz + sz * 21 + dz, bricks);
                set(l, cx + sx * 21 + dx, y + 28, cz + sz * 21 + dz, cry);
            }
        }
        // Donjon 21×21 creux (haut de 30), ouverture côté base, cadre d'obsidienne pleureuse
        for (int dx = -10; dx <= 10; dx++) for (int dz = -10; dz <= 10; dz++) {
            boolean shell = Math.abs(dx) == 10 || Math.abs(dz) == 10;
            for (int h = 1; h <= 30; h++) {
                if (h == 30) set(l, cx + dx, y + h, cz + dz, bricks);
                else set(l, cx + dx, y + h, cz + dz, shell ? bricks : air);
            }
        }
        for (int dz = -3; dz <= 3; dz++) for (int h = 1; h <= 14; h++) set(l, cx - 10, y + h, cz + dz, air);
        for (int dz = -4; dz <= 4; dz++) for (int h = 1; h <= 15; h++) {
            boolean frame = Math.abs(dz) == 4 || h == 15;
            if (frame) set(l, cx - 10, y + h, cz + dz, cry);
        }
        set(l, cx, y + 29, cz, Blocks.SOUL_LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
    }

    // ─── Bouclier du Siège ───

    /** Particules du dôme et refoulement des joueurs : on ne peut ni entrer dans la forteresse ni l'atteindre tant que le bouclier tient. */
    static void shield(ServerLevel l, int sr, long time) {
        BlockPos c = center(sr);
        double cx = c.getX() + 0.5, cz = c.getZ() + 0.5;
        if (time % 10 == 0) {
            for (int ring = 0; ring < 6; ring++) {
                double y = c.getY() + 1 + ring * 6;
                double rr = (R + 1.8) * Math.sqrt(Math.max(0.2, 1 - sq(ring / 6.5)));
                for (int i = 0; i < 56; i++) {
                    double a = (i / 56.0) * Math.PI * 2 + time * 0.01;
                    l.sendParticles(((i + ring) & 1) == 0 ? TEAL : VIOLET, cx + Math.cos(a) * rr, y, cz + Math.sin(a) * rr, 1, 0, 0, 0, 0);
                }
            }
        }
        if (time % 4 != 0) return;
        for (ServerPlayer p : l.players()) {
            if (p.isSpectator() || Math.abs(p.getY() - c.getY()) > 40) continue;
            double dx = p.getX() - cx, dz = p.getZ() - cz, dist = Math.hypot(dx, dz);
            if (dist >= R + 2.5) continue;
            if (dist < R - 1) {
                BlockPos bh = bridgeHead(sr);
                p.teleportTo(bh.getX() + 0.5, bh.getY(), bh.getZ() + 0.5);
                p.fallDistance = 0;
            } else {
                p.setDeltaMovement(-dx / dist * 0.9, 0.25, -dz / dist * 0.9);
                p.hurtMarked = true;
            }
            l.playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.4f, 1.6f);
        }
    }

    private static double sq(double v) { return v * v; }

    // ─── Ressources des calmes ───

    /** Fait apparaître des arbres et des gisements de minerais sur l'île (pendant un calme du Siège). */
    static void resources(ServerLevel l, int islandRadius, int trees, int ores) {
        BlockPos c = RiftManager.CENTER;
        int y = c.getY();
        for (int t = 0; t < trees; t++) {
            int[] p = spot(l, islandRadius, y);
            if (p == null) continue;
            for (int h = 1; h <= 5; h++) set(l, p[0], y + h, p[1], Blocks.OAK_LOG.defaultBlockState());
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 3; dy <= 6; dy++) {
                if (Math.abs(dx) + Math.abs(dz) + Math.abs(dy - 4) > 3 + (dy == 6 ? -2 : 0)) continue;
                BlockPos q = new BlockPos(p[0] + dx, y + dy, p[1] + dz);
                if (l.getBlockState(q).isAir()) l.setBlock(q, Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true), 2);
            }
        }
        BlockState[] kinds = {Blocks.DEEPSLATE_COAL_ORE.defaultBlockState(), Blocks.DEEPSLATE_IRON_ORE.defaultBlockState(),
                Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState(), Blocks.DEEPSLATE_REDSTONE_ORE.defaultBlockState(), Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState()};
        for (int o = 0; o < ores; o++) {
            int[] p = spot(l, islandRadius, y);
            if (p == null) continue;
            int roll = RNG.nextInt(100);
            BlockState ore = roll < 38 ? kinds[0] : roll < 72 ? kinds[1] : roll < 86 ? kinds[2] : roll < 95 ? kinds[3] : kinds[4];
            for (int i = 0; i < 4; i++) {
                BlockPos q = new BlockPos(p[0] + RNG.nextInt(3) - 1, y + 1, p[1] + RNG.nextInt(3) - 1);
                if (l.getBlockState(q).isAir()) l.setBlock(q, ore, 2);
            }
            l.sendParticles(ParticleTypes.POOF, p[0] + 0.5, y + 1.5, p[1] + 0.5, 12, 0.6, 0.4, 0.6, 0.02);
        }
    }

    private static int[] spot(ServerLevel l, int islandRadius, int y) {
        for (int tries = 0; tries < 30; tries++) {
            double a = RNG.nextDouble() * Math.PI * 2, d = 6 + RNG.nextDouble() * Math.max(1, islandRadius - 10);
            int x = RiftManager.CENTER.getX() + (int) Math.round(Math.cos(a) * d), z = RiftManager.CENTER.getZ() + (int) Math.round(Math.sin(a) * d);
            if (!l.getBlockState(new BlockPos(x, y + 1, z)).isAir() || l.getBlockState(new BlockPos(x, y, z)).isAir()) continue;
            return new int[]{x, z};
        }
        return null;
    }

    // ─── Cibles à PV : catalyseurs et cœur ───

    /** Pose un cristal invulnérable portant une réserve de PV (catalyseur ou cœur). */
    static UUID spawnTarget(ServerLevel l, double x, double y, double z, float hp, String label) {
        EndCrystal e = new EndCrystal(l, x, y, z);
        e.setShowBottom(false);
        e.setInvulnerable(true);
        e.setCustomNameVisible(true);
        e.getPersistentData().putBoolean("ws_rift_target", true);
        TARGETS.put(e.getUUID(), new float[]{hp, hp});
        LABELS.put(e.getUUID(), label);
        refreshName(e);
        l.addFreshEntity(e);
        l.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 40, 0.6, 0.8, 0.6, 0.2);
        l.playSound(null, BlockPos.containing(x, y, z), SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 1.2f, 1.4f);
        return e.getUUID();
    }

    private static void refreshName(Entity e) {
        float[] t = TARGETS.get(e.getUUID());
        if (t == null) return;
        e.setCustomName(Component.literal(LABELS.getOrDefault(e.getUUID(), "") + " §c❤ " + Math.max(0, Math.round(t[0])) + "/" + Math.round(t[1])));
    }

    static boolean isTarget(Entity e) {
        return e instanceof EndCrystal && TARGETS.containsKey(e.getUUID());
    }

    /** Inflige des dégâts à une cible ; la détruit (explosion visuelle, sans dégâts de blocs) quand sa réserve est vide. */
    static void damageTarget(Entity e, float amount) {
        float[] t = TARGETS.get(e.getUUID());
        if (t == null || !(e.level() instanceof ServerLevel l)) return;
        t[0] -= amount;
        l.sendParticles(VIOLET, e.getX(), e.getY() + 1, e.getZ(), 12, 0.5, 0.5, 0.5, 0.05);
        l.playSound(null, e.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 1.5f, 0.7f);
        if (t[0] <= 0) {
            l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, e.getX(), e.getY() + 1, e.getZ(), 1, 0, 0, 0, 0);
            l.sendParticles(ParticleTypes.END_ROD, e.getX(), e.getY() + 1, e.getZ(), 60, 1.2, 1.2, 1.2, 0.25);
            l.playSound(null, e.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3f, 0.8f);
            TARGETS.remove(e.getUUID());
            LABELS.remove(e.getUUID());
            e.discard();
        } else {
            refreshName(e);
        }
    }

    /** Cibles encore debout dans ce monde (celles dont le cristal a disparu sont retirées). */
    static int aliveTargets(ServerLevel l) {
        int n = 0;
        for (UUID id : new java.util.ArrayList<>(TARGETS.keySet())) {
            Entity e = l.getEntity(id);
            if (e == null || e.isRemoved()) { TARGETS.remove(id); LABELS.remove(id); } else n++;
        }
        return n;
    }

    static void clearTargets(ServerLevel l) {
        for (UUID id : new java.util.ArrayList<>(TARGETS.keySet())) {
            Entity e = l.getEntity(id);
            if (e != null) e.discard();
        }
        TARGETS.clear();
        LABELS.clear();
    }
}
