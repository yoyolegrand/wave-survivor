package com.wavesurvivor.horde.benediction;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.horde.HordeManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Gère la persistance des bénédictions à travers respawn et login.
 *
 *  - Respawn : si la horde est encore en cours, réapplique les modifiers (les attributes
 *    sont reset au respawn par Minecraft, donc il faut les remettre).
 *    Si la horde est finie, purge tout.
 *  - Login   : idem — permet de récupérer les modifiers après un crash/reconnect en pleine horde.
 */
public class BenedictionEventHandler {

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (HordeManager.get().isRunning()) {
            RogueUpgradeManager.reapplyAll(sp);
            WaveSurvivorMod.LOGGER.info("[Benediction] Respawn de '{}' pendant horde → reapply",
                    sp.getName().getString());
        } else {
            // Horde finie mais NBT encore là (edge case) → clean ; sauf partie sauvegardée en attente de reprise
            if (!RogueUpgradeManager.keptForResume(sp.getServer())) RogueUpgradeManager.clearAll(sp);
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (HordeManager.get().isRunning()) {
            RogueUpgradeManager.reapplyAll(sp);
            WaveSurvivorMod.LOGGER.info("[Benediction] Login de '{}' pendant horde → reapply",
                    sp.getName().getString());
        } else {
            // Cleanup si NBT résiduel d'une session précédente qui n'a pas fini proprement ; sauf partie sauvegardée à reprendre
            if (!RogueUpgradeManager.keptForResume(sp.getServer())) RogueUpgradeManager.clearAll(sp);
        }
    }
}
