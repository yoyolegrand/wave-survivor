package com.wavesurvivor.client;

import com.wavesurvivor.item.RelicSets;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Côté client : pièces de set portées par le joueur local (infobulle des reliques). */
@OnlyIn(Dist.CLIENT)
public final class RelicClient {

    private RelicClient() {}

    public static int count(RelicSets.Family f) {
        var p = Minecraft.getInstance().player;
        return p == null ? 0 : RelicSets.countNow(p, f);
    }

    /** Le joueur local a-t-il cette relique équipée (Curios ou autel) ? */
    public static boolean equipped(net.minecraft.world.item.Item item) {
        var p = Minecraft.getInstance().player;
        return p != null && com.wavesurvivor.item.RelicEffects.hasDirect(p, item);
    }
}
