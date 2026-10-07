package com.wavesurvivor.client;

import com.wavesurvivor.horde.mutator.Mutator;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Set;

/**
 * ÉCRAN DES MUTATEURS (ouvert depuis l'écran de l'autel) : pastilles à cocher, groupées par catégorie, survol = effet
 * et bonus, multiplicateur total en direct. La sélection est gardée dans {@link AltarConfirmScreen#SELECTED}.
 */
@OnlyIn(Dist.CLIENT)
public class MutatorScreen extends Screen {

    private static final int CHIP_W = 132, CHIP_H = 18, COLS = 3, GAP = 4;
    private final Screen parent;

    public MutatorScreen(Screen parent) {
        super(Component.literal(WSLang.t("mutator.title")));
        this.parent = parent;
    }

    private Set<Mutator> sel() { return AltarConfirmScreen.SELECTED; }

    private int gridX() { return width / 2 - (COLS * CHIP_W + (COLS - 1) * GAP) / 2; }

    @Override
    protected void init() {
        clearWidgets();
        int x0 = gridX(), y = 46;
        for (Mutator.Category cat : Mutator.Category.values()) {
            y += 12; // titre de catégorie (dessiné dans render)
            int col = 0;
            for (Mutator m : Mutator.values()) {
                if (m.category != cat) continue;
                boolean on = sel().contains(m);
                String label = (on ? "\u00a7a\u2714 " : "\u00a77") + m.icon + " " + WSLang.t("mutator." + m.id) + " \u00a78+" + m.bonus + "%";
                Button b = Button.builder(Component.literal(label), btn -> toggle(m))
                        .bounds(x0 + col * (CHIP_W + GAP), y, CHIP_W, CHIP_H).build();
                String tip = "\u00a7f" + m.icon + " " + WSLang.t("mutator." + m.id) + "\n\u00a77" + WSLang.t("mutator." + m.id + ".desc")
                        + "\n\u00a7a" + WSLang.t("mutator.bonus", m.bonus)
                        + (m.kingdomAllowed() ? "" : "\n\u00a7c" + WSLang.t("mutator.no_kingdom"))
                        + (m.exclusive() != null ? "\n\u00a78" + WSLang.t("mutator.exclusive", WSLang.t("mutator." + m.exclusive().id)) : "");
                b.setTooltip(Tooltip.create(Component.literal(tip)));
                addRenderableWidget(b);
                if (++col >= COLS) { col = 0; y += CHIP_H + GAP; }
            }
            if (col != 0) y += CHIP_H + GAP;
        }
        int by = Math.min(height - 28, y + 22);
        addRenderableWidget(Button.builder(Component.literal(WSLang.t("mutator.clear")), b -> { sel().clear(); init(); })
                .bounds(width / 2 - 154, by, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("\u00a7a\u2714 " + WSLang.t("mutator.done")), b -> onClose())
                .bounds(width / 2 + 54, by, 100, 20).build());
    }

    private void toggle(Mutator m) {
        if (!sel().remove(m)) {
            if (m.exclusive() != null) sel().remove(m.exclusive());
            sel().add(m);
        }
        init();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(0, 0, width, height, 0xCC000000);
        g.drawCenteredString(font, "\u00a75\u00a7l\ud83c\udfb2 " + WSLang.t("mutator.title"), width / 2, 14, 0xFFFFFFFF);
        g.drawCenteredString(font, "\u00a77" + WSLang.t("mutator.subtitle"), width / 2, 28, 0xFFFFFFFF);
        int x0 = gridX(), y = 46;
        for (Mutator.Category cat : Mutator.Category.values()) {
            g.drawString(font, "\u00a76\u00a7l" + WSLang.t("mutator.cat." + cat.name().toLowerCase()), x0, y + 2, 0xFFFFFFFF);
            y += 12;
            int n = 0;
            for (Mutator m : Mutator.values()) if (m.category == cat) n++;
            y += ((n + COLS - 1) / COLS) * (CHIP_H + GAP);
        }
        String mult = String.format(java.util.Locale.ROOT, "%.2f", Mutator.multiplier(sel())).replace('.', ',');
        int by = Math.min(height - 28, y + 22);
        g.drawCenteredString(font, WSLang.t("mutator.total", sel().size(), mult), width / 2, by - 12, 0xFFFFFFFF);
        super.render(g, mx, my, pt);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
