package com.wavesurvivor.horde.rewards;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.HordeConfigMultiData;
import com.wavesurvivor.horde.model.RewardEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;

/**
 * À la fin d'une horde, distribue les récompenses aux joueurs
 * en fonction de leurs kills et de qui a killé un boss.
 */
public class RewardsDistributor {

    public static void distribute(HordeConfigMultiData horde, MinecraftServer server) {
        HordeConfigMultiData.RewardsBundle r = horde.rewards;
        if (r == null || !r.enabled) {
            WaveSurvivorMod.LOGGER.info("[Rewards] Rewards désactivées pour '{}', skip.", horde.hordeName);
            return;
        }

        Map<UUID, Integer> kills = KillTracker.snapshotKills();
        Set<UUID> bossKillers = KillTracker.snapshotBossKillers();

        // Classement par kills desc
        List<Map.Entry<UUID, Integer>> ranked = new ArrayList<>(kills.entrySet());
        ranked.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        broadcast(server, Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.classement_de_la_horde")).withStyle(ChatFormatting.GOLD));

        // Top 1
        if (!ranked.isEmpty() && r.top1 != null && !r.top1.isEmpty()) {
            var top = ranked.get(0);
            giveRewards(server, top.getKey(), r.top1, com.wavesurvivor.i18n.WSLang.t("srv.top_1"));
        }
        // Top 2
        if (ranked.size() >= 2 && r.top2 != null && !r.top2.isEmpty()) {
            var top = ranked.get(1);
            giveRewards(server, top.getKey(), r.top2, com.wavesurvivor.i18n.WSLang.t("srv.top_2"));
        }
        // Top 3
        if (ranked.size() >= 3 && r.top3 != null && !r.top3.isEmpty()) {
            var top = ranked.get(2);
            giveRewards(server, top.getKey(), r.top3, com.wavesurvivor.i18n.WSLang.t("srv.top_3"));
        }

        // Ligne récap kills
        for (int i = 0; i < Math.min(3, ranked.size()); i++) {
            var e = ranked.get(i);
            String pname = playerName(server, e.getKey());
            broadcast(server, Component.literal("  " + (i + 1) + ". " + pname + " — " + e.getValue() + com.wavesurvivor.i18n.WSLang.t("srv.kills"))
                    .withStyle(ChatFormatting.WHITE));
        }
        if (ranked.isEmpty()) {
            broadcast(server, Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.aucun_kill_enregistre")).withStyle(ChatFormatting.GRAY));
        }

        // Boss killers
        if (r.bossKiller != null && !r.bossKiller.isEmpty() && !bossKillers.isEmpty()) {
            for (UUID id : bossKillers) {
                giveRewards(server, id, r.bossKiller, com.wavesurvivor.i18n.WSLang.t("srv.boss_killer"));
            }
        }

        KillTracker.stopTracking();
    }

    private static void giveRewards(MinecraftServer server, UUID playerUuid, List<RewardEntry> rewards, String label) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
        String pname = player != null ? player.getName().getString() : "?";

        int totalItems = 0;
        for (RewardEntry rw : rewards) {
            if (rw == null || rw.item == null || rw.item.isBlank()) continue;
            int qty = Math.max(1, com.wavesurvivor.horde.mutator.MutatorEffects.scaleQty(Math.max(1, rw.quantity))); // bonus des mutateurs
            ItemStack stack = com.wavesurvivor.horde.loot.LootItems.resolve(rw.item, qty);
            if (stack.isEmpty()) continue;
            qty = stack.getCount();
            if (player != null) {
                // Donne dans l'inventaire ou drop au sol si plein
                if (!player.getInventory().add(stack)) {
                    ItemEntity drop = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack);
                    player.level().addFreshEntity(drop);
                }
                totalItems += qty;
            }
        }
        broadcast(server, Component.literal(label + " → " + pname + " (+" + totalItems + com.wavesurvivor.i18n.WSLang.t("srv.items"))
                .withStyle(ChatFormatting.GREEN));
        WaveSurvivorMod.LOGGER.info("[Rewards] {} : {} → {} items", label, pname, totalItems);
    }

    private static void broadcast(MinecraftServer server, Component msg) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(msg);
        }
    }

    private static String playerName(MinecraftServer server, UUID uuid) {
        ServerPlayer p = server.getPlayerList().getPlayer(uuid);
        return p != null ? p.getName().getString() : com.wavesurvivor.i18n.WSLang.t("srv.joueur_inconnu");
    }
}
