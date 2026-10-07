package com.wavesurvivor.client;

import com.wavesurvivor.i18n.WSLang;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Fenêtre de CHOIX GÉNÉRIQUE : liste déroulante avec recherche.
 *   - chaque entrée : {identifiant, nom affiché} ou {identifiant, nom affiché, texte de droite} ; identifiant (ou texte
 *     de droite) en gris ;
 *   - recherche sur le nom, l'identifiant ou le texte de droite ; molette pour défiler ; Échap / Annuler pour revenir ;
 *   - mode simple : un clic choisit ;
 *   - mode « double-clic » : un clic sélectionne, double-clic / Entrée / « Ajouter » choisit ; un panneau en bas
 *     affiche la description de l'entrée survolée ou sélectionnée.
 */
public class ListPickerScreen extends Screen {

    private static final int ROW_H = 12;

    private final Screen parent;
    private final String titleText;
    private final String current;
    private final Consumer<String> onPick;
    private final List<String[]> all;
    private final boolean doubleClick;
    private final Function<String, String> descOf;
    private List<String[]> shown = new ArrayList<>();
    private EditBox search;
    private int scroll = 0;
    private int selected = -1, lastIdx = -1;
    private long lastClick = 0;

    public ListPickerScreen(Screen parent, String title, List<String[]> entries, String current, Consumer<String> onPick) {
        this(parent, title, entries, current, onPick, false, null);
    }

    /**
     * @param doubleClick un clic s\u00e9lectionne, double-clic (ou Entr\u00e9e / \u00ab Ajouter \u00bb) choisit
     * @param descOf      description d'une entr\u00e9e (par identifiant, lignes s\u00e9par\u00e9es par \\n), affich\u00e9e en bas ; null = aucune
     */
    public ListPickerScreen(Screen parent, String title, List<String[]> entries, String current, Consumer<String> onPick,
                            boolean doubleClick, Function<String, String> descOf) {
        super(Component.literal(title));
        this.parent = parent;
        this.titleText = title;
        this.all = new ArrayList<>(entries);
        this.current = current == null ? "" : current;
        this.onPick = onPick;
        this.doubleClick = doubleClick;
        this.descOf = descOf;
    }

    private int listX() { return width / 2 - 130; }
    private int listY() { return 56; }
    private int listW() { return 260; }
    private int descH() { return descOf != null ? 50 : 0; }
    private int rows() { return Math.max(4, (height - listY() - 40 - descH()) / ROW_H); }

    @Override
    protected void init() {
        search = new EditBox(font, listX(), 34, listW(), 16, Component.literal(WSLang.t("ui.list.search")));
        search.setHint(Component.literal("\u00a77" + WSLang.t("ui.list.search")));
        search.setResponder(s -> { filter(); scroll = 0; selected = -1; });
        addRenderableWidget(search);
        setInitialFocus(search);
        if (doubleClick) {
            addRenderableWidget(Button.builder(Component.literal(WSLang.t("ui.list.add")), b -> pickSelected())
                    .bounds(width / 2 - 104, height - 28, 100, 20).build());
            addRenderableWidget(Button.builder(Component.literal(WSLang.t("ui.spell.cancel")), b -> onClose())
                    .bounds(width / 2 + 4, height - 28, 100, 20).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal(WSLang.t("ui.spell.cancel")), b -> onClose())
                    .bounds(width / 2 - 50, height - 28, 100, 20).build());
        }
        filter();
        for (int i = 0; i < shown.size(); i++) {
            if (shown.get(i)[0].equals(current)) { scroll = Math.max(0, i - rows() / 2); break; }
        }
        clampScroll();
    }

    private static String right(String[] s) { return s.length > 2 ? s[2] : s[0]; }

    private void filter() {
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        shown = new ArrayList<>();
        for (String[] s : all) {
            String hay = (s[0] + " " + s[1] + " " + right(s)).replaceAll("\u00a7.", "").toLowerCase(Locale.ROOT);
            if (q.isEmpty() || hay.contains(q)) shown.add(s);
        }
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, shown.size() - rows())));
    }

    private int rowAt(double mx, double my) {
        if (mx < listX() || mx >= listX() + listW() || my < listY()) return -1;
        int r = (int) ((my - listY()) / ROW_H);
        if (r >= rows()) return -1;
        int i = r + scroll;
        return i < shown.size() ? i : -1;
    }

    private void pick(int i) {
        if (i < 0 || i >= shown.size()) return;
        onPick.accept(shown.get(i)[0]);
        minecraft.setScreen(parent);
    }

    private void pickSelected() { pick(selected); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        g.drawCenteredString(font, "\u00a7e" + titleText + " \u00a78(" + shown.size() + ")", width / 2, 12, 0xFFFFFF);
        if (doubleClick) g.drawCenteredString(font, WSLang.t("ui.list.dbl_hint"), width / 2, 23, 0xFFFFFF);
        int x = listX(), y = listY(), w = listW();
        g.fill(x - 2, y - 2, x + w + 2, y + rows() * ROW_H + 2, 0xC0101018);
        int hover = rowAt(mx, my);
        for (int r = 0; r < rows() && r + scroll < shown.size(); r++) {
            String[] s = shown.get(r + scroll);
            int ry = y + r * ROW_H;
            boolean cur = s[0].equals(current);
            if (r + scroll == selected) g.fill(x, ry, x + w, ry + ROW_H, 0xFF5A3A7A);
            else if (r + scroll == hover) g.fill(x, ry, x + w, ry + ROW_H, 0xFF3A3A55);
            else if (cur) g.fill(x, ry, x + w, ry + ROW_H, 0xFF2E3A2E);
            String rt = right(s);
            int rw = font.width(rt.replaceAll("\u00a7.", ""));
            String left = (cur ? "\u00a7a\u2714 " : "\u00a7f") + s[1];
            int maxLeft = w - rw - 12;
            if (font.width(left) > maxLeft) left = font.plainSubstrByWidth(left, Math.max(20, maxLeft - 6)) + "\u2026";
            g.drawString(font, left, x + 3, ry + 2, 0xFFFFFF, false);
            g.drawString(font, "\u00a78" + rt, x + w - rw - 3, ry + 2, 0xFFFFFF, false);
        }
        if (shown.isEmpty()) g.drawCenteredString(font, WSLang.t("ui.list.none"), width / 2, y + 6, 0xFFFFFF);
        if (shown.size() > rows()) {
            int h = rows() * ROW_H;
            int bh = Math.max(10, h * rows() / shown.size());
            int by = y + (int) ((h - bh) * (scroll / (double) Math.max(1, shown.size() - rows())));
            g.fill(x + w + 3, y, x + w + 5, y + h, 0xFF2A2A30);
            g.fill(x + w + 3, by, x + w + 5, by + bh, 0xFFAAAAAA);
        }
        // Panneau de description (entr\u00e9e survol\u00e9e, sinon s\u00e9lectionn\u00e9e)
        if (descOf != null) {
            int py = y + rows() * ROW_H + 6;
            g.fill(x - 2, py, x + w + 2, py + descH() - 4, 0xC0181424);
            int idx = hover >= 0 ? hover : selected;
            if (idx >= 0 && idx < shown.size()) {
                String d = descOf.apply(shown.get(idx)[0]);
                int ly = py + 3;
                if (d != null) for (String part : d.split("\n")) {
                    for (FormattedCharSequence line : font.split(Component.literal(part), w - 6)) {
                        if (ly > py + descH() - 14) break;
                        g.drawString(font, line, x + 2, ly, 0xFFFFFF, false);
                        ly += 10;
                    }
                }
            } else {
                g.drawString(font, WSLang.t("ui.list.desc_hint"), x + 2, py + 3, 0xFFFFFF, false);
            }
        }
        super.render(g, mx, my, partial);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int i = rowAt(mx, my);
        if (i >= 0 && button == 0) {
            if (!doubleClick) {
                pick(i);
                return true;
            }
            long now = Util.getMillis();
            if (i == lastIdx && now - lastClick < 400) {
                pick(i); // double-clic
            } else {
                selected = i;
                lastIdx = i;
                lastClick = now;
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (doubleClick && (key == 257 || key == 335) && selected >= 0) { // Entr\u00e9e
            pickSelected();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll -= (int) Math.signum(delta) * 3;
        clampScroll();
        return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
