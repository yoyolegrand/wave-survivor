package com.wavesurvivor.entity;

import net.minecraft.core.Rotations;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * ANCREUR (monstre de la Faille) — contrôle, modèle de Claude chat (AncreurModel).
 * États : 0 = PORTE (marche, ancre sur le dos), 1 = PLANTE (20 ticks : lève l'ancre puis l'abat, plantée à 55 %), 2 = TIENT.
 *  - ANCRE : cible à moins de 12 blocs, recharge 15 s. Il s'arrête et plante l'ancre ≈ 2,2 blocs devant lui ; elle enchaîne le joueur
 *    le plus proche dans 10 blocs : impossible de s'éloigner à plus de 6 blocs de l'ancre (tiré en arrière) pendant 8 s, ou jusqu'à ce que
 *    l'ancre soit brisée (petite cible frappable, 20 PV). Il reste en TIENT pendant ce temps.
 *  - La chaîne (laisse) est dessinée côté client par {@link com.wavesurvivor.client.AncreurChainFx} à partir de l'ancre et du joueur synchronisés.
 */
public class Ancreur extends FailleMonster {

    public static final int CARRY = 0, PLANT = 1, HOLD = 2;
    public static final int PLANT_TICKS = 20, HOLD_TICKS = 160, ANCHOR_COOLDOWN = 300;
    public static final double ANCHOR_RANGE = 12, CHAIN_PICK = 10, CHAIN_LEN = 6, ANCHOR_AHEAD = 2.2;
    public static final float ANCHOR_HP = 20f;
    public static final String ANCHOR_TAG = "ws_ancreur_anchor";
    private static final DustParticleOptions TEAL = new DustParticleOptions(new Vector3f(0.25f, 0.95f, 0.85f), 1.0f);

    /** Joueur enchaîné (id réseau, -1 = aucun) et position de l'ancre plantée (x, y, z rangés dans un « Rotations »). */
    private static final EntityDataAccessor<Integer> CHAINED = SynchedEntityData.defineId(Ancreur.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Rotations> ANCHOR = SynchedEntityData.defineId(Ancreur.class, EntityDataSerializers.ROTATIONS);

    private long anchorReadyAt = 0;
    private float anchorHp;
    private UUID hitbox;

    public Ancreur(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 14;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 80.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.ATTACK_DAMAGE, 9.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(CHAINED, -1);
        entityData.define(ANCHOR, new Rotations(0, 0, 0));
    }

    @Override
    protected int stateTicks(int s) {
        return s == PLANT ? PLANT_TICKS : s == HOLD ? HOLD_TICKS : 20;
    }

    /** Joueur enchaîné (côté client aussi), ou null. */
    public Entity chained() {
        int id = entityData.get(CHAINED);
        return id < 0 ? null : level().getEntity(id);
    }

    /** Position de l'ancre plantée (valable en état TIENT). */
    public Vec3 anchorPos() {
        Rotations r = entityData.get(ANCHOR);
        return new Vec3(r.getX(), r.getY(), r.getZ());
    }

    /** Plante l'ancre (aussi utilisé par la commande de test, sans condition de distance ni de recharge). */
    public boolean startAnchor() {
        if (level().isClientSide || failleState() != CARRY) return false;
        anchorReadyAt = level().getGameTime() + ANCHOR_COOLDOWN;
        getNavigation().stop();
        LivingEntity tg = getTarget();
        if (tg != null) {
            getLookControl().setLookAt(tg, 60f, 60f);
            setYRot((float) Math.toDegrees(Math.atan2(-(tg.getX() - getX()), tg.getZ() - getZ())));
            yBodyRot = getYRot();
        }
        setState(PLANT);
        playSound(SoundEvents.CHAIN_PLACE, 1.6f, 0.6f);
        return true;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel sl)) return;
        int st = failleState();
        long t = stateAge();
        if (st == CARRY) {
            LivingEntity tg = getTarget();
            if (tickCount % 5 == 0 && validTarget(tg) && sl.getGameTime() >= anchorReadyAt && distanceTo(tg) <= ANCHOR_RANGE) startAnchor();
            return;
        }
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        if (st == PLANT) {
            yBodyRot = getYRot();
            if (t == 11) plant(sl);             // l'ancre touche le sol (55 % de l'animation)
            if (t >= PLANT_TICKS) chain(sl);
        } else if (st == HOLD) {
            yBodyRot = getYRot();
            hold(sl, t);
        }
    }

    /** L'ancre s'abat devant lui : secousse de terre et d'obsidienne, cible frappable. */
    private void plant(ServerLevel sl) {
        Vec3 fwd = Vec3.directionFromRotation(0, getYRot());
        Vec3 p = new Vec3(getX() + fwd.x * ANCHOR_AHEAD, getY(), getZ() + fwd.z * ANCHOR_AHEAD);
        entityData.set(ANCHOR, new Rotations((float) p.x, (float) p.y, (float) p.z));
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OBSIDIAN.defaultBlockState()), p.x, p.y + 0.1, p.z, 24, 0.6, 0.1, 0.6, 0.15);
        sl.sendParticles(TEAL, p.x, p.y + 0.4, p.z, 12, 0.5, 0.3, 0.5, 0);
        sl.playSound(null, p.x, p.y, p.z, SoundEvents.ANVIL_LAND, net.minecraft.sounds.SoundSource.HOSTILE, 1.4f, 0.5f);
        removeHitbox(sl);
        Interaction hb = EntityType.INTERACTION.create(sl);
        if (hb != null) {
            hb.moveTo(p.x, p.y, p.z, 0f, 0f);
            CompoundTag tag = hb.saveWithoutId(new CompoundTag());
            tag.putFloat("width", 1.1f);
            tag.putFloat("height", 1.8f);
            tag.putBoolean("response", false);
            hb.load(tag);
            hb.addTag(ANCHOR_TAG);
            if (sl.addFreshEntity(hb)) hitbox = hb.getUUID();
        }
        anchorHp = ANCHOR_HP;
    }

    /** Enchaîne le joueur le plus proche de l'ancre (10 blocs) ; personne : il reprend la marche. */
    private void chain(ServerLevel sl) {
        Vec3 a = anchorPos();
        ServerPlayer best = null;
        double bd = CHAIN_PICK * CHAIN_PICK;
        for (ServerPlayer p : sl.players()) {
            if (!validTarget(p)) continue;
            double d = p.distanceToSqr(a);
            if (d <= bd) { bd = d; best = p; }
        }
        if (best == null) { release(sl); return; }
        entityData.set(CHAINED, best.getId());
        setState(HOLD);
        sl.playSound(null, best.blockPosition(), SoundEvents.CHAIN_PLACE, net.minecraft.sounds.SoundSource.HOSTILE, 1.6f, 0.5f);
    }

    /** Tient la chaîne : le joueur ne peut pas s'éloigner à plus de 6 blocs de l'ancre. */
    private void hold(ServerLevel sl, long t) {
        Entity e = chained();
        if (!(e instanceof ServerPlayer p) || !validTarget(p) || p.level() != sl || t >= HOLD_TICKS || anchorHp <= 0) { release(sl); return; }
        getLookControl().setLookAt(p, 30f, 30f);
        Vec3 a = anchorPos();
        Vec3 d = new Vec3(p.getX() - a.x, 0, p.getZ() - a.z);
        double dist = d.length();
        if (dist > CHAIN_LEN) {
            Vec3 back = d.normalize().scale(-1);
            if (dist > CHAIN_LEN + 2.5) {   // trop loin (saut, téléportation) : ramené au bout de la chaîne
                Vec3 to = new Vec3(a.x, 0, a.z).add(d.normalize().scale(CHAIN_LEN - 0.3));
                p.teleportTo(to.x, p.getY(), to.z);
            } else {
                double k = 0.25 + (dist - CHAIN_LEN) * 0.35;
                p.setDeltaMovement(back.x * k, Math.max(p.getDeltaMovement().y, 0.02), back.z * k);
                p.hurtMarked = true;
            }
            if (t % 6 == 0) sl.playSound(null, p.blockPosition(), SoundEvents.CHAIN_STEP, net.minecraft.sounds.SoundSource.HOSTILE, 1f, 0.6f);
        }
        if (t % 8 == 0) sl.sendParticles(TEAL, a.x, a.y + 1.0, a.z, 2, 0.2, 0.3, 0.2, 0);
    }

    /** Libère le joueur, retire l'ancre plantée et reprend la marche. */
    private void release(ServerLevel sl) {
        entityData.set(CHAINED, -1);
        removeHitbox(sl);
        if (failleState() != CARRY) setState(CARRY);
    }

    private void removeHitbox(ServerLevel sl) {
        if (hitbox != null) {
            Entity h = sl.getEntity(hitbox);
            if (h != null) h.discard();
            hitbox = null;
        }
    }

    /** Coup porté sur l'ancre plantée. */
    private void hitAnchor(ServerLevel sl, float dmg) {
        anchorHp -= dmg;
        Vec3 a = anchorPos();
        sl.sendParticles(ParticleTypes.CRIT, a.x, a.y + 0.9, a.z, 8, 0.3, 0.4, 0.3, 0.1);
        sl.playSound(null, a.x, a.y, a.z, SoundEvents.ANVIL_HIT, net.minecraft.sounds.SoundSource.HOSTILE, 1f, 1.2f);
        if (anchorHp <= 0) {
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CHAIN.defaultBlockState()), a.x, a.y + 0.9, a.z, 30, 0.4, 0.6, 0.4, 0.2);
            sl.playSound(null, a.x, a.y, a.z, SoundEvents.CHAIN_BREAK, net.minecraft.sounds.SoundSource.HOSTILE, 2f, 0.6f);
            release(sl);
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        return failleState() == CARRY && super.doHurtTarget(target);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel sl) {
            entityData.set(CHAINED, -1);
            removeHitbox(sl);
        }
        super.remove(reason);
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() { return SoundEvents.CHAIN_STEP; }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource s) { return SoundEvents.IRON_GOLEM_HURT; }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() { return SoundEvents.IRON_GOLEM_DEATH; }

    /** Ancre plantée frappée au corps à corps (bus Forge, enregistré dans WaveSurvivorMod). */
    public static class Events {
        @SubscribeEvent
        public void onAttack(AttackEntityEvent e) {
            Entity t = e.getTarget();
            if (!(t instanceof Interaction) || !t.getTags().contains(ANCHOR_TAG)) return;
            e.setCanceled(true);
            if (!(t.level() instanceof ServerLevel sl)) return;
            Player pl = e.getEntity();
            float dmg = (float) Math.max(1.0, pl.getAttributeValue(Attributes.ATTACK_DAMAGE));
            for (Ancreur a : sl.getEntitiesOfClass(Ancreur.class, t.getBoundingBox().inflate(6))) {
                if (t.getUUID().equals(a.hitbox)) { a.hitAnchor(sl, dmg); return; }
            }
            t.discard();   // ancre orpheline (Ancreur disparu)
        }

        /** Ancre restée dans un monde rechargé (son Ancreur ne la connaît plus) : retirée. */
        @SubscribeEvent
        public void onJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent e) {
            if (e.loadedFromDisk() && e.getEntity() instanceof Interaction && e.getEntity().getTags().contains(ANCHOR_TAG)) e.setCanceled(true);
        }
    }
}
