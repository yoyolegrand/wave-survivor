package com.wavesurvivor.client;

import com.wavesurvivor.horde.editor.HordeTemplates;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/** « Nouvelle horde » : choix d'un modèle de départ (Classique courte / longue, Kingdom, Vide). */
@OnlyIn(Dist.CLIENT)
public class HordeTemplateScreen extends Screen {

    private static final int PANEL_W = 360, CARD_W = 168, CARD_H = 70;
    private static final String[] ICONS = {"⚔", "🏰", "♛", "📄"};
    private final HordeEditorListScreen parent;
    private int left, top;

    public HordeTemplateScreen(HordeEditorListScreen parent) {
        super(Component.literal(WSLang.t("tpl.title")));
        this.parent = parent;
    }

    private int panelH() { return 36 + 2 * (CARD_H + 6) + 30; }

    @Override
    protected void init() {
        clearWidgets();
        left = (width - PANEL_W) / 2;
        top = Math.max(4, (height - panelH()) / 2);
        for (int i = 0; i < HordeTemplates.IDS.length; i++) {
            String id = HordeTemplates.IDS[i];
            int x = left + 8 + (i % 2) * (CARD_W + 8), y = top + 28 + (i / 2) * (CARD_H + 6);
            addRenderableWidget(Button.builder(Component.empty(), b -> pick(id)).bounds(x, y, CARD_W, CARD_H).build());
        }
        addRenderableWidget(Button.builder(Component.literal(WSLang.t("ui.annuler")), b -> onClose())
                .bounds(left + PANEL_W / 2 - 50, top + panelH() - 26, 100, 20).build());
    }

    private void pick(String id) {
        String name = parent.uniqueName(WSLang.t("tpl." + id));
        Minecraft.getInstance().setScreen(new HordeEditorScreen(parent,
                HordeTemplates.build(id, name), "", parent.customEntities, ""));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(left, top, left + PANEL_W, top + panelH(), 0xF0101418);
        g.fill(left, top, left + PANEL_W, top + 2, 0xFFF59E0B);
        g.drawString(font, "§l" + WSLang.t("tpl.title"), left + 8, top + 9, 0xFFFFFFFF);
        g.drawString(font, "§7" + WSLang.t("tpl.subtitle"), left + 8, top + 18, 0xFFFFFFFF);
        super.render(g, mx, my, pt);
        // Contenu des cartes (dessiné par-dessus les boutons)
        for (int i = 0; i < HordeTemplates.IDS.length; i++) {
            String id = HordeTemplates.IDS[i];
            int x = left + 8 + (i % 2) * (CARD_W + 8), y = top + 28 + (i / 2) * (CARD_H + 6);
            g.drawString(font, "§e" + ICONS[i] + " §l" + WSLang.t("tpl." + id), x + 6, y + 6, 0xFFFFFFFF);
            List<FormattedCharSequence> lines = font.split(Component.literal("§7" + WSLang.t("tpl." + id + ".desc")), CARD_W - 12);
            for (int l = 0; l < Math.min(5, lines.size()); l++) g.drawString(font, lines.get(l), x + 6, y + 19 + l * 10, 0xFFFFFFFF);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
