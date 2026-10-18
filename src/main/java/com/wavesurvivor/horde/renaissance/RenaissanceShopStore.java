package com.wavesurvivor.horde.renaissance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.wavesurvivor.WaveSurvivorMod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reliques ajoutées/éditées EN JEU par les admins (fichier séparé du config.json base44,
 * donc pas écrasé par un export).
 * Fichier : config/wavesurvivor/renaissance_shop_custom.json
 *
 *   items    : reliques custom (un id identique à une relique de config = override)
 *   disabled : ids masqués (reliques supprimées en jeu, y compris celles de la config)
 */
public class RenaissanceShopStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String FILE_NAME = "renaissance_shop_custom.json";

    private static final Map<String, RenaissanceConfigData.ShopItem> ITEMS = new LinkedHashMap<>();
    private static final Set<String> DISABLED = new LinkedHashSet<>();

    private static class FileFormat {
        List<RenaissanceConfigData.ShopItem> items = new ArrayList<>();
        /** Reliques masquées par le joueur (en plus de celles que la base masque déjà). */
        List<String> disabled = new ArrayList<>();
        /** Reliques que la base masque et que le joueur a réactivées. */
        List<String> enabled = new ArrayList<>();
    }

    /** Reliques de la base livrée avec le mod (relue du jar à chaque démarrage). */
    private static Map<String, RenaissanceConfigData.ShopItem> baseItems() {
        Map<String, RenaissanceConfigData.ShopItem> out = new LinkedHashMap<>();
        for (com.google.gson.JsonElement e : com.wavesurvivor.config.ConfigLayers.builtin().shopItems) {
            RenaissanceConfigData.ShopItem s = GSON.fromJson(e, RenaissanceConfigData.ShopItem.class);
            if (s != null && s.id != null && !s.id.isBlank()) out.put(s.id, s);
        }
        return out;
    }

    private static Set<String> baseDisabled() {
        Set<String> out = new LinkedHashSet<>();
        for (com.google.gson.JsonElement e : com.wavesurvivor.config.ConfigLayers.builtin().shopDisabled) out.add(e.getAsString());
        return out;
    }

    /** Charge la base du mod + la couche du joueur. */
    public static void load() {
        ITEMS.clear();
        DISABLED.clear();
        ITEMS.putAll(baseItems());
        DISABLED.addAll(baseDisabled());
        Path file = filePath();
        try {
            if (Files.exists(file)) {
                FileFormat f = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), FileFormat.class);
                if (f != null) {
                    if (f.items != null) for (RenaissanceConfigData.ShopItem s : f.items) {
                        if (s != null && s.id != null && !s.id.isBlank()) ITEMS.put(s.id, s);
                    }
                    if (f.disabled != null) DISABLED.addAll(f.disabled);
                    if (f.enabled != null) DISABLED.removeAll(f.enabled);
                }
            }
            WaveSurvivorMod.LOGGER.info("[RenaissanceShop] {} reliques (base + les vôtres), {} masquées.", ITEMS.size(), DISABLED.size());
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[RenaissanceShop] Erreur load : {}", e.getMessage(), e);
        }
    }

    /** Écrit uniquement ce qui diffère de la base. */
    public static void save() {
        try {
            Path dir = FMLPaths.CONFIGDIR.get().resolve("wavesurvivor");
            if (!Files.exists(dir)) Files.createDirectories(dir);
            Map<String, RenaissanceConfigData.ShopItem> base = baseItems();
            Set<String> baseOff = baseDisabled();
            FileFormat f = new FileFormat();
            for (RenaissanceConfigData.ShopItem s : ITEMS.values()) {
                RenaissanceConfigData.ShopItem b = base.get(s.id);
                if (b != null && com.wavesurvivor.config.Canon.same(GSON.toJsonTree(s), GSON.toJsonTree(b))) continue;
                f.items.add(s);
            }
            for (String d : DISABLED) if (!baseOff.contains(d)) f.disabled.add(d);
            for (String d : baseOff) if (!DISABLED.contains(d)) f.enabled.add(d);
            Files.writeString(filePath(), GSON.toJson(f), StandardCharsets.UTF_8);
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[RenaissanceShop] Erreur save : {}", e.getMessage(), e);
        }
    }

    /** Ajoute ou remplace (même id). Réactive l'id s'il était masqué. */
    public static void put(RenaissanceConfigData.ShopItem s) {
        ITEMS.put(s.id, s);
        DISABLED.remove(s.id);
        save();
    }

    /** Supprime une relique (custom ou config) de la boutique. */
    public static void remove(String id) {
        ITEMS.remove(id);
        DISABLED.add(id);
        save();
    }

    /** Fusionne dans la config : retire les masqués, remplace/ajoute les custom. */
    public static void applyTo(RenaissanceConfigData data) {
        List<RenaissanceConfigData.ShopItem> out = new ArrayList<>();
        for (RenaissanceConfigData.ShopItem s : data.shopItems) {
            if (DISABLED.contains(s.id)) continue;
            out.add(ITEMS.getOrDefault(s.id, s));
        }
        for (RenaissanceConfigData.ShopItem s : ITEMS.values()) {
            boolean already = false;
            for (RenaissanceConfigData.ShopItem o : out) if (o.id.equals(s.id)) { already = true; break; }
            if (!already && !DISABLED.contains(s.id)) out.add(s);
        }
        data.shopItems = out;
    }

    private static Path filePath() {
        return FMLPaths.CONFIGDIR.get().resolve("wavesurvivor").resolve(FILE_NAME);
    }
}
