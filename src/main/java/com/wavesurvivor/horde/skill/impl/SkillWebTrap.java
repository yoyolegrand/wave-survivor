package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.skill.BossSkill;
import com.wavesurvivor.horde.skill.WebTrapTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * web_trap : pose une cobweb au pied de chaque joueur dans un rayon webTrapRange.
 * Chaque cobweb est trackée et supprimée après webTrapDuration ticks (si toujours présente).
 * Ne remplace pas les blocs existants qui ne sont pas de l'air.
 */
public class SkillWebTrap extends BossSkill {

    public SkillWebTrap(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(40, config.webTrapInterval);
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        double range = Math.max(2, config.webTrapRange);
        AABB box = boss.getBoundingBox().inflate(range);
        List<ServerPlayer> victims = level.getEntitiesOfClass(ServerPlayer.class, box,
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator());

        if (victims.isEmpty()) {
            WaveSurvivorMod.LOGGER.debug("[Skill web_trap] '{}' → aucun joueur dans rayon {}", boss.getType(), range);
            return;
        }

        int trapped = 0;
        for (ServerPlayer p : victims) {
            BlockPos pos = p.blockPosition();
            BlockState state = level.getBlockState(pos);
            // Ne pose que si le bloc est actuellement air (ou remplaçable comme herbe)
            if (state.isAir() || state.canBeReplaced()) {
                level.setBlockAndUpdate(pos, Blocks.COBWEB.defaultBlockState());
                WebTrapTracker.register(level, pos, config.webTrapDuration);
                trapped++;

                if (config.webTrapMessage != null && !config.webTrapMessage.isBlank()) {
                    p.sendSystemMessage(Component.literal(config.webTrapMessage));
                }
            }
        }

        if (trapped > 0 && config.webTrapSound != null && !config.webTrapSound.isBlank()) {
            try {
                SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation(config.webTrapSound));
                if (sound != null) {
                    level.playSound(null, boss.blockPosition(), sound, SoundSource.HOSTILE, 1.0f, 1.0f);
                }
            } catch (Exception ignore) {}
        }

        WaveSurvivorMod.LOGGER.info("[Skill web_trap] '{}' piège {} joueur(s) ({} tentatives) dans rayon {} (durée {}t)",
                boss.getCustomName() != null ? boss.getCustomName().getString() : boss.getType().toString(),
                trapped, victims.size(), (int) range, config.webTrapDuration);
    }
}
