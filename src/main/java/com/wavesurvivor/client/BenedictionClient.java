package com.wavesurvivor.client;

/**
 * Multiplicateur de difficulté des bénédictions côté client (1.7) : envoyé par le serveur avec l'écran de choix et le
 * catalogue, il sert à afficher les valeurs RÉELLES (déjà multipliées) des bonus.
 */
public final class BenedictionClient {

    /** 1 = valeurs de base (Facile, Normal, hors horde). */
    public static volatile double mult = 1.0;

    /** Statistiques du monde (1.8) : identifiant → [proposée, choisie, bannie, parties, victoires], reçues à l'ouverture du catalogue. */
    public static volatile java.util.Map<String, int[]> stats = new java.util.HashMap<>();

    private BenedictionClient() {}
}
