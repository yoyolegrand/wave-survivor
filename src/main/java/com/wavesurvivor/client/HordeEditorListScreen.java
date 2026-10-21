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
    /** Début de la liste (sous la barre d'onglets). */
    private static final int LIST_Y = 76;
    private static final String[] TAB_KEYS = {"waves", "kingdom", "invasion"};
    /** Onglet affiché (gardé d'une ouverture à l'autre). */
    private static String tabMode = "waves";

    /** Choisit l'onglet à afficher à la prochaine ouverture (après avoir enregistré une horde : son onglet). */
    public static void focusMode(String mode) {
        tabMode = mode != null && (mode.equals("kingdom") || mode.equals("invasion")) ? mode : "waves";
    }

    private static String modeOf(HordeEditorPackets.Entry e) {
        String m = e.mode() == null ? "waves" : e.mode().toLowerCase();
        return m.equals("kingdom") || m.equals("invasion") ? m : "waves";
    }

    final List<HordeEditorPackets.Entry> entries;
    final List<String> customEntities;
    final List<String> chests;
    /** Catalogue des compétences { skills, schema } (onglet Mobs du Horde Editor). */
    final com.google.gson.JsonObject skillCatalog;
    private int scroll = 0;
    private String pendingDelete = null;
    private String pendingRevert = null;
    private String status = "";
    private int left, top, panelH, visible;
    /** Hordes de l'onglet affiché. */
    private List<HordeEditorPackets.Entry> shown = new java.util.ArrayList<>();

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
        shown = new java.util.ArrayList<>();
        int[] counts = new int[TAB_KEYS.length];
        for (HordeEditorPackets.Entry e : entries) {
            String m = modeOf(e);
            for (int t = 0; t < TAB_KEYS.length; t++) if (TAB_KEYS[t].equals(m)) counts[t]++;
            if (m.equals(tabMode)) shown.add(e);
        }
        visible = Math.max(3, (height - 134) / ROW_H);
        panelH = LIST_Y + 8 + visible * ROW_H + 10;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, shown.size() - visible)));
        left = (width - PANEL_W) / 2;
        top = Math.max(4, (height - panelH) / 2);

        // Onglets : Hordes / Kingdom / Invasions (une horde enregistrée se range d'elle-même dans le bon onglet)
        String[] tabNames = {com.wavesurvivor.i18n.WSLang.t("cfg.tab_hordes"), com.wavesurvivor.i18n.WSLang.t("cfg.tab_kingdom"),
                com.wavesurvivor.i18n.WSLang.t("cfg.tab_invasions")};
        for (int t = 0; t < TAB_KEYS.length; t++) {
            final String key = TAB_KEYS[t];
            boolean sel = key.equals(tabMode);
            addRenderableWidget(Button.builder(Component.literal((sel ? "§e§l" : "§7") + tabNames[t] + " (" + counts[t] + ")"), b -> {
                tabMode = key;
                scroll = 0;
                pendingDelete = null;
                pendingRevert = null;
                init();
            }).bounds(left + 8 + t * 120, top + 50, 116, 18).build());
        }

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

        addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("blessed.btn")),
                        b -> NetworkHandler.CHANNEL.sendToServer(new com.wavesurvivor.network.BlessingEditorPackets.Open()))
                .bounds(left + PANEL_W - 146 - 104, top + 4, 98, 18).build());

        addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.entites_competences_b16d")),
                        b -> NetworkHandler.CHANNEL.sendToServer(new com.wavesurvivor.network.EntityEditorPackets.Open()))
                .bounds(left + PANEL_W - 146, top + 4, 138, 18).build());

        // Hordes du mod supprimées par le joueur : les rétablir (couches de configuration)
        addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("cfg.restore_deleted")),
                        b -> NetworkHandler.CHANNEL.sendToServer(new HordeEditorPackets.Revert("")))
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(com.wavesurvivor.i18n.WSLang.c("cfg.restore_deleted_tip")))
                .bounds(left + PANEL_W - 176, top + panelH - 17, 168, 14).build());

        for (int i = 0; i < visible && i + scroll < shown.size(); i++) {
            HordeEditorPackets.Entry e = shown.get(i + scroll);
            int y = top + LIST_Y + i * ROW_H;
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
            if (e.source().startsWith("OVERRIDE")) {
                bx -= 22;
                boolean rArmed = e.name().equals(pendingRevert);
                addRenderableWidget(Button.builder(Component.literal(rArmed ? "§e§l?" : "§e↺"), b -> {
                    if (e.name().equals(pendingRevert)) {
                        NetworkHandler.CHANNEL.sendToServer(new HordeEditorPackets.Revert(e.name()));
                        pendingRevert = null;
                    } else {
                        pendingRevert = e.name();
                        status = com.wavesurvivor.i18n.WSLang.t("cfg.revert_confirm", e.name());
                    }
                    init();
                }).tooltip(net.minecraft.client.gui.components.Tooltip.create(com.wavesurvivor.i18n.WSLang.c("cfg.revert_tip")))
                        .bounds(bx, y, 20, 20).build());
            }
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
        int max = Math.max(0, shown.size() - visible);
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

        if (shown.isEmpty()) g.drawCenteredString(font, com.wavesurvivor.i18n.WSLang.t("cfg.tab_empty"), left + PANEL_W / 2, top + LIST_Y + 12, 0xFF9CA3AF);
        for (int i = 0; i < visible && i + scroll < shown.size(); i++) {
            HordeEditorPackets.Entry e = shown.get(i + scroll);
            int y = top + LIST_Y + i * ROW_H;
            g.fill(left + 6, y - 1, left + PANEL_W - 6, y + ROW_H - 3, 0x22FFFFFF);
            String srcKey = e.source().contains(":") ? e.source().substring(0, e.source().indexOf(':')) : e.source();
            String badge = switch (srcKey) {
                case "CUSTOM" -> com.wavesurvivor.i18n.WSLang.t("§a[en jeu]");
                case "OVERRIDE" -> e.source().contains(":") && !e.source().endsWith(":0")
                        ? com.wavesurvivor.i18n.WSLang.t("cfg.badge_modified", e.source().substring(e.source().indexOf(':') + 1))
                        : com.wavesurvivor.i18n.WSLang.t("§e[modifiée]");
                default -> com.wavesurvivor.i18n.WSLang.t("cfg.badge_base");
            };
            String name = e.name();
            if (font.width(name) > 150) name = font.plainSubstrByWidth(name, 144) + "…";
            g.drawString(font, "§f" + name + " " + badge, left + 12, y + 2, 0xFFFFFFFF);
            g.drawString(font, "§7" + e.waves() + com.wavesurvivor.i18n.WSLang.t("ui.vagues_b512") + e.mobs() + com.wavesurvivor.i18n.WSLang.t("ui.mobs") + e.specials() + com.wavesurvivor.i18n.WSLang.t("ui.speciales")
                    + e.bosses() + com.wavesurvivor.i18n.WSLang.t("ui.boss_3847"), left + 12, y + 12, 0xFFFFFFFF);
        }
        if (shown.size() > visible) {
            g.drawString(font, com.wavesurvivor.i18n.WSLang.t("ui.molette") + (scroll + 1) + "–" + Math.min(shown.size(), scroll + visible) + "/" + shown.size(),
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
