package com.wavesurvivor.client;

import com.google.gson.JsonObject;
import com.wavesurvivor.horde.editor.HordeJson;
import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.network.BlessingEditorPackets;
import com.wavesurvivor.network.NetworkHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * RÉGLAGES DES BÉNÉDICTIONS (1.8, opérateurs) : nombre de choix, poids de rareté, croissance par vague, Chance, relances et bannissements.
 * Ouvert depuis l'éditeur des bénédictions ; « Enregistrer » envoie les réglages complets au serveur, qui ne garde que les différences
 * avec la base du mod (couche du joueur). « Valeurs du mod » efface les modifications.
 */
@OnlyIn(Dist.CLIENT)
public class BlessingSettingsScreen extends Screen {

    private static final String[] R = {"COMMON", "RARE", "EPIC", "LEGENDARY"};
    private static final String[] USES = {"draw", "roulette", "brazier", "extraLoot"};

    /** Une ligne : chemin dans le JSON, type (0 entier, 1 décimal, 2 booléen), clé de texte. */
    private record Field(String[] path, int kind, String key) {}

    private final Screen parent;
    private final JsonObject s;
    private final List<Object> rows = new ArrayList<>(); // Field ou String (titre de section)
    private int scroll = 0;
    private boolean dirty = false;
    private int left, top, W, H;
    private final List<Object[]> labels = new ArrayList<>();

    public BlessingSettingsScreen(Screen parent, JsonObject settings) {
        super(Component.literal(WSLang.t("blessset.title")));
        this.parent = parent;
        this.s = settings;
        rows.add("blessset.sec_draw");
        rows.add(new Field(new String[]{"choices"}, 0, "blessset.choices"));
        rows.add("blessset.sec_weights");
        for (String r : R) rows.add(new Field(new String[]{"weights", r}, 1, "blessed.rarity." + r));
        rows.add("blessset.sec_growth");
        for (String r : R) rows.add(new Field(new String[]{"waveGrowth", r}, 1, "blessed.rarity." + r));
        rows.add("blessset.sec_luck");
        rows.add(new Field(new String[]{"luck", "scale"}, 1, "blessset.luck_scale"));
        rows.add(new Field(new String[]{"luck", "maxBonus"}, 1, "blessset.luck_max"));
        for (String r : R) rows.add(new Field(new String[]{"luck", "shift", r}, 1, "blessed.rarity." + r));
        rows.add("blessset.sec_uses");
        for (String u : USES) rows.add(new Field(new String[]{"luck", "uses", u}, 2, "blessset.use_" + u));
        rows.add(new Field(new String[]{"luck", "extraLoot", "maxChance"}, 1, "blessset.extra_chance"));
        rows.add("blessset.sec_rerolls");
        for (String k : new String[]{"start", "every", "grant"}) rows.add(new Field(new String[]{"rerolls", k}, 0, "blessset.r_" + k));
        rows.add("blessset.sec_bans");
        for (String k : new String[]{"start", "every", "grant"}) rows.add(new Field(new String[]{"bans", k}, 0, "blessset.r_" + k));
    }

    private JsonObject holder(Field f) {
        JsonObject o = s;
        for (int i = 0; i < f.path().length - 1; i++) o = HordeJson.obj(o, f.path()[i]);
        return o;
    }

    private static String fmt(double v) {
        return Math.abs(v - Math.round(v)) < 1e-9 ? String.valueOf((long) Math.round(v)) : String.valueOf(v);
    }

    private static double parse(String v) {
        try { return v.isEmpty() || v.equals("-") ? 0 : Double.parseDouble(v); } catch (Exception e) { return 0; }
    }

    private int visibleRows() { return Math.max(4, (H - 30 - 30) / 18); }

    @Override
    protected void init() {
        clearWidgets();
        labels.clear();
        W = Math.min(width - 8, 360);
        H = Math.min(height - 8, 310);
        left = (width - W) / 2;
        top = (height - H) / 2;
        int vis = visibleRows();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - vis)));
        int y = top + 28;
        for (int i = 0; i < vis && i + scroll < rows.size(); i++, y += 18) {
            Object row = rows.get(i + scroll);
            if (row instanceof String sec) {
                labels.add(new Object[]{left + 8, y + 4, "§6§l" + WSLang.t(sec)});
                continue;
            }
            Field f = (Field) row;
            JsonObject h = holder(f);
            String leaf = f.path()[f.path().length - 1];
            labels.add(new Object[]{left + 16, y + 4, "§f" + WSLang.t(f.key())});
            if (f.kind() == 2) {
                boolean on = HordeJson.bool(h, leaf, true);
                addRenderableWidget(Button.builder(Component.literal(on ? "§a✔" : "§c✘"), b -> {
                    h.addProperty(leaf, !HordeJson.bool(h, leaf, true));
                    dirty = true;
                    init();
                }).bounds(left + W - 70, y, 50, 16).build());
            } else {
                double cur = HordeJson.dbl(h, leaf, 0);
                EditBox e = new EditBox(font, left + W - 90, y + 1, 70, 14, Component.empty());
                e.setMaxLength(10);
                e.setFilter(v -> v.isEmpty() || v.matches("-?\\d*[.,]?\\d*"));
                e.setValue(fmt(cur));
                e.setResponder(v -> {
                    double d = parse(v.replace(',', '.'));
                    if (f.kind() == 0) h.addProperty(leaf, (int) Math.round(d)); else h.addProperty(leaf, d);
                    dirty = true;
                });
                String tip = WSLang.t(f.key() + "_tip");
                if (!tip.equals(f.key() + "_tip")) e.setTooltip(Tooltip.create(Component.literal(tip)));
                addRenderableWidget(e);
            }
        }
        int by = top + H - 22;
        Button save = Button.builder(Component.literal(WSLang.t("blessed.save")), b -> {
            NetworkHandler.CHANNEL.sendToServer(new BlessingEditorPackets.SaveSettings(s.toString()));
            onClose();
        }).bounds(left + 8, by, 100, 16).build();
        save.active = dirty;
        addRenderableWidget(save);
        Button reset = Button.builder(Component.literal(WSLang.t("blessset.reset")), b -> {
            NetworkHandler.CHANNEL.sendToServer(new BlessingEditorPackets.SaveSettings(""));
            onClose();
        }).bounds(left + 112, by, 120, 16).build();
        reset.setTooltip(Tooltip.create(WSLang.c("blessset.reset_tip")));
        addRenderableWidget(reset);
        addRenderableWidget(Button.builder(Component.literal(WSLang.t("ui.fermer")), b -> onClose()).bounds(left + W - 68, by, 60, 16).build());
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int old = scroll;
        scroll = Math.max(0, scroll - (int) Math.signum(delta) * 2);
        if (scroll != old) init();
        return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(left, top, left + W, top + H, 0xEE101018);
        g.drawCenteredString(font, Component.literal("§l" + WSLang.t("blessset.title")), left + W / 2, top + 8, 0xFFFFFF);
        g.drawCenteredString(font, Component.literal("§8" + WSLang.t("blessset.hint")), left + W / 2, top + 19, 0xAAAAAA);
        for (Object[] l : labels) g.drawString(font, (String) l[2], (int) l[0], (int) l[1], 0xFFFFFF);
        super.render(g, mx, my, pt);
    }
}
