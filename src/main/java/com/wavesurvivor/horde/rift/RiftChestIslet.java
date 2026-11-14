package com.wavesurvivor.horde.rift;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.HordeConfigMultiData;
import com.wavesurvivor.horde.roulette.RouletteChestBlock;
import com.wavesurvivor.horde.roulette.RouletteChestBlockEntity;
import com.wavesurvivor.horde.roulette.RouletteChestConfig;
import com.wavesurvivor.horde.roulette.RouletteChestManager;
import com.wavesurvivor.horde.roulette.RouletteChestRegistry;
import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.network.EventFeedPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * 2.1 — L'ÎLE AU COFFRE (fiche docs/faille/v2_1_ilots/FICHE_2_1_0.md § 4 ; sauvegarde § A).
 * <ul>
 *   <li>Une fois par partie, pendant un Calme du Siège tiré entre le 2e et le 4e : une petite île en ruines monte du vide et se place
 *       près de l'île principale (côté libre) ; un pont de cristal gratuit se forme jusqu'à elle.</li>
 *   <li>En posant le pied dessus : 3 à 5 monstres d'élite + un Gardien du trésor (barre de vie). Chaque gardien tué brise une partie
 *       des 4 chaînes ; le dernier tué ouvre le coffre : coffre roulette « Coffre des Ruines », 1 tirage gratuit par joueur de la Faille.</li>
 *   <li>Elle repart au début de l'Assaut suivant : coffre non ouvert = perdu, joueurs encore dessus ramenés sur l'île principale.</li>
 * </ul>
 * Sauvegarde : venue ou pas, ouverte ou pas ; une sauvegarde pendant l'événement, coffre non ouvert, compte comme « pas encore venue ».
 */
public final class RiftChestIslet {

    private RiftChestIslet() {}

    public static final String CHEST_NAME = "Coffre des Ruines";
    private static final String TAG = "ws_chest_islet";
    private static final int R = 9, LIFT = 3, RISE = 24, RISE_STEP = 3;
    private static final Random RNG = new Random();

    // ── état de la partie ──
    private static int calms, targetCalm;
    private static boolean done, opened;
    // ── île présente ──
    private static boolean present, triggered, leaving;
    private static int cx, cz, top, offset;   // offset : décalage vertical pendant la montée (0 = en place)
    private static double dx, dz;
    private static long nextRise;
    private static final Map<BlockPos, BlockState> PLAN = new LinkedHashMap<>();
    private static final List<List<BlockPos>> CHAINS = new ArrayList<>();
    private static final List<BlockPos> CRYSTALS = new ArrayList<>();
    private static final List<BlockPos> BIG_CHEST = new ArrayList<>();
    private static final List<BlockPos> BRIDGE = new ArrayList<>();
    private static final List<BlockPos> BRIDGE_QUEUE = new ArrayList<>();
    private static final Map<BlockPos, BlockState> BRIDGE_ST = new HashMap<>();
    private static final List<BlockPos> BRIDGE_PLACED = new ArrayList<>();   // seulement les blocs posés par nous (jamais l'île principale)
    private static final List<UUID> GUARDS = new ArrayList<>();
    private static final Set<UUID> COUNTED = new HashSet<>();
    private static UUID boss;
    private static ServerBossEvent bar;
    private static int total, kills, broken;
    private static BlockPos chestPos;
    private static UUID hitbox;
    private static final Map<UUID, Integer> FREE = new HashMap<>();

    private static HordeConfigMultiData.RiftSettings rs() { return RiftManager.settingsPublic(); }

    private static int dist(HordeConfigMultiData.RiftSettings s, int siegeFinal) {
        return siegeFinal + Math.max(8, Math.min(60, s.chestGap)) + R;
    }

    /** Zone à garder chargée (RiftIslets.forceAreas), relative au centre. */
    static int[] forceArea(HordeConfigMultiData.RiftSettings s, int siegeFinal) {
        if (s == null || !s.isletChest) return null;
        double[] d = RiftIslets.dirOf(RiftIslets.chestSide(s, siegeFinal));
        int dd = dist(s, siegeFinal), m = R + 8;
        int ox = (int) Math.round(d[0] * dd), oz = (int) Math.round(d[1] * dd);
        return new int[]{ox - m, ox + m, oz - m, oz + m};
    }

    // ─── Cycle ───

    /** Début d'un Calme du Siège : l'île arrive au Calme tiré au hasard. */
    static void onSiegeCalm(ServerLevel l) {
        HordeConfigMultiData.RiftSettings s = rs();
        calms++;
        if (done || present || !s.isletChest) return;
        int lo = Math.max(1, s.chestCalmMin), hi = Math.max(lo, s.chestCalmMax);
        if (targetCalm <= 0) targetCalm = lo + RNG.nextInt(hi - lo + 1);
        if (calms >= targetCalm) arrive(l);
    }

    /** Début d'un Assaut (ou fin du Siège) : l'île repart. */
    static void onAssault(ServerLevel l) {
        if (present) leaving = true;
    }

    private static void arrive(ServerLevel l) {
        HordeConfigMultiData.RiftSettings s = rs();
        int siegeFinal = RiftManager.siegeFinalPublic();
        double[] d = RiftIslets.dirOf(RiftIslets.chestSide(s, siegeFinal));
        dx = d[0];
        dz = d[1];
        int dd = dist(s, siegeFinal);
        BlockPos c = RiftManager.CENTER;
        cx = (int) Math.round(c.getX() + dx * dd);
        cz = (int) Math.round(c.getZ() + dz * dd);
        top = c.getY() + LIFT;
        plan(new Random(RNG.nextLong()));
        planBridge(siegeFinal, dd);
        present = true;
        triggered = false;
        leaving = false;
        total = kills = broken = 0;
        GUARDS.clear();
        COUNTED.clear();
        boss = null;
        FREE.clear();
        offset = RISE;
        place(l, offset, true);
        nextRise = l.getGameTime() + 10;
        EventFeedPacket.toAll(l.getServer(), Component.literal(WSLang.t("rift.chest.arrive")), "minecraft:chest", EventFeedPacket.RESOURCE, true);
        l.playSound(null, new BlockPos(cx, top, cz), SoundEvents.BEACON_POWER_SELECT, SoundSource.AMBIENT, 4f, 0.5f);
    }

    private static BlockPos local(double u, int y, double v) {
        return BlockPos.containing(cx + 0.5 + dx * u - dz * v, y, cz + 0.5 + dz * u + dx * v);
    }

    private static double smooth(double t) { return t * t * (3 - 2 * t); }

    private static double noise(long sd, double x, double z, double cell) {
        double gx = x / cell, gz = z / cell;
        int ix = (int) Math.floor(gx), iz = (int) Math.floor(gz);
        double fx = smooth(gx - ix), fz = smooth(gz - iz);
        double a = hash(sd, ix, iz), b = hash(sd, ix + 1, iz), c = hash(sd, ix, iz + 1), e = hash(sd, ix + 1, iz + 1);
        double t = a + (b - a) * fx, u = c + (e - c) * fx;
        return t + (u - t) * fz;
    }

    private static double hash(long sd, int x, int z) {
        long k = sd ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        k = (k ^ (k >>> 30)) * 0xBF58476D1CE4E5B9L;
        k = (k ^ (k >>> 27)) * 0x94D049BB133111EBL;
        k ^= k >>> 31;
        return (k >>> 11) * 0x1.0p-53;
    }

    /**
     * Plan de l'île d'après sources/chest.py : ruines (colonnes cassées, mousse), piédestal, coffre géant en bois cerclé d'or,
     * 4 piliers sombres qui le retiennent par des chaînes, éclats dorés qui flottent. Coordonnées du plan : px = −u (vers le pont), pz = v.
     */
    private static void plan(Random rnd) {
        PLAN.clear();
        CHAINS.clear();
        CRYSTALS.clear();
        BIG_CHEST.clear();
        long sA = rnd.nextLong(), sB = rnd.nextLong();
        BlockState deep = Blocks.DEEPSLATE.defaultBlockState(), dark = Blocks.COBBLED_DEEPSLATE.defaultBlockState(),
                tuff = Blocks.TUFF.defaultBlockState(), ruinM = Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
                tilesD = Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState();
        for (int x = cx - R - 3; x <= cx + R + 3; x++) {
            for (int z = cz - R - 3; z <= cz + R + 3; z++) {
                double r0 = Math.hypot(x - cx, z - cz);
                double rr = r0 + (noise(sA, x, z, 5) - 0.5) * 2.6;
                if (rr > R) continue;
                double nA = noise(sA, x, z, 5), nB = noise(sB, x, z, 3);
                PLAN.put(new BlockPos(x, top, z), nA < 0.3 ? ruinM : r0 < 4.5 ? tilesD : nB > 0.6 ? tuff : deep);
                int dep = (int) (3 + 11 * Math.pow(1 - rr / R, 0.9) + (nB - 0.5) * 5);
                if (rnd.nextDouble() < 0.1) dep += 3 + rnd.nextInt(4);
                for (int y = top - 1; y > top - dep; y--) PLAN.put(new BlockPos(x, y, z), rnd.nextDouble() > 0.25 ? deep : dark);
            }
        }
        BlockState polished = Blocks.POLISHED_DEEPSLATE.defaultBlockState(), wood = Blocks.STRIPPED_DARK_OAK_WOOD.defaultBlockState(),
                gold = Blocks.GOLD_BLOCK.defaultBlockState(), plank = Blocks.DARK_OAK_PLANKS.defaultBlockState(),
                pillar = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), crys = Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState(),
                ruin = Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), chain = Blocks.CHAIN.defaultBlockState();
        // piédestal 7 × 7
        for (int px = -3; px <= 3; px++) for (int pz = -3; pz <= 3; pz++) put(px, 1, pz, polished);
        // coffre géant 5 × 3 × 3 : caisse en bois, bandes et serrure dorées
        for (int px = -2; px <= 2; px++) {
            for (int pz = -1; pz <= 1; pz++) {
                boolean band = px == -2 || px == 2;
                for (int y = 2; y <= 3; y++) big(px, y, pz, band ? gold : wood);
                big(px, 4, pz, pz == 0 || band ? gold : plank);
            }
        }
        big(0, 3, 2, gold);   // serrure
        // 4 piliers coiffés d'un cristal, chaînes vers le coffre
        for (int[] c : new int[][]{{-5, -4}, {5, -4}, {-5, 4}, {5, 4}}) {
            for (int k = 1; k <= 5; k++) put(c[0], k, c[1], pillar);
            put(c[0], 6, c[1], crys);
            CRYSTALS.add(at(c[0], 6, c[1]));
            int tx = c[0] > 0 ? 2 : -2, tz = c[1] > 0 ? 1 : -1;
            List<BlockPos> ch = new ArrayList<>();
            for (int t = 1; t <= 4; t++) {
                int px = (int) Math.round(c[0] + (tx - c[0]) * t / 5.0), pz = (int) Math.round(c[1] + (tz - c[1]) * t / 5.0);
                int y = 5 - (int) Math.round(t * 0.5);
                put(px, y, pz, chain);
                ch.add(at(px, y, pz));
            }
            CHAINS.add(ch);
        }
        // ruines : colonnes cassées
        for (int[] c : new int[][]{{-7, -1, 3}, {-6, 6, 2}, {2, -7, 4}, {7, 2, 2}, {-2, 7, 3}}) {
            for (int k = 1; k <= c[2]; k++) put(c[0], k, c[1], ruin);
            if (c[2] > 2) put(c[0] + 1, 1, c[1], ruin);
        }
        // éclats dorés qui flottent
        for (int[] c : new int[][]{{3, -6, 8}, {-4, 5, 9}, {6, 1, 7}, {-6, -1, 8}, {0, 0, 9}}) put(c[0], c[2], c[1], gold);
    }

    private static BlockPos at(int px, int y, int pz) {
        BlockPos p = local(-px, 0, pz);
        return new BlockPos(p.getX(), top + y, p.getZ());
    }

    private static void put(int px, int y, int pz, BlockState st) { PLAN.put(at(px, y, pz), st); }

    private static void big(int px, int y, int pz, BlockState st) {
        BlockPos p = at(px, y, pz);
        PLAN.put(p, st);
        BIG_CHEST.add(p);
    }

    /** Pont de cristal : 3 de large, monte doucement du bord de l'île jusqu'à l'îlot (posé petit à petit). */
    private static void planBridge(int siegeFinal, int dd) {
        BRIDGE.clear();
        BRIDGE_QUEUE.clear();
        BRIDGE_ST.clear();
        BlockPos c = RiftManager.CENTER;
        int a0 = siegeFinal - 3, a1 = dd - R + 2, L = Math.max(1, a1 - a0);
        Set<Long> seen = new HashSet<>();
        for (int a = 0; a <= L; a++) {
            double d = a0 + a;
            int y = c.getY() + (int) Math.round((double) LIFT * a / L);
            for (int o = -1; o <= 1; o++) {
                int bx = (int) Math.round(c.getX() + 0.5 + dx * d - dz * o - 0.5), bz = (int) Math.round(c.getZ() + 0.5 + dz * d + dx * o - 0.5);
                if (!seen.add(BlockPos.asLong(bx, y, bz))) continue;
                BlockPos p = new BlockPos(bx, y, bz);
                BRIDGE.add(p);
                BRIDGE_ST.put(p, (a + o) % 4 == 0 ? Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState() : Blocks.PURPLE_STAINED_GLASS.defaultBlockState());
            }
        }
    }

    /** Pose (ou retire) le plan avec un décalage vertical. */
    private static void place(ServerLevel l, int off, boolean set) {
        for (Map.Entry<BlockPos, BlockState> e : PLAN.entrySet()) {
            BlockPos p = e.getKey().below(off);
            if (set) {
                if (BIG_CHEST.contains(e.getKey()) && opened) continue;
                l.setBlock(p, e.getValue(), 2 | 16);
            } else if (!l.getBlockState(p).isAir()) {
                l.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
            }
        }
    }

    /** Chaque tick (via RiftIslets.tick) : montée, pont, déclenchement, gardiens, départ. */
    static void tick(ServerLevel l, long now) {
        if (!present) return;
        if (RiftManager.phasePublic() != RiftManager.Phase.SIEGE) leaving = true;   // fin du Siège : l'île repart aussi
        if (leaving) {
            if (chestPos != null && RouletteChestManager.isBusy(chestPos)) return;   // une roulette tourne encore
            leave(l);
            return;
        }
        // montée depuis le vide
        if (offset > 0) {
            if (now < nextRise) return;
            place(l, offset, false);
            offset = Math.max(0, offset - RISE_STEP);
            place(l, offset, true);
            nextRise = now + 5;
            l.sendParticles(ParticleTypes.CLOUD, cx + 0.5, top - offset - 4, cz + 0.5, 30, R * 0.6, 1.5, R * 0.6, 0.02);
            if (offset == 0) {
                BRIDGE_QUEUE.addAll(BRIDGE);
                l.playSound(null, new BlockPos(cx, top, cz), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.AMBIENT, 4f, 0.8f);
            }
            return;
        }
        // pont de cristal (3 blocs par tick)
        for (int k = 0; k < 3 && !BRIDGE_QUEUE.isEmpty(); k++) {
            BlockPos p = BRIDGE_QUEUE.remove(0);
            if (l.getBlockState(p).isAir()) { l.setBlock(p, BRIDGE_ST.get(p), 3); BRIDGE_PLACED.add(p); }
            l.sendParticles(ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, 1, 0.2, 0.1, 0.2, 0.01);
            if (BRIDGE_QUEUE.isEmpty()) EventFeedPacket.toAll(l.getServer(), Component.literal(WSLang.t("rift.chest.bridge")), "minecraft:purple_stained_glass", EventFeedPacket.RESOURCE, false);
        }
        if (now % 10 != 0) return;
        if (!triggered && !opened) {
            for (ServerPlayer p : l.players()) {
                if (p.isSpectator() || !p.isAlive()) continue;
                if (Math.hypot(p.getX() - (cx + 0.5), p.getZ() - (cz + 0.5)) <= R && p.getY() >= top - 2 && p.getY() <= top + 8) { trigger(l); break; }
            }
        }
        if (triggered && !opened) {
            // gardiens morts, disparus ou tombés dans le vide : chacun compte une fois
            List<UUID> all = new ArrayList<>(GUARDS);
            if (boss != null) all.add(boss);
            for (UUID id : all) {
                if (COUNTED.contains(id)) continue;
                Entity e = l.getEntity(id);
                if (e == null || !e.isAlive() || e.getY() < top - 40) {
                    if (e != null && e.isAlive()) e.discard();
                    COUNTED.add(id);
                    kills++;
                }
            }
            int want = total <= 0 ? 0 : kills * CHAINS.size() / total;
            while (broken < want && broken < CHAINS.size()) breakChain(l, broken++);
            if (bar != null) {
                Entity b = boss == null ? null : l.getEntity(boss);
                if (b instanceof LivingEntity le && le.isAlive()) bar.setProgress(Math.max(0f, Math.min(1f, le.getHealth() / le.getMaxHealth())));
                else { bar.removeAllPlayers(); bar = null; }
                if (bar != null) for (ServerPlayer p : l.players()) if (!bar.getPlayers().contains(p)) bar.addPlayer(p);
            }
            if (kills >= total) open(l);
        }
        if (opened && chestPos != null && now % 40 == 0)
            l.sendParticles(ParticleTypes.END_ROD, chestPos.getX() + 0.5, chestPos.getY() + 2, chestPos.getZ() + 0.5, 4, 0.6, 0.6, 0.6, 0.02);
    }

    /** Un joueur pose le pied sur l'île : les gardiens apparaissent. */
    private static void trigger(ServerLevel l) {
        triggered = true;
        HordeConfigMultiData.RiftSettings s = rs();
        int lo = Math.max(0, s.chestGuardMin), hi = Math.max(lo, s.chestGuardMax);
        int n = lo + RNG.nextInt(hi - lo + 1);
        double hpMul = Math.max(0.1, s.chestGuardHpPct / 100.0);
        int[][] pts = {{-6, -2}, {6, -2}, {-6, 3}, {6, 3}, {0, -6}, {0, 6}};
        for (int k = 0; k < n && s.chestGuardMobs != null && !s.chestGuardMobs.isEmpty(); k++) {
            int[] pt = pts[k % pts.length];
            Mob m = spawn(l, s.chestGuardMobs.get(RNG.nextInt(s.chestGuardMobs.size())), pt[0], pt[1]);
            if (m == null) continue;
            var a = m.getAttribute(Attributes.MAX_HEALTH);
            if (a != null) { a.setBaseValue(a.getBaseValue() * hpMul); m.setHealth(m.getMaxHealth()); }
            m.setCustomName(Component.literal("§6" + WSLang.t("rift.chest.elite")));
            GUARDS.add(m.getUUID());
        }
        Mob b = spawn(l, s.chestBossMob, 3, 0);
        if (b != null) {
            var a = b.getAttribute(Attributes.MAX_HEALTH);
            if (a != null) { a.setBaseValue(Math.max(20, Math.min(5000, s.chestBossHp))); b.setHealth(b.getMaxHealth()); }
            b.setCustomName(Component.literal("§6§l" + WSLang.t("rift.chest.boss")));
            boss = b.getUUID();
            bar = new ServerBossEvent(Component.literal(WSLang.t("rift.chest.boss")), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);
            for (ServerPlayer p : l.players()) bar.addPlayer(p);
        }
        total = GUARDS.size() + (boss != null ? 1 : 0);
        EventFeedPacket.toAll(l.getServer(), Component.literal(WSLang.t("rift.chest.guards")), "minecraft:golden_sword", EventFeedPacket.DANGER, true);
        l.playSound(null, new BlockPos(cx, top, cz), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.2f, 1.4f);
        if (total == 0) open(l);
    }

    private static Mob spawn(ServerLevel l, String type, int px, int pz) {
        ResourceLocation id = ResourceLocation.tryParse(type == null ? "" : type);
        if (id == null || !ForgeRegistries.ENTITY_TYPES.containsKey(id)) return null;
        Entity e = ForgeRegistries.ENTITY_TYPES.getValue(id).create(l);
        if (!(e instanceof Mob m)) { if (e != null) e.discard(); return null; }
        BlockPos p = at(px, 1, pz);
        m.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, RNG.nextFloat() * 360f, 0f);
        m.finalizeSpawn(l, l.getCurrentDifficultyAt(p), MobSpawnType.EVENT, null, null);
        m.addTag(TAG);
        m.setPersistenceRequired();
        m.restrictTo(new BlockPos(cx, top + 1, cz), R - 1);
        if (!l.addFreshEntity(m)) return null;
        l.sendParticles(ParticleTypes.REVERSE_PORTAL, m.getX(), m.getY() + 1, m.getZ(), 30, 0.4, 0.8, 0.4, 0.2);
        return m;
    }

    private static void breakChain(ServerLevel l, int k) {
        if (k < 0 || k >= CHAINS.size()) return;
        for (BlockPos p : CHAINS.get(k)) {
            l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CHAIN.defaultBlockState()), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 8, 0.2, 0.2, 0.2, 0.05);
            l.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        }
        BlockPos c = CRYSTALS.get(k);
        l.setBlock(c, Blocks.TINTED_GLASS.defaultBlockState(), 3);
        l.playSound(null, c, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 2f, 0.6f);
        l.playSound(null, c, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.6f, 1.6f);
        EventFeedPacket.toAll(l.getServer(), Component.literal(WSLang.t("rift.chest.chain", k + 1, CHAINS.size())), "minecraft:chain", EventFeedPacket.RESOURCE, false);
    }

    public static RouletteChestConfig config() {
        return RouletteChestRegistry.getByChestName(CHEST_NAME);
    }

    /** Toutes les chaînes brisées : le coffre géant s'ouvre (coffre roulette à sa place), 1 tirage gratuit par joueur. */
    private static void open(ServerLevel l) {
        opened = true;
        done = true;
        if (bar != null) { bar.removeAllPlayers(); bar = null; }
        while (broken < CHAINS.size()) breakChain(l, broken++);
        for (BlockPos p : BIG_CHEST) {
            BlockState st = l.getBlockState(p);
            if (!st.isAir()) l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
            l.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        }
        BlockPos at = at(0, 2, 0);
        RouletteChestConfig cfg = config();
        if (cfg != null) {
            Direction facing = Direction.getNearest(-dx, 0, -dz);
            l.setBlock(at, com.wavesurvivor.registry.ModBlocks.ROULETTE_CHEST.get().defaultBlockState().setValue(RouletteChestBlock.FACING, facing), 3);
            if (l.getBlockEntity(at) instanceof RouletteChestBlockEntity be) {
                be.setCustomName(Component.literal(cfg.chestName()));
                be.setColor(RouletteChestManager.colorFor(cfg));
                be.setStyle(RouletteChestManager.styleFor(cfg).toJson());
            }
            RouletteChestManager.registerIdle(at, cfg);
            chestPos = at.immutable();
            double s = RouletteChestManager.styleFor(cfg).scale;
            Interaction hb = EntityType.INTERACTION.create(l);
            if (hb != null) {
                hb.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
                CompoundTag t = hb.saveWithoutId(new CompoundTag());
                t.putFloat("width", (float) (0.9 * s + 0.1));
                t.putFloat("height", (float) (0.9 * s + 0.1));
                t.putBoolean("response", false);
                hb.load(t);
                hb.addTag(TAG);
                if (l.addFreshEntity(hb)) hitbox = hb.getUUID();
            }
        } else {
            WaveSurvivorMod.LOGGER.warn("[Faille] « {} » introuvable dans les coffres roulette (supprimé ?).", CHEST_NAME);
        }
        for (ServerPlayer p : l.players()) FREE.put(p.getUUID(), 1);
        l.playSound(null, at, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 2f, 0.5f);
        l.playSound(null, at, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 2f, 0.8f);
        l.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 60, 1, 1, 1, 0.3);
        EventFeedPacket.toAll(l.getServer(), Component.literal(WSLang.t("rift.chest.open")), "minecraft:chest", EventFeedPacket.RESOURCE, true);
    }

    /** Tirage gratuit (RouletteChestManager.tryOpenChest) : vrai s'il est consommé pour ce joueur sur ce coffre. */
    public static boolean takeFreeDraw(ServerPlayer p, RouletteChestConfig cfg, BlockPos pos) {
        if (chestPos == null || pos == null || !chestPos.equals(pos)) return false;
        int n = FREE.getOrDefault(p.getUUID(), 0);
        if (n <= 0) return false;
        FREE.put(p.getUUID(), n - 1);
        return true;
    }

    /** L'île repart : coffre non ouvert perdu, joueurs dessus ramenés sur l'île principale. */
    private static void leave(ServerLevel l) {
        boolean lost = !opened;
        done = true;
        cleanupEntities(l);
        BlockPos home = RiftManager.CENTER.above();
        for (ServerPlayer p : l.players()) {
            if (Math.hypot(p.getX() - (cx + 0.5), p.getZ() - (cz + 0.5)) <= R + 3 && p.getY() > top - 30) {
                p.teleportTo(l, home.getX() + 0.5 + RNG.nextInt(5) - 2, home.getY() + 1, home.getZ() + 0.5 + RNG.nextInt(5) - 2, p.getYRot(), p.getXRot());
                p.fallDistance = 0;
                p.displayClientMessage(Component.literal(WSLang.t("rift.chest.sent_back")), false);
            }
        }
        for (BlockPos p : BRIDGE_PLACED) l.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        BRIDGE_PLACED.clear();
        place(l, offset, false);
        l.sendParticles(ParticleTypes.REVERSE_PORTAL, cx + 0.5, top, cz + 0.5, 150, R * 0.6, 3, R * 0.6, 0.3);
        l.playSound(null, new BlockPos(cx, top, cz), SoundEvents.BEACON_DEACTIVATE, SoundSource.AMBIENT, 4f, 0.5f);
        EventFeedPacket.toAll(l.getServer(), Component.literal(WSLang.t(lost ? "rift.chest.lost" : "rift.chest.leave")), "minecraft:chest", lost ? EventFeedPacket.DANGER : EventFeedPacket.RESOURCE, true);
        present = false;
        leaving = false;
        PLAN.clear();
        BRIDGE.clear();
        BRIDGE_QUEUE.clear();
    }

    private static void cleanupEntities(ServerLevel l) {
        for (UUID id : GUARDS) { Entity e = l.getEntity(id); if (e != null) e.discard(); }
        if (boss != null) { Entity e = l.getEntity(boss); if (e != null) e.discard(); }
        GUARDS.clear();
        COUNTED.clear();
        boss = null;
        if (bar != null) { bar.removeAllPlayers(); bar = null; }
        if (chestPos != null) {
            RouletteChestManager.unregister(chestPos);
            if (l.getBlockState(chestPos).getBlock() instanceof RouletteChestBlock) l.setBlock(chestPos, Blocks.AIR.defaultBlockState(), 3);
        }
        if (hitbox != null) { Entity e = l.getEntity(hitbox); if (e != null) e.discard(); }
        chestPos = null;
        hitbox = null;
        FREE.clear();
    }

    // ─── Sauvegarde (§ A) ───

    static CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putBoolean("done", done);   // venue et repartie, ou ouverte ; pendant l'événement sans ouverture : pas encore venue
        t.putBoolean("opened", opened);
        t.putInt("calms", calms);
        t.putInt("target", targetCalm);
        return t;
    }

    static void restore(ServerLevel l, CompoundTag t) {
        clear(l);
        if (t == null) return;
        done = t.getBoolean("done");
        opened = t.getBoolean("opened");
        calms = t.getInt("calms");
        targetCalm = t.getInt("target");
        if (!done && targetCalm > 0 && calms >= targetCalm) targetCalm = calms + 1;   // interrompue : elle reviendra au prochain Calme
    }

    static void clear(ServerLevel l) {
        if (l != null) {
            cleanupEntities(l);
            if (present) {
                for (BlockPos p : BRIDGE_PLACED) l.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
                place(l, offset, false);
            }
        }
        present = triggered = leaving = done = opened = false;
        calms = targetCalm = 0;
        total = kills = broken = 0;
        PLAN.clear();
        BRIDGE.clear();
        BRIDGE_QUEUE.clear();
        BRIDGE_PLACED.clear();
    }

    static String status() {
        if (!rs().isletChest) return "";
        if (present) return "§6✪ " + WSLang.t(opened ? "rift.chest.st_open" : triggered ? "rift.chest.st_fight" : "rift.chest.st_here", kills, total);
        return done ? "§8✪ " + WSLang.t("rift.chest.st_done") : "§7✪ " + WSLang.t("rift.chest.st_wait", targetCalm <= 0 ? "?" : targetCalm, calms);
    }

    /** Test : l'île au coffre arrive tout de suite (une fois par partie, sauf si elle est déjà là). */
    static boolean debugArrive(ServerLevel l) {
        if (present || RiftManager.phasePublic() != RiftManager.Phase.SIEGE) return false;
        done = false;
        opened = false;
        arrive(l);
        return true;
    }

    // ─── Événements : zone cliquable du coffre agrandi ───

    @Mod.EventBusSubscriber(modid = WaveSurvivorMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Events {
        private Events() {}

        private static boolean ours(Entity e) { return e instanceof Interaction && e.getTags().contains(TAG); }

        @SubscribeEvent
        public static void onInteract(PlayerInteractEvent.EntityInteract e) {
            if (!ours(e.getTarget())) return;
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
            if (e.getLevel().isClientSide || e.getHand() != InteractionHand.MAIN_HAND) return;
            if (!(e.getEntity() instanceof ServerPlayer sp) || !(e.getLevel() instanceof ServerLevel l) || chestPos == null) return;
            RouletteChestConfig cfg = RouletteChestManager.getConfigAt(chestPos);
            if (cfg == null) cfg = config();
            if (cfg != null) RouletteChestManager.tryOpenChest(sp, l, chestPos, cfg, e.getItemStack());
        }

        @SubscribeEvent
        public static void onAttack(AttackEntityEvent e) {
            if (!ours(e.getTarget())) return;
            e.setCanceled(true);
            RouletteChestConfig cfg = config();
            if (e.getEntity() instanceof ServerPlayer sp && cfg != null) RouletteChestManager.showLoot(sp, cfg);
        }
    }
}
