package com.wavesurvivor.client;

import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.network.AltarBonusPackets;
import com.wavesurvivor.network.NetworkHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * 2.1 — Choix du bonus de l'île-autel, présenté comme une ouverture de bénédiction : 3 cartes (Rempart, Arsenal, Prospérité)
 * qui arrivent l'une après l'autre, survol en relief, bouton « Choisir » sous chaque carte.
 */
@OnlyIn(Dist.CLIENT)
public class AltarBonusScreen extends Screen {

    private static final int CW = 150, CH = 190, GAP = 18;
    private static final String[] IDS = {"rampart", "arsenal", "prosperity"};
    private static final int[] COLORS = {0xFF55C8FF, 0xFFFF7A45, 0xFFFFD23F};
    private static final ItemStack[] ICONS = {new ItemStack(Items.SHIELD), new ItemStack(Items.CROSSBOW), new ItemStack(Items.GOLD_INGOT)};

    private final int[] vals;
    private long opened;

    private AltarBonusScreen(int rampart, int arsenal, int prosperity) {
        super(Component.literal(WSLang.t("rift.altar.screen_title")));
        this.vals = new int[]{rampart, arsenal, prosperity};
    }

    public static void open(int rampart, int arsenal, int prosperity) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new AltarBonusScreen(rampart, arsenal, prosperity));
        if (mc.player != null) mc.player.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 1.2f);
    }

    private int startX() { return (width - (3 * CW + 2 * GAP)) / 2; }

    private int cardY() { return Math.max(46, (height - CH) / 2); }

    @Override
    protected void init() {
        opened = opened == 0 ? System.currentTimeMillis() : opened;
        for (int i = 0; i < 3; i++) {
            final int k = i;
            int x = startX() + i * (CW + GAP);
            addRenderableWidget(Button.builder(Component.literal("§l" + WSLang.t("rift.altar.screen_pick")), b -> {
                NetworkHandler.CHANNEL.sendToServer(new AltarBonusPackets.Choose(k));
                onClose();
            }).bounds(x + 20, cardY() + CH - 28, CW - 40, 20).build());
        }
    }

    /** Progression d'arrivée d'une carte (0 → 1), décalée pour chaque carte. */
    private float appear(int i) {
        float t = (System.currentTimeMillis() - opened) / 1000f - i * 0.12f;
        float p = Mth.clamp(t / 0.35f, 0f, 1f);
        return 1f - (1f - p) * (1f - p) * (1f - p);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(0, 0, width, height, 0x99000000);
        int ty = cardY() - 34;
        g.drawCenteredString(font, Component.literal("§d§l✦ " + WSLang.t("rift.altar.screen_title") + " ✦"), width / 2, ty, 0xFFFFFFFF);
        g.drawCenteredString(font, Component.literal(WSLang.t("rift.altar.screen_sub")).withStyle(ChatFormatting.GRAY), width / 2, ty + 13, 0xFFAAAAAA);
        for (int i = 0; i < 3; i++) {
            float a = appear(i);
            int x = startX() + i * (CW + GAP);
            int y = cardY() + Math.round((1f - a) * 40f);
            boolean hover = mx >= x && mx < x + CW && my >= cardY() && my < cardY() + CH;
            if (hover && a >= 1f) y -= 4;
            drawCard(g, i, x, y, a);
        }
        super.render(g, mx, my, pt);
    }

    private void drawCard(GuiGraphics g, int i, int x, int y, float a) {
        int alpha = Math.round(a * 255);
        if (alpha < 8) return;
        int col = COLORS[i];
        int edge = (alpha << 24) | (col & 0xFFFFFF);
        g.fill(x, y, x + CW, y + CH, (Math.round(a * 0xEE) << 24) | 0x1A1A2E);
        g.fill(x + 2, y + 2, x + CW - 2, y + CH - 2, (Math.round(a * 0x33) << 24) | (col & 0xFFFFFF));
        g.fill(x, y, x + CW, y + 2, edge);
        g.fill(x, y + CH - 2, x + CW, y + CH, edge);
        g.fill(x, y, x + 2, y + CH, edge);
        g.fill(x + CW - 2, y, x + CW, y + CH, edge);
        if (a < 0.6f) return;
        // grande icône
        g.pose().pushPose();
        g.pose().translate(x + CW / 2f - 16, y + 16, 0);
        g.pose().scale(2f, 2f, 1f);
        g.renderItem(ICONS[i], 0, 0);
        g.pose().popPose();
        int ny = y + 58;
        for (FormattedCharSequence line : font.split(Component.literal(WSLang.t("rift.altar.bonus." + IDS[i])).withStyle(ChatFormatting.BOLD), CW - 20)) {
            g.drawCenteredString(font, line, x + CW / 2, ny, col);
            ny += font.lineHeight + 1;
        }
        g.fill(x + 20, ny + 6, x + CW - 20, ny + 7, 0x66FFFFFF);
        int dy = ny + 16;
        List<FormattedCharSequence> lines = font.split(Component.literal(WSLang.t("rift.altar.bonus." + IDS[i] + ".desc", vals[i]))
                .withStyle(ChatFormatting.GRAY), CW - 20);
        for (FormattedCharSequence line : lines) {
            g.drawCenteredString(font, line, x + CW / 2, dy, 0xFFCCCCCC);
            dy += font.lineHeight + 1;
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
