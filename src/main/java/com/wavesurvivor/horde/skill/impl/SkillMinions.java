package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.CustomEntityData;
import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.skill.BossSkill;
import com.wavesurvivor.horde.spawn.CustomEntityRegistry;
import com.wavesurvivor.horde.spawn.CustomEntitySpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;
import java.util.Random;

/**
 * minions : le boss invoque N minions autour de lui à chaque activation.
 * Différent du système BossMinionTracker (qui est un mécanisme natif de boss) — ici c'est un
 * skill classique avec cooldown standard, activable sur n'importe quelle entité custom.
 *
 * Les minions invoqués par ce skill ne sont PAS enregistrés dans MobRegistry
 * (pour éviter de bloquer la fin de vague en boucle infinie d'invocations).
 */
public class SkillMinions extends BossSkill {

    private static final Random RNG = new Random();

    public SkillMinions(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(40, config.minionCooldown);
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        // Roll de chance
        if (config.minionSpawnChance < 1.0 && RNG.nextDouble() > config.minionSpawnChance) {
            WaveSurvivorMod.LOGGER.debug("[Skill minions] '{}' → roll échoué (chance {})",
                    boss.getType(), config.minionSpawnChance);
            return;
        }

        int count = Math.max(1, config.minionCount);
        BlockPos bossPos = boss.blockPosition();

        // Résolution : CustomEntity d'abord, sinon vanilla/mod EntityType
        CustomEntityData ce = CustomEntityRegistry.get(config.minionType);
        int spawned;
        if (ce != null) {
            spawned = CustomEntitySpawner.spawnMobs(level, ce, bossPos, 2, count, null);
        } else {
            if (CustomEntityRegistry.looksLikeCustomRef(config.minionType)) {
                WaveSurvivorMod.LOGGER.warn("[Skill minions] minionType '{}' ressemble à une CustomEntity mais introuvable — skip.",
                        config.minionType);
                return;
            }
            spawned = spawnVanillaMinions(level, boss, bossPos, count);
        }

        playSound(level, boss);
        WaveSurvivorMod.LOGGER.info("[Skill minions] '{}' invoque {} × {} en ({},{},{})",
                boss.getCustomName() != null ? boss.getCustomName().getString() : boss.getType().toString(),
                spawned, config.minionType, bossPos.getX(), bossPos.getY(), bossPos.getZ());
    }

    private int spawnVanillaMinions(ServerLevel level, LivingEntity boss, BlockPos bossPos, int count) {
        EntityType<?> type = resolveEntityType(config.minionType);
        if (type == null) {
            WaveSurvivorMod.LOGGER.warn("[Skill minions] minionType introuvable : {}", config.minionType);
            return 0;
        }

        int spawned = 0;
        for (int i = 0; i < count; i++) {
            double angle = RNG.nextDouble() * Math.PI * 2;
            double dist = 1.5 + RNG.nextDouble() * 1.5;
            double x = bossPos.getX() + Math.cos(angle) * dist + 0.5;
            double z = bossPos.getZ() + Math.sin(angle) * dist + 0.5;
            double y = bossPos.getY();

            Entity ent = type.create(level);
            if (ent == null) continue;
            ent.moveTo(x, y, z, RNG.nextFloat() * 360f, 0f);

            if (ent instanceof LivingEntity living) {
                setAttribute(living, Attributes.MAX_HEALTH, config.minionHealth);
                living.setHealth((float) config.minionHealth);
                setAttribute(living, Attributes.MOVEMENT_SPEED, config.minionSpeed);
            }
            if (ent instanceof Mob mob) {
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(bossPos), MobSpawnType.MOB_SUMMONED, null, null);
                // Cible le même joueur que le boss, si dispo
                if (boss instanceof Mob bossMob && bossMob.getTarget() != null) {
                    mob.setTarget(bossMob.getTarget());
                }
            }
            if (level.addFreshEntity(ent)) spawned++;
        }
        return spawned;
    }

    private static EntityType<?> resolveEntityType(String id) {
        if (id == null || id.isBlank()) return null;
        try {
            Optional<EntityType<?>> opt = ForgeRegistries.ENTITY_TYPES.getHolder(new ResourceLocation(id)).map(h -> h.value());
            return opt.orElse(null);
        } catch (Exception e) { return null; }
    }

    private static void setAttribute(LivingEntity living, Attribute attr, double value) {
        AttributeInstance inst = living.getAttribute(attr);
        if (inst != null) inst.setBaseValue(value);
    }

    private void playSound(ServerLevel level, LivingEntity from) {
        if (config.minionSound == null || config.minionSound.isBlank()) return;
        try {
            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation(config.minionSound));
            if (sound != null) {
                level.playSound(null, from.blockPosition(), sound, SoundSource.HOSTILE, 1.0f, 1.0f);
            }
        } catch (Exception ignore) {}
    }
}
