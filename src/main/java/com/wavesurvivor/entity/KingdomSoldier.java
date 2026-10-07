package com.wavesurvivor.entity;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * SOLDAT DU ROYAUME — humanoïde allié du mode Kingdom (étape 4b), apparu depuis un Baraquement.
 * Créature propre au mod (base neutre, sans logique d'illageois ni de raid) :
 *  - attaque tous les monstres hostiles (jamais les joueurs, les autres soldats ni les portails) ;
 *  - IMMUNISÉ aux coups des joueurs et aux flèches des tours alliées (qui le traversent, cf. KingdomDefenses) ;
 *  - reste dans le royaume (zone posée par le Baraquement) ; ne disparaît pas tout seul ; ne lâche rien.
 * Rendu : modèle humain + armure et arme portées (KingdomSoldierRenderer).
 */
public class KingdomSoldier extends PathfinderMob {

    /** Ticks passés à 1 PV ou moins en étant attaqué (filet de sécurité contre les soldats « immortels »). */
    private int lowHpTicks = 0;

    public KingdomSoldier(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.ATTACK_KNOCKBACK, 0.5)
                .add(Attributes.ARMOR, 0.0);
    }

    /**
     * Frappe « vivante » : petite fente vers la cible, arc de taille (particules) et bruit d'épée.
     * L'animation du bras est gérée côté client (KingdomSoldierModel).
     */
    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        // Provocation : le monstre frappé riposte immédiatement contre le soldat (archers compris)
        if (hit && target instanceof Mob victim && !(victim instanceof KingdomSoldier)) victim.setTarget(this);
        if (hit && this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            net.minecraft.world.phys.Vec3 dir = target.position().subtract(this.position()).multiply(1, 0, 1);
            if (dir.lengthSqr() > 1.0E-4) {
                dir = dir.normalize();
                this.setDeltaMovement(this.getDeltaMovement().add(dir.x * 0.25, 0.05, dir.z * 0.25));
            }
            double mx = (this.getX() + target.getX()) / 2, mz = (this.getZ() + target.getZ()) / 2;
            double my = this.getY() + this.getBbHeight() * 0.6;
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK, mx, my, mz, 1, 0, 0, 0, 0);
            sl.playSound(null, this.blockPosition(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP,
                    net.minecraft.sounds.SoundSource.NEUTRAL, 0.8f, 0.95f + this.random.nextFloat() * 0.2f);
        }
        return hit;
    }

    /**
     * Indispensable pour l'animation de frappe : les monstres hostiles font avancer le balancement du bras
     * à chaque tick, mais pas les créatures neutres (base PathfinderMob) → sans ça, le coup ne s'anime jamais.
     */
    @Override
    public void aiStep() {
        this.updateSwingTime();
        super.aiStep();
        if (!this.level().isClientSide) {
            // Filet de sécurité : un soldat bloqué à 1 PV ou moins alors qu'il se fait attaquer finit par mourir (5 s)
            boolean recentlyHurt = this.tickCount - this.getLastHurtByMobTimestamp() < 60;
            if (this.isAlive() && this.getHealth() <= 1.0f && recentlyHurt) {
                if (++lowHpTicks >= 100) this.kill();
            } else {
                lowHpTicks = 0;
            }
        }
        // PV visibles dans le nom (mis à jour 2 fois par seconde)
        if (!this.level().isClientSide && this.tickCount % 10 == 0) {
            this.setCustomName(net.minecraft.network.chat.Component.literal(com.wavesurvivor.i18n.WSLang.t("kingdom.soldier_name")
                    + " §c❤ §f" + (int) Math.ceil(this.getHealth()) + "§7/" + (int) Math.ceil(this.getMaxHealth())));
        }
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true));
        this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1.0));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8f));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, Player.class, KingdomSoldier.class));
        // mustSee = false : il repère les monstres même derrière un obstacle
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Mob.class, 5, false, false,
                e -> (e instanceof Enemy || com.wavesurvivor.horde.spawn.MobRegistry.get(e.getUUID()) != null)
                        && !(e instanceof KingdomSoldier) && !(e instanceof BrecheEntity)));
    }

    /** Immunisé aux joueurs et aux flèches des tours du royaume. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof Player) return false;
        Entity direct = source.getDirectEntity();
        if (direct != null && direct.getPersistentData().getBoolean("ws_tower_arrow")) return false;
        float before = this.getHealth();
        boolean ok = super.hurt(source, amount);
        // Mémoire du dernier coup (commande /ws debug soldier)
        lastDmgAsked = amount;
        lastDmgTaken = before - this.getHealth();
        lastDmgType = source.getMsgId();
        lastDmgBy = source.getEntity() != null ? source.getEntity().getName().getString() : "-";
        lastDmgTick = this.tickCount;
        lastDmgApplied = ok;
        return ok;
    }

    // ─── Diagnostic (/ws debug soldier) ───
    private float lastDmgAsked, lastDmgTaken;
    private String lastDmgType = "-", lastDmgBy = "-";
    private int lastDmgTick = -1;
    private boolean lastDmgApplied;

    /** Résumé lisible de l'état du soldat (PV exacts, effets, dernier coup, invulnérabilité, cible). */
    public java.util.List<String> debugLines() {
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add(String.format(java.util.Locale.ROOT, "§f❤ %.2f §7/ %.1f §8· §7armor %d §8· §7alive %s §8· §7dying %s",
                this.getHealth(), this.getMaxHealth(), this.getArmorValue(), this.isAlive(), this.isDeadOrDying()));
        StringBuilder fx = new StringBuilder();
        for (var e : this.getActiveEffects()) {
            if (fx.length() > 0) fx.append(", ");
            fx.append(e.getEffect().getDisplayName().getString()).append(' ').append(e.getAmplifier() + 1)
                    .append(" (").append(e.getDuration() / 20).append("s)");
        }
        out.add("§7effects: §f" + (fx.length() > 0 ? fx : "-"));
        out.add(lastDmgTick < 0 ? "§7last hit: §f-"
                : String.format(java.util.Locale.ROOT, "§7last hit: §f%.2f asked → %.2f taken §8(%s, applied %s)§7 by §f%s §7%d ticks ago",
                lastDmgAsked, lastDmgTaken, lastDmgType, lastDmgApplied, lastDmgBy, this.tickCount - lastDmgTick));
        out.add("§7invulnerableTime: §f" + this.invulnerableTime + " §8· §7invulnerable flag: §f" + this.isInvulnerable()
                + " §8· §7low-HP timer: §f" + lowHpTicks + "/100");
        out.add("§7target: §f" + (this.getTarget() != null ? this.getTarget().getName().getString() : "-"));
        return out;
    }

    /** Alliés : les joueurs et les autres soldats. */
    @Override
    public boolean isAlliedTo(Entity other) {
        return other instanceof KingdomSoldier || other instanceof Player || super.isAlliedTo(other);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
