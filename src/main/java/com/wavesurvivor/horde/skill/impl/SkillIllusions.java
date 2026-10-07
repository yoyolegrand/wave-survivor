package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.skill.BossSkill;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
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
 * illusions : spawn illusionsCloneCount clones illusionsCloneType (défaut enderman) autour du boss.
 * Chaque clone a illusionsCloneHealth HP (défaut 1) — donc facilement tuables, mais dispersent l'attention.
 * Les clones ne sont PAS enregistrés dans MobRegistry (pas de blocage fin de vague).
 */
public class SkillIllusions extends BossSkill {

    private static final Random RNG = new Random();

    public SkillIllusions(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(60, config.illusionsCooldown);
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        // « self » (ou vide) : clones identiques au lanceur (même créature, même nom, même équipement)
        String rawType = config.illusionsCloneType == null ? "" : config.illusionsCloneType.trim();
        boolean self = rawType.isEmpty() || "self".equalsIgnoreCase(rawType);
        EntityType<?> cloneType = self ? boss.getType() : resolveEntityType(rawType);
        if (cloneType == null) {
            WaveSurvivorMod.LOGGER.warn("[Skill illusions] type introuvable : {}", config.illusionsCloneType);
            return;
        }

        // Son fort d'invocation AU DÉCLENCHEMENT (avant spawn, volume élevé)
        level.playSound(null, boss.blockPosition(),
                SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 2.5f, 1.0f);
        level.playSound(null, boss.blockPosition(),
                SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 2.0f, 0.8f);

        int count = Math.max(1, config.illusionsCloneCount);
        double radius = Math.max(2, config.illusionsSpawnRadius);
        BlockPos bossPos = boss.blockPosition();

        int spawned = 0;
        for (int i = 0; i < count; i++) {
            double angle = RNG.nextDouble() * Math.PI * 2;
            double dist = radius * (0.3 + RNG.nextDouble() * 0.7);
            double x = bossPos.getX() + Math.cos(angle) * dist + 0.5;
            double z = bossPos.getZ() + Math.sin(angle) * dist + 0.5;
            double y = bossPos.getY();

            Entity ent = cloneType.create(level);
            if (ent == null) continue;
            ent.moveTo(x, y, z, RNG.nextFloat() * 360f, 0f);

            if (ent instanceof LivingEntity living) {
                setAttribute(living, Attributes.MAX_HEALTH, config.illusionsCloneHealth);
                living.setHealth((float) config.illusionsCloneHealth);
            }
            if (ent instanceof Mob mob) {
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(bossPos), MobSpawnType.MOB_SUMMONED, null, null);
                if (target != null) mob.setTarget(target);
            }
            if (self && ent instanceof LivingEntity living) {
                // Copie conforme du lanceur : nom + équipement, sans aucun butin à la mort
                if (boss.getCustomName() != null) {
                    living.setCustomName(boss.getCustomName().copy());
                    living.setCustomNameVisible(true);
                }
                for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                    living.setItemSlot(slot, boss.getItemBySlot(slot).copy());
                    if (living instanceof Mob m) m.setDropChance(slot, 0f);
                }
                living.getPersistentData().putBoolean("ws_illusion", true);
                living.getPersistentData().putBoolean("ws_revenant", true); // jamais relevé par la Résurrection
            }
            if (level.addFreshEntity(ent)) spawned++;
        }

        // Son
        if (config.illusionsSound != null && !config.illusionsSound.isBlank()) {
            try {
                SoundEvent snd = BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation(config.illusionsSound));
                if (snd != null) {
                    level.playSound(null, boss.blockPosition(), snd, SoundSource.HOSTILE, 1.0f, 1.0f);
                }
            } catch (Exception ignore) {}
        }
        // Message
        if (config.illusionsMessage != null && !config.illusionsMessage.isBlank() && target instanceof ServerPlayer sp) {
            sp.sendSystemMessage(Component.literal(config.illusionsMessage));
        }

        WaveSurvivorMod.LOGGER.info("[Skill illusions] '{}' invoque {} clones de {}",
                boss.getCustomName() != null ? boss.getCustomName().getString() : boss.getType().toString(),
                spawned, config.illusionsCloneType);
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
}
