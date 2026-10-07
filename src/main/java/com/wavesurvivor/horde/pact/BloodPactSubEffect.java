package com.wavesurvivor.horde.pact;

/** Un sub-effet à l'intérieur d'un pack. */
public record BloodPactSubEffect(
        String effectId,   // "minecraft:speed"
        int amplifier,     // 0 = niveau I
        int duration,      // en TICKS (6000 = 5min)
        boolean isPositive
) {}
