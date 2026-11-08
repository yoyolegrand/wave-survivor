package com.wavesurvivor.client;

import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.network.AltarBindPacket;
import com.wavesurvivor.network.NetworkHandler;
import com.wavesurvivor.network.OpenAltarBindScreenPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * Écran "Lier l'autel" : deux onglets (⚔ Hordes / ♛ Kingdoms), ingrédients requis (✓/✗ selon l'inventaire),
 * hordes verrouillées (progression) grisées avec leurs conditions. Bouton "Lier" actif seulement si la horde est
 * débloquée et que le joueur a tout. Le serveur revalide et consomme.
 */
@OnlyIn(Dist.CLIENT)
public class AltarBindScreen extends Screen {

    private static final int PANEL_W = 300;
    private static final int PANEL_H = 238;
    private static final int ROW_H = 42;
    private static final int VISIBLE = 4;
    /** Dernier onglet ouvert (gardé entre deux ouvertures). */
    private static int lastTab = 0;

    private final BlockPos pos;
    private final List<OpenAltarBindScreenPacket.Option> all;
    private final List<OpenAltarBindScreenPacket.Option> options = new ArrayList<>();
    /** Onglet courant : 0 = Hordes, 1 = Kingdoms, 2 = La Faille. */
    private int tab;
    private boolean kingdomTab;
    private int scroll = 0;
    private int left, top;

    public AltarBindScreen(OpenAltarBindScreenPacket pkt) {
        super(Component.literal(WSLang.t("ui.lier_l_autel")));
        this.pos = pkt.pos;
        this.all = pkt.options;
        // Onglet par défaut : le dernier utilisé, sauf s'il est vide
        tab = count(lastTab) > 0 ? lastTab : count(0) > 0 ? 0 : count(1) > 0 ? 1 : count(2) > 0 ? 2 : 0;
        refresh();
    }

    /** Liste de l'onglet : débloquées d'abord, puis verrouillées (ordre de la config conservé). */
    private void refresh() {
        options.clear();
        kingdomTab = tab == 1;
        for (var o : all) if (cat(o) == tab && o.unlocked()) options.add(o);
        for (var o : all) if (cat(o) == tab && !o.unlocked()) options.add(o);
        scroll = 0;
    }

    private static int cat(OpenAltarBindScreenPacket.Option o) { return o.rift() ? 2 : o.kingdom() ? 1 : 0; }

    private int count(int category) {
        int n = 0;
        for (var o : all) if (cat(o) == category) n++;
        return n;
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;
        clearWidgets();
        // Bouton "Custom" (haut droite) : teinte des runes
        addRenderableWidget(Button.builder(Component.literal(WSLang.t("ui.custom_0434")),
                        btn -> NetworkHandler.CHANNEL.sendToServer(
                                new com.wavesurvivor.network.AltarCustomPackets.Request(pos)))
                .bounds(width - 90, 10, 80, 18).build());
        // Onglets (celui de La Faille n'apparaît que s'il existe une horde Faille)
        boolean showRift = count(2) > 0;
        int nTabs = showRift ? 3 : 2;
        int tw = (PANEL_W - 12 - 6 * (nTabs - 1)) / nTabs;
        String[] names = {(tab == 0 ? "§e§l" : "§7") + WSLang.t("ui.bind.tab_hordes", count(0)),
                (tab == 1 ? "§6§l" : "§7") + WSLang.t("ui.bind.tab_kingdoms", count(1)),
                (tab == 2 ? "§d§l" : "§7") + WSLang.t("ui.bind.tab_rift", count(2))};
        for (int t = 0; t < nTabs; t++) {
            final int target = t;
            addRenderableWidget(Button.builder(Component.literal(names[t]),
                    b -> { tab = target; lastTab = target; refresh(); init(); }).bounds(left + 6 + t * (tw + 6), top + 32, tw, 18).build());
        }
        int y = top + 56;
        for (int i = scroll; i < Math.min(options.size(), scroll + VISIBLE); i++) {
            OpenAltarBindScreenPacket.Option o = options.get(i);
            Button b = addRenderableWidget(Button.builder(
                            Component.literal(!o.unlocked() ? "🔒" : o.canBind() ? WSLang.t("ui.lier_c231") : WSLang.t("ui.lier")),
                            btn -> {
                                NetworkHandler.CHANNEL.sendToServer(new AltarBindPacket(pos, o.hordeName()));
                                onClose();
                            })
                    .bounds(left + PANEL_W - 70, y + 11, 60, 18).build());
            b.active = o.canBind();
            y += ROW_H;
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (options.size() > VISIBLE) {
            int old = scroll;
            scroll = Math.max(0, Math.min(options.size() - VISIBLE, scroll - (int) Math.signum(delta)));
            if (old != scroll) init();
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        int accent = tab == 2 ? 0xFFB06AE0 : kingdomTab ? 0xFFE0B040 : 0xFFB91C1C;
        g.fill(left, top, left + PANEL_W, top + PANEL_H, 0xF0120808);
        g.fill(left, top, left + PANEL_W, top + 2, accent);
        g.fill(left, top + PANEL_H - 2, left + PANEL_W, top + PANEL_H, accent);

        g.drawCenteredString(font, WSLang.t("ui.lier_l_autel_2c97"), width / 2, top + 8, 0xFFFFFFFF);
        g.drawCenteredString(font, WSLang.t("ui.offre_les_ingredients_pour_sceller_une_h"), width / 2, top + 20, 0xFFFFFFFF);

        List<Component> tooltip = null;
        int y = top + 56;
        for (int i = scroll; i < Math.min(options.size(), scroll + VISIBLE); i++) {
            OpenAltarBindScreenPacket.Option o = options.get(i);
            boolean locked = !o.unlocked();
            g.fill(left + 6, y, left + PANEL_W - 6, y + ROW_H - 3,
                    locked ? 0x18FFFFFF : o.canBind() ? (tab == 2 ? 0x40B06AE0 : kingdomTab ? 0x40E0B040 : 0x40B91C1C) : 0x25FFFFFF);

            String title = (locked ? "§8§l🔒 " : o.canBind() ? (tab == 2 ? "§d§l" : kingdomTab ? "§6§l" : "§c§l") : "§7§l") + WSLang.t(o.hordeName());
            g.drawString(font, title, left + 12, y + 4, 0xFFFFFFFF);
            String info = "§8" + o.totalWaves() + (o.kingdom() ? WSLang.t("ui.bind.assaults") : WSLang.t("ui.vagues"))
                    + (o.totalBosses() > 0 ? " · " + o.totalBosses() + WSLang.t("ui.boss_3847") : "");
            g.drawString(font, info, left + 12 + font.width(title) + 6, y + 4, 0xFFFFFFFF);

            if (locked) {
                // Conditions de déblocage (✔ remplie / ✘ manquante)
                int ly = y + 16;
                String[] lines = o.lock() == null || o.lock().isEmpty() ? new String[0] : o.lock().split("\n");
                int shown = 0;
                for (String l : lines) {
                    if (shown >= 2) break;
                    boolean ok = l.startsWith("1|");
                    String txt = l.length() > 2 ? l.substring(2) : l;
                    g.drawString(font, (ok ? "§a✔ §7" : "§c✘ §7") + txt, left + 14, ly, 0xFFFFFFFF);
                    ly += 10;
                    shown++;
                }
                if (lines.length > 2) g.drawString(font, "§8+" + (lines.length - 2), left + PANEL_W - 90, y + 26, 0xFFFFFFFF);
            } else {
                // Ingrédients : icône + have/need
                int ix = left + 12;
                int iy = y + 16;
                for (OpenAltarBindScreenPacket.Ing ing : o.ingredients()) {
                    ItemStack st = stackOf(ing.item());
                    g.renderItem(st, ix, iy);
                    boolean ok = ing.have() >= ing.need();
                    String txt = (ok ? "§a✓ " : "§c✗ ") + Math.min(ing.have(), 999) + "/" + ing.need();
                    g.drawString(font, txt, ix + 18, iy + 5, 0xFFFFFFFF);
                    if (mx >= ix && mx < ix + 16 && my >= iy && my < iy + 16) {
                        tooltip = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), st));
                        tooltip.add(Component.literal((ok ? "§a" : "§c") + WSLang.t("ui.requis") + ing.need() + WSLang.t("ui.tu_as") + ing.have()));
                    }
                    ix += 18 + font.width(txt) + 10;
                }
            }
            // Survol d'une ligne verrouillée : toutes ses conditions
            if (locked && mx >= left + 6 && mx < left + PANEL_W - 76 && my >= y && my < y + ROW_H - 3 && tooltip == null) {
                tooltip = new ArrayList<>();
                tooltip.add(Component.literal(WSLang.t("ui.bind.locked_title")));
                if (o.lock() != null && !o.lock().isEmpty()) {
                    for (String l : o.lock().split("\n")) {
                        boolean ok = l.startsWith("1|");
                        tooltip.add(Component.literal((ok ? "§a✔ §7" : "§c✘ §7") + (l.length() > 2 ? l.substring(2) : l)));
                    }
                }
            }
            y += ROW_H;
        }
        if (options.isEmpty()) {
            g.drawCenteredString(font, WSLang.t(tab == 1 ? "ui.bind.empty_kingdoms" : "ui.bind.empty_hordes"), width / 2, top + 100, 0xFF888888);
        }
        if (options.size() > VISIBLE) {
            g.drawCenteredString(font, WSLang.t("ui.molette") + (scroll + 1) + "-" + Math.min(options.size(), scroll + VISIBLE)
                    + " / " + options.size(), width / 2, top + PANEL_H - 12, 0xFFFFFFFF);
        }

        super.render(g, mx, my, pt);
        if (tooltip != null) g.renderComponentTooltip(font, tooltip, mx, my);
    }

    private static ItemStack stackOf(String id) {
        try {
            Item it = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
            if (it != null && it != Items.AIR) return new ItemStack(it);
        } catch (Exception ignored) {}
        return new ItemStack(Items.BARRIER);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
