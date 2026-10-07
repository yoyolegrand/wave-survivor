package com.wavesurvivor.network;

import com.wavesurvivor.WaveSurvivorMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * Envoie automatiquement la config au client à chaque login,
 * et permet de broadcast la config à tous les joueurs après un /ws reload.
 *
 * Register via MinecraftForge.EVENT_BUS.register(new PlayerLoginHandler())
 * dans le constructeur de WaveSurvivorMod.
 */
public class PlayerLoginHandler {

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            // Langue du mod
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                    new LangSyncPacket(com.wavesurvivor.i18n.WSLang.get()));
            try {
                SyncConfigPacket pkt = SyncConfigPacket.fromConfig(WaveSurvivorMod.getConfig());
                NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), pkt);
                WaveSurvivorMod.LOGGER.info("[Network] Config envoyée au joueur '{}'", sp.getName().getString());
            } catch (Exception e) {
                WaveSurvivorMod.LOGGER.error("[Network] Erreur envoi config au login pour '{}' : {}",
                        sp.getName().getString(), e.getMessage(), e);
            }
        }
    }

    /**
     * Broadcast la config actuelle à tous les joueurs connectés.
     * Appelé après un /ws reload pour que tout le monde ait la nouvelle config.
     */
    public static void broadcastToAll(MinecraftServer server) {
        if (server == null) return;
        try {
            SyncConfigPacket pkt = SyncConfigPacket.fromConfig(WaveSurvivorMod.getConfig());
            int n = 0;
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), pkt);
                n++;
            }
            WaveSurvivorMod.LOGGER.info("[Network] Config broadcast à {} joueur(s)", n);
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[Network] Erreur broadcast config : {}", e.getMessage(), e);
        }
    }
}
