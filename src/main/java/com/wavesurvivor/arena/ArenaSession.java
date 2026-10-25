package com.wavesurvivor.arena;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.altar.AltarBlockEntity;
import com.wavesurvivor.horde.HordeManager;
import com.wavesurvivor.horde.roulette.RouletteChestConfig;
import com.wavesurvivor.horde.roulette.RouletteChestRegistry;
import com.wavesurvivor.horde.roulette.RouletteChestSpawner;
import com.wavesurvivor.horde.skill.DelayedActionScheduler;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PARTIE EN COURS DANS UNE ARÈNE (affiliation à une horde) : quand une horde est lancée depuis l'autel d'une arène,
 *   • les apparitions se font dans la crypte (repère « crypt_spawn »), sur les tombes (« grave_spawns », avec terre et son) et un peu aux
 *     entrées (« entrances ») au lieu d'un cercle au hasard ; le boss apparaît toujours dans la crypte ;
 *   • la grille du portail (barreaux de fer) s'ouvre à la première apparition dans la crypte et se referme à la fin de la partie ;
 *   • les braseros des âmes (« soul_braziers ») s'éteignent un par un au fil des vagues et se rallument à la fin ;
 *   • à la première victoire, un coffre roulette apparaît dans la crypte (une fois par arène).
 * Les réglages (poids d'apparition, grille, braseros, coffre) sont dans config/wavesurvivor/arenas.json (voir ArenaSettings).
 * Une seule partie à la fois : la session est démarrée par l'autel (AltarManager) et terminée par HordeManager.reset().
 */
public final class ArenaSession {

    private static ArenaSession cur = null;

    private final ServerLevel level;
    private final BlockPos altar;
    private final ArenaBlueprint bp;
    private final Map<String, List<BlockPos>> markers;
    private final List<BlockPos> gate = new ArrayList<>();
    private final int ox, oz, y0;
    private boolean gateOpen = false;
    private int braziersOff = 0;

    private ArenaSession(ServerLevel level, BlockPos altar, ArenaBlueprint bp, int ox, int oz, int y0, Rotation rot) {
        this.level = level;
        this.altar = altar;
        this.bp = bp;
        this.ox = ox;
        this.oz = oz;
        this.y0 = y0;
        this.markers = bp.worldMarkers(ox, oz, y0, rot);
        BlockPos g = marker("crypt_gate");
        if (g != null) for (BlockPos p : bp.cellsOf('I', ox, oz, y0, rot)) {
            if (Math.abs(p.getX() - g.getX()) <= 4 && Math.abs(p.getZ() - g.getZ()) <= 4) gate.add(p);
        }
    }

    private BlockPos marker(String name) {
        List<BlockPos> l = markers.get(name);
        return l == null || l.isEmpty() ? null : l.get(0);
    }

    // ─── Début et fin ───

    /** Démarre la session si l'autel est celui d'une arène (sinon : rien). La grille est refermée et les braseros rallumés. */
    public static void begin(ServerLevel level, BlockPos altarPos) {
        cur = null;
        if (!(level.getBlockEntity(altarPos) instanceof AltarBlockEntity ab) || ab.arenaId() == null) return;
        ArenaBlueprint bp = ArenaBlueprint.load(ab.arenaId());
        if (bp == null) return;
        ArenaSession s = new ArenaSession(level, altarPos, bp, ab.arenaOx(), ab.arenaOz(), ab.arenaY0(), Rotation.values()[Math.max(0, Math.min(3, ab.arenaRot()))]);
        s.closeGate();
        s.relight();
        cur = s;
        WaveSurvivorMod.LOGGER.info("[Arènes] Partie lancée dans « {} » (autel {}).", bp.id, altarPos.toShortString());
    }

    /** Fin de partie (victoire, défaite ou arrêt) : grille refermée, braseros rallumés. */
    public static void end() {
        ArenaSession s = cur;
        cur = null;
        if (s == null) return;
        s.closeGate();
        s.relight();
    }

    public static boolean active() { return cur != null; }

    // ─── Apparitions ───

    /** Position d'apparition d'un monstre de la horde dans l'arène (null = pas dans une arène, ou rien de valide : le jeu choisit lui-même). */
    public static BlockPos pick(ServerLevel level, BlockPos center, int radius, EntityType<?> type) {
        ArenaSession s = cur;
        if (s == null || !s.owns(level, center)) return null;
        return s.pickMob(type);
    }

    /** Position d'apparition du boss : toujours la crypte. */
    public static BlockPos pickBoss(ServerLevel level, BlockPos center, int radius, EntityType<?> type) {
        ArenaSession s = cur;
        if (s == null || !s.owns(level, center)) return null;
        BlockPos p = s.cryptSpot(type);
        if (p == null) return null;
        s.openGate();
        s.effect(p, 0);
        return p;
    }

    private boolean owns(ServerLevel l, BlockPos center) {
        return l == level && Math.abs(center.getX() - altar.getX()) <= 3 && Math.abs(center.getZ() - altar.getZ()) <= 3;
    }

    private BlockPos pickMob(EntityType<?> type) {
        RandomSource r = level.getRandom();
        int wc = ArenaSettings.cryptWeight(), wg = ArenaSettings.graveWeight(), we = ArenaSettings.entranceWeight();
        int total = wc + wg + we;
        if (total <= 0) return null;
        for (int attempt = 0; attempt < 6; attempt++) {
            int roll = r.nextInt(total);
            BlockPos p;
            int kind;
            if (roll < wc) { kind = 0; p = cryptSpot(type); }
            else if (roll < wc + wg) { kind = 1; p = fromList("grave_spawns", type); }
            else { kind = 2; p = fromList("entrances", type); }
            if (p == null) continue;
            if (kind == 0) openGate();
            effect(p, kind);
            return p;
        }
        return null;
    }

    private BlockPos cryptSpot(EntityType<?> type) {
        BlockPos base = marker("crypt_spawn");
        if (base == null) return null;
        RandomSource r = level.getRandom();
        for (int i = 0; i < 8; i++) {
            BlockPos c = i == 0 ? base : base.offset(r.nextInt(5) - 2, 0, r.nextInt(3) - 1);
            BlockPos ok = free(c, type);
            if (ok != null) return ok;
        }
        return null;
    }

    private BlockPos fromList(String name, EntityType<?> type) {
        List<BlockPos> l = markers.get(name);
        if (l == null || l.isEmpty()) return null;
        RandomSource r = level.getRandom();
        for (int i = 0; i < 4; i++) {
            BlockPos ok = free(l.get(r.nextInt(l.size())), type);
            if (ok != null) return ok;
        }
        return null;
    }

    /** Premier endroit libre (sol solide dessous, deux blocs libres, boîte du monstre libre) à ce repère ou juste au-dessus. */
    private BlockPos free(BlockPos p, EntityType<?> type) {
        for (int dy = 0; dy <= 2; dy++) {
            BlockPos q = p.above(dy);
            if (!level.getBlockState(q.below()).isSolid()) continue;
            if (!level.getBlockState(q).getCollisionShape(level, q).isEmpty() || !level.getBlockState(q.above()).getCollisionShape(level, q.above()).isEmpty()) continue;
            if (type != null && !level.noCollision(type.getAABB(q.getX() + 0.5, q.getY(), q.getZ() + 0.5))) continue;
            return q;
        }
        return null;
    }

    /** Effets d'apparition : 0 crypte (âmes), 1 tombe (terre qui jaillit, son de pelle et de mort-vivant), 2 entrée (rien). */
    private void effect(BlockPos p, int kind) {
        double x = p.getX() + 0.5, y = p.getY() + 0.2, z = p.getZ() + 0.5;
        if (kind == 1) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PODZOL.defaultBlockState()), x, y, z, 24, 0.3, 0.15, 0.3, 0.05);
            level.playSound(null, p, SoundEvents.SHOVEL_FLATTEN, SoundSource.HOSTILE, 1.0f, 0.7f);
            level.playSound(null, p, SoundEvents.ZOMBIE_AMBIENT, SoundSource.HOSTILE, 0.6f, 0.6f);
        } else if (kind == 0) {
            level.sendParticles(ParticleTypes.SOUL, x, y + 0.5, z, 10, 0.4, 0.5, 0.4, 0.02);
            level.playSound(null, p, SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 0.8f, 0.7f);
        }
    }

    // ─── Grille de la crypte ───

    private void openGate() {
        if (gateOpen || gate.isEmpty() || !ArenaSettings.gateOpens()) return;
        gateOpen = true;
        for (BlockPos p : gate) if (level.getBlockState(p).is(Blocks.IRON_BARS)) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        level.playSound(null, gate.get(0), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.4f, 0.6f);
    }

    private void closeGate() {
        gateOpen = false;
        boolean changed = false;
        for (BlockPos p : gate) {
            if (level.isLoaded(p) && level.getBlockState(p).isAir()) { level.setBlock(p, Blocks.IRON_BARS.defaultBlockState(), 3); changed = true; }
        }
        if (changed) level.playSound(null, gate.get(0), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.2f, 0.7f);
    }

    // ─── Braseros ───

    private void setBraziers(int off) {
        List<BlockPos> l = markers.getOrDefault("soul_braziers", List.of());
        for (int i = 0; i < l.size(); i++) {
            BlockPos p = l.get(i);
            BlockState st = level.getBlockState(p);
            if (!st.hasProperty(CampfireBlock.LIT)) continue;
            boolean lit = i >= off;
            if (st.getValue(CampfireBlock.LIT) != lit) {
                level.setBlock(p, st.setValue(CampfireBlock.LIT, lit), 3);
                if (!lit) level.playSound(null, p, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 0.8f);
            }
        }
        braziersOff = off;
    }

    private void relight() { setBraziers(0); }

    // ─── Suivi des vagues (braseros) ───

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        ArenaSession s = cur;
        if (s == null || e.phase != TickEvent.Phase.END || e.getServer().getTickCount() % 20 != 0) return;
        if (!ArenaSettings.braziersFade() || !HordeManager.get().isRunning()) return;
        int total = HordeManager.get().getTotalWavesCount(), n = s.markers.getOrDefault("soul_braziers", List.of()).size();
        if (total <= 0 || n == 0) return;
        int off = Math.min(n, HordeManager.get().getCurrentWave() * n / total);
        if (off != s.braziersOff) s.setBraziers(off);
    }

    // ─── Victoire : coffre de récompense (une fois par arène) ───

    /** À appeler à la victoire (avant la remise à zéro) : braseros rallumés et, la première fois, un coffre apparaît dans la crypte. */
    public static void onVictory(MinecraftServer server) {
        ArenaSession s = cur;
        if (s == null) return;
        s.relight();
        if (!ArenaSettings.rewardChest()) return;
        if (!(s.level.getBlockEntity(s.altar) instanceof AltarBlockEntity ab) || ab.arenaRewarded()) return;
        RouletteChestConfig cfg = RouletteChestRegistry.getByChestName(ArenaSettings.rewardChestName());
        BlockPos at = s.marker("crypt_spawn");
        if (cfg == null || at == null) {
            WaveSurvivorMod.LOGGER.warn("[Arènes] Coffre de récompense « {} » introuvable : aucun coffre posé.", ArenaSettings.rewardChestName());
            return;
        }
        ab.setArenaRewarded(true);
        final ServerLevel lvl = s.level;
        final BlockPos chestPos = s.free(at, null) != null ? s.free(at, null) : at;
        // Un peu après la fin de partie (le nettoyage des coffres de horde est passé)
        DelayedActionScheduler.schedule(server, 100, () -> {
            RouletteChestSpawner.placePermanent(lvl, chestPos, cfg);
            lvl.sendParticles(ParticleTypes.END_ROD, chestPos.getX() + 0.5, chestPos.getY() + 1.0, chestPos.getZ() + 0.5, 30, 0.4, 0.6, 0.4, 0.03);
            lvl.playSound(null, chestPos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 1.0f, 0.8f);
            for (ServerPlayer p : lvl.players()) {
                if (p.distanceToSqr(chestPos.getX(), chestPos.getY(), chestPos.getZ()) < 96 * 96) p.displayClientMessage(Component.literal(WSLang.t("arena.reward_chest")), false);
            }
        }, "arena reward chest");
    }
}
