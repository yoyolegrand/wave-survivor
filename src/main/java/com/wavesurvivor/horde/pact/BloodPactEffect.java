package com.wavesurvivor.horde.pact;

import java.util.List;

/**
 * Un effet du pacte, deux types :
 *   - SINGLE : effectId + amplifier + duration + displayName (packEffects null)
 *   - PACK   : packName + packEffects list (effectId/amplifier/duration/displayName null)
 * Fields communs : isPositive, chance.
 */
public record BloodPactEffect(
        Type type,
        boolean isPositive,
        double chance,
        // SINGLE fields (null si PACK)
        String effectId,
        int amplifier,
        int duration,
        String displayName,
        // PACK fields (null si SINGLE)
        String packName,
        List<BloodPactSubEffect> packEffects
) {
    public enum Type { SINGLE, PACK }

    /** Factory pour un effet single. */
    public static BloodPactEffect single(String effectId, int amplifier, int duration,
                                          boolean isPositive, double chance, String displayName) {
        return new BloodPactEffect(Type.SINGLE, isPositive, chance,
                effectId, amplifier, duration, displayName, null, null);
    }

    /** Factory pour un pack. */
    public static BloodPactEffect pack(String packName, boolean isPositive, double chance,
                                        List<BloodPactSubEffect> packEffects) {
        return new BloodPactEffect(Type.PACK, isPositive, chance,
                null, 0, 0, null, packName, packEffects);
    }

    /** Durée max en ticks de cet effet (pour calcul cooldown). */
    public int maxDuration() {
        if (type == Type.SINGLE) return duration;
        int max = 0;
        for (BloodPactSubEffect e : packEffects) {
            if (e.duration() > max) max = e.duration();
        }
        return max;
    }
}
