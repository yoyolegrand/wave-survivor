package com.wavesurvivor.altar;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.joml.Vector3f;

/**
 * Préréglages de particules du monolithe (menu Custom → onglet Particules, style par horde).
 * Uniquement des particules à durée de vie courte (pas de bubble_column_up, end_rod, dragon_breath...).
 *
 *   ring   : cercle tournant de 3 particules au-dessus de l'autel (autel lié)
 *   column : particule qui monte au centre, toutes les 6 ticks (autel lié)
 *   Autel vierge : 1 particule "ring" toutes les 10 ticks (présence discrète)
 */
public enum AltarParticles {

    AMES("ames", "Âmes", Items.SOUL_LANTERN),
    FLAMMES("flammes", "Flammes", Items.CAMPFIRE),
    ABYSSES("abysses", "Abysses", Items.NAUTILUS_SHELL),
    BRAISES("braises", "Braises", Items.MAGMA_CREAM),
    ENCHANT("enchant", "Enchantement", Items.ENCHANTED_BOOK),
    ETINCELLES("etincelles", "Étincelles", Items.LIGHTNING_ROD),
    PORTAIL("portail", "Portail", Items.ENDER_PEARL),
    LUEUR("lueur", "Lueur", Items.GLOW_INK_SAC),
    FEUX("feux", "Feux d'artifice", Items.FIREWORK_ROCKET),
    POUSSIERE("poussiere", "Poussière teintée", Items.REDSTONE),
    AUCUNE("aucune", "Aucune", Items.BARRIER);

    public final String id;
    public final String nameFr;
    public final Item icon;

    AltarParticles(String id, String nameFr, Item icon) {
        this.id = id;
        this.nameFr = nameFr;
        this.icon = icon;
    }

    public static AltarParticles byId(String id) {
        if (id != null) for (AltarParticles p : values()) if (p.id.equalsIgnoreCase(id)) return p;
        return AMES;
    }

    public static boolean exists(String id) {
        if (id == null) return false;
        for (AltarParticles p : values()) if (p.id.equalsIgnoreCase(id)) return true;
        return false;
    }

    // ─── Rendu serveur ───

    private ParticleOptions ring(DyeColor color) {
        return switch (this) {
            case AMES -> ParticleTypes.SOUL_FIRE_FLAME;
            case FLAMMES -> ParticleTypes.FLAME;
            case ABYSSES -> ParticleTypes.NAUTILUS;
            case BRAISES -> ParticleTypes.SMALL_FLAME;
            case ENCHANT -> ParticleTypes.ENCHANT;
            case ETINCELLES -> ParticleTypes.ELECTRIC_SPARK;
            case PORTAIL -> ParticleTypes.PORTAL;
            case LUEUR -> ParticleTypes.GLOW;
            case FEUX -> ParticleTypes.FIREWORK;
            case POUSSIERE -> dust(color);
            case AUCUNE -> null;
        };
    }

    private ParticleOptions column(DyeColor color) {
        return switch (this) {
            case AMES -> ParticleTypes.FLAME;
            case FLAMMES -> ParticleTypes.SMOKE;
            case ABYSSES -> ParticleTypes.SPLASH;
            case BRAISES -> ParticleTypes.LAVA;
            case ENCHANT -> ParticleTypes.ENCHANTED_HIT;
            case ETINCELLES -> ParticleTypes.ELECTRIC_SPARK;
            case PORTAIL -> ParticleTypes.REVERSE_PORTAL;
            case LUEUR -> ParticleTypes.GLOW;
            case FEUX -> ParticleTypes.FIREWORK;
            case POUSSIERE -> dust(color);
            case AUCUNE -> null;
        };
    }

    private static ParticleOptions dust(DyeColor color) {
        int rgb = AltarColors.rgb(color != null ? color : DyeColor.RED);
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255f,
                ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f), 1.0f);
    }

    /** Appelé toutes les 2 ticks par AltarTickHandler. */
    public void spawn(ServerLevel level, double cx, double baseY, double cz, long now, boolean bound, DyeColor color) {
        ParticleOptions ring = ring(color);
        if (ring == null) return;

        if (!bound) {
            if (now % 10 == 0) level.sendParticles(ring, cx, baseY + 0.5, cz, 1, 0.3, 0.2, 0.3, 0.01);
            return;
        }

        // Cercle tournant de 3 particules
        double angleOffset = (now % 60) * (Math.PI * 2 / 60);
        for (int i = 0; i < 3; i++) {
            double a = (Math.PI * 2 / 3) * i + angleOffset;
            level.sendParticles(ring, cx + Math.cos(a) * 0.7, baseY + 0.2, cz + Math.sin(a) * 0.7,
                    1, 0, 0, 0, 0.005);
        }
        // Colonne montante
        if (now % 6 == 0) {
            ParticleOptions col = column(color);
            if (col != null) level.sendParticles(col, cx, baseY + 0.3, cz, 1, 0.05, 0.4, 0.05, 0.02);
        }
    }
}
