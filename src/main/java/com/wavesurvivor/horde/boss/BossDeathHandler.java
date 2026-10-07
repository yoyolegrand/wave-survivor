package com.wavesurvivor.horde.boss;

import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Détecte la mort d'un boss pour retirer sa bar wither.
 */
public class BossDeathHandler {

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        BossManager.onBossDeath(event.getEntity().getUUID());
    }
}
