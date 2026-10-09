package com.wavesurvivor.horde.daily;

import com.wavesurvivor.horde.difficulty.Difficulty;
import com.wavesurvivor.horde.mutator.Mutator;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * DÉFI DU JOUR (logique partagée client + serveur, sans état).
 * Chaque jour (date UTC) : une difficulté (Normal, Difficile ou Cauchemar) et 3 mutateurs, tirés à partir de la date seule.
 * Tous les joueurs et tous les serveurs ont donc le même défi le même jour, sans rien synchroniser.
 * Un mutateur par catégorie (Monstres, Joueurs, Boss, Arène, Fun) ; « Une seule vie » est exclu.
 * Récompense (première victoire du jour) : 10 PR + 2 par jour de série (max +10), × difficulté (1 / 1,5 / 2),
 * × 1,5 en mode Kingdom.
 */
public final class DailyChallenge {

    private DailyChallenge() {}

    public static final int BASE_PR = 10;
    public static final int STREAK_STEP = 2;
    public static final int STREAK_MAX = 10;
    public static final double KINGDOM_MULT = 1.5;

    /** Le défi d'un jour : difficulté imposée + 3 mutateurs. */
    public record Plan(long day, List<Mutator> mutators, Difficulty difficulty) {

        /** Les mutateurs et la difficulté choisis sont exactement ceux du défi. */
        public boolean matches(Collection<Mutator> active, Difficulty diff) {
            return diff == difficulty && active != null && active.size() == mutators.size() && active.containsAll(mutators);
        }

        public String date() { return LocalDate.ofEpochDay(day).toString(); }
    }

    /** Multiplicateur de récompense selon la difficulté du défi. */
    public static double difficultyMult(Difficulty d) {
        return d == Difficulty.NIGHTMARE ? 2.0 : d == Difficulty.HARD ? 1.5 : 1.0;
    }

    /** PR gagnés pour une série de {@code streak} jours (1 = premier jour). */
    public static int reward(int streak, Difficulty d, boolean kingdom) {
        int bonus = Math.min(STREAK_MAX, STREAK_STEP * Math.max(0, streak - 1));
        return (int) Math.round((BASE_PR + bonus) * difficultyMult(d) * (kingdom ? KINGDOM_MULT : 1.0));
    }

    public static long todayEpochDay() {
        return LocalDate.now(ZoneOffset.UTC).toEpochDay();
    }

    public static Plan today() {
        return forDay(todayEpochDay());
    }

    /** Défi d'un jour donné (même résultat partout : seule la date sert de graine). */
    public static Plan forDay(long day) {
        Random rnd = new Random(day * 0x9E3779B97F4A7C15L ^ 0x5DA11C4L);
        Difficulty[] pool = {Difficulty.NORMAL, Difficulty.HARD, Difficulty.NIGHTMARE};
        Difficulty diff = pool[rnd.nextInt(pool.length)];
        List<Mutator.Category> cats = new ArrayList<>(List.of(Mutator.Category.values()));
        Collections.shuffle(cats, rnd);
        List<Mutator> picked = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            List<Mutator> candidates = new ArrayList<>();
            for (Mutator m : Mutator.values()) {
                if (m.category == cats.get(i) && m != Mutator.ONE_LIFE) candidates.add(m);
            }
            picked.add(candidates.get(rnd.nextInt(candidates.size())));
        }
        return new Plan(day, List.copyOf(picked), diff);
    }
}
