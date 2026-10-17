package com.wavesurvivor.horde.endless;

import com.wavesurvivor.config.model.HordeConfigMultiData;
import com.wavesurvivor.horde.HordeManager;
import com.wavesurvivor.horde.difficulty.HordeProgress;
import com.wavesurvivor.horde.model.BossWave;
import com.wavesurvivor.horde.stats.PlayerStats;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * MODE SANS FIN (1.7) — la horde ne s'arrête plus après sa dernière vague : elle continue, de plus en plus forte
 * (monstres, PV et dégâts qui montent à chaque vague), avec le retour périodique de ses boss, jusqu'à la défaite
 * (vies partagées épuisées ou Monolithe tombé). Score = vagues terminées, classement par horde.
 * Choisi sur l'écran de l'autel, réglé par horde dans l'éditeur (bloc « endless » de la config).
 * Hordes classiques seulement (le Kingdom a déjà ses Assauts qui montent en puissance).
 * Cette classe garde l'état de la partie en cours (statique, comme BossRush) et écoute la mort des joueurs.
 */
public class Endless {

    private static boolean pending = false;
    private static boolean active = false;
    private static HordeConfigMultiData.EndlessSettings cfg = new HordeConfigMultiData.EndlessSettings();
    /** Dernière vague « normale » de la horde : au-delà, c'est le sans fin. */
    private static int baseTotal = 0;
    private static int lives = 0;
    private static long startTick = 0;
    private static boolean lost = false;

    // ─── Disponibilité ───

    private static HordeConfigMultiData.EndlessSettings settingsOf(HordeConfigMultiData h) {
        return h != null && h.configData != null && h.configData.endless != null ? h.configData.endless : new HordeConfigMultiData.EndlessSettings();
    }

    /** Le sans fin est-il proposé pour cette horde ? (réglé dans l'éditeur ; hordes classiques seulement) */
    public static boolean available(HordeConfigMultiData h) {
        if (h == null || h.configData == null || h.configData.isKingdom() || h.configData.totalWaves < 1) return false;
        return settingsOf(h).enabled;
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

    /** Pour l'écran de l'autel : « » = indisponible, sinon « vies;boss tous les N;verrouillé(0/1);vagues normales ». */
    public static String encodeFor(ServerPlayer p, HordeConfigMultiData h) {
        if (!available(h)) return "";
        HordeConfigMultiData.EndlessSettings s = settingsOf(h);
        return Math.max(1, s.lives) + ";" + Math.max(0, s.bossEvery) + ";" + (unlockedFor(p, h) ? 0 : 1) + ";" + h.configData.totalWaves;
    }

    // ─── Cycle de vie ───

    public static void setPending(boolean on) { pending = on; }

    public static boolean active() { return active; }

    /** Démarrage de la horde : active le sans fin si l'autel l'a demandé et que la horde le permet. */
    public static boolean begin(MinecraftServer srv, HordeConfigMultiData horde) {
        boolean wanted = pending;
        pending = false;
        active = false;
        if (!wanted || srv == null || !available(horde)) return false;
        cfg = settingsOf(horde);
        baseTotal = horde.configData.totalWaves;
        lives = Math.max(1, cfg.lives);
        lost = false;
        startTick = srv.getTickCount();
        active = true;
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.literal(WSLang.t("endless.start", baseTotal, lives)));
        }
        return true;
    }

    /** Fin de horde (défaite, arrêt) : plus de sans fin en cours. */
    public static void clear() {
        active = false;
        pending = false;
        lost = false;
    }

    // ─── Montée en puissance ───

    /** Vagues au-delà de la dernière vague normale (0 tant qu'on n'y est pas). */
    public static int extraWaves(int wave) {
        return active ? Math.max(0, wave - baseTotal) : 0;
    }

    /** Numéro de vague à utiliser pour choisir les monstres et leur nombre : plafonné à la dernière vague normale. */
    public static int templateWave(int wave) {
        return active ? Math.min(wave, Math.max(1, baseTotal)) : wave;
    }

    /** Total affiché sur la barre de la horde : 0 (= « ∞ ») une fois la dernière vague normale atteinte en mode sans fin. */
    public static int hudTotal(int total, int wave) {
        return active && wave >= total ? 0 : total;
    }

    /** Facteur sur le nombre de monstres d'une vague (plafonné par maxCountMultiplier). */
    public static double countFactor(int wave) {
        int n = extraWaves(wave);
        if (n <= 0) return 1.0;
        return Math.min(Math.max(1.0, cfg.maxCountMultiplier), 1.0 + n * Math.max(0, cfg.countPercent) / 100.0);
    }

    private static double healthFactor(int n) { return 1.0 + n * Math.max(0, cfg.healthPercent) / 100.0; }

    private static double damageFactor(int n) { return 1.0 + n * Math.max(0, cfg.damagePercent) / 100.0; }

    /** Nombre de retours de boss déjà passés (1 au premier retour). */
    private static int bossCycle(int wave) {
        int every = Math.max(0, cfg.bossEvery);
        return every <= 0 ? 0 : extraWaves(wave) / every;
    }

    /** Boss de cette vague (retour périodique, boss de la horde tour à tour), ou null. */
    public static BossWave bossFor(HordeConfigMultiData.ConfigDataInner c, int wave) {
        if (!active || c == null || !c.useBossWaves || c.bossWaves == null || c.bossWaves.isEmpty()) return null;
        int every = Math.max(0, cfg.bossEvery);
        int n = extraWaves(wave);
        if (every <= 0 || n <= 0 || n % every != 0) return null;
        List<BossWave> list = new ArrayList<>(c.bossWaves);
        list.sort(Comparator.comparingInt(b -> b.waveNumber));
        return list.get((n / every - 1) % list.size());
    }

    private static void scale(LivingEntity le, Attribute a, double f) {
        if (f == 1.0) return;
        AttributeInstance i = le.getAttribute(a);
        if (i != null) i.setBaseValue(Math.max(0.5, i.getBaseValue() * f));
    }

    /** Unités de horde (à leur enregistrement) : PV et dégâts en hausse à chaque vague du sans fin. */
    public static void applyToUnit(net.minecraft.world.entity.Entity e) {
        if (!active || !(e instanceof LivingEntity le)) return;
        int n = extraWaves(HordeManager.get().getCurrentWave());
        if (n <= 0) return;
        var tag = le.getPersistentData();
        if (tag.getBoolean("ws_endless")) return;
        tag.putBoolean("ws_endless", true);
        double hf = healthFactor(n);
        tag.putDouble("ws_endless_hp", hf);
        tag.putDouble("ws_endless_dmg", damageFactor(n));
        scale(le, Attributes.MAX_HEALTH, hf);
        scale(le, Attributes.ATTACK_DAMAGE, damageFactor(n));
        if (hf != 1.0) le.setHealth(le.getMaxHealth());
    }

    /** Boss : comme une unité, plus la puissance cumulée de ses retours (bossPowerPercent par retour). */
    public static void applyToBoss(LivingEntity boss) {
        if (!active) return;
        int wave = HordeManager.get().getCurrentWave();
        int n = extraWaves(wave);
        if (n <= 0) return;
        var tag = boss.getPersistentData();
        if (tag.getBoolean("ws_endless_boss")) return;
        tag.putBoolean("ws_endless_boss", true);
        double bonus = 1.0 + bossCycle(wave) * Math.max(0, cfg.bossPowerPercent) / 100.0;
        double prevHp = tag.getBoolean("ws_endless") ? Math.max(0.01, tag.getDouble("ws_endless_hp")) : 1.0;
        double prevDmg = tag.getBoolean("ws_endless") ? Math.max(0.01, tag.getDouble("ws_endless_dmg")) : 1.0;
        scale(boss, Attributes.MAX_HEALTH, healthFactor(n) * bonus / prevHp);
        scale(boss, Attributes.ATTACK_DAMAGE, damageFactor(n) * bonus / prevDmg);
        boss.setHealth(boss.getMaxHealth());
    }

    /** Début d'une vague : annonces au passage dans le sans fin et aux paliers. */
    public static void onWaveStart(MinecraftServer srv, int wave) {
        if (!active || srv == null) return;
        int n = extraWaves(wave);
        String key = null;
        if (n == 1) key = "endless.begins";
        else if (n > 1 && n % 5 == 0) key = "endless.milestone";
        if (key == null) return;
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) p.sendSystemMessage(Component.literal(WSLang.t(key, wave)));
    }

    // ─── Fin ───

    /**
     * Fin de la partie (appelée par HordeManager.stop) : score enregistré si c'est une vraie défaite.
     * @param cleared vagues terminées
     * @param defeat  vrai si l'équipe a perdu (vies épuisées, Monolithe tombé) ; un arrêt manuel n'est pas enregistré
     */
    public static void onEnd(MinecraftServer srv, String hordeName, int cleared, boolean defeat) {
        if (!active || srv == null) return;
        long ticks = Math.max(1, srv.getTickCount() - startTick);
        List<ServerPlayer> team = new ArrayList<>();
        StringBuilder names = new StringBuilder();
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            if (p.isSpectator()) continue;
            team.add(p);
            if (names.length() > 0) names.append(", ");
            names.append(p.getGameProfile().getName());
        }
        int score = Math.max(0, cleared);
        for (ServerPlayer p : team) PlayerStats.max(p, "endless_best_wave", score);
        if (!defeat) return;
        int rank = score > 0 ? EndlessRecords.get(srv).submit(hordeName, names.toString(), score, ticks, Math.max(1, cfg.topSize)) : -1;
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.literal(WSLang.t("endless.result", score, formatTime(ticks))));
            if (rank > 0) p.sendSystemMessage(Component.literal(WSLang.t(rank == 1 ? "endless.record" : "endless.rank", rank)));
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
        if (!active || lost || e.getEntity().level().isClientSide || !(e.getEntity() instanceof ServerPlayer p)) return;
        HordeManager hm = HordeManager.get();
        if (!hm.isRunning() || p.getServer() == null) return;
        lives--;
        MinecraftServer srv = p.getServer();
        for (ServerPlayer o : srv.getPlayerList().getPlayers()) {
            o.sendSystemMessage(Component.literal(WSLang.t("endless.life_lost", p.getGameProfile().getName(), Math.max(0, lives))));
        }
        if (lives <= 0) {
            lost = true;
            for (ServerPlayer o : srv.getPlayerList().getPlayers()) o.sendSystemMessage(Component.literal(WSLang.t("endless.defeat")));
            com.wavesurvivor.horde.renaissance.RenaissanceRewards.markDefeat(); // PR des vagues terminées
            srv.execute(hm::stop);
        }
    }

    /** Barre d'action chaque seconde : vague, vies, durée. */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !active || lost) return;
        MinecraftServer srv = e.getServer();
        if (srv.getTickCount() % 20 != 0 || !HordeManager.get().isRunning()) return;
        long elapsed = srv.getTickCount() - startTick;
        int wave = Math.max(1, HordeManager.get().getCurrentWave());
        Component bar = Component.literal(WSLang.t("endless.bar", wave, Math.max(0, lives), formatTime(elapsed)));
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) p.displayClientMessage(bar, true);
    }

    // ─── Commande /ws endless ───

    public static int showTop(CommandSourceStack src) {
        MinecraftServer srv = src.getServer();
        if (srv == null) return 0;
        EndlessRecords rec = EndlessRecords.get(srv);
        List<String> keys = new ArrayList<>(rec.keys());
        Collections.sort(keys);
        if (keys.isEmpty()) {
            src.sendSuccess(() -> Component.literal(WSLang.t("endless.top_none")), false);
            return 1;
        }
        src.sendSuccess(() -> Component.literal(WSLang.t("endless.top_head")), false);
        for (String key : keys) {
            src.sendSuccess(() -> Component.literal("§e• " + WSLang.t(key)), false);
            List<EndlessRecords.Entry> top = rec.top(key);
            for (int i = 0; i < Math.min(5, top.size()); i++) {
                final int n = i + 1;
                final EndlessRecords.Entry en = top.get(i);
                src.sendSuccess(() -> Component.literal("§7   " + n + ". §f" + WSLang.t("endless.top_line", en.waves(), formatTime(en.ticks()))
                        + " §7— " + en.players()), false);
            }
        }
        return 1;
    }
}
