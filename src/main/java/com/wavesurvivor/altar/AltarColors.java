package com.wavesurvivor.altar;

import net.minecraft.world.item.DyeColor;

/**
 * Teintes appliquées au calque "runes + tapis" de l'autel selon le colorant choisi.
 * Couleurs vives (plus saturées que les teintes vanilla) pour garder l'aura lumineuse.
 * Le calque est en niveaux de gris : couleur finale = gris × teinte.
 */
public class AltarColors {

    public static int rgb(DyeColor c) {
        return switch (c) {
            case WHITE -> 0xFFFFFF;
            case ORANGE -> 0xFF8C14;
            case MAGENTA -> 0xE63CDC;
            case LIGHT_BLUE -> 0x5AC8FF;
            case YELLOW -> 0xFFE628;
            case LIME -> 0x82FF28;
            case PINK -> 0xFF82B4;
            case GRAY -> 0x96969B;
            case LIGHT_GRAY -> 0xD2D2D7;
            case CYAN -> 0x1ED2D2;
            case PURPLE -> 0xA03CF0;
            case BLUE -> 0x325AFF;
            case BROWN -> 0xAA6432;
            case GREEN -> 0x3CAA28;
            case RED -> 0xFF221E;
            case BLACK -> 0x46464F;
        };
    }

    /** Nom français du colorant pour l'affichage. */
    public static String nameFr(DyeColor c) {
        // Nom traduit dans la langue du mod (le texte français sert de clé)
        return com.wavesurvivor.i18n.WSLang.t(switch (c) {
            case WHITE -> "Blanc";
            case ORANGE -> "Orange";
            case MAGENTA -> "Magenta";
            case LIGHT_BLUE -> "Bleu clair";
            case YELLOW -> "Jaune";
            case LIME -> "Vert clair";
            case PINK -> "Rose";
            case GRAY -> "Gris";
            case LIGHT_GRAY -> "Gris clair";
            case CYAN -> "Cyan";
            case PURPLE -> "Violet";
            case BLUE -> "Bleu";
            case BROWN -> "Marron";
            case GREEN -> "Vert";
            case RED -> "Rouge";
            case BLACK -> "Noir";
        });
    }
}
