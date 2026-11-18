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
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
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
 * après le Siège § C, Calme prolongé § E) ; île-autel § 2 (capture, bonus Rempart / Arsenal / Prospérité, reprise à chaque Assaut).
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
        MINE("mine", 18), ALTAR("altar", 12), MERCHANT("merchant", 9);
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
        final List<List<BlockPos>> deck = new ArrayList<>();   // milieu du tablier, par segment (Brise-pont)
        int bStart, bEnd;        // distance au centre du début et de la fin du pont
        // île minière
        final List<Slot> slots = new ArrayList<>();
        final List<UUID> guards = new ArrayList<>();
        // île-autel
        boolean captured;
        Bonus bonus;             // null = pas encore choisi pour cette capture
        float progress;          // 0..1 (capture, puis reprise par les monstres)
        int lit = -1;            // piliers allumés affichés
        boolean beamOn, retakeWarned;
        final List<BlockPos> pillarTops = new ArrayList<>();
        final List<BlockPos> crystal = new ArrayList<>();
        final List<UUID> raiders = new ArrayList<>();
        int top;                 // hauteur du dallage (y monde)
        // cristaux blancs animés (blocs d'affichage) : grand cristal, 4 piliers, mini-cristaux en orbite quand l'autel est capturé
        UUID crystalMain;
        final UUID[] crystalPillar = new UUID[4];
        final List<UUID> orbit = new ArrayList<>();
        boolean[] pillarLit = new boolean[4];
        final java.util.Set<UUID> inCircle = new java.util.HashSet<>();   // joueurs dans le cercle au dernier passage
        boolean mainLit;

        final double sx, sz;     // direction ramenée à l'axe le plus proche (décors en blocs : jamais de trou en diagonale)

        Islet(Kind kind, String side, double dx, double dz, int cx, int cz, int r) {
            this.kind = kind; this.side = side; this.dx = dx; this.dz = dz; this.cx = cx; this.cz = cz; this.r = r;
            if (Math.abs(dx) >= Math.abs(dz)) { this.sx = Math.signum(dx); this.sz = 0; }
            else { this.sx = 0; this.sz = Math.signum(dz); }
        }

        /** Position monde d'un point local (u vers l'extérieur, v de côté) à la hauteur y, dans le repère carré des décors. */
        BlockPos local(double u, int y, double v) {
            return BlockPos.containing(cx + 0.5 + sx * u - sz * v, y, cz + 0.5 + sz * u + sx * v);
        }

        String name() { return WSLang.t("rift.islet." + kind.id); }
    }

    /** Bonus de l'île-autel (un seul actif, choisi à chaque capture). */
    public enum Bonus {
        RAMPART("rampart", "minecraft:shield"), ARSENAL("arsenal", "minecraft:crossbow"), PROSPERITY("prosperity", "minecraft:gold_ingot");
        final String id, icon;
        Bonus(String id, String icon) { this.id = id; this.icon = icon; }
        String label() { return WSLang.t("rift.altar.bonus." + id); }
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
        java.util.Set<String> used = new java.util.HashSet<>();
        if (s.isletMine) out.add(make(Kind.MINE, freeSide(s.isletMineSide, "north", used), s, siegeFinal));
        if (s.isletAltar) out.add(make(Kind.ALTAR, freeSide(s.isletAltarSide, "west", used), s, siegeFinal));
        if (s.isletMerchant) out.add(make(Kind.MERCHANT, freeSide(s.isletMerchantSide, "northwest", used), s, siegeFinal));
        return out;
    }

    private static final String[] SIDES = {"north", "west", "northwest", "southwest", "northeast"};

    /** Côté demandé s'il est libre (et autorisé), sinon le premier côté libre : deux îlots ne se chevauchent jamais. */
    private static String freeSide(String want, String def, java.util.Set<String> used) {
        String w = want == null ? def : want.toLowerCase();
        boolean ok = false;
        for (String sd : SIDES) if (sd.equals(w)) ok = true;
        if (!ok) w = def;
        if (used.add(w)) return w;
        for (String sd : SIDES) if (used.add(sd)) return sd;
        return w;
    }

    static double[] dirOf(String side) { return sideDir(side); }

    /** Côté de l'île au coffre : le premier côté libre après les autres îlots. */
    static String chestSide(HordeConfigMultiData.RiftSettings s, int siegeFinal) {
        java.util.Set<String> used = new java.util.HashSet<>();
        for (Islet i : planned(s, siegeFinal)) used.add(i.side);
        for (String sd : SIDES) if (!used.contains(sd)) return sd;
        return "southwest";
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
        if (s != null && s.siegeEnabled && s.isletsEnabled) {
            int[] ch = RiftChestIslet.forceArea(s, siegeFinal);
            if (ch != null) out.add(ch);
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
            else if (i.kind == Kind.ALTAR) buildAltar(l, i, r);
            else if (i.kind == Kind.MERCHANT) buildMerchant(l, i, r);
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
        clearBreakers(l);   // les Brise-pont redescendent dans le vide
        if (RiftManager.phasePublic() != RiftManager.Phase.SIEGE) clearRaiders(l);   // § C : plus de reprise après le Siège
        else {
            if (get(Kind.MERCHANT) != null) VoidMerchant.onSiegeCalm(l);          // arrivée du Marchand, stock renouvelé
            RiftChestIslet.onSiegeCalm(l);                                         // l'île au coffre (une fois par partie)
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
        boolean vAlongX = Math.abs(i.sz) > Math.abs(i.sx);   // v suit l'axe X quand l'îlot est au nord / au sud
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
            k = (int) Math.round(k * com.wavesurvivor.horde.difficulty.HordeDifficulty.countMultiplier());   // 2.1 : nombre selon la difficulté
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
                com.wavesurvivor.horde.difficulty.HordeDifficulty.applyToUnit(mob);   // 2.1 : PV et dégâts selon la difficulté
                mob.addTag(GUARD_TAG);
                mob.setPersistenceRequired();
                mob.restrictTo(i.local(0, y, 0), i.r);   // restent sur leur île
                if (l.addFreshEntity(mob)) { i.guards.add(mob.getUUID()); n++; }
            }
        }
        if (n > 0) EventFeedPacket.toAll(l.getServer(), Component.literal(WSLang.t("rift.mine.guards")), "minecraft:iron_sword", EventFeedPacket.DANGER, false);
    }

    // ─── Île-autel ───

    private static final BlockState CRYS_OFF = Blocks.TINTED_GLASS.defaultBlockState();
    private static final BlockState CRYS_ON = Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState();
    private static final int BEAM_FROM = 12, BEAM_TO = 44;
    private static final double RING = 5.5;

    /**
     * Île-autel (rayon 12) d'après sources/altar.py : rocher à dessus plat dallé en damier, cercle de capture violet lumineux (rayon 5),
     * estrade à 2 marches aux coins dorés, grand cristal flottant (éteint tant que l'autel n'est pas capturé), 4 piliers sombres
     * coiffés d'un cristal aux points cardinaux (rayon 8), allée dallée vers le pont.
     */
    private static void buildAltar(ServerLevel l, Islet i, Random rnd) {
        int y0 = RiftManager.CENTER.getY();
        int R = i.r;
        i.top = y0 + 1;
        long sA = seed + 61, sB = seed + 73;
        BlockState deep = Blocks.DEEPSLATE.defaultBlockState(), tuff = Blocks.TUFF.defaultBlockState(), moss = Blocks.MOSS_BLOCK.defaultBlockState(),
                polished = Blocks.POLISHED_DEEPSLATE.defaultBlockState(), tiles = Blocks.DEEPSLATE_TILES.defaultBlockState(),
                dark = Blocks.COBBLED_DEEPSLATE.defaultBlockState();
        // rocher, dessus plat ; cône du dessous déchiqueté
        for (int x = i.cx - R - 3; x <= i.cx + R + 3; x++) {
            for (int z = i.cz - R - 3; z <= i.cz + R + 3; z++) {
                double ox = x + 0.5 - (i.cx + 0.5), oz = z + 0.5 - (i.cz + 0.5);
                double r0 = Math.hypot(ox, oz);
                double rr = r0 + (noise(sA, x, z, 6) - 0.5) * 2.4;
                if (rr > R) continue;
                double nB = noise(sB, x, z, 3), nA = noise(sA, x, z, 6);
                BlockState top = r0 <= 9 ? (Math.floorMod(x + z, 2) == 0 ? tiles : polished) : nB > 0.6 ? tuff : nA < 0.3 ? moss : deep;
                set(l, new BlockPos(x, y0 + 1, z), top);
                set(l, new BlockPos(x, y0, z), deep);
                int dep = (int) (3 + 15 * Math.pow(1 - rr / R, 0.9) + (nB - 0.5) * 6);
                if (rnd.nextDouble() < 0.07) dep += 3 + rnd.nextInt(5);
                for (int y = -1; y > -dep; y--) set(l, new BlockPos(x, y0 + y, z), rnd.nextDouble() > 0.25 ? deep : dark);
            }
        }
        // allée dallée vers le pont (côté île principale), dans la vraie direction du pont
        path(l, i, 3, R, y0 + 1, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        // cercle de capture incrusté (rayon 5)
        for (int a = 0; a < 360; a += 3) {
            int x = (int) Math.round(i.cx + 5 * Math.cos(Math.toRadians(a))), z = (int) Math.round(i.cz + 5 * Math.sin(Math.toRadians(a)));
            set(l, new BlockPos(x, y0 + 1, z), Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState());
        }
        // estrade 5 × 5 en 2 marches, coins dorés, autel
        BlockState step = Blocks.POLISHED_DEEPSLATE.defaultBlockState(), altar = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
                gold = Blocks.GOLD_BLOCK.defaultBlockState();
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) set(l, new BlockPos(i.cx + dx, y0 + 2, i.cz + dz), step);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) set(l, new BlockPos(i.cx + dx, y0 + 3, i.cz + dz), altar);
        for (int[] c : new int[][]{{-2, -2}, {-2, 2}, {2, -2}, {2, 2}}) set(l, new BlockPos(i.cx + c[0], y0 + 3, i.cz + c[1]), gold);
        set(l, new BlockPos(i.cx, y0 + 4, i.cz), Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
        set(l, new BlockPos(i.cx, y0 + 5, i.cz), Blocks.GILDED_BLACKSTONE.defaultBlockState());
        // grand cristal flottant
        i.crystal.clear();
        int[][] cr = {{0, 7, 0}, {0, 8, 0}, {0, 9, 0}, {0, 10, 0}, {1, 8, 0}, {-1, 8, 0}, {0, 8, 1}, {0, 8, -1}, {1, 9, 0}, {-1, 9, 0}, {0, 9, 1}, {0, 9, -1}};
        for (int[] c : cr) i.crystal.add(new BlockPos(i.cx + c[0], y0 + c[1], i.cz + c[2]));
        // 4 piliers aux points cardinaux (rayon 8), sommet en cristal
        i.pillarTops.clear();
        BlockState pillar = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        for (int[] c : new int[][]{{0, -8}, {8, 0}, {0, 8}, {-8, 0}}) {
            int x = i.cx + c[0], z = i.cz + c[1];
            for (int y = 2; y <= 6; y++) set(l, new BlockPos(x, y0 + y, z), pillar);
            set(l, new BlockPos(x, y0 + 7, z), Blocks.GILDED_BLACKSTONE.defaultBlockState());
            i.pillarTops.add(new BlockPos(x, y0 + 8, z));   // cristal animé posé au-dessus (bloc d'affichage)
        }
        // anciens cristaux en blocs (sauvegardes d'avant les cristaux animés) : retirés
        for (BlockPos p : i.crystal) set(l, p, Blocks.AIR.defaultBlockState());
        for (BlockPos p : i.pillarTops) set(l, p, Blocks.AIR.defaultBlockState());
        i.captured = false;
        i.bonus = null;
        i.progress = 0f;
        i.lit = -1;
        i.beamOn = true;   // force l'effacement du faisceau au premier affichage
        altarVisual(l, i);
    }

    /** Piliers allumés selon la progression, cristal et faisceau selon la capture. */
    private static void altarVisual(ServerLevel l, Islet i) {
        int n = i.captured ? (int) Math.ceil(i.progress * 4 - 1e-4) : (int) Math.floor(i.progress * 4 + 1e-4);
        n = Math.max(0, Math.min(4, n));
        if (n != i.lit) {
            for (int k = 0; k < i.pillarTops.size() && k < 4; k++) i.pillarLit[k] = k < n;
            if (i.lit >= 0 && n > i.lit) l.playSound(null, i.pillarTops.get(Math.min(3, n - 1)), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 2.5f, 0.7f + 0.15f * n);
            i.lit = n;
        }
        boolean on = i.captured;
        i.mainLit = on;
        crystals(l, i, l.getGameTime(), true);
        if (i.beamOn) {   // ancien faisceau en barres de l'End (retiré) : effacé s'il reste des blocs
            for (int y = BEAM_FROM; y <= BEAM_TO; y++) {
                BlockPos p = new BlockPos(i.cx, RiftManager.CENTER.getY() + y, i.cz);
                if (l.getBlockState(p).is(Blocks.END_ROD)) l.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
            i.beamOn = false;
        }
    }

    // ─── Cristaux blancs animés (même modèle facetté que le cristal de la Mairie) ───

    private static BlockState crystal(boolean lit) {
        return com.wavesurvivor.registry.ModBlocks.ALTAR_CRYSTAL.get().defaultBlockState().setValue(AltarCrystalBlock.LIT, lit);
    }

    private static Display.BlockDisplay display(ServerLevel l, UUID id) {
        return id != null && l.getEntity(id) instanceof Display.BlockDisplay d && d.isAlive() ? d : null;
    }

    /** Crée un cristal (bloc d'affichage) centré en (x, y, z), de taille s. */
    private static Display.BlockDisplay spawnCrystal(ServerLevel l, double x, double y, double z, float s, boolean lit) {
        Display.BlockDisplay d = IceFx.spawn(l, x, y, z, crystal(lit), new Quaternionf(), s, s, s, 0f);
        if (d == null) return null;
        CompoundTag t = d.saveWithoutId(new CompoundTag());
        t.putFloat("view_range", 6f);
        d.load(t);
        setLit(d, lit);
        return d;
    }

    private static void setLit(Display.BlockDisplay d, boolean lit) {
        CompoundTag t = d.saveWithoutId(new CompoundTag());
        t.put("block_state", net.minecraft.nbt.NbtUtils.writeBlockState(crystal(lit)));
        CompoundTag light = new CompoundTag();
        light.putInt("sky", lit ? 15 : 7);
        light.putInt("block", lit ? 15 : 0);
        t.put("brightness", light);
        d.load(t);
    }

    /** Transformation d'un cristal : rotation autour de son axe vertical, décalage (dx, lift, dz), taille s, interpolée. */
    private static void spin(Display.BlockDisplay d, float angleDeg, float s, float ox, float lift, float oz, int dur) {
        Quaternionf q = new Quaternionf().rotationY((float) Math.toRadians(angleDeg));
        org.joml.Vector3f tr = q.transform(new org.joml.Vector3f(-s / 2f, 0f, -s / 2f)).add(ox, lift, oz);
        CompoundTag tag = d.saveWithoutId(new CompoundTag());
        CompoundTag tf = new CompoundTag();
        tf.put("translation", floats3(tr.x, tr.y, tr.z));
        ListTag lr = new ListTag();
        for (float f : new float[]{q.x, q.y, q.z, q.w}) lr.add(net.minecraft.nbt.FloatTag.valueOf(f));
        tf.put("left_rotation", lr);
        tf.put("scale", floats3(s, s, s));
        ListTag rr = new ListTag();
        for (float f : new float[]{0, 0, 0, 1}) rr.add(net.minecraft.nbt.FloatTag.valueOf(f));
        tf.put("right_rotation", rr);
        tag.put("transformation", tf);
        tag.putInt("interpolation_duration", Math.max(0, dur));
        tag.putInt("start_interpolation", 0);
        d.load(tag);
    }

    private static ListTag floats3(float a, float b, float c) {
        ListTag l = new ListTag();
        l.add(net.minecraft.nbt.FloatTag.valueOf(a));
        l.add(net.minecraft.nbt.FloatTag.valueOf(b));
        l.add(net.minecraft.nbt.FloatTag.valueOf(c));
        return l;
    }

    /**
     * Cristaux de l'autel : le grand cristal flotte et tourne (allumé quand l'autel est capturé, avec 4 mini-cristaux en orbite),
     * un petit cristal sur chaque pilier (allumé selon la progression). {@code stateOnly} : met juste à jour l'allumage.
     */
    private static void crystals(ServerLevel l, Islet i, long now, boolean stateOnly) {
        double cx = i.cx + 0.5, cz = i.cz + 0.5, cy = RiftManager.CENTER.getY() + 7.0;
        float t = now;
        // grand cristal
        Display.BlockDisplay m = display(l, i.crystalMain);
        if (m == null) { m = spawnCrystal(l, cx, cy, cz, 3f, i.mainLit); if (m != null) i.crystalMain = m.getUUID(); }
        else if (stateOnly) setLit(m, i.mainLit);
        if (m != null && !stateOnly) spin(m, t * 1.2f, 3f, 0f, Mth.sin(t * 0.05f) * 0.15f, 0f, 20);
        // piliers
        for (int k = 0; k < 4 && k < i.pillarTops.size(); k++) {
            BlockPos p = i.pillarTops.get(k);
            Display.BlockDisplay d = display(l, i.crystalPillar[k]);
            if (d == null) { d = spawnCrystal(l, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 1f, i.pillarLit[k]); if (d != null) i.crystalPillar[k] = d.getUUID(); }
            else if (stateOnly) setLit(d, i.pillarLit[k]);
            if (d != null && !stateOnly) spin(d, t * (i.pillarLit[k] ? 2.4f : 0.6f) + k * 30f, 1f, 0f, Mth.sin(t * 0.07f + k) * 0.08f, 0f, 20);
        }
        // mini-cristaux en orbite (autel capturé)
        if (!i.mainLit) {
            for (UUID id : i.orbit) { Display.BlockDisplay d = display(l, id); if (d != null) IceFx.remove(d); }
            i.orbit.clear();
        } else {
            i.orbit.removeIf(id -> display(l, id) == null);
            while (i.orbit.size() < 4) {
                Display.BlockDisplay d = spawnCrystal(l, cx, cy, cz, 0.7f, true);
                if (d == null) break;
                i.orbit.add(d.getUUID());
            }
            if (!stateOnly) {
                for (int k = 0; k < i.orbit.size(); k++) {
                    Display.BlockDisplay d = display(l, i.orbit.get(k));
                    if (d == null) continue;
                    double a = Math.toRadians(t * 1.6f + k * 90f);
                    float r = 3.2f + (k % 2) * 0.5f;
                    spin(d, -t * 2f + k * 40f, 0.7f, (float) Math.cos(a) * r, 0.6f + Mth.sin(t * 0.07f + k) * 0.6f, (float) Math.sin(a) * r, 20);
                }
            }
        }
    }

    private static void removeCrystals(ServerLevel l, Islet i) {
        Display.BlockDisplay m = display(l, i.crystalMain);
        if (m != null) IceFx.remove(m);
        for (UUID id : i.crystalPillar) { Display.BlockDisplay d = display(l, id); if (d != null) IceFx.remove(d); }
        for (UUID id : i.orbit) { Display.BlockDisplay d = display(l, id); if (d != null) IceFx.remove(d); }
        i.orbit.clear();
    }

    private static float captureSeconds(int players) {
        float base = Math.max(5, Math.min(600, rs().altarCaptureSeconds));
        return base / (1f + (Math.max(1, players) - 1) / 3f);   // 30 s seul, ~15 s à 4
    }

    /** Toutes les 10 ticks : capture (joueurs dans le cercle), reprise (monstres seuls dans le cercle), bonus Rempart, raid. */
    private static void altarTick(ServerLevel l, long now) {
        Islet a = get(Kind.ALTAR);
        if (a == null) return;
        RiftManager.Phase ph = RiftManager.phasePublic();
        boolean siege = ph == RiftManager.Phase.SIEGE;
        boolean open = siege || ph == RiftManager.Phase.COUNTER || ph == RiftManager.Phase.TITAN;
        double cx = a.cx + 0.5, cz = a.cz + 0.5;
        AABB box = new AABB(cx - RING, a.top - 1, cz - RING, cx + RING, a.top + 7, cz + RING);
        List<ServerPlayer> in = new ArrayList<>();
        for (ServerPlayer p : l.players()) {
            if (p.isSpectator() || !p.isAlive() || !box.contains(p.position())) continue;
            if (Math.hypot(p.getX() - cx, p.getZ() - cz) <= RING) in.add(p);
        }
        int mobs = 0;
        for (Mob m : l.getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && m instanceof Enemy && !(m instanceof GisementEntity))) {
            if (Math.hypot(m.getX() - cx, m.getZ() - cz) <= RING) mobs++;
        }
        float dt = 0.5f;
        if (open) {
            if (!a.captured) {
                if (!in.isEmpty() && mobs == 0) {
                    a.progress = Math.min(1f, a.progress + dt / captureSeconds(in.size()));
                    if (a.progress >= 1f) capture(l, a);
                }
                if (!a.captured) for (ServerPlayer p : in) p.displayClientMessage(Component.literal(mobs > 0 ? WSLang.t("rift.altar.frozen")
                        : WSLang.t("rift.altar.capturing", Math.round(a.progress * 100))), true);
            } else if (siege && in.isEmpty() && mobs > 0) {
                float ret = Math.max(5, Math.min(600, rs().altarRetakeSeconds));
                a.progress = Math.max(0f, a.progress - dt / ret);
                if (!a.retakeWarned) {
                    a.retakeWarned = true;
                    EventFeedPacket.toAll(l.getServer(), Component.literal(WSLang.t("rift.altar.retaking")), "minecraft:amethyst_shard", EventFeedPacket.DANGER, true);
                }
                if (a.progress <= 0f) lose(l, a);
            } else if (a.progress < 1f && !in.isEmpty() && mobs == 0) {   // les joueurs reviennent : l'autel se raffermit
                a.progress = Math.min(1f, a.progress + dt / captureSeconds(in.size()));
                if (a.progress >= 1f) a.retakeWarned = false;
                for (ServerPlayer p : in) p.displayClientMessage(Component.literal(WSLang.t("rift.altar.capturing", Math.round(a.progress * 100))), true);
            }
        }
        altarVisual(l, a);
        if (now % 20 == 0) crystals(l, a, now, false);   // les cristaux tournent (interpolés côté client)
        // autel capturé : quelques étincelles autour du cristal
        if (a.captured && now % 20 == 0) l.sendParticles(ParticleTypes.END_ROD, cx, a.top + 7.5, cz, 3, 1.2, 1.2, 1.2, 0.01);
        // bonus pas encore choisi : l'écran des cartes revient dès qu'un joueur entre dans le cercle
        java.util.Set<UUID> nowIn = new java.util.HashSet<>();
        for (ServerPlayer p : in) {
            nowIn.add(p.getUUID());
            if (a.captured && a.bonus == null && !a.inCircle.contains(p.getUUID())) sendChoice(p);
        }
        a.inCircle.clear();
        a.inCircle.addAll(nowIn);
        // Rempart : le Monolithe régénère 1 % de ses PV toutes les 10 s (pendant le Siège)
        if (siege && a.captured && a.bonus == Bonus.RAMPART && now % 200 == 0 && com.wavesurvivor.altar.AltarDefense.isActive()) {
            float pct = Math.max(0, Math.min(20, rs().altarRampartPct)) / 100f;
            com.wavesurvivor.altar.AltarDefense.healMonolith(com.wavesurvivor.altar.AltarDefense.monolithMaxHp() * pct);
        }
        // les monstres du raid marchent vers le cercle quand ils n'ont personne à frapper
        if (now % 20 == 0 && !a.raiders.isEmpty()) {
            a.raiders.removeIf(id -> !(l.getEntity(id) instanceof Mob m) || !m.isAlive());
            for (UUID id : a.raiders) {
                if (!(l.getEntity(id) instanceof Mob m)) continue;
                if (m.getTarget() != null && m.getTarget().isAlive() && m.distanceToSqr(m.getTarget()) < 24 * 24) continue;
                if (Math.hypot(m.getX() - cx, m.getZ() - cz) > 2.5) m.getNavigation().moveTo(cx + RNG.nextInt(5) - 2, a.top + 1, cz + RNG.nextInt(5) - 2, 1.0);
            }
        }
    }

    private static void capture(ServerLevel l, Islet a) {
        a.captured = true;
        a.bonus = null;
        a.progress = 1f;
        a.retakeWarned = false;
        altarVisual(l, a);
        BlockPos at = new BlockPos(a.cx, a.top + 8, a.cz);
        l.playSound(null, at, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 4f, 0.8f);
        l.playSound(null, at, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 4f, 1f);
        l.sendParticles(ParticleTypes.END_ROD, a.cx + 0.5, a.top + 8.5, a.cz + 0.5, 80, 1.5, 1.5, 1.5, 0.15);
        EventFeedPacket.toAll(l.getServer(), Component.literal(WSLang.t("rift.altar.captured")), "minecraft:amethyst_cluster", EventFeedPacket.RESOURCE, true);
        // lien dans le chat pour tous : rouvrir l'écran (après Échap par exemple)
        net.minecraft.network.chat.MutableComponent link = Component.literal("§d✦ " + WSLang.t("rift.altar.reopen_hint") + " ")
                .append(Component.literal("§e§l[" + WSLang.t("rift.altar.reopen_btn") + "]").withStyle(st -> st.withClickEvent(
                        new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/wavesurvivor riftaltar"))));
        for (ServerPlayer p : l.players()) p.sendSystemMessage(link);
        // ceux qui ont capturé : écran à 3 cartes tout de suite ; les autres : en entrant dans le cercle, clic droit sur l'autel ou le lien
        double cx = a.cx + 0.5, cz = a.cz + 0.5;
        for (ServerPlayer p : l.players()) {
            if (!p.isSpectator() && Math.hypot(p.getX() - cx, p.getZ() - cz) <= RING + 1 && Math.abs(p.getY() - a.top) < 8) sendChoice(p);
        }
    }

    private static void lose(ServerLevel l, Islet a) {
        Bonus old = a.bonus;
        a.captured = false;
        a.bonus = null;
        a.progress = 0f;
        a.retakeWarned = false;
        altarVisual(l, a);
        l.playSound(null, new BlockPos(a.cx, a.top + 8, a.cz), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 4f, 0.7f);
        EventFeedPacket.toAll(l.getServer(), Component.literal(WSLang.t("rift.altar.lost", old == null ? "—" : old.label())), "minecraft:tinted_glass", EventFeedPacket.DANGER, true);
    }

    /** Écran de choix du bonus (3 cartes, comme une bénédiction). */
    private static void sendChoice(ServerPlayer p) {
        HordeConfigMultiData.RiftSettings s = rs();
        com.wavesurvivor.network.NetworkHandler.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p),
                new com.wavesurvivor.network.AltarBonusPackets.Open(s.altarRampartPct, s.altarArsenalPct, s.altarProsperityPct));
    }

    /** Ligne de choix cliquable (secours : commande ouverte à tous /wavesurvivor riftaltar <0-2>). */
    @SuppressWarnings("unused")
    private static void sendChoiceChat(ServerPlayer p) {
        net.minecraft.network.chat.MutableComponent line = Component.literal("§d✦ " + WSLang.t("rift.altar.choose") + " ");
        HordeConfigMultiData.RiftSettings s = rs();
        int[] vals = {s.altarRampartPct, s.altarArsenalPct, s.altarProsperityPct};
        for (Bonus b : Bonus.values()) {
            final String cmd = "/wavesurvivor riftaltar " + b.ordinal();
            final Component tip = Component.literal(WSLang.t("rift.altar.bonus." + b.id + ".desc", vals[b.ordinal()]));
            line.append(Component.literal("§e§l[" + b.label() + "]  ").withStyle(st -> st
                    .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, cmd))
                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, tip))));
        }
        p.sendSystemMessage(line);
    }

    /** Rouvre l'écran des cartes (commande /wavesurvivor riftaltar, lien dans le chat). */
    public static void reopenChoice(ServerPlayer p) {
        Islet a = get(Kind.ALTAR);
        if (a == null || !a.captured) { p.displayClientMessage(Component.literal(WSLang.t("rift.altar.not_captured")), true); return; }
        if (a.bonus != null) { p.displayClientMessage(Component.literal(WSLang.t("rift.altar.already", a.bonus.label())), true); return; }
        sendChoice(p);
    }

    /** Choix du bonus (écran des cartes) : un seul par capture, pour toute l'équipe. */
    public static void chooseBonus(ServerPlayer p, int idx) {
        Islet a = get(Kind.ALTAR);
        if (a == null || !a.captured) { p.displayClientMessage(Component.literal(WSLang.t("rift.altar.not_captured")), true); return; }
        if (a.bonus != null) { p.displayClientMessage(Component.literal(WSLang.t("rift.altar.already", a.bonus.label())), true); return; }
        if (idx < 0 || idx >= Bonus.values().length || p.level().dimension() != RiftManager.RIFT_DIM) return;
        a.bonus = Bonus.values()[idx];
        if (p.level() instanceof ServerLevel l) {
            EventFeedPacket.toAll(l.getServer(), Component.literal(WSLang.t("rift.altar.chosen", p.getName().getString(), a.bonus.label())), a.bonus.icon, EventFeedPacket.RESOURCE, true);
            l.playSound(null, new BlockPos(a.cx, a.top + 8, a.cz), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 4f, 1.2f);
        }
    }

    /** Début d'un Assaut du Siège (KingdomManager.startAssault) : un groupe de monstres part vers l'île-autel. */
    public static void onAssault(MinecraftServer server) {
        ServerLevel cl = server.getLevel(RiftManager.RIFT_DIM);
        if (cl != null && active()) RiftChestIslet.onAssault(cl);   // l'île au coffre repart
        if (cl != null && active() && RiftManager.phasePublic() == RiftManager.Phase.SIEGE) spawnBreakers(cl, server.getTickCount());   // Brise-pont (horloge du serveur, comme tick())
        Islet a = get(Kind.ALTAR);
        if (a == null || RiftManager.phasePublic() != RiftManager.Phase.SIEGE) return;
        ServerLevel l = server.getLevel(RiftManager.RIFT_DIM);
        HordeConfigMultiData.RiftSettings s = rs();
        if (l == null || s.altarRaidSize <= 0 || s.altarRaidMobs == null || s.altarRaidMobs.isEmpty()) return;
        clearRaiders(l);
        int total = Math.min(24, s.altarRaidSize);
        int[] vs = {-6, -2, 2, 6};   // 4 points d'arrivée, côté opposé au pont
        for (int k = 0; k < total; k++) {
            ResourceLocation id = ResourceLocation.tryParse(s.altarRaidMobs.get(RNG.nextInt(s.altarRaidMobs.size())));
            if (id == null || !ForgeRegistries.ENTITY_TYPES.containsKey(id)) continue;
            Entity e = ForgeRegistries.ENTITY_TYPES.getValue(id).create(l);
            if (!(e instanceof Mob mob)) { if (e != null) e.discard(); continue; }
            BlockPos p = a.local(a.r - 3, 0, vs[k % 4] + RNG.nextInt(3) - 1);
            int y = top(l, p);
            if (y == Integer.MIN_VALUE) { e.discard(); continue; }
            mob.moveTo(p.getX() + 0.5, y + 1, p.getZ() + 0.5, RNG.nextFloat() * 360f, 0f);
            mob.finalizeSpawn(l, l.getCurrentDifficultyAt(p), MobSpawnType.EVENT, null, null);
            mob.addTag(GUARD_TAG);
            mob.setPersistenceRequired();
            mob.restrictTo(new BlockPos(a.cx, a.top + 1, a.cz), 4);
            if (l.addFreshEntity(mob)) {
                a.raiders.add(mob.getUUID());
                l.sendParticles(ParticleTypes.PORTAL, mob.getX(), mob.getY() + 1, mob.getZ(), 30, 0.4, 0.8, 0.4, 0.3);
            }
        }
        if (!a.raiders.isEmpty()) EventFeedPacket.toAll(server, Component.literal(WSLang.t("rift.altar.raid")), "minecraft:iron_sword", EventFeedPacket.DANGER, true);
    }

    private static void clearRaiders(ServerLevel l) {
        Islet a = get(Kind.ALTAR);
        if (a == null) return;
        for (UUID id : a.raiders) { Entity e = l.getEntity(id); if (e != null) e.discard(); }
        a.raiders.clear();
    }

    private static Bonus activeBonus() {
        Islet a = get(Kind.ALTAR);
        return a != null && a.captured ? a.bonus : null;
    }

    /** Arsenal : multiplicateur des dégâts des engins de siège (RiftEngines). */
    public static float arsenalMul() {
        return activeBonus() == Bonus.ARSENAL ? 1f + Math.max(0, Math.min(200, rs().altarArsenalPct)) / 100f : 1f;
    }

    /** Prospérité : ressources gagnées +25 % (arrondi au hasard, pour que les petits gains en profitent aussi). */
    public static int prosper(int n) {
        if (n <= 0 || activeBonus() != Bonus.PROSPERITY) return n;
        double v = n * (1 + Math.max(0, Math.min(200, rs().altarProsperityPct)) / 100.0);
        int base = (int) Math.floor(v);
        return base + (RNG.nextDouble() < v - base ? 1 : 0);
    }

    // ─── Île du Marchand du Néant ───

    /**
     * Île du Marchand (rayon 9) d'après sources/merchant.py : mousse et roche, tapis violet devant l'échoppe ; auvent en toile violette
     * sur la moitié arrière, étagères (améthyste, coffres), comptoir avec marchandises, gros cristal d'améthyste flottant (enseigne),
     * lanternes de cristal flottantes, allée vers le pont. Le marchand lui-même arrive au Calme prévu (VoidMerchant).
     */
    private static void buildMerchant(ServerLevel l, Islet i, Random rnd) {
        int y0 = RiftManager.CENTER.getY();
        int R = i.r;
        i.top = y0 + 1;
        long sA = seed + 81, sB = seed + 93;
        BlockState deep = Blocks.DEEPSLATE.defaultBlockState(), tuff = Blocks.TUFF.defaultBlockState(), moss = Blocks.MOSS_BLOCK.defaultBlockState(),
                dark = Blocks.COBBLED_DEEPSLATE.defaultBlockState();
        for (int x = i.cx - R - 3; x <= i.cx + R + 3; x++) {
            for (int z = i.cz - R - 3; z <= i.cz + R + 3; z++) {
                double ox = x + 0.5 - (i.cx + 0.5), oz = z + 0.5 - (i.cz + 0.5);
                double rr = Math.hypot(ox, oz) + (noise(sA, x, z, 6) - 0.5) * 2.4;
                if (rr > R) continue;
                double nA = noise(sA, x, z, 6), nB = noise(sB, x, z, 3);
                set(l, new BlockPos(x, y0 + 1, z), nA < 0.32 ? moss : nB > 0.62 ? tuff : deep);
                set(l, new BlockPos(x, y0, z), deep);
                int dep = (int) (3 + 13 * Math.pow(1 - rr / R, 0.9) + (nB - 0.5) * 5);
                if (rnd.nextDouble() < 0.08) dep += 3 + rnd.nextInt(4);
                for (int y = -1; y > -dep; y--) set(l, new BlockPos(x, y0 + y, z), rnd.nextDouble() > 0.25 ? deep : dark);
            }
        }
        // repère local du plan : px = −u (vers le pont), pz = v
        java.util.function.BiFunction<Integer, Integer, BlockPos> at = (px, pz) -> i.local(-px, 0, pz);
        java.util.function.Consumer<Object[]> put = o -> {   // {px, y (plan), pz, état}
            BlockPos c = at.apply((Integer) o[0], (Integer) o[2]);
            set(l, new BlockPos(c.getX(), y0 + (Integer) o[1], c.getZ()), (BlockState) o[3]);
        };
        BlockState carpet = Blocks.PURPLE_WOOL.defaultBlockState(), carpetD = Blocks.BLACK_WOOL.defaultBlockState(),
                canvas = Blocks.PURPLE_WOOL.defaultBlockState(), canvasD = Blocks.MAGENTA_WOOL.defaultBlockState(),
                post = Blocks.DARK_OAK_LOG.defaultBlockState(), plank = Blocks.DARK_OAK_PLANKS.defaultBlockState(),
                cluster = Blocks.AMETHYST_CLUSTER.defaultBlockState(), chest = Blocks.BARREL.defaultBlockState(),
                essence = Blocks.SEA_LANTERN.defaultBlockState(), gold = Blocks.GOLD_BLOCK.defaultBlockState(),
                amethyst = Blocks.AMETHYST_BLOCK.defaultBlockState(), lamp = Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState();
        // tapis devant l'échoppe
        for (int px = 0; px < 6; px++) for (int pz = -2; pz <= 2; pz++) put.accept(new Object[]{px, 1, pz, Math.floorMod(px + pz, 2) == 1 ? carpet : carpetD});
        // échoppe : auvent sur la moitié arrière
        int sx0 = -6, sx1 = -2, sz0 = -3, sz1 = 3;
        for (int[] c : new int[][]{{sx0, sz0}, {sx0, sz1}, {sx1, sz0}, {sx1, sz1}}) for (int y = 2; y <= 6; y++) put.accept(new Object[]{c[0], y, c[1], post});
        for (int pz = sz0 - 1; pz <= sz1 + 1; pz++) {
            for (int px = sx0 - 1; px <= sx1; px++) put.accept(new Object[]{px, px >= sx1 - 1 ? 7 : 8, pz, Math.floorMod(pz, 2) == 0 ? canvas : canvasD});
            put.accept(new Object[]{sx1 + 1, 6, pz, Math.floorMod(pz, 2) == 1 ? canvasD : canvas});   // lambrequin
        }
        // étagères au fond
        for (int pz = sz0 + 1; pz < sz1; pz++) {
            put.accept(new Object[]{sx0, 2, pz, plank});
            put.accept(new Object[]{sx0, 4, pz, plank});
            put.accept(new Object[]{sx0, 3, pz, Math.floorMod(pz, 2) == 1 ? cluster : chest});
            put.accept(new Object[]{sx0, 5, pz, pz == 0 ? essence : gold});
        }
        // place du marchand derrière le comptoir, tourné vers le pont
        int mx = sx1 + 1;
        BlockPos m = at.apply(mx, 0);
        float facing = (float) Math.toDegrees(Math.atan2(i.sx, -i.sz));
        VoidMerchant.place(new BlockPos(m.getX(), y0 + 2, m.getZ()), facing);
        // comptoir avec marchandises
        int cxp = mx + 2;
        for (int pz = -2; pz <= 2; pz++) put.accept(new Object[]{cxp, 2, pz, plank});
        put.accept(new Object[]{cxp, 3, -2, cluster});
        put.accept(new Object[]{cxp, 3, -1, gold});
        put.accept(new Object[]{cxp, 3, 1, chest});
        put.accept(new Object[]{cxp, 3, 2, essence});
        // gros cristal d'améthyste flottant (enseigne)
        for (int[] c : new int[][]{{0, 0, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}}) put.accept(new Object[]{-4 + c[0], 11 + c[1], c[2], amethyst});
        put.accept(new Object[]{-4, 12, 0, lamp});
        put.accept(new Object[]{-4, 10, 0, lamp});
        put.accept(new Object[]{-4, 13, 0, amethyst});
        // tonneaux, caisses autour
        for (int[] c : new int[][]{{cxp + 1, -4, 0}, {cxp + 2, -4, 1}, {cxp + 1, -5, 0}, {cxp + 1, 4, 0}, {cxp + 2, 5, 2}}) {
            put.accept(new Object[]{c[0], 2, c[1], c[2] == 1 ? plank : c[2] == 2 ? amethyst : chest});
        }
        // lanternes de cristal flottantes
        for (int[] c : new int[][]{{3, -6, 5}, {-2, -7, 6}, {6, 5, 4}, {-6, 6, 6}, {1, 7, 5}, {-8, 0, 7}}) put.accept(new Object[]{c[0], c[2], c[1], lamp});
        // allée vers le pont (vraie direction du pont : en diagonale aussi)
        path(l, i, 5, R, y0 + 1, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
    }

    /** Kit de pont : reconstruit le segment effondré le plus proche (à 8 blocs). */
    public static boolean kitRepair(ServerPlayer p) {
        if (!active() || p.level().dimension() != RiftManager.RIFT_DIM) { p.displayClientMessage(Component.literal(WSLang.t("item.kit_pont.none")), true); return false; }
        int[] hit = brokenNear(p, 8);
        if (hit == null || !repairSegment(p.serverLevel(), Kind.values()[hit[0]], hit[1])) {
            p.displayClientMessage(Component.literal(WSLang.t("item.kit_pont.none")), true);
            return false;
        }
        p.displayClientMessage(Component.literal(WSLang.t("rift.bridge.repaired")), true);
        p.level().playSound(null, p.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.8f, 1.2f);
        return true;
    }

    // ─── Brise-pont (§ 8) ───

    /** Un Brise-pont annoncé : particules violettes et raclement sous le segment, puis il grimpe. */
    private record Pending(Islet islet, int seg, BlockPos deck, long when) {}

    private static final List<Pending> PENDING = new ArrayList<>();
    private static final List<UUID> BREAKERS = new ArrayList<>();
    private static final net.minecraft.core.particles.DustParticleOptions VIOLET =
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.69f, 0.3f, 1f), 1.6f);

    private static BlockPos deckCenter(Islet i, int seg) {
        List<BlockPos> d = i.deck.get(seg);
        return d.isEmpty() ? null : d.get(d.size() / 2);
    }

    private static int nextIntact(Islet i, int seg) {
        for (int s = seg + 1; s < SEGMENTS; s++) if (!i.down[s]) return s;   // vers l'îlot d'abord
        for (int s = seg - 1; s >= 0; s--) if (!i.down[s]) return s;
        return -1;
    }

    /** Début d'un Assaut du Siège : 1 à 2 Brise-pont par pont construit (3 en même temps au plus). */
    private static void spawnBreakers(ServerLevel l, long now) {
        HordeConfigMultiData.RiftSettings s = rs();
        if (!s.breakerEnabled) return;
        BREAKERS.removeIf(id -> !(l.getEntity(id) instanceof com.wavesurvivor.entity.BrisePont b) || !b.isAlive());
        int room = Math.max(0, s.breakerMax) - BREAKERS.size() - PENDING.size();
        for (Islet i : ISLETS) {
            if (!i.bought || room <= 0) continue;
            List<Integer> intact = new ArrayList<>();
            for (int k = 0; k < SEGMENTS; k++) if (!i.down[k] && deckCenter(i, k) != null) intact.add(k);
            if (intact.isEmpty()) continue;
            int lo = Math.max(0, s.breakerMin), hi = Math.max(lo, s.breakerPer);
            int n = Math.min(room, lo + RNG.nextInt(hi - lo + 1));
            for (int k = 0; k < n; k++) {
                int seg = intact.get(RNG.nextInt(intact.size()));
                PENDING.add(new Pending(i, seg, deckCenter(i, seg), now + 60 + k * 40L));
                room--;
            }
        }
    }

    private static Vec3 below(Islet i, BlockPos deck) {
        double side = RNG.nextBoolean() ? 3.5 : -3.5;
        return new Vec3(deck.getX() + 0.5 - i.dz * side, deck.getY() - 5, deck.getZ() + 0.5 + i.dx * side);
    }

    /** Chaque tick : annonces, apparitions, rongement (toutes les secondes). */
    private static void breakerTick(ServerLevel l, long now) {
        for (java.util.Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
            Pending pd = it.next();
            BlockPos d = pd.deck();
            if (now % 5 == 0) {
                l.sendParticles(VIOLET, d.getX() + 0.5, d.getY() - 2, d.getZ() + 0.5, 12, 1.2, 1.5, 1.2, 0.02);
                l.sendParticles(ParticleTypes.REVERSE_PORTAL, d.getX() + 0.5, d.getY() - 3, d.getZ() + 0.5, 6, 1, 1, 1, 0.05);
            }
            if (now % 15 == 0) l.playSound(null, d, SoundEvents.GRINDSTONE_USE, SoundSource.HOSTILE, 1.2f, 0.5f);
            if (now < pd.when()) continue;
            it.remove();
            if (!pd.islet().bought || pd.islet().down[pd.seg()]) continue;
            com.wavesurvivor.entity.BrisePont b = com.wavesurvivor.registry.ModEntities.BRISE_PONT.get().create(l);
            if (b == null) continue;
            Vec3 from = below(pd.islet(), d);
            var a = b.getAttribute(Attributes.MAX_HEALTH);
            if (a != null) { a.setBaseValue(Math.max(4, Math.min(2000, rs().breakerHp))); b.setHealth(b.getMaxHealth()); }
            float yaw = (float) Math.toDegrees(Math.atan2(-(d.getX() + 0.5 - from.x), d.getZ() + 0.5 - from.z));
            b.moveTo(from.x, from.y, from.z, yaw, 0f);
            b.isletKind = pd.islet().kind.ordinal();
            b.segment = pd.seg();
            b.addTag(GUARD_TAG);
            b.startClimb(from, new Vec3(d.getX() + 0.5, d.getY() + 1, d.getZ() + 0.5), d.getY());
            if (l.addFreshEntity(b)) {
                BREAKERS.add(b.getUUID());
                EventFeedPacket.toAll(l.getServer(), Component.literal(WSLang.t("rift.breaker.climb", pd.islet().name())), "minecraft:cracked_deepslate_bricks", EventFeedPacket.DANGER, true);
            }
        }
        if (now % 20 != 0 || BREAKERS.isEmpty()) return;
        float dps = segmentHp() / Math.max(3, Math.min(120, rs().breakerGnawSeconds));
        for (java.util.Iterator<UUID> it = BREAKERS.iterator(); it.hasNext(); ) {
            if (!(l.getEntity(it.next()) instanceof com.wavesurvivor.entity.BrisePont b) || !b.isAlive()) { it.remove(); continue; }
            if (b.climbing() || !b.gnawing()) continue;
            Islet i = b.isletKind >= 0 && b.isletKind < Kind.values().length ? get(Kind.values()[b.isletKind]) : null;
            if (i == null || !i.bought) { b.releaseBridge(); it.remove(); continue; }
            // segment sous ses pattes (il a pu être poussé)
            int seg = b.segment;
            double best = 9;
            for (int k = 0; k < SEGMENTS; k++) {
                for (BlockPos p : i.deck.get(k)) {
                    double dd = p.distToCenterSqr(b.getX(), p.getY() + 0.5, b.getZ());
                    if (dd < best) { best = dd; seg = k; }
                }
            }
            if (seg < 0 || i.down[seg]) {
                int nx = seg < 0 ? -1 : nextIntact(i, seg);
                if (nx < 0) { b.releaseBridge(); it.remove(); continue; }
                b.segment = nx;
                b.hopTo(Vec3.atBottomCenterOf(deckCenter(i, nx).above()));
                continue;
            }
            b.segment = seg;
            crack(l, i, seg);
            if (i.hp[seg] - dps <= 0) {   // il saute sur le segment suivant juste avant l'effondrement
                int nx = nextIntact(i, seg);
                if (nx >= 0) { b.segment = nx; b.hopTo(Vec3.atBottomCenterOf(deckCenter(i, nx).above())); }
                else { b.releaseBridge(); it.remove(); }
            }
            damageSegment(l, i.kind, seg, dps);
        }
    }

    /**
     * Brise-pont libre (œuf, ou il a perdu son segment) : le segment debout le plus proche d'un pont construit, à 32 blocs ;
     * il y marchera puis le rongera. Sinon il reste un simple chasseur de joueurs.
     */
    public static void claimBridge(com.wavesurvivor.entity.BrisePont b) {
        if (!active() || !(b.level() instanceof ServerLevel l) || l.dimension() != RiftManager.RIFT_DIM) return;
        Islet best = null;
        int bestSeg = -1;
        BlockPos bestPos = null;
        double bd = 32 * 32;
        for (Islet i : ISLETS) {
            if (!i.bought) continue;
            for (int k = 0; k < SEGMENTS; k++) {
                if (i.down[k]) continue;
                BlockPos p = deckCenter(i, k);
                if (p == null) continue;
                double d = b.distanceToSqr(p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5);
                if (d < bd) { bd = d; best = i; bestSeg = k; bestPos = p; }
            }
        }
        if (best == null) return;
        b.assignBridge(best.kind.ordinal(), bestSeg, bestPos);
        if (!BREAKERS.contains(b.getUUID())) BREAKERS.add(b.getUUID());
    }

    /** Fissures orange qui s'étendent sur le tablier rongé. */
    private static void crack(ServerLevel l, Islet i, int seg) {
        List<BlockPos> d = i.deck.get(seg);
        if (d.isEmpty()) return;
        for (int k = 0; k < 2; k++) {
            BlockPos p = d.get(RNG.nextInt(d.size()));
            for (int o = -1; o <= 1; o++) {
                BlockPos q = p.offset((int) Math.round(-i.dz * o), 0, (int) Math.round(i.dx * o));
                BlockState st = l.getBlockState(q);
                if (st.is(Blocks.DEEPSLATE_BRICKS) || st.is(Blocks.POLISHED_DEEPSLATE)) {
                    if (RNG.nextInt(3) == 0) l.setBlock(q, Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState(), 3);
                    l.sendParticles(ParticleTypes.LAVA, q.getX() + 0.5, q.getY() + 1, q.getZ() + 0.5, 1, 0.3, 0, 0.3, 0);
                }
            }
        }
    }

    /** Le Brise-pont redescend dans le vide (Calme, plus rien à ronger). */
    private static void retreat(ServerLevel l, Entity e) {
        l.sendParticles(ParticleTypes.REVERSE_PORTAL, e.getX(), e.getY() + 0.5, e.getZ(), 30, 0.5, 0.5, 0.5, 0.2);
        e.discard();
    }

    private static void clearBreakers(ServerLevel l) {
        PENDING.clear();
        for (UUID id : BREAKERS) { Entity e = l.getEntity(id); if (e != null) retreat(l, e); }
        BREAKERS.clear();
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
        i.deck.clear();
        for (int k = 0; k < SEGMENTS; k++) i.deck.add(new ArrayList<>());
        BlockPos c = RiftManager.CENTER;
        int y = c.getY();
        int L = Math.max(4, i.bEnd - i.bStart);
        BlockState deck = Blocks.DEEPSLATE_BRICKS.defaultBlockState(), deckB = Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
                edge = Blocks.DEEPSLATE_TILES.defaultBlockState(), wall = Blocks.DEEPSLATE_TILE_WALL.defaultBlockState(),
                postSt = Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState(), lamp = Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState(),
                chain = Blocks.CHAIN.defaultBlockState(), under = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        // Chaque bloc dont le centre tombe dans la bande du pont (distance au centre et écart latéral continus) : aucun trou,
        // même en diagonale. Tablier |écart| ≤ 1,5 ; bordure 1,5 – 2,6 ; poutre dessous |écart| ≤ 0,72.
        List<double[]> cells = new ArrayList<>();   // {x, z, le long, écart}
        for (double[] cell : strip(i.dx, i.dz, i.bStart, i.bStart + L, 2.6)) cells.add(cell);
        cells.sort(java.util.Comparator.comparingDouble(q -> q[2]));
        java.util.Set<Long> posts = new java.util.HashSet<>(), chains = new java.util.HashSet<>();
        for (double[] q : cells) {
            int bx = (int) q[0], bz = (int) q[1];
            int a = (int) Math.max(0, Math.min(L, Math.round(q[2] - i.bStart)));
            double side = q[3], as = Math.abs(side);
            int seg = Math.min(SEGMENTS - 1, a * SEGMENTS / (L + 1));
            boolean lantern = a % 8 == 4;
            if (as <= 1.5) {
                i.pieces.add(new Piece(new BlockPos(bx, y, bz), (a + (int) Math.round(side)) % 3 == 0 ? deckB : deck, seg));
                if (as <= 0.72) {
                    i.pieces.add(new Piece(new BlockPos(bx, y - 1, bz), under, seg));
                    i.deck.get(seg).add(new BlockPos(bx, y, bz));
                    if (lantern && chains.add((long) a)) {
                        for (int k = 2; k <= 4; k++) i.pieces.add(new Piece(new BlockPos(bx, y - k, bz), chain, seg));
                        i.pieces.add(new Piece(new BlockPos(bx, y - 5, bz), lamp, seg));
                    }
                }
            } else {
                i.pieces.add(new Piece(new BlockPos(bx, y, bz), edge, seg));
                if (lantern && posts.add(a * 2L + (side > 0 ? 1 : 0))) {
                    for (int k = 1; k <= 3; k++) i.pieces.add(new Piece(new BlockPos(bx, y + k, bz), postSt, seg));
                    i.pieces.add(new Piece(new BlockPos(bx, y + 4, bz), lamp, seg));
                } else {
                    i.pieces.add(new Piece(new BlockPos(bx, y + 1, bz), wall, seg));
                }
            }
        }
    }

    /**
     * Blocs d'une bande droite partant du centre de l'île dans la direction (dx, dz) : distance « le long » entre from et to,
     * écart latéral ≤ half. Test sur le centre de chaque bloc : pas de trou en diagonale. Renvoie {x, z, le long, écart}.
     */
    static List<double[]> strip(double dx, double dz, double from, double to, double half) {
        BlockPos c = RiftManager.CENTER;
        double ox = c.getX() + 0.5, oz = c.getZ() + 0.5;
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (double al : new double[]{from - 1, to + 1}) {
            for (double sd : new double[]{-half - 1, half + 1}) {
                double x = ox + dx * al - dz * sd, z = oz + dz * al + dx * sd;
                minX = Math.min(minX, x); maxX = Math.max(maxX, x); minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
            }
        }
        List<double[]> out = new ArrayList<>();
        for (int x = (int) Math.floor(minX); x <= (int) Math.ceil(maxX); x++) {
            for (int z = (int) Math.floor(minZ); z <= (int) Math.ceil(maxZ); z++) {
                double px = x + 0.5 - ox, pz = z + 0.5 - oz;
                double along = px * dx + pz * dz, side = -px * dz + pz * dx;
                if (along < from - 0.5 || along > to + 0.5 || Math.abs(side) > half) continue;
                out.add(new double[]{x, z, along, side});
            }
        }
        return out;
    }

    /** Allée d'un îlot vers son pont (sans trou en diagonale) : dallage posé sur le dessus existant, de u = from à u = to (vers le pont). */
    private static void path(ServerLevel l, Islet i, double from, double to, int y, BlockState st) {
        double cx = i.cx + 0.5 - (RiftManager.CENTER.getX() + 0.5), cz = i.cz + 0.5 - (RiftManager.CENTER.getZ() + 0.5);
        double base = cx * i.dx + cz * i.dz;   // distance du centre de l'îlot au centre de l'île
        for (double[] q : strip(i.dx, i.dz, base - to, base - from, 1.5)) {
            BlockPos p = new BlockPos((int) q[0], y, (int) q[1]);
            if (!l.getBlockState(p).isAir()) set(l, p, st);
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
        if (now % 10 == 0) altarTick(l, now);
        breakerTick(l, now);
        if (get(Kind.MERCHANT) != null) VoidMerchant.tick(l, now);
        RiftChestIslet.tick(l, now);
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
        t.put("chest", RiftChestIslet.save());
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
            if (i.kind == Kind.ALTAR) {   // § A : capturé + bonus ; une capture en cours repart de 0
                o.putBoolean("captured", i.captured);
                o.putString("bonus", i.bonus == null ? "" : i.bonus.id);
            }
            if (i.kind == Kind.MERCHANT) o.put("merchant", VoidMerchant.save());   // § A : arrivé, stock du Calme, achats « 1 par partie »
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
        RiftChestIslet.restore(l, t.getCompound("chest"));
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
            if (i.kind == Kind.ALTAR && o.getBoolean("captured")) {
                i.captured = true;
                i.progress = 1f;
                for (Bonus b : Bonus.values()) if (b.id.equals(o.getString("bonus"))) i.bonus = b;
                altarVisual(l, i);
            }
            if (i.kind == Kind.MERCHANT) VoidMerchant.restore(l, o.getCompound("merchant"));
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
            clearRaiders(l);
            clearBreakers(l);
            Islet al = get(Kind.ALTAR);
            if (al != null) removeCrystals(l, al);
        }
        PENDING.clear();
        BREAKERS.clear();
        VoidMerchant.clear(l);
        RiftChestIslet.clear(l);
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
            if (i.kind == Kind.MERCHANT) sb.append(VoidMerchant.status());
            if (i.kind == Kind.ALTAR) sb.append(i.captured ? " §b✦ " + (i.bonus == null ? "?" : i.bonus.label()) : " §8✦ " + Math.round(i.progress * 100) + "%");
            sb.append("\n");
        }
        String ch = RiftChestIslet.status();
        if (!ch.isEmpty()) sb.append(ch).append("\n");
        return sb.toString().trim();
    }

    /** Test : force la pose des îlots tout de suite (Siège en cours). */
    static boolean forcePlace(ServerLevel l, int siegeFinal) {
        if (placed) return false;
        onGrowthDone(l, siegeFinal);
        onSiegeStart(l, false);
        return true;
    }

    /** Test : un Brise-pont sous le premier pont construit (segment au hasard). */
    static boolean debugBreaker(ServerLevel l) {
        for (Islet i : ISLETS) {
            if (!i.bought) continue;
            List<Integer> intact = new ArrayList<>();
            for (int k = 0; k < SEGMENTS; k++) if (!i.down[k] && deckCenter(i, k) != null) intact.add(k);
            if (intact.isEmpty()) return false;
            int seg = intact.get(RNG.nextInt(intact.size()));
            PENDING.add(new Pending(i, seg, deckCenter(i, seg), l.getServer().getTickCount() + 60));
            return true;
        }
        return false;
    }

    /** Test : capture l'autel tout de suite. */
    static boolean debugCapture(ServerLevel l) {
        Islet a = get(Kind.ALTAR);
        if (a == null || a.captured) return false;
        capture(l, a);
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
            // Île-autel : clic droit sur l'autel capturé sans bonus → choix du bonus
            Islet a = get(Kind.ALTAR);
            if (a != null && a.captured && a.bonus == null && Math.hypot(e.getPos().getX() - a.cx, e.getPos().getZ() - a.cz) <= RING
                    && e.getPos().getY() >= a.top && e.getPos().getY() <= a.top + 8) {
                if (e.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND) sendChoice(p);
                e.setCanceled(true);
                e.setCancellationResult(InteractionResult.SUCCESS);
                return;
            }
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
