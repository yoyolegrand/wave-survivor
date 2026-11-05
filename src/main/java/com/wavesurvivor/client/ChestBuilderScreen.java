package com.wavesurvivor.client;

import com.google.gson.Gson;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.client.roulette.ChestPreview;
import com.wavesurvivor.client.roulette.RoulettePresets;
import com.wavesurvivor.horde.roulette.CustomChestData;
import com.wavesurvivor.horde.roulette.ParticlePatterns;
import com.wavesurvivor.horde.roulette.RouletteColors;
import com.wavesurvivor.horde.roulette.RouletteKeyItem;
import com.wavesurvivor.horde.roulette.RouletteStyle;
import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.network.DeleteCustomChestPacket;
import com.wavesurvivor.network.NetworkHandler;
import com.wavesurvivor.network.SaveCustomChestPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * ÉDITEUR DE COFFRE ROULETTE (1.9) : aperçu en direct à gauche (« Tourner », « Tester l'ouverture ») et six onglets —
 * Apparence (nom, accent, bois, ferrures, gemmes), Symboles (médaillon par face, couches, bibliothèque, dessin de symbole), Clé (tête, panneton, matière,
 * couleur), Particules (trois moments, types A et B, forme, réglages, préréglages), Butin et Messages (reprennent l'ancien éditeur).
 * Tout est enregistré dans un seul CustomChestData ; les champs que l'éditeur ne touche pas sont conservés.
 */
@OnlyIn(Dist.CLIENT)
public class ChestBuilderScreen extends Screen {

    private static final ChatFormatting[] TIER_COLORS = {ChatFormatting.GRAY, ChatFormatting.GREEN, ChatFormatting.BLUE, ChatFormatting.LIGHT_PURPLE, ChatFormatting.GOLD};
    private static final String[] TIERS = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY"};
    private static final int[] TIER_RGB = {0xE6E6EE, 0x55E070, 0x4F8BFF, 0xB35CFF, 0xFFB52E};

    private static final String[] TAB_IDS = {"look", "symbols", "key", "particles", "loot", "messages"};
    private static final String[] FACES = {"front", "back", "left", "right", "top"};
    private static final String[] MOMENTS = {"idle", "spin", "reveal"};
    private static final String[] SYMBOLS = {"dot", "star", "skull", "cross", "crown", "flame", "drop", "eye", "sun", "moon", "lightning", "heart", "swords", "rune", "gem", "leaf", "hourglass", "key"};
    /** Couleurs proposées (accent, symboles, poussières). */
    private static final int[] PALETTE = {0xC85AF0, 0xFAC846, 0x50DC82, 0x5AD2F0, 0xF05050, 0xFA9628, 0xEBEBF0, 0x22222A, 0xE650C8, 0x5A96F0};
    private static final Gson GSON = new Gson();

    // ─── État ───
    private int tab = 0;
    private String chestName;
    private final CustomChestData draft;
    private final Map<String, String> tierTemplates = CustomChestData.defaultTierMessages();
    private final List<CustomChestData.Reward> rewards = new ArrayList<>();
    private int rewardsScrollOffset = 0;
    private boolean isEditMode = false;
    private String originalName = null;
    private boolean deletePending = false;

    private int face = 0, selLayer = 0, moment = 0;
    private boolean autoRotate = true;
    private float manualYaw = 0;
    private long testStart = -1;
    private final com.wavesurvivor.client.roulette.ChestParticlePreview fxPreview = new com.wavesurvivor.client.roulette.ChestParticlePreview();
    private int testColor = 0xFFB52E;
    private ItemStack testItem = new ItemStack(Items.DIAMOND);

    // ─── Mise en page ───
    private int contentX, contentW, contentY, bottomY;
    private static final int LEFT_W = 172;
    private static final int INV_SLOT_SIZE = 18, REWARD_ROW_HEIGHT = 22;
    private int invX, invY, rewardListX, rewardListY, rewardListW, rewardListVisibleRows;
    private EditBox nameBox;
    private Button deleteBtn;

    /** Pastille de couleur cliquable (dessinée par render, testée par mouseClicked). */
    private record Swatch(int x, int y, int size, int rgb, boolean selected, String tip, Runnable act) {}
    /** Symbole de la bibliothèque cliquable. */
    private record Icon(int x, int y, String id, boolean selected, String tip, Runnable act) {}
    private final List<Swatch> swatches = new ArrayList<>();
    private final List<Icon> icons = new ArrayList<>();

    public ChestBuilderScreen(String initialName) {
        super(Component.literal(WSLang.t("chest.ed.title")));
        this.chestName = initialName != null && !initialName.isBlank() ? initialName : WSLang.t("ui.coffre_custom");
        this.draft = new CustomChestData();
    }

    /** Mode édition : reprend un coffre existant (copie profonde : on n'abîme rien tant qu'on n'enregistre pas). */
    public ChestBuilderScreen(CustomChestData existing) {
        super(Component.literal(WSLang.t("chest.ed.title")));
        this.chestName = existing.name;
        this.originalName = existing.name;
        this.isEditMode = true;
        this.draft = GSON.fromJson(GSON.toJson(existing), CustomChestData.class);
        if (existing.tierMessages != null && !existing.tierMessages.isEmpty()) this.tierTemplates.putAll(existing.tierMessages);
        if (existing.rewards != null) {
            for (CustomChestData.Reward r : existing.rewards) {
                CustomChestData.Reward copy = new CustomChestData.Reward();
                copy.item = r.item;
                copy.chance = r.chance;
                copy.minQty = r.minQty;
                copy.maxQty = r.maxQty;
                copy.tier = r.tier;
                copy.customMessage = r.customMessage;
                this.rewards.add(copy);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Construction
    // ═══════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        swatches.clear();
        icons.clear();
        contentX = LEFT_W + 16;
        contentW = width - contentX - 8;
        contentY = 50;
        bottomY = height - 28;

        // Aperçu : boutons sous le coffre
        int lw = (LEFT_W - 6) / 2;
        addRenderableWidget(Button.builder(Component.literal("↺ " + WSLang.t("chest.ed.rotate")), b -> autoRotate = !autoRotate).bounds(8, bottomY - 2, lw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("▶ " + WSLang.t("chest.ed.test")), b -> startTest()).bounds(8 + lw + 2, bottomY - 2, lw, 20).build());

        // Aperçu des particules (onglet Particules) : joue l'effet du moment choisi sur le coffre de gauche
        if (tab == 3) {
            addRenderableWidget(Button.builder(Component.literal("✦ " + WSLang.t("chest.ed.pm.preview")), b -> {
                CustomChestData.Effect e = fx(false);
                if (e == null || e.a == null || e.a.isBlank() || "none".equalsIgnoreCase(e.a)) return;
                int ticks = moment == 2 ? 20 : moment == 1 ? 80 : 100;
                int acc = accent();
                fxPreview.play(e, acc, ticks);
            }).bounds(8, bottomY - 24, LEFT_W, 20).build());
        }

        // Onglets
        int tw = Math.min(110, (contentW - 5 * 3) / TAB_IDS.length);
        for (int i = 0; i < TAB_IDS.length; i++) {
            final int t = i;
            addRenderableWidget(Button.builder(Component.literal((i == tab ? "§e§l" : "§7") + WSLang.t("chest.ed.tab." + TAB_IDS[i])), b -> { tab = t; rebuildWidgets(); })
                    .bounds(contentX + i * (tw + 3), 24, tw, 20).build());
        }

        // Pied de page
        int bx = contentX + contentW;
        addRenderableWidget(Button.builder(Component.literal("§a" + WSLang.t("ui.sauvegarder")), b -> save()).bounds(bx - 130, bottomY - 2, 130, 20).build());
        addRenderableWidget(Button.builder(Component.literal("§7" + WSLang.t("chest.ed.cancel")), b -> onClose()).bounds(bx - 236, bottomY - 2, 100, 20).build());
        if (isEditMode) {
            deleteBtn = Button.builder(Component.literal(WSLang.t("ui.supprimer")).withStyle(ChatFormatting.RED), b -> onDeleteClicked()).bounds(contentX, bottomY - 2, 110, 20).build();
            addRenderableWidget(deleteBtn);
        }

        switch (tab) {
            case 0 -> buildLook();
            case 1 -> buildSymbols();
            case 2 -> buildKey();
            case 3 -> buildParticles();
            case 4 -> buildLoot();
            default -> buildMessages();
        }
    }

    // ─── Aides de construction ───

    private void swatch(int x, int y, int rgb, boolean sel, String tip, Runnable act) { swatches.add(new Swatch(x, y, 14, rgb, sel, tip, act)); }

    private Button btn(int x, int y, int w, String label, Runnable r) {
        return addRenderableWidget(Button.builder(Component.literal(label), b -> r.run()).bounds(x, y, w, 20).build());
    }

    private EditBox hexBox(int x, int y, String current, Consumer<String> set) {
        EditBox e = new EditBox(font, x, y, 64, 16, Component.literal("#"));
        e.setMaxLength(7);
        e.setValue(current == null ? "" : current);
        e.setResponder(s -> { if (RouletteColors.isHex(s)) set.accept(s.startsWith("#") ? s : "#" + s); });
        return addRenderableWidget(e);
    }

    /** Bouton « Auto », pastilles de la palette et case « #RRGGBB » : {@code current} est la valeur actuelle (hex ou vide), {@code set} la modifie. */
    private void colorRow(int x, int y, String current, Consumer<String> set) {
        boolean auto = current == null || current.isBlank() || current.equalsIgnoreCase("accent") || current.equalsIgnoreCase("auto");
        btn(x, y - 3, 46, (auto ? "§a✔ " : "§7") + WSLang.t("chest.ed.auto"), () -> { set.accept(null); rebuildWidgets(); });
        int cx = x + 50;
        for (int rgb : PALETTE) {
            String hex = String.format("#%06X", rgb);
            swatch(cx, y, rgb, hex.equalsIgnoreCase(current), hex, () -> { set.accept(hex); rebuildWidgets(); });
            cx += 17;
        }
        hexBox(cx + 4, y - 1, current != null && RouletteColors.isHex(current) ? current : "", set);
    }

    private void label(GuiGraphics g, int x, int y, String text) { g.drawString(font, text, x, y, 0xFFFFFFFF); }

    private void ensureFx() {
        if (draft.particles == null) draft.particles = CustomChestData.Particles.legacy("none");
        if (draft.particles.fx == null) draft.particles.fx = new LinkedHashMap<>();
    }

    private CustomChestData.Effect fx(boolean create) {
        ensureFx();
        CustomChestData.Effect e = draft.particles.fx.get(MOMENTS[moment]);
        if (e == null && create) { e = new CustomChestData.Effect(); e.a = "none"; draft.particles.fx.put(MOMENTS[moment], e); }
        return e;
    }

    private Map<String, List<CustomChestData.Medallion>> medallions() {
        if (draft.medallions == null) {
            draft.medallions = new LinkedHashMap<>();
            draft.medallions.put("front", new ArrayList<>(List.of(new CustomChestData.Medallion("dot", "large", "accent"))));
        }
        return draft.medallions;
    }

    private List<CustomChestData.Medallion> layers() { return medallions().get(FACES[face]); }

    private int autoAccent() {
        net.minecraft.world.item.DyeColor dye = net.minecraft.world.item.DyeColor.byName(draft.color == null ? "auto" : draft.color, null);
        return dye != null ? com.wavesurvivor.altar.AltarColors.rgb(dye) : RouletteKeyItem.colorForName(chestName == null ? "" : chestName);
    }

    private int accent() { return RouletteColors.parseHex(draft.accentColor, autoAccent()); }

    private RouletteStyle style() { return RouletteStyle.resolve(draft, accent()); }

    // ─── Onglet Apparence ───

    private void buildLook() {
        int x = contentX + 10, y = contentY + 6;
        nameBox = new EditBox(font, x + 90, y, 220, 18, Component.literal(WSLang.t("ui.nom_du_coffre")));
        nameBox.setMaxLength(60);
        nameBox.setValue(chestName);
        nameBox.setResponder(v -> chestName = v);
        addRenderableWidget(nameBox);
        y += 34;
        colorRow(x + 90, y, draft.accentColor, v -> draft.accentColor = v);
        y += 34;
        // Bois : préréglages (pastilles à la couleur du bois) + couleur libre
        int cx = x + 90;
        for (Map.Entry<String, Integer> e : RouletteColors.WOOD.entrySet()) {
            final String id = e.getKey();
            swatch(cx, y, e.getValue(), id.equals(draft.woodColor) || (draft.woodColor == null && id.equals("dark_oak")), WSLang.t("chest.ed.wood." + id), () -> { draft.woodColor = id; rebuildWidgets(); });
            cx += 17;
        }
        hexBox(cx + 4, y - 1, draft.woodColor != null && RouletteColors.isHex(draft.woodColor) ? draft.woodColor : "", v -> draft.woodColor = v);
        y += 34;
        cx = x + 90;
        for (Map.Entry<String, Integer> e : RouletteColors.IRON.entrySet()) {
            final String id = e.getKey();
            swatch(cx, y, e.getValue(), id.equals(draft.ironColor) || (draft.ironColor == null && id.equals("iron")), WSLang.t("chest.ed.iron." + id), () -> { draft.ironColor = id; rebuildWidgets(); });
            cx += 17;
        }
        hexBox(cx + 4, y - 1, draft.ironColor != null && RouletteColors.isHex(draft.ironColor) ? draft.ironColor : "", v -> draft.ironColor = v);
        y += 34;
        colorRow(x + 90, y, draft.gemColor, v -> draft.gemColor = v);
    }

    // ─── Onglet Symboles ───

    private void buildSymbols() {
        int x = contentX + 10, y = contentY + 4;
        for (int i = 0; i < FACES.length; i++) {
            final int f = i;
            btn(x + i * 62, y + 12, 60, (i == face ? "§e§l" : "§7") + WSLang.t("chest.ed.face." + FACES[i]), () -> { face = f; selLayer = 0; rebuildWidgets(); });
        }
        boolean has = layers() != null && !layers().isEmpty();
        btn(x + 5 * 62 + 8, y + 12, 140, (has ? "§a☑ " : "§7☐ ") + WSLang.t("chest.ed.medallion_on"), () -> {
            if (has) medallions().remove(FACES[face]);
            else medallions().put(FACES[face], new ArrayList<>(List.of(new CustomChestData.Medallion("dot", "large", "accent"))));
            selLayer = 0;
            rebuildWidgets();
        });
        y += 48;
        if (!has) return;
        List<CustomChestData.Medallion> ls = layers();
        selLayer = Math.max(0, Math.min(selLayer, ls.size() - 1));
        int rowW = Math.min(300, contentW - 20);
        for (int i = 0; i < ls.size(); i++) {
            final int idx = i;
            CustomChestData.Medallion m = ls.get(i);
            int ry = y + i * 24;
            btn(x + rowW - 100, ry, 44, "small".equalsIgnoreCase(m.size) ? WSLang.t("chest.ed.size_small") : WSLang.t("chest.ed.size_large"),
                    () -> { m.size = "small".equalsIgnoreCase(m.size) ? "large" : "small"; selLayer = idx; rebuildWidgets(); });
            btn(x + rowW - 54, ry, 16, "▲", () -> { if (idx > 0) { ls.add(idx - 1, ls.remove(idx)); selLayer = idx - 1; rebuildWidgets(); } });
            btn(x + rowW - 36, ry, 16, "▼", () -> { if (idx < ls.size() - 1) { ls.add(idx + 1, ls.remove(idx)); selLayer = idx + 1; rebuildWidgets(); } });
            btn(x + rowW - 18, ry, 16, "§c✕", () -> { if (ls.size() > 1) { ls.remove(idx); selLayer = Math.max(0, idx - 1); rebuildWidgets(); } });
        }
        int ay = y + ls.size() * 24 + 2;
        btn(x, ay, 140, "+ " + WSLang.t("chest.ed.add_layer"), () -> {
            if (ls.size() < 4) { ls.add(new CustomChestData.Medallion("dot", "small", "accent")); selLayer = ls.size() - 1; rebuildWidgets(); }
        });
        btn(x + 144, ay, 150, "✎ " + WSLang.t("chest.ed.draw"), () -> Minecraft.getInstance().setScreen(new SymbolDrawScreen(this, "", null, (name, rows) -> {
            if (draft.customSymbols == null) draft.customSymbols = new LinkedHashMap<>();
            draft.customSymbols.put(name, rows);
            if (!ls.isEmpty()) ls.get(selLayer).symbol = "custom:" + name;
        })));
        // Couche sélectionnée : couleur
        int px = x + rowW + 14;
        CustomChestData.Medallion sel = ls.get(selLayer);
        int cy = y + 18;
        for (int i = 0; i < PALETTE.length; i++) {
            String hex = String.format("#%06X", PALETTE[i]);
            swatch(px + (i % 5) * 17, cy + (i / 5) * 17, PALETTE[i], hex.equalsIgnoreCase(sel.color), hex, () -> { sel.color = hex; rebuildWidgets(); });
        }
        swatch(px + 5 * 17 + 4, cy, accent(), "accent".equalsIgnoreCase(sel.color), WSLang.t("chest.ed.accent"), () -> { sel.color = "accent"; rebuildWidgets(); });
        hexBox(px, cy + 40, RouletteColors.isHex(sel.color) ? sel.color : "", v -> sel.color = v);
        // Bibliothèque
        int ly = ay + 34;
        int per = Math.max(6, Math.min(9, (contentW - 20) / 34));
        List<String> all = new ArrayList<>(List.of(SYMBOLS));
        if (draft.customSymbols != null) for (String n : draft.customSymbols.keySet()) all.add("custom:" + n);
        for (int i = 0; i < all.size(); i++) {
            final String id = all.get(i);
            icons.add(new Icon(x + (i % per) * 34, ly + (i / per) * 34, id, id.equals(sel.symbol), id.startsWith("custom:") ? id.substring(7) : WSLang.t("chest.ed.sym." + id), () -> { sel.symbol = id; rebuildWidgets(); }));
        }
    }

    // ─── Onglet Clé ───

    private void buildKey() {
        int x = contentX + 10, y = contentY + 10;
        List<String> heads = RouletteKeyItem.HEADS, bits = RouletteKeyItem.BITS;
        String head = draft.keyHead == null ? "ring" : draft.keyHead, bit = draft.keyBit == null ? "simple" : draft.keyBit;
        btn(x + 100, y, 170, WSLang.t("chest.ed.key.head_" + head) + "  ↻", () -> { draft.keyHead = heads.get((heads.indexOf(head) + (hasShiftDown() ? heads.size() - 1 : 1)) % heads.size()); rebuildWidgets(); });
        btn(x + 100, y + 28, 170, WSLang.t("chest.ed.key.bit_" + bit) + "  ↻", () -> { draft.keyBit = bits.get((bits.indexOf(bit) + (hasShiftDown() ? bits.size() - 1 : 1)) % bits.size()); rebuildWidgets(); });
        var mats = RouletteKeyItem.MATERIALS;
        String curMat = draft.keyMaterial == null ? "or" : draft.keyMaterial;
        var m = RouletteKeyItem.material(curMat);
        btn(x + 100, y + 56, 170, "■ " + m.nameFr() + "  ↻", () -> {
            int idx = 0;
            for (int i = 0; i < mats.length; i++) if (mats[i].id().equals(curMat)) idx = i;
            draft.keyMaterial = mats[(idx + (hasShiftDown() ? mats.length - 1 : 1)) % mats.length].id();
            rebuildWidgets();
        });
        colorRow(x + 100, y + 94, draft.keyColor, v -> draft.keyColor = v);
    }

    // ─── Onglet Particules ───

    private void buildParticles() {
        int x = contentX + 10, y = contentY + 4;
        for (int i = 0; i < MOMENTS.length; i++) {
            final int mo = i;
            btn(x + i * 150, y, 146, (i == moment ? "§e§l" : "§7") + WSLang.t("chest.ed.pm." + MOMENTS[i]), () -> { moment = mo; rebuildWidgets(); });
        }
        y += 30;
        CustomChestData.Effect e = fx(false);
        boolean valid = e != null && e.a != null && !e.a.isBlank() && !"none".equalsIgnoreCase(e.a);
        btn(x + 110, y, 170, label(valid ? e.a : null) + "  ↻", () -> Minecraft.getInstance().setScreen(new ParticlePickerScreen(this, valid ? e.a : "", v -> {
            if (v.startsWith("block:")) return;
            CustomChestData.Effect ef = fx(true);
            ef.a = v.isEmpty() ? "none" : v.replace("minecraft:", "");
        })));
        btn(x + 284, y, 62, "§7dust", () -> fx(true).a = "dust");
        btn(x + 350, y, 62, "§7dust ⇄", () -> fx(true).a = "dust_color_transition");
        y += 28;
        boolean hasB = e != null && e.b != null && !e.b.isBlank();
        btn(x + 110, y, 170, label(hasB ? e.b : null) + "  ↻", () -> Minecraft.getInstance().setScreen(new ParticlePickerScreen(this, hasB ? e.b : "", v -> {
            if (v.startsWith("block:") || v.isEmpty()) return;
            fx(true).b = v.replace("minecraft:", "");
        })));
        btn(x + 284, y, 70, "✖ " + WSLang.t("chest.ed.remove"), () -> { CustomChestData.Effect ef = fx(false); if (ef != null) ef.b = null; rebuildWidgets(); });
        y += 28;
        String pat = e != null && e.pattern != null ? e.pattern : "circle";
        btn(x + 110, y, 170, pat + "  ↻", () -> {
            String[] all = ParticlePatterns.ALL;
            int idx = 0;
            for (int i = 0; i < all.length; i++) if (all[i].equals(pat)) idx = i;
            fx(true).pattern = all[(idx + (hasShiftDown() ? all.length - 1 : 1)) % all.length];
            rebuildWidgets();
        });
        y += 30;
        CustomChestData.Effect cur = e != null ? e : new CustomChestData.Effect();
        slider(x + 110, y, "density", 1, 20, cur.density, true, v -> fx(true).density = (int) Math.round(v));
        slider(x + 110, y + 24, "radius", 0.2, 4, cur.radius, false, v -> fx(true).radius = Math.round(v * 10) / 10.0);
        slider(x + 110, y + 48, "height", 0.2, 4, cur.height, false, v -> fx(true).height = Math.round(v * 10) / 10.0);
        slider(x + 110, y + 72, "speed", 1, 10, cur.speed, true, v -> fx(true).speed = (int) Math.round(v));
        slider(x + 110, y + 96, "size", 0.5, 3, cur.size, false, v -> fx(true).size = Math.round(v * 10) / 10.0);
        int cy = y + 124;
        boolean dust = e != null && e.a != null && e.a.startsWith("dust");
        if (dust) {
            for (int i = 0; i < PALETTE.length; i++) {
                String hex = String.format("#%06X", PALETTE[i]);
                swatch(x + 110 + i * 17, cy, PALETTE[i], hex.equalsIgnoreCase(e.color), hex, () -> { fx(true).color = hex; rebuildWidgets(); });
                if ("dust_color_transition".equals(e.a)) swatch(x + 110 + i * 17, cy + 17, PALETTE[i], hex.equalsIgnoreCase(e.color2), hex, () -> { fx(true).color2 = hex; rebuildWidgets(); });
            }
        }
        // Préréglages
        int py = cy + 40;
        int px = x + 110;
        for (String name : RoulettePresets.names()) {
            final String n = name;
            int w = font.width(n) + 14;
            btn(px, py, w, n, () -> {
                CustomChestData.Effect p = RoulettePresets.effect(n, MOMENTS[moment]);
                ensureFx();
                if (p != null) draft.particles.fx.put(MOMENTS[moment], p);
                rebuildWidgets();
            });
            px += w + 3;
        }
    }

    private static String label(String type) { return type == null || type.isBlank() || type.equals("none") ? "§8—" : type; }

    private void slider(int x, int y, String key, double min, double max, double value, boolean integer, DoubleConsumer set) {
        addRenderableWidget(new AbstractSliderButton(x, y, 200, 18, Component.empty(), (Math.max(min, Math.min(max, value)) - min) / (max - min)) {
            { updateMessage(); }
            private double v() { double d = min + value * (max - min); return integer ? Math.round(d) : Math.round(d * 10) / 10.0; }
            @Override protected void updateMessage() { setMessage(Component.literal(WSLang.t("chest.ed.pm." + key) + " : " + (integer ? String.valueOf((int) v()) : String.valueOf(v())))); }
            @Override protected void applyValue() { set.accept(min + value * (max - min)); }
        });
    }

    // ─── Onglets Butin et Messages (ancien éditeur) ───

    private void buildLoot() {
        int invGridH = 4 * INV_SLOT_SIZE + 4;
        invX = contentX + (contentW - 9 * INV_SLOT_SIZE) / 2;
        invY = bottomY - invGridH - 8;
        rewardListX = contentX;
        rewardListY = contentY + 18;
        rewardListW = contentW;
        rewardListVisibleRows = Math.max(1, (invY - rewardListY - 20) / REWARD_ROW_HEIGHT);
        int y = rewardListY;
        int endIdx = Math.min(rewards.size(), rewardsScrollOffset + rewardListVisibleRows);
        for (int i = rewardsScrollOffset; i < endIdx; i++) {
            final int idx = i;
            addRenderableWidget(Button.builder(Component.literal("✕").withStyle(ChatFormatting.RED), b -> removeReward(idx))
                    .bounds(rewardListX + rewardListW - 22, y + 2, 18, 18).build());
            y += REWARD_ROW_HEIGHT;
        }
    }

    private void buildMessages() {
        btn(contentX + 10, contentY + 30, 220, WSLang.t("ui.templates_par_tier_5046"), this::openTemplatesEditor);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Rendu
    // ═══════════════════════════════════════════════════════════════

    private float time() { return System.currentTimeMillis() / 50f; }

    private float testTicks() { return testStart < 0 ? -1 : (System.currentTimeMillis() - testStart) / 50f; }

    private void startTest() {
        testStart = System.currentTimeMillis();
        int r = (int) (Math.random() * 5);
        testColor = TIER_RGB[r];
        testItem = new ItemStack(r == 4 ? Items.NETHERITE_INGOT : r == 3 ? Items.DIAMOND : r == 2 ? Items.EMERALD : r == 1 ? Items.GOLD_INGOT : Items.IRON_INGOT);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, "§6§l" + WSLang.t("chest.ed.title") + " §r§f— « " + chestName + " »", width / 2, 8, 0xFFFFFFFF);
        // Aperçu en direct
        g.fill(8, 24, 8 + LEFT_W, bottomY - 6, 0xC0181622);
        g.drawCenteredString(font, "§e" + WSLang.t("chest.ed.preview"), 8 + LEFT_W / 2, 30, 0xFFFFFFFF);
        float yaw = autoRotate ? (System.currentTimeMillis() % 12000) / 12000f * 360f : manualYaw;
        float tt = testTicks();
        if (tt > ChestPreview.TEST_GIVE + 120) testStart = -1;
        ChestPreview.draw(g, style(), 8 + LEFT_W / 2, (24 + bottomY) / 2 + 20, LEFT_W * 0.78f, yaw, time(), tt, testColor, testItem);
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        fxPreview.draw(g, 8 + LEFT_W / 2, (24 + bottomY) / 2 + 20, LEFT_W * 0.78f, yaw);
        g.pose().popPose();
        // Panneau de l'onglet
        g.fill(contentX - 4, contentY - 4, contentX + contentW + 4, bottomY - 6, 0x80141220);
        for (Swatch s : swatches) {
            g.fill(s.x - 1, s.y - 1, s.x + s.size + 1, s.y + s.size + 1, s.selected ? 0xFFFFFFFF : 0xFF000000);
            if (s.size > 0 && s.rgb != 0) g.fill(s.x, s.y, s.x + s.size, s.y + s.size, 0xFF000000 | s.rgb);
        }
        switch (tab) {
            case 0 -> renderLookLabels(g);
            case 1 -> renderSymbols(g, mx, my);
            case 2 -> renderKey(g);
            case 3 -> renderParticleLabels(g);
            case 4 -> renderLoot(g, mx, my);
            default -> label(g, contentX + 10, contentY + 8, "§7" + WSLang.t("chest.ed.messages_hint"));
        }
        super.render(g, mx, my, pt);
        for (Swatch s : swatches) {
            if (mx >= s.x && mx < s.x + s.size && my >= s.y && my < s.y + s.size && !s.tip.isEmpty()) g.renderTooltip(font, Component.literal(s.tip), mx, my);
        }
        for (Icon ic : icons) {
            if (mx >= ic.x && mx < ic.x + 32 && my >= ic.y && my < ic.y + 32) g.renderTooltip(font, Component.literal(ic.tip), mx, my);
        }
        if (tab == 4) {
            ItemStack hovered = getInventoryStackAt(mx, my);
            if (hovered != null && !hovered.isEmpty()) g.renderTooltip(font, hovered, mx, my);
        }
    }

    private void renderLookLabels(GuiGraphics g) {
        int x = contentX + 10, y = contentY + 6;
        label(g, x, y + 5, WSLang.t("chest.ed.name"));
        label(g, x, y + 39, WSLang.t("chest.ed.accent"));
        label(g, x, y + 73, WSLang.t("chest.ed.wood"));
        label(g, x, y + 107, WSLang.t("chest.ed.iron"));
        label(g, x, y + 141, WSLang.t("chest.ed.gem"));
        label(g, x, y + 170, "§8" + WSLang.t("chest.ed.look_hint"));
    }

    private void renderSymbols(GuiGraphics g, int mx, int my) {
        int x = contentX + 10, y = contentY + 4;
        label(g, x, y, "§e" + WSLang.t("chest.ed.face"));
        List<CustomChestData.Medallion> ls = layers();
        if (ls == null || ls.isEmpty()) { label(g, x, y + 56, "§7" + WSLang.t("chest.ed.no_medallion")); return; }
        int rowW = Math.min(300, contentW - 20);
        label(g, x, y + 36, "§e" + WSLang.t("chest.ed.layers"));
        label(g, x + rowW + 14, y + 36, "§e" + WSLang.t("chest.ed.layer_selected", selLayer + 1));
        int ry0 = y + 48;
        for (int i = 0; i < ls.size(); i++) {
            CustomChestData.Medallion m = ls.get(i);
            int ry = ry0 + i * 24;
            g.fill(x, ry, x + rowW - 104, ry + 20, i == selLayer ? 0x80B060F0 : 0x60000000);
            int rgb = "accent".equalsIgnoreCase(m.color) || m.color == null ? accent() : RouletteColors.parseHex(m.color, accent());
            if (!m.symbol.startsWith("custom:")) ChestPreview.drawSymbol(g, m.symbol, "small".equalsIgnoreCase(m.size), rgb, x + 2, ry + 2, 16);
            label(g, x + 22, ry + 6, (i + 1) + ". " + (m.symbol.startsWith("custom:") ? m.symbol.substring(7) : WSLang.t("chest.ed.sym." + m.symbol)));
            g.fill(x + rowW - 118, ry + 3, x + rowW - 106, ry + 15, 0xFF000000 | rgb);
        }
        label(g, x + 1, ry0 + ls.size() * 24 + 24, "§e" + WSLang.t("chest.ed.library"));
        for (Icon ic : icons) {
            boolean hover = mx >= ic.x && mx < ic.x + 32 && my >= ic.y && my < ic.y + 32;
            g.fill(ic.x, ic.y, ic.x + 32, ic.y + 32, ic.selected ? 0xFFB060F0 : hover ? 0xFF555566 : 0xFF2A2838);
            g.fill(ic.x + 1, ic.y + 1, ic.x + 31, ic.y + 31, 0xFF1C1B28);
            if (!ic.id.startsWith("custom:")) ChestPreview.drawSymbol(g, ic.id, false, 0xEDE6FF, ic.x + 4, ic.y + 4, 24);
            else {
                List<String> rows = draft.customSymbols == null ? null : draft.customSymbols.get(ic.id.substring(7));
                if (rows != null) for (int yy = 0; yy < 10 && yy < rows.size(); yy++) for (int xx = 0; xx < 10 && xx < rows.get(yy).length(); xx++)
                    if (rows.get(yy).charAt(xx) == '#') g.fill(ic.x + 6 + xx * 2, ic.y + 6 + yy * 2, ic.x + 8 + xx * 2, ic.y + 8 + yy * 2, 0xFFEDE6FF);
            }
        }
        label(g, x, bottomY - 22, "§8" + WSLang.t("chest.ed.ring_hint"));
    }

    private void renderKey(GuiGraphics g) {
        int x = contentX + 10, y = contentY + 10;
        label(g, x, y + 6, WSLang.t("chest.ed.key.head"));
        label(g, x, y + 34, WSLang.t("chest.ed.key.bit"));
        label(g, x, y + 62, WSLang.t("chest.ed.key.material"));
        label(g, x, y + 94, WSLang.t("chest.ed.key.color"));
        RouletteStyle st = style();
        var mat = RouletteKeyItem.material(draft.keyMaterial);
        int body = RouletteColors.isHex(draft.keyColor) ? RouletteColors.parseHex(draft.keyColor, mat.color()) : mat.color();
        ChestPreview.drawKey(g, draft.keyHead == null ? "ring" : draft.keyHead, draft.keyBit == null ? "simple" : draft.keyBit, body, st.gem, x + 300, y + 4, 96);
        label(g, x + 300, y + 106, "§8" + WSLang.t("chest.ed.key.hint"));
    }

    private void renderParticleLabels(GuiGraphics g) {
        int x = contentX + 10, y = contentY + 4;
        label(g, x, y + 36, WSLang.t("chest.ed.pm.type_a"));
        label(g, x, y + 64, WSLang.t("chest.ed.pm.type_b"));
        label(g, x, y + 92, WSLang.t("chest.ed.pm.pattern"));
        CustomChestData.Effect e = fx(false);
        if (e != null && e.a != null && e.a.startsWith("dust")) label(g, x, y + 30 + 30 + 28 + 28 + 124 + 2, WSLang.t("chest.ed.pm.dust_color"));
        label(g, x, y + 30 + 30 + 28 + 28 + 124 + 44, WSLang.t("chest.ed.pm.presets"));
        boolean legacy = (draft.particles == null || !draft.particles.hasEffects()) && draft.particles != null && draft.particles.legacy != null && !"none".equals(draft.particles.legacy);
        if (legacy) label(g, x, y + 30 + 30 + 28 + 28 + 124 + 74, "§8" + WSLang.t("chest.ed.pm.legacy", draft.particles.legacy));
        // Bonus de révélation selon la rareté (ajouté automatiquement)
        int bx = contentX + contentW - 190;
        if (contentW > 640) {
            g.fill(bx, y + 30, bx + 180, y + 190, 0x80000000);
            label(g, bx + 8, y + 36, "§e" + WSLang.t("chest.ed.reveal_bonus"));
            for (int i = 0; i < 5; i++) {
                g.drawString(font, "§" + new String[]{"7", "a", "9", "5", "6"}[i] + WSLang.t("chest.ed.tier." + TIERS[i]), bx + 8, y + 54 + i * 26, 0xFFFFFFFF);
                g.drawString(font, "§8" + WSLang.t("chest.ed.bonus." + TIERS[i]), bx + 8, y + 64 + i * 26, 0xFFFFFFFF);
            }
        }
    }

    // ─── Butin (reprise de l'ancien éditeur) ───

    private void renderLoot(GuiGraphics g, int mx, int my) {
        double total = totalChance();
        ChatFormatting totalColor = total > 100 ? ChatFormatting.RED : total >= 100 ? ChatFormatting.GREEN : ChatFormatting.WHITE;
        g.drawString(font, Component.literal(WSLang.t("ui.rewards") + rewards.size() + WSLang.t("ui.clique_une_ligne_pour_editer")).withStyle(ChatFormatting.GOLD), rewardListX, rewardListY - 14, 0xFFFFFFFF);
        g.drawString(font, Component.literal(WSLang.t("ui.total") + fmt(total) + "% / 100%").withStyle(totalColor, ChatFormatting.BOLD), rewardListX + rewardListW - 130, rewardListY - 14, 0xFFFFFFFF);
        renderRewardList(g, mx, my);
        g.drawString(font, Component.literal(WSLang.t("ui.ton_inventaire_clique_un_item_pour_l_ajo")).withStyle(ChatFormatting.GRAY), invX, invY - 12, 0xFFAAAAAA);
        renderPlayerInventory(g, mx, my);
    }

    private void renderRewardList(GuiGraphics g, int mouseX, int mouseY) {
        int y = rewardListY;
        int endIdx = Math.min(rewards.size(), rewardsScrollOffset + rewardListVisibleRows);
        if (rewards.isEmpty()) {
            g.drawString(font, Component.literal(WSLang.t("ui.aucun_reward_clique_un_item_de_ton_inven")).withStyle(ChatFormatting.DARK_GRAY), rewardListX + 10, y, 0xFF888888);
            return;
        }
        for (int i = rewardsScrollOffset; i < endIdx; i++) {
            CustomChestData.Reward r = rewards.get(i);
            boolean rowHovered = mouseX >= rewardListX && mouseX < rewardListX + rewardListW - 24 && mouseY >= y && mouseY < y + REWARD_ROW_HEIGHT - 2;
            g.fill(rewardListX, y, rewardListX + rewardListW, y + REWARD_ROW_HEIGHT - 2, rowHovered ? 0x80404060 : 0x60000000);
            ItemStack stack = itemStackOf(r.item);
            if (!stack.isEmpty()) g.renderItem(stack, rewardListX + 3, y + 2);
            String displayName = !stack.isEmpty() ? stack.getHoverName().getString() : r.item;
            g.drawString(font, Component.literal(displayName).withStyle(tierColorOf(r.tier)), rewardListX + 24, y + 3, 0xFFFFFFFF);
            String subInfo = tierLabelOf(r.tier);
            if (r.customMessage != null && !r.customMessage.isBlank()) subInfo += WSLang.t("ui.msg_custom");
            g.drawString(font, Component.literal(subInfo).withStyle(ChatFormatting.DARK_GRAY), rewardListX + 24, y + 12, 0xFF888888);
            String qty = "x" + r.minQty + (r.maxQty > r.minQty ? "-" + r.maxQty : "");
            g.drawString(font, Component.literal(qty).withStyle(ChatFormatting.GRAY), rewardListX + rewardListW - 130, y + 7, 0xFFAAAAAA);
            g.drawString(font, Component.literal(fmt(r.chance) + "%").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), rewardListX + rewardListW - 80, y + 7, 0xFFFFFF55);
            y += REWARD_ROW_HEIGHT;
        }
    }

    private void renderPlayerInventory(GuiGraphics g, int mouseX, int mouseY) {
        if (Minecraft.getInstance().player == null) return;
        var inv = Minecraft.getInstance().player.getInventory();
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            renderSlot(g, invX + col * INV_SLOT_SIZE, invY + row * INV_SLOT_SIZE, inv.getItem(9 + row * 9 + col), mouseX, mouseY);
        int hotbarY = invY + 3 * INV_SLOT_SIZE + 4;
        for (int col = 0; col < 9; col++) renderSlot(g, invX + col * INV_SLOT_SIZE, hotbarY, inv.getItem(col), mouseX, mouseY);
    }

    private void renderSlot(GuiGraphics g, int sx, int sy, ItemStack stack, int mouseX, int mouseY) {
        boolean hovered = mouseX >= sx && mouseX < sx + INV_SLOT_SIZE && mouseY >= sy && mouseY < sy + INV_SLOT_SIZE;
        g.fill(sx, sy, sx + INV_SLOT_SIZE, sy + INV_SLOT_SIZE, hovered ? 0xFF555555 : 0xFF373737);
        g.fill(sx + 1, sy + 1, sx + INV_SLOT_SIZE - 1, sy + INV_SLOT_SIZE - 1, hovered ? 0xFF888888 : 0xFF555555);
        if (!stack.isEmpty()) {
            g.renderItem(stack, sx + 1, sy + 1);
            g.renderItemDecorations(font, stack, sx + 1, sy + 1);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Souris
    // ═══════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        for (Swatch s : swatches) {
            if (s.size > 0 && mouseX >= s.x && mouseX < s.x + s.size && mouseY >= s.y && mouseY < s.y + s.size) { s.act.run(); return true; }
        }
        for (Icon ic : new ArrayList<>(icons)) {
            if (mouseX >= ic.x && mouseX < ic.x + 32 && mouseY >= ic.y && mouseY < ic.y + 32) { ic.act.run(); return true; }
        }
        if (tab == 1) { // clic sur une ligne de couche : la sélectionner
            List<CustomChestData.Medallion> ls = layers();
            int rowW = Math.min(300, contentW - 20);
            if (ls != null) for (int i = 0; i < ls.size(); i++) {
                int ry = contentY + 4 + 48 + i * 24, x = contentX + 10;
                if (mouseX >= x && mouseX < x + rowW - 104 && mouseY >= ry && mouseY < ry + 20) { selLayer = i; rebuildWidgets(); return true; }
            }
        }
        if (tab == 4) {
            int rewardIdx = getRewardIdxAt((int) mouseX, (int) mouseY);
            if (rewardIdx >= 0) { openConfigPromptForEdit(rewardIdx); return true; }
            ItemStack clicked = getInventoryStackAt((int) mouseX, (int) mouseY);
            if (clicked != null && !clicked.isEmpty()) { openConfigPromptForAdd(clicked); return true; }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (mx < 8 + LEFT_W && my > 24 && my < bottomY - 6) { // glisser sur l'aperçu : tourner à la main
            autoRotate = false;
            manualYaw += (float) dx * 1.5f;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (tab == 4 && rewards.size() > rewardListVisibleRows) {
            int max = rewards.size() - rewardListVisibleRows;
            int newOffset = Math.max(0, Math.min(max, rewardsScrollOffset - (int) Math.signum(delta)));
            if (newOffset != rewardsScrollOffset) { rewardsScrollOffset = newOffset; rebuildWidgets(); }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private int getRewardIdxAt(int mouseX, int mouseY) {
        if (mouseX < rewardListX || mouseX > rewardListX + rewardListW - 26) return -1;
        int endIdx = Math.min(rewards.size(), rewardsScrollOffset + rewardListVisibleRows);
        for (int i = rewardsScrollOffset; i < endIdx; i++) {
            int y = rewardListY + (i - rewardsScrollOffset) * REWARD_ROW_HEIGHT;
            if (mouseY >= y && mouseY < y + REWARD_ROW_HEIGHT - 2) return i;
        }
        return -1;
    }

    private ItemStack getInventoryStackAt(int mouseX, int mouseY) {
        if (Minecraft.getInstance().player == null) return null;
        var inv = Minecraft.getInstance().player.getInventory();
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) {
            int sx = invX + col * INV_SLOT_SIZE, sy = invY + row * INV_SLOT_SIZE;
            if (mouseX >= sx && mouseX < sx + INV_SLOT_SIZE && mouseY >= sy && mouseY < sy + INV_SLOT_SIZE) return inv.getItem(9 + row * 9 + col);
        }
        int hotbarY = invY + 3 * INV_SLOT_SIZE + 4;
        for (int col = 0; col < 9; col++) {
            int sx = invX + col * INV_SLOT_SIZE;
            if (mouseX >= sx && mouseX < sx + INV_SLOT_SIZE && mouseY >= hotbarY && mouseY < hotbarY + INV_SLOT_SIZE) return inv.getItem(col);
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Actions
    // ═══════════════════════════════════════════════════════════════

    private void onDeleteClicked() {
        if (!deletePending) {
            deletePending = true;
            if (deleteBtn != null) deleteBtn.setMessage(Component.literal(WSLang.t("ui.confirmer_1df0")).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
            return;
        }
        String toDelete = originalName != null ? originalName : chestName;
        NetworkHandler.CHANNEL.sendToServer(new DeleteCustomChestPacket(toDelete));
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal(WSLang.t("ui.suppression_demandee_pour") + toDelete + "'…").withStyle(ChatFormatting.RED), false);
        }
        onClose();
    }

    private void openTemplatesEditor() {
        Minecraft.getInstance().setScreen(new ChestTierTemplatesPrompt(this, tierTemplates, updated -> {
            tierTemplates.clear();
            tierTemplates.putAll(updated);
        }));
    }

    private void openConfigPromptForAdd(ItemStack stack) {
        String itemId = idOf(stack);
        for (CustomChestData.Reward r : rewards) if (r.item.equals(itemId)) return;
        double remaining = 100 - totalChance();
        if (remaining <= 0) return;
        Minecraft.getInstance().setScreen(new ChestItemConfigPrompt(this, stack, remaining, null, r -> { rewards.add(r); rebuildWidgets(); }));
    }

    private void openConfigPromptForEdit(int idx) {
        if (idx < 0 || idx >= rewards.size()) return;
        CustomChestData.Reward existing = rewards.get(idx);
        ItemStack stack = itemStackOf(existing.item);
        if (stack.isEmpty()) return;
        double remaining = 100 - totalChance() + existing.chance;
        Minecraft.getInstance().setScreen(new ChestItemConfigPrompt(this, stack, remaining, existing, updated -> rebuildWidgets()));
    }

    private void removeReward(int idx) {
        if (idx < 0 || idx >= rewards.size()) return;
        rewards.remove(idx);
        int max = Math.max(0, rewards.size() - rewardListVisibleRows);
        if (rewardsScrollOffset > max) rewardsScrollOffset = max;
        rebuildWidgets();
    }

    private void save() {
        String finalName = chestName != null ? chestName.trim() : "";
        if (finalName.isEmpty()) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(Component.literal(WSLang.t("ui.nom_vide_impossible_de_sauver")).withStyle(ChatFormatting.RED), false);
            }
            return;
        }
        draft.name = finalName;
        draft.tierMessages = tierTemplates;
        draft.rewards = new ArrayList<>(rewards);
        if (draft.keyMaterial == null || draft.keyMaterial.isBlank()) draft.keyMaterial = "or";
        draft.pattern = draft.idlePattern(); // ancien champ : suit la forme de l'effet « au repos »
        NetworkHandler.CHANNEL.sendToServer(new SaveCustomChestPacket(GSON.toJson(draft)));
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal(WSLang.t("ui.coffre") + finalName + WSLang.t("ui.envoye_au_serveur")).withStyle(ChatFormatting.GREEN), false);
        }
        onClose();
    }

    // ─── Aides ───

    private double totalChance() {
        double t = 0;
        for (CustomChestData.Reward r : rewards) t += r.chance;
        return t;
    }

    private static ChatFormatting tierColorOf(String tier) {
        for (int i = 0; i < TIERS.length; i++) if (TIERS[i].equals(tier)) return TIER_COLORS[i];
        return ChatFormatting.WHITE;
    }

    private static String tierLabelOf(String tier) {
        return switch (tier != null ? tier : "COMMON") {
            case "UNCOMMON" -> WSLang.t("Peu commun");
            case "RARE" -> WSLang.t("Rare");
            case "EPIC" -> WSLang.t("Épique");
            case "LEGENDARY" -> WSLang.t("Légendaire");
            default -> WSLang.t("ui.commun");
        };
    }

    private static ItemStack itemStackOf(String id) {
        if (id == null || id.isBlank()) return ItemStack.EMPTY;
        try {
            Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
            return item == null ? ItemStack.EMPTY : new ItemStack(item);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    private static String idOf(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); }

    private static String fmt(double d) {
        if (d == Math.floor(d)) return String.valueOf((int) d);
        return String.format(java.util.Locale.ROOT, "%.1f", d);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
