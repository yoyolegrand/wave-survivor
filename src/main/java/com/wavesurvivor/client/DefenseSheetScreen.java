package com.wavesurvivor.client;

import com.wavesurvivor.horde.kingdom.DefenseBlock;
import com.wavesurvivor.horde.kingdom.KingdomCosts;
import com.wavesurvivor.horde.kingdom.KingdomTreasury;
import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.network.DefenseSheetPackets;
import com.wavesurvivor.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * FICHE DE DÉFENSE (Kingdom) : niveau, PV, description, amélioration (niveau 2) ou spécialisations (niveau 3) avec
 * leurs coûts, réparation, démontage (50 % remboursés), ordres du Baraquement, ouverture du stockage / de l'atelier.
 */
@OnlyIn(Dist.CLIENT)
public class DefenseSheetScreen extends Screen {

    private static final int W = 300, H = 262;
    private DefenseSheetPackets.Sheet s;
    private int left, top;
    private boolean confirmDemolish = false;

    public DefenseSheetScreen(DefenseSheetPackets.Sheet s) {
        super(Component.literal(""));
        this.s = s;
    }

    /** Ouverture, ou mise à jour si la même fiche est déjà affichée. */
    public static void show(DefenseSheetPackets.Sheet pkt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof DefenseSheetScreen cur && cur.s.pos.equals(pkt.pos)) {
            cur.s = pkt;
            cur.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        } else {
            mc.setScreen(new DefenseSheetScreen(pkt));
        }
    }

    private DefenseBlock.Kind kind() {
        return DefenseBlock.Kind.values()[Math.max(0, Math.min(DefenseBlock.Kind.values().length - 1, s.kind))];
    }

    private String kindId() { return kind().name().toLowerCase(); }

    private void act(String action, String arg) {
        NetworkHandler.CHANNEL.sendToServer(new DefenseSheetPackets.Action(s.pos, action, arg));
    }

    private static boolean affordable(List<DefenseSheetPackets.Cost> l) {
        for (DefenseSheetPackets.Cost c : l) if (c.have() < c.count()) return false;
        return true;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        clearWidgets();
        boolean creative = Minecraft.getInstance().player != null && Minecraft.getInstance().player.isCreative();
        int y = top + 104;
        // Amélioration / spécialisations
        if (s.level == 1) {
            Button b = addRenderableWidget(Button.builder(Component.literal(WSLang.t("sheet.upgrade")), bt -> act("upgrade", ""))
                    .bounds(left + W - 98, y + 2, 88, 18).build());
            b.active = s.townTier >= 2 && (creative || affordable(s.next));
        } else if (s.level == 2) {
            int cw = (W - 24) / 3;
            for (int i = 0; i < s.variants.size(); i++) {
                var v = s.variants.get(i);
                final String id = v.id();
                Button b = addRenderableWidget(Button.builder(Component.literal(WSLang.t("sheet.choose")), bt -> act("variant", id))
                        .bounds(left + 8 + i * (cw + 4), y + 76, cw, 16).build());
                b.active = s.townTier >= 3 && (creative || affordable(v.cost()));
            }
        }
        // Ordres du Baraquement
        if (kind() == DefenseBlock.Kind.BARRACKS) {
            String[] orders = {"hold", "monolith", "patrol", "follow"};
            int ow = (W - 16 - 12) / 4;
            for (int i = 0; i < 4; i++) {
                final String o = orders[i];
                Button ob = addRenderableWidget(Button.builder(Component.literal((o.equals(s.order) ? "§a§l" : i == 3 ? "§6" : "§7") + WSLang.t("kingdom.order." + o)),
                        bt -> act("order", o)).bounds(left + 8 + i * (ow + 4), top + H - 48, ow, 16).build());
                if (i == 3) ob.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(WSLang.t("kingdom.order.follow_tip"))));
            }
        }
        // Stockage (Glaneur) / emplacements (Atelier)
        if (kind() == DefenseBlock.Kind.COLLECTOR || kind() == DefenseBlock.Kind.WORKSHOP) {
            addRenderableWidget(Button.builder(Component.literal(WSLang.t(kind() == DefenseBlock.Kind.WORKSHOP ? "sheet.open_workshop" : "sheet.open_storage")),
                    bt -> act("open", "")).bounds(left + 8, top + H - 48, W - 16, 16).build());
        }
        // Réparer · Démonter · Fermer
        int by = top + H - 26;
        Button rep = addRenderableWidget(Button.builder(Component.literal(WSLang.t("sheet.repair", s.repairCost)), bt -> act("repair", ""))
                .bounds(left + 8, by, 92, 18).build());
        rep.active = s.hp < s.maxHp - 0.5f;
        addRenderableWidget(Button.builder(Component.literal(confirmDemolish ? WSLang.t("sheet.demolish_confirm") : WSLang.t("sheet.demolish", s.refund)),
                bt -> {
                    if (!confirmDemolish) { confirmDemolish = true; init(); return; }
                    act("demolish", "");
                    onClose();
                }).bounds(left + 104, by, 108, 18).build());
        addRenderableWidget(Button.builder(Component.literal(WSLang.t("ui.fermer")), bt -> onClose()).bounds(left + 216, by, 76, 18).build());
    }

    private void costLine(GuiGraphics g, List<DefenseSheetPackets.Cost> l, int x, int y, int maxW) {
        int cx = x;
        for (DefenseSheetPackets.Cost c : l) {
            var res = KingdomTreasury.Res.values()[Math.max(0, Math.min(KingdomTreasury.Res.values().length - 1, c.res()))];
            ItemStack icon = new ItemStack(new KingdomCosts.Cost(res, c.count()).icon());
            g.pose().pushPose();
            g.pose().translate(cx, y, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.renderItem(icon, 0, 0);
            g.pose().popPose();
            String n = (c.have() >= c.count() ? "§a" : "§c") + c.count();
            g.drawString(font, n, cx + 13, y + 3, 0xFFFFFFFF, false);
            cx += 15 + font.width(n) + 4;
            if (cx > x + maxW - 20) { cx = x; y += 12; }
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(left, top, left + W, top + H, 0xF0101418);
        g.fill(left, top, left + W, top + 2, 0xFF4AA3FF);
        // Titre : nom, niveau, spécialisation
        String name = WSLang.t("ui.shop.map.def." + kindId());
        String stars = "§6" + "★".repeat(Math.max(1, s.level)) + "§8" + "★".repeat(Math.max(0, 3 - s.level));
        g.drawString(font, "§f§l" + name + " " + stars, left + 10, top + 8, 0xFFFFFFFF);
        if (!s.variant.isEmpty()) g.drawString(font, "§b" + WSLang.t("kingdom.variant." + s.variant), left + 10, top + 20, 0xFFFFFFFF);
        // PV
        float p = s.maxHp > 0 ? Math.min(1f, s.hp / s.maxHp) : 0f;
        int bx = left + 10, by = top + 34, bw = W - 20;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + 9, 0xFF000000);
        g.fill(bx, by, bx + (int) (bw * p), by + 8, p > 0.5f ? 0xFF22C55E : p > 0.25f ? 0xFFEAB308 : 0xFFEF4444);
        g.drawCenteredString(font, Math.round(s.hp) + " / " + Math.round(s.maxHp) + " PV", left + W / 2, by, 0xFFFFFFFF);
        // Description (et effet de la spécialisation)
        int y = top + 48;
        String desc = WSLang.t("kingdom.defense.desc." + kindId()).replaceAll("§.", "");
        for (FormattedCharSequence line : font.split(Component.literal(desc), W - 20)) {
            if (y > top + 96) break;
            g.drawString(font, line, left + 10, y, 0xFFAAAAAA, false);
            y += 10;
        }
        if (!s.variant.isEmpty()) {
            for (FormattedCharSequence line : font.split(Component.literal(WSLang.t("kingdom.variant.desc." + s.variant).replaceAll("§.", "")), W - 20)) {
                if (y > top + 100) break;
                g.drawString(font, line, left + 10, y, 0xFF7FC8FF, false);
                y += 10;
            }
        }
        // Amélioration
        int uy = top + 104;
        g.fill(left + 6, uy - 2, left + W - 6, uy - 1, 0x40FFFFFF);
        if (s.level == 1) {
            g.drawString(font, "§e" + WSLang.t("sheet.next_level"), left + 10, uy + 2, 0xFFFFFFFF);
            costLine(g, s.next, left + 10, uy + 14, W - 110);
            if (s.townTier < 2) g.drawString(font, "§c" + WSLang.t("sheet.need_town", 2), left + 10, uy + 30, 0xFFFFFFFF);
        } else if (s.level == 2) {
            g.drawString(font, "§e" + WSLang.t("sheet.pick_variant") + (s.townTier < 3 ? " §c" + WSLang.t("sheet.need_town", 3) : ""), left + 10, uy + 2, 0xFFFFFFFF);
            int cw = (W - 24) / 3;
            for (int i = 0; i < s.variants.size(); i++) {
                var v = s.variants.get(i);
                int cx = left + 8 + i * (cw + 4), cy = uy + 14;
                g.fill(cx, cy, cx + cw, cy + 60, 0x30FFFFFF);
                g.drawString(font, "§f§l" + font.plainSubstrByWidth(WSLang.t("kingdom.variant." + v.id()), cw - 4), cx + 3, cy + 3, 0xFFFFFFFF);
                int ly = cy + 14;
                for (FormattedCharSequence line : font.split(Component.literal(WSLang.t("kingdom.variant.desc." + v.id()).replaceAll("§.", "")), cw - 6)) {
                    if (ly > cy + 36) break;
                    g.pose().pushPose();
                    g.pose().translate(cx + 3, ly, 0);
                    g.pose().scale(0.75f, 0.75f, 1f);
                    g.drawString(font, line, 0, 0, 0xFFBBBBBB, false);
                    g.pose().popPose();
                    ly += 8;
                }
                costLine(g, v.cost(), cx + 3, cy + 44, cw - 4);
                // Survol : description complète
                if (mx >= cx && mx < cx + cw && my >= cy && my < cy + 60) {
                    g.renderTooltip(font, font.split(Component.literal(WSLang.t("kingdom.variant.desc." + v.id())), 200), mx, my);
                }
            }
        } else {
            g.drawString(font, "§6" + WSLang.t("sheet.max_level"), left + 10, uy + 2, 0xFFFFFFFF);
        }
        // Baraquement : soldats
        if (kind() == DefenseBlock.Kind.BARRACKS) {
            g.drawString(font, "§7" + WSLang.t("sheet.soldiers", s.soldiers, s.soldiersMax), left + 10, top + H - 60, 0xFFFFFFFF);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
