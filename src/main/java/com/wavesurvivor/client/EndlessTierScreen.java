package com.wavesurvivor.client;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.i18n.WSLang;
import com.wavesurvivor.network.EndlessTierPacket;
import com.wavesurvivor.network.EndlessVotePacket;
import com.wavesurvivor.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Écran du palier du mode Sans fin : Continuer (100 % des gains, aucun malus) ou Encaisser et s'arrêter (part réglée par la horde).
 * Si une autre fenêtre est ouverte à l'arrivée du paquet (choix de bénédiction), l'écran attend qu'elle soit fermée tant que le temps n'est pas écoulé.
 */
@OnlyIn(Dist.CLIENT)
public class EndlessTierScreen extends Screen {

    /** Palier en attente d'affichage (null = aucun) et heure de fin du choix (millisecondes). */
    private static EndlessTierPacket pending = null;
    private static long endMillis = 0;
    /** Vote déjà envoyé pour ce palier : 0 = non, 1 = continuer, 2 = encaisser. */
    private static int voted = 0;

    private final EndlessTierPacket pkt;

    private EndlessTierScreen(EndlessTierPacket pkt) {
        super(Component.literal(WSLang.t("endless.tier_title", pkt.tier, pkt.wave)));
        this.pkt = pkt;
    }

    /** Réception du paquet du serveur : nouveau palier, ou fermeture si le choix est terminé. */
    public static void receive(EndlessTierPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (p.close) {
            pending = null;
            voted = 0;
            if (mc.screen instanceof EndlessTierScreen) mc.setScreen(null);
            return;
        }
        pending = p;
        voted = 0;
        endMillis = System.currentTimeMillis() + p.seconds * 1000L;
        if (mc.screen == null) mc.setScreen(new EndlessTierScreen(p));
    }

    @Override
    protected void init() {
        int w = 150, x = width / 2 - w - 6, y = height / 2 + 6;
        addRenderableWidget(Button.builder(Component.literal("§a" + WSLang.t("endless.tier_continue")), b -> send(false))
                .bounds(x, y, w, 20).build());
        addRenderableWidget(Button.builder(Component.literal("§6" + WSLang.t("endless.tier_cash")), b -> send(true))
                .bounds(width / 2 + 6, y, w, 20).build());
    }

    private void send(boolean cash) {
        voted = cash ? 2 : 1;
        NetworkHandler.CHANNEL.sendToServer(new EndlessVotePacket(cash));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int pw = 330, ph = 120, left = width / 2 - pw / 2, top = height / 2 - 44;
        g.fill(left, top, left + pw, top + ph, 0xF0141018);
        g.fill(left, top, left + pw, top + 2, 0xFFF59E0B);
        g.drawCenteredString(font, "§6§l" + WSLang.t("endless.tier_title", pkt.tier, pkt.wave), width / 2, top + 8, 0xFFFFFFFF);
        g.drawCenteredString(font, "§7" + WSLang.t("endless.tier_chest_got"), width / 2, top + 22, 0xFFFFFFFF);
        g.drawCenteredString(font, "§a" + WSLang.t("endless.tier_continue.d"), width / 2 - 81, top + 74, 0xFFFFFFFF);
        g.drawCenteredString(font, "§6" + WSLang.t("endless.tier_cash.d", pkt.cashPercent), width / 2 + 81, top + 74, 0xFFFFFFFF);
        long left_ms = Math.max(0, endMillis - System.currentTimeMillis());
        g.drawCenteredString(font, "§e" + WSLang.t("endless.tier_time", (left_ms + 999) / 1000), width / 2, top + 38, 0xFFFFFFFF);
        g.drawCenteredString(font, "§8" + WSLang.t(pkt.hostDecides ? "endless.tier_rule_host" : "endless.tier_rule_majority"), width / 2, top + 50, 0xFFFFFFFF);
        if (voted != 0) g.drawCenteredString(font, "§f" + WSLang.t(voted == 2 ? "endless.tier_voted_cash" : "endless.tier_voted_continue"), width / 2, top + ph - 14, 0xFFFFFFFF);
        super.render(g, mx, my, pt);
    }

    /** Échap : fermer sans voter (compte comme « Continuer »), et ne pas rouvrir l'écran pour ce palier. */
    @Override
    public void onClose() {
        pending = null;
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    /** Rouvre l'écran quand une autre fenêtre (bénédiction) se ferme, tant que le choix est possible. */
    @Mod.EventBusSubscriber(modid = WaveSurvivorMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class Ticks {
        @SubscribeEvent
        public static void onTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END || pending == null) return;
            Minecraft mc = Minecraft.getInstance();
            if (System.currentTimeMillis() >= endMillis) { pending = null; return; }
            if (mc.player != null && mc.screen == null && voted == 0) mc.setScreen(new EndlessTierScreen(pending));
        }
    }
}
