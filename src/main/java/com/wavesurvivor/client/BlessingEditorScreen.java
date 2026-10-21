package com.wavesurvivor.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wavesurvivor.horde.benediction.BlessingCatalog;
import com.wavesurvivor.horde.benediction.BlessingEffects;
import com.wavesurvivor.horde.editor.HordeJson;
import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.network.BlessingEditorPackets;
import com.wavesurvivor.network.HordeEditorPackets;
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
import java.util.function.Consumer;

/**
 * ÉDITEUR DES BÉNÉDICTIONS (1.8, opérateurs). Les bénédictions sont groupées en CATÉGORIES (« Bénédiction de Vie »…) qui contiennent leurs
 * niveaux de rareté : on choisit une catégorie à gauche, puis on passe de Commun à Légendaire avec ◀ ▶ ; chaque niveau a sa case
 * « disponible » (ex. Longévité : Épique et Légendaire seulement), son soin et ses effets (n'importe quel attribut du jeu ou d'un mod,
 * via le sélecteur à recherche). Les modifications d'une catégorie du mod sont gardées champ par champ (couche du joueur) : ↺ rétablit.
 */
@OnlyIn(Dist.CLIENT)
public class BlessingEditorScreen extends Screen {

    private static final String[] R = BlessingCatalog.RARITIES;
    private static final String[] R_COLOR = {"§f", "§9", "§d", "§e"};
    private static final int[] R_RGB = {0xFFFFFF, 0x5555FF, 0xFF55FF, 0xFFFF55};
    private static final String[] FAMILIES = {"attack", "defense", "mobility", "utility", "team", "pact", "stats"};
    private static final String[] OPS = {"addition", "multiply_base", "multiply_total"};
    private static final String[] OP_LABEL = {"+", "×base", "×tot."};
    private static final int ROW_H = 12;

    private final Screen parent;
    private JsonObject data;
    private int sel = -1, listScroll = 0, modScroll = 0, tier = 0;
    private JsonObject edit;
    private String editOldId = "";
    private boolean dirty = false, confirmClose = false;
    private String pendingDelete = null, pendingRevert = null;
    private String status = "";
    private int left, top, W, H;
    private final List<Object[]> labels = new ArrayList<>();

    public BlessingEditorScreen(Screen parent, JsonObject data) {
        super(Component.literal(WSLang.t("blessed.title")));
        this.parent = parent;
        this.data = data;
    }

    // ─── Données ───

    private JsonArray cats() { return HordeJson.arr(data, "categories"); }

    private String idOf(JsonObject o) { return HordeJson.str(o, "id", ""); }

    private String sourceRaw(String id) { return HordeJson.str(HordeJson.obj(data, "sources"), id, "BASE44"); }

    private String sourceOf(String id) {
        String s = sourceRaw(id);
        return s.contains(":") ? s.substring(0, s.indexOf(':')) : s;
    }

    private int modified(String id) {
        String s = sourceRaw(id);
        try { return s.contains(":") ? Integer.parseInt(s.substring(s.indexOf(':') + 1)) : 0; } catch (NumberFormatException e) { return 0; }
    }

    private String sourceTag(String id) {
        if (id == null || id.isEmpty()) return WSLang.t("ui.nouveau");
        return switch (sourceOf(id)) {
            case "CUSTOM" -> WSLang.t("[en jeu]");
            case "OVERRIDE" -> modified(id) > 0 ? WSLang.t("cfg.badge_modified", modified(id)) : WSLang.t("[modifié]");
            default -> WSLang.t("cfg.badge_base");
        };
    }

    /** Réponse du serveur après enregistrement / suppression : données fraîches, sélection conservée par identifiant. */
    public void refresh(JsonObject fresh) {
        String keep = edit != null ? idOf(edit) : null;
        this.data = fresh;
        sel = -1;
        edit = null;
        editOldId = "";
        dirty = false;
        confirmClose = false;
        if (keep != null) {
            JsonArray a = cats();
            for (int i = 0; i < a.size(); i++) if (a.get(i).isJsonObject() && idOf(a.get(i).getAsJsonObject()).equalsIgnoreCase(keep)) { select(i); break; }
        }
        init();
    }

    public void onResult(HordeEditorPackets.Result r) {
        status = (r.ok ? "§a✔ " : "§c✘ ") + r.message;
    }

    private void select(int i) {
        JsonArray a = cats();
        if (i < 0 || i >= a.size() || !a.get(i).isJsonObject()) return;
        sel = i;
        edit = a.get(i).getAsJsonObject().deepCopy();
        editOldId = idOf(edit);
        dirty = false;
        confirmClose = false;
        modScroll = 0;
        pendingDelete = null;
        pendingRevert = null;
        tier = 0;
        for (int k = 0; k < R.length; k++) if (tierObj(k) != null) { tier = k; break; }
    }

    private void changed() { dirty = true; confirmClose = false; }

    private JsonObject tiers() { return HordeJson.obj(edit, "tiers"); }

    private JsonObject tierObj(int i) {
        return edit != null && tiers().has(R[i]) && tiers().get(R[i]).isJsonObject() ? tiers().getAsJsonObject(R[i]) : null;
    }

    private String rarityName(int i) { return WSLang.t("blessed.rarity." + R[i]); }

    private String uniqueId(String base) {
        String id = base;
        int n = 2;
        while (idExists(id)) id = base + "_" + n++;
        return id;
    }

    private boolean idExists(String id) {
        for (JsonElement e : cats()) if (e.isJsonObject() && idOf(e.getAsJsonObject()).equalsIgnoreCase(id)) return true;
        return false;
    }

    // ─── Widgets ───

    private void label(int x, int y, String t) { labels.add(new Object[]{x, y, t}); }

    private Button button(int x, int y, int w, String text, Runnable r) {
        return addRenderableWidget(Button.builder(Component.literal(text), b -> r.run()).bounds(x, y, w, 16).build());
    }

    private EditBox text(int x, int y, int w, String v, Consumer<String> set) {
        EditBox e = new EditBox(font, x, y, w, 14, Component.empty());
        e.setMaxLength(160);
        e.setValue(v != null ? v : "");
        e.setResponder(s -> { set.accept(s); changed(); });
        return addRenderableWidget(e);
    }

    private EditBox num(int x, int y, int w, String v, Consumer<String> set) {
        EditBox e = new EditBox(font, x, y, w, 14, Component.empty());
        e.setMaxLength(12);
        e.setFilter(s -> s.isEmpty() || s.matches("-?\\d*[.,]?\\d*"));
        e.setValue(v);
        e.setResponder(s -> { set.accept(s.replace(',', '.')); changed(); });
        return addRenderableWidget(e);
    }

    private static String fmt(double v) {
        return Math.abs(v - Math.round(v)) < 1e-9 ? String.valueOf((long) Math.round(v)) : String.valueOf(v);
    }

    private static double parse(String s) {
        try { return s.isEmpty() || s.equals("-") ? 0 : Double.parseDouble(s); } catch (Exception e) { return 0; }
    }

    private int rows() { return Math.max(3, (H - 26 - 24) / ROW_H); }

    @Override
    protected void init() {
        clearWidgets();
        labels.clear();
        W = Math.min(width - 8, 520);
        H = Math.min(height - 8, 310);
        left = (width - W) / 2;
        top = (height - H) / 2;

        // ── Boutons de la liste
        int by = top + H - 22;
        button(left + 6, by, 22, "§a+", this::createNew);
        button(left + 30, by, 40, WSLang.t("blessed.dupl"), () -> {
            if (edit == null) return;
            JsonObject c = edit.deepCopy();
            c.addProperty("id", uniqueId(idOf(edit) + "_COPIE"));
            c.addProperty("name", HordeJson.str(edit, "name", "") + " " + WSLang.t("blessed.copy_suffix"));
            edit = c;
            editOldId = "";
            sel = -1;
            dirty = true;
            status = WSLang.t("blessed.copy_made");
            init();
        }).active = edit != null;
        boolean armedDel = edit != null && !editOldId.isEmpty() && editOldId.equals(pendingDelete);
        button(left + 72, by, 22, armedDel ? "§c§l?" : "§c🗑", () -> {
            if (edit == null) return;
            if (editOldId.isEmpty()) { edit = null; sel = -1; init(); return; }
            if (editOldId.equals(pendingDelete)) {
                NetworkHandler.CHANNEL.sendToServer(new BlessingEditorPackets.Delete(editOldId));
                pendingDelete = null;
            } else {
                pendingDelete = editOldId;
                status = WSLang.t("ui.reclique_sur_pour_supprimer") + editOldId + " ».";
            }
            init();
        }).active = edit != null;
        boolean revertable = edit != null && !editOldId.isEmpty() && "OVERRIDE".equals(sourceOf(editOldId));
        boolean armedRev = revertable && editOldId.equals(pendingRevert);
        Button rb = button(left + 96, by, 22, armedRev ? "§e§l?" : "§e↺", () -> {
            if (!revertable) return;
            if (editOldId.equals(pendingRevert)) {
                NetworkHandler.CHANNEL.sendToServer(new BlessingEditorPackets.Revert(editOldId));
                pendingRevert = null;
            } else {
                pendingRevert = editOldId;
                status = WSLang.t("cfg.revert_confirm", editOldId);
            }
            init();
        });
        rb.active = revertable;
        rb.setTooltip(Tooltip.create(WSLang.c("cfg.revert_tip")));
        Button rd = button(left + 120, by, 36, "↺ ✖", () -> NetworkHandler.CHANNEL.sendToServer(new BlessingEditorPackets.Revert("")));
        rd.setTooltip(Tooltip.create(WSLang.c("blessed.restore_tip")));

        // ── Bas de la fiche : enregistrer / fermer
        int x0 = left + 162;
        int fw = left + W - 8 - x0;
        Button save = button(x0, by, 110, WSLang.t("blessed.save"), this::save);
        save.active = edit != null && dirty;
        button(x0 + 114, by, 60, WSLang.t("ui.fermer"), this::onClose);

        if (edit == null) {
            label(x0, top + 40, WSLang.t("blessed.hint_select"));
            label(x0, top + 54, WSLang.t("blessed.hint_tiers"));
            return;
        }
        buildCategory(x0, fw);
    }

    private void buildCategory(int x0, int fw) {
        JsonObject e = edit;
        int lw = 66;
        int y = top + 26;
        boolean existing = !editOldId.isEmpty();

        label(x0, y + 3, WSLang.t("blessed.id"));
        EditBox idBox = text(x0 + lw, y, 130, idOf(e), v -> e.addProperty("id", v.trim().toUpperCase().replace(' ', '_')));
        idBox.setEditable(!existing); // l'identifiant d'une catégorie existante ne change jamais (les joueurs retiennent leurs niveaux par identifiant)
        label(x0 + lw + 136, y + 3, "§8" + sourceTag(editOldId));
        y += 18;
        label(x0, y + 3, WSLang.t("blessed.name"));
        text(x0 + lw, y, Math.min(fw - lw, 260), HordeJson.str(e, "name", ""), v -> e.addProperty("name", v));
        y += 18;
        label(x0, y + 3, WSLang.t("blessed.icon"));
        text(x0 + lw, y, 40, HordeJson.str(e, "icon", ""), v -> e.addProperty("icon", v));
        String fam = HordeJson.str(e, "family", "utility");
        button(x0 + lw + 46, y - 1, 140, WSLang.t("blessed.family") + " " + WSLang.t("blessed.family." + fam), () -> {
            int i = 0;
            for (int k = 0; k < FAMILIES.length; k++) if (FAMILIES[k].equals(fam)) i = k;
            e.addProperty("family", FAMILIES[(i + 1) % FAMILIES.length]);
            changed();
            init();
        });
        y += 18;
        label(x0, y + 3, WSLang.t("blessed.desc"));
        text(x0 + lw, y, Math.min(fw - lw, 330), HordeJson.str(e, "description", ""), v -> e.addProperty("description", v));
        y += 24;

        // ── Sélecteur de niveau de rareté
        JsonObject t = tierObj(tier);
        button(x0, y - 1, 18, "◀", () -> { tier = (tier + R.length - 1) % R.length; modScroll = 0; init(); });
        label(x0 + 24, y + 3, R_COLOR[tier] + "§l" + rarityName(tier));
        button(x0 + 100, y - 1, 18, "▶", () -> { tier = (tier + 1) % R.length; modScroll = 0; init(); });
        boolean present = t != null;
        boolean enabled = present && HordeJson.bool(t, "enabled", true);
        String toggle = !present ? "§7✘ " + WSLang.t("blessed.tier_none") : enabled ? "§a✔ " + WSLang.t("blessed.tier_on") : "§c✘ " + WSLang.t("blessed.tier_off");
        button(x0 + 124, y - 1, 170, toggle, this::toggleTier);
        // Aperçu des niveaux disponibles
        StringBuilder dots = new StringBuilder();
        for (int k = 0; k < R.length; k++) {
            JsonObject tk = tierObj(k);
            boolean on = tk != null && HordeJson.bool(tk, "enabled", true);
            dots.append(on ? R_COLOR[k] + "■ " : "§8□ ");
        }
        label(x0 + 300, y + 3, dots.toString());
        y += 22;

        if (!present) {
            label(x0, y, WSLang.t("blessed.no_tier"));
            return;
        }

        label(x0, y + 3, WSLang.t("blessed.tier_name"));
        EditBox nb = text(x0 + lw + 20, y, Math.min(fw - lw - 20, 300), HordeJson.str(t, "name", ""), v -> { if (v.isBlank()) t.remove("name"); else t.addProperty("name", v); });
        nb.setHint(Component.literal("§7" + WSLang.t("blessed.auto")));
        y += 18;
        label(x0, y + 3, WSLang.t("blessed.heal"));
        num(x0 + lw + 20, y, 40, String.valueOf(HordeJson.num(t, "heal", 0)), v -> t.addProperty("heal", (int) Math.round(parse(v))));
        y += 22;

        // Effets spéciaux (Exécution, Bastion…) : type, description et paramètres modifiables
        label(x0, y + 3, "§b" + WSLang.t("blessed.fx.title"));
        List<String> fxTypes = new ArrayList<>(BlessingEffects.PARAMS.keySet());
        fxAddIdx = Math.max(0, Math.min(fxAddIdx, fxTypes.size() - 1));
        button(x0 + lw + 20, y - 1, 150, "§7" + WSLang.t("blessed.fx_type") + " §f" + WSLang.t("blessed.fxname." + fxTypes.get(fxAddIdx)), () -> { fxAddIdx = (fxAddIdx + 1) % fxTypes.size(); init(); })
                .setTooltip(Tooltip.create(Component.literal(WSLang.t("blessed.fxdesc." + fxTypes.get(fxAddIdx)))));
        button(x0 + lw + 174, y - 1, 70, "§a+ " + WSLang.t("blessed.fx_add"), () -> {
            JsonObject fx = new JsonObject();
            String ty = fxTypes.get(fxAddIdx);
            fx.addProperty("type", ty);
            for (BlessingEffects.ParamDef pd : BlessingEffects.PARAMS.get(ty)) fx.addProperty(pd.key(), pd.def());
            HordeJson.arr(t, "effects").add(fx);
            changed();
            init();
        });
        y += 20;
        JsonArray fxs = t.has("effects") && t.get("effects").isJsonArray() ? t.getAsJsonArray("effects") : new JsonArray();
        for (int fi = 0; fi < fxs.size(); fi++) {
            if (!fxs.get(fi).isJsonObject()) continue;
            final int fidx = fi;
            JsonObject fx = fxs.get(fi).getAsJsonObject();
            String ty = HordeJson.str(fx, "type", "?");
            boolean known = BlessingEffects.PARAMS.containsKey(ty);
            Button hb = button(x0, y - 1, 150, "§b" + (known ? WSLang.t("blessed.fxname." + ty) : ty), () -> {});
            if (known) hb.setTooltip(Tooltip.create(Component.literal(WSLang.t("blessed.fxdesc." + ty))));
            if (known) label(x0 + 156, y + 3, "§7" + WSLang.t("blessed.fxshort." + ty));
            button(x0 + 304, y - 1, 16, "§c✕", () -> { fxs.remove(fidx); if (fxs.size() == 0) t.remove("effects"); changed(); init(); });
            y += 18;
            int px = x0;
            if (known) for (BlessingEffects.ParamDef pd : BlessingEffects.PARAMS.get(ty)) {
                label(px, y + 3, "§8" + WSLang.t("blessed.fxs." + pd.key()));
                int lwid = font.width(WSLang.t("blessed.fxs." + pd.key())) + 4;
                EditBox pb = num(px + lwid, y, 44, fmt(HordeJson.dbl(fx, pd.key(), pd.def())), v -> fx.addProperty(pd.key(), parse(v)));
                pb.setTooltip(Tooltip.create(Component.literal(WSLang.t("blessed.fxp." + pd.key()))));
                px += lwid + 50;
            }
            y += 20;
        }
        y += 2;

        label(x0, y + 3, "§6" + WSLang.t("blessed.mods"));
        button(x0 + lw + 20, y - 1, 90, "§a+ " + WSLang.t("blessed.add_mod"), () -> {
            JsonObject m = new JsonObject();
            m.addProperty("attribute", "minecraft:generic.max_health");
            m.addProperty("amount", 1);
            m.addProperty("operation", "addition");
            HordeJson.arr(t, "modifiers").add(m);
            changed();
            init();
        });
        Button prev = button(x0 + lw + 114, y - 1, 170, WSLang.t("blessed.copy_prev"), () -> {
            JsonObject p = tierObj(tier - 1);
            if (p == null) return;
            t.add("modifiers", HordeJson.arr(p, "modifiers").deepCopy());
            t.addProperty("heal", HordeJson.num(p, "heal", 0));
            changed();
            init();
        });
        prev.active = tier > 0 && tierObj(tier - 1) != null;
        y += 20;

        JsonArray mods = HordeJson.arr(t, "modifiers");
        int rows = Math.max(1, (top + H - 30 - y) / 18);
        modScroll = Math.max(0, Math.min(modScroll, Math.max(0, mods.size() - rows)));
        for (int i = 0; i < rows && i + modScroll < mods.size(); i++) {
            final int idx = i + modScroll;
            if (!mods.get(idx).isJsonObject()) continue;
            JsonObject m = mods.get(idx).getAsJsonObject();
            int ry = y + i * 18;
            String attr = HordeJson.str(m, "attribute", "");
            EditBox ab = text(x0, ry, 150, attr, v -> m.addProperty("attribute", v.trim()));
            net.minecraft.world.entity.ai.attributes.Attribute a = BlessingCatalog.attribute(attr);
            ab.setTooltip(Tooltip.create(Component.literal(a != null ? Component.translatable(a.getDescriptionId()).getString() : WSLang.t("blessed.attr_unknown"))));
            button(x0 + 153, ry - 1, 24, "…", () ->
                    minecraft.setScreen(new AttributePickerScreen(this, attr, id -> { m.addProperty("attribute", id); changed(); })));
            num(x0 + 180, ry, 50, fmt(HordeJson.dbl(m, "amount", 0)), v -> m.addProperty("amount", parse(v)));
            String op = HordeJson.str(m, "operation", "addition");
            int oi = 0;
            for (int k = 0; k < OPS.length; k++) if (OPS[k].equals(op)) oi = k;
            final int fo = oi;
            Button ob = button(x0 + 234, ry - 1, 46, OP_LABEL[oi], () -> { m.addProperty("operation", OPS[(fo + 1) % OPS.length]); changed(); init(); });
            ob.setTooltip(Tooltip.create(WSLang.c("blessed.op_tip")));
            button(x0 + 284, ry - 1, 16, "§c✕", () -> { mods.remove(idx); changed(); init(); });
        }
        if (mods.size() > rows) {
            button(x0 + 304, y - 1, 14, "▲", () -> { modScroll = Math.max(0, modScroll - rows); init(); }).active = modScroll > 0;
            button(x0 + 304, y + (rows - 1) * 18 - 1, 14, "▼", () -> { modScroll += rows; init(); }).active = modScroll + rows < mods.size();
        }
        if (mods.size() == 0) label(x0, y + 3, "§8" + WSLang.t("blessed.no_mods"));
    }

    /** Type d'effet proposé par le bouton « Ajouter ». */
    private int fxAddIdx = 0;

    private void toggleTier() {
        JsonObject t = tierObj(tier);
        if (t == null) {
            // Création du niveau : copie du niveau précédent s'il existe, sinon un effet simple
            JsonObject nt = new JsonObject();
            nt.addProperty("enabled", true);
            JsonObject p = tier > 0 ? tierObj(tier - 1) : null;
            if (p != null) {
                nt.add("modifiers", HordeJson.arr(p, "modifiers").deepCopy());
                nt.addProperty("heal", HordeJson.num(p, "heal", 0));
            } else {
                JsonArray ms = new JsonArray();
                JsonObject m = new JsonObject();
                m.addProperty("attribute", "minecraft:generic.max_health");
                m.addProperty("amount", 1);
                m.addProperty("operation", "addition");
                ms.add(m);
                nt.add("modifiers", ms);
                nt.addProperty("heal", 0);
            }
            tiers().add(R[tier], nt);
        } else {
            t.addProperty("enabled", !HordeJson.bool(t, "enabled", true));
        }
        changed();
        init();
    }

    private void createNew() {
        JsonObject c = new JsonObject();
        c.addProperty("id", uniqueId("NOUVELLE"));
        c.addProperty("name", WSLang.t("blessed.new_name"));
        c.addProperty("icon", "✨");
        c.addProperty("description", "");
        c.addProperty("family", "utility");
        JsonObject ts = new JsonObject();
        JsonObject t = new JsonObject();
        t.addProperty("enabled", true);
        JsonArray ms = new JsonArray();
        JsonObject m = new JsonObject();
        m.addProperty("attribute", "minecraft:generic.max_health");
        m.addProperty("amount", 1);
        m.addProperty("operation", "addition");
        ms.add(m);
        t.add("modifiers", ms);
        t.addProperty("heal", 0);
        ts.add("COMMON", t);
        c.add("tiers", ts);
        edit = c;
        editOldId = "";
        sel = -1;
        tier = 0;
        dirty = true;
        status = WSLang.t("blessed.new_made");
        init();
    }

    private void save() {
        if (edit == null || !dirty) return;
        NetworkHandler.CHANNEL.sendToServer(new BlessingEditorPackets.Save(editOldId, edit.toString()));
        status = WSLang.t("blessed.sending");
    }

    // ─── Affichage ───

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(left, top, left + W, top + H, 0xF0101418);
        g.fill(left, top, left + W, top + 2, 0xFFF59E0B);
        g.drawString(font, "§e🙏 " + WSLang.t("blessed.title") + (dirty ? " §c●" : ""), left + 8, top + 8, 0xFFFFFF, false);
        // liste des catégories
        int lx = left + 6, ly = top + 26, lw = 150;
        int rows = rows();
        g.fill(lx - 1, ly - 1, lx + lw + 1, ly + rows * ROW_H + 1, 0x88000000);
        JsonArray a = cats();
        for (int r = 0; r < rows && r + listScroll < a.size(); r++) {
            if (!a.get(r + listScroll).isJsonObject()) continue;
            JsonObject c = a.get(r + listScroll).getAsJsonObject();
            int ry = ly + r * ROW_H;
            boolean cur = sel == r + listScroll || (edit != null && !editOldId.isEmpty() && idOf(c).equals(editOldId));
            if (cur) g.fill(lx, ry, lx + lw, ry + ROW_H, 0xFF3A3A55);
            else if (mx >= lx && mx < lx + lw && my >= ry && my < ry + ROW_H) g.fill(lx, ry, lx + lw, ry + ROW_H, 0x44FFFFFF);
            String src = sourceOf(idOf(c));
            String mark = src.equals("CUSTOM") ? "§a+ " : src.equals("OVERRIDE") ? "§e* " : "";
            String name = font.plainSubstrByWidth(WSLang.t(HordeJson.str(c, "name", idOf(c))), lw - 52);
            g.drawString(font, mark + "§f" + HordeJson.str(c, "icon", "") + " " + name, lx + 2, ry + 2, 0xFFFFFF, false);
            JsonObject ts = HordeJson.obj(c, "tiers");
            for (int k = 0; k < R.length; k++) {
                boolean on = ts.has(R[k]) && ts.get(R[k]).isJsonObject() && HordeJson.bool(ts.getAsJsonObject(R[k]), "enabled", true);
                int dx = lx + lw - 42 + k * 10;
                g.fill(dx, ry + 3, dx + 7, ry + 10, on ? 0xFF000000 | R_RGB[k] : 0xFF303030);
            }
        }
        if (a.size() > rows) g.drawString(font, "§8" + (listScroll + 1) + "–" + Math.min(a.size(), listScroll + rows) + "/" + a.size(), lx, ly + rows * ROW_H + 3, 0xFFFFFF, false);
        // séparateur de la fiche
        g.fill(left + 158, top + 24, left + 159, top + H - 26, 0x55FFFFFF);
        for (Object[] l : labels) g.drawString(font, (String) l[2], (int) l[0], (int) l[1], 0xFFFFFF, false);
        if (!status.isEmpty()) g.drawString(font, font.plainSubstrByWidth(status, W - 360), left + 162 + 180, top + H - 18, 0xFFFFFF, false);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int lx = left + 6, ly = top + 26, lw = 150;
        if (button == 0 && mx >= lx && mx < lx + lw && my >= ly) {
            int r = (int) ((my - ly) / ROW_H);
            int i = r + listScroll;
            if (r < rows() && i < cats().size()) {
                if (dirty && edit != null) status = WSLang.t("blessed.unsaved_switch");
                select(i);
                init();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx < left + 158) {
            int max = Math.max(0, cats().size() - rows());
            listScroll = Math.max(0, Math.min(max, listScroll - (int) Math.signum(delta)));
        } else if (edit != null) {
            modScroll = Math.max(0, modScroll - (int) Math.signum(delta));
            init();
        }
        return true;
    }

    @Override
    public void onClose() {
        if (dirty && !confirmClose) {
            confirmClose = true;
            status = WSLang.t("blessed.unsaved");
            return;
        }
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
