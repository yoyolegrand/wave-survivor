package com.wavesurvivor.horde.kingdom;

import com.wavesurvivor.horde.model.HordeEntity;
import com.wavesurvivor.horde.spawn.MobRegistry;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * MODE KINGDOM — UNITÉS DE SIÈGE. Le rôle se règle MONSTRE PAR MONSTRE dans l'onglet Mobs de l'éditeur
 * (champ « siege_role »), donc n'importe quel monstre (custom, moddé, avec compétences…) peut en être une :
 *  - BÉLIER (« ram ») : ×5 dégâts aux murs (géré par KingdomClaim), aucun recul.
 *  - SAPEUR (« sapper ») : court vers le mur de joueur le plus proche, mèche 1,5 s puis explosion qui détruit les blocs
 *    POSÉS PAR LES JOUEURS dans un rayon de 2 ; sans mur, il se bat normalement (terrain protégé).
 *  - GRIMPEUR (« climber ») : escalade les murs posés par les joueurs.
 * Le bouton « ＋ Unités de siège » de l'onglet Mobs ajoute 3 modèles prêts (Ravageur, Creeper, Araignée).
 */
final class KingdomSiege {

    static final String ROLE = "ws_siege_role";

    private static ServerLevel level;
    private static BlockPos center;
    private static int claimRadius = 16;
    private static final Set<UUID> SEEN = new HashSet<>();
    private static final Set<UUID> CLIMBERS = new HashSet<>();
    /** Sapeurs dont la mèche est allumée → tick d'explosion. */
    private static final Map<UUID, Long> FUSES = new HashMap<>();

    private KingdomSiege() {}

    static void start(ServerLevel lvl, BlockPos c, com.wavesurvivor.config.model.HordeConfigMultiData.KingdomSettings cfg) {
        clear();
        level = lvl;
        center = c;
        claimRadius = Math.max(4, cfg.claimRadius);
    }

    static void clear() {
        SEEN.clear();
        CLIMBERS.clear();
        FUSES.clear();
        BANNERS.clear();
        BUFFED.clear();
        level = null;
    }

    static String roleOf(Entity e) {
        return e.getPersistentData().getString(ROLE);
    }

    static void tick(long now, int cycle) {
        if (level == null) return;
        if (now % 10 == 0) assignRoles();
        if (now % 10 == 0) sappers(now);
        if (now % 10 == 0) rams(now);
        if (now % 10 == 0) builders(now);
        if (now % 10 == 0) banners();
        climbers();
    }

    /** Distance de détection d'un bâtiment par les unités de siège (elles quittent leur chemin pour l'attaquer). */
    private static final int DETECT = 10;
    /** Idem pour les unités « Attaque les bâtiments » (portée plus courte : elles ne dévient que pour un bâtiment proche). */
    private static final int DETECT_BUILDERS = 6;

    /**
     * Cible d'une unité qui attaque les bâtiments : le bâtiment le plus proche (mur, tour, rempart, porte…), sinon le
     * Monolithe, dans un rayon de 10 blocs (Sapeur, Bélier) ou 6 blocs (option « Attaque les bâtiments »).
     * null = rien à attaquer (elle suit son chemin) ou unité non concernée.
     */
    static BlockPos siegeTarget(Mob m) {
        if (level == null || m.level() != level) return null;
        String role = roleOf(m);
        int detect;
        if ("sapper".equals(role) || "ram".equals(role)) {
            detect = DETECT;
        } else {
            HordeEntity t = MobRegistry.get(m.getUUID());
            if (t == null || !t.attackBuildings || com.wavesurvivor.altar.AltarDefense.isProfaner(m.getUUID())) return null;
            detect = DETECT_BUILDERS;
        }
        BlockPos wall = KingdomClaim.nearestPlaced(m.blockPosition(), detect);
        if (wall != null) return wall;
        if (center != null && m.blockPosition().distSqr(center) <= (double) detect * detect) return center;
        return null;
    }

    /**
     * Unités « Attaque les bâtiments » : un bâtiment à moins de 6 blocs → elles quittent leur chemin et vont au contact
     * (les coups sont portés par BuildingAttack) ; bâtiment détruit / rien à portée → elles reprennent leur chemin.
     */
    private static void builders(long now) {
        for (Map.Entry<UUID, HordeEntity> en : new ArrayList<>(MobRegistry.entries())) {
            HordeEntity t = en.getValue();
            if (t == null || !t.attackBuildings) continue;
            String role = roleOf(t);
            if ("sapper".equals(role) || "ram".equals(role)) continue; // ont leur propre comportement
            Entity e = level.getEntity(en.getKey());
            if (!(e instanceof Mob m) || !m.isAlive()) continue;
            BlockPos target = siegeTarget(m);
            if (target == null) continue;
            KingdomMarch.release(m.getUUID());
            if (KingdomMarch.isMarching(m.getUUID())) continue; // vient d'abandonner : reste sur son chemin
            // Un joueur tout proche reste prioritaire (elle se défend) ; sinon, cap sur le bâtiment
            if (level.getNearestPlayer(m, DETECT_BUILDERS) != null && m.getTarget() != null) continue;
            m.setTarget(null);
            m.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.1);
        }
    }

    private static String roleOf(HordeEntity t) {
        return t == null || t.siegeRole == null ? "" : t.siegeRole;
    }

    // ─── Rôle de siège des unités fraîchement apparues ───

    private static void assignRoles() {
        for (Map.Entry<UUID, HordeEntity> en : MobRegistry.entries()) {
            UUID id = en.getKey();
            String role = roleOf(en.getValue());
            if (role.isEmpty()) continue;
            if (!SEEN.add(id)) continue;
            if (!(level.getEntity(id) instanceof Mob m) || !m.isAlive()) continue;
            m.getPersistentData().putString(ROLE, role);
            if ("ram".equals(role)) {
                AttributeInstance kb = m.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
                if (kb != null) kb.setBaseValue(1.0);
            } else if ("climber".equals(role)) {
                CLIMBERS.add(id);
            } else if ("banner".equals(role)) {
                // Porte-étendard : bannière rouge sur la tête (ne tombe pas à sa mort)
                m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.RED_BANNER));
                m.setDropChance(net.minecraft.world.entity.EquipmentSlot.HEAD, 0f);
                BANNERS.put(id, m.position());
            }
        }
        if (SEEN.size() > 2000) SEEN.clear();
    }

    // ─── Porte-étendard ───

    /** Porte-étendards vivants → dernière position connue. */
    private static final Map<UUID, Vec3> BANNERS = new HashMap<>();
    /** Monstres actuellement galvanisés par un étendard. */
    private static final Set<UUID> BUFFED = new HashSet<>();
    private static final UUID BANNER_SPEED = UUID.fromString("6f1c2a52-7b2e-4d0e-9a51-0b5a1e2c3d41");
    private static final UUID BANNER_DMG = UUID.fromString("6f1c2a52-7b2e-4d0e-9a51-0b5a1e2c3d42");
    private static final double BANNER_RADIUS = 12.0;
    private static final net.minecraft.core.particles.DustParticleOptions RED =
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.9f, 0.12f, 0.1f), 1.3f);

    /**
     * PORTE-ÉTENDARD : tant qu'il vit, les monstres de la horde à 12 blocs ou moins gagnent +20 % de vitesse et
     * +25 % de dégâts (petite aura rouge). À sa mort : bonus perdu, Lenteur 5 s autour de lui, +1 Essence au trésor.
     */
    private static void banners() {
        List<Vec3> alive = new ArrayList<>();
        for (java.util.Iterator<Map.Entry<UUID, Vec3>> it = BANNERS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Vec3> en = it.next();
            Entity e = level.getEntity(en.getKey());
            if (e instanceof Mob m && m.isAlive()) {
                en.setValue(m.position());
                alive.add(m.position());
                level.sendParticles(RED, m.getX(), m.getY() + m.getBbHeight() + 0.6, m.getZ(), 4, 0.15, 0.5, 0.15, 0.0);
            } else {
                if (e instanceof net.minecraft.world.entity.LivingEntity le && le.isDeadOrDying()) bannerFallen(en.getValue());
                it.remove();
            }
        }
        Set<UUID> inRange = new HashSet<>();
        if (!alive.isEmpty()) {
            double r2 = BANNER_RADIUS * BANNER_RADIUS;
            for (Map.Entry<UUID, HordeEntity> en : MobRegistry.entries()) {
                if (BANNERS.containsKey(en.getKey())) continue;
                if (!(level.getEntity(en.getKey()) instanceof Mob m) || !m.isAlive()) continue;
                for (Vec3 p : alive) {
                    if (m.position().distanceToSqr(p) <= r2) { inRange.add(en.getKey()); break; }
                }
            }
        }
        for (UUID id : inRange) {
            if (BUFFED.add(id)) bannerBuff(id, true);
            if (level.getEntity(id) instanceof Mob m && level.random.nextInt(3) == 0) {
                level.sendParticles(RED, m.getX(), m.getY() + m.getBbHeight() * 0.5, m.getZ(), 1, 0.25, 0.3, 0.25, 0.0);
            }
        }
        for (UUID id : new ArrayList<>(BUFFED)) {
            if (!inRange.contains(id)) {
                bannerBuff(id, false);
                BUFFED.remove(id);
            }
        }
    }

    private static void bannerBuff(UUID id, boolean on) {
        if (!(level.getEntity(id) instanceof Mob m)) return;
        AttributeInstance speed = m.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance dmg = m.getAttribute(Attributes.ATTACK_DAMAGE);
        if (on) {
            if (speed != null && speed.getModifier(BANNER_SPEED) == null) speed.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    BANNER_SPEED, "ws_banner_speed", 0.20, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE));
            if (dmg != null && dmg.getModifier(BANNER_DMG) == null) dmg.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    BANNER_DMG, "ws_banner_damage", 0.25, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE));
        } else {
            if (speed != null) speed.removeModifier(BANNER_SPEED);
            if (dmg != null) dmg.removeModifier(BANNER_DMG);
        }
    }

    /** L'étendard tombe : l'escouade autour est démoralisée (Lenteur 5 s), l'équipe gagne 1 Essence. */
    private static void bannerFallen(Vec3 at) {
        double r2 = BANNER_RADIUS * BANNER_RADIUS;
        for (Map.Entry<UUID, HordeEntity> en : MobRegistry.entries()) {
            if (!(level.getEntity(en.getKey()) instanceof Mob m) || !m.isAlive()) continue;
            if (m.position().distanceToSqr(at) > r2) continue;
            if (BUFFED.remove(en.getKey())) bannerBuff(en.getKey(), false);
            m.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 100, 0));
        }
        level.sendParticles(RED, at.x, at.y + 1.5, at.z, 40, 1.2, 0.8, 1.2, 0.05);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 1.2f, 0.6f);
        if (KingdomTreasury.active()) KingdomTreasury.add(KingdomTreasury.Res.ESSENCE, 1);
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceToSqr(at) < 64 * 64) p.displayClientMessage(WSLang.c("kingdom.banner.fallen"), true);
        }
    }

    // ─── Sapeurs ───

    private static void sappers(long now) {
        for (Map.Entry<UUID, HordeEntity> en : new ArrayList<>(MobRegistry.entries())) {
            if (!"sapper".equals(roleOf(en.getValue()))) continue;
            Entity e = level.getEntity(en.getKey());
            if (!(e instanceof Mob m) || !m.isAlive()) continue;
            UUID id = m.getUUID();

            Long fuse = FUSES.get(id);
            if (fuse != null) {
                level.sendParticles(ParticleTypes.SMOKE, m.getX(), m.getY() + m.getBbHeight() + 0.2, m.getZ(), 4, 0.1, 0.1, 0.1, 0.01);
                if (now >= fuse) detonate(m);
                continue;
            }
            // Priorité : un bâtiment à portée (même en suivant un chemin tracé) ; sinon il continue sa marche
            BlockPos wall = siegeTarget(m);
            if (wall == null) continue;
            KingdomMarch.release(id); // quitte son chemin le temps de l'attaque
            if (KingdomMarch.isMarching(id)) continue; // vient d'abandonner une attaque impossible : reste sur son chemin
            m.setTarget(null);
            m.getNavigation().moveTo(wall.getX() + 0.5, wall.getY(), wall.getZ() + 0.5, 1.15);
            double reach = wall.equals(center) ? 9.0 : 6.5;
            if (m.position().distanceToSqr(Vec3.atCenterOf(wall)) < reach) {
                FUSES.put(id, now + 30);
                m.getNavigation().stop();
                level.playSound(null, m.blockPosition(), SoundEvents.TNT_PRIMED, SoundSource.HOSTILE, 1.2f, 1f);
            }
        }
        FUSES.keySet().removeIf(id -> level.getEntity(id) == null);
    }

    private static void detonate(Mob m) {
        FUSES.remove(m.getUUID());
        BlockPos at = m.blockPosition();
        // Explosion SANS destruction de terrain (dégâts aux entités seulement)…
        level.explode(m, m.getX(), m.getY() + 0.5, m.getZ(), 2.5f, Level.ExplosionInteraction.NONE);
        // …puis 200 dégâts aux murs et défenses posés par les joueurs alentour (ce qui tombe à 0 PV est détruit)
        int broken = KingdomClaim.blastDamage(at, 2, 200f);
        // …et au Monolithe s'il est dans le souffle
        if (com.wavesurvivor.altar.AltarDefense.inReach(level, m.position(), 4.5)) com.wavesurvivor.altar.AltarDefense.externalHit(level, 200f);
        if (broken > 0) {
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(Vec3.atCenterOf(center)) < (claimRadius + 32) * (claimRadius + 32)) {
                    p.displayClientMessage(WSLang.c("kingdom.sapper_blast", broken), true);
                }
            }
        }
        m.discard();
    }

    // ─── Béliers ───

    /** Dernier coup de chaque Bélier (un coup par seconde). */
    private static final Map<UUID, Long> RAM_HIT = new java.util.HashMap<>();

    /**
     * Bélier : un bâtiment à moins de 10 blocs → il quitte son chemin, charge dessus et frappe (×5 ses dégâts) chaque
     * seconde, Monolithe compris. Rien à portée → il suit son chemin.
     */
    private static void rams(long now) {
        for (Map.Entry<UUID, HordeEntity> en : new ArrayList<>(MobRegistry.entries())) {
            if (!"ram".equals(roleOf(en.getValue()))) continue;
            Entity e = level.getEntity(en.getKey());
            if (!(e instanceof Mob m) || !m.isAlive()) continue;
            UUID id = m.getUUID();
            BlockPos target = siegeTarget(m);
            if (target == null) continue;
            KingdomMarch.release(id);
            if (KingdomMarch.isMarching(id)) continue; // vient d'abandonner une attaque impossible : reste sur son chemin
            m.setTarget(null);
            m.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.3); // charge
            double dx = m.getX() - (target.getX() + 0.5), dz = m.getZ() - (target.getZ() + 0.5);
            boolean monolith = target.equals(center);
            double reach = (monolith ? 3.0 : 2.2) + m.getBbWidth() * 0.5;
            if (dx * dx + dz * dz > reach * reach) continue;
            if (now - RAM_HIT.getOrDefault(id, 0L) < 20) continue;
            RAM_HIT.put(id, now);
            var atkAttr = m.getAttribute(Attributes.ATTACK_DAMAGE);
            float dmg = (float) (atkAttr != null ? Math.max(1.0, atkAttr.getValue()) : 4.0) * 5f;
            m.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            m.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
            level.playSound(null, target, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.6f, 0.8f);
            level.sendParticles(ParticleTypes.CRIT, target.getX() + 0.5, target.getY() + 1, target.getZ() + 0.5, 12, 0.4, 0.4, 0.4, 0.2);
            if (monolith) com.wavesurvivor.altar.AltarDefense.externalHit(level, dmg);
            else KingdomClaim.hitNear(m, reach + 0.5, dmg);
        }
        RAM_HIT.keySet().removeIf(id -> level.getEntity(id) == null);
    }

    // ─── Grimpeurs ───

    private static void climbers() {
        if (CLIMBERS.isEmpty()) return;
        CLIMBERS.removeIf(id -> {
            Entity e = level.getEntity(id);
            if (!(e instanceof Mob m) || !m.isAlive()) return true;
            if (m.horizontalCollision) {
                Vec3 look = Vec3.directionFromRotation(0, m.getYRot()).normalize();
                BlockPos front = BlockPos.containing(m.getX() + look.x * 0.9, m.getY() + 0.5, m.getZ() + look.z * 0.9);
                if (KingdomClaim.isPlaced(front) || KingdomClaim.isPlaced(front.above())) {
                    Vec3 v = m.getDeltaMovement();
                    m.setDeltaMovement(v.x, 0.28, v.z);
                    m.fallDistance = 0;
                }
            }
            return false;
        });
    }
}
