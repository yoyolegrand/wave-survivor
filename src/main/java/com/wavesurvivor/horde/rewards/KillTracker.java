package com.wavesurvivor.horde.rewards;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.horde.spawn.MobRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Compte les kills par joueur pendant une horde active.
 * Séparément : compte aussi qui a killé chaque boss.
 * Reset au start d'une nouvelle horde.
 */
public class KillTracker {

    private static final Map<UUID, Integer> KILLS = new HashMap<>();
    /** UUID du dernier joueur ayant killé un boss (peut y en avoir plusieurs → set). */
    private static final java.util.Set<UUID> BOSS_KILLERS = new java.util.HashSet<>();

    private static boolean active = false;

    public static void startTracking() {
        KILLS.clear();
        BOSS_KILLERS.clear();
        active = true;
        WaveSurvivorMod.LOGGER.info("[Kills] Tracking démarré.");
    }

    /** Reprise d'une partie sauvegardée : compteurs de kills restaurés. */
    public static void restore(Map<UUID, Integer> kills) {
        if (kills != null) KILLS.putAll(kills);
    }

    public static void stopTracking() {
        active = false;
    }

    public static boolean isActive() {
        return active;
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!active) return;
        LivingEntity dead = event.getEntity();

        // Est-ce un mob de la horde ? (registered dans MobRegistry ou boss)
        boolean isHordeMob = MobRegistry.get(dead.getUUID()) != null
                || com.wavesurvivor.horde.boss.BossManager.isBoss(dead.getUUID());
        if (!isHordeMob) return;

        // Qui a fait le dernier hit ?
        DamageSource src = event.getSource();
        if (src == null) return;
        Object killer = src.getEntity();
        if (!(killer instanceof ServerPlayer p)) return;

        KILLS.merge(p.getUUID(), 1, Integer::sum);

        // Bonus : bossKiller si le mort était un boss
        if (com.wavesurvivor.horde.boss.BossManager.isBoss(dead.getUUID())) {
            BOSS_KILLERS.add(p.getUUID());
        }
    }

    public static Map<UUID, Integer> snapshotKills() {
        return new HashMap<>(KILLS);
    }

    public static java.util.Set<UUID> snapshotBossKillers() {
        return new java.util.HashSet<>(BOSS_KILLERS);
    }
}
