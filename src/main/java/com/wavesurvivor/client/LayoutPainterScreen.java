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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * DISPOSITION — placement à la main des coffres roulette et des marchands autour de l'autel / du Monolithe.
 * Même ergonomie que le peintre de sol : grille vue de dessus (nord en haut), le sol dessiné en fond.
 * Palette à droite : chaque coffre et chaque marchand (par son nom), posé UNE seule fois (le reposer le déplace).
 * Clic gauche = poser l'élément choisi, clic droit = retirer ce qui est sur la case.
 * « Les autres » : placés automatiquement en cercle (comme avant) ou absents.
 * Résultat : zone.slots (type, configKey / name, dx, dz), zone.autoLayout, zone.excludedChests.
 */
@OnlyIn(Dist.CLIENT)
public class LayoutPainterScreen extends Screen {

    /** Un élément de la palette : coffre (clé de config) ou marchand (nom). */
    private record Entry(boolean chest, String key, String label) {}

    /** Un élément posé : élément + case relative à l'autel. */
    private static final class Placed {
        final Entry e;
        int dx, dz;
        Placed(Entry e, int dx, int dz) { this.e = e; this.dx = dx; this.dz = dz; }
    }

    private static final int ROW_H = 14;

    private final Screen parent;
    private final JsonObject zone;
    private final Runnable onChange;
    private final int radius, size;
    private final List<Entry> entries = new ArrayList<>();
    private final List<Placed> placed = new ArrayList<>();
    private final Set<String> excluded = new HashSet<>();
    private boolean autoOthers;
    private int selected = -1; // -1 = gomme
    private int scroll = 0;
    private int cell, gx, gy, px, py, rows;

    // Sol dessiné (fond)
    private final List<String> floorPalette = new ArrayList<>();
    private final List<String> floorPattern = new ArrayList<>();

    public LayoutPainterScreen(Screen parent, JsonObject zone, int radius, List<String> chests, List<String> merchants, Runnable onChange) {
        super(Component.literal(WSLang.t("ui.layout.title")));
        this.parent = parent;
        this.zone = zone;
        this.onChange = onChange;
        this.radius = Math.max(1, Math.min(32, radius));
        this.size = this.radius * 2 + 1;

        for (String c : chests) {
            String key = c.contains("|") ? c.substring(0, c.indexOf('|')) : c;
            String name = c.contains("|") ? c.substring(c.indexOf('|') + 1) : c;
            entries.add(new Entry(true, key, name));
        }
        for (int i = 0; i < merchants.size(); i++) {
            String n = merchants.get(i) == null ? "" : merchants.get(i);
            String shown = n.isBlank() ? WSLang.t("ui.layout.merchant_n", i + 1) : WSLang.t(n);
            entries.add(new Entry(false, n, shown));
        }

        autoOthers = !zone.has("autoLayout") || zone.get("autoLayout").getAsBoolean();
        if (zone.has("excludedChests") && zone.get("excludedChests").isJsonArray()) {
            for (JsonElement e : zone.getAsJsonArray("excludedChests")) if (e.isJsonPrimitive()) excluded.add(e.getAsString().toLowerCase());
        } else {
            excluded.add("coffre_ender"); // valeur par défaut historique
        }
        if (zone.has("slots") && zone.get("slots").isJsonArray()) {
            for (JsonElement el : zone.getAsJsonArray("slots")) {
                if (!el.isJsonObject()) continue;
                JsonObject s = el.getAsJsonObject();
                String type = s.has("type") ? s.get("type").getAsString() : "";
                Entry match = null;
                for (Entry e : entries) {
                    if ("chest".equals(type) && e.chest && s.has("configKey") && e.key.equalsIgnoreCase(s.get("configKey").getAsString())) match = e;
                    if ("merchant".equals(type) && !e.chest && s.has("name") && e.key.equalsIgnoreCase(s.get("name").getAsString())) match = e;
                    if (match != null) break;
                }
                if (match == null || placedOf(match) != null) continue;
                int dx = s.has("dx") ? s.get("dx").getAsInt() : 0, dz = s.has("dz") ? s.get("dz").getAsInt() : 0;
                if (inside(dx, dz)) placed.add(new Placed(match, dx, dz));
            }
        }
        if (zone.has("palette") && zone.get("palette").isJsonArray()) {
            for (JsonElement e : zone.getAsJsonArray("palette")) floorPalette.add(e.getAsString());
        }
        if (zone.has("pattern") && zone.get("pattern").isJsonArray()) {
            for (JsonElement e : zone.getAsJsonArray("pattern")) floorPattern.add(e.getAsString());
        }
        if (!entries.isEmpty()) selected = 0;
    }

    // ─── Données ───

    private Placed placedOf(Entry e) {
        for (Placed p : placed) if (p.e == e) return p;
        return null;
    }

    private Placed placedAt(int dx, int dz) {
        for (Placed p : placed) if (p.dx == dx && p.dz == dz) return p;
        return null;
    }

    /** Case dans le cercle de la zone (les emplacements hors cercle sont refusés en jeu). */
    private boolean inside(int dx, int dz) {
        return dx * dx + dz * dz <= (radius + 0.5) * (radius + 0.5) && !(Math.abs(dx) <= 1 && Math.abs(dz) <= 1);
    }

    private void save() {
        JsonArray slots = new JsonArray();
        for (Placed p : placed) {
            JsonObject s = new JsonObject();
            s.addProperty("type", p.e.chest ? "chest" : "merchant");
            if (p.e.chest) s.addProperty("configKey", p.e.key);
            else s.addProperty("name", p.e.key);
            s.addProperty("dx", p.dx);
            s.addProperty("dy", 0);
            s.addProperty("dz", p.dz);
            slots.add(s);
        }
        zone.add("slots", slots);
        zone.addProperty("autoLayout", autoOthers);
        JsonArray ex = new JsonArray();
        for (String k : excluded) ex.add(k);
        zone.add("excludedChests", ex);
        onChange.run();
        Minecraft.getInstance().setScreen(parent);
    }

    // ─── Écran ───

    @Override
    protected void init() {
        cell = Math.max(3, Math.min(12, Math.min((height - 70) / size, (width - 240) / size)));
        gx = 14;
        gy = 34;
        px = gx + size * cell + 16;
        py = gy;
        rows = Math.max(3, (height - 130 - (py + 12)) / ROW_H);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, entries.size() - rows)));
        clearWidgets();
        int bx = px, by = height - 92, bw = 104;
        addRenderableWidget(Button.builder(Component.literal((selected < 0 ? "§e" : "") + WSLang.t("ui.painter.eraser")),
                b -> { selected = -1; init(); }).bounds(bx, by, bw, 18).build());
        addRenderableWidget(Button.builder(Component.literal(WSLang.t("ui.layout.others") + (autoOthers ? "§a" + WSLang.t("ui.layout.auto") : "§c" + WSLang.t("ui.layout.none"))),
                b -> { autoOthers = !autoOthers; init(); }).bounds(bx + bw + 4, by, bw, 18).build());
        addRenderableWidget(Button.builder(Component.literal(WSLang.t("ui.layout.clear")),
                b -> { placed.clear(); init(); }).bounds(bx, by + 22, bw, 18).build());
        if (entries.size() > rows) {
            addRenderableWidget(Button.builder(Component.literal("▲"), b -> { scroll--; init(); })
                    .bounds(bx + bw + 4, by + 22, 50, 18).build()).active = scroll > 0;
            addRenderableWidget(Button.builder(Component.literal("▼"), b -> { scroll++; init(); })
                    .bounds(bx + bw + 58, by + 22, 50, 18).build()).active = scroll + rows < entries.size();
        }
        addRenderableWidget(Button.builder(Component.literal("§a" + WSLang.t("ui.painter.save")),
                b -> save()).bounds(bx, by + 50, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("§c" + WSLang.t("ui.painter.cancel")),
                b -> Minecraft.getInstance().setScreen(parent)).bounds(bx + bw + 4, by + 50, bw, 20).build());
    }

    private static Block block(String id) {
        try {
            Block b = BuiltInRegistries.BLOCK.get(new ResourceLocation(id));
            return b == null ? Blocks.AIR : b;
        } catch (Exception e) {
            return Blocks.AIR;
        }
    }

    private static final java.util.Map<String, net.minecraft.client.renderer.texture.TextureAtlasSprite> SPRITES = new java.util.HashMap<>();

    @SuppressWarnings("deprecation")
    private static net.minecraft.client.renderer.texture.TextureAtlasSprite spriteOf(String id) {
        return SPRITES.computeIfAbsent(id, k -> Minecraft.getInstance().getBlockRenderer()
                .getBlockModel(block(k).defaultBlockState()).getParticleIcon());
    }

    /** Bloc du sol dessiné à la case (dx, dz), ou null (le dessin peut avoir un autre rayon : recentré). */
    private String floorAt(int dx, int dz) {
        if (floorPattern.isEmpty()) return null;
        int n = floorPattern.size(), mid = n / 2;
        int r = dz + mid, c = dx + mid;
        if (r < 0 || r >= n) return null;
        String line = floorPattern.get(r);
        if (c < 0 || c >= line.length()) return null;
        int v = Character.digit(line.charAt(c), 36);
        return v >= 0 && v < floorPalette.size() ? floorPalette.get(v) : null;
    }

    private static ItemStack icon(Entry e) {
        return new ItemStack(e.chest ? Items.CHEST : Items.EMERALD);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawString(font, "§l" + WSLang.t("ui.layout.title") + " §7(" + size + "×" + size + ")", gx, 12, 0xFFFFFFFF);
        g.drawString(font, "§8" + WSLang.t("ui.layout.hint"), gx, 22, 0xFFFFFFFF);

        // Grille : sol dessiné en fond (assombri), hors zone grisé
        int mid = radius;
        g.fill(gx - 1, gy - 1, gx + size * cell + 1, gy + size * cell + 1, 0xFF000000);
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                int x = gx + c * cell, y = gy + r * cell, w = cell - (cell > 4 ? 1 : 0);
                int dx = c - mid, dz = r - mid;
                String fl = floorAt(dx, dz);
                if (fl != null) {
                    var sp = spriteOf(fl);
                    if (sp != null) g.blit(x, y, 0, w, w, sp);
                    else g.fill(x, y, x + w, y + w, 0xFF000000 | block(fl).defaultBlockState().getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col);
                    g.fill(x, y, x + w, y + w, 0x88000000);
                } else {
                    g.fill(x, y, x + w, y + w, ((r + c) & 1) == 0 ? 0xFF1E1E24 : 0xFF26262E);
                }
                if (!inside(dx, dz)) g.fill(x, y, x + w, y + w, 0xC0101010);
            }
        }
        // Autel / Monolithe au centre
        int ax = gx + mid * cell, ay = gy + mid * cell;
        g.fill(ax, ay, ax + cell - 1, ay + cell - 1, 0xFFFFD700);
        g.drawCenteredString(font, "N", gx + size * cell / 2, gy - 9, 0xFFAAAAAA);

        // Éléments posés
        for (Placed p : placed) {
            int x = gx + (p.dx + mid) * cell, y = gy + (p.dz + mid) * cell;
            g.fill(x - 1, y - 1, x + cell, y + cell, p.e.chest ? 0xFF8B5A2B : 0xFF2E8B57);
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            float s = Math.max(0.2f, (cell - 1) / 16f);
            g.pose().scale(s, s, 1f);
            g.renderItem(icon(p.e), 0, 0);
            g.pose().popPose();
        }

        // Case survolée
        int hc = mx >= gx ? (mx - gx) / Math.max(1, cell) : -1, hr = my >= gy ? (my - gy) / Math.max(1, cell) : -1;
        String hoverName = null;
        if (hc >= 0 && hr >= 0 && hc < size && hr < size) {
            g.renderOutline(gx + hc * cell - 1, gy + hr * cell - 1, cell + 1, cell + 1, 0xFFFFFFFF);
            Placed hp = placedAt(hc - mid, hr - mid);
            if (hp != null) hoverName = hp.e.label;
            g.drawString(font, "§7" + (hc - mid) + ", " + (hr - mid) + (hp != null ? "  §f" + hp.e.label : ""), gx, gy + size * cell + 4, 0xFFFFFFFF);
        }

        // Palette
        g.drawString(font, "§e" + WSLang.t("ui.layout.palette"), px, py, 0xFFFFFFFF);
        for (int i = 0; i < rows && i + scroll < entries.size(); i++) {
            int idx = i + scroll;
            Entry e = entries.get(idx);
            int y = py + 12 + i * ROW_H, w = 212;
            boolean sel = idx == selected;
            g.fill(px - 1, y - 1, px + w, y + ROW_H - 1, sel ? 0xFF5A4A10 : 0xFF22222A);
            if (sel) g.renderOutline(px - 1, y - 1, w + 1, ROW_H, 0xFFFFD700);
            g.pose().pushPose();
            g.pose().translate(px + 1, y, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.renderItem(icon(e), 0, 0);
            g.pose().popPose();
            String state;
            if (placedOf(e) != null) state = "§a📍";
            else if (!autoOthers) state = "§8—";
            else if (e.chest && excluded.contains(e.key.toLowerCase())) state = "§c✘";
            else state = "§7auto";
            String name = e.label.length() > 26 ? e.label.substring(0, 25) + "…" : e.label;
            g.drawString(font, name, px + 16, y + 3, 0xFFFFFFFF, false);
            g.drawString(font, state, px + w - 4 - font.width(state), y + 3, 0xFFFFFFFF, false);
        }
        if (entries.isEmpty()) g.drawString(font, "§8" + WSLang.t("ui.layout.empty"), px, py + 14, 0xFFFFFFFF);
        g.drawString(font, "§8" + WSLang.t("ui.layout.legend"), px, height - 108, 0xFFFFFFFF);
        super.render(g, mx, my, pt);
        if (hoverName != null) g.renderTooltip(font, Component.literal(hoverName), mx, my);
    }

    // ─── Souris ───

    @Override
    public boolean mouseClicked(double mxd, double myd, int button) {
        int mx = (int) mxd, my = (int) myd;
        // Grille
        if (mx >= gx && my >= gy && mx < gx + size * cell && my < gy + size * cell) {
            int dx = (mx - gx) / cell - radius, dz = (my - gy) / cell - radius;
            Placed here = placedAt(dx, dz);
            if (button == 1 || selected < 0) {
                if (here != null) placed.remove(here);
                return true;
            }
            if (!inside(dx, dz) || selected >= entries.size()) return true;
            Entry e = entries.get(selected);
            Placed old = placedOf(e);
            if (here != null && here != old) placed.remove(here); // la case est reprise
            if (old != null) { old.dx = dx; old.dz = dz; }   // déjà posé : déplacé
            else placed.add(new Placed(e, dx, dz));
            return true;
        }
        // Palette : gauche = choisir, droit sur un coffre = exclure / réinclure du placement auto
        if (mx >= px - 1 && mx < px + 212 && my >= py + 12 && my < py + 12 + rows * ROW_H) {
            int idx = (my - py - 12) / ROW_H + scroll;
            if (idx >= 0 && idx < entries.size()) {
                Entry e = entries.get(idx);
                if (button == 1 && e.chest) {
                    String k = e.key.toLowerCase();
                    if (!excluded.remove(k)) excluded.add(k);
                } else {
                    selected = idx;
                }
                init();
                return true;
            }
        }
        return super.mouseClicked(mxd, myd, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx >= px && entries.size() > rows) {
            scroll = Math.max(0, Math.min(entries.size() - rows, scroll - (int) Math.signum(delta)));
            init();
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
