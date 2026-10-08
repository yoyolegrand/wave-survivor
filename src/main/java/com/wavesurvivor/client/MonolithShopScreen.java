package com.wavesurvivor.client;

import com.wavesurvivor.horde.kingdom.KingdomDefenses;
import com.wavesurvivor.horde.loot.LootItems;
import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.network.MonolithShopPackets;
import com.wavesurvivor.network.NetworkHandler;
import com.wavesurvivor.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Boutique du Monolithe (clic droit pendant une horde en défense), en 3 ONGLETS :
 *  - Améliorations : Renfort / Régénération / Blindage (communs à l'équipe, perdus en fin de horde) ;
 *  - Défenses (mode Kingdom) : tour d'archers, tour de mage, sanctuaire, baraquement ;
 *  - Murs & Pièges (mode Kingdom) : blocs de rempart, porte de rempart, 5 pièges à usage unique.
 * Tout se paie avec la monnaie de la horde (émeraudes). Codes envoyés au serveur : 0-2, 10-13, 20-21, 30-34.
 */
@OnlyIn(Dist.CLIENT)
public class MonolithShopScreen extends Screen {

    private static final int PANEL_W = 340;
    private static final int PANEL_H = 326;
    private static final int ROW_H = 42;
    private static final ItemStack[] ICONS = {
            new ItemStack(Items.OBSIDIAN), new ItemStack(Items.GLISTERING_MELON_SLICE), new ItemStack(Items.SHIELD)};
    private static final String[] NAMES = {"§7§lRenfort", "§a§lRégénération", "§b§lBlindage"};
    private static final String[] DEF_IDS = {"archer", "mage", "shrine", "barracks", "collector", "workshop"};
    /** Hauteur d'une ligne de l'onglet Défense. */
    private static final int DEF_ROW = 30;
    /** Onglet Défense : 0 = Tours, 1 = Murs, 2 = Pièges (mémorisé). */
    private static int defSub = 0;
    /** Onglet Town Hall : 0 = Mairie, 1 = Carte du royaume (mémorisé). */
    private static int townSub = 0;
    /** Carte : niveau de zoom 0 = 64×64, 1 = 128×128, 2 = 256×256 blocs (mémorisé). */
    private static int mapZoom = 1;
    private static final int[] MAP_HALF = {32, 64, 128};
    private int mapTimer = 39; // première demande dès le premier tick
    /** Carte : repère survolé (dernier rendu), repère choisi pour la boussole, zone de la carte à l'écran. */
    private com.wavesurvivor.network.KingdomMapPackets.Marker mapHover = null;
    private static com.wavesurvivor.network.KingdomMapPackets.Marker compassPick = null;
    private int mapX0, mapY0, mapS;

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (tab == 3 && townSub == 1 && mx >= mapX0 && mx < mapX0 + mapS && my >= mapY0 && my < mapY0 + mapS) {
            if (button == 1) {
                compassPick = null;
                NetworkHandler.CHANNEL.sendToServer(new com.wavesurvivor.network.KingdomMapPackets.SetCompass(
                        new com.wavesurvivor.network.KingdomMapPackets.Marker((byte) 0, (short) 0, (short) 0, (byte) 0), true));
                return true;
            }
            if (button == 0 && mapHover != null) {
                compassPick = mapHover;
                NetworkHandler.CHANNEL.sendToServer(new com.wavesurvivor.network.KingdomMapPackets.SetCompass(mapHover, false));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private static void requestMap() {
        NetworkHandler.CHANNEL.sendToServer(new com.wavesurvivor.network.KingdomMapPackets.Request());
    }

    /** Couleur et nom de chaque marqueur de la carte. */
    private static int markerColor(com.wavesurvivor.network.KingdomMapPackets.Marker m) {
        return switch (m.type()) {
            case 0 -> 0xFFFFD700;                                  // Monolithe
            case 1 -> switch (m.extra()) {                          // défenses
                case 0 -> 0xFF4AA3FF; case 1 -> 0xFFB06BFF; case 2 -> 0xFFFF8FD0;
                case 3 -> 0xFFC0703A; case 4 -> 0xFFB08850; default -> 0xFFFF9030; };
            case 2 -> 0xFF9A9A9A;                                  // rempart
            case 3 -> 0xFFE04040;                                  // piège
            case 4 -> 0xFF8B0000;                                  // Porte ennemie
            case 5 -> 0xFFFF40FF;                                  // Catalyseur
            case 6 -> 0xFFF0D040;                                  // gisement
            case 7 -> 0xFF40C040;                                  // arbre
            case 10 -> 0xFFC03CE6;                                 // foyer de corruption
            case 9 -> 0xFF40FFFF;                                  // soi
            default -> 0xFFFFFFFF;                                 // joueurs
        };
    }

    private static String markerName(com.wavesurvivor.network.KingdomMapPackets.Marker m) {
        if (m.type() == 1) {
            return WSLang.t("ui.shop.map.def." + switch (m.extra()) {
                case 0 -> "archer"; case 1 -> "mage"; case 2 -> "shrine"; case 3 -> "barracks"; case 4 -> "collector"; default -> "workshop"; });
        }
        return WSLang.t("ui.shop.map.m" + m.type());
    }

    private void renderMap(GuiGraphics g, int mx, int my, int yTop) {
        var data = com.wavesurvivor.network.KingdomMapPackets.CLIENT;
        int S = 176, x0 = left + 12, y0 = yTop;
        mapX0 = x0; mapY0 = y0; mapS = S;
        int half = MAP_HALF[Math.max(0, Math.min(2, mapZoom))];
        float sc = S / (2f * half);
        g.fill(x0 - 1, y0 - 1, x0 + S + 1, y0 + S + 1, 0xFF000000);
        g.fill(x0, y0, x0 + S, y0 + S, 0xFF12201A);
        // Quadrillage tous les 16 blocs (32 en vue la plus large)
        int step = half >= 128 ? 32 : 16;
        for (int k = -half; k <= half; k += step) {
            int px = x0 + S / 2 + Math.round(k * sc), py = y0 + S / 2 + Math.round(k * sc);
            g.fill(px, y0, px + 1, y0 + S, 0x2266AA88);
            g.fill(x0, py, x0 + S, py + 1, 0x2266AA88);
        }
        if (com.wavesurvivor.network.KingdomMapPackets.CLIENT_CLAIM < 0) {
            g.drawCenteredString(font, WSLang.t("ui.shop.map_none"), x0 + S / 2, y0 + S / 2 - 4, 0xFF888888);
            return;
        }
        // Limite du claim
        int cr = Math.round(com.wavesurvivor.network.KingdomMapPackets.CLIENT_CLAIM * sc);
        int cx = x0 + S / 2, cy = y0 + S / 2;
        g.renderOutline(cx - cr, cy - cr, cr * 2 + 1, cr * 2 + 1, 0xAA22C55E);
        g.drawCenteredString(font, "N", cx, y0 + 2, 0xFFAAAAAA);
        // Marqueurs (les plus petits d'abord, joueurs par-dessus)
        com.wavesurvivor.network.KingdomMapPackets.Marker hover = null;
        double best = 25;
        for (int pass = 0; pass < 3; pass++) {
            for (var m : data) {
                int layer = m.type() == 2 || m.type() == 3 ? 0 : (m.type() == 8 || m.type() == 9) ? 2 : 1;
                if (layer != pass || Math.abs(m.dx()) > half || Math.abs(m.dz()) > half) continue;
                int px = cx + Math.round(m.dx() * sc), py = cy + Math.round(m.dz() * sc);
                int r = switch (m.type()) { case 0 -> 3; case 4 -> 3; case 10 -> 3; case 2, 3 -> 1; default -> 2; };
                int col = markerColor(m);
                if (m.type() == 4) g.fill(px - r - 1, py - r - 1, px + r + 2, py + r + 2, 0xFFFF5050);
                if (m.type() == 5 && (System.currentTimeMillis() / 400) % 2 == 0) g.fill(px - r - 1, py - r - 1, px + r + 2, py + r + 2, 0x88FFFFFF);
                if (m.type() == 10 && (System.currentTimeMillis() / 500) % 2 == 0) g.fill(px - r - 1, py - r - 1, px + r + 2, py + r + 2, 0x88E070FF); // foyer : clignote
                g.fill(px - r, py - r, px + r + 1, py + r + 1, col);
                // Repère visé par la boussole : entouré de doré
                if (compassPick != null && compassPick.type() == m.type() && compassPick.dx() == m.dx() && compassPick.dz() == m.dz()) {
                    g.renderOutline(px - r - 3, py - r - 3, 2 * r + 7, 2 * r + 7, 0xFFFFD54A);
                }
                double d2 = (mx - px) * (mx - px) + (my - py) * (my - py);
                if (m.type() != 2 && m.type() != 3 && d2 < best) { best = d2; hover = m; }
            }
        }
        // Légende
        int lx = left + 200, ly = yTop;
        byte[] legend = {0, 9, 8, 1, 2, 3, 4, 5, 10, 6, 7};
        for (byte t : legend) {
            var dummy = new com.wavesurvivor.network.KingdomMapPackets.Marker(t, (short) 0, (short) 0, (byte) 0);
            g.fill(lx, ly + 2, lx + 6, ly + 8, markerColor(dummy));
            g.drawString(font, "§7" + (t == 1 ? WSLang.t("ui.shop.map.defenses") : markerName(dummy)), lx + 10, ly + 1, 0xFFFFFFFF, false);
            ly += 11;
        }
        g.drawString(font, "§8" + WSLang.t("ui.shop.map_scale", half * 2), lx, ly + 4, 0xFFFFFFFF, false);
        g.drawString(font, "§8" + WSLang.t("ui.shop.map_compass_hint"), lx, ly + 16, 0xFFFFFFFF, false);
        mapHover = hover;
        // Infobulle : nom, distance et direction depuis le Monolithe
        if (hover != null) {
            int dist = (int) Math.round(Math.sqrt(hover.dx() * hover.dx() + hover.dz() * hover.dz()));
            double ang = Math.toDegrees(Math.atan2(hover.dx(), -hover.dz()));
            String[] keys = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
            String dir = WSLang.t("kingdom.dir8." + keys[Math.floorMod(Math.round(ang / 45.0), 8)]);
            String line2 = hover.type() == 0 ? "" : WSLang.t("ui.shop.map_where", dist, dir);
            java.util.List<Component> tip = new java.util.ArrayList<>();
            tip.add(Component.literal(markerName(hover)));
            if (!line2.isEmpty()) tip.add(Component.literal("§7" + line2));
            g.renderComponentTooltip(font, tip, mx, my);
        }
    }
    private static final String[] TRAP_IDS = {"spikes", "fire", "frost", "explosive", "snare"};

    /** Onglet affiché (gardé d'une ouverture à l'autre). */
    private static int tab = 0;

    private MonolithShopPackets.State state;
    private int left, top;

    public MonolithShopScreen(MonolithShopPackets.State state) {
        super(Component.literal(WSLang.t("ui.monolithe")));
        this.state = state;
    }

    public void applyState(MonolithShopPackets.State s) {
        this.state = s;
        init();
    }

    private static ItemStack defItem(int i) {
        return new ItemStack(switch (i) {
            case 0 -> ModItems.KINGDOM_ARCHER_TOWER_ITEM.get();
            case 1 -> ModItems.KINGDOM_MAGE_TOWER_ITEM.get();
            case 2 -> ModItems.KINGDOM_REPAIR_SHRINE_ITEM.get();
            case 3 -> ModItems.KINGDOM_BARRACKS_ITEM.get();
            case 4 -> ModItems.KINGDOM_COLLECTOR_ITEM.get();
            default -> ModItems.KINGDOM_WORKSHOP_ITEM.get();
        });
    }

    private static ItemStack trapItem(int i) {
        return new ItemStack(switch (i) {
            case 0 -> ModItems.KINGDOM_TRAP_SPIKES_ITEM.get();
            case 1 -> ModItems.KINGDOM_TRAP_FIRE_ITEM.get();
            case 2 -> ModItems.KINGDOM_TRAP_FROST_ITEM.get();
            case 3 -> ModItems.KINGDOM_TRAP_EXPLOSIVE_ITEM.get();
            default -> ModItems.KINGDOM_TRAP_SNARE_ITEM.get();
        });
    }

    private void buyButton(int x, int y, int w, int cost, int code, Component tip, int have) {
        // Bâtisseur présent : remise de 20 % (sauf la charge de démolition)
        if (code != 22) cost = com.wavesurvivor.horde.kingdom.KingdomRoles.builderPrice(cost,
                com.wavesurvivor.horde.kingdom.KingdomRoles.CLIENT_HOLDERS.containsKey(com.wavesurvivor.horde.kingdom.KingdomRoles.Role.BUILDER));
        Button.Builder bb = Button.builder(Component.literal(WSLang.t("ui.shop.buy") + " " + cost + " ◆"),
                btn -> NetworkHandler.CHANNEL.sendToServer(new MonolithShopPackets.Upgrade(code))).bounds(x, y, w, 20);
        if (tip != null) bb.tooltip(Tooltip.create(tip));
        Button b = addRenderableWidget(bb.build());
        b.active = have >= cost || creative();
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;
        clearWidgets();
        int have = currencyCount();

        // Onglets (Défense regroupe Tours, Murs et Pièges ; l'ancien onglet 2 « Remparts » n'est plus affiché)
        String[] tabs = {"ui.shop.tab_upgrades", "ui.shop.tab_defense", "ui.shop.tab_town", "ui.shop.tab_roles"};
        int[] tabIds = {0, 1, 3, 4};
        if (tab == 2) { tab = 1; defSub = 1; }
        int nTabs = state.townTier > 0 ? 4 : 1;   // horde classique : seulement les améliorations du Monolithe
        if (state.townTier <= 0) tab = 0;
        int tw = (PANEL_W - 16 - (nTabs - 1) * 4) / nTabs;
        for (int i = 0; nTabs > 1 && i < nTabs; i++) {
            final int t = tabIds[i];
            addRenderableWidget(Button.builder(Component.literal((tab == t ? "§e§l" : "§7") + WSLang.t(tabs[i])),
                    b -> { tab = t; init(); }).bounds(left + 8 + i * (tw + 4), top + 42, tw, 18).build());
        }

        int y0 = top + 66;
        if (tab == 0) {
            for (int i = 0; i < 3; i++) {
                final int type = i;
                boolean maxed = state.levels[i] >= state.maxLevel;
                Button b = addRenderableWidget(Button.builder(
                                Component.literal(maxed ? WSLang.t("ui.max_93f1") : WSLang.t("ui.ameliorer_f6a0") + state.costs[i] + " ◆"),
                                btn -> NetworkHandler.CHANNEL.sendToServer(new MonolithShopPackets.Upgrade(type)))
                        .bounds(left + PANEL_W - 100, y0 + i * ROW_H + 10, 90, 20).build());
                b.active = !maxed && (have >= state.costs[i] || creative());
            }
            // Mode Kingdom : retrait d'émeraudes du trésor (pour commercer avec les marchands)
            if (state.townTier > 0) {
                Button w = addRenderableWidget(Button.builder(Component.literal(WSLang.t("kingdom.treasury.withdraw_btn")),
                                btn -> NetworkHandler.CHANNEL.sendToServer(new MonolithShopPackets.Upgrade(50)))
                        .tooltip(Tooltip.create(WSLang.c("kingdom.treasury.withdraw_tip")))
                        .bounds(left + PANEL_W - 100, top + PANEL_H - 50, 90, 18).build());
                w.active = have >= 1;
            }
        } else if (tab == 1) {
            // Sous-catégories : Tours · Murs · Pièges
            String[] subs = {"ui.shop.def_towers", "ui.shop.def_walls", "ui.shop.def_traps"};
            int sw = (PANEL_W - 16 - 8) / 3;
            for (int i = 0; i < 3; i++) {
                final int s = i;
                addRenderableWidget(Button.builder(Component.literal((defSub == i ? "§b§l" : "§7") + WSLang.t(subs[i])),
                        b -> { defSub = s; init(); }).bounds(left + 8 + i * (sw + 4), y0 - 2, sw, 16).build());
            }
            int yd = y0 + 20;
            if (defSub == 0) {
                for (int i = 0; i < DEF_IDS.length; i++) {
                    buyButton(left + PANEL_W - 92, yd + i * DEF_ROW + 4, 82, KingdomDefenses.BUY[i], 10 + i,
                            WSLang.c("kingdom.defense.desc." + DEF_IDS[i]), have);
                }
            } else if (defSub == 1) {
                buyButton(left + PANEL_W - 92, yd + 4, 82, KingdomDefenses.RAMPART_COST, 20, WSLang.c("kingdom.rampart.desc"), have);
                buyButton(left + PANEL_W - 92, yd + DEF_ROW + 4, 82, KingdomDefenses.GATE_COST, 21, WSLang.c("kingdom.gate.desc"), have);
                // Bâtisseur : charge de démolition (1 par vague)
                if (amBuilder()) {
                    buyButton(left + PANEL_W - 92, yd + 2 * DEF_ROW + 4, 82, KingdomDefenses.CHARGE_COST, 22,
                            WSLang.c("kingdom.charge.tooltip"), have);
                }
            } else {
                for (int i = 0; i < 5; i++) {
                    buyButton(left + PANEL_W - 92, yd + i * DEF_ROW + 4, 82, KingdomDefenses.TRAP_COST[i], 30 + i,
                            WSLang.c("kingdom.trap.desc." + TRAP_IDS[i]), have);
                }
            }
        } else if (tab == 3) {
            // Sous-vues : Mairie / Carte du royaume
            String[] subs = {"ui.shop.town_sub_hall", "ui.shop.town_sub_map"};
            int sw = (PANEL_W - 16 - 4) / 2;
            for (int i = 0; i < 2; i++) {
                final int s = i;
                addRenderableWidget(Button.builder(Component.literal((townSub == i ? "§b§l" : "§7") + WSLang.t(subs[i])),
                        b -> { townSub = s; if (s == 1) requestMap(); init(); }).bounds(left + 8 + i * (sw + 4), y0 - 2, sw, 16).build());
            }
            if (townSub == 1) {
                // Zoom en 3 crans : − = voir plus loin, + = rapprocher
                Button out = addRenderableWidget(Button.builder(Component.literal("−"),
                        b -> { mapZoom = Math.min(2, mapZoom + 1); init(); }).bounds(left + 200, top + PANEL_H - 50, 20, 16).build());
                Button in = addRenderableWidget(Button.builder(Component.literal("+"),
                        b -> { mapZoom = Math.max(0, mapZoom - 1); init(); }).bounds(left + 224, top + PANEL_H - 50, 20, 16).build());
                out.active = mapZoom < 2;
                in.active = mapZoom > 0;
                // Boussole du Royaume (64 ◆, prix fixe)
                buyButton(left + 250, top + PANEL_H - 50, 80, KingdomDefenses.COMPASS_COST, 23, WSLang.c("compass.shop_tip"), have);
            } else {
            // Mairie : bouton d'amélioration (actif si toutes les ressources sont là)
            boolean maxed = state.townTier >= com.wavesurvivor.horde.kingdom.KingdomCosts.TOWN_MAX;
            var p = Minecraft.getInstance().player;
            boolean ok = !maxed && p != null && com.wavesurvivor.horde.kingdom.KingdomCosts.canAfford(p,
                    com.wavesurvivor.horde.kingdom.KingdomCosts.townHall(state.townTier + 1), currencyItem());
            Button b = addRenderableWidget(Button.builder(Component.literal(maxed ? WSLang.t("ui.max_93f1")
                            : WSLang.t("ui.shop.town_upgrade", state.townTier + 1)),
                    btn -> NetworkHandler.CHANNEL.sendToServer(new MonolithShopPackets.Upgrade(40)))
                    .bounds(left + PANEL_W - 152, top + PANEL_H - 50, 140, 20).build());
            b.active = ok;
            // Tout réparer : toutes les défenses abîmées, les plus endommagées d'abord
            addRenderableWidget(Button.builder(Component.literal(WSLang.t("ui.shop.repair_all")),
                    btn -> NetworkHandler.CHANNEL.sendToServer(new MonolithShopPackets.Upgrade(41)))
                    .tooltip(Tooltip.create(WSLang.c("ui.shop.repair_all_tip")))
                    .bounds(left + PANEL_W - 152, top + PANEL_H - 74, 140, 20).build());
            // Éclaireur : réaffiche le rapport sur le prochain Assaut
            addRenderableWidget(Button.builder(Component.literal(WSLang.t("ui.shop.scout")),
                    btn -> NetworkHandler.CHANNEL.sendToServer(new MonolithShopPackets.Upgrade(42)))
                    .tooltip(Tooltip.create(WSLang.c("ui.shop.scout_tip")))
                    .bounds(left + PANEL_W - 152, top + PANEL_H - 98, 140, 20).build());
            }
        } else if (tab == 4) {
            initRoles(y0);
        }
        addRenderableWidget(Button.builder(Component.literal(WSLang.t("ui.fermer")), b -> onClose())
                .bounds(width / 2 - 40, top + PANEL_H - 24, 80, 18).build());
    }

    private boolean creative() {
        var p = Minecraft.getInstance().player;
        return p != null && p.isCreative();
    }

    private Item currencyItem() {
        ItemStack st = LootItems.resolve(state.currency, 1);
        return st.isEmpty() ? Items.EMERALD : st.getItem();
    }

    private int currencyCount() {
        // Mode Kingdom : monnaie du trésor commun
        if (state.townTier > 0) return com.wavesurvivor.horde.kingdom.KingdomTreasury.CLIENT[0];
        var p = Minecraft.getInstance().player;
        if (p == null) return 0;
        Item cur = currencyItem();
        int n = 0;
        for (ItemStack st : p.getInventory().items) if (st.is(cur)) n += st.getCount();
        for (ItemStack st : p.getInventory().offhand) if (st.is(cur)) n += st.getCount();
        return n;
    }

    @Override
    public void tick() {
        super.tick();
        // Carte du royaume affichée : rafraîchie toutes les 2 s
        if (tab == 3 && townSub == 1 && ++mapTimer >= 40) {
            mapTimer = 0;
            requestMap();
        }
        // L'inventaire peut changer (ramassage d'émeraudes) : on rafraîchit l'état des boutons
        if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.getGameTime() % 10 == 0) init();
    }

    private String effect(int type, int lvl) {
        return switch (type) {
            case 0 -> "+" + (lvl * state.hpPerLevel) + WSLang.t(" PV max");
            case 1 -> "+" + fmt(lvl * state.regenPerLevel) + WSLang.t(" PV/s");
            default -> "-" + (lvl * state.armorPerLevel) + WSLang.t("ui.degats");
        };
    }

    private static String fmt(double v) {
        return Math.abs(v - Math.round(v)) < 1e-6 ? String.valueOf(Math.round(v)) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    /** Une ligne d'article : cadre, icône, nom (+ quantité), description sur une ligne. */
    private void itemRow(GuiGraphics g, int y, int h, ItemStack icon, String name, String desc) {
        g.fill(left + 8, y, left + PANEL_W - 8, y + h - 4, 0x30FFFFFF);
        g.renderItem(icon, left + 14, y + (h - 4) / 2 - 8);
        int textW = PANEL_W - 140;
        if (desc == null) {
            g.drawString(font, name, left + 36, y + (h - 4) / 2 - 4, 0xFFFFFFFF);
        } else {
            g.drawString(font, name, left + 36, y + 6, 0xFFFFFFFF);
            g.drawString(font, "§7" + font.plainSubstrByWidth(desc.replaceAll("§.", ""), textW), left + 36, y + 20, 0xFFFFFFFF);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        g.fill(left, top, left + PANEL_W, top + PANEL_H, 0xF00C1410);
        g.fill(left, top, left + PANEL_W, top + 2, 0xFF22C55E);
        g.fill(left, top + PANEL_H - 2, left + PANEL_W, top + PANEL_H, 0xFF22C55E);
        g.drawCenteredString(font, WSLang.t("ui.ameliorer_le_monolithe"), width / 2, top + 8, 0xFFFFFFFF);

        // Barre d'intégrité
        float p = state.maxHp > 0 ? Math.min(1f, (float) state.hp / state.maxHp) : 0f;
        int bx = left + 20, by = top + 24, bw = PANEL_W - 40;
        int col = p > 0.5f ? 0xFF22C55E : p > 0.25f ? 0xFFEAB308 : 0xFFEF4444;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + 11, 0xFF000000);
        g.fill(bx, by, bx + bw, by + 10, 0xFF2A2A2A);
        g.fill(bx, by, bx + (int) (bw * p), by + 10, col);
        g.drawCenteredString(font, state.hp + " / " + state.maxHp, width / 2, by + 1, 0xFFFFFFFF);

        int y0 = top + 66;
        if (tab == 0) {
            for (int i = 0; i < 3; i++) {
                int y = y0 + i * ROW_H;
                g.fill(left + 8, y, left + PANEL_W - 8, y + ROW_H - 4, 0x30FFFFFF);
                g.renderItem(ICONS[i], left + 14, y + 11);
                int lvl = state.levels[i];
                g.drawString(font, WSLang.t(NAMES[i]) + WSLang.t("ui.niv") + lvl + "§7/" + state.maxLevel, left + 36, y + 5, 0xFFFFFFFF);
                String line = lvl >= state.maxLevel
                        ? "§6" + effect(i, lvl) + WSLang.t("ui.max")
                        : "§7" + (lvl > 0 ? effect(i, lvl) : WSLang.t("aucun")) + " §8→ §a" + effect(i, lvl + 1);
                g.drawString(font, line, left + 36, y + 19, 0xFFFFFFFF);
            }
            g.drawCenteredString(font, WSLang.t("ui.ameliorations_communes_a_l_equipe_perdue"), width / 2, y0 + 3 * ROW_H + 4, 0xFFFFFFFF);
        } else if (tab == 1) {
            int yd = y0 + 20;
            if (defSub == 0) {
                for (int i = 0; i < DEF_IDS.length; i++) {
                    ItemStack it = defItem(i);
                    itemRow(g, yd + i * DEF_ROW, DEF_ROW, it, "§f§l" + it.getHoverName().getString(), WSLang.t("kingdom.defense.desc." + DEF_IDS[i]));
                }
            } else if (defSub == 1) {
                ItemStack ramp = new ItemStack(ModItems.KINGDOM_RAMPART_ITEM.get());
                itemRow(g, yd, DEF_ROW, ramp, "§f§l" + ramp.getHoverName().getString() + " §7×" + KingdomDefenses.RAMPART_COUNT,
                        WSLang.t("kingdom.rampart.desc"));
                ItemStack gate = new ItemStack(ModItems.KINGDOM_GATE_ITEM.get());
                itemRow(g, yd + DEF_ROW, DEF_ROW, gate, "§f§l" + gate.getHoverName().getString(), WSLang.t("kingdom.gate.desc"));
                if (amBuilder()) {
                    ItemStack ch = new ItemStack(ModItems.DEMOLITION_CHARGE.get());
                    itemRow(g, yd + 2 * DEF_ROW, DEF_ROW, ch, "§6" + ch.getHoverName().getString() + " " + WSLang.t("kingdom.charge.per_wave"),
                            WSLang.t("kingdom.charge.tooltip"));
                }
            } else {
                for (int i = 0; i < 5; i++) {
                    ItemStack it = trapItem(i);
                    itemRow(g, yd + i * DEF_ROW, DEF_ROW, it, "§f§l" + it.getHoverName().getString(), WSLang.t("kingdom.trap.desc." + TRAP_IDS[i]));
                }
            }
        } else if (tab == 3) {
            if (townSub == 1) renderMap(g, mx, my, y0 + 18);
            else renderTownHall(g, y0 + 20);
        } else if (tab == 4) {
            renderRoles(g, y0);
        }

        // Monnaie disponible (pas sur l'onglet Rôles)
        int have = currencyCount();
        if (tab != 4) {
        g.renderItem(new ItemStack(currencyItem()), left + 12, top + PANEL_H - 46);
        String haveTxt = state.townTier > 0 ? WSLang.t("kingdom.treasury.shop_money", have)
                : WSLang.t("ui.tu_as_e19f") + have + "§f " + new ItemStack(currencyItem()).getHoverName().getString().toLowerCase();
        g.drawString(font, haveTxt, left + 32, top + PANEL_H - 42, 0xFFFFFFFF);
        }

        super.render(g, mx, my, pt);
    }

    /** Rôle du joueur local (null = aucun). */
    private static boolean amBuilder() {
        var me = Minecraft.getInstance().player;
        return me != null && myRole(me.getUUID().toString()) == com.wavesurvivor.horde.kingdom.KingdomRoles.Role.BUILDER;
    }

    /** Rôle du joueur local (null = aucun). */
    private static com.wavesurvivor.horde.kingdom.KingdomRoles.Role myRole(String myId) {
        for (var e : com.wavesurvivor.horde.kingdom.KingdomRoles.CLIENT_HOLDERS.entrySet()) {
            if (e.getValue()[0].equals(myId)) return e.getKey();
        }
        return null;
    }

    // ═══ Onglet Rôles ═══
    // Sans rôle : liste des rôles à choisir. Avec un rôle (définitif jusqu'à la fin de la horde) : sa fiche
    // (description + actions propres au rôle). Créatif / opérateur : bouton « Changer de rôle » en haut à droite.

    private static final com.wavesurvivor.horde.kingdom.KingdomRoles.Role[] ROLES = com.wavesurvivor.horde.kingdom.KingdomRoles.Role.values();
    /** Créatif / opérateur : affiche la liste pour changer de rôle. */
    private static boolean switchMode = false;

    private static String myId() {
        var me = Minecraft.getInstance().player;
        return me == null ? "" : me.getUUID().toString();
    }

    private static boolean canSwitch() {
        var me = Minecraft.getInstance().player;
        return me != null && (me.isCreative() || me.hasPermissions(2));
    }

    /** Lignes de la description du rôle (sans la première ligne, qui reprend le nom), coupées à la largeur du panneau. */
    private java.util.List<net.minecraft.util.FormattedCharSequence> roleDesc(com.wavesurvivor.horde.kingdom.KingdomRoles.Role r) {
        java.util.List<net.minecraft.util.FormattedCharSequence> out = new java.util.ArrayList<>();
        String[] parts = WSLang.t("kingdom.role." + r.id + ".desc").split("\n");
        for (int i = 1; i < parts.length; i++) out.addAll(font.split(Component.literal(parts[i]), PANEL_W - 28));
        return out;
    }

    /** Début de la zone d'actions sous la description. */
    private int sectionY(int y0, com.wavesurvivor.horde.kingdom.KingdomRoles.Role r) {
        return y0 + 24 + roleDesc(r).size() * 10 + 8;
    }

    private void initRoles(int y0) {
        var mine = myRole(myId());
        boolean listView = mine == null || (switchMode && canSwitch());
        if (mine == null) switchMode = false;
        // Créatif / opérateur : bascule fiche ↔ liste (dans l'en-tête de la fiche, ou en bas à gauche sur la liste)
        if (mine != null && canSwitch()) {
            if (listView) {
                addRenderableWidget(Button.builder(Component.literal(WSLang.t("ui.shop.role_back")),
                        b -> { switchMode = false; init(); }).bounds(left + 10, top + PANEL_H - 24, 80, 18).build());
            } else {
                addRenderableWidget(Button.builder(Component.literal(WSLang.t("ui.shop.role_switch")),
                        b -> { switchMode = true; init(); }).bounds(left + PANEL_W - 92, y0 + 1, 80, 16).build());
            }
        }
        if (listView) {
            for (int i = 0; i < ROLES.length; i++) {
                var r = ROLES[i];
                String[] h = com.wavesurvivor.horde.kingdom.KingdomRoles.CLIENT_HOLDERS.get(r);
                boolean avail = com.wavesurvivor.horde.kingdom.KingdomRoles.CLIENT_AVAILABLE.contains(r);
                String label;
                boolean active;
                if (h != null && h[0].equals(myId())) { label = WSLang.t("ui.shop.role_mine"); active = false; }
                else if (h != null) { label = WSLang.t("ui.shop.role_taken"); active = false; }
                else if (!avail) { label = WSLang.t("ui.shop.role_off"); active = false; }
                else { label = WSLang.t("ui.shop.role_pick"); active = mine == null || canSwitch(); }
                final int code = 60 + i;
                Button rb = addRenderableWidget(Button.builder(Component.literal(label),
                                btn -> { switchMode = false; NetworkHandler.CHANNEL.sendToServer(new MonolithShopPackets.Upgrade(code)); })
                        .tooltip(Tooltip.create(WSLang.c("kingdom.role." + r.id + ".desc")))
                        .bounds(left + PANEL_W - 82, y0 + i * 20 + 1, 72, 16).build());
                rb.active = active;
            }
            return;
        }
        int ys = sectionY(y0, mine);
        boolean calm = com.wavesurvivor.horde.kingdom.KingdomRoles.clientCalm;
        switch (mine) {
            case COMMANDER -> {
                var me = Minecraft.getInstance().player;
                boolean hasHorn = me != null && me.getInventory().contains(new ItemStack(ModItems.COMMANDER_HORN.get()));
                Button hb = addRenderableWidget(Button.builder(Component.literal(WSLang.t("ui.shop.role_horn")),
                                btn -> NetworkHandler.CHANNEL.sendToServer(new MonolithShopPackets.Upgrade(90)))
                        .bounds(left + PANEL_W / 2 - 90, ys + 14, 180, 20).build());
                hb.active = !hasHorn;
            }
            case ALCHEMIST -> {
                int reag = com.wavesurvivor.horde.kingdom.KingdomRoleState.clientReagents;
                int[] lv = com.wavesurvivor.horde.kingdom.KingdomRoleState.clientFlaskLevels;
                for (int i = 0; i < com.wavesurvivor.horde.kingdom.KingdomAlchemy.FLASKS.length; i++) {
                    String f = com.wavesurvivor.horde.kingdom.KingdomAlchemy.FLASKS[i];
                    int c = com.wavesurvivor.horde.kingdom.KingdomAlchemy.COST[i];
                    int l = lv != null && i < lv.length ? lv[i] : 1;
                    int y = ys + i * 22;
                    final int brew = 70 + i, up = 75 + i;
                    Button fb = addRenderableWidget(Button.builder(Component.literal(WSLang.t("ui.shop.alchemy_brew", c)),
                                    btn -> NetworkHandler.CHANNEL.sendToServer(new MonolithShopPackets.Upgrade(brew)))
                            .tooltip(Tooltip.create(WSLang.c("kingdom.alchemy.desc." + f)))
                            .bounds(left + PANEL_W - 172, y, 78, 18).build());
                    fb.active = calm && (reag >= c || creative());
                    boolean max = l >= com.wavesurvivor.horde.kingdom.KingdomAlchemy.MAX_LEVEL;
                    int uc = max ? 0 : com.wavesurvivor.horde.kingdom.KingdomAlchemy.UPGRADE_COST[l - 1];
                    Button ub = addRenderableWidget(Button.builder(Component.literal(max ? WSLang.t("ui.max_93f1") : WSLang.t("ui.shop.alchemy_up", uc)),
                                    btn -> NetworkHandler.CHANNEL.sendToServer(new MonolithShopPackets.Upgrade(up)))
                            .tooltip(Tooltip.create(WSLang.c("kingdom.alchemy.desc." + f)))
                            .bounds(left + PANEL_W - 90, y, 80, 18).build());
                    ub.active = !max && (reag >= uc || creative());
                }
            }
            case AUGUR -> {
                String[] opts = com.wavesurvivor.horde.kingdom.KingdomRoleState.clientOmenOptions;
                String chosen = com.wavesurvivor.horde.kingdom.KingdomRoleState.clientOmenChosen;
                for (int i = 0; i < opts.length && i < 3; i++) {
                    final int code = 80 + i;
                    boolean sel = opts[i].equals(chosen);
                    Button ob = addRenderableWidget(Button.builder(
                                    Component.literal((sel ? "§a✔ §l" : "§d§l") + WSLang.t("kingdom.omen." + opts[i])),
                                    btn -> NetworkHandler.CHANNEL.sendToServer(new MonolithShopPackets.Upgrade(code)))
                            .tooltip(Tooltip.create(WSLang.c("kingdom.omen." + opts[i] + ".desc")))
                            .bounds(left + 30, ys + 14 + i * 36, PANEL_W - 60, 20).build());
                    ob.active = !sel && calm;
                }
            }
            default -> { }
        }
    }

    private void renderRoles(GuiGraphics g, int y0) {
        var mine = myRole(myId());
        if (mine == null || (switchMode && canSwitch())) {
            renderRoleList(g, y0);
            return;
        }
        // En-tête : icône + nom du rôle (en grand)
        g.fill(left + 8, y0 - 2, left + PANEL_W - 8, y0 + 20, 0x4022C55E);
        g.renderItem(new ItemStack(mine.icon), left + 12, y0 + 1);
        g.pose().pushPose();
        g.pose().translate(left + 34, y0 + 4, 0);
        g.pose().scale(1.4f, 1.4f, 1f);
        g.drawString(font, "§l" + WSLang.t("kingdom.role." + mine.id), 0, 0, 0xFFFFFFFF);
        g.pose().popPose();
        String lock = canSwitch() ? "" : WSLang.t("ui.shop.role_locked");
        g.drawString(font, lock, left + PANEL_W - 12 - font.width(lock.replaceAll("§.", "")), y0 + 6, 0xFFFFFFFF);
        // Description
        int y = y0 + 24;
        for (var line : roleDesc(mine)) {
            g.drawString(font, line, left + 14, y, 0xFFFFFFFF);
            y += 10;
        }
        int ys = sectionY(y0, mine);
        g.fill(left + 12, ys - 5, left + PANEL_W - 12, ys - 4, 0x60FFFFFF);
        switch (mine) {
            case COMMANDER -> centeredWrapped(g, WSLang.t("ui.shop.role_horn_hint"), ys);
            case ALCHEMIST -> {
                int[] lv = com.wavesurvivor.horde.kingdom.KingdomRoleState.clientFlaskLevels;
                for (int i = 0; i < com.wavesurvivor.horde.kingdom.KingdomAlchemy.FLASKS.length; i++) {
                    String f = com.wavesurvivor.horde.kingdom.KingdomAlchemy.FLASKS[i];
                    int l = lv != null && i < lv.length ? lv[i] : 1;
                    g.drawString(font, WSLang.t("kingdom.alchemy.flask." + f) + " §7" + WSLang.t("kingdom.alchemy.lvl", l),
                            left + 14, ys + i * 22 + 5, 0xFFFFFFFF);
                }
                int yy = ys + com.wavesurvivor.horde.kingdom.KingdomAlchemy.FLASKS.length * 22 + 2;
                g.drawCenteredString(font, WSLang.t("kingdom.alchemy.have", com.wavesurvivor.horde.kingdom.KingdomRoleState.clientReagents)
                        + (com.wavesurvivor.horde.kingdom.KingdomRoles.clientCalm ? "" : "  " + WSLang.t("ui.shop.alchemy_calm")),
                        width / 2, yy, 0xFFFFFFFF);
            }
            case AUGUR -> {
                String[] opts = com.wavesurvivor.horde.kingdom.KingdomRoleState.clientOmenOptions;
                if (opts.length == 0) {
                    String act = com.wavesurvivor.horde.kingdom.KingdomRoleState.clientOmenActive;
                    centeredWrapped(g, act != null && !act.isEmpty()
                            ? WSLang.t("kingdom.omen.active", WSLang.t("kingdom.omen." + act)) : WSLang.t("kingdom.omen.none_yet"), ys + 20);
                } else {
                    g.drawCenteredString(font, WSLang.t("ui.shop.omen_title"), width / 2, ys, 0xFFFFFFFF);
                    // Résumé de chaque présage sous son bouton (avantage / contrepartie)
                    for (int i = 0; i < opts.length && i < 3; i++) {
                        String[] d = WSLang.t("kingdom.omen." + opts[i] + ".desc").split("\n");
                        String sum = (d.length > 1 ? d[1] : "") + (d.length > 2 ? "  " + d[2] : "");
                        g.drawCenteredString(font, font.plainSubstrByWidth(sum, PANEL_W - 24), width / 2, ys + 36 + i * 36, 0xFFFFFFFF);
                    }
                }
            }
            case BOUNTY_HUNTER -> {
                String ct = com.wavesurvivor.horde.kingdom.KingdomRoleState.clientContract;
                if (ct == null || ct.isEmpty()) {
                    centeredWrapped(g, WSLang.t("kingdom.bounty.none"), ys + 6);
                } else {
                    int pr = com.wavesurvivor.horde.kingdom.KingdomRoleState.clientContractProgress;
                    int tg = com.wavesurvivor.horde.kingdom.KingdomRoleState.clientContractTarget;
                    int yy = centeredWrapped(g, WSLang.t("kingdom.bounty.tab", WSLang.t("kingdom.bounty.type." + ct), pr, tg), ys + 6);
                    centeredWrapped(g, pr >= tg ? WSLang.t("kingdom.bounty.tab_done")
                            : WSLang.t("kingdom.bounty.tab_reward", com.wavesurvivor.horde.kingdom.KingdomRoleState.clientContractReward), yy + 4);
                }
            }
            case BUILDER -> centeredWrapped(g, WSLang.t("ui.shop.role_builder_hint"), ys + 6);
            default -> { }
        }
    }

    /** Texte centré, renvoyé à la ligne pour rester dans le panneau. @return y sous la dernière ligne. */
    private int centeredWrapped(GuiGraphics g, String text, int y) {
        for (var line : font.split(Component.literal(text), PANEL_W - 28)) {
            g.drawString(font, line, width / 2 - font.width(line) / 2, y, 0xFFFFFFFF);
            y += 10;
        }
        return y;
    }

    /** Liste des rôles : icône, nom, joueur qui le tient ; survol du bouton = description. */
    private void renderRoleList(GuiGraphics g, int y0) {
        String myId = myId();
        var all = ROLES;
        for (int i = 0; i < all.length; i++) {
            var r = all[i];
            int y = y0 + i * 20;
            String[] h = com.wavesurvivor.horde.kingdom.KingdomRoles.CLIENT_HOLDERS.get(r);
            boolean avail = com.wavesurvivor.horde.kingdom.KingdomRoles.CLIENT_AVAILABLE.contains(r);
            boolean mine = h != null && h[0].equals(myId);
            g.fill(left + 8, y, left + PANEL_W - 8, y + 18, mine ? 0x4022C55E : 0x30FFFFFF);
            g.renderItem(new ItemStack(r.icon), left + 11, y + 1);
            String name = (avail ? "§f" : "§8") + WSLang.t("kingdom.role." + r.id);
            g.drawString(font, name, left + 31, y + 5, 0xFFFFFFFF);
            String who = h != null ? "§7" + h[1] : (avail ? WSLang.t("ui.shop.role_free") : "");
            g.drawString(font, who, left + PANEL_W - 88 - font.width(who.replaceAll("§.", "")), y + 5, 0xFFFFFFFF);
        }
        String hint = myRole(myId) != null ? WSLang.t("ui.shop.role_admin") : WSLang.t("ui.shop.role_hint");
        g.drawCenteredString(font, hint, width / 2, y0 + all.length * 20 + 3, 0xFFFFFFFF);
    }

    /** Onglet Mairie : niveau, claim, déblocages et ressources du niveau suivant (vert = OK, rouge = manquant). */
    private void renderTownHall(GuiGraphics g, int y0) {
        int t = state.townTier, max = com.wavesurvivor.horde.kingdom.KingdomCosts.TOWN_MAX;
        g.drawString(font, WSLang.t("ui.shop.town_title", t, max), left + 14, y0, 0xFFFFFFFF);
        g.drawString(font, WSLang.t("ui.shop.town_claim", state.claimRadius), left + 14, y0 + 14, 0xFFFFFFFF);
        if (t >= max) {
            g.drawString(font, WSLang.t("ui.shop.town_maxed"), left + 14, y0 + 34, 0xFFFFFFFF);
            return;
        }
        g.drawString(font, WSLang.t("ui.shop.town_next", t + 1) + " §f" + WSLang.t("ui.shop.town_unlock." + (t + 1)), left + 14, y0 + 34, 0xFFFFFFFF);
        var p = Minecraft.getInstance().player;
        Item cur = currencyItem();
        int y = y0 + 52;
        for (com.wavesurvivor.horde.kingdom.KingdomCosts.Cost c : com.wavesurvivor.horde.kingdom.KingdomCosts.townHall(t + 1)) {
            int have = p == null ? 0 : com.wavesurvivor.horde.kingdom.KingdomCosts.have(p, c, cur);
            ItemStack icon = new ItemStack(c.isCurrency() ? cur : c.icon());
            g.fill(left + 12, y - 2, left + PANEL_W - 12, y + 18, 0x30FFFFFF);
            g.renderItem(icon, left + 16, y);
            String name = c.label().getString();
            g.drawString(font, "§f" + name, left + 38, y + 5, 0xFFFFFFFF);
            String count = (have >= c.count() || (p != null && p.isCreative()) ? "§a" : "§c") + have + "§7 / §f" + c.count();
            g.drawString(font, count, left + PANEL_W - 16 - font.width(count.replaceAll("§.", "")), y + 5, 0xFFFFFFFF);
            y += 22;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
