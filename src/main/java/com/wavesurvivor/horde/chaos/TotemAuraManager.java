package com.wavesurvivor.horde.chaos;

import com.wavesurvivor.entity.BrecheEntity;
import com.wavesurvivor.entity.TotemEntity;
import com.wavesurvivor.horde.loot.LootItems;
import com.wavesurvivor.horde.model.ChaosEvent;
import com.wavesurvivor.horde.skill.NecroTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TOTEMS DU CHAOS : chaque seconde, applique l'aura d'un totem dans son rayon (cercle au sol à sa couleur).
 * Effets CONFIGURABLES (liste effet / niveau / cible joueurs ou monstres) ; si la liste est vide, effets par défaut :
 *   fureur      → monstres : Force I + Vitesse I
 *   malediction → joueurs  : Lenteur I + Faiblesse I
 *   soin        → monstres : +healPerSecond PV/s
 *   invocation  → summonCount monstres toutes les summonInterval s (summonMax vivants max)
 */
public class TotemAuraManager {

    private static final Random RNG = new Random();

    private static class Aura {
        final UUID id;
        final String aura;
        final double radius;
        final long expires;
        final String rewardItem;
        final int rewardCount;
        final String summonType;
        final List<ChaosEvent.AuraEffect> effects;
        final double heal;
        final int summonCount, summonInterval, summonMax;
        long nextSummon;
        final List<UUID> summoned = new ArrayList<>();

        Aura(UUID id, ChaosEvent ev, String aura, String summonType, long now) {
            this.id = id;
            this.aura = aura;
            this.radius = Math.max(2, ev.radius);
            this.expires = now + Math.max(10, ev.lifetime > 0 ? ev.lifetime : 60) * 20L;
            this.rewardItem = ev.rewardItem;
            this.rewardCount = ev.rewardCount;
            this.summonType = summonType;
            this.effects = ev.effects != null && !ev.effects.isEmpty() ? new ArrayList<>(ev.effects) : defaults(aura);
            this.heal = Math.max(0, ev.healPerSecond);
            this.summonCount = Math.max(1, ev.summonCount);
            this.summonInterval = Math.max(2, ev.summonInterval);
            this.summonMax = Math.max(1, ev.summonMax);
            this.nextSummon = now + 100;
        }
    }

    /** Effets par défaut d'une aura (liste vide dans la config). */
    public static List<ChaosEvent.AuraEffect> defaults(String aura) {
        List<ChaosEvent.AuraEffect> l = new ArrayList<>();
        switch (aura == null ? "" : aura) {
            case "malediction" -> {
                l.add(new ChaosEvent.AuraEffect("minecraft:slowness", 1, "joueurs"));
                l.add(new ChaosEvent.AuraEffect("minecraft:weakness", 1, "joueurs"));
            }
            case "soin", "invocation" -> { }
            default -> { // fureur
                l.add(new ChaosEvent.AuraEffect("minecraft:strength", 1, "monstres"));
                l.add(new ChaosEvent.AuraEffect("minecraft:speed", 1, "monstres"));
            }
        }
        return l;
    }

    private static final Map<UUID, Aura> AURAS = new ConcurrentHashMap<>();

    public static void register(TotemEntity t, ChaosEvent ev, String aura, String summonType) {
        long now = t.level().getServer() != null ? t.level().getServer().getTickCount() : 0;
        AURAS.put(t.getUUID(), new Aura(t.getUUID(), ev, aura, summonType, now));
    }

    /** Test / appel simple : réglages par défaut. */
    public static void register(TotemEntity t, String aura, double radius, int lifetimeSec, String rewardItem, int rewardCount,
                                String summonType) {
        ChaosEvent ev = new ChaosEvent();
        ev.radius = radius;
        ev.lifetime = lifetimeSec;
        ev.rewardItem = rewardItem;
        ev.rewardCount = rewardCount;
        register(t, ev, aura, summonType);
    }

    public static void clearAll(MinecraftServer server) {
        if (server != null) {
            for (Aura a : AURAS.values()) {
                for (ServerLevel lvl : server.getAllLevels()) {
                    Entity e = lvl.getEntity(a.id);
                    if (e != null) e.discard();
                }
            }
        }
        AURAS.clear();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || AURAS.isEmpty()) return;
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        if (now % 20 != 0) return;
        for (Aura a : AURAS.values()) {
            TotemEntity t = null;
            for (ServerLevel lvl : server.getAllLevels()) {
                if (lvl.getEntity(a.id) instanceof TotemEntity te) { t = te; break; }
            }
            if (t == null || !t.isAlive()) { AURAS.remove(a.id); continue; }
            final TotemEntity totem = t;
            ServerLevel level = (ServerLevel) t.level();
            if (now >= a.expires) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, t.getX(), t.getY() + 1.3, t.getZ(), 20, 0.3, 0.8, 0.3, 0.02);
                level.playSound(null, t.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 1f, 0.8f);
                t.discard();
                AURAS.remove(a.id);
                continue;
            }
            ring(level, t, a);
            t.setCustomName(Component.literal(title(a.aura) + " §r§c" + Math.round(t.getHealth()) + "§7/§c" + Math.round(t.getMaxHealth())
                    + " §8(" + Math.max(0, (a.expires - now) / 20) + " s)"));
            AABB box = t.getBoundingBox().inflate(a.radius, 3, a.radius);
            List<LivingEntity> monsters = null;
            List<ServerPlayer> players = null;

            // ─── Effets configurés ───
            for (ChaosEvent.AuraEffect fx : a.effects) {
                if (fx == null || fx.effect == null || fx.effect.isBlank()) continue;
                MobEffect effect;
                try { effect = BuiltInRegistries.MOB_EFFECT.get(new ResourceLocation(fx.effect.trim())); } catch (Exception e) { continue; }
                if (effect == null) continue;
                int amp = Math.max(0, fx.level - 1);
                boolean onPlayers = !"monstres".equalsIgnoreCase(fx.target);
                if (onPlayers) {
                    if (players == null) players = level.getEntitiesOfClass(ServerPlayer.class, box,
                            pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator() && pl.distanceToSqr(totem) <= a.radius * a.radius);
                    for (ServerPlayer p : players) {
                        p.addEffect(new MobEffectInstance(effect, effect.isInstantenous() ? 1 : 50, amp, false, true));
                        level.sendParticles(ParticleTypes.WITCH, p.getX(), p.getY() + 1, p.getZ(), 2, 0.3, 0.4, 0.3, 0);
                    }
                } else {
                    if (monsters == null) monsters = monsters(level, box, t, a.radius);
                    for (LivingEntity m : monsters) {
                        m.addEffect(new MobEffectInstance(effect, effect.isInstantenous() ? 1 : 50, amp, false, true));
                        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, m.getX(), m.getY() + m.getBbHeight() + 0.3, m.getZ(), 1, 0.2, 0, 0.2, 0);
                    }
                }
            }

            // ─── Soin ───
            if ("soin".equals(a.aura) && a.heal > 0) {
                if (monsters == null) monsters = monsters(level, box, t, a.radius);
                for (LivingEntity m : monsters) {
                    if (m.getHealth() < m.getMaxHealth()) {
                        m.heal((float) a.heal);
                        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, m.getX(), m.getY() + m.getBbHeight(), m.getZ(), 3, 0.3, 0.2, 0.3, 0);
                    }
                }
            }

            // ─── Invocation ───
            if ("invocation".equals(a.aura)) {
                a.summoned.removeIf(id -> { Entity e = level.getEntity(id); return e == null || !e.isAlive(); });
                if (now >= a.nextSummon && a.summoned.size() < a.summonMax) {
                    a.nextSummon = now + a.summonInterval * 20L;
                    int n = Math.min(a.summonCount, a.summonMax - a.summoned.size());
                    for (int i = 0; i < n; i++) {
                        double ang = RNG.nextDouble() * Math.PI * 2;
                        int x = t.getBlockX() + (int) Math.round(Math.cos(ang) * 2.5), z = t.getBlockZ() + (int) Math.round(Math.sin(ang) * 2.5);
                        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                        Mob m = NecroTracker.spawnMinion(level, a.summonType, new BlockPos(x, y, z), 20,
                                com.wavesurvivor.i18n.WSLang.t("totem.servant"));
                        if (m != null) {
                            a.summoned.add(m.getUUID());
                            level.sendParticles(ParticleTypes.FLAME, x + 0.5, y + 0.5, z + 0.5, 12, 0.3, 0.5, 0.3, 0.03);
                        }
                    }
                    level.playSound(null, t.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1f, 0.9f);
                }
            }
        }
    }

    /** Totem détruit : récompense + message. */
    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        Aura a = AURAS.remove(event.getEntity().getUUID());
        if (a == null || !(event.getEntity().level() instanceof ServerLevel level)) return;
        LivingEntity t = event.getEntity();
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, t.getX(), t.getY() + 1.3, t.getZ(), 40, 0.4, 0.8, 0.4, 0.4);
        ItemStack reward = LootItems.resolve(a.rewardItem, a.rewardCount);
        if (!reward.isEmpty()) {
            ItemEntity drop = new ItemEntity(level, t.getX(), t.getY() + 1, t.getZ(), reward);
            drop.setDefaultPickUpDelay();
            level.addFreshEntity(drop);
        }
        String who = event.getSource().getEntity() instanceof ServerPlayer sp ? sp.getGameProfile().getName()
                : com.wavesurvivor.i18n.WSLang.t("common.team");
        Component c = com.wavesurvivor.i18n.WSLang.c("totem.destroyed", label(a.aura), who);
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) p.sendSystemMessage(c);
    }

    private static List<LivingEntity> monsters(ServerLevel level, AABB box, TotemEntity t, double r) {
        return level.getEntitiesOfClass(LivingEntity.class, box, e -> e instanceof Enemy && e.isAlive()
                && !(e instanceof TotemEntity) && !(e instanceof BrecheEntity) && e.distanceToSqr(t) <= r * r);
    }

    private static void ring(ServerLevel level, TotemEntity t, Aura a) {
        int col = TotemEntity.roleColor(a.aura);
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(((col >> 16) & 0xFF) / 255f, ((col >> 8) & 0xFF) / 255f,
                (col & 0xFF) / 255f), 1.2f);
        int n = (int) Math.max(16, a.radius * 5);
        for (int k = 0; k < n; k++) {
            double ang = Math.PI * 2 * k / n;
            level.sendParticles(dust, t.getX() + Math.cos(ang) * a.radius, t.getY() + 0.15, t.getZ() + Math.sin(ang) * a.radius, 1, 0, 0, 0, 0);
        }
    }

    public static String label(String aura) {
        return com.wavesurvivor.i18n.WSLang.t("totem.label." + key(aura));
    }

    private static String title(String aura) {
        return com.wavesurvivor.i18n.WSLang.t("totem.title." + key(aura));
    }

    private static String key(String aura) {
        return switch (aura == null ? "" : aura) {
            case "soin", "malediction", "invocation" -> aura;
            default -> "fureur";
        };
    }
}
