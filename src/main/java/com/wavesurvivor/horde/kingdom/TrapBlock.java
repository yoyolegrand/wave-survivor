package com.wavesurvivor.horde.kingdom;

import com.wavesurvivor.entity.BrecheEntity;
import com.wavesurvivor.entity.KingdomSoldier;
import com.wavesurvivor.horde.spawn.MobRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;

/**
 * PIÈGE du mode Kingdom (à usage unique) : plaque posée au sol dans le claim, sans collision.
 * Se déclenche quand un monstre hostile marche dessus, puis disparaît.
 *  - PICS : gros dégâts + lenteur sur place ;  - FEU : enflamme les monstres à 3 blocs ;
 *  - GIVRE : gèle les monstres à 4 blocs (immobiles 3 s) ;  - EXPLOSIF : souffle qui ne blesse QUE les monstres ;
 *  - COLLET : immobilise un gros monstre 6 s (idéal contre les béliers).
 */
public class TrapBlock extends Block {

    public enum Kind { SPIKES, FIRE, FROST, EXPLOSIVE, SNARE }

    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 1, 15);
    private final Kind kind;

    public TrapBlock(Properties props, Kind kind) {
        super(props);
        this.kind = kind;
    }

    public Kind kind() { return kind; }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel sl) KingdomDefenses.registerTrap(sl, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) KingdomDefenses.unregisterTrap(pos);
        super.onRemove(state, level, pos, newState, moved);
    }

    /** Monstre hostile (ni joueur, ni soldat du royaume, ni portail). */
    static boolean isHostile(Entity e) {
        if (!(e instanceof Mob m) || !m.isAlive()) return false;
        if (m instanceof KingdomSoldier || m instanceof BrecheEntity) return false;
        return m instanceof Enemy || MobRegistry.get(m.getUUID()) != null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!(level instanceof ServerLevel sl) || !isHostile(entity)) return;
        trigger(sl, pos, (Mob) entity);
        sl.removeBlock(pos, false);
    }

    private List<Mob> hostilesAround(ServerLevel l, BlockPos pos, double r) {
        return l.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(r, 2, r), TrapBlock::isHostile);
    }

    private void trigger(ServerLevel l, BlockPos pos, Mob victim) {
        Vec3 c = Vec3.atBottomCenterOf(pos);
        switch (kind) {
            case SPIKES -> {
                for (Mob m : hostilesAround(l, pos, 1.5)) {
                    m.hurt(l.damageSources().cactus(), 12f);
                    m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
                }
                l.sendParticles(ParticleTypes.CRIT, c.x, c.y + 0.3, c.z, 30, 0.6, 0.3, 0.6, 0.3);
                l.playSound(null, pos, SoundEvents.TRIDENT_HIT, SoundSource.BLOCKS, 1.2f, 0.8f);
            }
            case FIRE -> {
                for (Mob m : hostilesAround(l, pos, 3)) m.setSecondsOnFire(8);
                l.sendParticles(ParticleTypes.FLAME, c.x, c.y + 0.5, c.z, 80, 2, 0.5, 2, 0.05);
                l.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.2f, 0.8f);
            }
            case FROST -> {
                for (Mob m : hostilesAround(l, pos, 4)) {
                    m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 9));
                    m.setTicksFrozen(Math.max(m.getTicksFrozen(), 160));
                }
                l.sendParticles(ParticleTypes.SNOWFLAKE, c.x, c.y + 0.8, c.z, 100, 3, 0.8, 3, 0.02);
                l.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.2f, 1.6f);
            }
            case EXPLOSIVE -> {
                for (Mob m : hostilesAround(l, pos, 3.5)) {
                    m.hurt(l.damageSources().explosion(null, null), 14f);
                    Vec3 push = m.position().subtract(c).normalize().scale(0.8);
                    m.push(push.x, 0.4, push.z);
                }
                l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
                l.playSound(null, pos, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.5f, 1f);
            }
            case SNARE -> {
                Mob target = victim;
                for (Mob m : hostilesAround(l, pos, 2)) if (m.getMaxHealth() > target.getMaxHealth()) target = m;
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 9));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1));
                target.getNavigation().stop();
                l.sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY() + 0.5, target.getZ(), 25, 0.4, 0.4, 0.4, 0.02);
                l.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.4f, 0.7f);
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(com.wavesurvivor.i18n.WSLang.c("kingdom.trap.desc." + kind.name().toLowerCase()));
        tooltip.add(com.wavesurvivor.i18n.WSLang.c("kingdom.trap.hint"));
    }
}
