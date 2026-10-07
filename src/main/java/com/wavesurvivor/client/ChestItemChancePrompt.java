package com.wavesurvivor.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.function.DoubleConsumer;

/**
 * Popup input pour saisir le % de chance d'un item ajouté au chest builder.
 * Ouvert par-dessus ChestBuilderScreen quand l'user clique sur un item de son inventaire.
 *
 * Appelle onConfirm(chance) si l'user valide, ou revient au parent sans rien faire si Cancel.
 */
@OnlyIn(Dist.CLIENT)
public class ChestItemChancePrompt extends Screen {

    private final Screen parent;
    private final ItemStack item;
    private final double remaining;      // % restant pour ne pas dépasser 100
    private final DoubleConsumer onConfirm;

    private EditBox chanceBox;
    private Component errorMsg;

    public ChestItemChancePrompt(Screen parent, ItemStack item, double remaining, DoubleConsumer onConfirm) {
        super(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.chance_de_drop")));
        this.parent = parent;
        this.item = item.copy();
        this.remaining = Math.max(0, Math.min(100, remaining));
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int cy = height / 2;

        chanceBox = new EditBox(this.font, cx - 60, cy, 120, 20, Component.literal("%"));
        chanceBox.setMaxLength(6);
        chanceBox.setValue("10.0");
        chanceBox.setFilter(s -> s.matches("^\\d{0,3}(\\.\\d{0,2})?$"));
        addRenderableWidget(chanceBox);
        setInitialFocus(chanceBox);

        addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.ok")), b -> confirm())
                .bounds(cx - 105, cy + 30, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.annuler")), b -> onClose())
                .bounds(cx + 5, cy + 30, 100, 20).build());
    }

    private void confirm() {
        String raw = chanceBox.getValue().trim();
        if (raw.isEmpty()) {
            errorMsg = Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.valeur_vide")).withStyle(ChatFormatting.RED);
            return;
        }
        double val;
        try {
            val = Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            errorMsg = Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.nombre_invalide")).withStyle(ChatFormatting.RED);
            return;
        }
        if (val <= 0) {
            errorMsg = Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.doit_etre_0")).withStyle(ChatFormatting.RED);
            return;
        }
        if (val > remaining) {
            errorMsg = Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.max_autorise") + fmt(remaining) + com.wavesurvivor.i18n.WSLang.t("ui.total_100"))
                    .withStyle(ChatFormatting.RED);
            return;
        }
        onConfirm.accept(val);
        this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        int cx = width / 2;
        int cy = height / 2;

        // Panneau central
        int panelW = 280;
        int panelH = 160;
        int px = cx - panelW / 2;
        int py = cy - panelH / 2;
        g.fill(px, py, px + panelW, py + panelH, 0xE0000000);
        g.renderOutline(px, py, panelW, panelH, 0xFF888888);

        // Titre
        g.drawCenteredString(this.font,
                Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.chance_de_drop_de_cet_item"))
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                cx, py + 8, 0xFFFFFFFF);

        // Item + nom
        g.renderItem(item, cx - 60, py + 30);
        g.drawString(this.font,
                item.getHoverName(), cx - 40, py + 34, 0xFFFFFFFF);

        // Info restant
        String remStr = com.wavesurvivor.i18n.WSLang.t("ui.restant") + fmt(remaining) + com.wavesurvivor.i18n.WSLang.t("ui.total_100");
        g.drawCenteredString(this.font,
                Component.literal(remStr).withStyle(ChatFormatting.GRAY),
                cx, py + 55, 0xFFAAAAAA);

        // Label du champ
        g.drawString(this.font,
                Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.chance_560f")).withStyle(ChatFormatting.WHITE),
                cx - 60, cy - 12, 0xFFFFFFFF);

        // Message d'erreur (dessous du input)
        if (errorMsg != null) {
            g.drawCenteredString(this.font, errorMsg, cx, cy + 22, 0xFFFF5555);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Enter pour valider
        if (keyCode == 257 || keyCode == 335) { // ENTER + NUMPAD_ENTER
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String fmt(double d) {
        if (d == Math.floor(d)) return String.valueOf((int) d);
        return String.format(java.util.Locale.ROOT, "%.1f", d);
    }
}
