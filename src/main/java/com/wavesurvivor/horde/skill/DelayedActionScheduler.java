package com.wavesurvivor.horde.skill;

import com.wavesurvivor.WaveSurvivorMod;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

/**
 * Scheduler générique : exécute un Runnable à un tick futur.
 * Utilisé par les skills qui nécessitent un délai (solar, supernova, shadow_trap, potion_rain, blood_pact...).
 *
 * Ticked depuis HordeTickHandler.onServerTick.
 *
 * Safe pour les re-schedules depuis un callback :
 *   - pendant tick(), les nouveaux schedule() sont mis dans PENDING_ADDS
 *   - après l'iteration, PENDING_ADDS est mergé dans ACTIONS
 * Évite ConcurrentModificationException si un callback re-schedule (ex: son mixte du pacte de sang).
 */
public class DelayedActionScheduler {

    private static final List<PendingAction> ACTIONS = new ArrayList<>();
    private static final List<PendingAction> PENDING_ADDS = new ArrayList<>();
    private static boolean ticking = false;

    private static class PendingAction {
        final long executeAtTick;
        final Runnable action;
        final String debugLabel;

        PendingAction(long tick, Runnable action, String label) {
            this.executeAtTick = tick;
            this.action = action;
            this.debugLabel = label;
        }
    }

    public static void schedule(MinecraftServer server, int delayTicks, Runnable action, String label) {
        long executeAt = server.getTickCount() + Math.max(1, delayTicks);
        PendingAction pa = new PendingAction(executeAt, action, label);
        if (ticking) {
            // Callback en cours d'exécution qui re-schedule : buffer pour éviter CME
            PENDING_ADDS.add(pa);
        } else {
            ACTIONS.add(pa);
        }
    }

    public static void tick(MinecraftServer server) {
        if (ACTIONS.isEmpty()) return;
        long now = server.getTickCount();
        ticking = true;
        try {
            var it = ACTIONS.iterator();
            while (it.hasNext()) {
                PendingAction pa = it.next();
                if (now < pa.executeAtTick) continue;
                try {
                    pa.action.run();
                } catch (Exception e) {
                    WaveSurvivorMod.LOGGER.error("[DelayedAction] Erreur exec '{}' : {}", pa.debugLabel, e.getMessage(), e);
                }
                it.remove();
            }
        } finally {
            ticking = false;
            // Flush les tasks ajoutées pendant l'iteration (re-schedules depuis callbacks)
            if (!PENDING_ADDS.isEmpty()) {
                ACTIONS.addAll(PENDING_ADDS);
                PENDING_ADDS.clear();
            }
        }
    }

    public static void clearAll() {
        int n = ACTIONS.size() + PENDING_ADDS.size();
        ACTIONS.clear();
        PENDING_ADDS.clear();
        if (n > 0) WaveSurvivorMod.LOGGER.debug("[DelayedAction] {} actions purgées", n);
    }

    public static int pendingCount() {
        return ACTIONS.size() + PENDING_ADDS.size();
    }
}
