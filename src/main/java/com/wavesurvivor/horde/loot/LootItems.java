package com.wavesurvivor.horde.loot;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.horde.roulette.RouletteChestConfig;
import com.wavesurvivor.horde.roulette.RouletteChestManager;
import com.wavesurvivor.horde.roulette.RouletteChestRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Résolveur UNIQUE "id d'item de config → ItemStack", utilisé par tous les systèmes qui donnent des items :
 * loot des mobs / entités custom / montures / boss, récompenses de fin de horde, trades des marchands.
 *
 * Gère les pseudo-items hérités de KubeJS :
 *   roulettechest:key_<configKey>  → vraie clé de roulette chest (name_tag + NBT)
 * avec résolution tolérante des anciens noms (key_coffre_magic → coffre_mysterieux_magic, key_coffreender → coffre_ender).
 */
public final class LootItems {

    private static final String KEY_PREFIX = "roulettechest:key_";
    /** Évite de spammer les logs pour un même id cassé. */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private static final Map<String, String> KEY_CACHE = new ConcurrentHashMap<>();

    private LootItems() {}

    /** @return l'ItemStack (qty ≥ 1), ou ItemStack.EMPTY si l'id est inconnu. */
    /** Identifiant d'un objet pour la config : potion ("minecraft:potion#minecraft:fire_resistance")
     *  et DONNÉES de l'objet (enchantements, nom, livre enchanté…) au format SNBT :
     *  "minecraft:diamond_sword{Enchantments:[{id:\"minecraft:sharpness\",lvl:5s}]}". */
    public static String idOf(ItemStack st) {
        if (st == null || st.isEmpty()) return "";
        ResourceLocation k = BuiltInRegistries.ITEM.getKey(st.getItem());
        String id = k != null ? k.toString() : "";
        boolean potionEncoded = false;
        var potion = net.minecraft.world.item.alchemy.PotionUtils.getPotion(st);
        if (potion != null && potion != net.minecraft.world.item.alchemy.Potions.EMPTY) {
            ResourceLocation pk = BuiltInRegistries.POTION.getKey(potion);
            if (pk != null) { id += "#" + pk; potionEncoded = true; }
        }
        if (st.hasTag()) {
            net.minecraft.nbt.CompoundTag tag = st.getTag().copy();
            if (potionEncoded) tag.remove("Potion");
            if (tag.contains("Damage") && tag.getInt("Damage") == 0) tag.remove("Damage");
            if (!tag.isEmpty()) id += tag.toString();
        }
        return id;
    }

    public static ItemStack resolve(String id, int qty) {
        if (id == null || id.isBlank()) return ItemStack.EMPTY;
        String raw = id.trim();
        int count = Math.max(1, qty);

        // ── Données de l'objet (SNBT) : "minecraft:diamond_sword{Enchantments:[...]}" ──
        int brace = raw.indexOf('{');
        if (brace > 0 && raw.endsWith("}")) {
            ItemStack base = resolve(raw.substring(0, brace), count);
            if (base.isEmpty()) return base;
            try {
                net.minecraft.nbt.CompoundTag extra = net.minecraft.nbt.TagParser.parseTag(raw.substring(brace));
                base.getOrCreateTag().merge(extra);
            } catch (Exception e) {
                warnOnce(raw, "données d'objet (NBT) illisibles");
            }
            return base;
        }

        // ── Clés de roulette chest (pseudo-items KubeJS) ──
        if (raw.startsWith(KEY_PREFIX)) {
            RouletteChestConfig cfg = findChestConfig(raw.substring(KEY_PREFIX.length()));
            if (cfg == null) {
                warnOnce(raw, "clé de coffre inconnue");
                return ItemStack.EMPTY;
            }
            ItemStack key = RouletteChestManager.createKeyItem(cfg);
            key.setCount(Math.min(count, key.getMaxStackSize()));
            return key;
        }

        // ── Potions précises : "minecraft:potion#minecraft:fire_resistance" (aussi splash_potion, lingering_potion, tipped_arrow) ──
        int hash = raw.indexOf('#');
        if (hash > 0) {
            ItemStack base = resolve(raw.substring(0, hash), count);
            if (base.isEmpty()) return base;
            try {
                var potion = BuiltInRegistries.POTION.get(new ResourceLocation(raw.substring(hash + 1)));
                if (potion != null) net.minecraft.world.item.alchemy.PotionUtils.setPotion(base, potion);
            } catch (Exception e) {
                warnOnce(raw, "potion inconnue");
            }
            return base;
        }

        // ── Item normal ──
        try {
            Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(raw));
            if (item == null || item == Items.AIR) {
                warnOnce(raw, "item introuvable");
                return ItemStack.EMPTY;
            }
            return new ItemStack(item, count);
        } catch (Exception e) {
            warnOnce(raw, "id invalide");
            return ItemStack.EMPTY;
        }
    }

    /**
     * Trouve la config de coffre d'une clé : clé exacte, puis même clé sans "_",
     * puis correspondance par mots (key_coffre_magic ⊂ coffre_mysterieux_magic), si unique.
     */
    public static RouletteChestConfig findChestConfig(String wanted) {
        if (wanted == null || wanted.isBlank()) return null;
        RouletteChestConfig exact = RouletteChestRegistry.get(wanted);
        if (exact != null) return exact;

        String cached = KEY_CACHE.get(wanted);
        if (cached != null) return RouletteChestRegistry.get(cached);

        String flat = wanted.replace("_", "");
        for (String k : RouletteChestRegistry.allKeys()) {
            if (k.replace("_", "").equals(flat)) return remember(wanted, k);
        }

        Set<String> want = tokens(wanted);
        String match = null;
        for (String k : RouletteChestRegistry.allKeys()) {
            if (tokens(k).containsAll(want)) {
                if (match != null) { match = null; break; } // ambigu → on ne devine pas
                match = k;
            }
        }
        return match != null ? remember(wanted, match) : null;
    }

    private static RouletteChestConfig remember(String wanted, String key) {
        KEY_CACHE.put(wanted, key);
        WaveSurvivorMod.LOGGER.info("[LootItems] Clé '{}' résolue vers le coffre '{}'", wanted, key);
        return RouletteChestRegistry.get(key);
    }

    /** Mots normalisés (minuscules, sans accents) d'un identifiant ou d'un nom. */
    public static Set<String> tokens(String s) {
        String n = Normalizer.normalize(s.toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        Set<String> out = new HashSet<>(Arrays.asList(n.split("[^a-z0-9]+")));
        out.remove("");
        return out;
    }

    private static void warnOnce(String id, String why) {
        if (WARNED.add(id)) WaveSurvivorMod.LOGGER.warn("[LootItems] {} : {}", why, id);
    }
}
