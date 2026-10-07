package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.skill.BossSkill;
import com.wavesurvivor.horde.skill.GatlingScheduler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * gatling : le boss tire une rafale de projectiles étalée dans le temps.
 * L'orchestration des shots individuels est déléguée à GatlingScheduler (qui tick tous les
 * gatlingShotInterval ticks jusqu'à épuisement du bulletCount).
 * Le son et le message sont joués une seule fois au déclenchement de la rafale.
 */
public class SkillGatling extends BossSkill {

    public SkillGatling(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(40, config.gatlingCooldown);
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        int count = Math.max(1, config.gatlingBulletCount);
        long now = level.getServer().getTickCount();

        GatlingScheduler.schedule(
                boss, target,
                config.gatlingProjectileType,
                config.gatlingSpeed,
                config.gatlingDispersion,
                config.gatlingShotInterval,
                count,
                now
        );

        // Son au déclenchement
        if (config.gatlingSound != null && !config.gatlingSound.isBlank()) {
            try {
                SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation(config.gatlingSound));
                if (sound != null) {
                    level.playSound(null, boss.blockPosition(), sound, SoundSource.HOSTILE, 1.0f, 1.0f);
                }
            } catch (Exception ignore) {}
        }

        // Message à la cible
        if (config.gatlingMessage != null && !config.gatlingMessage.isBlank() && target instanceof ServerPlayer sp) {
            sp.sendSystemMessage(Component.literal(config.gatlingMessage));
        }

        WaveSurvivorMod.LOGGER.info("[Skill gatling] '{}' démarre rafale de {} × {} (interval {}t, dispersion {})",
                boss.getCustomName() != null ? boss.getCustomName().getString() : boss.getType().toString(),
                count, config.gatlingProjectileType, config.gatlingShotInterval, config.gatlingDispersion);
    }
}
