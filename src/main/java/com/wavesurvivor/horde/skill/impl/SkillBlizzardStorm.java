package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.frost.StormManager;
import com.wavesurvivor.horde.skill.BossSkill;
import com.wavesurvivor.horde.skill.NecroTracker;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * blizzard_storm (Blizzard) : le boss déclenche une tempête locale qui le suit pendant blizzardDuration secondes.
 * Les joueurs exposés sont ralentis, gelés, blessés et attirés vers le boss ; un feu de camp proche les met à l'abri.
 * Même moteur que l'événement de chaos « Tempête » (StormManager).
 */
public class SkillBlizzardStorm extends BossSkill {

    public SkillBlizzardStorm(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(100, config.blizzardCooldown);
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        StormManager.Params p = new StormManager.Params();
        p.radius = Math.max(3, config.blizzardRadius);
        p.warningTicks = 0;
        p.durationTicks = Math.max(2, config.blizzardDuration) * 20;
        p.damagePerSecond = Math.max(0, config.blizzardDamage);
        p.pull = Math.max(0, config.blizzardPull);
        p.wind = Math.max(0, config.blizzardWind);
        p.freeze = config.blizzardFreeze;
        p.shelterRadius = Math.max(0, config.blizzardShelterRadius);
        p.announce = false;
        if (config.blizzardSlowAmplifier > 0) {
            p.effects.add(new StormManager.Fx(MobEffects.MOVEMENT_SLOWDOWN, config.blizzardSlowAmplifier - 1, true));
        }
        StormManager.start(level, boss.position(), boss.getUUID(), p);

        NecroTracker.playSound(level, boss.blockPosition(), config.blizzardSound, 1.4f);
        if (config.blizzardMessage != null && !config.blizzardMessage.isBlank()) {
            for (ServerPlayer pl : level.getEntitiesOfClass(ServerPlayer.class, boss.getBoundingBox().inflate(p.radius + 8))) {
                pl.displayClientMessage(Component.literal(config.blizzardMessage), true);
            }
        }
    }
}
