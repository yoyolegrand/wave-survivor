package com.wavesurvivor.horde.kingdom;

import com.wavesurvivor.horde.kingdom.vocation.Voc;
import com.wavesurvivor.horde.kingdom.vocation.Vocations;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * AUGURE : à chaque Calme, des PRÉSAGES tirés parmi ceux possibles (2 de base, plus avec les talents) ; il en choisit
 * un (onglet Rôles du Monolithe) qui s'applique à l'Assaut suivant, puis disparaît. Chaque présage a son avantage et sa contrepartie :
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
 * VOCATIONS (1.9) : les valeurs d'avantage et de contrepartie passent par {@link #boon} et {@link #curse} (talents Maîtrise,
 * Convergence), plusieurs présages peuvent être actifs (présage double / triple), un présage peut être scellé, le tirage relancé
 * ou annoncé à l'avance, et le présage peut durer un moment au début du Calme suivant.
 * Sans choix avant la fin du Calme : aucun présage.
 */
public final class KingdomOmens {

    private KingdomOmens() {}

    public enum Omen {
        TIDE, BOUNTY, SLUMBER, FRENZY, IRON_RAIN, DROWSY, ROYAL_HUNT, FAIR_WINDS, BLOOD_MOON, OFFERING;
        public String id() { return name().toLowerCase(); }
    }

    /** Prix de l'Offrande (monnaie du trésor), avant talents. */
    public static final int OFFERING_COST = 50;

    private static final Random RNG = new Random();
    private static final List<Omen> OPTIONS = new ArrayList<>();
    /** Présages proposés au Calme précédent : ils ne reviennent pas au Calme suivant (rotation). */
    private static final List<Omen> LAST = new ArrayList<>();
    /** Présages choisis pour l'Assaut suivant (1 de base ; 2 avec le présage double ; 3 avec l'ultime). */
    private static final List<Omen> CHOSEN = new ArrayList<>();
    /** Présages en vigueur pendant l'Assaut en cours. */
    private static final List<Omen> ACTIVE = new ArrayList<>();
    /** Présages de l'Assaut précédent, encore actifs au début du Calme (talent Maîtrise › présage persistant). */
    private static final List<Omen> CARRY = new ArrayList<>();
    private static long carryUntil = 0;
    /** Présages de l'Assaut précédent (pour le cumul) et ceux qui sont renforcés pour cet Assaut. */
    private static List<Omen> prevActive = new ArrayList<>();
    private static final Set<Omen> STACKED = new HashSet<>();
    /** Présage scellé (gardé pour plus tard), tirage annoncé à l'avance, relance déjà utilisée ce Calme. */
    private static Omen sealed = null;
    private static List<Omen> next = null;
    private static boolean rerolled = false;
    /** Somme payée pour l'Offrande choisie (remboursée telle quelle si l'Augure change d'avis). */
    private static int offeringPaid = 0;

    public static List<Omen> options() { return OPTIONS; }
    public static List<Omen> chosenList() { return CHOSEN; }
    public static List<Omen> activeList() { return ACTIVE; }
    public static Omen sealed() { return sealed; }

    /** Premier présage choisi / actif (compatibilité) ; null s'il n'y en a pas. */
    public static Omen chosen() { return CHOSEN.isEmpty() ? null : CHOSEN.get(0); }
    public static Omen active() { return ACTIVE.isEmpty() ? null : ACTIVE.get(0); }

    private static boolean carryNow() {
        var lvl = KingdomManager.level();
        return !CARRY.isEmpty() && lvl != null && lvl.getServer().getTickCount() < carryUntil;
    }

    public static boolean is(Omen o) {
        return KingdomRoles.active() && (ACTIVE.contains(o) || (CARRY.contains(o) && carryNow()));
    }

    // ─── Valeurs d'avantage / de contrepartie (talents de l'Augure) ───

    /** Multiplicateur des AVANTAGES d'un présage : talent « Avantages renforcés », cumul de présages, harmonie. */
    public static double boon(Omen o) {
        double b = 1.0 + Voc.pct(KingdomRoles.Role.AUGUR, "augur.mastery.omen_boon");
        if (STACKED.contains(o)) b *= 1.25;
        if (ACTIVE.size() >= 2 && Voc.has(KingdomRoles.Role.AUGUR, "augur.convergence.harmony")) b *= 1.15;
        return b;
    }

    /** Multiplicateur des CONTREPARTIES d'un présage (≤ 1 avec les talents) ; réduit encore avec deux présages ou plus. */
    public static double curse(Omen o) {
        double c = 1.0 - Math.min(0.8, Voc.pct(KingdomRoles.Role.AUGUR, "augur.mastery.omen_curse"));
        if (ACTIVE.size() >= 2) c *= 1.0 - Math.min(0.8, Voc.pct(KingdomRoles.Role.AUGUR, "augur.convergence.double_curse_cut"));
        return c;
    }

    /** Nombre de présages qu'on peut choisir : 1, +1 avec le présage double (ou au 1er Calme avec « double dès le début »), 3 avec l'ultime. */
    public static int maxPicks() {
        ServerPlayer a = Voc.holder(KingdomRoles.Role.AUGUR);
        if (a == null) return 1;
        if (Vocations.has(a, KingdomRoles.Role.AUGUR, "augur.ult.triple_omen")) return 3;
        if (Vocations.has(a, KingdomRoles.Role.AUGUR, "augur.convergence.double_omen")) return 2;
        if (Vocations.has(a, KingdomRoles.Role.AUGUR, "augur.convergence.double_from_start") && KingdomManager.cycle() <= 0) return 2;
        return 1;
    }

    private static int offeringCost() {
        return Math.max(1, (int) Math.round(OFFERING_COST * curse(Omen.OFFERING)));
    }

    public static void clear() {
        OPTIONS.clear();
        LAST.clear();
        CHOSEN.clear();
        ACTIVE.clear();
        CARRY.clear();
        carryUntil = 0;
        prevActive = new ArrayList<>();
        STACKED.clear();
        sealed = null;
        next = null;
        rerolled = false;
        offeringPaid = 0;
    }

    // ─── Tirage ───

    /** Nombre de présages proposés : 2 de base + talent Clairvoyance (3 au maximum : trois emplacements dans la boutique). */
    private static int drawCount() {
        return Math.min(3, (int) Voc.base(KingdomRoles.Role.AUGUR, "omenChoices", 2)
                + (int) Voc.total(KingdomRoles.Role.AUGUR, "augur.clairvoyance.extra_omen"));
    }

    /** Tirage parmi ceux possibles, sans ceux du Calme précédent (s'il en reste assez). Ne modifie rien. */
    private static List<Omen> draw() {
        List<Omen> pool = new ArrayList<>();
        for (Omen o : Omen.values()) {
            if (o == Omen.IRON_RAIN && !KingdomManager.hasSiegeUnits()) continue;
            if (o == Omen.ROYAL_HUNT && !KingdomManager.nextAssaultHasBoss()) continue;
            // Faille : une seule Porte, sans gardiens → pas de « Porte endormie » ni de « Gardiens assoupis »
            if ((o == Omen.SLUMBER || o == Omen.DROWSY) && com.wavesurvivor.horde.rift.RiftManager.kingdomSiege()) continue;
            pool.add(o);
        }
        List<Omen> fresh = new ArrayList<>(pool);
        fresh.removeAll(LAST);
        Collections.shuffle(fresh, RNG);
        int count = drawCount();
        List<Omen> out = new ArrayList<>(fresh.subList(0, Math.min(count, fresh.size())));
        // Pas assez de présages « neufs » : on complète avec les anciens
        if (out.size() < count) {
            List<Omen> rest = new ArrayList<>(pool);
            rest.removeAll(out);
            Collections.shuffle(rest, RNG);
            for (Omen o : rest) { if (out.size() >= count) break; out.add(o); }
        }
        return out;
    }

    private static void roll() {
        OPTIONS.clear();
        OPTIONS.addAll(draw());
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

    /** Début du Calme : fin des présages de l'Assaut (Frénésie payée), nouveaux présages si l'Augure est là. */
    static void onCalm(MinecraftServer server) {
        for (Omen o : ACTIVE) {
            if (o == Omen.FRENZY) {
                KingdomTreasury.reward(KingdomTreasury.Res.ESSENCE, (int) Math.round(6 * boon(o)), "kingdom.omen.frenzy.reward");
            }
        }
        // Talent « présage persistant » : les présages durent encore un moment au début du Calme
        double sec = Voc.total(KingdomRoles.Role.AUGUR, "augur.mastery.carry_over");
        CARRY.clear();
        if (sec > 0 && !ACTIVE.isEmpty() && KingdomManager.level() != null) {
            CARRY.addAll(ACTIVE);
            carryUntil = server.getTickCount() + (long) (sec * 20);
        }
        ACTIVE.clear();
        CHOSEN.clear();
        OPTIONS.clear();
        offeringPaid = 0;
        rerolled = false;
        ServerPlayer augur = KingdomRoles.holder(server, KingdomRoles.Role.AUGUR);
        if (augur != null) {
            if (next != null && next.size() >= Math.min(drawCount(), 1)) { // tirage déjà annoncé (talent Aperçu)
                OPTIONS.addAll(next);
                LAST.clear();
                LAST.addAll(OPTIONS);
                OPTIONS.removeIf(o -> (o == Omen.ROYAL_HUNT && !KingdomManager.nextAssaultHasBoss()) || (o == Omen.IRON_RAIN && !KingdomManager.hasSiegeUnits()));
                if (OPTIONS.size() < next.size()) roll();
            } else {
                roll();
            }
            announce(augur);
            augur.level().playSound(null, augur.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1f, 0.8f);
        }
        next = null;
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

    /** Début de l'Assaut : les présages choisis entrent en vigueur (appliqués aux budgets par KingdomManager). */
    static List<Omen> startAssault(MinecraftServer server) {
        ACTIVE.clear();
        ACTIVE.addAll(CHOSEN);
        STACKED.clear();
        for (Omen o : ACTIVE) if (prevActive.contains(o)) STACKED.add(o); // même présage deux Assauts de suite : plus fort (talent)
        if (!Voc.has(KingdomRoles.Role.AUGUR, "augur.mastery.stacking")) STACKED.clear();
        prevActive = new ArrayList<>(ACTIVE);
        CHOSEN.clear();
        CARRY.clear();
        OPTIONS.clear();
        offeringPaid = 0;
        if (!ACTIVE.isEmpty()) {
            com.wavesurvivor.horde.kingdom.vocation.VocationXp.awardHolder(server, KingdomRoles.Role.AUGUR, "omen_chosen");
            for (Omen o : ACTIVE) {
                Component msg = WSLang.c("kingdom.omen.active", WSLang.t("kingdom.omen." + o.id()) + (STACKED.contains(o) ? " ✦" : ""));
                for (ServerPlayer p : server.getPlayerList().getPlayers()) p.sendSystemMessage(msg);
            }
        }
        // Talent Clairvoyance › Aperçu : le tirage du prochain Calme est annoncé tout de suite
        ServerPlayer augur = KingdomRoles.holder(server, KingdomRoles.Role.AUGUR);
        next = null;
        if (augur != null && Vocations.has(augur, KingdomRoles.Role.AUGUR, "augur.clairvoyance.preview")) {
            next = draw();
            StringBuilder sb = new StringBuilder();
            for (Omen o : next) sb.append(sb.length() > 0 ? ", " : "").append(WSLang.t("kingdom.omen." + o.id()));
            augur.sendSystemMessage(WSLang.c("voc.omen.preview", sb.toString()));
        }
        KingdomRoleState.syncAll(server);
        return new ArrayList<>(ACTIVE);
    }

    // ─── Choix ───

    private static void unchoose(ServerPlayer p, Omen o) {
        CHOSEN.remove(o);
        if (o == Omen.OFFERING && offeringPaid > 0 && !p.isCreative()) {
            KingdomTreasury.add(KingdomTreasury.Res.MONEY, offeringPaid);
            p.sendSystemMessage(WSLang.c("kingdom.omen.offering.refund", offeringPaid));
        }
        if (o == Omen.OFFERING) offeringPaid = 0;
    }

    private static boolean augurInCalm(ServerPlayer p) {
        if (!KingdomRoles.has(p, KingdomRoles.Role.AUGUR)) {
            p.displayClientMessage(WSLang.c("kingdom.omen.only"), true);
            return false;
        }
        if (KingdomManager.phase() != KingdomManager.Phase.CALM) {
            p.displayClientMessage(WSLang.c("kingdom.omen.calm_only"), true);
            return false;
        }
        return true;
    }

    /** Choix d'un présage (codes 80-82 de la boutique) ; recliquer un présage déjà choisi le retire (si on peut en avoir plusieurs). */
    public static void choose(ServerPlayer p, int i) {
        if (!augurInCalm(p) || i < 0 || i >= OPTIONS.size()) return;
        Omen pick = OPTIONS.get(i);
        int max = maxPicks();
        if (CHOSEN.contains(pick)) {
            if (max > 1) {
                unchoose(p, pick);
                KingdomTreasury.sync(p.getServer());
                KingdomRoleState.syncAll(p.getServer());
            }
            return;
        }
        var money = KingdomTreasury.Res.MONEY;
        boolean full = CHOSEN.size() >= max;
        Omen oldest = full ? CHOSEN.get(0) : null;
        // Offrande : payée tout de suite (refusée sans les fonds) ; changer d'avis la rembourse
        int cost = offeringCost();
        if (pick == Omen.OFFERING && !p.isCreative()) {
            int back = oldest == Omen.OFFERING ? offeringPaid : 0;
            if (KingdomTreasury.get(money) + back < cost) {
                p.displayClientMessage(WSLang.c("kingdom.omen.offering.no_funds", cost), true);
                return;
            }
        }
        if (oldest != null) unchoose(p, oldest);
        if (pick == Omen.OFFERING && !p.isCreative()) {
            KingdomTreasury.spend(money, cost);
            offeringPaid = cost;
        }
        KingdomTreasury.sync(p.getServer());
        CHOSEN.add(pick);
        p.displayClientMessage(WSLang.c("kingdom.omen.chosen", WSLang.t("kingdom.omen." + pick.id())), true);
        p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 1f);
        KingdomRoleState.syncAll(p.getServer());
    }

    /** Talent Clairvoyance › Relance : redessine le tirage (1 fois par Calme). Code 83 de la boutique. */
    public static void reroll(ServerPlayer p) {
        if (!augurInCalm(p)) return;
        if (!Vocations.has(p, KingdomRoles.Role.AUGUR, "augur.clairvoyance.reroll") || rerolled) {
            p.displayClientMessage(WSLang.c("voc.omen.no_reroll"), true);
            return;
        }
        rerolled = true;
        for (Omen o : new ArrayList<>(CHOSEN)) unchoose(p, o);
        KingdomTreasury.sync(p.getServer());
        roll();
        announce(p);
        KingdomRoleState.syncAll(p.getServer());
    }

    /** Talent Clairvoyance › Présage scellé : garde le dernier présage choisi pour plus tard (hors Offrande). Code 84. */
    public static void seal(ServerPlayer p) {
        if (!augurInCalm(p)) return;
        if (!Vocations.has(p, KingdomRoles.Role.AUGUR, "augur.clairvoyance.sealed_omen") || sealed != null || CHOSEN.isEmpty()
                || CHOSEN.get(CHOSEN.size() - 1) == Omen.OFFERING) {
            p.displayClientMessage(WSLang.c("voc.omen.no_seal"), true);
            return;
        }
        sealed = CHOSEN.remove(CHOSEN.size() - 1);
        p.displayClientMessage(WSLang.c("voc.omen.sealed", WSLang.t("kingdom.omen." + sealed.id())), true);
        KingdomRoleState.syncAll(p.getServer());
    }

    /** Libère le présage scellé : il rejoint les présages choisis pour l'Assaut suivant. Code 85. */
    public static void release(ServerPlayer p) {
        if (!augurInCalm(p)) return;
        if (sealed == null) {
            p.displayClientMessage(WSLang.c("voc.omen.no_sealed"), true);
            return;
        }
        if (CHOSEN.size() >= maxPicks()) unchoose(p, CHOSEN.get(0));
        CHOSEN.add(sealed);
        p.displayClientMessage(WSLang.c("voc.omen.released", WSLang.t("kingdom.omen." + sealed.id())), true);
        sealed = null;
        KingdomTreasury.sync(p.getServer());
        KingdomRoleState.syncAll(p.getServer());
    }

    /** Indicateurs envoyés à l'Augure : 1 relance possible, 2 sceller possible, 4 libérer possible, 8 talent relance, 16 talent scellé, puis (présages max) << 5. */
    public static int flagsFor(ServerPlayer p) {
        if (!KingdomRoles.has(p, KingdomRoles.Role.AUGUR)) return 0;
        boolean calm = KingdomManager.phase() == KingdomManager.Phase.CALM;
        boolean tr = Vocations.has(p, KingdomRoles.Role.AUGUR, "augur.clairvoyance.reroll");
        boolean ts = Vocations.has(p, KingdomRoles.Role.AUGUR, "augur.clairvoyance.sealed_omen");
        int f = 0;
        if (tr && !rerolled && calm) f |= 1;
        if (ts && sealed == null && calm && !CHOSEN.isEmpty() && CHOSEN.get(CHOSEN.size() - 1) != Omen.OFFERING) f |= 2;
        if (sealed != null && calm) f |= 4;
        if (tr) f |= 8;
        if (ts) f |= 16;
        return f | (maxPicks() << 5);
    }
}
