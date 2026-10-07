package com.wavesurvivor.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.wavesurvivor.horde.editor.HordeJson;
import com.wavesurvivor.horde.loot.LootItems;
import com.wavesurvivor.network.EntityEditorPackets;
import com.wavesurvivor.network.HordeEditorPackets;
import com.wavesurvivor.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Éditeur d'ENTITÉS CUSTOM et de COMPÉTENCES (P5).
 * Stockage protégé (custom_entities.json). Les paramètres d'une compétence sont générés depuis
 * le schéma envoyé par le serveur (réflexion sur CustomSkillData) : chaque type affiche ses propres champs.
 */
@OnlyIn(Dist.CLIENT)
public class CustomEntityEditorScreen extends Screen {

    private static final String[] VANILLA = {
            "minecraft:zombie", "minecraft:husk", "minecraft:drowned", "minecraft:zombie_villager", "minecraft:skeleton",
            "minecraft:stray", "minecraft:wither_skeleton", "minecraft:spider", "minecraft:cave_spider", "minecraft:creeper",
            "minecraft:witch", "minecraft:pillager", "minecraft:vindicator", "minecraft:evoker", "minecraft:ravager",
            "minecraft:piglin_brute", "minecraft:piglin", "minecraft:blaze", "minecraft:phantom", "minecraft:vex",
            "minecraft:slime", "minecraft:magma_cube", "minecraft:enderman", "minecraft:iron_golem", "minecraft:warden",
            "wavesurvivor:diablotin", "wavesurvivor:eclat_neant"};
    private static final String[] EQUIP_SLOTS = {"mainhand", "offhand", "head", "chest", "legs", "feet"};
    private static final String[] EQUIP_LABELS = {"Main", "Main 2", "Tête", "Torse", "Jambes", "Pieds"};

    private final Screen parent;
    private JsonObject data;
    private int tab = 0; // 0 = entités, 1 = compétences
    private int sel = -1, listScroll = 0, paramScroll = 0, lootScroll = 0;
    private JsonObject edit;          // copie de travail de l'élément sélectionné
    private String editOldName = "";  // "" = nouveau
    private boolean dirty = false;
    private String pendingDelete = null;
    private String status = "";
    private int left, top, W, H;

    private final List<Object[]> labels = new ArrayList<>();
    private record IconSlot(int x, int y, Supplier<String> id, Consumer<String> set, Runnable clear) {}
    private final List<IconSlot> iconSlots = new ArrayList<>();
    private Consumer<String> picker = null;
    /** Compétence affichée dans le navigateur de la fiche entité. */
    private int selSkill = 0;

    /** Ouvre les réglages de la compétence `skillName` propres à l'entité `e`. */
    private void openSkillOverrides(JsonObject e, String skillName) {
        JsonObject def = null;
        for (JsonElement el : HordeJson.arr(data, "skills")) {
            if (el.isJsonObject() && skillName.equals(HordeJson.str(el.getAsJsonObject(), "skillName", ""))) def = el.getAsJsonObject();
        }
        if (def == null) return;
        String type = HordeJson.str(def, "skillType", "");
        JsonArray fields = schema().has(type) && schema().get(type).isJsonArray() ? schema().getAsJsonArray(type) : new JsonArray();
        minecraft.setScreen(new SkillOverrideScreen(this, e, skillName, def, fields, this::changed));
    }
    /** Mode « œuf » du sélecteur : reçoit l'objet cliqué. */
    private Consumer<net.minecraft.world.item.ItemStack> stackPicker = null;

    /** Sélecteur d'ŒUF : l'entité invoquée par l'œuf cliqué devient le type de base. */
    private void openEggPicker(Consumer<String> onEntity) {
        picker = id -> {};
        stackPicker = st -> {
            String ent = SpawnEggs.entityId(st);
            if (ent == null) { openEggPicker(onEntity); return; }
            onEntity.accept(ent);
            changed();
            init();
        };
    }

    public CustomEntityEditorScreen(Screen parent, JsonObject data) {
        super(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.entites_competences")));
        this.parent = parent;
        this.data = data;
        // Ouverture ciblée (bouton ✎ de l'éditeur de hordes) : sélectionne l'entité demandée
        String focus = HordeJson.str(data, "focus", "");
        if (!focus.isEmpty()) {
            JsonArray a = items();
            for (int i = 0; i < a.size(); i++) {
                if (a.get(i).isJsonObject() && nameOf(a.get(i).getAsJsonObject()).equalsIgnoreCase(focus)) {
                    select(i);
                    listScroll = Math.max(0, i - 3);
                    break;
                }
            }
        }
    }

    // ─── Données ───

    private JsonArray items() { return HordeJson.arr(data, tab == 0 ? "entities" : "skills"); }
    private String nameKey() { return tab == 0 ? "entityName" : "skillName"; }
    private JsonObject schema() { return HordeJson.obj(data, "schema"); }

    private String nameOf(JsonObject o) { return HordeJson.str(o, nameKey(), "?"); }

    private String sourceOf(String name) {
        JsonObject s = HordeJson.obj(data, tab == 0 ? "entitySources" : "skillSources");
        return HordeJson.str(s, name, com.wavesurvivor.i18n.WSLang.t("ui.base44"));
    }

    private List<String> skillNames() {
        List<String> l = new ArrayList<>();
        for (JsonElement e : HordeJson.arr(data, "skills")) if (e.isJsonObject()) l.add(HordeJson.str(e.getAsJsonObject(), "skillName", ""));
        l.sort(String.CASE_INSENSITIVE_ORDER);
        return l;
    }

    private List<String> skillTypes() { return new ArrayList<>(schema().keySet()); }

    /** Réponse du serveur après enregistrement / suppression : données fraîches, sélection conservée par nom. */
    public void refresh(JsonObject fresh) {
        String keep = edit != null ? nameOf(edit) : null;
        this.data = fresh;
        sel = -1;
        edit = null;
        editOldName = "";
        dirty = false;
        if (keep != null) {
            JsonArray a = items();
            for (int i = 0; i < a.size(); i++) {
                if (a.get(i).isJsonObject() && nameOf(a.get(i).getAsJsonObject()).equalsIgnoreCase(keep)) { select(i); break; }
            }
        }
        init();
    }

    public void onResult(HordeEditorPackets.Result r) {
        status = (r.ok ? "§a✔ " : "§c✘ ") + r.message;
    }

    private void select(int i) {
        JsonArray a = items();
        if (i < 0 || i >= a.size() || !a.get(i).isJsonObject()) return;
        sel = i;
        edit = a.get(i).getAsJsonObject().deepCopy();
        editOldName = nameOf(edit);
        dirty = false;
        paramScroll = 0;
        lootScroll = 0;
        pendingDelete = null;
    }

    private void changed() { dirty = true; }

    private String uniqueName(String base) {
        String n = base;
        int i = 2;
        while (exists(n)) n = base + "_" + i++;
        return n;
    }

    private boolean exists(String n) {
        for (JsonElement e : items()) if (e.isJsonObject() && nameOf(e.getAsJsonObject()).equalsIgnoreCase(n)) return true;
        return false;
    }

    // ─── Widgets ───

    @Override
    protected void init() {
        clearWidgets();
        labels.clear();
        iconSlots.clear();
        W = Math.min(width - 8, 520);
        H = Math.min(height - 8, 300);
        left = (width - W) / 2;
        top = (height - H) / 2;

        // Recherche dans la liste (nom, nom affiché, type de base, type de compétence)
        boolean searchFocus = searchBox != null && searchBox.isFocused();
        searchBox = new EditBox(font, left + 6, top + 46, 134, 14, net.minecraft.network.chat.Component.literal(""));
        searchBox.setMaxLength(64);
        searchBox.setValue(search);
        searchBox.setHint(net.minecraft.network.chat.Component.literal("§8🔍 " + com.wavesurvivor.i18n.WSLang.t("ui.search")));
        searchBox.setResponder(v -> { if (!v.equals(search)) { search = v; listScroll = 0; } });
        addRenderableWidget(searchBox);
        if (searchFocus) { setFocused(searchBox); searchBox.setFocused(true); }

        button(left + W - 170, top + 5, 90, com.wavesurvivor.i18n.WSLang.t("ui.enregistrer"), this::save).active = edit != null;
        button(left + W - 76, top + 5, 70, com.wavesurvivor.i18n.WSLang.t("ui.fermer"), this::onClose);
        button(left + 6, top + 24, 100, (tab == 0 ? "§e" : "§7") + com.wavesurvivor.i18n.WSLang.t("ui.entites"), () -> switchTab(0));
        button(left + 108, top + 24, 110, (tab == 1 ? "§e" : "§7") + com.wavesurvivor.i18n.WSLang.t("ui.competences_b0e8"), () -> switchTab(1));

        int by = top + H - 22;
        button(left + 6, by, 22, "§a+", this::createNew);
        button(left + 30, by, 22, "📋", () -> {
            if (edit == null) return;
            Minecraft.getInstance().keyboardHandler.setClipboard(new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(edit));
            status = "§a📋 « " + nameOf(edit) + com.wavesurvivor.i18n.WSLang.t("ui.copie_1616");
        }).active = edit != null;
        button(left + 54, by, 22, "📥", this::paste);
        button(left + 78, by, 22, edit != null && nameOf(edit).equals(pendingDelete) ? "§c§l?" : "§c🗑", () -> {
            if (edit == null) return;
            if (editOldName.isEmpty()) { edit = null; sel = -1; init(); return; }
            if (editOldName.equals(pendingDelete)) {
                NetworkHandler.CHANNEL.sendToServer(new EntityEditorPackets.Delete(tab == 1, editOldName));
                pendingDelete = null;
            } else {
                pendingDelete = editOldName;
                status = com.wavesurvivor.i18n.WSLang.t("ui.reclique_sur_pour_supprimer") + editOldName + " ».";
            }
            init();
        }).active = edit != null;
        button(left + 102, by, 38, com.wavesurvivor.i18n.WSLang.t("ui.dupl"), () -> {
            if (edit == null) return;
            JsonObject c = edit.deepCopy();
            c.addProperty(nameKey(), uniqueName(nameOf(edit) + "_copie"));
            c.remove("id");
            edit = c;
            editOldName = "";
            sel = -1;
            dirty = true;
            status = com.wavesurvivor.i18n.WSLang.t("ui.copie_creee_pense_a_enregistrer");
            init();
        }).active = edit != null;

        if (edit == null) {
            label(left + 150, top + 60, com.wavesurvivor.i18n.WSLang.t("ui.selectionne_un_element_a_gauche_ou_cree"));
            label(left + 150, top + 72, tab == 0 ? com.wavesurvivor.i18n.WSLang.t("ui.les_entites_custom_s_utilisent_dans_les")
                    : com.wavesurvivor.i18n.WSLang.t("ui.les_competences_s_ajoutent_aux_entites_c"));
            return;
        }
        if (tab == 0) buildEntity(); else buildSkill();
    }

    private void switchTab(int t) {
        if (tab == t) return;
        tab = t;
        sel = -1;
        edit = null;
        editOldName = "";
        listScroll = 0;
        search = "";
        init();
    }

    private void label(int x, int y, String t) { labels.add(new Object[]{x, y, t}); }

    private String search = "";
    private EditBox searchBox;

    /** Indices (dans items()) des éléments qui correspondent à la recherche. */
    private List<Integer> visible() {
        List<Integer> out = new ArrayList<>();
        JsonArray a = items();
        String q = search.trim().toLowerCase(java.util.Locale.ROOT);
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).isJsonObject()) continue;
            if (q.isEmpty()) { out.add(i); continue; }
            JsonObject o = a.get(i).getAsJsonObject();
            String hay = (nameOf(o) + " " + HordeJson.str(o, "displayName", "") + " " + HordeJson.str(o, "skillType", "") + " "
                    + HordeJson.str(o, "baseEntityType", "")).replaceAll("§.", "").toLowerCase(java.util.Locale.ROOT);
            if (hay.contains(q)) out.add(i);
        }
        return out;
    }

    /** Largeur de la colonne des libellés (s'adapte à la langue : jamais de texte qui mord sur les champs). */
    private int labelCol(String... keys) {
        int w = 60;
        for (String k : keys) w = Math.max(w, font.width(com.wavesurvivor.i18n.WSLang.t(k)) + 6);
        return w;
    }

    /** Ce que fait un type de compétence (texte traduit), ou « » s'il n'est pas décrit. */
    static String typeDesc(String type) {
        String k = "skilltype." + type + ".desc";
        String t = com.wavesurvivor.i18n.WSLang.t(k);
        return t.equals(k) ? "" : t;
    }

    /** Infobulle d'une compétence : nom, type, ce que fait le type, et sa description personnalisée. */
    private String skillTip(String name) {
        JsonObject sk = null;
        for (JsonElement e : HordeJson.arr(data, "skills")) {
            if (e.isJsonObject() && HordeJson.str(e.getAsJsonObject(), "skillName", "").equalsIgnoreCase(name)) { sk = e.getAsJsonObject(); break; }
        }
        if (sk == null) return "§d" + name;
        String type = HordeJson.str(sk, "skillType", "");
        String desc = HordeJson.str(sk, "description", "");
        StringBuilder sb = new StringBuilder("§d§l" + name + "\n§b" + typeLabel(type));
        String td = typeDesc(type);
        if (!td.isEmpty()) sb.append("\n§7").append(td);
        if (!desc.isBlank()) sb.append("\n§f« ").append(desc).append(" »");
        return sb.toString();
    }

    /** Coupe un texte en lignes de {@code width} pixels au plus. */
    private List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : text.split(" ")) {
            String test = line.length() == 0 ? w : line + " " + w;
            if (font.width(test) > width && line.length() > 0) { out.add(line.toString()); line = new StringBuilder(w); }
            else line = new StringBuilder(test);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    private Button button(int x, int y, int w, String text, Runnable r) {
        return addRenderableWidget(Button.builder(Component.literal(text), b -> r.run()).bounds(x, y, w, 16).build());
    }

    private EditBox text(int x, int y, int w, String v, Consumer<String> set) {
        EditBox e = new EditBox(font, x, y, w, 14, Component.empty());
        e.setMaxLength(512);
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

    // ─── Fiche entité ───

    private void buildEntity() {
        JsonObject e = edit;
        int x = left + 146, y = top + 46;
        int lcol = labelCol("ui.nom_interne", "ui.nom_affiche", "ui.type_de_base");
        int fw = Math.max(120, Math.min(260, left + W - 10 - (x + lcol)));
        label(x, y + 3, com.wavesurvivor.i18n.WSLang.t("ui.nom_interne"));
        text(x + lcol, y, 120, HordeJson.str(e, "entityName", ""), v -> e.addProperty("entityName", v.trim()));
        label(x + lcol + 126, y + 3, "§8" + sourceTag(editOldName));
        y += 18;
        label(x, y + 3, com.wavesurvivor.i18n.WSLang.t("ui.nom_affiche"));
        text(x + lcol, y, fw, HordeJson.str(e, "displayName", ""), v -> e.addProperty("displayName", v));
        y += 18;
        label(x, y + 3, com.wavesurvivor.i18n.WSLang.t("ui.type_de_base"));
        text(x + lcol, y, 130, HordeJson.str(e, "baseEntityType", ""), v -> e.addProperty("baseEntityType", v.trim()));
        button(x + lcol + 133, y - 1, 24, com.wavesurvivor.i18n.WSLang.t("ui.egg.btn"), () -> openEggPicker(id -> e.addProperty("baseEntityType", id)));
        button(x + lcol + 160, y - 1, 16, "◀", () -> cycleBase(-1));
        button(x + lcol + 178, y - 1, 16, "▶", () -> cycleBase(1));
        y += 18;
        JsonObject st = HordeJson.obj(e, "stats");
        String[][] stats = {{com.wavesurvivor.i18n.WSLang.t("ui.pv"), "maxHealth"}, {com.wavesurvivor.i18n.WSLang.t("ui.deg"), "attackDamage"}, {com.wavesurvivor.i18n.WSLang.t("ui.vit"), "movementSpeed"}, {com.wavesurvivor.i18n.WSLang.t("ui.portee"), "followRange"}, {com.wavesurvivor.i18n.WSLang.t("ui.recul"), "knockbackResistance"}};
        int sx = x;
        for (String[] s : stats) {
            label(sx, y + 3, s[0]);
            int lw = font.width(s[0]) + 3;
            num(sx + lw, y, 26, fmt(HordeJson.dbl(st, s[1], 0)), v -> st.addProperty(s[1], parse(v)));
            sx += lw + 30;
        }
        y += 18;
        boolean boss = HordeJson.bool(e, "isBoss", false);
        button(x, y - 1, 70, (boss ? "§c" : "§7") + com.wavesurvivor.i18n.WSLang.t("ui.boss_d908") + (boss ? com.wavesurvivor.i18n.WSLang.t("ui.oui") : com.wavesurvivor.i18n.WSLang.t("ui.non")), () -> { e.addProperty("isBoss", !boss); changed(); init(); });
        boolean prof = HordeJson.bool(e, "targetsAltar", false);
        button(x + 72, y - 1, 92, (prof ? "§4" : "§7") + com.wavesurvivor.i18n.WSLang.t("ui.profanateur") + (prof ? com.wavesurvivor.i18n.WSLang.t("ui.oui") : com.wavesurvivor.i18n.WSLang.t("ui.non")), () -> { e.addProperty("targetsAltar", !prof); changed(); init(); });
        JsonObject bc = HordeJson.obj(e, "bossConfig");
        boolean liche = HordeJson.bool(bc, "licheMechanics", false);
        button(x + 166, y - 1, 90, (liche ? "§5" : "§7") + com.wavesurvivor.i18n.WSLang.t("ui.liche") + (liche ? com.wavesurvivor.i18n.WSLang.t("ui.oui") : com.wavesurvivor.i18n.WSLang.t("ui.non")), () -> {
            if (liche) bc.remove("licheMechanics"); else bc.addProperty("licheMechanics", true);
            changed();
            init();
        });
        y += 18;
        // Lanceur de sorts : distance idéale (0 = mêlée normale)
        label(x, y + 3, com.wavesurvivor.i18n.WSLang.t("ui.garde_ses_distances"));
        int dx = x + font.width(com.wavesurvivor.i18n.WSLang.t("ui.garde_ses_distances")) + 4;
        num(dx, y, 30, fmt(HordeJson.dbl(e, "keepDistance", 0)), v -> e.addProperty("keepDistance", Math.max(0, parse(v))));
        label(dx + 34, y + 3, com.wavesurvivor.i18n.WSLang.t("ui.blocs_0_corps_a_corps_10_pour_un_mage"));
        y += 18;
        // Compétences : navigateur ◀ n/N ▶ (sans limite) · ✎ réglages propres à l'entité · ✖ retirer
        JsonArray sk = HordeJson.arr(e, "skills");
        label(x, y + 3, com.wavesurvivor.i18n.WSLang.t("ui.compet"));
        if (sk.isEmpty()) {
            label(x + 42, y + 3, com.wavesurvivor.i18n.WSLang.t("ui.skillov.none"));
        } else {
            selSkill = Math.max(0, Math.min(selSkill, sk.size() - 1));
            final int idx = selSkill;
            String n = sk.get(idx).getAsString();
            JsonObject ovAll = e.has("skillOverrides") && e.get("skillOverrides").isJsonObject() ? e.getAsJsonObject("skillOverrides") : null;
            boolean custom = ovAll != null && ovAll.has(n);
            button(x + 42, y - 1, 14, "◀", () -> { selSkill = (selSkill - 1 + sk.size()) % sk.size(); init(); });
            label(x + 60, y + 3, "§f" + (idx + 1) + "/" + sk.size());
            button(x + 84, y - 1, 14, "▶", () -> { selSkill = (selSkill + 1) % sk.size(); init(); });
            String shownName = font.width(n) > 96 ? font.plainSubstrByWidth(n, 90) + "…" : n;
            Button cur = button(x + 100, y - 1, 118, (custom ? "§e✎ " : "§d✎ ") + shownName, () -> openSkillOverrides(e, n));
            cur.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(skillTip(n))));
            button(x + 220, y - 1, 16, "§c✖", () -> {
                sk.remove(idx);
                if (e.has("skillOverrides") && e.get("skillOverrides").isJsonObject()) {
                    boolean still = false;
                    for (JsonElement el : sk) if (el.getAsString().equals(n)) still = true;
                    if (!still) e.getAsJsonObject("skillOverrides").remove(n);
                }
                selSkill = Math.max(0, idx - 1);
                changed();
                init();
            });
        }
        y += 17;
        List<String> names = skillNames();
        if (!names.isEmpty()) {
            // Menu déroulant (recherche + description) : double-clic pour ajouter la compétence à l'entité
            button(x + 42, y - 1, 162, com.wavesurvivor.i18n.WSLang.t("ui.skill.add_pick"), () -> openSkillPicker(sk));
        }
        // Ce que fait la compétence sélectionnée (détail complet au survol des boutons)
        if (!sk.isEmpty()) {
            JsonObject skd = null;
            String sn = sk.get(Math.max(0, Math.min(selSkill, sk.size() - 1))).getAsString();
            for (JsonElement el : HordeJson.arr(data, "skills")) {
                if (el.isJsonObject() && HordeJson.str(el.getAsJsonObject(), "skillName", "").equalsIgnoreCase(sn)) { skd = el.getAsJsonObject(); break; }
            }
            String td = skd == null ? "" : typeDesc(HordeJson.str(skd, "skillType", ""));
            if (!td.isEmpty()) {
                y += 17;
                String line = "ℹ " + td;
                int maxW = left + W - 10 - x;
                if (font.width(line) > maxW) line = font.plainSubstrByWidth(line, maxW - 6) + "…";
                label(x, y, "§8" + line);
                y -= 6;
            }
        }
        y += 20;
        // Équipement (format entité custom : slot → "id")
        label(x, y, com.wavesurvivor.i18n.WSLang.t("ui.equipement_clic_choisir_clic_droit_vider"));
        JsonObject eq = HordeJson.obj(e, "equipment");
        int step = 40;
        for (String lab : EQUIP_LABELS) step = Math.max(step, font.width(com.wavesurvivor.i18n.WSLang.t(lab)) + 8);
        int ox = Math.max(0, step / 2 - 9); // décalage : le libellé centré de la 1re case ne déborde pas à gauche
        for (int s = 0; s < 6; s++) {
            final String slot = EQUIP_SLOTS[s];
            int ex = x + ox + s * step;
            iconSlots.add(new IconSlot(ex, y + 10, () -> HordeJson.str(eq, slot, ""), v -> eq.addProperty(slot, v),
                    () -> eq.addProperty(slot, "")));
            String lab = com.wavesurvivor.i18n.WSLang.t(EQUIP_LABELS[s]);
            label(ex + 9 - font.width(lab) / 2, y + 30, "§8" + lab); // nom de l'emplacement centré SOUS la case
        }
        y += 42;
        // Loot
        label(x, y + 2, com.wavesurvivor.i18n.WSLang.t("ui.loot_min_max"));
        JsonArray loot = HordeJson.arr(e, "lootTable");
        button(x + 200, y - 1, 56, com.wavesurvivor.i18n.WSLang.t("ui.loot_ab8d"), () -> picker = id -> {
            JsonObject l = new JsonObject();
            l.addProperty("lootType", "normal");
            l.addProperty("item", id);
            l.addProperty("minQty", 1);
            l.addProperty("maxQty", 1);
            l.addProperty("chance", 50);
            loot.add(l);
            changed();
            init();
        });
        int rows = Math.max(1, (top + H - 26 - (y + 14)) / 17);
        lootScroll = Math.max(0, Math.min(lootScroll, Math.max(0, loot.size() - rows)));
        for (int i = 0; i < rows && i + lootScroll < loot.size(); i++) {
            final int idx = i + lootScroll;
            if (!loot.get(idx).isJsonObject()) continue;
            JsonObject l = loot.get(idx).getAsJsonObject();
            int ly = y + 14 + i * 17;
            iconSlots.add(new IconSlot(x, ly - 2, () -> HordeJson.str(l, "item", ""), v -> l.addProperty("item", v), null));
            num(x + 110, ly, 22, String.valueOf(HordeJson.num(l, "minQty", 1)), v -> l.addProperty("minQty", (int) parse(v)));
            num(x + 136, ly, 22, String.valueOf(HordeJson.num(l, "maxQty", 1)), v -> l.addProperty("maxQty", (int) parse(v)));
            num(x + 162, ly, 30, fmt(HordeJson.dbl(l, "chance", 50)), v -> l.addProperty("chance", Math.min(100, parse(v))));
            button(x + 196, ly - 1, 16, "§c✖", () -> { loot.remove(idx); changed(); init(); });
        }
        if (loot.size() > rows) {
            // Flèches de défilement sur la ligne de titre (côte à côte : jamais superposées, même avec une seule ligne visible)
            button(x + 156, y - 1, 16, "▲", () -> { lootScroll = Math.max(0, lootScroll - 1); init(); }).active = lootScroll > 0;
            button(x + 174, y - 1, 16, "▼", () -> { lootScroll++; init(); }).active = lootScroll + rows < loot.size();
            label(x + 262, y + 2, "§8" + (lootScroll + 1) + "-" + Math.min(loot.size(), lootScroll + rows) + "/" + loot.size());
        }
    }

    private int addSkillIdx = 0;

    /** Menu déroulant des compétences (recherche, type, description) : double-clic pour ajouter à l'entité. */
    private void openSkillPicker(JsonArray sk) {
        List<String[]> entries = new ArrayList<>();
        for (JsonElement el : HordeJson.arr(data, "skills")) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            String n = HordeJson.str(o, "skillName", "");
            if (n.isEmpty()) continue;
            boolean has = false;
            for (JsonElement x : sk) if (x.getAsString().equalsIgnoreCase(n)) has = true;
            String type = typeLabel(HordeJson.str(o, "skillType", "")).replaceAll("§.", "");
            entries.add(new String[]{n, has ? "§8" + n + " ✔" : "§d" + n, type});
        }
        entries.sort((a, b) -> a[0].compareToIgnoreCase(b[0]));
        minecraft.setScreen(new ListPickerScreen(this, com.wavesurvivor.i18n.WSLang.t("ui.skill.pick_title"), entries, "", n -> {
            boolean has = false;
            for (JsonElement x : sk) if (x.getAsString().equalsIgnoreCase(n)) has = true;
            if (!has) {
                sk.add(n);
                selSkill = sk.size() - 1;
                changed();
                status = com.wavesurvivor.i18n.WSLang.t("ui.skill.added", n);
            } else {
                status = com.wavesurvivor.i18n.WSLang.t("ui.skill.already", n);
            }
        }, true, this::skillTip));
    }

    private void cycleBase(int dir) {
        List<String> l = List.of(VANILLA);
        String cur = HordeJson.str(edit, "baseEntityType", "");
        int i = l.indexOf(cur);
        i = i < 0 ? 0 : ((i + dir) % l.size() + l.size()) % l.size();
        edit.addProperty("baseEntityType", l.get(i));
        changed();
        init();
    }

    private String sourceTag(String name) {
        if (name == null || name.isEmpty()) return com.wavesurvivor.i18n.WSLang.t("ui.nouveau");
        return switch (sourceOf(name)) { case "CUSTOM" -> com.wavesurvivor.i18n.WSLang.t("[en jeu]"); case "OVERRIDE" -> com.wavesurvivor.i18n.WSLang.t("[modifié]"); default -> com.wavesurvivor.i18n.WSLang.t("[base44]"); };
    }

    // ─── Fiche compétence ───

    private void buildSkill() {
        JsonObject s = edit;
        int x = left + 146, y = top + 46;
        int lw = labelCol("ui.nom_interne", "ui.type_a1fa", "ui.description");
        int fw = Math.max(120, Math.min(260, left + W - 10 - (x + lw)));
        label(x, y + 3, com.wavesurvivor.i18n.WSLang.t("ui.nom_interne"));
        text(x + lw, y, 120, HordeJson.str(s, "skillName", ""), v -> s.addProperty("skillName", v.trim()));
        label(x + lw + 126, y + 3, "§8" + sourceTag(editOldName));
        y += 18;
        String type = HordeJson.str(s, "skillType", "");
        label(x, y + 3, com.wavesurvivor.i18n.WSLang.t("ui.type_a1fa"));
        button(x + lw, y - 1, 152, "§b▼ " + (type.isEmpty() ? "?" : typeLabel(type)), this::openTypePicker);
        y += 18;
        // Ce que fait ce type de compétence (en clair, pour comprendre la différence entre deux compétences)
        String td = typeDesc(type);
        if (!td.isEmpty()) {
            List<String> lines = wrap("ℹ " + td, left + W - 10 - x);
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                String ln = lines.get(i);
                if (i == 1 && lines.size() > 2) ln = font.plainSubstrByWidth(ln, left + W - 22 - x) + "…";
                label(x, y + i * 10, "§7" + ln);
            }
            y += Math.min(2, lines.size()) * 10 + 4;
        }
        label(x, y + 3, com.wavesurvivor.i18n.WSLang.t("ui.description"));
        text(x + lw, y, fw, HordeJson.str(s, "description", ""), v -> s.addProperty("description", v));
        y += 20;

        JsonArray fields = schema().has(type) && schema().get(type).isJsonArray() ? schema().getAsJsonArray(type) : new JsonArray();
        label(x, y, com.wavesurvivor.i18n.WSLang.t("ui.parametres") + fields.size() + com.wavesurvivor.i18n.WSLang.t("ui.listes_en_json_a_b"));
        y += 12;
        int rows = Math.max(1, (top + H - 26 - y) / 16);
        paramScroll = Math.max(0, Math.min(paramScroll, Math.max(0, fields.size() - rows)));
        String prefix = commonPrefix(fields);
        for (int i = 0; i < rows && i + paramScroll < fields.size(); i++) {
            JsonObject f = fields.get(i + paramScroll).getAsJsonObject();
            String name = HordeJson.str(f, "name", ""), kind = HordeJson.str(f, "kind", "string");
            JsonElement def = f.get("def");
            int ry = y + i * 16;
            String shown = "unlockPhase".equals(name) ? com.wavesurvivor.i18n.WSLang.t("ui.skill.unlock_phase")
                    : name.startsWith(prefix) && name.length() > prefix.length() ? name.substring(prefix.length()) : name;
            label(x, ry + 3, "§f" + shown);
            JsonElement cur = s.has(name) ? s.get(name) : def;
            switch (kind) {
                case "bool" -> {
                    boolean b = cur != null && cur.isJsonPrimitive() && cur.getAsBoolean();
                    button(x + 120, ry - 1, 60, b ? com.wavesurvivor.i18n.WSLang.t("ui.oui_f7a6") : com.wavesurvivor.i18n.WSLang.t("ui.non_0835"), () -> { s.addProperty(name, !b); changed(); init(); });
                }
                case "int", "double" -> num(x + 120, ry, 60, cur != null && cur.isJsonPrimitive() ? fmt(cur.getAsDouble()) : "0", v -> {
                    double d = parse(v);
                    if (kind.equals("int")) s.addProperty(name, (long) Math.round(d)); else s.addProperty(name, d);
                });
                case "list" -> text(x + 120, ry, 136, cur != null ? cur.toString() : "[]", v -> {
                    try {
                        JsonElement el = JsonParser.parseString(v);
                        if (el.isJsonArray()) s.add(name, el);
                    } catch (Exception ignored) {}
                });
                default -> {
                    String curStr = cur != null && cur.isJsonPrimitive() ? cur.getAsString() : "";
                    if ("ironsSpellId".equals(name)) {
                        // Sort Iron's : liste déroulante avec recherche (Iron's + addons)
                        text(x + 120, ry, 94, curStr, v -> s.addProperty(name, v.trim()));
                        button(x + 217, ry - 1, 40, com.wavesurvivor.i18n.WSLang.t("ui.spell.pick"), () ->
                                minecraft.setScreen(new SpellPickerScreen(this, curStr, id -> { s.addProperty(name, id); changed(); })));
                    } else {
                        text(x + 120, ry, 136, curStr, v -> s.addProperty(name, v));
                    }
                }
            }
        }
        if (fields.size() > rows) {
            button(x + 262, y, 14, "▲", () -> { paramScroll = Math.max(0, paramScroll - rows); init(); }).active = paramScroll > 0;
            button(x + 262, y + (rows - 1) * 16, 14, "▼", () -> { paramScroll += rows; init(); }).active = paramScroll + rows < fields.size();
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
        // coupe à la dernière majuscule pour garder des noms lisibles (webTrapRange → Range)
        int cut = 0;
        for (int i = 0; i < p.length(); i++) if (Character.isUpperCase(p.charAt(i))) cut = i;
        return cut > 0 ? p.substring(0, cut) : p;
    }

    /** Nom lisible d'un type de compétence (traduit), ou l'identifiant brut. */
    static String typeLabel(String type) {
        String key = "skilltype." + type;
        String t = com.wavesurvivor.i18n.WSLang.t(key);
        return t.equals(key) ? type : t;
    }

    /** Menu déroulant (avec recherche) des types de compétences. */
    private void openTypePicker() {
        List<String[]> entries = new ArrayList<>();
        for (String t : skillTypes()) entries.add(new String[]{t, typeLabel(t)});
        entries.sort((a, b) -> a[1].compareToIgnoreCase(b[1]));
        minecraft.setScreen(new ListPickerScreen(this, com.wavesurvivor.i18n.WSLang.t("ui.list.skilltype"), entries,
                HordeJson.str(edit, "skillType", ""), this::setType));
    }

    /** Change le type de la compétence en cours d'édition (paramètres par défaut ajoutés). */
    private void setType(String t) {
        if (edit == null || t == null || t.isEmpty()) return;
        edit.addProperty("skillType", t);
        fillDefaults(edit, t);
        paramScroll = 0;
        changed();
    }

    private void cycleType(int dir) {
        List<String> types = skillTypes();
        if (types.isEmpty()) return;
        String cur = HordeJson.str(edit, "skillType", "");
        int i = types.indexOf(cur);
        i = i < 0 ? 0 : ((i + dir) % types.size() + types.size()) % types.size();
        String t = types.get(i);
        edit.addProperty("skillType", t);
        fillDefaults(edit, t);
        paramScroll = 0;
        changed();
        init();
    }

    /** Ajoute les paramètres du type absents de la compétence (valeurs par défaut du schéma). */
    private void fillDefaults(JsonObject s, String type) {
        if (!schema().has(type)) return;
        for (JsonElement e : schema().getAsJsonArray(type)) {
            JsonObject f = e.getAsJsonObject();
            String n = HordeJson.str(f, "name", "");
            if (!n.isEmpty() && !s.has(n) && f.has("def")) s.add(n, f.get("def").deepCopy());
        }
    }

    // ─── Création / collage ───

    private void createNew() {
        JsonObject o = new JsonObject();
        if (tab == 0) {
            o.addProperty("entityName", uniqueName("nouvelle_entite"));
            o.addProperty("displayName", com.wavesurvivor.i18n.WSLang.t("ui.nouvelle_entite"));
            o.addProperty("baseEntityType", "minecraft:zombie");
            JsonObject st = new JsonObject();
            st.addProperty("maxHealth", 30);
            st.addProperty("attackDamage", 4);
            st.addProperty("movementSpeed", 0.25);
            st.addProperty("followRange", 64);
            st.addProperty("knockbackResistance", 0.2);
            o.add("stats", st);
            o.add("equipment", new JsonObject());
            o.add("lootTable", new JsonArray());
            JsonObject mount = new JsonObject();
            mount.addProperty("enabled", false);
            o.add("mountEntity", mount);
            o.addProperty("isBoss", false);
            o.add("bossConfig", new JsonObject());
            o.add("skills", new JsonArray());
        } else {
            o.addProperty("skillName", uniqueName("nouvelle_competence"));
            o.addProperty("skillType", "gatling");
            o.addProperty("description", "");
            o.addProperty("cooldown", 5);
            fillDefaults(o, "gatling");
        }
        edit = o;
        editOldName = "";
        sel = -1;
        dirty = true;
        init();
    }

    private void paste() {
        JsonObject o = HordeJson.parseObject(Minecraft.getInstance().keyboardHandler.getClipboard());
        boolean ok = o != null && (tab == 0 ? o.has("baseEntityType") || o.has("entityName") : o.has("skillType"));
        if (!ok) {
            status = com.wavesurvivor.i18n.WSLang.t("ui.le_presse_papiers_ne_contient_pas") + (tab == 0 ? com.wavesurvivor.i18n.WSLang.t("ui.d_entite_custom") : com.wavesurvivor.i18n.WSLang.t("ui.de_competence"));
            return;
        }
        o.addProperty(nameKey(), uniqueName(HordeJson.str(o, nameKey(), tab == 0 ? "entite" : "competence")));
        o.remove("id");
        edit = o;
        editOldName = "";
        sel = -1;
        dirty = true;
        status = com.wavesurvivor.i18n.WSLang.t("ui.colle_pense_a_enregistrer");
        init();
    }

    private void save() {
        if (edit == null) return;
        status = com.wavesurvivor.i18n.WSLang.t("ui.enregistrement");
        NetworkHandler.CHANNEL.sendToServer(new EntityEditorPackets.Save(tab == 1, editOldName,
                new GsonBuilder().disableHtmlEscaping().create().toJson(edit)));
    }

    // ─── Souris ───

    private int listRows() { return Math.max(3, (H - 64 - 50) / 12); } // place pour la légende (2 lignes) et les boutons

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (picker != null) {
            int slot = pickerSlotAt(mx, my);
            var inv = inv();
            Consumer<String> cb = picker;
            Consumer<net.minecraft.world.item.ItemStack> sc = stackPicker;
            picker = null;
            stackPicker = null;
            if (slot >= 0 && slot < inv.size() && !inv.get(slot).isEmpty()) {
                if (sc != null) sc.accept(inv.get(slot));
                else cb.accept(idOf(inv.get(slot)));
            }
            return true;
        }
        int lx = left + 6, ly = top + 64;
        if (mx >= lx && mx < lx + 134 && my >= ly && my < ly + listRows() * 12) {
            int i = (int) ((my - ly) / 12) + listScroll;
            List<Integer> vis = visible();
            if (i < vis.size()) {
                if (dirty && edit != null) status = com.wavesurvivor.i18n.WSLang.t("ui.modifications_non_enregistrees_abandonne");
                select(vis.get(i));
                init();
            }
            return true;
        }
        for (IconSlot s : iconSlots) {
            if (mx >= s.x() && mx < s.x() + 18 && my >= s.y() && my < s.y() + 18) {
                if (button == 1) { if (s.clear() != null) { s.clear().run(); changed(); } }
                else picker = id -> { s.set().accept(id); changed(); init(); };
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx < left + 142) {
            int max = Math.max(0, visible().size() - listRows());
            listScroll = Math.max(0, Math.min(max, listScroll - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (picker != null && key == 256) { picker = null; stackPicker = null; return true; }
        return super.keyPressed(key, scan, mods);
    }

    // ─── Rendu ───

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(left, top, left + W, top + H, 0xF0100E18);
        g.fill(left, top, left + W, top + 2, 0xFFD946EF);
        g.drawString(font, com.wavesurvivor.i18n.WSLang.t("ui.entites_competences_2cd0") + (dirty ? " §e●" : ""), left + 8, top + 9, 0xFFFFFFFF);

        // Liste
        int lx = left + 6, ly = top + 64, rows = listRows();
        JsonArray a = items();
        List<Integer> vis = visible();
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, vis.size() - rows)));
        g.fill(lx, ly, lx + 134, ly + rows * 12, 0x40000000);
        for (int i = 0; i < rows && i + listScroll < vis.size(); i++) {
            int idx = vis.get(i + listScroll), ry = ly + i * 12;
            if (!a.get(idx).isJsonObject()) continue;
            JsonObject o = a.get(idx).getAsJsonObject();
            if (idx == sel) g.fill(lx, ry, lx + 134, ry + 12, 0x60D946EF);
            String n = nameOf(o);
            String col = switch (sourceOf(n)) { case "CUSTOM" -> "§a"; case "OVERRIDE" -> "§e"; default -> "§f"; };
            String extra = tab == 1 ? " §8" + HordeJson.str(o, "skillType", "") : (HordeJson.bool(o, "isBoss", false) ? " §c☠" : "");
            String line = col + n + extra;
            if (font.width(line) > 128) line = font.plainSubstrByWidth(line, 124) + "…";
            g.drawString(font, line, lx + 3, ry + 2, 0xFFFFFFFF);
        }
        if (a.isEmpty()) g.drawString(font, com.wavesurvivor.i18n.WSLang.t("ui.vide_b333"), lx + 3, ly + 2, 0xFFFFFFFF);
        else if (vis.isEmpty()) g.drawString(font, com.wavesurvivor.i18n.WSLang.t("ui.search_none"), lx + 3, ly + 2, 0xFFFFFFFF);
        // Légende des couleurs sous la liste (sur 2 lignes si elle dépasse la largeur de la colonne)
        String legend = com.wavesurvivor.i18n.WSLang.t("ui.en_jeu_modifie_base44");
        int legendY = ly + rows * 12 + 3;
        if (font.width(legend) <= 134) {
            g.drawString(font, legend, lx, legendY, 0xFFFFFFFF);
        } else {
            int cut = legend.lastIndexOf('■');
            if (cut >= 2 && legend.charAt(cut - 2) == '§') cut -= 2; // garde la couleur avec son carré
            if (cut <= 0) cut = legend.length() / 2;
            g.drawString(font, legend.substring(0, cut).trim(), lx, legendY, 0xFFFFFFFF);
            g.drawString(font, legend.substring(cut).trim(), lx, legendY + 10, 0xFFFFFFFF);
        }

        for (Object[] l : labels) g.drawString(font, (String) l[2], (int) l[0], (int) l[1], 0xFFBBBBBB);
        for (IconSlot s : iconSlots) {
            g.fill(s.x(), s.y(), s.x() + 18, s.y() + 18, 0xFF2A2A30);
            g.fill(s.x(), s.y(), s.x() + 18, s.y() + 1, 0xFF555560);
            String id = s.id().get();
            if (id != null && !id.isBlank()) {
                ItemStack st = LootItems.resolve(id, 1);
                g.renderItem(st.isEmpty() ? new ItemStack(Items.BARRIER) : st, s.x() + 1, s.y() + 1);
                if (s.clear() == null) { // ligne de loot : nom de l'objet
                    String nm = st.isEmpty() ? id : st.getHoverName().getString();
                    if (font.width(nm) > 86) nm = font.plainSubstrByWidth(nm, 82) + "…";
                    g.drawString(font, "§f" + nm, s.x() + 20, s.y() + 5, 0xFFFFFFFF);
                }
            }
        }
        super.render(g, mx, my, pt);
        if (!status.isEmpty()) g.drawCenteredString(font, status, width / 2, Math.min(top + H + 2, height - 10), 0xFFFFFFFF);
        if (picker != null) renderPicker(g, mx, my);
    }

    // ─── Sélecteur inventaire ───

    private List<ItemStack> inv() {
        return Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getInventory().items : List.of();
    }

    private static String idOf(ItemStack st) {
        return LootItems.idOf(st); // potions comprises ("minecraft:potion#...")
    }

    private int pickerX() { return width / 2 - 87; }
    private int pickerY() { return height / 2 - 44; }

    private int pickerSlotAt(double mx, double my) {
        for (int row = 0; row < 4; row++) {
            int ry = pickerY() + 16 + row * 18 + (row == 3 ? 4 : 0);
            for (int col = 0; col < 9; col++) {
                int rx = pickerX() + 6 + col * 18;
                if (mx >= rx && mx < rx + 17 && my >= ry && my < ry + 17) return row == 3 ? col : 9 + row * 9 + col;
            }
        }
        return -1;
    }

    private void renderPicker(GuiGraphics g, int mx, int my) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        int px = pickerX(), py = pickerY();
        g.fill(0, 0, width, height, 0x88000000);
        g.fill(px, py, px + 174, py + 100, 0xFF1E1E26);
        g.fill(px, py, px + 174, py + 1, 0xFFD946EF);
        g.drawString(font, com.wavesurvivor.i18n.WSLang.t("ui.clique_un_item_echap_annuler_70b7"), px + 6, py + 5, 0xFFFFFFFF);
        List<ItemStack> items = inv();
        int hovered = pickerSlotAt(mx, my);
        for (int row = 0; row < 4; row++) {
            int ry = py + 16 + row * 18 + (row == 3 ? 4 : 0);
            for (int col = 0; col < 9; col++) {
                int slot = row == 3 ? col : 9 + row * 9 + col;
                int rx = px + 6 + col * 18;
                g.fill(rx, ry, rx + 17, ry + 17, slot == hovered ? 0xFF4A4A58 : 0xFF2A2A30);
                if (slot < items.size() && !items.get(slot).isEmpty()) g.renderItem(items.get(slot), rx, ry);
            }
        }
        g.pose().popPose();
    }

    @Override
    public void onClose() {
        // Retour à l'éditeur de hordes si on en vient, sinon à la liste (rafraîchie)
        if (!(parent instanceof HordeEditorScreen)) {
            NetworkHandler.CHANNEL.sendToServer(new HordeEditorPackets.Request("", HordeEditorPackets.PURPOSE_LIST));
        }
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
