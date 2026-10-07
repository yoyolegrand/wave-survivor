package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.skill.BossSkill;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

import java.util.Random;

/**
 * spectral_vanish : au trigger (roll chance vanishTriggerChance), le boss devient invisible
 * (Invisibility potion), gagne Speed, et se téléporte dans un rayon vanishTeleportRadius.
 */
public class SkillSpectralVanish extends BossSkill {

    private static final Random RNG = new Random();

    public SkillSpectralVanish(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(20, config.vanishCooldown);
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        // Roll de chance
        if (RNG.nextDouble() > config.vanishTriggerChance) {
            WaveSurvivorMod.LOGGER.debug("[Skill spectral_vanish] roll échoué (chance {})", config.vanishTriggerChance);
            return;
        }

        // Effets
        if (config.vanishInvisibilityDuration > 0) {
            boss.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,
                    config.vanishInvisibilityDuration, 0, false, false));
        }
        if (config.vanishSpeedDuration > 0) {
            boss.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,
                    config.vanishSpeedDuration, Math.max(0, config.vanishSpeedAmplifier), false, false));
        }

        // Son enderman TP au déclenchement (avant le tp lui-même, joue à la position de départ)
        level.playSound(null, boss.blockPosition(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.5f, 1.0f);

        // Téléportation aléatoire dans le rayon
        double radius = Math.max(2, config.vanishTeleportRadius);
        boolean teleported = false;
        for (int i = 0; i < 12; i++) {
            double angle = RNG.nextDouble() * Math.PI * 2;
            double dist = radius * (0.5 + RNG.nextDouble() * 0.5);
            double x = boss.getX() + Math.cos(angle) * dist;
            double z = boss.getZ() + Math.sin(angle) * dist;
            double y = boss.getY();
            if (boss.randomTeleport(x, y, z, true)) {
                teleported = true;
                break;
            }
        }

        // Son enderman TP au point d'arrivée (donne l'impression du warp)
        if (teleported) {
            level.playSound(null, boss.blockPosition(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.2f, 1.2f);
        }

        // Son
        if (config.vanishSound != null && !config.vanishSound.isBlank()) {
            try {
                SoundEvent snd = BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation(config.vanishSound));
                if (snd != null) {
                    level.playSound(null, boss.blockPosition(), snd, SoundSource.HOSTILE, 1.0f, 1.0f);
                }
            } catch (Exception ignore) {}
        }

        WaveSurvivorMod.LOGGER.info("[Skill spectral_vanish] '{}' → invisible {}t, speed {}t, tp={}",
                boss.getCustomName() != null ? boss.getCustomName().getString() : boss.getType().toString(),
                config.vanishInvisibilityDuration, config.vanishSpeedDuration, teleported);
    }
}
