package com.wavesurvivor.horde.renaissance;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.ModConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Config Renaissance v2 (lue depuis ModConfig.renaissanceConfig[0]).
 * Code COMMUN client/serveur : aucune classe client ici.
 *
 * Format JSON :
 *   raritiesConfig[] : { name, tag, value?, ratio? (legacy), color, customItems[] }
 *   skillPointsEnabled, skillPointCost, skillPointCommand ("{player}", "{amount}")
 *   shopItems[] : { id, item, count, cost, maxPerPlayer (0 = illimité), displayName, lore[], nbt (SNBT), description }
 *   noItemsMessage
 */
public class RenaissanceConfigData {

    /** Tag NBT posé sur les reliques achetées : elles ne peuvent pas être re-sacrifiées. */
    public static final String RELIC_TAG = "ws_relic";
    /** ID virtuel de l'entrée "points de compétence" dans la boutique. */
    public static final String SKILL_POINT_ID = "__skillpoint__";

    public List<Rarity> raritiesConfig = new ArrayList<>();
    public String noItemsMessage;
    public boolean skillPointsEnabled = true;
    public int skillPointCost = 4;
    public String skillPointCommand = "skilltree points add {player} {amount}";
    public List<ShopItem> shopItems = new ArrayList<>();
    /** "auto" = stats visibles seulement si le Skill Tree n'est PAS dispo ; "always" ; "never". */
    public String statsMode = "auto";
    public List<StatUpgrade> statUpgrades;
    /** Raretés réglées en jeu (RenaissanceRarityStore) : itemId → nom de rareté ou "__none__" (exclu). Prioritaires. */
    public Map<String, String> rarityOverrides;

    public static class Rarity {
        public String name;
        public String tag;
        public Integer value;   // PR par item (prioritaire)
        public Integer ratio;   // legacy KubeJS : N items = 1 point → converti en value
        public String color;
        public List<String> customItems;

        public int effectiveValue() {
            if (value != null && value > 0) return value;
            if (ratio != null && ratio > 0) return Math.max(1, Math.round(4f / ratio));
            return 1;
        }

        /** Vrai si la rareté fonctionne au ratio « N items = 1 PR » (et pas à la valeur par item). */
        public boolean usesRatio() {
            return (value == null || value <= 0) && ratio != null && ratio > 0;
        }

        /** PR rapportés par {@code count} items de cette rareté. */
        public int pointsFor(int count) {
            if (count <= 0) return 0;
            return usesRatio() ? count / ratio : count * effectiveValue();
        }

        /** Items réellement consommés pour {@code count} items offerts (le reste d'un ratio incomplet est rendu). */
        public int consumedFor(int count) {
            if (count <= 0) return 0;
            return usesRatio() ? (count / ratio) * ratio : count;
        }

        /** Texte du taux : « 4 items = 1 PR », « 1 PR / item »… */
        public String rateLabel() {
            if (usesRatio()) return ratio == 1 ? "1 PR / item" : ratio + " items = 1 PR";
            return effectiveValue() + " PR / item";
        }

        /** Code couleur § selon la classe tailwind base44 (bg-blue-600 → §9, etc.). */
        public String chatColor() {
            String c = color != null ? color.toLowerCase() : "";
            if (c.contains("blue")) return "§9";
            if (c.contains("purple") || c.contains("violet")) return "§d";
            if (c.contains("yellow") || c.contains("amber")) return "§6";
            if (c.contains("green")) return "§a";
            if (c.contains("red")) return "§c";
            if (c.contains("orange")) return "§6";
            return "§f";
        }

        /** Couleur ARGB pour les bordures GUI. */
        public int argb() {
            String c = color != null ? color.toLowerCase() : "";
            if (c.contains("blue")) return 0xFF3B82F6;
            if (c.contains("purple") || c.contains("violet")) return 0xFFA855F7;
            if (c.contains("yellow") || c.contains("amber")) return 0xFFEAB308;
            if (c.contains("green")) return 0xFF22C55E;
            if (c.contains("red")) return 0xFFEF4444;
            if (c.contains("orange")) return 0xFFF97316;
            return 0xFFAAAAAA;
        }
    }

    public static class ShopItem {
        public String id;
        public String item;
        public int count = 1;
        public int cost = 1;
        public int maxPerPlayer = 0;
        public String displayName;
        public List<String> lore;
        public String nbt;
        public String description;
    }

    private static final Gson GSON = new Gson();

    /**
     * Stat permanente achetable (attribut vanilla, cumulable par niveaux).
     * Coût du niveau suivant = cost + costIncrease × niveauActuel.
     */
    public static class StatUpgrade {
        public String id;
        public String name;
        public String attribute;          // "minecraft:generic.max_health" ou alias "max_health"
        public double amount;             // par niveau
        public String operation = "addition"; // addition | multiply_base | multiply_total
        public int cost = 3;
        public int costIncrease = 1;
        public int maxLevel = 10;
        public String icon;               // item id pour l'icône
        public String description;

        public int costFor(int currentLevel) {
            return Math.max(0, cost + costIncrease * currentLevel);
        }

        public AttributeModifier.Operation op() {
            String o = operation != null ? operation.toLowerCase() : "";
            if (o.contains("total")) return AttributeModifier.Operation.MULTIPLY_TOTAL;
            if (o.contains("mult") || o.contains("base")) return AttributeModifier.Operation.MULTIPLY_BASE;
            return AttributeModifier.Operation.ADDITION;
        }

        public Attribute resolveAttribute() {
            if (attribute == null || attribute.isBlank()) return null;
            String a = attribute.trim().toLowerCase();
            if (!a.contains(":")) a = "minecraft:" + (a.startsWith("generic.") ? a : "generic." + a);
            try { return BuiltInRegistries.ATTRIBUTE.get(new ResourceLocation(a)); }
            catch (Exception e) { return null; }
        }

        public ItemStack iconStack() {
            if (icon != null && !icon.isBlank()) {
                try {
                    Item it = BuiltInRegistries.ITEM.get(new ResourceLocation(icon));
                    if (it != null && it != Items.AIR) return new ItemStack(it);
                } catch (Exception ignored) {}
            }
            return new ItemStack(Items.NETHER_STAR);
        }

        /** Texte lisible du bonus total pour un niveau donné (ex "+6" ou "+15%"). */
        public String formatTotal(int level) {
            double v = amount * level;
            if (op() != AttributeModifier.Operation.ADDITION) return "+" + trim(v * 100) + "%";
            return "+" + trim(v);
        }

        private static String trim(double v) {
            if (Math.abs(v - Math.round(v)) < 1e-6) return String.valueOf(Math.round(v));
            return String.format(java.util.Locale.ROOT, "%.2f", v).replaceAll("0+$", "");
        }
    }

    private static StatUpgrade stat(String id, String name, String attr, double amount, String op,
                                    int cost, int inc, int max, String icon, String desc) {
        StatUpgrade s = new StatUpgrade();
        s.id = id; s.name = name; s.attribute = attr; s.amount = amount; s.operation = op;
        s.cost = cost; s.costIncrease = inc; s.maxLevel = max; s.icon = icon; s.description = desc;
        return s;
    }

    /** Stats par défaut si la config n'en définit pas (mêmes attributs que les bénédictions). */
    private static List<StatUpgrade> defaultStats() {
        List<StatUpgrade> l = new ArrayList<>();
        l.add(stat("hp", "§c❤ Vitalité", "max_health", 2, "addition", 3, 1, 10,
                "minecraft:golden_apple", "+1 cœur max par niveau"));
        l.add(stat("damage", "§6⚔ Force", "attack_damage", 1, "addition", 4, 2, 10,
                "minecraft:iron_sword", "+1 dégât d'attaque par niveau"));
        l.add(stat("speed", "§bCélérité", "movement_speed", 0.05, "multiply_base", 3, 1, 10,
                "minecraft:sugar", "+5% vitesse de déplacement par niveau"));
        l.add(stat("attack_speed", "§eFrappe rapide", "attack_speed", 0.1, "addition", 3, 1, 10,
                "minecraft:feather", "+0.1 vitesse d'attaque par niveau"));
        l.add(stat("armor", "§7Carapace", "armor", 1, "addition", 3, 1, 10,
                "minecraft:iron_chestplate", "+1 point d'armure par niveau"));
        l.add(stat("toughness", "§3Robustesse", "armor_toughness", 1, "addition", 4, 2, 5,
                "minecraft:diamond_chestplate", "+1 robustesse d'armure par niveau"));
        l.add(stat("knockback", "§8Ancrage", "knockback_resistance", 0.05, "addition", 3, 1, 10,
                "minecraft:shield", "+5% résistance au recul par niveau"));
        l.add(stat("luck", "§aFortune", "luck", 1, "addition", 2, 1, 10,
                "minecraft:rabbit_foot", "+1 chance par niveau"));
        return l;
    }

    public StatUpgrade findStat(String id) {
        if (id == null) return null;
        for (StatUpgrade s : statUpgrades) if (id.equals(s.id)) return s;
        return null;
    }

    // ─── Chargement ───

    public static RenaissanceConfigData current() {
        RenaissanceConfigData data = fromModConfig(WaveSurvivorMod.getConfig());
        RenaissanceShopStore.applyTo(data); // reliques ajoutées/masquées en jeu
        RenaissanceRarityStore.applyTo(data); // raretés réglées en jeu
        return data;
    }

    public static RenaissanceConfigData fromModConfig(ModConfig cfg) {
        RenaissanceConfigData data = null;
        try {
            if (cfg != null && cfg.renaissanceConfig != null && !cfg.renaissanceConfig.isEmpty()) {
                Map<String, Object> raw = cfg.renaissanceConfig.get(0);
                JsonElement tree = GSON.toJsonTree(raw);
                data = GSON.fromJson(tree, RenaissanceConfigData.class);
            }
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[Renaissance] Parse config échoué : {}", e.getMessage());
        }
        if (data == null) data = new RenaissanceConfigData();
        data.normalize();
        return data;
    }

    public static RenaissanceConfigData fromJson(String json) {
        RenaissanceConfigData data = null;
        try { data = GSON.fromJson(json, RenaissanceConfigData.class); } catch (Exception ignored) {}
        if (data == null) data = new RenaissanceConfigData();
        data.normalize();
        return data;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    private void normalize() {
        if (raritiesConfig == null) raritiesConfig = new ArrayList<>();
        if (shopItems == null) shopItems = new ArrayList<>();
        if (statUpgrades == null || statUpgrades.isEmpty()) statUpgrades = defaultStats();
        if (statsMode == null || statsMode.isBlank()) statsMode = "auto";
        for (int i = 0; i < statUpgrades.size(); i++) {
            StatUpgrade s = statUpgrades.get(i);
            if (s.id == null || s.id.isBlank()) s.id = "stat_" + i;
            if (s.maxLevel <= 0) s.maxLevel = 10;
            if (s.name == null || s.name.isBlank()) s.name = s.id;
        }
        if (skillPointCost <= 0) skillPointCost = 4;
        if (skillPointCommand == null || skillPointCommand.isBlank())
            skillPointCommand = "skilltree points add {player} {amount}";
        // IDs de boutique manquants : on en génère un stable depuis l'item + index
        for (int i = 0; i < shopItems.size(); i++) {
            ShopItem s = shopItems.get(i);
            if (s.id == null || s.id.isBlank()) s.id = (s.item != null ? s.item.replace(':', '_') : "item") + "_" + i;
            if (s.count <= 0) s.count = 1;
            if (s.cost < 0) s.cost = 0;
        }
    }

    // ─── Raretés ───

    /** Rareté d'un stack, ou null si non sacrifiable (reliques exclues). */
    public Rarity rarityOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (stack.hasTag() && stack.getTag().getBoolean(RELIC_TAG)) return null;
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String itemId = key != null ? key.toString() : "";

        // Réglage en jeu (op) : prioritaire sur tags et listes
        if (rarityOverrides != null && rarityOverrides.containsKey(itemId)) {
            String o = rarityOverrides.get(itemId);
            if (RenaissanceRarityStore.EXCLUDED.equals(o)) return null;
            Rarity forced = findRarity(o);
            if (forced != null) return forced;
        }

        for (Rarity r : raritiesConfig) {
            if (r.customItems != null && r.customItems.contains(itemId)) return r;
            if (r.tag != null && !r.tag.isBlank() && hasMatchingTag(stack, r.tag)) return r;
        }
        return null;
    }

    public int valueOf(ItemStack stack) {
        Rarity r = rarityOf(stack);
        return r == null ? 0 : r.effectiveValue();
    }

    public Rarity findRarity(String name) {
        if (name == null) return null;
        for (Rarity r : raritiesConfig) if (r.name != null && r.name.equalsIgnoreCase(name)) return r;
        return null;
    }

    /** Rareté SANS les réglages en jeu (pour afficher "Défaut : …" dans l'écran de configuration). */
    public Rarity baseRarityOf(ItemStack stack) {
        Map<String, String> saved = rarityOverrides;
        rarityOverrides = null;
        try { return rarityOf(stack); } finally { rarityOverrides = saved; }
    }

    /**
     * Match strict : "rare" matche les tags "xxx:rare" ou "xxx:rarity/rare",
     * mais PAS "xxx:rare_earth" (le script KubeJS faisait un includes() trop large).
     * Si le tag contient ':', on compare l'ID complet.
     */
    private static boolean hasMatchingTag(ItemStack stack, String wanted) {
        String w = wanted.toLowerCase();
        Iterator<TagKey<Item>> it = stack.getTags().iterator();
        while (it.hasNext()) {
            ResourceLocation loc = it.next().location();
            if (w.contains(":")) {
                if (loc.toString().equals(w)) return true;
                continue;
            }
            String path = loc.getPath();
            if (path.equals(w)) return true;
            for (String seg : path.split("/")) {
                if (seg.equals(w)) return true;
            }
        }
        return false;
    }

    // ─── Boutique ───

    public ShopItem findShop(String id) {
        if (id == null) return null;
        for (ShopItem s : shopItems) if (id.equals(s.id)) return s;
        return null;
    }

    /** Construit l'ItemStack d'une relique (nom, lore, NBT, marqueur relique). EMPTY si item invalide. */
    public static ItemStack buildStack(ShopItem e) {
        if (e == null || e.item == null || e.item.isBlank()) return ItemStack.EMPTY;
        Item item;
        try { item = BuiltInRegistries.ITEM.get(new ResourceLocation(e.item)); }
        catch (Exception ex) { return ItemStack.EMPTY; }
        if (item == null || item == Items.AIR) return ItemStack.EMPTY;

        ItemStack st = new ItemStack(item, Math.max(1, e.count));
        if (e.nbt != null && !e.nbt.isBlank()) {
            try {
                CompoundTag parsed = TagParser.parseTag(e.nbt);
                st.getOrCreateTag().merge(parsed);
            } catch (Exception ex) {
                WaveSurvivorMod.LOGGER.warn("[Renaissance] NBT invalide pour '{}' : {}", e.id, ex.getMessage());
            }
        }
        if (e.displayName != null && !e.displayName.isBlank()) {
            st.setHoverName(Component.literal(e.displayName).withStyle(s -> s.withItalic(false)));
        }
        if (e.lore != null && !e.lore.isEmpty()) {
            CompoundTag display = st.getOrCreateTagElement("display");
            ListTag lore = new ListTag();
            for (String line : e.lore) {
                lore.add(StringTag.valueOf(Component.Serializer.toJson(
                        Component.literal(line).withStyle(s -> s.withItalic(false)))));
            }
            display.put("Lore", lore);
        }
        st.getOrCreateTag().putBoolean(RELIC_TAG, true);
        return st;
    }
}
