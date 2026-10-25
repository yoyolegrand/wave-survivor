package com.wavesurvivor.client;

import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.registry.ModItems;
import com.wavesurvivor.shrine.SeraphShrineMenu;
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
 * panneau à partir de y = 24. Étape en cours : onglet Offrandes (jauge, offrande, état). Les autres onglets sont grisés.
 */
@OnlyIn(Dist.CLIENT)
public class SeraphShrineScreen extends AbstractContainerScreen<SeraphShrineMenu> {

    /** Onglets déjà actifs (les suivants arrivent aux étapes 3 et 4 du développement). */
    private static final boolean[] TAB_ENABLED = {true, false, false, false};
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
        th = THEMES[theme(menu.stage())];
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
        for (int i = 1; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            int sx = x + s.x - 1, sy = y + s.y - 1;
            g.fill(sx, sy, sx + 18, sy + 18, th[3]);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, th[4]);
        }
        if (menu.tab == SeraphShrineMenu.TAB_OFFER) drawOffering(g, x, py);
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
        g.drawString(font, "§e" + WSLang.t("shrine.pure_label"), gx, py + 21, th[7], false);
        String corr = WSLang.t("shrine.corrupt_label");
        g.drawString(font, "§d" + corr, gx + gw - font.width(corr), py + 21, th[7], false);
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
        // Libellé inventaire
        g.drawString(font, WSLang.t("shrine.inventory"), x + 8, py + 113, th[9], false);
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
            if (menu.tab == SeraphShrineMenu.TAB_OFFER) {
                int bx = leftPos + 11, by = topPos + PANEL_Y + 66;
                if (mx >= bx && mx < bx + 44 && my >= by && my < by + 14) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, SeraphShrineMenu.BTN_OFFER);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack st) {
        List<Component> l = super.getTooltipFromContainerItem(st);
        int v = ShrineConfig.offerValue(st);
        if (v != 0) l.add(Component.literal(WSLang.t(v > 0 ? "shrine.tip_grace" : "shrine.tip_taint", Math.abs(v))));
        return l;
    }
}
