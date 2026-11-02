package com.wavesurvivor.horde.kingdom.vocation;

import com.wavesurvivor.entity.GisementEntity;
import com.wavesurvivor.horde.chaos.GisementManager;
import com.wavesurvivor.horde.kingdom.KingdomRoles;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** VOCATIONS — talents qui passent par un événement de combat (pas rattachés à une mécanique de rôle existante). */
public class VocationEvents {

    @SubscribeEvent
    public void onHurt(LivingHurtEvent e) {
        if (!KingdomRoles.active()) return;
        // Exploitant › Endurance : dégâts subis réduits près d'un gisement ou d'un arbre
        if (e.getEntity() instanceof ServerPlayer v && KingdomRoles.has(v, KingdomRoles.Role.MINER)) {
            double cut = Vocations.total(v, KingdomRoles.Role.MINER, "miner.endurance.resist") / 100.0;
            if (cut > 0) {
                for (GisementEntity g : GisementManager.deposits(v.getServer())) {
                    if (g.level() == v.level() && g.distanceToSqr(v) < 36) {
                        e.setAmount((float) (e.getAmount() * (1.0 - Math.min(0.8, cut))));
                        break;
                    }
                }
            }
        }
        if (!(e.getSource().getEntity() instanceof ServerPlayer p)) return;
        // Chasseur de primes › Traque : +10 % de dégâts par rang sur les élites
        if (KingdomRoles.has(p, KingdomRoles.Role.BOUNTY_HUNTER) && e.getEntity().getPersistentData().getBoolean("ws_elite")) {
            double bonus = Vocations.total(p, KingdomRoles.Role.BOUNTY_HUNTER, "bounty_hunter.hunt.elite_damage") / 100.0;
            if (bonus > 0) e.setAmount((float) (e.getAmount() * (1.0 + bonus)));
        }
        // Arcaniste › Arcanes offensives : dégâts magiques (magie, magie indirecte, foudre)
        if (KingdomRoles.has(p, KingdomRoles.Role.ARCANIST)
                && (e.getSource().is(DamageTypes.MAGIC) || e.getSource().is(DamageTypes.INDIRECT_MAGIC) || e.getSource().is(DamageTypes.LIGHTNING_BOLT))) {
            double bonus = Vocations.total(p, KingdomRoles.Role.ARCANIST, "arcanist.offense.magic_damage") / 100.0;
            if (bonus > 0) e.setAmount((float) (e.getAmount() * (1.0 + bonus)));
        }
    }
}
