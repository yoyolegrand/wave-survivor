package com.wavesurvivor.horde.mutator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * MUTATEURS DE HORDE (catalogue partagé client + serveur, cf. docs/DESIGN_MUTATEURS_BOSSRUSH.md).
 * Chacun rend la horde plus dure contre un bonus de récompenses ; les bonus s'additionnent (ex. ×1,45).
 * « Une seule vie » est exclue du mode Kingdom ; « Brutes » et « Avortons » s'excluent mutuellement.
 */
public enum Mutator {
    // Monstres
    FRENZY("frenzy", Category.MONSTERS, 15, "\u26a1"),
    ARMORED("armored", Category.MONSTERS, 20, "\ud83d\udee1"),
    SWARM("swarm", Category.MONSTERS, 25, "\ud83d\udc1c"),
    VOLATILE("volatile", Category.MONSTERS, 15, "\ud83d\udca5"),
    VENOMOUS("venomous", Category.MONSTERS, 15, "\u2620"),
    // Joueurs
    FRAGILE("fragile", Category.PLAYERS, 20, "\ud83d\udc94"),
    HUNGRY("hungry", Category.PLAYERS, 10, "\ud83c\udf56"),
    ONE_LIFE("one_life", Category.PLAYERS, 40, "\ud83d\udc80"),
    // Boss
    ENRAGED("enraged", Category.BOSSES, 30, "\ud83d\ude21"),
    ESCORT("escort", Category.BOSSES, 15, "\u2694"),
    // Arène
    ETERNAL_NIGHT("eternal_night", Category.ARENA, 15, "\ud83c\udf11"),
    UNSTABLE("unstable", Category.ARENA, 20, "\ud83c\udf00"),
    GLASS_MONOLITH("glass_monolith", Category.ARENA, 25, "\ud83d\udc8e"),
    // Fun
    BRUTES("brutes", Category.FUN, 10, "\ud83d\udcaa"),
    RUNTS("runts", Category.FUN, 10, "\ud83d\udc2d");

    public enum Category { MONSTERS, PLAYERS, BOSSES, ARENA, FUN }

    public final String id;
    public final Category category;
    /** Bonus de récompenses en % (additionné aux autres). */
    public final int bonus;
    public final String icon;

    Mutator(String id, Category category, int bonus, String icon) {
        this.id = id;
        this.category = category;
        this.bonus = bonus;
        this.icon = icon;
    }

    /** Autorisé en mode Kingdom ? (« Une seule vie » serait trop punitive sur une longue partie). */
    public boolean kingdomAllowed() { return this != ONE_LIFE; }

    /** Mutateur incompatible (cocher l'un décoche l'autre), ou null. */
    public Mutator exclusive() {
        return this == BRUTES ? RUNTS : this == RUNTS ? BRUTES : null;
    }

    public static Mutator byId(String id) {
        for (Mutator m : values()) if (m.id.equalsIgnoreCase(id)) return m;
        return null;
    }

    /** Liste depuis « id1,id2,… » (ids inconnus ignorés, exclusivités résolues : le dernier gagne). */
    public static List<Mutator> parse(String csv) {
        List<Mutator> out = new ArrayList<>();
        if (csv == null || csv.isBlank()) return out;
        for (String s : csv.split(",")) {
            Mutator m = byId(s.trim());
            if (m == null || out.contains(m)) continue;
            if (m.exclusive() != null) out.remove(m.exclusive());
            out.add(m);
        }
        return out;
    }

    public static String join(Collection<Mutator> ms) {
        StringBuilder sb = new StringBuilder();
        for (Mutator m : ms) sb.append(sb.length() > 0 ? "," : "").append(m.id);
        return sb.toString();
    }

    /** Multiplicateur de récompenses : 1 + somme des bonus (ex. 1,45). */
    public static double multiplier(Collection<Mutator> ms) {
        int sum = 0;
        for (Mutator m : ms) sum += m.bonus;
        return 1.0 + sum / 100.0;
    }
}
