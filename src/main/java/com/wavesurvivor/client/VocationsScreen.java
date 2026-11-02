package com.wavesurvivor.client;

import com.wavesurvivor.horde.kingdom.KingdomRoles;
import com.wavesurvivor.horde.kingdom.vocation.Vocations;
import com.wavesurvivor.horde.kingdom.vocation.VocationsConfig;
import com.wavesurvivor.horde.kingdom.vocation.VocationsConfig.Branch;
import com.wavesurvivor.horde.kingdom.vocation.VocationsConfig.RoleTree;
import com.wavesurvivor.horde.kingdom.vocation.VocationsConfig.Talent;
import com.wavesurvivor.horde.kingdom.vocation.VocationsConfig.Ultimate;
import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.network.NetworkHandler;
import com.wavesurvivor.network.VocationActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * VOCATIONS — écran de progression des métiers du mode Kingdom.
 *  - Grille des métiers (onglet « Vocations » de l'autel de Renaissance) : Maîtrise, barre d'XP, points à dépenser,
 *    défilement à la molette + barre de défilement ;
 *  - Arbre d'un métier : 3 branches de 4 talents + 2 talents ultimes ; clic sur un talent = +1 rang.
 * En lecture seule (ouvert depuis le Monolithe), rien ne peut être acheté.
 * Toute la validation est refaite côté serveur ({@link com.wavesurvivor.network.VocationActionPacket}).
 */
@OnlyIn(Dist.CLIENT)
public class VocationsScreen extends Screen {

    private static final int GRID_W = 325, GRID_H = 295; // même taille que l'autel de Renaissance (+25 %)
    private static final int TREE_W = 410, TREE_H = 304;
    private static final int CARD_W = 96, CARD_H = 54, CARD_STEP_X = 100, CARD_STEP_Y = 60, GRID_ROWS = 3;
    private static final int BOX_W = 128, BOX_H = 34, BOX_STEP = 40;

    private static final int GOLD = 0xFFFFD34D, PURPLE = 0xFFA855F7, GREY = 0xFF9A98A8, WHITE = 0xFFFFFFFF;

    private static final KingdomRoles.Role[] ROLES = KingdomRoles.Role.values();

    private final Screen parent;
    private final boolean readOnly;
    /** Vrai si l'écran a été ouvert directement sur un arbre (Retour ferme l'écran au lieu de revenir à la grille). */
    private final boolean direct;
    private KingdomRoles.Role open;
    private int scrollRow = 0;
    private boolean resetArmed = false;
    private int left, top, pw, ph;

    /** Zones cliquables du dernier rendu. */
    private record Hit(int x, int y, int w, int h, int kind, String id, KingdomRoles.Role role) {
        boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private static final int K_CARD = 0, K_TALENT = 1, K_ULT = 2;
    private final List<Hit> hits = new ArrayList<>();

    /** @param startRole arbre à ouvrir tout de suite (null = grille des métiers) */
    public VocationsScreen(Screen parent, KingdomRoles.Role startRole, boolean readOnly) {
        super(Component.literal(WSLang.t("voc.ui.tab")));
        this.parent = parent;
        this.open = startRole;
        this.direct = startRole != null;
        this.readOnly = readOnly;
    }

    // ─── Widgets ───

    @Override
    protected void init() {
        super.init();
        clearWidgets();
        pw = open == null ? GRID_W : TREE_W;
        ph = open == null ? GRID_H : TREE_H;
        left = (width - pw) / 2;
        top = (height - ph) / 2;
        if (open == null) {
            String[] keys = {"ui.sacrifice", "ui.boutique", "forge.tab", "heritage.tab", "voc.ui.tab"};
            for (int i = 0; i < keys.length; i++) {
                final int tabIndex = i;
                Button b = addRenderableWidget(Button.builder(Component.literal(WSLang.t(keys[i])), btn -> goTab(tabIndex))
                        .bounds(width / 2 - 155 + i * 62, top + 22, 60, 18).build());
                b.active = i != 4;
            }
        } else {
            addRenderableWidget(Button.builder(Component.literal(WSLang.t("voc.ui.back")), b -> back())
                    .bounds(left + 8, top + ph - 24, 80, 18).build());
            if (!readOnly) {
                String label = resetArmed ? WSLang.t("voc.ui.reset_confirm") : WSLang.t("voc.ui.reset", VocationsConfig.resetCostPR());
                Button rb = addRenderableWidget(Button.builder(Component.literal(label), b -> {
                    if (!resetArmed) {
                        resetArmed = true;
                        init();
                    } else {
                        resetArmed = false;
                        NetworkHandler.CHANNEL.sendToServer(new VocationActionPacket(VocationActionPacket.RESET, open.id, ""));
                        init();
                    }
                }).bounds(left + pw - 8 - 130, top + ph - 24, 130, 18).build());
                rb.active = !VocationsClient.inGame() && VocationsClient.pointsSpent(open) + (VocationsClient.ultimate(open).isEmpty() ? 0 : 1) > 0;
            }
        }
        NetworkHandler.CHANNEL.sendToServer(new VocationActionPacket(VocationActionPacket.SYNC, "", ""));
    }

    private void goTab(int tabIndex) {
        if (parent instanceof RenaissanceScreen rs) rs.switchTab(tabIndex);
        Minecraft.getInstance().setScreen(parent);
    }

    private void back() {
        if (open != null && !direct) {
            open = null;
            resetArmed = false;
            init();
        } else {
            onClose();
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ─── Formats ───

    private static String num(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    private static String val(Talent t, double v) {
        return switch (t.unit()) {
            case "pct" -> t.sign() + num(v) + " %";
            case "sec" -> t.sign() + num(v) + " s";
            case "flat" -> t.sign() + num(v);
            default -> "";
        };
    }

    private static double bv(KingdomRoles.Role r, String key, double def) { return VocationsConfig.baseValue(r.id, key, def); }

    private static Object[] baseArgs(KingdomRoles.Role r) {
        return switch (r) {
            case ARCANIST -> new Object[]{num(bv(r, "secondCatalystChance", 0.10) * 100), num(bv(r, "purifyMult", 1.5))};
            case MINER -> new Object[]{num(bv(r, "doubleLootChance", 0.15) * 100)};
            case BUILDER -> new Object[]{num(bv(r, "discount", 0.10) * 100), num(bv(r, "hpBonus", 0.05) * 100), num(bv(r, "demolitionCharges", 1))};
            case COMMANDER -> new Object[]{num(bv(r, "soldiersPerBarracks", 1)), num(bv(r, "soldierMinBarracksLevel", 2)), num(bv(r, "bannerSeconds", 40))};
            case QUARTERMASTER -> new Object[]{num(bv(r, "killCoinBonus", 0.10) * 100), num(bv(r, "merchantDiscount", 0.08) * 100),
                    num(bv(r, "interestRate", 0.03) * 100), num(bv(r, "interestCap", 60))};
            case ALCHEMIST -> new Object[]{num(bv(r, "maxFlaskLevel", 2))};
            case BOUNTY_HUNTER -> new Object[]{num(bv(r, "payoutMult", 0.70) * 100), num(bv(r, "eliteChance", 0.10) * 100)};
            case AUGUR -> new Object[]{num(bv(r, "omenChoices", 2))};
            case SCAVENGER -> new Object[]{num(bv(r, "debrisChance", 0.08) * 100), num(bv(r, "siegeDropMult", 0.8))};
        };
    }

    private String fit(String s, int w) {
        return font.width(s) <= w ? s : font.plainSubstrByWidth(s, w - font.width("…")) + "…";
    }

    // ─── Rendu ───

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        hits.clear();
        List<Component> tip = null;
        if (!VocationsClient.ready()) {
            int w = GRID_W, h = 60;
            g.fill(width / 2 - w / 2, height / 2 - h / 2, width / 2 + w / 2, height / 2 + h / 2, 0xF0120A1C);
            g.drawCenteredString(font, WSLang.t("voc.ui.loading"), width / 2, height / 2 - 4, WHITE);
        } else if (open == null) {
            renderGrid(g, mx, my);
        } else {
            tip = renderTree(g, mx, my);
        }
        super.render(g, mx, my, partial);
        if (tip != null) g.renderComponentTooltip(font, tip, mx, my);
    }

    private void panel(GuiGraphics g) {
        g.fill(left, top, left + pw, top + ph, 0xF0120A1C);
        g.fill(left, top, left + pw, top + 2, PURPLE);
        g.fill(left, top + ph - 2, left + pw, top + ph, PURPLE);
    }

    private void renderGrid(GuiGraphics g, int mx, int my) {
        panel(g);
        g.drawCenteredString(font, WSLang.t("voc.ui.altar"), width / 2, top + 8, WHITE);
        String pr = WSLang.t("voc.ui.pr", String.format(Locale.ROOT, "%,d", VocationsClient.pr()).replace(',', ' '));
        g.drawString(font, pr, left + pw - 8 - font.width(pr), top + 8, GOLD, false);
        g.drawString(font, fit(WSLang.t("voc.ui.title"), pw - 16), left + 8, top + 46, GREY, false);
        int y0 = top + 60;
        int rows = (ROLES.length + 2) / 3;
        scrollRow = Math.max(0, Math.min(scrollRow, Math.max(0, rows - GRID_ROWS)));
        for (int i = 0; i < ROLES.length; i++) {
            int row = i / 3 - scrollRow, col = i % 3;
            if (row < 0 || row >= GRID_ROWS) continue;
            KingdomRoles.Role r = ROLES[i];
            int x = left + 8 + col * CARD_STEP_X, y = y0 + row * CARD_STEP_Y;
            boolean hover = mx >= x && mx < x + CARD_W && my >= y && my < y + CARD_H;
            g.fill(x, y, x + CARD_W, y + CARD_H, hover ? 0xFF3A2D52 : 0xFF241E33);
            g.fill(x, y, x + CARD_W, y + 1, hover ? GOLD : 0xFF45405A);
            g.fill(x, y + CARD_H - 1, x + CARD_W, y + CARD_H, hover ? GOLD : 0xFF45405A);
            g.fill(x, y, x + 1, y + CARD_H, hover ? GOLD : 0xFF45405A);
            g.fill(x + CARD_W - 1, y, x + CARD_W, y + CARD_H, hover ? GOLD : 0xFF45405A);
            g.renderItem(new ItemStack(r.icon), x + 5, y + 6);
            g.drawString(font, fit(Vocations.roleName(r), CARD_W - 30), x + 26, y + 6, WHITE, false);
            int lvl = VocationsClient.level(r);
            g.drawString(font, WSLang.t("voc.ui.mastery", lvl, VocationsConfig.maxLevel()), x + 6, y + 25, lvl > 0 ? 0xFFE0B040 : GREY, false);
            g.fill(x + 6, y + 36, x + CARD_W - 6, y + 40, 0xFF26232F);
            g.fill(x + 6, y + 36, x + 6 + Math.round((CARD_W - 12) * VocationsClient.progress(r)), y + 40, PURPLE);
            g.drawString(font, Math.round(VocationsClient.progress(r) * 100) + " %", x + 6, y + 43, GREY, false);
            int free = VocationsClient.pointsFree(r);
            if (free > 0) {
                String s = WSLang.t(free == 1 ? "voc.ui.points_one" : "voc.ui.points_many", free);
                g.drawString(font, s, x + CARD_W - 6 - font.width(s), y + 43, GOLD, false);
            }
            hits.add(new Hit(x, y, CARD_W, CARD_H, K_CARD, r.id, r));
        }
        if (rows > GRID_ROWS) {
            int tx = left + pw - 14, th = GRID_ROWS * CARD_STEP_Y - 4;
            g.fill(tx, y0, tx + 4, y0 + th, 0xFF241E33);
            int thumb = Math.max(12, th * GRID_ROWS / rows);
            int ty = y0 + (th - thumb) * scrollRow / Math.max(1, rows - GRID_ROWS);
            g.fill(tx, ty, tx + 4, ty + thumb, 0xFF6B4FA0);
        }
        g.drawCenteredString(font, fit(WSLang.t("voc.ui.hint"), pw - 16), width / 2, top + ph - 14, 0xFFE0B040);
    }

    private List<Component> renderTree(GuiGraphics g, int mx, int my) {
        panel(g);
        KingdomRoles.Role r = open;
        RoleTree tree = VocationsConfig.tree(r.id);
        if (tree == null) return null;
        List<Component> tip = null;
        int lvl = VocationsClient.level(r), free = VocationsClient.pointsFree(r), spent = VocationsClient.pointsSpent(r);
        // En-tête : icône, nom, Maîtrise, barre, points
        g.renderItem(new ItemStack(r.icon), left + 10, top + 8);
        g.pose().pushPose();
        g.pose().translate(left + 32, top + 7, 0);
        g.pose().scale(1.3f, 1.3f, 1f);
        g.drawString(font, "§l" + Vocations.roleName(r), 0, 0, WHITE, false);
        g.pose().popPose();
        g.drawString(font, WSLang.t("voc.ui.mastery", lvl, VocationsConfig.maxLevel()), left + 32, top + 21, 0xFFE0B040, false);
        int bx = left + 118, bw = 80;
        g.fill(bx, top + 23, bx + bw, top + 27, 0xFF26232F);
        g.fill(bx, top + 23, bx + Math.round(bw * VocationsClient.progress(r)), top + 27, PURPLE);
        g.drawString(font, Math.round(VocationsClient.progress(r) * 100) + " %", bx + bw + 4, top + 21, GREY, false);
        String pts = WSLang.t("voc.ui.to_spend", free);
        g.drawString(font, pts, left + pw - 8 - font.width(pts), top + 9, free > 0 ? GOLD : GREY, false);
        String sp = WSLang.t("voc.ui.spent", spent);
        g.drawString(font, sp, left + pw - 8 - font.width(sp), top + 21, GREY, false);
        g.drawString(font, fit(WSLang.t("voc.base." + r.id, baseArgs(r)), pw - 16), left + 8, top + 40, GREY, false);

        // 3 colonnes de talents
        for (int c = 0; c < tree.branches().size() && c < 3; c++) {
            Branch b = tree.branches().get(c);
            int cx = left + 7 + c * (BOX_W + 5);
            int bpts = VocationsClient.branchPoints(r, b);
            g.drawString(font, "§l" + fit(WSLang.t("voc.branch." + r.id + "." + b.id()), BOX_W - 44), cx, top + 54, WHITE, false);
            String bp = WSLang.t("voc.ui.pts", bpts);
            g.drawString(font, bp, cx + BOX_W - font.width(bp), top + 54, GREY, false);
            for (int i = 0; i < b.talents().size() && i < 4; i++) {
                Talent t = b.talents().get(i);
                int y = top + 66 + i * BOX_STEP;
                int rank = VocationsClient.rank(r, t.id());
                int req = Vocations.requiredBranchPoints(tree, t.id());
                boolean unlocked = bpts >= req;
                boolean maxed = rank >= t.ranks();
                if (i < b.talents().size() - 1 && i < 3) { // lien vers le talent suivant
                    int need = Vocations.requiredBranchPoints(tree, b.talents().get(i + 1).id());
                    g.fill(cx + BOX_W / 2 - 1, y + BOX_H, cx + BOX_W / 2 + 1, y + BOX_STEP, bpts >= need ? 0xFF5DBB63 : 0xFF45445A);
                }
                boolean hover = mx >= cx && mx < cx + BOX_W && my >= y && my < y + BOX_H;
                int fill, border, txt;
                if (t.wip()) { fill = 0xFF1B1A22; border = 0xFF33323F; txt = 0xFF6A6878; }
                else if (maxed) { fill = 0xFF2D7A3A; border = 0xFF5DBB63; txt = WHITE; }
                else if (rank > 0 || unlocked) { fill = rank > 0 ? 0xFF8F6A00 : 0xFF4A3A10; border = GOLD; txt = WHITE; }
                else { fill = 0xFF25242E; border = 0xFF45445A; txt = 0xFF77758A; }
                g.fill(cx, y, cx + BOX_W, y + BOX_H, fill);
                int bc = hover && !t.wip() ? WHITE : border;
                g.fill(cx, y, cx + BOX_W, y + 1, bc);
                g.fill(cx, y + BOX_H - 1, cx + BOX_W, y + BOX_H, bc);
                g.fill(cx, y, cx + 1, y + BOX_H, bc);
                g.fill(cx + BOX_W - 1, y, cx + BOX_W, y + BOX_H, bc);
                // Ligne 1 : nom (+ total appliqué ou points requis) ; ligne 2 : valeur par rang (ou description) ; carrés de rang
                String right = !unlocked && !t.wip() ? WSLang.t("voc.ui.pts", req) : (rank > 0 ? val(t, rank * t.value()) : "");
                if (right.isEmpty() && t.unit().equals("bool") && rank > 0) right = WSLang.t("voc.ui.bool_on");
                int rw = right.isEmpty() ? 0 : font.width(right) + 4;
                g.drawString(font, fit(WSLang.t("voc.talent." + r.id + "." + t.id()), BOX_W - 10 - rw), cx + 4, y + 4, txt, false);
                if (!right.isEmpty()) g.drawString(font, right, cx + BOX_W - 4 - font.width(right), y + 4, !unlocked ? 0xFF77758A : 0xFFFFF0B0, false);
                String line2 = t.wip() ? WSLang.t("voc.ui.soon") : t.unit().equals("bool")
                        ? WSLang.t("voc.talent." + r.id + "." + t.id() + ".d") : WSLang.t("voc.ui.per_rank", val(t, t.value()));
                int sqW = t.ranks() * 5;
                int c2 = txt == WHITE ? 0xFFE8E0C8 : txt;
                if (t.unit().equals("bool") && !t.wip()) {
                    // Description longue : jusqu'à 2 lignes (la dernière est abrégée si besoin)
                    String l1 = font.plainSubstrByWidth(line2, BOX_W - 8);
                    if (l1.length() < line2.length()) {
                        int cut = l1.lastIndexOf(' ');
                        if (cut > 0) l1 = l1.substring(0, cut);
                    }
                    g.drawString(font, l1, cx + 4, y + 14, c2, false);
                    String l2 = line2.substring(l1.length()).trim();
                    if (!l2.isEmpty()) g.drawString(font, fit(l2, BOX_W - 8 - sqW), cx + 4, y + 24, c2, false);
                } else {
                    g.drawString(font, fit(line2, BOX_W - 8 - sqW), cx + 4, y + 14, c2, false);
                }
                for (int k = 0; k < t.ranks(); k++) {
                    int sx = cx + BOX_W - 4 - (t.ranks() - k) * 5 + 1, sy = y + BOX_H - 6;
                    g.fill(sx, sy, sx + 4, sy + 4, k < rank ? 0xFF7FE08A : 0xFF55546A);
                }
                hits.add(new Hit(cx, y, BOX_W, BOX_H, K_TALENT, t.id(), r));
                if (hover) tip = talentTip(r, t, rank, req, bpts, unlocked);
            }
        }

        // Talents ultimes
        boolean paid = VocationsClient.ultimatePaid(r);
        String uh = paid ? WSLang.t("voc.ui.ult_title_paid", VocationsConfig.ultimatePoints())
                : WSLang.t("voc.ui.ult_title", VocationsConfig.ultimatePoints(), VocationsConfig.ultimateCostPR());
        g.drawCenteredString(font, fit(uh, pw - 16), left + pw / 2, top + 66 + 4 * BOX_STEP + 2, 0xFFE0B040);
        String chosen = VocationsClient.ultimate(r);
        int uy = top + 66 + 4 * BOX_STEP + 14, uw = 196;
        for (int i = 0; i < tree.ultimates().size() && i < 2; i++) {
            Ultimate u = tree.ultimates().get(i);
            int ux = i == 0 ? left + 7 : left + pw - 7 - uw;
            boolean sel = u.id().equals(chosen);
            boolean can = spent >= VocationsConfig.ultimatePoints();
            boolean hover = mx >= ux && mx < ux + uw && my >= uy && my < uy + 28;
            g.fill(ux, uy, ux + uw, uy + 28, sel ? 0xFF2D7A3A : u.wip() ? 0xFF1B1A22 : 0xFF2A2338);
            int bc = sel ? 0xFF5DBB63 : hover && !u.wip() ? WHITE : u.wip() ? 0xFF33323F : 0xFF6B4FA0;
            g.fill(ux, uy, ux + uw, uy + 1, bc);
            g.fill(ux, uy + 27, ux + uw, uy + 28, bc);
            g.fill(ux, uy, ux + 1, uy + 28, bc);
            g.fill(ux + uw - 1, uy, ux + uw, uy + 28, bc);
            int tc = sel ? WHITE : u.wip() || !can ? 0xFF77758A : 0xFFD8C8F0;
            g.drawCenteredString(font, "§l" + fit(WSLang.t("voc.ult." + r.id + "." + u.id()), uw - 24), ux + uw / 2, uy + 5, tc);
            String sub = u.wip() ? WSLang.t("voc.ui.soon") : sel ? WSLang.t("voc.ui.ult_chosen") : !can ? WSLang.t("voc.ui.pts", VocationsConfig.ultimatePoints())
                    : WSLang.t("voc.ult." + r.id + "." + u.id() + ".d");
            g.drawCenteredString(font, fit(sub, uw - 8), ux + uw / 2, uy + 16, sel ? 0xFFB8F0BE : 0xFF9A98A8);
            hits.add(new Hit(ux, uy, uw, 28, K_ULT, u.id(), r));
            if (hover) {
                tip = new ArrayList<>();
                tip.add(Component.literal("§6" + WSLang.t("voc.ult." + r.id + "." + u.id())));
                for (var line : font.split(Component.literal(WSLang.t("voc.ult." + r.id + "." + u.id() + ".d")), 170)) tip.add(wrap(line));
                if (u.wip()) tip.add(Component.literal(WSLang.t("voc.ui.soon")));
            }
        }
        if (readOnly) {
            g.drawCenteredString(font, fit(WSLang.t("voc.ui.readonly"), pw - 16), left + pw / 2, top + ph - 38, 0xFF77758A);
        } else if (VocationsClient.inGame()) {
            g.drawCenteredString(font, fit(WSLang.t("voc.ui.in_game"), pw - 16), left + pw / 2, top + ph - 38, WHITE);
        }
        return tip;
    }

    private static Component wrap(net.minecraft.util.FormattedCharSequence seq) {
        StringBuilder sb = new StringBuilder();
        seq.accept((i, style, cp) -> { sb.appendCodePoint(cp); return true; });
        return Component.literal("§7" + sb);
    }

    private List<Component> talentTip(KingdomRoles.Role r, Talent t, int rank, int req, int bpts, boolean unlocked) {
        List<Component> tip = new ArrayList<>();
        tip.add(Component.literal("§6" + WSLang.t("voc.talent." + r.id + "." + t.id())));
        for (var line : font.split(Component.literal(WSLang.t("voc.talent." + r.id + "." + t.id() + ".d")), 170)) tip.add(wrap(line));
        if (!t.unit().equals("bool")) {
            tip.add(Component.literal("§e" + WSLang.t("voc.ui.per_rank", val(t, t.value()))));
            tip.add(Component.literal("§a" + WSLang.t("voc.ui.total", val(t, rank * t.value()))));
        }
        tip.add(Component.literal("§7" + WSLang.t("voc.ui.rank", rank, t.ranks())));
        if (t.wip()) tip.add(Component.literal(WSLang.t("voc.ui.soon")));
        else if (!unlocked) tip.add(Component.literal("§c" + WSLang.t("voc.ui.requires", req)));
        return tip;
    }

    // ─── Souris ───

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (Hit h : hits) {
                if (!h.in(mx, my)) continue;
                switch (h.kind()) {
                    case K_CARD -> {
                        open = h.role();
                        resetArmed = false;
                        init();
                    }
                    case K_TALENT -> {
                        if (!readOnly) NetworkHandler.CHANNEL.sendToServer(new VocationActionPacket(VocationActionPacket.BUY, h.role().id, h.id()));
                    }
                    case K_ULT -> {
                        if (!readOnly) NetworkHandler.CHANNEL.sendToServer(new VocationActionPacket(VocationActionPacket.ULTIMATE, h.role().id, h.id()));
                    }
                    default -> { }
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (open == null) {
            int rows = (ROLES.length + 2) / 3;
            if (rows > GRID_ROWS) {
                scrollRow = Math.max(0, Math.min(rows - GRID_ROWS, scrollRow - (int) Math.signum(delta)));
                return true;
            }
        }
        return super.mouseScrolled(mx, my, delta);
    }
}
