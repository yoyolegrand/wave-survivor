package com.wavesurvivor.client;

import com.wavesurvivor.horde.renaissance.RenaissanceConfigData;
import com.wavesurvivor.network.NetworkHandler;
import com.wavesurvivor.network.RenaissanceBuyPacket;
import com.wavesurvivor.network.RenaissanceSacrificePacket;
import com.wavesurvivor.network.RenaissanceShopEditPacket;
import com.wavesurvivor.network.RenaissanceStatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Écran Renaissance v2 — deux onglets :
 *   0) Sacrifice : grille de l'inventaire principal, sélection au clic, aperçu PR, sacrifice en 2 clics
 *   1) Boutique  : points Skill Tree + reliques exclusives achetables en PR
 *
 * Toute la validation est refaite côté serveur (RenaissanceManager).
 */
@OnlyIn(Dist.CLIENT)
public class RenaissanceScreen extends Screen {

    private static final int PANEL_W = 260;
    private static final int PANEL_H = 236;
    private static final int CELL = 18;
    private static final int ROW_H = 22;
    private static final int VISIBLE_ROWS = 7;

    // État serveur
    private int tab;
    private int points;
    private Map<String, Integer> purchases = new HashMap<>();
    private boolean skillTreeAvailable;
    private boolean statsAvailable;
    private boolean editMode;
    private String pendingDelete;
    private RenaissanceConfigData cfg;

    // Sélection sacrifice : slot → quantité / itemId attendu
    private final Map<Integer, Integer> selected = new HashMap<>();
    private final Map<Integer, String> selectedIds = new HashMap<>();
    private boolean confirmArmed = false;
    private Button sacrificeBtn;

    // Boutique
    private int scroll = 0;
    private final List<ShopRow> rows = new ArrayList<>();

    private int left, top;

    private enum Kind { HEADER, SKILL, STAT, RELIC }

    /** STAT : bought = niveau actuel, limit = niveau max, cost = coût du prochain niveau. */
    private record ShopRow(Kind kind, String id, ItemStack icon, String name, int cost, int limit, int bought) {}

    public RenaissanceScreen(RenaissanceStatePacket pkt) {
        super(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.renaissance")));
        applyStateFields(pkt);
        this.editMode = pkt.editMode;
    }

    // ─── Sync serveur ───

    private void applyStateFields(RenaissanceStatePacket pkt) {
        this.tab = pkt.tab;
        this.points = pkt.points;
        this.purchases = new HashMap<>(pkt.purchases);
        this.skillTreeAvailable = pkt.skillTreeAvailable;
        this.statsAvailable = pkt.statsAvailable;
        this.cfg = RenaissanceConfigData.fromJson(pkt.configJson);
        if (pkt.resetSelection) {
            selected.clear();
            selectedIds.clear();
            confirmArmed = false;
        }
    }

    public void applyState(RenaissanceStatePacket pkt) {
        applyStateFields(pkt);
        if (pkt.open) editMode = pkt.editMode;
        pendingDelete = null;
        rebuild();
    }

    /** Config courante (utilisée par l'écran Configuration des raretés). */
    public RenaissanceConfigData config() {
        return cfg;
    }

    // ─── Widgets ───

    @Override
    protected void init() {
        super.init();
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        sacrificeBtn = null;
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;

        int tabY = top + 22;
        Button sacTab = addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.sacrifice")), b -> switchTab(0))
                .bounds(width / 2 - 127, tabY, 62, 18).build());
        Button shopTab = addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.boutique")), b -> switchTab(1))
                .bounds(width / 2 - 63, tabY, 62, 18).build());
        Button forgeTab = addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("forge.tab")), b -> switchTab(2))
                .bounds(width / 2 + 1, tabY, 62, 18).build());
        Button herTab = addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("heritage.tab")), b -> switchTab(3))
                .bounds(width / 2 + 65, tabY, 62, 18).build());
        sacTab.active = tab != 0;
        shopTab.active = tab != 1;
        forgeTab.active = tab != 2;
        herTab.active = tab != 3;

        // Op : configuration des raretés de sacrifice (valable sur tout le serveur)
        var mcPlayer = Minecraft.getInstance().player;
        if (mcPlayer != null && mcPlayer.hasPermissions(2)) {
            addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.configuration")),
                            b -> Minecraft.getInstance().setScreen(new RenaissanceRarityScreen(this, cfg)))
                    .bounds(width - 110, 10, 100, 18).build());
        }

        if (tab == 0) buildSacrificeWidgets();
        else if (tab == 2) buildForgeWidgets();
        else if (tab == 3) buildHeritageWidgets();
        else buildShopWidgets();
    }

    // ─── Héritage (1.5) : 3 branches de 5 nœuds + Renaissance ───

    private static final com.wavesurvivor.horde.renaissance.Heritage.Branch[] BRANCHES = com.wavesurvivor.horde.renaissance.Heritage.Branch.values();
    private boolean rebirthArmed = false;

    private int colX(int i) { return left + 8 + i * 83; }

    private void buildHeritageWidgets() {
        var H = com.wavesurvivor.horde.renaissance.Heritage.class;
        int[] nodes = com.wavesurvivor.horde.renaissance.Heritage.CLIENT_NODES;
        for (int i = 0; i < BRANCHES.length; i++) {
            int lvl = nodes[i];
            final String id = BRANCHES[i].name();
            String lbl = lvl >= com.wavesurvivor.horde.renaissance.Heritage.MAX_NODE ? com.wavesurvivor.i18n.WSLang.t("heritage.btn_max")
                    : com.wavesurvivor.i18n.WSLang.t("heritage.btn_buy", com.wavesurvivor.horde.renaissance.Heritage.NODE_COST[lvl]);
            Button b = addRenderableWidget(Button.builder(Component.literal(lbl),
                    bt -> NetworkHandler.CHANNEL.sendToServer(new com.wavesurvivor.network.RelicForgePacket("heritage", id)))
                    .bounds(colX(i), top + 182, 78, 16).build());
            b.active = lvl < com.wavesurvivor.horde.renaissance.Heritage.MAX_NODE
                    && points >= com.wavesurvivor.horde.renaissance.Heritage.NODE_COST[lvl];
        }
        // Renaissance (deux clics : armer puis confirmer)
        int rank = com.wavesurvivor.horde.renaissance.Heritage.CLIENT_RANK;
        boolean can = rank < com.wavesurvivor.horde.renaissance.Heritage.MAX_RANK
                && com.wavesurvivor.horde.renaissance.Heritage.CLIENT_INVESTED >= com.wavesurvivor.horde.renaissance.Heritage.RANK_THRESHOLD[Math.min(4, rank)]
                && com.wavesurvivor.horde.renaissance.Heritage.CLIENT_WON;
        Button rb = addRenderableWidget(Button.builder(Component.literal(rebirthArmed ? com.wavesurvivor.i18n.WSLang.t("heritage.btn_confirm")
                        : com.wavesurvivor.i18n.WSLang.t("heritage.btn_rebirth")),
                bt -> {
                    if (!rebirthArmed) { rebirthArmed = true; rebuild(); return; }
                    rebirthArmed = false;
                    NetworkHandler.CHANNEL.sendToServer(new com.wavesurvivor.network.RelicForgePacket("rebirth", ""));
                }).bounds(left + 8, top + PANEL_H - 30, PANEL_W - 16, 18).build());
        rb.active = can;
        if (!can) rebirthArmed = false;
    }

    private List<Component> renderHeritage(GuiGraphics g, int mx, int my) {
        List<Component> tip = null;
        int rank = com.wavesurvivor.horde.renaissance.Heritage.CLIENT_RANK;
        int inv = com.wavesurvivor.horde.renaissance.Heritage.CLIENT_INVESTED;
        boolean won = com.wavesurvivor.horde.renaissance.Heritage.CLIENT_WON;
        // En-tête : rang, titre, progression vers la prochaine Renaissance
        String title = rank > 0 ? com.wavesurvivor.i18n.WSLang.t(com.wavesurvivor.horde.renaissance.Heritage.titleKey(rank)) : "—";
        g.drawString(font, com.wavesurvivor.i18n.WSLang.t("heritage.header", rank, title), left + 8, top + 44, 0xFFE0B040, false);
        if (rank < com.wavesurvivor.horde.renaissance.Heritage.MAX_RANK) {
            int need = com.wavesurvivor.horde.renaissance.Heritage.RANK_THRESHOLD[rank];
            g.drawString(font, com.wavesurvivor.i18n.WSLang.t("heritage.progress", Math.min(inv, need), need) + (won ? " §a✔" : " §c✘")
                    + com.wavesurvivor.i18n.WSLang.t("heritage.won"), left + 8, top + 54, 0xFFBBBBBB, false);
        } else {
            g.drawString(font, com.wavesurvivor.i18n.WSLang.t("heritage.max_rank"), left + 8, top + 54, 0xFF55FFFF, false);
        }
        // 3 colonnes de 5 nœuds
        int[] nodes = com.wavesurvivor.horde.renaissance.Heritage.CLIENT_NODES;
        for (int i = 0; i < BRANCHES.length; i++) {
            int x = colX(i);
            String key = "heritage." + BRANCHES[i].name().toLowerCase();
            g.drawString(font, "§l" + com.wavesurvivor.i18n.WSLang.t(key), x, top + 68, 0xFFFFFFFF, false);
            for (int n = 1; n <= com.wavesurvivor.horde.renaissance.Heritage.MAX_NODE; n++) {
                int y = top + 78 + (n - 1) * 20;
                boolean owned = nodes[i] >= n;
                boolean next = nodes[i] == n - 1;
                int bg = owned ? 0xFF2E7D32 : next ? 0xFF8D6E00 : 0xFF2A2A33;
                g.fill(x, y, x + 78, y + 18, bg);
                g.renderOutline(x, y, 78, 18, owned ? 0xFF66BB6A : next ? 0xFFFFD54F : 0xFF444455);
                String txt = com.wavesurvivor.i18n.WSLang.t(key + "." + n);
                String shown = font.width(txt) > 70 ? font.plainSubstrByWidth(txt, 66) + "…" : txt;
                g.drawString(font, shown, x + 4, y + 5, owned ? 0xFFFFFFFF : next ? 0xFFFFF3C4 : 0xFF777788, false);
                if (mx >= x && mx < x + 78 && my >= y && my < y + 18) {
                    tip = new ArrayList<>();
                    tip.add(Component.literal((owned ? "§a✔ " : next ? "§e" : "§8") + txt));
                    if (!owned) tip.add(Component.literal(com.wavesurvivor.i18n.WSLang.t("heritage.cost",
                            com.wavesurvivor.horde.renaissance.Heritage.NODE_COST[n - 1])));
                    if (!owned && !next) tip.add(Component.literal(com.wavesurvivor.i18n.WSLang.t("heritage.locked")));
                }
            }
        }
        // Survol du bouton Renaître : ce qui se passe
        if (mx >= left + 8 && mx < left + PANEL_W - 8 && my >= top + PANEL_H - 30 && my < top + PANEL_H - 12) {
            tip = new ArrayList<>();
            for (String l : com.wavesurvivor.i18n.WSLang.t("heritage.rebirth_tip").split("\n")) tip.add(Component.literal(l));
        }
        return tip;
    }

    private void unusedHeritage() {
    }

    // ─── Forge (1.5) : améliorer les reliques (doublon + PR), fusionner (lot 3) ───

    private final List<net.minecraft.world.item.Item> forgeRows = new ArrayList<>();
    private String forgeSig = "";

    private static String roman(int l) { return l >= 3 ? "III" : l == 2 ? "II" : "I"; }

    private void buildForgeWidgets() {
        forgeRows.clear();
        var mcp = Minecraft.getInstance().player;
        if (mcp == null) return;
        var inv = mcp.getInventory().items;
        forgeSig = com.wavesurvivor.item.RelicForge.relicsIn(inv).toString() + com.wavesurvivor.item.RelicEquip.CLIENT.size();
        // Sous-vues : Améliorer / Fusionner (/ Équipement sans Curios)
        boolean noCurios = !com.wavesurvivor.item.RelicEffects.curiosAvailable();
        int sw = noCurios ? 80 : 120;
        Button up = addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("forge.sub_upgrade")),
                b -> { forgeSub = 0; scroll = 0; rebuild(); }).bounds(left + 8, top + 42, sw, 14).build());
        Button fu = addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("forge.sub_fuse")),
                b -> { forgeSub = 1; scroll = 0; rebuild(); }).bounds(left + 8 + sw + 2, top + 42, sw, 14).build());
        up.active = forgeSub != 0;
        fu.active = forgeSub != 1;
        if (noCurios) {
            Button eqb = addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("forge.sub_equip")),
                    b -> { forgeSub = 2; scroll = 0; rebuild(); }).bounds(left + 8 + 2 * (sw + 2), top + 42, sw, 14).build());
            eqb.active = forgeSub != 2;
            if (forgeSub == 2) { buildEquipWidgets(inv); return; }
        } else if (forgeSub == 2) forgeSub = 0;
        if (forgeSub == 1) { buildFusionWidgets(inv); return; }
        forgeRows.addAll(com.wavesurvivor.item.RelicForge.relicsIn(inv).keySet());
        scroll = Math.max(0, Math.min(scroll, Math.max(0, forgeRows.size() - VISIBLE_ROWS)));
        int y = top + 62;
        for (int i = scroll; i < Math.min(forgeRows.size(), scroll + VISIBLE_ROWS); i++) {
            net.minecraft.world.item.Item it = forgeRows.get(i);
            var plan = com.wavesurvivor.item.RelicForge.planUpgrade(inv, it);
            int best = 0;
            for (ItemStack st : inv) if (st.is(it)) best = Math.max(best, com.wavesurvivor.item.RelicEffects.levelOf(st));
            String lbl = plan != null ? com.wavesurvivor.i18n.WSLang.t("forge.btn_upgrade", roman(plan.fromLevel() + 1), plan.cost())
                    : it instanceof com.wavesurvivor.item.LegendaryRelicItem ? com.wavesurvivor.i18n.WSLang.t("forge.btn_legend")
                    : best >= 3 ? com.wavesurvivor.i18n.WSLang.t("forge.btn_max") : com.wavesurvivor.i18n.WSLang.t("forge.btn_need");
            final String id = BuiltInRegistries.ITEM.getKey(it).toString();
            Button b = addRenderableWidget(Button.builder(Component.literal(lbl),
                    bt -> NetworkHandler.CHANNEL.sendToServer(new com.wavesurvivor.network.RelicForgePacket("upgrade", id)))
                    .bounds(left + PANEL_W - 104, y, 96, 18).build());
            b.active = plan != null && points >= plan.cost();
            y += ROW_H;
        }
    }

    /** L'inventaire change (doublon consommé, niveau gagné) : la liste de la Forge se remet à jour. */
    private void tickForge() {
        if (tab != 2) return;
        var mcp = Minecraft.getInstance().player;
        if (mcp != null && !(com.wavesurvivor.item.RelicForge.relicsIn(mcp.getInventory().items).toString()
                + com.wavesurvivor.item.RelicEquip.CLIENT.size()).equals(forgeSig)) rebuild();
    }

    /** Forge : 0 = améliorer, 1 = fusionner, 2 = équipement (sans Curios). */
    private int forgeSub = 0;

    /** Équipement (sans Curios) : lignes = reliques équipées (rang ≥ 0) puis reliques du sac (case = -1 - slot). */
    private final List<Integer> equipRows = new ArrayList<>();

    private void buildEquipWidgets(List<ItemStack> inv) {
        equipRows.clear();
        var eq = com.wavesurvivor.item.RelicEquip.CLIENT;
        for (int i = 0; i < eq.size(); i++) equipRows.add(i);
        for (int s = 0; s < Math.min(36, inv.size()); s++) {
            if (inv.get(s).getItem() instanceof com.wavesurvivor.item.RelicItem) equipRows.add(-1 - s);
        }
        var mcp = Minecraft.getInstance().player;
        int max = com.wavesurvivor.item.RelicEquip.slots(mcp);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, equipRows.size() - VISIBLE_ROWS)));
        int y = top + 62;
        for (int i = scroll; i < Math.min(equipRows.size(), scroll + VISIBLE_ROWS); i++) {
            int r = equipRows.get(i);
            boolean equipped = r >= 0;
            final String id = String.valueOf(equipped ? r : -1 - r);
            Button b = addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t(equipped ? "equip.btn_remove" : "equip.btn_equip")),
                    bt -> NetworkHandler.CHANNEL.sendToServer(new com.wavesurvivor.network.RelicForgePacket(equipped ? "unequip" : "equip", id)))
                    .bounds(left + PANEL_W - 84, y, 76, 18).build());
            if (!equipped) b.active = eq.size() < max;
            y += ROW_H;
        }
    }

    private List<Component> renderEquip(GuiGraphics g, int mx, int my) {
        List<Component> tip = null;
        var mcp = Minecraft.getInstance().player;
        if (mcp == null) return null;
        var inv = mcp.getInventory().items;
        var eq = com.wavesurvivor.item.RelicEquip.CLIENT;
        g.drawCenteredString(font, com.wavesurvivor.i18n.WSLang.t("equip.count", eq.size(), com.wavesurvivor.item.RelicEquip.slots(mcp)),
                width / 2, top + PANEL_H - 12, 0xFFE0B040);
        int y = top + 62;
        for (int i = scroll; i < Math.min(equipRows.size(), scroll + VISIBLE_ROWS); i++) {
            int r = equipRows.get(i);
            ItemStack st = r >= 0 ? (r < eq.size() ? eq.get(r) : ItemStack.EMPTY) : inv.get(-1 - r);
            if (st.isEmpty()) { y += ROW_H; continue; }
            g.fill(left + 6, y - 2, left + PANEL_W - 6, y + ROW_H - 4, r >= 0 ? 0x4040C060 : 0x20FFFFFF);
            g.renderItem(st, left + 10, y);
            String name = st.getHoverName().getString();
            if (font.width(name) > 130) name = font.plainSubstrByWidth(name, 126) + "…";
            g.drawString(font, (r >= 0 ? "§a✔ " : "§7") + name, left + 30, y + 4, 0xFFFFFFFF, false);
            if (mx >= left + 10 && mx < left + 26 && my >= y && my < y + 16) {
                tip = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), st));
            }
            y += ROW_H;
        }
        if (equipRows.isEmpty()) g.drawCenteredString(font, com.wavesurvivor.i18n.WSLang.t("forge.empty"), width / 2, top + 90, 0xFF888888);
        return tip;
    }

    private void buildFusionWidgets(List<ItemStack> inv) {
        var all = com.wavesurvivor.item.RelicFusions.all();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, all.size() - VISIBLE_ROWS)));
        int y = top + 62;
        for (int i = scroll; i < Math.min(all.size(), scroll + VISIBLE_ROWS); i++) {
            var f = all.get(i);
            boolean ok = com.wavesurvivor.item.RelicFusions.canFuse(inv, f);
            Button b = addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("forge.btn_fuse", com.wavesurvivor.item.RelicFusions.FUSE_COST)),
                    bt -> NetworkHandler.CHANNEL.sendToServer(new com.wavesurvivor.network.RelicForgePacket("fuse", f.id())))
                    .bounds(left + PANEL_W - 84, y, 76, 18).build());
            b.active = ok && points >= com.wavesurvivor.item.RelicFusions.FUSE_COST;
            y += ROW_H;
        }
    }

    private List<Component> renderFusions(GuiGraphics g, int mx, int my) {
        List<Component> tip = null;
        var mcp = Minecraft.getInstance().player;
        if (mcp == null) return null;
        var inv = mcp.getInventory().items;
        var all = com.wavesurvivor.item.RelicFusions.all();
        int y = top + 62;
        for (int i = scroll; i < Math.min(all.size(), scroll + VISIBLE_ROWS); i++) {
            var f = all.get(i);
            ItemStack a = new ItemStack(f.a().get()), b = new ItemStack(f.b().get()), r = new ItemStack(f.result().get());
            boolean hasA = com.wavesurvivor.item.RelicFusions.findSlot(inv, f.a().get(), com.wavesurvivor.item.RelicFusions.MIN_LEVEL, -1) >= 0;
            boolean hasB = com.wavesurvivor.item.RelicFusions.findSlot(inv, f.b().get(), com.wavesurvivor.item.RelicFusions.MIN_LEVEL, -1) >= 0;
            g.fill(left + 6, y - 2, left + PANEL_W - 6, y + ROW_H - 4, 0x30E0B040);
            g.renderItem(a, left + 10, y);
            g.drawString(font, hasA ? "§a✔" : "§c✘", left + 27, y + 4, 0xFFFFFFFF, false);
            g.drawString(font, "§7+", left + 36, y + 4, 0xFFFFFFFF, false);
            g.renderItem(b, left + 44, y);
            g.drawString(font, hasB ? "§a✔" : "§c✘", left + 61, y + 4, 0xFFFFFFFF, false);
            g.drawString(font, "§6→", left + 72, y + 4, 0xFFFFFFFF, false);
            g.renderItem(r, left + 82, y);
            String name = r.getHoverName().getString();
            if (font.width(name) > 70) name = font.plainSubstrByWidth(name, 66) + "…";
            g.drawString(font, "§6" + name, left + 100, y + 4, 0xFFFFFFFF, false);
            int[][] icons = {{left + 10, 0}, {left + 44, 1}, {left + 82, 2}};
            for (int[] ic : icons) {
                if (mx >= ic[0] && mx < ic[0] + 16 && my >= y && my < y + 16) {
                    ItemStack st = ic[1] == 0 ? a : ic[1] == 1 ? b : r;
                    tip = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), st));
                    if (ic[1] < 2) tip.add(Component.literal(com.wavesurvivor.i18n.WSLang.t("forge.need_level_ii")));
                }
            }
            y += ROW_H;
        }
        if (all.size() > VISIBLE_ROWS) {
            g.drawCenteredString(font, com.wavesurvivor.i18n.WSLang.t("ui.molette") + (scroll + 1) + "-"
                    + Math.min(all.size(), scroll + VISIBLE_ROWS) + " / " + all.size(), width / 2, top + PANEL_H - 12, 0xFFFFFFFF);
        }
        return tip;
    }

    private List<Component> renderForge(GuiGraphics g, int mx, int my) {
        if (forgeSub == 1) return renderFusions(g, mx, my);
        if (forgeSub == 2) return renderEquip(g, mx, my);
        List<Component> tip = null;
        var mcp = Minecraft.getInstance().player;
        if (mcp == null) return null;
        var rel = com.wavesurvivor.item.RelicForge.relicsIn(mcp.getInventory().items);
        if (forgeRows.isEmpty()) {
            g.drawCenteredString(font, com.wavesurvivor.i18n.WSLang.t("forge.empty"), width / 2, top + 90, 0xFF888888);
        }
        int y = top + 62;
        for (int i = scroll; i < Math.min(forgeRows.size(), scroll + VISIBLE_ROWS); i++) {
            net.minecraft.world.item.Item it = forgeRows.get(i);
            ItemStack icon = new ItemStack(it);
            g.fill(left + 6, y - 2, left + PANEL_W - 6, y + ROW_H - 4, 0x30A855F7);
            g.renderItem(icon, left + 10, y);
            String name = icon.getHoverName().getString();
            if (font.width(name) > 110) name = font.plainSubstrByWidth(name, 106) + "…";
            g.drawString(font, "§f" + name, left + 30, y + 1, 0xFFFFFFFF, false);
            // Copies par niveau : « I×2  II×1 »
            int[] byLvl = new int[4];
            for (int l : rel.getOrDefault(it, List.of())) byLvl[Math.max(1, Math.min(3, l))]++;
            StringBuilder sb = new StringBuilder("§7");
            for (int l = 1; l <= 3; l++) if (byLvl[l] > 0) sb.append(roman(l)).append("×").append(byLvl[l]).append("  ");
            g.drawString(font, sb.toString(), left + 30, y + 10, 0xFFFFFFFF, false);
            if (mx >= left + 10 && mx < left + 26 && my >= y && my < y + 16) {
                tip = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), icon));
            }
            y += ROW_H;
        }
        if (forgeRows.size() > VISIBLE_ROWS) {
            g.drawCenteredString(font, com.wavesurvivor.i18n.WSLang.t("ui.molette") + (scroll + 1) + "-"
                    + Math.min(forgeRows.size(), scroll + VISIBLE_ROWS) + " / " + forgeRows.size(), width / 2, top + PANEL_H - 12, 0xFFFFFFFF);
        }
        return tip;
    }

    private void switchTab(int t) {
        tab = t;
        confirmArmed = false;
        scroll = 0;
        rebuild();
    }

    private void buildSacrificeWidgets() {
        int by = top + PANEL_H - 26;
        addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.tout")), b -> selectAll())
                .bounds(left + 8, by, 44, 18).build());
        addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.vider")), b -> {
            selected.clear(); selectedIds.clear(); confirmArmed = false; refreshSacrificeBtn();
        }).bounds(left + 56, by, 44, 18).build());
        sacrificeBtn = addRenderableWidget(Button.builder(Component.literal(""), b -> onSacrificeClick())
                .bounds(left + PANEL_W - 142, by, 134, 18).build());
        refreshSacrificeBtn();
    }

    private void refreshSacrificeBtn() {
        if (sacrificeBtn == null) return;
        int total = selectionValue();
        sacrificeBtn.active = total > 0;
        String label = confirmArmed
                ? com.wavesurvivor.i18n.WSLang.t("ui.confirmer") + total + com.wavesurvivor.i18n.WSLang.t("ui.pr_fec1")
                : com.wavesurvivor.i18n.WSLang.t("ui.sacrifier") + total + com.wavesurvivor.i18n.WSLang.t("ui.pr_fec1");
        sacrificeBtn.setMessage(Component.literal(label));
    }

    private void onSacrificeClick() {
        if (selectionValue() <= 0) return;
        if (!confirmArmed) {
            confirmArmed = true;
            refreshSacrificeBtn();
            return;
        }
        List<RenaissanceSacrificePacket.Entry> list = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : selected.entrySet()) {
            list.add(new RenaissanceSacrificePacket.Entry(e.getKey(), e.getValue(), selectedIds.get(e.getKey())));
        }
        NetworkHandler.CHANNEL.sendToServer(new RenaissanceSacrificePacket(list));
        confirmArmed = false;
        refreshSacrificeBtn();
    }

    private void buildShopWidgets() {
        rows.clear();
        if (skillTreeAvailable) {
            rows.add(header(com.wavesurvivor.i18n.WSLang.t("ui.competences")));
            rows.add(new ShopRow(Kind.SKILL, RenaissanceConfigData.SKILL_POINT_ID, new ItemStack(Items.EXPERIENCE_BOTTLE),
                    com.wavesurvivor.i18n.WSLang.t("ui.point_de_competence"), cfg.skillPointCost, 0, 0));
        }
        if (statsAvailable && !cfg.statUpgrades.isEmpty()) {
            rows.add(header(com.wavesurvivor.i18n.WSLang.t("ui.stats_permanentes")));
            for (RenaissanceConfigData.StatUpgrade s : cfg.statUpgrades) {
                int lvl = purchases.getOrDefault("stat:" + s.id, 0);
                rows.add(new ShopRow(Kind.STAT, "stat:" + s.id, s.iconStack(), com.wavesurvivor.i18n.WSLang.t(s.name), s.costFor(lvl), s.maxLevel, lvl));
            }
        }
        boolean relicHeader = false;
        if (editMode) { rows.add(header(com.wavesurvivor.i18n.WSLang.t("ui.reliques_edition"))); relicHeader = true; }
        for (RenaissanceConfigData.ShopItem s : cfg.shopItems) {
            ItemStack icon = RenaissanceConfigData.buildStack(s);
            if (icon.isEmpty()) continue;
            if (!relicHeader) { rows.add(header(com.wavesurvivor.i18n.WSLang.t("ui.reliques"))); relicHeader = true; }
            String name = (s.count > 1 ? s.count + "× " : "") + com.wavesurvivor.i18n.WSLang.t(icon.getHoverName().getString());
            rows.add(new ShopRow(Kind.RELIC, s.id, icon, name, s.cost, s.maxPerPlayer, purchases.getOrDefault(s.id, 0)));
        }
        int maxScroll = Math.max(0, rows.size() - VISIBLE_ROWS);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        int y = top + 48;
        for (int i = scroll; i < Math.min(rows.size(), scroll + VISIBLE_ROWS); i++) {
            ShopRow r = rows.get(i);
            int bx = left + PANEL_W - 70;
            switch (r.kind()) {
                case SKILL -> {
                    Button b1 = addRenderableWidget(Button.builder(Component.literal("×1"),
                            b -> NetworkHandler.CHANNEL.sendToServer(new RenaissanceBuyPacket(r.id(), 1)))
                            .bounds(bx, y + 2, 30, 18).build());
                    Button b5 = addRenderableWidget(Button.builder(Component.literal("×5"),
                            b -> NetworkHandler.CHANNEL.sendToServer(new RenaissanceBuyPacket(r.id(), 5)))
                            .bounds(bx + 32, y + 2, 30, 18).build());
                    b1.active = points >= r.cost();
                    b5.active = points >= r.cost() * 5;
                }
                case STAT -> {
                    boolean maxed = r.bought() >= r.limit();
                    Button b = addRenderableWidget(Button.builder(
                                    Component.literal(maxed ? com.wavesurvivor.i18n.WSLang.t("ui.max_93f1") : com.wavesurvivor.i18n.WSLang.t("ui.ameliorer")),
                                    btn -> NetworkHandler.CHANNEL.sendToServer(new RenaissanceBuyPacket(r.id(), 1)))
                            .bounds(bx, y + 2, 62, 18).build());
                    b.active = !maxed && points >= r.cost();
                }
                case RELIC -> {
                    boolean soldOut = r.limit() > 0 && r.bought() >= r.limit();
                    Button b = addRenderableWidget(Button.builder(
                                    Component.literal(soldOut ? com.wavesurvivor.i18n.WSLang.t("ui.obtenu") : com.wavesurvivor.i18n.WSLang.t("ui.acheter")),
                                    btn -> NetworkHandler.CHANNEL.sendToServer(new RenaissanceBuyPacket(r.id(), 1)))
                            .bounds(bx, y + 2, 62, 18).build());
                    b.active = !soldOut && points >= r.cost();
                    if (editMode) {
                        addRenderableWidget(Button.builder(Component.literal("§e✎"),
                                        btn -> Minecraft.getInstance().setScreen(
                                                new RenaissanceShopEditorScreen(this, cfg.findShop(r.id()))))
                                .bounds(bx - 44, y + 2, 20, 18).build());
                        boolean armed = r.id().equals(pendingDelete);
                        addRenderableWidget(Button.builder(Component.literal(armed ? "§c§l?" : "§c✖"),
                                        btn -> {
                                            if (r.id().equals(pendingDelete)) {
                                                NetworkHandler.CHANNEL.sendToServer(new RenaissanceShopEditPacket(
                                                        RenaissanceShopEditPacket.DELETE, r.id()));
                                                pendingDelete = null;
                                            } else {
                                                pendingDelete = r.id();
                                                rebuild();
                                            }
                                        })
                                .bounds(bx - 22, y + 2, 20, 18).build());
                    }
                }
                default -> { }
            }
            y += ROW_H;
        }
        if (editMode) {
            addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.ajouter_une_relique")),
                            b -> Minecraft.getInstance().setScreen(new RenaissanceShopEditorScreen(this, null)))
                    .bounds(width / 2 - 70, top + PANEL_H - 22, 140, 18).build());
        }
    }

    private static ShopRow header(String title) {
        return new ShopRow(Kind.HEADER, "", ItemStack.EMPTY, title, 0, 0, 0);
    }

    // ─── Sélection ───

    private List<ItemStack> inv() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getInventory().items : List.of();
    }

    private static String idOf(ItemStack st) {
        ResourceLocation k = BuiltInRegistries.ITEM.getKey(st.getItem());
        return k != null ? k.toString() : "";
    }

    private void selectAll() {
        List<ItemStack> items = inv();
        for (int slot = 0; slot < items.size() && slot < 36; slot++) {
            ItemStack st = items.get(slot);
            if (cfg.rarityOf(st) != null) {
                selected.put(slot, st.getCount());
                selectedIds.put(slot, idOf(st));
            }
        }
        confirmArmed = false;
        refreshSacrificeBtn();
    }

    private int selectionValue() {
        // Groupé par rareté : le ratio « N items = 1 PR » s'applique au total de chaque rareté
        Map<RenaissanceConfigData.Rarity, Integer> byRarity = new LinkedHashMap<>();
        List<ItemStack> items = inv();
        for (Map.Entry<Integer, Integer> e : selected.entrySet()) {
            int slot = e.getKey();
            if (slot >= items.size()) continue;
            RenaissanceConfigData.Rarity r = cfg.rarityOf(items.get(slot));
            if (r != null) byRarity.merge(r, e.getValue(), Integer::sum);
        }
        int total = 0;
        for (Map.Entry<RenaissanceConfigData.Rarity, Integer> e : byRarity.entrySet()) total += e.getKey().pointsFor(e.getValue());
        return total;
    }

    @Override
    public void tick() {
        super.tick();
        tickForge();
        // Si l'inventaire bouge (pickup, drop...), on nettoie la sélection devenue invalide
        List<ItemStack> items = inv();
        boolean changed = false;
        Iterator<Map.Entry<Integer, Integer>> it = selected.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Integer> e = it.next();
            int slot = e.getKey();
            ItemStack st = slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
            if (st.isEmpty() || !idOf(st).equals(selectedIds.get(slot)) || cfg.rarityOf(st) == null) {
                it.remove();
                selectedIds.remove(slot);
                changed = true;
            } else if (e.getValue() > st.getCount()) {
                e.setValue(st.getCount());
                changed = true;
            }
        }
        if (changed) {
            confirmArmed = false;
            refreshSacrificeBtn();
        }
    }

    /** Slot inventaire sous la souris (grille : 3 rangées 9..35 puis hotbar 0..8), -1 sinon. */
    private int slotAt(double mx, double my) {
        int gx = left + (PANEL_W - 9 * CELL) / 2;
        int gy = top + 48;
        for (int row = 0; row < 4; row++) {
            int ry = gy + row * CELL + (row == 3 ? 4 : 0);
            for (int col = 0; col < 9; col++) {
                int rx = gx + col * CELL;
                if (mx >= rx && mx < rx + CELL && my >= ry && my < ry + CELL) {
                    return row == 3 ? col : 9 + row * 9 + col;
                }
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (tab == 0) {
            int slot = slotAt(mx, my);
            if (slot >= 0) {
                List<ItemStack> items = inv();
                ItemStack st = slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
                if (!st.isEmpty() && cfg.rarityOf(st) != null) {
                    int cur = selected.getOrDefault(slot, 0);
                    int next;
                    if (button == 0) {
                        next = cur > 0 ? 0 : st.getCount();              // tout / rien
                    } else if (button == 1) {
                        next = hasShiftDown() ? cur - 1 : cur + 1;        // +1 / -1
                        if (next > st.getCount()) next = 0;
                    } else {
                        return super.mouseClicked(mx, my, button);
                    }
                    if (next <= 0) { selected.remove(slot); selectedIds.remove(slot); }
                    else { selected.put(slot, next); selectedIds.put(slot, idOf(st)); }
                    confirmArmed = false;
                    refreshSacrificeBtn();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (tab == 2 && forgeSub == 1 && com.wavesurvivor.item.RelicFusions.all().size() > VISIBLE_ROWS) {
            int old = scroll;
            scroll = Math.max(0, Math.min(com.wavesurvivor.item.RelicFusions.all().size() - VISIBLE_ROWS, scroll - (int) Math.signum(delta)));
            if (old != scroll) rebuild();
            return true;
        }
        if (tab == 2 && forgeRows.size() > VISIBLE_ROWS) {
            int old = scroll;
            scroll = Math.max(0, Math.min(forgeRows.size() - VISIBLE_ROWS, scroll - (int) Math.signum(delta)));
            if (old != scroll) rebuild();
            return true;
        }
        if (tab == 1 && rows.size() > VISIBLE_ROWS) {
            int old = scroll;
            scroll = Math.max(0, Math.min(rows.size() - VISIBLE_ROWS, scroll - (int) Math.signum(delta)));
            if (old != scroll) rebuild();
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    // ─── Rendu ───

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);

        // Panneau
        g.fill(left, top, left + PANEL_W, top + PANEL_H, 0xF0140A1E);
        g.fill(left, top, left + PANEL_W, top + 2, 0xFFA855F7);
        g.fill(left, top + PANEL_H - 2, left + PANEL_W, top + PANEL_H, 0xFFA855F7);

        g.drawCenteredString(font, com.wavesurvivor.i18n.WSLang.t("ui.renaissance_c721"), width / 2, top + 8, 0xFFFFFFFF);
        String pr = "§d✦ " + points + com.wavesurvivor.i18n.WSLang.t("ui.pr");
        g.drawString(font, pr, left + PANEL_W - 8 - font.width(pr), top + 8, 0xFFFFFFFF);

        super.render(g, mx, my, pt);

        List<Component> tooltip = null;
        if (tab == 0) tooltip = renderSacrifice(g, mx, my);
        else if (tab == 2) tooltip = renderForge(g, mx, my);
        else if (tab == 3) tooltip = renderHeritage(g, mx, my);
        else tooltip = renderShop(g, mx, my);

        if (tooltip != null && !tooltip.isEmpty()) {
            g.renderComponentTooltip(font, tooltip, mx, my);
        }
    }

    private List<Component> renderSacrifice(GuiGraphics g, int mx, int my) {
        List<ItemStack> items = inv();
        int gx = left + (PANEL_W - 9 * CELL) / 2;
        int gy = top + 48;
        List<Component> tooltip = null;
        int hovered = slotAt(mx, my);

        for (int row = 0; row < 4; row++) {
            int ry = gy + row * CELL + (row == 3 ? 4 : 0);
            for (int col = 0; col < 9; col++) {
                int slot = row == 3 ? col : 9 + row * 9 + col;
                int rx = gx + col * CELL;
                ItemStack st = slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
                RenaissanceConfigData.Rarity r = cfg.rarityOf(st);
                int sel = selected.getOrDefault(slot, 0);

                // Fond de case + bordure rareté
                g.fill(rx, ry, rx + CELL - 1, ry + CELL - 1, sel > 0 ? 0xFF4C1D6E : 0xFF26202E);
                if (r != null) {
                    int c = r.argb();
                    g.fill(rx, ry, rx + CELL - 1, ry + 1, c);
                    g.fill(rx, ry + CELL - 2, rx + CELL - 1, ry + CELL - 1, c);
                    g.fill(rx, ry, rx + 1, ry + CELL - 1, c);
                    g.fill(rx + CELL - 2, ry, rx + CELL - 1, ry + CELL - 1, c);
                }
                if (st.isEmpty()) continue;

                g.renderItem(st, rx + 1, ry + 1);
                String countText = sel > 0 ? "§d" + sel + (sel < st.getCount() ? "/" + st.getCount() : "") : null;
                g.renderItemDecorations(font, st, rx + 1, ry + 1, countText);

                if (r == null) {
                    // Assombrit les items non sacrifiables (au-dessus des items)
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 300);
                    g.fill(rx, ry, rx + CELL - 1, ry + CELL - 1, 0xAA000000);
                    g.pose().popPose();
                }

                if (slot == hovered) {
                    tooltip = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), st));
                    if (r != null) {
                        tooltip.add(Component.literal(""));
                        tooltip.add(Component.literal(r.chatColor() + com.wavesurvivor.i18n.WSLang.t(r.name) + " §7→ §d" + r.rateLabel()));
                        tooltip.add(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.clic_gauche_tout_rien_clic_droit_1_shift")));
                    } else {
                        tooltip.add(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.non_sacrifiable")));
                    }
                }
            }
        }

        // Récap par rareté
        Map<RenaissanceConfigData.Rarity, Integer> byRarity = new LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> e : selected.entrySet()) {
            int slot = e.getKey();
            if (slot >= items.size()) continue;
            RenaissanceConfigData.Rarity r = cfg.rarityOf(items.get(slot));
            if (r != null) byRarity.merge(r, e.getValue(), Integer::sum);
        }
        int ty = gy + 4 * CELL + 10;
        if (byRarity.isEmpty()) {
            g.drawCenteredString(font, com.wavesurvivor.i18n.WSLang.t("ui.clique_sur_les_items_a_offrir_cadres_col"), width / 2, ty, 0xFFFFFFFF);
            g.drawCenteredString(font, com.wavesurvivor.i18n.WSLang.t("ui.armure_et_main_secondaire_ne_sont_jamais"), width / 2, ty + 11, 0xFFFFFFFF);
        } else {
            for (Map.Entry<RenaissanceConfigData.Rarity, Integer> e : byRarity.entrySet()) {
                RenaissanceConfigData.Rarity r = e.getKey();
                int kept = e.getValue() - r.consumedFor(e.getValue());
                g.drawString(font, "§7• §f" + e.getValue() + "× " + r.chatColor() + com.wavesurvivor.i18n.WSLang.t(r.name)
                        + " §8→ §d+" + r.pointsFor(e.getValue()) + com.wavesurvivor.i18n.WSLang.t("ui.pr")
                        + (kept > 0 ? " §8(" + com.wavesurvivor.i18n.WSLang.t("ui.sacrifice_not_used", kept) + ")" : ""), left + 30, ty, 0xFFFFFFFF);
                ty += 11;
            }
        }
        return tooltip;
    }

    private List<Component> renderShop(GuiGraphics g, int mx, int my) {
        List<Component> tooltip = null;
        if (rows.isEmpty()) {
            g.drawCenteredString(font, com.wavesurvivor.i18n.WSLang.t("ui.la_boutique_est_vide"), width / 2, top + 80, 0xFFFFFFFF);
            return null;
        }
        int y = top + 48;
        for (int i = scroll; i < Math.min(rows.size(), scroll + VISIBLE_ROWS); i++) {
            ShopRow r = rows.get(i);
            if (r.kind() == Kind.HEADER) {
                g.drawCenteredString(font, r.name(), width / 2, y + 7, 0xFFFFFFFF);
                y += ROW_H;
                continue;
            }
            g.fill(left + 6, y, left + PANEL_W - 6, y + ROW_H - 2, (i % 2 == 0) ? 0x40FFFFFF : 0x20FFFFFF);
            g.renderItem(r.icon(), left + 10, y + 2);

            g.drawString(font, fit(r.name(), editMode && r.kind() == Kind.RELIC ? PANEL_W - 150 : PANEL_W - 110),
                    left + 32, y + 2, 0xFFFFFFFF);
            String costTxt = (points >= r.cost() ? "§d" : "§c") + r.cost() + com.wavesurvivor.i18n.WSLang.t("ui.pr");
            String sub;
            RenaissanceConfigData.StatUpgrade stat = r.kind() == Kind.STAT ? cfg.findStat(r.id().substring(5)) : null;
            switch (r.kind()) {
                case SKILL -> sub = costTxt + " §8/ point";
                case STAT -> {
                    String bonus = stat != null && r.bought() > 0 ? " §a" + stat.formatTotal(r.bought()) : "";
                    sub = r.bought() >= r.limit()
                            ? com.wavesurvivor.i18n.WSLang.t("ui.niv_max") + bonus
                            : com.wavesurvivor.i18n.WSLang.t("ui.niv_988b") + r.bought() + "/" + r.limit() + bonus + " §8· " + costTxt;
                }
                default -> sub = costTxt + (r.limit() > 0 ? " §8· " + r.bought() + "/" + r.limit() : "");
            }
            g.drawString(font, sub, left + 32, y + 11, 0xFFFFFFFF);

            if (mx >= left + 8 && mx < left + 28 && my >= y && my < y + ROW_H) {
                switch (r.kind()) {
                    case SKILL -> tooltip = List.of(Component.literal(com.wavesurvivor.i18n.WSLang.t("§aPoint de compétence")),
                            Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.ajoute_des_points_dans_ton_arbre_de_comp")));
                    case STAT -> {
                        tooltip = new ArrayList<>();
                        tooltip.add(Component.literal(r.name()));
                        if (stat != null && stat.description != null) tooltip.add(Component.literal("§7" + com.wavesurvivor.i18n.WSLang.t(stat.description)));
                        if (stat != null) tooltip.add(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.bonus_actuel") + stat.formatTotal(r.bought())));
                        tooltip.add(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.permanent_survit_a_la_mort_et_aux_hordes")));
                    }
                    default -> {
                        tooltip = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), r.icon()));
                        RenaissanceConfigData.ShopItem s = cfg.findShop(r.id());
                        if (s != null && s.description != null && !s.description.isBlank()) {
                            tooltip.add(Component.literal(""));
                            tooltip.add(Component.literal("§7" + com.wavesurvivor.i18n.WSLang.t(s.description)));
                        }
                    }
                }
            }
            y += ROW_H;
        }
        if (rows.size() > VISIBLE_ROWS) {
            g.drawCenteredString(font, com.wavesurvivor.i18n.WSLang.t("ui.molette") + (scroll + 1) + "-" + Math.min(rows.size(), scroll + VISIBLE_ROWS)
                    + " / " + rows.size(), width / 2, top + PANEL_H - 33, 0xFFFFFFFF);
        }
        return tooltip;
    }

    private String fit(String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        String out = s;
        while (out.length() > 1 && font.width(out + "…") > maxW) out = out.substring(0, out.length() - 1);
        return out + "…";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
