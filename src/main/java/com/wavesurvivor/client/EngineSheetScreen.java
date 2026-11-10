package com.wavesurvivor.client;

import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.network.EngineSheetPackets;
import com.wavesurvivor.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * FICHE D'ENGIN DE SIÈGE (Faille) : type, niveau, description, amélioration au niveau 2 (Renforcée / Consacrée) puis choix d'une des
 * trois variantes de niveau 3, avec leurs prix. Même présentation que la fiche des tours du Kingdom.
 */
@OnlyIn(Dist.CLIENT)
public class EngineSheetScreen extends Screen {

    private static final int W = 300, H = 236;
    private EngineSheetPackets.Sheet s;
    private int left, top;

    public EngineSheetScreen(EngineSheetPackets.Sheet s) {
        super(Component.literal(""));
        this.s = s;
    }

    /** Ouverture, ou mise à jour si la même fiche est déjà affichée. */
    public static void show(EngineSheetPackets.Sheet pkt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof EngineSheetScreen cur && cur.s.entity == pkt.entity) {
            cur.s = pkt;
            cur.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        } else {
            mc.setScreen(new EngineSheetScreen(pkt));
        }
    }

    private String id() { return s.kind == 1 ? "ballista" : "catapult"; }

    private void act(String action) {
        NetworkHandler.CHANNEL.sendToServer(new EngineSheetPackets.Action(s.entity, action));
    }

    private boolean creative() {
        return Minecraft.getInstance().player != null && Minecraft.getInstance().player.isCreative();
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        clearWidgets();
        int y = top + 100;
        if (s.level == 1) {
            Button b = addRenderableWidget(Button.builder(Component.literal(WSLang.t("rift.engine.sheet.upgrade")), bt -> act("upgrade"))
                    .bounds(left + W - 98, y + 2, 88, 18).build());
            b.active = creative() || s.have >= s.upCost;
        } else if (s.level == 2) {
            int cw = (W - 24) / 3;
            for (int i = 0; i < 3; i++) {
                final String a = "variant" + i;
                Button b = addRenderableWidget(Button.builder(Component.literal(WSLang.t("rift.engine.sheet.choose")), bt -> act(a))
                        .bounds(left + 8 + i * (cw + 4), y + 78, cw, 16).build());
                b.active = creative() || s.have >= s.varCost;
            }
        }
        if (s.repairCost > 0) {   // engin abîmé : réparation complète, prix selon les PV manquants
            Button r = addRenderableWidget(Button.builder(Component.literal(WSLang.t("rift.engine.sheet.repair", s.repairCost)), bt -> act("repair"))
                    .bounds(left + 10, top + H - 26, 150, 18).build());
            r.active = creative() || s.have >= s.repairCost;
        }
        addRenderableWidget(Button.builder(Component.literal(WSLang.t("ui.fermer")), bt -> onClose()).bounds(left + W - 86, top + H - 26, 76, 18).build());
    }

    private void wrap(GuiGraphics g, String text, int x, int y, int w, int maxY, int color) {
        for (FormattedCharSequence line : font.split(Component.literal(text.replaceAll("§.", "")), w)) {
            if (y > maxY) break;
            g.drawString(font, line, x, y, color, false);
            y += 10;
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(left, top, left + W, top + H, 0xF0101418);
        g.fill(left, top, left + W, top + 2, 0xFF4AA3FF);
        String name = WSLang.t("rift.engine.sheet.name." + id() + (s.level >= 2 ? ".l2" : ""));
        String stars = "§6" + "★".repeat(Math.max(1, s.level)) + "§8" + "★".repeat(Math.max(0, 3 - s.level));
        g.drawString(font, "§f§l" + name + " " + stars, left + 10, top + 8, 0xFFFFFFFF);
        if (s.maxHp > 0) {
            float f = s.hp / (float) s.maxHp;
            String hp = (f > 0.6f ? "§a" : f > 0.3f ? "§e" : "§c") + "♥ " + s.hp + " §7/ " + s.maxHp;
            g.drawString(font, hp, left + W - 10 - font.width(hp), top + 8, 0xFFFFFFFF);
        }
        if (s.level >= 3 && s.variant >= 1) {
            g.drawString(font, "§b" + WSLang.t("rift.engine.sheet.variant." + id() + "." + (s.variant - 1)), left + 10, top + 20, 0xFFFFFFFF);
        }
        wrap(g, WSLang.t("rift.engine.sheet.desc." + id()), left + 10, top + 34, W - 20, top + 70, 0xFFAAAAAA);
        if (s.level >= 3 && s.variant >= 1) {
            wrap(g, WSLang.t("rift.engine.sheet.variant.desc." + id() + "." + (s.variant - 1)), left + 10, top + 70, W - 20, top + 94, 0xFF7FC8FF);
        }
        if (s.kind == 1) {
            g.drawString(font, "§7" + WSLang.t("rift.engine.sheet.ammo", s.ammo), left + 10, top + 58, 0xFFFFFFFF);
        }
        int uy = top + 96;
        g.fill(left + 6, uy - 2, left + W - 6, uy - 1, 0x40FFFFFF);
        String coins = (s.have >= s.upCost || creative() ? "§a" : "§c");
        if (s.level == 1) {
            g.drawString(font, "§e" + WSLang.t("rift.engine.sheet.next"), left + 10, uy + 4, 0xFFFFFFFF);
            g.drawString(font, coins + WSLang.t("rift.engine.sheet.cost", s.upCost, s.have), left + 10, uy + 18, 0xFFFFFFFF);
            wrap(g, WSLang.t("rift.engine.sheet.l2." + id()), left + 10, uy + 32, W - 120, uy + 60, 0xFF7FC8FF);
        } else if (s.level == 2) {
            g.drawString(font, "§e" + WSLang.t("rift.engine.sheet.pick") + " §7(" + WSLang.t("rift.engine.sheet.cost", s.varCost, s.have) + ")", left + 10, uy + 4, 0xFFFFFFFF);
            int cw = (W - 24) / 3;
            for (int i = 0; i < 3; i++) {
                int cx = left + 8 + i * (cw + 4), cy = uy + 18;
                g.fill(cx, cy, cx + cw, cy + 76, 0x30FFFFFF);
                g.drawString(font, "§f§l" + font.plainSubstrByWidth(WSLang.t("rift.engine.sheet.variant." + id() + "." + i), cw - 4), cx + 3, cy + 3, 0xFFFFFFFF);
                int ly = cy + 15;
                for (FormattedCharSequence line : font.split(Component.literal(WSLang.t("rift.engine.sheet.variant.desc." + id() + "." + i).replaceAll("§.", "")), cw - 6)) {
                    if (ly > cy + 56) break;
                    g.pose().pushPose();
                    g.pose().translate(cx + 3, ly, 0);
                    g.pose().scale(0.75f, 0.75f, 1f);
                    g.drawString(font, line, 0, 0, 0xFFBBBBBB, false);
                    g.pose().popPose();
                    ly += 8;
                }
                if (mx >= cx && mx < cx + cw && my >= cy && my < cy + 76) {
                    g.renderTooltip(font, font.split(Component.literal(WSLang.t("rift.engine.sheet.variant.desc." + id() + "." + i)), 200), mx, my);
                }
            }
        } else {
            g.drawString(font, "§6" + WSLang.t("rift.engine.sheet.max"), left + 10, uy + 4, 0xFFFFFFFF);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
