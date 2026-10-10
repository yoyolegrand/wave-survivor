package com.wavesurvivor.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.SpellcasterIllager;
import net.minecraft.world.entity.monster.Stray;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * SORCIER DES NEIGES (horde des Pics Gelés) — illageois lanceur de sorts, sur la base de l'Évocateur.
 * Fragile mais dangereux à distance : il fuit les joueurs proches et lance trois sorts.
 *  - Pics de glace : éclats de glace sous la cible et autour (6 dégâts, Lenteur II, gel).
 *  - Blizzard : tempête de neige de 6 blocs autour de la cible (Lenteur, gel pendant 5 s).
 *  - Appel du froid : invoque 2 Vagabonds (squelettes des neiges) à la place des Vex.
 * Insensible au gel.
 */
public class SnowSorcerer extends SpellcasterIllager {

    public SnowSorcerer(EntityType<? extends SpellcasterIllager> type, Level level) {
        super(type, level);
        this.xpReward = 15;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, 20.0)
                .add(Attributes.MAX_HEALTH, 30.0);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SpellcasterCastingSpellGoal());
        this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, 8.0f, 0.6, 1.0));
        this.goalSelector.addGoal(4, new SummonStraysSpellGoal());
        this.goalSelector.addGoal(5, new FrostSpikesSpellGoal());
        this.goalSelector.addGoal(6, new BlizzardSpellGoal());
        this.goalSelector.addGoal(8, new RandomStrollGoal(this, 0.6));
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 3.0f, 1.0f));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 8.0f));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, Raider.class).setAlertOthers());
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true).setUnseenMemoryTicks(300));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, KingdomSoldier.class, true));
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, false).setUnseenMemoryTicks(300));
        this.targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, IronGolem.class, false,
                e -> !(e instanceof FrostGolem)));
    }

    @Override
    public boolean canFreeze() {
        return false; // insensible au gel
    }

    @Override
    public void applyRaidBuffs(int wave, boolean unused) {}

    @Override
    public SoundEvent getCelebrateSound() { return SoundEvents.EVOKER_CELEBRATE; }

    @Override
    protected SoundEvent getAmbientSound() { return SoundEvents.EVOKER_AMBIENT; }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.EVOKER_DEATH; }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.EVOKER_HURT; }

    @Override
    protected SoundEvent getCastingSoundEvent() { return SoundEvents.EVOKER_CAST_SPELL; }

    /** Cibles de ses sorts : tout ce qui vit autour, sauf les monstres (ses alliés). */
    private boolean isFoe(LivingEntity e) {
        if (e == this || !e.isAlive() || e instanceof Enemy) return false;
        return !(e instanceof Player p) || (!p.isCreative() && !p.isSpectator());
    }

    // ─── Sort 1 : Pics de glace ───

    class FrostSpikesSpellGoal extends SpellcasterUseSpellGoal {
        @Override
        protected int getCastingTime() { return 40; }

        @Override
        protected int getCastingInterval() { return 100; }

        @Override
        protected void performSpellCasting() {
            LivingEntity target = getTarget();
            if (target == null || !(level() instanceof ServerLevel sl)) return;
            Vec3 c = target.position();
            spike(sl, c.x, c.y, c.z);
            for (int i = 0; i < 3; i++) {
                double a = random.nextDouble() * Math.PI * 2, r = 1.5 + random.nextDouble() * 1.5;
                spike(sl, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r);
            }
            sl.playSound(null, target.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.2f, 0.7f);
        }

        private void spike(ServerLevel sl, double x, double y, double z) {
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()),
                    x, y + 0.6, z, 25, 0.25, 0.6, 0.25, 0.15);
            sl.sendParticles(ParticleTypes.SNOWFLAKE, x, y + 1.0, z, 10, 0.2, 0.6, 0.2, 0.05);
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(x - 1.2, y - 0.5, z - 1.2, x + 1.2, y + 2.5, z + 1.2))) {
                if (!isFoe(e)) continue;
                e.hurt(damageSources().indirectMagic(SnowSorcerer.this, SnowSorcerer.this), 6.0f);
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                e.setTicksFrozen(Math.max(e.getTicksFrozen(), 140));
            }
        }

        @Override
        protected SoundEvent getSpellPrepareSound() { return SoundEvents.EVOKER_PREPARE_ATTACK; }

        @Override
        protected IllagerSpell getSpell() { return IllagerSpell.FANGS; }
    }

    // ─── Sort 2 : Blizzard ───

    class BlizzardSpellGoal extends SpellcasterUseSpellGoal {
        @Override
        protected int getCastingTime() { return 60; }

        @Override
        protected int getCastingInterval() { return 300; }

        @Override
        protected void performSpellCasting() {
            LivingEntity target = getTarget();
            if (target == null || !(level() instanceof ServerLevel sl)) return;
            Vec3 c = target.position();
            sl.sendParticles(ParticleTypes.SNOWFLAKE, c.x, c.y + 1.5, c.z, 200, 4.0, 1.5, 4.0, 0.08);
            sl.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 2.5, c.z, 40, 4.0, 0.5, 4.0, 0.02);
            sl.playSound(null, target.blockPosition(), SoundEvents.POWDER_SNOW_BREAK, SoundSource.HOSTILE, 2.0f, 0.5f);
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(BlockPos.containing(c)).inflate(6, 3, 6))) {
                if (!isFoe(e)) continue;
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
                e.setTicksFrozen(Math.max(e.getTicksFrozen(), 240));
            }
        }

        @Override
        protected SoundEvent getSpellPrepareSound() { return SoundEvents.EVOKER_PREPARE_WOLOLO; }

        @Override
        protected IllagerSpell getSpell() { return IllagerSpell.BLINDNESS; }
    }

    // ─── Sort 3 : Appel du froid (2 Vagabonds) ───

    class SummonStraysSpellGoal extends SpellcasterUseSpellGoal {
        @Override
        public boolean canUse() {
            if (!super.canUse()) return false;
            // pas plus de 4 Vagabonds autour de lui
            return level().getEntitiesOfClass(Stray.class, getBoundingBox().inflate(16)).size() < 4;
        }

        @Override
        protected int getCastingTime() { return 100; }

        @Override
        protected int getCastingInterval() { return 400; }

        @Override
        protected void performSpellCasting() {
            if (!(level() instanceof ServerLevel sl)) return;
            for (int i = 0; i < 2; i++) {
                Stray s = EntityType.STRAY.create(sl);
                if (s == null) continue;
                BlockPos p = blockPosition().offset(-2 + random.nextInt(5), 0, -2 + random.nextInt(5));
                s.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, random.nextFloat() * 360f, 0f);
                DifficultyInstance diff = sl.getCurrentDifficultyAt(p);
                s.finalizeSpawn(sl, diff, MobSpawnType.MOB_SUMMONED, null, null);
                if (getTarget() != null) s.setTarget(getTarget());
                sl.addFreshEntityWithPassengers(s);
                // Partie en cours : le Vagabond fait partie de la horde (compte dans la vague, difficulté, mutateurs, nettoyage)
                if (com.wavesurvivor.horde.HordeManager.get().isRunning()) {
                    com.wavesurvivor.horde.model.HordeEntity t = new com.wavesurvivor.horde.model.HordeEntity();
                    t.entityType = "minecraft:stray";
                    t.customName = null;
                    com.wavesurvivor.horde.spawn.MobRegistry.register(s, t);
                }
                sl.sendParticles(ParticleTypes.SNOWFLAKE, s.getX(), s.getY() + 1, s.getZ(), 30, 0.3, 0.8, 0.3, 0.05);
            }
        }

        @Nullable
        @Override
        protected SoundEvent getSpellPrepareSound() { return SoundEvents.EVOKER_PREPARE_SUMMON; }

        @Override
        protected IllagerSpell getSpell() { return IllagerSpell.SUMMON_VEX; }
    }
}
