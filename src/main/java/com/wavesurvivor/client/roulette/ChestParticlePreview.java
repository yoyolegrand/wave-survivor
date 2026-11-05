package com.wavesurvivor.client.roulette;

import com.wavesurvivor.horde.roulette.CustomChestData;
import com.wavesurvivor.horde.roulette.RouletteParticles;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Aperçu des particules dans l'éditeur (1.9) : les mêmes formes d'émission que dans le monde (ParticlePatterns), mais les particules sont
 * simulées et dessinées en petits carrés dans l'aperçu du coffre (approximation : couleur selon le type, mouvement simple). Rien n'est envoyé au monde.
 * Coordonnées en blocs, le coffre occupe (0..1) ; l'émetteur est à 1,2 au-dessus comme dans le jeu.
 */
@OnlyIn(Dist.CLIENT)
public final class ChestParticlePreview {

    private static final Random RNG = new Random();
    private static final int MAX = 700;

    private static final class P {
        double x, y, z, vx, vy, vz, rise, size;
        int age, life, rgb;
    }

    private final List<P> parts = new ArrayList<>();
    private CustomChestData.Effect fx;
    private int accent;
    private long startMs = -1, lastTick = -1, lastSimMs = -1;
    private int duration;

    /** Lance l'aperçu de l'effet pendant {@code ticks} ticks (20 par seconde). */
    public void play(CustomChestData.Effect effect, int accentRgb, int ticks) {
        this.fx = effect;
        this.accent = accentRgb;
        this.duration = ticks;
        this.startMs = System.currentTimeMillis();
        this.lastTick = -1;
        this.lastSimMs = this.startMs;
        parts.clear();
    }

    /** Avance la simulation d'autant de ticks (50 ms) que le temps écoulé. */
    private void step() {
        if (startMs < 0 && parts.isEmpty()) return;
        long nowMs = System.currentTimeMillis();
        long ticks = Math.min(5, (nowMs - lastSimMs) / 50);
        if (ticks <= 0) return;
        lastSimMs += ticks * 50;
        for (long i = 0; i < ticks; i++) {
            if (startMs >= 0) {
                lastTick++;
                if (lastTick > duration) startMs = -1;
                else RouletteParticles.emit(null, this::add, 0.5, 1.2, 0.5, fx, accent, lastTick);
            }
            for (P p : parts) tickP(p);
            parts.removeIf(p -> p.age >= p.life);
        }
    }

    private void tickP(P p) {
        p.age++;
        p.x += p.vx; p.y += p.vy + p.rise; p.z += p.vz;
        p.vx *= 0.96; p.vy *= 0.96; p.vz *= 0.96;
    }

    private void add(ParticleOptions o, double x, double y, double z, double dx, double dy, double dz, double speed) {
        if (parts.size() >= MAX) return;
        P p = new P();
        p.x = x + RNG.nextGaussian() * dx; p.y = y + RNG.nextGaussian() * dy; p.z = z + RNG.nextGaussian() * dz;
        p.vx = RNG.nextGaussian() * speed; p.vy = RNG.nextGaussian() * speed; p.vz = RNG.nextGaussian() * speed;
        p.life = 14 + RNG.nextInt(14);
        p.size = 1;
        String name = "";
        var key = BuiltInRegistries.PARTICLE_TYPE.getKey(o.getType());
        if (key != null) name = key.getPath();
        if (o instanceof DustParticleOptions d) { p.rgb = rgb(d.getColor()); p.size = d.getScale(); }
        else if (o instanceof DustColorTransitionOptions d) { p.rgb = rgb(d.getFromColor()); p.size = d.getScale(); }
        else p.rgb = colorFor(name);
        p.rise = name.contains("flame") || name.contains("smoke") || name.contains("bubble") || name.contains("ash") || name.contains("soul") || name.contains("lava") ? 0.012 : 0;
        if (name.contains("enchant") || name.contains("portal")) p.life += 8;
        parts.add(p);
    }

    private static int rgb(Vector3f c) { return (Math.round(c.x * 255) << 16) | (Math.round(c.y * 255) << 8) | Math.round(c.z * 255); }

    private static int colorFor(String n) {
        if (n.contains("soul")) return 0x5FD7E8;
        if (n.contains("flame") || n.contains("lava") || n.contains("fire")) return 0xFFA030;
        if (n.contains("enchant") || n.contains("portal") || n.contains("witch")) return 0xB070FF;
        if (n.contains("bubble") || n.contains("splash") || n.contains("water") || n.contains("drip")) return 0x8FD0FF;
        if (n.contains("heart")) return 0xFF4060;
        if (n.contains("happy") || n.contains("spore")) return 0x50E070;
        if (n.contains("angry")) return 0xC02020;
        if (n.contains("smoke") || n.contains("ash") || n.contains("cloud") || n.contains("poof")) return 0xA8A8A8;
        if (n.contains("ink")) return 0x303060;
        if (n.contains("crit") || n.contains("note") || n.contains("totem")) return 0xE6C85A;
        if (n.contains("firework") || n.contains("end_rod") || n.contains("flash") || n.contains("glow")) return 0xFFF0B0;
        return 0xE8E8FF;
    }

    /** Avance la simulation et dessine, avec la même projection que {@link ChestPreview#draw} (yaw en degrés, taille en pixels). */
    public void draw(GuiGraphics g, int cx, int cy, float size, float yaw) {
        step();
        if (parts.isEmpty()) return;
        float s = size * 0.78f;
        double ry = Math.toRadians(180 + yaw), rx = Math.toRadians(22);
        double cry = Math.cos(ry), sry = Math.sin(ry), crx = Math.cos(rx), srx = Math.sin(rx);
        for (P p : parts) {
            double x = p.x - 0.5, y = p.y - 0.45, z = p.z - 0.5;
            double x1 = x * cry + z * sry, z1 = -x * sry + z * cry; // rotation autour de Y
            double y2 = y * crx - z1 * srx;                          // inclinaison autour de X
            int px = Math.round((float) (cx + x1 * s)), py = Math.round((float) (cy - y2 * s));
            float f = 1f - p.age / (float) p.life;
            int a = Math.max(0, Math.min(255, (int) (255 * Math.min(1f, f * 1.6f))));
            int sz = Math.max(2, Math.round((float) p.size * 2.5f));
            g.fill(px, py, px + sz, py + sz, (a << 24) | p.rgb);
        }
    }
}
