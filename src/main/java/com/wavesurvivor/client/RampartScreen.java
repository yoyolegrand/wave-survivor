package com.wavesurvivor.client;

import com.wavesurvivor.horde.kingdom.RampartBlock;
import com.wavesurvivor.horde.kingdom.RampartMenu;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** FICHE D'UN REMPART : PV, état, amélioration avec son coût, bouton ▲ Renforcer (grisé si impossible). */
@OnlyIn(Dist.CLIENT)
public class RampartScreen extends AbstractContainerScreen<RampartMenu> {

    private Button upgrade;

    public RampartScreen(RampartMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 210;
        imageHeight = 112;
    }

    @Override
    protected void init() {
        super.init();
        upgrade = addRenderableWidget(Button.builder(Component.literal(WSLang.t("ui.rampart.upgrade", menu.cost())), b -> {
            if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
        }).bounds(leftPos + 10, topPos + imageHeight - 28, 120, 20).build());
        addRenderableWidget(Button.builder(Component.literal(WSLang.t("ui.rampart.close")), b -> onClose())
                .bounds(leftPos + imageWidth - 72, topPos + imageHeight - 28, 62, 20).build());
        refresh();
    }

    private void refresh() {
        if (upgrade == null) return;
        upgrade.setMessage(Component.literal(WSLang.t(menu.reinforced() ? "ui.rampart.done" : "ui.rampart.upgrade", menu.cost())));
        upgrade.active = !menu.reinforced() && (menu.money() >= menu.cost() || menu.creative());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refresh();
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xF0202024);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, 0xF02E2E34);
        // Barre de PV
        int bx = x + 10, by = y + 34, bw = imageWidth - 20;
        float f = menu.maxHp() <= 0 ? 0f : Math.min(1f, menu.hp() / (float) menu.maxHp());
        int col = f > 0.6f ? 0xFF4CC040 : f > 0.3f ? 0xFFE0B030 : 0xFFE04030;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + 9, 0xFF000000);
        g.fill(bx, by, bx + bw, by + 8, 0xFF3A3A3A);
        g.fill(bx, by, bx + (int) (bw * f), by + 8, col);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, "§f§l" + title.getString() + (menu.reinforced() ? " §7— §b" + WSLang.t("ui.rampart.reinforced") : ""), 10, 9, 0xFFFFFFFF, false);
        g.drawString(font, "§7" + WSLang.t("ui.rampart.hp", menu.hp(), menu.maxHp()), 10, 22, 0xFFFFFFFF, false);
        if (menu.reinforced()) {
            g.drawString(font, "§a" + WSLang.t("ui.rampart.max_level"), 10, 50, 0xFFFFFFFF, false);
        } else {
            g.drawString(font, "§6" + WSLang.t("ui.rampart.next", Math.round(RampartBlock.UPGRADE_HP)), 10, 50, 0xFFFFFFFF, false);
            String money = (menu.money() >= menu.cost() ? "§a" : "§c") + WSLang.t("ui.rampart.cost", menu.cost(), menu.money());
            g.drawString(font, money, 10, 62, 0xFFFFFFFF, false);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }
}
