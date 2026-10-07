package com.wavesurvivor.client;

import com.wavesurvivor.horde.roulette.CustomChestData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Popup pour éditer les 5 templates de message par tier d'un chest custom.
 *
 * Variables disponibles dans les templates : {player}, {item}, {qty}
 * Un template vide laisse le tier utiliser le message par défaut.
 */
@OnlyIn(Dist.CLIENT)
public class ChestTierTemplatesPrompt extends Screen {

    private static final String[] TIERS = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY"};
    private static final String[] TIER_LABELS = {"Commun", "Peu commun", "Rare", "Épique", "Légendaire"};
    private static final ChatFormatting[] TIER_COLORS = {
            ChatFormatting.GRAY, ChatFormatting.GREEN, ChatFormatting.BLUE,
            ChatFormatting.LIGHT_PURPLE, ChatFormatting.GOLD
    };

    private final Screen parent;
    private final Map<String, String> currentTemplates;
    private final Consumer<Map<String, String>> onConfirm;
    private final EditBox[] tierBoxes = new EditBox[5];

    public ChestTierTemplatesPrompt(Screen parent, Map<String, String> currentTemplates,
                                    Consumer<Map<String, String>> onConfirm) {
        super(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.templates_par_tier")));
        this.parent = parent;
        this.currentTemplates = new LinkedHashMap<>(currentTemplates != null ? currentTemplates : CustomChestData.defaultTierMessages());
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int panelW = Math.min(width - 40, 560);
        int panelH = 260;
        int px = cx - panelW / 2;
        int py = (height - panelH) / 2;

        // 5 EditBox alignés verticalement
        int boxW = panelW - 130;
        int boxH = 18;
        int startY = py + 44;

        for (int i = 0; i < 5; i++) {
            tierBoxes[i] = new EditBox(this.font, px + 110, startY + i * 28, boxW, boxH,
                    Component.literal(com.wavesurvivor.i18n.WSLang.t(TIER_LABELS[i])));
            tierBoxes[i].setMaxLength(200);
            String current = currentTemplates.get(TIERS[i]);
            tierBoxes[i].setValue(current != null ? current : "");
            addRenderableWidget(tierBoxes[i]);
        }

        // Boutons du bas
        addRenderableWidget(Button.builder(
                Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.defauts")).withStyle(ChatFormatting.YELLOW),
                b -> resetToDefaults()
        ).bounds(px + 10, py + panelH - 30, 100, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.annuler")).withStyle(ChatFormatting.GRAY),
                b -> onClose()
        ).bounds(cx - 55, py + panelH - 30, 110, 20).build());

        addRenderableWidget(Button.builder(
                Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.valider")).withStyle(ChatFormatting.GREEN),
                b -> confirm()
        ).bounds(px + panelW - 110, py + panelH - 30, 100, 20).build());
    }

    private void resetToDefaults() {
        Map<String, String> defaults = CustomChestData.defaultTierMessages();
        for (int i = 0; i < 5; i++) {
            tierBoxes[i].setValue(defaults.getOrDefault(TIERS[i], ""));
        }
    }

    private void confirm() {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < 5; i++) {
            String v = tierBoxes[i].getValue().trim();
            if (v.isEmpty()) {
                // Vide = use default
                result.put(TIERS[i], CustomChestData.defaultTierMessages().get(TIERS[i]));
            } else {
                result.put(TIERS[i], v);
            }
        }
        onConfirm.accept(result);
        this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        int cx = width / 2;
        int panelW = Math.min(width - 40, 560);
        int panelH = 260;
        int px = cx - panelW / 2;
        int py = (height - panelH) / 2;

        // Panel background
        g.fill(px, py, px + panelW, py + panelH, 0xE8000000);
        g.renderOutline(px, py, panelW, panelH, 0xFF888888);

        // Titre
        g.drawCenteredString(this.font,
                Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.messages_de_drop_par_rarete"))
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                cx, py + 8, 0xFFFFFFFF);

        g.drawCenteredString(this.font,
                Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.variables_player_item_qty_pour_couleur_s"))
                        .withStyle(ChatFormatting.DARK_GRAY),
                cx, py + 24, 0xFF888888);

        // Labels à gauche des EditBox
        int startY = py + 44;
        for (int i = 0; i < 5; i++) {
            g.drawString(this.font,
                    Component.literal(com.wavesurvivor.i18n.WSLang.t(TIER_LABELS[i])).withStyle(TIER_COLORS[i], ChatFormatting.BOLD),
                    px + 10, startY + i * 28 + 5, 0xFFFFFFFF);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() { this.minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
