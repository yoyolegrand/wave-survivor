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
        List<String> disabled = new ArrayList<>();
    }

    public static void load() {
        ITEMS.clear();
        DISABLED.clear();
        Path file = filePath();
        if (!Files.exists(file)) return;
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            FileFormat f = GSON.fromJson(content, FileFormat.class);
            if (f == null) return;
            if (f.items != null) for (RenaissanceConfigData.ShopItem s : f.items) {
                if (s != null && s.id != null && !s.id.isBlank()) ITEMS.put(s.id, s);
            }
            if (f.disabled != null) DISABLED.addAll(f.disabled);
            WaveSurvivorMod.LOGGER.info("[RenaissanceShop] {} reliques custom, {} masquées.", ITEMS.size(), DISABLED.size());
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[RenaissanceShop] Erreur load : {}", e.getMessage(), e);
        }
    }

    public static void save() {
        try {
            Path dir = FMLPaths.CONFIGDIR.get().resolve("wavesurvivor");
            if (!Files.exists(dir)) Files.createDirectories(dir);
            FileFormat f = new FileFormat();
            f.items = new ArrayList<>(ITEMS.values());
            f.disabled = new ArrayList<>(DISABLED);
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
