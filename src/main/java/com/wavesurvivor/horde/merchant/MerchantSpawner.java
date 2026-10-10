package com.wavesurvivor.horde.merchant;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.HordeConfigMultiData;
import com.wavesurvivor.config.model.MerchantData;
import com.wavesurvivor.config.model.TradeData;
import com.wavesurvivor.horde.chaos.ChaosTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Spawn les marchands d'une horde et gère leur cleanup.
 * Chaque marchand est tagué "wave_survivor_merchant" pour retrouvaille facile.
 */
public class MerchantSpawner {

    private static final String MERCHANT_TAG = "wave_survivor_merchant";
    private static final String NBT_HORDE_NAME = "hordeName";
    private static final Random RNG = new Random();

    /** Tracking des UUIDs des marchands spawnés pendant cette horde. */
    private static final List<UUID> ACTIVE_MERCHANTS = new ArrayList<>();

    /**
     * Spawn tous les marchands configurés pour cette horde.
     * Appelé au start de la horde.
     */
    public static void spawnAll(HordeConfigMultiData horde, ServerLevel level) {
        spawnAll(horde, level, null, null);
    }

    public static void spawnAll(HordeConfigMultiData horde, ServerLevel level, List<BlockPos> overridePositions) {
        spawnAll(horde, level, overridePositions, null);
    }

    /**
     * Idem, mais avec des positions imposées (zone d'autel, écran « 📍 Disposition ») : une position PAR marchand,
     * dans l'ordre de la liste (null = pas de place / non posé sans placement auto → il n'apparaît pas).
     * {@code lookAt} : les marchands se tournent vers ce point (l'autel / le Monolithe).
     */
    public static void spawnAll(HordeConfigMultiData horde, ServerLevel level, List<BlockPos> overridePositions, BlockPos lookAt) {
        if (com.wavesurvivor.horde.bossrush.BossRush.active() && !com.wavesurvivor.horde.bossrush.BossRush.merchants()) return; // Boss Rush : marchands désactivés dans l'éditeur
        HordeConfigMultiData.ConfigDataInner cfg = horde.configData;
        if (cfg == null || !cfg.useMerchants || cfg.merchants == null || cfg.merchants.isEmpty()) {
            WaveSurvivorMod.LOGGER.info("[Merchant] Aucun marchand configuré pour '{}'", horde.hordeName);
            return;
        }

        BlockPos hordeCenter = cfg.spawnCoords != null
                ? new BlockPos(cfg.spawnCoords.x, cfg.spawnCoords.y, cfg.spawnCoords.z)
                : BlockPos.ZERO;

        MinecraftServer server = level.getServer();
        int spawned = 0;
        int idx = -1;
        for (MerchantData m : cfg.merchants) {
            idx++;
            // Roll spawnChance
            double roll = RNG.nextDouble();
            if (cfg.merchantDebug) {
                String dbg = String.format(com.wavesurvivor.i18n.WSLang.t("srv.debug_marchand_s_roll_2f_chance_2f"),
                        m.name, roll, m.spawnChance);
                broadcast(server, Component.literal(dbg));
            }
            if (roll > m.spawnChance) continue;

            BlockPos pos = overridePositions != null
                    ? (idx < overridePositions.size() ? overridePositions.get(idx) : null)
                    : resolvePosition(m, hordeCenter);
            if (pos == null) {
                if (overridePositions == null) WaveSurvivorMod.LOGGER.warn("[Merchant] '{}' n'a ni offset ni spawnPos — skip", m.name);
                continue;
            }

            LivingEntity ent = spawnMerchantEntity(m, pos, level, horde.hordeName);
            if (ent != null && lookAt != null) {
                // Tourné vers le Monolithe / l'autel
                float yaw = (float) Math.toDegrees(Math.atan2(-(lookAt.getX() + 0.5 - ent.getX()), lookAt.getZ() + 0.5 - ent.getZ()));
                ent.setYRot(yaw);
                ent.setYHeadRot(yaw);
                ent.setYBodyRot(yaw);
            }
            if (ent != null) {
                ACTIVE_MERCHANTS.add(ent.getUUID());
                spawned++;

                // Annonce dans le fil d'événements (plus dans le chat)
                com.wavesurvivor.network.EventFeedPacket.toAll(server, Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.marchand") + com.wavesurvivor.i18n.WSLang.t(m.name) + com.wavesurvivor.i18n.WSLang.t("srv.est_arrive_position")
                        + pos.getX() + ", " + pos.getY() + ", " + pos.getZ())
                        .withStyle(ChatFormatting.GOLD), "minecraft:emerald", 0xFFFFC34D, false);

                // Support duration : despawn programmé avec poof + villager.no via ChaosTracker
                if (m.duration > 0) {
                    long despawnTick = server.getTickCount() + (long) m.duration * 20L;
                    ChaosTracker.schedule(ent.getId(), despawnTick);
                }

                if (cfg.merchantDebug) {
                    WaveSurvivorMod.LOGGER.info("[Merchant] '{}' spawné @ {} (uuid={}, duration={}s)",
                            m.name, pos, ent.getUUID(), m.duration);
                }
            }
        }

        // Broadcast résumé
        if (spawned > 0) {
            com.wavesurvivor.network.EventFeedPacket.toAll(server, Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.marchand") + spawned + com.wavesurvivor.i18n.WSLang.t("srv.marchand_s_apparu_s"))
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), "minecraft:emerald", 0xFFFFC34D, false);
        } else if (cfg.merchantDebug) {
            broadcast(server, Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.debug_marchand_aucun_marchand_n_a_spawn")));
        }

        WaveSurvivorMod.LOGGER.info("[Merchant] {} marchand(s) spawné(s) pour horde '{}'", spawned, horde.hordeName);
    }

    /** Broadcast à tous les joueurs. */
    private static void broadcast(MinecraftServer server, Component msg) {
        if (server == null) return;
        for (var p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(msg);
        }
    }

    /** Résout la position finale : offset prioritaire, sinon spawnPos absolue. */
    private static BlockPos resolvePosition(MerchantData m, BlockPos hordeCenter) {
        if (m.offset != null) {
            return hordeCenter.offset(m.offset.x, m.offset.y, m.offset.z);
        }
        if (m.spawnPos != null) {
            return new BlockPos(m.spawnPos.x, m.spawnPos.y, m.spawnPos.z);
        }
        return null;
    }

    private static LivingEntity spawnMerchantEntity(MerchantData m, BlockPos pos, ServerLevel level, String hordeName) {
        EntityType<?> type;
        try {
            type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(m.entityType));
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.warn("[Merchant] Type entité invalide '{}' pour marchand '{}'", m.entityType, m.name);
            return null;
        }
        if (type == null) return null;

        Entity ent = type.create(level);
        if (!(ent instanceof LivingEntity living)) {
            WaveSurvivorMod.LOGGER.warn("[Merchant] Type '{}' n'est pas LivingEntity pour '{}'", m.entityType, m.name);
            return null;
        }

        living.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        living.setCustomName(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.marchand") + com.wavesurvivor.i18n.WSLang.t(m.name))
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        living.setCustomNameVisible(true);
        living.setInvulnerable(true);
        // setNoAi + setPersistenceRequired sont sur Mob, pas LivingEntity
        if (living instanceof net.minecraft.world.entity.Mob mob) {
            mob.setNoAi(true);
            try { mob.setPersistenceRequired(); } catch (Exception ignore) {}
        }

        // Tag pour cleanup ultérieur
        living.addTag(MERCHANT_TAG);
        CompoundTag data = living.getPersistentData();
        data.putBoolean("isHordeMerchant", true);
        data.putString(NBT_HORDE_NAME, hordeName);

        if (!level.addFreshEntity(living)) {
            WaveSurvivorMod.LOGGER.warn("[Merchant] Impossible d'ajouter '{}' au monde", m.name);
            return null;
        }

        // Setup villageois : profession + trades
        if (living instanceof Villager villager) {
            applyProfession(villager, m.profession);
            applyTrades(villager, m.trades);
        }

        return living;
    }

    private static void applyProfession(Villager villager, String professionId) {
        if (professionId == null || professionId.isBlank() || "none".equalsIgnoreCase(professionId)) return;
        try {
            ResourceLocation profRL = professionId.contains(":")
                    ? new ResourceLocation(professionId)
                    : new ResourceLocation("minecraft", professionId.toLowerCase());
            VillagerProfession prof = BuiltInRegistries.VILLAGER_PROFESSION.get(profRL);
            if (prof == null) {
                WaveSurvivorMod.LOGGER.warn("[Merchant] Profession '{}' introuvable", professionId);
                return;
            }
            VillagerData vdata = villager.getVillagerData()
                    .setType(VillagerType.PLAINS)
                    .setLevel(5)
                    .setProfession(prof);
            villager.setVillagerData(vdata);
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.warn("[Merchant] Erreur profession '{}': {}", professionId, e.getMessage());
        }
    }

    private static void applyTrades(Villager villager, List<TradeData> trades) {
        if (trades == null || trades.isEmpty()) return;
        MerchantOffers offers = villager.getOffers();
        offers.clear();

        for (TradeData t : trades) {
            ItemStack input1 = resolveItemStack(t.input1, t.input1Count);
            if (input1.isEmpty()) continue;
            ItemStack input2 = (t.input2 != null && !t.input2.isBlank() && t.input2Count > 0)
                    ? resolveItemStack(t.input2, t.input2Count)
                    : ItemStack.EMPTY;
            ItemStack output = resolveOutputStack(t.output, t.outputCount);
            if (output.isEmpty()) continue;

            int maxUses = Math.max(1, t.maxUses);
            MerchantOffer offer = new MerchantOffer(input1, input2, output, maxUses, 0, 0.0f);
            offers.add(offer);
        }
    }

    private static ItemStack resolveItemStack(String id, int count) {
        // Résolveur partagé : gère aussi les clés de roulette chest (roulettechest:key_...)
        return com.wavesurvivor.horde.loot.LootItems.resolve(id, count);
    }

    /** Sortie de trade : même résolveur (vraie clé de roulette chest pour roulettechest:key_*). */
    private static ItemStack resolveOutputStack(String output, int count) {
        return com.wavesurvivor.horde.loot.LootItems.resolve(output, count);
    }

    /** Cleanup tous les marchands actifs (fin de horde). */
    public static void despawnAll(MinecraftServer server) {
        if (server == null || ACTIVE_MERCHANTS.isEmpty()) return;
        int removed = 0;
        for (UUID id : new ArrayList<>(ACTIVE_MERCHANTS)) {
            for (ServerLevel level : server.getAllLevels()) {
                Entity e = level.getEntity(id);
                if (e != null && !e.isRemoved()) {
                    e.discard();
                    removed++;
                    break;
                }
            }
        }
        // Cleanup aussi les entités taguées orphelines (safety net)
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                if (e.getTags().contains(MERCHANT_TAG) && !e.isRemoved()) {
                    e.discard();
                    removed++;
                }
            }
        }
        ACTIVE_MERCHANTS.clear();
        WaveSurvivorMod.LOGGER.info("[Merchant] {} marchand(s) despawné(s)", removed);
    }
}
