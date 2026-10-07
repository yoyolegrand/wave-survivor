package com.wavesurvivor.horde.kingdom;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.horde.boss.BossManager;
import com.wavesurvivor.horde.model.BossWave;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * ARÈNE DU ROI (mode Kingdom, option « raidArena ») : le boss final n'apparaît pas dans le monde mais attend dans une
 * arène bâtie dans la dimension wavesurvivor:raid. Une fois l'Assaut final repoussé, la Porte du Roi s'ouvre : y entrer
 * téléporte dans l'arène ; le boss surgit 3 s après la première arrivée. Boss vaincu → retour au Monolithe + victoire.
 * Tous les joueurs tombés dans l'arène → le boss se régénère, la Porte reste ouverte (nouvelle tentative).
 * Si la dimension est introuvable, l'arène est bâtie haut dans le ciel, loin du Monolithe.
 */
public final class RaidManager {

    public static final ResourceKey<Level> RAID_DIM = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(WaveSurvivorMod.MODID, "raid"));
    private static final String RETURN_TAG = "ws_raid_return";
    private static final Random RNG = new Random();

    private static boolean open;
    private static ServerLevel world, arena;
    private static BlockPos gate, home, center;
    private static int radius = 18;
    private static BossWave wave;
    private static UUID bossId;
    private static long bossAt = -1;
    private static int bossMissing, emptyTicks;
    private static final Set<Long> FORCED = new HashSet<>();

    /** Public : instance enregistrée sur le bus d'événements (connexion / réapparition). */
    public RaidManager() {}

    public static boolean isOpen() { return open; }

    // ─── Ouverture : arène bâtie, Porte du Roi ouverte ───

    static void open(ServerLevel w, BlockPos gatePos, BlockPos monolith, BossWave bw, int r) {
        open(w, gatePos, monolith, bw, r, KingdomManager::raidVictory, "raid.open");
    }

    /**
     * @param onWin   action à la victoire (fin de partie Kingdom, ou fin de horde classique)
     * @param openKey message d'ouverture (« raid.open » : Porte du Roi ; « raid.open_classic » : portail près de l'autel)
     */
    public static void open(ServerLevel w, BlockPos gatePos, BlockPos monolith, BossWave bw, int r, Runnable onWin, String openKey) {
        if (open || w == null || bw == null) return;
        onVictory = onWin;
        MinecraftServer server = w.getServer();
        world = w;
        gate = gatePos;
        home = monolith;
        wave = bw;
        radius = Math.max(10, Math.min(40, r));
        arena = server.getLevel(RAID_DIM);
        if (arena != null) {
            center = new BlockPos(0, 100, 0);
        } else {
            arena = server.overworld();
            center = new BlockPos(monolith.getX() + 4000, 200, monolith.getZ());
            WaveSurvivorMod.LOGGER.warn("[Raid] Dimension wavesurvivor:raid introuvable : arène bâtie dans le ciel du monde ({}).", center);
        }
        forceChunks();
        build(arena, center, radius);
        bossId = null;
        bossAt = -1;
        bossMissing = 0;
        emptyTicks = 0;
        open = true;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.literal(WSLang.t(openKey, WSLang.t(bw.bossName))));
        }
        w.playSound(null, gate, SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 3f, 0.7f);
        WaveSurvivorMod.LOGGER.info("[Raid] Porte du Roi ouverte @ {} → arène {} @ {}", gate, arena.dimension().location(), center);
    }

    // ─── Boucle ───

    static void tick(long now) {
        if (!open || world == null || arena == null) return;
        // Porte ouverte : tourbillon de particules + faisceau
        if (now % 4 == 0) {
            double gx = gate.getX() + 0.5, gy = gate.getY() + 1.5, gz = gate.getZ() + 0.5;
            for (int i = 0; i < 6; i++) {
                double a = (now * 0.15) + i * Math.PI / 3, rr = 1.4;
                world.sendParticles(ParticleTypes.REVERSE_PORTAL, gx + Math.cos(a) * rr, gy + (i % 3) * 0.7, gz + Math.sin(a) * rr, 1, 0, 0.05, 0, 0.01);
            }
            world.sendParticles(ParticleTypes.END_ROD, gx, gy + 3 + RNG.nextDouble() * 10, gz, 2, 0.1, 1.5, 0.1, 0.01);
        }
        if (now % 60 == 0) world.playSound(null, gate, SoundEvents.PORTAL_AMBIENT, SoundSource.HOSTILE, 1.2f, 0.6f);
        // Entrée : un joueur au contact de la Porte est envoyé dans l'arène
        for (ServerPlayer p : new ArrayList<>(world.players())) {
            if (p.isSpectator()) continue;
            double dx = p.getX() - (gate.getX() + 0.5), dz = p.getZ() - (gate.getZ() + 0.5);
            if (dx * dx + dz * dz <= 2.5 * 2.5 && Math.abs(p.getY() - gate.getY()) <= 4) enter(p, now);
        }
        // Le boss surgit 3 s après la première arrivée
        if (bossId == null && bossAt > 0 && now >= bossAt) spawnBoss();
        if (bossId != null && now % 10 == 0) {
            Entity b = arena.getEntity(bossId);
            if (b instanceof LivingEntity lb && !lb.isAlive()) { victory(); return; }
            if (b == null) {
                if (++bossMissing >= 3) { victory(); return; } // zone forcée chargée : introuvable = mort et retiré
            } else bossMissing = 0;
            // Tous les joueurs tombés : le boss se régénère, nouvelle tentative possible
            if (arenaPlayers().isEmpty()) {
                emptyTicks += 10;
                if (emptyTicks >= 60) wipe();
            } else emptyTicks = 0;
        }
    }

    private static List<ServerPlayer> arenaPlayers() {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : arena.players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            if (p.blockPosition().distSqr(center) <= (double) (radius + 12) * (radius + 12)) out.add(p);
        }
        return out;
    }

    private static void enter(ServerPlayer p, long now) {
        p.getPersistentData().putString(RETURN_TAG, world.dimension().location() + "|" + home.getX() + "|" + home.getY() + "|" + home.getZ());
        double x = center.getX() + 0.5 + (RNG.nextDouble() - 0.5) * 3, z = center.getZ() + radius - 3.5;
        p.teleportTo(arena, x, center.getY(), z, 180f, 0f);
        p.fallDistance = 0;
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 4, false, false));
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(WSLang.t("raid.enter_sub"))));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(WSLang.t("raid.enter_title", WSLang.t(wave.bossName)))));
        arena.playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 0.6f);
        if (bossAt < 0) bossAt = now + 60;
    }

    private static void spawnBoss() {
        BlockPos at = center.offset(0, 0, -radius / 2);
        boolean ok = BossManager.spawnBoss(arena, wave, at, 2, arena.getServer());
        if (!ok) { // boss introuvable / mal configuré : on ne bloque pas la partie
            WaveSurvivorMod.LOGGER.error("[Raid] Impossible de faire appara\u00eetre le boss '{}' : victoire accord\u00e9e.", wave.bossName);
            victory();
            return;
        }
        for (LivingEntity e : arena.getEntitiesOfClass(LivingEntity.class, new AABB(center).inflate(radius + 6))) {
            if (BossManager.isBoss(e.getUUID())) { bossId = e.getUUID(); break; }
        }
        if (bossId == null) { victory(); return; }
        arena.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 2, 0.5, 0.5, 0.5, 0);
        arena.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 80, 3, 0.2, 3, 0.05);
        arena.playSound(null, at, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3f, 0.8f);
        for (ServerPlayer p : arena.players()) p.sendSystemMessage(Component.literal(WSLang.t("raid.boss_arrives", WSLang.t(wave.bossName))));
    }

    /** Tous les joueurs de l'arène sont tombés : le boss se régénère, ses sbires disparaissent, la Porte reste ouverte. */
    private static void wipe() {
        emptyTicks = 0;
        Entity b = arena.getEntity(bossId);
        if (b instanceof LivingEntity lb) {
            lb.setHealth(lb.getMaxHealth());
            if (lb instanceof Mob m) m.setTarget(null);
        }
        for (Mob m : arena.getEntitiesOfClass(Mob.class, new AABB(center).inflate(radius + 10))) {
            if (!m.getUUID().equals(bossId)) m.discard();
        }
        for (ServerPlayer p : arena.getServer().getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.literal(WSLang.t("raid.wiped", WSLang.t(wave.bossName))));
        }
    }

    /** Boss vaincu : retour au Monolithe / à l'autel, puis victoire (Kingdom ou horde classique). */
    private static void victory() {
        MinecraftServer server = arena.getServer();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.literal(WSLang.t("raid.victory", WSLang.t(wave.bossName))));
        }
        returnAll();
        close();
        Runnable win = onVictory;
        onVictory = null;
        if (win != null) win.run();
    }

    /** Action à la victoire (fournie à l'ouverture). */
    private static Runnable onVictory;

    /** Tick depuis la boucle de la horde classique (le Kingdom l'appelle lui-même). */
    public static void tickFromHorde(long now) { tick(now); }

    // ─── Fermeture / retour ───

    /** Fin de partie (victoire, arrêt, fermeture du serveur) : tout le monde rentre, l'arène est vidée. */
    public static void stop() {
        onVictory = null;
        if (!open) return;
        returnAll();
        if (arena != null) {
            for (Mob m : arena.getEntitiesOfClass(Mob.class, new AABB(center).inflate(radius + 10))) m.discard();
        }
        close();
    }

    private static void close() {
        open = false;
        bossId = null;
        bossAt = -1;
        releaseChunks();
    }

    private static void returnAll() {
        if (arena == null) return;
        for (ServerPlayer p : new ArrayList<>(arena.players())) {
            if (p.blockPosition().distSqr(center) <= (double) (radius + 30) * (radius + 30) || arena.dimension() == RAID_DIM) sendHome(p);
        }
    }

    /** Renvoie un joueur près du Monolithe (ou à l'endroit noté à son entrée dans l'arène). */
    private static void sendHome(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        ServerLevel dest = world != null ? world : server.overworld();
        BlockPos to = home;
        String tag = p.getPersistentData().getString(RETURN_TAG);
        if (!tag.isEmpty()) {
            String[] a = tag.split("\\|");
            try {
                ServerLevel l = server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(a[0])));
                if (l != null) dest = l;
                to = new BlockPos(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]));
            } catch (Exception ignored) {}
        }
        if (to == null) to = dest.getSharedSpawnPos();
        // Retour juste au-dessus de l'autel / du Monolithe. Le chunk est chargé AVANT de lire la hauteur du sol :
        // non chargé, la carte des hauteurs renvoie le fond du monde (téléportation dans le vide).
        int x = to.getX(), z = to.getZ();
        dest.getChunk(x >> 4, z >> 4);
        int y = dest.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= dest.getMinBuildHeight() + 1 || y < to.getY() + 1) y = to.getY() + 1;
        p.getPersistentData().remove(RETURN_TAG);
        p.teleportTo(dest, x + 0.5, y, z + 0.5, p.getYRot(), 0f);
        p.fallDistance = 0;
    }

    /**
     * Filet de sécurité : toute créature qui apparaît dans l'arène HORS des limites (au-delà du bord, sous le sol, au-dessus
     * de la barrière ou d'un pilier) est replacée au sol, à l'intérieur du cercle des piliers. Couvre tous les spawns :
     * totems protecteurs, sbires, invocations, compétences, autres mods.
     */
    @SubscribeEvent
    public void onJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (!open || arena == null || center == null || event.getLevel() != arena) return;
        Entity e = event.getEntity();
        if (!(e instanceof LivingEntity) || e instanceof net.minecraft.world.entity.player.Player) return;
        double dx = e.getX() - (center.getX() + 0.5), dz = e.getZ() - (center.getZ() + 0.5);
        double d = Math.sqrt(dx * dx + dz * dz);
        // Arène de secours dans le ciel du monde : seulement ce qui apparaît à proximité, jamais le reste du monde
        if (arena.dimension() != RAID_DIM && (d > radius + 64 || Math.abs(e.getY() - center.getY()) > 64)) return;
        boolean outside = d > radius - 2 || e.getY() < center.getY() - 2 || e.getY() > center.getY() + 4;
        if (!outside) return;
        double keep = Math.max(2, radius - 7); // à l'intérieur des piliers (placés au rayon - 4)
        double nx, nz;
        if (d < 0.01) {
            nx = center.getX() + 0.5;
            nz = center.getZ() + 0.5;
        } else {
            double f = Math.min(d, keep) / d;
            nx = center.getX() + 0.5 + dx * f;
            nz = center.getZ() + 0.5 + dz * f;
        }
        BlockPos g = com.wavesurvivor.horde.spawn.SpawnZone.groundNear(arena, (int) Math.floor(nx), (int) Math.floor(nz), center.getY());
        e.moveTo(nx, g.getY(), nz, e.getYRot(), e.getXRot());
        e.setDeltaMovement(Vec3.ZERO);
        e.fallDistance = 0;
    }

    /** Connexion : un joueur resté dans l'arène alors qu'aucun raid n'est ouvert est renvoyé chez lui. */
    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || open) return;
        if (p.level().dimension() == RAID_DIM || !p.getPersistentData().getString(RETURN_TAG).isEmpty()) {
            if (p.level().dimension() == RAID_DIM || p.blockPosition().getY() > 150) sendHome(p);
            else p.getPersistentData().remove(RETURN_TAG);
        }
    }

    /** Mort dans l'arène : le joueur réapparaît chez lui (lit / point d'apparition) ; son retour noté n'a plus lieu d'être. */
    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && p.level().dimension() != RAID_DIM) p.getPersistentData().remove(RETURN_TAG);
    }

    // ─── Zone chargée ───

    private static void forceChunks() {
        int cr = (radius + 8) >> 4;
        int cx = center.getX() >> 4, cz = center.getZ() >> 4;
        for (int dx = -cr - 1; dx <= cr + 1; dx++) for (int dz = -cr - 1; dz <= cr + 1; dz++) {
            long k = ChunkPos.asLong(cx + dx, cz + dz);
            if (!arena.getForcedChunks().contains(k) && arena.setChunkForced(cx + dx, cz + dz, true)) FORCED.add(k);
        }
    }

    private static void releaseChunks() {
        if (arena != null) for (long k : FORCED) arena.setChunkForced(ChunkPos.getX(k), ChunkPos.getZ(k), false);
        FORCED.clear();
    }

    // ─── Construction de l'arène ───

    private static void build(ServerLevel lv, BlockPos c, int r) {
        int R = r + 3;
        BlockState air = Blocks.AIR.defaultBlockState();
        // Zone vidée (restes d'un raid précédent)
        for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
            if (dx * dx + dz * dz > R * R) continue;
            for (int dy = -3; dy <= 12; dy++) {
                BlockPos p = c.offset(dx, dy, dz);
                if (!lv.getBlockState(p).isAir()) lv.setBlock(p, air, Block.UPDATE_CLIENTS);
            }
        }
        BlockState[] floor = {Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
                Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState(), Blocks.POLISHED_BLACKSTONE.defaultBlockState()};
        int[] weight = {40, 25, 20, 15};
        Random rng = new Random(c.asLong());
        for (int dx = -r - 1; dx <= r + 1; dx++) for (int dz = -r - 1; dz <= r + 1; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > r + 0.5) continue;
            BlockState s;
            if (d <= 2.5) s = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();          // cercle de runes au centre
            else if (d >= 6.5 && d < 7.5) s = Blocks.CRYING_OBSIDIAN.defaultBlockState();      // anneau d'obsidienne pleureuse
            else if (d > r - 1) s = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();    // bordure
            else if (Math.floorMod(dx, 6) == 0 && Math.floorMod(dz, 6) == 0 && d > 3) s = Blocks.SEA_LANTERN.defaultBlockState(); // lumières
            else {
                int roll = rng.nextInt(100), i = 0;
                while (i < weight.length - 1 && roll >= weight[i]) roll -= weight[i++];
                s = floor[i];
            }
            lv.setBlock(c.offset(dx, -1, dz), s, Block.UPDATE_CLIENTS);
            lv.setBlock(c.offset(dx, -2, dz), Blocks.DEEPSLATE.defaultBlockState(), Block.UPDATE_CLIENTS);
            // Muret de bordure + barrière invisible (personne ne tombe dans le vide)
            if (d > r - 1) {
                boolean crying = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(dz, dx))), 30) < 4;
                lv.setBlock(c.offset(dx, 0, dz), (crying ? Blocks.CRYING_OBSIDIAN : Blocks.POLISHED_BLACKSTONE_BRICK_WALL).defaultBlockState(), Block.UPDATE_CLIENTS);
                for (int dy = 1; dy <= 6; dy++) lv.setBlock(c.offset(dx, dy, dz), Blocks.BARRIER.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        // 8 piliers surmont\u00e9s de lanternes des \u00e2mes
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            int px = (int) Math.round(Math.cos(a) * (r - 4)), pz = (int) Math.round(Math.sin(a) * (r - 4));
            for (int dy = 0; dy < 5; dy++) lv.setBlock(c.offset(px, dy, pz), (dy == 4 ? Blocks.CHISELED_POLISHED_BLACKSTONE : Blocks.POLISHED_BLACKSTONE_BRICKS).defaultBlockState(), Block.UPDATE_CLIENTS);
            lv.setBlock(c.offset(px, 5, pz), Blocks.SOUL_LANTERN.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
