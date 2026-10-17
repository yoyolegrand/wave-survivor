package com.wavesurvivor.horde.invasion;

import com.wavesurvivor.config.model.HordeConfigMultiData;
import com.wavesurvivor.horde.HordeManager;
import com.wavesurvivor.horde.stats.PlayerStats;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * INVASIONS — événements Forge (enregistré dans WaveSurvivorMod) :
 *  • boucle du moteur d'invasion ;
 *  • MAUVAIS PRÉSAGE : un joueur qui entre dans un village avec l'effet lance une roulette (raid vanilla ou une des
 *    invasions du mod, selon les poids). Le raid vanilla se déroule alors normalement, avec en plus les récompenses
 *    du mod ; une invasion du mod retire l'effet avant que le jeu ne crée son raid ;
 *  • raid vanilla : suivi des victoires (récompenses, statistiques, succès) et pillards qui ouvrent / brisent les portes.
 */
public class InvasionEvents {

    private static final Random RNG = new Random();
    /** Joueurs dont le présage en cours a déjà été tranché (raid vanilla laissé faire). */
    private static final Set<UUID> DECIDED = ConcurrentHashMap.newKeySet();

    private static final class VTrack {
        final Raid raid;
        final Set<UUID> players = new HashSet<>();
        boolean paid;
        long doneAt;
        VTrack(Raid raid) { this.raid = raid; }
    }
    private static final Map<Integer, VTrack> VANILLA = new ConcurrentHashMap<>();

    /** La commande /ws invasion start vanilla : ce présage-là ne passe pas par la roulette. */
    public static void forceVanilla(ServerPlayer p) { DECIDED.add(p.getUUID()); }

    // ─── Boucle ───

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        InvasionManager.tick(e.getServer());
        if (e.getServer().getTickCount() % 20 == 0) watchVanilla(e.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent e) {
        InvasionManager.shutdown(e.getServer());
        VANILLA.clear();
        DECIDED.clear();
    }

    // ─── Mauvais présage : la roulette ───

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.START || !(e.player instanceof ServerPlayer p) || p.level().isClientSide) return;
        MobEffectInstance omen = p.getEffect(MobEffects.BAD_OMEN);
        if (omen == null) { DECIDED.remove(p.getUUID()); return; }
        if (DECIDED.contains(p.getUUID())) return;
        InvasionGlobal g = InvasionGlobal.get();
        if (!g.enabled || !g.omenRoulette || p.isSpectator()) return;
        ServerLevel level = p.serverLevel();
        // Mêmes conditions que le raid vanilla : pas en Pacifique, raids autorisés, dans un village
        if (level.getDifficulty() == Difficulty.PEACEFUL || level.getGameRules().getBoolean(GameRules.RULE_DISABLE_RAIDS)) return;
        if (!level.isVillage(p.blockPosition())) return;
        DECIDED.add(p.getUUID()); // un seul tirage par présage : en cas de doute, le jeu fait son raid vanilla

        MinecraftServer srv = level.getServer();
        List<HordeConfigMultiData> pool = InvasionManager.pool();
        pool.removeIf(h -> h.configData.invasion().weight <= 0);
        int vanillaW = Math.max(0, g.vanillaWeight), total = vanillaW;
        for (HordeConfigMultiData h : pool) total += h.configData.invasion().weight;
        if (pool.isEmpty() || total <= 0 || InvasionManager.active() || HordeManager.get().isRunning() || InvasionManager.inCooldown(srv)) return;

        int r = RNG.nextInt(total);
        if (r < vanillaW) {
            p.sendSystemMessage(Component.literal(WSLang.t("invasion.omen_vanilla")));
            return;
        }
        r -= vanillaW;
        HordeConfigMultiData chosen = pool.get(pool.size() - 1);
        for (HordeConfigMultiData h : pool) {
            r -= h.configData.invasion().weight;
            if (r < 0) { chosen = h; break; }
        }
        String err = InvasionManager.start(level, p.blockPosition(), chosen, omen.getAmplifier() + 1);
        if (err != null) return; // village vide, etc. : raid vanilla
        p.removeEffect(MobEffects.BAD_OMEN); // le jeu ne créera pas son propre raid
        p.sendSystemMessage(Component.literal(WSLang.t("invasion.omen_pick", WSLang.t(chosen.hordeName))));
    }

    // ─── Raid vanilla : récompenses du mod, portes ───

    private void watchVanilla(MinecraftServer srv) {
        InvasionGlobal g = InvasionGlobal.get();
        if (!g.enabled || !g.vanillaExtras) { VANILLA.clear(); return; }
        long now = srv.getTickCount();
        for (ServerLevel level : srv.getAllLevels()) {
            for (ServerPlayer p : level.players()) {
                Raid raid = level.getRaidAt(p.blockPosition());
                if (raid == null || !raid.isStarted()) continue;
                VANILLA.computeIfAbsent(raid.getId(), id -> new VTrack(raid)).players.add(p.getUUID());
            }
        }
        var it = VANILLA.entrySet().iterator();
        while (it.hasNext()) {
            VTrack t = it.next().getValue();
            if (t.raid.isVictory() && !t.paid) {
                t.paid = true;
                t.doneAt = now;
                int omen = Math.max(1, t.raid.getBadOmenLevel());
                for (UUID id : t.players) {
                    ServerPlayer p = srv.getPlayerList().getPlayer(id);
                    if (p == null || p.isSpectator()) continue;
                    double dx = p.getX() - t.raid.getCenter().getX(), dz = p.getZ() - t.raid.getCenter().getZ();
                    if (dx * dx + dz * dz > 160.0 * 160.0) continue;
                    InvasionManager.giveRewards(p, omen, g.vanillaRewardEmeralds, g.vanillaRewardKeys, g.vanillaRewardPr, "");
                    PlayerStats.add(p, "invasions_won", 1);
                }
            }
            if (t.raid.isStopped() || t.raid.isLoss() || (t.paid && now - t.doneAt > 600)) it.remove();
        }
    }

    /** Pillards du raid vanilla : ouvrent les portes et les brisent (réglable). */
    @SubscribeEvent
    public void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof Raider raider)) return;
        InvasionGlobal g = InvasionGlobal.get();
        if (!g.enabled || (!g.vanillaOpenDoors && !g.vanillaBreakDoors)) return;
        InvasionManager.addGoals(raider, null, g.vanillaOpenDoors, g.vanillaBreakDoors, false);
    }
}
