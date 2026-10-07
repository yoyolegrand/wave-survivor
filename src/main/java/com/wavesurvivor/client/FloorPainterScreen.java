package com.wavesurvivor.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * PEINTRE DE SOL — dessin manuel du sol de l'arène d'un autel (hordes classiques et Kingdom).
 * Grille carrée (2 × rayon + 1) centrée sur l'autel ; chaque case = un bloc de la palette, ou vide
 * (vide = tirage pondéré du sol classique, comme avant). Outils : pinceau, gomme, symétrie ×4, remplir, tout effacer,
 * annuler. Résultat stocké dans la zone : "palette" (ids de blocs) + "pattern" (lignes de caractères).
 */
@OnlyIn(Dist.CLIENT)
public class FloorPainterScreen extends Screen {

    private static final int MAX_PALETTE = 36;
    private final Screen parent;
    private final JsonObject zone;
    private final Runnable onChange;
    private final List<String> palette = new ArrayList<>();
    private final Deque<char[][]> undo = new ArrayDeque<>();
    private int size;
    /** Rayon de la zone (la grille fait 2×rayon+1), modifiable en direct avec les boutons − / +. */
    private int radius;
    private char[][] grid;
    private int brush = 0;          // index de palette, -1 = gomme
    private boolean symmetry = false;
    private boolean painting = false;
    private int cell, gx, gy, px, py;
    private final List<String> suggestions = new ArrayList<>();

    public FloorPainterScreen(Screen parent, JsonObject zone, Runnable onChange) {
        super(Component.literal(WSLang.t("ui.painter.title")));
        this.parent = parent;
        this.zone = zone;
        this.onChange = onChange;
        int radius = zone.has("radius") ? Math.max(1, Math.min(32, zone.get("radius").getAsInt())) : 8;
        this.radius = radius;
        this.size = radius * 2 + 1;
        this.grid = new char[size][size];
        for (char[] row : grid) java.util.Arrays.fill(row, '.');
        if (zone.has("palette") && zone.get("palette").isJsonArray()) {
            for (JsonElement e : zone.getAsJsonArray("palette")) palette.add(e.getAsString());
        }
        // Reprend un dessin existant (recentré si le rayon a changé)
        if (zone.has("pattern") && zone.get("pattern").isJsonArray()) {
            JsonArray rows = zone.getAsJsonArray("pattern");
            int old = rows.size(), off = (size - old) / 2;
            for (int r = 0; r < old; r++) {
                String line = rows.get(r).getAsString();
                for (int c = 0; c < line.length(); c++) {
                    int rr = r + off, cc = c + off;
                    if (rr >= 0 && rr < size && cc >= 0 && cc < size) grid[rr][cc] = line.charAt(c);
                }
            }
        }
        // Blocs proposés : sol pondéré de la zone + blocs de l'inventaire
        Set<String> sug = new LinkedHashSet<>();
        if (zone.has("floor") && zone.get("floor").isJsonArray()) {
            for (JsonElement e : zone.getAsJsonArray("floor")) {
                if (e.isJsonObject() && e.getAsJsonObject().has("block")) sug.add(e.getAsJsonObject().get("block").getAsString());
            }
        }
        var player = Minecraft.getInstance().player;
        if (player != null) {
            for (ItemStack st : player.getInventory().items) {
                if (st.getItem() instanceof BlockItem bi) sug.add(BuiltInRegistries.BLOCK.getKey(bi.getBlock()).toString());
            }
        }
        suggestions.addAll(sug);
    }

    @Override
    protected void init() {
        cell = Math.max(3, Math.min(12, Math.min((height - 70) / size, (width - 220) / size)));
        gx = 14;
        gy = 34;
        px = gx + size * cell + 16;
        py = gy;
        clearWidgets();
        int bx = px, by = height - 112, bw = 98;
        // Rayon de la zone : la grille se redimensionne en direct (Maj = ±4), le dessin reste centré
        addRenderableWidget(Button.builder(Component.literal("−"), b -> resize(hasShiftDown() ? -4 : -1))
                .bounds(bx, by - 24, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> resize(hasShiftDown() ? 4 : 1))
                .bounds(bx + 2 * bw + 4 - 20, by - 24, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal(brush < 0 ? "§e" + WSLang.t("ui.painter.eraser") : WSLang.t("ui.painter.eraser")),
                b -> { brush = -1; init(); }).bounds(bx, by, bw, 18).build());
        addRenderableWidget(Button.builder(Component.literal(WSLang.t("ui.painter.symmetry") + (symmetry ? "§a" + WSLang.t("ui.painter.on") : "§7" + WSLang.t("ui.painter.off"))),
                b -> { symmetry = !symmetry; init(); }).bounds(bx + bw + 4, by, bw, 18).build());
        addRenderableWidget(Button.builder(Component.literal(WSLang.t("ui.painter.fill")),
                b -> { pushUndo(); fillAll(brush < 0 ? '.' : Character.forDigit(brush, 36)); }).bounds(bx, by + 22, bw, 18).build());
        addRenderableWidget(Button.builder(Component.literal(WSLang.t("ui.painter.clear")),
                b -> { pushUndo(); fillAll('.'); }).bounds(bx + bw + 4, by + 22, bw, 18).build());
        addRenderableWidget(Button.builder(Component.literal(WSLang.t("ui.painter.undo")),
                b -> { if (!undo.isEmpty()) grid = undo.pop(); }).bounds(bx, by + 44, bw, 18).build());
        addRenderableWidget(Button.builder(Component.literal("§a" + WSLang.t("ui.painter.save")),
                b -> save()).bounds(bx, by + 72, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("§c" + WSLang.t("ui.painter.cancel")),
                b -> Minecraft.getInstance().setScreen(parent)).bounds(bx + bw + 4, by + 72, bw, 20).build());
    }

    // ─── Données ───

    private void pushUndo() {
        char[][] copy = new char[size][];
        for (int r = 0; r < size; r++) copy[r] = grid[r].clone();
        undo.push(copy);
        while (undo.size() > 30) undo.removeLast();
    }

    private void fillAll(char c) {
        for (char[] row : grid) java.util.Arrays.fill(row, c);
    }

    private void paint(int col, int row, char c) {
        set(col, row, c);
        if (symmetry) {
            set(size - 1 - col, row, c);
            set(col, size - 1 - row, c);
            set(size - 1 - col, size - 1 - row, c);
        }
    }

    private void set(int col, int row, char c) {
        if (row >= 0 && row < size && col >= 0 && col < size) grid[row][col] = c;
    }

    /** Retire un bloc de la palette : ses cases deviennent vides, les index suivants sont décalés. */
    private void removeFromPalette(int idx) {
        pushUndo();
        palette.remove(idx);
        for (char[] row : grid) {
            for (int c = 0; c < size; c++) {
                int v = Character.digit(row[c], 36);
                if (v < 0) continue;
                if (v == idx) row[c] = '.';
                else if (v > idx) row[c] = Character.forDigit(v - 1, 36);
            }
        }
        if (brush >= palette.size()) brush = palette.size() - 1;
    }

    /** Change le rayon : nouvelle grille (2×rayon+1), ancien dessin recopié au centre (rogné si on réduit). */
    private void resize(int delta) {
        int nr = Math.max(1, Math.min(32, radius + delta));
        if (nr == radius) return;
        int ns = nr * 2 + 1, off = (ns - size) / 2;
        char[][] g2 = new char[ns][ns];
        for (char[] row : g2) java.util.Arrays.fill(row, '.');
        for (int r = 0; r < size; r++) for (int c = 0; c < size; c++) {
            int nr2 = r + off, nc = c + off;
            if (nr2 >= 0 && nc >= 0 && nr2 < ns && nc < ns) g2[nr2][nc] = grid[r][c];
        }
        undo.clear(); // les états précédents ont une autre taille
        grid = g2;
        size = ns;
        radius = nr;
        init();
    }

    private void save() {
        zone.addProperty("radius", radius); // rayon choisi dans le peintre
        boolean any = false;
        for (char[] row : grid) for (char ch : row) if (ch != '.') { any = true; break; }
        if (!any || palette.isEmpty()) {
            zone.remove("pattern");
            zone.remove("palette");
        } else {
            JsonArray pal = new JsonArray();
            for (String id : palette) pal.add(id);
            JsonArray rows = new JsonArray();
            for (char[] row : grid) rows.add(new String(row));
            zone.add("palette", pal);
            zone.add("pattern", rows);
        }
        onChange.run();
        Minecraft.getInstance().setScreen(parent);
    }

    // ─── Rendu ───

    private static Block block(String id) {
        try {
            Block b = BuiltInRegistries.BLOCK.get(new ResourceLocation(id));
            return b == null ? Blocks.AIR : b;
        } catch (Exception e) {
            return Blocks.AIR;
        }
    }

    private static int colorOf(String id) {
        Block b = block(id);
        int col = b.defaultBlockState().getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col;
        return 0xFF000000 | (col == 0 ? 0x808080 : col);
    }

    /** Texture réelle du bloc (celle du modèle), en cache : deux blocs de même couleur de carte restent distincts. */
    private static final java.util.Map<String, net.minecraft.client.renderer.texture.TextureAtlasSprite> SPRITES = new java.util.HashMap<>();

    @SuppressWarnings("deprecation")
    private static net.minecraft.client.renderer.texture.TextureAtlasSprite spriteOf(String id) {
        return SPRITES.computeIfAbsent(id, k -> Minecraft.getInstance().getBlockRenderer()
                .getBlockModel(block(k).defaultBlockState()).getParticleIcon());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawString(font, "§l" + WSLang.t("ui.painter.title") + " §7(" + size + "×" + size + ")", gx, 12, 0xFFFFFFFF);
        g.drawString(font, "§8" + WSLang.t("ui.painter.hint"), gx, 22, 0xFFFFFFFF);

        // Grille
        g.fill(gx - 1, gy - 1, gx + size * cell + 1, gy + size * cell + 1, 0xFF000000);
        int[] colors = new int[palette.size()];
        for (int i = 0; i < palette.size(); i++) colors[i] = colorOf(palette.get(i));
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                int x = gx + c * cell, y = gy + r * cell;
                int v = Character.digit(grid[r][c], 36);
                int w = cell - (cell > 4 ? 1 : 0);
                if (v >= 0 && v < colors.length) {
                    // Texture du bloc (repli sur la couleur de carte si elle manque)
                    var sp = spriteOf(palette.get(v));
                    if (sp != null) g.blit(x, y, 0, w, w, sp);
                    else g.fill(x, y, x + w, y + w, colors[v]);
                } else {
                    g.fill(x, y, x + w, y + w, ((r + c) & 1) == 0 ? 0xFF1E1E24 : 0xFF26262E);
                }
            }
        }
        // Autel au centre
        int mid = size / 2, ax = gx + mid * cell, ay = gy + mid * cell;
        g.renderOutline(ax - 1, ay - 1, cell + 1, cell + 1, 0xFFFFD700);
        // Case survolée
        int hc = (mx - gx) / Math.max(1, cell), hr = (my - gy) / Math.max(1, cell);
        if (mx >= gx && my >= gy && hc < size && hr < size) {
            g.renderOutline(gx + hc * cell - 1, gy + hr * cell - 1, cell + 1, cell + 1, 0xFFFFFFFF);
            int hv = Character.digit(grid[hr][hc], 36);
            String hname = hv >= 0 && hv < palette.size() ? "  §f" + block(palette.get(hv)).getName().getString() : "";
            g.drawString(font, "§7" + (hc - mid) + ", " + (hr - mid) + hname, gx, gy + size * cell + 4, 0xFFFFFFFF);
        }

        // Palette
        g.drawCenteredString(font, "§7" + WSLang.t("ui.painter.radius", radius) + " §8(" + size + "×" + size + ")",
                px + 98 + 2, height - 112 - 19, 0xFFFFFFFF);
        g.drawString(font, "§e" + WSLang.t("ui.painter.palette"), px, py, 0xFFFFFFFF);
        for (int i = 0; i < palette.size(); i++) {
            int x = px + (i % 10) * 20, y = py + 12 + (i / 10) * 20;
            if (i == brush) g.fill(x - 2, y - 2, x + 18, y + 18, 0xFFFFD700);
            g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF2A2A33);
            g.renderItem(new ItemStack(block(palette.get(i)).asItem()), x, y);
        }
        // Blocs proposés
        int sy = py + 12 + ((palette.size() + 9) / 10) * 20 + 8;
        g.drawString(font, "§b" + WSLang.t("ui.painter.add"), px, sy, 0xFFFFFFFF);
        for (int i = 0; i < Math.min(suggestions.size(), 30); i++) {
            int x = px + (i % 10) * 20, y = sy + 12 + (i / 10) * 20;
            g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF1A2A33);
            g.renderItem(new ItemStack(block(suggestions.get(i)).asItem()), x, y);
        }
        super.render(g, mx, my, pt);

        // Infobulle : nom du bloc survolé (palette / propositions)
        String hover = blockAt(mx, my, sy);
        if (hover != null) g.renderTooltip(font, new ItemStack(block(hover).asItem()), mx, my);
    }

    /** Id du bloc sous la souris (palette ou propositions), ou null. */
    private String blockAt(int mx, int my, int sy) {
        int i = slotIndex(mx, my, px, py + 12, palette.size());
        if (i >= 0) return palette.get(i);
        i = slotIndex(mx, my, px, sy + 12, Math.min(suggestions.size(), 30));
        return i >= 0 ? suggestions.get(i) : null;
    }

    private static int slotIndex(int mx, int my, int x0, int y0, int count) {
        if (mx < x0 || my < y0) return -1;
        int c = (mx - x0) / 20, r = (my - y0) / 20;
        if (c >= 10) return -1;
        int i = r * 10 + c;
        return i < count ? i : -1;
    }

    // ─── Souris ───

    @Override
    public boolean mouseClicked(double mxd, double myd, int button) {
        int mx = (int) mxd, my = (int) myd;
        // Grille
        if (mx >= gx && my >= gy && mx < gx + size * cell && my < gy + size * cell) {
            pushUndo();
            painting = true;
            paintAt(mx, my, button);
            return true;
        }
        // Palette : gauche = pinceau, droit = retirer
        int i = slotIndex(mx, my, px, py + 12, palette.size());
        if (i >= 0) {
            if (button == 1) removeFromPalette(i);
            else brush = i;
            init();
            return true;
        }
        // Propositions : ajout à la palette (et sélection)
        int sy = py + 12 + ((palette.size() + 9) / 10) * 20 + 8;
        i = slotIndex(mx, my, px, sy + 12, Math.min(suggestions.size(), 30));
        if (i >= 0) {
            String id = suggestions.get(i);
            int existing = palette.indexOf(id);
            if (existing >= 0) brush = existing;
            else if (palette.size() < MAX_PALETTE) {
                palette.add(id);
                brush = palette.size() - 1;
            }
            init();
            return true;
        }
        return super.mouseClicked(mxd, myd, button);
    }

    @Override
    public boolean mouseDragged(double mxd, double myd, int button, double dx, double dy) {
        if (painting) {
            paintAt((int) mxd, (int) myd, button);
            return true;
        }
        return super.mouseDragged(mxd, myd, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        painting = false;
        return super.mouseReleased(mx, my, button);
    }

    private void paintAt(int mx, int my, int button) {
        int c = (mx - gx) / cell, r = (my - gy) / cell;
        if (c < 0 || r < 0 || c >= size || r >= size) return;
        char ch = (button == 1 || brush < 0 || palette.isEmpty()) ? '.' : Character.forDigit(brush, 36);
        paint(c, r, ch);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
