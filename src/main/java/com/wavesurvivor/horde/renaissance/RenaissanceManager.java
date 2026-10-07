package com.wavesurvivor.horde.renaissance;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.network.NetworkHandler;
import com.wavesurvivor.network.RenaissanceSacrificePacket;
import com.wavesurvivor.network.RenaissanceStatePacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Logique serveur Renaissance v2 :
 *   - open()      : envoie l'état au client et ouvre l'écran (onglet 0 = Sacrifice, 1 = Boutique)
 *   - sacrifice() : revalide la sélection, détruit les items, crédite les Points de Renaissance (PR)
 *   - buy()       : dépense des PR contre une relique ou des points Skill Tree
 */
public class RenaissanceManager {

    public static final int TAB_SACRIFICE = 0;
    public static final int TAB_SHOP = 1;
    public static final int TAB_FORGE = 2;

    // ─── Forge (1.5) ───

    /** Monte la meilleure copie d'une relique d'un niveau : un doublon est consommé, 15 / 30 PR sont dépensés. */
    public static void forgeUpgrade(ServerPlayer p, String itemId) {
        net.minecraft.world.item.Item item;
        try {
            item = BuiltInRegistries.ITEM.get(new ResourceLocation(itemId));
        } catch (Exception e) {
            return;
        }
        if (!(item instanceof com.wavesurvivor.item.RelicItem)) return;
        var inv = p.getInventory().items;
        com.wavesurvivor.item.RelicForge.Plan plan = com.wavesurvivor.item.RelicForge.planUpgrade(inv, item);
        if (plan == null) {
            fail(p, com.wavesurvivor.i18n.WSLang.t("forge.need_duplicate"));
            sendState(p, false, TAB_FORGE, false);
            return;
        }
        int points = RenaissanceStore.getPoints(p);
        if (points < plan.cost()) {
            fail(p, com.wavesurvivor.i18n.WSLang.t("srv.pas_assez_de_pr") + plan.cost() + com.wavesurvivor.i18n.WSLang.t("srv.requis_tu_en_as") + points + ".");
            sendState(p, false, TAB_FORGE, false);
            return;
        }
        RenaissanceStore.addPoints(p, -plan.cost());
        Heritage.addInvested(p, plan.cost()); // compte pour la Renaissance
        inv.get(plan.consumeSlot()).shrink(1);
        ItemStack target = inv.get(plan.targetSlot());
        com.wavesurvivor.item.RelicEffects.setLevel(target, plan.fromLevel() + 1);
        p.getInventory().setChanged();
        p.inventoryMenu.broadcastChanges();
        p.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("forge.upgraded",
                target.getHoverName().getString(), plan.cost(), RenaissanceStore.getPoints(p))));
        playSound(p, SoundEvents.ANVIL_USE, 1.2f);
        playSound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f);
        if (p.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 1.2, p.getZ(), 50, 0.6, 0.8, 0.6, 0.6);
        }
        sendState(p, false, TAB_FORGE, false);
    }

    /** Fusion de deux reliques en une légendaire (lot 3 : recettes et légendaires). */
    public static void forgeFuse(ServerPlayer p, String fusionId) {
        com.wavesurvivor.item.RelicFusions.fuse(p, fusionId);
        sendState(p, false, TAB_FORGE, false);
    }

    private static final int MAIN_INV_SIZE = 36; // hotbar + inventaire principal (armure/offhand exclues)

    // ─── Ouverture / sync ───

    public static void open(ServerPlayer p, int tab) {
        sendState(p, true, tab, false, false);
    }

    /** Ouvre la boutique en mode édition (admin). */
    public static void openEditor(ServerPlayer p) {
        sendState(p, true, TAB_SHOP, false, true);
    }

    public static void sendState(ServerPlayer p, boolean open, int tab, boolean resetSelection) {
        sendState(p, open, tab, resetSelection, false);
    }

    public static void sendState(ServerPlayer p, boolean open, int tab, boolean resetSelection, boolean editMode) {
        Heritage.sync(p); // 1.5 : onglet Héritage (rang, nœuds, progression)
        RenaissanceConfigData cfg = RenaissanceConfigData.current();
        Map<String, Integer> merged = new java.util.HashMap<>(RenaissanceStore.getAllPurchases(p));
        for (Map.Entry<String, Integer> e : RenaissanceStore.getAllStatLevels(p).entrySet()) {
            merged.put(STAT_PREFIX + e.getKey(), e.getValue());
        }
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                new RenaissanceStatePacket(open, tab, resetSelection,
                        RenaissanceStore.getPoints(p),
                        merged,
                        isSkillTreeAvailable(p.server, cfg),
                        isStatsAvailable(p.server, cfg),
                        editMode && p.hasPermissions(2),
                        cfg.toJson()));
    }

    public static final String STAT_PREFIX = "stat:";

    /** Stats visibles : "always" / "never" / "auto" (= seulement si le Skill Tree n'est pas dispo). */
    public static boolean isStatsAvailable(MinecraftServer server, RenaissanceConfigData cfg) {
        String mode = cfg.statsMode.toLowerCase();
        if (mode.equals("always")) return true;
        if (mode.equals("never")) return false;
        return !isSkillTreeAvailable(server, cfg);
    }

    /** Skill Tree dispo = activé en config ET la commande racine existe sur le serveur. */
    public static boolean isSkillTreeAvailable(MinecraftServer server, RenaissanceConfigData cfg) {
        if (server == null || cfg == null || !cfg.skillPointsEnabled) return false;
        String cmd = cfg.skillPointCommand.trim();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        String root = cmd.split(" ")[0];
        return server.getCommands().getDispatcher().getRoot().getChild(root) != null;
    }

    // ─── Sacrifice ───

    public static void sacrifice(ServerPlayer p, List<RenaissanceSacrificePacket.Entry> entries) {
        RenaissanceConfigData cfg = RenaissanceConfigData.current();
        Set<Integer> seen = new HashSet<>();
        Map<RenaissanceConfigData.Rarity, Integer> countByRarity = new LinkedHashMap<>();
        int total = 0;

        // 1) Inventaire des items offerts, groupés par rareté (rien n'est encore retiré)
        record Offer(ItemStack stack, int count, RenaissanceConfigData.Rarity rarity) {}
        java.util.List<Offer> offers = new java.util.ArrayList<>();
        for (RenaissanceSacrificePacket.Entry e : entries) {
            int slot = e.slot();
            if (slot < 0 || slot >= MAIN_INV_SIZE || !seen.add(slot)) continue;
            ItemStack st = p.getInventory().items.get(slot);
            if (st.isEmpty()) continue;

            ResourceLocation key = BuiltInRegistries.ITEM.getKey(st.getItem());
            if (key == null || !key.toString().equals(e.itemId())) continue; // l'item a bougé → on ignore

            int c = Math.min(e.count(), st.getCount());
            if (c <= 0) continue;

            RenaissanceConfigData.Rarity r = cfg.rarityOf(st);
            if (r == null) continue;
            offers.add(new Offer(st, c, r));
            countByRarity.merge(r, c, Integer::sum);
        }

        // 2) PR par rareté (ratio « N items = 1 PR ») + items réellement consommés (le reste est rendu)
        Map<RenaissanceConfigData.Rarity, Integer> toConsume = new LinkedHashMap<>();
        for (Map.Entry<RenaissanceConfigData.Rarity, Integer> en : countByRarity.entrySet()) {
            total += en.getKey().pointsFor(en.getValue());
            toConsume.put(en.getKey(), en.getKey().consumedFor(en.getValue()));
        }
        if (total > 0) {
            for (Offer o : offers) {
                int left = toConsume.getOrDefault(o.rarity(), 0);
                int take = Math.min(left, o.count());
                if (take <= 0) continue;
                o.stack().shrink(take);
                toConsume.put(o.rarity(), left - take);
            }
        }

        if (total <= 0) {
            String msg = cfg.noItemsMessage != null && !cfg.noItemsMessage.isBlank()
                    ? com.wavesurvivor.i18n.WSLang.t(cfg.noItemsMessage) : com.wavesurvivor.i18n.WSLang.t("srv.aucun_item_sacrifiable_dans_la_selection");
            p.sendSystemMessage(Component.literal(msg));
            playSound(p, SoundEvents.VILLAGER_NO, 1f);
            sendState(p, false, TAB_SACRIFICE, true);
            return;
        }

        p.getInventory().setChanged();
        p.inventoryMenu.broadcastChanges();
        RenaissanceStore.addPoints(p, total);

        p.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.renaissance")));
        for (Map.Entry<RenaissanceConfigData.Rarity, Integer> en : countByRarity.entrySet()) {
            RenaissanceConfigData.Rarity r = en.getKey();
            int pts = r.pointsFor(en.getValue());
            int used = r.consumedFor(en.getValue());
            int kept = en.getValue() - used;
            p.sendSystemMessage(Component.literal("§7• §f" + used + "× " + r.chatColor() + com.wavesurvivor.i18n.WSLang.t(r.name)
                    + " §8→ §d+" + pts + com.wavesurvivor.i18n.WSLang.t("srv.pr")
                    + (kept > 0 ? com.wavesurvivor.i18n.WSLang.t("srv.sacrifice_kept", kept) : "")));
        }
        p.sendSystemMessage(Component.literal("§d✦ +" + total + com.wavesurvivor.i18n.WSLang.t("srv.points_de_renaissance_total")
                + RenaissanceStore.getPoints(p) + com.wavesurvivor.i18n.WSLang.t("srv.pr_7352")));

        playSound(p, SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.9f);
        if (p.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX(), p.getY() + 1.0, p.getZ(),
                    40, 0.6, 0.8, 0.6, 0.03);
            sl.sendParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 1.2, p.getZ(),
                    60, 0.8, 0.8, 0.8, 0.5);
        }
        WaveSurvivorMod.LOGGER.info("[Renaissance] {} sacrifie → +{} PR", p.getGameProfile().getName(), total);
        sendState(p, false, TAB_SACRIFICE, true);
    }

    // ─── Boutique ───

    public static void buy(ServerPlayer p, String shopId, int quantity) {
        RenaissanceConfigData cfg = RenaissanceConfigData.current();
        int qty = Math.max(1, Math.min(64, quantity));

        if (RenaissanceConfigData.SKILL_POINT_ID.equals(shopId)) {
            buySkillPoints(p, cfg, qty);
            sendState(p, false, TAB_SHOP, false);
            return;
        }

        if (shopId != null && shopId.startsWith(STAT_PREFIX)) {
            buyStat(p, cfg, shopId.substring(STAT_PREFIX.length()));
            sendState(p, false, TAB_SHOP, false);
            return;
        }

        RenaissanceConfigData.ShopItem e = cfg.findShop(shopId);
        if (e == null) {
            fail(p, com.wavesurvivor.i18n.WSLang.t("srv.cette_relique_n_existe_plus"));
            sendState(p, false, TAB_SHOP, false);
            return;
        }

        if (e.maxPerPlayer > 0) {
            int already = RenaissanceStore.getPurchases(p, e.id);
            qty = Math.min(qty, e.maxPerPlayer - already);
            if (qty <= 0) {
                fail(p, com.wavesurvivor.i18n.WSLang.t("srv.tu_as_deja_obtenu_cette_relique_limite") + e.maxPerPlayer + ").");
                sendState(p, false, TAB_SHOP, false);
                return;
            }
        }

        long cost = (long) e.cost * qty;
        int points = RenaissanceStore.getPoints(p);
        if (points < cost) {
            fail(p, com.wavesurvivor.i18n.WSLang.t("srv.pas_assez_de_pr") + cost + com.wavesurvivor.i18n.WSLang.t("srv.requis_tu_en_as") + points + ".");
            sendState(p, false, TAB_SHOP, false);
            return;
        }

        ItemStack proto = RenaissanceConfigData.buildStack(e);
        if (proto.isEmpty()) {
            fail(p, com.wavesurvivor.i18n.WSLang.t("srv.item_introuvable_pour_cette_relique") + e.item + com.wavesurvivor.i18n.WSLang.t("srv.previens_un_admin"));
            WaveSurvivorMod.LOGGER.warn("[Renaissance] Relique '{}' : item invalide '{}'", e.id, e.item);
            sendState(p, false, TAB_SHOP, false);
            return;
        }

        RenaissanceStore.addPoints(p, (int) -cost);
        Heritage.addInvested(p, (int) cost); // compte pour la Renaissance
        for (int i = 0; i < qty; i++) {
            ItemStack give = proto.copy();
            if (!p.getInventory().add(give)) p.drop(give, false);
        }
        RenaissanceStore.addPurchases(p, e.id, qty);

        p.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.relique_obtenue") + (qty > 1 ? qty + "× " : "")
                + proto.getHoverName().getString() + " §8(-" + cost + com.wavesurvivor.i18n.WSLang.t("srv.pr_reste") + RenaissanceStore.getPoints(p) + ")"));
        playSound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f);
        playSound(p, SoundEvents.PLAYER_LEVELUP, 1.4f);
        sendState(p, false, TAB_SHOP, false);
    }

    private static void buySkillPoints(ServerPlayer p, RenaissanceConfigData cfg, int qty) {
        if (!isSkillTreeAvailable(p.server, cfg)) {
            fail(p, com.wavesurvivor.i18n.WSLang.t("srv.le_skill_tree_n_est_pas_disponible_sur_c"));
            return;
        }
        long cost = (long) cfg.skillPointCost * qty;
        int points = RenaissanceStore.getPoints(p);
        if (points < cost) {
            fail(p, com.wavesurvivor.i18n.WSLang.t("srv.pas_assez_de_pr") + cost + com.wavesurvivor.i18n.WSLang.t("srv.requis_tu_en_as") + points + ".");
            return;
        }
        String cmd = cfg.skillPointCommand
                .replace("{player}", p.getGameProfile().getName())
                .replace("{amount}", String.valueOf(qty));
        if (cmd.startsWith("/")) cmd = cmd.substring(1);

        RenaissanceStore.addPoints(p, (int) -cost);
        Heritage.addInvested(p, (int) cost); // compte pour la Renaissance
        try {
            CommandSourceStack src = p.server.createCommandSourceStack().withSuppressedOutput().withPermission(4);
            p.server.getCommands().performPrefixedCommand(src, cmd);
        } catch (Exception ex) {
            // Remboursement si la commande plante
            RenaissanceStore.addPoints(p, (int) cost);
            fail(p, com.wavesurvivor.i18n.WSLang.t("srv.erreur_skill_tree_pr_rembourses"));
            WaveSurvivorMod.LOGGER.error("[Renaissance] Commande skill tree échouée '{}' : {}", cmd, ex.getMessage());
            return;
        }
        p.sendSystemMessage(Component.literal("§a✦ +" + qty + com.wavesurvivor.i18n.WSLang.t("srv.point") + (qty > 1 ? "s" : "")
                + com.wavesurvivor.i18n.WSLang.t("srv.de_competence") + cost + com.wavesurvivor.i18n.WSLang.t("srv.pr_reste") + RenaissanceStore.getPoints(p) + ")"));
        playSound(p, SoundEvents.PLAYER_LEVELUP, 1.0f);
    }

    private static void buyStat(ServerPlayer p, RenaissanceConfigData cfg, String statId) {
        if (!isStatsAvailable(p.server, cfg)) {
            fail(p, com.wavesurvivor.i18n.WSLang.t("srv.les_stats_permanentes_ne_sont_pas_dispon"));
            return;
        }
        RenaissanceConfigData.StatUpgrade s = cfg.findStat(statId);
        if (s == null || s.resolveAttribute() == null) {
            fail(p, com.wavesurvivor.i18n.WSLang.t("srv.cette_stat_n_existe_pas"));
            return;
        }
        int lvl = RenaissanceStore.getStatLevel(p, s.id);
        if (lvl >= s.maxLevel) {
            fail(p, "§c" + s.name + com.wavesurvivor.i18n.WSLang.t("srv.est_deja_au_niveau_max_f4e9") + s.maxLevel + ").");
            return;
        }
        int cost = s.costFor(lvl);
        int points = RenaissanceStore.getPoints(p);
        if (points < cost) {
            fail(p, com.wavesurvivor.i18n.WSLang.t("srv.pas_assez_de_pr") + cost + com.wavesurvivor.i18n.WSLang.t("srv.requis_tu_en_as") + points + ".");
            return;
        }
        RenaissanceStore.addPoints(p, -cost);
        RenaissanceStore.setStatLevel(p, s.id, lvl + 1);
        Heritage.addInvested(p, cost); // 1.5 : compte pour la Renaissance
        RenaissanceStats.apply(p, s, lvl + 1);
        if ("hp".equals(s.id) || s.attribute.toLowerCase().contains("max_health")) {
            p.heal((float) (s.amount));
        }
        p.sendSystemMessage(Component.literal("§d✦ " + s.name + com.wavesurvivor.i18n.WSLang.t("srv.niveau") + (lvl + 1) + "/" + s.maxLevel
                + com.wavesurvivor.i18n.WSLang.t("srv.total") + s.formatTotal(lvl + 1) + ") §8(-" + cost + com.wavesurvivor.i18n.WSLang.t("srv.pr_reste") + RenaissanceStore.getPoints(p) + ")"));
        playSound(p, SoundEvents.PLAYER_LEVELUP, 1.2f);
        if (p.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.0, p.getZ(),
                    15, 0.4, 0.6, 0.4, 0.0);
        }
    }

    // ─── Helpers ───

    private static void fail(ServerPlayer p, String msg) {
        p.sendSystemMessage(Component.literal(msg));
        playSound(p, SoundEvents.VILLAGER_NO, 1f);
    }

    private static void playSound(ServerPlayer p, SoundEvent sound, float pitch) {
        p.level().playSound(null, p.blockPosition(), sound, SoundSource.PLAYERS, 1f, pitch);
    }
}
