package com.wavesurvivor.horde.pact;

import java.util.List;

/**
 * Configuration d'un pacte de sang.
 * Le headCustomId est un string unique (ex "Custom Head ID: 7103") qu'on cherche
 * dans le Lore de la tête pour identifier de quel pacte il s'agit au right-click.
 */
public record BloodPactConfig(
        String pactName,              // identifiant : "demon", "test", "Pacte1"
        String headCustomId,          // ex "Custom Head ID: 7103" (marker dans Lore)
        String displayName,           // "§4§lCalice Démoniaque"
        String skullOwnerNbt,         // NBT SkullOwner (textures Base64) — string tel qu'en JS
        PactType pactType,            // RANDOM ou SEALED
        boolean consumeHead,
        boolean showAnimation,
        int animationDurationSeconds,
        String particles,             // "minecraft:soul_fire_flame"
        BloodPactSounds sounds,
        List<BloodPactEffect> effects
) {
    public enum PactType { RANDOM, SEALED }
}
