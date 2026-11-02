package com.wavesurvivor.horde.kingdom.vocation;

import com.wavesurvivor.entity.GisementEntity;
import com.wavesurvivor.horde.chaos.GisementManager;
import com.wavesurvivor.horde.kingdom.KingdomRoles;
import com.wavesurvivor.horde.kingdom.KingdomTreasury;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

/** VOCATIONS — talents qui passent par un événement de combat (pas rattachés à une mécanique de rôle existante). */
public class VocationEvents {

    /** Chasseur › Traque : cible marquée (une seule à la fois) et fin de la marque. */
    private static UUID marked = null;
    private static long markedUntil = 0;
    /** Chasseur › Trophées : élites abattues pendant la partie (bonus permanent de dégâts). */
    private static int trophies = 0;
    /** Panacée de l'Alchimiste : déjà déclenchée pendant cet Assaut. */
    private static boolean panaceaUsed = false;

    /** Remise à zéro (fin de partie). */
    public static void reset() {
        marked = null;
        markedUntil = 0;
        trophies = 0;
        panaceaUsed = false;
    }

    /** Nouvel Assaut : la Panacée redevient disponible. */
    public static void onAssaultStart() { panaceaUsed = false; }

    /** Chasseur de primes › Trophées : les élites qu'il abat lâchent plus de butin (comme l'Intendant : arrondi au hasard). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDrops(LivingDropsEvent e) {
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

    /** Connexion : le client reçoit sa Maîtrise et la configuration des arbres (affichage dans le Monolithe). */
    @SubscribeEvent
    public void onLogin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) VocationSync.send(p, true);
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent e) {
        if (!KingdomRoles.active() || !(e.getEntity().level() instanceof ServerLevel lvl)) return;
        LivingEntity dead = e.getEntity();
        boolean champion = dead.getPersistentData().getBoolean("ws_obj_champion");
        boolean elite = dead.getPersistentData().getBoolean("ws_elite");
        if (e.getSource().getEntity() instanceof ServerPlayer p && KingdomRoles.has(p, KingdomRoles.Role.BOUNTY_HUNTER)) {
            // Trophées : Essence d'un Champion, collection, chance de trophée
            if (champion) {
                int ess = (int) Vocations.total(p, KingdomRoles.Role.BOUNTY_HUNTER, "bounty_hunter.trophies.champion_essence");
                if (ess > 0) KingdomTreasury.reward(KingdomTreasury.Res.ESSENCE, ess, "voc.reason.champion");
            }
            if (elite) {
                if (Vocations.has(p, KingdomRoles.Role.BOUNTY_HUNTER, "bounty_hunter.trophies.collection")) trophies++;
                double chance = Vocations.total(p, KingdomRoles.Role.BOUNTY_HUNTER, "bounty_hunter.trophies.trophy_chance") / 100.0;
                if (chance > 0 && lvl.random.nextDouble() < chance) {
                    p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 1200, 0)); // trophée : Force I pendant 60 s
                    p.sendSystemMessage(com.wavesurvivor.i18n.WSLang.c("voc.trophy"));
                }
                // Ultime Grand veneur : chaque élite abattue soigne l'équipe
                if (Vocations.has(p, KingdomRoles.Role.BOUNTY_HUNTER, "bounty_hunter.ult.great_hunter")) {
                    for (ServerPlayer o : lvl.players()) o.heal(4f);
                }
            }
        }
        // Récupérateur › Pillage : les Champions lâchent des débris
        if (champion) {
            ServerPlayer s = Voc.holder(KingdomRoles.Role.SCAVENGER);
            double bonus = s == null ? 0 : Vocations.total(s, KingdomRoles.Role.SCAVENGER, "scavenger.plunder.champion_loot") / 100.0;
            if (bonus > 0) com.wavesurvivor.horde.kingdom.KingdomRoleExtras.bonusDrop(lvl, dead.getX(), dead.getY(), dead.getZ(),
                    (int) Math.round(4 * (1 + bonus)), (int) Math.round(3 * (1 + bonus)), (int) Math.round(4 * (1 + bonus)));
        }
        // Ultime Tête mise à prix : un boss abattu rapporte une prime énorme
        if (com.wavesurvivor.horde.boss.BossManager.isBoss(dead.getUUID()) && Voc.has(KingdomRoles.Role.BOUNTY_HUNTER, "bounty_hunter.ult.bounty_boss")) {
            KingdomTreasury.reward(KingdomTreasury.Res.MONEY, 150, "voc.reason.bounty_boss");
            KingdomTreasury.reward(KingdomTreasury.Res.ESSENCE, 8, "voc.reason.bounty_boss");
        }
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent e) {
        if (!KingdomRoles.active()) return;
        LivingEntity target = e.getEntity();
        // Exploitant › Endurance : dégâts subis réduits près d'un gisement ou d'un arbre
        if (target instanceof ServerPlayer v && KingdomRoles.has(v, KingdomRoles.Role.MINER)) {
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
        // Ultime Panacée : 1 fois par Assaut, un joueur sous 30 % de PV → toute l'équipe est soignée et purgée
        if (target instanceof ServerPlayer v && !panaceaUsed && Voc.has(KingdomRoles.Role.ALCHEMIST, "alchemist.ult.panacea")
                && com.wavesurvivor.horde.kingdom.KingdomManager.phase() == com.wavesurvivor.horde.kingdom.KingdomManager.Phase.ASSAULT
                && v.getHealth() - e.getAmount() < v.getMaxHealth() * 0.3f) {
            panaceaUsed = true;
            for (ServerPlayer o : v.serverLevel().players()) {
                o.heal(o.getMaxHealth());
                for (MobEffectInstance fx : new java.util.ArrayList<>(o.getActiveEffects())) {
                    if (fx.getEffect().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL) o.removeEffect(fx.getEffect());
                }
                o.sendSystemMessage(com.wavesurvivor.i18n.WSLang.c("voc.panacea"));
            }
            e.setAmount(0);
            return;
        }
        if (!(e.getSource().getEntity() instanceof ServerPlayer p)) return;
        long now = target.level().getGameTime();
        // Cible marquée : toute l'équipe lui fait plus de dégâts
        if (marked != null && now < markedUntil && marked.equals(target.getUUID())) e.setAmount(e.getAmount() * 1.25f);
        if (KingdomRoles.has(p, KingdomRoles.Role.BOUNTY_HUNTER)) {
            // Traque › Marque : sa première cible touchée est marquée 10 s
            if (Vocations.has(p, KingdomRoles.Role.BOUNTY_HUNTER, "bounty_hunter.hunt.mark") && (marked == null || now >= markedUntil)
                    && target instanceof net.minecraft.world.entity.monster.Enemy) {
                marked = target.getUUID();
                markedUntil = now + 200;
            }
            // Traque : +10 % de dégâts par rang sur les élites
            if (target.getPersistentData().getBoolean("ws_elite")) {
                double bonus = Vocations.total(p, KingdomRoles.Role.BOUNTY_HUNTER, "bounty_hunter.hunt.elite_damage") / 100.0;
                if (bonus > 0) e.setAmount((float) (e.getAmount() * (1.0 + bonus)));
            }
            // Trophées › Collection : +1 % de dégâts par élite abattue (15 % au maximum)
            if (trophies > 0) e.setAmount(e.getAmount() * (1f + Math.min(0.15f, trophies * 0.01f)));
        }
        // Arcaniste › Arcanes offensives : dégâts magiques (magie, magie indirecte, foudre)
        if (KingdomRoles.has(p, KingdomRoles.Role.ARCANIST)
                && (e.getSource().is(DamageTypes.MAGIC) || e.getSource().is(DamageTypes.INDIRECT_MAGIC) || e.getSource().is(DamageTypes.LIGHTNING_BOLT))) {
            double bonus = Vocations.total(p, KingdomRoles.Role.ARCANIST, "arcanist.offense.magic_damage") / 100.0;
            if (bonus > 0) e.setAmount((float) (e.getAmount() * (1.0 + bonus)));
        }
    }
}
