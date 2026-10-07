package com.wavesurvivor.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.wavesurvivor.horde.editor.HordeJson;
import com.wavesurvivor.horde.loot.LootItems;
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
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Éditeur d'une horde : Général / Mobs / Spéciales / Boss / Aperçu.
 * Toute saisie modifie directement le JSON de la horde (aucun champ inconnu n'est perdu).
 * Presse-papiers (JSON) : copier / coller mobs, vagues spéciales, entités de vague et boss, entre hordes.
 */
@OnlyIn(Dist.CLIENT)
public class HordeEditorScreen extends Screen {

    private static final String[] TAB_NAMES = {"⚙ Général", "🧟 Mobs", "⭐ Spéciales", "👑 Boss", "🛒 Pauses", "🌀 Chaos", "🗿 Autel", "📊 Aperçu"};
    private static final int T_GENERAL = 0, T_MOBS = 1, T_SPECIAL = 2, T_BOSS = 3, T_MERCH = 4, T_CHAOS = 5, T_ALTAR = 6, T_PREVIEW = 7;
    private static final String[] PROFESSIONS = {"armorer", "butcher", "cartographer", "cleric", "farmer", "fisherman",
            "fletcher", "leatherworker", "librarian", "mason", "shepherd", "toolsmith", "weaponsmith", "nitwit", "none"};
    private static final String[] CHAOS_TYPES = {"spawn", "merchant", "lightning", "totem", "gisement", "arbre"};
    private static final String[] PICKAXE_TIERS = {"bois", "pierre", "fer", "diamant", "netherite"};
    private static final String[] TOTEM_AURAS = {"fureur", "soin", "malediction", "invocation"};
    private static final String[] VANILLA_TYPES = {
            "minecraft:zombie", "minecraft:husk", "minecraft:drowned", "minecraft:zombie_villager", "minecraft:skeleton",
            "minecraft:stray", "minecraft:wither_skeleton", "minecraft:spider", "minecraft:cave_spider", "minecraft:creeper",
            "minecraft:witch", "minecraft:pillager", "minecraft:vindicator", "minecraft:evoker", "minecraft:ravager",
            "minecraft:piglin_brute", "minecraft:blaze", "minecraft:phantom", "minecraft:vex", "minecraft:slime",
            "minecraft:magma_cube", "minecraft:enderman", "minecraft:silverfish", "minecraft:guardian", "minecraft:warden",
            "minecraft:wither", "minecraft:iron_golem", "wavesurvivor:diablotin", "wavesurvivor:eclat_neant"};
    private static final String[] EQUIP_SLOTS = {"mainhand", "offhand", "head", "chest", "legs", "feet"};
    private static final String[] EQUIP_LABELS = {"Main", "Main 2", "Tête", "Torse", "Jambes", "Pieds"};

    private final HordeEditorListScreen parent;
    private final JsonObject horde;
    private String oldName;
    private final List<String> customEntities;
    private final List<String> allTypes = new ArrayList<>();
    private final List<String> vanillaTypes = new ArrayList<>(List.of(VANILLA_TYPES));

    private int tab = 0;
    private int selMob = -1, selSpecial = -1, selSpecialEnt = -1, selBoss = -1, selMerchant = -1, selChaos = -1;
    private int lootScroll = 0, previewScroll = 0, tradeScroll = 0;
    private boolean dirty = false, confirmClose = false;
    private String status = "";

    // ─── Édition à plusieurs (verrous par onglet, cf. HordeEditSessions) ───
    /** Éditeur affiché (reçoit les verrous et les mises à jour des autres joueurs). */
    private static HordeEditorScreen ACTIVE;
    /** Onglet → joueur qui l'occupe (les autres éditeurs de la même horde). */
    private final java.util.Map<Integer, String> lockedBy = new java.util.HashMap<>();
    /** Nombre d'éditeurs de la horde (moi compris). */
    private int editors = 1;
    /** Onglets modifiés depuis le dernier enregistrement. */
    private final java.util.Set<Integer> dirtyTabs = new java.util.HashSet<>();
    /** Copie de la horde à l'entrée dans l'onglet (« Annuler mes modifs »). */
    private JsonObject tabSnapshot;
    private JsonObject altarSnapshot;
    private boolean altarDeletedSnapshot, presenceSent = false;
    private int heartbeat = 0;
    private final List<Button> tabButtons = new ArrayList<>();
    private Button revertBtn;

    private int left, top, W, H;
    private final List<Object[]> labels = new ArrayList<>(); // {x, y, texte}

    // Bloc équipement + loot actif (fiche affichée)
    private JsonObject formOwner = null;
    private String formEquipKey = "equipment", formLootKey = "loot_table";
    private int formX, formEquipY, formLootY;
    private boolean formIsCustom = false;

    // Listes
    private final ListBox mobList, specialList, specialEntList, bossList, merchantList, chaosList, breachList;

    /** Case d'objet cliquable (échanges) : clic = choisir dans l'inventaire, clic droit = vider (si autorisé). */
    private record IconSlot(int x, int y, Supplier<String> id, Consumer<String> set, Runnable clear) {}
    private final List<IconSlot> iconSlots = new ArrayList<>();

    // Autel (altar_recipes.json) : null = aucune config pour cette horde
    private JsonObject altar;
    private boolean altarDeleted = false;
    private int altarSub = 0, chestScroll = 0;
    /** Gisement (onglet Chaos) : 0 = butin à la destruction, 1 = par coup ; défilement de la liste. */
    private int gisLootMode = 0, gisDropScroll = 0;
    private int totemFxScroll = 0;
    /** Onglet Brèches & Anomalies : vue (0 = Brèches, 1 = Anomalies), type sélectionné, défilements. */
    private int eventsSub = 0, breachMobScroll = 0, breachRewardScroll = 0, anomalyFxScroll = 0;
    /** Onglet Boss : 0 = le boss, 1 = ses sbires (équipement, butin, stats). */
    private int bossSub = 0;
    private final int[] breachTypeIdx = {0}, anomalyTypeIdx = {0};
    /** Effets proposés par ◀ ▶ (saisie libre possible, effets moddés compris). */
    private static final String[] EFFECTS = {"minecraft:speed", "minecraft:slowness", "minecraft:haste", "minecraft:mining_fatigue",
            "minecraft:strength", "minecraft:weakness", "minecraft:resistance", "minecraft:regeneration", "minecraft:poison",
            "minecraft:wither", "minecraft:blindness", "minecraft:darkness", "minecraft:nausea", "minecraft:hunger",
            "minecraft:jump_boost", "minecraft:levitation", "minecraft:slow_falling", "minecraft:glowing", "minecraft:invisibility",
            "minecraft:fire_resistance", "minecraft:absorption", "minecraft:instant_damage", "minecraft:instant_health",
            "minecraft:night_vision"};

    /** Sélection d'un item dans l'inventaire (équipement / loot). */
    private Consumer<String> picker = null;
    private String pickerTitle = "";
    /** Mode « œuf » du sélecteur : reçoit l'objet cliqué (au lieu de son identifiant). */
    private Consumer<ItemStack> stackPicker = null;

    public HordeEditorScreen(HordeEditorListScreen parent, JsonObject horde, String oldName, List<String> customEntities, String altarJson) {
        super(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.editeur_de_horde")));
        this.parent = parent;
        this.horde = horde;
        this.oldName = oldName != null ? oldName : "";
        this.customEntities = customEntities != null ? customEntities : List.of();
        this.altar = HordeJson.parseObject(altarJson);
        allTypes.addAll(vanillaTypes);
        allTypes.addAll(this.customEntities);
        if (this.oldName.isEmpty()) dirty = true;

        mobList = new ListBox(() -> labelsOf(mobs(), false), () -> selMob, i -> { selMob = i; lootScroll = 0; init(); });
        specialList = new ListBox(() -> {
            List<String> l = new ArrayList<>();
            for (JsonElement e : specials()) if (e.isJsonObject()) {
                JsonObject s = e.getAsJsonObject();
                int w = HordeJson.num(s, "chance", 0);
                l.add((w <= 0 ? "§8" : "§f") + HordeJson.str(s, "name", "?").trim() + " §7(" + w + ")");
            }
            return l;
        }, () -> selSpecial, i -> { selSpecial = i; selSpecialEnt = -1; init(); });
        specialEntList = new ListBox(() -> labelsOf(specialEnts(), true), () -> selSpecialEnt,
                i -> { selSpecialEnt = i; lootScroll = 0; init(); });
        breachList = new ListBox(() -> labelsOf(breachUnits(), true), () -> selBreach,
                i -> { selBreach = i; lootScroll = 0; init(); });
        bossList = new ListBox(() -> {
            List<String> l = new ArrayList<>();
            int maxWave = 0;
            for (JsonElement e : bosses()) if (e.isJsonObject()) maxWave = Math.max(maxWave, HordeJson.num(e.getAsJsonObject(), "waveNumber", 0));
            for (JsonElement e : bosses()) if (e.isJsonObject()) {
                JsonObject b = e.getAsJsonObject();
                int wn = HordeJson.num(b, "waveNumber", 0);
                boolean fin = kingdom() && wn == maxWave;
                l.add((fin ? "§6♛ " : "") + "§c" + (kingdom() ? "A" : "V") + wn + " §f" + HordeJson.str(b, "bossName", com.wavesurvivor.i18n.WSLang.t("ui.boss_5859")).replaceAll("§.", ""));
            }
            return l;
        }, () -> selBoss, i -> { selBoss = i; lootScroll = 0; init(); });
        merchantList = new ListBox(() -> {
            List<String> l = new ArrayList<>();
            for (JsonElement e : merchants()) if (e.isJsonObject()) {
                JsonObject m = e.getAsJsonObject();
                l.add("§f" + HordeJson.str(m, "name", com.wavesurvivor.i18n.WSLang.t("ui.marchand")) + " §8(" + HordeJson.arr(m, "trades").size() + ")");
            }
            return l;
        }, () -> selMerchant, i -> { selMerchant = i; tradeScroll = 0; init(); });
        chaosList = new ListBox(() -> {
            List<String> l = new ArrayList<>();
            for (JsonElement e : chaosEvents()) if (e.isJsonObject()) {
                JsonObject c = e.getAsJsonObject();
                String t = HordeJson.str(c, "type", "spawn");
                String icon = switch (t) { case "merchant" -> "§a🛒"; case "lightning" -> "§e⚡"; case "spawn" -> "§c☠"; case "totem" -> "§6⚜"; case "gisement" -> "§e⛏"; case "arbre" -> "§a🪓"; default -> "§8?"; };
                l.add(icon + " §f" + HordeJson.str(c, "message", t).replaceAll("§.", "").trim());
            }
            return l;
        }, () -> selChaos, i -> { selChaos = i; tradeScroll = 0; gisDropScroll = 0; totemFxScroll = 0; init(); });
    }

    // ─── Accès JSON ───

    private JsonObject cd() { return HordeJson.obj(horde, "configData"); }
    private JsonArray mobs() { return HordeJson.arr(cd(), "hordeEntities"); }
    private JsonArray specials() { return HordeJson.arr(cd(), "specialWaves"); }
    private JsonArray bosses() { return HordeJson.arr(cd(), "bossWaves"); }
    private JsonArray merchants() { return HordeJson.arr(cd(), "merchants"); }
    private JsonArray pauses() { return HordeJson.arr(cd(), "wavePauses"); }
    private JsonArray chaosEvents() { return HordeJson.arr(cd(), "chaosEvents"); }

    private static JsonObject at(JsonArray a, int i) {
        return i >= 0 && i < a.size() && a.get(i).isJsonObject() ? a.get(i).getAsJsonObject() : null;
    }

    private JsonObject special() { return at(specials(), selSpecial); }
    private JsonArray specialEnts() {
        JsonObject s = special();
        return s != null ? HordeJson.arr(s, "entities") : new JsonArray();
    }

    /** Onglet Calme en mode Kingdom : 0 = Marchands, 1 = Brèches ; unité de brèche sélectionnée. */
    private int calmSub = 0, selBreach = -1;
    /** Onglet Calme › Objectifs : réglage affiché (0 général, 1 convoi, 2 champion, 3 trésor, 4 purification). */
    private int objSub = 0, objDropScroll = 0;

    /** Unités des brèches du Calme (mode Kingdom). */
    private JsonArray breachUnits() {
        return HordeJson.arr(HordeJson.obj(cd(), "kingdom"), "calmBreachUnits");
    }

    private List<String> labelsOf(JsonArray a, boolean special) {
        List<String> l = new ArrayList<>();
        for (JsonElement e : a) {
            if (!e.isJsonObject()) { l.add("?"); continue; }
            JsonObject m = e.getAsJsonObject();
            String col = customEntities.contains(HordeJson.str(m, "entity_type", "")) ? "§d" : "§f";
            if (special) {
                l.add("§7" + HordeJson.num(m, "count", 1) + "× " + col + HordeJson.displayName(m));
            } else {
                int min = HordeJson.num(m, "min_wave", 0);
                l.add((kingdom() && HordeJson.bool(m, "kingdom_guardian", false) ? "§6⛨ " : "") + col + HordeJson.displayName(m)
                        + (min > 1 ? " §8" + (kingdom() ? "A" : "V") + min + "+" : ""));
            }
        }
        return l;
    }

    private void changed() {
        dirty = true;
        confirmClose = false;
        dirtyTabs.add(tab);
    }

    private void copyJson(JsonObject o, String what) {
        Minecraft.getInstance().keyboardHandler.setClipboard(new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(o));
        status = "§a📋 " + what + com.wavesurvivor.i18n.WSLang.t("ui.copie_dans_le_presse_papiers");
    }

    private JsonObject clipboard() {
        return HordeJson.parseObject(Minecraft.getInstance().keyboardHandler.getClipboard());
    }

    // ─── Widgets ───

    @Override
    protected void init() {
        clearWidgets();
        labels.clear();
        iconSlots.clear();
        formOwner = null;
        W = Math.min(width - 8, 520);
        H = Math.min(height - 8, 320);
        left = (width - W) / 2;
        top = (height - H) / 2;

        addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.enregistrer")), b -> save())
                .bounds(left + W - 170, top + 5, 90, 16).build());
        // Vérification de la horde (aussi lancée à chaque enregistrement)
        addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("check.btn")), b -> openIssues())
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(com.wavesurvivor.i18n.WSLang.c("check.btn_tip")))
                .bounds(left + W - 334, top + 5, 66, 16).build());
        // Export : la version ENREGISTRÉE de la horde part dans config/wavesurvivor/export (fichier .zip)
        // (à plusieurs : remplacé par « Annuler mes modifs », plus bas)
        if (!collab()) {
        Button exp = addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("exchange.export_btn")), b -> {
                    if (dirty) {
                        status = com.wavesurvivor.i18n.WSLang.t("exchange.export.save_first");
                        return;
                    }
                    NetworkHandler.CHANNEL.sendToServer(new com.wavesurvivor.network.HordeExchangePackets.Export(oldName));
                    status = com.wavesurvivor.i18n.WSLang.t("exchange.export.sent");
                })
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(com.wavesurvivor.i18n.WSLang.c("exchange.export_tip")))
                .bounds(left + W - 264, top + 5, 90, 16).build());
        exp.active = !oldName.isEmpty();
        }
        addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.fermer")), b -> onClose())
                .bounds(left + W - 76, top + 5, 70, 16).build());

        int tw = Math.min(90, (W - 12) / TAB_NAMES.length - 2);
        int tx = left + 6;
        tabButtons.clear();
        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int t = i;
            Button b = addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t(
                    kingdom() && TAB_NAMES_KINGDOM[i] != null ? TAB_NAMES_KINGDOM[i] : TAB_NAMES[i])), bt -> switchTab(t))
                    .bounds(tx, top + 24, tw, 16).build());
            tabButtons.add(b);
            tx += tw + 2;
        }
        refreshTabButtons();

        // Édition à plusieurs : première ouverture = présence annoncée + copie de l'onglet
        ACTIVE = this;
        if (!presenceSent) {
            presenceSent = true;
            snapshotTab();
            sendPresence(true);
        }
        revertBtn = null;
        if (collab()) {
            revertBtn = addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("collab.revert")), b -> revertTab())
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(com.wavesurvivor.i18n.WSLang.c("collab.revert_tip")))
                    .bounds(left + W - 264, top + 5, 90, 16).build());
            revertBtn.active = dirtyTabs.contains(tab);
        }

        switch (tab) {
            case T_GENERAL -> buildGeneral();
            case T_MOBS -> buildMobs();
            case T_SPECIAL -> buildSpecials();
            case T_BOSS -> buildBosses();
            case T_MERCH -> buildMerchants();
            case T_CHAOS -> buildChaos();
            case T_ALTAR -> buildAltar();
            case T_PREVIEW -> buildTestTools();
            default -> {}
        }
    }

    // ─── Tester une vague (onglet Aperçu) ───

    /** Vague à tester (la version en cours d'édition, même non enregistrée). */
    private static int testWave = 1;

    private void buildTestTools() {
        int max = Math.max(1, HordeJson.num(cd(), "totalWaves", 1));
        testWave = Math.max(1, Math.min(testWave, max));
        int y = top + H - 24, x = left + 10;
        button(x, y, 16, "◀", () -> { testWave = Math.max(1, testWave - 1); init(); });
        label(x + 22, y + 4, "§f" + (kingdom() ? "A" : "V") + testWave);
        button(x + 50, y, 16, "▶", () -> { testWave = Math.min(max, testWave + 1); init(); });
        addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("test.btn")), b -> {
                    NetworkHandler.CHANNEL.sendToServer(new com.wavesurvivor.network.HordeTestPackets.Test(
                            new GsonBuilder().disableHtmlEscaping().create().toJson(horde), testWave));
                    status = com.wavesurvivor.i18n.WSLang.t("test.sent", (kingdom() ? "A" : "V") + testWave);
                })
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(com.wavesurvivor.i18n.WSLang.c("test.btn_tip")))
                .bounds(x + 72, y, 110, 16).build());
        addRenderableWidget(Button.builder(Component.literal(com.wavesurvivor.i18n.WSLang.t("test.clear")), b -> {
                    NetworkHandler.CHANNEL.sendToServer(new com.wavesurvivor.network.HordeTestPackets.Clear());
                })
                .bounds(x + 188, y, 90, 16).build());
    }

    private void label(int x, int y, String text) { labels.add(new Object[]{x, y, text}); }

    private EditBox text(int x, int y, int w, String value, Consumer<String> onChange) {
        EditBox e = new EditBox(font, x, y, w, 14, Component.empty());
        e.setMaxLength(256);
        e.setValue(value != null ? value : "");
        e.setResponder(s -> { onChange.accept(s); changed(); });
        return addRenderableWidget(e);
    }

    private EditBox intBox(int x, int y, int w, int value, Consumer<Integer> onChange) {
        EditBox e = new EditBox(font, x, y, w, 14, Component.empty());
        e.setMaxLength(7);
        e.setFilter(s -> s.isEmpty() || s.matches("-?\\d*"));
        e.setValue(String.valueOf(value));
        e.setResponder(s -> {
            try { onChange.accept(s.isEmpty() || s.equals("-") ? 0 : Integer.parseInt(s)); changed(); } catch (Exception ignored) {}
        });
        return addRenderableWidget(e);
    }

    private EditBox dblBox(int x, int y, int w, double value, Consumer<Double> onChange) {
        EditBox e = new EditBox(font, x, y, w, 14, Component.empty());
        e.setMaxLength(8);
        e.setFilter(s -> s.isEmpty() || s.matches("\\d*[.,]?\\d*"));
        e.setValue(fmt(value));
        e.setResponder(s -> {
            try { onChange.accept(s.isEmpty() ? 0 : Double.parseDouble(s.replace(',', '.'))); changed(); } catch (Exception ignored) {}
        });
        return addRenderableWidget(e);
    }

    private Button button(int x, int y, int w, String text, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(text), b -> action.run()).bounds(x, y, w, 16).build());
    }

    private Button toggle(int x, int y, int w, String label, JsonObject o, String key, boolean def) {
        boolean on = HordeJson.bool(o, key, def);
        return button(x, y, w, label + (on ? com.wavesurvivor.i18n.WSLang.t("ui.oui_f7a6") : com.wavesurvivor.i18n.WSLang.t("ui.non_0835")), () -> {
            o.addProperty(key, !HordeJson.bool(o, key, def));
            changed();
            init();
        });
    }

    private static String fmt(double v) {
        return Math.abs(v - Math.round(v)) < 1e-9 ? String.valueOf((long) Math.round(v)) : String.valueOf(v);
    }

    private static String pretty(String type) {
        String n = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;
        n = n.replace('_', ' ');
        return n.isEmpty() ? type : Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    private static String cycle(List<String> list, String cur, int dir) {
        if (list.isEmpty()) return cur;
        int i = list.indexOf(cur);
        i = i < 0 ? 0 : ((i + dir) % list.size() + list.size()) % list.size();
        return list.get(i);
    }

    // ─── Onglet Général ───

    /** Mode Kingdom actif pour cette horde : libellés et champs des onglets adaptés. */
    private boolean kingdom() {
        try {
            return "kingdom".equalsIgnoreCase(HordeJson.str(cd(), "mode", "waves"));
        } catch (Exception e) {
            return false;
        }
    }

    /** Libellé classique ou sa version Kingdom. */
    private String kt(String classicKey, String kingdomKey) {
        return com.wavesurvivor.i18n.WSLang.t(kingdom() ? kingdomKey : classicKey);
    }

    /** Noms d'onglets en mode Kingdom (null = inchangé). */
    private static final String[] TAB_NAMES_KINGDOM = {null, null, "ui.kgd.tab_special", "ui.kgd.tab_boss", "ui.kgd.tab_calm", "ui.kgd.tab_chaos", null, null};

    /** Sous-onglet de l'onglet Général en mode Kingdom : 0 = Horde, 1 = Royaume. */
    private int generalSub = 0;

    /** Numéros de Portes d'une unité (« 1,3 » → {1, 3}, triés ; vide = toutes). */
    private static java.util.Set<Integer> parseGates(String s) {
        java.util.Set<Integer> out = new java.util.TreeSet<>();
        if (s == null || s.isBlank()) return out;
        for (String p : s.split(",")) {
            try { out.add(Integer.parseInt(p.trim())); } catch (NumberFormatException ignored) {}
        }
        return out;
    }

    /** Nombre de Portes de la horde éditée (coordonnées fixes ou nombre en mode auto), 1 à 8. */
    private int editorGateCount() {
        JsonObject k = HordeJson.obj(cd(), "kingdom");
        if ("fixed".equalsIgnoreCase(HordeJson.str(k, "gatePlacement", "auto"))) {
            int n = HordeJson.arr(k, "gatePoints").size();
            if (n > 0) return Math.min(8, n);
        }
        return Math.max(1, Math.min(8, HordeJson.num(k, "gateCount", 4)));
    }

    // ─── Menu déroulant des couleurs du fond de l'antre ───

    /** Réglages Kingdom dont on choisit la couleur (null = menu fermé) et position du menu. */
    private JsonObject denMenu = null;
    private int denMenuX, denMenuY;
    private static final int DM_W = 170, DM_ROW = 13;

    private int dmH() { return 28 + GreatPortalArt.DEN_HUES.length * DM_ROW + 4; }

    private int dmY() { return Math.max(4, Math.min(denMenuY, height - dmH() - 4)); }

    /** Nom affiché d'une couleur : « Rouge foncé », « Rouge », « Rouge clair », « Auto (thème) ». */
    private String denName(String key) {
        int[] hv = GreatPortalArt.denParse(key);
        if (hv == null) return t("ui.color.auto");
        String n = t("ui.color." + GreatPortalArt.DEN_HUES[hv[0]]);
        return hv[1] == 0 ? n + t("ui.color.v_dark") : hv[1] == 2 ? n + t("ui.color.v_light") : n;
    }

    /** Clé de la couleur sous la souris (« auto » pour la ligne du haut), null en dehors. */
    private String denMenuAt(double mx, double my) {
        int px = denMenuX, py = dmY();
        if (mx >= px + 6 && mx < px + DM_W - 6 && my >= py + 5 && my < py + 19) return "auto";
        for (int i = 0; i < GreatPortalArt.DEN_HUES.length; i++) {
            int ry = py + 26 + i * DM_ROW;
            for (int v = 0; v < 3; v++) {
                int sx = px + 86 + v * 26;
                if (mx >= sx && mx < sx + 22 && my >= ry && my < ry + 11) return GreatPortalArt.denKey(i, v);
            }
        }
        return null;
    }

    private void renderDenMenu(GuiGraphics g, int mx, int my) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        int px = denMenuX, py = dmY();
        String cur = HordeJson.str(denMenu, "denBackground", "auto");
        String hover = denMenuAt(mx, my);
        g.fill(px, py, px + DM_W, py + dmH(), 0xFF1E1E26);
        g.fill(px, py, px + DM_W, py + 1, 0xFFF59E0B);
        boolean autoSel = GreatPortalArt.denParse(cur) == null;
        g.fill(px + 6, py + 5, px + DM_W - 6, py + 19, "auto".equals(hover) ? 0xFF3A3A48 : autoSel ? 0xFF33333F : 0xFF2A2A34);
        g.drawString(font, (autoSel ? "§e▶ " : "") + t("ui.color.auto"), px + 10, py + 8, 0xFFFFFFFF);
        for (int i = 0; i < GreatPortalArt.DEN_HUES.length; i++) {
            int ry = py + 26 + i * DM_ROW;
            g.drawString(font, "§7" + t("ui.color." + GreatPortalArt.DEN_HUES[i]), px + 8, ry + 2, 0xFFFFFFFF);
            for (int v = 0; v < 3; v++) {
                int sx = px + 86 + v * 26;
                String key = GreatPortalArt.denKey(i, v);
                int border = key.equals(cur) ? 0xFFFFFFFF : key.equals(hover) ? 0xFFF59E0B : 0xFF000000;
                g.fill(sx - 1, ry - 1, sx + 23, ry + 12, border);
                g.fill(sx, ry, sx + 22, ry + 11, 0xFF000000 | GreatPortalArt.denRgb(i, v));
            }
        }
        if (hover != null && !"auto".equals(hover)) g.renderTooltip(font, Component.literal(denName(hover)), mx, my);
        g.pose().popPose();
    }

    /** Sous-onglet « 🛤 Chemins » (mode Kingdom) : chemins tracés au Bâton de tracé, suivis par les escouades. */
    private void buildPaths(JsonObject cd, int x, int y) {
        JsonObject k = HordeJson.obj(cd, "kingdom");
        JsonArray paths = HordeJson.arr(k, "paths");
        label(x, y, "§6§l" + t("ui.paths.title"));
        y += 12;
        label(x, y, t("ui.paths.hint1"));
        y += 11;
        label(x, y, t("ui.paths.hint2"));
        y += 16;
        String hordeName = HordeJson.str(horde, "hordeName", "");
        for (int i = 0; i < paths.size() && i < 8; i++) {
            if (!paths.get(i).isJsonObject()) continue;
            JsonObject p = paths.get(i).getAsJsonObject();
            final int idx = i;
            int n = HordeJson.arr(p, "points").size();
            text(x, y, 130, HordeJson.str(p, "name", t("ui.paths.default") + " " + (i + 1)), v -> p.addProperty("name", v));
            label(x + 136, y + 4, (n == 0 ? "§c" : "§a") + n + " §7" + t("ui.paths.points"));
            button(x + 200, y - 1, 90, t("ui.paths.wand"), () -> {
                com.wavesurvivor.network.NetworkHandler.CHANNEL.sendToServer(new com.wavesurvivor.network.HordeEditorPackets.GiveWand(hordeName, idx));
                status = t("ui.paths.wand_given");
            });
            button(x + 294, y - 1, 56, t("ui.paths.clear"), () -> {
                p.add("points", new JsonArray());
                changed();
                init();
            });
            button(x + 354, y - 1, 22, "§c✖", () -> {
                paths.remove(idx);
                changed();
                init();
            });
            y += 20;
        }
        if (paths.size() < 8) {
            button(x, y + 2, 120, t("ui.paths.add"), () -> {
                JsonObject np = new JsonObject();
                np.addProperty("name", t("ui.paths.default") + " " + (paths.size() + 1));
                np.add("points", new JsonArray());
                paths.add(np);
                changed();
                init();
            });
        }
        if (paths.size() == 0) label(x + 128, y + 7, t("ui.paths.empty"));
        label(x, y + 28, t("ui.paths.save_first"));
    }

    /** Sous-onglet « 🚪 Portes » (mode Kingdom) : placement automatique (1 à 8 Portes) ou coordonnées fixes (maps custom). */
    private void buildGates(JsonObject cd, int x, int y) {
        JsonObject k = HordeJson.obj(cd, "kingdom");
        boolean fixed = "fixed".equalsIgnoreCase(HordeJson.str(k, "gatePlacement", "auto"));
        label(x, y, "§6§l" + t("ui.gates.sec_place"));
        y += 12;
        // Forme des Portes : arche monumentale ou antre rocheux (ovale tourbillonnant + vrilles)
        boolean den = "den".equalsIgnoreCase(HordeJson.str(k, "gateShape", "arch"));
        button(x, y - 1, 220, t("ui.gates.shape") + t(den ? "ui.gates.shape_den" : "ui.gates.shape_arch"), () -> {
            k.addProperty("gateShape", den ? "arch" : "den");
            changed();
            init();
        });
        label(x + 228, y + 3, t("ui.gates.shape_hint"));
        y += 22;
        // Antre : couleur du fond (portail du Nether recoloré) choisie dans un menu déroulant
        if (den) {
            String cur = HordeJson.str(k, "denBackground", "auto");
            int rgb = GreatPortalArt.denColor(cur, null);
            net.minecraft.network.chat.MutableComponent lbl = Component.literal(t("ui.gates.den_bg"))
                    .append(Component.literal("■ ").withStyle(s -> s.withColor(rgb)))
                    .append(Component.literal(denName(cur) + " §7▼"));
            final int menuY = y + 20;
            addRenderableWidget(Button.builder(lbl, b -> {
                denMenu = k;
                denMenuX = x;
                denMenuY = menuY;
            }).bounds(x, y - 1, 220, 20).build());
            label(x + 228, y + 3, t("ui.gates.den_bg_hint"));
            y += 22;
        }
        button(x, y - 1, 220, t("ui.gates.mode") + t(fixed ? "ui.gates.fixed" : "ui.gates.auto"), () -> {
            k.addProperty("gatePlacement", fixed ? "auto" : "fixed");
            changed();
            init();
        });
        // Orientation générale des Portes (face au Monolithe / dos / côté gauche / droit)
        String gf = HordeJson.str(k, "gateFacing", "monolith");
        button(x + 228, y - 1, 170, t("ui.gates.facing") + t("ui.gates.face." + gf), () -> {
            String[] fs = {"monolith", "away", "left", "right"};
            int fi = java.util.Arrays.asList(fs).indexOf(HordeJson.str(k, "gateFacing", "monolith"));
            k.addProperty("gateFacing", fs[(fi + 1 + fs.length) % fs.length]);
            changed();
            init();
        });
        y += 22;
        if (!fixed) {
            flow(x + 228, y);
            fl(t("ui.gates.count"));
            fInt(22, HordeJson.num(k, "gateCount", 4), v -> k.addProperty("gateCount", Math.max(1, Math.min(8, v))));
            fl(t("ui.gates.count_hint"));
            label(x, y + 26, t("ui.gates.auto_hint"));
            label(x, y + 40, t("ui.gates.theme_hint"));
            return;
        }
        y += 22;
        label(x, y, t("ui.gates.fixed_hint"));
        y += 14;
        JsonArray pts = HordeJson.arr(k, "gatePoints");
        for (int i = 0; i < pts.size() && i < 8; i++) {
            if (!pts.get(i).isJsonObject()) continue;
            JsonObject p = pts.get(i).getAsJsonObject();
            final int idx = i;
            flow(x, y);
            fl("§e" + com.wavesurvivor.i18n.WSLang.t("ui.gates.portal_n", i + 1) + "§7  X");
            fInt(48, HordeJson.num(p, "x", 0), v -> p.addProperty("x", v));
            fl("Y");
            fInt(34, HordeJson.num(p, "y", 64), v -> p.addProperty("y", v));
            fl("Z");
            fInt(48, HordeJson.num(p, "z", 0), v -> p.addProperty("z", v));
            fBtn(84, t("ui.gates.here"), () -> {
                var pl = Minecraft.getInstance().player;
                if (pl == null) return;
                p.addProperty("x", pl.getBlockX());
                p.addProperty("y", pl.getBlockY());
                p.addProperty("z", pl.getBlockZ());
                changed();
                init();
            });
            fBtn(22, "§c✖", () -> {
                pts.remove(idx);
                changed();
                init();
            });
            // Orientation propre à cette Porte (« Global » = réglage général)
            String pf = HordeJson.str(p, "facing", "");
            fBtn(70, "↻ " + (pf.isEmpty() ? t("ui.gates.face.global") : t("ui.gates.face." + pf)), () -> {
                String[] fs = {"", "monolith", "away", "left", "right"};
                int fi = java.util.Arrays.asList(fs).indexOf(HordeJson.str(p, "facing", ""));
                p.addProperty("facing", fs[(fi + 1 + fs.length) % fs.length]);
                changed();
                init();
            });
            y += 19;
        }
        if (pts.size() < 8) {
            button(x, y + 2, 120, t("ui.gates.add"), () -> {
                JsonObject p = new JsonObject();
                var pl = Minecraft.getInstance().player;
                p.addProperty("x", pl != null ? pl.getBlockX() : 0);
                p.addProperty("y", pl != null ? pl.getBlockY() : 64);
                p.addProperty("z", pl != null ? pl.getBlockZ() : 0);
                pts.add(p);
                changed();
                init();
            });
        }
        if (pts.size() == 0) label(x + 128, y + 7, t("ui.gates.empty"));
    }

    private void buildGeneral() {
        JsonObject cd = cd();
        int x = left + 10, y = top + 48;
        // Mode Kingdom : 2 sous-onglets (Horde / Royaume) pour que tout tienne dans le panneau
        if (kingdom()) {
            button(x, y - 2, 100, (generalSub == 0 ? "§e§l" : "§7") + t("ui.kgd.sub_horde"), () -> { generalSub = 0; init(); });
            button(x + 104, y - 2, 100, (generalSub == 1 ? "§e§l" : "§7") + t("ui.kgd.sub_kingdom"), () -> { generalSub = 1; init(); });
            button(x + 208, y - 2, 100, (generalSub == 2 ? "§e§l" : "§7") + t("ui.kgd.sub_gates"), () -> { generalSub = 2; init(); });
            button(x + 312, y - 2, 100, (generalSub == 3 ? "§e§l" : "§7") + t("ui.kgd.sub_paths"), () -> { generalSub = 3; init(); });
            y += 24;
            if (generalSub == 1) {
                buildKingdomSettings(cd, x, y);
                return;
            }
            if (generalSub == 2) {
                buildGates(cd, x, y);
                return;
            }
            if (generalSub == 3) {
                buildPaths(cd, x, y);
                return;
            }
        }
        label(x, y + 3, com.wavesurvivor.i18n.WSLang.t("ui.nom"));
        text(x + 70, y, 200, HordeJson.str(horde, "hordeName", ""), s -> horde.addProperty("hordeName", s));
        // Pendant la horde : bloque l'apparition naturelle des créatures (moins d'entités, aucun monstre sauvage dans le combat)
        toggle(x + 280, y, 190, t("ui.block_natural_spawns"), cd, "blockNaturalSpawns", false);
        y += 20;
        label(x, y + 3, com.wavesurvivor.i18n.WSLang.t("ui.description"));
        text(x + 70, y, W - 90, HordeJson.str(horde, "hordeDescription", ""), s -> horde.addProperty("hordeDescription", s));
        y += 24;
        // Difficulté affichée (étoiles) + niveaux de difficulté autorisés au lancement
        flow(x, y);
        fl(t("ui.diff.stars"));
        int stars = HordeJson.num(cd, "stars", 0);
        for (int s = 1; s <= 5; s++) {
            final int ss = s;
            fBtn(14, s <= stars ? "§6★" : "§8☆", () -> {
                cd.addProperty("stars", HordeJson.num(cd, "stars", 0) == ss ? 0 : ss);
                changed();
                init();
            });
        }
        fl("  " + t("ui.diff.allowed"));
        java.util.Set<com.wavesurvivor.horde.difficulty.Difficulty> allowed =
                com.wavesurvivor.horde.difficulty.Difficulty.allowed(HordeJson.str(cd, "difficulties", ""));
        for (com.wavesurvivor.horde.difficulty.Difficulty d : com.wavesurvivor.horde.difficulty.Difficulty.values()) {
            boolean on = allowed.contains(d);
            fBtn(60, (on ? d.color : "§8") + com.wavesurvivor.i18n.WSLang.t("difficulty." + d.id), () -> {
                java.util.Set<com.wavesurvivor.horde.difficulty.Difficulty> a2 = new java.util.LinkedHashSet<>(
                        com.wavesurvivor.horde.difficulty.Difficulty.allowed(HordeJson.str(cd, "difficulties", "")));
                if (a2.contains(d)) { if (a2.size() > 1) a2.remove(d); } else a2.add(d);
                StringBuilder sb = new StringBuilder();
                if (a2.size() < com.wavesurvivor.horde.difficulty.Difficulty.values().length) {
                    for (com.wavesurvivor.horde.difficulty.Difficulty x2 : com.wavesurvivor.horde.difficulty.Difficulty.values()) {
                        if (a2.contains(x2)) sb.append(sb.length() > 0 ? "," : "").append(x2.id);
                    }
                }
                cd.addProperty("difficulties", sb.toString()); // vide = tous autorisés
                changed();
                init();
            });
        }
        y += 22;
        // Déblocage : hordes à terminer (éventuellement à un niveau minimum) avant de pouvoir lancer celle-ci
        JsonArray uReqs = HordeJson.arr(cd, "unlockRequires");
        List<String> uOthers = new ArrayList<>();
        String uSelf = HordeJson.str(horde, "hordeName", "");
        for (HordeEditorPackets.Entry e : parent.entries) if (!e.name().equals(uSelf)) uOthers.add(e.name());
        flow(x, y);
        fl(t("ui.unlock.title"));
        if (uReqs.size() == 0) fl(t("ui.unlock.none"));
        if (uReqs.size() < 4 && !uOthers.isEmpty()) {
            fBtn(90, t("ui.unlock.add"), () -> {
                JsonObject nr = new JsonObject();
                nr.addProperty("horde", uOthers.get(0));
                nr.addProperty("difficulty", "");
                uReqs.add(nr);
                changed();
                init();
            });
        }
        y += 20;
        for (int i = 0; i < uReqs.size() && i < 4; i++) {
            JsonObject ur = at(uReqs, i);
            if (ur == null) continue;
            final int uri = i;
            flow(x + 12, y);
            fl("§7" + t("ui.unlock.finish"));
            String uhn = HordeJson.str(ur, "horde", "");
            fBtn(150, "§f" + (uhn.length() > 22 ? uhn.substring(0, 21) + "…" : uhn), () -> {
                if (uOthers.isEmpty()) return;
                ur.addProperty("horde", cycle(uOthers, HordeJson.str(ur, "horde", ""), hasShiftDown() ? -1 : 1));
                changed();
                init();
            });
            fl("§7" + t("ui.unlock.min"));
            com.wavesurvivor.horde.difficulty.Difficulty udd = com.wavesurvivor.horde.difficulty.Difficulty.byId(HordeJson.str(ur, "difficulty", ""));
            fBtn(70, udd == null ? "§7" + t("ui.unlock.any") : udd.color + com.wavesurvivor.i18n.WSLang.t("difficulty." + udd.id), () -> {
                String[] ids = {"", "easy", "normal", "hard", "nightmare"};
                int k = java.util.Arrays.asList(ids).indexOf(HordeJson.str(ur, "difficulty", ""));
                ur.addProperty("difficulty", ids[(k + 1 + ids.length) % ids.length]);
                changed();
                init();
            });
            fBtn(16, "§c✖", () -> {
                uReqs.remove(uri);
                changed();
                init();
            });
            y += 19;
        }
        flow(x, y);
        fl(kt("ui.vagues_a678", "ui.kgd.tiers"));
        fInt(34, HordeJson.num(cd, "totalWaves", 5), v -> cd.addProperty("totalWaves", Math.max(1, v)));
        if (kingdom()) {
            fl(com.wavesurvivor.i18n.WSLang.t("ui.kgd.tiers_hint"));
        } else {
            fl(com.wavesurvivor.i18n.WSLang.t("ui.pause_entre_vagues_s"));
            fInt(34, HordeJson.num(cd, "delayBetweenWaves", 30), v -> cd.addProperty("delayBetweenWaves", Math.max(0, v)));
            fl(com.wavesurvivor.i18n.WSLang.t("ui.rayon_de_spawn"));
            fInt(30, HordeJson.num(cd, "spawnRadius", 12), v -> cd.addProperty("spawnRadius", Math.max(1, v)));
        }
        y += 20;
        // Fin de vague forcée (hordes classiques et Assauts Kingdom)
        flow(x, y);
        fl(t("ui.force_end"));
        fInt(30, HordeJson.num(cd, "forceEndSeconds", 60), v -> cd.addProperty("forceEndSeconds", Math.max(0, v)));
        fl(t("ui.force_end_hint"));
        y += 20;
        // Bonus si la vague est nettoyée avant la fin du décompte
        {
            JsonObject fb = HordeJson.obj(cd, "forceEndBonus");
            flow(x, y);
            fl(t("ui.force_end_bonus"));
            if (kingdom()) {
                String[] k = {"money", "wood", "stone", "iron", "essence"};
                int[] def = {5, 5, 5, 2, 1};
                String[] lbl = {"§e◆", "§6" + t("obj.res.wood"), "§7" + t("obj.res.stone"), "§f" + t("obj.res.iron"), "§d" + t("obj.res.essence")};
                for (int i = 0; i < k.length; i++) {
                    final String key = k[i];
                    fl(lbl[i]);
                    fInt(22, HordeJson.num(fb, key, def[i]), v -> fb.addProperty(key, Math.max(0, v)));
                }
            } else {
                fl(t("ui.force_end_emeralds"));
                fInt(24, HordeJson.num(fb, "emeralds", 5), v -> fb.addProperty("emeralds", Math.max(0, v)));
            }
            y += 20;
        }
        if (!kingdom()) {
        flow(x, y);
        fl(com.wavesurvivor.i18n.WSLang.t("ui.rayon_de_game_over"));
        fInt(44, HordeJson.num(cd, "gameOverRadius", 0), v -> cd.addProperty("gameOverRadius", Math.max(0, v)));
        fl(com.wavesurvivor.i18n.WSLang.t("ui.en_blocs_0_aucun"));
        y += 20;
        // Arène du boss final (horde classique) : le boss de la dernière vague attend dans sa dimension
        toggle(x, y, 190, t("ui.raid_arena_classic"), cd, "raidArena", false);
        flow(x + 196, y);
        fl(t("ui.kingdom.raid_radius"));
        fInt(24, HordeJson.num(cd, "raidRadius", 18), v -> cd.addProperty("raidRadius", Math.max(10, Math.min(40, v))));
        fl(t("ui.raid_hint_classic"));
        y += 20;
        flow(x, y);
        fl(com.wavesurvivor.i18n.WSLang.t("ui.spawn_batch"));
        fInt(26, HordeJson.num(cd, "spawnBatchSize", 5), v -> cd.addProperty("spawnBatchSize", Math.max(0, v)));
        fl(com.wavesurvivor.i18n.WSLang.t("ui.spawn_every"));
        fDbl(30, HordeJson.dbl(cd, "spawnBatchInterval", 3.0), v -> cd.addProperty("spawnBatchInterval", Math.max(0.1, v)));
        fl(com.wavesurvivor.i18n.WSLang.t("ui.spawn_batch_hint"));
        }
        y += 24;

        JsonObject ps = HordeJson.obj(cd, "playerScaling");
        flow(x, y + 1);
        toggle(x, y, 140, com.wavesurvivor.i18n.WSLang.t("ui.scaling_joueurs"), ps, "enabled", true);
        fx = x + 148;
        fl(com.wavesurvivor.i18n.WSLang.t("ui.nombre_d9b7"));
        fDbl(34, HordeJson.dbl(ps, "countMultiplier", 0.5), v -> ps.addProperty("countMultiplier", v));
        fl(com.wavesurvivor.i18n.WSLang.t("ui.stats"));
        fDbl(34, HordeJson.dbl(ps, "statsMultiplier", 0.1), v -> ps.addProperty("statsMultiplier", v));
        y += 24;

        JsonObject msg = HordeJson.obj(cd, "spawnMessages");
        label(x, y + 3, com.wavesurvivor.i18n.WSLang.t("ui.msg_vague"));
        text(x + 70, y, W - 90, HordeJson.str(msg, "normalWave", ""), s -> msg.addProperty("normalWave", s));
        y += 20;
        label(x, y + 3, com.wavesurvivor.i18n.WSLang.t("ui.msg_speciale"));
        text(x + 70, y, W - 90, HordeJson.str(msg, "specialWave", ""), s -> msg.addProperty("specialWave", s));
        y += 18;
        label(x + 70, y, com.wavesurvivor.i18n.WSLang.t("ui.variables_wave_total_name_couleurs_avec"));

        // ── Mode de jeu : Vagues / Kingdom ──
        y += 18;
        boolean kingdom = "kingdom".equalsIgnoreCase(HordeJson.str(cd, "mode", "waves"));
        button(x, y - 1, 150, kingdom ? t("ui.kingdom.mode_kingdom") : t("ui.kingdom.mode_waves"), () -> {
            cd.addProperty("mode", "kingdom".equalsIgnoreCase(HordeJson.str(cd, "mode", "waves")) ? "waves" : "kingdom");
            generalSub = 0;
            changed();
            init();
        });
        label(x + 158, y + 3, kingdom ? t("ui.kgd.see_sub") : t("ui.kingdom.hint"));
    }

    /** Sous-onglet « ♛ Royaume » : tous les réglages du mode Kingdom, en 5 blocs titrés qui tiennent dans le panneau. */
    private void buildKingdomSettings(JsonObject cd, int x, int y) {
        JsonObject k = HordeJson.obj(cd, "kingdom");

        // ── Portes ──
        label(x, y, "§6§l" + t("ui.kgd.sec_gates"));
        y += 11;
        boolean blocks = !"visual".equalsIgnoreCase(HordeJson.str(k, "gateStyle", "blocks"));
        button(x, y - 1, 150, t("ui.kingdom.gate_style") + t(blocks ? "ui.kingdom.gate_blocks" : "ui.kingdom.gate_visual"), () -> {
            k.addProperty("gateStyle", "visual".equalsIgnoreCase(HordeJson.str(k, "gateStyle", "blocks")) ? "blocks" : "visual");
            changed();
            init();
        });
        String[] themes = {"auto", "abyss", "fire", "bone", "end", "ocean", "arcane", "terrain"};
        String th = HordeJson.str(k, "portalTheme", "auto");
        button(x + 156, y - 1, 120, t("ui.kingdom.theme") + t("ui.kingdom.theme." + th), () -> {
            int idx = java.util.Arrays.asList(themes).indexOf(HordeJson.str(k, "portalTheme", "auto"));
            k.addProperty("portalTheme", themes[(idx + (hasShiftDown() ? themes.length - 1 : 1) + themes.length) % themes.length]);
            changed();
            init();
        });
        toggle(x + 282, y, 110, t("ui.kingdom.final_boss"), k, "finalBoss", true);
        y += 20;
        // Arène du Roi : le boss final attend dans sa dimension, accessible par la Porte du Roi une fois l'Assaut final repoussé
        toggle(x, y, 170, t("ui.kingdom.raid_arena"), k, "raidArena", false);
        flow(x + 176, y);
        fl(t("ui.kingdom.raid_radius"));
        fInt(24, HordeJson.num(k, "raidRadius", 18), v -> k.addProperty("raidRadius", Math.max(10, Math.min(40, v))));
        fl(t("ui.kingdom.raid_hint"));
        y += 20;
        flow(x, y);
        fl(t("ui.kingdom.ring"));
        fInt(26, HordeJson.num(k, "ringMin", 64), v -> k.addProperty("ringMin", Math.max(8, v)));
        fl("→");
        fInt(26, HordeJson.num(k, "ringMax", 72), v -> k.addProperty("ringMax", Math.max(8, v)));
        fl(t("ui.kingdom.portal_hp"));
        fInt(36, (int) HordeJson.dbl(k, "portalHealth", 800), v -> k.addProperty("portalHealth", Math.max(10, v)));
        y += 24;

        // ── Rythme des assauts ──
        label(x, y, "§6§l" + t("ui.kgd.sec_rhythm"));
        y += 11;
        flow(x, y);
        fl(t("ui.kingdom.budget"));
        fInt(30, HordeJson.num(k, "assaultBudget", 100), v -> k.addProperty("assaultBudget", Math.max(1, v)));
        fl(t("ui.kingdom.growth"));
        fInt(24, (int) Math.round(HordeJson.dbl(k, "assaultGrowth", 0.2) * 100), v -> k.addProperty("assaultGrowth", Math.max(0, v) / 100.0));
        fl(t("ui.kingdom.cap"));
        fInt(26, HordeJson.num(k, "aliveCap", 80), v -> k.addProperty("aliveCap", Math.max(1, v)));
        fl(t("ui.kingdom.cycles"));
        fInt(24, HordeJson.num(k, "maxCycles", 10), v -> k.addProperty("maxCycles", Math.max(1, v)));
        y += 20;
        flow(x, y);
        fl(t("ui.kingdom.calm"));
        fInt(22, (int) Math.round(HordeJson.dbl(k, "calmPercent", 5)), v -> k.addProperty("calmPercent", Math.max(0, v)));
        fl(t("ui.kingdom.calm_secs"));
        fInt(28, HordeJson.num(k, "calmSeconds", 120), v -> k.addProperty("calmSeconds", Math.max(10, v)));
        fl(t("ui.kgd.breach_moved"));
        y += 24;

        // ── Marche ──
        label(x, y, "§6§l" + t("ui.kgd.sec_march"));
        y += 11;
        flow(x, y);
        fl(t("ui.kingdom.squad"));
        fInt(22, HordeJson.num(k, "squadSize", 8), v -> k.addProperty("squadSize", Math.max(1, Math.min(20, v))));
        fl(t("ui.kingdom.engage"));
        fInt(24, HordeJson.num(k, "engageRadius", 16), v -> k.addProperty("engageRadius", Math.max(4, v)));
        fl(t("ui.kingdom.engage_unit"));
        y += 20;

        // ── Royaume (claim et murs) : même bloc que la marche, une seule ligne ──
        toggle(x, y, 118, t("ui.kingdom.build_claim"), k, "buildOnlyInClaim", true);
        toggle(x + 122, y, 104, t("ui.kingdom.walls"), k, "wallDurability", true);
        toggle(x + 230, y, 104, t("ui.kingdom.claim_markers"), k, "claimMarkers", true);
        flow(x + 338, y);
        fl(t("ui.kingdom.claim_radius"));
        fInt(24, HordeJson.num(k, "claimRadius", 16), v -> k.addProperty("claimRadius", Math.max(4, Math.min(64, v))));
        fl(t("ui.kingdom.wall_hp"));
        fDbl(30, HordeJson.dbl(k, "wallHpMultiplier", 1.0), v -> k.addProperty("wallHpMultiplier", Math.max(0.1, v)));
        y += 24;

        // ── Objectifs (Gardiens, Catalyseur, corruption) ──
        label(x, y, "§6§l" + t("ui.kgd.sec_objectives"));
        y += 11;
        flow(x, y);
        fl(t("ui.kingdom.guardians"));
        fInt(18, HordeJson.num(k, "guardiansPerGate", 2), v -> k.addProperty("guardiansPerGate", Math.max(0, Math.min(6, v))));
        fl("×");
        fDbl(24, HordeJson.dbl(k, "guardianHpMult", 3.0), v -> k.addProperty("guardianHpMult", Math.max(1, v)));
        fl(t("ui.kingdom.catalyst"));
        fInt(28, (int) HordeJson.dbl(k, "catalystHealth", 150), v -> k.addProperty("catalystHealth", Math.max(10, v)));
        fl(t("ui.kingdom.breach_pct"));
        fInt(20, (int) Math.round(HordeJson.dbl(k, "breachPercent", 35)), v -> k.addProperty("breachPercent", Math.max(5, Math.min(100, v))));
        y += 20;
        JsonObject cor = HordeJson.obj(k, "corruption");
        flow(x, y);
        fl(t("ui.kingdom.corruption"));
        fInt(24, HordeJson.num(cor, "radius", 0), v -> cor.addProperty("radius", Math.max(0, Math.min(32, v))));
        fBtn(110, t("ui.kingdom.corruption_copy"), () -> {
            if (altar == null) return;
            JsonObject altarZone = HordeJson.obj(altar, "zone");
            for (String corKey : new String[]{"floor", "palette", "pattern"}) {
                if (altarZone.has(corKey)) cor.add(corKey, altarZone.get(corKey).deepCopy());
                else cor.remove(corKey);
            }
            if (HordeJson.num(cor, "radius", 0) <= 0) cor.addProperty("radius", Math.max(1, Math.min(32, HordeJson.num(altarZone, "radius", 8))));
            changed();
            init();
        }).active = altar != null;
        fBtn(84, t("ui.kingdom.corruption_draw"), () ->
                net.minecraft.client.Minecraft.getInstance().setScreen(new FloorPainterScreen(this, cor, this::changed)));
        int nFloor = cor.has("floor") && cor.get("floor").isJsonArray() ? cor.getAsJsonArray("floor").size() : 0;
        boolean drawnCor = cor.has("pattern") && cor.get("pattern").isJsonArray() && cor.getAsJsonArray("pattern").size() > 0;
        fl(HordeJson.num(cor, "radius", 0) <= 0 ? t("ui.kingdom.corruption_off")
                : "§7" + nFloor + " " + t("ui.kingdom.corruption_blocks") + (drawnCor ? " · §a" + t("ui.kingdom.corruption_drawn") : ""));
    }

    // ─── Géométrie commune ───

    private int listX() { return left + 6; }
    private int detailX() { return left + 152; }

    // ─── Mise en page « en flux » : chaque libellé décale le champ suivant selon sa largeur réelle ───

    private int fx, fy;

    private void flow(int x, int y) { fx = x; fy = y; }

    private void fl(String t) {
        label(fx, fy + 3, t);
        fx += font.width(t) + 4;
    }

    private void fInt(int w, int v, Consumer<Integer> c) { intBox(fx, fy, w, v, c); fx += w + 8; }

    private void fDbl(int w, double v, Consumer<Double> c) { dblBox(fx, fy, w, v, c); fx += w + 8; }

    private void fText(int w, String v, Consumer<String> c) { text(fx, fy, w, v, c); fx += w + 8; }

    private Button fBtn(int w, String t, Runnable r) {
        Button b = button(fx, fy - 1, w, t, r);
        fx += w + 4;
        return b;
    }

    /** Case d'objet (inventaire) dans le flux. */
    private void fIcon(Supplier<String> id, Consumer<String> set) {
        iconSlot(fx, fy - 2, id, set, null);
        fx += 24;
    }

    /** Largeur restante jusqu'au bord droit du panneau. */
    private int restW() { return Math.max(40, left + W - 10 - fx); }
    private int rowsFrom(int y) { return Math.max(3, (top + H - 26 - y) / 12); }

    // ─── Onglet Mobs ───

    private void buildMobs() {
        int ly = top + 46;
        mobList.place(listX(), ly, 134, rowsFrom(ly));
        int by = mobList.bottom() + 4, bx = listX();
        button(bx, by, 22, "§a+", () -> { mobs().add(HordeJson.newMob()); selMob = mobs().size() - 1; changed(); init(); });
        JsonObject m = at(mobs(), selMob);
        button(bx + 24, by, 22, "📋", () -> { if (m != null) copyJson(m, com.wavesurvivor.i18n.WSLang.t("ui.mob") + HordeJson.displayName(m) + " »"); }).active = m != null;
        button(bx + 48, by, 22, "📥", () -> {
            JsonObject o = clipboard();
            if (!HordeJson.looksLikeMob(o)) { status = com.wavesurvivor.i18n.WSLang.t("ui.le_presse_papiers_ne_contient_pas_de_mob"); return; }
            if (!o.has("base_count") && o.has("count")) o.addProperty("base_count", HordeJson.num(o, "count", 1));
            mobs().add(o);
            selMob = mobs().size() - 1;
            status = com.wavesurvivor.i18n.WSLang.t("ui.mob_3127") + HordeJson.displayName(o) + com.wavesurvivor.i18n.WSLang.t("ui.colle");
            changed();
            init();
        });
        button(bx + 72, by, 22, "§c🗑", () -> { if (m == null) return; mobs().remove(selMob); selMob = Math.min(selMob, mobs().size() - 1); changed(); init(); })
                .active = m != null;
        button(bx + 96, by, 18, "↑", () -> selMob = move(mobs(), selMob, -1)).active = m != null && selMob > 0;
        button(bx + 116, by, 18, "↓", () -> selMob = move(mobs(), selMob, 1)).active = m != null && selMob < mobs().size() - 1;
        // Mode Kingdom : ajoute 3 unités de siège prêtes à l'emploi (modifiables comme n'importe quel monstre)
        if (kingdom()) {
            button(bx, by + 22, 134, com.wavesurvivor.i18n.WSLang.t("ui.kgd.add_siege"), () -> {
                String[] presets = {
                        "{\"entity_type\":\"minecraft:ravager\",\"custom_name\":\"§6§l🐏 Bélier\",\"base_count\":2,\"count_increment\":1,\"min_wave\":2,\"max_wave\":0,\"max_health\":90,\"attack_damage\":8,\"movement_speed\":0.24,\"knockback_resistance\":1.0,\"override_stats\":true,\"siege_role\":\"ram\"}",
                        "{\"entity_type\":\"minecraft:creeper\",\"custom_name\":\"§c§l💣 Sapeur\",\"base_count\":3,\"count_increment\":1,\"min_wave\":2,\"max_wave\":0,\"max_health\":24,\"movement_speed\":0.3,\"override_stats\":true,\"siege_role\":\"sapper\"}",
                        "{\"entity_type\":\"minecraft:spider\",\"custom_name\":\"§a§l🕷 Grimpeur\",\"base_count\":3,\"count_increment\":1,\"min_wave\":2,\"max_wave\":0,\"max_health\":24,\"attack_damage\":4,\"movement_speed\":0.33,\"override_stats\":true,\"siege_role\":\"climber\"}",
                        "{\"entity_type\":\"minecraft:pillager\",\"custom_name\":\"§c§l🚩 Porte-étendard\",\"base_count\":1,\"count_increment\":0,\"min_wave\":3,\"max_wave\":0,\"max_health\":40,\"attack_damage\":4,\"movement_speed\":0.27,\"override_stats\":true,\"siege_role\":\"banner\"}"
                };
                for (String presetJson : presets) mobs().add(com.google.gson.JsonParser.parseString(presetJson).getAsJsonObject());
                selMob = mobs().size() - 1;
                status = com.wavesurvivor.i18n.WSLang.t("ui.kgd.siege_added");
                changed();
                init();
            });
        }

        if (m == null) {
            label(detailX(), ly + 20, com.wavesurvivor.i18n.WSLang.t("ui.selectionne_un_mob_a_gauche_ou_ajoute_en"));
            label(detailX(), ly + 32, com.wavesurvivor.i18n.WSLang.t("ui.colle_un_mob_copie_meme_depuis_une_autre"));
            return;
        }
        buildEntityForm(m, true, detailX(), ly);
    }

    private int move(JsonArray a, int sel, int dir) {
        int j = sel + dir;
        if (sel < 0 || j < 0 || j >= a.size()) return sel;
        JsonElement tmp = a.get(sel);
        a.set(sel, a.get(j));
        a.set(j, tmp);
        changed();
        final int nj = j;
        Minecraft.getInstance().tell(this::init);
        return nj;
    }

    /** Fiche d'un mob (isMob) ou d'une entité de vague spéciale. Hauteur fixe : 78 + équipement 30 + loot. */
    private void buildEntityForm(JsonObject m, boolean isMob, int x, int y) {
        label(x, y + 3, com.wavesurvivor.i18n.WSLang.t("ui.type_a1fa"));
        text(x + 44, y, 104, HordeJson.str(m, "entity_type", ""), s -> m.addProperty("entity_type", s.trim()));
        listBtn(x + 151, y - 1, HordeJson.str(m, "entity_type", ""), id -> m.addProperty("entity_type", id));
        eggBtn(x + 170, y - 1, id -> m.addProperty("entity_type", id));
        button(x + 198, y - 1, 16, "◀", () -> { cycleType(m, -1); init(); });
        button(x + 216, y - 1, 16, "▶", () -> { cycleType(m, 1); init(); });
        if (customEntities.contains(HordeJson.str(m, "entity_type", ""))) forkButtons(m, "entity_type", x + 236, y - 1);
        y += 18;
        boolean customType = customEntities.contains(HordeJson.str(m, "entity_type", ""));
        label(x, y + 3, com.wavesurvivor.i18n.WSLang.t("ui.nom"));
        text(x + 44, y, customType ? 118 : 190, HordeJson.str(m, "custom_name", ""), s -> m.addProperty("custom_name", s));
        if (customType) {
            boolean ov = HordeJson.bool(m, "override_stats", false);
            button(x + 166, y - 1, 70, ov ? com.wavesurvivor.i18n.WSLang.t("ui.stats_horde") : com.wavesurvivor.i18n.WSLang.t("ui.stats_entite"), () -> {
                m.addProperty("override_stats", !HordeJson.bool(m, "override_stats", false));
                changed();
                init();
            });
        }
        // 🔗 Synchroniser : même mob ailleurs dans la horde (onglet Mobs + vagues spéciales)
        List<JsonObject> twins = twinsOf(m);
        if (!twins.isEmpty()) {
            button(x + 238, y - 1, 36, "§b🔗 " + twins.size(), () -> {
                for (JsonObject t : twinsOf(m)) syncInto(m, t);
                status = com.wavesurvivor.i18n.WSLang.t("ui.equipement_loot_et_stats_copies_vers") + twins.size() + com.wavesurvivor.i18n.WSLang.t("ui.autre_s") + HordeJson.displayName(m)
                        + com.wavesurvivor.i18n.WSLang.t("ui.nombres_inchanges");
                changed();
                init();
            });
        }
        y += 20;
        flow(x, y);
        if (isMob) {
            fl(kt("ui.nombre", "ui.kgd.weight"));
            fInt(26, HordeJson.num(m, "base_count", 1), v -> m.addProperty("base_count", Math.max(0, v)));
            fl(kt("ui.vague_114f", "ui.kgd.per_assault"));
            fInt(24, HordeJson.num(m, "count_increment", 0), v -> m.addProperty("count_increment", v));
            fl(kt("ui.vagues_a678", "ui.kgd.assaults"));
            fInt(24, HordeJson.num(m, "min_wave", 0), v -> m.addProperty("min_wave", Math.max(0, v)));
            fl("→");
            fInt(24, HordeJson.num(m, "max_wave", 0), v -> m.addProperty("max_wave", Math.max(0, v)));
        } else {
            fl(com.wavesurvivor.i18n.WSLang.t("ui.nombre"));
            fInt(26, HordeJson.num(m, "count", 1), v -> m.addProperty("count", Math.max(0, v)));
        }
        y += 18;
        // Mode Kingdom : Gardien des Portes + rôle de siège (Bélier / Sapeur / Grimpeur)
        if (isMob && kingdom()) {
            flow(x, y);
            boolean isGuardian = HordeJson.bool(m, "kingdom_guardian", false);
            fBtn(74, (isGuardian ? "§6§l⛨ " : "§8⛨ ") + com.wavesurvivor.i18n.WSLang.t("ui.kgd.guardian"), () -> {
                m.addProperty("kingdom_guardian", !HordeJson.bool(m, "kingdom_guardian", false));
                changed();
                init();
            });
            fl(com.wavesurvivor.i18n.WSLang.t("ui.kgd.siege_role"));
            String siegeRoleKey = HordeJson.str(m, "siege_role", "");
            fBtn(96, siegeRoleKey.isEmpty() ? com.wavesurvivor.i18n.WSLang.t("ui.kgd.role_none") : com.wavesurvivor.i18n.WSLang.t("kingdom.role." + siegeRoleKey), () -> {
                String[] roles = {"", "ram", "sapper", "climber", "banner"};
                int roleIdx = java.util.Arrays.asList(roles).indexOf(HordeJson.str(m, "siege_role", ""));
                m.addProperty("siege_role", roles[(roleIdx + 1) % roles.length]);
                changed();
                init();
            });
            y += 18;
        }
        // Mode Kingdom : Portes d'où sort cette unité (toutes, ou une sélection de Portes numérotées)
        if (kingdom()) {
            java.util.Set<Integer> sel = parseGates(HordeJson.str(m, "gates", ""));
            flow(x, y);
            fl(t("ui.kgd.unit_gates"));
            fBtn(54, (sel.isEmpty() ? "§a§l✔ " : "§7") + t("ui.kgd.gates_all"), () -> {
                m.addProperty("gates", "");
                changed();
                init();
            });
            for (int gi = 1; gi <= editorGateCount(); gi++) {
                final int gn = gi;
                fBtn(22, (sel.contains(gi) ? "§e§l" : "§8") + gi, () -> {
                    java.util.Set<Integer> s2 = parseGates(HordeJson.str(m, "gates", ""));
                    if (!s2.remove(gn)) s2.add(gn);
                    StringBuilder sb = new StringBuilder();
                    for (int v : s2) sb.append(sb.length() > 0 ? "," : "").append(v);
                    m.addProperty("gates", sb.toString());
                    changed();
                    init();
                });
            }
            y += 18;
        }
        flow(x, y);
        fl(com.wavesurvivor.i18n.WSLang.t("ui.pv"));
        fDbl(34, HordeJson.dbl(m, "max_health", 20), v -> m.addProperty("max_health", v));
        fl(com.wavesurvivor.i18n.WSLang.t("ui.degats_3efd"));
        fDbl(30, HordeJson.dbl(m, "attack_damage", 3), v -> m.addProperty("attack_damage", v));
        fl(com.wavesurvivor.i18n.WSLang.t("ui.vitesse"));
        fDbl(40, HordeJson.dbl(m, "movement_speed", 0.23), v -> m.addProperty("movement_speed", v));
        // Taille (mod Pehkui, optionnel) : 1 = normale
        fl(t("ui.scale"));
        fDbl(30, HordeJson.dbl(m, "scale", 1.0), v -> m.addProperty("scale", Math.max(0.1, Math.min(10.0, v))));
        if (!com.wavesurvivor.compat.PehkuiCompat.isLoaded()) fl(t("ui.scale_needs_pehkui"));
        y += 22;
        // Briseur de Monolithe : ignore les joueurs, ne vise que le Monolithe (sans créer d'entité custom)
        toggle(x, y, 190, t("ui.unit.targets_altar"), m, "targets_altar", false);
        label(x + 196, y + 4, t("ui.unit.targets_altar_hint"));
        y += 20;
        // Bâtiments : frappe le Monolithe / les murs / les tours à portée ; dégâts par coup (et par explosion)
        toggle(x, y, 172, t("ui.unit.attack_buildings"), m, "attack_buildings", false);
        flow(x + 178, y);
        fl(t("ui.unit.building_damage"));
        fDbl(30, HordeJson.dbl(m, "building_damage", 0), v -> m.addProperty("building_damage", Math.max(0, v)));
        fl(t("ui.unit.building_hint"));
        y += 20;
        // Compétences de l'unité : ◀ n/N ▶ ✎ (réglages propres) ✖ · ＋ menu déroulant (recherche + description)
        JsonArray usk = m.has("skills") && m.get("skills").isJsonArray() ? m.getAsJsonArray("skills") : new JsonArray();
        flow(x, y);
        fl(t("ui.unit.skills"));
        String uSel = null;
        if (usk.isEmpty()) {
            fl(t("ui.skillov.none"));
        } else {
            unitSkillSel = Math.max(0, Math.min(unitSkillSel, usk.size() - 1));
            final int ui = unitSkillSel;
            uSel = usk.get(ui).getAsString();
            final String un = uSel;
            boolean uCustom = m.has("skillOverrides") && m.get("skillOverrides").isJsonObject() && m.getAsJsonObject("skillOverrides").has(un);
            fBtn(14, "◀", () -> { unitSkillSel = (unitSkillSel - 1 + usk.size()) % usk.size(); init(); });
            fl("§f" + (ui + 1) + "/" + usk.size());
            fBtn(14, "▶", () -> { unitSkillSel = (unitSkillSel + 1) % usk.size(); init(); });
            String shown = font.width(un) > 96 ? font.plainSubstrByWidth(un, 90) + "…" : un;
            Button ub = fBtn(118, (uCustom ? "§e✎ " : "§d✎ ") + shown, () -> openUnitSkillOverrides(m, un));
            ub.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(unitSkillTip(un))));
            fBtn(16, "§c✖", () -> {
                JsonArray cur = m.getAsJsonArray("skills");
                for (int i = 0; i < cur.size(); i++) if (cur.get(i).getAsString().equals(un)) { cur.remove(i); break; }
                if (m.has("skillOverrides") && m.get("skillOverrides").isJsonObject()) {
                    boolean still = false;
                    for (JsonElement el : cur) if (el.getAsString().equals(un)) still = true;
                    if (!still) m.getAsJsonObject("skillOverrides").remove(un);
                    if (m.getAsJsonObject("skillOverrides").size() == 0) m.remove("skillOverrides");
                }
                if (cur.isEmpty()) m.remove("skills");
                unitSkillSel = Math.max(0, ui - 1);
                changed();
                init();
            });
        }
        if (!skillDefs().isEmpty()) fBtn(150, t("ui.skill.add_pick"), () -> openUnitSkillPicker(m));
        y += 18;
        if (uSel != null) {
            JsonObject def = skillDef(uSel);
            String td = def == null ? "" : CustomEntityEditorScreen.typeDesc(HordeJson.str(def, "skillType", ""));
            if (!td.isEmpty()) {
                String line = "ℹ " + td;
                int maxW = left + W - 14 - x;
                if (font.width(line) > maxW) line = font.plainSubstrByWidth(line, maxW - 6) + "…";
                label(x, y, "§8" + line);
                y += 11;
            }
        }
        setForm(m, "equipment", "loot_table", x, y + 10, customEntities.contains(HordeJson.str(m, "entity_type", "")));
        // Interrupteurs de drop d'équipement (à droite des 6 cases)
        int bx = x + 6 * EQUIP_STEP + 6, byy = formEquipY + 11;
        boolean noWeapon = HordeJson.bool(m, "no_weapon_drop", false);
        boolean noArmor = HordeJson.bool(m, "no_armor_drop", false);
        button(bx, byy, 92, com.wavesurvivor.i18n.WSLang.t("ui.drop_armes") + (noWeapon ? com.wavesurvivor.i18n.WSLang.t("ui.non_0835") : com.wavesurvivor.i18n.WSLang.t("ui.oui_f7a6")), () -> {
            m.addProperty("no_weapon_drop", !HordeJson.bool(m, "no_weapon_drop", false));
            changed();
            init();
        });
        button(bx + 96, byy, 96, com.wavesurvivor.i18n.WSLang.t("ui.drop_armure") + (noArmor ? com.wavesurvivor.i18n.WSLang.t("ui.non_0835") : com.wavesurvivor.i18n.WSLang.t("ui.oui_f7a6")), () -> {
            m.addProperty("no_armor_drop", !HordeJson.bool(m, "no_armor_drop", false));
            changed();
            init();
        });
    }

    private void cycleType(JsonObject m, int dir) {
        String cur = HordeJson.str(m, "entity_type", "");
        String t = cycle(allTypes, cur, dir);
        m.addProperty("entity_type", t);
        String name = HordeJson.str(m, "custom_name", "");
        if (name.isBlank() || name.equalsIgnoreCase(pretty(cur))) m.addProperty("custom_name", pretty(t));
        changed();
    }

    // ─── Bloc équipement + loot (mobs, entités de vague, boss) ───

    private void setForm(JsonObject owner, String equipKey, String lootKey, int x, int equipY, boolean isCustom) {
        formOwner = owner;
        formEquipKey = equipKey;
        formLootKey = lootKey;
        formX = x;
        formEquipY = equipY;
        formLootY = equipY + 30;
        formIsCustom = isCustom;
        buildLoot();
    }

    private int lootRows() { return Math.max(1, (top + H - 8 - (formLootY + 14)) / 17); }

    private void buildLoot() {
        JsonObject m = formOwner;
        int x = formX, y = formLootY, c = x + LOOT_COL;
        button(c + 60, y - 2, 50, com.wavesurvivor.i18n.WSLang.t("ui.loot_ab8d"), () -> openPicker(com.wavesurvivor.i18n.WSLang.t("ui.ajouter_au_loot"), id -> {
            JsonObject l = new JsonObject();
            l.addProperty("item", id);
            l.addProperty("minQty", 1);
            l.addProperty("maxQty", 1);
            l.addProperty("chance", 50);
            HordeJson.arr(m, formLootKey).add(l);
            changed();
            init();
        }));
        JsonArray loot = HordeJson.arr(m, formLootKey);
        int rows = lootRows();
        lootScroll = Math.max(0, Math.min(lootScroll, Math.max(0, loot.size() - rows)));
        for (int i = 0; i < rows && i + lootScroll < loot.size(); i++) {
            final int idx = i + lootScroll;
            JsonObject l = at(loot, idx);
            if (l == null) continue;
            int ly = y + 14 + i * 17;
            intBox(c, ly, 24, HordeJson.num(l, "minQty", 1), v -> l.addProperty("minQty", Math.max(0, v)));
            intBox(c + 34, ly, 24, HordeJson.num(l, "maxQty", 1), v -> l.addProperty("maxQty", Math.max(0, v)));
            dblBox(c + 68, ly, 32, HordeJson.dbl(l, "chance", 50), v -> l.addProperty("chance", Math.min(100, v)));
            button(c + 114, ly - 1, 16, "§c✖", () -> { loot.remove(idx); changed(); init(); });
        }
        if (loot.size() > rows) {
            button(c + 134, y + 14, 14, "▲", () -> { lootScroll--; init(); }).active = lootScroll > 0;
            button(c + 134, y + 14 + (rows - 1) * 17, 14, "▼", () -> { lootScroll++; init(); }).active = lootScroll + rows < loot.size();
        }
    }

    /** Colonne des quantités du loot (le nom de l'objet occupe l'espace avant). */
    private static final int LOOT_COL = 132;
    private static final int EQUIP_STEP = 24;
    private List<Component> formTooltip = null;

    private void renderForm(GuiGraphics g, int mx, int my) {
        formTooltip = null;
        JsonObject m = formOwner;
        if (m == null) return;
        if (formIsCustom) {
            boolean ov = formOwner.has("override_stats") && HordeJson.bool(formOwner, "override_stats", false);
            g.drawString(font, com.wavesurvivor.i18n.WSLang.t("ui.equipement_ici_remplace_loot_s_ajoute") + (ov ? com.wavesurvivor.i18n.WSLang.t("ui.stats_de_la_horde") : com.wavesurvivor.i18n.WSLang.t("ui.stats_de_l_entite")),
                    formX, formEquipY - 10, 0xFFFFFFFF);
        }
        g.drawString(font, com.wavesurvivor.i18n.WSLang.t("ui.equipement_survol_emplacement_clic_chois"), formX, formEquipY, 0xFFFFFFFF);
        JsonObject eq = HordeJson.obj(m, formEquipKey);
        for (int s = 0; s < 6; s++) {
            int ex = formX + s * EQUIP_STEP, ey = formEquipY + 10;
            boolean hov = mx >= ex && mx < ex + 18 && my >= ey && my < ey + 18;
            g.fill(ex, ey, ex + 18, ey + 18, hov ? 0xFF45455A : 0xFF2A2A30);
            g.fill(ex, ey, ex + 18, ey + 1, 0xFF555560);
            JsonObject slot = eq.has(EQUIP_SLOTS[s]) && eq.get(EQUIP_SLOTS[s]).isJsonObject() ? eq.getAsJsonObject(EQUIP_SLOTS[s]) : null;
            String id = slot != null ? HordeJson.str(slot, "item", "") : "";
            ItemStack st = id.isBlank() ? ItemStack.EMPTY : LootItems.resolve(id, 1);
            if (!st.isEmpty()) g.renderItem(st, ex + 1, ey + 1);
            if (hov) {
                formTooltip = new ArrayList<>();
                formTooltip.add(Component.literal("§e" + com.wavesurvivor.i18n.WSLang.t(EQUIP_LABELS[s])));
                formTooltip.add(st.isEmpty() ? Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.vide_b333")) : st.getHoverName());
            }
        }
        g.drawString(font, com.wavesurvivor.i18n.WSLang.t("ui.loot"), formX, formLootY, 0xFFFFFFFF);
        int c = formX + LOOT_COL;
        g.drawString(font, "§8min", c + 2, formLootY, 0xFFFFFFFF);
        g.drawString(font, "§8max", c + 36, formLootY, 0xFFFFFFFF);
        JsonArray loot = HordeJson.arr(m, formLootKey);
        int rows = lootRows();
        for (int i = 0; i < rows && i + lootScroll < loot.size(); i++) {
            JsonObject l = at(loot, i + lootScroll);
            if (l == null) continue;
            int ly = formLootY + 14 + i * 17;
            ItemStack st = LootItems.resolve(HordeJson.str(l, "item", ""), 1);
            if (st.isEmpty()) st = new ItemStack(Items.BARRIER);
            g.renderItem(st, formX, ly - 2);
            String nm = st.getHoverName().getString();
            if (font.width(nm) > LOOT_COL - 26) nm = font.plainSubstrByWidth(nm, LOOT_COL - 30) + "…";
            g.drawString(font, "§f" + nm, formX + 18, ly + 3, 0xFFFFFFFF);
            g.drawString(font, "§8–", c + 27, ly + 3, 0xFFFFFFFF);
            g.drawString(font, "§8%", c + 103, ly + 3, 0xFFFFFFFF);
        }
        if (loot.isEmpty()) g.drawString(font, com.wavesurvivor.i18n.WSLang.t("ui.aucun_loot"), formX, formLootY + 16, 0xFFFFFFFF);
    }

    private boolean clickForm(double mx, double my, int button) {
        JsonObject m = formOwner;
        if (m == null) return false;
        for (int s = 0; s < 6; s++) {
            int ex = formX + s * EQUIP_STEP, ey = formEquipY + 10;
            if (mx >= ex && mx < ex + 18 && my >= ey && my < ey + 18) {
                final String slot = EQUIP_SLOTS[s];
                final String key = formEquipKey;
                if (button == 1) {
                    HordeJson.obj(m, key).remove(slot);
                    changed();
                } else {
                    openPicker(com.wavesurvivor.i18n.WSLang.t("ui.equipement") + com.wavesurvivor.i18n.WSLang.t(EQUIP_LABELS[s]), id -> {
                        JsonObject e = new JsonObject();
                        e.addProperty("item", id);
                        e.addProperty("dropChance", 5);
                        HordeJson.obj(m, key).add(slot, e);
                        changed();
                    });
                }
                return true;
            }
        }
        return false;
    }

    // ─── Onglet Vagues spéciales ───

    private void buildSpecials() {
        JsonObject cd = cd();
        int y0 = top + 46;
        toggle(listX(), y0, 134, com.wavesurvivor.i18n.WSLang.t("ui.speciales_5feb"), cd, "useSpecialWaves", false);
        label(detailX(), y0 + 4, kt("ui.chance_qu_une_vague_soit_speciale", "ui.kgd.special_chance"));
        intBox(detailX() + 176, y0 + 1, 26, HordeJson.num(cd, "specialWaveChance", 35),
                v -> cd.addProperty("specialWaveChance", Math.max(0, Math.min(100, v))));
        label(detailX() + 206, y0 + 4, "%");

        int ly = y0 + 22;
        specialList.place(listX(), ly, 134, rowsFrom(ly));
        int by = specialList.bottom() + 4, bx = listX();
        JsonObject s = special();
        button(bx, by, 22, "§a+", () -> {
            JsonObject w = new JsonObject();
            w.addProperty("name", com.wavesurvivor.i18n.WSLang.t("ui.nouvelle_vague"));
            w.addProperty("chance", 10);
            w.add("entities", new JsonArray());
            specials().add(w);
            selSpecial = specials().size() - 1;
            selSpecialEnt = -1;
            changed();
            init();
        });
        button(bx + 24, by, 22, "📋", () -> { if (s != null) copyJson(s, com.wavesurvivor.i18n.WSLang.t("ui.vague_f938") + HordeJson.str(s, "name", "") + " »"); }).active = s != null;
        button(bx + 48, by, 22, "📥", () -> {
            JsonObject o = clipboard();
            if (o == null || !o.has("entities") || !o.get("entities").isJsonArray()) {
                status = com.wavesurvivor.i18n.WSLang.t("ui.le_presse_papiers_ne_contient_pas_de_vag");
                return;
            }
            specials().add(o);
            selSpecial = specials().size() - 1;
            selSpecialEnt = -1;
            status = com.wavesurvivor.i18n.WSLang.t("ui.vague_a964") + HordeJson.str(o, "name", "") + com.wavesurvivor.i18n.WSLang.t("ui.collee");
            changed();
            init();
        });
        button(bx + 72, by, 22, "§c🗑", () -> {
            if (s == null) return;
            specials().remove(selSpecial);
            selSpecial = Math.min(selSpecial, specials().size() - 1);
            selSpecialEnt = -1;
            changed();
            init();
        }).active = s != null;

        if (s == null) {
            label(detailX(), ly + 20, com.wavesurvivor.i18n.WSLang.t("ui.selectionne_une_vague_speciale_ou_crees"));
            label(detailX(), ly + 32, com.wavesurvivor.i18n.WSLang.t("ui.poids_0_desactivee_plus_le_poids_est_gra"));
            return;
        }

        int x = detailX();
        JsonObject ent = at(specialEnts(), selSpecialEnt);
        if (ent == null) {
            label(x, ly + 3, com.wavesurvivor.i18n.WSLang.t("ui.nom"));
            text(x + 34, ly, 140, HordeJson.str(s, "name", ""), v -> s.addProperty("name", v));
            label(x + 180, ly + 3, com.wavesurvivor.i18n.WSLang.t("ui.poids_ae90"));
            intBox(x + 212, ly, 26, HordeJson.num(s, "chance", 10), v -> s.addProperty("chance", Math.max(0, v)));
            label(x, ly + 22, com.wavesurvivor.i18n.WSLang.t("ui.composition_clic_editer_une_entite"));
            int ey = ly + 34;
            specialEntList.place(x, ey, 200, Math.max(3, (top + H - 30 - ey) / 12));
            int eb = specialEntList.bottom() + 4;
            button(x, eb, 60, com.wavesurvivor.i18n.WSLang.t("ui.entite_950b"), () -> {
                JsonObject e = HordeJson.newMob();
                e.remove("base_count"); e.remove("count_increment"); e.remove("min_wave"); e.remove("max_wave");
                e.addProperty("count", 3);
                specialEnts().add(e);
                selSpecialEnt = specialEnts().size() - 1;
                changed();
                init();
            });
            button(x + 62, eb, 70, com.wavesurvivor.i18n.WSLang.t("ui.coller_mob"), () -> {
                JsonObject o = clipboard();
                if (!HordeJson.looksLikeMob(o)) { status = com.wavesurvivor.i18n.WSLang.t("ui.le_presse_papiers_ne_contient_pas_de_mob"); return; }
                if (!o.has("count")) o.addProperty("count", Math.max(1, HordeJson.num(o, "base_count", 1)));
                specialEnts().add(o);
                status = "§a📥 « " + HordeJson.displayName(o) + com.wavesurvivor.i18n.WSLang.t("ui.ajoute_a_la_vague");
                changed();
                init();
            });
        } else {
            button(x, ly - 1, 80, com.wavesurvivor.i18n.WSLang.t("ui.composition"), () -> { selSpecialEnt = -1; init(); });
            button(x + 84, ly - 1, 22, "📋", () -> copyJson(ent, com.wavesurvivor.i18n.WSLang.t("ui.entite_e8cc") + HordeJson.displayName(ent) + " »"));
            button(x + 108, ly - 1, 22, "§c🗑", () -> {
                specialEnts().remove(selSpecialEnt);
                selSpecialEnt = -1;
                changed();
                init();
            });
            label(x + 136, ly + 3, "§8" + HordeJson.str(s, "name", "").trim());
            buildEntityForm(ent, false, x, ly + 18);
        }
    }

    // ─── Onglet Boss ───

    private void buildBosses() {
        JsonObject cd = cd();
        int y0 = top + 46;
        toggle(listX(), y0, 134, com.wavesurvivor.i18n.WSLang.t("ui.boss_d908"), cd, "useBossWaves", false);
        if (kingdom()) label(detailX(), y0 + 4, com.wavesurvivor.i18n.WSLang.t("ui.kgd.boss_hint"));
        int ly = y0 + 22;
        bossList.place(listX(), ly, 134, rowsFrom(ly));
        int by = bossList.bottom() + 4, bx = listX();
        JsonObject b = at(bosses(), selBoss);
        button(bx, by, 22, "§a+", () -> {
            JsonObject nb = new JsonObject();
            nb.addProperty("waveNumber", Math.max(1, HordeJson.num(cd, "totalWaves", 5)));
            nb.addProperty("bossName", com.wavesurvivor.i18n.WSLang.t("ui.nouveau_boss"));
            nb.addProperty("entityType", "minecraft:zombie");
            nb.addProperty("useCustomEntity", false);
            nb.addProperty("customEntityName", "");
            nb.addProperty("maxHealth", 150);
            nb.addProperty("attackDamage", 8);
            nb.addProperty("movementSpeed", 0.25);
            nb.addProperty("knockbackResistance", 0.8);
            nb.add("equipment", new JsonObject());
            nb.addProperty("useMinionSummon", false);
            nb.add("minionConfig", new JsonObject());
            nb.add("lootTable", new JsonArray());
            nb.addProperty("spawnMessage", com.wavesurvivor.i18n.WSLang.t("ui.boss_name_apparait"));
            nb.addProperty("deathMessage", com.wavesurvivor.i18n.WSLang.t("ui.victoire_name_a_ete_vaincu"));
            bosses().add(nb);
            selBoss = bosses().size() - 1;
            changed();
            init();
        });
        button(bx + 24, by, 22, "📋", () -> { if (b != null) copyJson(b, com.wavesurvivor.i18n.WSLang.t("ui.boss_2a9b") + HordeJson.str(b, "bossName", "") + " »"); }).active = b != null;
        button(bx + 48, by, 22, "📥", () -> {
            JsonObject o = clipboard();
            if (o == null || !o.has("bossName")) { status = com.wavesurvivor.i18n.WSLang.t("ui.le_presse_papiers_ne_contient_pas_de_bos"); return; }
            bosses().add(o);
            selBoss = bosses().size() - 1;
            status = com.wavesurvivor.i18n.WSLang.t("ui.boss_e347") + HordeJson.str(o, "bossName", "") + com.wavesurvivor.i18n.WSLang.t("ui.colle");
            changed();
            init();
        });
        button(bx + 72, by, 22, "§c🗑", () -> {
            if (b == null) return;
            bosses().remove(selBoss);
            selBoss = Math.min(selBoss, bosses().size() - 1);
            changed();
            init();
        }).active = b != null;

        if (b == null) {
            label(detailX(), ly + 20, com.wavesurvivor.i18n.WSLang.t("ui.selectionne_un_boss_ou_crees_en_un"));
            return;
        }
        int x = detailX(), y = ly;
        flow(x, y);
        fl(kt("ui.vague_9a6d", "ui.kgd.boss_assault"));
        fInt(26, HordeJson.num(b, "waveNumber", 1), v -> b.addProperty("waveNumber", Math.max(1, v)));
        fl(com.wavesurvivor.i18n.WSLang.t("ui.nom"));
        fText(Math.min(kingdom() ? 150 : 200, restW()), HordeJson.str(b, "bossName", ""), v -> b.addProperty("bossName", v));
        // Mode Kingdom : Porte d'où sort ce boss d'Assaut (Hasard, 1, 2, 3…)
        if (kingdom()) {
            int bg = HordeJson.num(b, "gate", 0);
            fl(t("ui.kgd.boss_gate"));
            fBtn(58, bg <= 0 ? "§7" + t("ui.kgd.gate_random") : "§e" + t("ui.gates.portal_n_short") + bg, () -> {
                int nb = HordeJson.num(b, "gate", 0) + 1;
                b.addProperty("gate", nb > editorGateCount() ? 0 : nb);
                changed();
                init();
            });
        }
        y += 18;
        boolean custom = HordeJson.bool(b, "useCustomEntity", false);
        button(x, y - 1, 64, custom ? com.wavesurvivor.i18n.WSLang.t("ui.custom_0434") : com.wavesurvivor.i18n.WSLang.t("ui.vanilla_58cf"), () -> {
            b.addProperty("useCustomEntity", !HordeJson.bool(b, "useCustomEntity", false));
            if (HordeJson.bool(b, "useCustomEntity", false) && HordeJson.str(b, "customEntityName", "").isBlank() && !customEntities.isEmpty()) {
                b.addProperty("customEntityName", customEntities.get(0));
            }
            changed();
            init();
        });
        String typeKey = custom ? "customEntityName" : "entityType";
        List<String> pool = custom ? customEntities : vanillaTypes;
        text(x + 68, y, 92, HordeJson.str(b, typeKey, ""), v -> b.addProperty(typeKey, v.trim()));
        listBtn(x + 163, y - 1, HordeJson.str(b, typeKey, ""), id -> {
            boolean isCustom = customEntities.contains(id);
            b.addProperty("useCustomEntity", isCustom);
            b.addProperty(isCustom ? "customEntityName" : "entityType", id);
        });
        eggBtn(x + 181, y - 1, id -> { b.addProperty("entityType", id); b.addProperty("useCustomEntity", false); });
        button(x + 208, y - 1, 16, "◀", () -> { b.addProperty(typeKey, cycle(pool, HordeJson.str(b, typeKey, ""), -1)); changed(); init(); });
        button(x + 226, y - 1, 16, "▶", () -> { b.addProperty(typeKey, cycle(pool, HordeJson.str(b, typeKey, ""), 1)); changed(); init(); });
        if (custom && customEntities.contains(HordeJson.str(b, "customEntityName", ""))) forkButtons(b, "customEntityName", x + 244, y - 1);
        y += 18;
        flow(x, y);
        fl(com.wavesurvivor.i18n.WSLang.t("ui.pv"));
        fDbl(36, HordeJson.dbl(b, "maxHealth", 150), v -> b.addProperty("maxHealth", v));
        fl(com.wavesurvivor.i18n.WSLang.t("ui.degats_3efd"));
        fDbl(30, HordeJson.dbl(b, "attackDamage", 8), v -> b.addProperty("attackDamage", v));
        fl(com.wavesurvivor.i18n.WSLang.t("ui.vitesse"));
        fDbl(34, HordeJson.dbl(b, "movementSpeed", 0.25), v -> b.addProperty("movementSpeed", v));
        fl(com.wavesurvivor.i18n.WSLang.t("ui.recul"));
        fDbl(28, HordeJson.dbl(b, "knockbackResistance", 0.8), v -> b.addProperty("knockbackResistance", v));
        // Taille du boss (mod Pehkui, optionnel) : 1 = normale, 3 = géant
        fl(t("ui.scale"));
        fDbl(28, HordeJson.dbl(b, "scale", 1.0), v -> b.addProperty("scale", Math.max(0.1, Math.min(10.0, v))));
        if (!com.wavesurvivor.compat.PehkuiCompat.isLoaded()) fl(t("ui.scale_needs_pehkui"));
        y += 18;
        JsonObject mc = HordeJson.obj(b, "minionConfig");
        boolean inv = HordeJson.bool(b, "useMinionSummon", false);
        button(x, y - 1, 64, inv ? com.wavesurvivor.i18n.WSLang.t("ui.invoc_oui") : com.wavesurvivor.i18n.WSLang.t("ui.invoc_non"), () -> {
            b.addProperty("useMinionSummon", !HordeJson.bool(b, "useMinionSummon", false));
            if (HordeJson.bool(b, "useMinionSummon", false)) {
                if (!mc.has("triggerType")) mc.addProperty("triggerType", "interval");
                if (!mc.has("triggerValue")) mc.addProperty("triggerValue", 30);
                if (!mc.has("minionType")) mc.addProperty("minionType", "minecraft:zombie");
                if (!mc.has("minionCount")) mc.addProperty("minionCount", 2);
            }
            changed();
            init();
        });
        if (inv) {
            flow(x + 70, y);
            fText(72, HordeJson.str(mc, "minionType", "minecraft:zombie"), v -> mc.addProperty("minionType", v.trim()));
            fBtn(16, "§e▼", () -> openEntityPicker(HordeJson.str(mc, "minionType", ""), id -> mc.addProperty("minionType", id)));
            fBtn(24, t("ui.egg.btn"), () -> openEggPicker(id -> mc.addProperty("minionType", id), false));
            fx -= 4;
            fBtn(14, "◀", () -> { mc.addProperty("minionType", cycle(allTypes, HordeJson.str(mc, "minionType", ""), -1)); changed(); init(); });
            fBtn(14, "▶", () -> { mc.addProperty("minionType", cycle(allTypes, HordeJson.str(mc, "minionType", ""), 1)); changed(); init(); });
            fl("×");
            fInt(22, HordeJson.num(mc, "minionCount", 2), v -> mc.addProperty("minionCount", Math.max(1, v)));
            fl(com.wavesurvivor.i18n.WSLang.t("ui.toutes_les_c0b6"));
            fInt(26, HordeJson.num(mc, "triggerValue", 30), v -> mc.addProperty("triggerValue", Math.max(1, v)));
            fl("s");
        }
        y += 18;
        label(x, y + 3, com.wavesurvivor.i18n.WSLang.t("ui.message"));
        text(x + 46, y, Math.min(300, left + W - 10 - (x + 46)), HordeJson.str(b, "spawnMessage", ""), v -> b.addProperty("spawnMessage", v));
        y += 20;
        // ── Sous-onglets : Boss / Sbires (si le boss invoque) / Difficulté ──
        if (!inv && bossSub == 1) bossSub = 0;
        button(x, y - 1, 70, (bossSub == 0 ? "§e" : "§7") + t("ui.minion.tab_boss"), () -> { bossSub = 0; init(); });
        int subX = x + 74;
        if (inv) {
            button(subX, y - 1, 90, (bossSub == 1 ? "§e" : "§7") + t("ui.minion.tab_minions"), () -> { bossSub = 1; init(); });
            subX += 94;
        }
        button(subX, y - 1, 96, (bossSub == 2 ? "§e" : "§7") + t("ui.bossdiff.tab"), () -> { bossSub = 2; init(); });
        y += 20;
        if (bossSub == 2) { buildBossDifficulty(b, x, y); return; }
        if (inv && bossSub == 1) {
            // Stats des sbires (l'objet n'est créé qu'à la première modification : sinon stats d'origine du monstre)
            JsonObject ms = mc.has("minionStats") && mc.get("minionStats").isJsonObject() ? mc.getAsJsonObject("minionStats") : new JsonObject();
            flow(x, y);
            fl(t("ui.pv"));
            fDbl(34, HordeJson.dbl(ms, "maxHealth", 20), v -> HordeJson.obj(mc, "minionStats").addProperty("maxHealth", Math.max(1, v)));
            fl(t("ui.minion.dmg"));
            fDbl(28, HordeJson.dbl(ms, "attackDamage", 3), v -> HordeJson.obj(mc, "minionStats").addProperty("attackDamage", Math.max(0, v)));
            fl(t("ui.minion.speed"));
            fDbl(34, HordeJson.dbl(ms, "movementSpeed", 0.25), v -> HordeJson.obj(mc, "minionStats").addProperty("movementSpeed", Math.max(0.05, v)));
            // Taille des sbires (mod Pehkui, optionnel) : 1 = normale
            fl(t("ui.scale"));
            fDbl(28, HordeJson.dbl(ms, "scale", 1.0), v -> HordeJson.obj(mc, "minionStats").addProperty("scale", Math.max(0.1, Math.min(10.0, v))));
            if (!com.wavesurvivor.compat.PehkuiCompat.isLoaded()) fl(t("ui.scale_needs_pehkui"));
            y += 20;
            setForm(mc, "minionEquipment", "minionLoot", x, y + 10, false); // équipement + butin des sbires
            return;
        }
        setForm(b, "equipment", "lootTable", x, y + 10, custom);
    }

    /** Sous-onglet « 🔥 Difficulté » d'un boss (Boss 2.0) : mise à l'échelle, phases, anti-exploitation, bouclier, plafond. */
    private void buildBossDifficulty(JsonObject b, int x, int y) {
        JsonObject d = HordeJson.obj(b, "difficulty");
        toggle(x, y, 150, t("ui.bossdiff.enabled"), d, "enabled", true);
        flow(x + 160, y);
        fl(t("ui.bossdiff.hp_per_player"));
        fInt(28, (int) Math.round(HordeJson.dbl(d, "hpPerExtraPlayer", 0.4) * 100), v -> d.addProperty("hpPerExtraPlayer", Math.max(0, v) / 100.0));
        fl("%");
        y += 22;
        toggle(x, y, 150, t("ui.bossdiff.phases"), d, "phases", true);
        flow(x + 160, y);
        fl(t("ui.bossdiff.speed"));
        fInt(24, (int) Math.round(HordeJson.dbl(d, "phaseSpeedBonus", 0.15) * 100), v -> d.addProperty("phaseSpeedBonus", Math.max(0, v) / 100.0));
        fl(t("ui.bossdiff.dmg"));
        fInt(24, (int) Math.round(HordeJson.dbl(d, "phaseDamageBonus", 0.15) * 100), v -> d.addProperty("phaseDamageBonus", Math.max(0, v) / 100.0));
        fl(t("ui.bossdiff.cooldown"));
        fDbl(30, HordeJson.dbl(d, "phaseCooldownMult", 0.8), v -> d.addProperty("phaseCooldownMult", Math.max(0.2, Math.min(1, v))));
        y += 22;
        toggle(x, y, 150, t("ui.bossdiff.anticheese"), d, "antiCheese", true);
        flow(x + 160, y);
        fl(t("ui.bossdiff.leash"));
        fInt(24, HordeJson.num(d, "leashDistance", 20), v -> d.addProperty("leashDistance", Math.max(6, v)));
        fl(t("ui.bossdiff.leash_delay"));
        fInt(20, HordeJson.num(d, "leashSeconds", 5), v -> d.addProperty("leashSeconds", Math.max(2, v)));
        fl("s");
        y += 22;
        toggle(x, y, 150, t("ui.bossdiff.shield"), d, "reflectShield", true);
        flow(x + 160, y);
        fl(t("ui.bossdiff.every"));
        fInt(24, HordeJson.num(d, "reflectEverySeconds", 30), v -> d.addProperty("reflectEverySeconds", Math.max(5, v)));
        fl(t("ui.bossdiff.during"));
        fInt(20, HordeJson.num(d, "reflectDurationSeconds", 5), v -> d.addProperty("reflectDurationSeconds", Math.max(1, v)));
        fl("s");
        y += 22;
        flow(x, y);
        fl(t("ui.bossdiff.cap"));
        fDbl(30, HordeJson.dbl(d, "damageCapPercent", 8), v -> d.addProperty("damageCapPercent", Math.max(0, Math.min(100, v))));
        fl(t("ui.bossdiff.cap_unit"));
        y += 22;
        toggle(x, y, 150, t("ui.bossdiff.affixes"), d, "affixes", true);
        boolean fixedAff = "fixed".equalsIgnoreCase(HordeJson.str(d, "affixMode", "random"));
        flow(x + 160, y);
        fBtn(84, fixedAff ? t("ui.bossdiff.mode_fixed") : t("ui.bossdiff.mode_random"), () -> {
            d.addProperty("affixMode", "fixed".equalsIgnoreCase(HordeJson.str(d, "affixMode", "random")) ? "random" : "fixed");
            changed();
            init();
        });
        if (!fixedAff) {
            fl(t("ui.bossdiff.affix_count"));
            fInt(18, HordeJson.num(d, "affixMin", 1), v -> d.addProperty("affixMin", Math.max(0, Math.min(14, v))));
            fl("→");
            fInt(18, HordeJson.num(d, "affixMax", 2), v -> d.addProperty("affixMax", Math.max(0, Math.min(14, v))));
        }
        y += 20;
        // Pastilles des 14 affixes : clic = activer / désactiver ; survol = ce que fait l'affixe
        java.util.Set<String> onAff = new java.util.LinkedHashSet<>();
        if (d.has("allowedAffixes") && d.get("allowedAffixes").isJsonArray()) {
            for (JsonElement ae : d.getAsJsonArray("allowedAffixes")) if (ae.isJsonPrimitive()) onAff.add(ae.getAsString());
        }
        String[] affIds = {"vampiric", "armored", "summoner", "swift", "reflector", "volatile", "glacial", "infernal",
                "stormcaller", "blinking", "regenerating", "berserker", "magnetic", "toxic"};
        int cx = x, maxX = left + W - 10;
        for (String id : affIds) {
            String lab = com.wavesurvivor.i18n.WSLang.t("boss.affix." + id);
            String plain = lab.replaceAll("§.", "");
            boolean onA = onAff.contains(id);
            int bw = font.width(plain) + 10;
            if (cx + bw > maxX) { cx = x; y += 18; }
            Button chip = button(cx, y, bw, onA ? lab : "§8" + plain, () -> {
                JsonArray na = new JsonArray();
                for (String other : affIds) {
                    boolean keep = other.equals(id) ? !onAff.contains(id) : onAff.contains(other);
                    if (keep) na.add(other);
                }
                d.add("allowedAffixes", na);
                changed();
                init();
            });
            chip.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(lab + "\n§7"
                    + com.wavesurvivor.i18n.WSLang.t("boss.affix." + id + ".desc"))));
            cx += bw + 3;
        }
        y += 20;
        label(x, y, fixedAff ? (onAff.isEmpty() ? t("ui.bossdiff.chips_fixed_none") : t("ui.bossdiff.chips_fixed")) : t("ui.bossdiff.chips_random"));
        y += 14;
        label(x, y, t("ui.bossdiff.hint1"));
        label(x, y + 11, t("ui.bossdiff.hint2"));
    }

    // ─── 🔗 Synchronisation d'un même mob entre l'onglet Mobs et les vagues spéciales ───

    /** Autres entrées de la horde avec le même type ET le même nom (hors elle-même). */
    private List<JsonObject> twinsOf(JsonObject m) {
        String type = HordeJson.str(m, "entity_type", "");
        String name = HordeJson.displayName(m);
        List<JsonObject> out = new ArrayList<>();
        List<JsonArray> pools = new ArrayList<>();
        pools.add(mobs());
        for (JsonElement e : specials()) if (e.isJsonObject()) pools.add(HordeJson.arr(e.getAsJsonObject(), "entities"));
        for (JsonArray pool : pools) {
            for (JsonElement e : pool) {
                if (!e.isJsonObject()) continue;
                JsonObject o = e.getAsJsonObject();
                if (o == m) continue;
                if (HordeJson.str(o, "entity_type", "").equalsIgnoreCase(type) && HordeJson.displayName(o).equalsIgnoreCase(name)) out.add(o);
            }
        }
        return out;
    }

    // ─── Compétences des unités (onglet Mobs) ───

    /** Compétence sélectionnée dans la liste de l'unité en cours. */
    private int unitSkillSel = 0;

    /** Définitions des compétences (catalogue envoyé par le serveur à l'ouverture de l'éditeur). */
    private JsonArray skillDefs() {
        JsonObject cat = parent != null ? parent.skillCatalog : null;
        return cat != null && cat.has("skills") && cat.get("skills").isJsonArray() ? cat.getAsJsonArray("skills") : new JsonArray();
    }

    private JsonObject skillDef(String name) {
        for (JsonElement el : skillDefs()) {
            if (el.isJsonObject() && HordeJson.str(el.getAsJsonObject(), "skillName", "").equalsIgnoreCase(name)) return el.getAsJsonObject();
        }
        return null;
    }

    /** Infobulle : nom, type, ce que fait le type, description personnalisée. */
    private String unitSkillTip(String name) {
        JsonObject d = skillDef(name);
        if (d == null) return "§d" + name;
        String type = HordeJson.str(d, "skillType", "");
        StringBuilder sb = new StringBuilder("§d§l" + name + "\n§b" + CustomEntityEditorScreen.typeLabel(type));
        String td = CustomEntityEditorScreen.typeDesc(type);
        if (!td.isEmpty()) sb.append("\n§7").append(td);
        String desc = HordeJson.str(d, "description", "");
        if (!desc.isBlank()) sb.append("\n§f« ").append(desc).append(" »");
        return sb.toString();
    }

    /** Menu déroulant (recherche, type, description) : double-clic pour ajouter la compétence à l'unité. */
    private void openUnitSkillPicker(JsonObject m) {
        JsonArray have = m.has("skills") && m.get("skills").isJsonArray() ? m.getAsJsonArray("skills") : new JsonArray();
        List<String[]> entries = new ArrayList<>();
        for (JsonElement el : skillDefs()) {
            if (!el.isJsonObject()) continue;
            String n = HordeJson.str(el.getAsJsonObject(), "skillName", "");
            if (n.isEmpty()) continue;
            boolean has = false;
            for (JsonElement h : have) if (h.getAsString().equalsIgnoreCase(n)) has = true;
            String type = CustomEntityEditorScreen.typeLabel(HordeJson.str(el.getAsJsonObject(), "skillType", "")).replaceAll("§.", "");
            entries.add(new String[]{n, has ? "§8" + n + " ✔" : "§d" + n, type});
        }
        entries.sort((a, b) -> a[0].compareToIgnoreCase(b[0]));
        minecraft.setScreen(new ListPickerScreen(this, com.wavesurvivor.i18n.WSLang.t("ui.skill.pick_title"), entries, "", n -> {
            JsonArray arr = m.has("skills") && m.get("skills").isJsonArray() ? m.getAsJsonArray("skills") : new JsonArray();
            boolean has = false;
            for (JsonElement h : arr) if (h.getAsString().equalsIgnoreCase(n)) has = true;
            if (!has) {
                arr.add(n);
                m.add("skills", arr);
                unitSkillSel = arr.size() - 1;
                changed();
            }
        }, true, this::unitSkillTip));
    }

    /** Réglages de la compétence POUR CETTE UNITÉ seulement (même fenêtre que l'éditeur d'entités). */
    private void openUnitSkillOverrides(JsonObject m, String name) {
        JsonObject def = skillDef(name);
        if (def == null) return;
        JsonObject cat = parent != null ? parent.skillCatalog : null;
        JsonObject schema = cat != null && cat.has("schema") && cat.get("schema").isJsonObject() ? cat.getAsJsonObject("schema") : new JsonObject();
        String type = HordeJson.str(def, "skillType", "");
        JsonArray fields = schema.has(type) && schema.get(type).isJsonArray() ? schema.getAsJsonArray(type) : new JsonArray();
        minecraft.setScreen(new SkillOverrideScreen(this, m, name, def, fields, this::changed));
    }

    /** Copie équipement, loot, stats (et override) de src vers dst — le nombre de dst n'est pas touché. */
    private static void syncInto(JsonObject src, JsonObject dst) {
        for (String k : new String[]{"equipment", "loot_table"}) {
            if (src.has(k)) dst.add(k, src.get(k).deepCopy()); else dst.remove(k);
        }
        for (String k : new String[]{"max_health", "attack_damage", "movement_speed", "follow_range", "knockback_resistance", "override_stats",
                "no_weapon_drop", "no_armor_drop", "scale", "attack_buildings", "targets_altar", "building_damage", "skills", "skillOverrides"}) {
            if (src.has(k)) dst.add(k, src.get(k).deepCopy());
        }
    }

    // ─── Copie dédiée d'une entité custom (🧬) / édition de l'entité (✎) ───

    private JsonObject forkTarget = null;
    private String forkKey = "entity_type";

    private void forkButtons(JsonObject owner, String key, int x, int y) {
        String ce = HordeJson.str(owner, key, "");
        button(x, y, 18, "🧬", () -> {
            forkTarget = owner;
            forkKey = key;
            status = com.wavesurvivor.i18n.WSLang.t("ui.creation_d_une_copie_de") + ce + com.wavesurvivor.i18n.WSLang.t("ui.pour_cette_horde");
            NetworkHandler.CHANNEL.sendToServer(new com.wavesurvivor.network.EntityEditorPackets.Fork(ce, HordeJson.str(horde, "hordeName", "")));
        });
        button(x + 20, y, 18, "✎", () -> NetworkHandler.CHANNEL.sendToServer(new com.wavesurvivor.network.EntityEditorPackets.Open(ce)));
    }

    /** Le serveur a créé la copie : cette entrée pointe désormais dessus (l'original reste intact). */
    public void onForked(String source, String newName) {
        if (!customEntities.contains(newName)) customEntities.add(newName);
        if (!allTypes.contains(newName)) allTypes.add(newName);
        if (forkTarget != null && HordeJson.str(forkTarget, forkKey, "").equalsIgnoreCase(source)) {
            forkTarget.addProperty(forkKey, newName);
            changed();
        }
        forkTarget = null;
        status = com.wavesurvivor.i18n.WSLang.t("ui.copie_1f25") + newName + com.wavesurvivor.i18n.WSLang.t("ui.creee_et_utilisee_ici") + source + com.wavesurvivor.i18n.WSLang.t("ui.intacte_pour_la_modifier_puis");
        init();
    }

    // ─── Onglet Autel ───

    private static JsonObject defaultAltar() {
        JsonObject a = new JsonObject();
        JsonArray ing = new JsonArray();
        JsonObject i1 = new JsonObject();
        i1.addProperty("item", "minecraft:iron_ingot");
        i1.addProperty("count", 1);
        ing.add(i1);
        a.add("ingredients", ing);
        JsonObject z = new JsonObject();
        z.addProperty("radius", 8);
        JsonArray floor = new JsonArray();
        JsonObject f = new JsonObject();
        f.addProperty("block", "minecraft:stone_bricks");
        f.addProperty("weight", 100);
        floor.add(f);
        z.add("floor", floor);
        z.addProperty("autoLayout", true);
        z.addProperty("maxChests", 8);
        z.add("excludedChests", new JsonArray());
        z.add("slots", new JsonArray());
        a.add("zone", z);
        JsonObject st = new JsonObject();
        st.addProperty("color", "red");
        st.addProperty("particles", "ames");
        a.add("style", st);
        JsonObject d = new JsonObject();
        d.addProperty("enabled", false);
        a.add("defense", d);
        return a;
    }

    private void buildAltar() {
        int x0 = left + 8, y = top + 46;
        if (altar == null) {
            label(x0, y + 4, com.wavesurvivor.i18n.WSLang.t("ui.cette_horde_n_a_pas_de_config_d_autel_el"));
            button(x0, y + 18, 160, com.wavesurvivor.i18n.WSLang.t("ui.creer_la_config_d_autel"), () -> {
                altar = defaultAltar();
                altarDeleted = false;
                changed();
                init();
            });
            return;
        }
        button(x0, y, 110, (altarSub == 0 ? "§e" : "§7") + com.wavesurvivor.i18n.WSLang.t("ui.liaison_zone"), () -> { altarSub = 0; init(); });
        button(x0 + 114, y, 130, (altarSub == 1 ? "§e" : "§7") + com.wavesurvivor.i18n.WSLang.t("ui.defense_variantes"), () -> { altarSub = 1; init(); });
        button(x0 + 248, y, 132, (altarSub == 2 ? "§e" : "§7") + com.wavesurvivor.i18n.WSLang.t("ui.breches_anomalies"), () -> { altarSub = 2; init(); });
        button(left + W - 124, y, 116, com.wavesurvivor.i18n.WSLang.t("ui.supprimer_l_autel"), () -> {
            altar = null;
            altarDeleted = true;
            changed();
            init();
        });
        if (altarSub == 0) buildAltarZone(x0, y + 22);
        else if (altarSub == 1) buildAltarDefense(x0, y + 22);
        else buildAltarEvents(x0, y + 22);
    }

    /** Sous-onglet « Brèches & Anomalies » : deux vues, chacune avec une LISTE DE TYPES éditable. */
    private void buildAltarEvents(int x0, int y0) {
        button(x0, y0, 90, (eventsSub == 0 ? "§e" : "§7") + com.wavesurvivor.i18n.WSLang.t("ui.ev.tab_breaches"), () -> { eventsSub = 0; init(); });
        button(x0 + 94, y0, 90, (eventsSub == 1 ? "§e" : "§7") + com.wavesurvivor.i18n.WSLang.t("ui.ev.tab_anomalies"), () -> { eventsSub = 1; init(); });
        if (eventsSub == 0) buildBreachesView(x0, y0 + 22);
        else buildAnomaliesView(x0, y0 + 22);
    }

    private static String t(String k) { return com.wavesurvivor.i18n.WSLang.t(k); }

    /** Ancien format de Brèche → un premier type (fait une seule fois, à l'ouverture de l'onglet). */
    private static void ensureBreachTypes(JsonObject b) {
        JsonArray types = HordeJson.arr(b, "types");
        if (!types.isEmpty()) return;
        JsonObject tp = new JsonObject();
        tp.addProperty("name", "");
        tp.addProperty("weight", 1);
        tp.addProperty("style", HordeJson.str(b, "style", "infernale"));
        tp.addProperty("color", HordeJson.num(b, "color", -1));
        tp.addProperty("health", HordeJson.dbl(b, "health", 60));
        tp.addProperty("spawnIntervalSeconds", HordeJson.num(b, "spawnIntervalSeconds", 15));
        tp.addProperty("spawnPerPulse", HordeJson.num(b, "spawnPerPulse", 2));
        tp.addProperty("maxAlive", HordeJson.num(b, "maxAlivePerBreach", 6));
        JsonArray mobs = new JsonArray();
        java.util.Map<String, Integer> w = new java.util.LinkedHashMap<>();
        JsonArray legacy = b.has("spawnEntities") && b.get("spawnEntities").isJsonArray() ? b.getAsJsonArray("spawnEntities") : null;
        if (legacy != null && !legacy.isEmpty()) {
            for (var el : legacy) w.merge(el.getAsString().trim(), 1, Integer::sum);
        } else {
            w.put("minecraft:zombified_piglin", 2); w.put("minecraft:hoglin", 1); w.put("minecraft:blaze", 1);
        }
        w.forEach((id, n) -> { JsonObject m = new JsonObject(); m.addProperty("entity", id); m.addProperty("weight", n); mobs.add(m); });
        tp.add("mobs", mobs);
        JsonArray rewards = new JsonArray();
        JsonObject r = new JsonObject();
        int cnt = HordeJson.num(b, "rewardCount", 4);
        r.addProperty("item", HordeJson.str(b, "rewardItem", "minecraft:emerald"));
        r.addProperty("minQty", cnt);
        r.addProperty("maxQty", cnt);
        r.addProperty("chance", 100);
        rewards.add(r);
        tp.add("rewards", rewards);
        types.add(tp);
    }

    /** Ancien format d'anomalie (lévitation) → un premier type. */
    private static void ensureAnomalyTypes(JsonObject a) {
        JsonArray types = HordeJson.arr(a, "types");
        if (!types.isEmpty()) return;
        JsonObject tp = new JsonObject();
        tp.addProperty("name", "");
        tp.addProperty("weight", 1);
        tp.addProperty("color", 0xD940FF);
        tp.addProperty("warningTicks", HordeJson.num(a, "warningTicks", 40));
        tp.addProperty("durationTicks", HordeJson.num(a, "durationTicks", 60));
        tp.addProperty("radius", HordeJson.dbl(a, "radius", 3.0));
        JsonArray fx = new JsonArray();
        JsonObject e = new JsonObject();
        e.addProperty("effect", "minecraft:levitation");
        e.addProperty("level", HordeJson.num(a, "levitationAmplifier", 1) + 1);
        e.addProperty("duration", 10);
        fx.add(e);
        tp.add("effects", fx);
        tp.addProperty("damagePerSecond", 0);
        tp.addProperty("pull", 0);
        tp.addProperty("burstDamage", 0);
        types.add(tp);
    }

    /** Sélecteur de type : ◀ n/N ▶ · + Type · ⧉ dupliquer · ✖ supprimer. Renvoie le type sélectionné. */
    private JsonObject typeSelector(int x, int y, JsonArray types, int[] idx, java.util.function.Supplier<JsonObject> factory) {
        idx[0] = Math.max(0, Math.min(idx[0], types.size() - 1));
        label(x, y + 3, t("ui.ev.type"));
        button(x + 30, y - 1, 14, "◀", () -> { idx[0] = (idx[0] - 1 + types.size()) % types.size(); init(); });
        label(x + 48, y + 3, "§f" + (idx[0] + 1) + "/" + types.size());
        button(x + 72, y - 1, 14, "▶", () -> { idx[0] = (idx[0] + 1) % types.size(); init(); });
        button(x + 90, y - 1, 50, "§a" + t("ui.ev.add_type"), () -> { types.add(factory.get()); idx[0] = types.size() - 1; changed(); init(); });
        button(x + 142, y - 1, 18, "⧉", () -> {
            types.add(types.get(idx[0]).deepCopy()); idx[0] = types.size() - 1; changed(); init();
        });
        Button del = button(x + 162, y - 1, 18, "§c✖", () -> { types.remove(idx[0]); idx[0] = Math.max(0, idx[0] - 1); changed(); init(); });
        del.active = types.size() > 1;
        return types.get(idx[0]).getAsJsonObject();
    }

    private void buildBreachesView(int x0, int y0) {
        int colW = (W - 24) / 2;
        int xr = x0 + colW + 8;
        JsonObject b = HordeJson.obj(altar, "breaches");
        ensureBreachTypes(b);
        JsonArray types = HordeJson.arr(b, "types");

        // ── Colonne gauche : réglages globaux + type sélectionné ──
        int y = y0;
        toggle(x0, y, 150, t("ui.breches"), b, "enabled", false);
        y += 20;
        flow(x0, y);
        fl(t("ui.toutes_les"));
        fInt(22, HordeJson.num(b, "everyNWaves", 3), v -> b.addProperty("everyNWaves", Math.max(1, v)));
        fl(t("ui.vagues_7e84"));
        fInt(20, HordeJson.num(b, "count", 3), v -> b.addProperty("count", Math.max(1, Math.min(8, v))));
        fl(t("ui.breche_s"));
        y += 18;
        flow(x0, y);
        fl(t("ui.distance"));
        fInt(24, (int) HordeJson.dbl(b, "distance", 0), v -> b.addProperty("distance", Math.max(0, v)));
        fl(t("ui.0_auto"));
        fl(t("ui.ev.buff"));
        fInt(22, (int) Math.round(HordeJson.dbl(b, "buffPerOpen", 0.10) * 100), v -> b.addProperty("buffPerOpen", Math.max(0, v) / 100.0));
        fl("%");
        y += 22;
        JsonObject tp = typeSelector(x0, y, types, breachTypeIdx, () -> {
            JsonObject n = new JsonObject();
            n.addProperty("name", ""); n.addProperty("weight", 1); n.addProperty("style", "infernale"); n.addProperty("color", -1);
            n.addProperty("health", 60); n.addProperty("spawnIntervalSeconds", 15); n.addProperty("spawnPerPulse", 2); n.addProperty("maxAlive", 6);
            n.add("mobs", new JsonArray()); n.add("rewards", new JsonArray());
            return n;
        });
        y += 20;
        flow(x0, y);
        fl(t("ui.ev.name"));
        fText(118, HordeJson.str(tp, "name", ""), v -> tp.addProperty("name", v));
        fl(t("ui.ev.weight"));
        fDbl(28, HordeJson.dbl(tp, "weight", 1), v -> tp.addProperty("weight", Math.max(0, v)));
        y += 18;
        flow(x0, y);
        fl(t("ui.style"));
        String style = HordeJson.str(tp, "style", "infernale");
        fBtn(70, t("ui.ev.style." + switch (style) { case "ames", "end", "abysses" -> style; default -> "infernale"; }), () -> {
            tp.addProperty("style", cycle(List.of("infernale", "ames", "end", "abysses"), HordeJson.str(tp, "style", "infernale"), 1));
            changed();
            init();
        });
        fl(t("ui.couleur"));
        int col = HordeJson.num(tp, "color", -1);
        fText(52, col < 0 ? "" : String.format("%06X", col & 0xFFFFFF), v -> {
            String h = v.trim().replace("#", "");
            try { tp.addProperty("color", h.isEmpty() ? -1 : Integer.parseInt(h, 16)); } catch (NumberFormatException ignored) {}
        });
        y += 18;
        flow(x0, y);
        fl(t("ui.pv"));
        fInt(34, (int) HordeJson.dbl(tp, "health", 60), v -> tp.addProperty("health", Math.max(5, v)));
        y += 18;
        flow(x0, y);
        fl(t("ui.renforts"));
        fInt(20, HordeJson.num(tp, "spawnPerPulse", 2), v -> tp.addProperty("spawnPerPulse", Math.max(0, v)));
        fl(t("ui.toutes_les_c0b6"));
        fInt(22, HordeJson.num(tp, "spawnIntervalSeconds", 15), v -> tp.addProperty("spawnIntervalSeconds", Math.max(2, v)));
        fl(t("ui.s_max"));
        fInt(20, HordeJson.num(tp, "maxAlive", 6), v -> tp.addProperty("maxAlive", Math.max(1, v)));

        // ── Colonne droite : monstres (pondérés) ──
        JsonArray mobs = HordeJson.arr(tp, "mobs");
        int ry = y0;
        label(xr, ry + 3, t("ui.ev.mobs"));
        button(xr + colW - 76, ry - 1, 72, "§a" + t("ui.ev.add_mob"), () -> {
            JsonObject m = new JsonObject(); m.addProperty("entity", "minecraft:zombie"); m.addProperty("weight", 1);
            mobs.add(m); breachMobScroll = Math.max(0, mobs.size() - 5); changed(); init();
        });
        ry += 18;
        int mobRows = 5;
        breachMobScroll = Math.max(0, Math.min(breachMobScroll, Math.max(0, mobs.size() - mobRows)));
        for (int i = 0; i < mobRows && i + breachMobScroll < mobs.size(); i++) {
            final int idx = i + breachMobScroll;
            JsonObject m = at(mobs, idx);
            if (m == null) continue;
            int yy = ry + i * 18;
            text(xr, yy, colW - 122, HordeJson.str(m, "entity", ""), v -> m.addProperty("entity", v.trim()));
            listBtn(xr + colW - 119, yy - 1, HordeJson.str(m, "entity", ""), id -> m.addProperty("entity", id));
            eggBtn(xr + colW - 101, yy - 1, id -> m.addProperty("entity", id));
            label(xr + colW - 74, yy + 3, "×");
            dblBox(xr + colW - 66, yy, 28, HordeJson.dbl(m, "weight", 1), v -> m.addProperty("weight", Math.max(0, v)));
            button(xr + colW - 34, yy - 1, 16, "§c✖", () -> { mobs.remove(idx); changed(); init(); });
        }
        if (mobs.isEmpty()) label(xr, ry + 3, t("ui.ev.none"));
        if (mobs.size() > mobRows) {
            button(xr + colW - 16, ry - 1, 14, "▲", () -> { breachMobScroll--; init(); }).active = breachMobScroll > 0;
            button(xr + colW - 16, ry + (mobRows - 1) * 18 - 1, 14, "▼", () -> { breachMobScroll++; init(); })
                    .active = breachMobScroll + mobRows < mobs.size();
        }

        // ── Colonne droite : récompenses ──
        JsonArray rewards = HordeJson.arr(tp, "rewards");
        ry += mobRows * 18 + 6;
        label(xr, ry + 3, t("ui.ev.rewards"));
        button(xr + colW - 90, ry - 1, 86, "§a" + t("ui.ev.add_reward"), () -> {
            JsonObject r = new JsonObject(); r.addProperty("item", "minecraft:emerald");
            r.addProperty("minQty", 1); r.addProperty("maxQty", 3); r.addProperty("chance", 100);
            rewards.add(r); breachRewardScroll = Math.max(0, rewards.size() - 3); changed(); init();
        });
        ry += 20;
        int rewRows = 3;
        breachRewardScroll = Math.max(0, Math.min(breachRewardScroll, Math.max(0, rewards.size() - rewRows)));
        for (int i = 0; i < rewRows && i + breachRewardScroll < rewards.size(); i++) {
            final int idx = i + breachRewardScroll;
            JsonObject r = at(rewards, idx);
            if (r == null) continue;
            int yy = ry + i * 22;
            flow(xr, yy);
            fIcon(() -> HordeJson.str(r, "item", "minecraft:emerald"), v -> r.addProperty("item", v));
            fInt(24, HordeJson.num(r, "minQty", 1), v -> r.addProperty("minQty", Math.max(0, v)));
            fl("→");
            fInt(24, HordeJson.num(r, "maxQty", 1), v -> r.addProperty("maxQty", Math.max(0, v)));
            fDbl(34, HordeJson.dbl(r, "chance", 100), v -> r.addProperty("chance", Math.max(0, Math.min(100, v))));
            fl("%");
            button(xr + colW - 34, yy - 1, 16, "§c✖", () -> { rewards.remove(idx); changed(); init(); });
        }
        if (rewards.isEmpty()) label(xr, ry + 3, t("ui.ev.none"));
        if (rewards.size() > rewRows) {
            button(xr + colW - 16, ry - 1, 14, "▲", () -> { breachRewardScroll--; init(); }).active = breachRewardScroll > 0;
            button(xr + colW - 16, ry + (rewRows - 1) * 22 - 1, 14, "▼", () -> { breachRewardScroll++; init(); })
                    .active = breachRewardScroll + rewRows < rewards.size();
        }
    }

    private void buildAnomaliesView(int x0, int y0) {
        int colW = (W - 24) / 2;
        int xr = x0 + colW + 8;
        JsonObject a = HordeJson.obj(altar, "anomalies");
        ensureAnomalyTypes(a);
        JsonArray types = HordeJson.arr(a, "types");

        // ── Colonne gauche : réglages globaux + type sélectionné ──
        int y = y0;
        toggle(x0, y, 170, t("ui.anomalies"), a, "enabled", false);
        y += 20;
        flow(x0, y);
        fl(t("ui.des_la_vague"));
        fInt(22, HordeJson.num(a, "fromWave", 1), v -> a.addProperty("fromWave", Math.max(1, v)));
        fl(t("ui.zone_aleatoire"));
        fDbl(28, HordeJson.dbl(a, "zoneRadius", 12), v -> a.addProperty("zoneRadius", Math.max(2, v)));
        y += 18;
        flow(x0, y);
        fl(t("ui.toutes_les"));
        fInt(24, HordeJson.num(a, "intervalSeconds", 20), v -> a.addProperty("intervalSeconds", Math.max(5, v)));
        fl(t("ui.ev.s_dot"));
        fInt(20, HordeJson.num(a, "count", 2), v -> a.addProperty("count", Math.max(1, Math.min(8, v))));
        fl(t("ui.anomalie_s"));
        y += 22;
        JsonObject tp = typeSelector(x0, y, types, anomalyTypeIdx, () -> {
            JsonObject n = new JsonObject();
            n.addProperty("name", ""); n.addProperty("weight", 1); n.addProperty("color", 0xD940FF);
            n.addProperty("warningTicks", 40); n.addProperty("durationTicks", 60); n.addProperty("radius", 3.0);
            n.add("effects", new JsonArray());
            n.addProperty("damagePerSecond", 0); n.addProperty("pull", 0); n.addProperty("burstDamage", 0);
            return n;
        });
        y += 20;
        flow(x0, y);
        fl(t("ui.ev.name"));
        fText(118, HordeJson.str(tp, "name", ""), v -> tp.addProperty("name", v));
        fl(t("ui.ev.weight"));
        fDbl(28, HordeJson.dbl(tp, "weight", 1), v -> tp.addProperty("weight", Math.max(0, v)));
        y += 18;
        flow(x0, y);
        fl(t("ui.couleur"));
        int col = HordeJson.num(tp, "color", 0xD940FF);
        fText(52, String.format("%06X", col & 0xFFFFFF), v -> {
            String h = v.trim().replace("#", "");
            try { if (!h.isEmpty()) tp.addProperty("color", Integer.parseInt(h, 16)); } catch (NumberFormatException ignored) {}
        });
        fl(t("ui.rayon"));
        fDbl(28, HordeJson.dbl(tp, "radius", 3.0), v -> tp.addProperty("radius", Math.max(1, v)));
        y += 18;
        flow(x0, y);
        fl(t("ui.alerte"));
        fDbl(28, HordeJson.num(tp, "warningTicks", 40) / 20.0, v -> tp.addProperty("warningTicks", (int) Math.max(10, Math.round(v * 20))));
        fl(t("ui.s_duree"));
        fDbl(28, HordeJson.num(tp, "durationTicks", 60) / 20.0, v -> tp.addProperty("durationTicks", (int) Math.max(20, Math.round(v * 20))));
        fl(t("ui.ev.s"));
        y += 18;
        flow(x0, y);
        fl(t("ui.ev.dps"));
        fDbl(28, HordeJson.dbl(tp, "damagePerSecond", 0), v -> tp.addProperty("damagePerSecond", Math.max(0, v)));
        fl(t("ui.ev.burst"));
        fDbl(28, HordeJson.dbl(tp, "burstDamage", 0), v -> tp.addProperty("burstDamage", Math.max(0, v)));
        y += 18;
        flow(x0, y);
        fl(t("ui.ev.pull"));
        fDbl(34, HordeJson.dbl(tp, "pull", 0), v -> tp.addProperty("pull", Math.max(-1.5, Math.min(1.5, v))));
        fl(t("ui.ev.pull_hint"));

        // ── Colonne droite : effets libres ──
        JsonArray fxs = HordeJson.arr(tp, "effects");
        int ry = y0;
        label(xr, ry + 3, t("ui.ev.effects"));
        button(xr + colW - 76, ry - 1, 72, "§a" + t("ui.ev.add_effect"), () -> {
            JsonObject e = new JsonObject(); e.addProperty("effect", "minecraft:slowness"); e.addProperty("level", 1); e.addProperty("duration", 40);
            fxs.add(e); anomalyFxScroll = Math.max(0, fxs.size() - 8); changed(); init();
        });
        ry += 18;
        int rows = 8;
        anomalyFxScroll = Math.max(0, Math.min(anomalyFxScroll, Math.max(0, fxs.size() - rows)));
        for (int i = 0; i < rows && i + anomalyFxScroll < fxs.size(); i++) {
            final int idx = i + anomalyFxScroll;
            JsonObject e = at(fxs, idx);
            if (e == null) continue;
            int yy = ry + i * 18;
            int tw = colW - 128;
            text(xr, yy, tw, HordeJson.str(e, "effect", ""), v -> e.addProperty("effect", v.trim()));
            button(xr + tw + 2, yy - 1, 14, "◀", () -> { e.addProperty("effect", cycle(List.of(EFFECTS), HordeJson.str(e, "effect", ""), -1)); changed(); init(); });
            button(xr + tw + 18, yy - 1, 14, "▶", () -> { e.addProperty("effect", cycle(List.of(EFFECTS), HordeJson.str(e, "effect", ""), 1)); changed(); init(); });
            intBox(xr + tw + 36, yy, 20, HordeJson.num(e, "level", 1), v -> e.addProperty("level", Math.max(1, Math.min(10, v))));
            dblBox(xr + tw + 60, yy, 28, HordeJson.num(e, "duration", 40) / 20.0, v -> e.addProperty("duration", (int) Math.max(10, Math.round(v * 20))));
            button(xr + colW - 34, yy - 1, 16, "§c✖", () -> { fxs.remove(idx); changed(); init(); });
        }
        if (fxs.isEmpty()) label(xr, ry + 3, t("ui.ev.none"));
        if (fxs.size() > rows) {
            button(xr + colW - 16, ry - 1, 14, "▲", () -> { anomalyFxScroll--; init(); }).active = anomalyFxScroll > 0;
            button(xr + colW - 16, ry + (rows - 1) * 18 - 1, 14, "▼", () -> { anomalyFxScroll++; init(); })
                    .active = anomalyFxScroll + rows < fxs.size();
        }
    }

    private void buildAltarZone(int x0, int y) {
        JsonObject a = altar;
        // Ingrédients de liaison
        label(x0, y, com.wavesurvivor.i18n.WSLang.t("ui.ingredients_de_liaison_clic_droit_retire"));
        JsonArray ing = HordeJson.arr(a, "ingredients");
        for (int i = 0; i < Math.min(ing.size(), 6); i++) {
            final int idx = i;
            JsonObject o = at(ing, i);
            if (o == null) continue;
            int sx = x0 + i * 30;
            iconSlot(sx, y + 10, () -> HordeJson.str(o, "item", ""), v -> o.addProperty("item", v), () -> ing.remove(idx));
            intBox(sx, y + 30, 22, HordeJson.num(o, "count", 1), v -> o.addProperty("count", Math.max(1, v)));
        }
        if (ing.size() < 6) button(x0 + Math.min(ing.size(), 6) * 30, y + 11, 18, "§a+", () -> openPicker(com.wavesurvivor.i18n.WSLang.t("ui.ingredient"), id -> {
            JsonObject o = new JsonObject();
            o.addProperty("item", id);
            o.addProperty("count", 1);
            HordeJson.arr(altar, "ingredients").add(o);
            changed();
            init();
        }));
        y += 50;

        // Zone
        JsonObject z = HordeJson.obj(a, "zone");
        label(x0, y + 3, com.wavesurvivor.i18n.WSLang.t("ui.rayon"));
        intBox(x0 + 34, y, 24, HordeJson.num(z, "radius", 8), v -> z.addProperty("radius", Math.max(0, Math.min(32, v))));
        y += 20;
        label(x0, y, com.wavesurvivor.i18n.WSLang.t("ui.sol_de_la_zone_poids_clic_droit_retirer"));
        JsonArray floor = HordeJson.arr(z, "floor");
        for (int i = 0; i < Math.min(floor.size(), 7); i++) {
            final int idx = i;
            JsonObject o = at(floor, i);
            if (o == null) continue;
            int sx = x0 + i * 30;
            iconSlot(sx, y + 10, () -> HordeJson.str(o, "block", ""), v -> o.addProperty("block", v), () -> floor.remove(idx));
            intBox(sx, y + 30, 24, HordeJson.num(o, "weight", 10), v -> o.addProperty("weight", Math.max(1, v)));
        }
        if (floor.size() < 7) button(x0 + Math.min(floor.size(), 7) * 30, y + 11, 18, "§a+", () -> openPicker(com.wavesurvivor.i18n.WSLang.t("ui.bloc_du_sol"), id -> {
            JsonObject o = new JsonObject();
            o.addProperty("block", id);
            o.addProperty("weight", 10);
            HordeJson.arr(HordeJson.obj(altar, "zone"), "floor").add(o);
            changed();
            init();
        }));
        y += 50;

        // Sol dessiné à la main (prioritaire sur le tirage pondéré)
        boolean drawn = z.has("pattern") && z.get("pattern").isJsonArray() && z.getAsJsonArray("pattern").size() > 0;
        button(x0, y - 1, 150, com.wavesurvivor.i18n.WSLang.t("ui.painter.open"), () ->
                net.minecraft.client.Minecraft.getInstance().setScreen(new FloorPainterScreen(this, z, this::changed)));
        label(x0 + 158, y + 4, drawn ? com.wavesurvivor.i18n.WSLang.t("ui.painter.has_drawing") : com.wavesurvivor.i18n.WSLang.t("ui.painter.no_drawing"));
        y += 24;

        // Style
        JsonObject st = HordeJson.obj(a, "style");
        net.minecraft.world.item.DyeColor dye = net.minecraft.world.item.DyeColor.byName(HordeJson.str(st, "color", "red"), net.minecraft.world.item.DyeColor.RED);
        label(x0, y + 3, com.wavesurvivor.i18n.WSLang.t("ui.couleur"));
        button(x0 + 42, y - 1, 90, "■ " + com.wavesurvivor.altar.AltarColors.nameFr(dye), () -> {
            net.minecraft.world.item.DyeColor[] all = net.minecraft.world.item.DyeColor.values();
            st.addProperty("color", all[(dye.ordinal() + (hasShiftDown() ? all.length - 1 : 1)) % all.length].getName());
            changed();
            init();
        });
        com.wavesurvivor.altar.AltarParticles part = com.wavesurvivor.altar.AltarParticles.byId(HordeJson.str(st, "particles", "ames"));
        label(x0 + 138, y + 3, com.wavesurvivor.i18n.WSLang.t("ui.particules"));
        button(x0 + 190, y - 1, 78, com.wavesurvivor.i18n.WSLang.t(part.nameFr), () -> {
            com.wavesurvivor.altar.AltarParticles[] all = com.wavesurvivor.altar.AltarParticles.values();
            st.addProperty("particles", all[(part.ordinal() + (hasShiftDown() ? all.length - 1 : 1)) % all.length].id);
            changed();
            init();
        });
        label(x0, y + 18, com.wavesurvivor.i18n.WSLang.t("ui.maj_clic_precedent_applique_a_la_liaison"));

        // Disposition des coffres roulette et des marchands (colonne droite) : écran « 📍 Disposition »
        int cx = left + W - 150, cy = top + 70;
        label(cx, cy, t("ui.layout.section"));
        button(cx, cy + 12, 140, t("ui.layout.open"), () -> {
            java.util.List<String> names = new java.util.ArrayList<>();
            if (cd().has("merchants") && cd().get("merchants").isJsonArray()) {
                for (JsonElement e : cd().getAsJsonArray("merchants")) {
                    if (e.isJsonObject()) names.add(HordeJson.str(e.getAsJsonObject(), "name", ""));
                }
            }
            int rad = Math.max(1, HordeJson.num(z, "radius", 8));
            if (kingdom()) rad = Math.max(rad, Math.max(4, HordeJson.num(HordeJson.obj(cd(), "kingdom"), "claimRadius", 16)));
            net.minecraft.client.Minecraft.getInstance().setScreen(new LayoutPainterScreen(this, z, Math.min(32, rad), parent.chests, names, this::changed));
        });
        int placedChests = 0, placedMerchants = 0;
        for (JsonElement e : HordeJson.arr(z, "slots")) {
            if (!e.isJsonObject()) continue;
            String ty = HordeJson.str(e.getAsJsonObject(), "type", "");
            if ("chest".equals(ty)) placedChests++;
            else if ("merchant".equals(ty)) placedMerchants++;
        }
        label(cx, cy + 34, com.wavesurvivor.i18n.WSLang.t("ui.layout.summary", placedChests, placedMerchants));
        label(cx, cy + 46, HordeJson.bool(z, "autoLayout", true) ? t("ui.layout.others_auto") : t("ui.layout.others_none"));
    }

    private void buildAltarDefense(int x0, int y) {
        JsonObject d = HordeJson.obj(altar, "defense");
        toggle(x0, y, 150, com.wavesurvivor.i18n.WSLang.t("ui.defense_du_monolithe"), d, "enabled", false);
        y += 22;
        flow(x0, y);
        fl(com.wavesurvivor.i18n.WSLang.t("ui.pv"));
        fInt(36, HordeJson.num(d, "maxHealth", 500), v -> d.addProperty("maxHealth", Math.max(10, v)));
        fl(com.wavesurvivor.i18n.WSLang.t("ui.reparation"));
        fIcon(() -> HordeJson.str(d, "repairItem", "minecraft:bone_meal"), v -> d.addProperty("repairItem", v));
        fl("+");
        fInt(28, HordeJson.num(d, "repairAmount", 10), v -> d.addProperty("repairAmount", Math.max(1, v)));
        fl(com.wavesurvivor.i18n.WSLang.t("ui.pv"));
        y += 22;
        flow(x0, y);
        fl(com.wavesurvivor.i18n.WSLang.t("ui.bonus_si"));
        fInt(26, HordeJson.num(d, "bonusThreshold", 75), v -> d.addProperty("bonusThreshold", Math.max(0, Math.min(100, v))));
        fl("% :");
        fIcon(() -> HordeJson.str(d, "bonusItem", ""), v -> d.addProperty("bonusItem", v));
        fl("×");
        fInt(24, HordeJson.num(d, "bonusCount", 1), v -> d.addProperty("bonusCount", Math.max(1, v)));
        y += 22;
        flow(x0, y);
        fl(com.wavesurvivor.i18n.WSLang.t("ui.degats_des_profanateurs"));
        fDbl(32, HordeJson.dbl(d, "profanerDamageMultiplier", 0.5), v -> d.addProperty("profanerDamageMultiplier", v));
        y += 24;
        flow(x0, y);
        fl(com.wavesurvivor.i18n.WSLang.t("ui.boutique_niveau_max"));
        fInt(22, HordeJson.num(d, "upgradeMaxLevel", 5), v -> d.addProperty("upgradeMaxLevel", Math.max(0, v)));
        fl("monnaie");
        fIcon(() -> HordeJson.str(d, "upgradeCurrency", "minecraft:emerald"), v -> d.addProperty("upgradeCurrency", v));
        y += 22;
        int col = x0 + 64;
        label(x0, y + 3, com.wavesurvivor.i18n.WSLang.t("ui.renfort"));
        flow(col, y);
        fl("+");
        fInt(32, HordeJson.num(d, "hpPerLevel", 100), v -> d.addProperty("hpPerLevel", Math.max(0, v)));
        fl(com.wavesurvivor.i18n.WSLang.t("ui.pv_cout"));
        fInt(26, HordeJson.num(d, "hpCostBase", 8), v -> d.addProperty("hpCostBase", Math.max(0, v)));
        y += 18;
        label(x0, y + 3, com.wavesurvivor.i18n.WSLang.t("ui.regeneration"));
        flow(col, y);
        fl("+");
        fDbl(32, HordeJson.dbl(d, "regenPerLevel", 1.0), v -> d.addProperty("regenPerLevel", v));
        fl(com.wavesurvivor.i18n.WSLang.t("ui.pv_s_cout"));
        fInt(26, HordeJson.num(d, "regenCostBase", 10), v -> d.addProperty("regenCostBase", Math.max(0, v)));
        y += 18;
        label(x0, y + 3, com.wavesurvivor.i18n.WSLang.t("ui.blindage"));
        flow(col, y);
        fl("-");
        fInt(32, HordeJson.num(d, "armorPerLevel", 10), v -> d.addProperty("armorPerLevel", Math.max(0, Math.min(100, v))));
        fl(com.wavesurvivor.i18n.WSLang.t("ui.cout"));
        fInt(26, HordeJson.num(d, "armorCostBase", 12), v -> d.addProperty("armorCostBase", Math.max(0, v)));
        label(x0, y + 20, com.wavesurvivor.i18n.WSLang.t("ui.cout_du_niveau_n_cout_de_base_n"));

        // Variantes (colonne droite)
        int vx = left + W - 212, vy = top + 70;
        label(vx, vy, com.wavesurvivor.i18n.WSLang.t("ui.variantes_vide_la_horde_seule"));
        JsonArray vars = HordeJson.arr(altar, "variants");
        List<String> hordeNames = new ArrayList<>();
        for (HordeEditorPackets.Entry e : parent.entries) hordeNames.add(e.name());
        String self = HordeJson.str(horde, "hordeName", "");
        if (!hordeNames.contains(self)) hordeNames.add(0, self);
        for (int i = 0; i < Math.min(vars.size(), 6); i++) {
            final int idx = i;
            JsonObject v = at(vars, i);
            if (v == null) continue;
            int ry = vy + 12 + i * 18;
            text(vx, ry, 54, HordeJson.str(v, "label", ""), s -> v.addProperty("label", s));
            String hn = HordeJson.str(v, "horde", "");
            String shown = hn.length() > 16 ? hn.substring(0, 15) + "…" : hn;
            button(vx + 58, ry - 1, 110, "§f" + shown, () -> {
                v.addProperty("horde", cycle(hordeNames, HordeJson.str(v, "horde", ""), hasShiftDown() ? -1 : 1));
                changed();
                init();
            });
            button(vx + 170, ry - 1, 16, "§c✖", () -> { vars.remove(idx); changed(); init(); });
        }
        if (vars.size() < 6) button(vx, vy + 12 + Math.min(vars.size(), 6) * 18, 84, com.wavesurvivor.i18n.WSLang.t("ui.variante"), () -> {
            JsonObject v = new JsonObject();
            v.addProperty("label", vars.isEmpty() ? com.wavesurvivor.i18n.WSLang.t("ui.vanilla") : com.wavesurvivor.i18n.WSLang.t("ui.moddee"));
            v.addProperty("horde", HordeJson.str(horde, "hordeName", ""));
            HordeJson.arr(altar, "variants").add(v);
            changed();
            init();
        });
        label(vx, vy + 30 + Math.min(vars.size() + 1, 7) * 18, com.wavesurvivor.i18n.WSLang.t("ui.clic_sur_la_horde_suivante_maj_prec"));
    }

    // ─── Sélecteur d'item (inventaire) ───

    private void iconSlot(int x, int y, Supplier<String> id, Consumer<String> set, Runnable clear) {
        iconSlots.add(new IconSlot(x, y, id, set, clear));
    }

    private void renderIconSlots(GuiGraphics g, int mx, int my) {
        IconSlot hover = null;
        for (IconSlot s : iconSlots) {
            boolean h = mx >= s.x() && mx < s.x() + 18 && my >= s.y() && my < s.y() + 18;
            if (h) hover = s;
            g.fill(s.x(), s.y(), s.x() + 18, s.y() + 18, h ? 0xFF45455A : 0xFF2A2A30);
            g.fill(s.x(), s.y(), s.x() + 18, s.y() + 1, 0xFF555560);
            String id = s.id().get();
            if (id != null && !id.isBlank()) {
                ItemStack st = LootItems.resolve(id, 1);
                g.renderItem(st.isEmpty() ? new ItemStack(Items.BARRIER) : st, s.x() + 1, s.y() + 1);
            }
        }
        if (hover != null && picker == null) {
            String id = hover.id().get();
            List<Component> tip = new ArrayList<>();
            if (id != null && !id.isBlank()) {
                ItemStack st = LootItems.resolve(id, 1);
                if (st.isEmpty()) {
                    tip.add(Component.literal("§c" + id));
                } else {
                    tip.addAll(getTooltipFromItem(minecraft, st)); // nom + enchantements + données
                }
                int brace = id.indexOf('{');
                tip.add(Component.literal("§8" + (brace > 0 ? id.substring(0, brace) + " {…}" : id)));
            } else {
                tip.add(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.vide")));
            }
            tip.add(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.clic_choisir_dans_l_inventaire") + (hover.clear() != null ? com.wavesurvivor.i18n.WSLang.t("ui.clic_droit_vider") : "")));
            g.renderComponentTooltip(font, tip, mx, my);
        }
    }

    // ─── Onglet Pauses & Marchands ───

    /** Sous-onglet « 🕳 Brèches » (mode Kingdom) : fréquence, unités par brèche et liste des unités qui en sortent. */
    private void buildBreaches(JsonObject cd, int x0, int y) {
        JsonObject k = HordeJson.obj(cd, "kingdom");
        flow(x0, y);
        fl(t("ui.kgd.breach_every"));
        fInt(28, HordeJson.num(k, "smallBreachSeconds", 30), v -> k.addProperty("smallBreachSeconds", Math.max(0, v)));
        fl(t("ui.kgd.breach_count"));
        fInt(22, HordeJson.num(k, "smallBreachUnits", 4), v -> k.addProperty("smallBreachUnits", Math.max(1, Math.min(12, v))));
        fl(t("ui.kgd.breach_count_unit"));
        y += 22;
        JsonObject ent = at(breachUnits(), selBreach);
        if (ent == null) {
            label(x0, y + 2, breachUnits().size() == 0 ? t("ui.kgd.breach_empty") : t("ui.kgd.breach_list"));
            int ey = y + 14;
            breachList.place(x0, ey, 220, Math.max(3, (top + H - 30 - ey) / 12));
            int eb = breachList.bottom() + 4;
            button(x0, eb, 60, com.wavesurvivor.i18n.WSLang.t("ui.entite_950b"), () -> {
                JsonObject e = HordeJson.newMob();
                e.remove("base_count"); e.remove("count_increment"); e.remove("min_wave"); e.remove("max_wave");
                e.addProperty("count", 1);
                breachUnits().add(e);
                selBreach = breachUnits().size() - 1;
                changed();
                init();
            });
            button(x0 + 62, eb, 70, com.wavesurvivor.i18n.WSLang.t("ui.coller_mob"), () -> {
                JsonObject o = clipboard();
                if (!HordeJson.looksLikeMob(o)) { status = com.wavesurvivor.i18n.WSLang.t("ui.le_presse_papiers_ne_contient_pas_de_mob"); return; }
                if (!o.has("count")) o.addProperty("count", 1);
                breachUnits().add(o);
                status = "§a📥 « " + HordeJson.displayName(o) + " » " + t("ui.kgd.breach_pasted");
                changed();
                init();
            });
        } else {
            button(x0, y - 1, 80, t("ui.kgd.breach_back"), () -> { selBreach = -1; init(); });
            button(x0 + 84, y - 1, 22, "📋", () -> copyJson(ent, com.wavesurvivor.i18n.WSLang.t("ui.entite_e8cc") + HordeJson.displayName(ent) + " »"));
            button(x0 + 108, y - 1, 22, "§c🗑", () -> {
                breachUnits().remove(selBreach);
                selBreach = -1;
                changed();
                init();
            });
            buildEntityForm(ent, false, x0, y + 18);
        }
    }

    // ─── Onglet Calme › Objectifs (convoi, champion, trésor enfoui, purification) ───

    private void buildObjectives(JsonObject cd, int x0, int y) {
        JsonObject o = HordeJson.obj(HordeJson.obj(cd, "kingdom"), "calmObjectives");
        String[] subs = {"ui.obj.general", "ui.obj.convoy", "ui.obj.champion", "ui.obj.treasure", "ui.obj.purify"};
        String[] keys = {null, "convoy", "champion", "treasure", "purify"};
        int bx = x0;
        for (int i = 0; i < subs.length; i++) {
            final int s = i;
            boolean on = keys[i] == null ? HordeJson.bool(o, "enabled", true) : HordeJson.bool(HordeJson.obj(o, keys[i]), "enabled", true);
            String lbl = (objSub == i ? "§e§l" : on ? "§f" : "§8") + t(subs[i]);
            int w = Math.max(60, font.width(t(subs[i])) + 14);
            button(bx, y - 2, w, lbl, () -> { objSub = s; objDropScroll = 0; init(); });
            bx += w + 3;
        }
        y += 22;
        switch (objSub) {
            case 0 -> {
                toggle(x0, y, 170, t("ui.obj.enabled"), o, "enabled", true);
                y += 20;
                flow(x0, y);
                fl(t("ui.obj.every"));
                fInt(22, HordeJson.num(o, "every", 1), v -> o.addProperty("every", Math.max(1, Math.min(10, v))));
                fl(t("ui.obj.every_unit"));
                y += 20;
                flow(x0, y);
                fl(t("ui.obj.delay"));
                fInt(28, HordeJson.num(o, "delaySeconds", 15), v -> o.addProperty("delaySeconds", Math.max(0, v)));
                fl(t("ui.obj.delay_unit"));
                y += 22;
                for (String line : t("ui.obj.hint").split("\n")) { label(x0, y, line); y += 11; }
            }
            case 1 -> {
                JsonObject c = HordeJson.obj(o, "convoy");
                toggle(x0, y, 140, t("ui.obj.active"), c, "enabled", true);
                y += 20;
                objEntityRow(c, "minecraft:wandering_trader", x0, y);
                y += 20;
                flow(x0, y);
                fl(t("ui.obj.hp"));
                fDbl(34, HordeJson.dbl(c, "health", 80), v -> c.addProperty("health", Math.max(1, v)));
                fl(t("ui.obj.speed"));
                fDbl(30, HordeJson.dbl(c, "speed", 0.2), v -> c.addProperty("speed", Math.max(0.05, Math.min(0.6, v))));
                fl(t("ui.obj.attackers"));
                fInt(22, HordeJson.num(c, "attackers", 6), v -> c.addProperty("attackers", Math.max(0, Math.min(30, v))));
                fl(t("ui.obj.seconds"));
                fInt(28, HordeJson.num(c, "seconds", 150), v -> c.addProperty("seconds", Math.max(20, v)));
                y += 20;
                objRewardRow(c, new int[]{15, 10, 10, 5, 1}, x0, y);
                y += 24;
                label(x0, y, t("ui.obj.convoy_hint"));
            }
            case 2 -> {
                JsonObject c = HordeJson.obj(o, "champion");
                toggle(x0, y, 140, t("ui.obj.active"), c, "enabled", true);
                label(x0 + 148, y + 4, t("ui.obj.name"));
                text(x0 + 180, y, 150, HordeJson.str(c, "name", ""), v -> c.addProperty("name", v));
                y += 20;
                objEntityRow(c, "minecraft:vindicator", x0, y);
                y += 20;
                flow(x0, y);
                fl(t("ui.obj.hp"));
                fDbl(34, HordeJson.dbl(c, "health", 200), v -> c.addProperty("health", Math.max(1, v)));
                fl(t("ui.obj.damage"));
                fDbl(28, HordeJson.dbl(c, "damage", 10), v -> c.addProperty("damage", Math.max(0, v)));
                fl(t("ui.obj.guards"));
                fInt(22, HordeJson.num(c, "guards", 4), v -> c.addProperty("guards", Math.max(0, Math.min(20, v))));
                fl(t("ui.obj.seconds"));
                fInt(28, HordeJson.num(c, "seconds", 150), v -> c.addProperty("seconds", Math.max(20, v)));
                y += 20;
                objRewardRow(c, new int[]{0, 0, 0, 0, 3}, x0, y);
                y += 24;
                objDrops(c, new String[][]{{"wavesurvivor:roulette_key", "1", "1", "100"}}, x0, y);
            }
            case 3 -> {
                JsonObject c = HordeJson.obj(o, "treasure");
                toggle(x0, y, 140, t("ui.obj.active"), c, "enabled", true);
                y += 20;
                flow(x0, y);
                fl(t("ui.obj.distance"));
                fInt(26, HordeJson.num(c, "minDistance", 28), v -> c.addProperty("minDistance", Math.max(4, v)));
                fl("–");
                fInt(26, HordeJson.num(c, "maxDistance", 55), v -> c.addProperty("maxDistance", Math.max(8, v)));
                fl(t("ui.obj.blocks"));
                fl(t("ui.obj.seconds"));
                fInt(28, HordeJson.num(c, "seconds", 150), v -> c.addProperty("seconds", Math.max(20, v)));
                y += 20;
                objRewardRow(c, new int[]{20, 0, 0, 0, 1}, x0, y);
                y += 24;
                objDrops(c, new String[][]{{"minecraft:golden_apple", "1", "2", "100"}, {"minecraft:diamond", "1", "2", "60"},
                        {"minecraft:experience_bottle", "3", "6", "100"}}, x0, y);
            }
            case 4 -> {
                JsonObject c = HordeJson.obj(o, "purify");
                toggle(x0, y, 140, t("ui.obj.active"), c, "enabled", true);
                y += 20;
                flow(x0, y);
                fl(t("ui.obj.foyers"));
                fInt(22, HordeJson.num(c, "count", 3), v -> c.addProperty("count", Math.max(1, Math.min(8, v))));
                fl(t("ui.obj.hp"));
                fDbl(30, HordeJson.dbl(c, "health", 40), v -> c.addProperty("health", Math.max(4, v)));
                fl(t("ui.obj.seconds"));
                fInt(28, HordeJson.num(c, "seconds", 120), v -> c.addProperty("seconds", Math.max(20, v)));
                y += 20;
                flow(x0, y);
                fl(t("ui.obj.fail"));
                fDbl(28, HordeJson.dbl(c, "failPercent", 15), v -> c.addProperty("failPercent", Math.max(0, Math.min(200, v))));
                fl("%");
                y += 20;
                flow(x0, y);
                fl(t("ui.obj.success"));
                fDbl(28, HordeJson.dbl(c, "successPercent", 10), v -> c.addProperty("successPercent", Math.max(0, Math.min(90, v))));
                fl("%");
                y += 22;
                label(x0, y, t("ui.obj.purify_hint"));
            }
            default -> {}
        }
    }

    /** Ligne « Créature » d'un objectif (texte + liste + œuf). */
    private void objEntityRow(JsonObject c, String def, int x, int y) {
        if (!c.has("entityType")) c.addProperty("entityType", def);
        label(x, y + 3, t("ui.obj.entity"));
        text(x + 52, y, 150, HordeJson.str(c, "entityType", def), v -> c.addProperty("entityType", v.trim()));
        listBtn(x + 205, y - 1, HordeJson.str(c, "entityType", def), id -> c.addProperty("entityType", id));
        eggBtn(x + 223, y - 1, id -> c.addProperty("entityType", id));
    }

    /** Récompense au trésor commun : ◆ / Bois / Pierre / Fer / Essence. */
    private void objRewardRow(JsonObject c, int[] def, int x, int y) {
        JsonObject r = HordeJson.obj(c, "reward");
        String[] k = {"money", "wood", "stone", "iron", "essence"};
        String[] lbl = {"§e◆", "§6" + t("obj.res.wood"), "§7" + t("obj.res.stone"), "§f" + t("obj.res.iron"), "§d" + t("obj.res.essence")};
        for (int i = 0; i < k.length; i++) if (!r.has(k[i])) r.addProperty(k[i], def[i]);
        flow(x, y);
        fl(t("ui.obj.reward"));
        for (int i = 0; i < k.length; i++) {
            final String key = k[i];
            fl(lbl[i]);
            fInt(24, HordeJson.num(r, key, def[i]), v -> r.addProperty(key, Math.max(0, v)));
        }
    }

    /** Butin personnalisable d'un objectif (prérempli avec le butin par défaut). */
    private void objDrops(JsonObject c, String[][] defaults, int x, int y) {
        if (!c.has("drops")) {
            JsonArray a = new JsonArray();
            for (String[] d : defaults) {
                JsonObject e = new JsonObject();
                e.addProperty("item", d[0]);
                e.addProperty("minQty", Integer.parseInt(d[1]));
                e.addProperty("maxQty", Integer.parseInt(d[2]));
                e.addProperty("chance", Double.parseDouble(d[3]));
                a.add(e);
            }
            c.add("drops", a);
        }
        JsonArray drops = HordeJson.arr(c, "drops");
        label(x, y + 3, t("ui.obj.drops"));
        button(x + 60, y - 1, 54, com.wavesurvivor.i18n.WSLang.t("ui.butin"), () -> openPicker(t("ui.obj.drops"), id -> {
            JsonObject d = new JsonObject();
            d.addProperty("item", id);
            d.addProperty("minQty", 1);
            d.addProperty("maxQty", 1);
            d.addProperty("chance", 100);
            HordeJson.arr(c, "drops").add(d);
            objDropScroll = Math.max(0, HordeJson.arr(c, "drops").size() - 1);
            changed();
            init();
        }));
        label(x + 120, y + 3, com.wavesurvivor.i18n.WSLang.t("ui.min_max"));
        int ry0 = y + 18;
        int rows = Math.max(1, (top + H - 8 - ry0) / 18);
        objDropScroll = Math.max(0, Math.min(objDropScroll, Math.max(0, drops.size() - rows)));
        for (int i = 0; i < rows && i + objDropScroll < drops.size(); i++) {
            final int idx = i + objDropScroll;
            JsonObject d = at(drops, idx);
            if (d == null) continue;
            int ry = ry0 + i * 18;
            iconSlot(x, ry - 2, () -> HordeJson.str(d, "item", ""), v -> d.addProperty("item", v), null);
            intBox(x + 24, ry, 24, HordeJson.num(d, "minQty", 1), v -> d.addProperty("minQty", Math.max(0, v)));
            intBox(x + 54, ry, 24, HordeJson.num(d, "maxQty", 1), v -> d.addProperty("maxQty", Math.max(0, v)));
            dblBox(x + 84, ry, 30, HordeJson.dbl(d, "chance", 100), v -> d.addProperty("chance", Math.max(0, Math.min(100, v))));
            button(x + 120, ry - 1, 16, "§c✖", () -> { drops.remove(idx); changed(); init(); });
        }
        if (drops.isEmpty()) label(x, ry0 + 3, t("ui.obj.no_drops"));
        if (drops.size() > rows) {
            button(x + 142, ry0 - 1, 16, "▲", () -> { objDropScroll--; init(); }).active = objDropScroll > 0;
            button(x + 142, ry0 + (rows - 1) * 18 - 1, 16, "▼", () -> { objDropScroll++; init(); }).active = objDropScroll + rows < drops.size();
        }
    }

    /** Un marchand change de nom : ses emplacements de la Disposition (onglet Autel) le suivent. */
    private void renameMerchantSlots(String oldName, String newName) {
        if (altar == null || oldName == null || oldName.equals(newName)) return;
        JsonObject z = altar.has("zone") && altar.get("zone").isJsonObject() ? altar.getAsJsonObject("zone") : null;
        if (z == null || !z.has("slots") || !z.get("slots").isJsonArray()) return;
        for (JsonElement e : z.getAsJsonArray("slots")) {
            if (!e.isJsonObject()) continue;
            JsonObject s = e.getAsJsonObject();
            if ("merchant".equals(HordeJson.str(s, "type", "")) && oldName.equalsIgnoreCase(HordeJson.str(s, "name", ""))) {
                s.addProperty("name", newName);
            }
        }
    }

    private void buildMerchants() {
        JsonObject cd = cd();
        int x0 = listX(), y = top + 46;
        // Mode Kingdom : 2 sous-onglets (Marchands / Brèches)
        if (kingdom()) {
            button(x0, y - 2, 110, (calmSub == 0 ? "§e§l" : "§7") + t("ui.kgd.sub_merchants"), () -> { calmSub = 0; init(); });
            button(x0 + 114, y - 2, 110, (calmSub == 1 ? "§e§l" : "§7") + t("ui.kgd.sub_breaches"), () -> { calmSub = 1; selBreach = -1; init(); });
            button(x0 + 228, y - 2, 110, (calmSub == 2 ? "§e§l" : "§7") + t("ui.obj.sub"), () -> { calmSub = 2; objDropScroll = 0; init(); });
            y += 24;
            if (calmSub == 1) {
                buildBreaches(cd, x0, y);
                return;
            }
            if (calmSub == 2) {
                buildObjectives(cd, x0, y);
                return;
            }
        }
        // Pauses
        if (kingdom()) label(x0, y + 4, com.wavesurvivor.i18n.WSLang.t("ui.kgd.calm_hint"));
        else toggle(x0, y, 120, com.wavesurvivor.i18n.WSLang.t("ui.pauses"), cd, "useWavePauses", false);
        JsonArray ps = pauses();
        int px = x0 + 126;
        for (int i = 0; i < (kingdom() ? 0 : Math.min(ps.size(), 2)); i++) {
            JsonObject p = at(ps, i);
            if (p == null) continue;
            final int idx = i;
            flow(px, y + i * 18 + 1);
            fl(com.wavesurvivor.i18n.WSLang.t("ui.toutes_les"));
            fInt(24, HordeJson.num(p, "interval", 3), v -> p.addProperty("interval", Math.max(1, v)));
            fl(com.wavesurvivor.i18n.WSLang.t("ui.vagues_pause_de"));
            fInt(34, HordeJson.num(p, "pauseDuration", 60), v -> p.addProperty("pauseDuration", Math.max(1, v)));
            fl("s");
            fBtn(16, "§c✖", () -> { ps.remove(idx); changed(); init(); });
            if (i == ps.size() - 1 && ps.size() < 2) {
                fBtn(60, com.wavesurvivor.i18n.WSLang.t("ui.pause"), () -> {
                    JsonObject np = new JsonObject();
                    np.addProperty("interval", 3);
                    np.addProperty("pauseDuration", 60);
                    pauses().add(np);
                    changed();
                    init();
                });
            }
        }
        if (ps.isEmpty() && !kingdom()) {
            button(px, y, 60, com.wavesurvivor.i18n.WSLang.t("ui.pause"), () -> {
                JsonObject p = new JsonObject();
                p.addProperty("interval", 3);
                p.addProperty("pauseDuration", 60);
                pauses().add(p);
                changed();
                init();
            });
        }
        y += 40;

        // Marchands
        toggle(x0, y, 134, com.wavesurvivor.i18n.WSLang.t("ui.marchands"), cd, "useMerchants", false);
        int ly = y + 20;
        merchantList.place(x0, ly, 134, rowsFrom(ly));
        int by = merchantList.bottom() + 4;
        JsonObject m = at(merchants(), selMerchant);
        button(x0, by, 22, "§a+", () -> {
            JsonObject nm = new JsonObject();
            nm.addProperty("name", com.wavesurvivor.i18n.WSLang.t("ui.marchand"));
            nm.addProperty("entityType", "minecraft:villager");
            nm.addProperty("profession", "cleric");
            JsonObject off = new JsonObject();
            off.addProperty("x", 3); off.addProperty("y", 0); off.addProperty("z", 2);
            nm.add("offset", off);
            nm.addProperty("spawnChance", 1.0);
            nm.add("trades", new JsonArray());
            merchants().add(nm);
            selMerchant = merchants().size() - 1;
            changed();
            init();
        });
        button(x0 + 24, by, 22, "📋", () -> { if (m != null) copyJson(m, com.wavesurvivor.i18n.WSLang.t("ui.marchand_781a") + HordeJson.str(m, "name", "") + " »"); }).active = m != null;
        button(x0 + 48, by, 22, "📥", () -> {
            JsonObject o = clipboard();
            if (o == null || !o.has("trades") || o.has("type")) { status = com.wavesurvivor.i18n.WSLang.t("ui.le_presse_papiers_ne_contient_pas_de_mar"); return; }
            merchants().add(o);
            selMerchant = merchants().size() - 1;
            status = com.wavesurvivor.i18n.WSLang.t("ui.marchand_5f27") + HordeJson.str(o, "name", "") + com.wavesurvivor.i18n.WSLang.t("ui.colle");
            changed();
            init();
        });
        button(x0 + 72, by, 22, "§c🗑", () -> {
            if (m == null) return;
            merchants().remove(selMerchant);
            selMerchant = Math.min(selMerchant, merchants().size() - 1);
            changed();
            init();
        }).active = m != null;

        if (m == null) {
            label(detailX(), ly + 20, com.wavesurvivor.i18n.WSLang.t("ui.selectionne_un_marchand_ou_crees_en_un"));
            label(detailX(), ly + 32, com.wavesurvivor.i18n.WSLang.t("ui.il_apparait_entre_les_vagues_pres_du_poi"));
            return;
        }
        int x = detailX(), fy = ly;
        flow(x, fy);
        fl(com.wavesurvivor.i18n.WSLang.t("ui.nom"));
        fText(120, HordeJson.str(m, "name", ""), v -> {
            String old = HordeJson.str(m, "name", "");
            m.addProperty("name", v);
            renameMerchantSlots(old, v); // la Disposition suit le marchand renommé
        });
        fl(com.wavesurvivor.i18n.WSLang.t("ui.metier"));
        fText(70, HordeJson.str(m, "profession", "none"), v -> m.addProperty("profession", v.trim()));
        fx -= 4;
        fBtn(14, "▶", () -> {
            m.addProperty("profession", cycle(List.of(PROFESSIONS), HordeJson.str(m, "profession", "none"), 1));
            changed();
            init();
        });
        fy += 18;
        JsonObject off = HordeJson.obj(m, "offset");
        flow(x, fy);
        fl(com.wavesurvivor.i18n.WSLang.t("ui.position_x_y_z"));
        fInt(24, HordeJson.num(off, "x", 0), v -> off.addProperty("x", v));
        fx -= 4;
        fInt(24, HordeJson.num(off, "y", 0), v -> off.addProperty("y", v));
        fx -= 4;
        fInt(24, HordeJson.num(off, "z", 0), v -> off.addProperty("z", v));
        fl(com.wavesurvivor.i18n.WSLang.t("ui.chance"));
        fInt(28, (int) Math.round(HordeJson.dbl(m, "spawnChance", 1.0) * 100),
                v -> m.addProperty("spawnChance", Math.max(0, Math.min(100, v)) / 100.0));
        fl("%");
        fl(com.wavesurvivor.i18n.WSLang.t("ui.duree"));
        fInt(28, HordeJson.num(m, "duration", 0), v -> m.addProperty("duration", Math.max(0, v)));
        fl("s");
        fy += 18;
        label(x, fy + 2, com.wavesurvivor.i18n.WSLang.t("ui.position_par_rapport_au_point_de_horde_d"));
        fy += 14;
        buildTrades(HordeJson.arr(m, "trades"), x, fy);
    }

    /** Liste d'échanges (marchands et marchand du chaos) : [in1]×n + [in2]×n → [out]×n  max n  ✖ */
    private void buildTrades(JsonArray trades, int x, int y) {
        label(x, y + 3, com.wavesurvivor.i18n.WSLang.t("ui.echanges_clic_sur_une_case_choisir_l_obj"));
        button(x + 250, y, 64, com.wavesurvivor.i18n.WSLang.t("ui.echange"), () -> {
            JsonObject t = new JsonObject();
            t.addProperty("input1", "minecraft:emerald");
            t.addProperty("input1Count", 1);
            t.addProperty("input2", "");
            t.addProperty("input2Count", 0);
            t.addProperty("output", "minecraft:bread");
            t.addProperty("outputCount", 1);
            t.addProperty("maxUses", 12);
            trades.add(t);
            tradeScroll = Math.max(0, trades.size() - 1);
            changed();
            init();
        });
        int ry0 = y + 20;
        int rows = Math.max(1, (top + H - 8 - ry0) / 20);
        tradeScroll = Math.max(0, Math.min(tradeScroll, Math.max(0, trades.size() - rows)));
        for (int i = 0; i < rows && i + tradeScroll < trades.size(); i++) {
            final int idx = i + tradeScroll;
            JsonObject t = at(trades, idx);
            if (t == null) continue;
            int ry = ry0 + i * 20;
            iconSlot(x, ry, () -> HordeJson.str(t, "input1", ""), v -> t.addProperty("input1", v), null);
            intBox(x + 20, ry + 2, 20, HordeJson.num(t, "input1Count", 1), v -> t.addProperty("input1Count", Math.max(1, v)));
            label(x + 43, ry + 5, "+");
            iconSlot(x + 50, ry, () -> HordeJson.str(t, "input2", ""), v -> {
                t.addProperty("input2", v);
                if (HordeJson.num(t, "input2Count", 0) <= 0) t.addProperty("input2Count", 1);
            }, () -> { t.addProperty("input2", ""); t.addProperty("input2Count", 0); });
            intBox(x + 70, ry + 2, 20, HordeJson.num(t, "input2Count", 0), v -> t.addProperty("input2Count", Math.max(0, v)));
            label(x + 93, ry + 5, "→");
            iconSlot(x + 102, ry, () -> HordeJson.str(t, "output", ""), v -> t.addProperty("output", v), null);
            intBox(x + 122, ry + 2, 20, HordeJson.num(t, "outputCount", 1), v -> t.addProperty("outputCount", Math.max(1, v)));
            label(x + 146, ry + 5, "§8max");
            intBox(x + 166, ry + 2, 22, HordeJson.num(t, "maxUses", 12), v -> t.addProperty("maxUses", Math.max(1, v)));
            button(x + 192, ry + 1, 16, "§c✖", () -> { trades.remove(idx); changed(); init(); });
        }
        if (trades.isEmpty()) label(x, ry0 + 4, com.wavesurvivor.i18n.WSLang.t("ui.aucun_echange"));
        if (trades.size() > rows) {
            button(x + 212, ry0, 14, "▲", () -> { tradeScroll--; init(); }).active = tradeScroll > 0;
            button(x + 212, ry0 + (rows - 1) * 20, 14, "▼", () -> { tradeScroll++; init(); }).active = tradeScroll + rows < trades.size();
        }
    }

    // ─── Onglet Chaos ───

    private void buildChaos() {
        JsonObject cd = cd();
        int x0 = listX(), y = top + 46;
        toggle(x0, y, 134, com.wavesurvivor.i18n.WSLang.t("ui.chaos"), cd, "chaosEnabled", false);
        // Placement « au fil » : les champs suivent la largeur réelle du texte (pas de chevauchement, FR comme EN)
        flow(detailX(), y + 1);
        fl(kt("ui.un_evenement_toutes_les", "ui.kgd.chaos_every"));
        fInt(30, HordeJson.num(cd, "chaosMinInterval", 60), v -> cd.addProperty("chaosMinInterval", Math.max(1, v)));
        fl(com.wavesurvivor.i18n.WSLang.t("ui.chaos_to"));
        fInt(30, HordeJson.num(cd, "chaosMaxInterval", 120), v -> cd.addProperty("chaosMaxInterval", Math.max(1, v)));
        fl("s");

        int ly = y + 22;
        chaosList.place(x0, ly, 134, rowsFrom(ly));
        int by = chaosList.bottom() + 4;
        JsonObject ev = at(chaosEvents(), selChaos);
        button(x0, by, 22, "§a+", () -> {
            JsonObject e = new JsonObject();
            e.addProperty("type", "spawn");
            e.addProperty("message", com.wavesurvivor.i18n.WSLang.t("ui.une_menace_apparait"));
            e.addProperty("sound", "minecraft:entity.lightning_bolt.thunder");
            e.addProperty("entityType", "minecraft:ravager");
            e.addProperty("isCustomEntity", false);
            e.addProperty("count", 1);
            chaosEvents().add(e);
            selChaos = chaosEvents().size() - 1;
            changed();
            init();
        });
        button(x0 + 24, by, 22, "📋", () -> { if (ev != null) copyJson(ev, com.wavesurvivor.i18n.WSLang.t("ui.evenement")); }).active = ev != null;
        button(x0 + 48, by, 22, "📥", () -> {
            JsonObject o = clipboard();
            if (o == null || !o.has("type")) { status = com.wavesurvivor.i18n.WSLang.t("ui.le_presse_papiers_ne_contient_pas_d_even"); return; }
            chaosEvents().add(o);
            selChaos = chaosEvents().size() - 1;
            status = com.wavesurvivor.i18n.WSLang.t("ui.evenement_colle");
            changed();
            init();
        });
        button(x0 + 72, by, 22, "§c🗑", () -> {
            if (ev == null) return;
            chaosEvents().remove(selChaos);
            selChaos = Math.min(selChaos, chaosEvents().size() - 1);
            changed();
            init();
        }).active = ev != null;

        if (ev == null) {
            label(detailX(), ly + 20, com.wavesurvivor.i18n.WSLang.t("ui.selectionne_un_evenement_ou_crees_en_un"));
            label(detailX(), ly + 32, com.wavesurvivor.i18n.WSLang.t("ui.apparition_marchand_mystere_eclairs_sur"));
            return;
        }
        int x = detailX(), fy = ly;
        String type = HordeJson.str(ev, "type", "spawn");
        label(x, fy + 3, com.wavesurvivor.i18n.WSLang.t("ui.type_a1fa"));
        button(x + 30, fy - 1, 110, switch (type) {
            case "merchant" -> com.wavesurvivor.i18n.WSLang.t("§a🛒 Marchand mystère");
            case "lightning" -> com.wavesurvivor.i18n.WSLang.t("§e⚡ Éclairs");
            case "spawn" -> com.wavesurvivor.i18n.WSLang.t("§c☠ Apparition");
            case "totem" -> com.wavesurvivor.i18n.WSLang.t("§6⚜ Totems");
            case "gisement" -> com.wavesurvivor.i18n.WSLang.t("§e⛏ Gisement");
            case "arbre" -> com.wavesurvivor.i18n.WSLang.t("chaos.type.arbre");
            default -> "§8" + type + com.wavesurvivor.i18n.WSLang.t("ui.non_gere");
        }, () -> {
            String next = cycle(List.of(CHAOS_TYPES), HordeJson.str(ev, "type", "spawn"), 1);
            ev.addProperty("type", next);
            // Passage en Arbre : blocs et butin par défaut (chêne) si rien de personnalisé
            if ("arbre".equals(next)) {
                JsonArray bl = HordeJson.arr(ev, "blocks");
                if (bl.isEmpty() || (bl.size() == 2 && "minecraft:iron_ore".equals(bl.get(0).getAsString()))) {
                    JsonArray nb = new JsonArray();
                    nb.add("minecraft:oak_log");
                    nb.add("minecraft:oak_leaves");
                    ev.add("blocks", nb);
                }
                if (HordeJson.arr(ev, "drops").isEmpty()) {
                    JsonObject d = new JsonObject();
                    d.addProperty("item", "minecraft:oak_log");
                    d.addProperty("minQty", 2);
                    d.addProperty("maxQty", 4);
                    d.addProperty("chance", 100);
                    HordeJson.arr(ev, "drops").add(d);
                }
                if (!ev.has("minTier")) ev.addProperty("minTier", 0);
            }
            changed();
            init();
        });
        label(x + 146, fy + 3, com.wavesurvivor.i18n.WSLang.t("ui.clic_changer_de_type"));
        fy += 18;
        label(x, fy + 3, com.wavesurvivor.i18n.WSLang.t("ui.message"));
        text(x + 44, fy, 212, HordeJson.str(ev, "message", ""), v -> ev.addProperty("message", v));
        fy += 18;
        label(x, fy + 3, com.wavesurvivor.i18n.WSLang.t("ui.son"));
        text(x + 44, fy, 212, HordeJson.str(ev, "sound", ""), v -> ev.addProperty("sound", v.trim()));
        fy += 20;
        switch (type) {
            case "spawn" -> {
                label(x, fy + 3, com.wavesurvivor.i18n.WSLang.t("ui.entite"));
                text(x + 44, fy, 96, HordeJson.str(ev, "entityType", ""), v -> {
                    ev.addProperty("entityType", v.trim());
                    ev.addProperty("isCustomEntity", customEntities.contains(v.trim()));
                });
                listBtn(x + 143, fy - 1, HordeJson.str(ev, "entityType", ""), id -> {
                    ev.addProperty("entityType", id);
                    ev.addProperty("isCustomEntity", customEntities.contains(id));
                });
                eggBtn(x + 161, fy - 1, id -> { ev.addProperty("entityType", id); ev.addProperty("isCustomEntity", false); });
                button(x + 188, fy - 1, 16, "◀", () -> setChaosEntity(ev, -1));
                button(x + 206, fy - 1, 16, "▶", () -> setChaosEntity(ev, 1));
                label(x + 226, fy + 3, "×");
                intBox(x + 234, fy, 22, HordeJson.num(ev, "count", 1), v -> ev.addProperty("count", Math.max(1, v)));
                fy += 18;
                label(x, fy + 3, HordeJson.bool(ev, "isCustomEntity", false) ? com.wavesurvivor.i18n.WSLang.t("ui.entite_custom") : com.wavesurvivor.i18n.WSLang.t("ui.mob_vanilla"));
            }
            case "lightning" -> {
                label(x, fy + 3, com.wavesurvivor.i18n.WSLang.t("ui.degats_3efd"));
                intBox(x + 44, fy, 26, HordeJson.num(ev, "damage", 5), v -> ev.addProperty("damage", Math.max(0, v)));
                label(x + 76, fy + 3, com.wavesurvivor.i18n.WSLang.t("ui.par_joueur_touche"));
            }
            case "merchant" -> {
                label(x, fy + 3, com.wavesurvivor.i18n.WSLang.t("ui.duree"));
                intBox(x + 44, fy, 30, HordeJson.num(ev, "lifetime", 60), v -> ev.addProperty("lifetime", Math.max(10, v)));
                label(x + 78, fy + 3, com.wavesurvivor.i18n.WSLang.t("ui.s_avant_de_disparaitre"));
                fy += 18;
                buildTrades(HordeJson.arr(ev, "trades"), x, fy);
            }
            case "totem" -> {
                String aura = HordeJson.str(ev, "aura", "fureur");
                flow(x, fy);
                fl(com.wavesurvivor.i18n.WSLang.t("ui.aura"));
                fBtn(110, switch (aura) {
                    case "soin" -> com.wavesurvivor.i18n.WSLang.t("§a✚ Régénération");
                    case "malediction" -> com.wavesurvivor.i18n.WSLang.t("§5☠ Malédiction");
                    case "invocation" -> com.wavesurvivor.i18n.WSLang.t("§6✦ Invocation");
                    default -> com.wavesurvivor.i18n.WSLang.t("ui.fureur");
                }, () -> {
                    ev.addProperty("aura", cycle(List.of(TOTEM_AURAS), HordeJson.str(ev, "aura", "fureur"), 1));
                    changed();
                    init();
                });
                fl(com.wavesurvivor.i18n.WSLang.t("ui.nombre"));
                fInt(22, HordeJson.num(ev, "count", 1), v -> ev.addProperty("count", Math.max(1, Math.min(6, v))));
                fy += 18;
                flow(x, fy);
                fl(com.wavesurvivor.i18n.WSLang.t("ui.pv"));
                fInt(30, (int) HordeJson.dbl(ev, "totemHealth", 40), v -> ev.addProperty("totemHealth", Math.max(5, v)));
                fl(com.wavesurvivor.i18n.WSLang.t("ui.rayon"));
                fInt(24, (int) HordeJson.dbl(ev, "radius", 8), v -> ev.addProperty("radius", Math.max(2, v)));
                fl(com.wavesurvivor.i18n.WSLang.t("ui.duree"));
                fInt(30, HordeJson.num(ev, "lifetime", 60), v -> ev.addProperty("lifetime", Math.max(10, v)));
                fl("s");
                fy += 18;
                flow(x, fy);
                fl(com.wavesurvivor.i18n.WSLang.t("ui.recompense"));
                fIcon(() -> HordeJson.str(ev, "rewardItem", "minecraft:emerald"), v -> ev.addProperty("rewardItem", v));
                fl("×");
                fInt(22, HordeJson.num(ev, "rewardCount", 3), v -> ev.addProperty("rewardCount", Math.max(0, v)));
                fl(com.wavesurvivor.i18n.WSLang.t("ui.tete"));
                iconSlot(fx, fy - 2, () -> HordeJson.str(ev, "headItem", ""), v -> ev.addProperty("headItem", v),
                        () -> ev.addProperty("headItem", ""));
                fx += 24;
                fl(com.wavesurvivor.i18n.WSLang.t("ui.vide_tete_du_role"));
                fy += 20;
                if ("invocation".equals(aura)) {
                    flow(x, fy);
                    fl(com.wavesurvivor.i18n.WSLang.t("ui.invoque"));
                    fText(78, HordeJson.str(ev, "entityType", "minecraft:zombie"), v -> ev.addProperty("entityType", v.trim()));
                    fBtn(16, "§e▼", () -> openEntityPicker(HordeJson.str(ev, "entityType", ""), id -> ev.addProperty("entityType", id)));
                    fBtn(24, t("ui.egg.btn"), () -> openEggPicker(id -> ev.addProperty("entityType", id), false));
                    fx -= 4;
                    fBtn(14, "▶", () -> {
                        ev.addProperty("entityType", cycle(allTypes, HordeJson.str(ev, "entityType", "minecraft:zombie"), 1));
                        changed();
                        init();
                    });
                    fl("×");
                    fInt(20, HordeJson.num(ev, "summonCount", 2), v -> ev.addProperty("summonCount", Math.max(1, v)));
                    fl(com.wavesurvivor.i18n.WSLang.t("ui.toutes_les_c0b6"));
                    fInt(24, HordeJson.num(ev, "summonInterval", 10), v -> ev.addProperty("summonInterval", Math.max(2, v)));
                    fl(com.wavesurvivor.i18n.WSLang.t("ui.s_max"));
                    fInt(20, HordeJson.num(ev, "summonMax", 6), v -> ev.addProperty("summonMax", Math.max(1, v)));
                    fy += 18;
                }
                if ("soin".equals(aura)) {
                    flow(x, fy);
                    fl(com.wavesurvivor.i18n.WSLang.t("ui.soin_des_monstres"));
                    fDbl(30, HordeJson.dbl(ev, "healPerSecond", 2), v -> ev.addProperty("healPerSecond", Math.max(0, v)));
                    fl(com.wavesurvivor.i18n.WSLang.t("ui.pv_s"));
                    fy += 18;
                }
                // ── Effets de l'aura ──
                JsonArray fxs = HordeJson.arr(ev, "effects");
                label(x, fy + 3, fxs.isEmpty() ? com.wavesurvivor.i18n.WSLang.t("ui.effets_vide_effets_par_defaut") : com.wavesurvivor.i18n.WSLang.t("ui.effets_effet_niveau_cible"));
                button(x + 190, fy - 1, 58, com.wavesurvivor.i18n.WSLang.t("ui.effet"), () -> {
                    JsonArray list = HordeJson.arr(ev, "effects");
                    if (list.isEmpty()) { // on part des effets par défaut pour les modifier
                        for (var d : com.wavesurvivor.horde.chaos.TotemAuraManager.defaults(HordeJson.str(ev, "aura", "fureur"))) {
                            JsonObject o = new JsonObject();
                            o.addProperty("effect", d.effect);
                            o.addProperty("level", d.level);
                            o.addProperty("target", d.target);
                            list.add(o);
                        }
                    }
                    JsonObject o = new JsonObject();
                    o.addProperty("effect", "minecraft:blindness");
                    o.addProperty("level", 1);
                    o.addProperty("target", "joueurs");
                    list.add(o);
                    totemFxScroll = Math.max(0, list.size() - 1);
                    changed();
                    init();
                });
                int fy0 = fy + 18;
                int frows = Math.max(1, (top + H - 8 - fy0) / 18);
                totemFxScroll = Math.max(0, Math.min(totemFxScroll, Math.max(0, fxs.size() - frows)));
                for (int i = 0; i < frows && i + totemFxScroll < fxs.size(); i++) {
                    final int idx = i + totemFxScroll;
                    JsonObject o = at(fxs, idx);
                    if (o == null) continue;
                    int ry = fy0 + i * 18;
                    text(x, ry, 118, HordeJson.str(o, "effect", ""), v -> o.addProperty("effect", v.trim()));
                    button(x + 120, ry - 1, 14, "◀", () -> { o.addProperty("effect", cycle(List.of(EFFECTS), HordeJson.str(o, "effect", ""), -1)); changed(); init(); });
                    button(x + 136, ry - 1, 14, "▶", () -> { o.addProperty("effect", cycle(List.of(EFFECTS), HordeJson.str(o, "effect", ""), 1)); changed(); init(); });
                    label(x + 154, ry + 3, com.wavesurvivor.i18n.WSLang.t("ui.niv_2bc6"));
                    intBox(x + 176, ry, 20, HordeJson.num(o, "level", 1), v -> o.addProperty("level", Math.max(1, Math.min(10, v))));
                    boolean onPlayers = !"monstres".equalsIgnoreCase(HordeJson.str(o, "target", "joueurs"));
                    button(x + 200, ry - 1, 62, onPlayers ? com.wavesurvivor.i18n.WSLang.t("ui.joueurs") : com.wavesurvivor.i18n.WSLang.t("ui.monstres_70a5"), () -> {
                        o.addProperty("target", "monstres".equalsIgnoreCase(HordeJson.str(o, "target", "joueurs")) ? "joueurs" : "monstres");
                        changed();
                        init();
                    });
                    button(x + 264, ry - 1, 16, "§c✖", () -> { fxs.remove(idx); changed(); init(); });
                }
                if (fxs.isEmpty()) {
                    StringBuilder sb = new StringBuilder(com.wavesurvivor.i18n.WSLang.t("ui.par_defaut"));
                    var defs = com.wavesurvivor.horde.chaos.TotemAuraManager.defaults(aura);
                    if (defs.isEmpty()) sb.append(com.wavesurvivor.i18n.WSLang.t("ui.aucun_effet")).append("soin".equals(aura) ? com.wavesurvivor.i18n.WSLang.t("ui.soin_seul") : com.wavesurvivor.i18n.WSLang.t("ui.invocation_seule")).append(")");
                    for (int i = 0; i < defs.size(); i++) {
                        var d = defs.get(i);
                        if (i > 0) sb.append(", ");
                        sb.append(d.effect.replace("minecraft:", "")).append(" ").append(d.level).append(" (").append(d.target).append(")");
                    }
                    label(x, fy0 + 3, sb.toString());
                }
                if (fxs.size() > frows) {
                    button(x + 284, fy0 - 1, 14, "▲", () -> { totemFxScroll--; init(); }).active = totemFxScroll > 0;
                    button(x + 284, fy0 + Math.max(0, frows - 1) * 18 - 1 + (frows < 2 ? 16 : 0), 14, "▼", () -> { totemFxScroll++; init(); })
                            .active = totemFxScroll + frows < fxs.size();
                }
            }
            case "gisement", "arbre" -> {
                boolean treeEv = "arbre".equals(type);
                if (treeEv) {
                    // Arbre : tronc et feuillage séparés, plusieurs blocs chacun (mélangés sur l'arbre) — clic droit : retirer
                    JsonArray old = HordeJson.arr(ev, "blocks");
                    JsonArray tr = HordeJson.arr(ev, "trunkBlocks"), lf = HordeJson.arr(ev, "leafBlocks");
                    if (tr.isEmpty()) tr.add(old.size() > 0 ? old.get(0).getAsString() : "minecraft:oak_log");
                    if (lf.isEmpty()) lf.add(old.size() > 1 ? old.get(1).getAsString() : "minecraft:oak_leaves");
                    treeBlockRow(ev, "trunkBlocks", "ui.tronc", x, fy, 1);
                    treeBlockRow(ev, "leafBlocks", "ui.feuillage", x + 146, fy, 1);
                    fy += 22;
                    // Fruits lumineux (shroomlight par défaut ; tous retirés = arbre sans fruits)
                    if (!ev.has("fruitBlocks")) HordeJson.arr(ev, "fruitBlocks").add("minecraft:shroomlight");
                    treeBlockRow(ev, "fruitBlocks", "ui.fruits", x, fy, 0);
                    fy += 22;
                } else {
                // Blocs visuels (clic droit : retirer)
                label(x, fy + 4, com.wavesurvivor.i18n.WSLang.t("ui.blocs"));
                JsonArray bl = HordeJson.arr(ev, "blocks");
                if (bl.isEmpty()) { bl.add("minecraft:iron_ore"); bl.add("minecraft:stone"); }
                for (int i = 0; i < Math.min(bl.size(), 5); i++) {
                    final int idx = i;
                    iconSlot(x + 34 + i * 22, fy - 1, () -> bl.get(idx).getAsString(), v -> bl.set(idx, new com.google.gson.JsonPrimitive(v)),
                            () -> { if (bl.size() > 1) bl.remove(idx); });
                }
                if (bl.size() < 5) button(x + 34 + Math.min(bl.size(), 5) * 22, fy, 16, "§a+", () -> openPicker(com.wavesurvivor.i18n.WSLang.t("ui.bloc_du_gisement"), id -> {
                    HordeJson.arr(ev, "blocks").add(id);
                    changed();
                    init();
                }));
                fy += 22;
                }
                flow(x, fy);
                fl(com.wavesurvivor.i18n.WSLang.t("ui.solidite"));
                fInt(24, HordeJson.num(ev, "hits", 8), v -> ev.addProperty("hits", Math.max(1, v)));
                fl(com.wavesurvivor.i18n.WSLang.t(treeEv ? "ui.hache" : "ui.pioche"));
                int tier = Math.max(0, Math.min(4, HordeJson.num(ev, "minTier", 1)));
                fBtn(64, "§f" + PICKAXE_TIERS[tier], () -> {
                    ev.addProperty("minTier", (Math.max(0, Math.min(4, HordeJson.num(ev, "minTier", 1))) + 1) % 5);
                    changed();
                    init();
                });
                fl(com.wavesurvivor.i18n.WSLang.t("ui.nombre"));
                fInt(20, HordeJson.num(ev, "count", 1), v -> ev.addProperty("count", Math.max(1, Math.min(5, v))));
                fy += 18;
                flow(x, fy);
                fl(com.wavesurvivor.i18n.WSLang.t("ui.taille"));
                String size = HordeJson.str(ev, "size", "petit");
                fBtn(84, switch (size) {
                    case "moyen" -> com.wavesurvivor.i18n.WSLang.t("§fNormal §8(×2)");
                    case "grand" -> com.wavesurvivor.i18n.WSLang.t("§6Grand §8(×3,5)");
                    case "aleatoire" -> com.wavesurvivor.i18n.WSLang.t("§dAléatoire");
                    default -> com.wavesurvivor.i18n.WSLang.t("ui.petit_1");
                }, () -> {
                    ev.addProperty("size", cycle(List.of("petit", "moyen", "grand", "aleatoire"), HordeJson.str(ev, "size", "petit"), 1));
                    changed();
                    init();
                });
                fl(com.wavesurvivor.i18n.WSLang.t("ui.butin_solidite_1_1_75_2_75"));
                fy += 18;
                flow(x, fy);
                fl(com.wavesurvivor.i18n.WSLang.t("ui.distance"));
                fInt(24, HordeJson.num(ev, "distanceMin", 10), v -> ev.addProperty("distanceMin", Math.max(4, v)));
                fl("→");
                fInt(24, HordeJson.num(ev, "distanceMax", 25), v -> ev.addProperty("distanceMax", Math.max(5, v)));
                fl(com.wavesurvivor.i18n.WSLang.t("ui.blocs_duree"));
                fInt(30, HordeJson.num(ev, "lifetime", 120), v -> ev.addProperty("lifetime", Math.max(20, v)));
                fl("s");
                fy += 18;
                // Apparitions garanties à chaque Calme (Kingdom) / pause entre deux vagues (classique)
                flow(x, fy);
                fl(com.wavesurvivor.i18n.WSLang.t("ui.chaos.guaranteed"));
                fInt(20, HordeJson.num(ev, "guaranteed", 0), v -> ev.addProperty("guaranteed", Math.max(0, Math.min(5, v))));
                fl(com.wavesurvivor.i18n.WSLang.t("ui.chaos.guaranteed_hint"));
                fy += 18;
                // ── Butin : à la destruction OU par coup (liste défilante) ──
                // Migration : ancien fragment unique → liste « par coup »
                String oldFrag = HordeJson.str(ev, "hitDropItem", "");
                if (!oldFrag.isBlank()) {
                    JsonObject f = new JsonObject();
                    f.addProperty("item", oldFrag);
                    f.addProperty("minQty", 1);
                    f.addProperty("maxQty", 1);
                    f.addProperty("chance", HordeJson.dbl(ev, "hitDropChance", 25));
                    HordeJson.arr(ev, "hitDrops").add(f);
                    ev.remove("hitDropItem");
                    changed();
                }
                final String key = gisLootMode == 0 ? "drops" : "hitDrops";
                button(x, fy - 1, 150, gisLootMode == 0 ? com.wavesurvivor.i18n.WSLang.t("ui.butin_a_la_destruction_3ff3") : com.wavesurvivor.i18n.WSLang.t("ui.butin_a_chaque_coup"), () -> {
                    gisLootMode = 1 - gisLootMode;
                    gisDropScroll = 0;
                    init();
                });
                JsonArray drops = HordeJson.arr(ev, key);
                button(x + 154, fy - 1, 54, com.wavesurvivor.i18n.WSLang.t("ui.butin"), () -> openPicker(gisLootMode == 0 ? com.wavesurvivor.i18n.WSLang.t("ui.butin_a_la_destruction") : com.wavesurvivor.i18n.WSLang.t("ui.butin_par_coup"), id -> {
                    JsonObject d = new JsonObject();
                    d.addProperty("item", id);
                    d.addProperty("minQty", 1);
                    d.addProperty("maxQty", gisLootMode == 0 ? 3 : 1);
                    d.addProperty("chance", gisLootMode == 0 ? 100 : 20);
                    HordeJson.arr(ev, key).add(d);
                    gisDropScroll = Math.max(0, HordeJson.arr(ev, key).size() - 1);
                    changed();
                    init();
                }));
                label(x + 212, fy + 3, com.wavesurvivor.i18n.WSLang.t("ui.min_max"));
                int ry0 = fy + 18;
                int rows = Math.max(1, (top + H - 8 - ry0) / 18);
                gisDropScroll = Math.max(0, Math.min(gisDropScroll, Math.max(0, drops.size() - rows)));
                for (int i = 0; i < rows && i + gisDropScroll < drops.size(); i++) {
                    final int idx = i + gisDropScroll;
                    JsonObject d = at(drops, idx);
                    if (d == null) continue;
                    int ry = ry0 + i * 18;
                    iconSlot(x, ry - 2, () -> HordeJson.str(d, "item", ""), v -> d.addProperty("item", v), null);
                    intBox(x + 24, ry, 24, HordeJson.num(d, "minQty", 1), v -> d.addProperty("minQty", Math.max(0, v)));
                    intBox(x + 54, ry, 24, HordeJson.num(d, "maxQty", 1), v -> d.addProperty("maxQty", Math.max(0, v)));
                    dblBox(x + 84, ry, 30, HordeJson.dbl(d, "chance", 100), v -> d.addProperty("chance", Math.max(0, Math.min(100, v))));
                    button(x + 120, ry - 1, 16, "§c✖", () -> { drops.remove(idx); changed(); init(); });
                }
                if (drops.isEmpty()) label(x, ry0 + 3, com.wavesurvivor.i18n.WSLang.t("ui.aucun_butin_butin_pour_en_ajouter"));
                if (drops.size() > rows) {
                    button(x + 142, ry0 - 1, 16, "▲", () -> { gisDropScroll--; init(); }).active = gisDropScroll > 0;
                    button(x + (rows < 2 ? 160 : 142), ry0 + (rows < 2 ? 0 : (rows - 1) * 18) - 1, 16, "▼", () -> { gisDropScroll++; init(); }).active = gisDropScroll + rows < drops.size();
                    label(x + (rows < 2 ? 180 : 162), ry0 + 3, "§8" + (gisDropScroll + 1) + "–" + Math.min(drops.size(), gisDropScroll + rows) + " / " + drops.size());
                }
            }
            default -> label(x, fy + 3, com.wavesurvivor.i18n.WSLang.t("ui.type_non_gere_par_le_moteur_ignore_en_je"));
        }
    }

    private void setChaosEntity(JsonObject ev, int dir) {
        String t = cycle(allTypes, HordeJson.str(ev, "entityType", ""), dir);
        ev.addProperty("entityType", t);
        ev.addProperty("isCustomEntity", customEntities.contains(t));
        changed();
        init();
    }

    // ─── Sélecteur d'item (inventaire) ───

    private void openPicker(String title, Consumer<String> onPick) {
        picker = onPick;
        pickerTitle = title;
    }

    /** Sélecteur d'ŒUF : renvoie l'identifiant de l'entité invoquée par l'œuf cliqué (vanilla ou moddé). */
    private void openEggPicker(Consumer<String> onEntity, boolean retry) {
        openPicker((retry ? t("ui.egg.not_egg") : "") + t("ui.egg.title"), id -> {});
        stackPicker = st -> {
            String ent = SpawnEggs.entityId(st);
            if (ent == null) { openEggPicker(onEntity, true); return; }
            onEntity.accept(ent);
            changed();
            init();
        };
    }

    /** Bouton « Œuf » à côté d'un champ d'entité. */
    private void eggBtn(int x, int y, Consumer<String> onEntity) {
        button(x, y, 24, t("ui.egg.btn"), () -> openEggPicker(onEntity, false));
    }

    /** Liste déroulante avec recherche : entités custom + toutes les créatures du jeu. */
    private void openEntityPicker(String current, Consumer<String> onPick) {
        minecraft.setScreen(new EntityPickerScreen(this, customEntities, current, id -> { onPick.accept(id); changed(); }));
    }

    /** Bouton « ▼ » (liste déroulante) à côté d'un champ d'entité. */
    private void listBtn(int x, int y, String current, Consumer<String> onPick) {
        button(x, y, 16, "§e▼", () -> openEntityPicker(current, onPick));
    }

    private int pickerX() { return width / 2 - 9 * 9 - 6; }
    private int pickerY() { return height / 2 - 44; }

    private int pickerSlotAt(double mx, double my) {
        int gx = pickerX() + 6, gy = pickerY() + 16;
        for (int row = 0; row < 4; row++) {
            int ry = gy + row * 18 + (row == 3 ? 4 : 0);
            for (int col = 0; col < 9; col++) {
                int rx = gx + col * 18;
                if (mx >= rx && mx < rx + 17 && my >= ry && my < ry + 17) return row == 3 ? col : 9 + row * 9 + col;
            }
        }
        return -1;
    }

    private static String idOf(ItemStack st) {
        return LootItems.idOf(st); // potions comprises ("minecraft:potion#...")
    }

    private List<ItemStack> inv() {
        return Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getInventory().items : List.of();
    }

    // ─── Souris / clavier ───

    private List<ListBox> activeLists() {
        return switch (tab) {
            case T_MOBS -> List.of(mobList);
            case T_SPECIAL -> selSpecial >= 0 && selSpecialEnt < 0 ? List.of(specialList, specialEntList) : List.of(specialList);
            case T_BOSS -> List.of(bossList);
            case T_MERCH -> kingdom() && calmSub == 2 ? List.of()
                    : kingdom() && calmSub == 1 ? (selBreach < 0 ? List.of(breachList) : List.of()) : List.of(merchantList);
            case T_CHAOS -> List.of(chaosList);
            default -> List.of();
        };
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (issues != null) return clickIssues(mx, my);
        // Menu des couleurs de l'antre ouvert : un clic choisit (ou ferme si en dehors)
        if (denMenu != null) {
            String key = denMenuAt(mx, my);
            if (key != null) {
                denMenu.addProperty("denBackground", key);
                changed();
            }
            denMenu = null;
            init();
            return true;
        }
        if (picker != null) {
            int slot = pickerSlotAt(mx, my);
            List<ItemStack> items = inv();
            Consumer<String> cb = picker;
            Consumer<ItemStack> sc = stackPicker;
            picker = null;
            stackPicker = null;
            if (slot >= 0 && slot < items.size() && !items.get(slot).isEmpty()) {
                if (sc != null) sc.accept(items.get(slot));
                else cb.accept(idOf(items.get(slot)));
            }
            return true;
        }
        for (ListBox l : activeLists()) if (l.click(mx, my)) return true;
        for (IconSlot s : iconSlots) {
            if (mx >= s.x() && mx < s.x() + 18 && my >= s.y() && my < s.y() + 18) {
                if (button == 1) {
                    if (s.clear() != null) { s.clear().run(); changed(); init(); }
                } else {
                    openPicker(com.wavesurvivor.i18n.WSLang.t("ui.choisis_l_objet"), id -> { s.set().accept(id); changed(); init(); });
                }
                return true;
            }
        }
        if (clickForm(mx, my, button)) return true;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        for (ListBox l : activeLists()) if (l.contains(mx, my)) { l.scroll(delta); return true; }
        if (tab == T_PREVIEW) {
            previewScroll = Math.max(0, previewScroll - (int) Math.signum(delta));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (issues != null && key == 256) { issues = null; return true; }
        if (picker != null && key == 256) { picker = null; stackPicker = null; return true; }
        return super.keyPressed(key, scan, mods);
    }

    // ─── Rendu ───

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(left, top, left + W, top + H, 0xF0101418);
        g.fill(left, top, left + W, top + 2, 0xFFF59E0B);
        String title = HordeJson.str(horde, "hordeName", "?");
        int titleMax = Math.max(50, Math.min(170, W - 360)); // place laissée aux boutons Vérifier / Exporter / Enregistrer / Fermer
        g.drawString(font, "§6§l⚒ " + (font.width(title) > titleMax ? font.plainSubstrByWidth(title, titleMax - 6) + "…" : title)
                + (dirty ? " §e●" : "") + (collab() ? " §b👥" + editors : ""), left + 8, top + 9, 0xFFFFFFFF);

        for (Object[] l : labels) g.drawString(font, (String) l[2], (int) l[0], (int) l[1], 0xFFBBBBBB);
        for (ListBox l : activeLists()) l.render(g, mx, my);
        renderIconSlots(g, mx, my);
        if (tab == T_MOBS && at(mobs(), selMob) != null) {
            JsonObject m = at(mobs(), selMob);
            if (!customEntities.contains(HordeJson.str(m, "entity_type", ""))) {
                g.drawString(font, "§8vanilla", detailX() + 238, top + 49, 0xFFFFFFFF);
            }
        }
        renderForm(g, mx, my);
        if (tab == T_PREVIEW) renderPreview(g);

        super.render(g, mx, my, pt);
        if (formTooltip != null && picker == null) g.renderComponentTooltip(font, formTooltip, mx, my);

        if (!status.isEmpty()) g.drawCenteredString(font, status, width / 2, Math.min(top + H + 2, height - 10), 0xFFFFFFFF);
        if (picker != null) renderPicker(g, mx, my);
        if (denMenu != null) renderDenMenu(g, mx, my);
        if (issues != null) renderIssues(g, mx, my);
    }

    // ─── Vérification ───

    /** Problèmes affichés (null = fenêtre fermée) ; ouverte par un enregistrement bloqué ? */
    private List<HordeValidator.Issue> issues = null;
    private boolean issuesForSave = false;
    private static final int ISSUE_MAX = 14;

    private void openIssues() {
        List<HordeValidator.Issue> found = HordeValidator.check(horde, customEntities);
        if (found.isEmpty()) {
            status = com.wavesurvivor.i18n.WSLang.t("check.all_good");
            return;
        }
        issues = found;
        issuesForSave = false;
    }

    /** Cadre de la fenêtre : x, y, largeur, hauteur. */
    private int[] issuesBox() {
        int n = Math.min(ISSUE_MAX, issues.size());
        int w = Math.min(W - 20, 400), h = 40 + n * 12 + (issues.size() > ISSUE_MAX ? 12 : 0) + 26;
        return new int[]{(width - w) / 2, (height - h) / 2, w, h};
    }

    private void renderIssues(GuiGraphics g, int mx, int my) {
        int[] b = issuesBox();
        int x = b[0], y = b[1], w = b[2], h = b[3];
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(0, 0, width, height, 0x80000000);
        g.fill(x, y, x + w, y + h, 0xF8161B22);
        long blocking = issues.stream().filter(HordeValidator.Issue::blocking).count();
        g.fill(x, y, x + w, y + 2, blocking > 0 ? 0xFFEF4444 : 0xFFEAB308);
        g.drawCenteredString(font, com.wavesurvivor.i18n.WSLang.t(issuesForSave ? "check.title_save" : "check.title",
                blocking, issues.size() - blocking), x + w / 2, y + 8, 0xFFFFFFFF);
        g.drawCenteredString(font, com.wavesurvivor.i18n.WSLang.t("check.click_hint"), x + w / 2, y + 20, 0xFF888888);
        int ly = y + 34;
        for (int i = 0; i < Math.min(ISSUE_MAX, issues.size()); i++) {
            HordeValidator.Issue is = issues.get(i);
            boolean hover = mx >= x + 6 && mx < x + w - 6 && my >= ly - 1 && my < ly + 11;
            if (hover) g.fill(x + 6, ly - 1, x + w - 6, ly + 11, 0x30FFFFFF);
            String tabName = com.wavesurvivor.i18n.WSLang.t(kingdom() && TAB_NAMES_KINGDOM[is.tab()] != null
                    ? TAB_NAMES_KINGDOM[is.tab()] : TAB_NAMES[is.tab()]);
            String line = (is.blocking() ? "§c● " : "§e● ") + "§f" + is.text() + " §8[" + tabName.replaceAll("§.", "") + "]";
            g.drawString(font, font.plainSubstrByWidth(line, w - 16), x + 10, ly, 0xFFFFFFFF);
            ly += 12;
        }
        if (issues.size() > ISSUE_MAX) {
            g.drawString(font, com.wavesurvivor.i18n.WSLang.t("check.more", issues.size() - ISSUE_MAX), x + 10, ly, 0xFF888888);
        }
        // Boutons (dessinés ici : la fenêtre passe au-dessus des widgets de l'éditeur)
        drawIssueButton(g, mx, my, issueCloseBtn(), com.wavesurvivor.i18n.WSLang.t("ui.fermer"));
        if (issuesForSave) drawIssueButton(g, mx, my, issueSaveBtn(), com.wavesurvivor.i18n.WSLang.t("check.save_anyway"));
        g.pose().popPose();
    }

    private int[] issueCloseBtn() {
        int[] b = issuesBox();
        return new int[]{b[0] + b[2] - 76, b[1] + b[3] - 22, 70, 16};
    }

    private int[] issueSaveBtn() {
        int[] b = issuesBox();
        return new int[]{b[0] + 6, b[1] + b[3] - 22, 150, 16};
    }

    private void drawIssueButton(GuiGraphics g, int mx, int my, int[] r, String text) {
        boolean hover = mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
        g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], hover ? 0xFF4B5563 : 0xFF374151);
        g.drawCenteredString(font, text, r[0] + r[2] / 2, r[1] + 4, 0xFFFFFFFF);
    }

    private static boolean inside(double mx, double my, int[] r) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    /** Clic dans la fenêtre de vérification : ligne → onglet concerné, boutons, ou fermeture. */
    private boolean clickIssues(double mx, double my) {
        if (inside(mx, my, issueCloseBtn())) { issues = null; return true; }
        if (issuesForSave && inside(mx, my, issueSaveBtn())) {
            issues = null;
            save(true);
            return true;
        }
        int[] b = issuesBox();
        int ly = b[1] + 34;
        for (int i = 0; i < Math.min(ISSUE_MAX, issues.size()); i++) {
            if (mx >= b[0] + 6 && mx < b[0] + b[2] - 6 && my >= ly - 1 && my < ly + 11) {
                int target = issues.get(i).tab();
                issues = null;
                if (!switchTab(target)) init();
                return true;
            }
            ly += 12;
        }
        if (!inside(mx, my, b)) issues = null; // clic en dehors : fermeture
        return true;
    }

    private void renderPreview(GuiGraphics g) {
        JsonObject cd = cd();
        List<HordeJson.WaveLine> lines = HordeJson.preview(horde);
        int x = left + 10, y = top + 48;
        int maxLines = (H - 88) / 11; // place laissée aux outils de test en bas
        g.drawString(font, kt("ui.vague_par_vague_1_joueur_hors_speciales", "ui.kgd.preview_header"), x, y, 0xFFFFFFFF);
        y += 14;
        List<Component> out = new ArrayList<>();
        for (HordeJson.WaveLine wl : lines) {
            int total = 0;
            StringBuilder sb = new StringBuilder();
            for (String p : wl.parts()) {
                if (sb.length() > 0) sb.append("§8, §f");
                sb.append(p);
                try { total += Integer.parseInt(p.substring(0, p.indexOf('×'))); } catch (Exception ignored) {}
            }
            out.add(Component.literal("§6" + (kingdom() ? "A" : "V") + wl.wave() + " §8(" + total + ") §f" + (sb.length() > 0 ? sb.toString() : com.wavesurvivor.i18n.WSLang.t("ui.aucun_mob"))
                    + (wl.bosses().isEmpty() ? "" : "  §c☠ " + String.join(", ", wl.bosses()))));
        }
        // Vagues spéciales : probabilités réelles
        if (HordeJson.bool(cd, "useSpecialWaves", false) && !specials().isEmpty()) {
            int chance = HordeJson.num(cd, "specialWaveChance", 0);
            int totalW = 0;
            for (JsonElement e : specials()) if (e.isJsonObject()) totalW += Math.max(0, HordeJson.num(e.getAsJsonObject(), "chance", 0));
            out.add(Component.literal(""));
            out.add(Component.literal(com.wavesurvivor.i18n.WSLang.t("ui.vagues_speciales") + chance + com.wavesurvivor.i18n.WSLang.t("ui.de_chance_par_vague_hors_boss")));
            for (JsonElement e : specials()) {
                if (!e.isJsonObject()) continue;
                JsonObject s = e.getAsJsonObject();
                int w = Math.max(0, HordeJson.num(s, "chance", 0));
                double p = totalW > 0 ? chance * (double) w / totalW : 0;
                int n = 0;
                for (JsonElement en : HordeJson.arr(s, "entities")) if (en.isJsonObject()) n += HordeJson.num(en.getAsJsonObject(), "count", 1);
                out.add(Component.literal("  " + (w > 0 ? "§f" : "§8") + HordeJson.str(s, "name", "?").trim()
                        + " §8→ §e" + String.format(java.util.Locale.ROOT, "%.1f", p) + "% §8(" + n + com.wavesurvivor.i18n.WSLang.t("ui.mobs_c548")));
            }
        }
        int line = 0;
        for (Component c : out) {
            for (var seq : font.split(c, W - 24)) {
                if (line >= previewScroll && line < previewScroll + maxLines) {
                    g.drawString(font, seq, x, y + (line - previewScroll) * 11, 0xFFFFFFFF);
                }
                line++;
            }
        }
        previewScroll = Math.min(previewScroll, Math.max(0, line - maxLines));
        if (lines.isEmpty()) g.drawString(font, com.wavesurvivor.i18n.WSLang.t("ui.aucune_vague_regle_le_nombre_de_vagues_d"), x, y, 0xFFFFFFFF);
    }

    private void renderPicker(GuiGraphics g, int mx, int my) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        int px = pickerX(), py = pickerY();
        g.fill(0, 0, width, height, 0x88000000);
        g.fill(px, py, px + 9 * 18 + 12, py + 4 * 18 + 28, 0xFF1E1E26);
        g.fill(px, py, px + 9 * 18 + 12, py + 1, 0xFFF59E0B);
        g.drawString(font, "§e" + pickerTitle + com.wavesurvivor.i18n.WSLang.t("ui.clique_un_item_echap_annuler"), px + 6, py + 5, 0xFFFFFFFF);
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
        if (hovered >= 0 && hovered < items.size() && !items.get(hovered).isEmpty()) {
            g.renderTooltip(font, List.of(items.get(hovered).getHoverName(), Component.literal("§8" + idOf(items.get(hovered)))),
                    java.util.Optional.empty(), mx, my);
        }
    }

    /** Arbre (onglet Chaos) : une rangée de blocs (tronc, feuillage ou fruits), 4 au plus, clic droit = retirer. */
    private void treeBlockRow(JsonObject ev, String key, String labelKey, int x, int fy, int min) {
        label(x, fy + 4, com.wavesurvivor.i18n.WSLang.t(labelKey));
        JsonArray arr = HordeJson.arr(ev, key);
        int lx = x + 44;
        for (int i = 0; i < Math.min(arr.size(), 4); i++) {
            final int idx = i;
            iconSlot(lx + i * 22, fy - 1, () -> arr.get(idx).getAsString(), v -> { arr.set(idx, new com.google.gson.JsonPrimitive(v)); changed(); },
                    () -> { if (arr.size() > min) { arr.remove(idx); changed(); init(); } });
        }
        if (arr.size() < 4) button(lx + arr.size() * 22, fy, 16, "§a+", () -> openPicker(com.wavesurvivor.i18n.WSLang.t(labelKey), id -> {
            HordeJson.arr(ev, key).add(id);
            changed();
            init();
        }));
    }

    // ─── Enregistrer / fermer ───

    private void save() {
        save(false);
    }

    /** @param force enregistrer malgré des problèmes bloquants (bouton « Enregistrer quand même » de la vérification). */
    private void save(boolean force) {
        String name = HordeJson.str(horde, "hordeName", "").trim();
        if (name.isEmpty()) { status = com.wavesurvivor.i18n.WSLang.t("ui.donne_un_nom_a_la_horde_onglet_general"); return; }
        // Vérification : un problème bloquant ouvre la liste et demande confirmation
        if (!force) {
            List<HordeValidator.Issue> found = HordeValidator.check(horde, customEntities);
            if (found.stream().anyMatch(HordeValidator.Issue::blocking)) {
                issues = found;
                issuesForSave = true;
                return;
            }
        }
        status = com.wavesurvivor.i18n.WSLang.t("ui.enregistrement");
        String altarPayload = altarDeleted && altar == null ? "{\"__delete\":true}" : altar != null ? altar.toString() : "";
        // À plusieurs : seuls les onglets modifiés sont envoyés (le serveur ignore ceux occupés par un autre)
        int mask = com.wavesurvivor.horde.editor.HordeSections.ALL;
        if (collab()) {
            mask = 0;
            for (int t : dirtyTabs) mask |= com.wavesurvivor.horde.editor.HordeSections.bit(t);
            if (mask == 0) mask = com.wavesurvivor.horde.editor.HordeSections.bit(tab);
            if (!com.wavesurvivor.horde.editor.HordeSections.has(mask, T_ALTAR)) altarPayload = "";
        }
        NetworkHandler.CHANNEL.sendToServer(new HordeEditorPackets.Save(oldName,
                new GsonBuilder().disableHtmlEscaping().create().toJson(horde), altarPayload, mask));
    }

    public void onResult(HordeEditorPackets.Result r) {
        status = (r.ok ? "§a✔ " : "§c✘ ") + r.message;
        if (r.ok) {
            dirty = false;
            dirtyTabs.clear();
            if (!r.savedName.isEmpty()) oldName = r.savedName;
            snapshotTab();
            if (revertBtn != null) revertBtn.active = false;
            sendPresence(true); // nouvelle horde ou renommage : présence (re)annoncée sous son nom
        }
    }

    // ─── Édition à plusieurs ───

    /** Vrai si d'autres joueurs éditent la même horde (enregistrement par onglet, verrous). */
    private boolean collab() {
        return editors > 1 && !oldName.isEmpty();
    }

    /** Changement d'onglet : refusé à plusieurs si l'onglet actuel a des modifs non enregistrées ou si la cible est occupée. */
    private boolean switchTab(int t) {
        if (t == tab) return true;
        if (collab()) {
            if (dirtyTabs.contains(tab)) {
                status = com.wavesurvivor.i18n.WSLang.t("collab.save_first");
                return false;
            }
            String who = lockedBy.get(t);
            if (who != null) {
                status = com.wavesurvivor.i18n.WSLang.t("collab.locked", who);
                return false;
            }
        }
        tab = t;
        lootScroll = 0;
        snapshotTab();
        sendPresence(true);
        init();
        return true;
    }

    /** Boutons d'onglets : onglet courant inactif, onglets occupés grisés avec le nom du joueur. */
    private void refreshTabButtons() {
        for (int i = 0; i < tabButtons.size(); i++) {
            Button b = tabButtons.get(i);
            String who = collab() ? lockedBy.get(i) : null;
            String base = com.wavesurvivor.i18n.WSLang.t(kingdom() && TAB_NAMES_KINGDOM[i] != null ? TAB_NAMES_KINGDOM[i] : TAB_NAMES[i]);
            b.setMessage(Component.literal(who != null ? "§8🔒 " + base : base));
            b.setTooltip(who != null ? net.minecraft.client.gui.components.Tooltip.create(com.wavesurvivor.i18n.WSLang.c("collab.locked", who)) : null);
            b.active = tab != i && who == null;
        }
    }

    /** Copie de la horde (et de l'autel) à l'entrée dans l'onglet ou après un enregistrement. */
    private void snapshotTab() {
        tabSnapshot = horde.deepCopy();
        altarSnapshot = altar != null ? altar.deepCopy() : null;
        altarDeletedSnapshot = altarDeleted;
    }

    /** « Annuler mes modifs » : l'onglet revient à son état à l'entrée (ou au dernier enregistrement). */
    private void revertTab() {
        if (tabSnapshot == null) return;
        if (tab == T_ALTAR) {
            altar = altarSnapshot != null ? altarSnapshot.deepCopy() : null;
            altarDeleted = altarDeletedSnapshot;
        } else {
            com.wavesurvivor.horde.editor.HordeSections.merge(horde, tabSnapshot, com.wavesurvivor.horde.editor.HordeSections.bit(tab));
        }
        dirtyTabs.remove(tab);
        dirty = !dirtyTabs.isEmpty();
        confirmClose = false;
        status = com.wavesurvivor.i18n.WSLang.t("collab.reverted");
        init();
    }

    private void sendPresence(boolean open) {
        if (oldName.isEmpty()) return; // horde jamais enregistrée : personne d'autre ne peut l'ouvrir
        NetworkHandler.CHANNEL.sendToServer(new com.wavesurvivor.network.HordeCollabPackets.Presence(oldName, tab, open));
    }

    @Override
    public void tick() {
        super.tick();
        // Signe de vie toutes les 30 s (le verrou expire après 5 min sans nouvelles : plantage, perte de connexion)
        if (++heartbeat % 600 == 0) sendPresence(true);
        if (revertBtn != null) revertBtn.active = dirtyTabs.contains(tab);
    }

    /** Verrous reçus du serveur (autres éditeurs de la même horde). */
    public static void onLocks(com.wavesurvivor.network.HordeCollabPackets.Locks pkt) {
        HordeEditorScreen s = ACTIVE;
        if (s == null || !pkt.horde.equalsIgnoreCase(s.oldName.trim())) return;
        boolean wasCollab = s.collab();
        int before = s.editors;
        s.editors = Math.max(1, pkt.editors);
        s.lockedBy.clear();
        for (var l : pkt.locks) s.lockedBy.put(l.tab(), l.player());
        if (s.editors > before && before >= 1 && s.editors > 1) s.status = com.wavesurvivor.i18n.WSLang.t("collab.joined", s.editors);
        boolean current = Minecraft.getInstance().screen == s;
        if (pkt.denied) {
            s.tab = T_PREVIEW;
            s.status = com.wavesurvivor.i18n.WSLang.t("collab.denied");
            s.snapshotTab();
            if (current) s.init();
            return;
        }
        if (wasCollab != s.collab()) { if (current) s.init(); }
        else s.refreshTabButtons();
    }

    /** Un autre joueur a enregistré des onglets : on les reprend (sauf ceux que je modifie). */
    public static void onSectionUpdate(com.wavesurvivor.network.HordeCollabPackets.SectionUpdate pkt) {
        HordeEditorScreen s = ACTIVE;
        if (s == null || !pkt.horde.equalsIgnoreCase(s.oldName.trim())) return;
        JsonObject src = HordeJson.parseObject(pkt.json);
        if (src == null) return;
        int mask = pkt.mask;
        for (int t : s.dirtyTabs) mask &= ~com.wavesurvivor.horde.editor.HordeSections.bit(t);
        com.wavesurvivor.horde.editor.HordeSections.merge(s.horde, src, mask);
        if (s.tabSnapshot != null) com.wavesurvivor.horde.editor.HordeSections.merge(s.tabSnapshot, src, mask);
        if (com.wavesurvivor.horde.editor.HordeSections.has(mask, T_ALTAR)) {
            s.altar = HordeJson.parseObject(pkt.altarJson);
            s.altarDeleted = false;
        }
        s.status = com.wavesurvivor.i18n.WSLang.t("collab.updated", pkt.by);
    }

    @Override
    public void onClose() {
        if (dirty && !confirmClose) {
            confirmClose = true;
            status = com.wavesurvivor.i18n.WSLang.t("ui.modifications_non_enregistrees_fermer_a");
            return;
        }
        NetworkHandler.CHANNEL.sendToServer(new HordeEditorPackets.Request("", HordeEditorPackets.PURPOSE_LIST));
        sendPresence(false);
        if (ACTIVE == this) ACTIVE = null;
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ─── Liste générique (sélection + défilement) ───

    private final class ListBox {
        private final Supplier<List<String>> labelsSupplier;
        private final IntSupplier selected;
        private final IntConsumer onSelect;
        private int x, y, w, rows, scroll;

        ListBox(Supplier<List<String>> labels, IntSupplier selected, IntConsumer onSelect) {
            this.labelsSupplier = labels;
            this.selected = selected;
            this.onSelect = onSelect;
        }

        void place(int x, int y, int w, int rows) { this.x = x; this.y = y; this.w = w; this.rows = rows; }
        int bottom() { return y + rows * 12; }
        boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < bottom(); }

        void scroll(double delta) {
            int max = Math.max(0, labelsSupplier.get().size() - rows);
            scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        }

        boolean click(double mx, double my) {
            if (!contains(mx, my)) return false;
            int i = (int) ((my - y) / 12) + scroll;
            if (i < labelsSupplier.get().size()) onSelect.accept(i);
            return true;
        }

        void render(GuiGraphics g, int mx, int my) {
            List<String> l = labelsSupplier.get();
            scroll = Math.max(0, Math.min(scroll, Math.max(0, l.size() - rows)));
            g.fill(x, y, x + w, bottom(), 0x40000000);
            for (int i = 0; i < rows && i + scroll < l.size(); i++) {
                int idx = i + scroll, ry = y + i * 12;
                if (idx == selected.getAsInt()) g.fill(x, ry, x + w, ry + 12, 0x60F59E0B);
                else if (mx >= x && mx < x + w && my >= ry && my < ry + 12) g.fill(x, ry, x + w, ry + 12, 0x20FFFFFF);
                String s = l.get(idx);
                if (font.width(s) > w - 6) s = font.plainSubstrByWidth(s, w - 10) + "…";
                g.drawString(font, s, x + 3, ry + 2, 0xFFFFFFFF);
            }
            if (l.isEmpty()) g.drawString(font, com.wavesurvivor.i18n.WSLang.t("ui.vide_b333"), x + 3, y + 2, 0xFFFFFFFF);
            if (l.size() > rows) g.drawString(font, "§8" + (scroll + 1) + "–" + Math.min(l.size(), scroll + rows) + "/" + l.size(),
                    x + w - 40, bottom() - 10, 0xFFFFFFFF);
        }
    }
}
