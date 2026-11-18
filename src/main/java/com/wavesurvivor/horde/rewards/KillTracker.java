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
        if (!(creditedKiller(dead, src) instanceof ServerPlayer p)) return;

        KILLS.merge(p.getUUID(), 1, Integer::sum);
        com.wavesurvivor.horde.stats.PlayerStats.add(p, "monsters_killed", 1); // 1.7 : statistiques

        // Bonus : bossKiller si le mort était un boss
        if (com.wavesurvivor.horde.boss.BossManager.isBoss(dead.getUUID())) {
            BOSS_KILLERS.add(p.getUUID());
            com.wavesurvivor.horde.stats.PlayerStats.add(p, "bosses_killed", 1);
        }
    }

    /**
     * 2.1 — auteur d'une élimination : le joueur qui a porté le coup, le propriétaire de l'invocation / de l'animal,
     * sinon le dernier joueur à avoir blessé le monstre (feu, chute, sorts à dégâts indirects…).
     */
    public static net.minecraft.world.entity.Entity creditedKiller(LivingEntity dead, DamageSource src) {
        net.minecraft.world.entity.Entity e = src == null ? null : src.getEntity();
        if (e instanceof ServerPlayer) return e;
        if (e != null && e.getPersistentData().getBoolean("ws_kingdom_soldier")) return e;   // soldat du royaume : compté à part
        if (e instanceof net.minecraft.world.entity.OwnableEntity o && o.getOwner() instanceof ServerPlayer owner) return owner;
        if (e == null && dead.getKillCredit() instanceof ServerPlayer credit) return credit;   // sans auteur direct (feu, chute…)
        return e;
    }

    public static Map<UUID, Integer> snapshotKills() {
        return new HashMap<>(KILLS);
    }

    public static java.util.Set<UUID> snapshotBossKillers() {
        return new java.util.HashSet<>(BOSS_KILLERS);
    }
}
