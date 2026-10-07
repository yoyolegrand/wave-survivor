package com.wavesurvivor.horde.skill;

import com.wavesurvivor.WaveSurvivorMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Orchestrateur des rafales gatling.
 * Chaque appel à schedule() enregistre une rafale ; le tick() serveur tire un projectile
 * toutes les shotInterval ticks jusqu'à épuisement du bulletCount.
 *
 * Ticked depuis HordeTickHandler.onServerTick.
 */
public class GatlingScheduler {

    private static final Random RNG = new Random();
    private static final List<Burst> BURSTS = new ArrayList<>();

    public static class Burst {
        public final UUID shooterUuid;
        public final UUID targetUuid;
        public final String projectileType;
        public final double speed;
        public final double dispersion;
        public final int shotInterval;
        public int shotsRemaining;
        public long nextShotTick;

        public Burst(UUID shooter, UUID target, String projType, double speed, double dispersion,
                     int interval, int totalShots, long firstShotTick) {
            this.shooterUuid = shooter;
            this.targetUuid = target;
            this.projectileType = projType;
            this.speed = speed;
            this.dispersion = dispersion;
            this.shotInterval = Math.max(1, interval);
            this.shotsRemaining = totalShots;
            this.nextShotTick = firstShotTick;
        }
    }

    public static void schedule(LivingEntity shooter, LivingEntity target, String projType,
                                 double speed, double dispersion, int interval, int totalShots, long now) {
        if (totalShots <= 0) return;
        BURSTS.add(new Burst(shooter.getUUID(), target.getUUID(),
                projType != null ? projType : "minecraft:small_fireball",
                speed, dispersion, interval, totalShots, now));
    }

    public static void tick(MinecraftServer server) {
        if (BURSTS.isEmpty()) return;
        long now = server.getTickCount();

        var it = BURSTS.iterator();
        while (it.hasNext()) {
            Burst b = it.next();
            if (b.shotsRemaining <= 0) { it.remove(); continue; }
            if (now < b.nextShotTick) continue;

            LivingEntity shooter = findLiving(server, b.shooterUuid);
            LivingEntity target = findLiving(server, b.targetUuid);
            if (shooter == null || !shooter.isAlive()) { it.remove(); continue; }
            if (target == null || !target.isAlive()) { it.remove(); continue; }

            fireOneShot(shooter, target, b);
            b.shotsRemaining--;
            b.nextShotTick = now + b.shotInterval;
        }
    }

    private static void fireOneShot(LivingEntity shooter, LivingEntity target, Burst b) {
        ServerLevel level = (ServerLevel) shooter.level();
        Vec3 shooterEye = shooter.getEyePosition();
        Vec3 targetPos = target.getEyePosition();
        Vec3 dir = targetPos.subtract(shooterEye).normalize();

        // Dispersion aléatoire
        double dx = (RNG.nextDouble() - 0.5) * b.dispersion * 0.1;
        double dy = (RNG.nextDouble() - 0.5) * b.dispersion * 0.1;
        double dz = (RNG.nextDouble() - 0.5) * b.dispersion * 0.1;
        Vec3 shotDir = dir.add(dx, dy, dz).normalize();

        double vx = shotDir.x * b.speed;
        double vy = shotDir.y * b.speed;
        double vz = shotDir.z * b.speed;

        String type = b.projectileType != null ? b.projectileType.toLowerCase() : "minecraft:small_fireball";
        Entity proj = null;
        switch (type) {
            case "minecraft:small_fireball" -> {
                SmallFireball fb = new SmallFireball(level, shooter, vx, vy, vz);
                fb.setPos(shooterEye.x, shooterEye.y, shooterEye.z);
                proj = fb;
            }
            case "minecraft:fireball", "minecraft:large_fireball" -> {
                LargeFireball fb = new LargeFireball(level, shooter, vx, vy, vz, 1);
                fb.setPos(shooterEye.x, shooterEye.y, shooterEye.z);
                proj = fb;
            }
            case "minecraft:arrow" -> {
                Arrow arrow = new Arrow(level, shooter);
                arrow.setPos(shooterEye.x, shooterEye.y, shooterEye.z);
                arrow.shoot(shotDir.x, shotDir.y, shotDir.z, (float) b.speed, 0f);
                arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
                proj = arrow;
            }
            case "wavesurvivor:magic_bolt", "magic_bolt" -> {
                // Éclat d'âme : projectile magique simulé (MagicBolts), armure appliquée
                MagicBolts.spawn(level, shooter, shooterEye, shotDir, b.speed);
                proj = null;
            }
            case "minecraft:bone" -> {
                // Volée d'os : snowball avec l'apparence d'un os + vrais dégâts (BoneProjectiles)
                var bone = BoneProjectiles.create(level, shooter);
                bone.setPos(shooterEye.x, shooterEye.y, shooterEye.z);
                bone.shoot(shotDir.x, shotDir.y, shotDir.z, (float) b.speed, 0f);
                proj = bone;
            }
            case "minecraft:trident" -> {
                // Salve de tridents (non ramassables) — thème noyés
                net.minecraft.world.entity.projectile.ThrownTrident tr = new net.minecraft.world.entity.projectile.ThrownTrident(
                        level, shooter, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TRIDENT));
                tr.setPos(shooterEye.x, shooterEye.y, shooterEye.z);
                tr.shoot(shotDir.x, shotDir.y, shotDir.z, (float) b.speed, 0f);
                tr.pickup = AbstractArrow.Pickup.DISALLOWED;
                proj = tr;
            }
            default -> {
                // Fallback : small_fireball
                SmallFireball fb = new SmallFireball(level, shooter, vx, vy, vz);
                fb.setPos(shooterEye.x, shooterEye.y, shooterEye.z);
                proj = fb;
            }
        }
        if (proj != null) level.addFreshEntity(proj);
    }

    private static LivingEntity findLiving(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(uuid);
            if (e instanceof LivingEntity living) return living;
        }
        return null;
    }

    public static void clearAll() {
        int n = BURSTS.size();
        BURSTS.clear();
        if (n > 0) WaveSurvivorMod.LOGGER.debug("[GatlingScheduler] {} rafales purgées", n);
    }

    public static int burstCount() {
        return BURSTS.size();
    }
}
