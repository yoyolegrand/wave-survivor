package com.wavesurvivor.horde.benediction;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Bénédictions persistantes : un joueur mort (solo, multi, Kingdom) retrouve TOUTES ses bénédictions en réapparaissant,
 * tant que la horde n'est pas terminée. Idem à la reconnexion. Hors horde, les restes éventuels sont dissipés.
 * (Les niveaux sont stockés dans « PlayerPersisted », conservé à la mort ; les modificateurs d'attribut, eux, sont
 * perdus avec l'ancien corps et doivent être réappliqués.)
 */
public class BenedictionEvents {

    private static void refresh(ServerPlayer p) {
        if (com.wavesurvivor.horde.HordeManager.get().isRunning()) RogueUpgradeManager.reapplyAll(p);
        else RogueUpgradeManager.clearAll(p);
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && !e.isEndConquered()) refresh(p);
        else if (e.getEntity() instanceof ServerPlayer p2) RogueUpgradeManager.reapplyAll(p2); // retour de l'End : on garde
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) refresh(p);
    }

    @SubscribeEvent
    public void onChangeDim(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && com.wavesurvivor.horde.HordeManager.get().isRunning()) RogueUpgradeManager.reapplyAll(p);
    }
}
