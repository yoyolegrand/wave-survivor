package com.wavesurvivor.horde.rift;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.HordeConfigMultiData;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * 2.0 — LA FORTERESSE ENNEMIE de la Faille, posée d'après un PLAN (data/wavesurvivor/rift/fortress_&lt;id&gt;.json : plateforme, muraille, tours,
 * donjon ET pont). Chaque forme = un plan + ses repères (markers) : fortress_center, gate, siege_gate, enemy_portal, heart, catalysts,
 * spawn_points, shield, bridge (start côté île, end, width, length), bridge_bastion. Rien n'est codé en dur : tout vient des repères.
 * Repère du monde : bridge.start est aligné sur le bord de l'île, la porte et le pont sont côté -X, le sol du plan (fortress_center) sur le sol de l'île.
 * La Citadelle d'obsidienne est la première forme ; d'autres s'ajoutent en déposant un plan et en ajoutant son identifiant à {@link #FORM_IDS}.
 * Catalyseurs : 3 chaînes = 3 phases (gardiens à chaque tiers de PV perdu), régénération si personne à proximité ; le cœur n'apparaît qu'après eux.
 */
final class RiftFortress {

    /** Formes de forteresse disponibles (fichier fortress_&lt;id&gt;.json). La première est celle par défaut. */
    static final String[] FORM_IDS = {"citadelle"};

    private static final Random RNG = new Random();
    private static final DustParticleOptions VIOLET = new DustParticleOptions(new org.joml.Vector3f(0.65f, 0.2f, 0.95f), 1.3f);
    private static final DustParticleOptions TEAL = new DustParticleOptions(new org.joml.Vector3f(0.1f, 0.95f, 0.85f), 1.3f);

    /** Cibles à PV (catalyseurs, cœur) : cristaux invulnérables dont les dégâts sont gérés ici. {PV, PV max}. */
    static final Map<UUID, float[]> TARGETS = new HashMap<>();
    static final Map<UUID, String> LABELS = new HashMap<>();
    /** Catalyseurs (pas le cœur) et nombre de chaînes déjà brisées (0 à 3). */
    private static final Map<UUID, Integer> CHAINS = new HashMap<>();

    // ─── Plan ───

    private static final class Form {
        String id;
        int sx, sy, sz;
        final Map<Character, BlockState> pal = new HashMap<>();
        String[][] layers;
        int[] center, gate, siegeGate, portal, heart, bastion, bStart, bEnd, shieldC;
        int shieldR = 42, bWidth = 15, bLength = 72;
        final List<int[]> catalysts = new ArrayList<>(), spawns = new ArrayList<>();
    }

    private static final Map<String, Form> CACHE = new HashMap<>();
    private static Form current;
    /** Pose en cours (répartie sur plusieurs ticks pour éviter un à-coup) : couche et ligne atteintes. */
    private static boolean job;
    private static int jobY, jobZ, jobSr;
    private static final List<BlockPos> FIXUPS = new ArrayList<>();

    private RiftFortress() {}

    private static int[] arr(JsonElement e) {
        JsonArray a = e.getAsJsonArray();
        return new int[]{a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()};
    }

    private static Form load(String id) {
        Form cached = CACHE.get(id);
        if (cached != null) return cached;
        Form f = new Form();
        f.id = id;
        try (InputStream in = RiftFortress.class.getResourceAsStream("/data/wavesurvivor/rift/fortress_" + id + ".json")) {
            if (in == null) throw new IllegalStateException("plan de forteresse introuvable : " + id);
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : o.getAsJsonObject("palette").entrySet()) {
                if (e.getKey().length() != 1) continue;
                f.pal.put(e.getKey().charAt(0), BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), e.getValue().getAsString(), false).blockState());
            }
            JsonObject sz = o.getAsJsonObject("size");
            f.sx = sz.get("x").getAsInt();
            f.sy = sz.get("y").getAsInt();
            f.sz = sz.get("z").getAsInt();
            JsonObject mk = o.getAsJsonObject("markers");
            f.center = arr(mk.get("fortress_center"));
            f.gate = arr(mk.get("gate"));
            f.siegeGate = arr(mk.get("siege_gate"));
            f.portal = arr(mk.get("enemy_portal"));
            f.heart = arr(mk.get("heart"));
            for (JsonElement c : mk.getAsJsonArray("catalysts")) f.catalysts.add(arr(c));
            for (JsonElement c : mk.getAsJsonArray("spawn_points")) f.spawns.add(arr(c));
            JsonObject sh = mk.getAsJsonObject("shield");
            f.shieldC = arr(sh.get("center"));
            f.shieldR = sh.get("radius").getAsInt();
            JsonObject br = mk.getAsJsonObject("bridge");
            f.bStart = arr(br.get("start"));
            f.bEnd = arr(br.get("end"));
            f.bWidth = br.get("width").getAsInt();
            f.bLength = br.get("length").getAsInt();
            if (mk.has("bridge_bastion")) f.bastion = arr(mk.get("bridge_bastion"));
            JsonArray ly = o.getAsJsonArray("layers");
            f.layers = new String[ly.size()][];
            for (int y = 0; y < ly.size(); y++) {
                JsonArray rows = ly.get(y).getAsJsonArray();
                f.layers[y] = new String[rows.size()];
                for (int z = 0; z < rows.size(); z++) f.layers[y][z] = rows.get(z).getAsString();
            }
        } catch (Exception ex) {
            WaveSurvivorMod.LOGGER.error("[Faille] Plan de forteresse '{}' illisible : {}", id, ex.getMessage());
            return null;
        }
        CACHE.put(id, f);
        return f;
    }

    /** Choisit la forme de cette Faille : {@code choice} = identifiant, « random » ou vide (la première). */
    static void select(String choice) {
        String id = FORM_IDS[0];
        if ("random".equalsIgnoreCase(choice)) id = FORM_IDS[RNG.nextInt(FORM_IDS.length)];
        else if (choice != null) for (String s : FORM_IDS) if (s.equalsIgnoreCase(choice)) id = s;
        Form f = load(id);
        current = f != null ? f : load(FORM_IDS[0]);
        WaveSurvivorMod.LOGGER.info("[Faille] Forteresse : {}", current != null ? current.id : "aucune (plan illisible)");
    }

    /** Identifiant de la forme choisie (sauvegarde de partie). */
    static String currentId() {
        return current != null ? current.id : FORM_IDS[0];
    }

    private static Form form() {
        if (current == null) select(null);
        return current;
    }

    /** Monde : le repère {@code p} du plan, pour une île de rayon final {@code sr}. */
    private static BlockPos w(int sr, int[] p) {
        Form f = form();
        BlockPos c = RiftManager.CENTER;
        return new BlockPos(c.getX() + sr - f.bStart[0] + p[0], c.getY() - f.center[1] + p[1], c.getZ() - f.bStart[2] + p[2]);
    }

    /** Emprise du plan, relative au centre de l'île : {x0, x1, z0, z1} (chargement des chunks). */
    static int[] bounds(int sr) {
        Form f = form();
        int x0 = sr - f.bStart[0], z0 = -f.bStart[2];
        return new int[]{x0, x0 + f.sx - 1, z0, z0 + f.sz - 1};
    }

    static BlockPos center(int sr) {
        BlockPos p = w(sr, form().center);
        return new BlockPos(p.getX(), RiftManager.CENTER.getY(), p.getZ());
    }

    /** Devant la porte de la forteresse, sur l'axe : les ennemis y apparaissent (point du plan le plus proche de l'axe du pont). */
    static BlockPos spawnPoint(int sr) {
        Form f = form();
        int[] best = f.spawns.get(0);
        for (int[] s : f.spawns) if (Math.abs(s[2] - f.bStart[2]) < Math.abs(best[2] - f.bStart[2])) best = s;
        return w(sr, best);
    }

    /** Début du pont, hors du bouclier : les joueurs refoulés y sont replacés. */
    static BlockPos bridgeHead(int sr) {
        Form f = form();
        int x = f.shieldC[0] - f.shieldR - 6;
        return w(sr, new int[]{Math.max(f.bStart[0] + 4, x), f.bStart[1], f.bStart[2]});
    }

    /** Un point du pont, à {@code fromIsland} blocs du bord de l'île (portail d'invasion du Titan). */
    static BlockPos bridgePoint(int sr, int fromIsland) {
        Form f = form();
        return w(sr, new int[]{f.bStart[0] + Math.max(2, Math.min(f.bLength - 4, fromIsland)), f.bStart[1], f.bStart[2]});
    }

    static List<BlockPos> catalystPositions(int sr) {
        List<BlockPos> out = new ArrayList<>();
        for (int[] c : form().catalysts) out.add(w(sr, c));
        return out;
    }

    static BlockPos heart(int sr) { return w(sr, form().heart); }

    // ─── Île (repli si le plan d'île est illisible) ───

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

    // ─── Pose (étalée sur plusieurs ticks) ───

    /** Démarre la pose de la forteresse et du pont ; {@code allowCine} : jouer la cinématique de construction (une fois l'île agrandie). */
    static void startBuild(int sr, boolean allowCine) {
        form();
        job = current != null;
        jobY = 0;
        jobZ = 0;
        jobSr = sr;
        FIXUPS.clear();
        cine = false;
        pendingCine = job && allowCine && RiftManager.settingsPublic().fortressCine;
    }

    static boolean built() { return !job && !cine && !pendingCine; }

    static boolean pendingCine() { return pendingCine; }

    // ─── Cinématique de construction (~20 s) ───
    // 0-60 déchirure dans le vide · 60-140 socle · 140-200 pont · 200-300 murs · 300-360 sommets puis chaînes / bannières / portail · 360-400 bouclier

    private static final class Cell {
        final int x, y, z, cls;
        final BlockState st;
        final BlockPos pos;
        Cell(int x, int y, int z, BlockState st, int cls, BlockPos pos) { this.x = x; this.y = y; this.z = z; this.st = st; this.cls = cls; this.pos = pos; }
    }

    private static final int SOCLE = 0, PONT = 1, MURS = 2, SOMMETS = 3, FIN = 4;
    private static boolean pendingCine, cine, cineTitled, cinePortal, cineShieldDone;
    private static long cineStart;
    private static double cineF;
    private static final List<List<Cell>> CELLS = new ArrayList<>();
    private static final int[] PTR = new int[5];
    private static final java.util.Set<UUID> cineSkipped = new java.util.HashSet<>();

    private static int classify(Form f, int x, int y, int z, BlockState st) {
        String n = BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
        boolean bridgeBand = x < f.bEnd[0] - 1 && Math.abs(z - f.bStart[2]) <= f.bWidth / 2 + 1;
        if (bridgeBand) return PONT;                                                    // dessous et dalles du pont, lanternes comprises
        if (y < f.center[1]) return SOCLE;                                              // sous la plateforme : cône rocheux
        if (n.equals("chain") || n.endsWith("_banner") || n.contains("lantern") || n.equals("rift_portal")) return FIN;
        if (y >= f.center[1] + 15 || ((n.endsWith("_stairs") || n.endsWith("_slab")) && y >= f.center[1] + 8)) return SOMMETS;   // toits, flèches, pinacles
        return MURS;
    }

    /** Début de la cinématique : plan classé par phase (bas vers haut), joueurs figés, caméra vers la forteresse. */
    static void beginCine(ServerLevel l, long now) {
        pendingCine = false;
        Form f = form();
        if (f == null) { cine = false; return; }
        CELLS.clear();
        for (int i = 0; i < 5; i++) { CELLS.add(new ArrayList<>()); PTR[i] = 0; }
        for (int y = 0; y < f.layers.length; y++) {
            String[] rows = f.layers[y];
            for (int z = 0; z < rows.length; z++) {
                String row = rows[z];
                for (int x = 0; x < row.length(); x++) {
                    BlockState st = f.pal.get(row.charAt(x));
                    if (st == null || st.isAir()) continue;
                    CELLS.get(classify(f, x, y, z, st)).add(new Cell(x, y, z, st, 0, w(jobSr, new int[]{x, y, z})));
                }
            }
        }
        // Ordres : bas vers haut ; le pont se construit du bout côté forteresse jusqu'au bord de l'île, dalle par dalle
        java.util.Comparator<Cell> byY = java.util.Comparator.<Cell>comparingInt(c -> c.y).thenComparingInt(c -> c.z).thenComparingInt(c -> c.x);
        for (int i = 0; i < 5; i++) CELLS.get(i).sort(i == PONT
                ? java.util.Comparator.<Cell>comparingInt(c -> -c.x).thenComparingInt(c -> c.y).thenComparingInt(c -> c.z) : byY);
        HordeConfigMultiData.RiftSettings rs = RiftManager.settingsPublic();
        cineF = Math.max(8, Math.min(60, rs.fortressCineSeconds)) / 20.0;
        cineStart = now;
        cine = true;
        cineTitled = cinePortal = cineShieldDone = false;
        cineSkipped.clear();
        int dur = (int) (400 * cineF);
        BlockPos fc = center(jobSr);
        for (ServerPlayer p : l.players()) {
            if (p.isSpectator()) continue;
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, dur + 20, 9, false, false));
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.JUMP, dur + 20, 128, false, false));
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, dur + 20, 4, false, false));
            com.wavesurvivor.network.NetworkHandler.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p),
                    new com.wavesurvivor.network.TitanCinePackets.Cine(true, dur, fc.getX() + 0.5, fc.getY() + 12, fc.getZ() + 0.5, rs.fortressCineSkippable));
        }
    }

    /** Un joueur passe la cinématique de construction (individuel). */
    static void cineSkip(ServerPlayer p) {
        if (!cine || !cineSkipped.add(p.getUUID())) return;
        p.removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
        p.removeEffect(net.minecraft.world.effect.MobEffects.JUMP);
        p.removeEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE);
        com.wavesurvivor.network.NetworkHandler.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p),
                new com.wavesurvivor.network.TitanCinePackets.Cine(false, 0, 0, 0, 0, false));
    }

    private static void snd(ServerLevel l, BlockPos at, net.minecraft.sounds.SoundEvent ev, float vol, float pitch) {
        l.playSound(null, at, ev, SoundSource.BLOCKS, vol, pitch);
    }

    /** Pose {@code n} blocs de la classe {@code cls} ; retourne le nombre posé. */
    private static int place(ServerLevel l, int cls, int n, long t) {
        List<Cell> list = CELLS.get(cls);
        int placed = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        while (PTR[cls] < list.size() && placed < n) {
            Cell c = list.get(PTR[cls]++);
            l.setBlock(m.set(c.pos), c.st, 2);
            placed++;
            String name = BuiltInRegistries.BLOCK.getKey(c.st.getBlock()).getPath();
            if (name.endsWith("_wall") || name.equals("iron_bars") || name.equals("chain")) FIXUPS.add(c.pos);
            if (name.contains("lantern") && placed < 3) snd(l, c.pos, SoundEvents.LANTERN_PLACE, 1.5f, 1.0f);
            if (placed % 12 == 0 && cls != PONT) l.sendParticles(VIOLET, c.pos.getX() + 0.5, c.pos.getY() + 0.8, c.pos.getZ() + 0.5, 1, 0.2, 0.2, 0.2, 0.01);
        }
        return placed;
    }

    private static void cineTick(ServerLevel l, long now) {
        long t = now - cineStart;
        double f = cineF;
        Form fm = current;
        BlockPos fc = center(jobSr);
        HordeConfigMultiData.RiftSettings rs = RiftManager.settingsPublic();
        int speed = Math.max(20, rs.fortressCineSpeed);
        long t1 = (long) (60 * f), t2 = (long) (140 * f), t3 = (long) (200 * f), t4 = (long) (300 * f), t5 = (long) (340 * f), t6 = (long) (360 * f), tEnd = (long) (400 * f);
        // 1. La Faille : déchirure violette verticale dans le vide, éclairs visuels, tonnerre lointain
        if (t < t1) {
            double p = t / (double) Math.max(1, t1);
            if (t == 0) { snd(l, fc, SoundEvents.LIGHTNING_BOLT_THUNDER, 6f, 0.6f); snd(l, fc, SoundEvents.PORTAL_TRIGGER, 5f, 0.7f); }
            int h = (int) (6 + 60 * p);
            for (int i = 0; i < 6 + (int) (16 * p); i++) {
                double y = fc.getY() - 5 + RNG.nextDouble() * h;
                l.sendParticles(ParticleTypes.REVERSE_PORTAL, fc.getX() + 0.5 + (RNG.nextDouble() - 0.5) * 3, y, fc.getZ() + 0.5 + (RNG.nextDouble() - 0.5) * 3, 1, 0.1, 0.3, 0.1, 0.05);
                l.sendParticles(VIOLET, fc.getX() + 0.5, y, fc.getZ() + 0.5, 1, 0.3, 0.2, 0.3, 0.0);
            }
            if (t % 14 == 7) {
                net.minecraft.world.entity.LightningBolt bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(l);
                if (bolt != null) {
                    bolt.moveTo(fc.getX() + 0.5 + (RNG.nextDouble() - 0.5) * 50, fc.getY(), fc.getZ() + 0.5 + (RNG.nextDouble() - 0.5) * 50);
                    bolt.setVisualOnly(true);
                    l.addFreshEntity(bolt);
                }
            }
            if (t % 10 == 0) for (ServerPlayer pl : l.players()) com.wavesurvivor.network.ShakePacket.send(pl, 10, (float) (0.2 + 0.5 * p));
            return;
        }
        // 2 à 5 : pose progressive, chaque phase du bas vers le haut ; le quota de la phase est toujours terminé avant sa fin
        long[][] win = {{t1, t2}, {t2, t3}, {t3, t4}, {t4, t5}, {t5, t6}};
        int[] cls = {SOCLE, PONT, MURS, SOMMETS, FIN};
        for (int i = 0; i < 5; i++) {
            if (t < win[i][0]) break;
            int left = CELLS.get(cls[i]).size() - PTR[cls[i]];
            if (left <= 0) continue;
            long ticksLeft = Math.max(1, win[i][1] - t);
            int rate = t >= win[i][1] ? left : Math.max(speed, (int) Math.ceil(left / (double) ticksLeft));
            if (t < win[i][1] || left > 0) place(l, cls[i], rate, t);
        }
        // sons d'ambiance : grondement du socle, pierre posée sur les murs
        if (t >= t1 && t < t2 && t % 10 == 0) snd(l, fc, SoundEvents.DEEPSLATE_BREAK, 5f, 0.5f);
        if (t >= t3 && t < t4 && t % 3 == 0) snd(l, fc.offset((int) ((RNG.nextDouble() - 0.5) * 40), 5, (int) ((RNG.nextDouble() - 0.5) * 40)), SoundEvents.STONE_PLACE, 3f, 0.7f + RNG.nextFloat() * 0.4f);
        if (t >= t1 && t % 12 == 0) for (ServerPlayer pl : l.players()) com.wavesurvivor.network.ShakePacket.send(pl, 12, 0.35f);
        // le portail ennemi s'allume
        if (!cinePortal && t >= t6 - 6 && fm != null) {
            cinePortal = true;
            BlockPos pp = w(jobSr, fm.portal);
            snd(l, pp, SoundEvents.END_PORTAL_SPAWN, 6f, 1.0f);
            l.sendParticles(ParticleTypes.REVERSE_PORTAL, pp.getX() + 0.5, pp.getY() + 1.5, pp.getZ() + 0.5, 80, 1.5, 2.0, 1.5, 0.3);
        }
        // 6. Le bouclier : un dôme violet de particules se referme depuis le sol (repère « shield »), puis le titre
        if (t >= t6 && fm != null) {
            double p = Math.min(1.0, (t - t6) / (double) Math.max(1, tEnd - t6));
            BlockPos sc = w(jobSr, fm.shieldC);
            double R = fm.shieldR;
            int rings = (int) Math.ceil(p * 8);
            if (t % 3 == 0) {
                for (int ring = 0; ring <= rings; ring++) {
                    double rr = R * Math.sqrt(Math.max(0.05, 1 - sq(ring / 8.0)));
                    double y = sc.getY() + ring * (R / 8.0) - (R * 0.15);
                    for (int i = 0; i < 48; i++) {
                        double a = (i / 48.0) * Math.PI * 2;
                        l.sendParticles(((i + ring) & 1) == 0 ? TEAL : VIOLET, sc.getX() + 0.5 + Math.cos(a) * rr, y, sc.getZ() + 0.5 + Math.sin(a) * rr, 1, 0, 0, 0, 0);
                    }
                }
            }
            if (!cineShieldDone) { cineShieldDone = true; snd(l, sc, SoundEvents.BEACON_ACTIVATE, 8f, 0.6f); }
            if (!cineTitled) {
                cineTitled = true;
                String key = "rift.fortress." + fm.id;
                String title = rs.fortressCineTitle != null && !rs.fortressCineTitle.isBlank() ? rs.fortressCineTitle : com.wavesurvivor.i18n.WSLang.t(key);
                for (ServerPlayer pl : l.players()) {
                    pl.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, Math.max(20, (int) (tEnd - t6) - 10), 10));
                    pl.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.literal("§7" + com.wavesurvivor.i18n.WSLang.t("rift.fortress.sub"))));
                    pl.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal("§5§l" + title)));
                }
            }
        }
        // Fin : tout le reste est posé, bandes retirées, contrôle rendu ; le Siège commence
        if (t >= tEnd) {
            for (int i = 0; i < 5; i++) place(l, i, Integer.MAX_VALUE, t);
            for (BlockPos p : FIXUPS) {
                BlockState s = l.getBlockState(p);
                BlockState u = net.minecraft.world.level.block.Block.updateFromNeighbourShapes(s, l, p);
                if (u != s) l.setBlock(p, u, 2 | 16);
            }
            FIXUPS.clear();
            cine = false;
            job = false;
            for (ServerPlayer pl : l.players()) {
                pl.removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
                pl.removeEffect(net.minecraft.world.effect.MobEffects.JUMP);
                pl.removeEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE);
                if (!cineSkipped.contains(pl.getUUID())) {
                    com.wavesurvivor.network.NetworkHandler.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> pl),
                            new com.wavesurvivor.network.TitanCinePackets.Cine(false, 0, 0, 0, 0, false));
                }
            }
            CELLS.clear();
            WaveSurvivorMod.LOGGER.info("[Faille] Forteresse '{}' construite (cinématique).", fm != null ? fm.id : "?");
        }
    }

    /** Pose une tranche du plan (≈ 20 000 blocs par tick). À appeler à chaque tick tant que {@link #built()} est faux. */
    static void buildTick(ServerLevel l, long now) {
        if (cine) { cineTick(l, now); return; }   // cinématique de construction
        if (!job || pendingCine) return;           // en attente de la fin de l'agrandissement de l'île
        Form f = current;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int budget = 20000;
        while (jobY < f.layers.length && budget > 0) {
            String[] rows = f.layers[jobY];
            while (jobZ < rows.length && budget > 0) {
                String row = rows[jobZ];
                for (int x = 0; x < row.length(); x++) {
                    BlockState st = f.pal.get(row.charAt(x));
                    if (st == null || st.isAir()) continue;
                    BlockPos p = w(jobSr, new int[]{x, jobY, jobZ});
                    l.setBlock(m.set(p), st, 2);
                    budget--;
                    String n = BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
                    if (n.endsWith("_wall") || n.equals("iron_bars") || n.equals("chain")) FIXUPS.add(p);
                }
                jobZ++;
            }
            if (jobZ >= rows.length) { jobY++; jobZ = 0; }
        }
        if (jobY >= f.layers.length) {
            // Murs, barreaux, chaînes : connexions recalculées une fois tout posé
            for (BlockPos p : FIXUPS) {
                BlockState s = l.getBlockState(p);
                BlockState u = net.minecraft.world.level.block.Block.updateFromNeighbourShapes(s, l, p);
                if (u != s) l.setBlock(p, u, 2 | 16);
            }
            FIXUPS.clear();
            job = false;
            WaveSurvivorMod.LOGGER.info("[Faille] Forteresse '{}' posée.", f.id);
        }
    }

    /** Ouvre la herse du Siège (barreaux au bout du pont) : les ennemis peuvent sortir de la forteresse. */
    static void openPortcullis(ServerLevel l, int sr) {
        Form f = form();
        BlockPos g = w(sr, f.siegeGate);
        int removed = 0;
        for (int dy = 0; dy <= 12; dy++) for (int dz = -(f.bWidth / 2 + 1); dz <= f.bWidth / 2 + 1; dz++) {
            BlockPos p = g.offset(0, dy, dz);
            if (l.getBlockState(p).is(Blocks.IRON_BARS)) { l.setBlock(p, Blocks.AIR.defaultBlockState(), 3); removed++; }
        }
        if (removed > 0) {
            l.playSound(null, g, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 4f, 0.5f);
            l.playSound(null, g, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 4f, 0.6f);
            l.sendParticles(ParticleTypes.CRIT, g.getX() + 0.5, g.getY() + 3, g.getZ() + 0.5, 40, 0.5, 2, 6, 0.05);
        }
    }

    /** Efface l'état de pose (fin ou arrêt de la Faille). */
    static void reset() {
        cine = false;
        pendingCine = false;
        CELLS.clear();
        job = false;
        FIXUPS.clear();
        current = null;
    }

    // ─── Bouclier du Siège ───

    /** Particules du dôme et refoulement des joueurs : on ne peut ni entrer dans la forteresse ni l'atteindre tant que le bouclier tient. */
    static void shield(ServerLevel l, int sr, long time) {
        if (pendingCine || cine || job) return;   // pas de bouclier tant que la forteresse n'est pas construite
        Form f = form();
        BlockPos sc = w(sr, f.shieldC);
        double cx = sc.getX() + 0.5, cz = sc.getZ() + 0.5, R = f.shieldR;
        if (time % 10 == 0) {
            for (int ring = 0; ring < 7; ring++) {
                double y = sc.getY() + ring * (R / 7.0);
                double rr = R * Math.sqrt(Math.max(0.05, 1 - sq(ring / 7.0)));
                for (int i = 0; i < 56; i++) {
                    double a = (i / 56.0) * Math.PI * 2 + time * 0.01;
                    l.sendParticles(((i + ring) & 1) == 0 ? TEAL : VIOLET, cx + Math.cos(a) * rr, y, cz + Math.sin(a) * rr, 1, 0, 0, 0, 0);
                }
            }
        }
        if (time % 4 != 0) return;
        for (ServerPlayer p : l.players()) {
            if (p.isSpectator() || Math.abs(p.getY() - sc.getY()) > 60) continue;
            double dx = p.getX() - cx, dz = p.getZ() - cz, dist = Math.hypot(dx, dz);
            if (dist >= R + 1.5) continue;
            if (dist < R - 2) {
                BlockPos bh = bridgeHead(sr);
                p.teleportTo(bh.getX() + 0.5, bh.getY() + 1, bh.getZ() + 0.5);
                p.fallDistance = 0;
            } else {
                p.setDeltaMovement(-dx / dist * 0.9, 0.25, -dz / dist * 0.9);
                p.hurtMarked = true;
            }
            l.playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.4f, 1.6f);
        }
    }

    private static double sq(double v) { return v * v; }

    // ─── Cibles à PV : catalyseurs et cœur ───

    /** Pose un cristal invulnérable portant une réserve de PV. {@code catalyst} : catalyseur à 3 chaînes (sinon cœur). */
    static UUID spawnTarget(ServerLevel l, double x, double y, double z, float hp, String label, boolean catalyst) {
        EndCrystal e = new EndCrystal(l, x, y, z);
        e.setShowBottom(false);
        e.setInvulnerable(true);
        e.setCustomNameVisible(true);
        e.getPersistentData().putBoolean("ws_rift_target", true);
        TARGETS.put(e.getUUID(), new float[]{hp, hp});
        LABELS.put(e.getUUID(), label);
        if (catalyst) CHAINS.put(e.getUUID(), 0);
        refreshName(e);
        l.addFreshEntity(e);
        l.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 40, 0.6, 0.8, 0.6, 0.2);
        l.playSound(null, BlockPos.containing(x, y, z), SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 1.2f, 1.4f);
        return e.getUUID();
    }

    /** Pose un point faible du Titan : un cristal avec ses PV, marqué pour que sa destruction touche le Titan. */
    static UUID spawnTitanCrystal(ServerLevel l, double x, double y, double z, float hp, String label) {
        UUID id = spawnTarget(l, x, y, z, hp, label, false);
        Entity e = l.getEntity(id);
        if (e != null) e.getPersistentData().putBoolean("ws_titan_crystal", true);
        return id;
    }

    private static void refreshName(Entity e) {
        float[] t = TARGETS.get(e.getUUID());
        if (t == null) return;
        Integer ch = CHAINS.get(e.getUUID());
        String chains = ch == null ? "" : " §5⛓" + (3 - ch);
        e.setCustomName(Component.literal(LABELS.getOrDefault(e.getUUID(), "") + " §c❤ " + Math.max(0, Math.round(t[0])) + "/" + Math.round(t[1]) + chains));
    }

    static boolean isTarget(Entity e) {
        return e instanceof EndCrystal && TARGETS.containsKey(e.getUUID());
    }

    /** Inflige des dégâts à une cible ; chaque tiers de PV perdu brise une chaîne du catalyseur ; détruite quand sa réserve est vide. */
    static void damageTarget(Entity e, float amount) {
        float[] t = TARGETS.get(e.getUUID());
        if (t == null || !(e.level() instanceof ServerLevel l)) return;
        t[0] -= amount;
        l.sendParticles(VIOLET, e.getX(), e.getY() + 1, e.getZ(), 12, 0.5, 0.5, 0.5, 0.05);
        l.playSound(null, e.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 1.5f, 0.7f);
        Integer broken = CHAINS.get(e.getUUID());
        if (broken != null) {
            float frac = Math.max(0f, t[0] / t[1]);
            int should = frac <= 0f ? 3 : frac <= 1f / 3f ? 2 : frac <= 2f / 3f ? 1 : 0;
            while (broken < should) {
                broken++;
                CHAINS.put(e.getUUID(), broken);
                breakChain(l, e, broken);
            }
        }
        if (t[0] <= 0) {
            l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, e.getX(), e.getY() + 1, e.getZ(), 1, 0, 0, 0, 0);
            l.sendParticles(ParticleTypes.END_ROD, e.getX(), e.getY() + 1, e.getZ(), 60, 1.2, 1.2, 1.2, 0.25);
            l.playSound(null, e.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3f, 0.8f);
            TARGETS.remove(e.getUUID());
            LABELS.remove(e.getUUID());
            CHAINS.remove(e.getUUID());
            boolean titanCrystal = e.getPersistentData().getBoolean("ws_titan_crystal");
            e.discard();
            if (titanCrystal) RiftManager.titanCrystalDown(l);   // point faible du Titan détruit
        } else {
            refreshName(e);
        }
    }

    /** Une chaîne se brise : éclat violet, son, la chaîne visible de l'autel disparaît, des gardiens surgissent (plus forts à chaque phase). */
    private static void breakChain(ServerLevel l, Entity crystal, int n) {
        BlockPos c = crystal.blockPosition();
        // Colonnes de chaînes de l'autel (rayon 4), triées par angle : la n-ième disparaît
        Map<Long, List<BlockPos>> cols = new HashMap<>();
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) for (int dy = -3; dy <= 8; dy++) {
            BlockPos p = c.offset(dx, dy, dz);
            if (l.getBlockState(p).is(Blocks.CHAIN)) cols.computeIfAbsent(((long) p.getX() << 32) ^ (p.getZ() & 0xffffffffL), k -> new ArrayList<>()).add(p);
        }
        List<List<BlockPos>> list = new ArrayList<>(cols.values());
        list.sort((a, b) -> Double.compare(Math.atan2(a.get(0).getZ() - c.getZ(), a.get(0).getX() - c.getX()),
                Math.atan2(b.get(0).getZ() - c.getZ(), b.get(0).getX() - c.getX())));
        if (!list.isEmpty()) {
            List<BlockPos> col = list.get(Math.min(n - 1, list.size() - 1));
            for (BlockPos p : col) {
                l.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                l.sendParticles(VIOLET, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 14, 0.3, 0.3, 0.3, 0.1);
            }
        }
        l.sendParticles(ParticleTypes.END_ROD, crystal.getX(), crystal.getY() + 1, crystal.getZ(), 40, 1.0, 1.0, 1.0, 0.2);
        l.sendParticles(VIOLET, crystal.getX(), crystal.getY() + 1, crystal.getZ(), 40, 1.2, 1.2, 1.2, 0.1);
        l.playSound(null, c, SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 4f, 0.7f);
        l.playSound(null, c, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 4f, 0.5f);
        RiftManager.catalystGuards(l, c, n - 1);
    }

    /**
     * Chaque seconde : un catalyseur que personne n'approche (à moins de {@code radius} blocs) regagne {@code pct} % de ses PV.
     * Les chaînes brisées restent brisées (le nombre de chaînes ne baisse jamais).
     */
    static void catalystRegen(ServerLevel l, double radius, double pct) {
        if (pct <= 0) return;
        for (UUID id : new ArrayList<>(CHAINS.keySet())) {
            Entity e = l.getEntity(id);
            float[] t = TARGETS.get(id);
            if (e == null || t == null || t[0] >= t[1]) continue;
            boolean near = false;
            for (ServerPlayer p : l.players()) if (!p.isSpectator() && p.isAlive() && p.distanceToSqr(e) <= radius * radius) { near = true; break; }
            if (near) continue;
            t[0] = (float) Math.min(t[1], t[0] + t[1] * pct / 100.0);
            l.sendParticles(TEAL, e.getX(), e.getY() + 1, e.getZ(), 6, 0.4, 0.6, 0.4, 0.02);
            refreshName(e);
        }
    }

    /** Cibles encore debout dans ce monde (celles dont le cristal a disparu sont retirées). */
    static int aliveTargets(ServerLevel l) {
        int n = 0;
        for (UUID id : new ArrayList<>(TARGETS.keySet())) {
            Entity e = l.getEntity(id);
            if (e == null || e.isRemoved()) { TARGETS.remove(id); LABELS.remove(id); CHAINS.remove(id); } else n++;
        }
        return n;
    }

    static void clearTargets(ServerLevel l) {
        for (UUID id : new ArrayList<>(TARGETS.keySet())) {
            Entity e = l.getEntity(id);
            if (e != null) e.discard();
        }
        TARGETS.clear();
        LABELS.clear();
        CHAINS.clear();
    }
}
