package com.wavesurvivor.horde.roulette;

/**
 * CHRONOLOGIE DE L'OUVERTURE D'UN COFFRE ROULETTE (1.9), partagée par le serveur (ouverture du couvercle, bonus de rareté, remise du gain) et le client
 * (rotation du médaillon, faisceau, objet qui s'élève). Tout est exprimé en ticks depuis le lancement ; seul l'instant de remise du gain ({@code give})
 * est échangé, le reste s'en déduit.
 * <pre>
 *   0 ............ accelEnd ........ decelStart ...... stop ..... riseStart ..... give ........ give + 40
 *   médaillon : accélère  | pleine vitesse | ralentit | arrêt (à l'endroit)
 *   couvercle :                                          | s'ouvre (0,4 s) |      | se referme
 *   faisceau  :                                          | de la couleur de rareté ............
 *   objet     :                                                            | s'élève dans le faisceau | donné
 * </pre>
 * Les durées nominales (1,5 s d'accélération, 1 s de ralentissement, 0,4 s d'ouverture, 1 s d'élévation) sont réduites proportionnellement
 * quand l'animation est plus courte (file d'attente accélérée).
 */
public final class RouletteAnim {

    /** Nominal : accélération 30, ralentissement 20, ouverture 8, élévation 20 ticks ; le couvercle reste ouvert 40 ticks après le gain. */
    public static final int CLOSE_AFTER = 40;

    /** Étapes en ticks et vitesse de pointe du médaillon (degrés par tick). */
    public record Timeline(int accelEnd, int decelStart, int stop, int riseStart, int give, float vmax) {}

    private RouletteAnim() {}

    public static Timeline of(int give) {
        float k = Math.min(1f, give / 56f);
        int rise = Math.max(2, Math.round(20 * k)), open = Math.max(2, Math.round(8 * k)), slow = Math.max(3, Math.round(20 * k));
        int stop = Math.max(4, give - rise - open);
        int accel = Math.max(2, Math.min(Math.round(30 * k), stop / 2));
        int decel = Math.max(accel + 1, stop - slow);
        if (decel >= stop) decel = stop - 1;
        // Vitesse de pointe choisie pour que le médaillon s'arrête exactement à l'endroit (nombre entier de tours)
        float span = accel / 2f + (decel - accel) + (stop - decel) / 2f;
        int turns = Math.max(2, Math.round(12f * span / 360f)); // ≈ 12° par tick au nominal
        float vmax = 360f * turns / span;
        return new Timeline(accel, decel, stop, give - rise, give, vmax);
    }

    /** Angle du médaillon (degrés) à l'instant {@code t} (ticks depuis le lancement). */
    public static float spinAngle(Timeline tl, float t) {
        if (t <= 0) return 0;
        float v = tl.vmax();
        float a1 = v * tl.accelEnd() / 2f;
        if (t <= tl.accelEnd()) return v * t * t / (2f * tl.accelEnd());
        float a2 = a1 + v * (tl.decelStart() - tl.accelEnd());
        if (t <= tl.decelStart()) return a1 + v * (t - tl.accelEnd());
        float d = tl.stop() - tl.decelStart();
        if (t <= tl.stop()) {
            float u = t - tl.decelStart();
            return a2 + v * (u - u * u / (2f * d));
        }
        return a2 + v * d / 2f;
    }

    /** Intensité du faisceau (0 à 1) : monte en 6 ticks à l'arrêt du médaillon, tient jusqu'après le gain, puis s'éteint en 14 ticks. */
    public static float beamStrength(Timeline tl, float t) {
        float in = Math.min(1f, Math.max(0f, (t - tl.stop()) / 6f));
        float out = Math.min(1f, Math.max(0f, (tl.give() + 20 - t) / 14f));
        return Math.min(in, out);
    }

    /** Avancement de l'objet qui s'élève (0 au début, 1 au moment du gain) ; négatif tant qu'il n'est pas apparu. */
    public static float rise(Timeline tl, float t) {
        return (t - tl.riseStart()) / Math.max(1f, tl.give() - tl.riseStart());
    }
}
