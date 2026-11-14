package com.wavesurvivor.horde.rift;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.HordeConfigMultiData;
import com.wavesurvivor.entity.GisementEntity;
import com.wavesurvivor.horde.chaos.GisementManager;
import com.wavesurvivor.horde.kingdom.KingdomRoles;
import com.wavesurvivor.horde.kingdom.KingdomTreasury;
import com.wavesurvivor.horde.model.ChaosEvent;
import com.wavesurvivor.horde.skill.IceFx;
import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.network.EventFeedPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * 2.1 — ÎLOTS DU SIÈGE et leurs ponts (fiche docs/faille/v2_1_ilots/FICHE_2_1_0.md, § 0 à 1 ; sauvegarde § A, placement § B,
 * après le Siège § C, Calme prolongé § E).
 * <ul>
 *   <li>Terrain posé à la fin de l'agrandissement de l'île, à « isletGap » blocs du bord, jamais côté forteresse (est) ni Titan (sud).</li>
 *   <li>Pont acheté au Monolithe (bois + pierre du trésor) : se construit tout seul, blocs qui arrivent en lévitation ; 5 segments à PV,
 *       un segment à 0 PV s'effondre ; réparé au clic droit avec de la pierre en main près du trou.</li>
 *   <li>Île minière : filons fixes (gisements du mod, pioche minimum, butin au trésor), repousse à chaque Calme du Siège, gardes.</li>
 * </ul>
 * Les îlots restent jusqu'à la fin de la Faille et ne sont pas cassables (terrain hors du claim) ; on peut seulement y poser des blocs
 * (et casser ceux qu'on a posés) pour revenir d'un îlot coincé.
 */
public final class RiftIslets {

    private RiftIslets() {}

    public enum Kind {
        MINE("mine", 18);
        final String id;
        final int radius;
        Kind(String id, int radius) { this.id = id; this.radius = radius; }

        static Kind byId(String s) {
            for (Kind k : values()) if (k.id.equalsIgnoreCase(s)) return k;
            return null;
        }
    }

    /** Un bloc du pont : position, état, segment (0 = côté île principale). */
    private record Piece(BlockPos pos, BlockState state, int seg) {}

    /** Un filon fixe de l'île minière (coordonnées locales u = vers l'extérieur, v = de côté). */
    private static final class Slot {
        final String type;
        final int u, v;
        UUID entity;
        boolean alive;
        Slot(String type, int u, int v) { this.type = type; this.u = u; this.v = v; }
    }

    private static final class Islet {
        final Kind kind;
        final String side;
        final double dx, dz;     // direction vers l'extérieur (unité)
        final int cx, cz, r;
        // pont
        final List<Piece> pieces = new ArrayList<>();
        boolean bought;
        float[] hp = new float[SEGMENTS];
        boolean[] down = new boolean[SEGMENTS];
        final List<Piece> queue = new ArrayList<>();   // blocs encore à poser (construction ou réparation)
        int bStart, bEnd;        // distance au centre du début et de la fin du pont
        // île minière
        final List<Slot> slots = new ArrayList<>();
        final List<UUID> guards = new ArrayList<>();

        Islet(Kind kind, String side, double dx, double dz, int cx, int cz, int r) {
            this.kind = kind; this.side = side; this.dx = dx; this.dz = dz; this.cx = cx; this.cz = cz; this.r = r;
        }

        /** Position monde d'un point local (u vers l'extérieur, v de côté) à la hauteur y. */
        BlockPos local(double u, int y, double v) {
            return BlockPos.containing(cx + 0.5 + dx * u - dz * v, y, cz + 0.5 + dz * u + dx * v);
        }

        String name() { return WSLang.t("rift.islet." + kind.id); }
    }

    static final int SEGMENTS = 5;
    private static final String GUARD_TAG = "ws_islet_guard";
    private static final Random RNG = new Random();

    private static final List<Islet> ISLETS = new ArrayList<>();
    private static long seed;
    private static boolean placed;

    // ─── Requêtes ───

    /** Au moins un îlot est posé (Calme prolongé, part des gisements de l'île principale…). */
    public static boolean active() { return placed && !ISLETS.isEmpty(); }

    public static boolean mineActive() {
        for (Islet i : ISLETS) if (i.kind == Kind.MINE) return true;
        return false;
    }

    /** Part (%) des gisements de l'île principale gardée quand l'île minière est là. */
    public static int mainDepositShare() {
        return Math.max(0, Math.min(100, rs().mainDepositShare));
    }

    private static Islet get(Kind k) {
        for (Islet i : ISLETS) if (i.kind == k) return i;
        return null;
    }

    private static HordeConfigMultiData.RiftSettings rs() { return RiftManager.settingsPublic(); }

    /** Côtés autorisés (jamais l'est = forteresse, jamais le sud = Titan) → direction unité. */
    private static double[] sideDir(String side) {
        double h = Math.sqrt(0.5);
        return switch (side == null ? "" : side.toLowerCase()) {
            case "west" -> new double[]{-1, 0};
            case "northwest" -> new double[]{-h, -h};
            case "southwest" -> new double[]{-h, h};
            case "northeast" -> new double[]{h, -h};
            default -> new double[]{0, -1};   // north
        };
    }

    /** Îlots prévus par les réglages (pour les chunks gardés chargés avant même qu'ils soient posés). */
    private static List<Islet> planned(HordeConfigMultiData.RiftSettings s, int siegeFinal) {
        List<Islet> out = new ArrayList<>();
        if (s == null || !s.siegeEnabled || !s.isletsEnabled) return out;
        if (s.isletMine) out.add(make(Kind.MINE, s.isletMineSide, s, siegeFinal));
        return out;
    }

    private static Islet make(Kind k, String side, HordeConfigMultiData.RiftSettings s, int siegeFinal) {
        double[] d = sideDir(side);
        int gap = Math.max(8, Math.min(60, s.isletGap));
        int dist = siegeFinal + gap + k.radius;
        BlockPos c = RiftManager.CENTER;
        Islet i = new Islet(k, side, d[0], d[1], (int) Math.round(c.getX() + d[0] * dist), (int) Math.round(c.getZ() + d[1] * dist), k.radius);
        i.bStart = siegeFinal - 3;
        i.bEnd = dist - k.radius + 4;
        return i;
    }

    /** Zones à garder chargées : chaque îlot et son pont (appelé par RiftManager.forceChunks). */
    static List<int[]> forceAreas(HordeConfigMultiData.RiftSettings s, int siegeFinal) {
        List<int[]> out = new ArrayList<>();
        BlockPos c = RiftManager.CENTER;
        for (Islet i : planned(s, siegeFinal)) {
            int m = i.r + 8;
            out.add(new int[]{i.cx - c.getX() - m, i.cx - c.getX() + m, i.cz - c.getZ() - m, i.cz - c.getZ() + m});
        }
        return out;
    }

    // ─── Pose ───

    /** Fin de l'agrandissement de l'île (début du Siège) : les îlots apparaissent (terrain seulement). */
    static void onGrowthDone(ServerLevel l, int siegeFinal) {
        if (placed) return;
        seed = RNG.nextLong();
        build(l, siegeFinal);
        if (!ISLETS.isEmpty()) {
            for (ServerPlayer p : l.players()) {
                p.sendSystemMessage(Component.literal(WSLang.t("rift.islet.appear")));
            }
            l.playSound(null, RiftManager.CENTER, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.AMBIENT, 4f, 0.6f);
        }
    }

    private static void build(ServerLevel l, int siegeFinal) {
        ISLETS.clear();
        placed = true;
        for (Islet i : planned(rs(), siegeFinal)) {
            Random r = new Random(seed ^ (i.kind.ordinal() * 0x9E3779B97F4A7C15L));
            if (i.kind == Kind.MINE) buildMine(l, i, r);
            planBridge(i);
            java.util.Arrays.fill(i.hp, segmentHp());
            ISLETS.add(i);
        }
    }

    /** Début du Siège (moteur du Kingdom lancé) : filons et gardes. Aussi appelé à la reprise, après restore. */
    static void onSiegeStart(ServerLevel l, boolean resumed) {
        if (!active()) return;
        for (Islet i : ISLETS) {
            if (i.kind != Kind.MINE) continue;
            for (Slot s : i.slots) {
                if (!resumed) s.alive = true;
                if (s.alive && (s.entity == null || !(l.getEntity(s.entity) instanceof GisementEntity g) || !g.isAlive())) spawnDeposit(l, i, s);
            }
            if (RiftManager.phasePublic() == RiftManager.Phase.SIEGE) spawnGuards(l, i);
        }
    }

    /**
     * Début d'un Calme du Siège (KingdomManager.startCalm) : repousse des filons et gardes de l'île minière.
     * @return secondes ajoutées au Calme (§ E : +60 s en solo, +30 s à 2)
     */
    public static int onCalm(MinecraftServer server) {
        if (!active()) return 0;
        ServerLevel l = server.getLevel(RiftManager.RIFT_DIM);
        if (l == null) return 0;
        HordeConfigMultiData.RiftSettings s = rs();
        if (RiftManager.phasePublic() == RiftManager.Phase.SIEGE) {
            for (Islet i : ISLETS) {
                if (i.kind != Kind.MINE) continue;
                int pct = KingdomRoles.present(KingdomRoles.Role.MINER) ? s.mineRegrowMiner : s.mineRegrow;
                int grown = 0;
                for (Slot sl : i.slots) {
                    if (sl.alive || RNG.nextInt(100) >= pct) continue;
                    sl.alive = true;
                    spawnDeposit(l, i, sl);
                    grown++;
                }
                if (grown > 0) EventFeedPacket.toAll(server, Component.literal(WSLang.t("rift.mine.regrow", grown)), "minecraft:iron_ore", EventFeedPacket.ORE, false);
                spawnGuards(l, i);
            }
        }
        int players = 0;
        for (ServerPlayer p : l.players()) if (!p.isSpectator()) players++;
        int bonus = players <= 1 ? s.calmBonusSolo : players == 2 ? s.calmBonusDuo : 0;
        bonus = Math.max(0, Math.min(600, bonus));
        if (bonus > 0) for (ServerPlayer p : l.players()) p.displayClientMessage(Component.literal(WSLang.t("rift.islet.calm_bonus", bonus)), false);
        return bonus;
    }

    // ─── Île minière ───

    private static double smooth(double t) { return t * t * (3 - 2 * t); }

    /** Bruit de valeur (0..1) à cellules de « cell » blocs, graine de l'îlot. */
    private static double noise(long sd, double x, double z, double cell) {
        double gx = x / cell, gz = z / cell;
        int ix = (int) Math.floor(gx), iz = (int) Math.floor(gz);
        double fx = smooth(gx - ix), fz = smooth(gz - iz);
        double a = hash(sd, ix, iz), b = hash(sd, ix + 1, iz), c = hash(sd, ix, iz + 1), d = hash(sd, ix + 1, iz + 1);
        double t = a + (b - a) * fx, u = c + (d - c) * fx;
        return t + (u - t) * fz;
    }

    private static double hash(long sd, int x, int z) {
        long k = sd ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        k = (k ^ (k >>> 30)) * 0xBF58476D1CE4E5B9L;
        k = (k ^ (k >>> 27)) * 0x94D049BB133111EBL;
        k ^= k >>> 31;
        return (k >>> 11) * 0x1.0p-53;
    }

    private static void set(ServerLevel l, BlockPos p, BlockState st) {
        l.setBlock(p, st, 2 | 16);
    }

    /** Premier bloc non vide en descendant (y monde), ou MIN_VALUE. */
    private static int top(ServerLevel l, BlockPos col) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(col.getX(), RiftManager.CENTER.getY() + 20, col.getZ());
        for (int y = RiftManager.CENTER.getY() + 20; y >= RiftManager.CENTER.getY() - 20; y--) {
            m.setY(y);
            if (!l.getBlockState(m).isAir()) return y;
        }
        return Integer.MIN_VALUE;
    }

    /**
     * Île minière (rayon 18) d'après sources/build.py : terrain presque plat côté pont qui monte vers le fond, cône du dessous
     * déchiqueté (pointes qui pendent, filons à flanc), mine décorative au fond (butte, galerie étayée, rails, wagonnet, tonneaux),
     * géode d'améthyste et cristaux d'Essence ; les filons à miner sont des gisements posés au début du Siège.
     */
    private static void buildMine(ServerLevel l, Islet i, Random rnd) {
        int y0 = RiftManager.CENTER.getY();
        int R = i.r;
        long sA = seed + 11, sB = seed + 23, sC = seed + 37;
        BlockState deep = Blocks.DEEPSLATE.defaultBlockState(), tuff = Blocks.TUFF.defaultBlockState(),
                dark = Blocks.COBBLED_DEEPSLATE.defaultBlockState(), gravel = Blocks.GRAVEL.defaultBlockState(),
                moss = Blocks.MOSS_BLOCK.defaultBlockState(), cobble = Blocks.COBBLESTONE.defaultBlockState();
        // ── terrain : chaque colonne du disque, en coordonnées locales continues (marche aussi sur une diagonale) ──
        for (int x = i.cx - R - 3; x <= i.cx + R + 3; x++) {
            for (int z = i.cz - R - 3; z <= i.cz + R + 3; z++) {
                double ox = x + 0.5 - (i.cx + 0.5), oz = z + 0.5 - (i.cz + 0.5);
                double u = ox * i.dx + oz * i.dz;          // vers l'extérieur (le fond de l'île)
                double rr = Math.hypot(ox, oz) + (noise(sA, x, z, 6) - 0.5) * 3.2;
                if (rr > R) continue;
                double nB = noise(sB, x, z, 3), nC = noise(sC, x, z, 10);
                int h = (int) Math.round(1 + 0.9 * (nB - 0.5) * 2 + 2.2 * Math.max(0, u + 2) / R + 1.2 * (1 - rr / R));
                h = Math.max(0, h);
                BlockState topSt = nC > 0.62 ? tuff : nB > 0.72 ? gravel : nC < 0.25 ? moss : deep;
                set(l, new BlockPos(x, y0 + h, z), topSt);
                for (int y = h - 1; y >= 0; y--) set(l, new BlockPos(x, y0 + y, z), deep);
                int dep = (int) (3 + 19 * Math.pow(1 - rr / R, 0.9) + (nB - 0.5) * 7);
                if (rnd.nextDouble() < 0.07) dep += 3 + rnd.nextInt(5);   // pointes qui pendent
                for (int y = -1; y > -dep; y--) {
                    double q = rnd.nextDouble();
                    BlockState st = q > 0.25 ? deep : (rnd.nextBoolean() ? tuff : dark);
                    set(l, new BlockPos(x, y0 + y, z), st);
                }
            }
        }
        // ── filons à flanc de falaise (dessous exposé) : décor ──
        BlockState ironOre = Blocks.DEEPSLATE_IRON_ORE.defaultBlockState(), emOre = Blocks.DEEPSLATE_EMERALD_ORE.defaultBlockState();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = i.cx - R - 3; x <= i.cx + R + 3; x++) {
            for (int z = i.cz - R - 3; z <= i.cz + R + 3; z++) {
                for (int y = y0 - 1; y > y0 - 30; y--) {
                    m.set(x, y, z);
                    if (l.getBlockState(m).isAir()) continue;
                    boolean exposed = false;
                    for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN}) {
                        if (l.getBlockState(m.relative(d)).isAir()) { exposed = true; break; }
                    }
                    if (!exposed) continue;
                    double q = rnd.nextDouble();
                    if (q < 0.06) set(l, m.immutable(), ironOre);
                    else if (q < 0.085) set(l, m.immutable(), emOre);
                }
            }
        }
        // ── la mine, au fond (u = 6), façade tournée vers le pont ──
        int fu = 6;
        int ys = topAt(l, i, fu - 2, 0, y0);
        // terrasse plate devant l'entrée
        for (int u = 1; u <= 5; u++) {
            for (int v = -6; v <= 6; v++) {
                BlockPos p = i.local(u, 0, v);
                if (Math.hypot(p.getX() - i.cx, p.getZ() - i.cz) > R - 0.5) continue;
                for (int y = ys + 1; y < ys + 6; y++) set(l, new BlockPos(p.getX(), y, p.getZ()), Blocks.AIR.defaultBlockState());
                for (int y = ys; y > ys - 3; y--) set(l, new BlockPos(p.getX(), y, p.getZ()), deep);
                double q = rnd.nextDouble();
                set(l, new BlockPos(p.getX(), ys, p.getZ()), q < 0.35 ? gravel : q < 0.68 ? cobble : tuff);
            }
        }
        // la butte : 17 de large, 8 de profondeur, ~10 de haut, bords qui s'abaissent
        long sN = seed + 51;
        for (int u = fu; u <= fu + 7; u++) {
            for (int v = -8; v <= 8; v++) {
                double taper = 1 - Math.pow(Math.abs(v) / 8.5, 2.2);
                double back = 1 - Math.max(0, (u - (fu + 4))) / 4.5;
                BlockPos p = i.local(u, 0, v);
                int hh = ys + (int) Math.round(10 * taper * Math.max(0.35, back) + (noise(sN, p.getX(), p.getZ(), 3) - 0.5) * 2.2);
                if (u == fu && rnd.nextDouble() < 0.3) hh--;
                int yb = top(l, p);
                if (yb == Integer.MIN_VALUE) continue;
                for (int y = yb + 1; y <= hh; y++) {
                    double q = rnd.nextDouble();
                    set(l, new BlockPos(p.getX(), y, p.getZ()), q < 0.28 ? tuff : q < 0.42 ? dark : q < 0.47 ? ironOre : deep);
                }
                if (hh > yb && rnd.nextDouble() < 0.35) set(l, new BlockPos(p.getX(), hh, p.getZ()), moss);
            }
        }
        // galerie 5 × 4 qui se perd dans le noir
        for (int u = fu; u <= fu + 3; u++) {
            for (int v = -2; v <= 2; v++) {
                BlockPos p = i.local(u, 0, v);
                for (int y = ys + 1; y < ys + 5; y++) set(l, new BlockPos(p.getX(), y, p.getZ()), Blocks.AIR.defaultBlockState());
                set(l, new BlockPos(p.getX(), ys, p.getZ()), rnd.nextDouble() < 0.4 ? gravel : cobble);
            }
        }
        for (int v = -2; v <= 2; v++) {   // fond noir
            BlockPos p = i.local(fu + 4, 0, v);
            for (int y = ys + 1; y < ys + 5; y++) set(l, new BlockPos(p.getX(), y, p.getZ()), Blocks.BLACK_CONCRETE.defaultBlockState());
        }
        // cadre : gros poteaux, double linteau, jambes de force, lanternes
        boolean vAlongX = Math.abs(i.dz) > Math.abs(i.dx);   // v suit l'axe X quand l'îlot est au nord / au sud
        BlockState post = Blocks.SPRUCE_LOG.defaultBlockState();
        BlockState beam = Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, vAlongX ? Direction.Axis.X : Direction.Axis.Z);
        for (int v : new int[]{-3, 3}) {
            BlockPos p = i.local(fu, 0, v);
            for (int y = ys + 1; y < ys + 6; y++) set(l, new BlockPos(p.getX(), y, p.getZ()), post);
        }
        for (int v = -4; v <= 4; v++) { BlockPos p = i.local(fu, 0, v); set(l, new BlockPos(p.getX(), ys + 6, p.getZ()), beam); }
        for (int v = -2; v <= 2; v++) { BlockPos p = i.local(fu, 0, v); set(l, new BlockPos(p.getX(), ys + 5, p.getZ()), beam); }
        for (int v : new int[]{-2, 2}) { BlockPos p = i.local(fu, 0, v); set(l, new BlockPos(p.getX(), ys + 4, p.getZ()), Blocks.SPRUCE_PLANKS.defaultBlockState()); }
        for (int v : new int[]{-4, 4}) {
            BlockPos p = i.local(fu - 1, 0, v);
            set(l, new BlockPos(p.getX(), ys + 1, p.getZ()), Blocks.SPRUCE_FENCE.defaultBlockState());
            set(l, new BlockPos(p.getX(), ys + 2, p.getZ()), Blocks.SPRUCE_FENCE.defaultBlockState());
            set(l, new BlockPos(p.getX(), ys + 3, p.getZ()), Blocks.LANTERN.defaultBlockState());
        }
        // rails de la galerie jusqu'au bord côté pont, voie de garage avec un wagonnet chargé (décor)
        RailShape along = vAlongX ? RailShape.NORTH_SOUTH : RailShape.EAST_WEST;
        BlockState rail = Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, along);
        for (int u = fu + 3; u >= -(R - 2); u--) {
            BlockPos p = i.local(u, 0, 0);
            int y = u >= fu - 5 && u <= fu + 3 ? ys : top(l, p);
            if (y == Integer.MIN_VALUE) continue;
            BlockPos at = new BlockPos(p.getX(), y + 1, p.getZ());
            if (l.getBlockState(at).isAir() && !l.getBlockState(at.below()).isAir()) set(l, at, rail);
        }
        for (int u = fu - 4; u <= fu - 2; u++) {
            BlockPos p = i.local(u, 0, 3);
            set(l, new BlockPos(p.getX(), ys + 1, p.getZ()), rail);
        }
        BlockPos cart = i.local(fu - 3, 0, 3);
        net.minecraft.world.entity.vehicle.MinecartChest mc = EntityType.CHEST_MINECART.create(l);
        if (mc != null) {
            mc.moveTo(cart.getX() + 0.5, ys + 1.1, cart.getZ() + 0.5, vAlongX ? 0f : 90f, 0f);
            mc.setInvulnerable(true);
            l.addFreshEntity(mc);
        }
        // tonneaux, caisse, tas de minerai
        int[][] deco = {{fu - 2, -5, 0}, {fu - 3, -5, 0}, {fu - 2, -4, 1}, {fu - 4, 5, 2}, {fu - 3, 5, 3}, {fu - 4, 4, 4}};
        BlockState[] decoSt = {Blocks.BARREL.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.RAW_IRON_BLOCK.defaultBlockState(),
                Blocks.EMERALD_ORE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState()};
        for (int[] d : deco) {
            BlockPos p = i.local(d[0], 0, d[1]);
            int y = Math.max(ys, top(l, p));
            set(l, new BlockPos(p.getX(), y + 1, p.getZ()), decoSt[Math.min(decoSt.length - 1, d[2])]);
        }
        BlockPos br = i.local(fu - 2, 0, -5);
        set(l, new BlockPos(br.getX(), ys + 2, br.getZ()), Blocks.BARREL.defaultBlockState());
        // géode d'améthyste (devant, à gauche en arrivant) : décor autour du filon
        for (int u = -9; u <= -3; u++) {
            for (int v = -17; v <= -11; v++) {
                if (Math.hypot(u + 6, v + 14) > 2.6) continue;
                BlockPos p = i.local(u, 0, v);
                if (Math.hypot(p.getX() - i.cx, p.getZ() - i.cz) > R - 1) continue;
                int y = top(l, p);
                if (y == Integer.MIN_VALUE) continue;
                set(l, new BlockPos(p.getX(), y, p.getZ()), rnd.nextBoolean() ? Blocks.AMETHYST_BLOCK.defaultBlockState() : Blocks.BUDDING_AMETHYST.defaultBlockState());
                if (rnd.nextDouble() < 0.4) set(l, new BlockPos(p.getX(), y + 1, p.getZ()), Blocks.AMETHYST_CLUSTER.defaultBlockState());
            }
        }
        // filons à miner (posés au début du Siège) : 4 fer, 2 émeraude, la géode, les cristaux d'Essence
        i.slots.clear();
        int[][] pos = {{-8, -8}, {-3, 9}, {-12, -4}, {1, 5}};
        for (int[] p : pos) i.slots.add(new Slot("iron", p[0], p[1]));
        i.slots.add(new Slot("emerald", -11, 6));
        i.slots.add(new Slot("emerald", -2, -11));
        i.slots.add(new Slot("amethyst", -6, -14));
        i.slots.add(new Slot("essence", -7, 12));
    }

    private static int topAt(ServerLevel l, Islet i, double u, double v, int def) {
        int y = top(l, i.local(u, 0, v));
        return y == Integer.MIN_VALUE ? def : y;
    }

    /** Pose (ou repose) le gisement d'un filon. */
    private static void spawnDeposit(ServerLevel l, Islet i, Slot s) {
        if (s.entity != null && l.getEntity(s.entity) instanceof GisementEntity old && old.isAlive()) return;
        BlockPos p = i.local(s.u, 0, s.v);
        int y = top(l, p);
        if (y == Integer.MIN_VALUE) return;
        GisementEntity g = com.wavesurvivor.registry.ModEntities.GISEMENT.get().create(l);
        if (g == null) return;
        ChaosEvent ev = new ChaosEvent();
        String csv;
        int tier, hits, size;
        List<ChaosEvent.Drop> drops = new ArrayList<>(), hitDrops = new ArrayList<>();
        switch (s.type) {
            case "emerald" -> {
                csv = "minecraft:emerald_ore,minecraft:deepslate_emerald_ore,minecraft:deepslate";
                tier = 2; hits = 10; size = 1;
                drops.add(drop("minecraft:emerald", 3, 6, 100));
            }
            case "amethyst" -> {
                csv = "minecraft:amethyst_block,minecraft:budding_amethyst,minecraft:amethyst_cluster";
                tier = 1; hits = 8; size = 1;
                drops.add(drop("minecraft:amethyst_shard", 4, 8, 100));
                hitDrops.add(drop("minecraft:amethyst_shard", 1, 1, 25));
            }
            case "essence" -> {
                csv = "minecraft:prismarine_bricks,minecraft:sea_lantern,minecraft:prismarine";
                tier = 2; hits = 12; size = 0;
            }
            default -> {
                csv = "minecraft:iron_ore,minecraft:deepslate_iron_ore,minecraft:raw_iron_block";
                tier = 1; hits = 8; size = 1;
                drops.add(drop("minecraft:raw_iron", 3, 6, 100));
            }
        }
        ev.drops = drops;
        ev.hitDrops = hitDrops;
        g.setTree(false);
        g.setStyle(0);
        g.setSize(size);
        g.moveTo(p.getX() + 0.5, y + 1, p.getZ() + 0.5, RNG.nextFloat() * 360f, 0);
        g.setup(csv, tier);
        int hp = (int) Math.max(1, Math.round(hits * GisementEntity.HIT_MULT[size]));
        var attr = g.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) attr.setBaseValue(hp);
        g.setHealth(hp);
        g.setCustomNameVisible(true);
        g.getPersistentData().putBoolean("ws_revenant", true);
        if (!l.addFreshEntity(g)) return;
        s.entity = g.getUUID();
        s.alive = true;
        GisementManager.registerPermanent(g, ev, WSLang.t("rift.mine.vein." + s.type), GisementEntity.LOOT_MULT[size]);
        l.sendParticles(ParticleTypes.WAX_OFF, g.getX(), g.getY() + 1, g.getZ(), 20, 0.5, 0.6, 0.5, 0.1);
    }

    private static ChaosEvent.Drop drop(String item, int min, int max, double chance) {
        ChaosEvent.Drop d = new ChaosEvent.Drop();
        d.item = item;
        d.minQty = min;
        d.maxQty = max;
        d.chance = chance;
        return d;
    }

    /** Gardes : quelques monstres légers sur 5 points de l'île minière (les anciens partent). */
    private static void spawnGuards(ServerLevel l, Islet i) {
        HordeConfigMultiData.RiftSettings s = rs();
        for (UUID id : i.guards) {
            Entity e = l.getEntity(id);
            if (e != null) e.discard();
        }
        i.guards.clear();
        if (!s.mineGuards || s.mineGuardMobs == null || s.mineGuardMobs.isEmpty()) return;
        int per = Math.max(0, Math.min(6, s.mineGuardsPerPoint));
        int[][] pts = {{-9, -2}, {-4, 10}, {-5, -9}, {2, 3}, {0, 0}};
        int n = 0;
        for (int[] pt : pts) {
            int k = per <= 1 ? per : 1 + RNG.nextInt(per);
            for (int j = 0; j < k; j++) {
                ResourceLocation id = ResourceLocation.tryParse(s.mineGuardMobs.get(RNG.nextInt(s.mineGuardMobs.size())));
                if (id == null || !ForgeRegistries.ENTITY_TYPES.containsKey(id)) continue;
                Entity e = ForgeRegistries.ENTITY_TYPES.getValue(id).create(l);
                if (!(e instanceof Mob mob)) { if (e != null) e.discard(); continue; }
                BlockPos p = i.local(pt[0] + RNG.nextInt(3) - 1, 0, pt[1] + RNG.nextInt(3) - 1);
                int y = top(l, p);
                if (y == Integer.MIN_VALUE) { e.discard(); continue; }
                mob.moveTo(p.getX() + 0.5, y + 1, p.getZ() + 0.5, RNG.nextFloat() * 360f, 0f);
                mob.finalizeSpawn(l, l.getCurrentDifficultyAt(p), MobSpawnType.EVENT, null, null);
                mob.addTag(GUARD_TAG);
                mob.setPersistenceRequired();
                mob.restrictTo(i.local(0, y, 0), i.r);   // restent sur leur île
                if (l.addFreshEntity(mob)) { i.guards.add(mob.getUUID()); n++; }
            }
        }
        if (n > 0) EventFeedPacket.toAll(l.getServer(), Component.literal(WSLang.t("rift.mine.guards")), "minecraft:iron_sword", EventFeedPacket.DANGER, false);
    }

    // ─── Le pont ───

    private static float segmentHp() {
        return Math.max(20, Math.min(20000, rs().bridgeSegmentHp));
    }

    /**
     * Plan du pont : 3 de large en briques sombres, bordures et garde-corps, poutre dessous, lanternes de cristal violettes sur des
     * poteaux tous les 8 blocs et d'autres qui pendent à des chaînes sous le tablier ; découpé en 5 segments le long du pont.
     */
    private static void planBridge(Islet i) {
        i.pieces.clear();
        BlockPos c = RiftManager.CENTER;
        int y = c.getY();
        int L = Math.max(4, i.bEnd - i.bStart);
        BlockState deck = Blocks.DEEPSLATE_BRICKS.defaultBlockState(), deckB = Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
                edge = Blocks.DEEPSLATE_TILES.defaultBlockState(), wall = Blocks.DEEPSLATE_TILE_WALL.defaultBlockState(),
                postSt = Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState(), lamp = Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState(),
                chain = Blocks.CHAIN.defaultBlockState(), under = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (int a = 0; a <= L; a++) {
            int seg = Math.min(SEGMENTS - 1, a * SEGMENTS / (L + 1));
            double d = i.bStart + a;
            boolean lantern = a % 8 == 4;
            for (int o = -2; o <= 2; o++) {
                int bx = (int) Math.round(c.getX() + 0.5 + i.dx * d - i.dz * o - 0.5);
                int bz = (int) Math.round(c.getZ() + 0.5 + i.dz * d + i.dx * o - 0.5);
                if (!seen.add(BlockPos.asLong(bx, 0, bz))) continue;
                List<Piece> col = new ArrayList<>();
                if (o == 0) col.add(new Piece(new BlockPos(bx, y - 1, bz), under, seg));
                col.add(new Piece(new BlockPos(bx, y, bz), Math.abs(o) == 2 ? edge : ((a + o) % 3 == 0 ? deckB : deck), seg));
                if (Math.abs(o) == 2) {
                    if (lantern) {
                        for (int k = 1; k <= 3; k++) col.add(new Piece(new BlockPos(bx, y + k, bz), postSt, seg));
                        col.add(new Piece(new BlockPos(bx, y + 4, bz), lamp, seg));
                    } else {
                        col.add(new Piece(new BlockPos(bx, y + 1, bz), wall, seg));
                    }
                }
                if (o == 0 && lantern) {
                    for (int k = 2; k <= 4; k++) col.add(new Piece(new BlockPos(bx, y - k, bz), chain, seg));
                    col.add(new Piece(new BlockPos(bx, y - 5, bz), lamp, seg));
                }
                i.pieces.addAll(col);
            }
        }
    }

    /** Achat au Monolithe (AltarDefense.upgrade, code 94 + n°). */
    public static void buyBridge(ServerPlayer p, int index) {
        if (!RiftManager.isActive() || !active()) { p.displayClientMessage(Component.literal(WSLang.t("rift.bridge.no_islet")), true); return; }
        Kind k = index >= 0 && index < Kind.values().length ? Kind.values()[index] : null;
        Islet i = k == null ? null : get(k);
        if (i == null) { p.displayClientMessage(Component.literal(WSLang.t("rift.bridge.no_islet")), true); return; }
        if (i.bought) { p.displayClientMessage(Component.literal(WSLang.t("rift.bridge.already")), true); return; }
        HordeConfigMultiData.RiftSettings s = rs();
        int wood = Math.max(0, s.bridgeWood), stone = Math.max(0, s.bridgeStone);
        if (!p.isCreative()) {
            if (!KingdomTreasury.has(KingdomTreasury.Res.WOOD, wood) || !KingdomTreasury.has(KingdomTreasury.Res.STONE, stone)) {
                p.displayClientMessage(Component.literal(WSLang.t("rift.bridge.need", wood, stone)), true);
                return;
            }
            KingdomTreasury.spend(KingdomTreasury.Res.WOOD, wood);
            KingdomTreasury.spend(KingdomTreasury.Res.STONE, stone);
        }
        i.bought = true;
        java.util.Arrays.fill(i.hp, segmentHp());
        java.util.Arrays.fill(i.down, false);
        i.queue.clear();
        i.queue.addAll(i.pieces);
        if (p.level() instanceof ServerLevel l) {
            EventFeedPacket.toAll(l.getServer(), Component.literal(WSLang.t("rift.bridge.bought", i.name())), "minecraft:deepslate_bricks", EventFeedPacket.RESOURCE, false);
            l.playSound(null, RiftManager.CENTER, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 2f, 1.4f);
        }
    }

    /** Chaque tick (RiftManager.flow) : les blocs du pont arrivent en lévitation et se posent (~10 s pour un pont entier). */
    static void tick(ServerLevel l, long now) {
        if (!active()) return;
        for (Islet i : ISLETS) {
            if (i.queue.isEmpty()) continue;
            int per = Math.max(1, (int) Math.ceil(i.pieces.size() / 200.0));
            for (int k = 0; k < per && !i.queue.isEmpty(); k++) flyIn(l, i, i.queue.remove(0), i.queue.isEmpty());
        }
    }

    /** Un bloc du pont : un bloc fantôme lumineux descend en flottant, puis le vrai bloc se pose. */
    private static void flyIn(ServerLevel l, Islet i, Piece pc, boolean last) {
        BlockPos p = pc.pos();
        Display.BlockDisplay d = IceFx.spawn(l, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, pc.state(), new Quaternionf(), 1f, 1f, 1f, 2.5f);
        if (d != null) {
            CompoundTag t = d.saveWithoutId(new CompoundTag());
            t.putInt("glow_color_override", 0x55E8FF);
            d.load(t);
            d.setGlowingTag(true);
            IceFx.animate(d, new Quaternionf(), 1f, 1f, 1f, 0f, 7);
        }
        l.sendParticles(ParticleTypes.GLOW, p.getX() + 0.5, p.getY() + 3, p.getZ() + 0.5, 2, 0.2, 0.2, 0.2, 0.01);
        RiftManager.later(l.getServer(), 8, () -> {
            IceFx.remove(d);
            if (i.down[pc.seg()]) return;   // le segment est retombé entre-temps
            BlockState st = Block.updateFromNeighbourShapes(pc.state(), l, p);
            l.setBlock(p, st, 3);
            l.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, 3, 0.3, 0.1, 0.3, 0.05);
            if (RNG.nextInt(6) == 0) l.playSound(null, p, SoundEvents.DEEPSLATE_BRICKS_PLACE, SoundSource.BLOCKS, 0.8f, 1.1f);
            if (last) {
                EventFeedPacket.toAll(l.getServer(), Component.literal(WSLang.t("rift.bridge.built", i.name())), "minecraft:deepslate_bricks", EventFeedPacket.RESOURCE, false);
                l.playSound(null, p, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 3f, 0.8f);
            }
        });
    }

    /** Dégâts à un segment de pont (Brise-pont, explosions…). Renvoie true s'il vient de s'effondrer. */
    static boolean damageSegment(ServerLevel l, Kind k, int seg, float dmg) {
        Islet i = get(k);
        if (i == null || !i.bought || seg < 0 || seg >= SEGMENTS || i.down[seg]) return false;
        i.hp[seg] = Math.max(0, i.hp[seg] - dmg);
        if (i.hp[seg] > 0) return false;
        collapse(l, i, seg);
        return true;
    }

    /** Un segment à 0 PV s'effondre : ses blocs tombent dans le vide. */
    private static void collapse(ServerLevel l, Islet i, int seg) {
        i.down[seg] = true;
        i.hp[seg] = 0;
        i.queue.removeIf(pc -> pc.seg() == seg);
        int falling = 0;
        for (Piece pc : i.pieces) {
            if (pc.seg() != seg) continue;
            BlockPos p = pc.pos();
            BlockState st = l.getBlockState(p);
            if (st.isAir()) continue;
            if (falling < 60 && RNG.nextInt(3) == 0) {
                FallingBlockEntity fb = FallingBlockEntity.fall(l, p, st);
                fb.disableDrop();
                fb.dropItem = false;
                falling++;
            } else {
                l.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
            if (RNG.nextInt(5) == 0) l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
        }
        Piece mid = midPiece(i, seg);
        if (mid != null) l.playSound(null, mid.pos(), SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 2f, 0.6f);
        EventFeedPacket.toAll(l.getServer(), Component.literal(WSLang.t("rift.bridge.collapse", i.name())), "minecraft:cracked_deepslate_bricks", EventFeedPacket.DANGER, true);
    }

    private static Piece midPiece(Islet i, int seg) {
        List<Piece> list = new ArrayList<>();
        for (Piece pc : i.pieces) if (pc.seg() == seg) list.add(pc);
        return list.isEmpty() ? null : list.get(list.size() / 2);
    }

    /** Reconstruit un segment effondré (même animation) ; utilisé par la réparation à la pierre et le Kit de pont. */
    static boolean repairSegment(ServerLevel l, Kind k, int seg) {
        Islet i = get(k);
        if (i == null || !i.bought || seg < 0 || seg >= SEGMENTS || !i.down[seg]) return false;
        i.down[seg] = false;
        i.hp[seg] = segmentHp();
        for (Piece pc : i.pieces) if (pc.seg() == seg) i.queue.add(pc);
        return true;
    }

    /** Segment effondré le plus proche du joueur (à moins de « reach » blocs d'un de ses blocs), ou null. */
    private static int[] brokenNear(ServerPlayer p, double reach) {
        for (Islet i : ISLETS) {
            if (!i.bought) continue;
            for (Piece pc : i.pieces) {
                if (!i.down[pc.seg()]) continue;
                if (p.distanceToSqr(pc.pos().getX() + 0.5, pc.pos().getY() + 0.5, pc.pos().getZ() + 0.5) <= reach * reach)
                    return new int[]{i.kind.ordinal(), pc.seg()};
            }
        }
        return null;
    }

    private static boolean isStone(ItemStack st) {
        return st.is(Items.COBBLESTONE) || st.is(Items.STONE) || st.is(Items.COBBLED_DEEPSLATE) || st.is(Items.DEEPSLATE)
                || st.is(Items.BLACKSTONE) || st.is(Items.STONE_BRICKS) || st.is(Items.DEEPSLATE_BRICKS);
    }

    // ─── Construction libre près des îlots (rentrer d'un îlot coincé) ───

    /** Vrai si la position est sur un îlot ou dans le couloir entre l'île et un îlot. */
    static boolean inBuildZone(BlockPos pos) {
        if (!active()) return false;
        BlockPos c = RiftManager.CENTER;
        for (Islet i : ISLETS) {
            if (Math.hypot(pos.getX() - i.cx, pos.getZ() - i.cz) <= i.r + 4) return true;
            double ox = pos.getX() + 0.5 - (c.getX() + 0.5), oz = pos.getZ() + 0.5 - (c.getZ() + 0.5);
            double along = ox * i.dx + oz * i.dz, side = -ox * i.dz + oz * i.dx;
            if (along >= i.bStart - 4 && along <= i.bEnd + 2 && Math.abs(side) <= 8 && Math.abs(pos.getY() - c.getY()) <= 24) return true;
        }
        return false;
    }

    // ─── Sauvegarde (§ A) ───

    static CompoundTag save() {
        CompoundTag t = new CompoundTag();
        if (!placed) return t;
        t.putBoolean("placed", true);
        t.putLong("seed", seed);
        ListTag list = new ListTag();
        for (Islet i : ISLETS) {
            CompoundTag o = new CompoundTag();
            o.putString("kind", i.kind.id);
            o.putString("side", i.side == null ? "" : i.side);
            o.putBoolean("bought", i.bought);
            int[] hp = new int[SEGMENTS];
            byte[] down = new byte[SEGMENTS];
            for (int s = 0; s < SEGMENTS; s++) { hp[s] = Math.round(i.hp[s]); down[s] = (byte) (i.down[s] ? 1 : 0); }
            o.putIntArray("hp", hp);
            o.putByteArray("down", down);
            byte[] alive = new byte[i.slots.size()];
            for (int s = 0; s < alive.length; s++) alive[s] = (byte) (i.slots.get(s).alive ? 1 : 0);
            o.putByteArray("veins", alive);
            list.add(o);
        }
        t.put("islets", list);
        return t;
    }

    /**
     * Reprise : terrain reposé à l'identique (même graine), ponts achetés reposés directement (segments effondrés laissés vides),
     * filons vivants reposés au début du Siège ({@link #onSiegeStart}).
     */
    static void restore(ServerLevel l, CompoundTag t, int siegeFinal) {
        clear(l);
        if (t == null || !t.getBoolean("placed")) {   // ancienne sauvegarde (ou îlots pas encore posés) : îlots neufs
            onGrowthDone(l, siegeFinal);
            return;
        }
        seed = t.getLong("seed");
        build(l, siegeFinal);
        Map<String, CompoundTag> byKind = new HashMap<>();
        for (Tag e : t.getList("islets", Tag.TAG_COMPOUND)) byKind.put(((CompoundTag) e).getString("kind"), (CompoundTag) e);
        for (Islet i : ISLETS) {
            CompoundTag o = byKind.get(i.kind.id);
            if (o == null) continue;
            int[] hp = o.getIntArray("hp");
            byte[] down = o.getByteArray("down");
            for (int s = 0; s < SEGMENTS; s++) {
                if (s < hp.length) i.hp[s] = hp[s];
                if (s < down.length) i.down[s] = down[s] != 0;
            }
            byte[] alive = o.getByteArray("veins");
            for (int s = 0; s < i.slots.size(); s++) i.slots.get(s).alive = s >= alive.length || alive[s] != 0;
            i.bought = o.getBoolean("bought");
            if (i.bought) {
                for (Piece pc : i.pieces) {
                    if (i.down[pc.seg()]) continue;
                    l.setBlock(pc.pos(), Block.updateFromNeighbourShapes(pc.state(), l, pc.pos()), 3);
                }
            }
        }
    }

    /** Fin de la Faille (RiftManager.stop) : tout oublier (les blocs sont effacés avec l'île). */
    static void clear(ServerLevel l) {
        if (l != null) {
            for (Islet i : ISLETS) {
                for (Slot s : i.slots) {
                    Entity e = s.entity == null ? null : l.getEntity(s.entity);
                    if (e != null) e.discard();
                }
                for (UUID id : i.guards) { Entity e = l.getEntity(id); if (e != null) e.discard(); }
            }
        }
        ISLETS.clear();
        placed = false;
    }

    // ─── Test (commande /ws rift islets) ───

    static String status() {
        if (!active()) return WSLang.t("rift.islet.cmd_none");
        StringBuilder sb = new StringBuilder();
        for (Islet i : ISLETS) {
            sb.append("§d").append(i.name()).append(" §7(").append(i.side).append(", ").append(i.cx).append(" ").append(i.cz).append(") ");
            sb.append(i.bought ? "§a" : "§8").append(WSLang.t(i.bought ? "rift.islet.cmd_bridge" : "rift.islet.cmd_nobridge"));
            if (i.bought) {
                sb.append(" §7[");
                for (int s = 0; s < SEGMENTS; s++) sb.append(i.down[s] ? "§c✖" : "§a" + Math.round(i.hp[s])).append(s < SEGMENTS - 1 ? "§7 " : "");
                sb.append("§7]");
            }
            int alive = 0;
            for (Slot s : i.slots) if (s.alive) alive++;
            if (i.kind == Kind.MINE) sb.append(" §e⛏ ").append(alive).append("/").append(i.slots.size());
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    /** Test : force la pose des îlots tout de suite (Siège en cours). */
    static boolean forcePlace(ServerLevel l, int siegeFinal) {
        if (placed) return false;
        onGrowthDone(l, siegeFinal);
        onSiegeStart(l, false);
        return true;
    }

    /** Test : abîme ou répare un segment du premier pont acheté. */
    static boolean debugSegment(ServerLevel l, int seg, boolean repair) {
        for (Islet i : ISLETS) {
            if (!i.bought) continue;
            return repair ? repairSegment(l, i.kind, seg) : damageSegment(l, i.kind, seg, 1e9f);
        }
        return false;
    }

    // ─── Événements ───

    @Mod.EventBusSubscriber(modid = WaveSurvivorMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Events {
        private Events() {}

        /** Un filon de l'île minière est épuisé : il pourra repousser ; les cristaux d'Essence paient en Essence. */
        @SubscribeEvent
        public static void onVeinDeath(LivingDeathEvent e) {
            if (!(e.getEntity() instanceof GisementEntity g) || !active()) return;
            for (Islet i : ISLETS) {
                for (Slot s : i.slots) {
                    if (!g.getUUID().equals(s.entity)) continue;
                    s.alive = false;
                    s.entity = null;
                    if ("essence".equals(s.type) && KingdomTreasury.active()) KingdomTreasury.reward(KingdomTreasury.Res.ESSENCE, 6, "rift.mine.reason_essence");
                    return;
                }
            }
        }

        /** Réparation : clic droit avec de la pierre en main près d'un segment effondré. */
        @SubscribeEvent
        public static void onRightClick(PlayerInteractEvent.RightClickBlock e) {
            if (!(e.getEntity() instanceof ServerPlayer p) || !active() || p.level().dimension() != RiftManager.RIFT_DIM) return;
            ItemStack st = p.getItemInHand(e.getHand());
            if (!isStone(st)) return;
            int[] hit = brokenNear(p, 5);
            if (hit == null) return;
            int need = Math.max(1, rs().bridgeRepairStone);
            if (!p.isCreative() && st.getCount() < need) {
                p.displayClientMessage(Component.literal(WSLang.t("rift.bridge.repair_need", need)), true);
                e.setCanceled(true);
                e.setCancellationResult(InteractionResult.FAIL);
                return;
            }
            if (repairSegment((ServerLevel) p.level(), Kind.values()[hit[0]], hit[1])) {
                if (!p.isCreative()) st.shrink(need);
                p.displayClientMessage(Component.literal(WSLang.t("rift.bridge.repaired")), true);
                p.level().playSound(null, p.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.8f, 1.2f);
            }
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
