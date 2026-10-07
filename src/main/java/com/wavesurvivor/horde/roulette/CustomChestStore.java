package com.wavesurvivor.horde.roulette;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.wavesurvivor.WaveSurvivorMod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.wavesurvivor.horde.roulette.RouletteReward.Rarity.*;

/**
 * Persistance des roulette chests custom (fichier séparé du config.json base44).
 * Fichier : config/wavesurvivor/custom_roulette_chests.json
 *
 * Format : { "chests": [ {CustomChestData}, ... ] }
 *
 * Load :
 *   - Appelé au boot (via WaveSurvivorMod) et à chaque /ws reload
 *   - Chaque CustomChestData est converti en RouletteChestConfig et enregistré dans RouletteChestRegistry
 *
 * Save :
 *   - Appelé après chaque création/modification depuis le ChestBuilderScreen
 */
public class CustomChestStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String FILE_NAME = "custom_roulette_chests.json";

    /** Chests custom keyés par nom (pour éviter les doublons + support d'édition future). */
    private static final Map<String, CustomChestData> CHESTS = new LinkedHashMap<>();

    // ─── Wrapper JSON ───
    private static class FileFormat {
        List<CustomChestData> chests = new ArrayList<>();
    }

    /** Charge le fichier + enregistre chaque chest dans RouletteChestRegistry. */
    public static void load() {
        CHESTS.clear();
        Path file = filePath();
        if (!Files.exists(file)) {
            WaveSurvivorMod.LOGGER.info("[CustomChestStore] Aucun fichier custom_roulette_chests.json — 0 chests custom.");
            return;
        }
        try {
            String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            FileFormat parsed = GSON.fromJson(content, FileFormat.class);
            if (parsed == null || parsed.chests == null) {
                WaveSurvivorMod.LOGGER.warn("[CustomChestStore] Fichier vide ou mal formé, ignoré.");
                return;
            }
            for (CustomChestData c : parsed.chests) {
                if (c == null || c.name == null || c.name.isBlank()) continue;
                CHESTS.put(c.name, c);
                RouletteChestConfig cfg = toRouletteConfig(c);
                RouletteChestRegistry.registerCustom(cfg);
                // Pattern d'émission (P6) : override du comportement idle par défaut
                RouletteChestManager.setPattern(cfg.configKey(), c.pattern);
                RouletteChestManager.setColor(cfg.configKey(), c.color);
                RouletteChestManager.setKeyMaterial(cfg.configKey(), c.keyMaterial);
            }
            WaveSurvivorMod.LOGGER.info("[CustomChestStore] {} chests custom chargés.", CHESTS.size());
        } catch (IOException | JsonSyntaxException e) {
            WaveSurvivorMod.LOGGER.error("[CustomChestStore] Erreur load : {}", e.getMessage(), e);
        }
    }

    /** Écrit tous les chests custom dans le fichier. */
    public static void save() {
        try {
            Path dir = FMLPaths.CONFIGDIR.get().resolve("wavesurvivor");
            if (!Files.exists(dir)) Files.createDirectories(dir);
            FileFormat fmt = new FileFormat();
            fmt.chests = new ArrayList<>(CHESTS.values());
            String json = GSON.toJson(fmt);
            Files.write(filePath(), json.getBytes(StandardCharsets.UTF_8));
            WaveSurvivorMod.LOGGER.info("[CustomChestStore] {} chests custom sauvegardés.", CHESTS.size());
        } catch (IOException e) {
            WaveSurvivorMod.LOGGER.error("[CustomChestStore] Erreur save : {}", e.getMessage(), e);
        }
    }

    /**
     * Ajoute ou remplace un chest custom, l'enregistre dans le registry, et save le fichier.
     * @return true si tout est OK
     */
    public static boolean addOrReplace(CustomChestData data) {
        if (data == null || data.name == null || data.name.isBlank()) return false;
        CHESTS.put(data.name, data);
        RouletteChestConfig cfg = toRouletteConfig(data);
        RouletteChestRegistry.registerCustom(cfg);
        RouletteChestManager.setPattern(cfg.configKey(), data.pattern);
        RouletteChestManager.setColor(cfg.configKey(), data.color);
        RouletteChestManager.setKeyMaterial(cfg.configKey(), data.keyMaterial);
        save();
        return true;
    }

    public static Collection<CustomChestData> all() {
        return CHESTS.values();
    }

    /**
     * Retire un chest custom : registry + fichier.
     * @return true si le chest existait et a été retiré
     */
    public static boolean delete(String name) {
        if (name == null || name.isBlank()) return false;
        CustomChestData removed = CHESTS.remove(name);
        if (removed == null) return false;
        String slug = slugify(name);
        String configKey = "custom_" + slug;
        RouletteChestRegistry.removeCustom(configKey);
        RouletteChestManager.removePattern(configKey);
        RouletteChestManager.setColor(configKey, "auto");
        RouletteChestManager.setKeyMaterial(configKey, "or");
        save();
        WaveSurvivorMod.LOGGER.info("[CustomChestStore] Chest '{}' supprimé.", name);
        return true;
    }

    public static CustomChestData get(String name) {
        return CHESTS.get(name);
    }

    public static int count() {
        return CHESTS.size();
    }

    private static Path filePath() {
        return FMLPaths.CONFIGDIR.get().resolve("wavesurvivor").resolve(FILE_NAME);
    }

    // ─── Conversion CustomChestData → RouletteChestConfig ───

    /**
     * Convertit un CustomChestData en RouletteChestConfig pour le registry standard.
     * - configKey : slug(name) préfixé par "custom_"
     * - chestName : le name tel quel (matching CustomName sur block)
     * - keyName : "Clé " + name
     * - particles : mappé depuis le preset court
     * - rewards : converti item/qty/chance/tier
     * - messages : template générique (les templates par tier ne sont pas utilisés dans cette phase,
     *              on utilise juste "reward" avec {reward} — le tier-messaging viendra Phase 5)
     */
    public static RouletteChestConfig toRouletteConfig(CustomChestData d) {
        String slug = slugify(d.name);
        String configKey = "custom_" + slug;
        String chestName = d.name;
        String keyName = "Clé " + d.name;

        List<RouletteReward> rewards = new ArrayList<>();
        if (d.rewards != null) {
            for (CustomChestData.Reward r : d.rewards) {
                if (r == null || r.item == null || r.item.isBlank()) continue;
                RouletteReward.Rarity rarity = parseRarity(r.tier);
                String displayName = extractItemName(r.item);
                rewards.add(new RouletteReward(
                        r.item,
                        Math.max(1, r.minQty),
                        Math.max(r.minQty, r.maxQty),
                        r.chance,
                        rarity,
                        displayName
                ));
            }
        }

        // Particules : mappe le preset court vers un ID particle vanilla
        String[] particles = resolveParticles(d.particles);

        // Sons standards (partagés avec les 8 chests base44)
        RouletteSounds sounds = new RouletteSounds(
                "minecraft:entity.creeper.death",
                "minecraft:block.chest.open",
                "minecraft:block.note_block.hat",
                "minecraft:entity.experience_orb.pickup",
                "minecraft:entity.player.levelup",
                "minecraft:entity.shulker.shoot",
                "minecraft:item.totem.use",
                "minecraft:ui.toast.challenge_complete"
        );

        RouletteMessages messages = new RouletteMessages(
                "§6✨ Ouverture de " + d.name + "...",
                "§e🎲 La roulette tourne...",
                "§a🎁 Vous avez gagné: §f{reward}",
                "§cVous avez besoin d'une §6{key} §cpour ouvrir ce coffre !",
                "§7La clé a été utilisée."
        );

        return new RouletteChestConfig(
                configKey, "minecraft:chest", chestName, keyName,
                true,             // consumeKey
                List.of(),        // allowedChests (custom = pas de chaîne pour l'instant)
                true,             // showAnimation
                5,                // 5 secondes d'animation par défaut
                particles[0], particles[1],
                sounds,
                new RouletteDynamicChances(999, 0, 0, 0, 0, 0), // pas de dynamic scaling pour les custom
                rewards,
                messages
        );
    }

    private static RouletteReward.Rarity parseRarity(String tier) {
        if (tier == null) return COMMON;
        return switch (tier.toUpperCase(Locale.ROOT)) {
            case "UNCOMMON" -> UNCOMMON;
            case "RARE" -> RARE;
            case "EPIC", "ÉPIQUE", "EPIQUE" -> EPIC;
            case "LEGENDARY", "LÉGENDAIRE", "LEGENDAIRE" -> LEGENDARY;
            default -> COMMON;
        };
    }

    /** Preset → [idle, active] particle ids vanilla. */
    private static String[] resolveParticles(String preset) {
        if (preset == null) preset = "none";
        return switch (preset.toLowerCase(Locale.ROOT)) {
            case "flame"         -> new String[]{"flame", "lava"};
            case "bubble"        -> new String[]{"bubble", "splash"};
            case "enchant"       -> new String[]{"enchant", "enchanted_hit"};
            case "portal"        -> new String[]{"portal", "reverse_portal"};
            case "soul_flame"    -> new String[]{"soul_fire_flame", "soul"};
            case "dust_glow"     -> new String[]{"end_rod", "glow"};
            case "crimson_spore" -> new String[]{"crimson_spore", "warped_spore"};
            case "none", ""      -> new String[]{"", ""};
            default              -> new String[]{"happy_villager", "enchanted_hit"};
        };
    }

    private static String extractItemName(String itemId) {
        // Vrai nom de l'objet (potions précises comprises) ; repli sur l'identifiant
        try {
            var st = com.wavesurvivor.horde.loot.LootItems.resolve(itemId, 1);
            if (!st.isEmpty()) return st.getHoverName().getString();
        } catch (Exception ignored) {}
        int colon = itemId.indexOf(':');
        String raw = colon >= 0 ? itemId.substring(colon + 1) : itemId;
        return raw.replace('_', ' ');
    }

    /** Slugifie un nom : "Coffre Test" → "coffre_test", "Épique !" → "epique". */
    private static String slugify(String s) {
        if (s == null) return "unnamed";
        String out = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return out.isEmpty() ? "unnamed" : out;
    }
}
