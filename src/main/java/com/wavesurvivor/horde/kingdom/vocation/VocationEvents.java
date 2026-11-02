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

    /** Chasseur de primes › Trophées : les élites qu'il abat lâchent plus de butin (comme l'Intendant : arrondi au hasard). */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public void onDrops(net.minecraftforge.event.entity.living.LivingDropsEvent e) {
        if (!KingdomRoles.active() || !(e.getSource().getEntity() instanceof ServerPlayer p) || !KingdomRoles.has(p, KingdomRoles.Role.BOUNTY_HUNTER)) return;
        if (!e.getEntity().getPersistentData().getBoolean("ws_elite")) return;
        double bonus = Vocations.total(p, KingdomRoles.Role.BOUNTY_HUNTER, "bounty_hunter.trophies.elite_loot") / 100.0;
        if (bonus <= 0) return;
        var rnd = e.getEntity().getRandom();
        for (net.minecraft.world.entity.item.ItemEntity ie : e.getDrops()) {
            var st = ie.getItem();
            double extra = st.getCount() * bonus;
            int add = (int) Math.floor(extra) + (rnd.nextDouble() < extra - Math.floor(extra) ? 1 : 0);
            if (add > 0) {
                var copy = st.copy();
                copy.grow(add);
                ie.setItem(copy);
            }
        }
    }

    /** Chasseur de primes › Trophées : un Champion de Porte abattu par lui rapporte de l'Essence en plus. */
    @SubscribeEvent
    public void onDeath(net.minecraftforge.event.entity.living.LivingDeathEvent e) {
        if (!KingdomRoles.active() || !(e.getSource().getEntity() instanceof ServerPlayer p) || !KingdomRoles.has(p, KingdomRoles.Role.BOUNTY_HUNTER)) return;
        if (!e.getEntity().getPersistentData().getBoolean("ws_obj_champion")) return;
        int ess = (int) Vocations.total(p, KingdomRoles.Role.BOUNTY_HUNTER, "bounty_hunter.trophies.champion_essence");
        if (ess > 0) com.wavesurvivor.horde.kingdom.KingdomTreasury.reward(com.wavesurvivor.horde.kingdom.KingdomTreasury.Res.ESSENCE, ess, "voc.reason.champion");
    }

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
