package com.wavesurvivor.entity;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/**
 * BOLIDE (monstre de la Faille) — arrivée spectaculaire, modèle de Claude chat (BolideModel, textures « brûlant » / « refroidi »).
 * États : 0 = CHUTE (seule la boule est visible), 1 = IMPACT (30 ticks, se relève), 2 = ACTIF (marche), 3 = COUP (12 ticks, direct du poing).
 *  - APPARITION : il tombe du ciel (marqueur rose au sol ~1,5 s avant, traînée de feu rose / violette) ; impact = 8 dégâts sur 3 blocs
 *    + cratère de particules (aucun bloc cassé). Venu d'un météore du Titan : la chute est celle du météore, il passe directement en IMPACT.
 *  - EN FUSION (chaleur 1 → 0 sur 15 s depuis l'impact) : rapide (0,3), coups de 9 qui mettent le feu 3 s.
 *  - REFROIDI (chaleur < 0,5) : plus lent (0,2), coups de 6, +30 % de dégâts reçus.
 */
public class Bolide extends FailleMonster {

    public static final int FALL = 0, IMPACT = 1, ACTIVE = 2, PUNCH = 3;
    public static final int FALL_TICKS = 30, IMPACT_TICKS = 30, PUNCH_TICKS = 12, HEAT_TICKS = 300;
    public static final double FALL_HEIGHT = 26, IMPACT_RADIUS = 3;
    public static final float IMPACT_DAMAGE = 8f;
    private static final DustParticleOptions PINK = new DustParticleOptions(new Vector3f(1f, 0.35f, 0.75f), 1.6f);
    private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.62f, 0.3f, 1f), 1.4f);

    /** Temps de jeu de l'impact (début du refroidissement), -1 = pas encore tombé. */
    private static final EntityDataAccessor<Integer> HEAT_START = SynchedEntityData.defineId(Bolide.class, EntityDataSerializers.INT);

    /** Point d'impact de la chute (x, y, z) ; null = chute pas encore préparée. */
    private double[] landing;
    /** Vrai : apparu d'un météore du Titan (la chute est déjà jouée par le météore). */
    private boolean fromMeteor;
    private boolean cooled;
    /** Vitesse et dégâts « en fusion » (valeurs de la vague ou du catalogue), relevés au premier tick ; refroidi = ×2/3. */
    private double hotSpeed = -1, hotAttack = -1;

    public Bolide(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 50.0)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.ATTACK_DAMAGE, 9.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(HEAT_START, -1);
    }

    @Override
    protected int stateTicks(int s) {
        return switch (s) {
            case FALL -> FALL_TICKS;
            case IMPACT -> IMPACT_TICKS;
            case PUNCH -> PUNCH_TICKS;
            default -> 20;
        };
    }

    @Override
    public float failleHeat() {
        int s = entityData.get(HEAT_START);
        if (s < 0) return 1f;
        return Math.max(0f, Math.min(1f, 1f - (float) (level().getGameTime() - s) / HEAT_TICKS));
    }

    /** Apparu d'un météore du Titan : passe directement en IMPACT (à appeler avant l'ajout au monde). */
    public void landFromMeteor() {
        fromMeteor = true;
    }

    /** Commande de test : relance la chute depuis le ciel au-dessus de sa position actuelle. */
    public void restartFall() {
        landing = null;
        fromMeteor = false;
        cooled = false;
        entityData.set(HEAT_START, -1);
        applyHeatStats(false);
        setState(FALL);
    }

    /** Commande de test : refroidit tout de suite (chaleur à 0). */
    public void coolNow() {
        if (entityData.get(HEAT_START) >= 0) entityData.set(HEAT_START, (int) level().getGameTime() - HEAT_TICKS);
    }

    @Override
    public void aiStep() {
        if (level() instanceof ServerLevel sl && failleState() == FALL) {   // la chute ne passe pas par la physique normale
            fallTick(sl);
            return;
        }
        super.aiStep();
        if (!(level() instanceof ServerLevel sl)) return;
        int st = failleState();
        long t = stateAge();
        if (st == IMPACT) {
            getNavigation().stop();
            setDeltaMovement(0, getDeltaMovement().y, 0);
            if (t % 4 == 0) sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.3, getZ(), 3, 0.8, 0.1, 0.8, 0.02);
            if (t >= IMPACT_TICKS) setState(ACTIVE);
        } else if (st == PUNCH && t >= PUNCH_TICKS) {
            setState(ACTIVE);
        }
        // Chaleur : fumée et braises tant qu'il est chaud ; passage à l'état refroidi sous 0,5
        float heat = failleHeat();
        if (heat > 0.5f && tickCount % 4 == 0) sl.sendParticles(heat > 0.8f ? ParticleTypes.FLAME : ParticleTypes.SMOKE, getX(), getY() + 1.4, getZ(), 2, 0.4, 0.6, 0.4, 0.01);
        if (!cooled && heat < 0.5f) {
            cooled = true;
            applyHeatStats(true);
            sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1.2, getZ(), 16, 0.5, 0.6, 0.5, 0.02);
            playSound(SoundEvents.FIRE_EXTINGUISH, 1.2f, 0.6f);
        }
    }

    /** En fusion : vitesse et dégâts de base (0,3 / 9) ; refroidi : ×2/3 (0,2 / 6). Les valeurs réglées par la vague sont respectées. */
    private void applyHeatStats(boolean cool) {
        var spd = getAttribute(Attributes.MOVEMENT_SPEED);
        var atk = getAttribute(Attributes.ATTACK_DAMAGE);
        if (spd == null || atk == null) return;
        if (hotSpeed < 0) { hotSpeed = spd.getBaseValue(); hotAttack = atk.getBaseValue(); }
        spd.setBaseValue(cool ? hotSpeed * 2 / 3 : hotSpeed);
        atk.setBaseValue(cool ? hotAttack * 2 / 3 : hotAttack);
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("WsLanded", failleState() != FALL);
        if (hotSpeed >= 0) { tag.putDouble("WsHotSpeed", hotSpeed); tag.putDouble("WsHotAttack", hotAttack); }
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("WsHotSpeed")) { hotSpeed = tag.getDouble("WsHotSpeed"); hotAttack = tag.getDouble("WsHotAttack"); }
        if (tag.getBoolean("WsLanded")) {   // rechargé après sa chute : debout, déjà refroidi (pas de nouvelle chute)
            landing = new double[]{getX(), getY(), getZ()};
            setState(ACTIVE);
            entityData.set(HEAT_START, (int) level().getGameTime() - HEAT_TICKS);
        }
    }

    private void fallTick(ServerLevel sl) {
        if (landing == null) {   // première image : prépare la chute au-dessus du point d'arrivée
            landing = new double[]{getX(), getY(), getZ()};
            setNoGravity(true);
            noPhysics = true;
            if (fromMeteor) { land(sl, false); return; }
            setPos(getX(), getY() + FALL_HEIGHT, getZ());
            setState(FALL);
            sl.playSound(null, landing[0], landing[1], landing[2], SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 2f, 0.5f);
        }
        long t = stateAge();
        double k = Math.min(1.0, t / (double) FALL_TICKS);
        double y = landing[1] + FALL_HEIGHT * (1 - k * k);   // accélère en tombant
        setDeltaMovement(0, 0, 0);
        setPos(landing[0], y, landing[2]);
        // Marqueur au sol (cercle rose) et traînée de feu rose / violette
        if (t % 2 == 0) {
            for (int i = 0; i < 16; i++) {
                double a = Math.PI * 2 * i / 16 + t * 0.1;
                sl.sendParticles(PINK, landing[0] + Math.cos(a) * IMPACT_RADIUS, landing[1] + 0.15, landing[2] + Math.sin(a) * IMPACT_RADIUS, 1, 0, 0, 0, 0);
            }
        }
        sl.sendParticles(ParticleTypes.FLAME, getX(), getY() + 1, getZ(), 4, 0.4, 0.6, 0.4, 0.02);
        sl.sendParticles(VIOLET, getX(), getY() + 1.6, getZ(), 4, 0.4, 0.8, 0.4, 0);
        sl.sendParticles(PINK, getX(), getY() + 2.2, getZ(), 3, 0.3, 1.0, 0.3, 0);
        if (t >= FALL_TICKS) land(sl, true);
    }

    /** Impact : dégâts de zone (sauf venu d'un météore, déjà fait), cratère de particules, passe en IMPACT. */
    private void land(ServerLevel sl, boolean damage) {
        setPos(landing[0], landing[1], landing[2]);
        setNoGravity(false);
        noPhysics = false;
        double x = landing[0], y = landing[1], z = landing[2];
        if (damage) {
            sl.sendParticles(ParticleTypes.EXPLOSION, x, y + 0.5, z, 3, 0.8, 0.2, 0.8, 0);
            sl.sendParticles(ParticleTypes.LAVA, x, y + 0.5, z, 14, 1.2, 0.2, 1.2, 0);
            for (int i = 0; i < 28; i++) {
                double a = Math.PI * 2 * i / 28;
                sl.sendParticles(i % 2 == 0 ? PINK : VIOLET, x + Math.cos(a) * IMPACT_RADIUS, y + 0.2, z + Math.sin(a) * IMPACT_RADIUS, 2, 0.1, 0.1, 0.1, 0);
            }
            sl.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.5f, 0.7f);
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(IMPACT_RADIUS, 1.5, IMPACT_RADIUS),
                    e -> e != this && e.isAlive() && !(e instanceof Enemy) && e.distanceToSqr(x, y, z) <= (IMPACT_RADIUS + 0.5) * (IMPACT_RADIUS + 0.5))) {
                if (e instanceof net.minecraft.world.entity.player.Player p && (p.isCreative() || p.isSpectator())) continue;
                e.hurt(damageSources().mobAttack(this), IMPACT_DAMAGE * diff());
            }
            for (ServerPlayer p : sl.players()) {
                if (p.distanceToSqr(x, y, z) < 20 * 20) com.wavesurvivor.network.ShakePacket.send(p, 14, 0.6f);
            }
        } else {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.5, z, 10, 0.8, 0.3, 0.8, 0.02);
        }
        entityData.set(HEAT_START, (int) sl.getGameTime());
        setState(IMPACT);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        int st = failleState();
        if (st == FALL || st == IMPACT) return false;
        boolean ok = super.doHurtTarget(target);
        if (ok) {
            setState(PUNCH);
            if (failleHeat() >= 0.5f) target.setSecondsOnFire(3);   // en fusion : met le feu
        }
        return ok;
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (failleState() == FALL && !src.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;   // intouchable en chute
        if (failleHeat() < 0.5f) amount *= 1.3f;   // refroidi : plus fragile
        return super.hurt(src, amount);
    }

    @Override
    public boolean fireImmune() { return true; }

    @Override
    public boolean causeFallDamage(float dist, float mult, DamageSource src) { return false; }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource s) { return SoundEvents.BASALT_HIT; }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() { return SoundEvents.BASALT_BREAK; }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState st) {
        playSound(SoundEvents.BASALT_STEP, 0.8f, 0.6f);
    }
}
