package com.wavesurvivor.client;

import com.wavesurvivor.altar.AltarBlock;
import com.wavesurvivor.altar.AltarColors;
import com.wavesurvivor.altar.AltarParticles;
import com.wavesurvivor.network.AltarColorPacket;
import com.wavesurvivor.network.AltarCustomPackets;
import com.wavesurvivor.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Menu "Custom" de l'autel (propriétaire / op uniquement, vérifié serveur) — 2 onglets :
 *   Couleur    : 16 colorants, 1 consommé
 *   Particules : préréglages AltarParticles, gratuit
 */
@OnlyIn(Dist.CLIENT)
public class AltarColorScreen extends Screen {

    private static final int COLS = 8;
    private static final int CELL = 26;
    private static final int PANEL_W = COLS * CELL + 24;
    private static final int PANEL_H = 2 * CELL + 84;

    private final Screen parent;
    private final BlockPos pos;
    private String currentParticle;
    private int tab = 0; // 0 = couleur, 1 = particules
    private int left, top;

    public AltarColorScreen(Screen parent, BlockPos pos, String currentParticle) {
        super(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.custom")));
        this.parent = parent;
        this.pos = pos;
        this.currentParticle = currentParticle != null ? currentParticle : AltarParticles.AMES.id;
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;
        clearWidgets();
        Button bColor = addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.couleur")), b -> { tab = 0; init(); })
                .bounds(width / 2 - 82, top + 22, 80, 16).build());
        Button bPart = addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.particules")), b -> { tab = 1; init(); })
                .bounds(width / 2 + 2, top + 22, 80, 16).build());
        bColor.active = tab != 0;
        bPart.active = tab != 1;
        addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.retour")), b -> onClose())
                .bounds(width / 2 - 40, top + PANEL_H - 24, 80, 18).build());
    }

    private DyeColor currentColor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return DyeColor.RED;
        BlockState st = mc.level.getBlockState(pos);
        return st.hasProperty(AltarBlock.COLOR) ? st.getValue(AltarBlock.COLOR) : DyeColor.RED;
    }

    private boolean hasDye(DyeColor c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        if (mc.player.isCreative()) return true;
        var dye = DyeItem.byColor(c);
        for (ItemStack st : mc.player.getInventory().items) if (st.is(dye)) return true;
        for (ItemStack st : mc.player.getInventory().offhand) if (st.is(dye)) return true;
        return false;
    }

    private int cellX(int i) { return left + 12 + (i % COLS) * CELL; }
    private int cellY(int i) { return top + 46 + (i / COLS) * CELL; }

    private boolean inCell(double mx, double my, int i) {
        int x = cellX(i), y = cellY(i);
        return mx >= x && mx < x + CELL - 2 && my >= y && my < y + CELL - 2;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (tab == 0) {
                DyeColor[] all = DyeColor.values();
                for (int i = 0; i < all.length; i++) {
                    if (!inCell(mx, my, i)) continue;
                    DyeColor c = all[i];
                    if (c != currentColor() && hasDye(c)) {
                        NetworkHandler.CHANNEL.sendToServer(new AltarColorPacket(pos, c));
                        onClose();
                    }
                    return true;
                }
            } else {
                AltarParticles[] all = AltarParticles.values();
                for (int i = 0; i < all.length; i++) {
                    if (!inCell(mx, my, i)) continue;
                    if (!all[i].id.equals(currentParticle)) {
                        NetworkHandler.CHANNEL.sendToServer(new AltarCustomPackets.SetParticle(pos, all[i].id));
                        currentParticle = all[i].id; // retour visuel immédiat, le serveur confirme
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        int curRgb = 0xFF000000 | AltarColors.rgb(currentColor());
        g.fill(left, top, left + PANEL_W, top + PANEL_H, 0xF0120808);
        g.fill(left, top, left + PANEL_W, top + 2, curRgb);
        g.fill(left, top + PANEL_H - 2, left + PANEL_W, top + PANEL_H, curRgb);
        g.drawCenteredString(font, com.wavesurvivor.i18n.WSLang.t("ui.custom_552c"), width / 2, top + 8, 0xFFFFFFFF);

        List<Component> tooltip = tab == 0 ? renderColors(g, mx, my) : renderParticles(g, mx, my);

        super.render(g, mx, my, pt);
        if (tooltip != null) g.renderComponentTooltip(font, tooltip, mx, my);
    }

    private List<Component> renderColors(GuiGraphics g, int mx, int my) {
        List<Component> tooltip = null;
        DyeColor cur = currentColor();
        DyeColor[] all = DyeColor.values();
        for (int i = 0; i < all.length; i++) {
            DyeColor c = all[i];
            int x = cellX(i), y = cellY(i);
            boolean ok = hasDye(c);
            if (c == cur) g.fill(x - 2, y - 2, x + CELL, y + CELL, 0xFFFFFFFF);
            g.fill(x, y, x + CELL - 2, y + CELL - 2, 0xFF000000 | AltarColors.rgb(c));
            g.fill(x + 3, y + 3, x + CELL - 5, y + CELL - 5, 0xFF1A1A1A);
            g.renderItem(new ItemStack(DyeItem.byColor(c)), x + 4, y + 4);
            if (!ok && c != cur) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 300);
                g.fill(x, y, x + CELL - 2, y + CELL - 2, 0xB0000000);
                g.pose().popPose();
            }
            if (inCell(mx, my, i)) {
                String status = c == cur ? com.wavesurvivor.i18n.WSLang.t("ui.couleur_actuelle")
                        : ok ? com.wavesurvivor.i18n.WSLang.t("ui.clic_appliquer_1_colorant_consomme") : com.wavesurvivor.i18n.WSLang.t("ui.tu_n_as_pas_ce_colorant");
                tooltip = List.of(Component.literal("§f" + AltarColors.nameFr(c)), Component.literal(status));
            }
        }
        return tooltip;
    }

    private List<Component> renderParticles(GuiGraphics g, int mx, int my) {
        List<Component> tooltip = null;
        AltarParticles[] all = AltarParticles.values();
        for (int i = 0; i < all.length; i++) {
            AltarParticles p = all[i];
            int x = cellX(i), y = cellY(i);
            boolean cur = p.id.equals(currentParticle);
            if (cur) g.fill(x - 2, y - 2, x + CELL, y + CELL, 0xFFFFFFFF);
            g.fill(x, y, x + CELL - 2, y + CELL - 2, 0xFF3A2A45);
            g.fill(x + 3, y + 3, x + CELL - 5, y + CELL - 5, 0xFF1A1A1A);
            g.renderItem(new ItemStack(p.icon), x + 4, y + 4);
            if (inCell(mx, my, i)) {
                tooltip = List.of(Component.literal("§f" + com.wavesurvivor.i18n.WSLang.t(p.nameFr)),
                        Component.literal(cur ? com.wavesurvivor.i18n.WSLang.t("ui.particules_actuelles") : com.wavesurvivor.i18n.WSLang.t("ui.clic_appliquer_gratuit")));
            }
        }
        return tooltip;
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
