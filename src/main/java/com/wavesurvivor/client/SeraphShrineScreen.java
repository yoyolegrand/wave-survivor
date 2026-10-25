package com.wavesurvivor.client;

import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.registry.ModItems;
import com.wavesurvivor.shrine.SeraphShrineMenu;
import com.wavesurvivor.shrine.ShrineGear;
import com.wavesurvivor.shrine.ShrineUpgrades;
import com.wavesurvivor.shrine.ShrineConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Écran du SANCTUAIRE DU SÉRAPHIN, dans le style de l'Atelier du Kingdom (aplats g.fill, liséré de couleur en haut, cases à fin contour) décliné en
 * trois thèmes : doré (étapes pure / apaisée), argenté (neutre), violet (souillée / corrompue). Fenêtre 176 × 234 ; onglets en haut (y 0-26) ;
 * panneau à partir de y = 24. Onglets : Offrandes (jauge, offrande, état), Récolte (stocks et production), Fusion, Forge (améliorer un équipement).
 */
@OnlyIn(Dist.CLIENT)
public class SeraphShrineScreen extends AbstractContainerScreen<SeraphShrineMenu> {

    /** Onglets déjà actifs (les suivants arrivent aux étapes 3 et 4 du développement). */
    private static final boolean[] TAB_ENABLED = {true, true, true, true};
    private static final int[] TAB_X = {4, 34, 64, 94};
    private static final int PANEL_Y = 24;

    // Thèmes : bord, fond, liséré / boutons, contour de case, fond de case, case spéciale (contour, fond), titre, aide, libellé
    private static final int[][] THEMES = {
            {0xFF2A2318, 0xFF3A3124, 0xFFF2C14E, 0xFF9C7A34, 0xFF1C160E, 0xFFFFD36A, 0xFF3A2A08, 0xFFFFE6A0, 0xFFB8A888, 0xFFC8B898},
            {0xFF26262A, 0xFF34343A, 0xFFC8CCD8, 0xFF707482, 0xFF18181C, 0xFFDDE2F0, 0xFF2A2C36, 0xFFE6EAF4, 0xFFA0A4B0, 0xFFB8BCC8},
            {0xFF221A28, 0xFF30243A, 0xFFB54BFF, 0xFF6A3A88, 0xFF140E1A, 0xFFD06BFF, 0xFF2A0A36, 0xFFEBC2FF, 0xFFA898B4, 0xFFBCA8C8},
    };
    private static final int BONUS = 0xFF6ED26E, MALUS = 0xFFEB5A5A;
    private static final int GOLD = 0xF2C14E, SILVER = 0xC8CCD8, VIOLET = 0xB54BFF;

    private int[] th = THEMES[1];

    public SeraphShrineScreen(SeraphShrineMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 234;
        this.inventoryLabelY = 1000; // dessiné à la main
        this.titleLabelY = 1000;
    }

    private static int theme(int stage) { return stage <= 1 ? 0 : stage == 2 ? 1 : 2; }

    private static int lerp(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static int gaugeColor(double t) { return t < 0.5 ? lerp(GOLD, SILVER, t * 2) : lerp(SILVER, VIOLET, (t - 0.5) * 2); }

    // ─── Dessin ───

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int themeIdx = theme(menu.stage());
        if (menu.tab == SeraphShrineMenu.TAB_FORGE) { // dans la Forge, le thème suit l'alignement de l'objet posé
            String al = ShrineGear.alignOf(menu.forgeEquipment());
            if (ShrineGear.CONSECRATED.equals(al)) themeIdx = 0;
            else if (ShrineGear.PROFANED.equals(al)) themeIdx = 2;
        }
        th = THEMES[themeIdx];
        int x = leftPos, y = topPos;
        // Onglets
        for (int i = 0; i < 4; i++) {
            int tx = x + TAB_X[i];
            boolean active = i == menu.tab, on = TAB_ENABLED[i];
            g.fill(tx, y + 2, tx + 28, y + PANEL_Y + 2, th[0]);
            g.fill(tx + 1, y + 3, tx + 27, y + PANEL_Y + 2, active ? th[1] : darker(th[1]));
            if (active) g.fill(tx, y + 2, tx + 28, y + 4, th[2]);
            ItemStack icon = switch (i) {
                case 0 -> new ItemStack(Items.GOLD_INGOT);
                case 1 -> new ItemStack(ModItems.GRACE_SHARD.get());
                case 2 -> new ItemStack(ModItems.GRACE_FRAGMENT.get());
                default -> new ItemStack(Items.ANVIL);
            };
            g.renderItem(icon, tx + 6, y + 7);
            if (!on) g.fill(tx + 1, y + 4, tx + 27, y + PANEL_Y + 2, 0xAA000000);
        }
        // Panneau
        int py = y + PANEL_Y;
        g.fill(x, py, x + imageWidth, y + imageHeight, th[0]);
        g.fill(x + 2, py + 2, x + imageWidth - 2, y + imageHeight - 2, th[1]);
        g.fill(x, py, x + imageWidth, py + 2, th[2]);
        // Cases d'inventaire
        for (int i = 5; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            int sx = x + s.x - 1, sy = y + s.y - 1;
            g.fill(sx, sy, sx + 18, sy + 18, th[3]);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, th[4]);
        }
        switch (menu.tab) {
            case SeraphShrineMenu.TAB_OFFER -> drawOffering(g, x, py);
            case SeraphShrineMenu.TAB_HARVEST -> drawHarvest(g, x, py);
            case SeraphShrineMenu.TAB_FUSION -> drawFusion(g, x, py);
            case SeraphShrineMenu.TAB_FORGE -> drawForge(g, x, py);
            default -> { }
        }
        g.drawString(font, WSLang.t("shrine.inventory"), x + 8, py + 113, th[9], false);
    }

    /** Case spéciale (plus vive) : emplacement d'offrande, de fusion… */
    private void specialSlot(GuiGraphics g, Slot s) {
        int sx = leftPos + s.x - 1, sy = topPos + s.y - 1;
        g.fill(sx - 1, sy - 1, sx + 19, sy + 19, th[5]);
        g.fill(sx, sy, sx + 18, sy + 18, th[6]);
    }

    private void drawHarvest(GuiGraphics g, int x, int py) {
        for (int k = 0; k < 2; k++) {
            boolean isGrace = k == 0;
            int bx = x + (isGrace ? 7 : 89), by = py + 20;
            g.fill(bx, by, bx + 80, by + 64, th[0]);
            g.fill(bx + 1, by + 1, bx + 79, by + 63, th[4]);
            g.fill(bx + 1, by + 1, bx + 79, by + 2, isGrace ? 0xFFF2C14E : 0xFFB54BFF);
            g.drawString(font, (isGrace ? "§e" : "§d") + WSLang.t(isGrace ? "shrine.grace_box" : "shrine.taint_box"), bx + 5, by + 5, 0xFFFFFFFF, false);
            // Case de sortie (affichage : on récolte avec le bouton)
            g.fill(bx + 5, by + 15, bx + 23, by + 33, th[5]);
            g.fill(bx + 6, by + 16, bx + 22, by + 32, th[6]);
            int n = isGrace ? menu.graceStock() : menu.taintStock();
            ItemStack shard = new ItemStack(isGrace ? ModItems.GRACE_SHARD.get() : ModItems.TAINT_SHARD.get(), Math.max(1, Math.min(n, 64)));
            if (n > 0) {
                g.renderItem(shard, bx + 6, by + 16);
                g.renderItemDecorations(font, shard, bx + 6, by + 16);
            }
            // Stock à droite de la case
            g.drawString(font, WSLang.t("shrine.stock_short"), bx + 28, by + 17, th[8], false);
            g.drawString(font, n + " / " + menu.stockCap(), bx + 28, by + 26, n >= menu.stockCap() ? MALUS : th[7], false);
            // Production : durée du cycle puis barre (seulement dans le sens actif de la statue)
            int stage = menu.stage();
            boolean active = isGrace ? stage <= 1 : stage >= 3;
            int iv = menu.intervalSeconds();
            String info = active && iv > 0 ? WSLang.t("shrine.every", fmtTime(iv)) : WSLang.t(active ? "shrine.no_production" : "shrine.sleeping");
            g.drawString(font, font.plainSubstrByWidth(info, 72), bx + 5, by + 39, active ? th[7] : th[8], false);
            int bw = 70, barX = bx + 5, barY = by + 52;
            g.fill(barX - 1, barY - 1, barX + bw + 1, barY + 7, th[0]);
            g.fill(barX, barY, barX + bw, barY + 6, th[4]);
            if (active && iv > 0) {
                int fill = n >= menu.stockCap() ? bw : (int) Math.min(bw, bw * (long) menu.progressSeconds() / iv);
                g.fill(barX, barY, barX + fill, barY + 6, isGrace ? 0xFFF2C14E : 0xFFB54BFF);
            }
        }
        boolean any = menu.graceStock() + menu.taintStock() > 0;
        drawButton(g, x + 56, py + 90, 64, 14, WSLang.t("shrine.harvest"), any);
    }

    private static String fmtTime(int sec) {
        return sec % 60 == 0 ? (sec / 60) + " min" : sec >= 60 ? (sec / 60) + " min " + (sec % 60) + " s" : sec + " s";
    }

    private void drawFusion(GuiGraphics g, int x, int py) {
        specialSlot(g, menu.slots.get(1));
        specialSlot(g, menu.slots.get(2));
        g.drawCenteredString(font, WSLang.t("shrine.fuse_in", menu.fusionCost()), x + 46, py + 31, th[7]);
        // Flèche
        int ax = x + 62, ay = py + 44;
        g.fill(ax, ay + 4, ax + 26, ay + 6, th[2]);
        for (int i = 0; i < 5; i++) g.fill(ax + 26 + i, ay + 5 - (4 - i), ax + 27 + i, ay + 5 + (4 - i) + 1, th[2]);
        ItemStack in = menu.fusionInput();
        net.minecraft.world.item.Item next = SeraphShrineMenu.nextLevel(in.getItem());
        int n = next == null ? 0 : in.getCount() / menu.fusionCost();
        drawButton(g, x + 56, py + 70, 64, 14, WSLang.t("shrine.fuse"), n > 0);
        String sum;
        if (in.isEmpty()) sum = WSLang.t("shrine.fuse_hint");
        else if (next == null) sum = WSLang.t("shrine.fuse_heart");
        else if (n <= 0) sum = WSLang.t("shrine.fuse_nothing", menu.fusionCost());
        else sum = WSLang.t("shrine.fuse_summary", n * menu.fusionCost(), in.getHoverName().getString(), n, new ItemStack(next).getHoverName().getString());
        g.drawWordWrap(font, net.minecraft.network.chat.FormattedText.of(sum), x + 7, py + 89, 162, th[8]);
    }

    // ─── Forge ───

    private void diamond(GuiGraphics g, int cx, int cy, int color, boolean filled) {
        for (int dy = -6; dy <= 5; dy++) {
            int w = 6 - (dy < 0 ? -dy - 1 : dy);
            if (filled) g.fill(cx - w, cy + dy, cx + w + 1, cy + dy + 1, color);
            else { g.fill(cx - w, cy + dy, cx - w + 1, cy + dy + 1, color); g.fill(cx + w, cy + dy, cx + w + 1, cy + dy + 1, color); }
        }
    }

    private void drawForge(GuiGraphics g, int x, int py) {
        specialSlot(g, menu.slots.get(3));
        specialSlot(g, menu.slots.get(4));
        small(g, WSLang.t("shrine.forge_equipment"), x + 14, py + 18, 0.75f, false, 60);
        small(g, WSLang.t("shrine.forge_fragment"), x + 14, py + 50, 0.75f, false, 60);
        ItemStack eq = menu.forgeEquipment();
        java.util.List<ShrineGear.Applied> ups = ShrineGear.upgradesOf(eq);
        String align = ShrineGear.alignOf(eq);
        boolean cons = ShrineGear.CONSECRATED.equals(align);
        int accent = align == null ? th[2] : cons ? 0xFFF2C14E : 0xFFB54BFF;
        String alignText = align == null ? WSLang.t("shrine.align.none") : (cons ? "§e" : "§d") + WSLang.t("shrine.align." + align);
        small(g, alignText, x + 62 - (int) (font.width(alignText) * 0.375f), py + 28, 0.75f, false, 80);
        // Trois emplacements d'amélioration (losanges)
        int max = ShrineUpgrades.maxSlots();
        for (int i = 0; i < Math.min(3, max); i++) {
            int cx = x + 52 + i * 16, cy = py + 46;
            diamond(g, cx, cy, i < ups.size() ? accent : th[3], i < ups.size());
        }
        String count = ups.size() + " / " + max;
        small(g, count, x + 62 - (int) (font.width(count) * 0.375f), py + 56, 0.75f, false, 40);
        // Prochain coût
        if (!eq.isEmpty() && ups.size() < max) {
            String cost = WSLang.t("shrine.forge_next", ShrineUpgrades.slotCost(ups.size()));
            small(g, cost, x + 62 - (int) (font.width(cost) * 0.375f), py + 66, 0.75f, false, 80);
        }
        // Liste des améliorations de l'objet (le détail complet est dans l'info-bulle)
        int lx = x + 96, ly = py + 20;
        g.fill(lx, ly, lx + 73, ly + 66, th[0]);
        g.fill(lx + 1, ly + 1, lx + 72, ly + 65, th[4]);
        if (ups.isEmpty()) {
            g.drawWordWrap(font, net.minecraft.network.chat.FormattedText.of(WSLang.t(eq.isEmpty() ? "shrine.forge_put" : "shrine.forge_empty")), lx + 4, ly + 5, 66, th[8]);
        }
        for (int i = 0; i < ups.size(); i++) {
            ShrineUpgrades.Def d = ShrineUpgrades.get(ups.get(i).id());
            int ry = ly + 3 + i * 21;
            String nm = (cons ? "§e" : "§d") + (d == null ? ups.get(i).id() : WSLang.t("shrine.up." + d.id())) + " §7" + ShrineGear.roman(ups.get(i).tier());
            small(g, fit(nm, 80), lx + 4, ry, 0.8f, false, 70);
            if (d != null) {
                java.util.List<String> lines = ShrineGear.effectLines(d, ups.get(i).tier());
                for (int k = 0; k < Math.min(2, lines.size()); k++) small(g, fit(lines.get(k), 112), lx + 4, ry + 7 + k * 6, 0.6f, false, 70);
            }
        }
        boolean canInsert = !eq.isEmpty() && !menu.forgeFragment().isEmpty();
        drawButton(g, x + 36, py + 90, 48, 14, WSLang.t("shrine.forge_remove"), !ups.isEmpty() && !menu.forgeFragment().isEmpty());
        drawButton(g, x + 100, py + 90, 64, 14, WSLang.t("shrine.forge_insert"), canInsert && ups.size() < max);
    }

    /** Info-bulle des améliorations de l'objet posé dans la Forge. */
    private void forgeTooltip(GuiGraphics g, int mx, int my) {
        int lx = leftPos + 96, ly = topPos + PANEL_Y + 20;
        if (mx < lx || mx >= lx + 73 || my < ly || my >= ly + 66) return;
        java.util.List<ShrineGear.Applied> ups = ShrineGear.upgradesOf(menu.forgeEquipment());
        int i = (my - ly - 3) / 21;
        if (i < 0 || i >= ups.size()) return;
        ShrineUpgrades.Def d = ShrineUpgrades.get(ups.get(i).id());
        if (d == null) return;
        java.util.List<Component> tip = new java.util.ArrayList<>();
        tip.add(Component.literal((d.align().equals(ShrineGear.CONSECRATED) ? "§e" : "§d") + WSLang.t("shrine.up." + d.id()) + " §7" + ShrineGear.roman(ups.get(i).tier())));
        for (String l : ShrineGear.effectLines(d, ups.get(i).tier())) tip.add(Component.literal(l));
        g.renderComponentTooltip(font, tip, mx, my);
    }

    /** Texte réduit (échelle {@code sc}), aligné à gauche en x ou, si {@code right}, à droite de x. */
    private void small(GuiGraphics g, String text, int x, int y, float sc, boolean right, int maxW) {
        int w = (int) (font.width(text) * sc);
        g.pose().pushPose();
        g.pose().translate(right ? x - w : x, y, 0);
        g.pose().scale(sc, sc, 1f);
        g.drawString(font, text, 0, 0, 0xFFFFFFFF, false);
        g.pose().popPose();
    }

    /** Texte tronqué à {@code w} pixels (avant réduction), avec « … » s'il est coupé. */
    private String fit(String t, int w) {
        if (font.width(t) <= w) return t;
        return font.plainSubstrByWidth(t, w - font.width("…")) + "…";
    }

    private static int darker(int c) {
        return 0xFF000000 | (((c >> 16 & 255) * 3 / 4) << 16) | (((c >> 8 & 255) * 3 / 4) << 8) | ((c & 255) * 3 / 4);
    }

    private void drawOffering(GuiGraphics g, int x, int py) {
        // Case d'offrande (case spéciale, plus vive)
        Slot os = menu.slots.get(0);
        int sx = x + os.x - 1, sy = topPos + os.y - 1;
        g.fill(sx - 1, sy - 1, sx + 19, sy + 19, th[5]);
        g.fill(sx, sy, sx + 18, sy + 18, th[6]);
        g.drawCenteredString(font, WSLang.t("shrine.offering"), sx + 9, sy - 11, th[7]);

        // Bouton « Offrir »
        boolean can = !menu.offered().isEmpty() && ShrineConfig.offerValue(menu.offered()) != 0;
        drawButton(g, x + 11, py + 66, 44, 14, WSLang.t("shrine.offer"), can);
        if (!menu.offered().isEmpty()) {
            int v = ShrineConfig.offerValue(menu.offered()) * menu.offered().getCount();
            if (v != 0) g.drawCenteredString(font, (v > 0 ? "§a+" : "§c") + v, x + 33, py + 84, 0xFFFFFFFF);
        }

        // Jauge pure ↔ corrompue : 104 × 10, dorée à gauche, violette à droite, curseur blanc
        int gx = x + 64, gy = py + 32, gw = 104, gh = 10;
        small(g, "§e" + WSLang.t("shrine.pure_label"), gx, py + 21, 0.75f, false, gw);
        small(g, "§d" + WSLang.t("shrine.corrupt_label"), gx + gw, py + 21, 0.75f, true, gw);
        g.fill(gx - 1, gy - 1, gx + gw + 1, gy + gh + 1, th[0]);
        for (int i = 0; i < gw; i++) g.fill(gx + i, gy, gx + i + 1, gy + gh, gaugeColor(i / (double) (gw - 1)));
        int max = menu.maxFaith();
        int[] marks = {menu.thrPure(), menu.thrCalm(), menu.thrSoiled(), menu.thrCorrupted()};
        for (int m : marks) {
            int mx2 = gx + (int) Math.round((max - m) / (2.0 * max) * (gw - 1));
            g.fill(mx2, gy + gh, mx2 + 1, gy + gh + 3, th[8]);
        }
        double t = (max - menu.faith()) / (2.0 * max);
        int cx = gx + (int) Math.round(Math.max(0, Math.min(1, t)) * (gw - 1));
        g.fill(cx - 1, gy - 3, cx + 1, gy + gh + 3, 0xFFFFFFFF);
        g.fill(cx - 2, gy - 4, cx + 2, gy - 3, 0xFFFFFFFF);

        // Zone d'état : étape et foi / palier supérieur / palier inférieur
        int stage = menu.stage();
        String name = WSLang.t("shrine.stage." + stage);
        g.drawString(font, name, gx, py + 55, th[7], false);
        int faith = menu.faith();
        String f = (faith > 0 ? "+" : "") + faith;
        g.drawString(font, f, gx + gw - font.width(f), py + 55, faith >= 0 ? BONUS : MALUS, false);
        int[] t0 = {menu.thrPure(), menu.thrCalm(), menu.thrSoiled() + 1, menu.thrCorrupted() + 1}; // foi minimale de l'étape 0..3
        if (stage > 0) g.drawString(font, WSLang.t("shrine.up", WSLang.t("shrine.stage." + (stage - 1)), t0[stage - 1] - faith), gx, py + 65, th[8], false);
        if (stage < 4) g.drawString(font, WSLang.t("shrine.down", WSLang.t("shrine.stage." + (stage + 1)), faith - t0[stage] + 1), gx, py + 75, th[8], false);
        if (stage == 0) g.drawString(font, WSLang.t("shrine.max_pure"), gx, py + 65, th[8], false);
        if (stage == 4) g.drawString(font, WSLang.t("shrine.max_corrupt"), gx, py + 75, th[8], false);

        // Message : dernière variation
        int d = menu.lastDelta();
        String msg = d == 0 ? WSLang.t("shrine.hint") : WSLang.t("shrine.last", (d > 0 ? "§a+" : "§c") + d);
        g.drawString(font, msg, x + 8, py + 100, th[8], false);
    }

    private void drawButton(GuiGraphics g, int bx, int by, int bw, int bh, String label, boolean enabled) {
        g.fill(bx, by, bx + bw, by + bh, th[0]);
        g.fill(bx + 1, by + 1, bx + bw - 1, by + bh - 1, enabled ? th[3] : th[4]);
        g.fill(bx + 1, by + 1, bx + bw - 1, by + 2, enabled ? th[2] : th[3]);
        g.drawCenteredString(font, label, bx + bw / 2, by + 3, enabled ? 0xFFFFFFFF : 0xFF777777);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, WSLang.t("shrine.tab." + menu.tab), 8, PANEL_Y + 6, th[7], false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
        if (menu.tab == SeraphShrineMenu.TAB_FORGE) forgeTooltip(g, mx, my);
        // Info-bulle des onglets
        for (int i = 0; i < 4; i++) {
            int tx = leftPos + TAB_X[i];
            if (mx >= tx && mx < tx + 28 && my >= topPos + 2 && my < topPos + PANEL_Y + 2) {
                String tip = WSLang.t("shrine.tab." + i) + (TAB_ENABLED[i] ? "" : " §8(" + WSLang.t("shrine.tab_soon") + ")");
                g.renderTooltip(font, Component.literal(tip), mx, my);
            }
        }
    }

    // ─── Clics ───

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int i = 0; i < 4; i++) {
                int tx = leftPos + TAB_X[i];
                if (TAB_ENABLED[i] && mx >= tx && mx < tx + 28 && my >= topPos + 2 && my < topPos + PANEL_Y + 2) {
                    if (menu.tab != i) {
                        menu.tab = i;
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i);
                    }
                    return true;
                }
            }
            int py = topPos + PANEL_Y;
            if (menu.tab == SeraphShrineMenu.TAB_OFFER && hit(mx, my, leftPos + 11, py + 66, 44, 14)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, SeraphShrineMenu.BTN_OFFER);
                return true;
            }
            if (menu.tab == SeraphShrineMenu.TAB_HARVEST && hit(mx, my, leftPos + 56, py + 90, 64, 14)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, SeraphShrineMenu.BTN_HARVEST);
                return true;
            }
            if (menu.tab == SeraphShrineMenu.TAB_FORGE && hit(mx, my, leftPos + 100, py + 90, 64, 14)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, SeraphShrineMenu.BTN_INSERT);
                return true;
            }
            if (menu.tab == SeraphShrineMenu.TAB_FORGE && hit(mx, my, leftPos + 36, py + 90, 48, 14)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, SeraphShrineMenu.BTN_REMOVE);
                return true;
            }
            if (menu.tab == SeraphShrineMenu.TAB_FUSION && hit(mx, my, leftPos + 56, py + 70, 64, 14)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, SeraphShrineMenu.BTN_FUSE);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private static boolean hit(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack st) {
        List<Component> l = super.getTooltipFromContainerItem(st);
        int v = ShrineConfig.offerValue(st);
        if (v != 0) l.add(Component.literal(WSLang.t(v > 0 ? "shrine.tip_grace" : "shrine.tip_taint", Math.abs(v))));
        return l;
    }
}
