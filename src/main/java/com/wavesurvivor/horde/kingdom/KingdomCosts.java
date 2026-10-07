package com.wavesurvivor.horde.kingdom;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * COÛTS DU ROYAUME (partagés serveur + écran de la boutique), payés avec le TRÉSOR COMMUN ({@link KingdomTreasury}) :
 *  - Améliorations des défenses (niv. 2) et SPÉCIALISATIONS au niveau 3 (2 à 3 variantes par défense) ;
 *  - Niveaux de la MAIRIE (le Monolithe) : 1 → 5, chaque niveau agrandit le claim et débloque la suite.
 * Cinq ressources : Monnaie (« ◆ »), Pierre, Bois, Fer et Essence de brèche (niveau 3, variantes, Mairie 4-5).
 */
public final class KingdomCosts {

    private KingdomCosts() {}

    /** Une ligne de coût : ressource du trésor + quantité. */
    public record Cost(KingdomTreasury.Res res, int count) {
        /** Monnaie de la horde. */
        public boolean isCurrency() { return res == KingdomTreasury.Res.MONEY; }

        /** Objet affiché en icône. */
        public Item icon() {
            return switch (res) {
                case MONEY -> Items.EMERALD;
                case STONE -> Items.COBBLESTONE;
                case WOOD -> Items.OAK_LOG;
                case IRON -> Items.IRON_INGOT;
                case ESSENCE -> Items.AMETHYST_SHARD;
            };
        }

        /** Nom affiché. */
        public Component label() {
            return com.wavesurvivor.i18n.WSLang.c("kingdom.res." + res.key);
        }
    }

    private static Cost money(int n) { return new Cost(KingdomTreasury.Res.MONEY, n); }
    private static Cost stone(int n) { return new Cost(KingdomTreasury.Res.STONE, n); }
    private static Cost wood(int n) { return new Cost(KingdomTreasury.Res.WOOD, n); }
    private static Cost iron(int n) { return new Cost(KingdomTreasury.Res.IRON, n); }
    private static Cost essence(int n) { return new Cost(KingdomTreasury.Res.ESSENCE, n); }

    // ─── Défenses ───

    /** Spécialisations proposées au niveau 3, par défense. */
    public static String[] variants(DefenseBlock.Kind k) {
        return switch (k) {
            case ARCHER -> new String[]{"fire", "frost", "explosive"};
            case MAGE -> new String[]{"glacier", "storm", "curse"};
            case SHRINE -> new String[]{"life", "bulwark", "blessing"};
            case BARRACKS -> new String[]{"guards", "berserkers", "paladins"};
            case COLLECTOR -> new String[]{"magnet", "smelter", "treasury"};
            case WORKSHOP -> new String[]{"forge", "armory", "foundry"};
        };
    }

    /** Passage au niveau 2. */
    public static List<Cost> upgradeToLevel2(DefenseBlock.Kind k) {
        return switch (k) {
            case ARCHER -> List.of(money(48), wood(24), iron(8));
            case MAGE -> List.of(money(48), stone(24), iron(8));
            case SHRINE -> List.of(money(48), stone(16), iron(8));
            case BARRACKS -> List.of(money(48), wood(32), iron(8));
            case COLLECTOR -> List.of(money(48), wood(24), iron(8));
            case WORKSHOP -> List.of(money(48), stone(24), iron(10));
        };
    }

    /** Passage au niveau 3 avec une spécialisation (même prix pour toutes : Essence de brèche). */
    public static List<Cost> variantCost(DefenseBlock.Kind k, String v) {
        List<Cost> out = new ArrayList<>();
        out.add(money(64));
        out.add(stone(32));
        out.add(essence(10));
        return out;
    }

    // ─── Mairie (Monolithe) ───

    public static final int TOWN_MAX = 5;

    /** Coût pour passer la Mairie au niveau {@code to} (2 à 5). */
    public static List<Cost> townHall(int to) {
        return switch (to) {
            case 2 -> List.of(money(24), wood(64), stone(64));
            case 3 -> List.of(money(40), wood(96), stone(128), iron(24));
            case 4 -> List.of(money(56), wood(128), iron(48), essence(20));
            case 5 -> List.of(money(80), stone(192), iron(48), essence(40));
            default -> List.of();
        };
    }

    /** Agrandissement du claim par niveau de Mairie (au-delà du niveau 1). */
    public static final int CLAIM_PER_TIER = 4;

    // ─── Trésor ───
    // (le paramètre « currency » est conservé pour la compatibilité des appels ; il n'est plus utilisé)

    /** Quantité disponible dans le trésor (copie client côté écran, trésor réel côté serveur). */
    public static int have(Player p, Cost cost, Item currency) {
        int i = cost.res().ordinal();
        return p != null && p.level().isClientSide ? KingdomTreasury.CLIENT[i] : KingdomTreasury.get(cost.res());
    }

    /** Vrai si le trésor couvre tout (créatif : toujours). */
    public static boolean canAfford(Player p, List<Cost> costs, Item currency) {
        if (p != null && p.isCreative()) return true;
        for (Cost c : costs) if (have(p, c, currency) < c.count()) return false;
        return true;
    }

    /** Retire les ressources du trésor (à appeler après canAfford). */
    public static void take(Player p, List<Cost> costs, Item currency) {
        if (p != null && p.isCreative()) return;
        for (Cost c : costs) KingdomTreasury.spend(c.res(), c.count());
        if (p != null) KingdomTreasury.sync(p.getServer());
    }

    /** Liste lisible des ressources manquantes : « 12× Bois, 4× Fer ». */
    public static Component missing(Player p, List<Cost> costs, Item currency) {
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        boolean first = true;
        for (Cost c : costs) {
            int miss = c.count() - have(p, c, currency);
            if (miss <= 0) continue;
            if (!first) out.append(Component.literal("§7, "));
            out.append(Component.literal("§c" + miss + "× §f")).append(c.label());
            first = false;
        }
        return out;
    }
}
