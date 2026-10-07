package com.wavesurvivor.horde.skill;

import net.minecraft.core.particles.ItemParticleOption;
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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Projectile "os" pour les rafales gatling (gatlingProjectileType = "minecraft:bone").
 * Visuel : un Snowball qui affiche un OS. Les dégâts sont gérés ici (pas par la boule de neige) :
 *   - hitbox élargie : touche toute cible à ~0,45 bloc de sa trajectoire (test à chaque tick) ;
 *   - chaque os d'une rafale compte (l'invulnérabilité post-coup est remise à zéro) ;
 *   - dégâts = 50 % de l'attaque du tireur par os, armure appliquée ;
 *   - traverse les autres monstres (pas de tir ami), se brise sur un bloc.
 */
public class BoneProjectiles {

    private static final double HIT_INFLATE = 0.45;
    private static final float DAMAGE_FACTOR = 0.5f;

    private record Bone(Snowball entity, float damage) {}

    /** UUID du projectile → os suivi. */
    private static final Map<UUID, Bone> BONES = new ConcurrentHashMap<>();

    public static Snowball create(ServerLevel level, LivingEntity shooter) {
        Snowball bone = new Snowball(level, shooter);
        bone.setItem(new ItemStack(Items.BONE));
        AttributeInstance atk = shooter.getAttribute(Attributes.ATTACK_DAMAGE);
        float base = atk != null ? (float) Math.max(1.0, atk.getValue()) : 4f;
        BONES.put(bone.getUUID(), new Bone(bone, Math.max(1f, base * DAMAGE_FACTOR)));
        return bone;
    }

    /** Cible valable : vivante, pas le tireur, pas un monstre (pas de tir ami), pas un joueur en créatif. */
    private static boolean validTarget(Entity e, Entity owner) {
        if (!(e instanceof LivingEntity le) || !le.isAlive() || e == owner) return false;
        if (e instanceof Enemy) return false;
        return !(e instanceof Player p) || (!p.isCreative() && !p.isSpectator());
    }

    /** Hitbox élargie, vérifiée à chaque tick le long du déplacement. */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || BONES.isEmpty()) return;
        Iterator<Map.Entry<UUID, Bone>> it = BONES.entrySet().iterator();
        while (it.hasNext()) {
            Bone b = it.next().getValue();
            Snowball s = b.entity();
            if (s == null || !s.isAlive() || !(s.level() instanceof ServerLevel level)) { it.remove(); continue; }
            Entity owner = s.getOwner();
            Vec3 motion = s.getDeltaMovement();
            AABB sweep = s.getBoundingBox().expandTowards(motion).inflate(HIT_INFLATE);
            Entity best = null;
            double bestD = Double.MAX_VALUE;
            for (Entity e : level.getEntities(s, sweep, e -> validTarget(e, owner))) {
                double d = e.distanceToSqr(s);
                if (d < bestD) { bestD = d; best = e; }
            }
            if (best != null) {
                it.remove();
                hit(level, s, owner, (LivingEntity) best, b.damage());
            }
        }
    }

    /** Impact géré par le jeu : on le remplace par le nôtre (monstres traversés, bloc = l'os se brise). */
    @SubscribeEvent
    public void onImpact(ProjectileImpactEvent event) {
        Projectile proj = event.getProjectile();
        Bone b = BONES.get(proj.getUUID());
        if (b == null || !(proj.level() instanceof ServerLevel level)) return;
        HitResult hit = event.getRayTraceResult();
        if (hit instanceof EntityHitResult ehr) {
            Entity target = ehr.getEntity();
            event.setCanceled(true); // pas de dégâts « boule de neige » ; les monstres sont traversés
            if (validTarget(target, proj.getOwner())) {
                BONES.remove(proj.getUUID());
                hit(level, (Snowball) proj, proj.getOwner(), (LivingEntity) target, b.damage());
            }
            return;
        }
        // Bloc : l'os se brise
        BONES.remove(proj.getUUID());
        shatter(level, proj.position());
    }

    private static void hit(ServerLevel level, Snowball s, Entity owner, LivingEntity target, float dmg) {
        target.invulnerableTime = 0; // chaque os de la rafale compte
        target.hurt(level.damageSources().thrown(s, owner), dmg);
        Vec3 push = s.getDeltaMovement().normalize().scale(0.25);
        target.push(push.x, 0.05, push.z);
        shatter(level, s.position());
        s.discard();
    }

    private static void shatter(ServerLevel level, Vec3 at) {
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.BONE)),
                at.x, at.y, at.z, 8, 0.1, 0.1, 0.1, 0.08);
        level.playSound(null, net.minecraft.core.BlockPos.containing(at), SoundEvents.SKELETON_HURT, SoundSource.HOSTILE, 0.6f, 1.6f);
    }
}
