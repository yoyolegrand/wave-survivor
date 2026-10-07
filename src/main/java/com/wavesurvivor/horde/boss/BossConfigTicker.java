package com.wavesurvivor.horde.boss;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.ModConfig;
import com.wavesurvivor.config.model.HordeConfigMultiData;
import com.wavesurvivor.horde.model.HordeEntity;
import com.wavesurvivor.horde.spawn.HordeSpawner;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Applique les 3 mécaniques bossConfig chaque tick :
 *   - playerAura : effects sur joueurs dans un rayon, périodique
 *   - selfEffects : effects sur boss, trigger interval OU healthThreshold (+ triggeredOnce)
 *   - summon : spawn adds, trigger idem, respecte summonMaxActive
 * Appelé depuis HordeTickHandler.onServerTick.
 */
public class BossConfigTicker {

    private static final Random RNG = new Random();

    public static void tick(MinecraftServer server) {
        if (BossConfigTracker.all().isEmpty()) return;
        long now = server.getTickCount();

        Iterator<Map.Entry<UUID, BossConfigTracker.BossState>> it = BossConfigTracker.all().entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, BossConfigTracker.BossState> e = it.next();
            UUID uuid = e.getKey();
            BossConfigTracker.BossState state = e.getValue();

            LivingEntity boss = findLiving(server, uuid);
            if (boss == null || !boss.isAlive()) {
                it.remove();
                continue;
            }

            try {
                processPlayerAura(boss, state, now);
                processSelfEffects(boss, state, now);
                processSummon(server, boss, state, now);
            } catch (Exception ex) {
                WaveSurvivorMod.LOGGER.error("[BossConfig] Erreur tick pour '{}' : {}",
                        state.ce.entityName, ex.getMessage(), ex);
            }
        }
    }

    // ─── playerAura : effects sur joueurs dans rayon, périodique ───

    private static void processPlayerAura(LivingEntity boss, BossConfigTracker.BossState s, long now) {
        BossConfigParsed cfg = s.cfg;
        if (!cfg.hasPlayerAura()) return;
        if (s.playerAuraLastTick >= 0 && now - s.playerAuraLastTick < cfg.playerAuraInterval) return;
        s.playerAuraLastTick = now;

        double r = cfg.playerAuraRadius;
        AABB box = boss.getBoundingBox().inflate(r);
        double r2 = r * r;
        for (ServerPlayer p : boss.level().getEntitiesOfClass(ServerPlayer.class, box)) {
            if (p.isCreative() || p.isSpectator()) continue;
            if (p.distanceToSqr(boss) > r2) continue; // sphère, pas boîte
            for (BossConfigParsed.EffectSpec fx : cfg.playerAuraEffects) {
                applyEffect(p, fx);
            }
        }
    }

    // ─── selfEffects : effects sur boss, trigger interval OU healthThreshold ───

    private static void processSelfEffects(LivingEntity boss, BossConfigTracker.BossState s, long now) {
        BossConfigParsed cfg = s.cfg;
        if (!cfg.hasSelfEffects()) return;
        if (!shouldTrigger(boss, now, cfg.selfEffectTrigger, cfg.selfEffectInterval,
                cfg.selfEffectHealthThreshold, cfg.selfEffectTriggeredOnce,
                s.selfEffectsLastTick, s.selfEffectsTriggered)) return;

        // Marquer trigger
        s.selfEffectsLastTick = now;
        if (cfg.selfEffectTriggeredOnce
                && "healthThreshold".equalsIgnoreCase(cfg.selfEffectTrigger)) {
            s.selfEffectsTriggered = true;
        }

        for (BossConfigParsed.EffectSpec fx : cfg.selfEffects) {
            applyEffect(boss, fx);
        }

        // Feedback visuel dans le chat pour les joueurs proches
        // Pas de message dans le chat : il se déclenche en boucle et polluait la discussion.
    }

    // ─── summon : spawn adds, respecte summonMaxActive ───

    private static void processSummon(MinecraftServer server, LivingEntity boss,
                                       BossConfigTracker.BossState s, long now) {
        BossConfigParsed cfg = s.cfg;
        if (!cfg.hasSummon()) return;
        if (!shouldTrigger(boss, now, cfg.summonTrigger, cfg.summonInterval,
                cfg.summonHealthThreshold, cfg.summonTriggeredOnce,
                s.summonLastTick, s.summonTriggered)) return;

        // Prune adds morts avant de compter
        pruneActive(s, server);
        int alive = s.activeSummons.size();
        if (alive >= cfg.summonMaxActive) {
            // Trop d'adds actifs, on ne trigger pas (ne consume pas le cooldown pour retenter plus tôt)
            return;
        }

        // Marquer trigger
        s.summonLastTick = now;
        if (cfg.summonTriggeredOnce
                && "healthThreshold".equalsIgnoreCase(cfg.summonTrigger)) {
            s.summonTriggered = true;
        }

        if (!(boss.level() instanceof ServerLevel level)) return;
        BlockPos center = boss.blockPosition();
        int slotsRemaining = cfg.summonMaxActive - alive;
        int totalSpawned = 0;

        int playerCount = Math.max(1, level.players().size());
        for (BossConfigParsed.SummonEntity se : cfg.summonEntities) {
            if (slotsRemaining <= 0) break;

            // 1) Mob de horde nommé (ex: "Ancien Noyé") → stats + équipement du template
            HordeEntity tpl = findHordeTemplateByName(se.entityType);
            if (tpl != null) {
                int toSpawn = Math.min(se.count, slotsRemaining);
                for (int i = 0; i < toSpawn; i++) {
                    BlockPos pos = pickSpawnPos(level, center, (int) se.radius);
                    Entity ent = HordeSpawner.spawnSummonFromTemplate(level, tpl, pos, playerCount);
                    if (ent != null) {
                        s.activeSummons.add(ent.getUUID());
                        slotsRemaining--;
                        totalSpawned++;
                    }
                }
                continue;
            }

            // 2) Sinon ID de registre (minecraft:drowned, etc.)
            EntityType<?> type = resolveEntityType(se.entityType);
            if (type == null) {
                WaveSurvivorMod.LOGGER.warn("[BossConfig] summon entityType introuvable : {}", se.entityType);
                continue;
            }
            int toSpawn = Math.min(se.count, slotsRemaining);
            for (int i = 0; i < toSpawn; i++) {
                BlockPos pos = pickSpawnPos(level, center, (int) se.radius);
                Entity ent = type.create(level);
                if (ent == null) continue;
                ent.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, RNG.nextFloat() * 360f, 0f);
                if (ent instanceof Mob mob) {
                    mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
                }
                if (level.addFreshEntity(ent)) {
                    s.activeSummons.add(ent.getUUID());
                    slotsRemaining--;
                    totalSpawned++;
                }
            }
        }
        if (totalSpawned > 0) {
            WaveSurvivorMod.LOGGER.info("[BossConfig] '{}' summon → {} adds ({}/{} actifs)",
                    s.ce.entityName, totalSpawned, s.activeSummons.size(), cfg.summonMaxActive);

            // Pas de message dans le chat : les invocations se répètent et polluaient la discussion.
        }
    }

    // ─── Helpers ───

    /**
     * Détermine si la mécanique doit trigger cette frame.
     * - trigger "interval" : cooldown écoulé depuis dernier trigger
     * - trigger "healthThreshold" : HP <= threshold%
     *     + si triggeredOnce=true : une seule fois (flag)
     *     + si triggeredOnce=false : rate-limité par interval quand HP sous threshold
     */
    private static boolean shouldTrigger(LivingEntity boss, long now,
                                         String trigger, int interval, int healthThresholdPct,
                                         boolean triggeredOnce, long lastTick, boolean triggeredFlag) {
        boolean cooldownReady = lastTick < 0 || (now - lastTick >= interval);

        if ("healthThreshold".equalsIgnoreCase(trigger)) {
            float pct = boss.getHealth() / boss.getMaxHealth() * 100f;
            if (pct > healthThresholdPct) return false;
            if (triggeredOnce) return !triggeredFlag;
            return cooldownReady;
        }
        // default = interval
        return cooldownReady;
    }

    /** Cherche un mob de horde par custom_name (toutes hordes). Null si l'ID contient ':' ou introuvable. */
    private static HordeEntity findHordeTemplateByName(String name) {
        if (name == null || name.isBlank() || name.contains(":")) return null;
        ModConfig cfg = WaveSurvivorMod.getConfig();
        if (cfg == null || cfg.hordeConfigMulti == null) return null;
        for (HordeConfigMultiData h : cfg.hordeConfigMulti) {
            if (h.configData == null || h.configData.hordeEntities == null) continue;
            for (HordeEntity he : h.configData.hordeEntities) {
                if (he.customName != null && he.customName.trim().equalsIgnoreCase(name.trim())) return he;
            }
        }
        return null;
    }

    private static void pruneActive(BossConfigTracker.BossState s, MinecraftServer server) {
        s.activeSummons.removeIf(uuid -> {
            Entity e = findEntity(server, uuid);
            return e == null || !e.isAlive();
        });
    }

    private static void applyEffect(LivingEntity target, BossConfigParsed.EffectSpec fx) {
        String id = fx.effect;
        if (id == null || id.isBlank()) return;
        if (!id.contains(":")) id = "minecraft:" + id;
        MobEffect eff;
        try { eff = BuiltInRegistries.MOB_EFFECT.get(new ResourceLocation(id)); }
        catch (Exception e) { return; }
        if (eff == null) return;
        // false/true/true = pas de particules ambient, particules visibles, icône visible
        target.addEffect(new MobEffectInstance(eff, fx.duration, fx.amplifier, false, true, true));
    }

    private static LivingEntity findLiving(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(uuid);
            if (e instanceof LivingEntity l) return l;
        }
        return null;
    }

    private static Entity findEntity(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(uuid);
            if (e != null) return e;
        }
        return null;
    }

    private static EntityType<?> resolveEntityType(String id) {
        if (id == null || id.isBlank()) return null;
        try {
            Optional<EntityType<?>> opt = ForgeRegistries.ENTITY_TYPES.getHolder(new ResourceLocation(id)).map(h -> h.value());
            return opt.orElse(null);
        } catch (Exception e) { return null; }
    }

    private static BlockPos pickSpawnPos(Level level, BlockPos center, int radius) {
        int r = Math.max(1, radius);
        double angle = RNG.nextDouble() * Math.PI * 2;
        double dist = RNG.nextDouble() * r;
        int x = center.getX() + (int) Math.round(Math.cos(angle) * dist);
        int z = center.getZ() + (int) Math.round(Math.sin(angle) * dist);
        int y = center.getY();
        for (int dy = 0; dy < 4; dy++) {
            BlockPos p = new BlockPos(x, y + dy, z);
            if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()) return p;
        }
        return new BlockPos(x, y, z);
    }

    /** Broadcast un message aux joueurs dans un rayon de 50 blocs autour du boss. */
    private static void broadcastToNearby(LivingEntity boss, Component msg) {
        if (!(boss.level() instanceof ServerLevel level)) return;
        double r2 = 50 * 50;
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            if (p.level() != level) continue;
            if (p.distanceToSqr(boss) > r2) continue;
            p.sendSystemMessage(msg);
        }
    }

    /** Formate un effet en lisible : "resistance / amp=0 / dur=100" → "Resistance I (5s)". */
    private static String formatEffect(BossConfigParsed.EffectSpec fx) {
        String name = fx.effect != null ? fx.effect : "?";
        // Enlever le namespace + capitaliser
        int col = name.indexOf(':');
        if (col >= 0) name = name.substring(col + 1);
        name = name.substring(0, 1).toUpperCase() + name.substring(1).replace('_', ' ');
        String amp = romanNumeral(fx.amplifier + 1);
        int seconds = fx.duration / 20;
        return name + " " + amp + " §7(" + seconds + "s)";
    }

    /** Nom lisible d'une entité : "minecraft:drowned" → "Drowned". */
    private static String prettyEntityName(String entityType) {
        if (entityType == null || entityType.isBlank()) return "?";
        int col = entityType.indexOf(':');
        String s = col >= 0 ? entityType.substring(col + 1) : entityType;
        s = s.replace('_', ' ');
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private static String romanNumeral(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }
}
