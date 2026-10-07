package com.wavesurvivor.client;

import com.wavesurvivor.horde.benediction.RogueUpgrade;
import com.wavesurvivor.horde.benediction.RogueUpgradeRegistry;
import com.wavesurvivor.network.ChooseBenedictionPacket;
import com.wavesurvivor.network.NetworkHandler;
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
 * Écran de sélection des bénédictions (3 cartes stylées).
 * Ouvert quand le serveur envoie OpenBenedictionScreenPacket (fin de vague ou /ws benediction menu).
 */
@OnlyIn(Dist.CLIENT)
public class BenedictionSelectionScreen extends Screen {

    /** Largeur d'une carte : 170 au plus, réduite pour que 4 choix (Héritage Seigneur 3) tiennent à l'écran. */
    private int CARD_WIDTH = 170;
    private static final int CARD_HEIGHT = 240;
    private static final int CARD_SPACING = 20;

    private final List<RogueUpgrade> choices;
    private final int currentWave;

    public BenedictionSelectionScreen(List<String> ids, int currentWave) {
        super(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.choisissez_un_don")));
        this.choices = new ArrayList<>();
        for (String id : ids) {
            RogueUpgrade upg = RogueUpgradeRegistry.get(id);
            if (upg != null) this.choices.add(upg);
        }
        this.currentWave = currentWave;
    }

    @Override
    protected void init() {
        super.init();
        int n = choices.size();
        if (n == 0) return;
        CARD_WIDTH = Math.max(110, Math.min(170, (width - 20 - (n - 1) * CARD_SPACING) / n));
        int totalWidth = n * CARD_WIDTH + (n - 1) * CARD_SPACING;
        int startX = (width - totalWidth) / 2;
        int cardY = (height - CARD_HEIGHT) / 2;

        for (int i = 0; i < n; i++) {
            RogueUpgrade upg = choices.get(i);
            int x = startX + i * (CARD_WIDTH + CARD_SPACING);
            Button b = Button.builder(
                    Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.choisir")).withStyle(upg.color(), ChatFormatting.BOLD),
                    btn -> {
                        NetworkHandler.CHANNEL.sendToServer(new ChooseBenedictionPacket(upg.id()));
                        this.onClose();
                    })
                    .bounds(x + 15, cardY + CARD_HEIGHT - 30, CARD_WIDTH - 30, 20)
                    .build();
            addRenderableWidget(b);
        }

        // Bouton "Fermer" en bas au centre pour skip
        Button close = Button.builder(
                Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.fermer")).withStyle(ChatFormatting.GRAY),
                btn -> this.onClose())
                .bounds(width / 2 - 50, height - 30, 100, 20)
                .build();
        addRenderableWidget(close);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Fond assombri
        this.renderBackground(graphics);
        graphics.fill(0, 0, width, height, 0x99000000);

        // Titre et sous-titre
        String title = com.wavesurvivor.i18n.WSLang.t("ui.niveau_superieur");
        int titleY = 40;
        graphics.drawCenteredString(font, Component.literal(title), width / 2, titleY, 0xFFFFFFFF);

        String subtitle = com.wavesurvivor.i18n.WSLang.t("ui.choisissez_un_don_pour_la_vague") + currentWave;
        graphics.drawCenteredString(font, Component.literal(subtitle).withStyle(ChatFormatting.GRAY),
                width / 2, titleY + 14, 0xFFAAAAAA);

        // Cards
        int n = choices.size();
        if (n > 0) {
            int totalWidth = n * CARD_WIDTH + (n - 1) * CARD_SPACING;
            int startX = (width - totalWidth) / 2;
            int cardY = (height - CARD_HEIGHT) / 2;
            for (int i = 0; i < n; i++) {
                RogueUpgrade upg = choices.get(i);
                int x = startX + i * (CARD_WIDTH + CARD_SPACING);
                drawCard(graphics, upg, x, cardY);
            }
        }

        // Widgets (boutons) par-dessus
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawCard(GuiGraphics g, RogueUpgrade upg, int x, int y) {
        int rarityColor = upg.color().getColor() != null
                ? (0xFF000000 | upg.color().getColor())
                : 0xFFFFFFFF;
        int softRarity = (rarityColor & 0x00FFFFFF) | 0x33000000; // 20% alpha pour glow

        // Fond carte (noir presque opaque)
        g.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, 0xEE1A1A2E);
        // Glow interne subtil
        g.fill(x + 2, y + 2, x + CARD_WIDTH - 2, y + CARD_HEIGHT - 2, softRarity);
        // Bordures (couleur rareté)
        int th = 2;
        g.fill(x, y, x + CARD_WIDTH, y + th, rarityColor);                                // top
        g.fill(x, y + CARD_HEIGHT - th, x + CARD_WIDTH, y + CARD_HEIGHT, rarityColor);    // bottom
        g.fill(x, y, x + th, y + CARD_HEIGHT, rarityColor);                                // left
        g.fill(x + CARD_WIDTH - th, y, x + CARD_WIDTH, y + CARD_HEIGHT, rarityColor);     // right

        // Icône emoji (grosse) - centre haut
        String icon = upg.icon() != null ? upg.icon() : "★";
        int iconY = y + 20;
        // On la scale un peu manuellement via drawString centré
        g.drawCenteredString(font, Component.literal(icon), x + CARD_WIDTH / 2, iconY, rarityColor);

        // Nom (potentiellement wrappé)
        int nameY = y + 45;
        List<FormattedCharSequence> nameLines = font.split(
                Component.literal(upg.name()).withStyle(upg.color(), ChatFormatting.BOLD),
                CARD_WIDTH - 20);
        for (FormattedCharSequence line : nameLines) {
            g.drawCenteredString(font, line, x + CARD_WIDTH / 2, nameY, rarityColor);
            nameY += font.lineHeight + 1;
        }

        // Séparateur (line horizontale)
        int sepY = nameY + 8;
        g.fill(x + 20, sepY, x + CARD_WIDTH - 20, sepY + 1, 0x66FFFFFF);

        // Description (wrappée)
        int descY = sepY + 10;
        List<FormattedCharSequence> descLines = font.split(
                Component.literal(upg.description()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC),
                CARD_WIDTH - 20);
        for (FormattedCharSequence line : descLines) {
            g.drawCenteredString(font, line, x + CARD_WIDTH / 2, descY, 0xFFCCCCCC);
            descY += font.lineHeight + 1;
        }

        // Gain réel des stats (une ligne par modifier)
        descY += 6;
        for (RogueUpgrade.AttributeMod mod : upg.modifiers()) {
            String statLine = formatModifier(mod);
            g.drawCenteredString(font, Component.literal(statLine).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                    x + CARD_WIDTH / 2, descY, 0xFF55FF55);
            descY += font.lineHeight + 1;
        }
        // Heal on apply (si applicable)
        if (upg.healOnApply() > 0) {
            String healLine = "+" + upg.healOnApply() + com.wavesurvivor.i18n.WSLang.t("ui.pv_immediats");
            g.drawCenteredString(font, Component.literal(healLine).withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                    x + CARD_WIDTH / 2, descY, 0xFFFF5555);
        }
    }

    /** Formate un modifier en chaîne lisible pour l'affichage : "+2 Armure" ou "+15% Vitesse". */
    private static String formatModifier(RogueUpgrade.AttributeMod mod) {
        String attrName = friendlyAttrName(mod.attribute().getDescriptionId());
        double amount = mod.amount();
        String sign = amount >= 0 ? "+" : "";
        if (mod.operation() == AttributeModifier.Operation.ADDITION) {
            // ADDITION : montant brut
            String num;
            if (amount == (long) amount) num = String.valueOf((long) amount);
            else num = String.format("%.2f", amount);
            return sign + num + " " + attrName;
        } else {
            // MULTIPLY_BASE ou MULTIPLY_TOTAL : pourcentage
            int pct = (int) Math.round(amount * 100);
            return sign + pct + "% " + attrName;
        }
    }

    /** Convertit un descriptionId d'attribut vanilla en nom français court. */
    private static String friendlyAttrName(String descId) {
        // descId est du style "attribute.name.generic.max_health"
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

    /** Empêche le jeu de se mettre en pause (les vagues continuent pendant le choix). */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
