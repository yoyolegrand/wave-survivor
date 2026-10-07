package com.wavesurvivor.client;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.horde.kingdom.KingdomTreasury;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Panneau du TRÉSOR COMMUN (mode Kingdom) : à droite de l'écran, Monnaie, Pierre, Bois, Fer et Essence de brèche.
 * Masqué hors partie Kingdom, avec F1 et pendant l'écran de débogage (F3).
 */
@Mod.EventBusSubscriber(modid = WaveSurvivorMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class TreasuryHud {

    private static final ItemStack[] ICONS = {
            new ItemStack(Items.EMERALD), new ItemStack(Items.COBBLESTONE), new ItemStack(Items.OAK_LOG),
            new ItemStack(Items.IRON_INGOT), new ItemStack(Items.AMETHYST_SHARD)};
    private static final String[] KEYS = {"money", "stone", "logs", "iron", "essence"};
    private static final int[] COLORS = {0xFF55FF55, 0xFFBBBBBB, 0xFFD9A066, 0xFFE8E8E8, 0xFFD58CFF};

    /** Dernières valeurs affichées : un gain fait briller la ligne un court instant. */
    private static final int[] LAST = new int[KingdomTreasury.N];
    private static final long[] FLASH = new long[KingdomTreasury.N];

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        // Présage « Lune de sang » : voile rouge juste au-dessus de la vignette vanilla (HUD toujours lisible)
        event.registerAbove(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.VIGNETTE.id(), "kingdom_blood_moon",
                (gui, g, partialTick, w, h) -> renderBloodMoon(g, w, h));
        event.registerAboveAll("kingdom_treasury", (gui, g, partialTick, w, h) -> render(g, w, h));
    }

    /** Voile rouge pulsant + vignette sombre sur les bords pendant la Lune de sang (même réglages que la vignette vanilla :
     *  sans profondeur, sinon le voile masque les barres de boss et le reste de l'interface). */
    private static void renderBloodMoon(GuiGraphics g, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !"blood_moon".equals(com.wavesurvivor.horde.kingdom.KingdomRoleState.clientOmenActive)) return;
        double t = (mc.level != null ? mc.level.getGameTime() : 0) + mc.getFrameTime();
        int pulse = (int) (18 + 10 * Math.sin(t / 20.0));          // respiration lente du voile
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.depthMask(false);
        g.fill(0, 0, w, h, (pulse << 24) | 0xB00000);
        int edge = Math.max(40, h / 4);
        g.fillGradient(0, 0, w, edge, 0x90400000, 0x00400000);       // haut
        g.fillGradient(0, h - edge, w, h, 0x00400000, 0x90400000);   // bas
        g.flush();
        com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
    }

    /** Déconnexion : panneau masqué (il ne doit pas suivre le joueur dans un autre monde). */
    @Mod.EventBusSubscriber(modid = WaveSurvivorMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class Logout {
        @SubscribeEvent
        public static void treasuryHudLogout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
            KingdomTreasury.clientOn = false;
            java.util.Arrays.fill(KingdomTreasury.CLIENT, 0);
            com.wavesurvivor.horde.kingdom.KingdomRoles.clientTrackKind = "";
            com.wavesurvivor.horde.kingdom.KingdomRoles.clientTrack = new double[0];
            com.wavesurvivor.horde.kingdom.KingdomRoles.clientObjKind = "";
            com.wavesurvivor.horde.kingdom.KingdomRoles.clientObjTrack = new double[0];
            com.wavesurvivor.horde.kingdom.KingdomRoles.clientOn = false;
            com.wavesurvivor.horde.kingdom.KingdomRoles.CLIENT_HOLDERS.clear();
            com.wavesurvivor.horde.kingdom.KingdomRoleState.clientReagents = 0;
            com.wavesurvivor.horde.kingdom.KingdomRoleState.clientOmenOptions = new String[0];
            com.wavesurvivor.horde.kingdom.KingdomRoleState.clientOmenChosen = "";
            com.wavesurvivor.horde.kingdom.KingdomRoleState.clientOmenActive = "";
            com.wavesurvivor.horde.kingdom.KingdomRoleState.clientContract = "";
        }
    }

    private static void render(GuiGraphics g, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (!KingdomTreasury.clientOn || mc.player == null || mc.options.hideGui || mc.options.renderDebug) return;
        Font font = mc.font;
        long now = mc.level != null ? mc.level.getGameTime() : 0;
        int rowH = 13;
        int panelW = 78;
        int n = KingdomTreasury.N;
        int panelH = 14 + n * rowH + 3;
        int x = w - panelW - 4;
        int y = h / 2 - panelH / 2 - 20;

        g.fill(x, y, x + panelW, y + panelH, 0x90000000);
        g.fill(x, y, x + panelW, y + 1, 0xFFE0B040);
        g.drawString(font, WSLang.t("kingdom.treasury.title"), x + 4, y + 3, 0xFFE0B040, false);

        for (int i = 0; i < n; i++) {
            int v = KingdomTreasury.CLIENT[i];
            if (v > LAST[i]) FLASH[i] = now + 15;
            LAST[i] = v;
            int ry = y + 14 + i * rowH;
            if (FLASH[i] > now) g.fill(x + 1, ry - 1, x + panelW - 1, ry + rowH - 1, 0x40FFFFFF);
            g.pose().pushPose();
            g.pose().translate(x + 3, ry, 0);
            g.pose().scale(0.7f, 0.7f, 1f);
            g.renderItem(ICONS[i], 0, 0);
            g.pose().popPose();
            g.drawString(font, WSLang.t("kingdom.res.short." + KEYS[i]), x + 17, ry + 2, 0xFFAAAAAA, false);
            String val = String.valueOf(v);
            g.drawString(font, val, x + panelW - 4 - font.width(val), ry + 2, COLORS[i], false);
        }
        boolean tracked = renderTracker(g, mc, font, x, y + panelH + 4, panelW,
                com.wavesurvivor.horde.kingdom.KingdomRoles.clientTrackKind, com.wavesurvivor.horde.kingdom.KingdomRoles.clientTrack);
        int y2 = y + panelH + 4 + (tracked ? 30 : 0);
        // 2e flèche : objectif du Calme (trésor enfoui, foyers de l'Arcaniste)
        boolean tracked2 = renderTracker(g, mc, font, x, y2, panelW,
                com.wavesurvivor.horde.kingdom.KingdomRoles.clientObjKind, com.wavesurvivor.horde.kingdom.KingdomRoles.clientObjTrack);
        renderRoleLines(g, font, x, y2 + (tracked2 ? 30 : 0), panelW);
    }

    /** Ligne de rôle sous le trésor : présage en vigueur (tous), réactifs (Alchimiste). */
    private static void renderRoleLines(GuiGraphics g, Font font, int x, int y, int w) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        String omen = com.wavesurvivor.horde.kingdom.KingdomRoleState.clientOmenActive;
        if (omen != null && !omen.isEmpty()) lines.add(WSLang.t("kingdom.omen.hud", WSLang.t("kingdom.omen." + omen)));
        // Augure pendant le Calme : présage à choisir / présage choisi pour le prochain Assaut
        String[] opts = com.wavesurvivor.horde.kingdom.KingdomRoleState.clientOmenOptions;
        String chosen = com.wavesurvivor.horde.kingdom.KingdomRoleState.clientOmenChosen;
        if (opts != null && opts.length > 0) {
            lines.add(chosen == null || chosen.isEmpty() ? WSLang.t("kingdom.omen.hud_pick")
                    : WSLang.t("kingdom.omen.hud_next", WSLang.t("kingdom.omen." + chosen)));
        }
        var me = Minecraft.getInstance().player;
        if (me != null) {
            String[] h = com.wavesurvivor.horde.kingdom.KingdomRoles.CLIENT_HOLDERS.get(com.wavesurvivor.horde.kingdom.KingdomRoles.Role.ALCHEMIST);
            if (h != null && h[0].equals(me.getUUID().toString())) {
                lines.add(WSLang.t("kingdom.alchemy.hud", com.wavesurvivor.horde.kingdom.KingdomRoleState.clientReagents));
            }
        }
        // Chasseur de primes : contrat en cours (✔ quand il est rempli)
        String ct = com.wavesurvivor.horde.kingdom.KingdomRoleState.clientContract;
        if (ct != null && !ct.isEmpty()) {
            int pr = com.wavesurvivor.horde.kingdom.KingdomRoleState.clientContractProgress;
            int tg = com.wavesurvivor.horde.kingdom.KingdomRoleState.clientContractTarget;
            lines.add(pr >= tg ? WSLang.t("kingdom.bounty.hud_done")
                    : WSLang.t("kingdom.bounty.hud." + ct, pr, tg));
        }
        if (lines.isEmpty()) return;
        g.fill(x, y, x + w, y + 3 + lines.size() * 11, 0x90000000);
        for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), x + 4, y + 3 + i * 11, 0xFFFFFFFF, false);
    }

    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    /** Flèche du rôle (Arcaniste : Catalyseur, Mineur : gisement) vers la cible la plus proche, avec sa distance. */
    private static boolean renderTracker(GuiGraphics g, Minecraft mc, Font font, int x, int y, int w, String kind, double[] pts) {
        if (kind == null || kind.isEmpty() || pts == null || pts.length < 3 || mc.player == null) return false;
        double px = mc.player.getX(), pz = mc.player.getZ();
        double best = Double.MAX_VALUE, bx = 0, bz = 0;
        for (int i = 0; i + 2 < pts.length; i += 3) {
            double dx = pts[i] - px, dz = pts[i + 2] - pz, d = dx * dx + dz * dz;
            if (d < best) { best = d; bx = dx; bz = dz; }
        }
        float targetYaw = (float) Math.toDegrees(Math.atan2(-bx, bz));
        float rel = net.minecraft.util.Mth.wrapDegrees(targetYaw - mc.player.getYRot());
        String arrow = ARROWS[Math.floorMod(Math.round(rel / 45f), 8)];
        int dist = (int) Math.round(Math.sqrt(best));
        int color = switch (kind) {
            case "catalyst" -> 0xFFD58CFF;
            case "treasure" -> 0xFF55DDFF;
            case "foyer" -> 0xFFFF55AA;
            default -> 0xFFFFD040;
        };
        int n = pts.length / 3;
        g.fill(x, y, x + w, y + 26, 0x90000000);
        g.fill(x, y, x + w, y + 1, color);
        String title = WSLang.t("kingdom.track." + kind) + (n > 1 ? " §7×" + n : "");
        g.drawString(font, title, x + 4, y + 3, color, false);
        g.pose().pushPose();
        g.pose().translate(x + 4, y + 13, 0);
        g.pose().scale(1.3f, 1.3f, 1f);
        g.drawString(font, arrow, 0, 0, 0xFFFFFFFF, false);
        g.pose().popPose();
        String d = dist + " m";
        g.drawString(font, d, x + w - 4 - font.width(d), y + 15, 0xFFFFFFFF, false);
        return true;
    }
}
