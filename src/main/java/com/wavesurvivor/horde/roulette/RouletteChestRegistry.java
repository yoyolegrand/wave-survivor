package com.wavesurvivor.horde.roulette;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.wavesurvivor.horde.roulette.RouletteReward.Rarity.*;

/**
 * Catalogue de tous les roulette chests. Portage direct de roulette_chest.js (8 configs uniques).
 */
public class RouletteChestRegistry {

    private static final Map<String, RouletteChestConfig> CHESTS = new LinkedHashMap<>();

    // Sons standards partagés par presque toutes les configs
    private static final RouletteSounds STANDARD_SOUNDS = new RouletteSounds(
            "minecraft:entity.creeper.death",
            "minecraft:block.chest.open",
            "minecraft:block.note_block.hat",
            "minecraft:entity.experience_orb.pickup",
            "minecraft:entity.player.levelup",
            "minecraft:entity.shulker.shoot",
            "minecraft:item.totem.use",
            "minecraft:ui.toast.challenge_complete"
    );

    private static final RouletteMessages STANDARD_MESSAGES = new RouletteMessages(
            "§6✨ Ouverture du coffre mystérieux...",
            "§e🎲 La roulette tourne...",
            "§a🎁 Vous avez gagné: §f{reward}",
            "§cVous avez besoin d'une §6{key} §cpour ouvrir ce coffre !",
            "§7La clé a été utilisée."
    );

    static {
        // ============ Coffre Mystérieux ============
        register(new RouletteChestConfig(
                "coffre_mysterieux", "minecraft:chest", "Coffre Mystérieux", "Clé Mystérieuse",
                true, List.of(), true, 3,
                "happy_villager", "enchanted_hit",
                STANDARD_SOUNDS,
                new RouletteDynamicChances(3, -10, -5, 5, 7, 3),
                List.of(
                        new RouletteReward("minecraft:diamond", 1, 3, 30, COMMON, "diamond"),
                        new RouletteReward("minecraft:emerald", 2, 5, 25, UNCOMMON, "emerald"),
                        new RouletteReward("minecraft:netherite_ingot", 1, 1, 10, LEGENDARY, "netherite ingot")
                ),
                STANDARD_MESSAGES
        ));

        // ============ Coffre Boss ============
        register(new RouletteChestConfig(
                "coffre_boss", "minecraft:chest", "Coffre Boss", "Clé Boss",
                true,
                List.of("Coffre Accessoire", "Coffre Magic", "Coffre Drowned", "Coffre Nether", "CoffreEnder", "Coffre Mystérieux"),
                true, 5, "angry_villager", "cloud",
                STANDARD_SOUNDS,
                new RouletteDynamicChances(100, -10, -5, 5, 7, 3),
                List.of(
                        new RouletteReward("minecraft:emerald", 2, 5, 25, UNCOMMON, "emerald"),
                        new RouletteReward("minecraft:netherite_ingot", 1, 1, 10, LEGENDARY, "netherite ingot"),
                        new RouletteReward("traveloptics:chronicles_of_the_firelord", 1, 1, 1, LEGENDARY, "Chronicles Of The Firelord")
                ),
                STANDARD_MESSAGES
        ));

        // ============ Coffre Mystérieux Accessoire (45 rewards) ============
        List<RouletteReward> accessoire = new ArrayList<>();
        accessoire.add(new RouletteReward("dungeons_and_combat:fairy_wings", 1, 1, 2.3, COMMON, "Fairy Wings"));
        String[] accItems = {
                "dungeons_and_combat:hunter_cape|Hunter Cape",
                "dungeons_and_combat:hunter_boots|Hunter Boots",
                "dungeons_and_combat:bottled_fairy_dust|Bottled Fairy Dust",
                "dungeons_and_combat:steel_shoulder|Steel Shoulder",
                "dungeons_and_combat:blessed_bandages|Blessed Bandages",
                "dungeons_and_combat:hell_ring|Hell Ring",
                "dungeons_and_combat:molten_ring|Molten Ring",
                "dungeons_and_combat:ring_of_the_eternal_resting|Ring Of The Eternal Resting",
                "traveloptics:nightstalkers_band|Nightstalkers Band",
                "traveloptics:azure_ignition_bracelet|Azure Ignition Bracelet",
                "traveloptics:cryostorm_bracelet|Cryostorm Bracelet",
                "traveloptics:hydrocharge_bracelet|Hydrocharge Bracelet",
                "traveloptics:firestorm_ring|Firestorm Ring",
                "traveloptics:aetherial_despair_ring|Aetherial Despair Ring",
                "traveloptics:bottled_raincloud|Bottled Raincloud",
                "dacxirons:wither_ring|Wither Ring",
                "traveloptics:pocket_black_hole|Pocket Black Hole",
                "dungeons_and_combat:nullfire_amulet|Nullfire Amulet",
                "dungeons_and_combat:scorpion_claw|Scorpion Claw",
                "dungeons_and_combat:flaming_gloves|Flaming Gloves",
                "dungeons_and_combat:ernos_fang_necklace|Ernos Fang Necklace",
                "dungeons_and_combat:fang_necklace|Fang Necklace",
                "dungeons_and_combat:klauen_necklace|Klauen Necklace",
                "dungeons_and_combat:thirsty_necklace|Thirsty Necklace",
                "dungeons_and_combat:blessed_amulet|Blessed Amulet",
                "dungeons_and_combat:champion_belt|Champion Belt",
                "irons_spellbooks:cast_time_ring|Cast Time Ring",
                "dungeons_and_combat:ring_of_life|Ring Of Life",
                "dungeons_and_combat:sunleia_hand|Sunleia Hand",
                "irons_spellbooks:poisonward_ring|Poisonward Ring",
                "irons_spellbooks:frostward_ring|Frostward Ring",
                "irons_spellbooks:fireward_ring|Fireward Ring",
                "irons_spellbooks:emerald_stoneplate_ring|Emerald Stoneplate Ring",
                "irons_spellbooks:heavy_chain_necklace|Heavy Chain Necklace",
                "irons_spellbooks:cooldown_ring|Cooldown Ring",
                "traveloptics:energy_unbound_necklace|Energy Unbound Necklace",
                "traveloptics:sigil_of_the_spider_sorcerer|Sigil Of The Spider Sorcerer",
                "traveloptics:amulet_of_spectral_shift|Amulet Of Spectral Shift",
                "irons_spellbooks:mana_ring|Mana Ring",
                "irons_spellbooks:amethyst_resonance_charm|Amethyst Resonance Charm",
                "irons_spellbooks:silver_ring|Silver Ring",
                "irons_spellbooks:conjurers_talisman|Conjurers Talisman",
                "irons_spellbooks:affinity_ring|Affinity Ring",
                "irons_spellbooks:concentration_amulet|Concentration Amulet"
        };
        for (String s : accItems) {
            String[] parts = s.split("\\|", 2);
            accessoire.add(new RouletteReward(parts[0], 1, 1, 2.22, COMMON, parts[1]));
        }
        register(new RouletteChestConfig(
                "coffre_mysterieux_accessoire", "minecraft:chest", "Coffre Mystérieux Accessoire", "Clé Mystérieuse",
                true, List.of("Coffre Magic", "Coffre Mystérieux"),
                true, 5, "electric_spark", "falling_obsidian_tear",
                STANDARD_SOUNDS,
                new RouletteDynamicChances(100, 0, 0, 0, 0, 0),
                accessoire, STANDARD_MESSAGES
        ));

        // ============ Coffre Mystérieux Magic ============
        register(new RouletteChestConfig(
                "coffre_mysterieux_magic", "minecraft:chest", "Coffre Mystérieux Magic", "Clé Mystérieuse",
                true, List.of("Coffre Accessoire", "Coffre Mystérieux"),
                true, 5, "firework", "glow",
                STANDARD_SOUNDS,
                new RouletteDynamicChances(3, -10, -5, 5, 7, 3),
                List.of(
                        new RouletteReward("irons_spellbooks:blank_rune", 2, 4, 5, RARE, "Blank rune"),
                        new RouletteReward("irons_spellbooks:arcane_essence", 16, 32, 10, COMMON, "Arcane essence"),
                        new RouletteReward("minecraft:emerald", 8, 16, 8, UNCOMMON, "emerald"),
                        new RouletteReward("irons_spellbooks:lightning_bottle", 8, 16, 8, UNCOMMON, "Lightning bottle"),
                        new RouletteReward("irons_spellbooks:frozen_bone", 8, 16, 8, UNCOMMON, "Frozen bone"),
                        new RouletteReward("irons_spellbooks:blood_vial", 8, 16, 8, UNCOMMON, "blood vial"),
                        new RouletteReward("irons_spellbooks:common_ink", 8, 16, 11, COMMON, "common ink"),
                        new RouletteReward("irons_spellbooks:uncommon_ink", 4, 8, 10, UNCOMMON, "uncommon ink"),
                        new RouletteReward("irons_spellbooks:rare_ink", 2, 4, 5, RARE, "rare ink"),
                        new RouletteReward("irons_spellbooks:epic_ink", 1, 2, 5, EPIC, "epic ink"),
                        new RouletteReward("irons_spellbooks:legendary_ink", 1, 1, 1, LEGENDARY, "legendary ink"),
                        new RouletteReward("irons_spellbooks:eldritch_manuscript", 1, 1, 1, LEGENDARY, "eldritch manuscript"),
                        new RouletteReward("irons_spellbooks:iron_spell_book", 1, 1, 10, UNCOMMON, "iron spell book"),
                        new RouletteReward("minecraft:poisonous_potato", 8, 16, 10, COMMON, "poisonous potato")
                ),
                STANDARD_MESSAGES
        ));

        // ============ Coffre Drowned ============
        register(new RouletteChestConfig(
                "coffre_drowned", "minecraft:chest", "Coffre Drowned", "Clé Drowned",
                true, List.of(), true, 5, "bubble", "splash",
                STANDARD_SOUNDS,
                new RouletteDynamicChances(3, -10, -5, 5, 7, 3),
                List.of(
                        new RouletteReward("alexscaves:pearl", 8, 16, 20, COMMON, "pearl"),
                        new RouletteReward("traveloptics:abyssal_tentacle", 1, 2, 15, UNCOMMON, "Abyssal tentacle"),
                        new RouletteReward("traveloptics:codex_of_the_crushing_depths", 1, 1, 1, LEGENDARY, "Codex of the crushing depths"),
                        new RouletteReward("traveloptics:abyssal_upgrade_smithing_template", 2, 4, 10, RARE, "Abyssal upgrade smithing template"),
                        new RouletteReward("alexscaves:magic_conch", 1, 1, 15, RARE, "magic conch"),
                        new RouletteReward("traveloptics:guide_to_watery_whispers", 1, 1, 10, EPIC, "guide to watery whispers"),
                        new RouletteReward("alexscaves:sea_staff", 1, 1, 8, EPIC, "Sea staff"),
                        new RouletteReward("alexscaves:ortholance", 1, 1, 5, EPIC, "Ortholance"),
                        new RouletteReward("traveloptics:aqua_rune", 4, 8, 10, RARE, "Aqua rune"),
                        new RouletteReward("dungeons_and_combat:neptunium_shield", 1, 1, 1, LEGENDARY, "Neptunium Shield"),
                        new RouletteReward("dungeons_and_combat:neptunium_trident", 1, 1, 1, LEGENDARY, "Neptunium Trident"),
                        new RouletteReward("dungeons_and_combat:neptunium_boots", 1, 1, 1, LEGENDARY, "Neptunium Boots"),
                        new RouletteReward("dungeons_and_combat:neptunium_chestplate", 1, 1, 1, LEGENDARY, "Neptunium Chestplate"),
                        new RouletteReward("dungeons_and_combat:neptunium_helmet", 1, 1, 1, LEGENDARY, "Neptunium Helmet"),
                        new RouletteReward("dungeons_and_combat:neptunium_leggings", 1, 1, 1, LEGENDARY, "Neptunium Leggings")
                ),
                STANDARD_MESSAGES
        ));

        // ============ Coffre Nether ============
        register(new RouletteChestConfig(
                "coffre_nether", "minecraft:chest", "Coffre Nether", "Clé Nether",
                true, List.of(), true, 5, "flame", "soul_fire_flame",
                STANDARD_SOUNDS,
                new RouletteDynamicChances(1, -10, -5, 5, 7, 3),
                List.of(
                        new RouletteReward("minecraft:blaze_rod", 8, 16, 25, COMMON, "blaze rod"),
                        new RouletteReward("irons_spellbooks:fire_rune", 4, 8, 15, UNCOMMON, "Fire Rune"),
                        new RouletteReward("minecraft:netherite_ingot", 2, 6, 5, EPIC, "netherite ingot"),
                        new RouletteReward("irons_spellbooks:blaze_spell_book", 1, 1, 5, EPIC, "Fire spellbook"),
                        new RouletteReward("irons_spellbooks:fire_upgrade_orb", 4, 4, 10, RARE, "Fire Upgrade orb"),
                        new RouletteReward("irons_spellbooks:cinder_essence", 6, 8, 15, UNCOMMON, "Cinder essence"),
                        new RouletteReward("dungeons_and_combat:blazing_block", 1, 1, 10, EPIC, "Blazing block"),
                        new RouletteReward("dungeons_and_combat:molten_bone_block", 1, 1, 14, RARE, "Molten Bone Block"),
                        new RouletteReward("minecraft:nether_star", 1, 1, 1, LEGENDARY, "Nether star")
                ),
                STANDARD_MESSAGES
        ));

        // ============ CoffreEnder ============
        register(new RouletteChestConfig(
                "coffre_ender", "minecraft:chest", "CoffreEnder", "Clé Ender",
                true, List.of(), true, 5, "end_rod", "dragon_breath",
                STANDARD_SOUNDS,
                new RouletteDynamicChances(3, -10, -5, 5, 7, 3),
                List.of(
                        new RouletteReward("minecraft:ender_pearl", 16, 16, 20, COMMON, "ender pearl"),
                        new RouletteReward("minecraft:dragon_breath", 16, 16, 15, UNCOMMON, "dragon breath"),
                        new RouletteReward("irons_spellbooks:dragonskin", 16, 32, 19, COMMON, "Dragon skin"),
                        new RouletteReward("irons_spellbooks:ender_rune", 8, 8, 15, RARE, "Ender rune"),
                        new RouletteReward("irons_spellbooks:dragon_spell_book", 1, 1, 1, LEGENDARY, "Dragon spell book"),
                        new RouletteReward("dungeons_and_combat:black_dragon_electric_boots", 1, 1, 5, EPIC, "Dragon Boots"),
                        new RouletteReward("dungeons_and_combat:black_dragon_electric_leggings", 1, 1, 5, EPIC, "Dragon Leggings"),
                        new RouletteReward("dungeons_and_combat:black_dragon_electric_chestplate", 1, 1, 5, EPIC, "Dragon Chestplate"),
                        new RouletteReward("dungeons_and_combat:black_dragon_electric_helmet", 1, 1, 5, EPIC, "Dragon Helmet"),
                        new RouletteReward("minecraft:elytra", 1, 1, 5, EPIC, "elytra"),
                        new RouletteReward("dungeons_and_combat:black_dragon_shield", 1, 1, 5, EPIC, "Dragon Shield")
                ),
                STANDARD_MESSAGES
        ));

        // ============ Coffre Mystérieux Ressource ============
        register(new RouletteChestConfig(
                "coffre_mysterieux_ressource", "minecraft:chest", "Coffre Mystérieux Ressource", "Clé Mystérieuse",
                true, List.of("Coffre Accessoire", "Coffre Magic"),
                true, 5, "enchant", "enchanted_hit",
                STANDARD_SOUNDS,
                new RouletteDynamicChances(3, -10, -5, 5, 7, 3),
                List.of(
                        new RouletteReward("minecraft:iron_ingot", 12, 32, 45, COMMON, "iron ingot"),
                        new RouletteReward("minecraft:gold_ingot", 10, 20, 25, UNCOMMON, "gold ingot"),
                        new RouletteReward("minecraft:netherite_ingot", 2, 6, 5, LEGENDARY, "netherite ingot"),
                        new RouletteReward("minecraft:diamond", 8, 12, 25, RARE, "diamond")
                ),
                STANDARD_MESSAGES
        ));
    }

    private static void register(RouletteChestConfig cfg) {
        CHESTS.put(cfg.configKey(), cfg);
    }

    /** Enregistre un chest custom (venant de CustomChestStore). Peut écraser un chest custom existant du même configKey. */
    public static void registerCustom(RouletteChestConfig cfg) {
        if (cfg == null || cfg.configKey() == null) return;
        CHESTS.put(cfg.configKey(), cfg);
    }

    /** Retire un chest custom du registre (utile avant reload pour éviter les doublons). */
    public static void removeCustom(String configKey) {
        if (configKey != null && configKey.startsWith("custom_")) {
            CHESTS.remove(configKey);
        }
    }

    /** Retire tous les chests custom (avant reload du CustomChestStore). */
    public static void clearAllCustom() {
        CHESTS.entrySet().removeIf(e -> e.getKey().startsWith("custom_"));
    }

    public static RouletteChestConfig get(String configKey) {
        return CHESTS.get(configKey);
    }

    /** Cherche une config par le chestName (pour matching au placement / right-click). */
    public static RouletteChestConfig getByChestName(String chestName) {
        if (chestName == null) return null;
        for (RouletteChestConfig c : CHESTS.values()) {
            if (chestName.equals(c.chestName())) return c;
        }
        return null;
    }

    public static Collection<RouletteChestConfig> all() {
        return CHESTS.values();
    }

    public static List<String> allKeys() {
        return new ArrayList<>(CHESTS.keySet());
    }

    public static int count() {
        return CHESTS.size();
    }
}
