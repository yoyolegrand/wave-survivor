package com.wavesurvivor.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.wavesurvivor.horde.editor.HordeJson;
import com.wavesurvivor.network.HordeEditorPackets;
import com.wavesurvivor.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/** Éditeur de Hordes — liste : créer, éditer, copier (presse-papiers), coller en nouvelle horde, supprimer. */
@OnlyIn(Dist.CLIENT)
public class HordeEditorListScreen extends Screen {

    private static final int PANEL_W = 380;
    private static final int ROW_H = 24;

    final List<HordeEditorPackets.Entry> entries;
    final List<String> customEntities;
    final List<String> chests;
    /** Catalogue des compétences { skills, schema } (onglet Mobs du Horde Editor). */
    final com.google.gson.JsonObject skillCatalog;
    private int scroll = 0;
    private String pendingDelete = null;
    private String status = "";
    private int left, top, panelH, visible;

    public HordeEditorListScreen(HordeEditorPackets.OpenList pkt) {
        super(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.editeur_de_hordes_5153")));
        this.entries = pkt.entries;
        this.customEntities = pkt.customEntities;
        this.chests = pkt.chests;
        com.google.gson.JsonObject cat = com.wavesurvivor.horde.editor.HordeJson.parseObject(pkt.skillCatalog);
        this.skillCatalog = cat != null ? cat : new com.google.gson.JsonObject();
    }

    public void setStatus(String s) {
        status = s != null ? s : "";
    }

    public String status() {
        return status;
    }

    @Override
    protected void init() {
        clearWidgets();
        visible = Math.max(3, (height - 110) / ROW_H);
        panelH = 62 + visible * ROW_H + 10;
        left = (width - PANEL_W) / 2;
        top = Math.max(4, (height - panelH) / 2);

        // Nouvelle horde : choix d'un modèle de départ (Classique courte / longue, Kingdom, Vide)
        addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.nouvelle_horde_cc62")),
                b -> Minecraft.getInstance().setScreen(new HordeTemplateScreen(this)))
                .bounds(left + 8, top + 26, 120, 18).build());

        addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.coller_nouvelle_horde")), b -> pasteAsNew())
                .bounds(left + 132, top + 26, 108, 18).build());

        // Importer une horde reçue (.zip déposé dans config/wavesurvivor/import) : ouvre l'écran de /ws import
        addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("exchange.import")), b -> {
                    var p = Minecraft.getInstance().player;
                    if (p != null) p.connection.sendCommand("ws import");
                })
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(com.wavesurvivor.i18n.WSLang.c("exchange.import_tip")))
                .bounds(left + 244, top + 26, 64, 18).build());

        addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.fermer")), b -> onClose())
                .bounds(left + PANEL_W - 68, top + 26, 60, 18).build());

        addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.entites_competences_b16d")),
                        b -> NetworkHandler.CHANNEL.sendToServer(new com.wavesurvivor.network.EntityEditorPackets.Open()))
                .bounds(left + PANEL_W - 146, top + 4, 138, 18).build());

        for (int i = 0; i < visible && i + scroll < entries.size(); i++) {
            HordeEditorPackets.Entry e = entries.get(i + scroll);
            int y = top + 54 + i * ROW_H;
            int bx = left + PANEL_W - 8;
            boolean armed = e.name().equals(pendingDelete);
            bx -= 22;
            addRenderableWidget(Button.builder(Component.literal(armed ? "§c§l?" : "§c🗑"), b -> {
                if (e.name().equals(pendingDelete)) {
                    NetworkHandler.CHANNEL.sendToServer(new HordeEditorPackets.Delete(e.name()));
                    pendingDelete = null;
                } else {
                    pendingDelete = e.name();
                    status = com.wavesurvivor.i18n.WSLang.t("ui.reclique_sur_pour_confirmer_la_suppressi") + e.name() + " ».";
                }
                init();
            }).bounds(bx, y, 20, 20).build());
            bx -= 22;
            addRenderableWidget(Button.builder(Component.literal("📋"), b -> {
                NetworkHandler.CHANNEL.sendToServer(new HordeEditorPackets.Request(e.name(), HordeEditorPackets.PURPOSE_COPY));
            }).bounds(bx, y, 20, 20).build());
            bx -= 52;
            addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.editer")), b ->
                    NetworkHandler.CHANNEL.sendToServer(new HordeEditorPackets.Request(e.name(), HordeEditorPackets.PURPOSE_EDIT)))
                    .bounds(bx, y, 50, 20).build());
        }
    }

    String uniqueName(String base) {
        String n = base;
        int i = 2;
        while (exists(n)) n = base + " " + i++;
        return n;
    }

    private boolean exists(String n) {
        for (HordeEditorPackets.Entry e : entries) if (e.name().equalsIgnoreCase(n)) return true;
        return false;
    }

    private void pasteAsNew() {
        String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
        JsonObject o = HordeJson.parseObject(clip);
        if (!HordeJson.looksLikeHorde(o)) {
            status = HordeJson.looksLikeMob(o)
                    ? com.wavesurvivor.i18n.WSLang.t("ui.c_est_un_mob_ouvre_une_horde_et_colle_le")
                    : com.wavesurvivor.i18n.WSLang.t("ui.le_presse_papiers_ne_contient_pas_une_ho");
            return;
        }
        String base = HordeJson.str(o, "hordeName", com.wavesurvivor.i18n.WSLang.t("ui.horde"));
        o.addProperty("hordeName", uniqueName(base.isBlank() ? com.wavesurvivor.i18n.WSLang.t("ui.horde_collee") : base + com.wavesurvivor.i18n.WSLang.t("ui.copie")));
        o.remove("id");
        // Config d'autel embarquée par 📋 (clé "_altar")
        String altar = o.has("_altar") && o.get("_altar").isJsonObject() ? o.get("_altar").toString() : "";
        o.remove("_altar");
        Minecraft.getInstance().setScreen(new HordeEditorScreen(this, o, "", customEntities, altar));
    }

    /** Réponse du serveur à 📋 : JSON de la horde (+ config d'autel en "_altar") → presse-papiers. */
    public void onCopied(String name, String json, String altarJson) {
        JsonObject o = HordeJson.parseObject(json);
        JsonObject altar = HordeJson.parseObject(altarJson);
        if (o != null && altar != null) o.add("_altar", altar);
        String pretty = o != null ? new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(o) : json;
        Minecraft.getInstance().keyboardHandler.setClipboard(pretty);
        status = "§a📋 « " + name + com.wavesurvivor.i18n.WSLang.t("ui.copiee_dans_le_presse_papiers_coller_pou");
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int max = Math.max(0, entries.size() - visible);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        init();
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(left, top, left + PANEL_W, top + panelH, 0xF0101418);
        g.fill(left, top, left + PANEL_W, top + 2, 0xFFF59E0B);
        g.drawString(font, com.wavesurvivor.i18n.WSLang.t("ui.editeur_de_hordes"), left + 8, top + 9, 0xFFFFFFFF);

        for (int i = 0; i < visible && i + scroll < entries.size(); i++) {
            HordeEditorPackets.Entry e = entries.get(i + scroll);
            int y = top + 54 + i * ROW_H;
            g.fill(left + 6, y - 1, left + PANEL_W - 6, y + ROW_H - 3, 0x22FFFFFF);
            String badge = switch (e.source()) {
                case "CUSTOM" -> com.wavesurvivor.i18n.WSLang.t("§a[en jeu]");
                case "OVERRIDE" -> com.wavesurvivor.i18n.WSLang.t("§e[modifiée]");
                default -> com.wavesurvivor.i18n.WSLang.t("ui.base44_511e");
            };
            String name = e.name();
            if (font.width(name) > 150) name = font.plainSubstrByWidth(name, 144) + "…";
            g.drawString(font, "§f" + name + " " + badge, left + 12, y + 2, 0xFFFFFFFF);
            g.drawString(font, "§7" + e.waves() + com.wavesurvivor.i18n.WSLang.t("ui.vagues_b512") + e.mobs() + com.wavesurvivor.i18n.WSLang.t("ui.mobs") + e.specials() + com.wavesurvivor.i18n.WSLang.t("ui.speciales")
                    + e.bosses() + com.wavesurvivor.i18n.WSLang.t("ui.boss_3847"), left + 12, y + 12, 0xFFFFFFFF);
        }
        if (entries.size() > visible) {
            g.drawString(font, com.wavesurvivor.i18n.WSLang.t("ui.molette") + (scroll + 1) + "–" + Math.min(entries.size(), scroll + visible) + "/" + entries.size(),
                    left + 8, top + panelH - 12, 0xFFFFFFFF);
        }
        if (!status.isEmpty()) g.drawCenteredString(font, status, width / 2, top + panelH + 4, 0xFFFFFFFF);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
