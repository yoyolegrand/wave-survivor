package com.wavesurvivor.horde.spawn;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.CustomEntityData;
import com.wavesurvivor.horde.model.EquipmentEntry;
import com.wavesurvivor.horde.model.HordeEntity;
import com.wavesurvivor.horde.skill.DelayedActionScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.Optional;
import java.util.Random;

public class HordeSpawner {

    private static final Random RNG = new Random();

    // Batching : au-delà de BATCH_SIZE mobs, on échelonne le spawn pour éviter les lag spikes.
    private static final int BATCH_SIZE = 25;
    private static final int BATCH_DELAY_TICKS = 60; // 3 secondes entre chaque batch

    public static int spawnMobs(ServerLevel level, HordeEntity template, BlockPos center,
                                int radius, int count, int playerCount) {
        if (template == null || template.entityType == null) return 0;

        // === Résolution CustomEntity : si le type est un nom simple (pas de ':')
        //     OU si présent dans le registre, on délègue à CustomEntitySpawner. ===
        CustomEntityData custom = CustomEntityRegistry.get(template.entityType);
        if (custom != null) {
            return CustomEntitySpawner.spawnMobs(level, custom, center, radius, count, template.customName, template);
        }
        if (CustomEntityRegistry.looksLikeCustomRef(template.entityType)) {
            WaveSurvivorMod.LOGGER.warn("[Spawner] '{}' ressemble à une CustomEntity mais introuvable dans le registre. Skip.", template.entityType);
            return 0;
        }

        // === Vanilla / modé standard ===
        EntityType<?> type = resolveEntityType(template.entityType);
        if (type == null) {
            WaveSurvivorMod.LOGGER.warn("[Spawner] entity_type introuvable : {}", template.entityType);
            return 0;
        }

        // Batching si count > 25 : éviter surcharge serveur sur grosses vagues
        if (count > BATCH_SIZE) {
            int totalBatches = (int) Math.ceil((double) count / BATCH_SIZE);
            WaveSurvivorMod.LOGGER.info("[Spawner] '{}' → batching {} mobs en {} batches de {} (délai {}t)",
                    template.entityType, count, totalBatches, BATCH_SIZE, BATCH_DELAY_TICKS);

            // Batch 1 immédiat
            int firstSpawned = spawnBatch(level, template, type, center, radius, BATCH_SIZE, playerCount, 1, totalBatches);

            // Batches 2..N planifiés
            int remaining = count - BATCH_SIZE;
            int batchNum = 2;
            while (remaining > 0) {
                final int fBatchSize = Math.min(BATCH_SIZE, remaining);
                final int fBatchNum = batchNum;
                final int fTotalBatches = totalBatches;
                final int fPlayerCount = playerCount;
                int delay = BATCH_DELAY_TICKS * (batchNum - 1);
                DelayedActionScheduler.schedule(
                        level.getServer(),
                        delay,
                        () -> spawnBatch(level, template, type, center, radius, fBatchSize, fPlayerCount, fBatchNum, fTotalBatches),
                        "spawn_batch_" + template.entityType + "_" + fBatchNum
                );
                remaining -= fBatchSize;
                batchNum++;
            }
            return count; // total prévu (batch 1 spawné + batches suivants planifiés)
        }

        // Spawn direct si count <= BATCH_SIZE
        return spawnBatch(level, template, type, center, radius, count, playerCount, 1, 1);
    }

    /**
     * Spawn effectif d'un batch de mobs. Extrait de spawnMobs pour permettre le batching.
     * batchNum/totalBatches sont juste pour logging.
     */
    private static int spawnBatch(ServerLevel level, HordeEntity template, EntityType<?> type,
                                  BlockPos center, int radius, int count, int playerCount,
                                  int batchNum, int totalBatches) {
        int spawned = 0;
        int failed = 0;
        for (int i = 0; i < count; i++) {
            BlockPos pos = SpawnZone.pick(level, center, radius, type); // zone de spawn sûre
            Entity ent = type.create(level);
            if (ent == null) { failed++; continue; }

            ent.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, RNG.nextFloat() * 360f, 0f);

            if (ent instanceof LivingEntity living) {
                applyStats(living, template, playerCount);
                if (template.customName != null && !template.customName.isBlank()) {
                    living.setCustomName(Component.literal(com.wavesurvivor.i18n.WSLang.t(template.customName)));
                    living.setCustomNameVisible(true); // toujours visible : on distingue les monstres d'un coup d'œil
                }
            }
            if (ent instanceof Mob mob) {
                // IMPORTANT : finalizeSpawn AVANT applyEquipment
                // Sinon les mobs qui ont un populateDefaultEquipmentSlots (drowned, zombie, skeleton...)
                // écrasent nos armures custom avec leurs armures vanilla.
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
                applyEquipment(mob, template);
            }

            boolean added = level.addFreshEntity(ent);
            if (added) {
                MobRegistry.register(ent, template);
                spawned++;
            } else {
                failed++;
                WaveSurvivorMod.LOGGER.warn("[Spawner] addFreshEntity REFUSÉ pour {} à {}", template.entityType, pos);
            }
        }
        if (totalBatches > 1) {
            WaveSurvivorMod.LOGGER.info("[Spawner] '{}' batch {}/{} → {}/{} spawnés ({} refusés)",
                    template.entityType, batchNum, totalBatches, spawned, count, failed);
        } else {
            WaveSurvivorMod.LOGGER.info("[Spawner] '{}' → {}/{} spawnés ({} refusés) autour de {}",
                    template.entityType, spawned, count, failed, center);
        }
        return spawned;
    }

    public static int spawnMobs(ServerLevel level, HordeEntity template, BlockPos center, int radius, int count) {
        return spawnMobs(level, template, center, radius, count, 1);
    }

    /**
     * Spawn UN mob depuis un template de horde (stats + equipment + nom), SANS l'enregistrer
     * dans MobRegistry (ne compte pas dans la vague, pas de loot custom).
     * Utilisé par les summons de boss (BossConfigTicker).
     * @return l'entité ajoutée au monde, ou null si échec.
     */
    public static Entity spawnSummonFromTemplate(ServerLevel level, HordeEntity template, BlockPos pos, int playerCount) {
        if (template == null || template.entityType == null) return null;
        EntityType<?> type = resolveEntityType(template.entityType);
        if (type == null) return null;
        Entity ent = type.create(level);
        if (ent == null) return null;
        ent.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, RNG.nextFloat() * 360f, 0f);
        if (ent instanceof LivingEntity living) {
            applyStats(living, template, playerCount);
            if (template.customName != null && !template.customName.isBlank()) {
                living.setCustomName(Component.literal(com.wavesurvivor.i18n.WSLang.t(template.customName)));
                living.setCustomNameVisible(true); // toujours visible : on distingue les monstres d'un coup d'œil
            }
        }
        if (ent instanceof Mob mob) {
            // finalizeSpawn AVANT applyEquipment (sinon armures vanilla écrasent les custom)
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
            applyEquipment(mob, template);
            // Pas de drop d'équipement sur les summons (anti-farm)
            for (EquipmentSlot slot : EquipmentSlot.values()) mob.setDropChance(slot, 0f);
        }
        return level.addFreshEntity(ent) ? ent : null;
    }

    private static EntityType<?> resolveEntityType(String id) {
        if (id == null || id.isBlank()) return null;
        try {
            Optional<EntityType<?>> opt = ForgeRegistries.ENTITY_TYPES.getHolder(new ResourceLocation(id)).map(h -> h.value());
            return opt.orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static BlockPos pickSpawnPos(Level level, BlockPos center, int radius) {
        return SpawnZone.pick(level, center, radius, null);
    }

    private static void applyStats(LivingEntity living, HordeEntity t, int playerCount) {
        double hp = t.effectiveMaxHealth(playerCount);
        double dmg = t.effectiveAttackDamage(playerCount);

        setAttribute(living, Attributes.MAX_HEALTH, hp);
        living.setHealth((float) hp);
        setAttribute(living, Attributes.ATTACK_DAMAGE, dmg);
        setAttribute(living, Attributes.MOVEMENT_SPEED, t.movementSpeed);
        setAttribute(living, Attributes.FOLLOW_RANGE, t.followRange);
        setAttribute(living, Attributes.KNOCKBACK_RESISTANCE, t.knockbackResistance);
    }

    private static void setAttribute(LivingEntity living, Attribute attr, double value) {
        AttributeInstance inst = living.getAttribute(attr);
        if (inst != null) inst.setBaseValue(value);
    }

    private static void applyEquipment(Mob mob, HordeEntity t) {
        NetherSpawnFix.apply(mob); // piglins/hoglins immunisés · neutres agressifs
        if (t.equipment == null || t.equipment.isEmpty()) {
            t.applyDropFlags(mob); // équipement aléatoire vanilla : drops coupés si demandé
            return;
        }
        for (Map.Entry<String, EquipmentEntry> entry : t.equipment.entrySet()) {
            EquipmentSlot slot = parseSlot(entry.getKey());
            if (slot == null) continue;
            EquipmentEntry data = entry.getValue();
            // IMPORTANT : même si le slot est configuré vide, on CLEAR au lieu de skip.
            // Sinon les armures random de finalizeSpawn (Zombie/Drowned/Skeleton) restent.
            if (data == null || data.item == null || data.item.isBlank()) {
                mob.setItemSlot(slot, ItemStack.EMPTY);
                mob.setDropChance(slot, 0f);
                continue;
            }
            // Résolveur commun : enchantements / données d'objet compris
            ItemStack eq = com.wavesurvivor.horde.loot.LootItems.resolve(data.item, 1);
            if (eq.isEmpty()) {
                mob.setItemSlot(slot, ItemStack.EMPTY);
                mob.setDropChance(slot, 0f);
                continue;
            }
            mob.setItemSlot(slot, eq);
            mob.setDropChance(slot, Math.max(0f, Math.min(1f, data.dropChance / 100f)));
        }
        t.applyDropFlags(mob); // interrupteurs « Drop armes / armure » de l'éditeur
    }

    private static EquipmentSlot parseSlot(String name) {
        if (name == null) return null;
        return switch (name.toLowerCase()) {
            case "mainhand" -> EquipmentSlot.MAINHAND;
            case "offhand"  -> EquipmentSlot.OFFHAND;
            case "head"     -> EquipmentSlot.HEAD;
            case "chest"    -> EquipmentSlot.CHEST;
            case "legs"     -> EquipmentSlot.LEGS;
            case "feet"     -> EquipmentSlot.FEET;
            default -> null;
        };
    }
}
