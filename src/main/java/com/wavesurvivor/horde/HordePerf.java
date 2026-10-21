package com.wavesurvivor.horde;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.horde.skill.DelayedActionScheduler;
import com.wavesurvivor.horde.skill.IceFx;
import com.wavesurvivor.horde.spawn.MobRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * MESURE DE LA PERFORMANCE (1.8) : combien coûte une vague en ressources ? Pendant une horde (ou une invasion), échantillonne chaque
 * seconde le temps de tick du serveur, le nombre d'entités (total, unités de horde, effets visuels, objets au sol) et les actions
 * différées en attente ; garde le pire de chaque vague, l'écrit dans le journal à la fin de chaque vague, et l'affiche avec /ws perf.
 *
 *   /ws perf              mesures actuelles + résumé des dernières vagues (opérateurs)
 *   /ws perf verbose      les résumés de fin de vague s'affichent aussi dans le chat des opérateurs (bascule)
 *
 * Repères : un serveur sain tourne à 50 ms par tick ou moins (20 TPS) ; au-delà, le jeu ralentit.
 */
public class HordePerf {

    private static final class Wave {
        int number, samples;
        double sumMs, maxMs;
        int maxEntities, maxMobs, maxDisplays, maxPending, maxItems;
    }

    private static Wave cur = null;
    private static final Deque<Wave> HISTORY = new ArrayDeque<>();
    private static boolean verbose = false;

    // ─── Échantillonnage ───

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer srv = e.getServer();
        if (srv.getTickCount() % 20 != 0) return;
        boolean active = HordeManager.get().isRunning() || com.wavesurvivor.horde.invasion.InvasionManager.active();
        if (!active) {
            if (cur != null) finish(srv);
            return;
        }
        int wave = HordeManager.get().getCurrentWave();
        if (cur != null && cur.number != wave) finish(srv);
        if (cur == null) { cur = new Wave(); cur.number = wave; }

        double ms = srv.getAverageTickTime();
        int entities = 0, items = 0;
        for (ServerLevel l : srv.getAllLevels()) {
            for (Entity en : l.getAllEntities()) {
                entities++;
                if (en instanceof ItemEntity) items++;
            }
        }
        cur.samples++;
        cur.sumMs += ms;
        cur.maxMs = Math.max(cur.maxMs, ms);
        cur.maxEntities = Math.max(cur.maxEntities, entities);
        cur.maxItems = Math.max(cur.maxItems, items);
        cur.maxMobs = Math.max(cur.maxMobs, MobRegistry.size());
        cur.maxDisplays = Math.max(cur.maxDisplays, IceFx.activeCount());
        cur.maxPending = Math.max(cur.maxPending, DelayedActionScheduler.pendingCount());
    }

    private static void finish(MinecraftServer srv) {
        Wave w = cur;
        cur = null;
        if (w == null || w.samples == 0) return;
        HISTORY.addLast(w);
        while (HISTORY.size() > 12) HISTORY.removeFirst();
        String line = summary(w);
        WaveSurvivorMod.LOGGER.info("[Perf] {}", line);
        if (verbose && srv != null) {
            for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                if (p.hasPermissions(2)) p.sendSystemMessage(Component.literal("§7[Perf] " + line));
            }
        }
    }

    private static String summary(Wave w) {
        double avg = w.sumMs / Math.max(1, w.samples);
        return String.format(java.util.Locale.ROOT, "vague %d : tick moyen %.1f ms (max %.1f) · entités max %d · unités de horde max %d · effets visuels max %d · objets au sol max %d · actions en attente max %d",
                w.number, avg, w.maxMs, w.maxEntities, w.maxMobs, w.maxDisplays, w.maxItems, w.maxPending);
    }

    // ─── Commande ───

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("perf").requires(src -> src.hasPermission(2))
                .executes(ctx -> show(ctx.getSource()))
                .then(Commands.literal("verbose").executes(ctx -> {
                    verbose = !verbose;
                    ctx.getSource().sendSuccess(() -> Component.literal("§7[Perf] " + (verbose ? "résumés de vague dans le chat : activés." : "résumés de vague dans le chat : désactivés (ils restent dans le journal).")), false);
                    return 1;
                }));
    }

    private static int show(CommandSourceStack src) {
        MinecraftServer srv = src.getServer();
        double ms = srv.getAverageTickTime();
        int entities = 0, items = 0;
        for (ServerLevel l : srv.getAllLevels()) {
            for (Entity en : l.getAllEntities()) {
                entities++;
                if (en instanceof ItemEntity) items++;
            }
        }
        final String now = String.format(java.util.Locale.ROOT, "§6[Perf] maintenant : tick %.1f ms (≈ %.0f TPS) · entités %d · unités de horde %d · effets visuels %d · objets au sol %d · actions en attente %d",
                ms, Math.min(20.0, 1000.0 / Math.max(1.0, ms)), entities, MobRegistry.size(), IceFx.activeCount(), items, DelayedActionScheduler.pendingCount());
        src.sendSuccess(() -> Component.literal(now), false);
        if (cur != null) src.sendSuccess(() -> Component.literal("§7  en cours, " + summary(cur)), false);
        if (HISTORY.isEmpty()) src.sendSuccess(() -> Component.literal("§7  Aucune vague mesurée pour l'instant (lance une horde)."), false);
        for (Wave w : HISTORY) src.sendSuccess(() -> Component.literal("§7  " + summary(w)), false);
        return 1;
    }
}
