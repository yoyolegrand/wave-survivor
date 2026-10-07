package com.wavesurvivor.client;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.horde.kingdom.PathWandItem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.List;

/**
 * APERÇU DU CHEMIN (côté client, seulement avec le Bâton de tracé en main) : une balise par point (verte = départ,
 * lilas = étapes, rouge = dernier point avant le Monolithe) et une guirlande de particules qui relie les points dans
 * l'ordre en défilant dans le sens de la marche. Ne dessine que les points à moins de 96 blocs.
 */
@Mod.EventBusSubscriber(modid = WaveSurvivorMod.MODID, value = Dist.CLIENT)
public final class PathWandPreview {

    private static final DustParticleOptions START = new DustParticleOptions(new Vector3f(0.3f, 1f, 0.4f), 1.6f);
    private static final DustParticleOptions STEP = new DustParticleOptions(new Vector3f(0.85f, 0.6f, 1f), 1.4f);
    private static final DustParticleOptions END = new DustParticleOptions(new Vector3f(1f, 0.3f, 0.3f), 1.6f);
    private static final DustParticleOptions LINE = new DustParticleOptions(new Vector3f(1f, 0.85f, 0.4f), 0.9f);

    private PathWandPreview() {}

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.isPaused()) return;
        ItemStack s = mc.player.getMainHandItem();
        if (!(s.getItem() instanceof PathWandItem)) s = mc.player.getOffhandItem();
        if (!(s.getItem() instanceof PathWandItem)) return;
        long now = mc.level.getGameTime();
        if (now % 4 != 0) return;
        List<BlockPos> pts = PathWandItem.points(s);
        BlockPos me = mc.player.blockPosition();
        int phase = (int) ((now / 4) % 3);
        for (int i = 0; i < pts.size(); i++) {
            BlockPos p = pts.get(i);
            boolean near = p.distSqr(me) < 96 * 96;
            if (near) {
                DustParticleOptions col = i == 0 ? START : i == pts.size() - 1 ? END : STEP;
                for (int h = 0; h < 6; h++) mc.level.addParticle(col, p.getX() + 0.5, p.getY() + 0.2 + h * 0.45, p.getZ() + 0.5, 0, 0, 0);
            }
            if (i + 1 < pts.size()) {
                BlockPos q = pts.get(i + 1);
                if (!near && q.distSqr(me) >= 96 * 96) continue;
                double dx = q.getX() - p.getX(), dy = q.getY() - p.getY(), dz = q.getZ() - p.getZ();
                int n = Math.max(1, (int) (Math.sqrt(dx * dx + dy * dy + dz * dz) / 0.7));
                for (int k = 0; k <= n; k++) {
                    if ((k + 3 - phase) % 3 != 0) continue; // défile dans le sens du chemin
                    double f = k / (double) n;
                    mc.level.addParticle(LINE, p.getX() + 0.5 + dx * f, p.getY() + 0.6 + dy * f, p.getZ() + 0.5 + dz * f, 0, 0, 0);
                }
            }
        }
    }
}
