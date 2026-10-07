package com.wavesurvivor.horde.kingdom;

import com.wavesurvivor.i18n.WSLang;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * RESTES DU MODE KINGDOM : défenses, blocs de collision invisibles, portes et pièges posés pendant une partie.
 *  - Registre SAUVEGARDÉ avec le monde : si le serveur s'arrête en pleine partie (déconnexion en solo…), le
 *    nettoyage de fin de partie n'a jamais lieu → au démarrage suivant, tout ce qui reste est retiré.
 *  - /ws kclean scan [rayon] : montre les restes orphelins (particules 10 s) ; /ws kclean [rayon] : les supprime.
 *    Pendant une partie en cours, les constructions actives ne sont jamais touchées.
 */
public final class KingdomLeftovers {

    private KingdomLeftovers() {}

    // ─── Registre sauvegardé ───

    public static class Data extends SavedData {
        private static final String NAME = "wavesurvivor_kingdom_blocks";
        final LongOpenHashSet set = new LongOpenHashSet();

        static Data get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(Data::load, Data::new, NAME);
        }

        private static Data load(CompoundTag t) {
            Data d = new Data();
            for (long l : t.getLongArray("p")) d.set.add(l);
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag t) {
            t.putLongArray("p", set.toLongArray());
            return t;
        }
    }

    /** Retient une position posée pendant une partie Kingdom. */
    static void track(ServerLevel level, BlockPos pos) {
        if (level == null) return;
        Data d = Data.get(level);
        if (d.set.add(pos.asLong())) d.setDirty();
    }

    static void track(ServerLevel level, List<BlockPos> positions) {
        if (level == null) return;
        Data d = Data.get(level);
        boolean changed = false;
        for (BlockPos p : positions) changed |= d.set.add(p.asLong());
        if (changed) d.setDirty();
    }

    /** Fin de partie normale : tout a été retiré, le registre est vidé. */
    static void forgetAll(ServerLevel level) {
        if (level == null) return;
        Data d = Data.get(level);
        if (!d.set.isEmpty()) {
            d.set.clear();
            d.setDirty();
        }
    }

    // ─── Reconnaissance des blocs ───

    static boolean isKingdomBlock(BlockState s) {
        Block b = s.getBlock();
        return b instanceof ColliderBlock || b instanceof DefenseBlock || b instanceof GatePartBlock || b instanceof TrapBlock;
    }

    /** Vrai si ce bloc Kingdom n'appartient à aucune construction active. */
    static boolean isOrphan(BlockPos pos, BlockState s) {
        if (!KingdomManager.isActive()) return true;
        Block b = s.getBlock();
        if (b instanceof ColliderBlock) return KingdomDefenses.parentOf(pos) == null;
        if (b instanceof DefenseBlock) return !KingdomDefenses.isDefense(pos);
        if (b instanceof GatePartBlock) return !KingdomDefenses.isGatePart(pos);
        if (b instanceof TrapBlock) return !KingdomDefenses.isTrap(pos);
        return false;
    }

    /** Retire une liste de blocs : socles de défense d'abord (ils emportent leurs blocs invisibles), puis le reste. */
    private static int removeAll(ServerLevel level, List<BlockPos> list) {
        int n = 0;
        for (int pass = 0; pass < 2; pass++) {
            for (BlockPos p : list) {
                BlockState s = level.getBlockState(p);
                if (!isKingdomBlock(s)) continue;
                boolean defense = s.getBlock() instanceof DefenseBlock;
                if ((pass == 0) != defense) continue;
                level.removeBlock(p, false);
                n++;
            }
        }
        return n;
    }

    // ─── Démarrage du serveur : nettoyage automatique ───

    static void cleanupSaved(ServerLevel level) {
        Data d = Data.get(level);
        if (d.set.isEmpty() || KingdomManager.isActive()) return;
        List<BlockPos> list = new ArrayList<>();
        for (long l : d.set) list.add(BlockPos.of(l));
        int n = removeAll(level, list);
        d.set.clear();
        d.setDirty();
        if (n > 0) com.wavesurvivor.WaveSurvivorMod.LOGGER.info("[Kingdom] {} bloc(s) restant(s) d'une partie interrompue retiré(s) dans {}",
                n, level.dimension().location());
    }

    // ─── /ws kclean ───

    /** Cherche les restes orphelins dans les chunks chargés autour du joueur ; les montre ou les supprime. */
    public static int scan(ServerPlayer p, int radius, boolean remove) {
        ServerLevel level = p.serverLevel();
        radius = Math.max(8, Math.min(256, radius));
        BlockPos c = p.blockPosition();
        long r2 = (long) radius * radius;
        List<BlockPos> found = new ArrayList<>();
        int minCx = (c.getX() - radius) >> 4, maxCx = (c.getX() + radius) >> 4;
        int minCz = (c.getZ() - radius) >> 4, maxCz = (c.getZ() + radius) >> 4;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int cx = minCx; cx <= maxCx; cx++) for (int cz = minCz; cz <= maxCz; cz++) {
            LevelChunk ch = level.getChunkSource().getChunkNow(cx, cz);
            if (ch == null) continue;
            LevelChunkSection[] sections = ch.getSections();
            for (int i = 0; i < sections.length; i++) {
                LevelChunkSection sec = sections[i];
                if (sec == null || sec.hasOnlyAir() || !sec.maybeHas(KingdomLeftovers::isKingdomBlock)) continue;
                int baseY = level.getSectionYFromSectionIndex(i) << 4;
                for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                    int wx = (cx << 4) + x, wz = (cz << 4) + z;
                    long dx = wx - c.getX(), dz = wz - c.getZ();
                    if (dx * dx + dz * dz > r2) continue;
                    for (int y = 0; y < 16; y++) {
                        BlockState s = sec.getBlockState(x, y, z);
                        if (!isKingdomBlock(s)) continue;
                        m.set(wx, baseY + y, wz);
                        if (isOrphan(m, s)) found.add(m.immutable());
                    }
                }
            }
        }
        if (found.isEmpty()) {
            p.sendSystemMessage(WSLang.c("kclean.none", radius));
            return 0;
        }
        if (remove) {
            int n = removeAll(level, found);
            p.sendSystemMessage(WSLang.c("kclean.removed", n, radius));
            return n;
        }
        HIGHLIGHTS.removeIf(h -> h.player.equals(p.getUUID()));
        HIGHLIGHTS.add(new Highlight(p.getUUID(), found.size() > MAX_SHOWN ? found.subList(0, MAX_SHOWN) : found,
                level.getGameTime() + 200));
        p.sendSystemMessage(WSLang.c("kclean.found", found.size(), radius));
        return found.size();
    }

    // ─── Particules de repérage (10 s) ───

    private static final int MAX_SHOWN = 3000;

    private record Highlight(UUID player, List<BlockPos> positions, long until) {}

    private static final List<Highlight> HIGHLIGHTS = new ArrayList<>();

    private static void tickHighlights(net.minecraft.server.MinecraftServer server) {
        if (HIGHLIGHTS.isEmpty() || server.getTickCount() % 10 != 0) return;
        for (Highlight h : new ArrayList<>(HIGHLIGHTS)) {
            ServerPlayer p = server.getPlayerList().getPlayer(h.player);
            if (p == null || p.serverLevel().getGameTime() > h.until) {
                HIGHLIGHTS.remove(h);
                continue;
            }
            ServerLevel l = p.serverLevel();
            for (BlockPos q : h.positions) {
                l.sendParticles(p, ParticleTypes.END_ROD, true, q.getX() + 0.5, q.getY() + 0.5, q.getZ() + 0.5, 1, 0.15, 0.15, 0.15, 0.0);
            }
        }
    }

    // ─── Événements (noms de méthodes uniques : piège de l'event bus Forge) ───

    public static class Events {
        @SubscribeEvent
        public void kleftoversServerStarted(ServerStartedEvent e) {
            for (ServerLevel l : e.getServer().getAllLevels()) {
                try {
                    cleanupSaved(l);
                } catch (Exception ex) {
                    com.wavesurvivor.WaveSurvivorMod.LOGGER.warn("[Kingdom] Nettoyage des restes impossible dans {} : {}",
                            l.dimension().location(), ex.getMessage());
                }
            }
        }

        @SubscribeEvent
        public void kleftoversServerTick(TickEvent.ServerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || HIGHLIGHTS.isEmpty()) return;
            var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) tickHighlights(server);
        }
    }
}
