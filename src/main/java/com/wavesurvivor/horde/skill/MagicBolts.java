package com.wavesurvivor.horde.skill;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * ÉCLATS D'ÂME — projectile magique en ligne droite (gatlingProjectileType = "wavesurvivor:magic_bolt").
 * Simulé côté serveur (pas d'entité) : avance de `speed` blocs par tick, sans gravité, laisse une traînée
 * de particules (poussière vert spectral + flammes d'âme), s'arrête sur un bloc ou sur la première cible.
 * Dégâts = attaque du lanceur, type "attaque de mob" → l'ARMURE S'APPLIQUE normalement.
 */
public class MagicBolts {

    private static final int MAX_LIFE = 40; // ticks (~48 blocs à 1.2 b/t)
    private static final DustParticleOptions CORE = new DustParticleOptions(new Vector3f(0.45f, 1.0f, 0.35f), 1.3f);

    private static class Bolt {
        final ServerLevel level;
        final LivingEntity shooter;
        Vec3 pos;
        final Vec3 vel;
        final float damage;
        int life = MAX_LIFE;
        Bolt(ServerLevel level, LivingEntity shooter, Vec3 pos, Vec3 vel, float damage) {
            this.level = level; this.shooter = shooter; this.pos = pos; this.vel = vel; this.damage = damage;
        }
    }

    private static final List<Bolt> BOLTS = new ArrayList<>();

    public static void spawn(ServerLevel level, LivingEntity shooter, Vec3 from, Vec3 dir, double speed) {
        AttributeInstance atk = shooter.getAttribute(Attributes.ATTACK_DAMAGE);
        float dmg = atk != null ? (float) Math.max(1.0, atk.getValue()) : 4f;
        BOLTS.add(new Bolt(level, shooter, from, dir.normalize().scale(Math.max(0.3, speed)), dmg));
        level.playSound(null, shooter.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 0.8f, 1.4f);
        level.sendParticles(ParticleTypes.SCULK_SOUL, from.x, from.y, from.z, 4, 0.15, 0.15, 0.15, 0.02);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || BOLTS.isEmpty()) return;
        Iterator<Bolt> it = BOLTS.iterator();
        while (it.hasNext()) {
            Bolt b = it.next();
            if (!b.shooter.isAlive() || b.life-- <= 0) { it.remove(); continue; }
            Vec3 next = b.pos.add(b.vel);

            // 1) Bloc sur la trajectoire ?
            BlockHitResult bh = b.level.clip(new ClipContext(b.pos, next, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, b.shooter));
            Vec3 end = bh.getType() == HitResult.Type.MISS ? next : bh.getLocation();

            // 2) Cible sur le segment (joueurs + créatures non hostiles, jamais les monstres de la horde)
            AABB box = new AABB(b.pos, end).inflate(0.5);
            Entity hit = null;
            double best = Double.MAX_VALUE;
            for (Entity e : b.level.getEntities(b.shooter, box, e -> e instanceof LivingEntity le && le.isAlive()
                    && !(e instanceof Enemy)
                    && !(e instanceof Player p && (p.isCreative() || p.isSpectator())))) {
                if (e.getBoundingBox().inflate(0.3).clip(b.pos, end).isEmpty()) continue;
                double d = e.distanceToSqr(b.pos);
                if (d < best) { best = d; hit = e; }
            }

            // Traînée
            trail(b.level, b.pos, end);

            if (hit != null) {
                hit.hurt(b.level.damageSources().mobAttack(b.shooter), b.damage); // armure appliquée
                impact(b.level, hit.position().add(0, hit.getBbHeight() * 0.6, 0));
                it.remove();
                continue;
            }
            if (bh.getType() != HitResult.Type.MISS) {
                impact(b.level, end);
                it.remove();
                continue;
            }
            b.pos = next;
        }
    }

    private static void trail(ServerLevel level, Vec3 a, Vec3 c) {
        Vec3 d = c.subtract(a);
        int steps = Math.max(2, (int) (d.length() * 3));
        for (int i = 0; i < steps; i++) {
            Vec3 p = a.add(d.scale((double) i / steps));
            level.sendParticles(CORE, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, c.x, c.y, c.z, 1, 0.03, 0.03, 0.03, 0.005);
    }

    private static void impact(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.SCULK_SOUL, at.x, at.y, at.z, 10, 0.25, 0.25, 0.25, 0.04);
        level.sendParticles(CORE, at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0);
        level.playSound(null, net.minecraft.core.BlockPos.containing(at), SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 1.0f, 1.2f);
    }

    public static void clearAll() {
        BOLTS.clear();
    }
}
