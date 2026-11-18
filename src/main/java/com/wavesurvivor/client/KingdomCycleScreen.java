package com.wavesurvivor.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.Map;

/**
 * 2.1 — Écran illustré « Le cycle du Royaume » (tuto du Kingdom, v3) : diorama Calme → Assaut → Bilan à gauche, trois cartes d'étapes
 * à droite, cartes Victoire / Défaite et bouton en bas. Les chiffres des médaillons sont gravés dans la texture.
 * Texture : textures/gui/kingdom_cycle.png (1024 × 1024, panneau 800 × 520 en haut à gauche, affiché en 400 × 260).
 * Le texte est écrit par le code d'après assets/wavesurvivor/gui/kingdom_cycle_layout.json (copie de docs/kingdom_tuto, la source de vérité),
 * en pixels d'écran du panneau : « center » = centré, « topLeft » + « width » = retour à la ligne, « rect » = bouton,
 * « scale » = taille de police, « color » (blanc cassé #D6D2C8 par défaut). Interligne 9 × scale ; ombre sur les titres.
 * Ouvert au lancement pour les joueurs en tuto ; aussi depuis le Codex (page Mode Kingdom) et le Carnet.
 */
public class KingdomCycleScreen extends Screen {

    private static final ResourceLocation TEX = new ResourceLocation(WaveSurvivorMod.MODID, "textures/gui/kingdom_cycle.png");
    private static final ResourceLocation LAYOUT = new ResourceLocation(WaveSurvivorMod.MODID, "gui/kingdom_cycle_layout.json");
    private static final int PW = 400, PH = 260, DEFAULT_COLOR = 0xD6D2C8;
    private static JsonObject layout;

    private final Screen parent;
    private float scale = 1f;
    private int left, top;
    /** Zone du bouton (lue dans la mise en page). */
    private final int[] button = new int[4];

    public KingdomCycleScreen(Screen parent) {
        super(Component.literal(WSLang.t("kingdom.cycle.title")));
        this.parent = parent;
    }

    @Override
    protected void init() {
        scale = Math.min(1f, Math.min((width - 8) / (float) PW, (height - 8) / (float) PH));
        left = (int) ((width - PW * scale) / 2 / scale);
        top = (int) ((height - PH * scale) / 2 / scale);
        for (Map.Entry<String, com.google.gson.JsonElement> en : zones().entrySet()) {
            if (!en.getValue().isJsonObject() || !en.getValue().getAsJsonObject().has("rect")) continue;
            JsonArray r = en.getValue().getAsJsonObject().getAsJsonArray("rect");
            for (int i = 0; i < 4 && i < r.size(); i++) button[i] = r.get(i).getAsInt();
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1f);
        g.blit(TEX, left, top, PW, PH, 0, 0, 800, 520, 1024, 1024);
        boolean hover = overButton(mx, my);
        if (hover) g.fill(left + button[0], top + button[1], left + button[2], top + button[3], 0x30FFFFFF);
        for (Map.Entry<String, com.google.gson.JsonElement> en : zones().entrySet()) {
            if (!en.getValue().isJsonObject()) continue;
            String name = en.getKey();
            JsonObject z = en.getValue().getAsJsonObject();
            String text = text(z.has("key") ? z.get("key").getAsString() : "");
            float s = z.has("scale") ? z.get("scale").getAsFloat() : 1f;
            int color = color(z);
            // Ombre portée sur les titres (titre, but, noms des phases, titres des cartes, bouton)
            boolean shadow = name.endsWith("title") || name.endsWith("_name") || name.equals("goal") || name.equals("button");
            if (z.has("center")) {
                JsonArray c = z.getAsJsonArray("center");
                center(g, text, c.get(0).getAsFloat(), c.get(1).getAsFloat(), s, color, shadow);
            } else if (z.has("topLeft")) {
                JsonArray c = z.getAsJsonArray("topLeft");
                wrap(g, text, c.get(0).getAsFloat(), c.get(1).getAsFloat(), z.has("width") ? z.get("width").getAsInt() : 100, s, color, shadow);
            } else if (z.has("rect")) {   // bouton dessiné dans la texture : libellé centré, éclairci au survol
                center(g, text, (button[0] + button[2]) / 2f, (button[1] + button[3]) / 2f, s, hover ? 0xFFFFA0 : (z.has("color") ? color : 0xFAE8BE), true);
            }
        }
        g.pose().popPose();
    }

    /** Texte d'une zone ; le « %s » du Calme reçoit la vraie touche « Prêt » configurée. */
    private static String text(String key) {
        String t = WSLang.t(key);
        if (t.contains("%s")) t = t.replace("%s", ReadyKey.KEY.getTranslatedKeyMessage().getString());
        return t;
    }

    private static int color(JsonObject z) {
        if (!z.has("color")) return DEFAULT_COLOR;
        try {
            return Integer.parseInt(z.get("color").getAsString().replace("#", ""), 16);
        } catch (NumberFormatException e) {
            return DEFAULT_COLOR;
        }
    }

    // ─── Mise en page ───

    private static JsonObject layout() {
        if (layout != null) return layout;
        layout = new JsonObject();
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(LAYOUT);
            if (res.isPresent()) try (var r = res.get().openAsReader()) {
                layout = JsonParser.parseReader(r).getAsJsonObject();
            }
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.warn("[Kingdom] Mise en page du cycle illisible : {}", e.getMessage());
        }
        return layout;
    }

    private static JsonObject zones() {
        JsonObject l = layout();
        return l.has("zones") && l.get("zones").isJsonObject() ? l.getAsJsonObject("zones") : new JsonObject();
    }

    /** Texte centré sur (cx, cy) en pixels du panneau (cy = milieu de la ligne), mis à l'échelle autour de ce point. */
    private void center(GuiGraphics g, String t, float cx, float cy, float s, int color, boolean shadow) {
        g.pose().pushPose();
        g.pose().translate(left + cx, top + cy, 0);
        g.pose().scale(s, s, 1f);
        g.drawString(font, t, -font.width(t) / 2, -4, 0xFF000000 | color, shadow);
        g.pose().popPose();
    }

    /** Texte à la ligne automatique dans une colonne de {@code w} pixels du panneau (interligne 9 × scale). */
    private void wrap(GuiGraphics g, String t, float x, float y, int w, float s, int color, boolean shadow) {
        List<FormattedCharSequence> ls = font.split(Component.literal(t), (int) (w / s));
        g.pose().pushPose();
        g.pose().translate(left + x, top + y, 0);
        g.pose().scale(s, s, 1f);
        for (int i = 0; i < ls.size(); i++) g.drawString(font, ls.get(i), 0, i * 9, 0xFF000000 | color, shadow);
        g.pose().popPose();
    }

    private boolean overButton(double mx, double my) {
        double px = mx / scale - left, py = my / scale - top;
        return px >= button[0] && px < button[2] && py >= button[1] && py < button[3];
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (b == 0 && overButton(mx, my)) {
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
            onClose();
            return true;
        }
        return super.mouseClicked(mx, my, b);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
