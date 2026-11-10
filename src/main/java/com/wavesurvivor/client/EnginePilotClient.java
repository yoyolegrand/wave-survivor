package com.wavesurvivor.client;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.entity.SiegeEngineEntity;
import com.wavesurvivor.network.EngineFirePacket;
import com.wavesurvivor.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 2.0 — Pilotage de la baliste céleste : le joueur y est assis (caméra derrière et un peu au-dessus de l'arme), vise avec la souris
 * (la tourelle et l'arme suivent), tire en maintenant le clic droit ; Maj pour descendre. Réticule et chargeur à l'écran.
 */
@Mod.EventBusSubscriber(modid = WaveSurvivorMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class EnginePilotClient {

    private EnginePilotClient() {}

    private static SiegeEngineEntity engine() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getVehicle() instanceof SiegeEngineEntity e && e.isBallista() ? e : null;
    }

    /** Clic droit maintenu : un tir à chaque tick (le serveur applique la cadence, le chargeur et le rechargement). */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || engine() == null) return;
        if (mc.options.keyUse.isDown()) NetworkHandler.CHANNEL.sendToServer(new EngineFirePacket());
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Post ev) {
        if (ev.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) return;
        SiegeEngineEntity e = engine();
        Minecraft mc = Minecraft.getInstance();
        if (e == null || !mc.options.getCameraType().isFirstPerson()) return;
        GuiGraphics g = ev.getGuiGraphics();
        int cx = ev.getWindow().getGuiScaledWidth() / 2, cy = ev.getWindow().getGuiScaledHeight() / 2;
        long now = mc.level != null ? mc.level.getGameTime() : 0;
        boolean reloading = e.reloadStart() > -1000 && now - e.reloadStart() < e.reloadTicks();
        int col = reloading ? 0xFFFFAA33 : e.ammo() > 0 ? 0xFF7FE8FF : 0xFFFF5555;
        // anneau (8 segments), quatre repères et point central
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8;
            int x = cx + (int) Math.round(Math.cos(a) * 13), y = cy + (int) Math.round(Math.sin(a) * 13);
            if (i % 2 == 0) g.fill(x, y, x + 1, y + 1, col);
        }
        g.fill(cx - 22, cy, cx - 16, cy + 1, col);
        g.fill(cx + 16, cy, cx + 22, cy + 1, col);
        g.fill(cx, cy - 22, cx + 1, cy - 16, col);
        g.fill(cx, cy + 16, cx + 1, cy + 22, col);
        g.fill(cx, cy, cx + 1, cy + 1, 0xFFFFFFFF);
        String txt = reloading ? "Rechargement…" : e.ammo() + " / 16";
        g.drawCenteredString(mc.font, txt, cx, cy + 28, col);
        g.drawCenteredString(mc.font, "§7" + com.wavesurvivor.i18n.WSLang.t("rift.e.dismount"), cx, cy + 40, 0xFFFFFFFF);
    }
}
