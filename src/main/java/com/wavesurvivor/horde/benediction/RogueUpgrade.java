package com.wavesurvivor.horde.benediction;

import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.List;

/**
 * Une bénédiction rogue-like.
 *
 * @param id           Identifiant unique (ex "BENEDICTION_DE_RESISTANCE_EPIC")
 * @param name         Nom d'affichage (ex "Bénédiction de Résistance (Épique)")
 * @param description  Description courte
 * @param icon         Icône emoji (ex "🪨")
 * @param color        Couleur du titre (rareté)
 * @param modifiers    Liste des modifiers à appliquer (multipliés par le level)
 * @param healOnApply  Nombre de PV rendus à l'application (0 = pas de heal)
 * @param category     Identifiant de la catégorie (ex. "VIE") : une catégorie regroupe ses niveaux de rareté (1.8)
 * @param family       Famille de la catégorie (attack, defense, mobility, utility, team, pact, stats)
 * @param rarity       Rareté : 0 COMMON, 1 RARE, 2 EPIC, 3 LEGENDARY
 * @param effects      Effets spéciaux (execution, magnet, bastion… voir BlessingEffects), en plus des attributs
 * @param maxLevel     Niveau maximal que le joueur peut atteindre avec cette bénédiction (0 = illimité)
 */
public record RogueUpgrade(
        String id,
        String name,
        String description,
        String icon,
        ChatFormatting color,
        List<AttributeMod> modifiers,
        int healOnApply,
        String category,
        String family,
        int rarity,
        List<Effect> effects,
        int maxLevel
) {
    /** Effet spécial : type + paramètres (valeurs de base du niveau 1 ; elles croissent avec le niveau de la bénédiction). */
    public record Effect(String type, com.google.gson.JsonObject params) {
        public double p(String key, double def) {
            return params != null && params.has(key) && params.get(key).isJsonPrimitive() ? params.get(key).getAsDouble() : def;
        }
    }

    /** Nom traduit dans la langue du mod (le texte français sert de clé). */
    @Override
    public String name() {
        return com.wavesurvivor.i18n.WSLang.t(name);
    }

    /** Description traduite dans la langue du mod. */
    @Override
    public String description() {
        return com.wavesurvivor.i18n.WSLang.t(description);
    }

    /** Un modifier d'attribut. */
    public record AttributeMod(Attribute attribute, double amount, AttributeModifier.Operation operation) {}
}
