package com.wavesurvivor.client;

import com.wavesurvivor.i18n.WSLang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Dessin d'un symbole personnalisé (1.9) : grille 10 × 10 (clic ou glisser pour peindre, clic droit pour effacer), un nom, puis « Valider ».
 * Le dessin est enregistré comme masque (10 lignes de 10 caractères « # » et « . ») ; sa version petite est le même dessin réduit à 6 × 6.
 */
@OnlyIn(Dist.CLIENT)
public class SymbolDrawScreen extends Screen {

    private static final int N = 10, CELL = 16;

    private final Screen parent;
    private final boolean[][] cells = new boolean[N][N];
    private String name;
    private final BiConsumer<String, List<String>> onDone;
    private EditBox nameBox;
    private int gx, gy;

    public SymbolDrawScreen(Screen parent, String name, List<String> rows, BiConsumer<String, List<String>> onDone) {
        super(Component.literal(WSLang.t("chest.ed.draw.title")));
        this.parent = parent;
        this.name = name == null ? "" : name;
        this.onDone = onDone;
        if (rows != null) for (int y = 0; y < N && y < rows.size(); y++) for (int x = 0; x < N && x < rows.get(y).length(); x++) cells[x][y] = rows.get(y).charAt(x) == '#';
    }

    @Override
    protected void init() {
        gx = width / 2 - N * CELL / 2;
        gy = height / 2 - N * CELL / 2 - 6;
        nameBox = new EditBox(font, width / 2 - 60, gy - 28, 120, 18, Component.literal(WSLang.t("chest.ed.draw.name")));
        nameBox.setMaxLength(16);
        nameBox.setValue(name);
        nameBox.setFilter(s -> s.matches("[a-zA-Z0-9_]*"));
        nameBox.setResponder(s -> name = s);
        addRenderableWidget(nameBox);
        int by = gy + N * CELL + 10;
        addRenderableWidget(Button.builder(Component.literal(WSLang.t("chest.ed.draw.clear")), b -> { for (boolean[] c : cells) java.util.Arrays.fill(c, false); })
                .bounds(width / 2 - 160, by, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal(WSLang.t("ui.fermer")), b -> onClose()).bounds(width / 2 - 50, by, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("§a" + WSLang.t("chest.ed.draw.ok")), b -> done()).bounds(width / 2 + 60, by, 100, 20).build());
    }

    private void done() {
        String n = name.trim().toLowerCase(java.util.Locale.ROOT);
        boolean any = false;
        List<String> rows = new ArrayList<>();
        for (int y = 0; y < N; y++) {
            StringBuilder sb = new StringBuilder();
            for (int x = 0; x < N; x++) { sb.append(cells[x][y] ? '#' : '.'); any |= cells[x][y]; }
            rows.add(sb.toString());
        }
        if (n.isEmpty() || !any) return; // un nom et au moins un point
        onDone.accept(n, rows);
        onClose();
    }

    private void paint(double mx, double my, boolean on) {
        int x = (int) Math.floor((mx - gx) / CELL), y = (int) Math.floor((my - gy) / CELL);
        if (x >= 0 && y >= 0 && x < N && y < N) cells[x][y] = on;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (super.mouseClicked(mx, my, btn)) return true;
        paint(mx, my, btn == 0);
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        paint(mx, my, btn == 0);
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, "§e§l" + WSLang.t("chest.ed.draw.title"), width / 2, gy - 44, 0xFFFFFFFF);
        g.fill(gx - 2, gy - 2, gx + N * CELL + 2, gy + N * CELL + 2, 0xFF11101A);
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            g.fill(gx + x * CELL, gy + y * CELL, gx + (x + 1) * CELL - 1, gy + (y + 1) * CELL - 1, cells[x][y] ? 0xFFEDE6FF : 0xFF2A2838);
        }
        // aperçu à l'échelle réelle (grand et petit)
        int px = gx + N * CELL + 24;
        g.drawString(font, "§7" + WSLang.t("chest.ed.draw.preview"), px, gy, 0xFFFFFFFF);
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) if (cells[x][y]) g.fill(px + x * 2, gy + 14 + y * 2, px + x * 2 + 2, gy + 14 + y * 2 + 2, 0xFFEDE6FF);
        g.drawString(font, "§8" + WSLang.t("chest.ed.draw.hint"), width / 2 - font.width(WSLang.t("chest.ed.draw.hint")) / 2, gy + N * CELL + 36, 0xFFFFFFFF);
        super.render(g, mx, my, pt);
    }

    @Override
    public void onClose() { net.minecraft.client.Minecraft.getInstance().setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
