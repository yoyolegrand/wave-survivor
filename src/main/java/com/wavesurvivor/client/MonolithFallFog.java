package com.wavesurvivor.client;

import com.wavesurvivor.WaveSurvivorMod;
import net.minecraft.Util;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * « La Chute du Monolithe » côté client : le brouillard vire au rouge sang et se resserre (teinte aussi l'horizon et le
 * ciel). Montée en 1 s, palier, puis retour à la normale sur les 2 dernières secondes.
 */
@Mod.EventBusSubscriber(modid = WaveSurvivorMod.MODID, value = Dist.CLIENT)
public final class MonolithFallFog {

    private static long startMs = -1, endMs = -1;

    private MonolithFallFog() {}

    public static void start(int ticks) {
        startMs = Util.getMillis();
        endMs = startMs + ticks * 50L;
    }

    /** Intensité 0 → 1 de l'effet (0 = aucun). */
    private static float strength() {
        if (startMs < 0) return 0;
        long now = Util.getMillis();
        if (now >= endMs) { startMs = endMs = -1; return 0; }
        float up = Math.min(1f, (now - startMs) / 1000f);
        float down = Math.min(1f, (endMs - now) / 2000f);
        float k = Math.min(up, down);
        return k * k * (3 - 2 * k);
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor e) {
        float k = strength();
        if (k <= 0) return;
        e.setRed(e.getRed() + (0.42f - e.getRed()) * k);
        e.setGreen(e.getGreen() + (0.02f - e.getGreen()) * k);
        e.setBlue(e.getBlue() + (0.03f - e.getBlue()) * k);
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog e) {
        float k = strength();
        if (k <= 0) return;
        float far = e.getFarPlaneDistance();
        e.setFarPlaneDistance(far + (Math.min(far, 40f) - far) * k);
        e.setNearPlaneDistance(e.getNearPlaneDistance() * (1 - k));
        e.setCanceled(true); // nécessaire pour que les distances modifiées soient appliquées
    }
}
