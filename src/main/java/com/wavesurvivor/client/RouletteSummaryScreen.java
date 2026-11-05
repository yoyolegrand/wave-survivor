package com.wavesurvivor.client;

import com.wavesurvivor.horde.roulette.RouletteChestManager;
import com.wavesurvivor.horde.roulette.RouletteReward;
import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.network.RouletteSummaryPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Résumé de plusieurs ouvertures d'un coup : « ×12 ouvertures », le meilleur gain en vedette, puis tous les gains (rares d'abord). */
@OnlyIn(Dist.CLIENT)
public class RouletteSummaryScreen extends Screen {

    private final RouletteSummaryPacket pkt;
    private final List<RouletteSummaryPacket.Entry> sorted = new ArrayList<>();
    private int scroll = 0;
    private int left, top, panelW, panelH;

    private RouletteSummaryScreen(RouletteSummaryPacket pkt) {
        super(Component.literal(WSLang.t("roulette.summary.title")));
        this.pkt = pkt;
        sorted.addAll(pkt.entries);
        sorted.sort(Comparator.comparingInt(RouletteSummaryPacket.Entry::rarity).reversed().thenComparing(Comparator.comparingInt(RouletteSummaryPacket.Entry::qty).reversed()));
    }

    public static void open(RouletteSummaryPacket pkt) { Minecraft.getInstance().setScreen(new RouletteSummaryScreen(pkt)); }

    private static int color(int rarity) {
        RouletteReward.Rarity[] r = RouletteReward.Rarity.values();
        return RouletteChestManager.rarityColor(r[Math.max(0, Math.min(r.length - 1, rarity))]);
    }

    @Override
    protected void init() {
        panelW = Math.min(320, width - 16);
        panelH = Math.min(240, height - 16);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        addRenderableWidget(Button.builder(Component.literal(WSLang.t("ui.fermer")), b -> onClose()).bounds(left + panelW / 2 - 40, top + panelH - 24, 80, 18).build());
    }

    private int rowsVisible() { return Math.max(1, (panelH - 120) / 20); }

    @Override
    public boolean mouseScrolled(double mx, double my, double d) {
        scroll = Math.max(0, Math.min(Math.max(0, sorted.size() - 1 - rowsVisible()), scroll - (int) Math.signum(d)));
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(left, top, left + panelW, top + panelH, 0xF0141018);
        g.fill(left, top, left + panelW, top + 2, 0xFFF59E0B);
        g.drawCenteredString(font, "§6§l" + WSLang.t("roulette.summary.opens", pkt.opens), left + panelW / 2, top + 8, 0xFFFFFFFF);
        g.drawCenteredString(font, "§7" + WSLang.t(pkt.chestName), left + panelW / 2, top + 20, 0xFFFFFFFF);
        if (!sorted.isEmpty()) {
            RouletteSummaryPacket.Entry best = sorted.get(0); // gain le plus rare
            int bx = left + 10, by = top + 36, bw = panelW - 20;
            g.fill(bx, by, bx + bw, by + 38, 0x55000000 | color(best.rarity()));
            g.fill(bx, by, bx + 2, by + 38, 0xFF000000 | color(best.rarity()));
            g.pose().pushPose();
            g.pose().translate(bx + 8, by + 6, 0);
            g.pose().scale(1.6f, 1.6f, 1f);
            g.renderItem(best.icon(), 0, 0);
            g.pose().popPose();
            g.drawString(font, "§e" + WSLang.t("roulette.summary.best"), bx + 40, by + 6, 0xFFFFFFFF);
            g.drawString(font, "§f" + best.qty() + "× " + best.name(), bx + 40, by + 20, 0xFFFFFFFF);
        }
        int y = top + 82, vis = rowsVisible();
        g.drawString(font, "§e" + WSLang.t("roulette.summary.all"), left + 10, y - 2, 0xFFFFFFFF);
        y += 10;
        int first = 1 + scroll;
        for (int i = first; i < sorted.size() && i < first + vis; i++, y += 20) {
            RouletteSummaryPacket.Entry e = sorted.get(i);
            g.fill(left + 10, y, left + panelW - 10, y + 18, 0x22FFFFFF);
            g.fill(left + 10, y, left + 12, y + 18, 0xFF000000 | color(e.rarity()));
            g.renderItem(e.icon(), left + 16, y + 1);
            g.drawString(font, "§f" + e.qty() + "× §7" + e.name(), left + 38, y + 5, 0xFFFFFFFF);
        }
        super.render(g, mx, my, pt);
    }
}
