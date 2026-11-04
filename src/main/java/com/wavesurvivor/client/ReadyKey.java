package com.wavesurvivor.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.network.NetworkHandler;
import com.wavesurvivor.network.ReadyPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Touche « Prêt » (réglable dans Contrôles › Wave Survivor, G par défaut) : se déclarer prêt pendant une pause entre deux vagues. */
public final class ReadyKey {

    public static final KeyMapping KEY = new KeyMapping("key.wavesurvivor.ready", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.wavesurvivor");

    private ReadyKey() {}

    @Mod.EventBusSubscriber(modid = WaveSurvivorMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent
        public static void onKeys(RegisterKeyMappingsEvent e) { e.register(KEY); }
    }

    @Mod.EventBusSubscriber(modid = WaveSurvivorMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class Ticks {
        @SubscribeEvent
        public static void onTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            while (KEY.consumeClick()) {
                if (mc.player != null && mc.screen == null) NetworkHandler.CHANNEL.sendToServer(new ReadyPacket());
            }
        }
    }
}
