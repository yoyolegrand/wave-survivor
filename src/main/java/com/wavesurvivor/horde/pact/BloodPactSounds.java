package com.wavesurvivor.horde.pact;

/** Sons du pacte de sang. Chaque champ = un sound ID vanilla. */
public record BloodPactSounds(
        String activation,
        String rolling,
        String positive,
        String negative
) {}
