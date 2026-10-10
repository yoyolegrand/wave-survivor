package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.skill.BossSkill;
import com.wavesurvivor.horde.skill.DelayedActionScheduler;
import com.wavesurvivor.horde.skill.NecroTracker;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * shatter (Prise dans la glace) : un joueur est enfermé dans une coque de glace, immobilisé et fatigué (il casse
 * lentement). Il subit shatterDamage par seconde ; ses ALLIÉS peuvent briser la glace pour le libérer. Si personne
 * ne la brise avant shatterDuration ticks, la glace éclate : shatterBurst dégâts. Mécanique de coopération.
 */
public class SkillShatter extends BossSkill {

    private static final Random RNG = new Random();

    public SkillShatter(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(100, config.shatterCooldown);
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        double range = Math.max(4, config.shatterRange);
        ServerPlayer victim = null;
        if (target instanceof ServerPlayer sp && sp.isAlive() && !sp.isCreative() && !sp.isSpectator()
                && boss.distanceToSqr(sp) <= range * range) {
            victim = sp;
        } else {
            List<ServerPlayer> near = level.getEntitiesOfClass(ServerPlayer.class, boss.getBoundingBox().inflate(range),
                    p -> p.isAlive() && !p.isCreative() && !p.isSpectator());
            if (!near.isEmpty()) victim = near.get(RNG.nextInt(near.size()));
        }
        if (victim == null) return;

        Block block = Blocks.PACKED_ICE;
        try {
            Block b = BuiltInRegistries.BLOCK.get(new ResourceLocation(config.shatterBlock.trim()));
            if (b != Blocks.AIR) block = b;
        } catch (Exception ignored) {}
        final Block ice = block;

        // Coque : 4 côtés sur 2 hauteurs + le dessus
        BlockPos b = victim.blockPosition();
        List<BlockPos> shell = new ArrayList<>();
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int h = 0; h < 2; h++) for (int[] s : sides) shell.add(b.offset(s[0], h, s[1]));
        shell.add(b.above(2));
        List<BlockPos> placed = new ArrayList<>();
        for (BlockPos bp : shell) {
            BlockState st = level.getBlockState(bp);
            if (st.isAir() || st.canBeReplaced()) {
                level.setBlockAndUpdate(bp, ice.defaultBlockState());
                placed.add(bp);
            }
        }
        if (placed.size() < 3) { // pas assez de place pour une vraie coque : annulé
            for (BlockPos bp : placed) if (level.getBlockState(bp).is(ice)) level.setBlockAndUpdate(bp, Blocks.AIR.defaultBlockState());
            return;
        }

        int dur = Math.max(20, config.shatterDuration);
        victim.teleportTo(b.getX() + 0.5, b.getY(), b.getZ() + 0.5);
        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, dur + 20, 6, false, true));
        victim.addEffect(new MobEffectInstance(MobEffects.JUMP, dur + 20, 128, false, false));
        victim.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, dur + 20, 2, false, true));
        victim.setTicksFrozen(Math.max(victim.getTicksFrozen(), victim.getTicksRequiredToFreeze() - 1));
        level.sendParticles(ParticleTypes.SNOWFLAKE, victim.getX(), victim.getY() + 1, victim.getZ(), 40, 0.5, 0.9, 0.5, 0.05);
        NecroTracker.playSound(level, b, config.shatterSound, 1.2f);

        String msg = config.shatterMessage != null && !config.shatterMessage.isBlank()
                ? config.shatterMessage : WSLang.t("frost.shatter", victim.getGameProfile().getName());
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, boss.getBoundingBox().inflate(range + 16))) {
            p.displayClientMessage(Component.literal(msg), true);
        }
        watch(level, victim, placed, ice, 0, dur);
    }

    /** Suivi seconde par seconde : libération si la glace est brisée, dégâts, puis éclatement final. */
    private void watch(ServerLevel level, ServerPlayer victim, List<BlockPos> placed, Block ice, int elapsed, int dur) {
        DelayedActionScheduler.schedule(level.getServer(), 20, () -> {
            int t = elapsed + 20;
            boolean broken = false;
            for (BlockPos bp : placed) if (!level.getBlockState(bp).is(ice)) { broken = true; break; }
            if (!victim.isAlive() || victim.hasDisconnected() || broken) {
                release(level, victim, placed, ice, true);
                return;
            }
            if (t >= dur) {
                victim.hurt(level.damageSources().freeze(), (float) Math.max(0, config.shatterBurst));
                release(level, victim, placed, ice, false);
                return;
            }
            if (config.shatterDamage > 0) victim.hurt(level.damageSources().freeze(), (float) config.shatterDamage);
            level.sendParticles(ParticleTypes.SNOWFLAKE, victim.getX(), victim.getY() + 1, victim.getZ(), 12, 0.4, 0.8, 0.4, 0.02);
            watch(level, victim, placed, ice, t, dur);
        }, "shatter watch");
    }

    private void release(ServerLevel level, ServerPlayer victim, List<BlockPos> placed, Block ice, boolean freed) {
        for (BlockPos bp : placed) if (level.getBlockState(bp).is(ice)) level.setBlockAndUpdate(bp, Blocks.AIR.defaultBlockState());
        victim.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        victim.removeEffect(MobEffects.JUMP);
        victim.removeEffect(MobEffects.DIG_SLOWDOWN);
        victim.setTicksFrozen(0);
        BlockPos at = victim.blockPosition();
        level.sendParticles(ParticleTypes.SNOWFLAKE, victim.getX(), victim.getY() + 1, victim.getZ(), 50, 0.8, 1.0, 0.8, 0.1);
        NecroTracker.playSound(level, at, config.shatterSound, freed ? 1.0f : 1.6f);
    }
}
