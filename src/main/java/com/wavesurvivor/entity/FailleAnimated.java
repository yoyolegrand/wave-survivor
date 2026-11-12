package com.wavesurvivor.entity;

/**
 * Monstres de la Faille à modèle sur mesure (Géodon, Ancreur, Bolide) : état d'animation lu par le modèle.
 * Purement visuel côté client ; l'entité synchronise l'état et son tick de départ (SynchedEntityData).
 * Les valeurs d'état propres à chaque monstre sont décrites dans son modèle et dans docs/faille/monstres/MONSTRES_FAILLE.md.
 */
public interface FailleAnimated {

    /** État courant (0 = état normal). */
    int failleState();

    /** Avancement 0 → 1 de l'état courant, à partir de {@code ageInTicks} (âge client + fraction de tick). */
    float failleProgress(float ageInTicks);

    /** Bolide uniquement : chaleur 1 (en fusion) → 0 (refroidi). */
    default float failleHeat() {
        return 0f;
    }
}
