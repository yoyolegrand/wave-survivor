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
 */
public record RogueUpgrade(
        String id,
        String name,
        String description,
        String icon,
        ChatFormatting color,
        List<AttributeMod> modifiers,
        int healOnApply
) {
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
