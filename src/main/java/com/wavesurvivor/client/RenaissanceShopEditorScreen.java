package com.wavesurvivor.client;

import com.google.gson.Gson;
import com.wavesurvivor.horde.renaissance.RenaissanceConfigData;
import com.wavesurvivor.network.NetworkHandler;
import com.wavesurvivor.network.RenaissanceShopEditPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * Éditeur admin d'une relique de la boutique Renaissance.
 * - Clique un item de ton inventaire → il devient la relique (NBT complet : nom, lore, enchants...)
 * - Coût PR, max par joueur (0 = illimité), quantité donnée, description
 * - existing = null → nouvelle relique ; sinon modification (même id)
 */
@OnlyIn(Dist.CLIENT)
public class RenaissanceShopEditorScreen extends Screen {

    private static final int PANEL_W = 260;
    private static final int PANEL_H = 264;
    private static final int CELL = 18;
    private static final Gson GSON = new Gson();

    private final Screen parent;
    private final RenaissanceConfigData.ShopItem existing;

    private ItemStack selected = ItemStack.EMPTY;
    private boolean picked = false;

    private EditBox costBox, maxBox, qtyBox, descBox;
    private String error;
    private int left, top;

    public RenaissanceShopEditorScreen(Screen parent, RenaissanceConfigData.ShopItem existing) {
        super(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.editeur_de_relique")));
        this.parent = parent;
        this.existing = existing;
        if (existing != null) this.selected = RenaissanceConfigData.buildStack(existing);
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;
        int fx = left + 150;
        int y = top + 128;

        costBox = box(fx, y, 60, existing != null ? String.valueOf(existing.cost) : "10", 5);
        y += 22;
        maxBox = box(fx, y, 60, existing != null ? String.valueOf(existing.maxPerPlayer) : "0", 4);
        y += 22;
        qtyBox = box(fx, y, 60, existing != null ? String.valueOf(existing.count)
                : String.valueOf(Math.max(1, selected.getCount())), 2);
        y += 22;

        descBox = new EditBox(font, left + 12, y + 12, PANEL_W - 24, 16, Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.description")));
        descBox.setMaxLength(120);
        descBox.setValue(existing != null && existing.description != null ? existing.description : "");
        addRenderableWidget(descBox);

        int by = top + PANEL_H - 24;
        addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.sauver_5a74")), b -> save())
                .bounds(width / 2 - 104, by, 100, 18).build());
        addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.annuler")), b -> onClose())
                .bounds(width / 2 + 4, by, 100, 18).build());
    }

    private EditBox box(int x, int y, int w, String value, int maxLen) {
        EditBox b = new EditBox(font, x, y, w, 16, Component.literal(""));
        b.setMaxLength(maxLen);
        b.setFilter(s -> s.matches("^\\d*$"));
        b.setValue(value);
        addRenderableWidget(b);
        return b;
    }

    // ─── Sélection dans l'inventaire ───

    private List<ItemStack> inv() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getInventory().items : List.of();
    }

    private int slotAt(double mx, double my) {
        int gx = left + (PANEL_W - 9 * CELL) / 2;
        int gy = top + 24;
        for (int row = 0; row < 4; row++) {
            int ry = gy + row * CELL + (row == 3 ? 4 : 0);
            for (int col = 0; col < 9; col++) {
                int rx = gx + col * CELL;
                if (mx >= rx && mx < rx + CELL && my >= ry && my < ry + CELL) return row == 3 ? col : 9 + row * 9 + col;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int slot = slotAt(mx, my);
        if (slot >= 0) {
            List<ItemStack> items = inv();
            ItemStack st = slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
            if (!st.isEmpty()) {
                selected = st.copy();
                picked = true;
                qtyBox.setValue(String.valueOf(st.getCount()));
                error = null;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    // ─── Sauvegarde ───

    private void save() {
        if (selected.isEmpty()) { error = com.wavesurvivor.i18n.WSLang.t("ui.clique_un_item_de_ton_inventaire"); return; }
        int cost = parse(costBox, -1);
        int max = parse(maxBox, 0);
        int qty = parse(qtyBox, 1);
        if (cost < 0) { error = com.wavesurvivor.i18n.WSLang.t("ui.cout_invalide"); return; }
        if (qty < 1 || qty > 64) { error = com.wavesurvivor.i18n.WSLang.t("ui.quantite_entre_1_et_64"); return; }

        RenaissanceConfigData.ShopItem s = new RenaissanceConfigData.ShopItem();
        s.id = existing != null ? existing.id : "";
        if (picked || existing == null) {
            s.item = BuiltInRegistries.ITEM.getKey(selected.getItem()).toString();
            CompoundTag tag = selected.getTag() != null ? selected.getTag().copy() : null;
            if (tag != null) tag.remove(RenaissanceConfigData.RELIC_TAG);
            s.nbt = tag != null && !tag.isEmpty() ? tag.toString() : null;
        } else {
            s.item = existing.item;
            s.nbt = existing.nbt;
            s.displayName = existing.displayName;
            s.lore = existing.lore;
        }
        s.cost = cost;
        s.maxPerPlayer = Math.max(0, max);
        s.count = qty;
        String d = descBox.getValue().trim();
        s.description = d.isEmpty() ? null : d;

        NetworkHandler.CHANNEL.sendToServer(new RenaissanceShopEditPacket(RenaissanceShopEditPacket.SAVE, GSON.toJson(s)));
        onClose();
    }

    private static int parse(EditBox b, int def) {
        try { return Integer.parseInt(b.getValue().trim()); } catch (Exception e) { return def; }
    }

    // ─── Rendu ───

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        g.fill(left, top, left + PANEL_W, top + PANEL_H, 0xF0140A1E);
        g.fill(left, top, left + PANEL_W, top + 2, 0xFFEF4444);
        g.drawCenteredString(font, existing != null ? com.wavesurvivor.i18n.WSLang.t("ui.modifier_la_relique") : com.wavesurvivor.i18n.WSLang.t("ui.nouvelle_relique"),
                width / 2, top + 8, 0xFFFFFFFF);

        // Grille inventaire
        List<ItemStack> items = inv();
        int gx = left + (PANEL_W - 9 * CELL) / 2;
        int gy = top + 24;
        int hovered = slotAt(mx, my);
        List<Component> tooltip = null;
        for (int row = 0; row < 4; row++) {
            int ry = gy + row * CELL + (row == 3 ? 4 : 0);
            for (int col = 0; col < 9; col++) {
                int slot = row == 3 ? col : 9 + row * 9 + col;
                int rx = gx + col * CELL;
                g.fill(rx, ry, rx + CELL - 1, ry + CELL - 1, slot == hovered ? 0xFF4C1D6E : 0xFF26202E);
                ItemStack st = slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
                if (st.isEmpty()) continue;
                g.renderItem(st, rx + 1, ry + 1);
                g.renderItemDecorations(font, st, rx + 1, ry + 1);
                if (slot == hovered) tooltip = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), st));
            }
        }

        // Aperçu
        int py = top + 104;
        g.fill(left + 10, py - 2, left + PANEL_W - 10, py + 20, 0x40FFFFFF);
        if (selected.isEmpty()) {
            g.drawString(font, com.wavesurvivor.i18n.WSLang.t("ui.clique_un_item_de_ton_inventaire_a1d2"), left + 16, py + 5, 0xFFFFFFFF);
        } else {
            g.renderItem(selected, left + 14, py);
            g.drawString(font, selected.getHoverName().getString(), left + 36, py + 5, 0xFFFFFFFF);
            if (mx >= left + 12 && mx < left + 32 && my >= py && my < py + 18) {
                tooltip = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), selected));
            }
        }

        // Labels
        int y = top + 132;
        g.drawString(font, com.wavesurvivor.i18n.WSLang.t("ui.cout_pr"), left + 12, y, 0xFFFFFFFF);
        y += 22;
        g.drawString(font, com.wavesurvivor.i18n.WSLang.t("ui.max_joueur_0_illimite"), left + 12, y, 0xFFFFFFFF);
        y += 22;
        g.drawString(font, com.wavesurvivor.i18n.WSLang.t("ui.quantite_donnee"), left + 12, y, 0xFFFFFFFF);
        y += 22;
        g.drawString(font, com.wavesurvivor.i18n.WSLang.t("ui.description_optionnel_affichee_au_survol"), left + 12, y + 1, 0xFFFFFFFF);

        if (error != null) g.drawCenteredString(font, "§c" + error, width / 2, top + PANEL_H - 34, 0xFFFFFFFF);

        super.render(g, mx, my, pt);
        if (tooltip != null) g.renderComponentTooltip(font, tooltip, mx, my);
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
