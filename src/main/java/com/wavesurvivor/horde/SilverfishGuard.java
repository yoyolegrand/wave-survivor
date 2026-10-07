package com.wavesurvivor.horde;

import net.minecraft.world.entity.monster.Silverfish;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Pendant une horde (classique ou Kingdom), les POISSONS D'ARGENT ne peuvent plus infester la pierre (ni réveiller
 * des blocs infestés en cassant le terrain) : les deux comportements vanilla passent par le « mob griefing », refusé ici.
 * Ils se battent normalement. Hors horde, rien ne change.
 */
public class SilverfishGuard {

    @SubscribeEvent
    public void silverfishNoInfest(EntityMobGriefingEvent e) {
        if (!(e.getEntity() instanceof Silverfish) || e.getEntity().level().isClientSide) return;
        if (HordeManager.get().isRunning()) e.setResult(Event.Result.DENY);
    }
}
