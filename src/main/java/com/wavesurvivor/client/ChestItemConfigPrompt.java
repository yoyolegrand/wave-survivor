package com.wavesurvivor.client;

import com.wavesurvivor.horde.roulette.CustomChestData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;
import java.util.function.Consumer;

/**
 * Popup pour configurer un item du chest builder :
 *   - Chance %
 *   - Min qty / Max qty
 *   - Tier (5 boutons)
 *   - Message custom (vide = template du tier)
 *
 * Utilisé en 2 modes :
 *   - Ajout : `existing` = null → callback appelé avec nouveau Reward
 *   - Édition : `existing` = Reward à modifier → valeurs pré-remplies, callback met à jour
 */
@OnlyIn(Dist.CLIENT)
public class ChestItemConfigPrompt extends Screen {

    private static final String[] TIERS = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY"};
    private static final String[] TIER_LABELS = {"Commun", "Peu C.", "Rare", "Épique", "Légend."};
    private static final ChatFormatting[] TIER_COLORS = {
            ChatFormatting.GRAY, ChatFormatting.GREEN, ChatFormatting.BLUE,
            ChatFormatting.LIGHT_PURPLE, ChatFormatting.GOLD
    };

    private final Screen parent;
    private final ItemStack item;
    private final double remaining;       // % restant SANS compter l'existing
    private final CustomChestData.Reward existing; // null = mode ajout
    private final Consumer<CustomChestData.Reward> onConfirm;

    private EditBox chanceBox;
    private EditBox minQtyBox;
    private EditBox maxQtyBox;
    private EditBox messageBox;
    private String selectedTier;
    private Button[] tierButtons;
    private Component errorMsg;

    /**
     * @param remaining : % restant EN EXCLUANT l'ancien % de existing (si édition)
     */
    public ChestItemConfigPrompt(Screen parent, ItemStack item, double remaining,
                                 CustomChestData.Reward existing,
                                 Consumer<CustomChestData.Reward> onConfirm) {
        super(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.config_item")));
        this.parent = parent;
        this.item = item.copy();
        this.remaining = Math.max(0, Math.min(100, remaining));
        this.existing = existing;
        this.selectedTier = existing != null && existing.tier != null ? existing.tier : "COMMON";
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int cy = height / 2;
        int panelW = 340;
        int panelH = 260;
        int px = cx - panelW / 2;
        int py = cy - panelH / 2;

        int lineY = py + 70;
        int labelX = px + 20;
        int inputX = px + 130;

        // Chance %
        chanceBox = new EditBox(this.font, inputX, lineY, 80, 18, Component.literal("%"));
        chanceBox.setMaxLength(6);
        chanceBox.setFilter(s -> s.matches("^\\d{0,3}(\\.\\d{0,2})?$"));
        chanceBox.setValue(existing != null ? fmt(existing.chance) : "10.0");
        addRenderableWidget(chanceBox);
        setInitialFocus(chanceBox);
        lineY += 26;

        // Min / Max qty
        minQtyBox = new EditBox(this.font, inputX, lineY, 35, 18, Component.literal("min"));
        minQtyBox.setMaxLength(3);
        minQtyBox.setFilter(s -> s.matches("^\\d{0,3}$"));
        minQtyBox.setValue(existing != null ? String.valueOf(existing.minQty) : "1");
        addRenderableWidget(minQtyBox);

        maxQtyBox = new EditBox(this.font, inputX + 90, lineY, 35, 18, Component.literal("max"));
        maxQtyBox.setMaxLength(3);
        maxQtyBox.setFilter(s -> s.matches("^\\d{0,3}$"));
        maxQtyBox.setValue(existing != null ? String.valueOf(existing.maxQty) : "1");
        addRenderableWidget(maxQtyBox);
        lineY += 26;

        // Tier buttons (5 boutons horizontaux)
        int tierBtnW = 58;
        int tierBtnH = 18;
        int tierStartX = px + 20;
        tierButtons = new Button[5];
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            tierButtons[i] = Button.builder(
                    Component.literal(com.wavesurvivor.i18n.WSLang.t(TIER_LABELS[i])).withStyle(TIER_COLORS[i]),
                    b -> {
                        selectedTier = TIERS[idx];
                        refreshTierButtons();
                    }
            ).bounds(tierStartX + i * (tierBtnW + 2), lineY, tierBtnW, tierBtnH).build();
            addRenderableWidget(tierButtons[i]);
        }
        refreshTierButtons();
        lineY += 30;

        // Message custom (EditBox large)
        messageBox = new EditBox(this.font, px + 20, lineY, panelW - 40, 18,
                Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.message_custom")));
        messageBox.setMaxLength(200);
        messageBox.setValue(existing != null && existing.customMessage != null
                ? existing.customMessage : "");
        addRenderableWidget(messageBox);
        lineY += 32;

        // Boutons OK / Cancel
        addRenderableWidget(Button.builder(
                Component.literal(existing != null ? com.wavesurvivor.i18n.WSLang.t("ui.sauver") : com.wavesurvivor.i18n.WSLang.t("ui.ajouter")),
                b -> confirm()
        ).bounds(cx - 105, py + panelH - 30, 100, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.annuler")), b -> onClose()
        ).bounds(cx + 5, py + panelH - 30, 100, 20).build());
    }

    private void refreshTierButtons() {
        if (tierButtons == null) return;
        for (int i = 0; i < 5; i++) {
            boolean active = TIERS[i].equals(selectedTier);
            tierButtons[i].active = !active; // le tier sélectionné apparaît grisé (comme "déjà choisi")
        }
    }

    private void confirm() {
        String cRaw = chanceBox.getValue().trim();
        if (cRaw.isEmpty()) { errorMsg = err(com.wavesurvivor.i18n.WSLang.t("ui.chance_vide")); return; }
        double chance;
        try { chance = Double.parseDouble(cRaw); }
        catch (NumberFormatException e) { errorMsg = err(com.wavesurvivor.i18n.WSLang.t("ui.chance_invalide")); return; }
        if (chance <= 0) { errorMsg = err(com.wavesurvivor.i18n.WSLang.t("ui.chance_0")); return; }
        if (chance > remaining + 0.001) {
            errorMsg = err(com.wavesurvivor.i18n.WSLang.t("ui.max_autorise") + fmt(remaining) + com.wavesurvivor.i18n.WSLang.t("ui.total_100"));
            return;
        }

        int minQ, maxQ;
        try { minQ = Integer.parseInt(minQtyBox.getValue().trim()); }
        catch (Exception e) { errorMsg = err(com.wavesurvivor.i18n.WSLang.t("ui.qty_min_invalide")); return; }
        try { maxQ = Integer.parseInt(maxQtyBox.getValue().trim()); }
        catch (Exception e) { errorMsg = err(com.wavesurvivor.i18n.WSLang.t("ui.qty_max_invalide")); return; }
        if (minQ < 1) minQ = 1;
        if (maxQ < minQ) maxQ = minQ;
        if (maxQ > 999) maxQ = 999;

        String msg = messageBox.getValue().trim();

        CustomChestData.Reward r = existing != null ? existing : new CustomChestData.Reward();
        r.item = itemIdOf(item);
        r.chance = chance;
        r.minQty = minQ;
        r.maxQty = maxQ;
        r.tier = selectedTier;
        r.customMessage = msg.isEmpty() ? null : msg;

        onConfirm.accept(r);
        this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        int cx = width / 2;
        int cy = height / 2;
        int panelW = 340;
        int panelH = 260;
        int px = cx - panelW / 2;
        int py = cy - panelH / 2;

        // Panel background
        g.fill(px, py, px + panelW, py + panelH, 0xE8000000);
        g.renderOutline(px, py, panelW, panelH, 0xFF888888);

        // Titre
        String title = existing != null ? com.wavesurvivor.i18n.WSLang.t("ui.modifier_l_item") : com.wavesurvivor.i18n.WSLang.t("ui.ajouter_un_item");
        g.drawCenteredString(this.font,
                Component.literal(title).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                cx, py + 8, 0xFFFFFFFF);

        // Item icon + nom
        g.renderItem(item, px + 20, py + 24);
        g.drawString(this.font, item.getHoverName(), px + 44, py + 28, 0xFFFFFFFF);
        g.drawString(this.font,
                Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.restant") + fmt(remaining) + "%")
                        .withStyle(ChatFormatting.GRAY),
                px + 44, py + 42, 0xFFAAAAAA);

        // Labels des champs
        int labelX = px + 20;
        int labelY = py + 74;
        g.drawString(this.font, Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.chance_9413")).withStyle(ChatFormatting.WHITE),
                labelX, labelY, 0xFFFFFFFF);

        labelY += 26;
        g.drawString(this.font, Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.qte_min_max")).withStyle(ChatFormatting.WHITE),
                labelX, labelY, 0xFFFFFFFF);

        labelY += 26;
        g.drawString(this.font, Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.rarete")).withStyle(ChatFormatting.WHITE),
                labelX, labelY, 0xFFFFFFFF);

        labelY += 30;
        g.drawString(this.font, Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.message_custom_vide_template_du_tier"))
                        .withStyle(ChatFormatting.GRAY),
                labelX, labelY, 0xFFAAAAAA);

        // Message d'erreur
        if (errorMsg != null) {
            g.drawCenteredString(this.font, errorMsg, cx, py + panelH - 48, 0xFFFF5555);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // ENTER
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() { this.minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }

    private static String fmt(double d) {
        if (d == Math.floor(d)) return String.valueOf((int) d);
        return String.format(java.util.Locale.ROOT, "%.1f", d);
    }

    private static Component err(String s) {
        return Component.literal(s).withStyle(ChatFormatting.RED);
    }

    private static String itemIdOf(ItemStack stack) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
