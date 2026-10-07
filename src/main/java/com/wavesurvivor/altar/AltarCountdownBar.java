package com.wavesurvivor.altar;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

/**
 * BossBar dédiée pour le countdown d'invocation d'un altar (5s avant que la horde démarre).
 * Distinct du HordeHudManager parce que la horde n'est pas encore active à ce moment.
 * Auto-cleanup quand la horde démarre effectivement.
 */
public class AltarCountdownBar {

    private static ServerBossEvent bar;

    /** Affiche la bar pour tous les joueurs, initialise à progress 1.0. */
    public static void show(MinecraftServer server, String hordeName) {
        if (bar == null) {
            bar = new ServerBossEvent(
                    Component.literal("☠ Invocation de '" + hordeName + "'").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                    BossEvent.BossBarColor.RED,
                    BossEvent.BossBarOverlay.NOTCHED_10
            );
        }
        bar.setProgress(1.0f);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            bar.addPlayer(p);
        }
    }

    /** Update le texte + progress selon les secondes restantes. */
    public static void update(int secondsRemaining, int totalSeconds, String hordeName) {
        if (bar == null) return;
        String label = com.wavesurvivor.i18n.WSLang.t("srv.countdown_bar", hordeName, secondsRemaining);
        bar.setName(Component.literal(label).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        float progress = totalSeconds > 0 ? Math.max(0f, Math.min(1f, (float) secondsRemaining / totalSeconds)) : 0f;
        bar.setProgress(progress);
    }

    /** Cache et cleanup. */
    public static void hide() {
        if (bar != null) {
            bar.removeAllPlayers();
            bar = null;
        }
    }
}
