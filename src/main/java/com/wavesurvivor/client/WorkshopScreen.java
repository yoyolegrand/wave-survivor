package com.wavesurvivor.client;

import com.wavesurvivor.horde.kingdom.KingdomWorkshop;
import com.wavesurvivor.horde.kingdom.WorkshopMenu;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Écran de l'Atelier de Réparation : emplacements, vitesse, propriétaire de chaque objet. */
@OnlyIn(Dist.CLIENT)
public class WorkshopScreen extends AbstractContainerScreen<WorkshopMenu> {

    public WorkshopScreen(WorkshopMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        // Fond
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF2B2420);
        g.fill(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, 0xFF3A302A);
        g.fill(x, y, x + imageWidth, y + 2, 0xFFE07B39); // liseré de forge
        // Cases de l'atelier (Fonderie : la dernière est un creuset, couleur lave)
        boolean foundry = "foundry".equals(menu.variant);
        for (int i = 0; i < menu.workshopSlots(); i++) {
            Slot s = menu.slots.get(i);
            int sx = x + s.x - 1, sy = y + s.y - 1;
            boolean crucible = foundry && i == menu.workshopSlots() - 1;
            g.fill(sx, sy, sx + 18, sy + 18, crucible ? 0xFFFF6A00 : 0xFF8A5A2B);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, crucible ? 0xFF3A1206 : 0xFF1C1612);
            if (crucible) g.drawCenteredString(font, WSLang.t("workshop.crucible"), sx + 9, sy - 9, 0xFFFF9A40);
        }
        // Cases de l'inventaire
        for (int i = menu.workshopSlots(); i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            int sx = x + s.x - 1, sy = y + s.y - 1;
            g.fill(sx, sy, sx + 18, sy + 18, 0xFF4A403A);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF201A16);
        }
        // Ligne d'info : niveau et vitesse
        String speed = WSLang.t("workshop.speed", menu.defLevel, KingdomWorkshop.speedText(menu.defLevel, menu.variant));
        g.drawCenteredString(font, speed, x + imageWidth / 2, y + 58, 0xFFE0C090);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, 8, 6, 0xFFFFD27A, false);
        g.drawString(font, WSLang.t("workshop.hint"), 8, 18, 0xFFA89880, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFBBAA99, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }

    @Override
    protected java.util.List<Component> getTooltipFromContainerItem(ItemStack st) {
        java.util.List<Component> l = super.getTooltipFromContainerItem(st);
        // Propriétaire (objet déposé dans l'atelier)
        if (st.hasTag() && st.getTag().contains(WorkshopMenu.OWNER_NAME)) {
            l.add(Component.literal(WSLang.t("workshop.owner", st.getTag().getString(WorkshopMenu.OWNER_NAME))));
        }
        if (st.isDamageableItem() && st.getMaxDamage() > 0) {
            int pct = Math.round(100f * (st.getMaxDamage() - st.getDamageValue()) / st.getMaxDamage());
            l.add(Component.literal(WSLang.t("workshop.durability", pct)));
        }
        return l;
    }
}
