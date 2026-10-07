package com.wavesurvivor.client;

import com.wavesurvivor.i18n.WSLang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Fenêtre de CHOIX D'UNE ENTITÉ : liste déroulante avec recherche.
 *   - en tête : les ENTITÉS CUSTOM (violet, ✦) ;
 *   - ensuite : toutes les créatures du jeu, vanilla et moddées (nom traduit + identifiant en gris) ;
 *   - recherche sur le nom ou l'identifiant ; molette pour défiler ; clic pour choisir ; Échap / Annuler pour revenir.
 */
public class EntityPickerScreen extends Screen {

    private static final int ROW_H = 12;
    /** Créatures de la catégorie « MISC » qu'on propose quand même. */
    private static final Set<String> MISC_OK = Set.of("minecraft:iron_golem", "minecraft:snow_golem", "minecraft:villager",
            "minecraft:wandering_trader");

    private final Screen parent;
    private final String current;
    private final Consumer<String> onPick;
    private final List<String[]> all = new ArrayList<>(); // {id, nom, "1" si custom}
    private List<String[]> shown = new ArrayList<>();
    private EditBox search;
    private int scroll = 0;

    public EntityPickerScreen(Screen parent, List<String> customEntities, String current, Consumer<String> onPick) {
        super(Component.literal(WSLang.t("ui.entpick.title")));
        this.parent = parent;
        this.current = current == null ? "" : current;
        this.onPick = onPick;
        List<String[]> customs = new ArrayList<>(), vanilla = new ArrayList<>();
        if (customEntities != null) for (String c : customEntities) customs.add(new String[]{c, c, "1"});
        for (EntityType<?> t : BuiltInRegistries.ENTITY_TYPE) {
            ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(t);
            if (key == null) continue;
            String id = key.toString();
            if (t.getCategory() == MobCategory.MISC && !MISC_OK.contains(id)) continue; // flèches, objets, projectiles…
            vanilla.add(new String[]{id, t.getDescription().getString(), "0"});
        }
        customs.sort((a, b) -> a[1].compareToIgnoreCase(b[1]));
        vanilla.sort((a, b) -> a[1].compareToIgnoreCase(b[1]));
        all.addAll(customs);
        all.addAll(vanilla);
    }

    private int listX() { return width / 2 - 140; }
    private int listY() { return 56; }
    private int listW() { return 280; }
    private int rows() { return Math.max(4, (height - listY() - 40) / ROW_H); }

    @Override
    protected void init() {
        search = new EditBox(font, listX(), 34, listW(), 16, Component.literal(WSLang.t("ui.entpick.search")));
        search.setHint(Component.literal("§7" + WSLang.t("ui.entpick.search")));
        search.setResponder(s -> { filter(); scroll = 0; });
        addRenderableWidget(search);
        setInitialFocus(search);
        addRenderableWidget(Button.builder(Component.literal(WSLang.t("ui.spell.cancel")), b -> onClose())
                .bounds(width / 2 - 50, height - 28, 100, 20).build());
        filter();
        for (int i = 0; i < shown.size(); i++) {
            if (shown.get(i)[0].equals(current)) { scroll = Math.max(0, i - rows() / 2); break; }
        }
        clampScroll();
    }

    private void filter() {
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        shown = new ArrayList<>();
        for (String[] s : all) {
            if (q.isEmpty() || s[0].toLowerCase(Locale.ROOT).contains(q) || s[1].toLowerCase(Locale.ROOT).contains(q)) shown.add(s);
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

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        g.drawCenteredString(font, "§e" + WSLang.t("ui.entpick.title") + " §8(" + shown.size() + ")", width / 2, 18, 0xFFFFFF);
        int x = listX(), y = listY(), w = listW();
        g.fill(x - 2, y - 2, x + w + 2, y + rows() * ROW_H + 2, 0xC0101018);
        int hover = rowAt(mx, my);
        for (int r = 0; r < rows() && r + scroll < shown.size(); r++) {
            String[] s = shown.get(r + scroll);
            int ry = y + r * ROW_H;
            boolean cur = s[0].equals(current);
            boolean custom = "1".equals(s[2]);
            if (r + scroll == hover) g.fill(x, ry, x + w, ry + ROW_H, 0xFF3A3A55);
            else if (cur) g.fill(x, ry, x + w, ry + ROW_H, 0xFF2E3A2E);
            String name = (cur ? "§a✔ " : "") + (custom ? "§d✦ " : "§f") + s[1];
            g.drawString(font, name, x + 3, ry + 2, 0xFFFFFF, false);
            String tag = custom ? WSLang.t("ui.entpick.custom") : s[0];
            g.drawString(font, "§8" + tag, x + w - font.width(tag) - 3, ry + 2, 0xFFFFFF, false);
        }
        if (shown.isEmpty()) g.drawCenteredString(font, WSLang.t("ui.entpick.none"), width / 2, y + 6, 0xFFFFFF);
        if (shown.size() > rows()) {
            int h = rows() * ROW_H;
            int bh = Math.max(10, h * rows() / shown.size());
            int by = y + (int) ((h - bh) * (scroll / (double) Math.max(1, shown.size() - rows())));
            g.fill(x + w + 3, y, x + w + 5, y + h, 0xFF2A2A30);
            g.fill(x + w + 3, by, x + w + 5, by + bh, 0xFFAAAAAA);
        }
        super.render(g, mx, my, partial);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int i = rowAt(mx, my);
        if (i >= 0 && button == 0) {
            onPick.accept(shown.get(i)[0]);
            minecraft.setScreen(parent);
            return true;
        }
        return super.mouseClicked(mx, my, button);
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
