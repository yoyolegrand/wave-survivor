package com.wavesurvivor.client;

import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.network.NetworkHandler;
import com.wavesurvivor.network.VoidMerchantPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/** 2.1 — Boutique du Marchand du Néant : 2 colonnes d'articles payés en éclats d'améthyste. */
@OnlyIn(Dist.CLIENT)
public class VoidMerchantScreen extends Screen {

    private static final int W = 360, ROW = 30, COLS = 2;
    private List<VoidMerchantPackets.Entry> entries;
    private int amethyst;
    private int left, top, h;

    private VoidMerchantScreen(List<VoidMerchantPackets.Entry> entries, int amethyst) {
        super(Component.literal(WSLang.t("rift.merchant.name")));
        this.entries = entries;
        this.amethyst = amethyst;
    }

    /** Paquet reçu : ouvre l'écran, ou le rafraîchit s'il est déjà ouvert (après un achat). */
    public static void receive(List<VoidMerchantPackets.Entry> entries, int amethyst) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof VoidMerchantScreen s) {
            s.entries = entries;
            s.amethyst = amethyst;
            s.rebuildWidgets();
        } else {
            mc.setScreen(new VoidMerchantScreen(entries, amethyst));
        }
    }

    private int rows() { return (entries.size() + COLS - 1) / COLS; }

    @Override
    protected void init() {
        h = 44 + rows() * ROW + 8;
        left = (width - W) / 2;
        top = Math.max(4, (height - h) / 2);
        int cw = (W - 24) / COLS;
        for (int i = 0; i < entries.size(); i++) {
            VoidMerchantPackets.Entry e = entries.get(i);
            int x = left + 8 + (i / rows()) * (cw + 8), y = top + 40 + (i % rows()) * ROW;
            final int idx = i;
            Button b = Button.builder(Component.literal(e.left() == 0 ? "§8" + WSLang.t("rift.merchant.btn_out") : "§a" + WSLang.t("rift.merchant.btn_buy")),
                    btn -> NetworkHandler.CHANNEL.sendToServer(new VoidMerchantPackets.Buy(idx))).bounds(x + cw - 42, y + 4, 40, 18).build();
            b.active = e.left() != 0 && amethyst >= e.price();
            addRenderableWidget(b);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(left, top, left + W, top + h, 0xF0120A1C);
        g.fill(left, top, left + W, top + 2, 0xFFB04DFF);
        g.fill(left, top + h - 2, left + W, top + h, 0xFFB04DFF);
        g.drawCenteredString(font, "§d§l" + WSLang.t("rift.merchant.name"), left + W / 2, top + 8, 0xFFFFFFFF);
        g.renderItem(new ItemStack(Items.AMETHYST_SHARD), left + W / 2 - 30, top + 20);
        g.drawString(font, "§f" + amethyst, left + W / 2 - 12, top + 24, 0xFFFFFFFF);
        g.drawString(font, "§7" + WSLang.t("rift.merchant.hint"), left + 10, top + 24, 0xFFAAAAAA);
        int cw = (W - 24) / COLS;
        String tip = null;
        for (int i = 0; i < entries.size(); i++) {
            VoidMerchantPackets.Entry e = entries.get(i);
            int x = left + 8 + (i / rows()) * (cw + 8), y = top + 40 + (i % rows()) * ROW;
            g.fill(x, y, x + cw, y + ROW - 3, 0x30B04DFF);
            g.renderItem(e.stack(), x + 4, y + 5);
            g.renderItemDecorations(font, e.stack(), x + 4, y + 5);
            String name = e.stack().getHoverName().getString();
            if (font.width(name) > cw - 72) name = font.plainSubstrByWidth(name, cw - 76) + "…";
            g.drawString(font, "§f" + name, x + 24, y + 4, 0xFFFFFFFF);
            String stock = e.left() < 0 ? "" : " §8· " + WSLang.t("rift.merchant.left", e.left());
            g.drawString(font, (amethyst >= e.price() ? "§d" : "§c") + e.price() + " ✦" + stock, x + 24, y + 15, 0xFFFFFFFF);
            if (mx >= x && mx < x + cw - 44 && my >= y && my < y + ROW - 3) tip = WSLang.t("rift.merchant.desc." + i);
        }
        super.render(g, mx, my, pt);
        if (tip != null) g.renderTooltip(font, font.split(Component.literal(tip), 200), mx, my);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
