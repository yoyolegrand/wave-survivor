package com.wavesurvivor.client;

import com.wavesurvivor.config.ModConfig;

/**
 * Cache client de la config serveur (reçue via SyncConfigPacket).
 * Utilisé par WaveInspectionScreen pour afficher les hordes/vagues sans re-request.
 *
 * Reset au logout implicite : le cache reste, mais sera écrasé à la prochaine connexion.
 * clear() peut être appelé sur ClientPlayerNetworkEvent.LoggingOut si besoin.
 */
public class ClientConfigCache {

    private static ModConfig cachedConfig;

    public static void set(ModConfig config) {
        cachedConfig = config;
    }

    public static ModConfig get() {
        return cachedConfig;
    }

    public static boolean isReady() {
        return cachedConfig != null;
    }

    public static void clear() {
        cachedConfig = null;
    }
}
