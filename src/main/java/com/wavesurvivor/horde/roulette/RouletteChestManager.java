package com.wavesurvivor.horde.roulette;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.horde.HordeManager;
import com.wavesurvivor.horde.skill.DelayedActionScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cœur du système Roulette Chest.
 * - Tracks idleChests (coffres enregistrés au placement)
 * - Tracks activeChests (coffres en cours d'animation)
 * - Tick handler spawn les particules idle en continu (colonne + cercle tournant au-dessus)
 * - Tick handler spawn les particules active pendant l'animation (fontaine + cercle rapide)
 * - openChest() gère validation key + animation + reward
 */
public class RouletteChestManager {

    private static final Random RNG = new Random();

    /** Coffres enregistrés (posés dans le monde). Key = pos, Value = config. */
    private static final Map<BlockPos, RouletteChestConfig> IDLE = new ConcurrentHashMap<>();

    /** Coffres en cours d'animation. */
    private static final Map<BlockPos, ActiveState> ACTIVE = new ConcurrentHashMap<>();

    /** Tirages en attente sur un coffre déjà en animation (clé déjà consommée, récompense déjà tirée). */
    private record Queued(ServerPlayer player, RouletteChestConfig config, RouletteReward reward) {}
    private static final Map<BlockPos, java.util.ArrayDeque<Queued>> QUEUE = new ConcurrentHashMap<>();

    /** Pattern d'émission par configKey (uniquement pour les custom chests). Absent → comportement idle par défaut. */
    private static final Map<String, String> PATTERN_BY_KEY = new ConcurrentHashMap<>();

    public static class ActiveState {
        public final RouletteChestConfig config;
        public final ServerLevel level;
        public int remainingTicks;

        public ActiveState(RouletteChestConfig config, ServerLevel level, int ticks) {
            this.config = config;
            this.level = level;
            this.remainingTicks = ticks;
        }
    }

    public static void registerIdle(BlockPos pos, RouletteChestConfig config) {
        IDLE.put(pos.immutable(), config);
        WaveSurvivorMod.LOGGER.info("[Roulette] Coffre '{}' enregistré à {}", config.chestName(), pos);
    }

    public static void unregister(BlockPos pos) {
        RouletteChestConfig removed = IDLE.remove(pos.immutable());
        ACTIVE.remove(pos.immutable());
        QUEUE.remove(pos.immutable());
        if (removed != null) {
            WaveSurvivorMod.LOGGER.info("[Roulette] Coffre '{}' retiré ({})", removed.chestName(), pos);
        }
    }

    public static RouletteChestConfig getConfigAt(BlockPos pos) {
        return IDLE.get(pos.immutable());
    }

    /** Enregistre le pattern d'émission d'un chest custom (appelé par CustomChestStore). */
    public static void setPattern(String configKey, String pattern) {
        if (configKey == null || pattern == null || pattern.isBlank()) return;
        PATTERN_BY_KEY.put(configKey, pattern);
    }

    /** Retire le pattern d'un chest (avant suppression). */
    public static void removePattern(String configKey) {
        PATTERN_BY_KEY.remove(configKey);
    }

    /** Couleur choisie dans le Chest Builder (RGB), par configKey. Absente = déduite du nom. */
    private static final Map<String, Integer> COLOR_BY_KEY = new ConcurrentHashMap<>();

    /** @param color "auto" / null = couleur déduite du nom, sinon nom de colorant Minecraft (red, cyan...). */
    public static void setColor(String configKey, String color) {
        if (configKey == null) return;
        net.minecraft.world.item.DyeColor dye = color == null ? null : net.minecraft.world.item.DyeColor.byName(color, null);
        if (dye == null) COLOR_BY_KEY.remove(configKey);
        else COLOR_BY_KEY.put(configKey, com.wavesurvivor.altar.AltarColors.rgb(dye));
    }

    /** Couleur effective d'un coffre (et de sa clé). */
    public static int colorFor(RouletteChestConfig cfg) {
        Integer c = cfg == null ? null : COLOR_BY_KEY.get(cfg.configKey());
        return c != null ? c : RouletteKeyItem.colorForName(cfg == null ? "" : cfg.chestName());
    }

    /** Matériau du corps de la clé choisi dans le Chest Builder (absent = or). */
    private static final Map<String, String> MATERIAL_BY_KEY = new ConcurrentHashMap<>();

    public static void setKeyMaterial(String configKey, String material) {
        if (configKey == null) return;
        String id = RouletteKeyItem.material(material).id();
        if (id.equals(RouletteKeyItem.MATERIALS[0].id())) MATERIAL_BY_KEY.remove(configKey);
        else MATERIAL_BY_KEY.put(configKey, id);
    }

    // ================================================================
    // TICK — particules idle + particules active
    // ================================================================

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null) return;
        long now = server.getTickCount();

        // Particules idle : cercle tournant + colonne subtile
        for (Map.Entry<BlockPos, RouletteChestConfig> e : IDLE.entrySet()) {
            BlockPos p = e.getKey();
            RouletteChestConfig cfg = e.getValue();
            ServerLevel level = findLevelFor(server, p);
            if (level == null) continue;
            // Anciens coffres vanilla → Coffre Roulette (1×/s) + couleur à jour
            if (now % 20 == 0) {
                convertIfVanilla(level, p, cfg);
                if (level.getBlockEntity(p) instanceof RouletteChestBlockEntity be) be.setColor(colorFor(cfg));
            }
            spawnIdleParticles(level, p, cfg, now);
        }

        // Particules active : fontaine intense + cercle rapide
        var it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            ActiveState st = entry.getValue();
            spawnActiveParticles(st.level, entry.getKey(), st.config, now);
            st.remainingTicks--;
            if (st.remainingTicks <= 0) {
                it.remove();
                // Tirage suivant en file d'attente : il enchaîne tout de suite (animation accélérée)
                if (!startNext(entry.getKey(), st.level)) setLid(st.level, entry.getKey(), false); // le couvercle redescend
            }
        }
    }

    /** Cherche le level dans lequel se trouve la pos (via chargement de chunk). */
    private static ServerLevel findLevelFor(MinecraftServer server, BlockPos pos) {
        for (ServerLevel lvl : server.getAllLevels()) {
            if (lvl.isLoaded(pos)) return lvl;
        }
        return null;
    }

    /** Particules idle : cercle tournant lentement au-dessus + colonne montante subtile. */
    private static void spawnIdleParticles(ServerLevel level, BlockPos p, RouletteChestConfig cfg, long now) {
        ParticleOptions particle = resolveParticle(cfg.particleIdle(), ParticleTypes.HAPPY_VILLAGER);
        double cx = p.getX() + 0.5;
        double cz = p.getZ() + 0.5;
        double baseY = p.getY() + 1.2;

        // Si un pattern custom est enregistré pour ce chest, on l'utilise (override du comportement classique)
        String pattern = PATTERN_BY_KEY.get(cfg.configKey());
        if (pattern != null && !pattern.isBlank() && !"circle".equalsIgnoreCase(pattern)) {
            ParticlePatterns.emit(pattern, level, cx, baseY, cz, particle, now);
            return;
        }

        // Comportement classique : cercle tournant + colonne
        // Particules volumineuses (villageois en colère, nuages...) : émises beaucoup moins souvent
        boolean heavy = isHeavy(particle);
        if (heavy && now % 6 != 0) return;
        double angleStep = (Math.PI * 2) / 3;
        double angleOffset = (now % 80) * (Math.PI * 2 / 80);
        for (int i = 0; i < (heavy ? 2 : 3); i++) {
            double angle = angleStep * i + angleOffset;
            double dx = Math.cos(angle) * 0.6;
            double dz = Math.sin(angle) * 0.6;
            level.sendParticles(particle, cx + dx, baseY, cz + dz, 1, 0, 0, 0, 0.01);
        }

        // Colonne verticale : 1 particule tous les 4 ticks qui monte
        if (now % (heavy ? 12 : 4) == 0) {
            level.sendParticles(particle, cx, baseY + 0.3, cz, 1, 0.1, 0.5, 0.1, 0.02);
        }
    }

    /** Particules grosses / opaques qui saturent vite l'écran. */
    private static boolean isHeavy(ParticleOptions p) {
        var t = p.getType();
        return t == ParticleTypes.ANGRY_VILLAGER || t == ParticleTypes.CLOUD || t == ParticleTypes.LARGE_SMOKE
                || t == ParticleTypes.CAMPFIRE_COSY_SMOKE || t == ParticleTypes.CAMPFIRE_SIGNAL_SMOKE
                || t == ParticleTypes.EXPLOSION || t == ParticleTypes.POOF || t == ParticleTypes.HEART
                || t == ParticleTypes.SQUID_INK || t == ParticleTypes.SNEEZE;
    }

    /** Particules active : cercle rapide + fontaine pendant l'animation (allégé : 1 tick sur 2). */
    private static void spawnActiveParticles(ServerLevel level, BlockPos p, RouletteChestConfig cfg, long now) {
        ParticleOptions particle = resolveParticle(cfg.particleActive(), ParticleTypes.ENCHANTED_HIT);
        boolean heavy = isHeavy(particle);
        if (now % (heavy ? 5 : 2) != 0) return;
        double cx = p.getX() + 0.5;
        double cz = p.getZ() + 0.5;
        double baseY = p.getY() + 1.3;

        // Cercle tournant rapide : 4 particules autour (2 si volumineuses)
        int ring = heavy ? 2 : 4;
        double angleStep = (Math.PI * 2) / ring;
        double angleOffset = (now % 20) * (Math.PI * 2 / 20);
        for (int i = 0; i < ring; i++) {
            double angle = angleStep * i + angleOffset;
            double dx = Math.cos(angle) * 0.8;
            double dz = Math.sin(angle) * 0.8;
            level.sendParticles(particle, cx + dx, baseY, cz + dz, 1, 0, 0, 0, 0.02);
        }

        // Fontaine : particules qui jaillissent vers le haut
        level.sendParticles(particle, cx, baseY, cz, heavy ? 1 : 2, 0.2, 0.4, 0.2, 0.15);

        // Étoile centrale plus haute
        if (now % 4 == 0) level.sendParticles(ParticleTypes.FIREWORK, cx, baseY + 0.8, cz, 1, 0.3, 0.2, 0.3, 0.03);
    }

    // ================================================================
    // OPEN CHEST — validation clé, animation, reward
    // ================================================================

    /**
     * Tenté d'ouvrir un roulette chest. Called from PlayerInteractEvent handler.
     * @return true si l'event doit être cancel (le clic était valide, on prend le relais)
     */
    public static boolean tryOpenChest(ServerPlayer player, ServerLevel level, BlockPos pos,
                                        RouletteChestConfig config, ItemStack itemInHand) {
        // Animation en cours : le tirage est validé maintenant et mis en file (spam de clés possible)
        boolean busy = ACTIVE.containsKey(pos.immutable());

        // Vérif clé : nouvelle clé (wavesurvivor:roulette_key) ou ancien name_tag, avec NBT rouletteKey=1
        String heldId = itemInHand.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(itemInHand.getItem()).toString();
        if (!"minecraft:name_tag".equals(heldId) && !"wavesurvivor:roulette_key".equals(heldId)) {
            player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t(config.messages().noKey()).replace("{key}", RouletteKeyItem.keyDisplayName(config.keyName()))));
            return true;
        }
        CompoundTag tag = itemInHand.getTag();
        if (tag == null || tag.getInt("rouletteKey") != 1) {
            player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t(config.messages().noKey()).replace("{key}", RouletteKeyItem.keyDisplayName(config.keyName()))));
            return true;
        }

        // Vérif que la clé correspond : même nom de clé (ex : toute "Clé Mystérieuse" ouvre toute la série),
        // même coffre, ou coffre de la clé listé dans allowedChests (comparaison tolérante aux anciens noms).
        String keyChestName = tag.getString("rouletteChestName");
        String keyName = tag.contains("rouletteKeyName") ? tag.getString("rouletteKeyName")
                : net.minecraft.ChatFormatting.stripFormatting(itemInHand.getHoverName().getString());
        boolean isValid = sameName(keyName, config.keyName()) || sameName(keyChestName, config.chestName());
        if (!isValid && config.allowedChests() != null) {
            for (String allowed : config.allowedChests()) {
                if (allowedMatches(allowed, keyChestName)) { isValid = true; break; }
            }
        }
        if (!isValid) {
            player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t(config.messages().noKey()).replace("{key}", RouletteKeyItem.keyDisplayName(config.keyName()))));
            return true;
        }

        // Consomme la clé si config.consumeKey
        if (config.consumeKey()) {
            itemInHand.shrink(1);
            if (!busy) player.displayClientMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t(config.messages().consumed())), true);
        }

        // Ajuste les rewards par vague en cours
        int currentWave = HordeManager.get().getCurrentWave();
        List<RouletteReward> adjusted = getAdjustedRewards(config, currentWave);

        // Sélectionne un reward random pondéré
        RouletteReward reward = selectRandomReward(adjusted);
        if (reward == null) {
            player.displayClientMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.erreur_aucune_recompense_disponible")), true);
            return true;
        }

        if (busy) {
            java.util.ArrayDeque<Queued> q = QUEUE.computeIfAbsent(pos.immutable(), k -> new java.util.ArrayDeque<>());
            q.add(new Queued(player, config, reward));
            player.displayClientMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("roulette.queued", q.size())), true);
            return true;
        }
        startAnimation(player, level, pos, config, reward, false);
        return true;
    }

    /** Weighted random selection. */
    private static RouletteReward selectRandomReward(List<RouletteReward> rewards) {
        if (rewards.isEmpty()) return null;
        double total = 0;
        for (RouletteReward r : rewards) total += r.chance();
        double roll = RNG.nextDouble() * total;
        double cum = 0;
        for (RouletteReward r : rewards) {
            cum += r.chance();
            if (roll <= cum) return r;
        }
        return rewards.get(rewards.size() - 1);
    }

    /**
     * Ajuste les chances des rewards par vague :
     *   newChance = base + (modifier[rarity] × (currentWave - startWave + 1))
     * Puis normalise à base 100.
     */
    private static List<RouletteReward> getAdjustedRewards(RouletteChestConfig cfg, int currentWave) {
        RouletteDynamicChances dyn = cfg.dynamicChances();
        if (dyn == null || currentWave <= 0 || currentWave < dyn.startWave()) return cfg.rewards();

        int waveBonus = currentWave - dyn.startWave() + 1;
        List<RouletteReward> out = new ArrayList<>();
        double totalWeight = 0;
        for (RouletteReward r : cfg.rewards()) {
            int mod = dyn.modifierFor(r.rarity());
            double newChance = r.chance() + mod * waveBonus;
            if (newChance < 1) newChance = 1;
            out.add(new RouletteReward(r.item(), r.minQty(), r.maxQty(), newChance, r.rarity(), r.displayName()));
            totalWeight += newChance;
        }
        // Normalise à base 100
        List<RouletteReward> normalized = new ArrayList<>();
        for (RouletteReward r : out) {
            double c = (r.chance() / totalWeight) * 100;
            normalized.add(new RouletteReward(r.item(), r.minQty(), r.maxQty(), c, r.rarity(), r.displayName()));
        }
        return normalized;
    }

    // ================================================================
    // ANIMATION
    // ================================================================

    /** Lance le prochain tirage en file sur ce coffre. @return vrai si un tirage a démarré. */
    private static boolean startNext(BlockPos pos, ServerLevel level) {
        java.util.ArrayDeque<Queued> q = QUEUE.get(pos);
        while (q != null && !q.isEmpty()) {
            Queued n = q.poll();
            if (n.player() == null || n.player().isRemoved() || n.player().hasDisconnected()) continue;
            if (q.isEmpty()) QUEUE.remove(pos);
            startAnimation(n.player(), level, pos, n.config(), n.reward(), true);
            if (q != null && !q.isEmpty()) n.player().displayClientMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("roulette.queued", q.size())), true);
            return true;
        }
        QUEUE.remove(pos);
        return false;
    }

    private static void startAnimation(ServerPlayer player, ServerLevel level, BlockPos pos,
                                        RouletteChestConfig config, RouletteReward reward, boolean fast) {
        MinecraftServer server = level.getServer();

        // Son activation
        playSound(level, pos, config.sounds().activation(), 1.0f, 1.0f);

        if (!config.showAnimation()) {
            // Pas d'animation : donne direct
            giveReward(server, player, reward, config);
            jackpot(server, player, config);
            return;
        }

        // File d'attente : animation accélérée (×3, 1 s minimum)
        int seconds = fast ? Math.max(1, Math.round(config.animationDurationSeconds() / 3f)) : config.animationDurationSeconds();
        // Enregistre comme active pour le tick de particules
        int animTicks = seconds * 20;
        ACTIVE.put(pos.immutable(), new ActiveState(config, level, animTicks));
        setLid(level, pos, true); // le couvercle lévite pendant la roulette

        // Message (barre d'action, plus dans le chat) + son opening
        player.displayClientMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t(config.messages().opening())), true);
        playSound(level, pos, config.sounds().opening(), 1.0f, 1.0f);

        // Animation rolling : toutes les 5 ticks, aperçu dans la barre d'action + son avec pitch qui monte
        int steps = seconds * 4; // 4 steps par seconde
        for (int i = 0; i < steps; i++) {
            final int step = i;
            DelayedActionScheduler.schedule(server, 10 + i * 5, () -> {
                if (!player.isAlive()) return;
                // Random preview reward (visual only)
                RouletteReward preview = config.rewards().get(RNG.nextInt(config.rewards().size()));
                player.displayClientMessage(Component.literal(
                        com.wavesurvivor.i18n.WSLang.t(config.messages().rolling()) + " §7[§f" + preview.displayName() + "§7]"), true);
                // Pitch qui monte
                float pitch = 1.0f + (step / (float) steps) * 0.5f;
                playSound(level, pos, config.sounds().rolling(), 0.5f, pitch);
            }, "roulette anim step " + i);
        }

        // Fin de l'animation : donne le reward
        DelayedActionScheduler.schedule(server, 10 + steps * 5, () -> {
            if (!player.isAlive()) return;
            giveReward(server, player, reward, config);
            jackpot(server, player, config);
        }, "roulette give reward");
    }

    /** Set Fortune (4 pièces) — Jackpot : 15 % de chance d'un 2e tirage gratuit. */
    private static void jackpot(MinecraftServer server, ServerPlayer player, RouletteChestConfig config) {
        // Héritage — Marchand 5 : 10 % de chance de récupérer sa clé
        if (com.wavesurvivor.horde.renaissance.Heritage.has(player, com.wavesurvivor.horde.renaissance.Heritage.Branch.MERCHANT, 5)
                && player.getRandom().nextFloat() < 0.20f) {
            ItemStack key = createKeyItem(config);
            if (!key.isEmpty()) {
                if (!player.getInventory().add(key)) player.drop(key, false);
                player.sendSystemMessage(com.wavesurvivor.i18n.WSLang.c("heritage.key_back"));
            }
        }
        if (!com.wavesurvivor.item.RelicSets.jackpot(player) || config.rewards() == null || config.rewards().isEmpty()) return;
        RouletteReward bonus = selectRandomReward(config.rewards());
        if (bonus == null) return;
        player.sendSystemMessage(com.wavesurvivor.i18n.WSLang.c("set.fortune.jackpot"));
        giveReward(server, player, bonus, config);
    }

    private static void giveReward(MinecraftServer server, ServerPlayer player,
                                    RouletteReward reward, RouletteChestConfig config) {
        int qty = reward.minQty() + (reward.maxQty() > reward.minQty()
                ? RNG.nextInt(reward.maxQty() - reward.minQty() + 1) : 0);

        try {
            // Résolveur commun : potions précises ("...#minecraft:strength"), clés de coffre, items normaux
            ItemStack resolved = com.wavesurvivor.horde.loot.LootItems.resolve(reward.item(), 1);
            Item item = resolved.isEmpty() ? null : resolved.getItem();
            if (item != null) {
                int left = Math.max(1, qty);
                while (left > 0) {
                    ItemStack stack = resolved.copy();
                    int n = Math.min(left, stack.getMaxStackSize());
                    stack.setCount(n);
                    left -= n;
                    if (!player.getInventory().add(stack)) player.drop(stack, false);
                }
                String rewardText = qty + "x " + reward.displayName();
                Component msg = Component.literal(
                        com.wavesurvivor.i18n.WSLang.t(config.messages().reward()).replace("{reward}", rewardText));
                // Barre d'action ; seules les récompenses épiques et légendaires restent aussi dans le chat
                player.displayClientMessage(msg, true);
                String rar = String.valueOf(reward.rarity()).toLowerCase();
                if (rar.contains("epic") || rar.contains("legend") || rar.contains("épique") || rar.contains("légend")) {
                    player.sendSystemMessage(msg);
                }

                // Son selon rareté
                String soundId = config.sounds().forRarity(reward.rarity());
                if (soundId != null) {
                    playSound((ServerLevel) player.level(), player.blockPosition(), soundId, 1.0f, 1.0f);
                }

                WaveSurvivorMod.LOGGER.info("[Roulette] '{}' -> {} × {} ({})",
                        player.getName().getString(), qty, reward.item(), reward.rarity());
            } else {
                player.sendSystemMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("srv.item_introuvable") + reward.item()));
                WaveSurvivorMod.LOGGER.warn("[Roulette] Item inconnu : {}", reward.item());
            }
        } catch (Exception e) {
            WaveSurvivorMod.LOGGER.error("[Roulette] Erreur giveReward: {}", e.getMessage());
        }
    }

    // ================================================================
    // Helpers
    // ================================================================

    private static void playSound(ServerLevel level, BlockPos pos, String soundId, float vol, float pitch) {
        if (soundId == null || soundId.isBlank()) return;
        try {
            SoundEvent snd = BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation(soundId));
            if (snd != null) {
                level.playSound(null, pos, snd, SoundSource.BLOCKS, vol, pitch);
            }
        } catch (Exception ignore) {}
    }

    private static ParticleOptions resolveParticle(String shortName, ParticleOptions fallback) {
        if (shortName == null || shortName.isBlank()) return fallback;
        // JS utilisait des noms courts sans "minecraft:" — on prefix si absent
        String id = shortName.contains(":") ? shortName : "minecraft:" + shortName;
        try {
            Object p = BuiltInRegistries.PARTICLE_TYPE.get(new ResourceLocation(id));
            if (p instanceof ParticleOptions po) return po;
        } catch (Exception ignore) {}
        return fallback;
    }

    /** Crée une clé (item wavesurvivor:roulette_key + NBT) pour un config. */
    public static ItemStack createKeyItem(RouletteChestConfig config) {
        ItemStack key = new ItemStack(com.wavesurvivor.registry.ModItems.ROULETTE_KEY.get());
        // Pas de nom figé : RouletteKeyItem.getName() l'affiche traduit à partir de rouletteKeyName
        CompoundTag tag = key.getOrCreateTag();
        tag.putInt("rouletteKey", 1);
        tag.putString("rouletteChestName", config.chestName());
        tag.putString("rouletteKeyName", config.keyName());
        // Couleur choisie dans le Chest Builder (sinon déduite du nom → les clés restent empilables)
        Integer chosen = COLOR_BY_KEY.get(config.configKey());
        if (chosen != null) tag.putInt("rouletteKeyColor", chosen);
        // Matériau (sauf or, par défaut : les clés restent empilables avec les anciennes)
        String mat = MATERIAL_BY_KEY.get(config.configKey());
        if (mat != null) tag.putString("rouletteKeyMaterial", mat);
        return key;
    }

    /** Noms égaux sans tenir compte de la casse, des accents ni de la ponctuation. */
    private static boolean sameName(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) return false;
        return com.wavesurvivor.horde.loot.LootItems.tokens(a).equals(com.wavesurvivor.horde.loot.LootItems.tokens(b));
    }

    /** "Coffre Accessoire" (ancien nom) accepte la clé de "Coffre Mystérieux Accessoire" : mots inclus. */
    private static boolean allowedMatches(String allowed, String keyChestName) {
        if (allowed == null || keyChestName == null || allowed.isBlank() || keyChestName.isBlank()) return false;
        return com.wavesurvivor.horde.loot.LootItems.tokens(keyChestName)
                .containsAll(com.wavesurvivor.horde.loot.LootItems.tokens(allowed));
    }

    /** Crée un coffre nommé prêt à être posé. */
    public static ItemStack createChestItem(RouletteChestConfig config) {
        try {
            Item chestItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(resolveChestBlockId(config)));
            if (chestItem == null) return ItemStack.EMPTY;
            ItemStack stack = new ItemStack(chestItem);
            stack.setHoverName(Component.literal(config.chestName()));
            stack.getOrCreateTag().putInt("rouletteColor", colorFor(config));
            return stack;
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    public static int idleCount() { return IDLE.size(); }

    // ─── Aperçu du loot (clic gauche en survie) ───

    private static final Map<java.util.UUID, Long> LAST_PREVIEW = new ConcurrentHashMap<>();

    public static void showLoot(ServerPlayer player, RouletteChestConfig cfg) {
        long now = player.level().getGameTime();
        Long last = LAST_PREVIEW.get(player.getUUID());
        if (last != null && now - last < 10) return; // le clic gauche maintenu se répète
        LAST_PREVIEW.put(player.getUUID(), now);

        int wave = HordeManager.get().getCurrentWave();
        List<RouletteReward> rewards = getAdjustedRewards(cfg, wave);
        boolean bonus = cfg.dynamicChances() != null && wave > 0 && wave >= cfg.dynamicChances().startWave();
        double total = 0;
        for (RouletteReward r : rewards) total += Math.max(0, r.chance());
        if (total <= 0) total = 1;

        List<RouletteReward> sorted = new ArrayList<>(rewards);
        sorted.sort((a, b) -> {
            int c = Integer.compare(b.rarity().ordinal(), a.rarity().ordinal()); // légendaire en premier
            return c != 0 ? c : Double.compare(b.chance(), a.chance());
        });
        List<com.wavesurvivor.network.RouletteLootPacket.Entry> entries = new ArrayList<>();
        for (RouletteReward r : sorted) {
            ItemStack icon = com.wavesurvivor.horde.loot.LootItems.resolve(r.item(), 1);
            if (icon.isEmpty()) icon = new ItemStack(net.minecraft.world.item.Items.BARRIER);
            String name = r.displayName() != null && !r.displayName().isBlank() ? r.displayName() : icon.getHoverName().getString();
            entries.add(new com.wavesurvivor.network.RouletteLootPacket.Entry(icon, name, r.minQty(), Math.max(r.minQty(), r.maxQty()),
                    100.0 * Math.max(0, r.chance()) / total, r.rarity().ordinal()));
        }
        com.wavesurvivor.network.NetworkHandler.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new com.wavesurvivor.network.RouletteLootPacket(cfg.chestName(), cfg.keyName(), bonus ? wave : 0, entries));
    }

    // ─── Coffre Roulette (bloc dédié) ───

    public static final String CUSTOM_CHEST_ID = "wavesurvivor:roulette_chest";

    /** Le coffre vanilla (défaut base44) est remplacé par le Coffre Roulette ; les autres blocs sont gardés. */
    public static String resolveChestBlockId(RouletteChestConfig cfg) {
        String t = cfg == null ? null : cfg.chestType();
        if (t == null || t.isBlank() || t.equals("minecraft:chest") || t.equals("minecraft:trapped_chest")) {
            return CUSTOM_CHEST_ID;
        }
        return t;
    }

    /** Remplace un coffre vanilla enregistré par un Coffre Roulette (même orientation, même nom). */
    public static void convertIfVanilla(ServerLevel level, BlockPos pos, RouletteChestConfig cfg) {
        if (cfg == null || !level.isLoaded(pos) || !CUSTOM_CHEST_ID.equals(resolveChestBlockId(cfg))) return;
        net.minecraft.world.level.block.state.BlockState st = level.getBlockState(pos);
        if (!(st.getBlock() instanceof net.minecraft.world.level.block.ChestBlock)) return;
        net.minecraft.core.Direction facing = st.hasProperty(net.minecraft.world.level.block.ChestBlock.FACING)
                ? st.getValue(net.minecraft.world.level.block.ChestBlock.FACING) : net.minecraft.core.Direction.NORTH;
        // Le coffre vanilla lâche son contenu éventuel en étant remplacé
        level.setBlock(pos, com.wavesurvivor.registry.ModBlocks.ROULETTE_CHEST.get().defaultBlockState()
                .setValue(RouletteChestBlock.FACING, facing), 3);
        if (level.getBlockEntity(pos) instanceof RouletteChestBlockEntity be) {
            be.setCustomName(Component.literal(cfg.chestName()));
        }
        level.sendParticles(ParticleTypes.SCULK_SOUL, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5,
                12, 0.3, 0.3, 0.3, 0.02);
    }

    /** Couvercle en lévitation (OPEN) pendant la roulette. */
    public static void setLid(ServerLevel level, BlockPos pos, boolean open) {
        if (level == null || !level.isLoaded(pos)) return;
        net.minecraft.world.level.block.state.BlockState st = level.getBlockState(pos);
        if (st.getBlock() instanceof RouletteChestBlock && st.getValue(RouletteChestBlock.OPEN) != open) {
            level.setBlock(pos, st.setValue(RouletteChestBlock.OPEN, open), 3);
        }
    }
    public static int activeCount() { return ACTIVE.size(); }
}
