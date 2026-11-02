package com.wavesurvivor.horde.kingdom.vocation;

import com.wavesurvivor.horde.kingdom.KingdomRoles;
import com.wavesurvivor.horde.kingdom.vocation.VocationsConfig.Branch;
import com.wavesurvivor.horde.kingdom.vocation.VocationsConfig.RoleTree;
import com.wavesurvivor.horde.kingdom.vocation.VocationsConfig.Talent;
import com.wavesurvivor.horde.kingdom.vocation.VocationsConfig.Ultimate;
import com.wavesurvivor.horde.renaissance.Heritage;
import com.wavesurvivor.horde.renaissance.RenaissanceStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * VOCATIONS (1.9) : progression de chaque rôle du Kingdom, par joueur.
 *  - Maîtrise : XP cumulée → niveau 0 à 20 (courbe dans vocations.json), 1 point de talent par niveau ;
 *  - arbre : 3 branches de 4 talents (paliers de points par branche) + un talent ultime au choix (15 points + 150 PR) ;
 *  - réinitialisation payée en PR ; modification des talents uniquement hors partie.
 * Données : dans les données persistantes du joueur, au même endroit que les PR et l'Héritage (conservées à la mort et
 * à la Renaissance) : PlayerPersisted.ws_vocations.<rôle>.{xp, t.<talent> = rang, ult, paid}.
 * Les écrans (étape 4) liront ces données synchronisées ; ici tout est côté serveur.
 */
public final class Vocations {

    private Vocations() {}

    /** Résultat d'une action sur l'arbre. */
    public enum Result { OK, UNKNOWN, IN_GAME, NO_POINTS, MAX_RANK, LOCKED, NOT_ENOUGH_PR, ULT_LOCKED, ULT_ALREADY, NOTHING, NOT_READY }

    /** Données brutes du joueur (lecture : synchronisation vers le client). */
    static CompoundTag root(Player p) {
        CompoundTag persistentRoot = p.getPersistentData();
        CompoundTag persisted = persistentRoot.getCompound("PlayerPersisted");
        CompoundTag d = persisted.getCompound("ws_vocations");
        persisted.put("ws_vocations", d);
        persistentRoot.put("PlayerPersisted", persisted);
        return d;
    }

    private static CompoundTag role(Player p, KingdomRoles.Role r) {
        CompoundTag root = root(p);
        CompoundTag t = root.getCompound(r.id);
        root.put(r.id, t);
        return t;
    }

    /** Nom du rôle sans l'émoji de tête (la police du jeu ne l'affiche pas). */
    public static String roleName(KingdomRoles.Role r) {
        return com.wavesurvivor.i18n.WSLang.t("kingdom.role." + r.id).replaceFirst("^[^\\p{L}]+", "");
    }

    // ─── Maîtrise ───

    public static long xp(Player p, KingdomRoles.Role r) { return role(p, r).getLong("xp"); }

    public static int level(Player p, KingdomRoles.Role r) { return VocationsConfig.levelForXp(xp(p, r)); }

    /** XP gagnée dans le niveau en cours. */
    public static long xpIntoLevel(Player p, KingdomRoles.Role r) {
        return xp(p, r) - VocationsConfig.xpToReach(level(p, r));
    }

    /** XP à gagner pour le niveau suivant (0 au niveau maximum). */
    public static int xpForNext(Player p, KingdomRoles.Role r) {
        int l = level(p, r);
        return l >= VocationsConfig.maxLevel() ? 0 : VocationsConfig.xpForLevel(l + 1);
    }

    /** Ajoute de l'XP de Maîtrise ; renvoie le nombre de niveaux gagnés (les annonces sont faites par l'appelant). */
    public static int addXp(Player p, KingdomRoles.Role r, long amount) {
        if (amount <= 0 || VocationsConfig.tree(r.id) == null) return 0;
        int before = level(p, r);
        CompoundTag t = role(p, r);
        long cap = VocationsConfig.xpToReach(VocationsConfig.maxLevel());
        t.putLong("xp", Math.min(cap, t.getLong("xp") + amount));
        return level(p, r) - before;
    }

    // ─── Points de talent ───

    public static int pointsTotal(Player p, KingdomRoles.Role r) { return level(p, r) * VocationsConfig.pointsPerLevel(); }

    public static int pointsSpent(Player p, KingdomRoles.Role r) {
        int n = 0;
        CompoundTag t = role(p, r).getCompound("t");
        for (String k : t.getAllKeys()) n += t.getInt(k);
        return n;
    }

    public static int pointsFree(Player p, KingdomRoles.Role r) { return Math.max(0, pointsTotal(p, r) - pointsSpent(p, r)); }

    /** Rang acquis dans un talent (0 si aucun). */
    public static int rank(Player p, KingdomRoles.Role r, String talentId) { return role(p, r).getCompound("t").getInt(talentId); }

    /** Points dépensés dans une branche. */
    public static int branchPoints(Player p, KingdomRoles.Role r, String branchId) {
        RoleTree tree = VocationsConfig.tree(r.id);
        if (tree == null) return 0;
        int n = 0;
        for (Branch b : tree.branches()) {
            if (!b.id().equals(branchId)) continue;
            for (Talent t : b.talents()) n += rank(p, r, t.id());
        }
        return n;
    }

    /** Points qu'il faut avoir dépensés dans sa branche pour pouvoir prendre ce talent (0 si inconnu). */
    public static int requiredBranchPoints(RoleTree tree, String talentId) {
        for (Branch b : tree.branches()) {
            for (int i = 0; i < b.talents().size(); i++) if (b.talents().get(i).id().equals(talentId)) return VocationsConfig.branchTier(i);
        }
        return 0;
    }

    // ─── Talent ultime ───

    /** Identifiant de l'ultime choisi ("" si aucun). */
    public static String ultimate(Player p, KingdomRoles.Role r) { return role(p, r).getString("ult"); }

    /** Les PR de l'ultime ont-ils déjà été payés pour ce rôle (le re-choix après une réinitialisation est alors gratuit) ? */
    public static boolean ultimatePaid(Player p, KingdomRoles.Role r) { return role(p, r).getBoolean("paid"); }

    // ─── Actions ───

    public static boolean inGame() {
        return com.wavesurvivor.altar.AltarDefense.isActive() || com.wavesurvivor.horde.kingdom.KingdomManager.isActive();
    }

    /** +1 rang dans un talent si la partie est finie, qu'il reste des points et que son palier est atteint. */
    public static Result buyTalent(ServerPlayer p, KingdomRoles.Role r, String talentId) {
        RoleTree tree = VocationsConfig.tree(r.id);
        if (tree == null) return Result.UNKNOWN;
        Branch branch = tree.branchOf(talentId);
        Talent talent = tree.talent(talentId);
        if (branch == null || talent == null) return Result.UNKNOWN;
        if (talent.wip()) return Result.NOT_READY;
        if (inGame()) return Result.IN_GAME;
        int rank = rank(p, r, talent.id());
        if (rank >= talent.ranks()) return Result.MAX_RANK;
        if (pointsFree(p, r) <= 0) return Result.NO_POINTS;
        if (branchPoints(p, r, branch.id()) < requiredBranchPoints(tree, talent.id())) return Result.LOCKED;
        CompoundTag t = role(p, r).getCompound("t");
        t.putInt(talent.id(), rank + 1);
        role(p, r).put("t", t);
        return Result.OK;
    }

    /** Choisit le talent ultime (un seul par rôle) : 15 points dépensés dans l'arbre, puis 150 PR la première fois. */
    public static Result chooseUltimate(ServerPlayer p, KingdomRoles.Role r, String ultimateId) {
        RoleTree tree = VocationsConfig.tree(r.id);
        Ultimate u = tree == null ? null : tree.ultimate(ultimateId);
        if (u == null) return Result.UNKNOWN;
        if (u.wip()) return Result.NOT_READY;
        if (inGame()) return Result.IN_GAME;
        if (!ultimate(p, r).isEmpty()) return Result.ULT_ALREADY;
        if (pointsSpent(p, r) < VocationsConfig.ultimatePoints()) return Result.ULT_LOCKED;
        CompoundTag t = role(p, r);
        if (!t.getBoolean("paid")) {
            int cost = VocationsConfig.ultimateCostPR();
            if (RenaissanceStore.getPoints(p) < cost) return Result.NOT_ENOUGH_PR;
            RenaissanceStore.addPoints(p, -cost);
            Heritage.addInvested(p, cost); // toutes les dépenses de PR comptent pour la Renaissance
            t.putBoolean("paid", true);
        }
        t.putString("ult", u.id());
        return Result.OK;
    }

    /** Rend tous les points du rôle contre des PR ; l'ultime déjà payé reste acquis mais doit être re-choisi. */
    public static Result reset(ServerPlayer p, KingdomRoles.Role r) {
        if (VocationsConfig.tree(r.id) == null) return Result.UNKNOWN;
        if (inGame()) return Result.IN_GAME;
        CompoundTag t = role(p, r);
        if (pointsSpent(p, r) == 0 && t.getString("ult").isEmpty()) return Result.NOTHING;
        int cost = VocationsConfig.resetCostPR();
        if (RenaissanceStore.getPoints(p) < cost) return Result.NOT_ENOUGH_PR;
        RenaissanceStore.addPoints(p, -cost);
        Heritage.addInvested(p, cost);
        t.remove("t");
        t.remove("ult");
        return Result.OK;
    }

    // ─── Lecture des effets (étape 3 : lu par les effets des rôles) ───

    /** Total appliqué d'un effet de talent pour un joueur qui tient ce rôle : rang × valeur par rang (0 si non pris). */
    public static double total(Player p, KingdomRoles.Role r, String effect) {
        RoleTree tree = VocationsConfig.tree(r.id);
        if (tree == null) return 0;
        for (Branch b : tree.branches()) {
            for (Talent t : b.talents()) {
                if (t.effect().equals(effect)) return rank(p, r, t.id()) * t.value();
            }
        }
        String ult = ultimate(p, r);
        if (!ult.isEmpty()) {
            Ultimate u = tree.ultimate(ult);
            if (u != null && u.effect().equals(effect)) return u.value();
        }
        return 0;
    }

    public static boolean has(Player p, KingdomRoles.Role r, String effect) { return total(p, r, effect) > 0; }
}
