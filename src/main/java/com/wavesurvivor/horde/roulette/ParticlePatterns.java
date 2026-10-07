package com.wavesurvivor.horde.roulette;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;

import java.util.Random;

/**
 * Patterns d'émission de particules pour les roulette chests custom.
 * Chaque pattern décide combien et où émettre à un tick donné.
 *
 * Ces patterns sont indépendants du TYPE de particule (flame, enchant, bubble, etc.) :
 * le type est passé en argument. Ça permet de combiner N particules × 19 patterns.
 *
 * Convention :
 *   - (cx, cy, cz) est le centre du coffre, cy déjà offset (=  blockY + 1.2)
 *   - tick est le tick serveur (utilisé pour animer)
 *   - Les patterns émettent 1-6 particules par tick pour éviter le lag
 */
public class ParticlePatterns {

    private static final Random RNG = new Random();

    /** Liste des IDs de patterns disponibles (utilisée par le GUI pour cycler). */
    public static final String[] ALL = {
            "circle", "square", "triangle", "pentagram", "hexagram", "cross",
            "double_ring", "spiral_up", "double_helix", "vortex_down", "cone_up",
            "portal_vertical", "shockwave", "firework", "aura_pulse", "beam",
            "ashes", "fireflies", "cloud"
    };

    /** Dispatch principal. */
    public static void emit(String pattern, ServerLevel level, double cx, double cy, double cz,
                            ParticleOptions particle, long tick) {
        if (pattern == null || pattern.isBlank()) pattern = "circle";
        switch (pattern) {
            case "circle"           -> circle(level, cx, cy, cz, particle, tick);
            case "square"           -> squareRotating(level, cx, cy, cz, particle, tick);
            case "triangle"         -> polygon(level, cx, cy, cz, particle, tick, 3);
            case "pentagram"        -> polygon(level, cx, cy, cz, particle, tick, 5);
            case "hexagram"         -> hexagram(level, cx, cy, cz, particle, tick);
            case "cross"            -> cross(level, cx, cy, cz, particle, tick);
            case "double_ring"      -> doubleRing(level, cx, cy, cz, particle, tick);
            case "spiral_up"        -> spiralUp(level, cx, cy, cz, particle, tick);
            case "double_helix"     -> doubleHelix(level, cx, cy, cz, particle, tick);
            case "vortex_down"      -> vortexDown(level, cx, cy, cz, particle, tick);
            case "cone_up"          -> coneUp(level, cx, cy, cz, particle, tick);
            case "portal_vertical"  -> portalVertical(level, cx, cy, cz, particle, tick);
            case "shockwave"        -> shockwave(level, cx, cy, cz, particle, tick);
            case "firework"         -> fireworkBurst(level, cx, cy, cz, particle, tick);
            case "aura_pulse"       -> auraPulse(level, cx, cy, cz, particle, tick);
            case "beam"             -> beam(level, cx, cy, cz, particle, tick);
            case "ashes"            -> ashesUp(level, cx, cy, cz, particle, tick);
            case "fireflies"        -> fireflies(level, cx, cy, cz, particle, tick);
            case "cloud"            -> cloud(level, cx, cy, cz, particle, tick);
            default                 -> circle(level, cx, cy, cz, particle, tick);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  GÉOMÉTRIQUES STATIQUES / ROTATIFS
    // ═══════════════════════════════════════════════════════════════════

    /** Cercle : 3 particules en rotation lente au-dessus du coffre. Comportement actuel. */
    private static void circle(ServerLevel level, double cx, double cy, double cz, ParticleOptions p, long tick) {
        double angleStep = Math.PI * 2 / 3;
        double offset = (tick % 80) * (Math.PI * 2 / 80);
        for (int i = 0; i < 3; i++) {
            double a = angleStep * i + offset;
            spawn(level, p, cx + Math.cos(a) * 0.6, cy, cz + Math.sin(a) * 0.6);
        }
        // Petite colonne verticale douce
        if (tick % 4 == 0) spawn(level, p, cx, cy + 0.3, cz, 0.1, 0.5, 0.1, 0.02);
    }

    /** Carré tournant : 4 particules aux coins avec pulsation de taille. */
    private static void squareRotating(ServerLevel level, double cx, double cy, double cz, ParticleOptions p, long tick) {
        double offset = (tick % 100) * (Math.PI * 2 / 100);
        double pulse = 0.55 + Math.sin(tick * 0.1) * 0.15;
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 2 * i + offset;
            spawn(level, p, cx + Math.cos(a) * pulse, cy, cz + Math.sin(a) * pulse);
        }
    }

    /** Polygone régulier tournant : triangle (n=3), pentagon (n=5). */
    private static void polygon(ServerLevel level, double cx, double cy, double cz, ParticleOptions p, long tick, int n) {
        double offset = (tick % 120) * (Math.PI * 2 / 120);
        double r = 0.7;
        for (int i = 0; i < n; i++) {
            double a = (Math.PI * 2 / n) * i + offset;
            spawn(level, p, cx + Math.cos(a) * r, cy, cz + Math.sin(a) * r);
            // Trace les côtés : ajoute des points intermédiaires
            if (tick % 3 == 0) {
                double aNext = (Math.PI * 2 / n) * ((i + 1) % n) + offset;
                double midX = (Math.cos(a) + Math.cos(aNext)) * 0.5 * r;
                double midZ = (Math.sin(a) + Math.sin(aNext)) * 0.5 * r;
                spawn(level, p, cx + midX, cy, cz + midZ);
            }
        }
    }

    /** Hexagramme : 2 triangles opposés (étoile à 6 branches), tourne. */
    private static void hexagram(ServerLevel level, double cx, double cy, double cz, ParticleOptions p, long tick) {
        double offset = (tick % 100) * (Math.PI * 2 / 100);
        double r = 0.7;
        // Triangle 1
        for (int i = 0; i < 3; i++) {
            double a = (Math.PI * 2 / 3) * i + offset;
            spawn(level, p, cx + Math.cos(a) * r, cy, cz + Math.sin(a) * r);
        }
        // Triangle 2 (offset de PI/3 = pointe opposée)
        for (int i = 0; i < 3; i++) {
            double a = (Math.PI * 2 / 3) * i + offset + Math.PI / 3;
            spawn(level, p, cx + Math.cos(a) * r, cy, cz + Math.sin(a) * r);
        }
    }

    /** Croix runique : 4 branches perpendiculaires + petit centre. */
    private static void cross(ServerLevel level, double cx, double cy, double cz, ParticleOptions p, long tick) {
        double offset = (tick % 200) * (Math.PI * 2 / 200); // rotation TRÈS lente
        for (double d = 0.2; d <= 0.8; d += 0.2) {
            for (int i = 0; i < 4; i++) {
                double a = Math.PI / 2 * i + offset;
                spawn(level, p, cx + Math.cos(a) * d, cy, cz + Math.sin(a) * d);
            }
        }
        if (tick % 8 == 0) spawn(level, p, cx, cy, cz); // centre
    }

    /** Double anneau : 2 cercles à des hauteurs différentes tournant en sens opposés. */
    private static void doubleRing(ServerLevel level, double cx, double cy, double cz, ParticleOptions p, long tick) {
        double a1 = (tick % 60) * (Math.PI * 2 / 60);
        double a2 = -a1 * 1.3;
        double r = 0.65;
        for (int i = 0; i < 4; i++) {
            double angle1 = (Math.PI / 2) * i + a1;
            spawn(level, p, cx + Math.cos(angle1) * r, cy - 0.2, cz + Math.sin(angle1) * r);
            double angle2 = (Math.PI / 2) * i + a2;
            spawn(level, p, cx + Math.cos(angle2) * r, cy + 0.4, cz + Math.sin(angle2) * r);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  VERTICAUX / SPATIAUX
    // ═══════════════════════════════════════════════════════════════════

    /** Spirale ascendante : particule qui monte + tourne, laisse une trace en spirale. */
    private static void spiralUp(ServerLevel level, double cx, double cy, double cz, ParticleOptions p, long tick) {
        // Émet 2 particules par tick à des heights différentes pour densifier
        for (int off = 0; off < 2; off++) {
            long t = tick + off * 20;
            double h = ((t % 60) / 60.0) * 2.5;   // monte de 0 à 2.5 blocs
            double a = t * 0.3;
            spawn(level, p, cx + Math.cos(a) * 0.6, cy + h, cz + Math.sin(a) * 0.6);
        }
    }

    /** Double hélice (DNA) : 2 spirales entrelacées. */
    private static void doubleHelix(ServerLevel level, double cx, double cy, double cz, ParticleOptions p, long tick) {
        for (int off = 0; off < 2; off++) {
            long t = tick + off * 15;
            double h = ((t % 60) / 60.0) * 2.5;
            double a = t * 0.3;
            spawn(level, p, cx + Math.cos(a) * 0.5, cy + h, cz + Math.sin(a) * 0.5);
            spawn(level, p, cx - Math.cos(a) * 0.5, cy + h, cz - Math.sin(a) * 0.5);
        }
    }

    /** Vortex descendant : particules qui convergent d'un cercle large vers un point haut. */
    private static void vortexDown(ServerLevel level, double cx, double cy, double cz, ParticleOptions p, long tick) {
        // 3 particules à hauteurs différentes qui convergent
        for (int i = 0; i < 3; i++) {
            long t = tick + i * 20;
            double progress = (t % 60) / 60.0;    // 0 → 1
            double h = 2.5 - progress * 2.3;      // descend de 2.5 à 0.2
            double r = 0.15 + progress * 0.7;     // le rayon INVERSE : petit en haut, large en bas
            r = 0.85 - progress * 0.7;            // s'inverse : large en haut, converge en bas
            double a = t * 0.4;
            spawn(level, p, cx + Math.cos(a) * r, cy + h, cz + Math.sin(a) * r);
        }
    }

    /** Cône inversé (pyramide de particules pointe en haut). */
    private static void coneUp(ServerLevel level, double cx, double cy, double cz, ParticleOptions p, long tick) {
        // 4 particules à des hauteurs random, rayon plus petit en haut
        for (int i = 0; i < 4; i++) {
            double h = RNG.nextDouble() * 2.5;
            double r = 0.9 * (1.0 - h / 2.5);
            double a = RNG.nextDouble() * Math.PI * 2;
            spawn(level, p, cx + Math.cos(a) * r, cy + h, cz + Math.sin(a) * r);
        }
    }

    /** Portail vertical : anneau qui tourne sur un plan vertical. */
    private static void portalVertical(ServerLevel level, double cx, double cy, double cz, ParticleOptions p, long tick) {
        double offset = (tick % 80) * (Math.PI * 2 / 80);
        double r = 0.7;
        // 6 particules sur l'anneau vertical (plan X-Y)
        for (int i = 0; i < 6; i++) {
            double a = (Math.PI * 2 / 6) * i + offset;
            spawn(level, p, cx + Math.cos(a) * r, cy + 0.7 + Math.sin(a) * r, cz);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  EXPLOSIFS / PULSATILES
    // ═══════════════════════════════════════════════════════════════════

    /** Onde de choc : cercle qui grandit puis disparait, cycle toutes les 2s. */
    private static void shockwave(ServerLevel level, double cx, double cy, double cz, ParticleOptions p, long tick) {
        long period = 40;
        long t = tick % period;
        double progress = t / (double) period;  // 0 → 1
        double r = progress * 1.2;              // rayon grandit
        int count = 8;
        for (int i = 0; i < count; i++) {
            double a = (Math.PI * 2 / count) * i;
            spawn(level, p, cx + Math.cos(a) * r, cy - 0.5, cz + Math.sin(a) * r);
        }
    }

    /** Firework burst : mini explosion sphérique toutes les 3-5s. */
    private static void fireworkBurst(ServerLevel level, double cx, double cy, double cz, ParticleOptions p, long tick) {
        // Explosion toutes les 80 ticks (4s)
        if (tick % 80 == 0) {
            for (int i = 0; i < 20; i++) {
                double a = RNG.nextDouble() * Math.PI * 2;
                double h = RNG.nextDouble() * Math.PI - Math.PI / 2;
                double r = 0.9;
                double dx = Math.cos(a) * Math.cos(h) * r;
                double dy = Math.sin(h) * r;
                double dz = Math.sin(a) * Math.cos(h) * r;
                spawn(level, p, cx + dx, cy + 0.7 + dy, cz + dz, 0, 0, 0, 0.08);
            }
        }
    }

    /** Aura pulsante : halo qui gonfle et dégonfle. */
    private static void auraPulse(ServerLevel level, double cx, double cy, double cz, ParticleOptions p, long tick) {
        double r = 0.55 + Math.sin(tick * 0.1) * 0.35;
        int count = 6;
        double offset = (tick % 100) * (Math.PI * 2 / 100);
        for (int i = 0; i < count; i++) {
            double a = (Math.PI * 2 / count) * i + offset;
            spawn(level, p, cx + Math.cos(a) * r, cy, cz + Math.sin(a) * r);
        }
    }

    /** Rayon de lumière : pilier vertical fin de particules qui monte. */
    private static void beam(ServerLevel level, double cx, double cy, double cz, ParticleOptions p, long tick) {
        // 3 particules à différentes hauteurs pour former une colonne dense
        for (double h = 0.0; h < 3.0; h += 0.5) {
            double jitter = (RNG.nextDouble() - 0.5) * 0.15;
            spawn(level, p, cx + jitter, cy + h, cz + jitter);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  ATMOSPHÉRIQUES / PASSIFS
    // ═══════════════════════════════════════════════════════════════════

    /** Cendres montantes : 2-3 particules qui dérivent lentement vers le haut. */
    private static void ashesUp(ServerLevel level, double cx, double cy, double cz, ParticleOptions p, long tick) {
        for (int i = 0; i < 2; i++) {
            double dx = (RNG.nextDouble() - 0.5) * 0.8;
            double dz = (RNG.nextDouble() - 0.5) * 0.8;
            double dy = 0.5 + RNG.nextDouble() * 1.5;
            spawn(level, p, cx + dx, cy + dy, cz + dz, 0, 0.08, 0, 0.01);
        }
    }

    /** Firefly cluster : 4-5 particules qui volent chaotiquement dans une bulle. */
    private static void fireflies(ServerLevel level, double cx, double cy, double cz, ParticleOptions p, long tick) {
        // Chaque particule suit sa propre trajectoire chaotique basée sur tick + phase
        for (int i = 0; i < 5; i++) {
            double phase = i * (Math.PI * 2 / 5);
            double dx = Math.sin(tick * 0.05 + phase) * 0.7 + Math.cos(tick * 0.03 + phase * 2) * 0.3;
            double dz = Math.cos(tick * 0.05 + phase) * 0.7 + Math.sin(tick * 0.03 + phase * 2) * 0.3;
            double dy = 0.3 + Math.sin(tick * 0.08 + phase) * 0.4;
            if (tick % 2 == i % 2) {  // stagger les émissions
                spawn(level, p, cx + dx, cy + dy, cz + dz);
            }
        }
    }

    /** Nuage stratifié : voile horizontal de particules qui dérive lentement. */
    private static void cloud(ServerLevel level, double cx, double cy, double cz, ParticleOptions p, long tick) {
        // Nuage à hauteur fixe, dispersion large horizontale
        double driftX = Math.sin(tick * 0.02) * 0.3;
        double driftZ = Math.cos(tick * 0.02) * 0.3;
        for (int i = 0; i < 4; i++) {
            double dx = (RNG.nextDouble() - 0.5) * 1.4 + driftX;
            double dz = (RNG.nextDouble() - 0.5) * 1.4 + driftZ;
            spawn(level, p, cx + dx, cy + 0.9, cz + dz, 0.05, 0, 0.05, 0.01);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════════

    private static void spawn(ServerLevel level, ParticleOptions p, double x, double y, double z) {
        level.sendParticles(p, x, y, z, 1, 0, 0, 0, 0.01);
    }

    private static void spawn(ServerLevel level, ParticleOptions p, double x, double y, double z,
                              double dx, double dy, double dz, double speed) {
        level.sendParticles(p, x, y, z, 1, dx, dy, dz, speed);
    }
}
