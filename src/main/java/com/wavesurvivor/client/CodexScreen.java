package com.wavesurvivor.client;

import com.wavesurvivor.i18n.WSLang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * CODEX : guide des mécaniques du mod (/ws codex ou bouton « Codex » de l'autel).
 * Sommaire à gauche, page défilante à droite. Textes dans wslang (codex.*) ; les listes (rôles, présages,
 * mutateurs, affixes, défenses…) réutilisent les descriptions déjà présentes en jeu, donc restent à jour.
 */
@OnlyIn(Dist.CLIENT)
public class CodexScreen extends Screen {

    private static final String[] PAGES = {
            "start", "classic", "breaches", "blessings", "difficulty", "boss",
            "kingdom", "objectives", "treasury", "monolith", "defenses", "workshop", "tools",
            "roles", "alchemy", "omens", "relics", "renaissance", "interface", "editor"
    };
    private static final String[] ICONS = {"📖", "⚔", "☄", "✚", "🎲", "☠",
            "♛", "⚑", "◆", "🏛", "🛡", "⚒", "➤",
            "♟", "⚗", "👁", "✦", "☼", "⌨", "✎"};
    private static final String[] WORKSHOP_SPECS = {"forge", "armory", "mechanic", "foundry"};
    private static final String[] HERITAGE_BRANCHES = {"survivor", "merchant", "lord"};

    private static final String[] DIFFICULTIES = {"easy", "normal", "hard", "nightmare"};
    private static final String[] MUTATORS = {"frenzy", "armored", "swarm", "volatile", "venomous", "fragile", "hungry",
            "one_life", "enraged", "escort", "eternal_night", "unstable", "glass_monolith", "brutes", "runts"};
    private static final String[] AFFIXES = {"vampiric", "armored", "summoner", "swift", "reflector", "volatile", "glacial",
            "infernal", "stormcaller", "blinking", "regenerating", "berserker", "magnetic", "toxic"};
    private static final String[][] DEFENSES = {
            {"kingdom_archer_tower", "archer"}, {"kingdom_mage_tower", "mage"}, {"kingdom_repair_shrine", "shrine"},
            {"kingdom_barracks", "barracks"}, {"kingdom_collector", "collector"}};
    private static final String[][] TRAPS = {
            {"kingdom_trap_spikes", "spikes"}, {"kingdom_trap_fire", "fire"}, {"kingdom_trap_frost", "frost"},
            {"kingdom_trap_explosive", "explosive"}, {"kingdom_trap_snare", "snare"}};
    private static final String[] ROLES = {"arcanist", "miner", "builder", "commander", "quartermaster",
            "alchemist", "bounty_hunter", "augur", "scavenger"};
    private static final String[] FLASKS = {"frost", "fire", "heal", "storm", "bastion"};
    private static final String[] OMENS = {"tide", "bounty", "slumber", "frenzy", "iron_rain", "drowsy",
            "royal_hunt", "fair_winds", "blood_moon", "offering"};

    /** Dernière page consultée (gardée pendant la session). */
    private static int lastPage = 0;

    private final Screen parent;
    private int page;
    private int scroll = 0;
    /** Première page visible dans le sommaire (le sommaire défile quand il y a plus de pages que de place). */
    private int sideScroll = 0;
    private int left, top, panelW, panelH;
    private static final int SIDE_W = 128, ROW_H = 16, LINE_H = 10;
    private List<FormattedCharSequence> lines = new ArrayList<>();

    public CodexScreen(Screen parent) {
        super(Component.literal(WSLang.t("codex.title")));
        this.parent = parent;
        this.page = lastPage;
    }

    public static void open(Screen parent) {
        Minecraft.getInstance().setScreen(new CodexScreen(parent));
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
        ensureVisible();
        rebuild();
    }

    private int textX() { return left + SIDE_W + 12; }
    private int textW() { return panelW - SIDE_W - 24; }
    private int textTop() { return top + 42; }
    private int visibleLines() { return Math.max(1, (top + panelH - 10 - textTop()) / LINE_H); }
    private int sideRows() { return Math.max(1, (panelH - 28 - 8) / ROW_H); }

    /** Garde la page choisie visible dans le sommaire. */
    private void ensureVisible() {
        int rows = sideRows();
        if (page < sideScroll) sideScroll = page;
        else if (page >= sideScroll + rows) sideScroll = page - rows + 1;
        sideScroll = Math.max(0, Math.min(sideScroll, Math.max(0, PAGES.length - rows)));
    }

    // ─── Contenu ───

    private void rebuild() {
        List<String> raw = content(PAGES[page]);
        lines = new ArrayList<>();
        for (String para : raw) {
            if (para.isEmpty()) { lines.add(FormattedCharSequence.EMPTY); continue; }
            lines.addAll(font.split(Component.literal(para), textW()));
        }
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    private int maxScroll() { return Math.max(0, lines.size() - visibleLines()); }

    /** Paragraphes de la page : texte du codex (lignes séparées par \n) + listes générées. */
    private List<String> content(String id) {
        List<String> out = new ArrayList<>();
        body(out, "codex." + id + ".body");
        switch (id) {
            case "difficulty" -> {
                section(out, "codex.sec.difficulties");
                for (String d : DIFFICULTIES) entry(out, "§f" + WSLang.t("difficulty." + d), WSLang.t("difficulty." + d + ".desc"));
                section(out, "codex.sec.mutators");
                out.add("§7" + WSLang.t("mutator.subtitle"));
                for (String m : MUTATORS) entry(out, "§d" + WSLang.t("mutator." + m), WSLang.t("mutator." + m + ".desc"));
            }
            case "boss" -> {
                section(out, "codex.sec.affixes");
                for (String a : AFFIXES) entry(out, WSLang.t("boss.affix." + a), WSLang.t("boss.affix." + a + ".desc"));
            }
            case "defenses" -> {
                section(out, "codex.sec.defenses");
                for (String[] d : DEFENSES) entry(out, "§f" + block(d[0]), WSLang.t("kingdom.defense.desc." + d[1]));
                out.add("§8" + strip(WSLang.t("kingdom.defense.hint")));
                section(out, "codex.sec.walls");
                entry(out, "§f" + block("kingdom_rampart"), WSLang.t("kingdom.rampart.desc"));
                entry(out, "§f" + block("kingdom_gate_part"), WSLang.t("codex.gate.desc"));
                entry(out, WSLang.t("kingdom.charge.name"), WSLang.t("kingdom.charge.tooltip"));
                section(out, "codex.sec.traps");
                for (String[] t : TRAPS) entry(out, "§f" + block(t[0]), WSLang.t("kingdom.trap.desc." + t[1]));
                out.add("§8" + strip(WSLang.t("kingdom.trap.hint")));
            }
            case "relics" -> {
                section(out, "codex.sec.sets");
                body(out, "codex.sets.hint");
                for (com.wavesurvivor.item.RelicSets.Family f : com.wavesurvivor.item.RelicSets.Family.values()) {
                    StringBuilder names = new StringBuilder();
                    for (net.minecraft.world.item.Item it : f.items()) {
                        if (names.length() > 0) names.append(", ");
                        names.append(Component.translatable(it.getDescriptionId()).getString());
                    }
                    out.add("");
                    out.add("§e• " + WSLang.t("set." + f.id));
                    out.add("§8" + names);
                    for (int t = 2; t <= f.max; t++) out.add("§7  " + t + " : " + WSLang.t("set." + f.id + "." + t));
                }
            }
            case "renaissance" -> {
                for (String b : HERITAGE_BRANCHES) {
                    out.add("");
                    out.add("§e• " + WSLang.t("heritage." + b));
                    for (int n = 1; n <= 5; n++) out.add("§7  " + n + ". " + WSLang.t("heritage." + b + "." + n));
                }
                out.add("");
                body(out, "codex.renaissance.tail");
                for (int r = 1; r <= 5; r++) out.add("§7  " + r + " · §f" + WSLang.t("heritage.title." + r));
            }
            case "workshop" -> {
                section(out, "codex.sec.specs");
                for (String v : WORKSHOP_SPECS) entry(out, "§f" + WSLang.t("kingdom.variant." + v), WSLang.t("kingdom.variant.desc." + v));
            }
            case "roles" -> { for (String r : ROLES) multi(out, WSLang.t("kingdom.role." + r + ".desc")); }
            case "alchemy" -> { for (String f : FLASKS) multi(out, WSLang.t("kingdom.alchemy.desc." + f)); }
            case "omens" -> { for (String o : OMENS) multi(out, WSLang.t("kingdom.omen." + o + ".desc")); }
            default -> {}
        }
        return out;
    }

    private static String block(String id) {
        return Component.translatable("block.wavesurvivor." + id).getString();
    }

    private static String strip(String s) { return s.replaceAll("§.", ""); }

    private static void body(List<String> out, String key) {
        String t = WSLang.t(key);
        if (t.equals(key)) return;
        for (String l : t.split("\n", -1)) out.add(l);
    }

    private static void section(List<String> out, String key) {
        out.add("");
        out.add("§6§l" + WSLang.t(key));
    }

    private static void entry(List<String> out, String name, String desc) {
        out.add("§e• " + name);
        out.add("§7" + desc.replace("|", " "));
    }

    /** Description « Nom|ligne|ligne » (rôles, fioles, présages) : nom en titre, une ligne par segment. */
    private static void multi(List<String> out, String desc) {
        String[] parts = desc.split("\\||\n");
        out.add("");
        out.add("§e• §l" + parts[0]);
        for (int i = 1; i < parts.length; i++) out.add("  " + parts[i]);
    }

    // ─── Interaction ───

    private int pageAt(double mx, double my) {
        int y0 = top + 28;
        if (mx < left + 6 || mx >= left + SIDE_W) return -1;
        int row = (int) Math.floor((my - y0) / ROW_H);
        int i = row + sideScroll;
        return my >= y0 && row >= 0 && row < sideRows() && i >= 0 && i < PAGES.length ? i : -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int p = pageAt(mx, my);
        if (p >= 0 && btn == 0) {
            page = lastPage = p;
            scroll = 0;
            rebuild();
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.0F));
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx < left + SIDE_W) { // sur le sommaire : il défile
            sideScroll = Math.max(0, Math.min(Math.max(0, PAGES.length - sideRows()), sideScroll - (int) Math.signum(delta)));
            return true;
        }
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(delta) * 3));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int sc, int mods) {
        // ↑ / ↓ : page précédente / suivante
        if (key == 265 || key == 264) {
            int d = key == 265 ? -1 : 1;
            page = lastPage = (page + d + PAGES.length) % PAGES.length;
            scroll = 0;
            ensureVisible();
            rebuild();
            return true;
        }
        return super.keyPressed(key, sc, mods);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(left, top, left + panelW, top + panelH, 0xF0141018);
        g.fill(left, top, left + panelW, top + 2, 0xFFF59E0B);
        g.fill(left + SIDE_W + 4, top + 26, left + SIDE_W + 5, top + panelH - 8, 0x55FFFFFF);
        g.drawString(font, "§6§l📖 " + WSLang.t("codex.title"), left + 8, top + 9, 0xFFFFFFFF);

        // Sommaire
        int hover = pageAt(mx, my);
        for (int i = sideScroll; i < Math.min(PAGES.length, sideScroll + sideRows()); i++) {
            int y = top + 28 + (i - sideScroll) * ROW_H;
            if (i == page) g.fill(left + 6, y, left + SIDE_W, y + ROW_H - 2, 0x66F59E0B);
            else if (i == hover) g.fill(left + 6, y, left + SIDE_W, y + ROW_H - 2, 0x33FFFFFF);
            String label = ICONS[i] + " " + WSLang.t("codex." + PAGES[i]);
            if (font.width(label) > SIDE_W - 14) label = font.plainSubstrByWidth(label, SIDE_W - 18) + "…";
            g.drawString(font, (i == page ? "§f" : "§7") + label, left + 10, y + 4, 0xFFFFFFFF);
        }

        // Flèches : d'autres pages au-dessus / en dessous
        if (sideScroll > 0) g.drawString(font, "§8▲", left + SIDE_W - 12, top + 12, 0xFFFFFFFF);
        if (sideScroll + sideRows() < PAGES.length) g.drawString(font, "§8▼", left + SIDE_W - 12, top + panelH - 11, 0xFFFFFFFF);

        // Page
        g.drawString(font, "§e§l" + ICONS[page] + " " + WSLang.t("codex." + PAGES[page]), textX(), top + 28, 0xFFFFFFFF);
        int vis = visibleLines();
        g.enableScissor(textX(), textTop(), textX() + textW() + 6, textTop() + vis * LINE_H);
        for (int i = 0; i < vis && i + scroll < lines.size(); i++) {
            g.drawString(font, lines.get(i + scroll), textX(), textTop() + i * LINE_H, 0xFFFFFFFF);
        }
        g.disableScissor();

        // Barre de défilement
        if (maxScroll() > 0) {
            int trackTop = textTop(), trackH = vis * LINE_H;
            int barH = Math.max(12, trackH * vis / lines.size());
            int barY = trackTop + (trackH - barH) * scroll / maxScroll();
            int bx = left + panelW - 8;
            g.fill(bx, trackTop, bx + 3, trackTop + trackH, 0x33FFFFFF);
            g.fill(bx, barY, bx + 3, barY + barH, 0xFFF59E0B);
        }
        super.render(g, mx, my, pt);
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
