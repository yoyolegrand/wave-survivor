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
    /** Relances et bannissements restants (1.8). */
    private final int rerolls, bans;
    /** Bénédictions déjà obtenues : sert au bouton « Mes bonus ». */
    private final java.util.Map<String, Integer> owned;
    /** Famille choisie pour la prochaine relance : -1 = au hasard (gardée d'un menu à l'autre). */
    private static int familyIdx = -1;
    private static final String[] FAMILIES = {"attack", "defense", "mobility", "utility", "team", "pact", "stats"};

    public BenedictionSelectionScreen(List<String> ids, int currentWave, int rerolls, int bans, java.util.Map<String, Integer> owned) {
        super(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.choisissez_un_don")));
        this.choices = new ArrayList<>();
        for (String id : ids) {
            RogueUpgrade upg = RogueUpgradeRegistry.get(id);
            if (upg != null) this.choices.add(upg);
        }
        this.currentWave = currentWave;
        this.rerolls = rerolls;
        this.bans = bans;
        this.owned = owned;
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

            // Bannir la catégorie de cette carte pour toute la partie (1.8)
            Button ban = Button.builder(
                    Component.literal("§c✖ " + com.wavesurvivor.i18n.WSLang.t("bless.ban", bans)),
                    btn -> NetworkHandler.CHANNEL.sendToServer(new com.wavesurvivor.network.BenedictionActionPacket(
                            com.wavesurvivor.network.BenedictionActionPacket.BAN, upg.category())))
                    .bounds(x + 15, cardY + CARD_HEIGHT + 4, CARD_WIDTH - 30, 14)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(com.wavesurvivor.i18n.WSLang.c("bless.ban_tip")))
                    .build();
            ban.active = bans > 0;
            addRenderableWidget(ban);
        }

        // Rangée du bas : famille de la relance, relance, fermer
        int by = height - 30;
        String famLabel = familyIdx < 0 ? com.wavesurvivor.i18n.WSLang.t("blessed.family.any") : com.wavesurvivor.i18n.WSLang.t("blessed.family." + FAMILIES[familyIdx]);
        addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("bless.family_btn", famLabel)), btn -> {
                    familyIdx = familyIdx + 1 >= FAMILIES.length ? -1 : familyIdx + 1;
                    this.rebuildWidgets();
                })
                .bounds(width / 2 - 232, by, 150, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(com.wavesurvivor.i18n.WSLang.c("bless.family_tip")))
                .build());
        Button reroll = Button.builder(Component.literal("§b↻ " + com.wavesurvivor.i18n.WSLang.t("bless.reroll", rerolls)),
                        btn -> NetworkHandler.CHANNEL.sendToServer(new com.wavesurvivor.network.BenedictionActionPacket(
                                com.wavesurvivor.network.BenedictionActionPacket.REROLL, familyIdx < 0 ? "" : FAMILIES[familyIdx])))
                .bounds(width / 2 - 78, by, 110, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(com.wavesurvivor.i18n.WSLang.c("bless.reroll_tip")))
                .build();
        reroll.active = rerolls > 0;
        addRenderableWidget(reroll);
        Button close = Button.builder(
                Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.fermer")).withStyle(ChatFormatting.GRAY),
                btn -> this.onClose())
                .bounds(width / 2 + 40, by, 70, 20)
                .build();
        addRenderableWidget(close);
        addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("bless.mine_btn")),
                        btn -> minecraft.setScreen(new BlessingSummaryScreen(owned, this)))
                .bounds(width / 2 + 116, by, 110, 20).build());
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

        String subtitle = com.wavesurvivor.i18n.WSLang.t("ui.choisissez_un_don_pour_la_vague") + currentWave
                + (BenedictionClient.mult != 1.0 ? "  §6(" + com.wavesurvivor.i18n.WSLang.t("benediction.diff_mult", String.format(java.util.Locale.ROOT, "%.2f", BenedictionClient.mult)) + ")" : "");
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
            String healLine = "+" + Math.round(upg.healOnApply() * BenedictionClient.mult) + com.wavesurvivor.i18n.WSLang.t("ui.pv_immediats");
            g.drawCenteredString(font, Component.literal(healLine).withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                    x + CARD_WIDTH / 2, descY, 0xFFFF5555);
            descY += font.lineHeight + 1;
        }
        // Effets spéciaux (Exécution, Aimant, Bastion…) : une ou plusieurs lignes par effet
        for (RogueUpgrade.Effect fx : upg.effects()) {
            for (FormattedCharSequence line : font.split(Component.literal(com.wavesurvivor.horde.benediction.BlessingEffects.describe(fx))
                    .withStyle(ChatFormatting.AQUA), CARD_WIDTH - 16)) {
                g.drawCenteredString(font, line, x + CARD_WIDTH / 2, descY, 0xFF55FFFF);
                descY += font.lineHeight + 1;
            }
        }
        if (upg.maxLevel() > 0) {
            g.drawCenteredString(font, Component.literal(com.wavesurvivor.i18n.WSLang.t("bless.max_level", upg.maxLevel())).withStyle(ChatFormatting.DARK_GRAY),
                    x + CARD_WIDTH / 2, descY + 2, 0xFF777777);
        }
    }

    /** Formate un modifier en chaîne lisible pour l'affichage : "+2 Armure" ou "+15% Vitesse". */
    static String formatModifier(RogueUpgrade.AttributeMod mod) {
        String attrName = friendlyAttrName(mod.attribute().getDescriptionId());
        double amount = mod.amount() * BenedictionClient.mult; // valeur réelle (difficulté)
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
        // Attributs d'autres mods (Apothic, Iron's…) : le nom que le jeu leur donne
        if (descId != null && !key.startsWith("attribute.name.generic.")) {
            String t = Component.translatable(descId).getString();
            if (!t.equals(descId)) return t;
        }
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

    /**
     * Solo (serveur intégré, un seul joueur, pas ouvert au réseau local) : le jeu se met en pause pendant le choix.
     * Multijoueur : pas de pause (le joueur est alors protégé côté serveur pendant son choix).
     */
    @Override
    public boolean isPauseScreen() {
        var s = net.minecraft.client.Minecraft.getInstance().getSingleplayerServer();
        return s != null && !s.isPublished() && s.getPlayerCount() <= 1;
    }
}
