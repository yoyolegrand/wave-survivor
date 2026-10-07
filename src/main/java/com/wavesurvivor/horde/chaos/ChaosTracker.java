package com.wavesurvivor.horde.chaos;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracker des entités éphémères (marchands du chaos) à despawn après un délai.
 * Ticked chaque seconde par HordeTickHandler.
 */
public class ChaosTracker {

    private static class Pending {
        final int entityId;
        final long despawnTick;
        Pending(int entityId, long despawnTick) {
            this.entityId = entityId;
            this.despawnTick = despawnTick;
        }
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    public static void schedule(int entityId, long despawnTick) {
        PENDING.add(new Pending(entityId, despawnTick));
    }

    public static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        long now = server.getTickCount();
        var it = PENDING.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (now < p.despawnTick) continue;

            // Chercher l'entité dans tous les levels
            for (ServerLevel lvl : server.getAllLevels()) {
                Entity e = lvl.getEntity(p.entityId);
                if (e != null && e.isAlive()) {
                    // Effet visuel + sonore au despawn (poof + villager.no comme le JS d'origine)
                    lvl.sendParticles(ParticleTypes.POOF,
                            e.getX(), e.getY() + 1.0, e.getZ(),
                            20, 0.5, 0.5, 0.5, 0.05);
                    lvl.playSound(null, e.blockPosition(),
                            SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0f, 1.0f);
                    e.discard();
                    break;
                }
            }
            it.remove();
        }
    }

    public static void clearAll(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        for (Pending p : PENDING) {
            for (ServerLevel lvl : server.getAllLevels()) {
                Entity e = lvl.getEntity(p.entityId);
                if (e != null && e.isAlive()) {
                    e.discard();
                    break;
                }
            }
        }
        PENDING.clear();
    }
}
