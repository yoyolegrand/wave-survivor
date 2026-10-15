package com.wavesurvivor.client;

import com.wavesurvivor.horde.kingdom.OfferingBrazierBlockEntity;
import com.wavesurvivor.horde.kingdom.OfferingBrazierMenu;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Écran du BRASIER DES OFFRANDES : niveau, jauge, total, Calme, bouton Sacrifier, choix de spécialisation. */
@OnlyIn(Dist.CLIENT)
public class OfferingBrazierScreen extends AbstractContainerScreen<OfferingBrazierMenu> {

    private Button sacrifice;
    private final Button[] specs = new Button[3];
    private static final String[] SPEC = {"abundance", "relics", "war"};

    public OfferingBrazierScreen(OfferingBrazierMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 176;
        imageHeight = 236;
        inventoryLabelY = OfferingBrazierMenu.INV_Y - 11;
        titleLabelY = 6;
    }

    @Override
    protected void init() {
        super.init();
        sacrifice = addRenderableWidget(Button.builder(Component.literal(WSLang.t("ui.brazier.sacrifice")), b -> click(0))
                .bounds(leftPos + imageWidth - 80, topPos + 47, 72, 16)
                .tooltip(Tooltip.create(WSLang.c("ui.brazier.sacrifice_tip"))).build());
        for (int i = 0; i < 3; i++) {
            final int id = i + 1;
            specs[i] = addRenderableWidget(Button.builder(Component.literal(WSLang.t("kingdom.brazier.variant." + SPEC[i])), b -> click(id))
                    .bounds(leftPos + 7 + i * 55, topPos + 65, 53, 14)
                    .tooltip(Tooltip.create(WSLang.c("kingdom.brazier.variant_desc." + SPEC[i]))).build());
        }
        refresh();
    }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private void refresh() {
        boolean choose = menu.level() >= 3 && menu.variantIndex() == 0;
        for (Button b : specs) if (b != null) b.visible = choose;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refresh();
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        // Fond
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xF0201820);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, 0xF0302530);
        // Cases (grille d'offrandes + inventaire)
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++) slot(g, x + 7 + c * 18, y + OfferingBrazierMenu.GRID_Y - 1 + r * 18, 0xFF3A2A3A);
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++) slot(g, x + 7 + c * 18, y + OfferingBrazierMenu.INV_Y - 1 + r * 18, 0xFF2A2A2A);
        for (int c = 0; c < 9; c++) slot(g, x + 7 + c * 18, y + OfferingBrazierMenu.INV_Y + 57, 0xFF2A2A2A);
        // Jauge
        int gx = x + 8, gy = y + 29, gw = imageWidth - 16;
        float f = Math.min(1f, menu.gauge() / (float) menu.perRoll());
        g.fill(gx - 1, gy - 1, gx + gw + 1, gy + 7, 0xFF000000);
        g.fill(gx, gy, gx + gw, gy + 6, 0xFF2A1A2A);
        g.fill(gx, gy, gx + (int) (gw * f), gy + 6, variantColor());
    }

    private static void slot(GuiGraphics g, int x, int y, int col) {
        g.fill(x, y, x + 18, y + 18, 0xFF101010);
        g.fill(x + 1, y + 1, x + 17, y + 17, col);
    }

    private int variantColor() {
        return switch (menu.variantIndex()) {
            case 1 -> 0xFFFFC040;   // Abondance : or
            case 2 -> 0xFFB060FF;   // Reliques : violet
            case 3 -> 0xFFFF5040;   // Guerre : rouge
            default -> 0xFF40D0FF;  // feu des âmes
        };
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        int lvl = menu.level();
        g.drawString(font, "§6§l" + title.getString() + " §e" + "★".repeat(lvl) + "§8" + "★".repeat(3 - lvl), 8, 6, 0xFFFFFFFF, false);
        int left = menu.perRoll() - menu.gauge();
        g.drawString(font, "§7" + WSLang.t("ui.brazier.gauge", menu.gauge(), menu.perRoll(), left), 8, 18, 0xFFFFFFFF, false);
        String next = lvl >= 3 ? WSLang.t("ui.brazier.max")
                : WSLang.t("ui.brazier.next", lvl == 1 ? OfferingBrazierBlockEntity.TIER2 : OfferingBrazierBlockEntity.TIER3);
        g.drawString(font, "§f" + WSLang.t("ui.brazier.total", menu.total()) + " §8· §7" + next, 8, 39, 0xFFFFFFFF, false);
        boolean full = menu.calmUsed() >= menu.calmCap();
        g.drawString(font, (full ? "§c" : "§7") + WSLang.t("ui.brazier.calm", menu.calmUsed(), menu.calmCap()), 8, 51, 0xFFFFFFFF, false);
        if (!(lvl >= 3 && menu.variantIndex() == 0)) {
            String spec = menu.variantIndex() > 0 ? "§d" + WSLang.t("kingdom.brazier.variant." + SPEC[menu.variantIndex() - 1])
                    : "§8" + WSLang.t("ui.brazier.spec_at3");
            g.drawString(font, spec, 8, 68, 0xFFFFFFFF, false);
        }
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFAAAAAA, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }
}
