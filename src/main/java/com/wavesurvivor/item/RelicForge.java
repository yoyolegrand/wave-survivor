package com.wavesurvivor.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FORGE DE L'AUTEL DE RENAISSANCE (1.5) — code COMMUN client / serveur (l'écran affiche exactement ce que le serveur fera).
 * Amélioration : la meilleure copie (niveau < III) monte d'un niveau ; une AUTRE copie de la même relique (la plus
 * faible) est consommée ; coût en PR : 15 pour le niveau II, 30 pour le niveau III.
 */
public final class RelicForge {

    private RelicForge() {}

    /** PR pour atteindre le niveau II puis III. */
    public static int upgradeCost(int fromLevel) {
        return fromLevel <= 1 ? 15 : 30;
    }

    /** Plan d'amélioration : emplacement qui monte, emplacement consommé, niveau de départ, coût. */
    public record Plan(int targetSlot, int consumeSlot, int fromLevel, int cost) {}

    /** Plan d'amélioration pour cette relique dans l'inventaire principal (36 cases), ou null (pas de doublon / d\u00e9j\u00e0 III). */
    public static Plan planUpgrade(List<ItemStack> inv, Item item) {
        if (item instanceof LegendaryRelicItem) return null; // les légendaires n'ont pas de niveau
        int target = -1, tLvl = 0;
        for (int i = 0; i < Math.min(36, inv.size()); i++) {
            ItemStack st = inv.get(i);
            if (!st.is(item)) continue;
            int l = RelicEffects.levelOf(st);
            if (l < 3 && l > tLvl) { target = i; tLvl = l; }
        }
        if (target < 0) return null;
        int consume = -1, cLvl = 99;
        for (int i = 0; i < Math.min(36, inv.size()); i++) {
            if (i == target) continue;
            ItemStack st = inv.get(i);
            if (!st.is(item)) continue;
            int l = RelicEffects.levelOf(st);
            if (l < cLvl) { consume = i; cLvl = l; }
        }
        if (consume < 0) return null;
        return new Plan(target, consume, tLvl, upgradeCost(tLvl));
    }

    /** Reliques présentes dans l'inventaire principal → niveaux de chaque copie (ordre d'apparition). */
    public static Map<Item, List<Integer>> relicsIn(List<ItemStack> inv) {
        Map<Item, List<Integer>> out = new LinkedHashMap<>();
        for (int i = 0; i < Math.min(36, inv.size()); i++) {
            ItemStack st = inv.get(i);
            if (st.isEmpty() || !(st.getItem() instanceof RelicItem)) continue;
            out.computeIfAbsent(st.getItem(), k -> new ArrayList<>()).add(RelicEffects.levelOf(st));
        }
        return out;
    }
}
