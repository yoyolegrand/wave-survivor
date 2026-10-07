package com.wavesurvivor.client;

import com.wavesurvivor.config.HordeExchange;
import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.network.HordeExchangePackets;
import com.wavesurvivor.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * /ws import : liste des hordes déposées dans config/wavesurvivor/import/.
 * Clic sur une ligne → fiche de confirmation (type, description, contenu, mods manquants, conflit de nom) →
 * « Importer », ou « Remplacer » / « Garder les deux » si une horde du même nom existe déjà.
 */
@OnlyIn(Dist.CLIENT)
public class HordeImportScreen extends Screen {

    private static final int W = 380, H = 270, ROW = 22, PER_PAGE = 8;

    private final List<HordeExchange.Entry> entries;
    private HordeExchange.Entry selected = null;
    private int page = 0;
    private int left, top;

    public HordeImportScreen(List<HordeExchange.Entry> entries) {
        super(Component.literal(WSLang.t("exchange.screen.title")));
        this.entries = entries;
    }

    public static void open(List<HordeExchange.Entry> entries) {
        Minecraft.getInstance().setScreen(new HordeImportScreen(entries));
    }

    @Override
    protected void init() {
        clearWidgets();
        left = (width - W) / 2;
        top = (height - H) / 2;
        if (selected == null) initList();
        else initConfirm();
        addRenderableWidget(Button.builder(Component.literal(WSLang.t("ui.fermer")), b -> onClose())
                .bounds(left + W - 80, top + H - 26, 70, 18).build());
    }

    private static String typeTag(HordeExchange.Entry e) {
        return "kingdom".equals(e.type()) ? WSLang.t("exchange.type.kingdom") : WSLang.t("exchange.type.classic");
    }

    private void initList() {
        int pages = Math.max(1, (entries.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.min(page, pages - 1);
        int y = top + 40;
        for (int i = page * PER_PAGE; i < Math.min(entries.size(), (page + 1) * PER_PAGE); i++) {
            HordeExchange.Entry e = entries.get(i);
            String label = typeTag(e) + " §f" + e.name() + (e.conflict() ? " §e●" : "") + (e.missingMods().isEmpty() ? "" : " §c⚠")
                    + " §8— " + e.file();
            addRenderableWidget(Button.builder(Component.literal(label), b -> { selected = e; init(); })
                    .tooltip(Tooltip.create(Component.literal(e.description() == null || e.description().isBlank()
                            ? WSLang.t("exchange.no_desc") : e.description())))
                    .bounds(left + 10, y, W - 20, 20).build());
            y += ROW;
        }
        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("◀"), b -> { page = Math.max(0, page - 1); init(); })
                    .bounds(left + 10, top + H - 26, 20, 18).build()).active = page > 0;
            addRenderableWidget(Button.builder(Component.literal("▶"), b -> { page = Math.min(pages - 1, page + 1); init(); })
                    .bounds(left + 70, top + H - 26, 20, 18).build()).active = page < pages - 1;
        }
        // Actualiser : relit le dossier import (nouveaux fichiers déposés)
        addRenderableWidget(Button.builder(Component.literal(WSLang.t("exchange.refresh")), b -> {
            var p = Minecraft.getInstance().player;
            if (p != null) p.connection.sendCommand("ws import");
        }).bounds(left + W - 168, top + H - 26, 84, 18).build());
    }

    private void initConfirm() {
        HordeExchange.Entry e = selected;
        int by = top + H - 52;
        if (e.conflict()) {
            addRenderableWidget(Button.builder(Component.literal(WSLang.t("exchange.replace")), b -> send(HordeExchange.MODE_REPLACE))
                    .tooltip(Tooltip.create(WSLang.c("exchange.replace.tip")))
                    .bounds(left + 10, by, 120, 20).build());
            addRenderableWidget(Button.builder(Component.literal(WSLang.t("exchange.keep_both")), b -> send(HordeExchange.MODE_KEEP_BOTH))
                    .tooltip(Tooltip.create(WSLang.c("exchange.keep_both.tip")))
                    .bounds(left + 136, by, 120, 20).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal(WSLang.t("exchange.import")), b -> send(HordeExchange.MODE_NEW))
                    .bounds(left + 10, by, 160, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal(WSLang.t("ui.shop.role_back")), b -> { selected = null; init(); })
                .bounds(left + 10, top + H - 26, 80, 18).build());
    }

    private void send(int mode) {
        NetworkHandler.CHANNEL.sendToServer(new HordeExchangePackets.DoImport(selected.file(), mode));
        selected = null;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(left, top, left + W, top + H, 0xF00C1018);
        g.fill(left, top, left + W, top + 2, 0xFF3B82F6);
        g.drawCenteredString(font, WSLang.t("exchange.screen.title"), width / 2, top + 10, 0xFFFFFFFF);
        if (selected == null) {
            if (entries.isEmpty()) {
                int y = top + 60;
                for (var line : font.split(Component.literal(WSLang.t("exchange.empty")), W - 40)) {
                    g.drawString(font, line, width / 2 - font.width(line) / 2, y, 0xFFFFFFFF);
                    y += 11;
                }
            } else {
                g.drawCenteredString(font, WSLang.t("exchange.pick"), width / 2, top + 24, 0xFFAAAAAA);
            }
        } else {
            HordeExchange.Entry e = selected;
            int x = left + 14, y = top + 30;
            g.drawString(font, WSLang.t("exchange.confirm", typeTag(e), e.name()), x, y, 0xFFFFFFFF);
            y += 14;
            g.drawString(font, WSLang.t("exchange.details", e.waves(), e.bosses(), e.entities(), e.skills(), e.chests()), x, y, 0xFFFFFFFF);
            y += 12;
            if (e.author() != null && !e.author().isBlank()) {
                g.drawString(font, WSLang.t("exchange.author", e.author()), x, y, 0xFFFFFFFF);
                y += 12;
            }
            y += 4;
            String desc = e.description() == null || e.description().isBlank() ? WSLang.t("exchange.no_desc") : "§7" + e.description();
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(desc), W - 28);
            for (int i = 0; i < Math.min(6, lines.size()); i++) {
                g.drawString(font, lines.get(i), x, y, 0xFFFFFFFF);
                y += 10;
            }
            y += 6;
            if (!e.missingMods().isEmpty()) {
                for (var line : font.split(Component.literal(WSLang.t("exchange.missing_mods", String.join(", ", e.missingMods()))), W - 28)) {
                    g.drawString(font, line, x, y, 0xFFFFFFFF);
                    y += 10;
                }
                y += 2;
            }
            if (e.conflict()) {
                for (var line : font.split(Component.literal(WSLang.t("exchange.conflict", e.name())), W - 28)) {
                    g.drawString(font, line, x, y, 0xFFFFFFFF);
                    y += 10;
                }
            }
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
