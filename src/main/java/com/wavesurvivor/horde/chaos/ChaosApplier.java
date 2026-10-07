package com.wavesurvivor.horde.chaos;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.CustomEntityData;
import com.wavesurvivor.horde.model.ChaosEvent;
import com.wavesurvivor.horde.spawn.CustomEntityRegistry;
import com.wavesurvivor.horde.spawn.CustomEntitySpawner;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

public class ChaosApplier {

    private static final Random RNG = new Random();

    public static void apply(ChaosEvent event, ServerLevel level, BlockPos center, MinecraftServer server) {
        if (event == null) return;

        String type0 = event.type != null ? event.type.toLowerCase() : "";
        boolean resource = "gisement".equals(type0) || "arbre".equals(type0);
        // Fil d'événements (plus dans le chat). Arbres et gisements : leur propre notification suffit, pas d'en-tête.
        if (!resource && event.message != null && !event.message.isBlank()) {
            Component msg = Component.literal(com.wavesurvivor.i18n.WSLang.t(event.message).replaceAll("§.", "").trim());
            com.wavesurvivor.network.EventFeedPacket.toAll(server, msg, "minecraft:blaze_powder",
                    com.wavesurvivor.network.EventFeedPacket.CHAOS, false);
        }
        playSound(event.sound, level, center);

        String type = event.type != null ? event.type.toLowerCase() : "";
        switch (type) {
            case "spawn"    -> applySpawn(event, level, center, server);
            case "merchant" -> applyMerchant(event, level, center, server);
            case "lightning"-> applyLightning(event, level, server);
            case "totem"    -> applyTotem(event, level, center);
            case "gisement" -> applyGisement(event, level, center, server, false);
            case "arbre"    -> applyGisement(event, level, center, server, true);
            default -> WaveSurvivorMod.LOGGER.warn("[Chaos] Type inconnu : {}", event.type);
        }
    }

    /** Gisements de minerai (pioche) ou arbres (hache) à exploiter (bénéfique) : placés entre distanceMin et distanceMax du centre. */
    private static void applyGisement(ChaosEvent event, ServerLevel level, BlockPos center, MinecraftServer server, boolean tree) {
        List<String> blocks = event.blocks != null && !event.blocks.isEmpty() ? event.blocks
                : tree ? List.of("minecraft:oak_log", "minecraft:oak_leaves") : List.of("minecraft:iron_ore", "minecraft:stone");
        String csv = String.join(",", blocks);
        if (tree) {
            // Arbre : « tronc1,tronc2|feuille1,feuille2 » (listes de l'éditeur, sinon repli sur blocks[0] / blocks[1])
            List<String> trunk = event.trunkBlocks != null && !event.trunkBlocks.isEmpty() ? event.trunkBlocks : List.of(blocks.get(0));
            List<String> leaves = event.leafBlocks != null && !event.leafBlocks.isEmpty() ? event.leafBlocks
                    : List.of(blocks.size() > 1 ? blocks.get(1) : "minecraft:oak_leaves");
            csv = String.join(",", trunk) + "|" + String.join(",", leaves) + "|"
                    + (event.fruitBlocks == null ? "minecraft:shroomlight" : String.join(",", event.fruitBlocks));
            blocks = trunk;
        }
        var first = com.wavesurvivor.horde.loot.LootItems.resolve(blocks.get(0), 1);
        String ore = first.isEmpty() ? "" : " (" + first.getHoverName().getString() + ")";
        String label = (tree ? com.wavesurvivor.i18n.WSLang.t("arbre.size.1") : com.wavesurvivor.i18n.WSLang.t("gisement.size.1")) + ore;
        int count = Math.max(1, Math.min(5, event.count));
        // Exploitant présent (Kingdom) : un gisement / arbre de plus, et chacun d'une taille au-dessus
        boolean miner = com.wavesurvivor.horde.kingdom.KingdomRoles.present(com.wavesurvivor.horde.kingdom.KingdomRoles.Role.MINER);
        if (miner) count = Math.min(6, count + 1);
        int dMin = Math.max(4, event.distanceMin), dMax = Math.max(dMin + 1, event.distanceMax);
        // Kingdom : la distance se mesure depuis le MONOLITHE (réglable dans l'éditeur), toujours hors du claim
        if (com.wavesurvivor.horde.kingdom.KingdomManager.isActive() && com.wavesurvivor.horde.kingdom.KingdomManager.center() != null) {
            center = com.wavesurvivor.horde.kingdom.KingdomManager.center();
            int outside = com.wavesurvivor.horde.kingdom.KingdomClaim.radius() + 3;
            dMin = Math.max(dMin, outside);
            dMax = Math.max(dMax, dMin + 4);
        }
        String firstDir = null;
        int firstDist = 0;
        for (int i = 0; i < count; i++) {
            double a = RNG.nextDouble() * Math.PI * 2;
            int dist = dMin + RNG.nextInt(dMax - dMin + 1);
            int x = center.getX() + (int) Math.round(Math.cos(a) * dist), z = center.getZ() + (int) Math.round(Math.sin(a) * dist);
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            var g = com.wavesurvivor.registry.ModEntities.GISEMENT.get().create(level);
            if (g == null) continue;
            int size = switch (event.size == null ? "petit" : event.size) {
                case "moyen" -> 1;
                case "grand" -> 2;
                case "aleatoire" -> { double r = RNG.nextDouble(); yield r < 0.6 ? 0 : r < 0.9 ? 1 : 2; }
                default -> 0;
            };
            if (miner) size = Math.min(2, size + 1);
            g.setTree(tree);
            g.setSize(size);
            g.moveTo(x + 0.5, y, z + 0.5, RNG.nextFloat() * 360f, 0);
            g.setup(csv, event.minTier);
            var hp = g.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            int hits = (int) Math.max(1, Math.round(Math.max(1, event.hits) * com.wavesurvivor.entity.GisementEntity.HIT_MULT[size]));
            if (hp != null) hp.setBaseValue(hits);
            g.setHealth(hits);
            g.setCustomNameVisible(true);
            g.getPersistentData().putBoolean("ws_revenant", true);
            if (level.addFreshEntity(g)) {
                String sizedLabel = (tree ? com.wavesurvivor.i18n.WSLang.t("arbre.size." + size)
                        : com.wavesurvivor.entity.GisementEntity.sizeLabel(size)) + ore;
                com.wavesurvivor.horde.chaos.GisementManager.register(g, event, sizedLabel,
                        com.wavesurvivor.entity.GisementEntity.LOOT_MULT[size]);
                if (i == 0) label = sizedLabel;
                level.sendParticles(tree ? net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER : net.minecraft.core.particles.ParticleTypes.WAX_OFF,
                        x + 0.5, y + 1, z + 0.5, 30, 0.5, 0.8, 0.5, 0.1);
                if (firstDir == null) { firstDir = direction(x - center.getX(), z - center.getZ()); firstDist = dist; }
            }
        }
        if (firstDir != null) {
            // Fil d'événements : « Grand arbre (End Stone) est apparu · hache en bois minimum » (sans direction : les rôles ont leur flèche)
            Component msg = com.wavesurvivor.i18n.WSLang.c(tree ? "arbre.feed" : "gisement.feed", label, (count > 1 ? " (×" + count + ")" : ""),
                    com.wavesurvivor.entity.GisementEntity.tierName(event.minTier));
            com.wavesurvivor.network.EventFeedPacket.toAll(server, msg, tree ? "minecraft:oak_sapling" : "minecraft:iron_ore",
                    tree ? com.wavesurvivor.network.EventFeedPacket.RESOURCE : com.wavesurvivor.network.EventFeedPacket.ORE, false);
        }
    }

    /** Direction cardinale (x = est, z = sud). */
    private static String direction(int dx, int dz) {
        double ang = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = nord, 90 = est
        if (ang < 0) ang += 360;
        return com.wavesurvivor.i18n.WSLang.t("dir." + ((int) Math.round(ang / 45) % 8));
    }

    /**
     * Totems du chaos : `count` totems plantés autour du centre, avec une aura (fureur, soin, malediction, invocation).
     * Apparence = TotemConfig (totemName). Disparaissent après `lifetime` s ; détruits → récompense.
     */
    private static void applyTotem(ChaosEvent event, ServerLevel level, BlockPos center) {
        var cfg = com.wavesurvivor.horde.spawn.CustomSkillRegistry.getTotem(event.totemName);
        String head = event.headItem != null && !event.headItem.isBlank() ? event.headItem
                : cfg != null && cfg.headItem != null && !cfg.headItem.isBlank() ? cfg.headItem : "minecraft:player_head";
        String aura = event.aura != null && !event.aura.isBlank() ? event.aura : "fureur";
        String summon = event.entityType != null && !event.entityType.isBlank() ? event.entityType : "minecraft:zombie";
        int count = Math.max(1, event.count);
        double offset = RNG.nextDouble() * Math.PI * 2;
        for (int i = 0; i < count; i++) {
            double a = offset + Math.PI * 2 * i / count;
            double dist = 6 + RNG.nextDouble() * 3;
            int x = center.getX() + (int) Math.round(Math.cos(a) * dist), z = center.getZ() + (int) Math.round(Math.sin(a) * dist);
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            var t = com.wavesurvivor.registry.ModEntities.TOTEM.get().create(level);
            if (t == null) continue;
            t.moveTo(x + 0.5, y, z + 0.5, RNG.nextFloat() * 360f, 0);
            t.setup(aura, head, -1);
            var hp = t.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            double health = Math.max(5, event.totemHealth);
            if (hp != null) hp.setBaseValue(health);
            t.setHealth((float) health);
            t.setCustomNameVisible(true);
            t.getPersistentData().putBoolean("ws_revenant", true);
            if (level.addFreshEntity(t)) {
                com.wavesurvivor.horde.chaos.TotemAuraManager.register(t, event, aura, summon);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING, x + 0.5, y + 1.3, z + 0.5,
                        30, 0.3, 0.8, 0.3, 0.3);
            }
        }
    }

    private static void applySpawn(ChaosEvent event, ServerLevel level, BlockPos center, MinecraftServer server) {
        if (event.entityType == null || event.entityType.isBlank()) return;
        int n = Math.max(1, event.count);
        List<UUID> spawnedUuids = new java.util.ArrayList<>();

        if (event.isCustomEntity || CustomEntityRegistry.looksLikeCustomRef(event.entityType)) {
            CustomEntityData ce = CustomEntityRegistry.get(event.entityType);
            if (ce == null) {
                WaveSurvivorMod.LOGGER.warn("[Chaos] CustomEntity '{}' introuvable — skip.", event.entityType);
                return;
            }
            int done = CustomEntitySpawner.spawnMobs(level, ce, center, 5, n, null);
            WaveSurvivorMod.LOGGER.info("[Chaos] Spawn CustomEntity : {} × '{}'", done, event.entityType);
            // Force aggro sur nearest player pour tous les mobs Chaos vivants près du center
            aggroNearbyMobsToPlayers(level, center, 30, server);
            return;
        }

        EntityType<?> type;
        try {
            Optional<EntityType<?>> opt = ForgeRegistries.ENTITY_TYPES.getHolder(new ResourceLocation(event.entityType)).map(h -> h.value());
            type = opt.orElse(null);
        } catch (Exception e) { return; }
        if (type == null) {
            WaveSurvivorMod.LOGGER.warn("[Chaos] entityType introuvable : {}", event.entityType);
            return;
        }

        for (int i = 0; i < n; i++) {
            BlockPos pos = pickPos(level, center, 5);
            Entity ent = type.create(level);
            if (ent == null) continue;
            ent.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, RNG.nextFloat() * 360f, 0f);
            if (ent instanceof Mob mob) {
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
                com.wavesurvivor.horde.spawn.NetherSpawnFix.apply(mob); // adulte, agressif, pas de zombification
            }
            if (level.addFreshEntity(ent) && ent instanceof Mob mob) {
                aggroToNearestPlayer(mob, server);
            }
        }
        WaveSurvivorMod.LOGGER.info("[Chaos] Spawn : {} × {}", n, event.entityType);
    }

    /** Aggresse tous les Mob dans un rayon autour du center. Utilisé après spawn CustomEntity. */
    private static void aggroNearbyMobsToPlayers(ServerLevel level, BlockPos center, int radius, MinecraftServer server) {
        int r = Math.max(1, radius);
        var box = new net.minecraft.world.phys.AABB(
                center.getX() - r, center.getY() - r, center.getZ() - r,
                center.getX() + r, center.getY() + r, center.getZ() + r);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box)) {
            if (mob.getTarget() == null) aggroToNearestPlayer(mob, server);
        }
    }

    private static void aggroToNearestPlayer(Mob mob, MinecraftServer server) {
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
            if (mob instanceof NeutralMob neutral) {
                neutral.setRemainingPersistentAngerTime(6000);
                neutral.setPersistentAngerTarget(nearest.getUUID());
            }
        }
    }

    private static void applyMerchant(ChaosEvent event, ServerLevel level, BlockPos center, MinecraftServer server) {
        BlockPos pos = pickPos(level, center, 5);
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) return;
        villager.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, RNG.nextFloat() * 360f, 0f);
        villager.setCustomName(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.marchand_du_chaos")).withStyle(ChatFormatting.GOLD));
        villager.setCustomNameVisible(true);
        villager.setPersistenceRequired();

        // FIX : sans profession + level max, le Villager reset ses trades à chaque interaction.
        villager.setVillagerData(new VillagerData(VillagerType.PLAINS, VillagerProfession.LIBRARIAN, 5));
        villager.setVillagerXp(1000);

        if (event.trades != null && !event.trades.isEmpty()) {
            MerchantOffers offers = villager.getOffers();
            offers.clear(); // enlève les trades par défaut de la profession
            for (ChaosEvent.ChaosTrade t : event.trades) {
                MerchantOffer offer = buildOffer(t);
                if (offer != null) offers.add(offer);
            }
            WaveSurvivorMod.LOGGER.info("[Chaos-Merchant] {} trades préparés, {} valides.",
                    event.trades.size(), offers.size());
        }

        if (level.addFreshEntity(villager)) {
            int lifetimeSec = event.lifetime > 0 ? event.lifetime : 120;
            int lifetimeTicks = Math.max(200, lifetimeSec * 20);
            ChaosTracker.schedule(villager.getId(), server.getTickCount() + lifetimeTicks);

            WaveSurvivorMod.LOGGER.info("[Chaos] Marchand spawné en ({},{},{}) — {} trades, lifetime {}s",
                    pos.getX(), pos.getY(), pos.getZ(),
                    event.trades != null ? event.trades.size() : 0, lifetimeSec);
        } else {
            WaveSurvivorMod.LOGGER.warn("[Chaos] addFreshEntity refusé pour Marchand à {}", pos);
        }
    }

    private static void applyLightning(ChaosEvent event, ServerLevel level, MinecraftServer server) {
        float damage = event.damage > 0 ? event.damage : 5f;
        int struck = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isCreative() || p.isSpectator()) continue;
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(p.level());
            if (bolt == null) continue;
            bolt.moveTo(p.getX(), p.getY(), p.getZ());
            bolt.setVisualOnly(true);
            p.level().addFreshEntity(bolt);
            p.hurt(p.damageSources().magic(), damage);
            struck++;
        }
        WaveSurvivorMod.LOGGER.info("[Chaos-Lightning] Foudre sur {} joueur(s), {} dégâts chacun.", struck, damage);
    }

    private static MerchantOffer buildOffer(ChaosEvent.ChaosTrade t) {
        // Résolveur commun : enchantements / données d'objet, potions et clés de Reliquaire compris
        ItemStack in1 = com.wavesurvivor.horde.loot.LootItems.resolve(t.input1, Math.max(1, t.input1Count));
        ItemStack out = com.wavesurvivor.horde.loot.LootItems.resolve(t.output, Math.max(1, t.outputCount));
        if (in1.isEmpty() || out.isEmpty()) {
            WaveSurvivorMod.LOGGER.warn("[Chaos-Merchant] Trade skip : input1='{}' output='{}'", t.input1, t.output);
            return null;
        }
        ItemStack in2 = ItemStack.EMPTY;
        if (t.input2 != null && !t.input2.isBlank()) {
            in2 = com.wavesurvivor.horde.loot.LootItems.resolve(t.input2, Math.max(1, t.input2Count));
        }
        return new MerchantOffer(in1, in2, out, Math.max(1, t.maxUses), 0, 0f);
    }

    @SuppressWarnings("unused")
    private static Item resolveItem(String id) {
        if (id == null || id.isBlank()) return null;
        try { return BuiltInRegistries.ITEM.get(new ResourceLocation(id)); }
        catch (Exception e) { return null; }
    }

    private static BlockPos pickPos(Level level, BlockPos center, int radius) {
        int r = Math.max(1, radius);
        double angle = RNG.nextDouble() * Math.PI * 2;
        double dist = 1 + RNG.nextDouble() * r;
        int x = center.getX() + (int) Math.round(Math.cos(angle) * dist);
        int z = center.getZ() + (int) Math.round(Math.sin(angle) * dist);
        int y = center.getY();
        for (int dy = 0; dy < 4; dy++) {
            BlockPos p = new BlockPos(x, y + dy, z);
            if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()) return p;
        }
        return new BlockPos(x, y, z);
    }

    private static void playSound(String soundId, ServerLevel level, BlockPos pos) {
        if (soundId == null || soundId.isBlank()) return;
        try {
            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation(soundId));
            if (sound != null) {
                level.playSound(null, pos, sound, SoundSource.MASTER, 1.0f, 1.0f);
            }
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.warn("[Chaos] Son invalide : {}", soundId);
        }
    }
}
