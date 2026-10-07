package com.wavesurvivor.client;

import com.wavesurvivor.horde.benediction.RogueUpgrade;
import com.wavesurvivor.horde.benediction.RogueUpgradeRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * Catalogue de toutes les bénédictions du serveur.
 * Deux vues :
 *   - Liste scrollable à gauche (une ligne par bénédiction, coloriée par rareté)
 *   - Panneau détails à droite (description + modifiers de la sélection courante)
 */
@OnlyIn(Dist.CLIENT)
public class BenedictionListScreen extends Screen {

    private static final int LIST_WIDTH = 260;
    private static final int ITEM_HEIGHT = 22;
    private static final int DETAIL_PADDING = 20;

    private final List<RogueUpgrade> all;
    private RogueUpgrade selected;
    private int scrollOffset = 0;

    public BenedictionListScreen() {
        super(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.catalogue_des_benedictions")));
        this.all = new ArrayList<>(RogueUpgradeRegistry.all());
        if (!all.isEmpty()) this.selected = all.get(0);
    }

    @Override
    protected void init() {
        super.init();
        // Bouton fermer en bas
        Button close = Button.builder(
                Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.fermer")).withStyle(ChatFormatting.GRAY),
                btn -> this.onClose())
                .bounds(width / 2 - 50, height - 30, 100, 20)
                .build();
        addRenderableWidget(close);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.fill(0, 0, width, height, 0xCC000000);

        // Titre en haut
        graphics.drawCenteredString(font,
                Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.catalogue_des_benedictions_5e81")).append(String.valueOf(all.size())).append(")"),
                width / 2, 20, 0xFFFFFFFF);

        int listX = 30;
        int listY = 50;
        int listH = height - 100;
        int detailX = listX + LIST_WIDTH + 20;
        int detailY = listY;
        int detailW = width - detailX - 30;

        // Fond liste
        graphics.fill(listX, listY, listX + LIST_WIDTH, listY + listH, 0xEE1A1A2E);
        graphics.fill(listX, listY, listX + LIST_WIDTH, listY + 2, 0xFF6644AA); // bordure haute

        // Items visibles
        int nVisible = listH / ITEM_HEIGHT;
        int startIdx = Math.max(0, Math.min(scrollOffset, all.size() - nVisible));
        int endIdx = Math.min(all.size(), startIdx + nVisible);

        for (int i = startIdx; i < endIdx; i++) {
            RogueUpgrade upg = all.get(i);
            int y = listY + (i - startIdx) * ITEM_HEIGHT;
            drawListItem(graphics, upg, listX, y, LIST_WIDTH, mouseX, mouseY);
        }

        // Scroll indicator si applicable
        if (all.size() > nVisible) {
            String scrollText = "§7[" + (startIdx + 1) + "-" + endIdx + " / " + all.size() + "]";
            graphics.drawString(font, scrollText, listX + 4, listY + listH + 4, 0xFF888888, false);
            graphics.drawString(font, com.wavesurvivor.i18n.WSLang.t("ui.utilisez_la_molette"), listX + 100, listY + listH + 4, 0xFF888888, false);
        }

        // Panneau détails
        graphics.fill(detailX, detailY, detailX + detailW, detailY + listH, 0xEE1A1A2E);
        if (selected != null) {
            drawDetail(graphics, selected, detailX + DETAIL_PADDING, detailY + DETAIL_PADDING,
                    detailW - DETAIL_PADDING * 2);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawListItem(GuiGraphics g, RogueUpgrade upg, int x, int y, int w, int mx, int my) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + ITEM_HEIGHT;
        boolean isSelected = upg == selected;
        int rarityColor = upg.color().getColor() != null
                ? (0xFF000000 | upg.color().getColor())
                : 0xFFFFFFFF;

        // Fond
        if (isSelected) {
            g.fill(x + 2, y + 1, x + w - 2, y + ITEM_HEIGHT - 1, (rarityColor & 0x00FFFFFF) | 0x55000000);
            g.fill(x + 2, y + 1, x + 4, y + ITEM_HEIGHT - 1, rarityColor); // bar gauche
        } else if (hover) {
            g.fill(x + 2, y + 1, x + w - 2, y + ITEM_HEIGHT - 1, 0x33FFFFFF);
        }

        // Icon + nom
        String label = (upg.icon() != null ? upg.icon() : "*") + " " + upg.name();
        g.drawString(font, Component.literal(label).withStyle(upg.color()),
                x + 8, y + 7, rarityColor, false);
    }

    private void drawDetail(GuiGraphics g, RogueUpgrade upg, int x, int y, int w) {
        int rarityColor = upg.color().getColor() != null
                ? (0xFF000000 | upg.color().getColor())
                : 0xFFFFFFFF;
        int cy = y;

        // Titre : icône + nom
        String title = (upg.icon() != null ? upg.icon() : "*") + " " + upg.name();
        List<FormattedCharSequence> titleLines = font.split(
                Component.literal(title).withStyle(upg.color(), ChatFormatting.BOLD), w);
        for (FormattedCharSequence line : titleLines) {
            g.drawString(font, line, x, cy, rarityColor, false);
            cy += font.lineHeight + 2;
        }

        // ID (utile pour debug/commandes)
        cy += 4;
        g.drawString(font, Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.id") + upg.id()), x, cy, 0xFF666666, false);
        cy += font.lineHeight + 6;

        // Séparateur
        g.fill(x, cy, x + w, cy + 1, 0x66FFFFFF);
        cy += 8;

        // Description
        List<FormattedCharSequence> descLines = font.split(
                Component.literal(upg.description()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), w);
        for (FormattedCharSequence line : descLines) {
            g.drawString(font, line, x, cy, 0xFFCCCCCC, false);
            cy += font.lineHeight + 1;
        }
        cy += 10;

        // Modifiers (gain de stats)
        g.drawString(font, Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.effets")).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
                x, cy, 0xFF55FFFF, false);
        cy += font.lineHeight + 4;
        for (RogueUpgrade.AttributeMod mod : upg.modifiers()) {
            String statLine = "  " + formatModifier(mod);
            g.drawString(font, Component.literal(statLine).withStyle(ChatFormatting.GREEN),
                    x, cy, 0xFF55FF55, false);
            cy += font.lineHeight + 1;
        }
        if (upg.healOnApply() > 0) {
            g.drawString(font, Component.literal("  +" + upg.healOnApply() + com.wavesurvivor.i18n.WSLang.t("ui.pv_immediats")).withStyle(ChatFormatting.RED),
                    x, cy, 0xFFFF5555, false);
            cy += font.lineHeight + 1;
        }
        cy += 10;

        // Note cumul
        g.drawString(font, Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.cumulable_le_montant_est_multiplie")).withStyle(ChatFormatting.DARK_GRAY),
                x, cy, 0xFF666666, false);
        cy += font.lineHeight;
        g.drawString(font, Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.par_le_level_choix_successifs")).withStyle(ChatFormatting.DARK_GRAY),
                x, cy, 0xFF666666, false);
    }

    private static String formatModifier(RogueUpgrade.AttributeMod mod) {
        String attrName = friendlyAttrName(mod.attribute().getDescriptionId());
        double amount = mod.amount();
        String sign = amount >= 0 ? "+" : "";
        if (mod.operation() == AttributeModifier.Operation.ADDITION) {
            String num;
            if (amount == (long) amount) num = String.valueOf((long) amount);
            else num = String.format("%.2f", amount);
            return sign + num + " " + attrName;
        } else {
            int pct = (int) Math.round(amount * 100);
            return sign + pct + "% " + attrName;
        }
    }

    private static String friendlyAttrName(String descId) {
        String key = descId != null ? descId.toLowerCase() : "";
        if (key.contains("max_health")) return com.wavesurvivor.i18n.WSLang.t("ui.pv_max");
        if (key.contains("armor_toughness")) return com.wavesurvivor.i18n.WSLang.t("ui.robustesse");
        if (key.contains("armor")) return com.wavesurvivor.i18n.WSLang.t("ui.armure");
        if (key.contains("attack_damage")) return com.wavesurvivor.i18n.WSLang.t("ui.degats_3efd");
        if (key.contains("attack_speed")) return com.wavesurvivor.i18n.WSLang.t("ui.vitesse_d_attaque");
        if (key.contains("movement_speed")) return com.wavesurvivor.i18n.WSLang.t("ui.vitesse");
        if (key.contains("knockback_resistance")) return com.wavesurvivor.i18n.WSLang.t("ui.resistance_recul");
        if (key.contains("luck")) return com.wavesurvivor.i18n.WSLang.t("ui.chance");
        return descId != null ? descId : "?";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Click sur la liste ?
        int listX = 30, listY = 50;
        int listH = height - 100;
        int nVisible = listH / ITEM_HEIGHT;
        int startIdx = Math.max(0, Math.min(scrollOffset, all.size() - nVisible));
        int endIdx = Math.min(all.size(), startIdx + nVisible);

        if (mouseX >= listX && mouseX < listX + LIST_WIDTH
                && mouseY >= listY && mouseY < listY + listH) {
            int relY = (int) (mouseY - listY);
            int idx = startIdx + relY / ITEM_HEIGHT;
            if (idx >= startIdx && idx < endIdx) {
                selected = all.get(idx);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // Molette dans la zone liste
        int listX = 30, listY = 50;
        int listH = height - 100;
        int nVisible = listH / ITEM_HEIGHT;
        if (mouseX >= listX && mouseX < listX + LIST_WIDTH
                && mouseY >= listY && mouseY < listY + listH) {
            int scrollStep = 2;
            if (delta > 0) scrollOffset = Math.max(0, scrollOffset - scrollStep);
            else scrollOffset = Math.min(Math.max(0, all.size() - nVisible), scrollOffset + scrollStep);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
