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
 * les apparitions se font aux repères du plan au lieu d'un cercle au hasard. Les repères reconnus (un plan n'a besoin que de ceux qui lui servent) :
 *   • apparition principale : « crypt_spawn » (crypte) ou « portal_spawn » (portail) ;
 *   • apparitions secondaires : « grave_spawns » (tombes : terre qui jaillit) ou « rift_spawns » (failles : lave et fumée) ;
 *   • bord : « entrances » (entrées) ou, à défaut, un point du bord du cercle ;
 *   • boss : « boss_spawn » (sinon l'apparition principale) ; « lava_pools » : jamais d'apparition à moins de 3 blocs ;
 *   • décor vivant : « crypt_gate » (grille qui s'ouvre), « soul_braziers » (s'éteignent au fil des vagues), feux du plan (reprennent à chaque
 *     vague), magma (refroidit en partie à la victoire), portail illuminé pendant le combat du boss ;
 *   • récompense : un coffre roulette à la première victoire (une fois par arène), à « reward » (sinon la crypte, sinon le portail).
 * Les réglages sont dans config/wavesurvivor/arenas.json (voir ArenaSettings). Une seule partie à la fois : la session est démarrée par l'autel
 * (AltarManager) et terminée par HordeManager.reset().
 */
public final class ArenaSession {

    private static ArenaSession cur = null;

    private final ServerLevel level;
    private final BlockPos altar;
    private final ArenaBlueprint bp;
    private final Map<String, List<BlockPos>> markers;
    private final List<BlockPos> gate = new ArrayList<>();
    private final List<BlockPos> fires, magma;
    private final BlockPos center;
    private boolean gateOpen = false, bossLit = false;
    private int braziersOff = 0, lastWave = -1;

    private ArenaSession(ServerLevel level, BlockPos altar, ArenaBlueprint bp, int ox, int oz, int y0, Rotation rot) {
        this.level = level;
        this.altar = altar;
        this.bp = bp;
        this.markers = bp.worldMarkers(ox, oz, y0, rot);
        int[] d = bp.size(rot);
        this.center = new BlockPos(ox + d[0] / 2, y0, oz + d[1] / 2);
        BlockPos g = marker("crypt_gate");
        if (g != null) for (BlockPos p : bp.cellsOf('I', ox, oz, y0, rot)) {
            if (Math.abs(p.getX() - g.getX()) <= 4 && Math.abs(p.getZ() - g.getZ()) <= 4) gate.add(p);
        }
        this.fires = bp.cellsOfBlock(Blocks.FIRE, ox, oz, y0, rot);
        this.magma = bp.cellsOfBlock(Blocks.MAGMA_BLOCK, ox, oz, y0, rot);
    }

    private BlockPos marker(String name) {
        List<BlockPos> l = markers.get(name);
        return l == null || l.isEmpty() ? null : l.get(0);
    }

    private BlockPos firstMarker(String... names) {
        for (String n : names) {
            BlockPos p = marker(n);
            if (p != null) return p;
        }
        return null;
    }

    // ─── Début et fin ───

    /** Démarre la session si l'autel est celui d'une arène (sinon : rien). La grille est refermée, les braseros et les feux rallumés. */
    public static void begin(ServerLevel level, BlockPos altarPos) {
        cur = null;
        if (!(level.getBlockEntity(altarPos) instanceof AltarBlockEntity ab) || ab.arenaId() == null) return;
        ArenaBlueprint bp = ArenaBlueprint.load(ab.arenaId());
        if (bp == null) return;
        ArenaSession s = new ArenaSession(level, altarPos, bp, ab.arenaOx(), ab.arenaOz(), ab.arenaY0(), Rotation.values()[Math.max(0, Math.min(3, ab.arenaRot()))]);
        s.closeGate();
        s.relight();
        s.rekindle();
        cur = s;
        WaveSurvivorMod.LOGGER.info("[Arènes] Partie lancée dans « {} » (autel {}).", bp.id, altarPos.toShortString());
    }

    /** Fin de partie (victoire, défaite ou arrêt) : grille refermée, braseros rallumés, portail éteint. */
    public static void end() {
        ArenaSession s = cur;
        cur = null;
        if (s == null) return;
        s.bossLit = false;
        s.closeGate();
        s.relight();
        s.rekindle();
    }

    public static boolean active() { return cur != null; }

    // ─── Apparitions ───

    /** Position d'apparition d'un monstre de la horde dans l'arène (null = pas dans une arène, ou rien de valide : le jeu choisit lui-même). */
    public static BlockPos pick(ServerLevel level, BlockPos center, int radius, EntityType<?> type) {
        ArenaSession s = cur;
        if (s == null || !s.owns(level, center)) return null;
        return s.pickMob(type);
    }

    /** Position d'apparition du boss : « boss_spawn », sinon l'apparition principale (la crypte). */
    public static BlockPos pickBoss(ServerLevel level, BlockPos center, int radius, EntityType<?> type) {
        ArenaSession s = cur;
        if (s == null || !s.owns(level, center)) return null;
        BlockPos base = s.firstMarker("boss_spawn", "crypt_spawn", "portal_spawn");
        BlockPos p = base == null ? null : s.spotNear(base, type, 0, 0);
        if (p == null) return null;
        s.openGate();
        s.bossLit = true;
        s.effect(p, s.marker("boss_spawn") != null ? 3 : 0);
        if (s.marker("boss_spawn") != null) { // le portail s'embrase à l'arrivée du boss
            s.level.sendParticles(ParticleTypes.PORTAL, p.getX() + 0.5, p.getY() + 1.5, p.getZ() + 0.5, 240, 1.2, 1.8, 1.2, 0.6);
            s.level.playSound(null, p, SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 0.8f, 0.7f);
        }
        return p;
    }

    private boolean owns(ServerLevel l, BlockPos c) {
        return l == level && Math.abs(c.getX() - altar.getX()) <= 3 && Math.abs(c.getZ() - altar.getZ()) <= 3;
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
            if (roll < wc) {
                BlockPos base = firstMarker("crypt_spawn", "portal_spawn");
                kind = marker("crypt_spawn") != null ? 0 : 3;
                p = base == null ? null : spotNear(base, type, 2, 1);
            } else if (roll < wc + wg) {
                String list = markers.containsKey("grave_spawns") ? "grave_spawns" : "rift_spawns";
                kind = list.equals("grave_spawns") ? 1 : 4;
                p = fromList(list, type);
            } else {
                kind = 2;
                p = markers.containsKey("entrances") ? fromList("entrances", type) : edgeSpot(type);
            }
            if (p == null) continue;
            if (kind == 0 || kind == 3) openGate();
            effect(p, kind);
            return p;
        }
        return null;
    }

    /** Endroit libre près d'un repère (décalage aléatoire jusqu'à ±dx, ±dz). */
    private BlockPos spotNear(BlockPos base, EntityType<?> type, int dx, int dz) {
        RandomSource r = level.getRandom();
        for (int i = 0; i < 8; i++) {
            BlockPos c = i == 0 || (dx == 0 && dz == 0) ? base : base.offset(r.nextInt(2 * dx + 1) - dx, 0, r.nextInt(2 * dz + 1) - dz);
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

    /** Point du bord du cercle de l'arène (pas d'« entrances » dans le plan). */
    private BlockPos edgeSpot(EntityType<?> type) {
        RandomSource r = level.getRandom();
        double rad = bp.radius() - 2.5;
        for (int i = 0; i < 8; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            BlockPos c = new BlockPos(center.getX() + (int) Math.round(Math.cos(a) * rad), center.getY() + 1, center.getZ() + (int) Math.round(Math.sin(a) * rad));
            BlockPos ok = free(c, type);
            if (ok != null) return ok;
        }
        return null;
    }

    /** Premier endroit libre (sol solide dessous, deux blocs libres, boîte du monstre libre, loin des mares de lave) à ce repère ou à côté en hauteur. */
    private BlockPos free(BlockPos p, EntityType<?> type) {
        for (int pool = 0; pool < markers.getOrDefault("lava_pools", List.of()).size(); pool++) {
            BlockPos lp = markers.get("lava_pools").get(pool);
            if (Math.abs(lp.getX() - p.getX()) <= 3 && Math.abs(lp.getZ() - p.getZ()) <= 3) return null;
        }
        for (int dy : new int[]{0, -1, -2, 1, 2}) {
            BlockPos q = p.above(dy);
            if (!level.getBlockState(q.below()).isSolid()) continue;
            if (!level.getBlockState(q).getCollisionShape(level, q).isEmpty() || !level.getBlockState(q.above()).getCollisionShape(level, q.above()).isEmpty()) continue;
            if (!level.getFluidState(q).isEmpty()) continue;
            if (type != null && !level.noCollision(type.getAABB(q.getX() + 0.5, q.getY(), q.getZ() + 0.5))) continue;
            return q;
        }
        return null;
    }

    /** Effets d'apparition : 0 crypte (âmes), 1 tombe (terre, pelle, mort-vivant), 2 bord (rien), 3 portail (particules violettes), 4 faille (lave, fumée). */
    private void effect(BlockPos p, int kind) {
        double x = p.getX() + 0.5, y = p.getY() + 0.2, z = p.getZ() + 0.5;
        switch (kind) {
            case 1 -> {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PODZOL.defaultBlockState()), x, y, z, 24, 0.3, 0.15, 0.3, 0.05);
                level.playSound(null, p, SoundEvents.SHOVEL_FLATTEN, SoundSource.HOSTILE, 1.0f, 0.7f);
                level.playSound(null, p, SoundEvents.ZOMBIE_AMBIENT, SoundSource.HOSTILE, 0.6f, 0.6f);
            }
            case 0 -> {
                level.sendParticles(ParticleTypes.SOUL, x, y + 0.5, z, 10, 0.4, 0.5, 0.4, 0.02);
                level.playSound(null, p, SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 0.8f, 0.7f);
            }
            case 3 -> {
                level.sendParticles(ParticleTypes.PORTAL, x, y + 1.0, z, 40, 0.6, 1.0, 0.6, 0.5);
                level.playSound(null, p, SoundEvents.PORTAL_TRIGGER, SoundSource.HOSTILE, 0.35f, 1.4f);
            }
            case 4 -> {
                level.sendParticles(ParticleTypes.LAVA, x, y + 0.3, z, 14, 0.3, 0.2, 0.3, 0.1);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.5, z, 8, 0.3, 0.4, 0.3, 0.03);
                level.playSound(null, p, SoundEvents.LAVA_EXTINGUISH, SoundSource.HOSTILE, 0.8f, 0.6f);
            }
            default -> { }
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

    // ─── Braseros et feux ───

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

    /** Les feux du plan reprennent là où ils se sont éteints. */
    private void rekindle() {
        if (!ArenaSettings.firesRekindle()) return;
        for (BlockPos p : fires) {
            if (!level.isLoaded(p) || !level.getBlockState(p).isAir()) continue;
            BlockState fire = Blocks.FIRE.defaultBlockState();
            if (fire.canSurvive(level, p)) level.setBlock(p, fire, 3);
        }
    }

    // ─── Suivi des vagues (braseros, feux) et portail du boss ───

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        ArenaSession s = cur;
        if (s == null || e.phase != TickEvent.Phase.END || e.getServer().getTickCount() % 20 != 0) return;
        if (s.bossLit) { // portail illuminé tant que le boss est là
            BlockPos b = s.marker("boss_spawn");
            if (b != null) {
                s.level.sendParticles(ParticleTypes.PORTAL, b.getX() + 0.5, b.getY() + 1.2, b.getZ() + 0.5, 50, 1.0, 1.6, 1.0, 0.5);
                if (s.level.getGameTime() % 100 == 0) s.level.playSound(null, b, SoundEvents.PORTAL_AMBIENT, SoundSource.AMBIENT, 0.5f, 0.8f);
            }
        }
        if (!HordeManager.get().isRunning()) return;
        int wave = HordeManager.get().getCurrentWave();
        if (wave != s.lastWave) { s.lastWave = wave; s.rekindle(); }
        if (!ArenaSettings.braziersFade()) return;
        int total = HordeManager.get().getTotalWavesCount(), n = s.markers.getOrDefault("soul_braziers", List.of()).size();
        if (total <= 0 || n == 0) return;
        int off = Math.min(n, wave * n / total);
        if (off != s.braziersOff) s.setBraziers(off);
    }

    // ─── Victoire : magma qui refroidit, coffre de récompense (une fois par arène) ───

    /** À appeler à la victoire (avant la remise à zéro) : braseros rallumés, magma en partie refroidi et, la première fois, un coffre apparaît. */
    public static void onVictory(MinecraftServer server) {
        ArenaSession s = cur;
        if (s == null) return;
        s.bossLit = false;
        s.relight();
        if (ArenaSettings.magmaCools()) {
            RandomSource r = s.level.getRandom();
            for (BlockPos p : s.magma) if (s.level.isLoaded(p) && s.level.getBlockState(p).is(Blocks.MAGMA_BLOCK) && r.nextInt(100) < 45) s.level.setBlock(p, Blocks.BLACKSTONE.defaultBlockState(), 3);
        }
        if (!ArenaSettings.rewardChest()) return;
        if (!(s.level.getBlockEntity(s.altar) instanceof AltarBlockEntity ab) || ab.arenaRewarded()) return;
        String chestName = ArenaSettings.rewardChestName(s.bp.id);
        RouletteChestConfig cfg = RouletteChestRegistry.getByChestName(chestName);
        BlockPos at = s.firstMarker("reward", "crypt_spawn", "portal_spawn", "boss_spawn");
        if (cfg == null || at == null) {
            WaveSurvivorMod.LOGGER.warn("[Arènes] Coffre de récompense « {} » introuvable : aucun coffre posé.", chestName);
            return;
        }
        ab.setArenaRewarded(true);
        final ServerLevel lvl = s.level;
        final BlockPos spot = s.free(at, null);
        final BlockPos chestPos = spot != null ? spot : at;
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
