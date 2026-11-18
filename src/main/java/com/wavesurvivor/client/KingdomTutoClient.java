package com.wavesurvivor.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.network.KingdomTutoPackets;
import com.wavesurvivor.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 2.1 — Tuto du Kingdom, côté client : bandeau de phase (≈ 3 s), teinte du bord de l'écran (dorée au Calme, rouge qui pulse au début
 * de l'Assaut), liste « À faire » sous le trésor (envoyée par le serveur) et ouverture de l'écran du cycle.
 * Option : config/wavesurvivor/client_options.json › {"kingdomTutorial": false} coupe tout (le serveur est prévenu à la connexion).
 */
@Mod.EventBusSubscriber(modid = WaveSurvivorMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class KingdomTutoClient {

    private static final long BANNER_MS = 3000;
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private static Boolean enabled;
    // Bandeau
    private static boolean bannerOn, bannerAssault;
    private static int bannerSecs;
    private static long bannerStart;   // 0 = pas encore affiché (un écran est ouvert)
    // Liste « À faire »
    private static String header = "", footer = "";
    private static List<KingdomTutoPackets.Line> lines = new ArrayList<>();
    private static long todoAt;
    private static boolean cyclePending;

    private KingdomTutoClient() {}

    /** Option client (lue une fois). */
    public static boolean enabled() {
        Boolean e = enabled;
        if (e != null) return e;
        e = true;
        Path f = FMLPaths.CONFIGDIR.get().resolve("wavesurvivor").resolve("client_options.json");
        try {
            if (Files.exists(f)) {
                JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                if (o.has("kingdomTutorial")) e = o.get("kingdomTutorial").getAsBoolean();
            }
        } catch (Exception ignored) { }
        enabled = e;
        return e;
    }

    public static void receive(KingdomTutoPackets.Hud pkt) {
        if (!enabled()) return;
        switch (pkt.kind) {
            case KingdomTutoPackets.Hud.OFF -> clear();
            case KingdomTutoPackets.Hud.BANNER -> {
                bannerOn = true;
                bannerAssault = pkt.assault;
                bannerSecs = pkt.seconds;
                bannerStart = 0;
                lines = new ArrayList<>();   // l'ancienne liste ne vaut plus pour la nouvelle phase
                header = "";
            }
            case KingdomTutoPackets.Hud.TODO -> {
                header = pkt.header;
                footer = pkt.footer;
                lines = new ArrayList<>(pkt.lines);
                todoAt = System.currentTimeMillis();
            }
            case KingdomTutoPackets.Hud.CYCLE -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen == null) mc.setScreen(new KingdomCycleScreen(null));
                else cyclePending = true;   // ouvert dès que l'écran en cours se ferme
            }
            default -> { }
        }
    }

    private static void clear() {
        bannerOn = false;
        lines = new ArrayList<>();
        header = "";
        footer = "";
        cyclePending = false;
    }

    // ─── Bandeau et teinte ───

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent e) {
        e.registerAbove(VanillaGuiOverlay.VIGNETTE.id(), "kingdom_tuto_tint", (gui, g, pt, w, h) -> renderTint(g, w, h));
        e.registerAboveAll("kingdom_tuto_banner", (gui, g, pt, w, h) -> renderBanner(g, w, h));
    }

    /** Temps écoulé du bandeau (ms) ; le compte ne démarre qu'une fois les écrans fermés. −1 = rien à afficher. */
    private static long bannerElapsed() {
        if (!bannerOn) return -1;
        Minecraft mc = Minecraft.getInstance();
        if (bannerStart == 0) {
            if (mc.screen != null) return -1;
            bannerStart = System.currentTimeMillis();
        }
        long t = System.currentTimeMillis() - bannerStart;
        if (t > BANNER_MS) { bannerOn = false; return -1; }
        return t;
    }

    private static float bannerAlpha(long t) {
        if (t < 300) return t / 300f;
        if (t > BANNER_MS - 700) return Math.max(0f, (BANNER_MS - t) / 700f);
        return 1f;
    }

    private static void renderTint(GuiGraphics g, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        long t = bannerElapsed();
        if (t < 0) return;
        float a = bannerAlpha(t);
        int base;
        if (bannerAssault) {   // rouge qui pulse
            a *= 0.55f + 0.45f * (float) Math.abs(Math.sin(t / 250.0));
            base = 0xC00000;
        } else {
            a *= 0.6f;   // doré, doux
            base = 0xE0B040;
        }
        int edge = Math.max(30, h / 5), col = ((int) (a * 0xA0) << 24) | base;
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.depthMask(false);
        g.fillGradient(0, 0, w, edge, col, base);
        g.fillGradient(0, h - edge, w, h, base, col);
        for (int i = 0; i < edge; i += 2) {   // côtés : dégradé horizontal en bandes
            int c = ((int) (a * 0xA0 * (1f - i / (float) edge)) << 24) | base;
            g.fill(i, 0, i + 2, h, c);
            g.fill(w - i - 2, 0, w - i, h, c);
        }
        g.flush();
        com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
    }

    private static void renderBanner(GuiGraphics g, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (cyclePending && mc.screen == null && mc.player != null) {
            cyclePending = false;
            mc.setScreen(new KingdomCycleScreen(null));
            return;
        }
        if (mc.player == null || mc.options.hideGui) return;
        long t = bannerElapsed();
        if (t < 0) return;
        float a = bannerAlpha(t);
        int alpha = Math.max(4, (int) (a * 255)) << 24;
        Font font = mc.font;
        String title = WSLang.t(bannerAssault ? "kingdom.tuto.banner.assault" : "kingdom.tuto.banner.calm");
        String sub = bannerAssault ? WSLang.t("kingdom.tuto.banner.assault_sub")
                : bannerSecs < 0 ? WSLang.t("kingdom.tuto.banner.calm_sub_gentle")
                : WSLang.t("kingdom.tuto.banner.calm_sub", String.format("%d:%02d", bannerSecs / 60, bannerSecs % 60));
        int y = h / 4;
        g.fill(0, y - 8, w, y + 46, ((int) (a * 0x70)) << 24);
        g.pose().pushPose();
        g.pose().translate(w / 2f, y, 0);
        g.pose().scale(2.5f, 2.5f, 1f);
        g.drawString(font, title, -font.width(title) / 2, 0, alpha | (bannerAssault ? 0xFF5A4A : 0xF0C660), true);
        g.pose().popPose();
        g.pose().pushPose();
        g.pose().translate(w / 2f, y + 28, 0);
        g.pose().scale(1.2f, 1.2f, 1f);
        g.drawString(font, sub, -font.width(sub) / 2, 0, alpha | 0xFFFFFF, true);
        g.pose().popPose();
    }

    // ─── Liste « À faire » (sous le trésor, appelée par TreasuryHud) ───

    /** @return hauteur dessinée. */
    public static int renderTodo(GuiGraphics g, int w, int y) {
        Minecraft mc = Minecraft.getInstance();
        if (!enabled() || mc.player == null || (lines.isEmpty() && header.isEmpty())) return 0;
        if (System.currentTimeMillis() - todoAt > 6000) return 0;   // plus de mise à jour (tuto terminé / déconnexion)
        Font font = mc.font;
        int pw = 150, x = w - pw - 4, tw = pw - 8;
        List<FormattedCharSequence> out = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        if (!header.isEmpty()) for (FormattedCharSequence s : font.split(Component.literal("§e" + header), tw)) { out.add(s); colors.add(0); }
        for (KingdomTutoPackets.Line l : lines) {
            String txt = l.done() ? "§a✔ §7" + l.text() : "§f• " + l.text();
            if (l.hasTarget() && !l.done()) txt += " §b" + arrow(mc, l.x(), l.z());
            for (FormattedCharSequence s : font.split(Component.literal(txt), tw)) { out.add(s); colors.add(0); }
        }
        if (!footer.isEmpty()) for (FormattedCharSequence s : font.split(Component.literal("§6" + footer), tw)) { out.add(s); colors.add(1); }
        int ph = 14 + out.size() * 10 + 2;
        g.fill(x, y, x + pw, y + ph, 0x90000000);
        g.fill(x, y, x + pw, y + 1, 0xFF6FB7FF);
        g.drawString(font, WSLang.t("kingdom.tuto.todo.title"), x + 4, y + 3, 0xFF6FB7FF, false);
        for (int i = 0; i < out.size(); i++) {
            int ly = y + 14 + i * 10;
            if (colors.get(i) == 1 && (i == 0 || colors.get(i - 1) == 0)) g.fill(x + 3, ly - 2, x + pw - 3, ly - 1, 0x40FFFFFF);
            g.drawString(font, out.get(i), x + 4, ly, 0xFFFFFFFF, false);
        }
        return ph;
    }

    /** Flèche de direction (relative au regard du joueur) et distance. */
    private static String arrow(Minecraft mc, double tx, double tz) {
        double dx = tx - mc.player.getX(), dz = tz - mc.player.getZ();
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float rel = Mth.wrapDegrees(targetYaw - mc.player.getYRot());
        return ARROWS[Math.floorMod(Math.round(rel / 45f), 8)] + " " + (int) Math.round(Math.sqrt(dx * dx + dz * dz)) + "m";
    }

    // ─── Connexion : option transmise au serveur ───

    @Mod.EventBusSubscriber(modid = WaveSurvivorMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class Conn {
        @SubscribeEvent
        public static void onLogin(ClientPlayerNetworkEvent.LoggingIn e) {
            if (!enabled()) NetworkHandler.CHANNEL.sendToServer(new KingdomTutoPackets.Action("optout"));
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
            clear();
        }
    }
}
