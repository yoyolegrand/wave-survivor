package com.wavesurvivor.horde.rift;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.HordeConfigMultiData;
import com.wavesurvivor.horde.HordeManager;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * LA FAILLE (2.0, jalon 1) : quatrième type d'événement lancé depuis l'autel. Les joueurs passent par un portail et arrivent sur une
 * île flottante dans la dimension dédiée {@code wavesurvivor:rift} (vide à 99 %). Cette classe porte le socle : disponibilité (mod Pehkui),
 * construction et nettoyage de l'île, entrée et retour des joueurs, filet anti-chute. Les phases (Horde, Siège, Contre-offensive, Titan)
 * viendront se brancher dessus. Documentation : docs/DESIGN_FAILLE_2_0.md.
 */
public final class RiftManager {

    public static final ResourceKey<Level> RIFT_DIM = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(WaveSurvivorMod.MODID, "rift"));
    /** Étiquette du joueur : « dimension|x|y|z » de l'endroit où il était en entrant (pour le renvoyer au même endroit). */
    private static final String RETURN_TAG = "ws_rift_return";
    /** Centre de l'île de départ, dans la dimension de la Faille. */
    public static final BlockPos CENTER = new BlockPos(0, 100, 0);
    /** Le Monolithe de l'île : un bloc d'autel posé au centre (sa défense et sa boutique fonctionnent comme dans une horde). */
    public static final BlockPos MONOLITH = CENTER.above();
    private static final Random RNG = new Random();

    private static boolean active;
    private static HordeConfigMultiData horde;
    private static ServerLevel world;
    private static BlockPos home;
    /** Portail de la Faille (blocs réels + animation d'ouverture), jalon 1b. */
    private static RiftGate gate;

    // ─── Déroulement (jalon 2 : phase Horde) ───
    private enum Phase { NONE, PORTAL, LOBBY, HORDE, INTERLUDE, SIEGE, COUNTER, TITAN, ENDING }
    private static Phase phase = Phase.NONE;
    private static long startAt = -1, endAt = -1;
    private static int lastWave;
    /** Joueurs inscrits au départ de la Horde (les autres, arrivés en cours de route, attendent la vague suivante). */
    private static final Set<UUID> registered = new HashSet<>();
    private static final Set<UUID> waiting = new HashSet<>();
    private static final Map<UUID, Long> reviveAt = new HashMap<>();
    private static final Map<UUID, GameType> prevMode = new HashMap<>();
    private static HordeConfigMultiData.ConfigDataInner spawnCfg;
    private static int spawnRadiusBackup, waveBackup;
    /** Siège sur le moteur du Kingdom : mode et réglages d'origine de la horde, rétablis à la fin de la phase. */
    /** Reprise d'une Faille sauvegardée : points de reprise à rejouer (null hors reprise). */
    private static RiftSession resuming;
    private static String modeBackup;
    private static HordeConfigMultiData.KingdomSettings kingdomBackup;
    private static boolean kingdomOverride;
    // Jalons 3-6
    private static Phase nextPhase = Phase.NONE;
    private static long interludeAt;
    private static int siegeFinal, growCur, growTarget;
    private static long growNextAt, siegeStartAt, nextReinforce;
    private static boolean siegeHordeOn, coreSpawned;
    private static HordeManager.State lastState;
    private static final List<long[]> DUE_KEYS = new ArrayList<>();
    private static final List<Runnable> DUE_TASKS = new ArrayList<>();
    private static int radius = 16;
    private static final Set<Long> FORCED = new HashSet<>();

    /** Public : instance enregistrée sur le bus d'événements (tick, connexion, réapparition). */
    public RiftManager() {}

    // ─── État ───

    /** La Faille n'existe que si le mod Pehkui est installé (il agrandit le Titan). */
    public static boolean available() { return ModList.get().isLoaded("pehkui"); }

    public static boolean isActive() { return active; }

    public static HordeConfigMultiData horde() { return horde; }

    public static int radius() { return radius; }

    /** Autel (ou point de lancement) de la Faille en cours. */
    static BlockPos altarOf() { return home; }

    /** Le portail est-il ouvert (animation terminée) ? */
    public static boolean portalOpen() { return gate != null && gate.isOpen(); }

    // ─── Ouverture ───

    /**
     * Lance la Faille depuis l'autel : construit l'île, puis envoie dedans les joueurs proches de l'autel.
     * (Jalon 1b : l'entrée se fera en franchissant le portail, après son animation d'ouverture.)
     *
     * @return faux si la Faille ne peut pas s'ouvrir (Pehkui absent, dimension introuvable, déjà ouverte)
     */
    public static boolean open(ServerPlayer starter, ServerLevel w, BlockPos altar, HordeConfigMultiData h) {
        MinecraftServer server = w.getServer();
        if (!available()) {
            starter.sendSystemMessage(Component.literal(WSLang.t("rift.need_pehkui")));
            return false;
        }
        if (active) {
            starter.sendSystemMessage(Component.literal(WSLang.t("rift.already")));
            return false;
        }
        ServerLevel rd = server.getLevel(RIFT_DIM);
        if (rd == null) {
            starter.sendSystemMessage(Component.literal(WSLang.t("rift.no_dim")));
            WaveSurvivorMod.LOGGER.error("[Faille] Dimension {} introuvable (data/wavesurvivor/dimension/rift.json absent ?).", RIFT_DIM.location());
            return false;
        }
        RiftSession.discard(server);
        resuming = null;
        horde = h;
        world = w;
        home = altar;
        HordeConfigMultiData.RiftSettings rs = h != null && h.configData != null ? h.configData.rift() : new HordeConfigMultiData.RiftSettings();
        radius = Math.max(8, Math.min(48, rs.islandRadius));
        siegeFinal = Math.max(radius, Math.min(48, rs.siegeRadius));
        RiftFortress.select(rs.fortress);
        ensureResourceEvents(h);
        forceChunks(rd, rs);
        buildIsland(rd);
        active = true;
        // Jalon 1b : le portail s'ouvre (animation complète la 1re fois), les joueurs le franchissent à pied
        boolean[] fresh = new boolean[1];
        phase = Phase.PORTAL;
        startAt = -1;
        endAt = -1;
        registered.clear();
        waiting.clear();
        reviveAt.clear();
        prevMode.clear();
        gate = RiftGate.plan(w, altar, fresh);
        gate.begin(fresh[0], rs);
        WaveSurvivorMod.LOGGER.info("[Faille] Ouverte depuis l'autel {} (île de rayon {}).", altar, radius);
        return true;
    }

    /** Envoie un joueur sur l'île (et note d'où il vient). */
    public static void enter(ServerPlayer p) {
        ServerLevel rd = p.getServer().getLevel(RIFT_DIM);
        if (!active || rd == null) return;
        if (p.level().dimension() != RIFT_DIM) {
            p.getPersistentData().putString(RETURN_TAG, p.level().dimension().location() + "|" + p.getBlockX() + "|" + p.getBlockY() + "|" + p.getBlockZ());
        }
        double[] at = arrival();
        p.teleportTo(rd, at[0], CENTER.getY() + 1, at[1], 0f, 0f);
        p.fallDistance = 0;
        long now = p.getServer().getTickCount();
        if (inCombat() && !registered.contains(p.getUUID())) {
            // Arrivé en cours de partie : spectateur jusqu'à la vague suivante
            if (waiting.add(p.getUUID())) {
                prevMode.put(p.getUUID(), p.gameMode.getGameModeForPlayer());
                p.setGameMode(GameType.SPECTATOR);
                p.sendSystemMessage(Component.literal(WSLang.t("rift.waiting")));
            }
        } else if (phase == Phase.LOBBY && horde != null && startAt < 0) {
            startAt = now + Math.max(0, settings().startDelaySeconds) * 20L;
        }
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 4, false, false));
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(WSLang.t("rift.enter_sub"))));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(WSLang.t("rift.enter_title"))));
        rd.playSound(null, p.blockPosition(), SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.6f, 0.8f);
    }

    // ─── Monolithe, ressources, arrivée ───

    /** Pendant le Siège, la seconde horde reprend la défense du Monolithe en cours au lieu d'en commencer une autre. */
    public static boolean keepMonolith() {
        return active && phase == Phase.SIEGE && com.wavesurvivor.altar.AltarDefense.isActive();
    }

    /** Horde et Siège : tous les ennemis viennent détruire le Monolithe (Contre-offensive et Titan : ils ne visent que les joueurs). */
    public static boolean forceProfaners() {
        return active && (phase == Phase.HORDE || phase == Phase.SIEGE || phase == Phase.LOBBY || phase == Phase.TITAN);   // Titan : les vagues de l'invasion visent le Monolithe
    }

    /** Rayon actuel de l'île (il grandit pendant le Siège) : sert à garder arbres et gisements dessus. */
    public static int islandRadiusNow() {
        return phase == Phase.SIEGE || phase == Phase.INTERLUDE ? Math.max(radius, growCur) : radius;
    }

    /** Le Monolithe est tombé : défaite de la Faille (pause de fin puis retour), sans la séquence de chute du monde normal. */
    public static boolean monolithFell(ServerLevel level) {
        if (!active || !inCombat() || level.dimension() != RIFT_DIM) return false;
        MinecraftServer server = level.getServer();
        ServerLevel rd = server.getLevel(RIFT_DIM);
        if (rd == null) return false;
        defeat(server, rd);
        return true;
    }

    /** Point d'arrivée / de réapparition : au sud du Monolithe (côté des engins de siège et du Titan). */
    private static double[] arrival() {
        return new double[]{CENTER.getX() + 0.5 + (RNG.nextDouble() - 0.5) * 5, CENTER.getZ() + 0.5 + 3.5 + RNG.nextDouble() * 2};
    }

    /** Choix d'une variante de niveau 3 pour l'engin que le joueur vient d'améliorer (boutons du chat). */
    public static void chooseEngineVariant(ServerPlayer p, int i) {
        if (active) RiftEngines.chooseVariant(p, i);
    }

    /** Engin de siège acheté : posé par l'objet (catapulte / baliste). @return 0 posé · 1 pas de place · 2 hors de l'île · 3 Faille inactive */
    public static int placeEngine(ServerLevel level, BlockPos at, boolean ballista) {
        if (!active || !inCombat()) return 3;
        return RiftEngines.place(level, at, ballista);
    }

    /** Pose le bloc d'autel au centre de l'île : il devient le Monolithe à défendre (clic droit = boutique, réparation). */
    private static void placeMonolith(ServerLevel rd) {
        rd.setBlock(MONOLITH, com.wavesurvivor.registry.ModBlocks.ALTAR_RUNIC.get().defaultBlockState(), 3);
        rd.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, MONOLITH.getX() + 0.5, MONOLITH.getY() + 1.2, MONOLITH.getZ() + 0.5, 30, 0.4, 0.6, 0.4, 0.05);
    }

    /** La Faille a besoin d'une défense du Monolithe : si la horde n'en a pas, on en active une par défaut (réglable ensuite dans l'éditeur d'autel). */
    private static void ensureDefense(String hordeName) {
        if (com.wavesurvivor.altar.AltarRecipes.getDefense(hordeName) != null) return;
        com.wavesurvivor.altar.AltarRecipes.Defense d = com.wavesurvivor.altar.AltarRecipes.editDefense(hordeName);
        d.enabled = true;
        d.maxHealth = 800;
    }

    /**
     * Arbres et gisements de la Faille = ceux du Chaos (réglage « Garantis par calme » de l'onglet Chaos). Si la horde n'en a aucun,
     * on copie ceux d'une autre horde (Kingdom d'abord), à défaut un chêne et un gisement de fer.
     */
    private static void ensureResourceEvents(HordeConfigMultiData h) {
        if (h == null || h.configData == null) return;
        var cd = h.configData;
        if (cd.chaosEvents == null) cd.chaosEvents = new ArrayList<>();
        for (var ev : cd.chaosEvents) if (HordeManager.isResourceEvent(ev) && ev.guaranteed > 0) return;
        int trees = 0, ores = 0;
        var cfg = WaveSurvivorMod.getConfig();
        if (cfg != null && cfg.hordeConfigMulti != null) {
            com.google.gson.Gson gs = new com.google.gson.Gson();
            for (int pass = 0; pass < 2; pass++) {
                for (HordeConfigMultiData o : cfg.hordeConfigMulti) {
                    if (o == null || o == h || o.configData == null || o.configData.chaosEvents == null) continue;
                    if (pass == 0 && !o.configData.isKingdom()) continue;
                    for (var ev : o.configData.chaosEvents) {
                        if (!HordeManager.isResourceEvent(ev)) continue;
                        boolean tree = "arbre".equalsIgnoreCase(ev.type);
                        if (tree ? trees >= 1 : ores >= 2) continue;
                        var c = gs.fromJson(gs.toJson(ev), com.wavesurvivor.horde.model.ChaosEvent.class);
                        c.guaranteed = 2;
                        cd.chaosEvents.add(c);
                        if (tree) trees++; else ores++;
                    }
                }
            }
        }
        if (trees == 0) {
            var t = new com.wavesurvivor.horde.model.ChaosEvent();
            t.type = "arbre";
            t.blocks = new ArrayList<>(List.of("minecraft:oak_log", "minecraft:oak_leaves"));
            var dr = new com.wavesurvivor.horde.model.ChaosEvent.Drop();
            dr.item = "minecraft:oak_log";
            dr.minQty = 2;
            dr.maxQty = 4;
            t.drops = new ArrayList<>(List.of(dr));
            t.minTier = 0;
            t.guaranteed = 2;
            cd.chaosEvents.add(t);
        }
        if (ores == 0) {
            var g = new com.wavesurvivor.horde.model.ChaosEvent();
            g.type = "gisement";
            g.blocks = new ArrayList<>(List.of("minecraft:iron_ore", "minecraft:stone"));
            var dr = new com.wavesurvivor.horde.model.ChaosEvent.Drop();
            dr.item = "minecraft:raw_iron";
            dr.minQty = 3;
            dr.maxQty = 6;
            g.drops = new ArrayList<>(List.of(dr));
            g.guaranteed = 2;
            cd.chaosEvents.add(g);
        }
        WaveSurvivorMod.LOGGER.info("[Faille] Arbres et gisements du Chaos ajoutés à la horde '{}' ({} arbre(s), {} gisement(s)).", h.hordeName, Math.max(trees, 1), Math.max(ores, 1));
    }

    // ─── Fin et retour ───

    /** Ferme la Faille : renvoie tout le monde chez soi, vide l'île et libère les chunks. */
    public static void stop(MinecraftServer server) {
        riftBosses = 0;
        com.wavesurvivor.horde.renaissance.RiftLegacy.resetRun();
        com.wavesurvivor.item.RiftRelics.resetRun();   // marques du Trophée du Titan
        RiftSession.discard(server);   // fin de la Faille : plus rien à reprendre (sauf à la fermeture du serveur)
        resuming = null;
        phase = Phase.NONE;
        if (HordeManager.get().isRunning()) HordeManager.get().stopSilently(); // Horde, Siège, ou royaume en veille (Contre-offensive, Titan)
        com.wavesurvivor.altar.AltarDefense.onHordeEnd(server, false);
        com.wavesurvivor.horde.benediction.RogueUpgradeManager.clearAllPlayers(server); // fin de la Faille : les bénédictions se dissipent
        restoreSpawnRadius();
        ServerLevel rd = server.getLevel(RIFT_DIM);
        if (rd != null) {
            for (ServerPlayer p : new ArrayList<>(rd.players())) sendHome(p);
            TitanChest.clear(rd);   // le Coffre du Titan implose (les clés non utilisées restent valables)
            RiftTitan.clear(rd);
            RiftFortress.clearTargets(rd);
            RiftFortress.reset();
            RiftEngines.clear();
            clearIsland(rd);
        }
        DUE_KEYS.clear();
        DUE_TASKS.clear();
        PLAYER_PLACED.clear();
        nextPhase = Phase.NONE;
        siegeHordeOn = false;
        coreSpawned = false;
        if (gate != null) {
            gate.seal();
            gate = null;
        }
        releaseChunks(rd);
        registered.clear();
        waiting.clear();
        reviveAt.clear();
        prevMode.clear();
        active = false;
        horde = null;
        world = null;
        home = null;
    }

    /** Renvoie un joueur là où il était en entrant (à défaut : près du point de réapparition du monde principal). */
    public static void sendHome(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        ServerLevel dest = server.overworld();
        BlockPos to = null;
        String tag = p.getPersistentData().getString(RETURN_TAG);
        if (home != null && world != null) {
            // Toujours au Monolithe (autel) qui a ouvert cette Faille, devant lui, sur le sol — jamais aux coordonnées de la dimension
            dest = world;
            int x = home.getX(), z = home.getZ() + 3;
            to = new BlockPos(x, dest.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
        } else if (!tag.isEmpty()) {   // Faille déjà fermée (reconnexion) : là où le joueur était en entrant
            String[] a = tag.split("\\|");
            try {
                ServerLevel l = server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(a[0])));
                if (l != null) dest = l;
                to = new BlockPos(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]));
            } catch (Exception ignored) { /* étiquette illisible : point de réapparition */ }
        }
        if (to == null) to = home != null && world != null ? home : dest.getSharedSpawnPos();
        // Le joueur est entré en touchant l'ovale du portail, qui se referme en pierre : on le ressort devant la façade, jamais dedans
        if (home == null && gate != null && gate.gateLevel() == dest && to.distSqr(gate.origin) <= 20 * 20) to = gate.frontSpot();
        to = safeSpot(dest, to);
        p.getPersistentData().remove(RETURN_TAG);
        restoreMode(p);
        p.teleportTo(dest, to.getX() + 0.5, to.getY() + 1, to.getZ() + 0.5, p.getYRot(), 0f);
        p.fallDistance = 0;
        p.setDeltaMovement(0, 0, 0);
        p.sendSystemMessage(Component.literal(WSLang.t("rift.left")));
    }

    /** Blocs posés par les joueurs dans la Faille (cassables même dans la forteresse). */
    private static final java.util.Set<Long> PLAYER_PLACED = new java.util.HashSet<>();

    /**
     * Contre-offensive et Titan : on peut construire partout (ponts, passerelles) hors du claim du Kingdom, et casser les blocs
     * qu'on a posés ainsi que le terrain ; seuls les murs de la forteresse elle-même restent intacts.
     */
    public static boolean freeBuild(BlockPos pos, boolean breaking) {
        if (!active) return false;
        boolean late = phase == Phase.COUNTER || phase == Phase.TITAN
                || (phase == Phase.INTERLUDE && (nextPhase == Phase.COUNTER || nextPhase == Phase.TITAN));
        if (!late) return false;
        if (!breaking || PLAYER_PLACED.contains(pos.asLong())) return true;
        int[] b = RiftFortress.bounds(siegeFinal);
        int dx = pos.getX() - CENTER.getX(), dz = pos.getZ() - CENTER.getZ();
        return !(dx >= b[0] && dx <= b[1] && dz >= b[2] && dz <= b[3]);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerPlace(net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent e) {
        if (active && e.getEntity() instanceof ServerPlayer && e.getLevel() instanceof ServerLevel l && l.dimension() == RIFT_DIM) PLAYER_PLACED.add(e.getPos().asLong());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerBreak(net.minecraftforge.event.level.BlockEvent.BreakEvent e) {
        if (active) PLAYER_PLACED.remove(e.getPos().asLong());
    }

    /** Évite d'arriver dans un bloc plein : si les pieds ou la tête sont coincés, on remonte à la surface la plus proche. */
    private static BlockPos safeSpot(ServerLevel l, BlockPos to) {
        BlockPos feet = to.above(), head = to.above(2);
        boolean free = l.getBlockState(feet).getCollisionShape(l, feet).isEmpty() && l.getBlockState(head).getCollisionShape(l, head).isEmpty()
                && l.getBlockState(feet).getFluidState().isEmpty();
        if (free) return to;
        int y = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, to.getX(), to.getZ());
        return new BlockPos(to.getX(), y - 1, to.getZ());
    }

    // ─── Île ───

    private static void buildIsland(ServerLevel l) {
        clearIsland(l);
        if (RiftIsland.place(l, radius)) return;   // plan définitif de l'île ; sinon île provisoire ci-dessous
        int y = CENTER.getY();
        BlockState tileA = Blocks.POLISHED_DEEPSLATE.defaultBlockState(), tileB = Blocks.DEEPSLATE_TILES.defaultBlockState();
        BlockState rock = Blocks.DEEPSLATE.defaultBlockState(), edge = Blocks.OBSIDIAN.defaultBlockState();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > radius + 0.5) continue;
                int depth = 3 + (int) Math.round((1 - d / radius) * 6); // plus épaisse au centre : une île flottante en pointe vers le bas
                for (int k = 0; k < depth; k++) {
                    BlockState st = k == 0 ? (d > radius - 1.5 ? edge : ((dx + dz) & 1) == 0 ? tileA : tileB) : rock;
                    l.setBlock(m.set(CENTER.getX() + dx, y - k, CENTER.getZ() + dz), st, 2);
                }
            }
        }
    }

    /** Efface tout ce qui a été construit dans la dimension (parcours des sections non vides des chunks forcés) et toutes les entités hors joueurs. */
    private static void clearIsland(ServerLevel l) {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (long c : new ArrayList<>(FORCED)) {
            net.minecraft.world.level.chunk.LevelChunk ch = l.getChunk(net.minecraft.world.level.ChunkPos.getX(c), net.minecraft.world.level.ChunkPos.getZ(c));
            for (int i = 0; i < ch.getSectionsCount(); i++) {
                net.minecraft.world.level.chunk.LevelChunkSection sec = ch.getSection(i);
                if (sec.hasOnlyAir()) continue;
                int y0 = (ch.getMinSection() + i) << 4, x0 = ch.getPos().getMinBlockX(), z0 = ch.getPos().getMinBlockZ();
                for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) for (int dy = 0; dy < 16; dy++) {
                    m.set(x0 + dx, y0 + dy, z0 + dz);
                    if (!l.getBlockState(m).isAir()) l.setBlock(m, air, 2 | 16);
                }
            }
        }
        List<Entity> gone = new ArrayList<>();
        for (Entity e : l.getAllEntities()) if (!(e instanceof ServerPlayer)) gone.add(e);
        for (Entity e : gone) e.discard();
    }

    /** Chunks gardés chargés : l'île, et si besoin la forteresse (à l'est) et le Titan (au sud). */
    private static void forceChunks(ServerLevel l, HordeConfigMultiData.RiftSettings rs) {
        boolean far = rs.siegeEnabled || rs.titanEnabled;
        int reach = (far ? siegeFinal : radius) + 16;
        forceRect(l, -reach, reach, -reach, reach);
        if (rs.siegeEnabled) {
            int[] b = RiftFortress.bounds(siegeFinal);
            forceRect(l, b[0] - 16, b[1] + 16, b[2] - 16, b[3] + 16);
        }
        if (rs.titanEnabled) {
            int d = (int) Math.ceil(RiftTitan.distance(rs, siegeFinal));
            forceRect(l, -64, 64, d - 64, d + 64);
        }
    }

    private static void forceRect(ServerLevel l, int x0, int x1, int z0, int z1) {
        for (int cx = Math.floorDiv(x0, 16); cx <= Math.floorDiv(x1, 16); cx++) {
            for (int cz = Math.floorDiv(z0, 16); cz <= Math.floorDiv(z1, 16); cz++) {
                l.setChunkForced(cx, cz, true);
                FORCED.add(net.minecraft.world.level.ChunkPos.asLong(cx, cz));
            }
        }
    }

    private static void releaseChunks(ServerLevel l) {
        if (l != null) for (long c : FORCED) l.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c), net.minecraft.world.level.ChunkPos.getZ(c), false);
        FORCED.clear();
    }

    // ─── Déroulement : portail → attente → Horde → fin ───

    /** Réglages de la Faille en cours (cadences des engins). */
    static HordeConfigMultiData.RiftSettings settingsPublic() { return settings(); }

    /** Un coup reçu par une boîte de contact du Titan est porté au Titan (appelé par TitanHitboxEntity). */
    public static boolean hurtTitan(net.minecraft.world.damagesource.DamageSource source, float amount) {
        return active && RiftTitan.hurtTitan(source, amount);
    }

    /** Réparation des ruines du Kingdom : pourcentage des ressources investies à payer (réglable dans l'éditeur). */
    public static int ruinRepairPercent() {
        return active ? settings().ruinRepairPercent : 50;
    }

    /** Un point faible du Titan vient d'être détruit. */
    static void titanCrystalDown(ServerLevel l) {
        if (active) RiftTitan.crystalDown(l);
    }

    /** Monstres d'une phase (« counter », « titan ») : sa liste si elle est renseignée, sinon ceux de la Horde. */
    static java.util.List<com.wavesurvivor.horde.model.HordeEntity> phaseEntities(String ph) {
        if (horde == null || horde.configData == null) return null;
        var cd = horde.configData;
        var own = ph.equals("counter") ? cd.counterEntities : ph.equals("titan") ? cd.titanEntities : null;
        if (own != null && !own.isEmpty()) return own;
        return listsSwapped ? bkEntities : cd.hordeEntities;
    }

    /** Vague spéciale d'une phase : avec la chance réglée, un groupe prédéfini (composition fixe) surgit en plus. */
    private static void phaseSpecial(ServerLevel l, String ph, BlockPos at, int radius, int wave) {
        if (horde == null || horde.configData == null) return;
        var cd = horde.configData;
        HordeConfigMultiData.RiftSettings rs = settings();
        boolean counter = ph.equals("counter");
        boolean on = counter ? rs.counterSpecialOn : rs.titanSpecialOn;
        int chance = counter ? rs.counterSpecialChance : rs.titanSpecialChance;
        var pool = counter ? cd.counterSpecialWaves : cd.titanSpecialWaves;
        if (!on || pool == null || pool.isEmpty() || RNG.nextInt(100) >= chance) return;
        int total = 0;
        java.util.List<com.wavesurvivor.horde.model.SpecialWave> ok = new ArrayList<>();
        for (var sw : pool) if (sw.entities != null && !sw.entities.isEmpty() && sw.chance > 0 && sw.allowedAt(wave)) { ok.add(sw); total += sw.chance; }
        if (total <= 0) return;
        int r = RNG.nextInt(total), cum = 0;
        for (var sw : ok) {
            cum += sw.chance;
            if (r < cum) {
                for (var swe : sw.entities) {
                    com.wavesurvivor.horde.spawn.HordeSpawner.spawnMobs(l, swe.toHordeEntity(), at, radius, Math.max(1, swe.count), playerCount(l));
                }
                feed(l, "§5" + WSLang.t("rift.special_group", WSLang.t(sw.name)));
                return;
            }
        }
    }

    /** Boss d'une phase : ceux dont « Vague » = {@code n} (Contre-offensive : 1 à 3 = chaîne brisée ; Titan : n-ième vague du pont). */
    private static void phaseBoss(ServerLevel l, String ph, BlockPos at, int radius, int n) {
        if (horde == null || horde.configData == null) return;
        var cd = horde.configData;
        HordeConfigMultiData.RiftSettings rs = settings();
        boolean counter = ph.equals("counter");
        if (!(counter ? rs.counterBossOn : rs.titanBossOn)) return;
        var pool = counter ? cd.counterBossWaves : cd.titanBossWaves;
        if (pool == null) return;
        for (var bw : pool) {
            if (bw != null && bw.waveNumber == n) com.wavesurvivor.horde.boss.BossManager.spawnBoss(l, bw, at, radius, l.getServer());
        }
    }

    private static int titanWaveN;

    /** Vague du Titan : {@code count} unités de la horde sortent d'un portail ouvert sur le pont (assez près de l'île pour ne pas partir de la forteresse). */
    static void titanWave(ServerLevel l, int count) {
        var pool = phaseEntities("titan");
        if (!active || pool == null || pool.isEmpty()) return;
        BlockPos p = RiftFortress.bridgePoint(siegeFinal, 26);
        l.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, p.getX() + 0.5, p.getY() + 2, p.getZ() + 0.5, 80, 1.2, 2.0, 2.5, 0.2);
        l.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL, p.getX() + 0.5, p.getY() + 1.5, p.getZ() + 0.5, 60, 1.0, 1.5, 2.0, 0.5);
        l.playSound(null, p, SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 2.5f, 1.3f);
        var tpl = pool.get(RNG.nextInt(pool.size()));
        com.wavesurvivor.horde.spawn.HordeSpawner.spawnMobs(l, tpl, p, 3, count, playerCount(l));
        titanWaveN++;
        phaseSpecial(l, "titan", p, 3, titanWaveN);
        phaseBoss(l, "titan", p, 3, titanWaveN);
    }

    /** Le Titan existe-t-il encore ? (les boîtes de contact disparaissent sans lui) */
    public static boolean titanAlive() {
        return active && RiftTitan.active();
    }

    /** Un joueur passe la cinématique du Titan (maintenir S'accroupir 1 s). */
    public static void titanCineSkip(ServerPlayer p) {
        if (active) { RiftTitan.cineSkip(p); RiftFortress.cineSkip(p); }
    }

    /** Un engin de siège tombe à 0 PV (appelé par l'entité). */
    public static void engineDestroyed(com.wavesurvivor.entity.SiegeEngineEntity e) {
        RiftEngines.destroyed(e);
    }

    /** Siège, Contre-offensive, Titan : tous les monstres abîment les bâtiments et engins qu'ils frôlent (réglable). */
    public static boolean monstersHitBuildings() {
        return active && (phase == Phase.SIEGE || phase == Phase.COUNTER || phase == Phase.TITAN) && settings().monstersAttackBuildings;
    }

    /** Coup d'un monstre au contact d'un engin de siège. */
    public static boolean hitEngineNear(net.minecraft.world.entity.Mob m, double reach, float dmg) {
        return active && m.level() instanceof ServerLevel l && l.dimension() == RIFT_DIM && RiftEngines.hitNear(l, m, reach, dmg);
    }

    /** Explosion d'un monstre : dégâts aux engins proches. */
    public static void engineBlast(ServerLevel l, net.minecraft.world.phys.Vec3 c, double r, float dmg) {
        if (active && l.dimension() == RIFT_DIM) RiftEngines.damageNear(l, c, r, dmg);
    }

    /**
     * Un catalyseur de la Contre-offensive est détruit : il compte comme un Catalyseur fermé du Kingdom — XP de Maîtrise et talents de
     * l'Arcaniste (bouclier, recul, foudre), set Abîme 2 du joueur qui l'a achevé (+1 Essence).
     */
    static void catalystClosed(ServerLevel l, ServerPlayer by) {
        MinecraftServer server = l.getServer();
        com.wavesurvivor.horde.kingdom.vocation.VocationXp.awardHolder(server, com.wavesurvivor.horde.kingdom.KingdomRoles.Role.ARCANIST, "catalyst_closed");
        com.wavesurvivor.horde.kingdom.vocation.VocationFx.onCatalystClosed(server);
        if (by != null) com.wavesurvivor.item.RelicSets.onCatalystClosed(by);
    }

    /** Un catalyseur (ou le cœur) de la Faille est-il ouvert à moins de {@code r} blocs ? (set Abîme 3) */
    public static boolean riftCatalystNear(net.minecraft.world.entity.player.Player p, double r) {
        if (!active || p.level().dimension() != RIFT_DIM) return false;
        for (UUID id : RiftFortress.TARGETS.keySet()) {
            if (p.level() instanceof ServerLevel l) { var t = l.getEntity(id); if (t != null && t.distanceToSqr(p) <= r * r) return true; }
        }
        return false;
    }

    /** Le pilote d'une baliste maintient le clic droit (paquet du client). */
    public static void engineFire(ServerPlayer p) {
        if (active && p.level().dimension() == RIFT_DIM) RiftEngines.fireFromSeat(p);
    }

    /** Action de la fiche d'un engin (amélioration, variante). */
    public static void engineSheetAction(ServerPlayer p, int entityId, String action) {
        if (active && p.level().dimension() == RIFT_DIM) RiftEngines.sheetAction(p, entityId, action);
    }

    /** Clic droit sur un engin de siège (appelé par l'entité). */
    public static void engineUse(ServerPlayer p, com.wavesurvivor.entity.SiegeEngineEntity e) {
        if (active && p.level().dimension() == RIFT_DIM) RiftEngines.onUse(p, e);
    }

    private static HordeConfigMultiData.RiftSettings settings() {
        return horde != null && horde.configData != null ? horde.configData.rift() : new HordeConfigMultiData.RiftSettings();
    }

    private static void tellAll(ServerLevel rd, String msg) {
        feed(rd, msg);
    }

    /** Annonce de la Faille dans le fil d'événements (petites étiquettes en haut à gauche) plutôt que dans le chat. */
    static void feed(ServerLevel rd, String msg) {
        if (rd == null || msg == null) return;
        String m = msg.startsWith("§") && msg.length() > 1 ? msg.substring(0, 2) : "";
        int color = m.equals("§c") || m.equals("§4") ? com.wavesurvivor.network.EventFeedPacket.DANGER
                : m.equals("§d") || m.equals("§5") ? com.wavesurvivor.network.EventFeedPacket.ARCANE
                : m.equals("§a") ? com.wavesurvivor.network.EventFeedPacket.RESOURCE
                : m.equals("§7") || m.equals("§8") ? com.wavesurvivor.network.EventFeedPacket.KINGDOM
                : com.wavesurvivor.network.EventFeedPacket.HORDE;
        String icon = color == com.wavesurvivor.network.EventFeedPacket.DANGER ? "minecraft:iron_sword"
                : color == com.wavesurvivor.network.EventFeedPacket.ARCANE ? "minecraft:amethyst_shard" : "minecraft:nether_star";
        com.wavesurvivor.network.EventFeedPacket.toAll(rd.getServer(), Component.literal(msg), icon, color, color == com.wavesurvivor.network.EventFeedPacket.DANGER);
    }

    /** Listes de la Horde mises de côté pendant le Siège (monstres, spéciales, boss propres au Siège). */
    private static boolean listsSwapped;
    private static java.util.List<com.wavesurvivor.horde.model.HordeEntity> bkEntities;
    private static java.util.List<com.wavesurvivor.horde.model.SpecialWave> bkSpecials;
    private static java.util.List<com.wavesurvivor.horde.model.BossWave> bkBosses;
    private static boolean bkUseSpecial, bkUseBoss;
    private static int bkSpecialChance;

    private static void restoreSpawnRadius() {
        if (spawnCfg != null && listsSwapped) {
            spawnCfg.hordeEntities = bkEntities;
            spawnCfg.specialWaves = bkSpecials;
            spawnCfg.useSpecialWaves = bkUseSpecial;
            spawnCfg.specialWaveChance = bkSpecialChance;
            spawnCfg.bossWaves = bkBosses;
            spawnCfg.useBossWaves = bkUseBoss;
        }
        listsSwapped = false;
        if (spawnCfg != null) {
            spawnCfg.spawnRadius = spawnRadiusBackup;
            if (waveBackup > 0) spawnCfg.totalWaves = waveBackup;
            if (kingdomOverride) {
                spawnCfg.mode = modeBackup;
                spawnCfg.kingdom = kingdomBackup;
            }
        }
        kingdomOverride = false;
        modeBackup = null;
        kingdomBackup = null;
        spawnCfg = null;
        waveBackup = 0;
    }

    private static void restoreMode(ServerPlayer p) {
        GameType m = prevMode.remove(p.getUUID());
        waiting.remove(p.getUUID());
        reviveAt.remove(p.getUUID());
        if (p.isSpectator() || m != null) p.setGameMode(m != null && m != GameType.SPECTATOR ? m : GameType.SURVIVAL);
    }

    private static void flow(MinecraftServer server, long now) {
        ServerLevel rd = server.getLevel(RIFT_DIM);
        if (rd == null) return;
        // Retours après une mort
        if (!reviveAt.isEmpty() && now % 10 == 0) {
            for (UUID id : new ArrayList<>(reviveAt.keySet())) {
                if (now < reviveAt.get(id)) continue;
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                reviveAt.remove(id);
                if (p == null || p.level().dimension() != RIFT_DIM) continue;
                restoreMode(p);
                respawnOnIsland(p, rd);
                com.wavesurvivor.horde.renaissance.Heritage.onRevive(p);   // Survivant 3 : 5 s d'invulnérabilité
                p.sendSystemMessage(Component.literal(WSLang.t("rift.revived")));
            }
        }
        if (inCombat() && now % 20 == 0 && !registered.isEmpty()) {
            boolean anyAlive = false;
            for (UUID id : registered) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null && p.level().dimension() == RIFT_DIM && p.isAlive() && !p.isSpectator()) { anyAlive = true; break; }
            }
            if (!anyAlive) { defeat(server, rd); return; }
        }
        RiftEngines.tick(rd);
        if (now % 600 == 0) captureSession(server, false);   // point de reprise toutes les 30 s
        switch (phase) {
            case PORTAL -> {
                if (gate != null && gate.isOpen()) phase = Phase.LOBBY;
            }
            case LOBBY -> {
                if (startAt < 0) return;
                if (now % 20 == 0) {
                    long left = Math.max(0, (startAt - now + 19) / 20);
                    if (left > 0 && (left % 5 == 0 || left <= 3)) {
                        for (ServerPlayer p : rd.players()) p.displayClientMessage(Component.literal(WSLang.t("rift.start_in", left)), true);
                    }
                }
                if (now >= startAt) startHorde(server, rd);
            }
            case HORDE -> {
                if (now % 20 == 0) waveWatch(server, rd, false);
            }
            case INTERLUDE -> {
                if (now < interludeAt) return;
                switch (nextPhase) {
                    case SIEGE -> startSiege(server, rd, now);
                    case COUNTER -> startCounter(server, rd, now);
                    case TITAN -> startTitan(server, rd, now);
                    default -> finalVictory(server, rd);
                }
            }
            case SIEGE -> {
                RiftFortress.shield(rd, siegeFinal, now);
                RiftFortress.buildTick(rd, now);   // la forteresse se pose (cinématique de construction, ou par tranches)
                if (growCur < growTarget) {
                    if (now >= growNextAt) {
                        int to = Math.min(growTarget, growCur + 2);
                        if (!RiftIsland.ring(rd, growCur, to, growTarget)) RiftFortress.islandRing(rd, growCur, to);
                        growCur = to;
                        radius = to;
                        growNextAt = now + 8;
                        for (ServerPlayer p : rd.players()) com.wavesurvivor.network.ShakePacket.send(p, 10, 0.5f);
                        rd.playSound(null, CENTER, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 3f, 0.5f);
                    }
                } else if (!siegeHordeOn) {
                    if (RiftFortress.pendingCine()) RiftFortress.beginCine(rd, now);   // l'île a fini de grandir : la forteresse se construit
                    if (now >= siegeStartAt && RiftFortress.built()) startSiegeHorde(server, rd);
                } else if (now % 20 == 0) {
                    waveWatch(server, rd, true);
                }
            }
            case COUNTER -> counterTick(server, rd, now);
            case TITAN -> {
                RiftTitan.tick(rd, now);
                if (RiftTitan.isDead()) finalVictory(server, rd);
            }
            case ENDING -> {
                if (now % 20 != 0) return;
                long left = Math.max(0, (endAt - now + 19) / 20);
                String clock = (left / 60) + ":" + String.format("%02d", left % 60);
                for (ServerPlayer p : rd.players()) p.displayClientMessage(Component.literal(WSLang.t("rift.closing", clock)), true);
                rd.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, EXIT_PORTAL.getX() + 0.5, EXIT_PORTAL.getY() + 1.5, EXIT_PORTAL.getZ() + 0.5, 20, 0.8, 1.2, 0.2, 0.1);
                if (now >= endAt) stop(server);
            }
            default -> { }
        }
    }

    private static void startHorde(MinecraftServer server, ServerLevel rd) {
        registered.clear();
        for (ServerPlayer p : rd.players()) if (!p.isSpectator() && p.isAlive()) registered.add(p.getUUID());
        if (registered.isEmpty() || horde == null || horde.configData == null) { startAt = -1; return; }
        if (resuming != null && resuming.phase != 1) { resumeLate(server, rd); return; }   // reprise au Siège / à la Contre-offensive
        if (resuming != null) {   // reprise de la Horde : même difficulté et mêmes mutateurs
            com.wavesurvivor.horde.difficulty.HordeDifficulty.setPending(com.wavesurvivor.horde.difficulty.Difficulty.byId(resuming.difficulty));
            com.wavesurvivor.horde.mutator.HordeMutators.setPending(com.wavesurvivor.horde.mutator.Mutator.parse(resuming.mutators));
        }
        // L'île est plus petite que le rayon d'apparition habituel : on le limite le temps de la partie
        spawnCfg = horde.configData;
        spawnRadiusBackup = spawnCfg.spawnRadius;
        spawnCfg.spawnRadius = Math.max(4, Math.min(spawnCfg.spawnRadius, radius - 2));
        ensureDefense(horde.hordeName);
        placeMonolith(rd);
        boolean ok = HordeManager.get().startAt(horde, server, MONOLITH, RIFT_DIM.location().toString());
        if (!ok) {
            restoreSpawnRadius();
            tellAll(rd, WSLang.t("rift.start_fail"));
            startAt = -1;
            return;
        }
        phase = Phase.HORDE;
        if (resuming != null) {   // la vague sauvegardée redémarre dans 5 s ; Monolithe et compteurs retrouvés
            RiftSession s = resuming;
            resuming = null;
            HordeManager.get().resumeAt(s.wave, s.elapsed);
            if (s.maxHp > 0) com.wavesurvivor.altar.AltarDefense.restore(s.hp, s.maxHp, s.levels);
            com.wavesurvivor.horde.rewards.KillTracker.restore(s.kills);
            tellAll(rd, WSLang.t("rift.session.resumed", s.wave));
        }
        lastWave = HordeManager.get().getCurrentWave();
        tellAll(rd, WSLang.t("rift.horde_go"));
        WaveSurvivorMod.LOGGER.info("[Faille] Horde '{}' lancée sur l'île ({} joueurs).", horde.hordeName, registered.size());
    }

    private static void defeat(MinecraftServer server, ServerLevel rd) {
        WaveSurvivorMod.LOGGER.info("[Faille] Défaite : plus aucun joueur debout.");
        tellAll(rd, WSLang.t("rift.defeat"));
        riftPayout(server, rd, false);   // PR des phases acquises (la Faille paie elle-même : pas de double versement)
        com.wavesurvivor.horde.renaissance.RenaissanceRewards.consumeDefeat();
        toEnding(server, rd, false);
        HordeManager.get().stopSilently();
    }

    /** Fin de la Horde, appelée par le HordeManager : victoire (dernière vague nettoyée) ou arrêt (commande ; la défaite est déjà traitée). */
    public static void onHordeEnd(boolean victory) {
        if (!active || (phase != Phase.HORDE && phase != Phase.SIEGE)) return;
        MinecraftServer server = world != null ? world.getServer() : null;
        if (server == null) return;
        ServerLevel rd = server.getLevel(RIFT_DIM);
        if (rd == null) return;
        HordeConfigMultiData.RiftSettings rs = settings();
        if (!victory) {
            endAt = server.getTickCount() + 20; // arrêt manuel : fermeture immédiate
            phase = Phase.ENDING;
            restoreSpawnRadius();
            return;
        }
        restoreSpawnRadius();
        if (phase == Phase.HORDE) {
            nextPhase = rs.siegeEnabled ? Phase.SIEGE : rs.titanEnabled ? Phase.TITAN : Phase.NONE;
            if (nextPhase == Phase.NONE) {   // fin de la Faille : les récompenses ont déjà été données par le HordeManager
                tellAll(rd, WSLang.t("rift.victory"));
                toEnding(server, rd, true);
                return;
            }
            tellAll(rd, WSLang.t(rs.siegeEnabled ? "rift.next_siege" : "rift.next_titan"));
        } else {
            nextPhase = Phase.COUNTER;
            siegeHordeOn = false;
            tellAll(rd, WSLang.t("rift.next_counter"));
        }
        phase = Phase.INTERLUDE;
        interludeAt = server.getTickCount() + Math.max(3, rs.interludeSeconds) * 20L;
    }

    /** Vrai tant qu'une phase suivante de la Faille est à venir : le HordeManager ne verse alors ni récompenses ni victoire. */
    public static boolean midCampaign() {
        if (!active) return false;
        HordeConfigMultiData.RiftSettings s = settings();
        return (phase == Phase.HORDE && (s.siegeEnabled || s.titanEnabled)) || phase == Phase.SIEGE;
    }

    private static boolean inCombat() {
        return phase == Phase.HORDE || phase == Phase.SIEGE || phase == Phase.COUNTER || phase == Phase.TITAN;
    }

    /** Lance une tâche dans {@code ticks} ticks (utilisé pour les attaques étalées du Titan). */
    static void later(MinecraftServer server, int ticks, Runnable r) {
        DUE_KEYS.add(new long[]{server.getTickCount() + ticks});
        DUE_TASKS.add(r);
    }

    /** Nouvelle vague / nouveau calme : retardataires admis, ressources du Siège. */
    private static void waveWatch(MinecraftServer server, ServerLevel rd, boolean siege) {
        HordeManager hm = HordeManager.get();
        int wv = hm.getCurrentWave();
        if (wv != lastWave) {   // nouvelle vague : les joueurs arrivés en cours de route entrent dans la partie
            lastWave = wv;
            promoteWaiting(server, rd);
        }
        HordeManager.State st = hm.getState();
        if (st != lastState) {
            lastState = st;
            if (siege && (st == HordeManager.State.WAITING_NEXT_WAVE || st == HordeManager.State.PAUSED)) { // un calme commence
                HordeConfigMultiData.RiftSettings rs = settings();
                tellAll(rd, WSLang.t("rift.calm"));
            }
        }
    }

    private static void promoteWaiting(MinecraftServer server, ServerLevel rd) {
        for (UUID id : new ArrayList<>(waiting)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            waiting.remove(id);
            if (p == null || p.level().dimension() != RIFT_DIM) continue;
            restoreMode(p);
            registered.add(id);
            respawnOnIsland(p, rd);
            p.sendSystemMessage(Component.literal(WSLang.t("rift.joined")));
        }
    }

    private static int playerCount(ServerLevel rd) {
        return Math.max(1, registered.size());
    }

    // ─── Phase 2 : le Siège ───

    private static void startSiege(MinecraftServer server, ServerLevel rd, long now) {
        HordeConfigMultiData.RiftSettings rs = settings();
        phase = Phase.SIEGE;
        growCur = radius;
        growTarget = siegeFinal;
        growNextAt = now + 40;
        siegeStartAt = now + 40 + ((growTarget - growCur + 1) / 2) * 8L + 80;
        siegeHordeOn = false;
        RiftFortress.startBuild(siegeFinal, true);   // cinématique de construction une fois l'île agrandie
        RiftEngines.clear(); // les engins ne sont plus pré-posés : on les achète à la boutique du Monolithe
        for (ServerPlayer p : rd.players()) {
            com.wavesurvivor.network.ShakePacket.send(p, 80, 1.2f);
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(WSLang.t("rift.siege_sub"))));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(WSLang.t("rift.siege_title"))));
        }
        rd.playSound(null, CENTER, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 8f, 0.6f);
        WaveSurvivorMod.LOGGER.info("[Faille] Siège : île {} → {}, forteresse en {}.", growCur, growTarget, RiftFortress.center(siegeFinal));
    }

    private static void startSiegeHorde(MinecraftServer server, ServerLevel rd) {
        HordeConfigMultiData.RiftSettings rs = settings();
        spawnCfg = horde.configData;
        spawnRadiusBackup = spawnCfg.spawnRadius;
        waveBackup = spawnCfg.totalWaves;
        spawnCfg.spawnRadius = 5;
        spawnCfg.totalWaves = Math.max(1, rs.siegeAssaults);
        // Le Siège tourne sur le moteur du Kingdom : Assauts / Calmes, trésor, rôles, tours, remparts et pièges.
        // Une seule Porte, fixe, devant la porte de la forteresse ; réglages propres à la Faille (rétablis ensuite).
        modeBackup = spawnCfg.mode;
        kingdomBackup = spawnCfg.kingdom;
        kingdomOverride = true;
        spawnCfg.mode = "kingdom";
        spawnCfg.kingdom = siegeKingdomSettings(rs);
        // Monstres, vagues spéciales et boss propres au Siège (onglets de la Faille) ; vide = ceux de la Horde
        if (!listsSwapped) {
            bkEntities = spawnCfg.hordeEntities; bkSpecials = spawnCfg.specialWaves; bkBosses = spawnCfg.bossWaves;
            bkUseSpecial = spawnCfg.useSpecialWaves; bkUseBoss = spawnCfg.useBossWaves; bkSpecialChance = spawnCfg.specialWaveChance;
            listsSwapped = true;
        }
        if (spawnCfg.siegeEntities != null && !spawnCfg.siegeEntities.isEmpty()) spawnCfg.hordeEntities = spawnCfg.siegeEntities;
        if (spawnCfg.siegeSpecialWaves != null) {
            spawnCfg.specialWaves = spawnCfg.siegeSpecialWaves;
            spawnCfg.useSpecialWaves = rs.siegeSpecialOn;
            spawnCfg.specialWaveChance = rs.siegeSpecialChance;
        }
        if (spawnCfg.siegeBossWaves != null) {
            spawnCfg.bossWaves = spawnCfg.siegeBossWaves;
            spawnCfg.useBossWaves = rs.siegeBossOn;
        }
        java.util.Map<UUID, Integer> keptKills = new HashMap<>(com.wavesurvivor.horde.rewards.KillTracker.snapshotKills());
        boolean ok = HordeManager.get().startAtQuiet(horde, server, MONOLITH, RIFT_DIM.location().toString());
        if (ok && resuming == null) com.wavesurvivor.horde.rewards.KillTracker.restore(keptKills);
        if (!ok) {
            restoreSpawnRadius();
            tellAll(rd, WSLang.t("rift.start_fail"));
            siegeStartAt = server.getTickCount() + 200; // nouvel essai
            return;
        }
        siegeHordeOn = true;
        RiftFortress.openPortcullis(rd, siegeFinal);   // la herse s'ouvre : les ennemis sortent de la forteresse
        if (resuming != null) {   // reprise : trésor, rôles, défenses et assaut atteint ; Contre-offensive : on enchaîne directement
            RiftSession s = resuming;
            resuming = null;
            com.wavesurvivor.horde.rewards.KillTracker.restore(s.kills);
            if (!s.kingdom.isEmpty()) com.wavesurvivor.horde.kingdom.KingdomManager.restoreState(s.kingdom, server);
            RiftEngines.restore(rd, s.engines);   // engins de siège reposés
            if (s.phase == 3) {
                HordeManager.get().clearKingdomMobs();
                com.wavesurvivor.horde.kingdom.KingdomManager.enterHold();
                siegeCleared();
            }
            tellAll(rd, WSLang.t("rift.session.resumed", s.wave));
        }
        lastWave = HordeManager.get().getCurrentWave();
        lastState = null;
        tellAll(rd, WSLang.t("rift.siege_go"));
    }

    /** Réglages Kingdom du Siège : une Porte fixe à la porte de la forteresse, pas de gardiens ni de boss final, claim = île. */
    private static HordeConfigMultiData.KingdomSettings siegeKingdomSettings(HordeConfigMultiData.RiftSettings rs) {
        HordeConfigMultiData.KingdomSettings k = new HordeConfigMultiData.KingdomSettings();
        BlockPos g = RiftFortress.spawnPoint(siegeFinal);
        HordeConfigMultiData.KingdomSettings.GatePoint gp = new HordeConfigMultiData.KingdomSettings.GatePoint();
        gp.x = g.getX(); gp.y = g.getY(); gp.z = g.getZ();
        k.gatePlacement = "fixed";
        k.gatePoints = new ArrayList<>(List.of(gp));
        k.gateCount = 1;
        k.gateFacing = "monolith";
        k.portalScale = 2.0;
        k.portalHealth = 100000;
        k.assaultBudget = Math.max(5, rs.siegeBudget);
        k.assaultGrowth = Math.max(0, rs.siegeGrowthPercent) / 100.0;
        k.calmSeconds = Math.max(15, rs.siegeCalmSeconds);
        k.aliveCap = 60;
        k.maxCycles = 0;                 // la fin du Siège est décidée par la Faille (nombre d'assauts)
        k.guardiansPerGate = 0;
        k.finalBoss = false;
        k.raidArena = false;
        k.smallBreachSeconds = 0;
        k.calmObjectives.enabled = false;
        k.claimRadius = Math.max(16, siegeFinal);
        k.buildOnlyInClaim = true;
        return k;
    }

    /** Appelé par le Kingdom à la fin de chaque Assaut : le nombre d'assauts du Siège est atteint → Contre-offensive. */
    public static boolean siegeAssaultsDone(int cycle) {
        return active && phase == Phase.SIEGE && kingdomOverride && cycle >= Math.max(1, settings().siegeAssaults);
    }

    /** Les assauts du Siège sont finis : le royaume passe en veille (le Monolithe garde sa boutique) et la Contre-offensive s'annonce. */
    public static void siegeCleared() {
        if (!active || phase != Phase.SIEGE) return;
        MinecraftServer server = world != null ? world.getServer() : null;
        ServerLevel rd = server != null ? server.getLevel(RIFT_DIM) : null;
        if (rd == null) return;
        nextPhase = Phase.COUNTER;
        siegeHordeOn = false;
        tellAll(rd, WSLang.t("rift.next_counter"));
        phase = Phase.INTERLUDE;
        interludeAt = server.getTickCount() + Math.max(3, settings().interludeSeconds) * 20L;
    }

    /** Vrai pendant le Siège sur le moteur du Kingdom (Porte invulnérable, pas de Catalyseur, présages à plusieurs Portes retirés). */
    public static boolean kingdomSiege() {
        return active && phase == Phase.SIEGE && kingdomOverride;
    }

    // ─── Sauvegarde et reprise ───

    /**
     * Enregistre un point de reprise : Horde (vague atteinte), Siège (au calme, avec le royaume) ou début de la Contre-offensive.
     * Les autres moments ne changent pas la sauvegarde existante (le Titan n'a pas de point de reprise).
     */
    static void captureSession(MinecraftServer server, boolean counterStart) {
        if (server == null || !active || horde == null || horde.configData == null || home == null || world == null) return;
        HordeManager hm = HordeManager.get();
        int ph;
        int wave = 0;
        net.minecraft.nbt.CompoundTag kingdom = new net.minecraft.nbt.CompoundTag();
        if (counterStart) {
            ph = 3;
            kingdom = com.wavesurvivor.horde.kingdom.KingdomManager.saveState();
        } else if (phase == Phase.HORDE && hm.isRunning()) {
            ph = 1;
            wave = hm.getCurrentWave();
            if (hm.getState() != HordeManager.State.RUNNING) wave++;
            wave = Math.max(1, Math.min(wave, Math.max(1, horde.configData.totalWaves)));
        } else if (phase == Phase.SIEGE && siegeHordeOn && com.wavesurvivor.horde.kingdom.KingdomManager.isActive()) {
            ph = 2;
            kingdom = com.wavesurvivor.horde.kingdom.KingdomManager.saveState();
            // Pendant un assaut, le point de reprise est l'assaut précédent (on rejouera celui-ci) ; tours, trésor et rôles sont à jour
            if (!com.wavesurvivor.horde.kingdom.KingdomManager.isCalmPhase()) kingdom.putInt("cycle", Math.max(0, kingdom.getInt("cycle") - 1));
            wave = kingdom.getInt("cycle");
        } else if ((phase == Phase.COUNTER || phase == Phase.TITAN || phase == Phase.INTERLUDE) && nextPhaseIsAfterSiege()
                && com.wavesurvivor.horde.kingdom.KingdomManager.isActive()) {
            ph = 3;   // Contre-offensive / Titan : reprise au début de la Contre-offensive, avec les tours, le trésor et les engins à jour
            kingdom = com.wavesurvivor.horde.kingdom.KingdomManager.saveState();
        } else {
            return;
        }
        RiftSession s = RiftSession.get(server);
        s.phase = ph;
        s.wave = wave;
        s.horde = horde.hordeName;
        s.dim = world.dimension().location().toString();
        s.altar = home;
        s.fortress = RiftFortress.currentId();
        s.kingdom = kingdom;
        s.engines = RiftEngines.save(server.getLevel(RIFT_DIM));
        var diff = com.wavesurvivor.horde.difficulty.HordeDifficulty.active();
        s.difficulty = diff == null ? "" : diff.id;
        s.mutators = com.wavesurvivor.horde.mutator.Mutator.join(com.wavesurvivor.horde.mutator.HordeMutators.active());
        float[] snap = com.wavesurvivor.altar.AltarDefense.snapshot();
        if (snap != null) {
            s.hp = snap[0];
            s.maxHp = snap[1];
            for (int i = 0; i < 3; i++) s.levels[i] = (int) snap[2 + i];
        }
        s.elapsed = hm.getStartedAtTick() > 0 ? Math.max(0, server.getTickCount() - hm.getStartedAtTick()) : 0;
        s.kills.clear();
        s.kills.putAll(com.wavesurvivor.horde.rewards.KillTracker.snapshotKills());
        s.setDirty();
    }

    /** Abandon de la Faille sauvegardée (bouton « Abandonner »). */
    public static void abandonSaved(MinecraftServer server) {
        RiftSession.discard(server);
    }

    /** Vrai une fois le Siège terminé (Contre-offensive, Titan, ou l'interlude qui y mène). */
    private static boolean nextPhaseIsAfterSiege() {
        return phase == Phase.COUNTER || phase == Phase.TITAN || (phase == Phase.INTERLUDE && (nextPhase == Phase.COUNTER || nextPhase == Phase.TITAN));
    }

    /** Fermeture du serveur : point de reprise enregistré, puis la Faille est fermée proprement (île vidée, joueurs renvoyés). */
    public static void shutdown(MinecraftServer server) {
        if (!active) return;
        captureSession(server, false);
        RiftSession.setKeep(true);
        try {
            stop(server);
        } finally {
            RiftSession.setKeep(false);
        }
    }

    /** Reprend la Faille sauvegardée : île et forteresse reconstruites, joueurs envoyés sur l'île, puis la phase sauvegardée repart. */
    public static boolean resumeSaved(MinecraftServer server, ServerPlayer starter) {
        RiftSession s = RiftSession.get(server);
        if (!s.present()) { if (starter != null) starter.sendSystemMessage(Component.literal(WSLang.t("rift.session.none"))); return false; }
        if (active) { if (starter != null) starter.sendSystemMessage(Component.literal(WSLang.t("rift.already"))); return false; }
        if (!available()) { if (starter != null) starter.sendSystemMessage(Component.literal(WSLang.t("rift.need_pehkui"))); return false; }
        HordeConfigMultiData h = WaveSurvivorMod.getConfig().findHorde(s.horde);
        ServerLevel rd = server.getLevel(RIFT_DIM);
        ServerLevel w = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                new net.minecraft.resources.ResourceLocation(s.dim)));
        if (h == null || rd == null || w == null || s.altar == null) {
            if (starter != null) starter.sendSystemMessage(Component.literal(WSLang.t("session.missing", s.horde)));
            return false;
        }
        RiftSession copy = s.copy();
        s.phase = 0;   // la reprise remet tout à zéro : une nouvelle sauvegarde sera écrite dans 30 s
        s.setDirty();
        horde = h;
        world = w;
        home = copy.altar;
        HordeConfigMultiData.RiftSettings rs = h.configData != null ? h.configData.rift() : new HordeConfigMultiData.RiftSettings();
        siegeFinal = Math.max(Math.max(8, Math.min(48, rs.islandRadius)), Math.min(48, rs.siegeRadius));
        radius = copy.phase == 1 ? Math.max(8, Math.min(48, rs.islandRadius)) : siegeFinal;   // Siège / Contre-offensive : l'île a fini de grandir
        RiftFortress.select(copy.fortress);
        ensureResourceEvents(h);
        forceChunks(rd, rs);
        buildIsland(rd);
        active = true;
        phase = Phase.LOBBY;
        long now = server.getTickCount();
        startAt = now + 100;
        endAt = -1;
        registered.clear();
        waiting.clear();
        reviveAt.clear();
        prevMode.clear();
        gate = null;
        resuming = copy;
        for (ServerPlayer p : new ArrayList<>(server.getPlayerList().getPlayers())) enter(p);
        WaveSurvivorMod.LOGGER.info("[Faille] Reprise de '{}' (phase {}, vague/assaut {}).", copy.horde, copy.phase, copy.wave);
        return true;
    }

    /** Reprise au Siège ou à la Contre-offensive : Monolithe et défense rétablis, forteresse reposée, puis le Siège (et son royaume) redémarre. */
    private static void resumeLate(MinecraftServer server, ServerLevel rd) {
        RiftSession s = resuming;
        ensureDefense(horde.hordeName);
        placeMonolith(rd);
        com.wavesurvivor.horde.difficulty.HordeDifficulty.setPending(com.wavesurvivor.horde.difficulty.Difficulty.byId(s.difficulty));
        com.wavesurvivor.horde.mutator.HordeMutators.setPending(com.wavesurvivor.horde.mutator.Mutator.parse(s.mutators));
        com.wavesurvivor.altar.AltarDefense.onHordeStart(server, horde.hordeName, RIFT_DIM.location().toString(), MONOLITH);
        if (!com.wavesurvivor.altar.AltarDefense.isActive()) {
            tellAll(rd, WSLang.t("rift.start_fail"));
            startAt = -1;
            resuming = null;
            return;
        }
        com.wavesurvivor.horde.difficulty.HordeDifficulty.begin(server);
        com.wavesurvivor.horde.mutator.HordeMutators.begin(server, false);
        if (s.maxHp > 0) com.wavesurvivor.altar.AltarDefense.restore(s.hp, s.maxHp, s.levels);
        long now = server.getTickCount();
        phase = Phase.SIEGE;
        growCur = growTarget = siegeFinal;
        radius = siegeFinal;
        growNextAt = now;
        siegeStartAt = now + 20;
        siegeHordeOn = false;
        RiftEngines.clear();
        RiftFortress.startBuild(siegeFinal, false);   // reprise : la forteresse se reforme sans cinématique
        tellAll(rd, WSLang.t("rift.session.rebuilding"));
    }

    // ─── Phase 3 : la Contre-offensive ───

    private static void startCounter(MinecraftServer server, ServerLevel rd, long now) {
        HordeConfigMultiData.RiftSettings rs = settings();
        phase = Phase.COUNTER;
        com.wavesurvivor.horde.renaissance.Heritage.onWaveStart(server);   // Survivant 1 / 2 au début de la Contre-offensive
        com.wavesurvivor.item.RiftRelics.onWaveStart(server);              // Bouclier de Faille (ensemble 4 pièces)
        coreSpawned = false;
        captureSession(server, true);   // point de reprise : la Contre-offensive recommence avec tous ses catalyseurs
        BlockPos c = RiftFortress.center(siegeFinal);
        double f = 1.0 + 0.4 * Math.max(0, playerCount(rd) - 1);
        java.util.List<BlockPos> cs = RiftFortress.catalystPositions(siegeFinal);
        int n = Math.max(1, Math.min(cs.size(), rs.catalysts));
        for (int i = 0; i < n; i++) {
            BlockPos p = cs.get(i * cs.size() / n);   // répartis sur le cercle des catalyseurs du plan
            RiftFortress.spawnTarget(rd, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, (float) (rs.catalystHp * f), WSLang.t("rift.catalyst"), true);
        }
        // Le bouclier tombe
        rd.playSound(null, c, SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 8f, 0.5f);
        rd.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER, c.getX() + 0.5, c.getY() + 6, c.getZ() + 0.5, 4, 8, 4, 8, 0);
        for (ServerPlayer p : rd.players()) {
            com.wavesurvivor.network.ShakePacket.send(p, 60, 1.0f);
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(WSLang.t("rift.counter_sub", n))));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(WSLang.t("rift.counter_title"))));
        }
        nextReinforce = now + 100;
    }

    private static void counterTick(MinecraftServer server, ServerLevel rd, long now) {
        if (now % 20 != 0) return;
        HordeConfigMultiData.RiftSettings rs = settings();
        RiftFortress.catalystRegen(rd, Math.max(1, rs.catalystRegenRadius), rs.catalystRegenPercent);
        int alive = RiftFortress.aliveTargets(rd);
        BlockPos c = RiftFortress.center(siegeFinal);
        if (!coreSpawned && alive == 0) {
            coreSpawned = true;   // le cœur du donjon n'apparaît (et n'est attaquable) qu'une fois les catalyseurs détruits
            double f = 1.0 + 0.4 * Math.max(0, playerCount(rd) - 1);
            BlockPos hp = RiftFortress.heart(siegeFinal);
            RiftFortress.spawnTarget(rd, hp.getX() + 0.5, hp.getY(), hp.getZ() + 0.5, (float) (rs.coreHp * f), WSLang.t("rift.core"), false);
            tellAll(rd, WSLang.t("rift.core_appears"));
            for (ServerPlayer p : rd.players()) com.wavesurvivor.network.ShakePacket.send(p, 40, 1.0f);
        } else if (coreSpawned && alive == 0) {
            tellAll(rd, WSLang.t("rift.core_down"));
            nextPhase = rs.titanEnabled ? Phase.TITAN : Phase.NONE;
            phase = Phase.INTERLUDE;
            interludeAt = now + 100;
            return;
        }
        // Renforts : les ennemis continuent d'arriver de la forteresse
        var counterPool = phaseEntities("counter");
        if (now >= nextReinforce && counterPool != null && !counterPool.isEmpty()) {
            nextReinforce = now + Math.max(5, rs.reinforceSeconds) * 20L;
            int near = rd.getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, new net.minecraft.world.phys.AABB(c).inflate(160, 60, 160)).size();
            if (near < 35) {
                var tpl = counterPool.get(RNG.nextInt(counterPool.size()));
                int count = Math.max(2, tpl.baseCount + tpl.countIncrement) + playerCount(rd);
                com.wavesurvivor.horde.spawn.HordeSpawner.spawnMobs(rd, tpl, RiftFortress.spawnPoint(siegeFinal), 5, count, playerCount(rd));
            }
        }
    }

    /** Le repère d'un catalyseur est dans l'air (cristal) : les gardiens apparaissent sur le sol en dessous (îlots du Bastion compris). */
    private static BlockPos onGround(ServerLevel l, BlockPos p) {
        for (int dy = 0; dy <= 10; dy++) {
            BlockPos q = p.below(dy);
            if (l.getBlockState(q).isAir() && !l.getBlockState(q.below()).getCollisionShape(l, q.below()).isEmpty()) return q;
        }
        return p;
    }

    /**
     * Pauses de la Horde et Calmes du Siège : coffres roulette en cercle autour du Monolithe de l'île (le Monolithe de la Faille n'est pas
     * un autel enregistré, le placement des hordes ne s'applique donc pas ici). @return vrai si la Faille s'en est chargée.
     */
    public static boolean placeChests(ServerLevel lvl) {
        if (!active || lvl == null || lvl.dimension() != RIFT_DIM) return false;
        int n = settings().riftChests;
        if (n <= 0) return true;
        com.wavesurvivor.altar.AltarRecipes.Zone z = new com.wavesurvivor.altar.AltarRecipes.Zone();
        z.maxChests = n;
        z.autoLayout = true;
        int r = phase == Phase.HORDE ? Math.max(6, radius - 3) : Math.max(8, Math.min(siegeFinal - 6, 18));
        var layout = com.wavesurvivor.altar.AltarLayout.compute(lvl, MONOLITH, r, z, java.util.List.of());
        com.wavesurvivor.horde.roulette.RouletteChestSpawner.spawnAtSpots(lvl, layout.chests());
        return true;
    }

    /** Rayon d'île du Siège : celui de la Faille ouverte, sinon celui de la horde « La Faille » par défaut (tests). */
    static int testRadius() {
        return active ? siegeFinal : Math.max(16, Math.min(48, new HordeConfigMultiData.RiftSettings().siegeRadius));
    }

    /** Une chaîne d'un catalyseur se brise : 3 à 5 gardiens surgissent autour, plus forts à chaque phase ({@code phase} = 0, 1, 2). */
    static void catalystGuards(ServerLevel l, BlockPos at, int phase) {
        var guardPool = phaseEntities("counter");
        if (guardPool == null || guardPool.isEmpty()) return;
        HordeConfigMultiData.RiftSettings rs = settings();
        int lo = Math.max(1, rs.catalystGuardMin), hi = Math.max(lo, rs.catalystGuardMax);
        int n = Math.min(hi, lo + phase);
        double boost = 1.0 + Math.max(0, rs.catalystGuardBoostPercent) / 100.0 * phase;
        phaseSpecial(l, "counter", at, 4, phase + 1);
        phaseBoss(l, "counter", at, 4, phase + 1);
        for (int i = 0; i < n; i++) {
            var tpl = guardPool.get(RNG.nextInt(guardPool.size()));
            double a = i * Math.PI * 2 / n + RNG.nextDouble();
            BlockPos pos = onGround(l, at.offset((int) Math.round(Math.cos(a) * 3), 0, (int) Math.round(Math.sin(a) * 3)));
            Entity e = com.wavesurvivor.horde.spawn.HordeSpawner.spawnSummonFromTemplate(l, tpl, pos, playerCount(l));
            if (e instanceof net.minecraft.world.entity.LivingEntity le && boost > 1.0) {
                var hpAttr = le.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                if (hpAttr != null) { hpAttr.setBaseValue(hpAttr.getBaseValue() * boost); le.setHealth(le.getMaxHealth()); }
                var dmg = le.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                if (dmg != null) dmg.setBaseValue(dmg.getBaseValue() * boost);
            }
        }
    }

    // ─── Phase 4 : le Titan, puis la fin ───

    private static void startTitan(MinecraftServer server, ServerLevel rd, long now) {
        promoteWaiting(server, rd);
        // Les derniers ennemis disparaissent : place au Titan
        List<Entity> gone = new ArrayList<>();
        for (Entity e : rd.getAllEntities()) if (e instanceof net.minecraft.world.entity.monster.Enemy && !(e instanceof ServerPlayer)) gone.add(e);
        for (Entity e : gone) e.discard();
        phase = Phase.TITAN;
        titanWaveN = 0;
        com.wavesurvivor.horde.renaissance.Heritage.onWaveStart(server);   // Survivant 1 / 2 à l'arrivée du Titan
        com.wavesurvivor.item.RiftRelics.onWaveStart(server);              // Bouclier de Faille (ensemble 4 pièces)
        RiftTitan.spawn(rd, settings(), playerCount(rd), siegeFinal);
    }

    private static void finalVictory(MinecraftServer server, ServerLevel rd) {
        tellAll(rd, WSLang.t("rift.victory"));
        finalRewards(server, rd);
        toEnding(server, rd, true);
    }

    /** Récompenses de fin de Faille : celles de la horde (classement, coffres…) et le butin du Titan. */
    private static void finalRewards(MinecraftServer server, ServerLevel rd) {
        if (horde == null) return;
        try {
            java.util.Map<UUID, Integer> kills = new HashMap<>(com.wavesurvivor.horde.rewards.KillTracker.snapshotKills());
            podiumRank.clear();
            List<java.util.Map.Entry<UUID, Integer>> order = new ArrayList<>(kills.entrySet());
            order.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
            for (int i = 0; i < Math.min(3, order.size()); i++) podiumRank.put(order.get(i).getKey(), i + 1);
            com.wavesurvivor.horde.rewards.RewardsDistributor.distribute(horde, server);   // récompenses de classement réglées dans l'éditeur
            riftPayout(server, rd, true);
            podium(server, rd, kills);
            com.wavesurvivor.horde.stats.PlayerStats.addForAll(server, "hordes_won", 1);
            com.wavesurvivor.horde.difficulty.HordeProgress.recordWin(server, horde.hordeName);
        } catch (Exception ex) {
            WaveSurvivorMod.LOGGER.warn("[Faille] Récompenses de fin : {}", ex.getMessage());
        }
        if (RiftTitan.isDead() || settings().titanEnabled) {
            for (ServerPlayer p : rd.players()) {
                if (p.isSpectator()) continue;
                net.minecraftforge.items.ItemHandlerHelper.giveItemToPlayer(p, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, 16));
                net.minecraftforge.items.ItemHandlerHelper.giveItemToPlayer(p, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.AMETHYST_SHARD, 12));
                net.minecraftforge.items.ItemHandlerHelper.giveItemToPlayer(p, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CRYING_OBSIDIAN, 4));
                p.sendSystemMessage(Component.literal(WSLang.t("rift.loot")));
            }
        }
    }

    /** 2.0 — Trophée du Titan : PR par boss vaincu (Titan compris), versés à part. */
    private static void trophyPayout(ServerPlayer p, int bosses) {
        int pr = com.wavesurvivor.item.RiftRelics.trophyPrFor(p, bosses);
        if (pr <= 0) return;
        com.wavesurvivor.horde.renaissance.RenaissanceStore.addPoints(p, pr);
        p.sendSystemMessage(Component.literal(WSLang.t("relic.trophee_titan.paid", pr, bosses)));
    }

    /** Boss tués pendant la Faille (PR). */
    private static int riftBosses;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRiftBossDeath(LivingDeathEvent e) {
        if (active && !e.getEntity().level().isClientSide && e.getEntity().level().dimension() == RIFT_DIM
                && com.wavesurvivor.horde.boss.BossManager.isBoss(e.getEntity().getUUID())) riftBosses++;
    }

    /** Joueurs ayant participé : inscrits à la Faille et encore connectés, plus ceux présents sur l'île. */
    private static List<ServerPlayer> participants(MinecraftServer server, ServerLevel rd) {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : registered) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) out.add(p);
        }
        if (rd != null) for (ServerPlayer p : rd.players()) if (!out.contains(p) && !waiting.contains(p.getUUID())) out.add(p);
        return out;
    }

    /**
     * PR de la Faille : l'équivalent d'une horde (1 par vague + 5 si la Horde est finie) et d'un Kingdom (2 par Assaut + 5 si le Siège est tenu),
     * + 1 par boss, + le bonus du Titan s'il est tué, + 15 à la première victoire ; × difficulté, mutateurs et bonus personnels (Renaissance, reliques).
     * En défaite : ce qui a été acquis jusque-là (phases terminées comprises).
     */
    /** Rang au podium de la Faille en cours (1 à 3), calculé juste avant les récompenses. */
    private static final java.util.Map<UUID, Integer> podiumRank = new HashMap<>();

    private static void riftPayout(MinecraftServer server, ServerLevel rd, boolean victory) {
        if (horde == null || horde.configData == null) return;
        HordeConfigMultiData.RiftSettings rs = settings();
        int rank = switch (phase) {
            case SIEGE -> 1;
            case COUNTER, TITAN -> 2;
            case INTERLUDE -> nextPhase == Phase.SIEGE ? 1 : nextPhase == Phase.COUNTER ? 2 : nextPhase == Phase.TITAN ? (rs.siegeEnabled ? 2 : 1) : 0;
            default -> 0;
        };
        if (victory) rank = 2;
        boolean hordeDone = rank >= 1, siegeDone = rank >= 2 && rs.siegeEnabled;
        int hordeTotal = Math.max(1, kingdomOverride && waveBackup > 0 ? waveBackup : horde.configData.totalWaves);
        int cur = Math.max(0, HordeManager.get().getCurrentWave() - 1);
        int hordeWaves = hordeDone ? hordeTotal : (phase == Phase.HORDE ? cur : 0);
        int assaults = siegeDone ? Math.max(1, rs.siegeAssaults) : (phase == Phase.SIEGE ? cur : 0);
        boolean titan = victory && RiftTitan.isDead();
        int base = hordeWaves + assaults * 2 + riftBosses;
        int bonus = (hordeDone ? 5 : 0) + (siegeDone ? 5 : 0) + (titan ? Math.max(0, rs.titanPrBonus) : 0);
        double diff = com.wavesurvivor.horde.renaissance.RenaissanceRewards.difficultyMult();
        double muta = Math.max(1.0, com.wavesurvivor.horde.mutator.HordeMutators.multiplier());
        var progress = com.wavesurvivor.horde.difficulty.HordeProgress.get(server);
        for (ServerPlayer p : participants(server, rd)) {
            boolean firstWin = victory;
            if (firstWin) for (String fam : com.wavesurvivor.altar.AltarRecipes.familyOf(horde.hordeName)) {
                if (progress.best(p.getUUID(), fam) >= 0) { firstWin = false; break; }
            }
            double personal = com.wavesurvivor.horde.renaissance.Heritage.prMultiplier(p) * com.wavesurvivor.item.RelicSets.prMultiplier(p);
            int total = (int) Math.round((base + bonus) * diff * muta * personal) + (firstWin ? 15 : 0);
            if (total <= 0) continue;
            com.wavesurvivor.horde.renaissance.RenaissanceStore.addPoints(p, total);
            p.sendSystemMessage(Component.literal(WSLang.t("rift.pr.earned", total, hordeWaves, assaults, riftBosses, titan ? rs.titanPrBonus : 0,
                    String.format("%.2f", diff * muta * personal)) + (firstWin ? WSLang.t("pr.first", 15) : "")));
            p.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.4f);
            trophyPayout(p, riftBosses + (titan ? 1 : 0));
            WaveSurvivorMod.LOGGER.info("[Faille] {} +{} PR ({} vagues, {} assauts, {} boss, Titan={}, victoire={})", p.getGameProfile().getName(), total, hordeWaves, assaults, riftBosses, titan, victory);
        }
        if (!victory) return;
        // Titan vaincu : Éclats de Faille pour chaque participant (+1 avec l'Héritage de la Faille 3) et Panthéon (titres, Marques du Titan)
        if (titan) {
            var dd = com.wavesurvivor.horde.difficulty.HordeDifficulty.active();
            String diffId = dd != null ? dd.id : "normal";
            for (ServerPlayer p : participants(server, rd)) {
                int shards = Math.max(0, rs.shardsPerPlayer) + (com.wavesurvivor.horde.renaissance.RiftLegacy.has(p, 3) ? 1 : 0);
                if (shards > 0) {
                    net.minecraftforge.items.ItemHandlerHelper.giveItemToPlayer(p, new net.minecraft.world.item.ItemStack(com.wavesurvivor.registry.ModItems.RIFT_SHARD.get(), shards));
                    p.sendSystemMessage(Component.literal(WSLang.t("rift.reward.shards", shards)));
                }
                com.wavesurvivor.horde.renaissance.RiftLegacy.onVictory(p, diffId, podiumRank.getOrDefault(p.getUUID(), 0));
            }
        }
        // Chaque participant : Reliquaire(s) scellé(s) (une relique au hasard)
        int n = Math.max(0, rs.rewardReliquaries);
        if (n > 0) for (ServerPlayer p : participants(server, rd)) {
            net.minecraftforge.items.ItemHandlerHelper.giveItemToPlayer(p, new net.minecraft.world.item.ItemStack(com.wavesurvivor.registry.ModItems.RELIQUAIRE_SCELLE.get(), n));
            p.sendSystemMessage(Component.literal(WSLang.t("rift.reward.reliquary", n)));
        }
    }

    /** Podium (éliminations sur toute la Faille) : 3e un peu plus, 2e plus que le 3e, 1er plus que le 2e. */
    private static void podium(MinecraftServer server, ServerLevel rd, java.util.Map<UUID, Integer> kills) {
        HordeConfigMultiData.RiftSettings rs = settings();
        List<java.util.Map.Entry<UUID, Integer>> ranked = new ArrayList<>(kills.entrySet());
        ranked.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        int[] pr = {rs.topPr1, rs.topPr2, rs.topPr3}, rel = {rs.topReliq1, rs.topReliq2, rs.topReliq3}, sh = {rs.topShards1, rs.topShards2, rs.topShards3};
        boolean titanDown = RiftTitan.isDead();
        String[] medal = {"§6§l", "§f§l", "§c§l"};
        for (int i = 0; i < Math.min(3, ranked.size()); i++) {
            ServerPlayer p = server.getPlayerList().getPlayer(ranked.get(i).getKey());
            if (p == null) continue;
            if (pr[i] > 0) com.wavesurvivor.horde.renaissance.RenaissanceStore.addPoints(p, pr[i]);
            if (rel[i] > 0) net.minecraftforge.items.ItemHandlerHelper.giveItemToPlayer(p, new net.minecraft.world.item.ItemStack(com.wavesurvivor.registry.ModItems.RELIQUAIRE_SCELLE.get(), rel[i]));
            if (titanDown && sh[i] > 0) net.minecraftforge.items.ItemHandlerHelper.giveItemToPlayer(p, new net.minecraft.world.item.ItemStack(com.wavesurvivor.registry.ModItems.RIFT_SHARD.get(), sh[i]));
            for (ServerPlayer o : participants(server, rd)) {
                o.sendSystemMessage(Component.literal(medal[i] + WSLang.t("rift.podium", i + 1, p.getGameProfile().getName(), ranked.get(i).getValue(), Math.max(0, pr[i]), Math.max(0, rel[i]))
                        + (titanDown && sh[i] > 0 ? WSLang.t("rift.podium_shards", sh[i]) : "")));
            }
        }
    }

    /** Emplacement du portail de sortie : au nord du Monolithe (face à l'arrivée des joueurs, qui est au sud). */
    private static final BlockPos EXIT_PORTAL = CENTER.offset(0, 1, -7);

    /** Le portail de sortie est-il ouvert (pause de fin) ? Le toucher renvoie le joueur chez lui. */
    public static boolean exitOpen() { return active && phase == Phase.ENDING; }

    private static void placeExitPortal(ServerLevel rd) {
        BlockState frame = Blocks.CRYING_OBSIDIAN.defaultBlockState();
        BlockState portal = com.wavesurvivor.registry.ModBlocks.RIFT_PORTAL.get().defaultBlockState();
        for (int dx = -2; dx <= 2; dx++) for (int dy = -1; dy <= 4; dy++) {
            BlockPos p = EXIT_PORTAL.offset(dx, dy, 0);
            boolean edge = dx == -2 || dx == 2 || dy == -1 || dy == 4;
            rd.setBlock(p, edge ? (dy == -1 && Math.abs(dx) < 2 ? Blocks.OBSIDIAN.defaultBlockState() : frame) : portal, 3);
        }
        rd.playSound(null, EXIT_PORTAL, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 3f, 1.0f);
        rd.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, EXIT_PORTAL.getX() + 0.5, EXIT_PORTAL.getY() + 1.5, EXIT_PORTAL.getZ() + 0.5, 80, 1.0, 1.5, 0.3, 0.2);
    }

    private static void toEnding(MinecraftServer server, ServerLevel rd, boolean victory) {
        phase = Phase.ENDING;
        // XP de Maîtrise des rôles du Kingdom (Siège, Contre-offensive, Titan) : versée ici, avant que le royaume en veille soit retiré
        com.wavesurvivor.horde.kingdom.vocation.VocationXp.finish(server, victory);
        if (HordeManager.get().isRunning() && com.wavesurvivor.horde.kingdom.KingdomManager.isActive()) HordeManager.get().stopSilently(); // royaume en veille : boutique et défenses retirées avec la partie
        endAt = server.getTickCount() + Math.max(10, settings().endPauseSeconds) * 20L;
        restoreSpawnRadius();
        // Le Titan et ses séquelles disparaissent ; un portail de sortie s'ouvre près du Monolithe (victoire comme défaite)
        RiftTitan.clear(rd);
        List<net.minecraft.world.entity.Entity> foes = new ArrayList<>();
        for (net.minecraft.world.entity.Entity e : rd.getAllEntities()) if (e instanceof net.minecraft.world.entity.monster.Monster) foes.add(e);
        for (net.minecraft.world.entity.Entity e : foes) e.discard();
        placeExitPortal(rd);
        feed(rd, "§a" + WSLang.t("rift.exit_open"));
        // Tout le monde est libéré : plus de spectateurs pendant la pause
        for (ServerPlayer p : new ArrayList<>(rd.players())) {
            boolean wasOut = p.isSpectator();
            restoreMode(p);
            if (wasOut) respawnOnIsland(p, rd);
        }
    }

    /** Dernier passage par un portail de la Faille (anti-rebond : un seul voyage par seconde et demie). */
    private static final Map<UUID, Long> PORTAL_CD = new HashMap<>();

    /** Un joueur touche un portail DANS la Faille : sortie en fin de partie, sinon retour sur l'île près du Monolithe. */
    public static void portalTouch(ServerPlayer p) {
        if (!active || p.isSpectator() || !p.isAlive()) return;
        long now = p.getServer().getTickCount();
        Long last = PORTAL_CD.get(p.getUUID());
        if (last != null && now - last < 30) return;
        PORTAL_CD.put(p.getUUID(), now);
        if (exitOpen()) sendHome(p);
        else backToMonolith(p);
    }

    /** Filet de sécurité : un joueur dont les pieds ou la tête sont dans un bloc de portail le traverse, même si le jeu n'a pas signalé le contact. */
    private static void portalScan(ServerLevel rd) {
        var portal = com.wavesurvivor.registry.ModBlocks.RIFT_PORTAL.get();
        for (ServerPlayer p : new ArrayList<>(rd.players())) {
            if (p.isSpectator() || !p.isAlive()) continue;
            net.minecraft.world.phys.AABB b = p.getBoundingBox().inflate(0.05);
            boolean in = false;
            for (BlockPos q : BlockPos.betweenClosed(BlockPos.containing(b.minX, b.minY, b.minZ), BlockPos.containing(b.maxX, b.maxY, b.maxZ))) {
                if (rd.getBlockState(q).is(portal)) { in = true; break; }
            }
            if (in) portalTouch(p);
        }
    }

    /** Portail touché dans la Faille pendant la partie : le joueur revient sur l'île, près du Monolithe. */
    public static void backToMonolith(ServerPlayer p) {
        if (!active || !(p.level() instanceof ServerLevel rd)) return;
        respawnOnIsland(p, rd);
        rd.playSound(null, p.blockPosition(), SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.6f, 1.2f);
    }

    private static void respawnOnIsland(ServerPlayer p, ServerLevel rd) {
        double[] at = arrival();
        p.teleportTo(rd, at[0], CENTER.getY() + 1, at[1], p.getYRot(), 0f);
        p.fallDistance = 0;
        p.setDeltaMovement(0, 0, 0);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 4, false, false));
    }

    // ─── Événements ───

    /**
     * Mort dans la Faille : on ne meurt pas vraiment (l'inventaire reste intact). Pendant la Horde, le joueur passe en spectateur
     * au-dessus de l'île puis revient après un délai ; sinon il réapparaît tout de suite.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDeath(LivingDeathEvent e) {
        if (!active || !(e.getEntity() instanceof ServerPlayer p) || p.level().dimension() != RIFT_DIM) return;
        ServerLevel rd = p.serverLevel();
        e.setCanceled(true);
        p.setHealth(p.getMaxHealth());
        p.clearFire();
        p.fallDistance = 0;
        if (inCombat() && registered.contains(p.getUUID())) {
            int secs = com.wavesurvivor.horde.renaissance.Heritage.respawnSeconds(p, Math.max(3, settings().reviveSeconds)); // Héritage : Survivant 3
            if (com.wavesurvivor.horde.renaissance.RiftLegacy.has(p, 4)) secs = Math.max(2, secs - 5);   // Héritage de la Faille 4 : −5 s
            prevMode.put(p.getUUID(), p.gameMode.getGameModeForPlayer());
            p.setGameMode(GameType.SPECTATOR);
            p.teleportTo(rd, CENTER.getX() + 0.5, CENTER.getY() + 10, CENTER.getZ() + 0.5, p.getYRot(), 30f);
            reviveAt.put(p.getUUID(), p.getServer().getTickCount() + secs * 20L);
            p.sendSystemMessage(Component.literal(WSLang.t("rift.down", secs)));
        } else {
            respawnOnIsland(p, rd);
        }
    }


    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && RiftFortress.testing()) {   // pose de test (/ws rift fortress <id>)
            ServerLevel trd = e.getServer().getLevel(RIFT_DIM);
            if (trd != null) RiftFortress.testTick(trd, e.getServer().getTickCount());
        }
        if (e.phase != TickEvent.Phase.END || !active) return;
        if (gate != null && gate.isRunning()) gate.tick();
        else if (gate != null) gate.tickOpen(e.getServer().getTickCount());
        MinecraftServer server = e.getServer();
        long now = server.getTickCount();
        flow(server, now);
        for (int i = DUE_KEYS.size() - 1; i >= 0; i--) {
            if (now >= DUE_KEYS.get(i)[0]) {
                Runnable r = DUE_TASKS.remove(i);
                DUE_KEYS.remove(i);
                try { r.run(); } catch (Exception ex) { WaveSurvivorMod.LOGGER.warn("[Faille] Tâche différée : {}", ex.getMessage()); }
            }
        }
        if (now % 4 == 0) {
            ServerLevel prd = server.getLevel(RIFT_DIM);
            if (prd != null) portalScan(prd);
        }
        if (now % 10 != 0) return;
        ServerLevel rd = server.getLevel(RIFT_DIM);
        if (rd == null) return;
        // Filet anti-chute (provisoire, jalon 2 : spectateur puis réapparition au Monolithe) : on repose le joueur sur l'île
        for (ServerPlayer p : new ArrayList<>(rd.players())) {
            if (p.isSpectator() || !p.isAlive()) continue;
            if (p.getY() < CENTER.getY() - 40) {
                double[] at = arrival();
                p.teleportTo(rd, at[0], CENTER.getY() + 1, at[1], p.getYRot(), 0f);
                p.fallDistance = 0;
                p.setDeltaMovement(0, 0, 0);
                p.hurt(p.damageSources().fall(), 2f);
            }
        }
    }

    /**
     * Tout monstre tué dans la Faille lâche des émeraudes (monnaie du Kingdom : sans elles, impossible d'acheter ou d'améliorer).
     * Valable pendant toute la partie (Horde, Siège, Contre-offensive, Titan) ; le Kingdom les convertit au ramassage.
     */
    @SubscribeEvent
    public void onMonsterDrops(net.minecraftforge.event.entity.living.LivingDropsEvent e) {
        if (!active || !(e.getEntity() instanceof net.minecraft.world.entity.monster.Enemy) || e.getEntity() instanceof ServerPlayer) return;
        if (e.getEntity().level().dimension() != RIFT_DIM || e.getEntity().getPersistentData().getBoolean("ws_rift_avatar")) return;
        HordeConfigMultiData.RiftSettings s = settings();
        int min = Math.max(0, s.emeraldMin), max = Math.max(min, s.emeraldMax);
        int n = min == max ? min : min + e.getEntity().getRandom().nextInt(max - min + 1);
        if (n <= 0) return;
        e.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(e.getEntity().level(), e.getEntity().getX(), e.getEntity().getY(), e.getEntity().getZ(),
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, n)));
    }

    /** Mort d'un avatar du Titan : le Titan perd des PV. */
    @SubscribeEvent
    public void onAvatarDeath(LivingDeathEvent e) {
        if (!active || e.getEntity() instanceof ServerPlayer || !(e.getEntity().level() instanceof ServerLevel l)) return;
        if (e.getEntity().getPersistentData().getBoolean("ws_rift_avatar")) RiftTitan.onAvatarDeath(l, e.getEntity());
    }

    /** Clic droit sur des vestiges de tour (Siège) : on les rebâtit. */
    @SubscribeEvent
    public void onRuinUse(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock e) {
        if (!active || e.getLevel().isClientSide || e.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        if (!com.wavesurvivor.horde.kingdom.KingdomDefenses.isRuin(e.getPos())) return;
        if (e.getEntity() instanceof ServerPlayer sp) com.wavesurvivor.horde.kingdom.KingdomDefenses.rebuild(sp, e.getPos());
        e.setCanceled(true);
    }

    /** Plafond de dégâts par coup sur le Titan. */
    @SubscribeEvent
    public void onTitanHurt(net.minecraftforge.event.entity.living.LivingHurtEvent e) {
        if (active && e.getEntity().getPersistentData().contains("ws_rift_mark")) e.setAmount(RiftEngines.markedDamage(e.getEntity(), e.getAmount()));
        if (active && RiftTitan.active() && RiftTitan.isTitan(e.getEntity())) e.setAmount(RiftTitan.capDamage(e.getAmount(), e.getEntity().getServer().getTickCount()));
    }

    /** Blocs d'affichage de météore laissés par un arrêt brutal : refusés à l'entrée dans le monde (les vivants sont connus de RiftTitan). */
    @SubscribeEvent
    public void onMeteorJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide || !(e.getEntity() instanceof net.minecraft.world.entity.Display.BlockDisplay d)) return;
        if (d.getTags().contains("ws_meteor") && !RiftTitan.isMeteorPart(d.getUUID())) e.setCanceled(true);
    }

    /** Les monstres de la horde ne s'en prennent jamais au Titan (un golem de fer est une cible naturelle pour eux) ni à ses boîtes de contact : ils restent concentrés sur le Monolithe. */
    @SubscribeEvent
    public void onMobTarget(net.minecraftforge.event.entity.living.LivingChangeTargetEvent e) {
        if (!active || e.getNewTarget() == null) return;
        net.minecraft.world.entity.LivingEntity t = e.getNewTarget();
        if (RiftTitan.isTitan(t) || t.getPersistentData().getBoolean("ws_rift_titan")) e.setCanceled(true);
    }

    /** Frappe au corps à corps sur un catalyseur / le cœur de la forteresse. */
    @SubscribeEvent
    public void onAttackTarget(net.minecraftforge.event.entity.player.AttackEntityEvent e) {
        if (!active || !RiftFortress.isTarget(e.getTarget())) return;
        e.setCanceled(true);
        if (e.getEntity().level().isClientSide) return;
        RiftFortress.damageTarget(e.getTarget(), (float) Math.max(2.0, e.getEntity().getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)),
                e.getEntity() instanceof ServerPlayer sp ? sp : null);
    }

    /** Flèche (baliste, arc) sur un catalyseur / le cœur. */
    @SubscribeEvent
    public void onProjectileTarget(net.minecraftforge.event.entity.ProjectileImpactEvent e) {
        if (!active || !(e.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit) || !RiftFortress.isTarget(hit.getEntity())) return;
        e.setCanceled(true);
        if (e.getProjectile().level().isClientSide) return;
        RiftFortress.damageTarget(hit.getEntity(), 6f, e.getProjectile().getOwner() instanceof ServerPlayer sp ? sp : null);
        e.getProjectile().discard();
    }

    /** Au démarrage du serveur : aucune Faille n'est ouverte, on referme les portails restés ouverts (redémarrage, arrêt brutal). */
    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent e) {
        for (RiftPortals.Rec r : RiftPortals.all(e.getServer())) {
            for (ServerLevel l : e.getServer().getAllLevels()) {
                if (l.dimension().location().toString().equals(r.dim())) RiftGate.of(l, r).seal();
            }
        }
    }

    /** Un joueur qui se reconnecte dans la dimension de la Faille alors qu'aucune Faille n'est ouverte (redémarrage…) est renvoyé chez lui. */
    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.getServer() == null) return;
        if (p.level().dimension() == RIFT_DIM && !active) sendHome(p);
    }

    /** Après une mort, le vanilla le fait réapparaître ailleurs : on oublie le retour mémorisé. */
    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && p.level().dimension() != RIFT_DIM) p.getPersistentData().remove(RETURN_TAG);
    }
}
