package com.wavesurvivor.client;

import com.wavesurvivor.horde.kingdom.GleanerMenu;
import com.wavesurvivor.horde.kingdom.GleanerSort;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** STOCKAGE D'UN GLANEUR : onglets vers les Glaneurs proches, filtre de tri (6 catégories), stockage + inventaire. */
@OnlyIn(Dist.CLIENT)
public class GleanerScreen extends AbstractContainerScreen<GleanerMenu> {

    private static final GleanerSort.Category[] CATS = GleanerSort.Category.values();
    private final Button[] cats = new Button[CATS.length];

    public GleanerScreen(GleanerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 176;
        imageHeight = menu.invY() + 82;
        inventoryLabelY = menu.invY() - 11;
        titleLabelY = 6;
    }

    @Override
    protected void init() {
        super.init();
        // Onglets des Glaneurs proches
        for (int i = 0; i < menu.tabs().size(); i++) {
            final int id = 100 + i;
            BlockPos t = menu.tabs().get(i);
            boolean here = t.equals(menu.pos());
            Button b = addRenderableWidget(Button.builder(Component.literal((here ? "§e§l" : "§7") + "#" + (i + 1)), x -> click(id))
                    .bounds(leftPos + i * 22, topPos - 19, 21, 18)
                    .tooltip(Tooltip.create(Component.literal(WSLang.t("ui.gleaner.tab", i + 1) + "\n§7" + filterText(menu.tabFilter(i))
                            + "\n§8" + t.getX() + ", " + t.getY() + ", " + t.getZ()))).build());
            b.active = !here;
        }
        // Filtre de tri
        int w = (imageWidth - 14 - 5 * 2) / CATS.length;
        for (int i = 0; i < CATS.length; i++) {
            final int id = i;
            cats[i] = addRenderableWidget(Button.builder(Component.literal(""), x -> click(id))
                    .bounds(leftPos + 7 + i * (w + 2), topPos + 18, w, 14)
                    .tooltip(Tooltip.create(WSLang.c("ui.gleaner.cat_tip." + CATS[i].name().toLowerCase()))).build());
        }
        refresh();
    }

    private static String filterText(int f) {
        if (f == 0) return WSLang.t("ui.gleaner.all");
        StringBuilder sb = new StringBuilder();
        for (GleanerSort.Category c : CATS) {
            if ((f & (1 << c.ordinal())) == 0) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(WSLang.t("ui.gleaner.cat." + c.name().toLowerCase()));
        }
        return sb.toString();
    }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private void refresh() {
        int f = menu.filter();
        for (int i = 0; i < CATS.length; i++) {
            if (cats[i] == null) continue;
            boolean on = (f & (1 << i)) != 0;
            cats[i].setMessage(Component.literal((on ? "§a" : "§8") + WSLang.t("ui.gleaner.cat_short." + CATS[i].name().toLowerCase())));
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refresh();
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xF0202018);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, 0xF0302E26);
        for (int r = 0; r < menu.rows(); r++)
            for (int c = 0; c < 9; c++) slot(g, x + 7 + c * 18, y + GleanerMenu.GRID_Y - 1 + r * 18, 0xFF3A382E);
        int iy = menu.invY();
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++) slot(g, x + 7 + c * 18, y + iy - 1 + r * 18, 0xFF2A2A2A);
        for (int c = 0; c < 9; c++) slot(g, x + 7 + c * 18, y + iy + 57, 0xFF2A2A2A);
    }

    private static void slot(GuiGraphics g, int x, int y, int col) {
        g.fill(x, y, x + 18, y + 18, 0xFF101010);
        g.fill(x + 1, y + 1, x + 17, y + 17, col);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, "§6" + title.getString(), 8, titleLabelY, 0xFFFFFFFF, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFAAAAAA, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }
}
