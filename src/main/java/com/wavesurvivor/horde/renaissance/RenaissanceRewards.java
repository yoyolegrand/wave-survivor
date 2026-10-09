package com.wavesurvivor.horde.renaissance;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.HordeConfigMultiData;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * PR GAGNÉS EN JOUANT (1.5) — chaque joueur présent reçoit (sans partage) :
 *   1 PR par vague terminée (2 par Assaut survécu en Kingdom) + 1 par boss vaincu
 *   + 5 en cas de victoire + 15 la première fois qu'il gagne cette horde (variantes comprises).
 * Multiplicateurs : difficulté (×0,75 / ×1 / ×1,5 / ×2), mutateurs actifs, puis bonus personnels
 * (+5 % par rang de Renaissance, +10 % Héritage Marchand 4, +10 % set Fortune 3).
 * Défaite (chute du Monolithe, équipe vaincue) : vagues et boss acquis, sans bonus de victoire. /ws stop : rien.
 */
public class RenaissanceRewards {

    private static int waves = 0;
    private static int bosses = 0;
    private static boolean kingdom = false;
    private static boolean defeat = false;

    /** Nouvelle horde : compteurs remis à zéro. */
    public static void reset(boolean isKingdom) {
        waves = 0;
        bosses = 0;
        kingdom = isKingdom;
        defeat = false;
    }

    /** Vague terminée (horde classique) ou Assaut survécu (Kingdom). */
    public static void onWaveCleared() {
        waves++;
    }

    /** Défaite réelle (pas un arrêt manuel) : la prochaine fin de horde verse les PR acquis. */
    public static void markDefeat() {
        defeat = true;
    }

    /** Arrêt de horde : vrai si c'était une défaite (consomme l'indicateur). */
    public static boolean consumeDefeat() {
        boolean d = defeat;
        defeat = false;
        return d;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDeath(LivingDeathEvent e) {
        if (e.getEntity().level().isClientSide || !com.wavesurvivor.horde.HordeManager.get().isRunning()) return;
        if (com.wavesurvivor.horde.boss.BossManager.isBoss(e.getEntity().getUUID())) bosses++;
    }

    private static double difficultyMult() {
        var d = com.wavesurvivor.horde.difficulty.HordeDifficulty.active();
        if (d == null) return 1.0;
        return switch (d.id) {
            case "easy" -> 0.75;
            case "hard" -> 1.5;
            case "nightmare" -> 2.0;
            default -> 1.0;
        };
    }

    /**
     * Fin de horde : verse les PR à chaque joueur présent. À appeler AVANT l'enregistrement de la victoire
     * (pour savoir si c'est la première).
     */
    public static void payout(MinecraftServer server, HordeConfigMultiData horde, boolean victory) {
        if (server == null || horde == null) return;
        int perWave = kingdom ? 2 : 1;
        double diff = difficultyMult();
        double muta = Math.max(1.0, com.wavesurvivor.horde.mutator.HordeMutators.multiplier());
        var progress = com.wavesurvivor.horde.difficulty.HordeProgress.get(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isSpectator() && !victory) continue;
            int base = waves * perWave + bosses;
            int bonusWin = victory ? 5 : 0;
            int first = 0;
            if (victory) {
                boolean done = false;
                for (String fam : com.wavesurvivor.altar.AltarRecipes.familyOf(horde.hordeName)) {
                    if (progress.best(p.getUUID(), fam) >= 0) { done = true; break; }
                }
                if (!done) first = 15;
            }
            double personal = Heritage.prMultiplier(p) * com.wavesurvivor.item.RelicSets.prMultiplier(p);
            int total = (int) Math.round((base + bonusWin) * diff * muta * personal) + first;
            // 1.6 — Défi du jour : première victoire du jour avec la difficulté et les 3 mutateurs imposés
            int daily = victory ? com.wavesurvivor.horde.daily.DailyServer.claim(p, kingdom) : 0;
            if (daily > 0) RenaissanceStore.addPoints(p, daily);
            if (total <= 0) continue;
            RenaissanceStore.addPoints(p, total);
            p.sendSystemMessage(Component.literal(WSLang.t("pr.earned", total, base, bonusWin,
                    String.format("%.2f", diff * muta), (int) Math.round((personal - 1.0) * 100))
                    + (first > 0 ? WSLang.t("pr.first", first) : "")));
            p.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.4f);
            WaveSurvivorMod.LOGGER.info("[PR] {} +{} PR ({} vagues, {} boss, victoire={})", p.getGameProfile().getName(), total, waves, bosses, victory);
        }
        waves = 0;
        bosses = 0;
    }
}
