package com.wavesurvivor.client;

import com.wavesurvivor.horde.renaissance.RenaissanceConfigData;
import com.wavesurvivor.horde.renaissance.RenaissanceRarityStore;
import com.wavesurvivor.network.NetworkHandler;
import com.wavesurvivor.network.RenaissanceRarityPacket;
import com.wavesurvivor.network.RenaissanceStatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * CONFIGURATION (op) de l'autel de Renaissance : clic sur un item de l'inventaire → règle sa rareté
 * de sacrifice pour TOUT le serveur (Défaut → Rare → Épique → Légendaire → Exclu → Défaut).
 * Enregistré côté serveur dans renaissance_rarities.json, prioritaire sur les tags de la config.
 */
@OnlyIn(Dist.CLIENT)
public class RenaissanceRarityScreen extends Screen {

    private static final int PANEL_W = 260;
    private static final int PANEL_H = 214;
    private static final int CELL = 18;

    private final RenaissanceScreen parent;
    private RenaissanceConfigData cfg;
    private int left, top;

    public RenaissanceRarityScreen(RenaissanceScreen parent, RenaissanceConfigData cfg) {
        super(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.configuration_renaissance")));
        this.parent = parent;
        this.cfg = cfg;
    }

    /** Réponse serveur après un réglage : met à jour la config (et l'écran parent). */
    public void applyState(RenaissanceStatePacket pkt) {
        parent.applyState(pkt);
        this.cfg = parent.config();
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;
        clearWidgets();
        addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.retour")), b -> onClose())
                .bounds(width / 2 - 40, top + PANEL_H - 24, 80, 18).build());
    }

    private List<ItemStack> inv() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getInventory().items : List.of();
    }

    private static String idOf(ItemStack st) {
        ResourceLocation k = BuiltInRegistries.ITEM.getKey(st.getItem());
        return k != null ? k.toString() : "";
    }

    private int gridX() { return left + (PANEL_W - 9 * CELL) / 2; }
    private int gridY() { return top + 40; }

    private int slotAt(double mx, double my) {
        for (int row = 0; row < 4; row++) {
            int ry = gridY() + row * CELL + (row == 3 ? 4 : 0);
            for (int col = 0; col < 9; col++) {
                int rx = gridX() + col * CELL;
                if (mx >= rx && mx < rx + CELL - 1 && my >= ry && my < ry + CELL - 1) return row == 3 ? col : 9 + row * 9 + col;
            }
        }
        return -1;
    }

    /** Valeurs possibles dans l'ordre du cycle : "" (défaut), raretés de la config, exclu. */
    private List<String> cycleValues() {
        List<String> l = new ArrayList<>();
        l.add("");
        for (RenaissanceConfigData.Rarity r : cfg.raritiesConfig) if (r.name != null) l.add(r.name);
        l.add(RenaissanceRarityStore.EXCLUDED);
        return l;
    }

    private String currentOverride(String itemId) {
        return cfg.rarityOverrides != null ? cfg.rarityOverrides.getOrDefault(itemId, "") : "";
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int slot = slotAt(mx, my);
        List<ItemStack> items = inv();
        if (slot >= 0 && slot < items.size() && !items.get(slot).isEmpty()) {
            String id = idOf(items.get(slot));
            String next;
            if (hasShiftDown()) {
                next = "";
            } else {
                List<String> vals = cycleValues();
                int i = vals.indexOf(currentOverride(id));
                if (i < 0) i = 0;
                int dir = button == 1 ? -1 : 1;
                next = vals.get(((i + dir) % vals.size() + vals.size()) % vals.size());
            }
            // Retour visuel immédiat, le serveur confirme ensuite
            if (cfg.rarityOverrides == null) cfg.rarityOverrides = new HashMap<>();
            if (next.isEmpty()) cfg.rarityOverrides.remove(id); else cfg.rarityOverrides.put(id, next);
            NetworkHandler.CHANNEL.sendToServer(new RenaissanceRarityPacket(id, next));
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance
                    .forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.2f));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private String label(String value) {
        if (value == null || value.isEmpty()) return com.wavesurvivor.i18n.WSLang.t("ui.defaut");
        if (RenaissanceRarityStore.EXCLUDED.equals(value)) return com.wavesurvivor.i18n.WSLang.t("ui.exclu_e5f9");
        RenaissanceConfigData.Rarity r = cfg.findRarity(value);
        return r != null ? r.chatColor() + r.name : "§f" + value;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(left, top, left + PANEL_W, top + PANEL_H, 0xF0140A1E);
        g.fill(left, top, left + PANEL_W, top + 2, 0xFFA855F7);
        g.fill(left, top + PANEL_H - 2, left + PANEL_W, top + PANEL_H, 0xFFA855F7);
        g.drawCenteredString(font, com.wavesurvivor.i18n.WSLang.t("ui.configuration_des_raretes"), width / 2, top + 8, 0xFFFFFFFF);
        g.drawCenteredString(font, com.wavesurvivor.i18n.WSLang.t("ui.reglage_valable_sur_tout_le_serveur"), width / 2, top + 22, 0xFFFFFFFF);

        List<ItemStack> items = inv();
        int hovered = slotAt(mx, my);
        List<Component> tooltip = null;
        for (int row = 0; row < 4; row++) {
            int ry = gridY() + row * CELL + (row == 3 ? 4 : 0);
            for (int col = 0; col < 9; col++) {
                int slot = row == 3 ? col : 9 + row * 9 + col;
                int rx = gridX() + col * CELL;
                ItemStack st = slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
                g.fill(rx, ry, rx + CELL - 1, ry + CELL - 1, 0xFF26202E);
                if (st.isEmpty()) continue;

                String id = idOf(st);
                String ov = currentOverride(id);
                RenaissanceConfigData.Rarity eff = cfg.rarityOf(st);
                if (eff != null) {
                    int c = eff.argb();
                    g.fill(rx, ry, rx + CELL - 1, ry + 1, c);
                    g.fill(rx, ry + CELL - 2, rx + CELL - 1, ry + CELL - 1, c);
                    g.fill(rx, ry, rx + 1, ry + CELL - 1, c);
                    g.fill(rx + CELL - 2, ry, rx + CELL - 1, ry + CELL - 1, c);
                }
                g.renderItem(st, rx + 1, ry + 1);
                g.renderItemDecorations(font, st, rx + 1, ry + 1);
                g.pose().pushPose();
                g.pose().translate(0, 0, 300);
                if (eff == null) g.fill(rx, ry, rx + CELL - 1, ry + CELL - 1, 0x99000000);
                if (!ov.isEmpty()) g.fill(rx + CELL - 5, ry + 1, rx + CELL - 2, ry + 4, 0xFFFFFFFF); // réglé en jeu
                g.pose().popPose();

                if (slot == hovered) {
                    tooltip = new ArrayList<>();
                    tooltip.add(st.getHoverName());
                    tooltip.add(Component.literal("§8" + id));
                    tooltip.add(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.effectif") + (eff != null
                            ? eff.chatColor() + eff.name + " §8(" + eff.rateLabel() + ")" : com.wavesurvivor.i18n.WSLang.t("ui.non_sacrifiable_a226"))));
                    RenaissanceConfigData.Rarity base = cfg.baseRarityOf(st);
                    tooltip.add(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.config") + (base != null ? base.chatColor() + base.name : "§8aucune")));
                    tooltip.add(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.reglage_en_jeu") + label(ov)));
                    tooltip.add(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.clic_gauche_droit_rarete_suivante_preced")));
                    tooltip.add(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.maj_clic_revenir_au_defaut")));
                }
            }
        }

        int n = cfg.rarityOverrides != null ? cfg.rarityOverrides.size() : 0;
        int ty = gridY() + 4 * CELL + 12;
        g.drawCenteredString(font, com.wavesurvivor.i18n.WSLang.t("ui.cycle_defaut") + cycleLegend() + com.wavesurvivor.i18n.WSLang.t("ui.exclu"), width / 2, ty, 0xFFFFFFFF);
        g.drawCenteredString(font, "§8" + n + com.wavesurvivor.i18n.WSLang.t("ui.item_s_regle_s_en_jeu_point_blanc_reglag"), width / 2, ty + 12, 0xFFFFFFFF);

        super.render(g, mx, my, pt);
        if (tooltip != null) g.renderComponentTooltip(font, tooltip, mx, my);
    }

    private String cycleLegend() {
        StringBuilder sb = new StringBuilder();
        for (RenaissanceConfigData.Rarity r : cfg.raritiesConfig) {
            if (sb.length() > 0) sb.append(" §8→ ");
            sb.append(r.chatColor()).append(r.name);
        }
        return sb.toString();
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
