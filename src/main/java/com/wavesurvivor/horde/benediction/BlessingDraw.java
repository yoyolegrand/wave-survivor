package com.wavesurvivor.horde.benediction;

import com.google.gson.JsonObject;
import com.wavesurvivor.horde.editor.HordeJson;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * TIRAGE DES BÉNÉDICTIONS (1.8) : les cartes proposées au joueur.
 *   1. on retient des CATÉGORIES distinctes (hors catégories bannies par le joueur, et dans la famille demandée s'il y en a une) ;
 *   2. pour chacune, on tire sa RARETÉ parmi les niveaux disponibles, avec des poids :
 *        poids de base de la rareté × évolution avec les vagues × effet de la Chance du joueur.
 * Réglages (blessings.json › settings, modifiables dans la couche du joueur) : weights, waveGrowth, luck (scale, maxBonus, shift).
 *
 * La Chance (attribut Luck : bénédictions de Chance, potion, équipement) agit avec des rendements décroissants :
 *   bonus = min(maxBonus, 1 − exp(−Chance / scale)) puis poids × max(0,05 ; 1 + bonus × décalage de la rareté).
 */
public final class BlessingDraw {

    private static final Random RNG = new Random();
    /** Famille des malédictions : bonus avec un malus, rares, jamais choisies par la famille de relance. */
    public static final String CURSE = "curse";

    private BlessingDraw() {}

    /**
     * Tire {@code count} cartes (identifiants de bénédictions) de catégories différentes.
     * @param exclude catégories à ne pas proposer (cartes déjà présentes)
     * @param family  famille demandée ("" = au hasard) ; si elle ne contient rien, elle est ignorée
     */
    public static List<String> draw(ServerPlayer p, int count, int wave, Collection<String> exclude, String family) {
        return draw(p, count, wave, exclude, family, true);
    }

    private static List<String> draw(ServerPlayer p, int count, int wave, Collection<String> exclude, String family, boolean useBans) {
        Map<String, List<RogueUpgrade>> byCat = new LinkedHashMap<>();
        for (RogueUpgrade u : RogueUpgradeRegistry.all()) {
            if (u.maxLevel() > 0 && RogueUpgradeManager.getLevel(p, u.id()) >= u.maxLevel()) continue; // déjà au niveau maximal
            byCat.computeIfAbsent(u.category(), k -> new ArrayList<>()).add(u);
        }
        Set<String> banned = useBans ? RogueUpgradeManager.banned(p) : Set.of();
        boolean wantFamily = family != null && !family.isEmpty();
        List<String> pool = new ArrayList<>();
        for (Map.Entry<String, List<RogueUpgrade>> e : byCat.entrySet()) {
            if (banned.contains(e.getKey()) || exclude.contains(e.getKey())) continue;
            if (CURSE.equals(e.getValue().get(0).family())) continue; // les malédictions ne viennent que par maybeCurse
            if (wantFamily && !family.equals(e.getValue().get(0).family())) continue;
            pool.add(e.getKey());
        }
        // Rien dans la famille demandée, ou tout est banni : on assouplit plutôt que de ne rien proposer
        if (pool.isEmpty() && wantFamily) return draw(p, count, wave, exclude, "", useBans);
        if (pool.isEmpty() && useBans && !banned.isEmpty()) return draw(p, count, wave, exclude, family, false);
        Collections.shuffle(pool, RNG);
        double[] w = weights(p, wave);
        List<String> out = new ArrayList<>();
        for (String cat : pool) {
            if (out.size() >= count) break;
            String id = pickTier(byCat.get(cat), w);
            if (id != null) out.add(id);
        }
        return out;
    }

    private static JsonObject cursesCfg() {
        JsonObject s = BlessingCatalog.settings();
        return s.has("curses") && s.get("curses").isJsonObject() ? s.getAsJsonObject("curses") : new JsonObject();
    }

    /**
     * Malédiction (1.8) : avec une faible probabilité (réglable, 10 % par défaut), l'une des cartes REMPLAÇABLES du menu devient une malédiction
     * (au plus une par menu). {@code ids} est la liste finale des cartes ; {@code replaceable} les positions qu'on peut remplacer.
     */
    public static void maybeCurse(ServerPlayer p, List<String> ids, List<Integer> replaceable, int wave) {
        JsonObject c = cursesCfg();
        if (!HordeJson.bool(c, "enabled", true) || ids.size() < 2 || replaceable.isEmpty()) return;
        if (RNG.nextDouble() >= HordeJson.dbl(c, "chance", 0.10)) return;
        for (String id : ids) {
            RogueUpgrade u = RogueUpgradeRegistry.get(id);
            if (u != null && CURSE.equals(u.family())) return; // déjà une malédiction sur la table
        }
        Set<String> banned = RogueUpgradeManager.banned(p);
        Map<String, List<RogueUpgrade>> byCat = new LinkedHashMap<>();
        for (RogueUpgrade u : RogueUpgradeRegistry.all()) {
            if (!CURSE.equals(u.family()) || banned.contains(u.category())) continue;
            if (u.maxLevel() > 0 && RogueUpgradeManager.getLevel(p, u.id()) >= u.maxLevel()) continue;
            byCat.computeIfAbsent(u.category(), k -> new ArrayList<>()).add(u);
        }
        if (byCat.isEmpty()) return;
        List<String> cats = new ArrayList<>(byCat.keySet());
        String id = pickTier(byCat.get(cats.get(RNG.nextInt(cats.size()))), weights(p, wave));
        if (id != null) ids.set(replaceable.get(RNG.nextInt(replaceable.size())), id);
    }

    /** Choisit un niveau de rareté de cette catégorie, au poids. */
    private static String pickTier(List<RogueUpgrade> tiers, double[] w) {
        double total = 0;
        for (RogueUpgrade u : tiers) total += w[clamp(u.rarity())];
        if (total <= 0) return tiers.isEmpty() ? null : tiers.get(RNG.nextInt(tiers.size())).id();
        double r = RNG.nextDouble() * total, cum = 0;
        for (RogueUpgrade u : tiers) {
            cum += w[clamp(u.rarity())];
            if (r <= cum) return u.id();
        }
        return tiers.get(tiers.size() - 1).id();
    }

    private static int clamp(int i) { return Math.max(0, Math.min(3, i)); }

    /** Poids des quatre raretés (COMMON, RARE, EPIC, LEGENDARY) pour ce joueur à cette vague. */
    public static double[] weights(ServerPlayer p, int wave) {
        JsonObject s = BlessingCatalog.settings();
        JsonObject base = child(s, "weights"), growth = child(s, "waveGrowth");
        JsonObject luck = child(s, "luck"), shift = child(luck, "shift");
        double[] def = {60, 28, 10, 2}, defGrowth = {-0.02, 0.01, 0.04, 0.06}, defShift = {-0.4, 0.15, 0.5, 1.0};
        double bonus = BlessingLuck.on("draw") ? luckBonus(p, HordeJson.dbl(luck, "scale", 12), HordeJson.dbl(luck, "maxBonus", 1.0)) : 0;
        double[] w = new double[4];
        for (int i = 0; i < 4; i++) {
            String k = BlessingCatalog.RARITIES[i];
            double v = HordeJson.dbl(base, k, def[i]);
            v *= Math.max(0.1, 1 + HordeJson.dbl(growth, k, defGrowth[i]) * Math.max(0, wave - 1));
            v *= Math.max(0.05, 1 + bonus * HordeJson.dbl(shift, k, defShift[i]));
            w[i] = Math.max(0, v);
        }
        return w;
    }

    /** Sous-objet de réglage (sans rien créer dans les réglages partagés). */
    private static JsonObject child(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : new JsonObject();
    }

    /** Bonus de Chance du joueur (0 à maxBonus), à rendements décroissants. */
    public static double luckBonus(ServerPlayer p, double scale, double maxBonus) {
        if (p == null) return 0;
        double luck = Math.max(0, p.getAttributeValue(Attributes.LUCK));
        return Math.min(maxBonus, 1 - Math.exp(-luck / Math.max(0.1, scale)));
    }
}
