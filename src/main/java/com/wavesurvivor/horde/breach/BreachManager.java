package com.wavesurvivor.horde.breach;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.altar.AltarRecipes;
import com.wavesurvivor.horde.HordeManager;
import com.wavesurvivor.horde.loot.LootItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * BRÈCHES (config par horde : altar_recipes.json → breaches).
 *
 *   - Toutes les everyNWaves vagues, `count` Brèches s'ouvrent autour du point de horde.
 *   - Chaque Brèche TIRE SON TYPE (breaches.types, pondérés) : nom, style, couleur, PV, rythme des renforts,
 *     liste de MONSTRES pondérés (vanilla, moddés ou entités custom) et liste de RÉCOMPENSES (quantité + chance).
 *   - Les renforts ne comptent pas dans la vague et disparaissent en fin de vague.
 *   - Encore ouverte en fin de vague → se referme, la horde gagne +buffPerOpen de dégâts (cumulable).
 *   - Ancien format (une seule sorte de Brèche) converti automatiquement (Breaches.effectiveTypes()).
 *   - API publique (open / openCount) pour les boss.
 */
public class BreachManager {

    private static final String TAG = "ws_breach";
    private static final Random RNG = new Random();

    private static class Breach {
        final UUID id;
        final BlockPos pos;
        final AltarRecipes.BreachType type;
        long nextSpawn;
        final List<UUID> spawned = new ArrayList<>();

        Breach(UUID id, BlockPos pos, AltarRecipes.BreachType type, long nextSpawn) {
            this.id = id;
            this.pos = pos;
            this.type = type;
            this.nextSpawn = nextSpawn;
        }
    }

    private static final List<Breach> OPEN = new ArrayList<>();
    /** Tous les renforts sortis des Brèches (retirés en fin de vague). */
    private static final List<UUID> REINFORCEMENTS = new ArrayList<>();
    private static ServerLevel LEVEL = null;
    private static AltarRecipes.Breaches CFG = null;
    private static double damageBonus = 0;

    public static int openCount() { return OPEN.size(); }
    public static double damageBonus() { return damageBonus; }

    // ─── Cycle de vie (HordeManager) ───

    /** Début de vague : ouvre les Brèches si c'est une vague concernée. */
    public static void onWaveStart(ServerLevel level, String hordeName, int wave, BlockPos center, int zoneRadius) {
        AltarRecipes.Breaches cfg = AltarRecipes.getBreaches(hordeName);
        if (cfg == null || level == null || center == null) return;
        CFG = cfg;
        if (cfg.everyNWaves <= 0 || wave % cfg.everyNWaves != 0) return;
        double dist = cfg.distance > 0 ? cfg.distance : (zoneRadius > 0 ? zoneRadius * 0.8 : 9);
        open(level, center, cfg.count, dist, cfg);
    }

    /** Ouvre `count` Brèches autour de center (utilisable par un boss). Chacune tire son type. */
    public static void open(ServerLevel level, BlockPos center, int count, double dist, AltarRecipes.Breaches cfg) {
        if (cfg == null) cfg = CFG != null ? CFG : new AltarRecipes.Breaches();
        CFG = cfg;
        LEVEL = level;
        long now = level.getServer().getTickCount();
        double offset = RNG.nextDouble() * Math.PI * 2;
        int opened = 0;
        for (int i = 0; i < count; i++) {
            AltarRecipes.BreachType type = cfg.pickType(RNG);
            double a = offset + Math.PI * 2 * i / Math.max(1, count);
            int x = center.getX() + (int) Math.round(Math.cos(a) * dist);
            int z = center.getZ() + (int) Math.round(Math.sin(a) * dist);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            com.wavesurvivor.entity.BrecheEntity b = com.wavesurvivor.registry.ModEntities.BRECHE.get().create(level);
            if (b == null) continue;
            b.moveTo(x + 0.5, y, z + 0.5, RNG.nextFloat() * 360f, 0);
            b.setStyle(type.style, type.color);
            AttributeInstance hp = b.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null) { hp.setBaseValue(Math.max(1, type.health)); b.setHealth((float) Math.max(1, type.health)); }
            b.getPersistentData().putBoolean(TAG, true);
            b.getPersistentData().putBoolean("ws_revenant", true); // jamais relevée par la Résurrection
            b.setCustomNameVisible(true);
            if (level.addFreshEntity(b)) {
                OPEN.add(new Breach(b.getUUID(), pos, type, now + 60)); // premiers renforts après 3 s
                rename(b, type);
                level.sendParticles(ParticleTypes.LAVA, x + 0.5, y + 1, z + 0.5, 25, 0.5, 0.5, 0.5, 0.1);
                level.playSound(null, pos, SoundEvents.PORTAL_TRIGGER, SoundSource.HOSTILE, 1.2f, 0.7f);
                opened++;
            }
        }
        if (opened > 0) {
            broadcast(level.getServer(), com.wavesurvivor.i18n.WSLang.t("breach.opened", opened));
        }
    }

    /** Fin de vague : les Brèches encore ouvertes renforcent la horde, les renforts disparaissent. */
    public static void onWaveEnd(MinecraftServer server) {
        int left = OPEN.size();
        if (left > 0 && CFG != null) {
            damageBonus += left * CFG.buffPerOpen;
            broadcast(server, com.wavesurvivor.i18n.WSLang.t("breach.left_open", left,
                    Math.round(left * CFG.buffPerOpen * 100), Math.round(damageBonus * 100)));
        }
        closeAll(server, true);
    }

    /** Fin / arrêt de horde : tout disparaît et le bonus repart à zéro. */
    public static void clearAll(MinecraftServer server) {
        closeAll(server, true);
        damageBonus = 0;
        CFG = null;
    }

    private static void closeAll(MinecraftServer server, boolean poof) {
        if (server == null) { OPEN.clear(); REINFORCEMENTS.clear(); return; }
        for (Breach b : OPEN) discard(server, b.id, poof);
        OPEN.clear();
        for (UUID id : REINFORCEMENTS) discard(server, id, poof);
        REINFORCEMENTS.clear();
    }

    private static void discard(MinecraftServer server, UUID id, boolean poof) {
        for (ServerLevel lvl : server.getAllLevels()) {
            Entity e = lvl.getEntity(id);
            if (e != null) {
                if (poof) lvl.sendParticles(ParticleTypes.LARGE_SMOKE, e.getX(), e.getY() + 0.8, e.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
                e.discard();
                return;
            }
        }
    }

    // ─── Tick : visuel + renforts ───

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || OPEN.isEmpty() || LEVEL == null) return;
        long now = event.getServer().getTickCount();
        ServerLevel level = LEVEL;
        Iterator<Breach> it = OPEN.iterator();
        while (it.hasNext()) {
            Breach b = it.next();
            Entity e = level.getEntity(b.id);
            if (!(e instanceof LivingEntity le) || !le.isAlive()) { it.remove(); continue; }
            double x = b.pos.getX() + 0.5, y = b.pos.getY(), z = b.pos.getZ() + 0.5;
            String style = b.type.style == null ? "infernale" : b.type.style;
            // Pilier de particules (selon le style)
            if (now % 3 == 0) {
                var flame = switch (style) {
                    case "ames" -> ParticleTypes.SOUL_FIRE_FLAME;
                    case "end" -> ParticleTypes.REVERSE_PORTAL;
                    case "abysses" -> ParticleTypes.BUBBLE_POP;
                    default -> ParticleTypes.FLAME;
                };
                for (int k = 0; k < 3; k++) {
                    level.sendParticles(flame, x + (RNG.nextDouble() - 0.5) * 0.8, y + RNG.nextDouble() * 3.0,
                            z + (RNG.nextDouble() - 0.5) * 0.8, 1, 0, 0.05, 0, 0.01);
                }
                level.sendParticles(ParticleTypes.PORTAL, x, y + 1.5, z, 3, 0.4, 1.2, 0.4, 0.3);
            }
            if (now % 10 == 0) {
                if ("infernale".equals(style)) level.sendParticles(ParticleTypes.LAVA, x, y + 0.3, z, 1, 0.3, 0.1, 0.3, 0);
                rename(le, b.type);
            }
            if (now % 60 == 0) level.playSound(null, b.pos, SoundEvents.PORTAL_AMBIENT, SoundSource.HOSTILE, 0.5f, 0.6f);

            // Renforts
            if (now >= b.nextSpawn) {
                b.nextSpawn = now + Math.max(40, b.type.spawnIntervalSeconds * 20L);
                b.spawned.removeIf(id -> { Entity s = level.getEntity(id); return s == null || !s.isAlive(); });
                int room = Math.max(0, b.type.maxAlive - b.spawned.size());
                int n = Math.min(room, Math.max(0, b.type.spawnPerPulse) + RNG.nextInt(2));
                int done = 0;
                for (int i = 0; i < n; i++) if (spawnReinforcement(level, b)) done++;
                if (done > 0) level.playSound(null, b.pos, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 0.8f, 0.5f);
            }
        }
    }

    /** Fait sortir un renfort (tiré selon les poids) : vanilla, moddé ou entité custom. */
    private static boolean spawnReinforcement(ServerLevel level, Breach b) {
        AltarRecipes.BreachMob pick = b.type.pickMob(RNG);
        if (pick == null || pick.entity == null || pick.entity.isBlank()) return false;
        String id = pick.entity.trim();
        double a = RNG.nextDouble() * Math.PI * 2;
        int x = b.pos.getX() + (int) Math.round(Math.cos(a) * 2);
        int z = b.pos.getZ() + (int) Math.round(Math.sin(a) * 2);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos at = new BlockPos(x, y, z);

        Mob mob = null;
        var custom = com.wavesurvivor.horde.spawn.CustomEntityRegistry.get(id);
        if (custom != null) {
            // Entité custom : on passe par son spawner, puis on la retire du compteur de vague
            Set<UUID> before = new HashSet<>();
            for (var en : com.wavesurvivor.horde.spawn.MobRegistry.entries()) before.add(en.getKey());
            com.wavesurvivor.horde.spawn.CustomEntitySpawner.spawnMobs(level, custom, b.pos, 3, 1, null); // zone sûre autour de la Brèche
            for (var en : new ArrayList<>(com.wavesurvivor.horde.spawn.MobRegistry.entries())) {
                if (before.contains(en.getKey())) continue;
                com.wavesurvivor.horde.spawn.MobRegistry.remove(en.getKey());
                Entity ent = level.getEntity(en.getKey());
                if (ent instanceof Mob m) mob = m;
                if (ent != null) { b.spawned.add(ent.getUUID()); REINFORCEMENTS.add(ent.getUUID()); }
            }
            if (mob == null) return false;
        } else {
            EntityType<?> type;
            try { type = BuiltInRegistries.ENTITY_TYPE.getOptional(new ResourceLocation(id)).orElse(null); } catch (Exception ex) { return false; }
            if (type == null) return false;
            Entity ent = type.create(level);
            if (!(ent instanceof Mob m)) return false;
            mob = m;
            BlockPos safe = com.wavesurvivor.horde.spawn.SpawnZone.pick(level, b.pos, 3, type); // zone sûre autour de la Brèche
            mob.moveTo(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, RNG.nextFloat() * 360f, 0f);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null, null);
            for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) mob.setDropChance(slot, 0f);
            mob.setPersistenceRequired();
            com.wavesurvivor.horde.spawn.NetherSpawnFix.apply(mob); // hoglins/piglins : pas de zombification
            if (!level.addFreshEntity(mob)) return false;
            b.spawned.add(mob.getUUID());
            REINFORCEMENTS.add(mob.getUUID());
        }
        // Agressif tout de suite (les mobs neutres aussi)
        Player target = level.getNearestPlayer(mob, 48);
        if (target != null && !target.isCreative() && !target.isSpectator()) {
            mob.setTarget(target);
            if (mob instanceof NeutralMob nm) {
                nm.setPersistentAngerTarget(target.getUUID());
                nm.startPersistentAngerTimer();
            }
        }
        level.sendParticles(ParticleTypes.FLAME, x + 0.5, y + 0.5, z + 0.5, 10, 0.3, 0.5, 0.3, 0.05);
        return true;
    }

    private static void rename(LivingEntity b, AltarRecipes.BreachType type) {
        String title;
        if (type != null && type.name != null && !type.name.isBlank()) {
            title = com.wavesurvivor.i18n.WSLang.t(type.name);
        } else {
            String style = type == null || type.style == null ? "infernale" : type.style;
            title = com.wavesurvivor.i18n.WSLang.t("breach.name." + switch (style) {
                case "ames", "end", "abysses" -> style;
                default -> "infernale";
            });
        }
        b.setCustomName(Component.literal(title + " §r§c" + Math.round(b.getHealth()) + "§7/§c"
                + Math.round(b.getMaxHealth())));
    }

    // ─── Événements ───

    /** Brèche détruite : récompenses du type + message. */
    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        LivingEntity e = event.getEntity();
        if (!e.getPersistentData().getBoolean(TAG) || !(e.level() instanceof ServerLevel level)) return;
        AltarRecipes.BreachType type = null;
        Iterator<Breach> it = OPEN.iterator();
        while (it.hasNext()) {
            Breach b = it.next();
            if (b.id.equals(e.getUUID())) { type = b.type; it.remove(); }
        }
        BlockPos p = e.blockPosition();
        level.sendParticles(ParticleTypes.EXPLOSION, p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, 2, 0.3, 0.5, 0.3, 0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, 40, 0.4, 1.0, 0.4, 0.1);
        level.playSound(null, p, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.8f, 1.3f);
        if (type == null && CFG != null) type = CFG.effectiveTypes().get(0);
        if (type != null && type.rewards != null) {
            for (AltarRecipes.BreachReward r : type.rewards) {
                if (r == null || r.item == null || r.item.isBlank()) continue;
                if (RNG.nextDouble() * 100 >= r.chance) continue;
                int n = r.minQty + (r.maxQty > r.minQty ? RNG.nextInt(r.maxQty - r.minQty + 1) : 0);
                if (n <= 0) continue;
                ItemStack reward = LootItems.resolve(r.item, n);
                if (reward.isEmpty()) continue;
                ItemEntity drop = new ItemEntity(level, p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, reward);
                drop.setDefaultPickUpDelay();
                level.addFreshEntity(drop);
            }
        }
        String who = event.getSource().getEntity() instanceof ServerPlayer sp ? sp.getGameProfile().getName()
                : com.wavesurvivor.i18n.WSLang.t("common.team");
        broadcast(level.getServer(), com.wavesurvivor.i18n.WSLang.t("breach.closed", who, OPEN.size()));
    }

    /** Bonus de dégâts de la horde pour chaque Brèche restée ouverte. */
    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        if (damageBonus <= 0 || !HordeManager.get().isRunning()) return;
        if (!(event.getEntity() instanceof Player)) return;
        if (event.getSource().getEntity() instanceof Enemy) {
            event.setAmount((float) (event.getAmount() * (1 + damageBonus)));
        }
    }

    /** Annonces des Brèches : dans le fil d'événements (plus dans le chat). Ouverture et brèches laissées ouvertes = importantes. */
    private static void broadcast(MinecraftServer server, String msg) {
        if (server == null) return;
        boolean closed = msg.contains("§a");
        com.wavesurvivor.network.EventFeedPacket.toAll(server, Component.literal(msg), "minecraft:fire_charge",
                closed ? com.wavesurvivor.network.EventFeedPacket.RESOURCE : com.wavesurvivor.network.EventFeedPacket.CHAOS, !closed);
    }

    @SuppressWarnings("unused")
    private static void debug(String m) { WaveSurvivorMod.LOGGER.debug("[Breach] {}", m); }
}
