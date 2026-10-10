package com.wavesurvivor.horde.roulette;

import com.wavesurvivor.WaveSurvivorMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Spawn/despawn les roulette chests entre les vagues.
 * Cycle de vie identique aux marchands :
 *   - clear de vague N  → spawnAll (pose les coffres + hologramme au-dessus)
 *   - start de vague N+1 → despawnAll (retire coffres + hologrammes)
 */
public class RouletteChestSpawner {

    private static final String HOLOGRAM_TAG = "roulette_hologram";

    /** Positions des coffres actuellement spawn (pour cleanup facile). */
    private static final List<BlockPos> ACTIVE_CHESTS = new ArrayList<>();

    /** Spawn tous les coffres configurés pour cette horde. */
    public static void spawnAll(String hordeName, MinecraftServer server) {
        if (com.wavesurvivor.horde.bossrush.BossRush.active() && !com.wavesurvivor.horde.bossrush.BossRush.supplyChests()) return; // Boss Rush : coffres de ravitaillement désactivés
        List<RouletteChestSpawnStore.SpawnPoint> spawns = RouletteChestSpawnStore.getSpawns(hordeName);
        if (spawns == null || spawns.isEmpty()) return;

        int spawned = 0;
        for (RouletteChestSpawnStore.SpawnPoint sp : spawns) {
            RouletteChestConfig cfg = RouletteChestRegistry.get(sp.configKey);
            if (cfg == null) {
                WaveSurvivorMod.LOGGER.warn("[RouletteSpawner] Config '{}' introuvable (horde '{}')", sp.configKey, hordeName);
                continue;
            }

            ServerLevel level = resolveLevel(server, sp.dimension);
            if (level == null) continue;

            BlockPos pos = new BlockPos(sp.x, sp.y, sp.z);
            if (spawnOne(level, pos, cfg)) {
                spawned++;
            }
        }

        if (spawned > 0) {
            com.wavesurvivor.network.EventFeedPacket.toAll(server, com.wavesurvivor.i18n.WSLang.c("roulette.spawned", spawned), "minecraft:chest", 0xFFFFC34D, false);
        }
        WaveSurvivorMod.LOGGER.info("[RouletteSpawner] {} coffre(s) spawn pour horde '{}'", spawned, hordeName);
    }

    /** Spawn des coffres à des positions calculées (zone d'autel), un par config. */
    public static void spawnAtSpots(ServerLevel level, List<com.wavesurvivor.altar.AltarLayout.ChestSpot> spots) {
        if (com.wavesurvivor.horde.bossrush.BossRush.active() && !com.wavesurvivor.horde.bossrush.BossRush.supplyChests()) return;
        if (spots == null || spots.isEmpty()) return;
        int spawned = 0;
        for (var s : spots) {
            if (spawnOne(level, s.pos(), s.config())) spawned++;
        }
        if (spawned > 0) {
            com.wavesurvivor.network.EventFeedPacket.toAll(level.getServer(), com.wavesurvivor.i18n.WSLang.c("roulette.spawned_altar", spawned), "minecraft:chest", 0xFFFFC34D, false);
        }
        WaveSurvivorMod.LOGGER.info("[RouletteSpawner] {} coffre(s) spawn dans la zone d'autel", spawned);
    }

    /** Spawn UN coffre à la position donnée avec son hologramme. */
    private static boolean spawnOne(ServerLevel level, BlockPos pos, RouletteChestConfig cfg) {
        try {
            // Résoudre le block à poser (coffre vanilla → Coffre Roulette)
            String blockId = RouletteChestManager.resolveChestBlockId(cfg);
            Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockId));
            if (block == null) {
                WaveSurvivorMod.LOGGER.warn("[RouletteSpawner] BlockType inconnu : {}", blockId);
                return false;
            }
            BlockState state = block.defaultBlockState();
            level.setBlock(pos, state, 3);

            // Set le custom name via BlockEntity
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof BaseContainerBlockEntity container) {
                container.setCustomName(Component.literal(cfg.chestName())); // nom d'origine : sert à identifier le coffre
                container.setChanged();
            }

            // Enregistre dans le manager pour tick particules + right-click
            RouletteChestManager.registerIdle(pos, cfg);

            // Spawn hologramme (armor_stand invisible avec name)
            spawnHologram(level, pos, cfg);

            ACTIVE_CHESTS.add(pos.immutable());
            return true;
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[RouletteSpawner] Erreur spawnOne @ {} : {}", pos, e.getMessage());
            return false;
        }
    }

    /** Spawn un armor_stand invisible avec le nom du coffre au-dessus. */
    private static void spawnHologram(ServerLevel level, BlockPos chestPos, RouletteChestConfig cfg) {
        ArmorStand stand = new ArmorStand(level, chestPos.getX() + 0.5, chestPos.getY() + 1.2, chestPos.getZ() + 0.5);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        // setMarker() est private en 1.20.1, on passe par NBT (Marker=1) pour rendre le hitbox non-cliquable
        CompoundTag tag = new CompoundTag();
        stand.saveWithoutId(tag);
        tag.putBoolean("Marker", true);
        stand.load(tag);
        stand.setCustomName(Component.literal("§6§l" + com.wavesurvivor.i18n.WSLang.t(cfg.chestName())));
        stand.setCustomNameVisible(true);
        stand.addTag(HOLOGRAM_TAG);
        level.addFreshEntity(stand);
    }

    /** Retire tous les coffres actifs + hologrammes. */
    public static void despawnAll(MinecraftServer server) {
        if (server == null || ACTIVE_CHESTS.isEmpty()) return;

        int chestsRemoved = 0;
        int holoRemoved = 0;

        for (BlockPos pos : new ArrayList<>(ACTIVE_CHESTS)) {
            for (ServerLevel level : server.getAllLevels()) {
                if (!level.isLoaded(pos)) continue;

                // Retire l'hologramme (armor_stand tagué) dans un cube 1×2×1
                AABB box = new AABB(pos.getX(), pos.getY(), pos.getZ(),
                        pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1);
                for (ArmorStand as : level.getEntitiesOfClass(ArmorStand.class, box)) {
                    if (as.getTags().contains(HOLOGRAM_TAG)) {
                        as.discard();
                        holoRemoved++;
                    }
                }

                // Unregister du manager
                RouletteChestManager.unregister(pos);

                // Set le block à AIR (le block est ceux qu'on a spawn)
                if (level.getBlockEntity(pos) instanceof BaseContainerBlockEntity) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    chestsRemoved++;
                }
                break;
            }
        }

        ACTIVE_CHESTS.clear();
        WaveSurvivorMod.LOGGER.info("[RouletteSpawner] Despawn : {} coffre(s), {} hologramme(s)", chestsRemoved, holoRemoved);
    }

    private static ServerLevel resolveLevel(MinecraftServer server, String dimensionId) {
        if (server == null) return null;
        String dim = dimensionId != null ? dimensionId : "minecraft:overworld";
        try {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dim));
            ServerLevel lvl = server.getLevel(key);
            if (lvl != null) return lvl;
        } catch (Exception ignore) {}
        return server.overworld();
    }

    private static void broadcast(MinecraftServer server, Component msg) {
        if (server == null) return;
        for (var p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(msg);
        }
    }
}
