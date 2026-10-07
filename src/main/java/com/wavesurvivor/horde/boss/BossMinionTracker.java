package com.wavesurvivor.horde.boss;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.CustomEntityData;
import com.wavesurvivor.horde.model.BossMinionConfig;
import com.wavesurvivor.horde.model.BossWave;
import com.wavesurvivor.horde.model.EquipmentEntry;
import com.wavesurvivor.horde.model.HordeEntity;
import com.wavesurvivor.horde.spawn.CustomEntityRegistry;
import com.wavesurvivor.horde.spawn.CustomEntitySpawner;
import com.wavesurvivor.horde.spawn.MobRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

public class BossMinionTracker {

    private static final Random RNG = new Random();

    public static class TrackerState {
        public BossWave boss;
        public long nextTriggerTick;
        public boolean healthTriggered;
    }

    private static final Map<UUID, TrackerState> STATES = new HashMap<>();

    public static void register(UUID bossUuid, BossWave boss, long currentTick) {
        if (!boss.useMinionSummon || boss.minionConfig == null) {
            WaveSurvivorMod.LOGGER.info("[Minions] Skip register '{}' : useMinionSummon={} minionConfig={}",
                    boss.bossName, boss.useMinionSummon, boss.minionConfig != null);
            return;
        }
        TrackerState s = new TrackerState();
        s.boss = boss;
        String triggerInfo;
        if ("interval".equalsIgnoreCase(boss.minionConfig.triggerType)) {
            long delayTicks = (long) (boss.minionConfig.triggerValue * 20);
            s.nextTriggerTick = currentTick + Math.max(20, delayTicks);
            triggerInfo = com.wavesurvivor.i18n.WSLang.t("srv.minions_in", (int) boss.minionConfig.triggerValue);
        } else {
            s.nextTriggerTick = -1;
            s.healthTriggered = false;
            triggerInfo = com.wavesurvivor.i18n.WSLang.t("srv.minions_at", (int) boss.minionConfig.triggerValue);
        }
        STATES.put(bossUuid, s);
        WaveSurvivorMod.LOGGER.info("[Minions] Tracker activé pour boss '{}' → {} × {} {}",
                boss.bossName, boss.minionConfig.minionCount, boss.minionConfig.minionType, triggerInfo);

        Component msg = Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.minions_announce", com.wavesurvivor.i18n.WSLang.t(boss.bossName),
                boss.minionConfig.minionCount, prettyName(boss.minionConfig.minionType), triggerInfo))
                .withStyle(ChatFormatting.DARK_RED);
        HordeBroadcast.toAll(msg);
    }

    public static void unregister(UUID bossUuid) {
        if (STATES.remove(bossUuid) != null) {
            WaveSurvivorMod.LOGGER.info("[Minions] Tracker unregister pour boss {}", bossUuid);
        }
    }

    public static void clearAll() {
        STATES.clear();
    }

    public static Map<UUID, TrackerState> statesSnapshot() {
        return new HashMap<>(STATES);
    }

    public static int trackerCount() {
        return STATES.size();
    }

    public static void tick(MinecraftServer server) {
        if (STATES.isEmpty()) return;
        long now = server.getTickCount();

        var it = STATES.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            UUID uuid = e.getKey();
            TrackerState s = e.getValue();

            LivingEntity bossEntity = findEntity(server, uuid);
            if (bossEntity == null || !bossEntity.isAlive()) {
                WaveSurvivorMod.LOGGER.info("[Minions] Boss '{}' introuvable/mort → tracker retiré", s.boss.bossName);
                it.remove();
                continue;
            }

            BossMinionConfig cfg = s.boss.minionConfig;
            if (cfg == null) continue;

            boolean shouldSummon = false;
            if ("health".equalsIgnoreCase(cfg.triggerType)) {
                if (!s.healthTriggered) {
                    float pct = bossEntity.getHealth() / bossEntity.getMaxHealth() * 100f;
                    if (pct <= cfg.triggerValue) {
                        shouldSummon = true;
                        s.healthTriggered = true;
                    }
                }
            } else {
                if (now >= s.nextTriggerTick) {
                    shouldSummon = true;
                    long delayTicks = (long) (cfg.triggerValue * 20);
                    s.nextTriggerTick = now + Math.max(20, delayTicks);
                }
            }

            if (shouldSummon) {
                WaveSurvivorMod.LOGGER.info("[Minions] Boss '{}' TRIGGER SUMMON ({}, val={})",
                        s.boss.bossName, cfg.triggerType, cfg.triggerValue);
                int spawned = summonMinions(bossEntity, cfg);
                Component msg = Component.literal("🩸 " + s.boss.bossName + " invoque " + spawned + " × " + prettyName(cfg.minionType) + " !")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    p.sendSystemMessage(msg);
                }
            }
        }
    }

    private static String prettyName(String id) {
        if (id == null) return "?";
        int idx = id.indexOf(':');
        return idx >= 0 ? id.substring(idx + 1) : id;
    }

    private static LivingEntity findEntity(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(uuid);
            if (e instanceof LivingEntity living) return living;
        }
        return null;
    }

    /** Construit un HordeEntity synthétique à partir d'un BossMinionConfig — pour tracking dans MobRegistry. */
    private static HordeEntity synthTemplate(BossMinionConfig cfg) {
        HordeEntity t = new HordeEntity();
        t.entityType = cfg.minionType;
        t.lootTable = cfg.minionLoot; // butin des sbires (déposé à leur mort comme pour les monstres de vague)
        if (cfg.minionStats != null) {
            t.maxHealth = cfg.minionStats.maxHealth;
            t.attackDamage = cfg.minionStats.attackDamage;
            t.movementSpeed = cfg.minionStats.movementSpeed;
            t.scale = cfg.minionStats.scale > 0 ? cfg.minionStats.scale : 1.0; // taille (Pehkui), appliquée à l'enregistrement
        }
        return t;
    }

    private static int summonMinions(LivingEntity boss, BossMinionConfig cfg) {
        ServerLevel level = (ServerLevel) boss.level();
        BlockPos bossPos = boss.blockPosition();
        int count = Math.max(1, cfg.minionCount);

        CustomEntityData ce = CustomEntityRegistry.get(cfg.minionType);
        if (ce != null) {
            // CustomEntitySpawner.spawnMobs register déjà dans MobRegistry
            int done = CustomEntitySpawner.spawnMobs(level, ce, bossPos, 2, count, null);
            WaveSurvivorMod.LOGGER.info("[Minions] Boss '{}' invoque {} × CustomEntity '{}'",
                    boss.getCustomName() != null ? boss.getCustomName().getString() : "?",
                    done, cfg.minionType);
            return done;
        }
        if (CustomEntityRegistry.looksLikeCustomRef(cfg.minionType)) {
            WaveSurvivorMod.LOGGER.warn("[Minions] minionType '{}' ressemble à une CustomEntity mais introuvable — skip.", cfg.minionType);
            return 0;
        }

        EntityType<?> type = resolveEntityType(cfg.minionType);
        if (type == null) {
            WaveSurvivorMod.LOGGER.warn("[Minions] minionType introuvable : {}", cfg.minionType);
            return 0;
        }

        // Template pour tracking (permet /ws skip de les tuer, /ws status de les compter)
        HordeEntity template = synthTemplate(cfg);

        int spawned = 0;
        for (int i = 0; i < count; i++) {
            double angle = RNG.nextDouble() * Math.PI * 2;
            double dist = 1 + RNG.nextDouble() * 2;
            int x = bossPos.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = bossPos.getZ() + (int) Math.round(Math.sin(angle) * dist);
            int y = bossPos.getY();

            Entity ent = type.create(level);
            if (ent == null) continue;
            ent.moveTo(x + 0.5, y, z + 0.5, RNG.nextFloat() * 360f, 0f);

            if (ent instanceof LivingEntity living) {
                applyMinionStats(living, cfg);
            }
            if (ent instanceof Mob mob) {
                // D'abord l'équipement naturel du monstre, PUIS celui de la config (qui le remplace)
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(bossPos), MobSpawnType.EVENT, null, null);
                applyMinionEquipment(mob, cfg);
            }
            if (level.addFreshEntity(ent)) {
                MobRegistry.register(ent, template);
                spawned++;
            }
        }
        WaveSurvivorMod.LOGGER.info("[Minions] Boss '{}' invoque {} × {} en ({},{},{})",
                boss.getCustomName() != null ? boss.getCustomName().getString() : "?",
                spawned, cfg.minionType, bossPos.getX(), bossPos.getY(), bossPos.getZ());
        return spawned;
    }

    private static EntityType<?> resolveEntityType(String id) {
        if (id == null || id.isBlank()) return null;
        try {
            Optional<EntityType<?>> opt = ForgeRegistries.ENTITY_TYPES.getHolder(new ResourceLocation(id)).map(h -> h.value());
            return opt.orElse(null);
        } catch (Exception e) { return null; }
    }

    private static void applyMinionStats(LivingEntity living, BossMinionConfig cfg) {
        if (cfg.minionStats == null) return;
        setAttribute(living, Attributes.MAX_HEALTH, cfg.minionStats.maxHealth);
        living.setHealth((float) cfg.minionStats.maxHealth);
        setAttribute(living, Attributes.ATTACK_DAMAGE, cfg.minionStats.attackDamage);
        setAttribute(living, Attributes.MOVEMENT_SPEED, cfg.minionStats.movementSpeed);
    }

    private static void setAttribute(LivingEntity living, Attribute attr, double value) {
        AttributeInstance inst = living.getAttribute(attr);
        if (inst != null) inst.setBaseValue(value);
    }

    private static void applyMinionEquipment(Mob mob, BossMinionConfig cfg) {
        if (cfg.minionEquipment == null || cfg.minionEquipment.isEmpty()) return;
        for (Map.Entry<String, EquipmentEntry> entry : cfg.minionEquipment.entrySet()) {
            EquipmentSlot slot = parseSlot(entry.getKey());
            if (slot == null) continue;
            EquipmentEntry data = entry.getValue();
            if (data == null || data.item == null || data.item.isBlank()) continue;
            ItemStack st = com.wavesurvivor.horde.loot.LootItems.resolve(data.item, 1); // enchantements compris
            if (st.isEmpty()) continue;
            mob.setItemSlot(slot, st);
            mob.setDropChance(slot, Math.max(0f, Math.min(1f, data.dropChance / 100f)));
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

    private static class HordeBroadcast {
        static void toAll(Component msg) {
            MinecraftServer srv = com.wavesurvivor.horde.HordeManager.get().getServer();
            if (srv == null) return;
            for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                p.sendSystemMessage(msg);
            }
        }
    }
}
