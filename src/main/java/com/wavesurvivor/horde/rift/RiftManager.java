package com.wavesurvivor.horde.rift;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.HordeConfigMultiData;
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
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

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
    private static final Random RNG = new Random();

    private static boolean active;
    private static HordeConfigMultiData horde;
    private static ServerLevel world;
    private static BlockPos home;
    /** Portail de la Faille (blocs réels + animation d'ouverture), jalon 1b. */
    private static RiftGate gate;
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
        horde = h;
        world = w;
        home = altar;
        HordeConfigMultiData.RiftSettings rs = h != null && h.configData != null ? h.configData.rift() : new HordeConfigMultiData.RiftSettings();
        radius = Math.max(8, Math.min(48, rs.islandRadius));
        forceChunks(rd);
        buildIsland(rd);
        active = true;
        // Jalon 1b : le portail s'ouvre (animation complète la 1re fois), les joueurs le franchissent à pied
        boolean[] fresh = new boolean[1];
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
        p.teleportTo(rd, CENTER.getX() + 0.5 + (RNG.nextDouble() - 0.5) * 3, CENTER.getY() + 1, CENTER.getZ() + 0.5 + (RNG.nextDouble() - 0.5) * 3, 0f, 0f);
        p.fallDistance = 0;
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 4, false, false));
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(WSLang.t("rift.enter_sub"))));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(WSLang.t("rift.enter_title"))));
        rd.playSound(null, p.blockPosition(), SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.6f, 0.8f);
    }

    // ─── Fin et retour ───

    /** Ferme la Faille : renvoie tout le monde chez soi, vide l'île et libère les chunks. */
    public static void stop(MinecraftServer server) {
        ServerLevel rd = server.getLevel(RIFT_DIM);
        if (rd != null) {
            for (ServerPlayer p : new ArrayList<>(rd.players())) sendHome(p);
            clearIsland(rd);
        }
        if (gate != null) {
            gate.seal();
            gate = null;
        }
        releaseChunks(rd);
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
        if (!tag.isEmpty()) {
            String[] a = tag.split("\\|");
            try {
                ServerLevel l = server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(a[0])));
                if (l != null) dest = l;
                to = new BlockPos(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]));
            } catch (Exception ignored) { /* étiquette illisible : point de réapparition */ }
        }
        if (to == null) to = home != null && world != null ? home : dest.getSharedSpawnPos();
        p.getPersistentData().remove(RETURN_TAG);
        p.teleportTo(dest, to.getX() + 0.5, to.getY() + 1, to.getZ() + 0.5, p.getYRot(), 0f);
        p.fallDistance = 0;
        p.setDeltaMovement(0, 0, 0);
        p.sendSystemMessage(Component.literal(WSLang.t("rift.left")));
    }

    // ─── Île ───

    private static void buildIsland(ServerLevel l) {
        clearIsland(l);
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

    private static void clearIsland(ServerLevel l) {
        int r = Math.max(radius, 48) + 2;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int y0 = Math.max(l.getMinBuildHeight(), CENTER.getY() - 24), y1 = Math.min(l.getMaxBuildHeight() - 1, CENTER.getY() + 40);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r) continue;
                for (int yy = y0; yy <= y1; yy++) {
                    m.set(CENTER.getX() + dx, yy, CENTER.getZ() + dz);
                    if (!l.getBlockState(m).isAir()) l.setBlock(m, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        for (Entity e : l.getEntitiesOfClass(Entity.class, new AABB(CENTER).inflate(r + 16, 128, r + 16), e -> !(e instanceof ServerPlayer))) e.discard();
    }

    private static void forceChunks(ServerLevel l) {
        int r = (Math.max(radius, 48) + 16) >> 4;
        for (int cx = -r; cx <= r; cx++) {
            for (int cz = -r; cz <= r; cz++) {
                l.setChunkForced(cx, cz, true);
                FORCED.add(net.minecraft.world.level.ChunkPos.asLong(cx, cz));
            }
        }
    }

    private static void releaseChunks(ServerLevel l) {
        if (l != null) for (long c : FORCED) l.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c), net.minecraft.world.level.ChunkPos.getZ(c), false);
        FORCED.clear();
    }

    // ─── Événements ───

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !active) return;
        if (gate != null && gate.isRunning()) gate.tick();
        MinecraftServer server = e.getServer();
        long now = server.getTickCount();
        if (now % 10 != 0) return;
        ServerLevel rd = server.getLevel(RIFT_DIM);
        if (rd == null) return;
        // Filet anti-chute (provisoire, jalon 2 : spectateur puis réapparition au Monolithe) : on repose le joueur sur l'île
        for (ServerPlayer p : new ArrayList<>(rd.players())) {
            if (p.isSpectator() || !p.isAlive()) continue;
            if (p.getY() < CENTER.getY() - 40) {
                p.teleportTo(rd, CENTER.getX() + 0.5, CENTER.getY() + 1, CENTER.getZ() + 0.5, p.getYRot(), 0f);
                p.fallDistance = 0;
                p.setDeltaMovement(0, 0, 0);
                p.hurt(p.damageSources().fall(), 2f);
            }
        }
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
