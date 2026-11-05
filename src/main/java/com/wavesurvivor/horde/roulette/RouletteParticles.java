package com.wavesurvivor.horde.roulette;

import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * EFFETS DE PARTICULES DES COFFRES ROULETTE (1.9) : un effet = type A, type B optionnel (émis en alternance), forme d'émission (les 19 de
 * {@link ParticlePatterns}), densité (1 à 10), rayon et hauteur (multiplicateurs de la forme), vitesse (1 à 10), couleur(s) et taille pour les
 * poussières colorées (« dust », « dust_color_transition »). Trois moments : au repos, pendant la roulette, à la révélation.
 */
public final class RouletteParticles {

    private RouletteParticles() {}

    /** L'effet émet-il quelque chose ? */
    public static boolean valid(CustomChestData.Effect fx) {
        return fx != null && fx.a != null && !fx.a.isBlank() && !"none".equalsIgnoreCase(fx.a);
    }

    /** Émet une fois (à appeler à chaque tick) l'effet autour de (cx, cy, cz). {@code accent} : couleur des poussières sans couleur choisie. */
    public static void emit(ServerLevel level, double cx, double cy, double cz, CustomChestData.Effect fx, int accent, long tick) {
        if (!valid(fx)) return;
        ParticleOptions a = option(fx.a, fx, accent);
        ParticleOptions b = fx.b != null && !fx.b.isBlank() && !"none".equalsIgnoreCase(fx.b) ? option(fx.b, fx, accent) : null;
        double density = Math.max(1, Math.min(10, fx.density)) / 5.0;
        if (isHeavy(a)) density /= 3.0; // particules volumineuses : beaucoup moins nombreuses
        double speed = Math.max(1, Math.min(10, fx.speed));
        ParticlePatterns.Ctx ctx = new ParticlePatterns.Ctx(cx, cy, cz, clamp(fx.radius, 0.2, 4.0), clamp(fx.height, 0.2, 4.0), density, 0.5 + speed / 10.0, a, b);
        ParticlePatterns.emit(fx.pattern == null || fx.pattern.isBlank() ? "circle" : fx.pattern, level, tick, speed, ctx);
    }

    /** Répète l'effet pendant {@code ticks} ticks (révélation). */
    public static void burst(ServerLevel level, double cx, double cy, double cz, CustomChestData.Effect fx, int accent, int ticks) {
        if (!valid(fx)) return;
        MinecraftServer server = level.getServer();
        for (int i = 0; i < ticks; i++) {
            final long t = i;
            com.wavesurvivor.horde.skill.DelayedActionScheduler.schedule(server, i, () -> emit(level, cx, cy, cz, fx, accent, t), "roulette reveal fx");
        }
    }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    /** Type de particule : poussières colorées, ou type vanilla simple par son nom (repli : étincelle enchantée). */
    public static ParticleOptions option(String id, CustomChestData.Effect fx, int accent) {
        String t = id.trim().toLowerCase(java.util.Locale.ROOT);
        float size = (float) clamp(fx.size, 0.5, 3.0);
        if (t.equals("dust")) return new DustParticleOptions(rgb(RouletteColors.parseHex(fx.color, accent)), size);
        if (t.equals("dust_color_transition")) {
            int c1 = RouletteColors.parseHex(fx.color, accent);
            return new DustColorTransitionOptions(rgb(c1), rgb(RouletteColors.parseHex(fx.color2, c1)), size);
        }
        try {
            Object p = BuiltInRegistries.PARTICLE_TYPE.get(new ResourceLocation(t.contains(":") ? t : "minecraft:" + t));
            if (p instanceof ParticleOptions po) return po;
        } catch (Exception ignored) { /* nom invalide : repli */ }
        return ParticleTypes.ENCHANTED_HIT;
    }

    private static Vector3f rgb(int c) { return new Vector3f(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f); }

    /** Particules grosses ou opaques qui saturent vite l'écran. */
    private static boolean isHeavy(ParticleOptions p) {
        var t = p.getType();
        return t == ParticleTypes.ANGRY_VILLAGER || t == ParticleTypes.CLOUD || t == ParticleTypes.LARGE_SMOKE
                || t == ParticleTypes.CAMPFIRE_COSY_SMOKE || t == ParticleTypes.CAMPFIRE_SIGNAL_SMOKE
                || t == ParticleTypes.EXPLOSION || t == ParticleTypes.POOF || t == ParticleTypes.HEART
                || t == ParticleTypes.SQUID_INK || t == ParticleTypes.SNEEZE;
    }

    /** Position (monde) du médaillon avant d'un coffre tourné vers {@code facing} (pour les étincelles de la roulette). */
    public static Vec3 frontMedallion(net.minecraft.core.BlockPos pos, net.minecraft.core.Direction facing) {
        return new Vec3(pos.getX() + 0.5 + facing.getStepX() * 0.47, pos.getY() + 0.32, pos.getZ() + 0.5 + facing.getStepZ() * 0.47);
    }
}
