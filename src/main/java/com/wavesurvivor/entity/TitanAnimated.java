package com.wavesurvivor.entity;

/**
 * 2.0 — Titan à modèle sur mesure (Dévoreur, puis Colosse et Archonte) : la pose de compétence que le modèle doit jouer.
 * Purement visuel : les compétences du Titan ne dépendent JAMAIS de ces poses (n'importe quelle entité peut être un Titan).
 * L'entité synchronise la pose et son tick de départ (SynchedEntityData) ; RiftTitan la déclenche au début de l'annonce
 * d'une compétence si le Titan implémente cette interface.
 */
public interface TitanAnimated {

    enum Pose { IDLE, ROAR, BEAM, SLAM, THROW, SUMMON, HURT, CHARGE, STUNNED }

    /** Pose en cours (IDLE s'il n'y en a pas). */
    Pose titanPose();

    /** Avancement de la pose, de 0 à 1, calculé côté client à partir de {@code ageInTicks} (âge + fraction de tick). */
    float titanPoseProgress(float ageInTicks);
}
