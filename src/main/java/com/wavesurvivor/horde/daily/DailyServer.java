package com.wavesurvivor.horde.daily;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.horde.difficulty.HordeDifficulty;
import com.wavesurvivor.horde.mutator.HordeMutators;
import com.wavesurvivor.horde.mutator.Mutator;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * DÉFI DU JOUR — partie serveur : série de jours de chaque joueur (sauvegardée avec le monde), détection du défi au
 * lancement d'une horde, et récompense à la victoire (une seule fois par jour et par joueur).
 */
public class DailyServer extends SavedData {

    private static final String NAME = "wavesurvivor_daily";

    /** joueur → {dernier jour réussi (jour epoch UTC), série en jours consécutifs}. */
    private final Map<UUID, long[]> data = new HashMap<>();

    /** Défi de la horde en cours (null = la horde en cours n'est pas le défi du jour). Fixé au lancement. */
    private static DailyChallenge.Plan runPlan = null;

    public static DailyServer get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(DailyServer::load, DailyServer::new, NAME);
    }

    // ─── Horde ───

    /** Lancement d'une horde (après difficulté et mutateurs) : est-ce le défi du jour ? */
    public static void onHordeStart(MinecraftServer server, boolean kingdom) {
        DailyChallenge.Plan plan = DailyChallenge.today();
        runPlan = plan.matches(HordeMutators.active(), HordeDifficulty.active()) ? plan : null;
        if (runPlan == null || server == null) return;
        int pr = DailyChallenge.reward(1, plan.difficulty(), kingdom);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.literal(WSLang.t("daily.start", WSLang.t("difficulty." + plan.difficulty().id), pr)));
        }
        WaveSurvivorMod.LOGGER.info("[Défi du jour] Horde lancée en défi du jour ({}, {}).", plan.date(), plan.difficulty().id);
    }

    /** Fin de horde : le défi n'est plus en cours. */
    public static void clear() {
        runPlan = null;
    }

    /**
     * Victoire : PR du défi du jour pour ce joueur (0 si ce n'était pas le défi ou s'il l'a déjà réussi aujourd'hui).
     * Enregistre le jour et la série, et prévient le joueur.
     */
    public static int claim(ServerPlayer p, boolean kingdom) {
        if (runPlan == null || p.getServer() == null) return 0;
        DailyServer s = get(p.getServer());
        long day = runPlan.day();
        long[] e = s.data.get(p.getUUID());
        if (e != null && e[0] == day) return 0;
        long streak = (e != null && e[0] == day - 1) ? e[1] + 1 : 1;
        s.data.put(p.getUUID(), new long[]{day, streak});
        s.setDirty();
        int pr = DailyChallenge.reward((int) streak, runPlan.difficulty(), kingdom);
        p.sendSystemMessage(Component.literal(WSLang.t("daily.reward", pr, streak)));
        return pr;
    }

    // ─── Commande /ws daily ───

    public static int show(CommandSourceStack src) {
        DailyChallenge.Plan plan = DailyChallenge.today();
        StringBuilder names = new StringBuilder();
        for (Mutator m : plan.mutators()) {
            names.append(names.length() > 0 ? "§7, " : "").append("§f").append(m.icon).append(" ").append(WSLang.t("mutator." + m.id));
        }
        String diffName = plan.difficulty().color + WSLang.t("difficulty." + plan.difficulty().id);
        long streak = 0;
        boolean done = false;
        if (src.getEntity() instanceof ServerPlayer p && src.getServer() != null) {
            long[] e = get(src.getServer()).data.get(p.getUUID());
            if (e != null) {
                done = e[0] == plan.day();
                // série encore valable : réussie aujourd'hui ou hier
                streak = (e[0] == plan.day() || e[0] == plan.day() - 1) ? e[1] : 0;
            }
        }
        int next = (int) (done ? streak : streak + 1);
        String mult = String.format(java.util.Locale.ROOT, "%.1f", DailyChallenge.difficultyMult(plan.difficulty())).replace('.', ',');
        src.sendSuccess(() -> Component.literal(WSLang.t("daily.cmd.head", plan.date())), false);
        src.sendSuccess(() -> Component.literal(WSLang.t("daily.cmd.diff", diffName, mult)), false);
        src.sendSuccess(() -> Component.literal(WSLang.t("daily.cmd.muta", names.toString())), false);
        src.sendSuccess(() -> Component.literal(WSLang.t("daily.cmd.reward",
                DailyChallenge.reward(next, plan.difficulty(), false), DailyChallenge.reward(next, plan.difficulty(), true))), false);
        final long st = streak;
        final String statusKey = done ? "daily.cmd.done" : "daily.cmd.todo";
        src.sendSuccess(() -> Component.literal(WSLang.t(statusKey, st)), false);
        return 1;
    }

    // ─── Sauvegarde NBT ───

    private static DailyServer load(CompoundTag t) {
        DailyServer s = new DailyServer();
        for (String key : t.getAllKeys()) {
            try {
                CompoundTag c = t.getCompound(key);
                s.data.put(UUID.fromString(key), new long[]{c.getLong("day"), c.getLong("streak")});
            } catch (IllegalArgumentException ignored) {}
        }
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        for (Map.Entry<UUID, long[]> e : data.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putLong("day", e.getValue()[0]);
            c.putLong("streak", e.getValue()[1]);
            t.put(e.getKey().toString(), c);
        }
        return t;
    }
}
