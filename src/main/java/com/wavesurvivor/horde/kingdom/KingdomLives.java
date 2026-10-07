package com.wavesurvivor.horde.kingdom;

import com.wavesurvivor.horde.HordeManager;
import com.wavesurvivor.horde.mutator.HordeMutators;
import com.wavesurvivor.horde.mutator.Mutator;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MORT EN MODE KINGDOM :
 *  - Solo : la mort met fin à la partie (défaite) ;
 *  - Multijoueur : le joueur réapparaît en SPECTATEUR au-dessus du Monolithe, attend 10 s, puis revient en Survie
 *    à côté du Monolithe ;
 *  - Défaite si TOUS les joueurs sont tombés en même temps (morts ou en attente).
 * Avec le mutateur « Une seule vie », le joueur reste spectateur jusqu'à la fin (règle du mutateur).
 */
public final class KingdomLives {

    private KingdomLives() {}

    /** Secondes en spectateur avant de revenir. */
    public static final int WAIT_SECONDS = 10;

    /** Joueurs tombés → tick de retour (-1 = encore sur l'écran de mort). */
    private static final Map<UUID, Long> DOWN = new HashMap<>();
    /** Joueurs tombés déconnectés à la fin de la partie : remis en Survie à leur retour. */
    private static final java.util.Set<UUID> RESTORE = new java.util.HashSet<>();
    private static boolean defeatPending = false;

    public static boolean isDown(UUID id) {
        return DOWN.containsKey(id);
    }

    /** Fin de partie : les joueurs en attente reviennent en Survie. */
    public static void stop(MinecraftServer server) {
        if (server != null) {
            for (UUID id : DOWN.keySet()) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p == null) RESTORE.add(id);
                else if (p.isSpectator() && !HordeMutators.on(Mutator.ONE_LIFE)) p.setGameMode(GameType.SURVIVAL);
            }
        }
        DOWN.clear();
        defeatPending = false;
    }

    private static boolean inGame(ServerPlayer p) {
        return KingdomManager.isActive() && HordeManager.get().isRunning() && KingdomManager.level() != null;
    }

    /** Point de retour : à quelques blocs du Monolithe, au niveau du sol. */
    private static BlockPos returnPos(ServerLevel lvl) {
        BlockPos c = KingdomManager.center();
        if (c == null) return lvl.getSharedSpawnPos();
        BlockPos q = c.offset(3, 0, 0);
        return lvl.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, q);
    }

    private static void defeat(MinecraftServer server, String key) {
        if (defeatPending) return;
        defeatPending = true;
        com.wavesurvivor.horde.renaissance.RenaissanceRewards.markDefeat(); // PR des Assauts survécus versés à l'arrêt
        // Hors de l'événement en cours : arrêt propre au tick suivant
        server.execute(() -> {
            if (!KingdomManager.isActive()) return;
            Component msg = WSLang.c(key);
            for (ServerPlayer o : server.getPlayerList().getPlayers()) o.sendSystemMessage(msg);
            HordeManager.get().endKingdom(false);
        });
    }

    public static class Events {

        @SubscribeEvent(priority = EventPriority.LOW)
        public void livesDeath(LivingDeathEvent e) {
            if (e.isCanceled() || !(e.getEntity() instanceof ServerPlayer p) || !inGame(p)) return;
            MinecraftServer server = p.getServer();
            if (server == null || p.isCreative() || p.isSpectator()) return;
            long others = server.getPlayerList().getPlayers().stream()
                    .filter(o -> o != p && !o.isCreative() && !(o.isSpectator() && !DOWN.containsKey(o.getUUID()))).count();
            if (others == 0) {
                defeat(server, "kingdom.death.solo");
                return;
            }
            DOWN.put(p.getUUID(), -1L);
            if (!HordeMutators.on(Mutator.ONE_LIFE)) {
                Component msg = WSLang.c("kingdom.death.fallen", p.getGameProfile().getName(), WAIT_SECONDS);
                for (ServerPlayer o : server.getPlayerList().getPlayers()) o.sendSystemMessage(msg);
            }
        }

        /** Réapparition : spectateur au-dessus du Monolithe, compte à rebours lancé. */
        @SubscribeEvent
        public void livesRespawn(PlayerEvent.PlayerRespawnEvent e) {
            if (!(e.getEntity() instanceof ServerPlayer p) || !DOWN.containsKey(p.getUUID()) || !inGame(p)) return;
            ServerLevel lvl = KingdomManager.level();
            BlockPos at = returnPos(lvl).above(6);
            p.setGameMode(GameType.SPECTATOR);
            p.teleportTo(lvl, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p.getYRot(), 60f);
            if (HordeMutators.on(Mutator.ONE_LIFE)) return; // le mutateur garde le joueur spectateur
            DOWN.put(p.getUUID(), (long) p.getServer().getTickCount()
                    + com.wavesurvivor.horde.renaissance.Heritage.respawnSeconds(p, WAIT_SECONDS) * 20L); // Héritage : Survivant 3
        }

        @SubscribeEvent
        public void livesTick(TickEvent.ServerTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            MinecraftServer server = e.getServer();
            if (!KingdomManager.isActive()) {
                if (!DOWN.isEmpty()) stop(server);
                return;
            }
            if (DOWN.isEmpty()) return;
            long now = server.getTickCount();
            ServerLevel lvl = KingdomManager.level();
            // Retours
            for (Map.Entry<UUID, Long> en : new HashMap<>(DOWN).entrySet()) {
                long at = en.getValue();
                if (at < 0) continue;
                ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
                if (p == null) continue;
                long left = at - now;
                if (left > 0) {
                    if (left % 20 == 0) p.displayClientMessage(WSLang.c("kingdom.death.countdown", left / 20), true);
                    continue;
                }
                DOWN.remove(en.getKey());
                BlockPos back = returnPos(lvl);
                p.setGameMode(GameType.SURVIVAL);
                p.teleportTo(lvl, back.getX() + 0.5, back.getY(), back.getZ() + 0.5, p.getYRot(), 0f);
                p.displayClientMessage(WSLang.c("kingdom.death.back"), true);
                com.wavesurvivor.horde.renaissance.Heritage.onRevive(p); // Survivant 3 : 5 s d'invulnérabilité
            }
            // Tout le monde est tombé en même temps → défaite
            if (now % 20 == 0) {
                boolean anyUp = false;
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    if (DOWN.containsKey(p.getUUID())) continue;
                    if (p.isSpectator() || !p.isAlive()) continue;
                    anyUp = true;
                    break;
                }
                if (!anyUp) defeat(server, "kingdom.death.wiped");
            }
        }

        /** Déconnecté pendant l'attente, reconnecté après la partie : remis en Survie. */
        @SubscribeEvent
        public void livesLogin(PlayerEvent.PlayerLoggedInEvent e) {
            if (!(e.getEntity() instanceof ServerPlayer p)) return;
            if (RESTORE.remove(p.getUUID()) && !KingdomManager.isActive() && p.isSpectator()) {
                p.setGameMode(GameType.SURVIVAL);
            }
        }
    }
}
