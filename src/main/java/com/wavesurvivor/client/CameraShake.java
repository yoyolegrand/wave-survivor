package com.wavesurvivor.client;

import com.wavesurvivor.WaveSurvivorMod;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Secousse de caméra (séisme, 1.7) : tant que le séisme dure, la vue tremble (tangage, lacet et roulis aléatoires,
 * qui s'atténuent sur la fin). Déclenchée par ShakePacket.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = WaveSurvivorMod.MODID, value = Dist.CLIENT)
public class CameraShake {

    private static int remaining = 0;
    private static float intensity = 0f;
    private static final RandomSource RNG = RandomSource.create();

    /** Démarre (ou prolonge) la secousse. */
    public static void start(int ticks, float inten) {
        remaining = Math.max(remaining, ticks);
        intensity = Math.max(0f, inten);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase == TickEvent.Phase.END && remaining > 0) remaining--;
    }

    @SubscribeEvent
    public static void onAngles(ViewportEvent.ComputeCameraAngles e) {
        if (remaining <= 0 || intensity <= 0f) return;
        float f = intensity * Math.min(1f, remaining / 12f);
        e.setPitch(e.getPitch() + (RNG.nextFloat() - 0.5f) * f * 1.6f);
        e.setYaw(e.getYaw() + (RNG.nextFloat() - 0.5f) * f * 1.6f);
        e.setRoll(e.getRoll() + (RNG.nextFloat() - 0.5f) * f * 2.6f);
    }
}
