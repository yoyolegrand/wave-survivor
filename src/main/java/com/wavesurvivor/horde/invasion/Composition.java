package com.wavesurvivor.horde.invasion;

import net.minecraft.world.Difficulty;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * COMPOSITION DES VAGUES DU RAID VANILLA (reprise des tables de Minecraft) : pillards, vindicateurs, évocateurs,
 * sorcières et ravageurs par vague, plus les renforts aléatoires selon la difficulté du monde. Une vague bonus
 * (niveau de Mauvais présage élevé) reprend la composition de la dernière vague de base, avec ses renforts.
 * Simplification : les montures de ravageur (pillard / évocateur / vindicateur sur son dos) ne sont pas reproduites.
 */
public final class Composition {

    /** Un groupe d'envahisseurs : type d'entité et nombre. */
    public record Group(String entityType, int count) {}

    // Tables du raid vanilla, indexées par numéro de vague (0 inutilisé)
    private static final int[] VINDICATOR = {0, 0, 2, 0, 1, 4, 2, 5};
    private static final int[] EVOKER = {0, 0, 0, 0, 0, 1, 1, 2};
    private static final int[] PILLAGER = {0, 4, 3, 3, 4, 4, 4, 2};
    private static final int[] WITCH = {0, 0, 0, 0, 3, 0, 0, 1};
    private static final int[] RAVAGER = {0, 0, 0, 1, 0, 1, 0, 2};

    private Composition() {}

    /** Nombre de vagues de base du raid vanilla selon la difficulté du monde (Facile 3, Normal 5, Difficile 7). */
    public static int baseWaves(Difficulty d) {
        return switch (d) {
            case EASY -> 3;
            case HARD -> 7;
            default -> 5;
        };
    }

    /**
     * @param wave      numéro de la vague (1 = première)
     * @param numGroups vagues de base (sans la vague bonus)
     * @param bonus     vrai pour la vague bonus (après les vagues de base)
     */
    public static List<Group> vanilla(int wave, int numGroups, boolean bonus, Difficulty diff, Random rng) {
        List<Group> out = new ArrayList<>();
        add(out, "minecraft:vindicator", VINDICATOR, 0, wave, numGroups, bonus, diff, rng);
        add(out, "minecraft:evoker", EVOKER, 1, wave, numGroups, bonus, diff, rng);
        add(out, "minecraft:pillager", PILLAGER, 2, wave, numGroups, bonus, diff, rng);
        add(out, "minecraft:witch", WITCH, 3, wave, numGroups, bonus, diff, rng);
        add(out, "minecraft:ravager", RAVAGER, 4, wave, numGroups, bonus, diff, rng);
        return out;
    }

    private static void add(List<Group> out, String type, int[] table, int kind, int wave, int numGroups, boolean bonus,
                            Difficulty diff, Random rng) {
        int idx = Math.max(0, Math.min(table.length - 1, bonus ? numGroups : wave));
        int n = table[idx] + extra(kind, wave, bonus, diff, rng);
        if (n > 0) out.add(new Group(type, n));
    }

    /** Renforts aléatoires du raid vanilla (selon la difficulté et la vague). */
    private static int extra(int kind, int wave, boolean bonus, Difficulty diff, Random rng) {
        boolean easy = diff == Difficulty.EASY, normal = diff == Difficulty.NORMAL;
        int i;
        switch (kind) {
            case 3 -> { // sorcière
                if (easy || wave <= 2 || wave == 4) return 0;
                i = 1;
            }
            case 0, 2 -> i = easy ? rng.nextInt(2) : normal ? 1 : 2; // vindicateur, pillard
            case 4 -> i = !easy && bonus ? 1 : 0; // ravageur
            default -> { return 0; }
        }
        return i > 0 ? rng.nextInt(i + 1) : 0;
    }
}
