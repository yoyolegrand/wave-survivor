package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.skill.BossSkill;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * execution : le boss se téléporte derrière la cible (à executionBackDistance blocks) et :
 *   - Gagne Strength (executionStrengthDuration ticks, amplifier executionStrengthAmplifier)
 *   - Inflige un coup lourd de "punishStrike" au moment du TP (son enclume + Slowness IV 20t)
 * Cela punit la cible qui ne réagit pas assez vite.
 */
public class SkillExecution extends BossSkill {

    // Constantes du "punish strike" au moment du TP
    private static final float PUNISH_STRIKE_DAMAGE = 6.0f;
    private static final int PUNISH_SLOWNESS_DURATION = 20; // 1s
    private static final int PUNISH_SLOWNESS_AMPLIFIER = 3; // Slowness IV

    public SkillExecution(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(40, config.executionCooldown);
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        // Position derrière la cible : cible - lookDir * distance
        Vec3 lookDir = target.getLookAngle();
        double distance = Math.max(0.5, config.executionBackDistance);
        double x = target.getX() - lookDir.x * distance;
        double y = target.getY();
        double z = target.getZ() - lookDir.z * distance;

        boolean tp = boss.randomTeleport(x, y, z, true);

        if (tp && boss instanceof net.minecraft.world.entity.Mob mob) {
            mob.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        }

        // Strength au boss
        if (config.executionStrengthDuration > 0) {
            boss.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,
                    config.executionStrengthDuration, Math.max(0, config.executionStrengthAmplifier), false, false));
        }

        // ---- Punish strike : coup lourd au moment du TP ----
        if (tp) {
            // Son d'enclume qui s'écrase (fort et grave)
            level.playSound(null, target.blockPosition(),
                    SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.5f, 0.7f);

            // Dégât direct (source = boss pour que le crédit du hit soit sur lui)
            target.hurt(level.damageSources().mobAttack(boss), PUNISH_STRIKE_DAMAGE);

            // Slowness IV pendant 1s
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    PUNISH_SLOWNESS_DURATION, PUNISH_SLOWNESS_AMPLIFIER, false, false));
        }

        // Message + son à la cible
        if (target instanceof ServerPlayer sp) {
            if (config.executionMessage != null && !config.executionMessage.isBlank()) {
                sp.sendSystemMessage(Component.literal(config.executionMessage));
            }
        }
        // Son configurable (garde le comportement d'origine)
        if (config.executionSound != null && !config.executionSound.isBlank()) {
            try {
                SoundEvent snd = BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation(config.executionSound));
                if (snd != null) {
                    level.playSound(null, boss.blockPosition(), snd, SoundSource.HOSTILE, 1.2f, 1.0f);
                }
            } catch (Exception ignore) {}
        }

        WaveSurvivorMod.LOGGER.info("[Skill execution] '{}' -> tp derrière '{}' (tp={}) + strike {}dmg + Slowness IV 1s + Strength {}t amp{}",
                boss.getCustomName() != null ? boss.getCustomName().getString() : boss.getType().toString(),
                target.getName().getString(), tp, PUNISH_STRIKE_DAMAGE,
                config.executionStrengthDuration, config.executionStrengthAmplifier);
    }
}
