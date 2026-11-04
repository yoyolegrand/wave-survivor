package com.wavesurvivor.arena;

import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.registry.ModEntities;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AUTEL SOUILLÉ (ruine générée « wavesurvivor:autel_souille ») : quand un joueur s'approche du repère « trigger » (l'autel), une mini-vague de
 * Corrompus surgit des repères « corrupted_spawns » : des Rootlings, et parfois un Stone Brute. Une seule fois par ruine (mémorisé avec le monde).
 * Comme la Chapelle, seules les ruines générées naturellement sont gérées (celles posées à la commande restent calmes).
 */
public class TaintedAltarWave {

    public static final String ARENA_ID = "autel_souille";
    /** Distance de déclenchement (blocs) autour du repère « trigger », et chance qu'un Stone Brute se joigne à la vague. */
    private static final double RADIUS = 10, BRUTE_CHANCE = 0.35;

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.isSpectator()) return;
        ServerLevel level = p.serverLevel();
        if (level.getGameTime() % 20 != 0 || level.getDifficulty() == Difficulty.PEACEFUL) return;
        ChapelGuardians.Found f = ChapelGuardians.locate(level, p.blockPosition(), ARENA_ID);
        if (f == null) return;
        ArenaPiece piece = f.piece();
        Rotation rot = piece.rotation();
        var markers = f.bp().worldMarkers(piece.ox(), piece.oz(), piece.y0(), rot);
        List<BlockPos> trig = markers.getOrDefault("trigger", List.of());
        if (trig.isEmpty()) return;
        BlockPos c = trig.get(0);
        if (Math.abs(p.getY() - c.getY()) > 8 || p.distanceToSqr(c.getX() + 0.5, p.getY(), c.getZ() + 0.5) > RADIUS * RADIUS) return;
        if (!ChapelGuardians.Data.get(level).claim(f.start().getChunkPos().toLong() ^ 0x7E57A17A5L)) return;
        awaken(level, p, markers.getOrDefault("corrupted_spawns", List.of()));
    }

    private static void awaken(ServerLevel level, ServerPlayer p, List<BlockPos> all) {
        List<BlockPos> spawns = new ArrayList<>(all);
        Collections.shuffle(spawns, new java.util.Random(level.random.nextLong()));
        boolean brute = level.random.nextDouble() < BRUTE_CHANCE;
        for (int i = 0; i < spawns.size(); i++) {
            BlockPos at = spawns.get(i);
            EntityType<? extends Mob> type = brute && i == 0 ? ModEntities.STONE_BRUTE.get() : ModEntities.ROOTLING.get();
            Mob m = type.create(level);
            if (m == null) continue;
            m.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
            m.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null, null);
            m.setPersistenceRequired();
            m.setTarget(p);
            level.addFreshEntity(m);
            level.sendParticles(ParticleTypes.SOUL, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 8, 0.3, 0.5, 0.3, 0.02);
        }
        level.playSound(null, p.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 1.5f, 0.6f);
        p.displayClientMessage(Component.literal(WSLang.t("ruin.altar.wave")), true);
    }
}
