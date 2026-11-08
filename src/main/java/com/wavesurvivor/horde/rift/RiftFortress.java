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

    /** Rayon de la plateforme de la forteresse. */
    static final int R = 13;
    private static final Random RNG = new Random();
    private static final DustParticleOptions VIOLET = new DustParticleOptions(new org.joml.Vector3f(0.65f, 0.2f, 0.95f), 1.3f);
    private static final DustParticleOptions TEAL = new DustParticleOptions(new org.joml.Vector3f(0.1f, 0.95f, 0.85f), 1.3f);

    /** Cibles à PV (catalyseurs, cœur) : cristaux invulnérables dont les dégâts sont gérés ici. {PV, PV max}. */
    static final Map<UUID, float[]> TARGETS = new HashMap<>();
    static final Map<UUID, String> LABELS = new HashMap<>();

    private RiftFortress() {}

    static BlockPos center(int siegeRadius) {
        BlockPos c = RiftManager.CENTER;
        return new BlockPos(c.getX() + siegeRadius + 24, c.getY(), c.getZ());
    }

    /** Devant la porte de la forteresse : les ennemis y apparaissent. */
    static BlockPos spawnPoint(int siegeRadius) {
        BlockPos c = center(siegeRadius);
        return new BlockPos(c.getX() - 8, c.getY() + 1, c.getZ());
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

    /** Construit le pont, la plateforme, la muraille (porte côté base), 4 tours et le donjon. */
    static void build(ServerLevel l, int sr) {
        BlockPos c = center(sr);
        int cx = c.getX(), cz = c.getZ(), y = c.getY();
        BlockState bricks = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), cracked = Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState rock = Blocks.BLACKSTONE.defaultBlockState(), edge = Blocks.OBSIDIAN.defaultBlockState();
        BlockState wall = Blocks.BLACKSTONE_WALL.defaultBlockState();
        BlockState cry = Blocks.CRYING_OBSIDIAN.defaultBlockState(), gild = Blocks.GILDED_BLACKSTONE.defaultBlockState();
        // Pont (5 de large) de l'île à la plateforme
        for (int x = RiftManager.CENTER.getX() + sr - 1; x <= cx - R + 1; x++) {
            for (int dz = -2; dz <= 2; dz++) {
                set(l, x, y, cz + dz, ((x + dz) & 1) == 0 ? bricks : cracked);
                set(l, x, y - 1, cz + dz, rock);
                if (Math.abs(dz) == 2) set(l, x, y + 1, cz + dz, wall);
            }
        }
        // Plateforme
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > R + 0.5) continue;
                int depth = 3 + (int) Math.round((1 - d / R) * 5);
                for (int k = 0; k < depth; k++) {
                    BlockState st = k == 0 ? (d > R - 1.5 ? edge : ((dx + dz) & 1) == 0 ? bricks : cracked) : (RNG.nextInt(14) == 0 ? gild : rock);
                    set(l, cx + dx, y - k, cz + dz, st);
                }
            }
        }
        // Muraille (rayon 10-11, hauteur 5 + créneaux) avec une porte côté base (-X)
        for (int dx = -12; dx <= 12; dx++) {
            for (int dz = -12; dz <= 12; dz++) {
                double d = Math.hypot(dx, dz);
                if (d < 9.8 || d > 11.2) continue;
                if (dx < -7 && Math.abs(dz) <= 2) continue; // la porte
                for (int h = 1; h <= 5; h++) set(l, cx + dx, y + h, cz + dz, h == 3 && ((dx + dz) & 3) == 0 ? cry : bricks);
                if (((dx + dz) & 1) == 0) set(l, cx + dx, y + 6, cz + dz, bricks);
            }
        }
        // 4 tours
        for (int sx : new int[]{-1, 1}) for (int sz : new int[]{-1, 1}) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                for (int h = 1; h <= 9; h++) set(l, cx + sx * 7 + dx, y + h, cz + sz * 7 + dz, bricks);
                set(l, cx + sx * 7 + dx, y + 10, cz + sz * 7 + dz, cry);
            }
        }
        // Donjon 7×7 creux, ouverture ovale côté base, cadre d'obsidienne pleureuse
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            boolean shell = Math.abs(dx) == 3 || Math.abs(dz) == 3;
            for (int h = 1; h <= 10; h++) {
                if (h == 10) set(l, cx + dx, y + h, cz + dz, bricks);
                else set(l, cx + dx, y + h, cz + dz, shell ? bricks : Blocks.AIR.defaultBlockState());
            }
        }
        for (int dz = -1; dz <= 1; dz++) for (int h = 1; h <= 5; h++) set(l, cx - 3, y + h, cz + dz, Blocks.AIR.defaultBlockState());
        for (int dz = -2; dz <= 2; dz++) for (int h = 1; h <= 6; h++) {
            boolean frame = Math.abs(dz) == 2 || h == 6;
            if (frame) set(l, cx - 3, y + h, cz + dz, cry);
        }
        set(l, cx, y + 9, cz, Blocks.SOUL_LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
    }

    // ─── Bouclier du Siège ───

    /** Particules du dôme et refoulement des joueurs : on ne peut ni entrer dans la forteresse ni l'atteindre tant que le bouclier tient. */
    static void shield(ServerLevel l, int sr, long time) {
        BlockPos c = center(sr);
        double cx = c.getX() + 0.5, cz = c.getZ() + 0.5;
        if (time % 10 == 0) {
            for (int ring = 0; ring < 4; ring++) {
                double y = c.getY() + 1 + ring * 3;
                double rr = (R + 1.8) * Math.sqrt(Math.max(0.2, 1 - sq(ring / 4.5)));
                for (int i = 0; i < 28; i++) {
                    double a = (i / 28.0) * Math.PI * 2 + time * 0.01;
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
