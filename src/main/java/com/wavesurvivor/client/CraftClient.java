package com.wavesurvivor.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Aides client de l'Artisanat (isolées ici pour que le serveur dédié ne charge jamais les classes du client). */
@OnlyIn(Dist.CLIENT)
public final class CraftClient {

    private CraftClient() {}

    /** Vrai la nuit dans le monde du joueur (la Lame de l'aube brille). */
    public static boolean isNight() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getDayTime() % 24000L >= 13000L;
    }
}
