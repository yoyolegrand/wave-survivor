package com.wavesurvivor.horde.boss;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.CustomEntityData;
import com.wavesurvivor.horde.model.BossWave;
import com.wavesurvivor.horde.model.EquipmentEntry;
import com.wavesurvivor.horde.skill.SkillManager;
import com.wavesurvivor.horde.spawn.CustomEntityRegistry;
import com.wavesurvivor.horde.spawn.CustomEntitySpawner;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

public class BossManager {

    private static final Random RNG = new Random();
    private static final Map<UUID, ServerBossEvent> ACTIVE_BARS = new HashMap<>();

    public static boolean spawnBoss(ServerLevel level, BossWave boss, BlockPos center, int radius,
                                     MinecraftServer server) {
        LivingEntity spawned;
        CustomEntityData ceForSkills = null;

        if (boss.useCustomEntity && boss.customEntityName != null && !boss.customEntityName.isBlank()) {
            CustomEntityData ce = CustomEntityRegistry.get(boss.customEntityName);
            if (ce == null) {
                WaveSurvivorMod.LOGGER.warn("[Boss] CustomEntity '{}' introuvable — boss '{}' skip.",
                        boss.customEntityName, boss.bossName);
                return false;
            }
            spawned = CustomEntitySpawner.spawnBoss(level, ce, center, radius, boss.bossName); // nom traduit dans applyName
            if (spawned == null) return false;
            // Équipement défini dans l'onglet Boss : remplace celui de l'entité custom (slots renseignés)
            if (spawned instanceof net.minecraft.world.entity.Mob bm) CustomEntitySpawner.applyEquipmentOverride(bm, boss.equipment);
            ceForSkills = ce;
        } else {
            EntityType<?> type = resolveEntityType(boss.entityType);
            if (type == null) {
                WaveSurvivorMod.LOGGER.warn("[Boss] entityType introuvable : {}", boss.entityType);
                return false;
            }

            BlockPos pos = com.wavesurvivor.horde.spawn.SpawnZone.pick(level, center, radius, type); // zone de spawn sûre
            Entity ent = type.create(level);
            if (ent == null) return false;

            ent.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, RNG.nextFloat() * 360f, 0f);

            if (ent instanceof LivingEntity living) {
                applyStats(living, boss);
                String name = boss.bossName != null && !boss.bossName.isBlank() ? com.wavesurvivor.i18n.WSLang.t(boss.bossName) : com.wavesurvivor.i18n.WSLang.t("srv.boss_5859");
                living.setCustomName(Component.literal(name).withStyle(ChatFormatting.RED));
                living.setCustomNameVisible(true);
            }
            if (ent instanceof Mob mob) {
                applyEquipment(mob, boss);
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
                com.wavesurvivor.horde.spawn.NetherSpawnFix.apply(mob); // adulte, pas de zombification
            }

            if (!level.addFreshEntity(ent)) return false;
            if (!(ent instanceof LivingEntity)) return false;
            spawned = (LivingEntity) ent;

            WaveSurvivorMod.LOGGER.info("[Boss] '{}' (vanilla {}) spawné en ({},{},{})",
                    boss.bossName, boss.entityType, pos.getX(), pos.getY(), pos.getZ());
        }

        if (spawned instanceof Mob mob) {
            tryAggroNearestPlayer(mob, server);
        }

        // Loot de la bossWave (s'ajoute au loot de la CustomEntity éventuelle)
        com.wavesurvivor.horde.loot.HordeLootHandler.registerBossLoot(spawned, boss.lootTable);

        ServerBossEvent bar = new ServerBossEvent(
                Component.literal("⚔ " + (boss.bossName != null ? com.wavesurvivor.i18n.WSLang.t(boss.bossName) : com.wavesurvivor.i18n.WSLang.t("srv.boss_5859")) + " ⚔")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.PROGRESS
        );
        bar.setProgress(1.0f);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            bar.addPlayer(p);
        }
        ACTIVE_BARS.put(spawned.getUUID(), bar);

        // Difficulté « Boss 2.0 » : mise à l'échelle, phases, anti-exploitation, bouclier, plafond de dégâts, affixes
        BossDirector.register(spawned, boss);
        String affixes = BossDirector.affixSuffix(spawned.getUUID());
        if (!affixes.isEmpty()) bar.setName(bar.getName().copy().append(Component.literal(affixes)));

        BossMinionTracker.register(spawned.getUUID(), boss, server.getTickCount());

        // Enregistrement skills (Phase 2b) — uniquement pour les CustomEntity boss
        if (ceForSkills != null) {
            WaveSurvivorMod.LOGGER.info("[Boss] spawnBoss terminé pour '{}' (uuid={}) → SkillManager.register (ceForSkills.skills={})",
                    boss.bossName, spawned.getUUID(),
                    ceForSkills.skills != null ? ceForSkills.skills.toString() : "NULL");
            SkillManager.register(spawned.getUUID(), ceForSkills);
            // BossConfig natif (playerAura + selfEffects + summon)
            BossConfigTracker.register(spawned.getUUID(), ceForSkills, server.getTickCount());
        } else {
            WaveSurvivorMod.LOGGER.info("[Boss] spawnBoss terminé pour '{}' (uuid={}) — pas de CustomEntity, skills skip",
                    boss.bossName, spawned.getUUID());
        }
        return true;
    }

    public static void updateBars(MinecraftServer server) {
        if (ACTIVE_BARS.isEmpty()) return;
        var it = ACTIVE_BARS.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            Entity ent = findInAllLevels(server, e.getKey());
            if (!(ent instanceof LivingEntity living) || !living.isAlive()) {
                e.getValue().removeAllPlayers();
                BossMinionTracker.unregister(e.getKey());
                SkillManager.unregister(e.getKey());
                BossConfigTracker.unregister(e.getKey());
                it.remove();
            } else {
                float pct = living.getHealth() / living.getMaxHealth();
                e.getValue().setProgress(Math.max(0f, Math.min(1f, pct)));
            }
        }
    }

    private static Entity findInAllLevels(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(uuid);
            if (e != null) return e;
        }
        return null;
    }

    private static void tryAggroNearestPlayer(Mob mob, MinecraftServer server) {
        ServerPlayer nearest = null;
        double bestDsq = Double.MAX_VALUE;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isCreative() || p.isSpectator()) continue;
            if (p.level() != mob.level()) continue;
            double dsq = p.distanceToSqr(mob);
            if (dsq < bestDsq) { bestDsq = dsq; nearest = p; }
        }
        if (nearest != null) {
            mob.setTarget(nearest);
            if (mob instanceof net.minecraft.world.entity.NeutralMob neutral) {
                neutral.setRemainingPersistentAngerTime(6000);
                neutral.setPersistentAngerTarget(nearest.getUUID());
            }
        }
    }

    public static void onBossDeath(UUID uuid) {
        ServerBossEvent bar = ACTIVE_BARS.remove(uuid);
        BossDirector.unregister(uuid);
        if (bar != null) {
            bar.removeAllPlayers();
            BossMinionTracker.unregister(uuid);
            SkillManager.unregister(uuid);
            BossConfigTracker.unregister(uuid);
            WaveSurvivorMod.LOGGER.info("[Boss] Bar wither retirée (kill).");
        }
    }

    public static void clearAll(MinecraftServer server) {
        if (server != null) {
            int discarded = 0;
            for (UUID uuid : ACTIVE_BARS.keySet()) {
                Entity ent = findInAllLevels(server, uuid);
                if (ent != null && ent.isAlive()) {
                    ent.discard();
                    discarded++;
                }
            }
            if (discarded > 0) {
                WaveSurvivorMod.LOGGER.info("[Boss] clearAll : {} boss discard.", discarded);
            }
        }
        for (ServerBossEvent bar : ACTIVE_BARS.values()) {
            bar.removeAllPlayers();
        }
        ACTIVE_BARS.clear();
        BossDirector.clearAll();
        BossMinionTracker.clearAll();
        SkillManager.clearAll();
        BossConfigTracker.clearAll();
    }

    public static void clearAll() {
        clearAll(null);
    }

    public static boolean isBoss(UUID uuid) {
        return ACTIVE_BARS.containsKey(uuid);
    }

    public static int activeBossCount() {
        return ACTIVE_BARS.size();
    }

    private static EntityType<?> resolveEntityType(String id) {
        if (id == null || id.isBlank()) return null;
        try {
            Optional<EntityType<?>> opt = ForgeRegistries.ENTITY_TYPES.getHolder(new ResourceLocation(id)).map(h -> h.value());
            return opt.orElse(null);
        } catch (Exception e) { return null; }
    }

    private static BlockPos pickSpawnPos(Level level, BlockPos center, int radius) {
        return com.wavesurvivor.horde.spawn.SpawnZone.pick(level, center, radius, null);
    }

    private static void applyStats(LivingEntity living, BossWave b) {
        setAttribute(living, Attributes.MAX_HEALTH, b.maxHealth);
        living.setHealth((float) b.maxHealth);
        setAttribute(living, Attributes.ATTACK_DAMAGE, b.attackDamage);
        setAttribute(living, Attributes.MOVEMENT_SPEED, b.movementSpeed);
        setAttribute(living, Attributes.FOLLOW_RANGE, b.followRange);
        setAttribute(living, Attributes.KNOCKBACK_RESISTANCE, b.knockbackResistance);
    }

    private static void setAttribute(LivingEntity living, Attribute attr, double value) {
        AttributeInstance inst = living.getAttribute(attr);
        if (inst != null) inst.setBaseValue(value);
    }

    private static void applyEquipment(Mob mob, BossWave b) {
        if (b.equipment == null || b.equipment.isEmpty()) return;
        for (Map.Entry<String, EquipmentEntry> entry : b.equipment.entrySet()) {
            EquipmentSlot slot = parseSlot(entry.getKey());
            if (slot == null) continue;
            EquipmentEntry data = entry.getValue();
            if (data == null || data.item == null || data.item.isBlank()) continue;
            Item item;
            try { item = BuiltInRegistries.ITEM.get(new ResourceLocation(data.item)); }
            catch (Exception e) { continue; }
            if (item == null || item == Items.AIR) continue;
            mob.setItemSlot(slot, new ItemStack(item));
            float chance = Math.max(0f, Math.min(1f, data.dropChance / 100f));
            mob.setDropChance(slot, chance);
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
