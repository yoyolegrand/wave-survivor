package com.wavesurvivor.horde.endless;

import com.wavesurvivor.config.model.HordeConfigMultiData;
import com.wavesurvivor.horde.HordeManager;
import com.wavesurvivor.horde.renaissance.RenaissanceRewards;
import com.wavesurvivor.horde.renaissance.RenaissanceStore;
import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.network.EndlessTierPacket;
import com.wavesurvivor.network.EventFeedPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PALIERS DU MODE SANS FIN (1.9) : tous les N vagues (à partir de la dernière vague normale), l'équipe reçoit un coffre de palier (de plus en plus
 * généreux), puis choisit pendant la pause : continuer, ou encaisser et s'arrêter. Continuer jusqu'à la défaite rapporte 100 % des gains (PR),
 * encaisser à un palier en rapporte une part réglable (90 % par défaut). La décision se prend à la majorité (les joueurs qui ne votent pas
 * comptent comme « Continuer ») ou par l'hôte seul, selon le réglage de la horde. Le classement enregistre la vague atteinte dans les deux cas.
 */
public final class EndlessTiers {

    private static boolean voting = false;
    private static long voteEnd = 0;
    private static int tier = 0, wave = 0;
    private static final Map<UUID, Boolean> VOTES = new HashMap<>();
    private static boolean cashedOut = false;

    private EndlessTiers() {}

    /** Vrai si la partie a été terminée par un encaissement (statistiques : pas une défaite). */
    public static boolean cashedOut() { return cashedOut; }

    public static boolean voting() { return voting; }

    public static void clear() {
        voting = false;
        VOTES.clear();
        cashedOut = false;
    }

    private static List<ServerPlayer> team(MinecraftServer srv) {
        List<ServerPlayer> l = new ArrayList<>();
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) if (!p.isSpectator()) l.add(p);
        return l;
    }

    /** L'hôte : le propriétaire du monde solo, ou à défaut un opérateur de plus haut niveau (premier de la liste). */
    private static boolean isHost(MinecraftServer srv, ServerPlayer p) {
        if (srv.isSingleplayerOwner(p.getGameProfile())) return true;
        for (ServerPlayer q : srv.getPlayerList().getPlayers()) if (srv.isSingleplayerOwner(q.getGameProfile())) return false;
        for (ServerPlayer q : srv.getPlayerList().getPlayers()) if (q.hasPermissions(4)) return q == p;
        return srv.getPlayerList().getPlayers().get(0) == p;
    }

    /**
     * Début d'une pause du mode Sans fin (vague {@code clearedWave} terminée). Si c'est un palier : coffre, puis ouverture du choix.
     * Renvoie vrai si un choix est en cours (la pause « Prêt » est alors désactivée).
     */
    public static boolean onPause(MinecraftServer srv, int clearedWave, long now) {
        HordeConfigMultiData.EndlessSettings s = Endless.settings();
        if (!Endless.active() || srv == null || s.tierEvery <= 0 || clearedWave < Endless.baseTotal() || clearedWave % s.tierEvery != 0) return false;
        tier = clearedWave / s.tierEvery;
        wave = clearedWave;
        List<ServerPlayer> team = team(srv);
        if (team.isEmpty()) return false;
        for (ServerPlayer p : team) giveChest(p, s);
        int seconds = Math.max(5, s.voteSeconds);
        voting = true;
        voteEnd = now + seconds * 20L;
        VOTES.clear();
        HordeManager.get().extendPause(voteEnd + 40, now); // la pause dure au moins le temps du choix
        for (ServerPlayer p : team) {
            EventFeedPacket.say(p, Component.literal(WSLang.t("endless.tier", tier, wave)));
            EndlessTierPacket.send(p, new EndlessTierPacket(tier, wave, seconds, Math.max(0, Math.min(100, s.cashPercent)), s.hostDecides, isHost(srv, p), false));
        }
        return true;
    }

    /** Le coffre de palier : « count + perTier × (palier − 1) » de chaque objet ; « pr » = points de Renaissance. */
    private static void giveChest(ServerPlayer p, HordeConfigMultiData.EndlessSettings s) {
        List<String> got = new ArrayList<>();
        if (s.tierChest != null) for (HordeConfigMultiData.TierItem t : s.tierChest) {
            if (t == null || t.item == null) continue;
            int n = Math.max(0, t.count + t.perTier * (tier - 1));
            if (n <= 0) continue;
            if (t.item.equals("pr")) {
                RenaissanceStore.addPoints(p, n);
                got.add(n + " PR");
                continue;
            }
            Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(t.item));
            if (it == null || it == net.minecraft.world.item.Items.AIR) continue;
            int left = n;
            while (left > 0) {
                int k = Math.min(left, it.getMaxStackSize(new ItemStack(it)));
                ItemStack st = new ItemStack(it, k);
                if (!p.getInventory().add(st)) p.drop(st, false);
                left -= k;
            }
            got.add(n + "× " + Component.translatable(it.getDescriptionId()).getString());
        }
        p.sendSystemMessage(Component.literal(WSLang.t("endless.tier_chest", tier, String.join(", ", got))));
    }

    /** Vote d'un joueur (renvoyé par l'écran du palier). */
    public static synchronized void vote(ServerPlayer p, boolean cash) {
        if (!voting || p.getServer() == null || p.isSpectator()) return;
        VOTES.put(p.getUUID(), cash);
        MinecraftServer srv = p.getServer();
        List<ServerPlayer> team = team(srv);
        int yes = 0;
        for (ServerPlayer q : team) if (Boolean.TRUE.equals(VOTES.get(q.getUUID()))) yes++;
        Component msg = Component.literal(WSLang.t("endless.vote", yes, team.size() - yes));
        for (ServerPlayer q : team) q.displayClientMessage(msg, true);
        HordeConfigMultiData.EndlessSettings s = Endless.settings();
        boolean allVoted = true;
        for (ServerPlayer q : team) if (!VOTES.containsKey(q.getUUID())) allVoted = false;
        boolean hostVoted = s.hostDecides && VOTES.containsKey(hostId(srv, team));
        if (allVoted || hostVoted) resolve(srv);
    }

    private static UUID hostId(MinecraftServer srv, List<ServerPlayer> team) {
        for (ServerPlayer p : team) if (isHost(srv, p)) return p.getUUID();
        return new UUID(0, 0);
    }

    /** Appelé chaque tick par le mode Sans fin : fin du temps de choix. */
    public static synchronized void tick(MinecraftServer srv) {
        if (voting && srv.getTickCount() >= voteEnd) resolve(srv);
    }

    private static void resolve(MinecraftServer srv) {
        if (!voting) return;
        voting = false;
        HordeConfigMultiData.EndlessSettings s = Endless.settings();
        List<ServerPlayer> team = team(srv);
        boolean cash;
        if (s.hostDecides) cash = Boolean.TRUE.equals(VOTES.get(hostId(srv, team)));
        else {
            int yes = 0;
            for (ServerPlayer p : team) if (Boolean.TRUE.equals(VOTES.get(p.getUUID()))) yes++;
            cash = team.size() > 0 && yes * 2 > team.size();
        }
        VOTES.clear();
        for (ServerPlayer p : team) EndlessTierPacket.send(p, new EndlessTierPacket(tier, wave, 0, 0, false, false, true));
        if (!cash) {
            for (ServerPlayer p : team) EventFeedPacket.say(p, Component.literal(WSLang.t("endless.continue", wave)));
            return;
        }
        int pct = Math.max(0, Math.min(100, s.cashPercent));
        for (ServerPlayer p : team) EventFeedPacket.say(p, Component.literal(WSLang.t("endless.cashout", wave, pct)));
        cashedOut = true;
        RenaissanceRewards.setCashScale(pct / 100.0);
        RenaissanceRewards.markDefeat(); // verse les PR de la partie (au prorata), classement compris
        srv.execute(() -> HordeManager.get().stop());
    }
}
