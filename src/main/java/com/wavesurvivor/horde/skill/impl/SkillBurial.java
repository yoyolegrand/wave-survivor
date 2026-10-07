package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.skill.BossSkill;
import com.wavesurvivor.horde.skill.DelayedActionScheduler;
import com.wavesurvivor.horde.skill.NecroTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * burial : la cible est "agrippée par la terre" (Lenteur forte + Fatigue + Saut bloqué) pendant burialDuration,
 * des gerbes de terre jaillissent autour d'elle, puis burialMinionCount morts (burialMinionEntity) surgissent
 * à ~2 blocs (ils comptent dans la vague, sans loot).
 */
public class SkillBurial extends BossSkill {

    public SkillBurial(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(80, config.burialCooldown);
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        double range = Math.max(3, config.burialRange);
        if (target == null || boss.distanceToSqr(target) > range * range) return;

        int dur = Math.max(20, config.burialDuration);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, dur, Math.max(0, config.burialSlowAmplifier), false, true));
        target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, dur, 1, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.JUMP, dur, 128, false, false)); // saut bloqué

        if (target instanceof ServerPlayer p && config.burialMessage != null && !config.burialMessage.isBlank()) {
            p.sendSystemMessage(Component.literal(config.burialMessage));
        }
        NecroTracker.playSound(level, target.blockPosition(), config.burialSound, 0.6f);

        // "Mains" de terre : gerbes de particules de terre autour de la cible pendant l'emprise
        var dirt = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ROOTED_DIRT.defaultBlockState());
        for (int t = 0; t < dur; t += 10) {
            DelayedActionScheduler.schedule(level.getServer(), t, () -> {
                if (!target.isAlive()) return;
                for (int i = 0; i < 4; i++) {
                    double a = Math.PI / 2 * i + level.random.nextDouble() * 0.5;
                    level.sendParticles(dirt, target.getX() + Math.cos(a) * 0.7, target.getY() + 0.2,
                            target.getZ() + Math.sin(a) * 0.7, 6, 0.1, 0.3, 0.1, 0.05);
                }
            }, "burial hands");
        }

        // Les morts surgissent après 1s
        int count = Math.max(0, config.burialMinionCount);
        BlockPos center = target.blockPosition();
        DelayedActionScheduler.schedule(level.getServer(), 20, () -> {
            for (int i = 0; i < count; i++) {
                double a = Math.PI * 2 * i / Math.max(1, count) + level.random.nextDouble();
                int x = center.getX() + (int) Math.round(Math.cos(a) * 2);
                int z = center.getZ() + (int) Math.round(Math.sin(a) * 2);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos at = new BlockPos(x, y, z);
                level.sendParticles(dirt, x + 0.5, y + 0.3, z + 0.5, 25, 0.3, 0.4, 0.3, 0.1);
                NecroTracker.spawnMinion(level, config.burialMinionEntity, at,
                        Math.max(1, config.burialMinionHealth), com.wavesurvivor.i18n.WSLang.t("§6Déterré"));
            }
        }, "burial minions");
    }
}
