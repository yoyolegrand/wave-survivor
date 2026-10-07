package com.wavesurvivor.horde.boss;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.altar.AltarDefense;
import com.wavesurvivor.altar.AltarRecipes;
import com.wavesurvivor.horde.HordeManager;
import com.wavesurvivor.horde.breach.BreachManager;
import com.wavesurvivor.horde.skill.NecroTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LE CAVALIER DES CENDRES (boss CustomEntity avec bossConfig.cavalierMechanics = true, monté sur un hoglin).
 *
 *   Phase 1 — La Charge : monté. Toutes les ~7 s, la monture s'arrête, gronde (1 s d'avertissement) puis CHARGE
 *             en ligne droite : projette et blesse les joueurs sur son passage.
 *   Phase 2 — Désarçonné (monture morte OU cavalier ≤ 60 %) : il met pied à terre, ENRAGÉ (+30 % vitesse,
 *             +25 % dégâts), déchire le sol (3 Brèches infernales) et relève 2 Gardes des Cendres.
 *             Tant qu'une Brèche est ouverte, il ne subit que 50 % des dégâts.
 *   Phase 3 — Rites de cendres (≤ 30 %) : PLUIE DE MÉTÉORES sur l'arène (cercles d'alerte puis impacts)
 *             et il siphonne le Monolithe pour se soigner.
 */
public class CavalierController {

    private static final UUID ENRAGE_SPEED = UUID.fromString("7c1f1d5e-2c8b-4f6e-9d41-0a1b2c3d4e51");
    private static final UUID ENRAGE_DAMAGE = UUID.fromString("7c1f1d5e-2c8b-4f6e-9d41-0a1b2c3d4e52");
    private static final Random RNG = new Random();

    private static class State {
        final UUID rider;
        final BlockPos arena;
        UUID mount;
        boolean mountSeen;
        int phase = 1;
        // Charge
        long nextCharge;
        int telegraph = 0, charging = 0;
        Vec3 chargeDir = Vec3.ZERO;
        final Set<UUID> hitThisCharge = new HashSet<>();
        // Phase 3
        long nextMeteor, nextSiphon;
        State(UUID rider, BlockPos arena, long now) { this.rider = rider; this.arena = arena; this.nextCharge = now + 100; }
    }

    private static final Map<UUID, State> BOSSES = new ConcurrentHashMap<>();

    public static void register(LivingEntity rider, BlockPos spawnCenter) {
        BlockPos altar = AltarDefense.activePos();
        long now = rider.level().getServer() != null ? rider.level().getServer().getTickCount() : 0;
        BOSSES.put(rider.getUUID(), new State(rider.getUUID(), altar != null ? altar : spawnCenter, now));
        WaveSurvivorMod.LOGGER.info("[Cavalier] Mécaniques activées pour {}", rider.getName().getString());
    }

    // ─── Tick ───

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || BOSSES.isEmpty()) return;
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        for (State s : BOSSES.values()) {
            LivingEntity rider = find(server, s.rider);
            if (rider == null || !rider.isAlive()) { BOSSES.remove(s.rider); continue; }
            ServerLevel level = (ServerLevel) rider.level();

            // Monture (capturée dès qu'il est en selle)
            if (s.mount == null && rider.getVehicle() instanceof LivingEntity v) { s.mount = v.getUUID(); s.mountSeen = true; }
            LivingEntity mount = s.mount != null ? find(server, s.mount) : null;
            boolean mounted = mount != null && mount.isAlive() && rider.getVehicle() == mount;
            float pct = rider.getHealth() / rider.getMaxHealth();

            if (s.phase == 1) {
                if (mounted && mount instanceof Mob mm) tickCharge(level, rider, mm, s, now);
                boolean mountDown = s.mountSeen && (mount == null || !mount.isAlive());
                if (mountDown || pct <= 0.60f) enterPhase2(level, rider, mount, s);
            } else if (s.phase == 2 && pct <= 0.30f) {
                enterPhase3(level, rider, s, now);
            }
            if (s.phase == 3) tickPhase3(level, rider, s, now);
            if (s.phase >= 2 && BreachManager.openCount() > 0 && now % 6 == 0) {
                // Aura de protection visible tant que des Brèches sont ouvertes
                level.sendParticles(ParticleTypes.FLAME, rider.getX(), rider.getY() + 1.2, rider.getZ(), 4, 0.5, 0.7, 0.5, 0.01);
            }
        }
    }

    // ─── Phase 1 : la Charge ───

    private static void tickCharge(ServerLevel level, LivingEntity rider, Mob mount, State s, long now) {
        if (s.charging > 0) {
            s.charging--;
            mount.getNavigation().stop();
            mount.setDeltaMovement(s.chargeDir.x, mount.getDeltaMovement().y, s.chargeDir.z);
            mount.hurtMarked = true;
            level.sendParticles(ParticleTypes.LARGE_SMOKE, mount.getX(), mount.getY() + 0.4, mount.getZ(), 3, 0.4, 0.2, 0.4, 0.01);
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, mount.getBoundingBox().inflate(0.8),
                    pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
                if (!s.hitThisCharge.add(p.getUUID())) continue;
                p.hurt(level.damageSources().mobAttack(mount), 8f);
                if (!com.wavesurvivor.item.RelicEffects.immovable(p)) {
                    Vec3 k = s.chargeDir.normalize().scale(1.4);
                    p.setDeltaMovement(k.x, 0.55, k.z);
                    p.hurtMarked = true;
                }
                level.playSound(null, p.blockPosition(), SoundEvents.HOGLIN_ATTACK, SoundSource.HOSTILE, 1.2f, 0.8f);
            }
            return;
        }
        if (s.telegraph > 0) {
            s.telegraph--;
            mount.getNavigation().stop();
            if (s.telegraph % 4 == 0) level.sendParticles(ParticleTypes.ANGRY_VILLAGER, mount.getX(), mount.getY() + 1.4, mount.getZ(), 2, 0.4, 0.2, 0.4, 0);
            if (s.telegraph == 0) {
                LivingEntity target = rider instanceof Mob rm ? rm.getTarget() : null;
                if (target == null) target = level.getNearestPlayer(mount, 24);
                if (target != null) {
                    Vec3 d = target.position().subtract(mount.position());
                    Vec3 flat = new Vec3(d.x, 0, d.z);
                    if (flat.lengthSqr() > 0.01) {
                        s.chargeDir = flat.normalize().scale(0.95);
                        s.charging = 16;
                        s.hitThisCharge.clear();
                        level.playSound(null, mount.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.4f, 1.2f);
                    }
                }
            }
            return;
        }
        if (now >= s.nextCharge) {
            LivingEntity target = rider instanceof Mob rm ? rm.getTarget() : null;
            if (target != null && target.distanceToSqr(mount) < 22 * 22) {
                s.telegraph = 20; // 1 s d'avertissement
                level.playSound(null, mount.blockPosition(), SoundEvents.HOGLIN_ANGRY, SoundSource.HOSTILE, 1.5f, 0.7f);
            }
            s.nextCharge = now + 140;
        }
    }

    // ─── Phase 2 : Désarçonné ───

    private static void enterPhase2(ServerLevel level, LivingEntity rider, LivingEntity mount, State s) {
        s.phase = 2;
        if (rider.isPassenger()) rider.stopRiding();
        addModifier(rider.getAttribute(Attributes.MOVEMENT_SPEED), ENRAGE_SPEED, "Cavalier enragé (vitesse)", 0.30);
        addModifier(rider.getAttribute(Attributes.ATTACK_DAMAGE), ENRAGE_DAMAGE, "Cavalier enragé (dégâts)", 0.25);
        level.sendParticles(ParticleTypes.LAVA, rider.getX(), rider.getY() + 1, rider.getZ(), 30, 0.6, 0.8, 0.6, 0.1);
        level.sendParticles(ParticleTypes.EXPLOSION, rider.getX(), rider.getY() + 1, rider.getZ(), 2, 0.4, 0.4, 0.4, 0);
        level.playSound(null, rider.blockPosition(), SoundEvents.WITHER_SKELETON_AMBIENT, SoundSource.HOSTILE, 2f, 0.5f);
        level.playSound(null, rider.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.5f, 0.5f);

        // Il déchire le sol : 3 Brèches autour de l'arène (réglages de la horde si elle en a)
        String horde = HordeManager.get().getActiveHorde() != null ? HordeManager.get().getActiveHorde().hordeName : null;
        AltarRecipes.Breaches cfg = horde != null ? AltarRecipes.getBreaches(horde) : null;
        BreachManager.open(level, s.arena, 3, 9, cfg);
        // Relève 2 Gardes des Cendres
        for (int i = 0; i < 2; i++) {
            double a = RNG.nextDouble() * Math.PI * 2;
            int x = rider.blockPosition().getX() + (int) Math.round(Math.cos(a) * 3);
            int z = rider.blockPosition().getZ() + (int) Math.round(Math.sin(a) * 3);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            Mob g = NecroTracker.spawnMinion(level, "minecraft:wither_skeleton", new BlockPos(x, y, z), 30, com.wavesurvivor.i18n.WSLang.t("§8Garde des Cendres"));
            if (g != null) {
                g.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE_SWORD));
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x + 0.5, y + 1, z + 0.5, 15, 0.3, 0.6, 0.3, 0.03);
            }
        }
        broadcast(level, com.wavesurvivor.i18n.WSLang.t("cavalier.unhorsed"));
    }

    // ─── Phase 3 : Rites de cendres ───

    private static void enterPhase3(ServerLevel level, LivingEntity rider, State s, long now) {
        s.phase = 3;
        s.nextMeteor = now + 20;
        s.nextSiphon = now + 40;
        level.playSound(null, rider.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.2f, 1.4f);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, rider.getX(), rider.getY() + 2, rider.getZ(), 60, 3, 1.5, 3, 0.02);
        broadcast(level, com.wavesurvivor.i18n.WSLang.t("cavalier.rites"));
    }

    private static void tickPhase3(ServerLevel level, LivingEntity rider, State s, long now) {
        if (now >= s.nextMeteor) {
            s.nextMeteor = now + 25;
            // 1 météore sur un joueur au hasard + 2 au hasard dans l'arène
            var players = level.getEntitiesOfClass(ServerPlayer.class, new AABB(s.arena).inflate(24),
                    p -> p.isAlive() && !p.isCreative() && !p.isSpectator());
            if (!players.isEmpty()) meteor(level, rider, players.get(RNG.nextInt(players.size())).position());
            for (int i = 0; i < 2; i++) {
                double a = RNG.nextDouble() * Math.PI * 2, r = 2 + RNG.nextDouble() * 10;
                int x = s.arena.getX() + (int) Math.round(Math.cos(a) * r), z = s.arena.getZ() + (int) Math.round(Math.sin(a) * r);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                meteor(level, rider, new Vec3(x + 0.5, y, z + 0.5));
            }
        }
        if (now >= s.nextSiphon) {
            s.nextSiphon = now + 40;
            BlockPos altar = AltarDefense.activePos();
            if (altar != null) {
                float taken = AltarDefense.siphon(level, 6f);
                if (taken > 0) {
                    rider.heal(taken);
                    Vec3 from = Vec3.atCenterOf(altar).add(0, 0.6, 0), to = rider.position().add(0, 1.2, 0), d = to.subtract(from);
                    int steps = (int) Math.max(4, d.length() * 2);
                    for (int i = 0; i <= steps; i++) {
                        Vec3 p = from.add(d.scale((double) i / steps));
                        level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                    }
                }
            }
        }
    }

    /** Cercle d'alerte 1,25 s, puis impact : dégâts 7 dans 2,5 blocs, feu, projection. Aucun bloc détruit. */
    private static void meteor(ServerLevel level, LivingEntity rider, Vec3 spot) {
        for (int t = 0; t < 25; t += 5) {
            final int tt = t;
            com.wavesurvivor.horde.skill.DelayedActionScheduler.schedule(level.getServer(), t, () -> {
                for (int k = 0; k < 14; k++) {
                    double a = Math.PI * 2 * k / 14;
                    level.sendParticles(ParticleTypes.FLAME, spot.x + Math.cos(a) * 2.5, spot.y + 0.1, spot.z + Math.sin(a) * 2.5, 1, 0, 0, 0, 0);
                }
                // traînée qui descend du ciel
                double h = 14 - tt * 0.55;
                level.sendParticles(ParticleTypes.LAVA, spot.x, spot.y + h, spot.z, 2, 0.2, 0.2, 0.2, 0);
            }, "meteor warn");
        }
        com.wavesurvivor.horde.skill.DelayedActionScheduler.schedule(level.getServer(), 25, () -> {
            BlockPos bp = BlockPos.containing(spot);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, spot.x, spot.y + 0.5, spot.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.LAVA, spot.x, spot.y + 0.5, spot.z, 20, 1.2, 0.4, 1.2, 0.2);
            level.playSound(null, bp, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.2f, 0.8f);
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(spot, spot).inflate(2.5, 2.5, 2.5),
                    pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
                p.hurt(rider.isAlive() ? level.damageSources().mobAttack(rider) : level.damageSources().inFire(), 7f);
                p.setSecondsOnFire(4);
                if (com.wavesurvivor.item.RelicEffects.immovable(p)) continue;
                Vec3 k = p.position().subtract(spot).normalize().scale(0.8);
                p.setDeltaMovement(k.x, 0.6, k.z);
                p.hurtMarked = true;
            }
        }, "meteor impact");
    }

    // ─── Protection des Brèches (phase ≥ 2) ───

    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        State s = BOSSES.get(event.getEntity().getUUID());
        if (s == null || s.phase < 2 || BreachManager.openCount() <= 0) return;
        event.setAmount(event.getAmount() * 0.5f);
        LivingEntity e = event.getEntity();
        if (e.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SMALL_FLAME, e.getX(), e.getY() + 1, e.getZ(), 6, 0.4, 0.5, 0.4, 0.02);
        }
        if (event.getSource().getEntity() instanceof ServerPlayer p) {
            p.displayClientMessage(com.wavesurvivor.i18n.WSLang.c("cavalier.protected"), true);
        }
    }

    // ─── Utils ───

    private static void addModifier(AttributeInstance inst, UUID id, String name, double pct) {
        if (inst == null || inst.getModifier(id) != null) return;
        inst.addTransientModifier(new AttributeModifier(id, name, pct, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    private static void broadcast(ServerLevel level, String msg) {
        Component c = Component.literal(msg);
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) p.sendSystemMessage(c);
    }

    private static LivingEntity find(MinecraftServer server, UUID id) {
        for (ServerLevel lvl : server.getAllLevels()) {
            Entity e = lvl.getEntity(id);
            if (e instanceof LivingEntity le) return le;
        }
        return null;
    }

    public static void clearAll(MinecraftServer server) {
        BOSSES.clear();
    }
}
