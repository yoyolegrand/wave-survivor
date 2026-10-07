package com.wavesurvivor.horde.skill;

import com.wavesurvivor.WaveSurvivorMod;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Suit les projectiles avec traînée de particules et spawn les particules chaque tick.
 * Register par SkillProjectilePotion quand config.trailParticle est set.
 * Tick par HordeTickHandler chaque tick serveur.
 * Purge auto quand le projectile est mort ou disparu.
 */
public class ProjectileTrailTracker {

    private static final Map<UUID, TrailInfo> ACTIVE = new HashMap<>();

    private record TrailInfo(String particleType, int count) {}

    /** Register un projectile à suivre. Appelé après spawn de l'arrow. */
    public static void register(UUID projectileUuid, String particleType, int count) {
        if (particleType == null || particleType.isBlank()) return;
        ACTIVE.put(projectileUuid, new TrailInfo(particleType, Math.max(1, count)));
    }

    /** Tick tous les projectiles trackés : spawn particules à leur position, purge morts. */
    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        var it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            Entity ent = findEntity(server, e.getKey());
            if (ent == null || !ent.isAlive() || ent.isRemoved()) {
                it.remove();
                continue;
            }
            ServerLevel level = (ServerLevel) ent.level();
            TrailInfo info = e.getValue();
            ParticleOptions particle = resolveParticle(info.particleType);
            if (particle == null) continue;

            Vec3 pos = ent.position();
            // Spawn count particules à la position actuelle avec petite dispersion
            level.sendParticles(particle,
                    pos.x, pos.y + 0.2, pos.z,
                    info.count,
                    0.05, 0.05, 0.05, // dispersion x/y/z
                    0.0); // pas de vitesse (particules statiques qui suivent)
        }
    }

    /** Clear tous les trackers (fin de horde). */
    public static void clearAll() {
        ACTIVE.clear();
    }

    /** Résout une particle option depuis son id "minecraft:effect" par ex. */
    private static ParticleOptions resolveParticle(String id) {
        try {
            var particleType = BuiltInRegistries.PARTICLE_TYPE.get(new ResourceLocation(id));
            if (particleType instanceof ParticleOptions po) return po;
            // Fallback : particules basiques comme "minecraft:effect" retournent souvent un SimpleParticleType
            if (particleType != null) {
                // SimpleParticleType implémente ParticleOptions via cast
                return (ParticleOptions) particleType;
            }
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.warn("[ProjectileTrail] Particule invalide '{}': {}", id, e.getMessage());
        }
        return ParticleTypes.EFFECT; // fallback safe
    }

    private static Entity findEntity(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(uuid);
            if (e != null) return e;
        }
        return null;
    }

    public static int trackedCount() {
        return ACTIVE.size();
    }
}
