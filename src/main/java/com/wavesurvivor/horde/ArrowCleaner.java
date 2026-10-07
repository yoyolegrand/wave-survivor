package com.wavesurvivor.horde;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * NETTOYAGE DES FLÈCHES pendant une horde (classique ou Kingdom) : avec les tours et les squelettes, des centaines
 * de flèches s'accumulent au sol. Une flèche « plantée » = qui n'a pas bougé depuis le contrôle précédent
 * (une flèche au sol garde une petite vitesse résiduelle en mémoire : on compare donc la POSITION, pas la vitesse).
 *  - flèches non récupérables (monstres, squelettes, tours) → disparaissent après ~1 s ;
 *  - flèches des joueurs (récupérables) → après 20 s (le temps de les ramasser) ;
 *  - tridents → jamais.
 * Hors horde : comportement vanilla.
 */
public class ArrowCleaner {

    private static final String TAG = "ws_ground_ticks";
    private static final String PX = "ws_last_x", PY = "ws_last_y", PZ = "ws_last_z";
    private static final int SCAN_EVERY = 10;      // 2 contrôles par seconde
    private static final int LIMIT_MONSTER = 20;   // ~1 s immobile
    private static final int LIMIT_PLAYER = 400;   // 20 s

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        if (level.getGameTime() % SCAN_EVERY != 0) return;
        HordeManager hm = HordeManager.get();
        if (hm == null || !hm.isRunning()) return;

        List<AbstractArrow> arrows = new ArrayList<>();
        level.getEntities(EntityTypeTest.forClass(AbstractArrow.class), a -> !(a instanceof ThrownTrident) && a.isAlive(), arrows);
        for (AbstractArrow a : arrows) {
            CompoundTag data = a.getPersistentData();
            boolean known = data.contains(PX);
            double dx = a.getX() - data.getDouble(PX), dy = a.getY() - data.getDouble(PY), dz = a.getZ() - data.getDouble(PZ);
            boolean still = known && dx * dx + dy * dy + dz * dz < 1.0E-4;
            data.putDouble(PX, a.getX());
            data.putDouble(PY, a.getY());
            data.putDouble(PZ, a.getZ());
            if (!still) {           // en vol (ou premier contrôle) : compteur remis à zéro
                data.remove(TAG);
                continue;
            }
            int t = data.getInt(TAG) + SCAN_EVERY;
            int limit = a.pickup == AbstractArrow.Pickup.ALLOWED ? LIMIT_PLAYER : LIMIT_MONSTER;
            if (t >= limit) a.discard();
            else data.putInt(TAG, t);
        }
    }
}
