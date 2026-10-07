package com.wavesurvivor.horde;

import com.wavesurvivor.horde.boss.BossManager;
import com.wavesurvivor.horde.spawn.MobRegistry;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * FIN DE VAGUE FORCÉE (hordes classiques et Assauts du mode Kingdom) : quand toutes les unités de la vague sont sorties
 * et qu'il en reste 10 % ou moins en vie (sans boss vivant), un compte à rebours démarre ({@code forceEndSeconds} de la
 * horde, 60 s par défaut, 0 = désactivé). Les survivantes brillent (visibles à travers les murs) pour être retrouvées ;
 * à la fin du compte à rebours, celles qui restent disparaissent SANS butin ni expérience et la partie enchaîne
 * (Calme ou vague suivante). Évite les vagues bloquées par une unité orpheline ou coincée.
 */
public final class WaveCleanup {

    private WaveCleanup() {}

    /** Part de la vague restante (au plus) qui déclenche le compte à rebours. */
    private static final double THRESHOLD = 0.10;

    /** Unités de la horde vues pendant la vague (= taille de la vague). */
    private static final Set<UUID> SEEN = new HashSet<>();
    /** Tick de fin du compte à rebours (-1 = pas lancé). */
    private static long deadline = -1;

    /** Nouvelle vague / nouvel Assaut : compteurs remis à zéro. */
    public static void reset() {
        SEEN.clear();
        deadline = -1;
    }

    public static boolean running() {
        return deadline >= 0;
    }

    /**
     * À appeler à chaque tick pendant une vague.
     * @param allSpawned toutes les unités de la vague sont sorties
     * @param seconds    durée du compte à rebours (0 = fonction désactivée)
     * @return vrai si les dernières unités viennent d'être retirées (la vague peut se terminer)
     */
    public static boolean tick(MinecraftServer srv, long now, boolean allSpawned, int seconds) {
        if (seconds <= 0) {
            deadline = -1;
            return false;
        }
        if (now % 20 == 0) for (var en : MobRegistry.entries()) SEEN.add(en.getKey());
        int alive = MobRegistry.size();
        if (deadline < 0) {
            if (now % 20 != 0 || !allSpawned || alive <= 0 || SEEN.isEmpty() || BossManager.activeBossCount() > 0) return false;
            int limit = Math.max(1, (int) Math.ceil(SEEN.size() * THRESHOLD));
            if (alive > limit) return false;
            deadline = now + seconds * 20L;
            highlight(srv);
            broadcast(srv, WSLang.c("horde.force_end.start", alive, seconds));
            return false;
        }
        // Compte à rebours en cours
        if (alive <= 0) {
            deadline = -1; // vague terminée normalement… et à temps : petit bonus
            clearedInTime(srv);
            return false;
        }
        if (BossManager.activeBossCount() > 0) {
            deadline = -1; // un boss est apparu entre-temps : on laisse le combat se terminer
            return false;
        }
        if (now < deadline) {
            if (now % 20 == 0) {
                long left = (deadline - now) / 20;
                Component bar = WSLang.c("horde.force_end.countdown", left, alive);
                for (ServerPlayer p : srv.getPlayerList().getPlayers()) p.displayClientMessage(bar, true);
                if (left % 5 == 0) highlight(srv); // nouvelles unités (renforts) mises en lumière aussi
            }
            return false;
        }
        // Temps écoulé : les survivantes disparaissent sans butin
        int removed = 0;
        for (var en : new ArrayList<>(MobRegistry.entries())) {
            UUID id = en.getKey();
            Entity e = find(srv, id);
            if (e != null) {
                if (e.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.5, e.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
                }
                e.discard();
                removed++;
            }
            MobRegistry.remove(id);
        }
        deadline = -1;
        broadcast(srv, WSLang.c("horde.force_end.done", removed));
        return true;
    }

    /**
     * Les dernières unités ont été tuées avant la fin du décompte : bonus de la horde (« forceEndBonus »).
     * Kingdom : versé au trésor commun ; horde classique : émeraudes données à chaque joueur.
     */
    private static void clearedInTime(MinecraftServer srv) {
        var horde = HordeManager.get().getActiveHorde();
        if (horde == null || horde.configData == null || horde.configData.forceEndBonus == null) return;
        var b = horde.configData.forceEndBonus;
        if (com.wavesurvivor.horde.kingdom.KingdomTreasury.active()) {
            if (b.money > 0) com.wavesurvivor.horde.kingdom.KingdomTreasury.add(com.wavesurvivor.horde.kingdom.KingdomTreasury.Res.MONEY, b.money);
            if (b.wood > 0) com.wavesurvivor.horde.kingdom.KingdomTreasury.add(com.wavesurvivor.horde.kingdom.KingdomTreasury.Res.WOOD, b.wood);
            if (b.stone > 0) com.wavesurvivor.horde.kingdom.KingdomTreasury.add(com.wavesurvivor.horde.kingdom.KingdomTreasury.Res.STONE, b.stone);
            if (b.iron > 0) com.wavesurvivor.horde.kingdom.KingdomTreasury.add(com.wavesurvivor.horde.kingdom.KingdomTreasury.Res.IRON, b.iron);
            if (b.essence > 0) com.wavesurvivor.horde.kingdom.KingdomTreasury.add(com.wavesurvivor.horde.kingdom.KingdomTreasury.Res.ESSENCE, b.essence);
            if (b.money + b.wood + b.stone + b.iron + b.essence > 0) {
                broadcast(srv, WSLang.c("horde.force_end.bonus_kingdom", b.money, b.wood, b.stone, b.iron, b.essence));
            }
        } else if (b.emeralds > 0) {
            for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                if (p.isSpectator()) continue;
                net.minecraft.world.item.ItemStack em = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, b.emeralds);
                if (!p.getInventory().add(em)) p.drop(em, false);
            }
            broadcast(srv, WSLang.c("horde.force_end.bonus_classic", b.emeralds));
        }
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            p.playNotifySound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 1.6f);
        }
    }

    /** Les survivantes brillent : contour visible à travers les murs. */
    private static void highlight(MinecraftServer srv) {
        for (var en : MobRegistry.entries()) {
            Entity e = find(srv, en.getKey());
            if (e != null) e.setGlowingTag(true);
        }
    }

    private static Entity find(MinecraftServer srv, UUID id) {
        for (ServerLevel l : srv.getAllLevels()) {
            Entity e = l.getEntity(id);
            if (e != null) return e;
        }
        return null;
    }

    private static void broadcast(MinecraftServer srv, Component msg) {
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) p.sendSystemMessage(msg);
    }
}
