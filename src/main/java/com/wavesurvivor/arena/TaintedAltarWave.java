package com.wavesurvivor.arena;

import com.wavesurvivor.config.RulesConfig;
import com.wavesurvivor.craft.CraftUtilityBlockEntity;
import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * AUTEL SOUILLÉ (ruine générée « wavesurvivor:autel_souille ») : quand un joueur entre dans le rayon de déclenchement (16 blocs) autour de l'autel,
 * le RITUEL commence : les obélisques s'illuminent (lumière + flammes roses), un son grave retentit, les totems d'appât de l'estrade s'activent et
 * une mini-vague de Corrompus (Rootlings, parfois un Stone Brute) surgit des repères « corrupted_spawns ». Une fois la vague vaincue, les obélisques
 * s'éteignent et (réglable : {@code altarOpenOnVictory}) l'estrade s'ouvre sur le coffre. Une seule fois par ruine (mémorisé avec le monde).
 * Comme la Chapelle, seules les ruines générées naturellement sont gérées (celles posées à la commande restent calmes).
 * Aucun état en mémoire : la vague est retrouvée grâce à l'étiquette des monstres, donc le rituel survit à un redémarrage.
 */
public class TaintedAltarWave {

    public static final String ARENA_ID = "autel_souille";
    /** Étiquette des monstres du rituel. */
    private static final String TAG = "ws_altar_ritual";
    private static final double RADIUS = 16;
    private static final long KEY_START = 0x7E57A17A5L, KEY_OPEN = 0x0BE17E55L;
    private static final DustParticleOptions PINK = new DustParticleOptions(new org.joml.Vector3f(1.0f, 0.3f, 0.78f), 1.3f);

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.isSpectator()) return;
        ServerLevel level = p.serverLevel();
        if (level.getGameTime() % 20 != 0 || level.getDifficulty() == Difficulty.PEACEFUL) return;
        ChapelGuardians.Found f = ChapelGuardians.locate(level, p.blockPosition(), ARENA_ID);
        if (f == null) return;
        ArenaPiece piece = f.piece();
        Rotation rot = piece.rotation();
        Map<String, List<BlockPos>> markers = f.bp().worldMarkers(piece.ox(), piece.oz(), piece.y0(), rot);
        List<BlockPos> trig = markers.getOrDefault("trigger", List.of());
        if (trig.isEmpty()) return;
        BlockPos c = trig.get(0);
        long key = f.start().getChunkPos().toLong();
        ChapelGuardians.Data data = ChapelGuardians.Data.get(level);
        if (data.has(key ^ KEY_OPEN)) return; // rituel terminé
        boolean near = Math.abs(p.getY() - c.getY()) <= 8 && p.distanceToSqr(c.getX() + 0.5, p.getY(), c.getZ() + 0.5) <= RADIUS * RADIUS;
        if (!data.has(key ^ KEY_START)) {
            if (!near || !data.claim(key ^ KEY_START)) return;
            awaken(level, p, markers, f, piece, rot);
            return;
        }
        // Rituel en cours : flammes sur les obélisques, puis fin quand plus aucun monstre du rituel n'est en vie
        for (BlockPos o : markers.getOrDefault("obelisks", List.of())) {
            level.sendParticles(PINK, o.getX() + 0.5, o.getY() + 2.4, o.getZ() + 0.5, 8, 0.2, 0.5, 0.2, 0.02);
            level.sendParticles(ParticleTypes.END_ROD, o.getX() + 0.5, o.getY() + 2.6, o.getZ() + 0.5, 1, 0.1, 0.2, 0.1, 0.02);
        }
        if (!near) return; // un joueur près de l'autel est nécessaire : les monstres lointains pourraient être dans des zones non chargées
        boolean alive = !level.getEntitiesOfClass(Mob.class, new AABB(c).inflate(60), m -> m.isAlive() && m.getTags().contains(TAG)).isEmpty();
        if (!alive && data.claim(key ^ KEY_OPEN)) victory(level, p, markers);
    }

    private static void awaken(ServerLevel level, ServerPlayer p, Map<String, List<BlockPos>> markers, ChapelGuardians.Found f, ArenaPiece piece, Rotation rot) {
        List<BlockPos> spawns = new ArrayList<>(markers.getOrDefault("corrupted_spawns", List.of()));
        if (spawns.isEmpty()) return;
        Collections.shuffle(spawns, new java.util.Random(level.random.nextLong()));
        // difficulté : Facile ×0,75, Normal ×1, Difficile ×1,5 ; un Stone Brute se joint à la vague parfois
        Difficulty d = level.getDifficulty();
        int count = Math.max(1, Math.round(RulesConfig.altarWaveSize() * (d == Difficulty.EASY ? 0.75f : d == Difficulty.HARD ? 1.5f : 1f)));
        boolean brute = level.random.nextDouble() < (d == Difficulty.EASY ? 0.2 : d == Difficulty.HARD ? 0.55 : 0.35);
        for (int i = 0; i < count; i++) {
            BlockPos at = spawns.get(i % spawns.size());
            EntityType<? extends Mob> type = brute && i == 0 ? ModEntities.STONE_BRUTE.get() : ModEntities.ROOTLING.get();
            Mob m = type.create(level);
            if (m == null) continue;
            m.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
            m.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null, null);
            m.setPersistenceRequired();
            m.addTag(TAG);
            m.setTarget(p);
            level.addFreshEntity(m);
            level.sendParticles(ParticleTypes.SOUL, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 8, 0.3, 0.5, 0.3, 0.02);
        }
        // les obélisques s'illuminent : un bloc de lumière juste au-dessus de chacun
        for (BlockPos o : markers.getOrDefault("obelisks", List.of())) {
            BlockPos top = o.above(2);
            if (level.getBlockState(top).isAir()) level.setBlock(top, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 12), 3);
            level.sendParticles(PINK, o.getX() + 0.5, o.getY() + 1.5, o.getZ() + 0.5, 30, 0.3, 0.8, 0.3, 0.05);
        }
        // les totems d'appât de l'estrade attirent les Corrompus vers l'autel pendant 40 s
        for (BlockPos t : f.bp().cellsOf('A', piece.ox(), piece.oz(), piece.y0(), rot))
            if (level.getBlockEntity(t) instanceof CraftUtilityBlockEntity be) be.activateBait(level.getGameTime() + 800);
        level.playSound(null, p.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 1.5f, 0.6f);
        level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2f, 0.7f);
        p.displayClientMessage(Component.literal(WSLang.t("ruin.altar.wave")), true);
    }

    private static void victory(ServerLevel level, ServerPlayer p, Map<String, List<BlockPos>> markers) {
        for (BlockPos o : markers.getOrDefault("obelisks", List.of())) {
            BlockPos top = o.above(2);
            if (level.getBlockState(top).is(Blocks.LIGHT)) level.setBlock(top, Blocks.AIR.defaultBlockState(), 3);
            level.sendParticles(ParticleTypes.SMOKE, o.getX() + 0.5, o.getY() + 2.2, o.getZ() + 0.5, 12, 0.2, 0.4, 0.2, 0.02);
        }
        boolean open = RulesConfig.altarOpenOnVictory();
        List<BlockPos> chest = markers.getOrDefault("chest", List.of());
        if (open && !chest.isEmpty()) { // le bloc de l'estrade juste au-dessus du coffre disparaît
            BlockPos cover = chest.get(0).above();
            if (!level.getBlockState(cover).isAir()) {
                level.destroyBlock(cover, false);
                level.sendParticles(ParticleTypes.SOUL, cover.getX() + 0.5, cover.getY() + 0.5, cover.getZ() + 0.5, 25, 0.3, 0.4, 0.3, 0.05);
            }
        }
        level.playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.5f, 0.7f);
        p.displayClientMessage(Component.literal(WSLang.t(open ? "ruin.altar.done_open" : "ruin.altar.done")), true);
    }
}
