package com.wavesurvivor.client;

import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.network.CarnetPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * CARNET DU SURVIVANT : 4 onglets — Démarrage (5 pages courtes avec lien vers le Codex), Objectifs (cochés automatiquement),
 * Ressources (où on les gagne, à quoi elles servent) et recherche plein texte dans le Codex. Textes dans wslang (carnet.*).
 */
@OnlyIn(Dist.CLIENT)
public class CarnetScreen extends Screen {

    /** Pages de l'onglet Démarrage : {id du texte, page du Codex}. */
    private static final String[][] START = {{"altar", "start"}, {"blessings", "blessings"}, {"renaissance", "renaissance"}, {"shrine", "shrine"}, {"kingdom", "kingdom"}};
    /** Fiches de l'onglet Ressources : {id, objet d'icône}. */
    private static final String[][] RESOURCES = {
            {"coins", "minecraft:emerald"}, {"essence", "minecraft:echo_shard"}, {"materials", "minecraft:iron_ingot"},
            {"pr", "minecraft:experience_bottle"}, {"grace", "wavesurvivor:grace_fragment"}, {"taint", "wavesurvivor:taint_fragment"},
            {"reagents", "minecraft:brewing_stand"}, {"mastery", "minecraft:enchanted_book"}, {"pouch", "wavesurvivor:key_pouch"}};
    private static final String[] TABS = {"start", "objectives", "resources", "codex"};

    private static final int SIDE_W = 128, ROW_H = 16, LINE_H = 10, OBJ_H = 38;

    /** Dernier onglet consulté (gardé pendant la session). */
    private static int lastTab = 0;

    private final List<CarnetPacket.Obj> objectives;
    private int tab = lastTab, sel = 0, scroll = 0;
    private int left, top, panelW, panelH;
    private List<FormattedCharSequence> lines = new ArrayList<>();
    private EditBox search;
    private String query = "";
    private List<Hit> hits = new ArrayList<>();

    /** Résultat de recherche : page du Codex et extrait. */
    private record Hit(int page, String title, String snippet) {}

    public CarnetScreen(List<CarnetPacket.Obj> objectives) {
        super(Component.literal(WSLang.t("carnet.title")));
        this.objectives = objectives;
    }

    public static void open(List<CarnetPacket.Obj> objectives) {
        Minecraft.getInstance().setScreen(new CarnetScreen(objectives));
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
        // onglets : placés après le titre, largeur selon le texte (réduite proportionnellement si ça ne tient pas)
        int startX = left + 8 + font.width("§l" + WSLang.t("carnet.title")) + 12;
        int avail = left + panelW - 72 - startX;
        int[] ws = new int[TABS.length];
        int total = 0;
        for (int i = 0; i < TABS.length; i++) { ws[i] = font.width(WSLang.t("carnet.tab." + TABS[i])) + 12; total += ws[i] + 2; }
        float k = total > avail ? avail / (float) total : 1f;
        int bx0 = startX;
        for (int i = 0; i < TABS.length; i++) {
            final int t = i;
            int w = Math.max(20, (int) (ws[i] * k));
            Button b = Button.builder(Component.literal((i == tab ? "§e" : "§7") + WSLang.t("carnet.tab." + TABS[i])), x -> switchTab(t))
                    .bounds(bx0, top + 5, w, 16).build();
            addRenderableWidget(b);
            bx0 += w + 2;
        }
        search = null;
        if (tab == 3) {
            search = new EditBox(font, left + 10, top + 30, panelW - 20, 16, Component.literal(WSLang.t("carnet.search")));
            search.setHint(Component.literal("§8" + WSLang.t("carnet.search")));
            search.setMaxLength(60);
            search.setValue(query);
            search.setResponder(s -> { query = s; scroll = 0; runSearch(); });
            addRenderableWidget(search);
            setInitialFocus(search);
            runSearch();
        }
        rebuildText();
    }

    private void switchTab(int t) {
        tab = lastTab = t;
        sel = 0;
        scroll = 0;
        rebuildWidgets();
    }

    // ─── Contenu textuel (Démarrage, Ressources) ───

    private int textX() { return left + SIDE_W + 12; }
    private int textW() { return panelW - SIDE_W - 28; }
    private int bodyTop() { return top + 30; }
    private int bodyBottom() { return top + panelH - 8; }
    private int visibleLines() { return Math.max(1, (bodyBottom() - bodyTop() - 34) / LINE_H); }

    private int listSize() { return tab == 0 ? START.length : tab == 2 ? RESOURCES.length : 0; }

    private void rebuildText() {
        lines = new ArrayList<>();
        if (tab == 0) {
            String id = START[Math.min(sel, START.length - 1)][0];
            addWrapped(WSLang.t("carnet.start." + id));
        } else if (tab == 2) {
            String id = RESOURCES[Math.min(sel, RESOURCES.length - 1)][0];
            lines.add(FormattedCharSequence.EMPTY);
            addWrapped("§6§l" + WSLang.t("carnet.res.where"));
            addWrapped("§7" + WSLang.t("carnet.res." + id + ".where"));
            lines.add(FormattedCharSequence.EMPTY);
            addWrapped("§6§l" + WSLang.t("carnet.res.use"));
            addWrapped("§7" + WSLang.t("carnet.res." + id + ".use"));
        }
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    private void addWrapped(String text) {
        for (String para : text.split("\n", -1)) {
            if (para.isEmpty()) { lines.add(FormattedCharSequence.EMPTY); continue; }
            lines.addAll(font.split(Component.literal(para), textW()));
        }
    }

    private int maxScroll() {
        return switch (tab) {
            case 1 -> Math.max(0, objectives.size() * OBJ_H - (bodyBottom() - (top + 46)));
            case 3 -> Math.max(0, hits.size() * 24 - (bodyBottom() - (top + 54)));
            default -> Math.max(0, (lines.size() - visibleLines()) * LINE_H);
        };
    }

    // ─── Recherche dans le Codex ───

    /** Index du Codex (construit une fois) : texte normalisé de chaque page. */
    private record PageIndex(String title, String normTitle, List<String> plain, List<String> norm) {}
    private static List<PageIndex> index = null;

    private static String normalize(String s) {
        String t = Normalizer.normalize(s.replaceAll("§.", ""), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return t.toLowerCase(Locale.ROOT);
    }

    private static List<PageIndex> index() {
        if (index != null) return index;
        List<PageIndex> out = new ArrayList<>();
        for (String id : CodexScreen.PAGES) {
            String title = WSLang.t("codex." + id);
            List<String> plain = new ArrayList<>(), norm = new ArrayList<>();
            try {
                for (String l : CodexScreen.content(id)) {
                    String p = l.replaceAll("§.", "").trim();
                    if (p.isEmpty()) continue;
                    plain.add(p);
                    norm.add(normalize(p));
                }
            } catch (Exception ignored) { /* page dépendant d'un état indisponible : titre seul */ }
            out.add(new PageIndex(title, normalize(title), plain, norm));
        }
        index = out;
        return out;
    }

    private void runSearch() {
        hits = new ArrayList<>();
        String q = normalize(query.trim());
        if (q.length() < 2) return;
        List<PageIndex> idx = index();
        List<Hit> titleHits = new ArrayList<>(), bodyHits = new ArrayList<>();
        for (int i = 0; i < idx.size(); i++) {
            PageIndex pg = idx.get(i);
            String snippet = null;
            for (int j = 0; j < pg.norm().size(); j++) {
                if (pg.norm().get(j).contains(q)) { snippet = pg.plain().get(j); break; }
            }
            if (pg.normTitle().contains(q)) titleHits.add(new Hit(i, pg.title(), snippet == null ? "" : snippet));
            else if (snippet != null) bodyHits.add(new Hit(i, pg.title(), snippet));
        }
        hits.addAll(titleHits);
        hits.addAll(bodyHits);
    }

    // ─── Interaction ───

    private boolean inSide(double mx) { return mx >= left + 6 && mx < left + SIDE_W; }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (super.mouseClicked(mx, my, btn)) return true;
        if (btn != 0) return false;
        if ((tab == 0 || tab == 2) && inSide(mx)) {
            int row = (int) Math.floor((my - (top + 30)) / ROW_H);
            if (row >= 0 && row < listSize()) { sel = row; scroll = 0; rebuildText(); click(); return true; }
        }
        if (tab == 0) { // bouton « Voir dans le Codex »
            int bx = textX(), by = bodyBottom() - 20;
            if (mx >= bx && mx < bx + 150 && my >= by && my < by + 16) {
                CodexScreen.openAt(this, START[sel][1]);
                return true;
            }
        }
        if (tab == 3) {
            int y0 = top + 54;
            int row = (int) Math.floor((my - y0 + scroll) / 24.0);
            if (my >= y0 && row >= 0 && row < hits.size()) {
                CodexScreen.openAt(this, CodexScreen.PAGES[hits.get(row).page()]);
                return true;
            }
        }
        return false;
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.0F));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int step = tab == 1 ? 16 : tab == 3 ? 24 : LINE_H * 3;
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(delta) * step));
        return true;
    }

    // ─── Rendu ───

    private String rewardText(CarnetPacket.Obj o) {
        List<String> parts = new ArrayList<>();
        if (o.pr() > 0) parts.add(WSLang.t("carnet.reward.pr", o.pr()));
        for (int i = 0; i < o.items().size(); i++) {
            Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(o.items().get(i)));
            if (it != null) parts.add(o.counts().get(i) + "× " + Component.translatable(it.getDescriptionId()).getString());
        }
        return String.join(", ", parts);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(left, top, left + panelW, top + panelH, 0xF0141018);
        g.fill(left, top, left + panelW, top + 2, 0xFFF59E0B);
        g.drawString(font, "§6§l" + WSLang.t("carnet.title"), left + 8, top + 9, 0xFFFFFFFF);
        switch (tab) {
            case 0, 2 -> renderListTab(g, mx, my);
            case 1 -> renderObjectives(g);
            default -> renderSearch(g, mx, my);
        }
        super.render(g, mx, my, pt);
    }

    private void renderListTab(GuiGraphics g, int mx, int my) {
        g.fill(left + SIDE_W + 4, top + 28, left + SIDE_W + 5, top + panelH - 8, 0x55FFFFFF);
        for (int i = 0; i < listSize(); i++) {
            int y = top + 30 + i * ROW_H;
            boolean hover = inSide(mx) && my >= y && my < y + ROW_H - 2;
            if (i == sel) g.fill(left + 6, y, left + SIDE_W, y + ROW_H - 2, 0x66F59E0B);
            else if (hover) g.fill(left + 6, y, left + SIDE_W, y + ROW_H - 2, 0x33FFFFFF);
            String name = WSLang.t(tab == 0 ? "carnet.start." + START[i][0] + ".t" : "carnet.res." + RESOURCES[i][0]);
            if (font.width(name) > SIDE_W - 28) name = font.plainSubstrByWidth(name, SIDE_W - 32) + "…";
            if (tab == 2) {
                Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(RESOURCES[i][1]));
                if (it != null) g.renderItem(new ItemStack(it), left + 8, y - 1);
                g.drawString(font, (i == sel ? "§f" : "§7") + name, left + 26, y + 4, 0xFFFFFFFF);
            } else {
                g.drawString(font, (i == sel ? "§f" : "§7") + (i + 1) + ". " + name, left + 10, y + 4, 0xFFFFFFFF);
            }
        }
        String head = WSLang.t(tab == 0 ? "carnet.start." + START[sel][0] + ".t" : "carnet.res." + RESOURCES[sel][0]);
        g.drawString(font, "§e§l" + head, textX(), top + 30, 0xFFFFFFFF);
        int vis = visibleLines(), first = scroll / LINE_H, ty = top + 46;
        g.enableScissor(textX(), ty, textX() + textW() + 6, ty + vis * LINE_H);
        for (int i = 0; i < vis && i + first < lines.size(); i++) g.drawString(font, lines.get(i + first), textX(), ty + i * LINE_H, 0xFFFFFFFF);
        g.disableScissor();
        if (tab == 0) {
            int bx = textX(), by = bodyBottom() - 20;
            boolean hover = mx >= bx && mx < bx + 150 && my >= by && my < by + 16;
            g.fill(bx, by, bx + 150, by + 16, hover ? 0xFFB07408 : 0xFF8A5A06);
            g.drawCenteredString(font, "§f" + WSLang.t("carnet.see_codex"), bx + 75, by + 4, 0xFFFFFFFF);
        }
    }

    private void renderObjectives(GuiGraphics g) {
        int done = 0;
        for (CarnetPacket.Obj o : objectives) if (o.done()) done++;
        g.drawString(font, "§e§l" + WSLang.t("carnet.tab.objectives") + " §7" + done + "/" + objectives.size(), left + 10, top + 30, 0xFFFFFFFF);
        if (objectives.isEmpty()) {
            g.drawString(font, "§7" + WSLang.t("carnet.no_objectives"), left + 10, top + 50, 0xFFFFFFFF);
            return;
        }
        int y0 = top + 46, h = bodyBottom() - y0;
        g.enableScissor(left + 6, y0, left + panelW - 6, y0 + h);
        int w = panelW - 28;
        for (int i = 0; i < objectives.size(); i++) {
            CarnetPacket.Obj o = objectives.get(i);
            int y = y0 + i * OBJ_H - scroll;
            if (y + OBJ_H < y0 || y > y0 + h) continue;
            g.fill(left + 10, y, left + 10 + w, y + OBJ_H - 4, o.done() ? 0x33406A2A : 0x22FFFFFF);
            g.drawString(font, o.done() ? "§a☑" : "§7☐", left + 16, y + 4, 0xFFFFFFFF);
            g.drawString(font, (o.done() ? "§a" : "§f") + WSLang.t("carnet.obj." + o.id()), left + 30, y + 4, 0xFFFFFFFF);
            String desc = WSLang.t("carnet.obj." + o.id() + ".d");
            if (font.width(desc) > w - 130) desc = font.plainSubstrByWidth(desc, w - 134) + "…";
            g.drawString(font, "§8" + desc, left + 30, y + 15, 0xFFFFFFFF);
            String rw = rewardText(o);
            g.drawString(font, (o.done() ? "§8" : "§6") + WSLang.t("carnet.reward") + " " + rw, left + 30, y + 25, 0xFFFFFFFF);
            // progression
            int bx = left + 10 + w - 112, bw = 100;
            g.fill(bx, y + 6, bx + bw, y + 12, 0x66000000);
            g.fill(bx, y + 6, bx + (int) (bw * Math.min(1.0, o.progress() / (double) o.threshold())), y + 12, o.done() ? 0xFF55AA33 : 0xFFF59E0B);
            g.drawString(font, "§7" + o.progress() + "/" + o.threshold(), bx + bw - font.width(o.progress() + "/" + o.threshold()), y + 15, 0xFFFFFFFF);
        }
        g.disableScissor();
    }

    private void renderSearch(GuiGraphics g, int mx, int my) {
        int y0 = top + 54, h = bodyBottom() - y0;
        if (query.trim().length() < 2) {
            g.drawString(font, "§7" + WSLang.t("carnet.search.hint"), left + 10, y0 + 4, 0xFFFFFFFF);
            return;
        }
        if (hits.isEmpty()) {
            g.drawString(font, "§7" + WSLang.t("carnet.search.none"), left + 10, y0 + 4, 0xFFFFFFFF);
            return;
        }
        g.enableScissor(left + 6, y0, left + panelW - 6, y0 + h);
        for (int i = 0; i < hits.size(); i++) {
            Hit hit = hits.get(i);
            int y = y0 + i * 24 - scroll;
            if (y + 24 < y0 || y > y0 + h) continue;
            boolean hover = mx >= left + 10 && mx < left + panelW - 10 && my >= y && my < y + 22;
            g.fill(left + 10, y, left + panelW - 10, y + 22, hover ? 0x44F59E0B : 0x22FFFFFF);
            g.drawString(font, "§e" + hit.title(), left + 14, y + 3, 0xFFFFFFFF);
            String sn = hit.snippet();
            if (font.width(sn) > panelW - 40) sn = font.plainSubstrByWidth(sn, panelW - 46) + "…";
            g.drawString(font, "§7" + sn, left + 14, y + 12, 0xFFFFFFFF);
        }
        g.disableScissor();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
