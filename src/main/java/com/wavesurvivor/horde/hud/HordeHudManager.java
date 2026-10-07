package com.wavesurvivor.horde.hud;

import com.wavesurvivor.horde.HordeManager;
import com.wavesurvivor.horde.boss.BossManager;
import com.wavesurvivor.horde.spawn.MobRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

/**
 * BossBar en haut d'écran qui suit l'état de la horde en temps réel.
 *   - RUNNING              : couleur PURPLE, "🧟 X mobs restants — Vague N/T"
 *   - WAITING_NEXT_WAVE    : couleur YELLOW, "⏱ Prochaine vague dans Xs — N/T" + progress bar countdown
 *   - PAUSED               : couleur GREEN,  "☕ Pause : Xs restants — N/T" + progress bar countdown
 *
 * Le progress bar en countdown se remplit de 1.0 (début du délai) vers 0.0 (départ imminent).
 */
public class HordeHudManager {

    private static ServerBossEvent hordeBar;
    private static String hordeName = "?";

    /** État de la dernière mise à jour pour éviter des updates inutiles chaque tick. */
    private static HordeManager.State lastState;
    private static int lastMobCount = -1;
    private static int lastWave = -1;
    private static int lastTotal = -1;
    private static int lastSecondsRemaining = -1;

    /** Affiche la bar au start d'une horde. */
    public static void show(MinecraftServer server, String name, int totalWaves) {
        hordeName = name;
        if (hordeBar == null) {
            hordeBar = new ServerBossEvent(
                    Component.literal("🧟 " + name + com.wavesurvivor.i18n.WSLang.t("srv.preparation")).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
                    BossEvent.BossBarColor.PURPLE,
                    BossEvent.BossBarOverlay.NOTCHED_10
            );
        }
        hordeBar.setProgress(0f);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            hordeBar.addPlayer(p);
        }
        resetCache();
        lastTotal = totalWaves;
    }

    /**
     * Update pendant RUNNING : compte mobs + affiche vague.
     * (Signature backward-compat, appelée par le code existant.)
     */
    public static void update(int currentWave, int totalWaves) {
        if (hordeBar == null) return;

        int mobs = MobRegistry.size() + BossManager.activeBossCount();
        if (lastState == HordeManager.State.RUNNING
                && mobs == lastMobCount && currentWave == lastWave && totalWaves == lastTotal) return;

        lastState = HordeManager.State.RUNNING;
        lastMobCount = mobs;
        lastWave = currentWave;
        lastTotal = totalWaves;
        lastSecondsRemaining = -1;

        String label = "🧟 " + mobs + com.wavesurvivor.i18n.WSLang.t("srv.mob") + (mobs > 1 ? "s" : "") + com.wavesurvivor.i18n.WSLang.t(mobs > 1 ? "srv.restants" : "srv.restant")
                + com.wavesurvivor.i18n.WSLang.t("srv.vague_668d") + currentWave + " / " + totalWaves;
        hordeBar.setName(Component.literal(label).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        hordeBar.setColor(BossEvent.BossBarColor.PURPLE);

        float progress = totalWaves > 0 ? Math.min(1f, (float) currentWave / totalWaves) : 0f;
        hordeBar.setProgress(progress);
    }

    /**
     * Update pendant WAITING_NEXT_WAVE ou PAUSED : affiche le countdown.
     * @param secondsRemaining secondes restantes avant la vague suivante
     * @param totalDelaySeconds durée totale du délai (pour calculer la progress bar)
     * @param isPaused true si PAUSED (pause explicite), false si WAITING_NEXT_WAVE
     */
    public static void updateCountdown(int currentWave, int totalWaves,
                                        int secondsRemaining, int totalDelaySeconds, boolean isPaused) {
        if (hordeBar == null) return;

        HordeManager.State targetState = isPaused ? HordeManager.State.PAUSED : HordeManager.State.WAITING_NEXT_WAVE;
        if (lastState == targetState && secondsRemaining == lastSecondsRemaining
                && currentWave == lastWave && totalWaves == lastTotal) return;

        lastState = targetState;
        lastMobCount = -1;
        lastWave = currentWave;
        lastTotal = totalWaves;
        lastSecondsRemaining = secondsRemaining;

        // Label : affiche la prochaine vague à venir
        int nextWave = Math.min(currentWave + 1, totalWaves);
        String prefix;
        ChatFormatting labelColor;
        BossEvent.BossBarColor barColor;
        if (isPaused) {
            prefix = com.wavesurvivor.i18n.WSLang.t("srv.pause");
            labelColor = ChatFormatting.AQUA;
            barColor = BossEvent.BossBarColor.GREEN;
        } else {
            prefix = com.wavesurvivor.i18n.WSLang.t("srv.prochaine_vague_dans");
            labelColor = ChatFormatting.YELLOW;
            barColor = BossEvent.BossBarColor.YELLOW;
        }
        String label = prefix + secondsRemaining + com.wavesurvivor.i18n.WSLang.t("srv.s_vague") + nextWave + " / " + totalWaves;
        hordeBar.setName(Component.literal(label).withStyle(labelColor, ChatFormatting.BOLD));
        hordeBar.setColor(barColor);

        // Progress bar : se vide au fur et à mesure que le countdown avance
        float progress = totalDelaySeconds > 0
                ? Math.max(0f, Math.min(1f, (float) secondsRemaining / totalDelaySeconds))
                : 0f;
        hordeBar.setProgress(progress);
    }

    /** Cache et cleanup la bar (fin ou stop de horde). */
    public static void hide() {
        if (hordeBar != null) {
            hordeBar.removeAllPlayers();
            hordeBar = null;
            resetCache();
        }
    }

    /** Ajoute les nouveaux joueurs connectés à la bar (login pendant horde). */
    public static void syncPlayers(MinecraftServer server) {
        if (hordeBar == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            hordeBar.addPlayer(p);
        }
    }

    private static void resetCache() {
        lastState = null;
        lastMobCount = -1;
        lastWave = -1;
        lastTotal = -1;
        lastSecondsRemaining = -1;
    }
}
