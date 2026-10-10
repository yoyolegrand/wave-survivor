package com.wavesurvivor.horde.bossrush;

import com.wavesurvivor.config.model.HordeConfigMultiData;
import com.wavesurvivor.horde.HordeManager;
import com.wavesurvivor.horde.difficulty.HordeProgress;
import com.wavesurvivor.horde.model.BossWave;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/**
 * MODE BOSS RUSH (1.6) — une horde jouée avec ses seuls boss, à la suite : plus de vagues de monstres, de vagues
 * spéciales, de chaos ni de brèches. Vies partagées par l'équipe, pause entre deux boss, chrono et classement.
 * Tout se règle par horde dans l'éditeur (bloc « bossRush » de la config) : vies, pause, boss retenus, Gantelet,
 * récompenses, déblocage…
 * Cette classe garde l'état de la partie en cours (statique, comme HordeMutators) et écoute la mort des joueurs.
 */
public class BossRush {

    /** Modes de lancement choisis sur l'écran de l'autel. */
    public enum Mode {
        OFF(""), RUSH("rush"), GAUNTLET("gauntlet");

        public final String id;

        Mode(String id) { this.id = id; }

        public static Mode byId(String s) {
            if (s != null) for (Mode m : values()) if (m.id.equalsIgnoreCase(s.trim())) return m;
            return OFF;
        }
    }

    /** Mode demandé à l'autel (consommé au démarrage de la horde). */
    private static Mode pending = Mode.OFF;
    private static Mode mode = Mode.OFF;
    /** Numéros de vague des boss retenus, dans l'ordre. */
    private static final List<Integer> STAGES = new ArrayList<>();
    /** Étape en cours (0 = premier boss, -1 = pas encore commencé). */
    private static int stageIdx = -1;
    private static int lives = 0;
    private static long startTick = 0;
    private static boolean lost = false;
    private static HordeConfigMultiData.BossRushSettings cfg = new HordeConfigMultiData.BossRushSettings();
    private static String hordeName = "";

    // ─── Disponibilité ───

    private static HordeConfigMultiData.BossRushSettings settingsOf(HordeConfigMultiData h) {
        return h != null && h.configData != null && h.configData.bossRush != null ? h.configData.bossRush : new HordeConfigMultiData.BossRushSettings();
    }

    /** Boss retenus (numéros de vague, triés) : tous les boss de la horde, ou ceux listés dans « waves ». */
    public static List<Integer> stagesOf(HordeConfigMultiData h) {
        TreeSet<Integer> out = new TreeSet<>();
        if (h == null || h.configData == null || !h.configData.useBossWaves || h.configData.bossWaves == null) return new ArrayList<>();
        TreeSet<Integer> only = new TreeSet<>();
        String csv = settingsOf(h).waves;
        if (csv != null) for (String s : csv.split(",")) {
            try { only.add(Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) {}
        }
        for (BossWave b : h.configData.bossWaves) {
            if (b.waveNumber < 1 || (!h.configData.isKingdom() && b.waveNumber > h.configData.totalWaves)) continue;
            if (only.isEmpty() || only.contains(b.waveNumber)) out.add(b.waveNumber);
        }
        return new ArrayList<>(out);
    }

    /** Le Boss Rush est-il proposé pour cette horde ? (réglé dans l'éditeur, hordes classiques et Kingdom, au moins un boss) */
    public static boolean available(HordeConfigMultiData h) {
        if (h == null || h.configData == null) return false;
        return settingsOf(h).enabled && !stagesOf(h).isEmpty();
    }

    /** Débloqué pour ce joueur : horde déjà terminée une fois (si la horde l'exige), ou créatif. */
    public static boolean unlockedFor(ServerPlayer p, HordeConfigMultiData h) {
        if (p.isCreative() || !settingsOf(h).requiresCompletion || p.getServer() == null) return true;
        HordeProgress progress = HordeProgress.get(p.getServer());
        for (String fam : com.wavesurvivor.altar.AltarRecipes.familyOf(h.hordeName)) {
            if (progress.best(p.getUUID(), fam) >= 0) return true;
        }
        return false;
    }

    /** Pour l'écran de l'autel : « » = indisponible, sinon « vies;boss;gantelet(0/1);verrouillé(0/1) ». */
    public static String encodeFor(ServerPlayer p, HordeConfigMultiData h) {
        if (!available(h)) return "";
        HordeConfigMultiData.BossRushSettings s = settingsOf(h);
        return Math.max(1, s.lives) + ";" + stagesOf(h).size() + ";" + (s.gauntlet ? 1 : 0) + ";" + (unlockedFor(p, h) ? 0 : 1);
    }

    // ─── Cycle de vie ───

    public static void setPending(Mode m) { pending = m == null ? Mode.OFF : m; }

    public static boolean active() { return mode != Mode.OFF; }

    public static Mode mode() { return mode; }

    /** Démarrage de la horde : active le Boss Rush si l'autel l'a demandé et que la horde le permet. */
    public static boolean begin(MinecraftServer srv, HordeConfigMultiData horde) {
        Mode m = pending;
        pending = Mode.OFF;
        mode = Mode.OFF;
        if (m == Mode.OFF || srv == null || !available(horde)) return false;
        cfg = settingsOf(horde);
        if (m == Mode.GAUNTLET && !cfg.gauntlet) m = Mode.RUSH;
        STAGES.clear();
        STAGES.addAll(stagesOf(horde));
        stageIdx = -1;
        lives = Math.max(1, cfg.lives);
        lost = false;
        startTick = srv.getTickCount();
        hordeName = horde.hordeName;
        mode = m;
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.literal(WSLang.t(m == Mode.GAUNTLET ? "bossrush.start_gauntlet" : "bossrush.start",
                    STAGES.size(), lives, cfg.pauseSeconds, (int) cfg.gauntletPercent)));
        }
        return true;
    }

    /** Fin de horde (victoire, défaite, arrêt) : plus de Boss Rush en cours. */
    public static void clear() {
        mode = Mode.OFF;
        pending = Mode.OFF;
        STAGES.clear();
        stageIdx = -1;
        lost = false;
    }

    // ─── Déroulement des étapes ───

    /** Prochain numéro de vague à jouer après {@code current} ; au-delà du dernier boss : totalWaves + 1 (= fin). */
    public static int nextWave(int current, int totalWaves) {
        for (int w : STAGES) if (w > current) return w;
        return totalWaves + 1;
    }

    public static boolean isLastStage(int wave) {
        return active() && !STAGES.isEmpty() && STAGES.get(STAGES.size() - 1) == wave;
    }

    /** Un boss commence : étape mémorisée (sert au Gantelet et à l'affichage). */
    public static void onStageStart(MinecraftServer srv, int wave) {
        stageIdx = Math.max(0, STAGES.indexOf(wave));
        if (srv == null) return;
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.literal(WSLang.t("bossrush.stage", stageIdx + 1, STAGES.size())));
        }
    }

    /** Numéro d'étape pour la barre de la horde (0 avant le premier boss). */
    public static int hudWave() { return Math.max(0, stageIdx + 1); }

    public static int hudTotal() { return Math.max(1, STAGES.size()); }

    /** Gantelet : chaque boss est plus fort que le précédent (+X % cumulés). */
    public static double gauntletMultiplier() {
        if (mode != Mode.GAUNTLET || stageIdx <= 0) return 1.0;
        return Math.pow(1.0 + Math.max(0, cfg.gauntletPercent) / 100.0, stageIdx);
    }

    // ─── Réglages lus par le gestionnaire de hordes ───

    public static int pauseSeconds() { return Math.max(5, cfg.pauseSeconds); }
    public static boolean blessings() { return cfg.blessings; }
    public static boolean merchants() { return cfg.merchants; }
    public static boolean supplyChests() { return cfg.supplyChests; }
    public static boolean leaderboardRewards() { return cfg.leaderboardRewards; }
    public static int prPerBoss() { return Math.max(0, cfg.prPerBoss); }
    public static int prVictory() { return Math.max(0, cfg.prVictory); }
    public static int maxMinutes() { return Math.max(0, cfg.maxMinutes); }

    /** Pause entre deux boss : les joueurs en vie récupèrent (si réglé). */
    public static void onPause(MinecraftServer srv) {
        if (srv == null || !cfg.healBetween) return;
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            if (p.isAlive() && !p.isSpectator()) {
                p.setHealth(p.getMaxHealth());
                p.getFoodData().setFoodLevel(20);
            }
        }
    }

    // ─── Fin ───

    /** Victoire : chrono, enregistrement au classement, annonce. À appeler avant la remise à zéro de la horde. */
    public static void onVictory(MinecraftServer srv) {
        if (!active() || srv == null) return;
        long ticks = Math.max(1, srv.getTickCount() - startTick);
        StringBuilder names = new StringBuilder();
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            if (p.isSpectator()) continue;
            if (names.length() > 0) names.append(", ");
            names.append(p.getGameProfile().getName());
        }
        int rank = BossRushRecords.get(srv).submit(BossRushRecords.key(hordeName, mode), names.toString(), ticks, Math.max(1, cfg.topSize));
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.literal(WSLang.t("bossrush.victory", formatTime(ticks), lives)));
            if (rank > 0) p.sendSystemMessage(Component.literal(WSLang.t(rank == 1 ? "bossrush.record" : "bossrush.rank", rank)));
        }
    }

    public static String formatTime(long ticks) {
        long s = ticks / 20;
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60) : String.format("%d:%02d", s / 60, s % 60);
    }

    // ─── Vies et affichage ───

    /** Chaque mort d'un joueur coûte une vie à l'équipe ; à zéro, c'est la défaite. */
    @SubscribeEvent
    public void onDeath(LivingDeathEvent e) {
        if (!active() || lost || e.getEntity().level().isClientSide || !(e.getEntity() instanceof ServerPlayer p)) return;
        HordeManager hm = HordeManager.get();
        if (!hm.isRunning() || p.getServer() == null) return;
        lives--;
        MinecraftServer srv = p.getServer();
        for (ServerPlayer o : srv.getPlayerList().getPlayers()) {
            o.sendSystemMessage(Component.literal(WSLang.t("bossrush.life_lost", p.getGameProfile().getName(), Math.max(0, lives))));
        }
        if (lives <= 0) {
            lost = true;
            for (ServerPlayer o : srv.getPlayerList().getPlayers()) o.sendSystemMessage(Component.literal(WSLang.t("bossrush.defeat")));
            com.wavesurvivor.horde.renaissance.RenaissanceRewards.markDefeat(); // PR des boss déjà vaincus
            srv.execute(hm::stop);
        }
    }

    /** Barre d'action chaque seconde : boss, vies, chrono. Fin de partie si la limite de temps (réglée) est dépassée. */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !active() || lost) return;
        MinecraftServer srv = e.getServer();
        if (srv.getTickCount() % 20 != 0 || !HordeManager.get().isRunning()) return;
        long elapsed = srv.getTickCount() - startTick;
        int limit = maxMinutes();
        if (limit > 0 && elapsed > limit * 1200L) {
            lost = true;
            for (ServerPlayer o : srv.getPlayerList().getPlayers()) o.sendSystemMessage(Component.literal(WSLang.t("bossrush.timeout", limit)));
            com.wavesurvivor.horde.renaissance.RenaissanceRewards.markDefeat();
            srv.execute(() -> HordeManager.get().stop());
            return;
        }
        Component bar = Component.literal(WSLang.t("bossrush.bar", Math.max(1, hudWave()), hudTotal(), Math.max(0, lives), formatTime(elapsed)));
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) p.displayClientMessage(bar, true);
    }

    // ─── Commande /ws bossrush ───

    public static int showTop(CommandSourceStack src) {
        MinecraftServer srv = src.getServer();
        if (srv == null) return 0;
        BossRushRecords rec = BossRushRecords.get(srv);
        List<String> keys = new ArrayList<>(rec.keys());
        Collections.sort(keys);
        if (keys.isEmpty()) {
            src.sendSuccess(() -> Component.literal(WSLang.t("bossrush.top_none")), false);
            return 1;
        }
        src.sendSuccess(() -> Component.literal(WSLang.t("bossrush.top_head")), false);
        for (String key : keys) {
            int bar = key.lastIndexOf('|');
            String horde = bar < 0 ? key : key.substring(0, bar);
            boolean gauntlet = bar >= 0 && key.substring(bar + 1).equals("gauntlet");
            String title = WSLang.t(horde) + (gauntlet ? " " + WSLang.t("bossrush.gauntlet_tag") : "");
            src.sendSuccess(() -> Component.literal("§e• " + title), false);
            List<BossRushRecords.Entry> top = rec.top(key);
            for (int i = 0; i < Math.min(5, top.size()); i++) {
                final int n = i + 1;
                final BossRushRecords.Entry en = top.get(i);
                src.sendSuccess(() -> Component.literal("§7   " + n + ". §f" + formatTime(en.ticks()) + " §7— " + en.players()), false);
            }
        }
        return 1;
    }
}
