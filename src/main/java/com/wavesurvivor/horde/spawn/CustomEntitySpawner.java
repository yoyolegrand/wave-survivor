package com.wavesurvivor.horde.spawn;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.CustomEntityData;
import com.wavesurvivor.horde.model.HordeEntity;
import com.wavesurvivor.horde.skill.DelayedActionScheduler;
import com.wavesurvivor.horde.skill.SkillManager;
import net.minecraft.ChatFormatting;
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

/**
 * Spawner spécialisé pour les CustomEntity.
 * Utilisé par HordeSpawner, BossManager, ChaosApplier, BossMinionTracker.
 *
 * Phase 2a : stats + equipment + name + loot.
 * Phase 2b : skills + mount.
 */
public class CustomEntitySpawner {

    private static final Random RNG = new Random();

    // Batching : même règle que HordeSpawner
    private static final int BATCH_SIZE = 25;
    private static final int BATCH_DELAY_TICKS = 60; // 3 secondes

    /**
     * Spawn count CustomEntity autour d'un centre, comme un mob de horde standard.
     * Register dans MobRegistry avec un HordeEntity synthétique pour que le loot fonctionne.
     * Si ce.mountEntity.enabled, spawn aussi la monture et fait chevaucher.
     *
     * @param customNameOverride  si non null, override le displayName de la CustomEntity
     * @return nombre effectivement spawné (compte les riders, pas les mounts)
     */
    public static int spawnMobs(ServerLevel level, CustomEntityData ce, BlockPos center,
                                 int radius, int count, String customNameOverride) {
        return spawnMobs(level, ce, center, radius, count, customNameOverride, null);
    }

    /**
     * @param hordeOverride entrée de horde qui référence cette entité (peut être null) :
     *   équipement renseigné = remplace le slot de l'entité ; loot = s'ajoute ; stats = si override_stats.
     */
    public static int spawnMobs(ServerLevel level, CustomEntityData ce, BlockPos center,
                                 int radius, int count, String customNameOverride, HordeEntity hordeOverride) {
        if (ce == null) return 0;
        EntityType<?> type = resolveEntityType(ce.baseEntityType);
        if (type == null) {
            WaveSurvivorMod.LOGGER.warn("[CustomSpawner] baseEntityType introuvable pour '{}' : {}",
                    ce.entityName, ce.baseEntityType);
            return 0;
        }

        // Batching si count > 25
        if (count > BATCH_SIZE) {
            int totalBatches = (int) Math.ceil((double) count / BATCH_SIZE);
            WaveSurvivorMod.LOGGER.info("[CustomSpawner] '{}' → batching {} mobs en {} batches de {} (délai {}t)",
                    ce.entityName, count, totalBatches, BATCH_SIZE, BATCH_DELAY_TICKS);

            spawnBatch(level, ce, type, center, radius, BATCH_SIZE, customNameOverride, 1, totalBatches, hordeOverride);

            int remaining = count - BATCH_SIZE;
            int batchNum = 2;
            while (remaining > 0) {
                final int fBatchSize = Math.min(BATCH_SIZE, remaining);
                final int fBatchNum = batchNum;
                final int fTotalBatches = totalBatches;
                int delay = BATCH_DELAY_TICKS * (batchNum - 1);
                DelayedActionScheduler.schedule(
                        level.getServer(),
                        delay,
                        () -> spawnBatch(level, ce, type, center, radius, fBatchSize, customNameOverride, fBatchNum, fTotalBatches, hordeOverride),
                        "ce_spawn_batch_" + ce.entityName + "_" + fBatchNum
                );
                remaining -= fBatchSize;
                batchNum++;
            }
            return count;
        }

        return spawnBatch(level, ce, type, center, radius, count, customNameOverride, 1, 1, hordeOverride);
    }

    /**
     * Spawn effectif d'un batch de CustomEntity (avec skills + mount éventuel).
     * batchNum/totalBatches uniquement pour logging.
     * @param hordeOverride entrée de horde (équipement / loot / stats par-dessus l'entité), peut être null
     */
    private static int spawnBatch(ServerLevel level, CustomEntityData ce, EntityType<?> type,
                                  BlockPos center, int radius, int count,
                                  String customNameOverride, int batchNum, int totalBatches,
                                  HordeEntity hordeOverride) {
        int spawned = 0;
        int failed = 0;
        for (int i = 0; i < count; i++) {
            BlockPos pos = SpawnZone.pick(level, center, radius, type); // zone de spawn sûre
            Entity ent = type.create(level);
            if (ent == null) { failed++; continue; }

            ent.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, RNG.nextFloat() * 360f, 0f);

            if (ent instanceof LivingEntity living) {
                applyStats(living, ce.stats);
                if (hordeOverride != null && hordeOverride.overrideStats) applyHordeStats(living, hordeOverride);
                applyName(living, ce, customNameOverride, false);
            }
            if (ent instanceof Mob mob) {
                // finalizeSpawn AVANT applyEquipment (voir HordeSpawner pour l'explication)
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
                applyEquipment(mob, ce.equipment);
                applyOverrideEquipment(mob, hordeOverride);
                if (hordeOverride != null) hordeOverride.applyDropFlags(mob);
                NetherSpawnFix.apply(mob); // piglins/hoglins immunisés · neutres agressifs
                // Lanceur de sorts : garde ses distances au lieu d'aller au corps à corps
                if (ce.keepDistance > 0) com.wavesurvivor.horde.ai.KeepDistanceGoal.apply(mob, ce.keepDistance);
            }

            if (level.addFreshEntity(ent)) {
                MobRegistry.register(ent, templateWithOverride(ce, hordeOverride));
                // Profanateur : ignore les joueurs et attaque le monolithe (Défense du Monolithe)
                if (ce.targetsAltar) com.wavesurvivor.altar.AltarDefense.registerProfaner(ent);
                spawned++;

                // Register skills (Phase 2b) — idem que pour les boss
                if (ce.skills != null && !ce.skills.isEmpty()) {
                    SkillManager.register(ent.getUUID(), ce);
                }

                // Spawn mount si demandé (rider chevauche mount)
                if (ce.mountEntity != null && ce.mountEntity.enabled) {
                    Entity mount = spawnMount(level, ce.mountEntity, pos);
                    if (mount != null) {
                        ent.startRiding(mount);
                    }
                }
            } else {
                failed++;
                WaveSurvivorMod.LOGGER.warn("[CustomSpawner] addFreshEntity refusé pour '{}' à {}", ce.entityName, pos);
            }
        }
        if (totalBatches > 1) {
            WaveSurvivorMod.LOGGER.info("[CustomSpawner] '{}' ({}) batch {}/{} → {}/{} spawnés ({} refusés)",
                    ce.entityName, ce.baseEntityType, batchNum, totalBatches, spawned, count, failed);
        } else {
            WaveSurvivorMod.LOGGER.info("[CustomSpawner] '{}' ({}) → {}/{} spawnés ({} refusés) autour de {}",
                    ce.entityName, ce.baseEntityType, spawned, count, failed, center);
        }
        return spawned;
    }

    /**
     * Spawn UN boss CustomEntity. Retourne l'entité créée (pour que BossManager
     * puisse attacher sa BossBar) ou null si échec.
     * Si ce.mountEntity.enabled, spawn aussi la monture.
     *
     * @param bossNameOverride  si non null, override le nom affiché (bossName du BossWave)
     */
    public static LivingEntity spawnBoss(ServerLevel level, CustomEntityData ce, BlockPos center,
                                          int radius, String bossNameOverride) {
        if (ce == null) return null;
        EntityType<?> type = resolveEntityType(ce.baseEntityType);
        if (type == null) {
            WaveSurvivorMod.LOGGER.warn("[CustomSpawner-Boss] baseEntityType introuvable : {}", ce.baseEntityType);
            return null;
        }

        BlockPos pos = SpawnZone.pick(level, center, radius, type); // zone de spawn sûre (taille du boss prise en compte)
        Entity ent = type.create(level);
        if (ent == null) return null;

        ent.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, RNG.nextFloat() * 360f, 0f);

        if (ent instanceof LivingEntity living) {
            applyStats(living, ce.stats);
            applyName(living, ce, bossNameOverride, true);
        }
        if (ent instanceof Mob mob) {
            // finalizeSpawn AVANT applyEquipment (voir HordeSpawner)
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
            applyEquipment(mob, ce.equipment);
            NetherSpawnFix.apply(mob);
            if (ce.keepDistance > 0) com.wavesurvivor.horde.ai.KeepDistanceGoal.apply(mob, ce.keepDistance);
        }
        if (!level.addFreshEntity(ent)) {
            WaveSurvivorMod.LOGGER.warn("[CustomSpawner-Boss] addFreshEntity refusé pour '{}'", ce.entityName);
            return null;
        }
        if (ent instanceof LivingEntity living) {
            // Enregistrer aussi le boss dans MobRegistry pour que le loot custom drop
            MobRegistry.register(ent, toHordeTemplate(ce));
            WaveSurvivorMod.LOGGER.info("[CustomSpawner-Boss] '{}' ({}) spawné en ({},{},{})",
                    ce.entityName, ce.baseEntityType, pos.getX(), pos.getY(), pos.getZ());

            // Mécaniques de Liche (phases, Phylactères, bouclier, siphon du monolithe)
            if (ce.bossConfig != null && Boolean.TRUE.equals(ce.bossConfig.get("licheMechanics"))) {
                com.wavesurvivor.horde.boss.LicheController.register(living, center);
            }
            // Mécaniques du Cavalier des Cendres (charge montée, désarçonné + Brèches, météores)
            if (ce.bossConfig != null && Boolean.TRUE.equals(ce.bossConfig.get("cavalierMechanics"))) {
                com.wavesurvivor.horde.boss.CavalierController.register(living, center);
            }
            // Mécaniques de Xâl'Tor (clones, cristaux du Néant, effondrement)
            if (ce.bossConfig != null && Boolean.TRUE.equals(ce.bossConfig.get("xaltorMechanics"))) {
                com.wavesurvivor.horde.boss.XaltorController.register(living, center);
            }

            // Spawn mount si demandé
            if (ce.mountEntity != null && ce.mountEntity.enabled) {
                Entity mount = spawnMount(level, ce.mountEntity, pos);
                if (mount != null) {
                    ent.startRiding(mount);
                    WaveSurvivorMod.LOGGER.info("[CustomSpawner-Boss] '{}' chevauche '{}'",
                            ce.entityName, ce.mountEntity.entityType);
                }
            }
            return living;
        }
        return null;
    }

    /**
     * Spawn une monture indépendante et l'enregistre aussi dans MobRegistry
     * (pour que /ws status la compte et que le prune fonctionne).
     * @return l'entité mount créée, ou null en cas d'échec.
     */
    private static Entity spawnMount(ServerLevel level, CustomEntityData.MountEntity mountCfg, BlockPos pos) {
        if (mountCfg == null || mountCfg.entityType == null || mountCfg.entityType.isBlank()) return null;
        EntityType<?> mountType = resolveEntityType(mountCfg.entityType);
        if (mountType == null) {
            WaveSurvivorMod.LOGGER.warn("[CustomSpawner] mount entityType introuvable : {}", mountCfg.entityType);
            return null;
        }
        Entity mount = mountType.create(level);
        if (mount == null) return null;
        mount.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, RNG.nextFloat() * 360f, 0f);

        if (mount instanceof LivingEntity mountLiving) {
            applyMountStats(mountLiving, mountCfg.stats);
            if (mountCfg.customName != null && !mountCfg.customName.isBlank()) {
                mountLiving.setCustomName(Component.literal(com.wavesurvivor.i18n.WSLang.t(mountCfg.customName)).withStyle(ChatFormatting.LIGHT_PURPLE));
                mountLiving.setCustomNameVisible(false);
            }
        }
        if (mount instanceof Mob mountMob) {
            // finalizeSpawn AVANT applyEquipment
            mountMob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
            applyEquipment(mountMob, mountCfg.equipment);
            NetherSpawnFix.apply(mountMob); // hoglin monture : pas de zombification
        }

        // Monture voulue (configurée dans l'entité) : marquée pour ne pas être retirée par le contrôle des montures parasites
        mount.getPersistentData().putBoolean("ws_mount", true);
        if (level.addFreshEntity(mount)) {
            MobRegistry.register(mount, mountToHordeTemplate(mountCfg));
            return mount;
        }
        WaveSurvivorMod.LOGGER.warn("[CustomSpawner] addFreshEntity refusé pour mount '{}'", mountCfg.entityType);
        return null;
    }

    // ─────── Helpers ───────

    /**
     * Convertit une CustomEntityData en HordeEntity synthétique (pour MobRegistry / loot).
     * On garde le lootTable et les stats effectives, ce qui suffit à HordeLootHandler.
     */
    private static HordeEntity toHordeTemplate(CustomEntityData ce) {
        HordeEntity t = new HordeEntity();
        t.entityType = ce.entityName; // clé pour /ws status
        t.customName = ce.displayName;
        if (ce.stats != null) {
            t.maxHealth = ce.stats.maxHealth;
            t.attackDamage = ce.stats.attackDamage;
            t.movementSpeed = ce.stats.movementSpeed;
            t.followRange = ce.stats.followRange;
            t.knockbackResistance = ce.stats.knockbackResistance;
        }
        t.lootTable = ce.lootTable;
        return t;
    }

    private static HordeEntity mountToHordeTemplate(CustomEntityData.MountEntity m) {
        HordeEntity t = new HordeEntity();
        t.entityType = m.entityType;
        t.customName = m.customName;
        if (m.stats != null) {
            t.maxHealth = m.stats.maxHealth;
            t.movementSpeed = m.stats.movementSpeed;
        }
        t.lootTable = m.lootTable;
        return t;
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

    private static void applyStats(LivingEntity living, CustomEntityData.Stats stats) {
        if (stats == null) return;
        setAttribute(living, Attributes.MAX_HEALTH, stats.maxHealth);
        living.setHealth((float) stats.maxHealth);
        setAttribute(living, Attributes.ATTACK_DAMAGE, stats.attackDamage);
        setAttribute(living, Attributes.MOVEMENT_SPEED, stats.movementSpeed);
        setAttribute(living, Attributes.FOLLOW_RANGE, stats.followRange);
        setAttribute(living, Attributes.KNOCKBACK_RESISTANCE, stats.knockbackResistance);
    }

    // ─── Réglages de la horde par-dessus l'entité custom ───

    /** Stats de l'entrée de horde (seulement si override_stats). */
    private static void applyHordeStats(LivingEntity living, HordeEntity h) {
        if (h.maxHealth > 0) {
            setAttribute(living, Attributes.MAX_HEALTH, h.maxHealth);
            living.setHealth((float) h.maxHealth);
        }
        if (h.attackDamage > 0) setAttribute(living, Attributes.ATTACK_DAMAGE, h.attackDamage);
        if (h.movementSpeed > 0) setAttribute(living, Attributes.MOVEMENT_SPEED, h.movementSpeed);
        if (h.followRange > 0) setAttribute(living, Attributes.FOLLOW_RANGE, h.followRange);
        if (h.knockbackResistance > 0) setAttribute(living, Attributes.KNOCKBACK_RESISTANCE, h.knockbackResistance);
    }

    /** Équipement de l'entrée de horde : chaque slot RENSEIGNÉ remplace celui de l'entité (les vides sont ignorés). */
    private static void applyOverrideEquipment(Mob mob, HordeEntity h) {
        if (h != null) applyEquipmentOverride(mob, h.equipment);
    }

    /** Utilisé aussi par BossManager (équipement de l'onglet Boss sur un boss custom). */
    public static void applyEquipmentOverride(Mob mob, java.util.Map<String, com.wavesurvivor.horde.model.EquipmentEntry> equipment) {
        if (mob == null || equipment == null || equipment.isEmpty()) return;
        for (var entry : equipment.entrySet()) {
            EquipmentSlot slot = parseSlot(entry.getKey());
            var data = entry.getValue();
            if (slot == null || data == null || data.item == null || data.item.isBlank()) continue;
            try {
                var st = com.wavesurvivor.horde.loot.LootItems.resolve(data.item, 1); // enchantements compris
                if (st.isEmpty()) continue;
                mob.setItemSlot(slot, st);
                mob.setDropChance(slot, Math.max(0f, Math.min(1f, data.dropChance / 100f)));
            } catch (Exception ignored) {}
        }
    }

    /** Loot : celui de l'entité + celui de l'entrée de horde. */
    private static HordeEntity templateWithOverride(CustomEntityData ce, HordeEntity h) {
        HordeEntity t = toHordeTemplate(ce);
        if (h != null) {
            // Mode Kingdom : rôle de siège et Gardien définis sur l'entrée de la horde
            t.siegeRole = h.siegeRole;
            t.kingdomGuardian = h.kingdomGuardian;
        }
        if (h != null && h.lootTable != null && !h.lootTable.isEmpty()) {
            java.util.List<com.wavesurvivor.horde.model.LootEntry> merged = new java.util.ArrayList<>();
            if (t.lootTable != null) merged.addAll(t.lootTable);
            merged.addAll(h.lootTable);
            t.lootTable = merged;
        }
        return t;
    }

    private static void applyMountStats(LivingEntity living, CustomEntityData.Stats stats) {
        if (stats == null) return;
        // Mounts ne portent que HP et vitesse dans le format base44
        setAttribute(living, Attributes.MAX_HEALTH, stats.maxHealth);
        living.setHealth((float) stats.maxHealth);
        setAttribute(living, Attributes.MOVEMENT_SPEED, stats.movementSpeed);
    }

    private static void setAttribute(LivingEntity living, Attribute attr, double value) {
        AttributeInstance inst = living.getAttribute(attr);
        if (inst != null) inst.setBaseValue(value);
    }

    private static void applyName(LivingEntity living, CustomEntityData ce, String override, boolean visible) {
        String name = override != null && !override.isBlank()
                ? override
                : (ce.displayName != null && !ce.displayName.isBlank() ? ce.displayName : ce.entityName);
        if (name != null && !name.isBlank()) {
            name = com.wavesurvivor.i18n.WSLang.t(name); // nom de la config, traduit
            // Le displayName peut contenir des codes couleur §
            living.setCustomName(Component.literal(name).withStyle(visible ? ChatFormatting.RED : ChatFormatting.WHITE));
            living.setCustomNameVisible(true); // toujours visible (boss en rouge, monstres en blanc)
        }
    }

    /**
     * Format base44 : equipment = slot -> item id (string simple).
     * Pas de dropChance dans ce format : on force 0 (pas de drop équipement) pour éviter le loot involontaire.
     */
    private static void applyEquipment(Mob mob, Map<String, String> equipment) {
        if (equipment == null || equipment.isEmpty()) return;
        for (Map.Entry<String, String> entry : equipment.entrySet()) {
            EquipmentSlot slot = parseSlot(entry.getKey());
            if (slot == null) continue;
            String itemId = entry.getValue();
            // Slot vide/invalide : CLEAR pour écraser ce que finalizeSpawn aurait mis (armures random)
            if (itemId == null || itemId.isBlank()) {
                mob.setItemSlot(slot, ItemStack.EMPTY);
                mob.setDropChance(slot, 0f);
                continue;
            }

            ItemStack eq = com.wavesurvivor.horde.loot.LootItems.resolve(itemId, 1); // enchantements compris
            if (eq.isEmpty()) {
                mob.setItemSlot(slot, ItemStack.EMPTY);
                mob.setDropChance(slot, 0f);
                continue;
            }

            mob.setItemSlot(slot, eq);
            mob.setDropChance(slot, 0f); // pas de drop par défaut sur CustomEntity
        }
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
