package com.wavesurvivor.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.registry.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * VOILE DU SOLEIL DE PLOMB (1.9) : léger voile orangé pulsant + bords chauffés tant que le joueur subit l'effet « Soleil de plomb »
 * (événement du désert, joueur exposé). Apparition et disparition en fondu. Mêmes réglages que le voile de la Lune de sang :
 * sans profondeur, pour ne jamais masquer l'interface.
 */
@Mod.EventBusSubscriber(modid = WaveSurvivorMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class SunVeilOverlay {

    /** Intensité actuelle du voile (0 à 1), qui suit la présence de l'effet. */
    private static float level = 0f;
    private static long lastTick = -1;

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.VIGNETTE.id(), "desert_sun_veil", (gui, g, partialTick, w, h) -> render(g, w, h));
    }

    private static void render(GuiGraphics g, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) { level = 0f; return; }
        long now = mc.level.getGameTime();
        if (now != lastTick) { // un pas par tick de jeu : fondu d'environ une seconde à l'entrée, deux à la sortie
            boolean on = mc.player.hasEffect(ModEffects.SCORCHING_SUN.get());
            level = on ? Math.min(1f, level + 0.05f) : Math.max(0f, level - 0.025f);
            lastTick = now;
        }
        if (level <= 0.01f) return;
        double t = now + mc.getFrameTime();
        int pulse = (int) ((14 + 7 * Math.sin(t / 25.0)) * level);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        g.fill(0, 0, w, h, (pulse << 24) | 0xFF8C1A);
        int edge = Math.max(40, h / 4);
        int a = (int) (0x70 * level);
        int hot = (a << 24) | 0xE05A00, none = 0x00E05A00;
        g.fillGradient(0, 0, w, edge, hot, none);             // haut
        g.fillGradient(0, h - edge, w, h, none, hot);         // bas
        g.flush();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }
}
