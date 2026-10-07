package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.skill.BossSkill;
import com.wavesurvivor.horde.skill.DelayedActionScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Optional;

/**
 * solar : cible les joueurs dans un rayon solarRadius autour du boss.
 * Envoie un warning (particules flame + son) à leur position, puis après solarWarningDelay ticks
 * spawn solarStrikeType (défaut lightning_bolt) à la position INITIALE.
 * Le joueur peut esquiver en se déplaçant avant l'impact.
 */
public class SkillSolar extends BossSkill {

    public SkillSolar(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(60, config.solarCooldown);
    }

    @Override
    public boolean requiresTarget() {
        return false;
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        double radius = Math.max(2, config.solarRadius);
        AABB box = boss.getBoundingBox().inflate(radius);
        List<ServerPlayer> victims = level.getEntitiesOfClass(ServerPlayer.class, box,
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator());

        if (victims.isEmpty()) return;

        // Message + son de warning
        Component msg = (config.solarMessage != null && !config.solarMessage.isBlank())
                ? Component.literal(config.solarMessage) : null;
        for (ServerPlayer p : victims) {
            if (msg != null) p.sendSystemMessage(msg);
        }
        if (config.solarWarningSound != null && !config.solarWarningSound.isBlank()) {
            try {
                SoundEvent snd = BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation(config.solarWarningSound));
                if (snd != null) {
                    level.playSound(null, boss.blockPosition(), snd, SoundSource.HOSTILE, 1.0f, 1.0f);
                }
            } catch (Exception ignore) {}
        }

        EntityType<?> strikeType = resolveEntityType(config.solarStrikeType);
        int delay = Math.max(5, config.solarWarningDelay);

        for (ServerPlayer p : victims) {
            final BlockPos strikePos = p.blockPosition();

            // Warning : colonne de particules flame au-dessus du joueur
            for (int y = 0; y < 5; y++) {
                level.sendParticles(ParticleTypes.FLAME,
                        strikePos.getX() + 0.5, strikePos.getY() + y + 0.5, strikePos.getZ() + 0.5,
                        3, 0.15, 0.05, 0.15, 0.02);
            }

            // Schedule strike
            final EntityType<?> finalType = strikeType;
            DelayedActionScheduler.schedule(level.getServer(), delay, () -> {
                if (finalType == null) return;
                Entity strike = finalType.create(level);
                if (strike == null) return;
                strike.moveTo(strikePos.getX() + 0.5, strikePos.getY(), strikePos.getZ() + 0.5, 0f, 0f);
                level.addFreshEntity(strike);
            }, "solar strike @ " + strikePos);
        }

        WaveSurvivorMod.LOGGER.info("[Skill solar] '{}' cible {} joueur(s) — impact dans {}t",
                boss.getCustomName() != null ? boss.getCustomName().getString() : boss.getType().toString(),
                victims.size(), delay);
    }

    private static EntityType<?> resolveEntityType(String id) {
        if (id == null || id.isBlank()) return EntityType.LIGHTNING_BOLT;
        try {
            Optional<EntityType<?>> opt = ForgeRegistries.ENTITY_TYPES.getHolder(new ResourceLocation(id)).map(h -> h.value());
            return opt.orElse(EntityType.LIGHTNING_BOLT);
        } catch (Exception e) { return EntityType.LIGHTNING_BOLT; }
    }
}
