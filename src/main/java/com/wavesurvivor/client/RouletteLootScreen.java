package com.wavesurvivor.client;

import com.wavesurvivor.horde.roulette.RouletteKeyItem;
import com.wavesurvivor.network.RouletteLootPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Locale;

/** Aperçu du loot d'un coffre roulette (clic gauche en survie) : items, quantités, chances, rareté. */
@OnlyIn(Dist.CLIENT)
public class RouletteLootScreen extends Screen {

    private static final int PANEL_W = 260;
    private static final int ROW_H = 22;
    private static final int MAX_ROWS = 8;
    private static final String[] RARITY_NAME = {"Commun", "Peu commun", "Rare", "Épique", "Légendaire"};
    private static final String[] RARITY_CODE = {"§7", "§a", "§9", "§5", "§6"};
    private static final int[] RARITY_RGB = {0xFFAAAAAA, 0xFF55FF55, 0xFF5555FF, 0xFFAA00AA, 0xFFFFAA00};

    private final RouletteLootPacket data;
    private int left, top, panelH, scroll;

    public RouletteLootScreen(RouletteLootPacket data) {
        super(Component.literal(data.chestName));
        this.data = data;
    }

    private int visibleRows() { return Math.min(MAX_ROWS, Math.max(1, data.entries.size())); }

    @Override
    protected void init() {
        panelH = 58 + visibleRows() * ROW_H + 30;
        left = (width - PANEL_W) / 2;
        top = (height - panelH) / 2;
        clearWidgets();
        addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.fermer")), b -> onClose())
                .bounds(width / 2 - 40, top + panelH - 24, 80, 18).build());
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int max = Math.max(0, data.entries.size() - MAX_ROWS);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int accent = 0xFF000000 | RouletteKeyItem.colorForName(data.chestName);
        g.fill(left, top, left + PANEL_W, top + panelH, 0xF0101014);
        g.fill(left, top, left + PANEL_W, top + 2, accent);
        g.fill(left, top + panelH - 2, left + PANEL_W, top + panelH, accent);

        g.drawCenteredString(font, "§l" + data.chestName, width / 2, top + 8, accent);
        g.drawCenteredString(font, com.wavesurvivor.i18n.WSLang.t("ui.cle_requise") + data.keyName, width / 2, top + 21, 0xFFFFFFFF);
        g.drawCenteredString(font, data.bonusWave > 0
                        ? com.wavesurvivor.i18n.WSLang.t("ui.chances_ajustees_vague") + data.bonusWave + ")"
                        : com.wavesurvivor.i18n.WSLang.t("ui.chances_de_base"), width / 2, top + 33, 0xFFFFFFFF);

        int y0 = top + 48;
        if (data.entries.isEmpty()) {
            g.drawCenteredString(font, com.wavesurvivor.i18n.WSLang.t("ui.aucun_loot_configure"), width / 2, y0 + 6, 0xFFFFFFFF);
        }
        for (int i = 0; i < visibleRows() && i + scroll < data.entries.size(); i++) {
            RouletteLootPacket.Entry e = data.entries.get(i + scroll);
            int y = y0 + i * ROW_H;
            int r = Math.max(0, Math.min(4, e.rarity()));
            g.fill(left + 6, y, left + PANEL_W - 6, y + ROW_H - 2, 0x22FFFFFF);
            g.fill(left + 6, y, left + 8, y + ROW_H - 2, RARITY_RGB[r]);
            g.renderItem(e.icon(), left + 12, y + 2);

            String qty = e.minQty() == e.maxQty() ? "×" + e.minQty() : "×" + e.minQty() + "–" + e.maxQty();
            String name = e.name();
            int maxNameW = PANEL_W - 110;
            if (font.width(name) > maxNameW) name = font.plainSubstrByWidth(name, maxNameW - 6) + "…";
            g.drawString(font, RARITY_CODE[r] + name + " §7" + qty, left + 32, y + 3, 0xFFFFFFFF);
            g.drawString(font, "§8" + com.wavesurvivor.i18n.WSLang.t(RARITY_NAME[r]), left + 32, y + 12, 0xFFFFFFFF);

            String pct = fmtPct(e.percent());
            g.drawString(font, "§f" + pct, left + PANEL_W - 12 - font.width(pct), y + 3, 0xFFFFFFFF);
            // Barre de chance
            int bw = 44, bx = left + PANEL_W - 12 - bw, by = y + 14;
            g.fill(bx, by, bx + bw, by + 3, 0xFF2A2A2A);
            g.fill(bx, by, bx + Math.max(1, (int) (bw * Math.min(1.0, e.percent() / 100.0))), by + 3, RARITY_RGB[r]);
        }
        if (data.entries.size() > MAX_ROWS) {
            g.drawCenteredString(font, com.wavesurvivor.i18n.WSLang.t("ui.molette") + (scroll + 1) + "–" + Math.min(data.entries.size(), scroll + MAX_ROWS)
                    + " / " + data.entries.size(), width / 2, y0 + visibleRows() * ROW_H + 2, 0xFFFFFFFF);
        }
        super.render(g, mx, my, pt);
    }

    private static String fmtPct(double p) {
        if (p >= 10) return String.format(Locale.ROOT, "%.0f%%", p);
        if (p >= 1) return String.format(Locale.ROOT, "%.1f%%", p);
        return String.format(Locale.ROOT, "%.2f%%", p);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
