package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.quake.QuakeManager;
import com.wavesurvivor.horde.skill.BossSkill;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * earthquake (Séisme) : un séisme local secoue la zone autour du boss tout de suite (secousses, fissures, éboulis,
 * caméra qui tremble). Même moteur que l'événement de chaos « Séisme » (QuakeManager) ; aucune relique n'en protège.
 */
public class SkillEarthquake extends BossSkill {

    public SkillEarthquake(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(100, config.earthquakeCooldown);
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        QuakeManager.Params p = new QuakeManager.Params();
        p.radius = Math.max(6, config.earthquakeRadius);
        p.warningTicks = 0;
        p.durationTicks = Math.max(3, config.earthquakeDuration) * 20;
        p.damage = Math.max(0, config.earthquakeDamage);
        p.jolt = Math.max(0, config.earthquakeJolt);
        p.cracks = Math.max(0, config.earthquakeCracks);
        p.rubble = Math.max(0, config.earthquakeRubble);
        p.shake = Math.max(0, config.earthquakeShake);
        QuakeManager.start(level, boss.position(), p);
        if (config.earthquakeMessage != null && !config.earthquakeMessage.isBlank()) {
            for (ServerPlayer pl : level.getEntitiesOfClass(ServerPlayer.class, boss.getBoundingBox().inflate(p.radius))) {
                pl.displayClientMessage(Component.literal(config.earthquakeMessage), true);
            }
        }
    }
}
