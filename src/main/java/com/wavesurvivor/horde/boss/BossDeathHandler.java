package com.wavesurvivor.horde.boss;

import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Détecte la mort d'un boss pour retirer sa bar wither.
 */
public class BossDeathHandler {

    /** Boss morts dont le butin n'est pas encore tiré (LivingDeathEvent précède LivingDropsEvent). */
    public static final java.util.Set<java.util.UUID> BOSSES = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!event.getEntity().level().isClientSide && BossManager.isBoss(event.getEntity().getUUID())) BOSSES.add(event.getEntity().getUUID()); // pour le butin (Marchand 9)
        if (!event.getEntity().level().isClientSide && BossManager.isBoss(event.getEntity().getUUID())
                && !com.wavesurvivor.horde.bossrush.BossRush.active()) {
            com.wavesurvivor.horde.roulette.SealedKey.onBossKilled(event.getEntity()); // butin rare : Clé scellée
        }
        BossManager.onBossDeath(event.getEntity().getUUID());
    }
}
