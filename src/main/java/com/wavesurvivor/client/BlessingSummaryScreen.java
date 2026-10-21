package com.wavesurvivor.client;

import com.wavesurvivor.horde.benediction.BlessingEffects;
import com.wavesurvivor.horde.benediction.RogueUpgrade;
import com.wavesurvivor.horde.benediction.RogueUpgradeRegistry;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bonus cumulés de toutes les bénédictions de la partie en cours (1.8) : colonne de gauche = totaux, colonne de droite = liste
 * des bénédictions. Calculé à partir des bénédictions du joueur, donc vide dès que la horde est terminée.
 */
@OnlyIn(Dist.CLIENT)
public class BlessingSummaryScreen extends Screen {

    private final Map<String, Integer> owned;
    private final Screen parent;
    private final List<String> totals = new ArrayList<>();
    private final List<String> list = new ArrayList<>();
    private int scroll = 0;

    public BlessingSummaryScreen(Map<String, Integer> owned, Screen parent) {
        super(Component.literal(WSLang.t("bless.mine_title")));
        this.owned = owned;
        this.parent = parent;
        compute();
    }

    private record Key(Attribute attr, AttributeModifier.Operation op) {}

    private void compute() {
        Map<Key, Double> sums = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : owned.entrySet()) {
            RogueUpgrade u = RogueUpgradeRegistry.get(e.getKey());
            if (u == null) continue;
            int lvl = e.getValue();
            list.add("§f" + u.name() + (lvl > 1 ? " §7x" + lvl : ""));
            for (RogueUpgrade.AttributeMod m : u.modifiers()) sums.merge(new Key(m.attribute(), m.operation()), m.amount() * lvl, Double::sum);
        }
        for (Map.Entry<Key, Double> e : sums.entrySet()) {
            totals.add("§a" + BenedictionSelectionScreen.formatModifier(new RogueUpgrade.AttributeMod(e.getKey().attr(), e.getValue(), e.getKey().op())));
        }
        for (RogueUpgrade.Effect fx : BlessingEffects.aggregate(owned)) totals.add("§b" + BlessingEffects.describe(fx));
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal(WSLang.t("ui.fermer")), b -> onClose())
                .bounds(width / 2 - 40, height - 28, 80, 20).build());
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Math.max(0, scroll - (int) Math.signum(delta));
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, Component.literal("§l" + WSLang.t("bless.mine_title")), width / 2, 14, 0xFFFFFF);
        if (owned.isEmpty()) {
            g.drawCenteredString(font, Component.literal("§7" + WSLang.t("bless.mine_none")), width / 2, height / 2, 0xAAAAAA);
            super.render(g, mx, my, pt);
            return;
        }
        int colW = Math.min(220, (width - 40) / 2);
        int lx = width / 2 - colW - 10, rx = width / 2 + 10;
        g.drawString(font, "§6" + WSLang.t("bless.mine_total"), lx, 34, 0xFFFFFF);
        g.drawString(font, "§6" + WSLang.t("bless.mine_list"), rx, 34, 0xFFFFFF);
        int rows = (height - 34 - 44) / (font.lineHeight + 2);
        scroll = Math.min(scroll, Math.max(0, Math.max(totals.size(), list.size()) - rows));
        for (int i = 0; i < rows; i++) {
            int y = 48 + i * (font.lineHeight + 2);
            if (scroll + i < totals.size()) g.drawString(font, font.plainSubstrByWidth(totals.get(scroll + i), colW), lx, y, 0xFFFFFF);
            if (scroll + i < list.size()) g.drawString(font, font.plainSubstrByWidth(list.get(scroll + i), colW), rx, y, 0xFFFFFF);
        }
        super.render(g, mx, my, pt);
    }
}
