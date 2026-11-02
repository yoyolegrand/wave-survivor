package com.wavesurvivor.horde.kingdom.vocation;

import com.wavesurvivor.horde.kingdom.KingdomManager;
import com.wavesurvivor.horde.kingdom.KingdomRoles;
import net.minecraft.server.level.ServerPlayer;

/**
 * VOCATIONS — accès rapide aux effets des talents depuis les mécaniques des rôles.
 * Beaucoup de mécaniques n'ont pas de joueur sous la main (« un Bâtisseur est là ») : on prend alors celui qui tient le rôle.
 * Tout vaut 0 hors d'une partie Kingdom ou sans titulaire du rôle.
 */
public final class Voc {

    private Voc() {}

    /** Joueur connecté qui tient ce rôle (null = personne, ou pas de partie Kingdom). */
    public static ServerPlayer holder(KingdomRoles.Role r) {
        if (!KingdomRoles.active()) return null;
        var lvl = KingdomManager.level();
        return lvl == null ? null : KingdomRoles.holder(lvl.getServer(), r);
    }

    /** Total appliqué d'un talent (rang × valeur par rang) pour le titulaire du rôle. */
    public static double total(KingdomRoles.Role r, String effect) {
        ServerPlayer p = holder(r);
        return p == null ? 0 : Vocations.total(p, r, effect);
    }

    /** Total en fraction (valeurs en % : 15 → 0,15). */
    public static double pct(KingdomRoles.Role r, String effect) { return total(r, effect) / 100.0; }

    public static boolean has(KingdomRoles.Role r, String effect) { return total(r, effect) > 0; }

    /** Effet de base du rôle (vocations.json › roles.<rôle>.base.<clé>). */
    public static double base(KingdomRoles.Role r, String key, double def) { return VocationsConfig.baseValue(r.id, key, def); }
}
