package com.wavesurvivor.client;

import com.google.gson.JsonElement;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PACKS DE BÉNÉDICTIONS (1.8, opérateurs) : exporter ce que TU as créé ou modifié (catégories, réglages, suppressions) dans un fichier
 * config/wavesurvivor/blessing_packs/&lt;nom&gt;.json — en cochant ce qui part dans le pack — ou importer un pack déposé dans ce dossier.
 * Sans « Remplacer », un conflit (même identifiant déjà à toi) est ignoré et signalé. Les bénédictions du mod non modifiées ne s'exportent pas
 * (tout le monde les a déjà).
 */
@OnlyIn(Dist.CLIENT)
public class BlessingPackScreen extends Screen {

    private final Screen parent;
    private final List<String> packs = new ArrayList<>();
    /** Ce qui peut être exporté : clé (identifiant, « @settings », « @deleted ») → libellé. */
    private final Map<String, String> items = new LinkedHashMap<>();
    private final Set<String> picked = new LinkedHashSet<>();
    private String name = "";
    private boolean overwrite = false;
    private int scroll = 0;
    private int left, top, W, H;
    private Button exportBtn, importBtn;

    public BlessingPackScreen(Screen parent, JsonObject data) {
        super(Component.literal(WSLang.t("blesspack.title")));
        this.parent = parent;
        for (JsonElement pe : HordeJson.arr(data, "packs")) if (pe.isJsonPrimitive()) packs.add(pe.getAsString());
        JsonObject sources = HordeJson.obj(data, "sources");
        for (JsonElement ce : HordeJson.arr(data, "categories")) {
            if (!ce.isJsonObject()) continue;
            JsonObject c = ce.getAsJsonObject();
            String id = HordeJson.str(c, "id", "");
            String src = HordeJson.str(sources, id, "BASE44");
            if (src.equals("CUSTOM")) items.put(id, "§f" + HordeJson.str(c, "name", id) + " §8" + WSLang.t("blesspack.tag_created"));
            else if (src.startsWith("OVERRIDE:") && !src.equals("OVERRIDE:0")) items.put(id, "§f" + HordeJson.str(c, "name", id) + " §8" + WSLang.t("blesspack.tag_modified", src.substring(9)));
        }
        if (HordeJson.bool(data, "settingsModified", false)) items.put("@settings", "§e⚙ " + WSLang.t("blesspack.item_settings"));
        int del = HordeJson.num(data, "deletedCount", 0);
        if (del > 0) items.put("@deleted", "§c🗑 " + WSLang.t("blesspack.item_deleted", del));
        picked.addAll(items.keySet());
    }

    private int visibleRows() { return Math.max(3, (H - 112 - 28) / 16); }

    @Override
    protected void init() {
        clearWidgets();
        labelRows.clear();
        W = Math.min(width - 8, 340);
        H = Math.min(height - 8, 300);
        left = (width - W) / 2;
        top = (height - H) / 2;
        EditBox box = new EditBox(font, left + 10, top + 44, W - 20, 16, Component.empty());
        box.setMaxLength(40);
        box.setFilter(s -> s.matches("[A-Za-z0-9_-]*"));
        box.setValue(name);
        box.setResponder(s -> { name = s; refreshButtons(); });
        box.setHint(Component.literal("§7" + WSLang.t("blesspack.name_hint")));
        addRenderableWidget(box);
        exportBtn = Button.builder(Component.literal("§a" + WSLang.t("blesspack.export")), b -> send(BlessingEditorPackets.PackAction.EXPORT))
                .bounds(left + 10, top + 66, 96, 18).tooltip(Tooltip.create(WSLang.c("blesspack.export_tip"))).build();
        addRenderableWidget(exportBtn);
        importBtn = Button.builder(Component.literal("§b" + WSLang.t("blesspack.import")), b -> send(overwrite ? BlessingEditorPackets.PackAction.IMPORT_OVERWRITE : BlessingEditorPackets.PackAction.IMPORT))
                .bounds(left + 110, top + 66, 96, 18).tooltip(Tooltip.create(WSLang.c("blesspack.import_tip"))).build();
        addRenderableWidget(importBtn);
        addRenderableWidget(Button.builder(Component.literal((overwrite ? "§e✔ " : "§7") + WSLang.t("blesspack.overwrite")), b -> { overwrite = !overwrite; init(); })
                .bounds(left + 210, top + 66, W - 220, 18).tooltip(Tooltip.create(WSLang.c("blesspack.overwrite_tip"))).build());
        refreshButtons();

        // Liste défilante : d'abord ce qui peut s'exporter (cases), puis les packs du dossier
        List<Object[]> rows = new ArrayList<>(); // {type, texte, clé}
        rows.add(new Object[]{"h", "§6" + WSLang.t("blesspack.what"), null});
        if (items.isEmpty()) rows.add(new Object[]{"t", "§7" + WSLang.t("blesspack.nothing"), null});
        for (Map.Entry<String, String> e : items.entrySet()) rows.add(new Object[]{"i", e.getValue(), e.getKey()});
        rows.add(new Object[]{"h", "§6" + WSLang.t(packs.isEmpty() ? "blesspack.none" : "blesspack.found"), null});
        for (String n : packs) rows.add(new Object[]{"p", "§f" + n, n});
        int vis = visibleRows();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - vis)));
        int y = top + 92;
        for (int i = 0; i < vis && i + scroll < rows.size(); i++, y += 16) {
            Object[] r = rows.get(i + scroll);
            String type = (String) r[0], text = (String) r[1], key = (String) r[2];
            switch (type) {
                case "h", "t" -> labelRows.add(new Object[]{left + 10, y + 3, text});
                case "i" -> addRenderableWidget(Button.builder(Component.literal((picked.contains(key) ? "§a✔ " : "§7☐ ") + text), b -> {
                    if (!picked.remove(key)) picked.add(key);
                    init();
                }).bounds(left + 10, y, W - 20, 14).build());
                default -> addRenderableWidget(Button.builder(Component.literal(text), b -> { name = key; init(); }).bounds(left + 10, y, W - 20, 14).build());
            }
        }
        addRenderableWidget(Button.builder(Component.literal(WSLang.t("ui.fermer")), b -> onClose()).bounds(left + W / 2 - 40, top + H - 22, 80, 16).build());
    }

    private final List<Object[]> labelRows = new ArrayList<>();

    private void refreshButtons() {
        boolean named = com.wavesurvivor.horde.benediction.CustomBlessingStore.validPackName(name);
        if (exportBtn != null) exportBtn.active = named && !picked.isEmpty();
        if (importBtn != null) importBtn.active = named;
    }

    private void send(byte action) {
        NetworkHandler.CHANNEL.sendToServer(new BlessingEditorPackets.PackAction(action, name, new ArrayList<>(picked)));
        onClose();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int old = scroll;
        scroll = Math.max(0, scroll - (int) Math.signum(delta));
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
        g.drawCenteredString(font, Component.literal("§l" + WSLang.t("blesspack.title")), left + W / 2, top + 8, 0xFFFFFF);
        g.drawCenteredString(font, Component.literal("§8" + WSLang.t("blesspack.hint")), left + W / 2, top + 22, 0xAAAAAA);
        g.drawString(font, "§6" + WSLang.t("blesspack.name"), left + 10, top + 34, 0xFFFFFF);
        for (Object[] l : labelRows) g.drawString(font, (String) l[2], (int) l[0], (int) l[1], 0xFFFFFF);
        super.render(g, mx, my, pt);
    }
}
