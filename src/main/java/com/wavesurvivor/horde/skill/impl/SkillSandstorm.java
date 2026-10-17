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
 * sandstorm (Tempête de sable) : une tempête de sable suit le boss pendant sandstormDuration secondes. Les joueurs
 * exposés sont blessés, ralentis et assoiffés ; on s'abrite à couvert (sous un toit ou dans l'eau). Une relique
 * (Voile du Désert par défaut) immunise son porteur. Même moteur que l'événement de chaos « Tempête » (StormManager).
 */
public class SkillSandstorm extends BossSkill {

    public SkillSandstorm(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(100, config.sandstormCooldown);
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        StormManager.Params p = new StormManager.Params();
        p.radius = Math.max(3, config.sandstormRadius);
        p.warningTicks = 0;
        p.durationTicks = Math.max(2, config.sandstormDuration) * 20;
        p.damagePerSecond = Math.max(0, config.sandstormDamage);
        p.pull = Math.max(0, config.sandstormPull);
        p.wind = Math.max(0, config.sandstormWind);
        p.freeze = false;
        p.announce = false;
        p.particle = "block:minecraft:sand";
        p.look = "sand";
        p.shelterMode = "cover";
        p.immuneItem = config.sandstormImmuneItem;
        if (config.sandstormHungerAmplifier > 0) {
            p.effects.add(new StormManager.Fx(MobEffects.HUNGER, config.sandstormHungerAmplifier - 1, true));
        }
        if (config.sandstormSlowAmplifier > 0) {
            p.effects.add(new StormManager.Fx(MobEffects.MOVEMENT_SLOWDOWN, config.sandstormSlowAmplifier - 1, true));
        }
        StormManager.start(level, boss.position(), boss.getUUID(), p);

        NecroTracker.playSound(level, boss.blockPosition(), config.sandstormSound, 0.9f);
        if (config.sandstormMessage != null && !config.sandstormMessage.isBlank()) {
            for (ServerPlayer pl : level.getEntitiesOfClass(ServerPlayer.class, boss.getBoundingBox().inflate(p.radius + 8))) {
                pl.displayClientMessage(Component.literal(config.sandstormMessage), true);
            }
        }
    }
}
