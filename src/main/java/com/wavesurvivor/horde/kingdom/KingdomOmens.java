package com.wavesurvivor.horde.kingdom;

import com.wavesurvivor.i18n.WSLang;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * AUGURE : à chaque Calme, 3 PRÉSAGES tirés parmi ceux possibles ; il en choisit un (onglet Rôles du Monolithe) qui
 * s'applique à l'Assaut suivant, puis disparaît. Chaque présage a son avantage et sa contrepartie :
 *  - TIDE (Marée basse) : -25 % d'unités, mais 20 % d'élites ;
 *  - BOUNTY (Butin abondant) : +50 % de monnaie lâchée, mais +20 % d'unités ;
 *  - SLUMBER (Porte endormie) : une Porte n'envoie personne, les autres +30 % d'unités ;
 *  - FRENZY (Frénésie) : +40 % d'unités, mais +6 Essence à la fin de l'Assaut ;
 *  - IRON_RAIN (Pluie de fer) : 15 % de chance de +1 Fer par monstre abattu, mais 25 % des groupes sont des unités de
 *    siège (proposé seulement si la horde en contient) ;
 *  - DROWSY (Gardiens assoupis) : les gardiens des Portes ne se reforment pas, mais +30 % d'unités ;
 *  - ROYAL_HUNT (Chasse royale) : boss d'assaut à +6 Essence au lieu de +3, mais un boss en plus (proposé seulement
 *    si l'Assaut suivant contient un boss d'assaut) ;
 *  - FAIR_WINDS (Vents favorables) : Vitesse I pour les joueurs, mais aussi pour les monstres ;
 *  - BLOOD_MOON (Lune de sang) : Essence gagnée pendant l'Assaut doublée, mais monstres sous Force I ;
 *  - OFFERING (Offrande) : -35 % d'unités, payé 50 ◆ au trésor dès le choix (remboursé si l'Augure change d'avis).
 * Sans choix avant la fin du Calme : aucun présage.
 */
public final class KingdomOmens {

    private KingdomOmens() {}

    public enum Omen {
        TIDE, BOUNTY, SLUMBER, FRENZY, IRON_RAIN, DROWSY, ROYAL_HUNT, FAIR_WINDS, BLOOD_MOON, OFFERING;
        public String id() { return name().toLowerCase(); }
    }

    /** Prix de l'Offrande (monnaie du trésor). */
    public static final int OFFERING_COST = 50;

    private static final Random RNG = new Random();
    private static final List<Omen> OPTIONS = new ArrayList<>();
    /** Présages proposés au Calme précédent : ils ne reviennent pas au Calme suivant (rotation). */
    private static final List<Omen> LAST = new ArrayList<>();
    private static Omen chosen;
    /** Présage en vigueur pendant l'Assaut en cours. */
    private static Omen active;

    public static List<Omen> options() { return OPTIONS; }
    public static Omen chosen() { return chosen; }
    public static Omen active() { return active; }

    public static boolean is(Omen o) {
        return active == o && KingdomRoles.active();
    }

    public static void clear() {
        OPTIONS.clear();
        LAST.clear();
        chosen = null;
        active = null;
    }

    /** Tirage de 3 présages parmi ceux possibles, sans ceux du Calme précédent (s'il en reste assez). */
    private static void roll() {
        OPTIONS.clear();
        List<Omen> pool = new ArrayList<>();
        for (Omen o : Omen.values()) {
            if (o == Omen.IRON_RAIN && !KingdomManager.hasSiegeUnits()) continue;
            if (o == Omen.ROYAL_HUNT && !KingdomManager.nextAssaultHasBoss()) continue;
            pool.add(o);
        }
        List<Omen> fresh = new ArrayList<>(pool);
        fresh.removeAll(LAST);
        Collections.shuffle(fresh, RNG);
        OPTIONS.addAll(fresh.subList(0, Math.min(3, fresh.size())));
        // Pas assez de présages « neufs » : on complète avec les anciens
        if (OPTIONS.size() < 3) {
            List<Omen> rest = new ArrayList<>(pool);
            rest.removeAll(OPTIONS);
            Collections.shuffle(rest, RNG);
            for (Omen o : rest) { if (OPTIONS.size() >= 3) break; OPTIONS.add(o); }
        }
        LAST.clear();
        LAST.addAll(OPTIONS);
    }

    /** Annonce les présages à l'Augure : boutons cliquables dans le chat (survol = détails), aussi dans l'onglet Rôles. */
    private static void announce(ServerPlayer augur) {
        augur.sendSystemMessage(WSLang.c("kingdom.omen.offer"));
        net.minecraft.network.chat.MutableComponent line = Component.literal("  ");
        for (int i = 0; i < OPTIONS.size(); i++) {
            Omen o = OPTIONS.get(i);
            final String cmd = "/wavesurvivor komen " + i;
            line.append(Component.literal("§d§l[" + WSLang.t("kingdom.omen." + o.id()) + "]").withStyle(st -> st
                    .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, cmd))
                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                            WSLang.c("kingdom.omen." + o.id() + ".desc")))));
            line.append(Component.literal("  "));
        }
        augur.sendSystemMessage(line);
    }

    /** Début du Calme : fin du présage de l'Assaut (Frénésie payée), nouveaux présages si l'Augure est là. */
    static void onCalm(MinecraftServer server) {
        if (active == Omen.FRENZY) KingdomTreasury.reward(KingdomTreasury.Res.ESSENCE, 6, "kingdom.omen.frenzy.reward");
        active = null;
        chosen = null;
        OPTIONS.clear();
        ServerPlayer augur = KingdomRoles.holder(server, KingdomRoles.Role.AUGUR);
        if (augur != null) {
            roll();
            announce(augur);
            augur.level().playSound(null, augur.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1f, 0.8f);
        }
        KingdomRoleState.syncAll(server);
    }

    /** Augure choisi en cours de Calme : il reçoit tout de suite ses présages. */
    static void offerIfNeeded(MinecraftServer server) {
        if (KingdomManager.phase() != KingdomManager.Phase.CALM || !OPTIONS.isEmpty()) return;
        ServerPlayer augur = KingdomRoles.holder(server, KingdomRoles.Role.AUGUR);
        if (augur == null) return;
        roll();
        announce(augur);
        KingdomRoleState.syncAll(server);
    }

    /** Début de l'Assaut : le présage choisi entre en vigueur (appliqué aux budgets par KingdomManager). */
    static Omen startAssault(MinecraftServer server) {
        active = chosen;
        chosen = null;
        OPTIONS.clear();
        if (active != null) {
            Component msg = WSLang.c("kingdom.omen.active", WSLang.t("kingdom.omen." + active.id()));
            for (ServerPlayer p : server.getPlayerList().getPlayers()) p.sendSystemMessage(msg);
        }
        KingdomRoleState.syncAll(server);
        return active;
    }

    /** Choix d'un présage (codes 80-82 de la boutique). */
    public static void choose(ServerPlayer p, int i) {
        if (!KingdomRoles.has(p, KingdomRoles.Role.AUGUR)) {
            p.displayClientMessage(WSLang.c("kingdom.omen.only"), true);
            return;
        }
        if (KingdomManager.phase() != KingdomManager.Phase.CALM || i < 0 || i >= OPTIONS.size()) {
            p.displayClientMessage(WSLang.c("kingdom.omen.calm_only"), true);
            return;
        }
        Omen pick = OPTIONS.get(i);
        if (pick == chosen) return;
        var money = KingdomTreasury.Res.MONEY;
        // Offrande : payée tout de suite (refusée sans les fonds) ; changer d'avis la rembourse
        if (pick == Omen.OFFERING && !p.isCreative()) {
            if (!KingdomTreasury.has(money, OFFERING_COST)) {
                p.displayClientMessage(WSLang.c("kingdom.omen.offering.no_funds", OFFERING_COST), true);
                return;
            }
            KingdomTreasury.spend(money, OFFERING_COST);
        }
        if (chosen == Omen.OFFERING && !p.isCreative()) {
            KingdomTreasury.add(money, OFFERING_COST);
            p.sendSystemMessage(WSLang.c("kingdom.omen.offering.refund", OFFERING_COST));
        }
        KingdomTreasury.sync(p.getServer());
        chosen = pick;
        p.displayClientMessage(WSLang.c("kingdom.omen.chosen", WSLang.t("kingdom.omen." + chosen.id())), true);
        p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 1f);
        KingdomRoleState.syncAll(p.getServer());
    }
}
