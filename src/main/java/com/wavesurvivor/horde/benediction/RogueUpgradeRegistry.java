package com.wavesurvivor.horde.benediction;

import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalogue de toutes les bénédictions rogue-like.
 * Portage direct de Benediction_horde.js — 10 catégories × 4 raretés (env. 40 upgrades).
 *
 * Raretés → couleurs :
 *   Common     → WHITE
 *   Rare       → BLUE
 *   Epic       → LIGHT_PURPLE
 *   Legendary  → YELLOW
 */
public class RogueUpgradeRegistry {

    private static final Map<String, RogueUpgrade> UPGRADES = new LinkedHashMap<>();

    static {
        // === Résistance (Épique + Légendaire seulement) ===
        register("BENEDICTION_DE_RESISTANCE_EPIC", "Bénédiction de Résistance (Épique)",
                "Augmente Armure, Robustesse et Résistance de Recul", "\uD83E\uDEA8", ChatFormatting.LIGHT_PURPLE,
                List.of(
                        mod(Attributes.ARMOR, 2, Operation.ADDITION),
                        mod(Attributes.ARMOR_TOUGHNESS, 1, Operation.ADDITION),
                        mod(Attributes.KNOCKBACK_RESISTANCE, 0.2, Operation.ADDITION)
                ), 0);
        register("BENEDICTION_DE_RESISTANCE_LEGENDARY", "Bénédiction de Résistance (Légendaire)",
                "Augmente Armure, Robustesse et Résistance de Recul", "\uD83E\uDEA8", ChatFormatting.YELLOW,
                List.of(
                        mod(Attributes.ARMOR, 4, Operation.ADDITION),
                        mod(Attributes.ARMOR_TOUGHNESS, 2, Operation.ADDITION),
                        mod(Attributes.KNOCKBACK_RESISTANCE, 0.4, Operation.ADDITION)
                ), 0);

        // === Longévité (Épique + Légendaire) ===
        register("BENEDICTION_DE_LONGEVITE_EPIC", "Bénédiction de Longévité (Épique)",
                "Augmente vos PV max fortement", "\u2728", ChatFormatting.LIGHT_PURPLE,
                List.of(
                        mod(Attributes.MAX_HEALTH, 2, Operation.ADDITION),
                        mod(Attributes.MAX_HEALTH, 0.1, Operation.MULTIPLY_BASE)
                ), 2);
        register("BENEDICTION_DE_LONGEVITE_LEGENDARY", "Bénédiction de Longévité (Légendaire)",
                "Augmente vos PV max fortement", "\u2728", ChatFormatting.YELLOW,
                List.of(
                        mod(Attributes.MAX_HEALTH, 4, Operation.ADDITION),
                        mod(Attributes.MAX_HEALTH, 0.2, Operation.MULTIPLY_BASE)
                ), 4);

        // === Célérité (Épique + Légendaire) ===
        register("BENEDICTION_DE_CELERITE_EPIC", "Bénédiction de Célérité (Épique)",
                "Augmente Vitesse et Vitesse d'Attaque", "\uD83D\uDC5F\u26A1", ChatFormatting.LIGHT_PURPLE,
                List.of(
                        mod(Attributes.MOVEMENT_SPEED, 0.1, Operation.MULTIPLY_BASE),
                        mod(Attributes.ATTACK_SPEED, 0.1, Operation.ADDITION)
                ), 0);
        register("BENEDICTION_DE_CELERITE_LEGENDARY", "Bénédiction de Célérité (Légendaire)",
                "Augmente Vitesse et Vitesse d'Attaque", "\uD83D\uDC5F\u26A1", ChatFormatting.YELLOW,
                List.of(
                        mod(Attributes.MOVEMENT_SPEED, 0.2, Operation.MULTIPLY_BASE),
                        mod(Attributes.ATTACK_SPEED, 0.2, Operation.ADDITION)
                ), 0);

        // === Chance (4 tiers) ===
        register("BENEDICTION_DE_CHANCE_COMMON", "Bénédiction de Chance (Commun)",
                "Augmente votre chance", "\uD83C\uDF40", ChatFormatting.WHITE,
                List.of(mod(Attributes.LUCK, 2, Operation.ADDITION)), 0);
        register("BENEDICTION_DE_CHANCE_RARE", "Bénédiction de Chance (Rare)",
                "Augmente votre chance", "\uD83C\uDF40", ChatFormatting.BLUE,
                List.of(mod(Attributes.LUCK, 4, Operation.ADDITION)), 0);
        register("BENEDICTION_DE_CHANCE_EPIC", "Bénédiction de Chance (Épique)",
                "Augmente votre chance", "\uD83C\uDF40", ChatFormatting.LIGHT_PURPLE,
                List.of(mod(Attributes.LUCK, 6, Operation.ADDITION)), 0);
        register("BENEDICTION_DE_CHANCE_LEGENDARY", "Bénédiction de Chance (Légendaire)",
                "Augmente votre chance", "\uD83C\uDF40", ChatFormatting.YELLOW,
                List.of(mod(Attributes.LUCK, 8, Operation.ADDITION)), 0);

        // === Vitesse d'attaque (4 tiers) ===
        register("BENEDICTION_DE_VITESSE_D_ATTAQUE_COMMON", "Bénédiction de Vitesse d'Attaque (Commun)",
                "Augmente votre Vitesse d'Attaque", "\u26A1", ChatFormatting.WHITE,
                List.of(mod(Attributes.ATTACK_SPEED, 0.05, Operation.ADDITION)), 0);
        register("BENEDICTION_DE_VITESSE_D_ATTAQUE_RARE", "Bénédiction de Vitesse d'Attaque (Rare)",
                "Augmente votre Vitesse d'Attaque", "\u26A1", ChatFormatting.BLUE,
                List.of(mod(Attributes.ATTACK_SPEED, 0.1, Operation.ADDITION)), 0);
        register("BENEDICTION_DE_VITESSE_D_ATTAQUE_EPIC", "Bénédiction de Vitesse d'Attaque (Épique)",
                "Augmente votre Vitesse d'Attaque", "\u26A1", ChatFormatting.LIGHT_PURPLE,
                List.of(mod(Attributes.ATTACK_SPEED, 0.15, Operation.ADDITION)), 0);
        register("BENEDICTION_DE_VITESSE_D_ATTAQUE_LEGENDARY", "Bénédiction de Vitesse d'Attaque (Légendaire)",
                "Augmente votre Vitesse d'Attaque", "\u26A1", ChatFormatting.YELLOW,
                List.of(mod(Attributes.ATTACK_SPEED, 0.2, Operation.ADDITION)), 0);

        // === Vitesse (4 tiers) ===
        register("BENEDICTION_DE_VITESSE_COMMON", "Bénédiction de Vitesse (Commun)",
                "Augmente votre Vitesse", "\uD83D\uDC5F", ChatFormatting.WHITE,
                List.of(mod(Attributes.MOVEMENT_SPEED, 0.05, Operation.MULTIPLY_BASE)), 0);
        register("BENEDICTION_DE_VITESSE_RARE", "Bénédiction de Vitesse (Rare)",
                "Augmente votre Vitesse", "\uD83D\uDC5F", ChatFormatting.BLUE,
                List.of(mod(Attributes.MOVEMENT_SPEED, 0.1, Operation.MULTIPLY_BASE)), 0);
        register("BENEDICTION_DE_VITESSE_EPIC", "Bénédiction de Vitesse (Épique)",
                "Augmente votre Vitesse", "\uD83D\uDC5F", ChatFormatting.LIGHT_PURPLE,
                List.of(mod(Attributes.MOVEMENT_SPEED, 0.15, Operation.MULTIPLY_BASE)), 0);
        register("BENEDICTION_DE_VITESSE_LEGENDARY", "Bénédiction de Vitesse (Légendaire)",
                "Augmente votre Vitesse", "\uD83D\uDC5F", ChatFormatting.YELLOW,
                List.of(mod(Attributes.MOVEMENT_SPEED, 0.2, Operation.MULTIPLY_BASE)), 0);

        // === Robustesse (4 tiers) ===
        register("BENEDICTION_DE_ROBUTESSE_COMMON", "Bénédiction de Robustesse (Commun)",
                "Augmente votre Robustesse", "\uD83D\uDC8E", ChatFormatting.WHITE,
                List.of(mod(Attributes.ARMOR_TOUGHNESS, 1, Operation.ADDITION)), 0);
        register("BENEDICTION_DE_ROBUTESSE_RARE", "Bénédiction de Robustesse (Rare)",
                "Augmente votre Robustesse", "\uD83D\uDC8E", ChatFormatting.BLUE,
                List.of(mod(Attributes.ARMOR_TOUGHNESS, 2, Operation.ADDITION)), 0);
        register("BENEDICTION_DE_ROBUTESSE_EPIC", "Bénédiction de Robustesse (Épique)",
                "Augmente votre Robustesse", "\uD83D\uDC8E", ChatFormatting.LIGHT_PURPLE,
                List.of(mod(Attributes.ARMOR_TOUGHNESS, 3, Operation.ADDITION)), 0);
        register("BENEDICTION_DE_ROBUTESSE_LEGENDARY", "Bénédiction de Robustesse (Légendaire)",
                "Augmente votre Robustesse", "\uD83D\uDC8E", ChatFormatting.YELLOW,
                List.of(mod(Attributes.ARMOR_TOUGHNESS, 4, Operation.ADDITION)), 0);

        // === Armure (4 tiers) ===
        register("BENEDICTION_DE_ARMURE_COMMON", "Bénédiction d'Armure (Commun)",
                "Augmente votre Armure", "\uD83D\uDEE1", ChatFormatting.WHITE,
                List.of(mod(Attributes.ARMOR, 2, Operation.ADDITION)), 0);
        register("BENEDICTION_DE_ARMURE_RARE", "Bénédiction d'Armure (Rare)",
                "Augmente votre Armure", "\uD83D\uDEE1", ChatFormatting.BLUE,
                List.of(mod(Attributes.ARMOR, 4, Operation.ADDITION)), 0);
        register("BENEDICTION_DE_ARMURE_EPIC", "Bénédiction d'Armure (Épique)",
                "Augmente votre Armure", "\uD83D\uDEE1", ChatFormatting.LIGHT_PURPLE,
                List.of(mod(Attributes.ARMOR, 6, Operation.ADDITION)), 0);
        register("BENEDICTION_DE_ARMURE_LEGENDARY", "Bénédiction d'Armure (Légendaire)",
                "Augmente votre Armure", "\uD83D\uDEE1", ChatFormatting.YELLOW,
                List.of(mod(Attributes.ARMOR, 8, Operation.ADDITION)), 0);

        // === Force (4 tiers) ===
        register("BENEDICTION_DE_FORCE_COMMON", "Bénédiction de Force (Commun)",
                "Augmente vos Dégâts", "\u2694", ChatFormatting.WHITE,
                List.of(mod(Attributes.ATTACK_DAMAGE, 1, Operation.ADDITION)), 0);
        register("BENEDICTION_DE_FORCE_RARE", "Bénédiction de Force (Rare)",
                "Augmente vos Dégâts", "\u2694", ChatFormatting.BLUE,
                List.of(mod(Attributes.ATTACK_DAMAGE, 2, Operation.ADDITION)), 0);
        register("BENEDICTION_DE_FORCE_EPIC", "Bénédiction de Force (Épique)",
                "Augmente vos Dégâts", "\u2694", ChatFormatting.LIGHT_PURPLE,
                List.of(mod(Attributes.ATTACK_DAMAGE, 3, Operation.ADDITION)), 0);
        register("BENEDICTION_DE_FORCE_LEGENDARY", "Bénédiction de Force (Légendaire)",
                "Augmente vos Dégâts", "\u2694", ChatFormatting.YELLOW,
                List.of(mod(Attributes.ATTACK_DAMAGE, 4, Operation.ADDITION)), 0);

        // === Vie (4 tiers) ===
        register("BENEDICTION_DE_VIE_COMMON", "Bénédiction de Vie (Commun)",
                "Augmente votre Santé", "\u2764", ChatFormatting.WHITE,
                List.of(mod(Attributes.MAX_HEALTH, 1, Operation.ADDITION)), 1);
        register("BENEDICTION_DE_VIE_RARE", "Bénédiction de Vie (Rare)",
                "Augmente votre Santé", "\u2764", ChatFormatting.BLUE,
                List.of(mod(Attributes.MAX_HEALTH, 2, Operation.ADDITION)), 2);
        register("BENEDICTION_DE_VIE_EPIC", "Bénédiction de Vie (Épique)",
                "Augmente votre Santé", "\u2764", ChatFormatting.LIGHT_PURPLE,
                List.of(mod(Attributes.MAX_HEALTH, 3, Operation.ADDITION)), 3);
        register("BENEDICTION_DE_VIE_LEGENDARY", "Bénédiction de Vie (Légendaire)",
                "Augmente votre Santé", "\u2764", ChatFormatting.YELLOW,
                List.of(mod(Attributes.MAX_HEALTH, 4, Operation.ADDITION)), 4);
    }

    private static void register(String id, String name, String description, String icon,
                                  ChatFormatting color, List<RogueUpgrade.AttributeMod> mods, int heal) {
        UPGRADES.put(id, new RogueUpgrade(id, name, description, icon, color, mods, heal));
    }

    private static RogueUpgrade.AttributeMod mod(Attribute attr, double amount,
                                                  net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation op) {
        return new RogueUpgrade.AttributeMod(attr, amount, op);
    }

    public static RogueUpgrade get(String id) {
        return UPGRADES.get(id);
    }

    public static Collection<RogueUpgrade> all() {
        return UPGRADES.values();
    }

    public static List<String> allIds() {
        return new ArrayList<>(UPGRADES.keySet());
    }

    public static int count() {
        return UPGRADES.size();
    }
}
