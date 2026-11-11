package com.wavesurvivor.entity;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsTargetGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3f;

/**
 * STONE BRUTE (horde des Corrompus) — colosse de rochers moussus, modèle sur mesure (StoneBruteModel, haute résolution).
 * Tank de première ligne : lent mais implacable, insensible au recul.
 *  - PEAU DE PIERRE : au-delà de 6 dégâts, le reste d'un coup est réduit de 40 %.
 *  - FRAPPE ÉCRASANTE : coup lent et très puissant (animation du poing levé), qui projette en l'air (Golem de fer).
 *  - LANCER DE ROCHER (2.0) : cible visible entre 7 et 20 blocs, recharge 12 s, jamais pendant une Frappe écrasante. Il s'arrête, arrache
 *    un rocher de pierre moussue (animation de 30 ticks, synchronisée), le lâche à 70 % en parabole vers la cible : 8 dégâts sur 2,5 blocs,
 *    Lenteur II 3 s (×1,25 en Difficile, ×1,5 en Cauchemar). Le rocher est un bloc d'affichage (aucun bloc posé). Modèle : StoneBruteModel.
 */
public class StoneBrute extends IronGolem implements Enemy {

    private static final BlockParticleOption STONE = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
    private static final DustParticleOptions CORRUPTION = new DustParticleOptions(new Vector3f(1.0f, 0.28f, 0.8f), 1.2f);

    public StoneBrute(EntityType<? extends IronGolem> type, Level level) {
        super(type, level);
        this.xpReward = 20;
    }

    // ─── Lancer de rocher ───

    /** Temps de jeu (game time) du début du lancer, -1 = ne lance pas. Synchronisé pour l'animation du modèle. */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> THROW_START =
            net.minecraft.network.syncher.SynchedEntityData.defineId(StoneBrute.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    public static final int THROW_TICKS = 30, THROW_RELEASE = 21, THROW_COOLDOWN = 240;
    public static final double THROW_MIN = 7, THROW_MAX = 20, THROW_RADIUS = 2.5;
    public static final float THROW_DAMAGE = 8f;
    /** Fin de la recharge (game time). */
    private long throwReadyAt = 0;
    /** Cible visée au début du lancer. */
    private LivingEntity throwTarget;

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(THROW_START, -1);
    }

    /**
     * Avancement du lancer pour le modèle : -1 quand il ne lance pas, sinon 0 → 1 sur 30 ticks.
     * {@code ageInTicks} = tickCount + fraction de tick (fournie par le rendu), d'où la fraction utilisée pour lisser.
     */
    public float getThrowProgress(float ageInTicks) {
        int start = entityData.get(THROW_START);
        if (start < 0) return -1f;
        float partial = Math.max(0f, Math.min(1f, ageInTicks - tickCount));
        float p = ((int) level().getGameTime() - start + partial) / (float) THROW_TICKS;
        return p < 0f || p > 1f ? -1f : p;
    }

    public boolean isThrowing() { return entityData.get(THROW_START) >= 0; }

    /** Lance le rocher sur cette cible (aussi utilisé par la commande de test, sans condition de distance). */
    public boolean startThrow(LivingEntity target) {
        if (target == null || isThrowing() || level().isClientSide) return false;
        throwTarget = target;
        entityData.set(THROW_START, (int) level().getGameTime());
        throwReadyAt = level().getGameTime() + THROW_COOLDOWN;
        getNavigation().stop();
        return true;
    }

    private boolean canThrow(LivingEntity t) {
        if (t == null || !t.isAlive() || getAttackAnimationTick() > 0 || level().getGameTime() < throwReadyAt) return false;
        if (t instanceof Player pl && (pl.isCreative() || pl.isSpectator())) return false;
        double d = distanceTo(t);
        return d >= THROW_MIN && d <= THROW_MAX && hasLineOfSight(t);
    }

    private void throwTick(ServerLevel sl) {
        int start = entityData.get(THROW_START);
        if (start < 0) {
            if (tickCount % 5 == 0 && canThrow(getTarget())) startThrow(getTarget());
            return;
        }
        long t = sl.getGameTime() - start;
        // Il s'arrête pendant tout le lancer et fait face à sa cible
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        if (throwTarget != null && throwTarget.isAlive()) getLookControl().setLookAt(throwTarget, 30f, 30f);
        // Il arrache le rocher : terre et pierre moussue à ses pieds, pierre qui se brise
        if (t >= 3 && t <= 10 && t % 2 == 1) {
            sl.sendParticles(STONE, getX(), getY() + 0.2, getZ(), 12, 0.8, 0.1, 0.8, 0.15);
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()), getX(), getY() + 0.1, getZ(), 8, 0.7, 0.05, 0.7, 0.1);
            if (t == 3) sl.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.STONE_BREAK, net.minecraft.sounds.SoundSource.HOSTILE, 2f, 0.5f);
        }
        if (t == THROW_RELEASE) release(sl);
        if (t >= THROW_TICKS) {
            entityData.set(THROW_START, -1);
            throwTarget = null;
        }
    }

    /** 70 % de l'animation : le rocher part au-dessus de sa tête, en parabole vers la position de la cible. */
    private void release(ServerLevel sl) {
        LivingEntity tg = throwTarget != null && throwTarget.isAlive() ? throwTarget : getTarget();
        if (tg == null) return;
        net.minecraft.world.phys.Vec3 from = new net.minecraft.world.phys.Vec3(getX(), getY() + getBbHeight() + 0.6, getZ());
        net.minecraft.world.phys.Vec3 to = tg.position();
        double dist = from.distanceTo(to);
        int flight = (int) Math.round(12 + dist * 0.6);
        double arc = 3 + dist * 0.25;
        float size = 1.4f;
        org.joml.Quaternionf rot0 = new org.joml.Quaternionf();
        // Bloc d'affichage posé au point d'impact ; sa transformation le place sur la parabole (interpolée côté client par paliers de 2 ticks)
        var rock = com.wavesurvivor.horde.skill.IceFx.spawn(sl, to.x, to.y, to.z, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), rot0, size, size, size, 0f);
        if (rock == null) return;
        rockStep(rock, from, to, arc, 0, flight, size);
        sl.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.IRON_GOLEM_ATTACK, net.minecraft.sounds.SoundSource.HOSTILE, 1.6f, 0.6f);
        for (int k = 2; k <= flight; k += 2) {
            final int step = Math.min(k, flight);
            com.wavesurvivor.horde.skill.DelayedActionScheduler.schedule(sl.getServer(), k - 2, () -> {
                rockStep(rock, from, to, arc, step, flight, size);
                net.minecraft.world.phys.Vec3 p = along(from, to, arc, step / (double) flight);
                sl.sendParticles(STONE, p.x, p.y, p.z, 2, 0.2, 0.2, 0.2, 0.02);
            }, "stone brute boulder");
        }
        com.wavesurvivor.horde.skill.DelayedActionScheduler.schedule(sl.getServer(), flight, () -> {
            com.wavesurvivor.horde.skill.IceFx.remove(rock);
            impact(sl, to);
        }, "stone brute boulder impact");
    }

    private static net.minecraft.world.phys.Vec3 along(net.minecraft.world.phys.Vec3 a, net.minecraft.world.phys.Vec3 b, double arc, double u) {
        return new net.minecraft.world.phys.Vec3(a.x + (b.x - a.x) * u, a.y + (b.y - a.y) * u + arc * 4 * u * (1 - u), a.z + (b.z - a.z) * u);
    }

    /** Place le rocher au point {@code step / flight} de la parabole, en tournant (interpolation de 2 ticks). */
    private static void rockStep(net.minecraft.world.entity.Display.BlockDisplay d, net.minecraft.world.phys.Vec3 from, net.minecraft.world.phys.Vec3 to,
                                 double arc, int step, int flight, float size) {
        if (d == null || d.isRemoved()) return;
        net.minecraft.world.phys.Vec3 p = along(from, to, arc, step / (double) flight);
        org.joml.Quaternionf rot = new org.joml.Quaternionf().rotateXYZ(step * 0.35f, step * 0.2f, 0f);
        org.joml.Vector3f corner = rot.transform(new org.joml.Vector3f(-size / 2f, -size / 2f, -size / 2f));
        net.minecraft.nbt.CompoundTag tr = new net.minecraft.nbt.CompoundTag();
        tr.put("translation", floats((float) (p.x - to.x) + corner.x, (float) (p.y - to.y) + corner.y, (float) (p.z - to.z) + corner.z));
        tr.put("left_rotation", floats(rot.x, rot.y, rot.z, rot.w));
        tr.put("scale", floats(size, size, size));
        tr.put("right_rotation", floats(0, 0, 0, 1));
        net.minecraft.nbt.CompoundTag tag = d.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        tag.put("transformation", tr);
        tag.putInt("interpolation_duration", step == 0 ? 0 : 2);
        tag.putInt("start_interpolation", 0);
        d.load(tag);
    }

    private static net.minecraft.nbt.ListTag floats(float... v) {
        net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag();
        for (float f : v) l.add(net.minecraft.nbt.FloatTag.valueOf(f));
        return l;
    }

    /** Impact : le rocher éclate (aucun bloc posé), 8 dégâts sur 2,5 blocs et Lenteur II 3 s (selon la difficulté de la partie). */
    private void impact(ServerLevel sl, net.minecraft.world.phys.Vec3 at) {
        sl.sendParticles(STONE, at.x, at.y + 0.5, at.z, 70, 0.9, 0.5, 0.9, 0.3);
        sl.sendParticles(CORRUPTION, at.x, at.y + 0.4, at.z, 30, 1.2, 0.4, 1.2, 0.0);
        sl.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 1, 0, 0, 0, 0);
        sl.playSound(null, net.minecraft.core.BlockPos.containing(at), net.minecraft.sounds.SoundEvents.STONE_BREAK, net.minecraft.sounds.SoundSource.HOSTILE, 2f, 0.4f);
        sl.playSound(null, net.minecraft.core.BlockPos.containing(at), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, net.minecraft.sounds.SoundSource.HOSTILE, 0.8f, 1.3f);
        float dmg = (float) (THROW_DAMAGE * com.wavesurvivor.horde.benediction.RogueUpgradeManager.difficultyMult());
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(at, at).inflate(THROW_RADIUS, 1.5, THROW_RADIUS);
        for (LivingEntity v : sl.getEntitiesOfClass(LivingEntity.class, box, e -> e != this && e.isAlive() && !(e instanceof Enemy))) {
            if (v.distanceToSqr(at) > THROW_RADIUS * THROW_RADIUS + 2.25) continue;
            if (v instanceof Player pl && (pl.isCreative() || pl.isSpectator())) continue;
            v.hurt(damageSources().mobAttack(this), dmg);
            v.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return IronGolem.createAttributes()
                .add(Attributes.MAX_HEALTH, 80.0)
                .add(Attributes.ATTACK_DAMAGE, 12.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.addGoal(3, new MoveTowardsTargetGoal(this, 0.9, 32.0f));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** PEAU DE PIERRE : les gros coups sont amortis. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (amount > 6.0f) amount = 6.0f + (amount - 6.0f) * 0.6f;
        boolean hit = super.hurt(source, amount);
        if (hit && level() instanceof ServerLevel sl) {
            sl.sendParticles(STONE, getX(), getY() + getBbHeight() * 0.6, getZ(), 10, 0.4, 0.5, 0.4, 0.1);
        }
        return hit;
    }

    /** FRAPPE ÉCRASANTE : éclats de pierre et lueur rose à l'impact. */
    @Override
    public boolean doHurtTarget(Entity target) {
        if (isThrowing()) return false;   // pas de Frappe écrasante pendant un lancer
        boolean hit = super.doHurtTarget(target);
        if (hit && level() instanceof ServerLevel sl) {
            sl.sendParticles(STONE, target.getX(), target.getY() + 0.3, target.getZ(), 25, 0.5, 0.2, 0.5, 0.15);
            sl.sendParticles(CORRUPTION, target.getX(), target.getY() + 0.5, target.getZ(), 12, 0.5, 0.3, 0.5, 0.0);
        }
        return hit;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level() instanceof ServerLevel sl0 && isAlive()) throwTick(sl0);   // Lancer de rocher
        // Poussière de corruption qui s'échappe des fissures
        if (level() instanceof ServerLevel sl && tickCount % 10 == 0) {
            sl.sendParticles(CORRUPTION, getX(), getY() + getBbHeight() * 0.65, getZ(), 1, 0.4, 0.4, 0.4, 0.0);
        }
    }

    /** Pas lourds : grondement grave et poussière à chaque pas. */
    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        playSound(net.minecraft.sounds.SoundEvents.IRON_GOLEM_STEP, 1.6f, 0.55f);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state.isAir() ? Blocks.DIRT.defaultBlockState() : state),
                    getX(), getY() + 0.1, getZ(), 14, 0.7, 0.05, 0.7, 0.12);
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.6f; // grondement grave
    }
}
