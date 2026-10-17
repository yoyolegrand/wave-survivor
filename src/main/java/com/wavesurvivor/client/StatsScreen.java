package com.wavesurvivor.client;

import com.wavesurvivor.horde.stats.Achievements;
import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.network.StatsPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * STATISTIQUES ET SUCCÈS (1.7) : compteurs du joueur à gauche, liste des succès à droite (icône, titre, progression).
 * Ouvert par /ws stats ou le bouton « Statistiques » de l'écran de l'autel.
 */
@OnlyIn(Dist.CLIENT)
public class StatsScreen extends Screen {

    /** Compteurs affichés, dans l'ordre (texte : clé stats.&lt;clé&gt;). */
    private static final String[] KEYS = {"hordes_started", "hordes_won", "distinct_hordes", "waves_cleared", "monsters_killed",
            "bosses_killed", "deaths", "playtime_ticks", "bossrush_wins", "daily_wins", "daily_best_streak", "endless_best_wave"};

    private static final int ROW_H = 26;

    private final StatsPackets.Data data;
    private final Set<String> done = new HashSet<>();
    private int left, top, panelW, panelH;
    private int scroll = 0;

    public StatsScreen(StatsPackets.Data data) {
        super(Component.literal(WSLang.t("stats.title")));
        this.data = data;
        this.done.addAll(data.done);
    }

    @Override
    protected void init() {
        clearWidgets();
        panelW = Math.min(480, width - 16);
        panelH = Math.min(320, height - 16);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        addRenderableWidget(Button.builder(Component.literal(WSLang.t("ui.fermer")), b -> onClose())
                .bounds(left + panelW - 66, top + 5, 60, 16).build());
    }

    private int listX() { return left + 190; }
    private int listTop() { return top + 44; }
    private int listW() { return panelW - 190 - 16; }
    private int visibleRows() { return Math.max(1, (top + panelH - 8 - listTop()) / ROW_H); }
    private int maxScroll() { return Math.max(0, Achievements.ALL.size() - visibleRows()); }

    private long stat(String key) { return data.stats.getOrDefault(key, 0L); }

    /** Valeur affichée d'un compteur (le temps de jeu en heures et minutes). */
    private String shown(String key) {
        long v = stat(key);
        if (key.equals("playtime_ticks")) {
            long min = v / 1200;
            return (min / 60) + " h " + String.format("%02d", min % 60) + " min";
        }
        return String.format("%,d", v).replace(',', ' ');
    }

    /** Progression d'un succès : « ✔ » une fois obtenu, sinon valeur / seuil. */
    private String progress(Achievements.Def d) {
        if (done.contains(d.id())) return "§a✔";
        long v = Math.min(stat(d.stat()), d.threshold());
        if (d.stat().equals("playtime_ticks")) return "§7" + (v / 72000) + "/" + (d.threshold() / 72000) + " h";
        return "§7" + String.format("%,d", v).replace(',', ' ') + "/" + String.format("%,d", d.threshold()).replace(',', ' ');
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(left, top, left + panelW, top + panelH, 0xF0141018);
        g.fill(left, top, left + panelW, top + 2, 0xFFF59E0B);
        g.drawString(font, "§6§l📊 " + WSLang.t("stats.title"), left + 8, top + 9, 0xFFFFFFFF);

        // Compteurs
        int y = top + 30;
        g.drawString(font, "§e§l" + WSLang.t("stats.section_stats"), left + 10, y, 0xFFFFFFFF);
        y += 14;
        for (String k : KEYS) {
            g.drawString(font, "§7" + WSLang.t("stats." + k), left + 10, y, 0xFFFFFFFF);
            String v = "§f" + shown(k);
            g.drawString(font, v, left + 178 - font.width(v), y, 0xFFFFFFFF);
            y += 13;
        }
        g.fill(left + 184, top + 28, left + 185, top + panelH - 8, 0x55FFFFFF);

        // Succès
        int total = Achievements.ALL.size();
        g.drawString(font, "§e§l" + WSLang.t("stats.section_ach", done.size(), total), listX(), top + 30, 0xFFFFFFFF);
        int vis = visibleRows();
        g.enableScissor(listX(), listTop(), listX() + listW() + 4, listTop() + vis * ROW_H);
        for (int i = 0; i < vis && i + scroll < total; i++) {
            Achievements.Def d = Achievements.ALL.get(i + scroll);
            int ry = listTop() + i * ROW_H;
            boolean ok = done.contains(d.id());
            g.fill(listX(), ry, listX() + listW(), ry + ROW_H - 2, ok ? 0x4422AA44 : 0x22FFFFFF);
            ItemStack icon = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(d.icon())));
            g.renderItem(icon, listX() + 4, ry + 3);
            String title = Component.translatable("advancement.wavesurvivor." + d.id() + ".title").getString();
            String desc = Component.translatable("advancement.wavesurvivor." + d.id() + ".desc").getString();
            if (font.width(desc) > listW() - 80) desc = font.plainSubstrByWidth(desc, listW() - 86) + "…";
            g.drawString(font, (ok ? "§a" : "§f") + title, listX() + 26, ry + 3, 0xFFFFFFFF);
            g.drawString(font, "§8" + desc, listX() + 26, ry + 14, 0xFFFFFFFF);
            String pr = progress(d);
            g.drawString(font, pr, listX() + listW() - 4 - font.width(pr), ry + 8, 0xFFFFFFFF);
        }
        g.disableScissor();

        if (maxScroll() > 0) {
            int trackH = vis * ROW_H;
            int barH = Math.max(12, trackH * vis / total);
            int barY = listTop() + (trackH - barH) * scroll / maxScroll();
            int bx = left + panelW - 8;
            g.fill(bx, listTop(), bx + 3, listTop() + trackH, 0x33FFFFFF);
            g.fill(bx, barY, bx + 3, barY + barH, 0xFFF59E0B);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
