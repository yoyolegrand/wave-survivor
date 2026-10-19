package com.wavesurvivor.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * DJINN (horde des Sables Ardents) — génie sur la base de l'Évocateur : crocs et invocations vanilla
 * (ses Vex deviennent des Esprits du Djinn), avec ses DEUX COMPÉTENCES PROPRES intégrées (indépendantes de la config) :
 *  - ORBES DU DJINN : 3 orbes dorés tournent autour de lui puis traquent la cible (esquivables), explosion de sable
 *    à l'impact (4 dégâts magiques + Lenteur). Toutes les ~8,5 s, cible à 26 blocs au plus.
 *  - DISPARITION DU DJINN : tourbillon de sable, il se volatilise et réapparaît près de sa cible dans un anneau d'or.
 *    Toutes les ~6,5 s.
 */
public class Djinn extends Evoker {

    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0f, 0.82f, 0.25f), 0.9f);
    private static final DustParticleOptions TURQ = new DustParticleOptions(new Vector3f(0.3f, 0.95f, 0.9f), 0.9f);
    private static final DustParticleOptions BIG_GOLD = new DustParticleOptions(new Vector3f(1.0f, 0.85f, 0.3f), 1.8f);
    private static final DustColorTransitionOptions ORB = new DustColorTransitionOptions(
            new Vector3f(1.0f, 0.92f, 0.45f), new Vector3f(1.0f, 0.45f, 0.1f), 1.6f);
    private static final BlockParticleOption SAND = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState());
    private static final BlockParticleOption SAND_BURST = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());

    /** Réglages des compétences (en ticks). */
    private static final int ORB_COOLDOWN = 170, BLINK_COOLDOWN = 130, GAP = 40, ORB_RANGE = 26, ORB_LIFE = 140, ORBIT = 12;
    private static final float ORB_DAMAGE = 4f;
    private static final double ORB_SPEED = 0.38;

    private int orbCd = 60, blinkCd = 100, gapCd = 0, blinkCharge = -1;
    private final List<Orb> orbs = new ArrayList<>();

    /** Un orbe de sable (simulé côté serveur, dessiné en particules). */
    private static final class Orb {
        Vec3 pos, vel = Vec3.ZERO;
        final LivingEntity target;
        final double angle0;
        int age;
        Orb(Vec3 pos, LivingEntity target, double angle0) { this.pos = pos; this.target = target; this.angle0 = angle0; }
    }

    public Djinn(EntityType<? extends Evoker> type, Level level) {
        super(type, level);
        this.xpReward = 15;
    }

    /** Statistiques de base (œuf, /summon, horde) : 40 PV au lieu des 24 de l'Évocateur. */
    public static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder createAttributes() {
        return Evoker.createAttributes()
                .add(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, 40.0);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel sl)) return;
        // Ambiance : paillettes autour de lui
        if (tickCount % 4 == 0) {
            sl.sendParticles(random.nextBoolean() ? GOLD : TURQ, getX(), getY() + 0.9, getZ(), 2, 0.5, 0.8, 0.5, 0.0);
            if (tickCount % 20 == 0) sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1.5, getZ(), 1, 0.4, 0.4, 0.4, 0.01);
        }
        tickOrbs(sl);
        tickBlink(sl);
        replaceFangs(sl);
        if (!isAlive() || isNoAi()) return;
        if (orbCd > 0) orbCd--;
        if (blinkCd > 0) blinkCd--;
        if (gapCd > 0) gapCd--;
        LivingEntity t = getTarget();
        if (t == null || !t.isAlive() || gapCd > 0 || blinkCharge >= 0) return;
        double d = distanceTo(t);
        if (orbCd == 0 && d <= ORB_RANGE && hasLineOfSight(t)) castOrbs(sl, t);
        else if (blinkCd == 0) startBlink(sl);
    }

    // ─── ORBES DU DJINN ───

    private void castOrbs(ServerLevel sl, LivingEntity t) {
        orbCd = ORB_COOLDOWN;
        gapCd = GAP;
        setIsCastingSpell(IllagerSpell.FANGS); // bras levés
        spellCastingTickCount = 20;
        Vec3 c = position().add(0, 1.4, 0);
        for (int i = 0; i < 3; i++) orbs.add(new Orb(c, t, i * Math.PI * 2 / 3));
        sl.playSound(null, blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 1.2f, 1.2f);
        sl.playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 1.5f, 0.8f);
        sl.sendParticles(BIG_GOLD, c.x, c.y, c.z, 25, 0.6, 0.4, 0.6, 0.0);
        sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 12, 0.3, 0.3, 0.3, 0.08);
    }

    private void tickOrbs(ServerLevel sl) {
        Iterator<Orb> it = orbs.iterator();
        while (it.hasNext()) {
            Orb o = it.next();
            o.age++;
            Vec3 prev = o.pos;
            if (o.age <= ORBIT) {
                // Phase 1 : les orbes tournent autour du djinn
                double a = o.angle0 + o.age * 0.55;
                o.pos = position().add(Math.cos(a) * 1.3, 1.5 + Math.sin(o.age * 0.4) * 0.25, Math.sin(a) * 1.3);
                o.vel = o.pos.subtract(prev);
            } else {
                // Phase 2 : chasse (virage limité : on peut esquiver)
                if (o.target == null || !o.target.isAlive()) { burst(sl, o.pos, false); it.remove(); continue; }
                Vec3 aim = o.target.position().add(0, o.target.getBbHeight() * 0.6, 0).subtract(o.pos).normalize().scale(ORB_SPEED);
                Vec3 v = o.vel.lengthSqr() < 1e-4 ? aim : o.vel.scale(0.86).add(aim.scale(0.14));
                o.vel = v.normalize().scale(ORB_SPEED);
                o.pos = o.pos.add(o.vel);
            }
            // Dessin : cœur or-orange, traînée de sable, étincelles
            sl.sendParticles(ORB, o.pos.x, o.pos.y, o.pos.z, 2, 0.05, 0.05, 0.05, 0.0);
            sl.sendParticles(SAND, prev.x, prev.y, prev.z, 2, 0.08, 0.08, 0.08, 0.0);
            if (o.age % 3 == 0) sl.sendParticles(ParticleTypes.END_ROD, o.pos.x, o.pos.y, o.pos.z, 1, 0.05, 0.05, 0.05, 0.01);
            if (o.age <= ORBIT) continue;
            // Impact : joueur touché
            Player hit = null;
            for (Player p : sl.getEntitiesOfClass(Player.class, new net.minecraft.world.phys.AABB(o.pos, o.pos).inflate(0.8))) {
                if (!p.isSpectator() && !p.isCreative()) { hit = p; break; }
            }
            LivingEntity victim = hit != null ? hit
                    : (o.target.getBoundingBox().inflate(0.3).contains(o.pos) ? o.target : null);
            if (victim != null) {
                victim.hurt(damageSources().indirectMagic(this, this), ORB_DAMAGE);
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, true));
                burst(sl, o.pos, true);
                it.remove();
                continue;
            }
            // Mur touché ou fin de vie : l'orbe éclate sur place
            BlockPos bp = BlockPos.containing(o.pos);
            if (!sl.getBlockState(bp).getCollisionShape(sl, bp).isEmpty() || o.age > ORB_LIFE) {
                burst(sl, o.pos, false);
                it.remove();
            }
        }
    }

    /** Explosion d'un orbe : sable, or, éclair (plus forte sur une cible). */
    private void burst(ServerLevel sl, Vec3 p, boolean onTarget) {
        sl.sendParticles(SAND_BURST, p.x, p.y, p.z, onTarget ? 30 : 15, 0.3, 0.3, 0.3, 0.15);
        sl.sendParticles(BIG_GOLD, p.x, p.y, p.z, onTarget ? 18 : 8, 0.35, 0.35, 0.35, 0.0);
        if (onTarget) sl.sendParticles(ParticleTypes.FLASH, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        sl.playSound(null, BlockPos.containing(p), onTarget ? SoundEvents.FIREWORK_ROCKET_BLAST : SoundEvents.SAND_BREAK,
                SoundSource.HOSTILE, onTarget ? 1.0f : 0.8f, onTarget ? 0.8f : 1.2f);
    }

    // ─── DISPARITION DU DJINN ───

    private void startBlink(ServerLevel sl) {
        blinkCd = BLINK_COOLDOWN;
        gapCd = GAP;
        blinkCharge = 10; // ~0,5 s de tourbillon avant de disparaître
        setIsCastingSpell(IllagerSpell.DISAPPEAR);
        spellCastingTickCount = 12;
        sl.playSound(null, blockPosition(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.HOSTILE, 1.2f, 1.0f);
    }

    private void tickBlink(ServerLevel sl) {
        if (blinkCharge < 0) return;
        // Tourbillon de sable qui se resserre et monte
        int k = 10 - blinkCharge;
        for (int i = 0; i < 3; i++) {
            double a = k * 0.7 + i * Math.PI * 2 / 3;
            double r = 1.4 - k * 0.1;
            sl.sendParticles(SAND, getX() + Math.cos(a) * r, getY() + 0.2 + k * 0.18, getZ() + Math.sin(a) * r, 2, 0.05, 0.05, 0.05, 0.0);
            sl.sendParticles(GOLD, getX() + Math.cos(a + 0.4) * r, getY() + 0.4 + k * 0.18, getZ() + Math.sin(a + 0.4) * r, 1, 0, 0, 0, 0);
        }
        if (--blinkCharge >= 0) return;
        LivingEntity t = getTarget();
        if (t == null || !t.isAlive()) return;
        // Départ : nuage doré
        Vec3 from = position();
        sl.sendParticles(SAND_BURST, from.x, from.y + 1, from.z, 25, 0.4, 0.6, 0.4, 0.1);
        sl.sendParticles(BIG_GOLD, from.x, from.y + 1, from.z, 20, 0.4, 0.7, 0.4, 0.0);
        // Réapparition près de la cible (3 à 4 blocs, sur un sol sûr)
        boolean moved = false;
        for (int tries = 0; tries < 8 && !moved; tries++) {
            double a = random.nextDouble() * Math.PI * 2, r = 3 + random.nextDouble();
            moved = randomTeleport(t.getX() + Math.cos(a) * r, t.getY() + 1, t.getZ() + Math.sin(a) * r, false);
        }
        if (!moved) return;
        lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, t.getEyePosition());
        // Arrivée : anneau d'or qui s'étend, éclair, sable
        Vec3 at = position();
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI * 2 / 24;
            sl.sendParticles(BIG_GOLD, at.x + Math.cos(a) * 0.6, at.y + 0.2, at.z + Math.sin(a) * 0.6, 0,
                    Math.cos(a), 0.05, Math.sin(a), 0.25);
            sl.sendParticles(ParticleTypes.END_ROD, at.x + Math.cos(a) * 0.4, at.y + 0.3, at.z + Math.sin(a) * 0.4, 0,
                    Math.cos(a), 0.02, Math.sin(a), 0.18);
        }
        sl.sendParticles(ParticleTypes.FLASH, at.x, at.y + 1, at.z, 1, 0, 0, 0, 0);
        sl.sendParticles(SAND_BURST, at.x, at.y + 0.5, at.z, 30, 0.6, 0.4, 0.6, 0.12);
        sl.playSound(null, blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 1.3f, 1.0f);
        sl.playSound(null, blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.9f, 0.6f);
    }

    // ─── CROCS DU DJINN ───

    /**
     * Remplace les crocs vanilla lancés par CE Djinn par des Crocs du Djinn (turquoise et or), pendant leur préparation
     * (encore invisibles) : copie exacte de leur position, rotation, délai et propriétaire.
     */
    private void replaceFangs(ServerLevel sl) {
        if (!isCastingSpell() && tickCount % 5 != 0) return;
        for (net.minecraft.world.entity.projectile.EvokerFangs f : sl.getEntitiesOfClass(
                net.minecraft.world.entity.projectile.EvokerFangs.class, getBoundingBox().inflate(24),
                x -> !(x instanceof DjinnFangs) && x.getOwner() == this)) {
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            f.saveWithoutId(tag);
            tag.remove("UUID");
            f.discard();
            DjinnFangs df = com.wavesurvivor.registry.ModEntities.DJINN_FANGS.get().create(sl);
            if (df == null) continue;
            df.load(tag);
            sl.addFreshEntity(df);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        orbs.clear();
        super.remove(reason);
    }

    /** Les Vex invoqués par un Djinn deviennent des Esprits du Djinn, et ses crocs des Crocs du Djinn (même comportement). */
    public static class Events {
        @SubscribeEvent
        public void onJoin(EntityJoinLevelEvent e) {
            if (!(e.getLevel() instanceof ServerLevel sl)) return;
            // Crocs : turquoise et or (copie exacte : position, délai, propriétaire)
            if (e.getEntity() instanceof net.minecraft.world.entity.projectile.EvokerFangs fangs && !(fangs instanceof DjinnFangs)
                    && fangs.getOwner() instanceof Djinn) {
                e.setCanceled(true);
                net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
                fangs.saveWithoutId(tag);
                tag.remove("UUID");
                sl.getServer().execute(() -> {
                    DjinnFangs df = com.wavesurvivor.registry.ModEntities.DJINN_FANGS.get().create(sl);
                    if (df == null) return;
                    df.load(tag);
                    sl.addFreshEntity(df);
                });
                return;
            }
            if (!(e.getEntity() instanceof Vex vex) || vex instanceof DjinnSpirit) return;
            if (!(vex.getOwner() instanceof Djinn djinn)) return;
            e.setCanceled(true);
            sl.getServer().execute(() -> {
                DjinnSpirit s = com.wavesurvivor.registry.ModEntities.DJINN_SPIRIT.get().create(sl);
                if (s == null) return;
                s.moveTo(vex.getX(), vex.getY(), vex.getZ(), vex.getYRot(), 0f);
                s.finalizeSpawn(sl, sl.getCurrentDifficultyAt(vex.blockPosition()), net.minecraft.world.entity.MobSpawnType.MOB_SUMMONED, null, null);
                s.setOwner(djinn);
                if (vex.getBoundOrigin() != null) s.setBoundOrigin(vex.getBoundOrigin());
                s.setLimitedLife(20 * (30 + djinn.getRandom().nextInt(90)));
                if (djinn.getTarget() != null) s.setTarget(djinn.getTarget());
                sl.addFreshEntityWithPassengers(s);
                sl.sendParticles(TURQ, s.getX(), s.getY() + 0.4, s.getZ(), 12, 0.25, 0.25, 0.25, 0.0);
            });
        }
    }
}
