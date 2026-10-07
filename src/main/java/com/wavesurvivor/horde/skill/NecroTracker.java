package com.wavesurvivor.horde.skill;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.horde.model.HordeEntity;
import com.wavesurvivor.horde.spawn.MobRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * État partagé des compétences de la Nécropole :
 *   - Marque funèbre : joueurs marqués (monstres proches forcés à les cibler + dégâts subis augmentés)
 *   - Résurrection   : lanceurs actifs ; un mort-vivant tué dans leur rayon peut se relever
 *   - Invocations    : sbires enregistrés dans le MobRegistry (comptent dans la vague, sans loot)
 */
public class NecroTracker {

    private static final String REVENANT_TAG = "ws_revenant";
    private static final java.util.Random RNG = new java.util.Random();

    private record Mark(long expireTick, int bonusPct, double radius) {}
    private record Resurrector(double radius, int chance, double health, String entity, String message) {}

    private static final Map<UUID, Mark> MARKED = new ConcurrentHashMap<>();
    private static final Map<UUID, Resurrector> RESURRECTORS = new ConcurrentHashMap<>();

    // ─── API ───

    public static void mark(ServerPlayer p, long now, int durationTicks, int bonusPct, double radius) {
        MARKED.put(p.getUUID(), new Mark(now + durationTicks, bonusPct, radius));
        p.addEffect(new MobEffectInstance(MobEffects.GLOWING, durationTicks, 0, false, false));
    }

    public static boolean isMarked(UUID id) { return MARKED.containsKey(id); }

    public static void registerResurrector(LivingEntity caster, double radius, int chance, double health,
                                           String entity, String message) {
        RESURRECTORS.put(caster.getUUID(), new Resurrector(radius, chance, health, entity, message));
    }

    /** Fait surgir un sbire (compte dans la vague via MobRegistry, pas de loot). */
    public static Mob spawnMinion(ServerLevel level, String typeId, BlockPos pos, double health, String name) {
        EntityType<?> type = null;
        try { type = BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation(typeId)); } catch (Exception ignored) {}
        if (type == null) type = EntityType.ZOMBIE;
        Entity e = type.create(level);
        if (!(e instanceof Mob mob)) return null;
        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, RNG.nextFloat() * 360f, 0f);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
        com.wavesurvivor.horde.spawn.NetherSpawnFix.apply(mob); // adulte (pas de bébé zombie déterré)
        AttributeInstance hp = mob.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null && health > 0) { hp.setBaseValue(health); mob.setHealth((float) health); }
        if (name != null) { mob.setCustomName(Component.literal(name)); mob.setCustomNameVisible(false); }
        for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) mob.setDropChance(slot, 0f);
        // Casque : les morts-vivants ne brûlent pas au soleil
        if (mob.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty()) {
            mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET));
        }
        mob.getPersistentData().putBoolean(REVENANT_TAG, true); // ne peut pas se relever lui-même
        if (!level.addFreshEntity(mob)) return null;

        HordeEntity t = new HordeEntity();
        t.entityType = typeId;
        t.customName = name;
        t.lootTable = new ArrayList<>();
        MobRegistry.register(mob, t);
        return mob;
    }

    public static void playSound(ServerLevel level, BlockPos at, String id, float pitch) {
        if (id == null || id.isBlank()) return;
        try {
            SoundEvent s = BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation(id));
            if (s != null) level.playSound(null, at, s, SoundSource.HOSTILE, 1.0f, pitch);
        } catch (Exception ignored) {}
    }

    // ─── Événements ───

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || MARKED.isEmpty()) return;
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        if (now % 10 != 0) return;
        for (Map.Entry<UUID, Mark> e : MARKED.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            Mark m = e.getValue();
            if (p == null || !p.isAlive() || now >= m.expireTick()) {
                MARKED.remove(e.getKey());
                continue;
            }
            ServerLevel level = p.serverLevel();
            // Tous les monstres proches se retournent vers le marqué
            AABB box = p.getBoundingBox().inflate(m.radius());
            for (Monster mon : level.getEntitiesOfClass(Monster.class, box, Monster::isAlive)) {
                if (mon.getTarget() != p) mon.setTarget(p);
            }
            level.sendParticles(ParticleTypes.SOUL, p.getX(), p.getY() + 2.3, p.getZ(), 3, 0.15, 0.05, 0.15, 0.01);
        }
    }

    /** Marque funèbre : dégâts subis augmentés. */
    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        Mark m = MARKED.get(event.getEntity().getUUID());
        if (m != null && m.bonusPct() > 0) event.setAmount(event.getAmount() * (1f + m.bonusPct() / 100f));
    }

    /** Résurrection : un mort-vivant tué près d'un lanceur actif peut se relever. */
    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (RESURRECTORS.isEmpty() || !(dead.level() instanceof ServerLevel level)) return;

        // Le lanceur lui-même meurt → plus d'aura
        if (RESURRECTORS.remove(dead.getUUID()) != null) return;
        if (!(dead instanceof Mob) || dead.getMobType() != MobType.UNDEAD) return;
        if (dead.getPersistentData().getBoolean(REVENANT_TAG)) return;

        for (Map.Entry<UUID, Resurrector> e : RESURRECTORS.entrySet()) {
            Entity caster = level.getEntity(e.getKey());
            if (!(caster instanceof LivingEntity lc) || !lc.isAlive()) {
                if (caster == null || !caster.isAlive()) RESURRECTORS.remove(e.getKey());
                continue;
            }
            Resurrector r = e.getValue();
            if (lc.distanceToSqr(dead) > r.radius() * r.radius()) continue;
            if (RNG.nextInt(100) >= r.chance()) return;

            BlockPos at = dead.blockPosition();
            level.sendParticles(ParticleTypes.SOUL, at.getX() + 0.5, at.getY() + 0.3, at.getZ() + 0.5, 15, 0.3, 0.1, 0.3, 0.02);
            DelayedActionScheduler.schedule(level.getServer(), 40, () -> {
                level.sendParticles(ParticleTypes.SCULK_SOUL, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 20, 0.3, 0.5, 0.3, 0.03);
                level.playSound(null, at, SoundEvents.SKELETON_AMBIENT, SoundSource.HOSTILE, 1f, 0.6f);
                Mob rev = spawnMinion(level, r.entity(), at, r.health(), "§7Revenant");
                if (rev != null && r.message() != null && !r.message().isBlank()) {
                    for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, rev.getBoundingBox().inflate(24))) {
                        p.displayClientMessage(Component.literal(r.message()), true);
                    }
                }
            }, "necro resurrection");
            WaveSurvivorMod.LOGGER.debug("[Necro] Résurrection programmée @ {}", at);
            return;
        }
    }

    /** Fin de horde : on oublie tout. */
    public static void clearAll() {
        MARKED.clear();
        RESURRECTORS.clear();
    }
}
