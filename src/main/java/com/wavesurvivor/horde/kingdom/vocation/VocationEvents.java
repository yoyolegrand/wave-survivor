package com.wavesurvivor.horde.kingdom.vocation;

import com.wavesurvivor.horde.kingdom.KingdomRoles;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** VOCATIONS — talents qui passent par un événement de combat (pas rattachés à une mécanique de rôle existante). */
public class VocationEvents {

    /** Chasseur de primes › Traque : +10 % de dégâts par rang sur les élites. */
    @SubscribeEvent
    public void onHurt(LivingHurtEvent e) {
        if (!KingdomRoles.active() || !(e.getSource().getEntity() instanceof ServerPlayer p)) return;
        if (!KingdomRoles.has(p, KingdomRoles.Role.BOUNTY_HUNTER)) return;
        if (!e.getEntity().getPersistentData().getBoolean("ws_elite")) return;
        double bonus = Vocations.total(p, KingdomRoles.Role.BOUNTY_HUNTER, "bounty_hunter.hunt.elite_damage") / 100.0;
        if (bonus > 0) e.setAmount((float) (e.getAmount() * (1.0 + bonus)));
    }
}
