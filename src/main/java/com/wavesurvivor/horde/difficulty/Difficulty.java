package com.wavesurvivor.horde.difficulty;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * NIVEAUX DE DIFFICULTÉ (catalogue partagé client + serveur). Choisi sur l'écran de l'autel au lancement ; chaque horde
 * peut restreindre les niveaux autorisés (champ « difficulties » : ids séparés par des virgules, vide = tous).
 * Les multiplicateurs par défaut ci-dessous sont modifiables dans config/wavesurvivor/difficulty.json.
 */
public enum Difficulty {
    //            id           couleur   PV   dégâts nombre  PV boss dégâts boss  récompenses
    EASY("easy", "\u00a7a", 0.7, 0.7, 0.8, 0.7, 0.7, 0.75),
    NORMAL("normal", "\u00a7f", 1.0, 1.0, 1.0, 1.0, 1.0, 1.0),
    HARD("hard", "\u00a76", 1.4, 1.3, 1.2, 1.5, 1.3, 1.4),
    NIGHTMARE("nightmare", "\u00a7c", 2.0, 1.6, 1.5, 2.2, 1.6, 2.0);

    public final String id, color;
    public final double monsterHealth, monsterDamage, monsterCount, bossHealth, bossDamage, rewards;

    Difficulty(String id, String color, double mh, double md, double mc, double bh, double bd, double rw) {
        this.id = id;
        this.color = color;
        this.monsterHealth = mh;
        this.monsterDamage = md;
        this.monsterCount = mc;
        this.bossHealth = bh;
        this.bossDamage = bd;
        this.rewards = rw;
    }

    public static Difficulty byId(String id) {
        if (id != null) for (Difficulty d : values()) if (d.id.equalsIgnoreCase(id.trim())) return d;
        return null;
    }

    /** Niveaux autorisés par une horde (« easy,hard » ; vide ou illisible = tous). */
    public static Set<Difficulty> allowed(String csv) {
        Set<Difficulty> out = new LinkedHashSet<>();
        if (csv != null) for (String s : csv.split(",")) {
            Difficulty d = byId(s);
            if (d != null) out.add(d);
        }
        if (out.isEmpty()) for (Difficulty d : values()) out.add(d);
        return out;
    }

    /** Niveau par défaut d'une horde : Normal s'il est autorisé, sinon le premier autorisé. */
    public static Difficulty defaultFor(String csv) {
        Set<Difficulty> a = allowed(csv);
        return a.contains(NORMAL) ? NORMAL : a.iterator().next();
    }
}
