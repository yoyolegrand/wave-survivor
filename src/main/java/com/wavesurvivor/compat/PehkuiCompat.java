package com.wavesurvivor.compat;

import com.wavesurvivor.WaveSurvivorMod;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/**
 * INTÉGRATION OPTIONNELLE DE PEHKUI (taille des entités). Appelée par réflexion : aucune dépendance de compilation,
 * et sans Pehkui installé tous les appels sont simplement ignorés. API utilisée :
 * {@code ScaleTypes.BASE.getScaleData(entity).setScale(f)} (taille enregistrée avec l'entité par Pehkui).
 */
public final class PehkuiCompat {

    private static Boolean ready;
    private static Object baseType;
    private static Method getScaleData, setScale;

    private PehkuiCompat() {}

    public static boolean isLoaded() { return ModList.get() != null && ModList.get().isLoaded("pehkui"); }

    private static boolean init() {
        if (ready != null) return ready;
        ready = false;
        if (!isLoaded()) return false;
        try {
            Class<?> types = Class.forName("virtuoel.pehkui.api.ScaleTypes");
            baseType = types.getField("BASE").get(null);
            Class<?> scaleType = Class.forName("virtuoel.pehkui.api.ScaleType");
            getScaleData = scaleType.getMethod("getScaleData", Entity.class);
            Class<?> scaleData = Class.forName("virtuoel.pehkui.api.ScaleData");
            setScale = scaleData.getMethod("setScale", float.class);
            ready = true;
            WaveSurvivorMod.LOGGER.info("[Pehkui] D\u00e9tect\u00e9 : r\u00e9glage de taille des unit\u00e9s activ\u00e9.");
        } catch (Throwable t) {
            WaveSurvivorMod.LOGGER.warn("[Pehkui] Pr\u00e9sent mais API introuvable ({}): tailles ignor\u00e9es.", t.toString());
        }
        return ready;
    }

    /** Donne la taille {@code scale} \u00e0 l'entit\u00e9 (1 = normale). Sans Pehkui : ne fait rien. */
    public static void setScale(Entity e, double scale) {
        if (e == null || !Double.isFinite(scale) || scale <= 0 || !init()) return;
        try {
            Object data = getScaleData.invoke(baseType, e);
            setScale.invoke(data, (float) Math.max(0.1, Math.min(10.0, scale)));
        } catch (Throwable t) {
            WaveSurvivorMod.LOGGER.debug("[Pehkui] Taille impossible pour {} : {}", e, t.toString());
        }
    }
}
