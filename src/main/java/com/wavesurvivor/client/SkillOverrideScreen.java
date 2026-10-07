package com.wavesurvivor.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.wavesurvivor.horde.editor.HordeJson;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * RÉGLAGES D'UNE COMPÉTENCE POUR UNE SEULE ENTITÉ.
 *   - chaque paramètre du type de compétence est listé ;
 *   - valeur jaune = réglage propre à l'entité (↺ pour revenir à la valeur de la compétence) ;
 *   - valeur grise = valeur de la compétence (modifier le champ crée le réglage propre) ;
 *   - stocké dans l'entité : skillOverrides[nomCompétence] = { champ : valeur }.
 */
public class SkillOverrideScreen extends Screen {

    private static final int ROW_H = 20;

    private final Screen parent;
    private final String skillName;
    private final JsonObject skillDef;
    private final JsonArray fields;
    private final JsonObject entity;
    private final Runnable onChanged;
    private int scroll = 0;
    private final List<Object[]> labels = new ArrayList<>(); // {x, y, texte}

    public SkillOverrideScreen(Screen parent, JsonObject entity, String skillName, JsonObject skillDef, JsonArray fields, Runnable onChanged) {
        super(Component.literal(WSLang.t("ui.skillov.title")));
        this.parent = parent;
        this.entity = entity;
        this.skillName = skillName;
        this.skillDef = skillDef;
        this.fields = fields;
        this.onChanged = onChanged;
    }

    /** Réglages propres de CETTE compétence (créés à la demande). */
    private JsonObject overrides(boolean create) {
        JsonObject all = entity.has("skillOverrides") && entity.get("skillOverrides").isJsonObject()
                ? entity.getAsJsonObject("skillOverrides") : null;
        if (all == null) {
            if (!create) return null;
            all = new JsonObject();
            entity.add("skillOverrides", all);
        }
        JsonObject ov = all.has(skillName) && all.get(skillName).isJsonObject() ? all.getAsJsonObject(skillName) : null;
        if (ov == null && create) {
            ov = new JsonObject();
            all.add(skillName, ov);
        }
        return ov;
    }

    /** Supprime les objets vides pour garder la config propre. */
    private void cleanup() {
        if (!entity.has("skillOverrides") || !entity.get("skillOverrides").isJsonObject()) return;
        JsonObject all = entity.getAsJsonObject("skillOverrides");
        if (all.has(skillName) && all.get(skillName).isJsonObject() && all.getAsJsonObject(skillName).size() == 0) all.remove(skillName);
        if (all.size() == 0) entity.remove("skillOverrides");
    }

    private JsonElement baseValue(JsonObject f) {
        String name = HordeJson.str(f, "name", "");
        return skillDef.has(name) ? skillDef.get(name) : f.get("def");
    }

    private int rows() { return Math.max(3, (height - 96) / ROW_H); }
    private int x0() { return width / 2 - 150; }

    @Override
    protected void init() {
        clearWidgets();
        labels.clear();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, fields.size() - rows())));
        String prefix = commonPrefix(fields);
        JsonObject ov = overrides(false);
        int x = x0(), y = 44;
        for (int i = 0; i < rows() && i + scroll < fields.size(); i++) {
            JsonObject f = fields.get(i + scroll).getAsJsonObject();
            String name = HordeJson.str(f, "name", ""), kind = HordeJson.str(f, "kind", "string");
            String shown = "unlockPhase".equals(name) ? WSLang.t("ui.skill.unlock_phase")
                    : name.startsWith(prefix) && name.length() > prefix.length() ? name.substring(prefix.length()) : name;
            boolean own = ov != null && ov.has(name);
            JsonElement val = own ? ov.get(name) : baseValue(f);
            int ry = y + i * ROW_H;
            labels.add(new Object[]{x, ry + 5, (own ? "§e" : "§7") + shown});
            if ("bool".equals(kind)) {
                boolean b = val != null && val.isJsonPrimitive() && val.getAsBoolean();
                addRenderableWidget(Button.builder(Component.literal((own ? "§e" : "§7") + (b ? WSLang.t("ui.oui_f7a6") : WSLang.t("ui.non_0835"))),
                        btn -> { overrides(true).addProperty(name, !b); onChanged.run(); init(); })
                        .bounds(x + 150, ry, 90, 18).build());
            } else {
                EditBox box = new EditBox(font, x + 150, ry + 1, 120, 16, Component.literal(shown));
                box.setMaxLength(2000);
                box.setValue(display(val, kind));
                box.setTextColor(own ? 0xFFFF55 : 0xAAAAAA);
                box.setResponder(v -> setValue(f, name, kind, v));
                addRenderableWidget(box);
            }
            if (own) {
                addRenderableWidget(Button.builder(Component.literal("↺"), btn -> {
                    JsonObject o = overrides(false);
                    if (o != null) o.remove(name);
                    cleanup();
                    onChanged.run();
                    init();
                }).bounds(x + 274, ry, 18, 18).build());
            }
        }
        if (fields.size() > rows()) {
            addRenderableWidget(Button.builder(Component.literal("▲"), b -> { scroll = Math.max(0, scroll - rows()); init(); })
                    .bounds(x + 298, y, 16, 16).build()).active = scroll > 0;
            addRenderableWidget(Button.builder(Component.literal("▼"), b -> { scroll += rows(); init(); })
                    .bounds(x + 298, y + (rows() - 1) * ROW_H, 16, 16).build()).active = scroll + rows() < fields.size();
        }
        addRenderableWidget(Button.builder(Component.literal("§c" + WSLang.t("ui.skillov.reset_all")), b -> {
            if (entity.has("skillOverrides") && entity.get("skillOverrides").isJsonObject()) entity.getAsJsonObject("skillOverrides").remove(skillName);
            cleanup();
            onChanged.run();
            init();
        }).bounds(width / 2 - 154, height - 28, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("§a" + WSLang.t("ui.skillov.done")), b -> onClose())
                .bounds(width / 2 + 4, height - 28, 150, 20).build());
    }

    private static String display(JsonElement v, String kind) {
        if (v == null || v.isJsonNull()) return "";
        if (!v.isJsonPrimitive()) return v.toString();
        if ("int".equals(kind) || "double".equals(kind)) {
            double d = v.getAsDouble();
            return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
        }
        return v.getAsString();
    }

    /** Écrit la valeur saisie dans les réglages propres (ou l'efface si elle redevient celle de la compétence). */
    private void setValue(JsonObject f, String name, String kind, String raw) {
        JsonElement parsed;
        try {
            switch (kind) {
                case "int" -> parsed = new JsonPrimitive(Math.round(Double.parseDouble(raw.trim().replace(',', '.'))));
                case "double" -> parsed = new JsonPrimitive(Double.parseDouble(raw.trim().replace(',', '.')));
                case "list" -> {
                    JsonElement el = JsonParser.parseString(raw);
                    if (!el.isJsonArray()) return;
                    parsed = el;
                }
                default -> parsed = new JsonPrimitive(raw);
            }
        } catch (Exception e) {
            return; // saisie en cours / invalide : on attend
        }
        JsonElement base = baseValue(f);
        boolean same = base != null && display(base, kind).equals(display(parsed, kind));
        if (same) {
            JsonObject o = overrides(false);
            if (o != null && o.has(name)) { o.remove(name); cleanup(); onChanged.run(); }
        } else {
            overrides(true).add(name, parsed);
            onChanged.run();
        }
    }

    private static String commonPrefix(JsonArray fields) {
        if (fields.size() < 2) return "";
        String p = null;
        for (JsonElement e : fields) {
            String n = HordeJson.str(e.getAsJsonObject(), "name", "");
            if ("unlockPhase".equals(n)) continue; // réglage commun, sans le préfixe du type
            if (p == null) { p = n; continue; }
            while (!p.isEmpty() && !n.startsWith(p)) p = p.substring(0, p.length() - 1);
        }
        if (p == null) return "";
        int cut = 0;
        for (int i = 0; i < p.length(); i++) if (Character.isUpperCase(p.charAt(i))) cut = i;
        return cut > 0 ? p.substring(0, cut) : p;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        String ent = HordeJson.str(entity, "displayName", HordeJson.str(entity, "entityName",
                HordeJson.str(entity, "custom_name", HordeJson.str(entity, "entity_type", "?"))));
        g.drawCenteredString(font, "§e" + WSLang.t("ui.skillov.title") + " §d" + skillName + " §7→ §f" + ent, width / 2, 8, 0xFFFFFF);
        // Ce que fait la compétence (type), pour savoir ce qu'on règle
        String td = CustomEntityEditorScreen.typeDesc(HordeJson.str(skillDef, "skillType", ""));
        if (!td.isEmpty()) {
            String line = "ℹ " + td;
            int maxW = Math.min(width - 20, 460);
            if (font.width(line) > maxW) line = font.plainSubstrByWidth(line, maxW - 6) + "…";
            g.drawCenteredString(font, "§7" + line, width / 2, 19, 0xFFFFFF);
        }
        g.drawCenteredString(font, WSLang.t("ui.skillov.hint"), width / 2, 30, 0xFFFFFF);
        for (Object[] l : labels) g.drawString(font, (String) l[2], (int) l[0], (int) l[1], 0xFFFFFF, false);
        super.render(g, mx, my, partial);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int old = scroll;
        scroll = Math.max(0, Math.min(scroll - (int) Math.signum(delta), Math.max(0, fields.size() - rows())));
        if (scroll != old) init();
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
