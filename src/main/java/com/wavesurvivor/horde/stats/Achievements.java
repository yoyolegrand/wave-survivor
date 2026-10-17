package com.wavesurvivor.horde.stats;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SUCCÈS (1.7) : de vrais advancements Minecraft (touche L), attribués par le code quand un compteur de PlayerStats
 * atteint son seuil. Fichier généré : la liste, les advancements (data/wavesurvivor/advancements) et les textes
 * (advancement.wavesurvivor.&lt;id&gt;.title / .desc) restent identiques ; pour en ajouter un, ajouter une ligne ici,
 * un fichier d'advancement (critère « done » de type minecraft:impossible) et ses deux textes.
 */
public final class Achievements {

    /** Un succès : compteur suivi, seuil, objet d'icône et cadre (task, goal, challenge). */
    public record Def(String id, String stat, long threshold, String icon, String frame) {}

    public static final List<Def> ALL = List.of(
            new Def("first_pact", "hordes_started", 1L, "minecraft:ender_eye", "task"),
            new Def("first_victory", "hordes_won", 1L, "minecraft:iron_sword", "task"),
            new Def("veteran", "hordes_won", 10L, "minecraft:diamond_sword", "goal"),
            new Def("conqueror", "hordes_won", 50L, "minecraft:netherite_sword", "challenge"),
            new Def("explorer", "distinct_hordes", 3L, "minecraft:map", "goal"),
            new Def("waves_50", "waves_cleared", 50L, "minecraft:clock", "task"),
            new Def("waves_250", "waves_cleared", 250L, "minecraft:recovery_compass", "goal"),
            new Def("waves_1000", "waves_cleared", 1000L, "minecraft:beacon", "challenge"),
            new Def("slayer_100", "monsters_killed", 100L, "minecraft:iron_axe", "task"),
            new Def("slayer_1000", "monsters_killed", 1000L, "minecraft:diamond_axe", "goal"),
            new Def("slayer_10000", "monsters_killed", 10000L, "minecraft:netherite_axe", "challenge"),
            new Def("boss_1", "bosses_killed", 1L, "minecraft:wither_skeleton_skull", "task"),
            new Def("boss_25", "bosses_killed", 25L, "minecraft:dragon_head", "goal"),
            new Def("boss_100", "bosses_killed", 100L, "minecraft:nether_star", "challenge"),
            new Def("boss_rush", "bossrush_wins", 1L, "minecraft:goat_horn", "goal"),
            new Def("daily_1", "daily_wins", 1L, "minecraft:sunflower", "task"),
            new Def("daily_7", "daily_best_streak", 7L, "minecraft:golden_apple", "goal"),
            new Def("endless_25", "endless_best_wave", 25L, "minecraft:compass", "task"),
            new Def("endless_40", "endless_best_wave", 40L, "minecraft:ender_chest", "goal"),
            new Def("endless_60", "endless_best_wave", 60L, "minecraft:dragon_egg", "challenge"),
            new Def("marathon", "playtime_ticks", 720000L, "minecraft:lantern", "goal"),
            new Def("invasion_1", "invasions_won", 1L, "minecraft:bell", "task"),
            new Def("invasion_10", "invasions_won", 10L, "minecraft:shield", "goal"),
            new Def("invasion_flawless", "invasions_flawless", 1L, "minecraft:emerald", "goal"));

    /** Succès déjà donnés pendant cette session (évite de réinterroger les advancements à chaque compteur). */
    private static final Set<String> GIVEN = ConcurrentHashMap.newKeySet();

    private Achievements() {}

    /** Vérifie tous les succès d'un joueur d'après ses compteurs. */
    public static void check(ServerPlayer p, Map<String, Long> stats) {
        if (p == null || p.getServer() == null) return;
        for (Def d : ALL) {
            if (stats.getOrDefault(d.stat(), 0L) < d.threshold()) continue;
            String key = p.getUUID() + "|" + d.id();
            if (GIVEN.contains(key)) continue;
            if (award(p, d.id())) GIVEN.add(key);
        }
    }

    /** Donne le succès (sans effet s'il est déjà obtenu). Faux si l'advancement n'existe pas (datapack désactivé). */
    public static boolean award(ServerPlayer p, String id) {
        if (p.getServer() == null) return false;
        Advancement adv = p.getServer().getAdvancements().getAdvancement(new ResourceLocation("wavesurvivor", id));
        if (adv == null) return false;
        AdvancementProgress progress = p.getAdvancements().getOrStartProgress(adv);
        if (!progress.isDone()) p.getAdvancements().award(adv, "done");
        return true;
    }

    public static boolean isDone(ServerPlayer p, String id) {
        if (p.getServer() == null) return false;
        Advancement adv = p.getServer().getAdvancements().getAdvancement(new ResourceLocation("wavesurvivor", id));
        return adv != null && p.getAdvancements().getOrStartProgress(adv).isDone();
    }
}
