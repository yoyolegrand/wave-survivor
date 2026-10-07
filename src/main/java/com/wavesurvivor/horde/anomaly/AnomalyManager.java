package com.wavesurvivor.horde.anomaly;

import com.wavesurvivor.altar.AltarDefense;
import com.wavesurvivor.altar.AltarRecipes;
import com.wavesurvivor.horde.HordeManager;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * ANOMALIES (config par horde : altar_recipes.json → anomalies).
 *   - toutes les intervalSeconds, `count` anomalies : la moitié sous des joueurs, le reste au hasard dans la zone ;
 *   - chacune TIRE SON TYPE (anomalies.types, pondérés) : couleur du cercle, rayon, durées, EFFETS LIBRES
 *     (effet · niveau · durée), dégâts par seconde, attraction (> 0) ou répulsion (< 0), explosion finale ;
 *   - AVERTISSEMENT : cercle coloré au sol (rouge juste avant), colonne de lumière, son qui monte ;
 *   - ACTIF : les JOUEURS dans le cercle subissent le type. Les monstres ne sont jamais affectés.
 *   - Ancien format (lévitation seule) converti automatiquement (Anomalies.effectiveTypes()).
 *   - API spawnAt / setIntensity pour les boss (Xâl'Tor) et la commande de test.
 */
public class AnomalyManager {

    private static final Random RNG = new Random();

    private static class Anomaly {
        final ServerLevel level;
        final Vec3 pos;
        final AltarRecipes.AnomalyType type;
        final double radius;
        final int warnTotal;
        final DustParticleOptions dust;
        int warning, active;

        Anomaly(ServerLevel level, Vec3 pos, AltarRecipes.AnomalyType type) {
            this.level = level;
            this.pos = pos;
            this.type = type;
            this.radius = Math.max(1, type.radius);
            this.warning = Math.max(10, type.warningTicks);
            this.active = Math.max(20, type.durationTicks);
            this.warnTotal = Math.max(1, warning);
            int c = type.color;
            this.dust = new DustParticleOptions(new Vector3f(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f), 2.0f);
        }
    }

    private static final List<Anomaly> ANOMALIES = new ArrayList<>();
    private static long nextWave = 0;
    /** Multiplicateur de fréquence (Xâl'Tor phase 3 : 2 = deux fois plus souvent). */
    private static double intensity = 1.0;

    public static void setIntensity(double v) { intensity = Math.max(0.5, v); }

    public static void clearAll() {
        ANOMALIES.clear();
        nextWave = 0;
        intensity = 1.0;
    }

    /** Crée une anomalie (type tiré au hasard dans la config) — utilisable par un boss ou la commande de test. */
    public static void spawnAt(ServerLevel level, Vec3 ground, AltarRecipes.Anomalies cfg) {
        if (cfg == null) cfg = new AltarRecipes.Anomalies();
        spawnAt(level, ground, cfg.pickType(RNG));
    }

    /** Crée une anomalie d'un type précis. */
    public static void spawnAt(ServerLevel level, Vec3 ground, AltarRecipes.AnomalyType type) {
        if (type == null) type = new AltarRecipes.Anomalies().effectiveTypes().get(0);
        ANOMALIES.add(new Anomaly(level, ground, type));
        level.playSound(null, BlockPos.containing(ground), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 2.0f, 0.7f);
        level.playSound(null, BlockPos.containing(ground), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.5f, 0.5f);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();

        // Déclenchement périodique pendant une horde qui a des anomalies
        HordeManager hm = HordeManager.get();
        if (hm.isRunning() && hm.getActiveHorde() != null) {
            AltarRecipes.Anomalies cfg = AltarRecipes.getAnomalies(hm.getActiveHorde().hordeName);
            if (cfg != null && hm.getCurrentWave() >= cfg.fromWave) {
                if (nextWave == 0) nextWave = now + 100;
                if (now >= nextWave) {
                    nextWave = now + (long) (Math.max(5, cfg.intervalSeconds) * 20 / intensity);
                    trigger(server, cfg);
                }
            }
        } else {
            nextWave = 0; // hors horde : pas de salve automatique (les anomalies de test restent actives)
        }

        if (ANOMALIES.isEmpty()) return;
        Iterator<Anomaly> it = ANOMALIES.iterator();
        while (it.hasNext()) {
            Anomaly a = it.next();
            if (a.warning > 0) {
                a.warning--;
                boolean urgent = a.warning < 12;
                if (a.warning % 2 == 0) ring(a, urgent);
                for (int k = 0; k < 2; k++) {
                    a.level.sendParticles(ParticleTypes.END_ROD, a.pos.x, a.pos.y + RNG.nextDouble() * 8, a.pos.z, 1, 0.05, 0.1, 0.05, 0.005);
                }
                if (a.warning % 8 == 0) {
                    float progress = 1f - (float) a.warning / a.warnTotal;
                    a.level.playSound(null, BlockPos.containing(a.pos), SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE,
                            1.8f, 0.8f + progress * 1.1f);
                }
                if (a.warning % 10 == 0) {
                    for (ServerPlayer p : playersIn(a, 4)) {
                        p.displayClientMessage(com.wavesurvivor.i18n.WSLang.c("anomaly.under_you"), true);
                    }
                }
                if (a.warning == 0) {
                    a.level.playSound(null, BlockPos.containing(a.pos), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 2.0f, 0.6f);
                    a.level.playSound(null, BlockPos.containing(a.pos), SoundEvents.ENDER_EYE_DEATH, SoundSource.HOSTILE, 1.5f, 0.6f);
                    a.level.sendParticles(ParticleTypes.REVERSE_PORTAL, a.pos.x, a.pos.y + 0.5, a.pos.z, 80, a.radius * 0.5, 0.3, a.radius * 0.5, 0.4);
                }
                continue;
            }
            if (a.active-- <= 0) {
                finish(a);
                it.remove();
                continue;
            }
            // Colonne active : particules
            if (a.active % 2 == 0) {
                for (int k = 0; k < 6; k++) {
                    double ang = RNG.nextDouble() * Math.PI * 2, r = RNG.nextDouble() * a.radius;
                    a.level.sendParticles(ParticleTypes.REVERSE_PORTAL, a.pos.x + Math.cos(ang) * r, a.pos.y + RNG.nextDouble() * 5,
                            a.pos.z + Math.sin(ang) * r, 1, 0, 0.2, 0, 0.05);
                }
                for (int c = 0; c < 8; c++) {
                    double ang = Math.PI * 2 * c / 8 + a.active * 0.05;
                    a.level.sendParticles(a.dust, a.pos.x + Math.cos(ang) * a.radius, a.pos.y + RNG.nextDouble() * 6,
                            a.pos.z + Math.sin(ang) * a.radius, 1, 0, 0, 0, 0);
                }
                if (a.active % 8 == 0) ring(a, false);
            }
            // Effets sur les joueurs dans le cercle
            for (ServerPlayer p : playersIn(a, 7)) {
                if (a.active % 5 == 0 && a.type.effects != null) {
                    for (AltarRecipes.AnomalyEffect fx : a.type.effects) {
                        MobEffect eff = effect(fx.effect);
                        if (eff == null) continue;
                        int dur = eff.isInstantenous() ? 1 : Math.max(10, fx.duration);
                        p.addEffect(new MobEffectInstance(eff, dur, Math.max(0, fx.level - 1), false, false, true));
                    }
                }
                if (a.type.damagePerSecond > 0 && a.active % 20 == 0) {
                    p.hurt(a.level.damageSources().magic(), (float) a.type.damagePerSecond);
                }
                if (a.type.pull != 0 && !com.wavesurvivor.item.RelicEffects.immovable(p)) {
                    Vec3 to = new Vec3(a.pos.x - p.getX(), 0, a.pos.z - p.getZ());
                    if (to.lengthSqr() > 0.16) {
                        Vec3 v = to.normalize().scale(a.type.pull);
                        p.setDeltaMovement(p.getDeltaMovement().add(v.x, 0, v.z));
                        p.hurtMarked = true;
                    }
                }
            }
        }
    }

    /** Fin de l'anomalie : explosion éventuelle (sans casser de blocs). */
    private static void finish(Anomaly a) {
        a.level.playSound(null, BlockPos.containing(a.pos), SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.HOSTILE, 0.8f, 0.7f);
        if (a.type.burstDamage <= 0) return;
        a.level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, a.pos.x, a.pos.y + 0.5, a.pos.z, 1, 0, 0, 0, 0);
        a.level.playSound(null, BlockPos.containing(a.pos), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.5f, 0.9f);
        for (ServerPlayer p : playersIn(a, 7)) {
            p.hurt(a.level.damageSources().magic(), (float) a.type.burstDamage);
        }
    }

    private static List<ServerPlayer> playersIn(Anomaly a, double height) {
        AABB box = new AABB(a.pos.x - a.radius, a.pos.y - 1, a.pos.z - a.radius, a.pos.x + a.radius, a.pos.y + height, a.pos.z + a.radius);
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : a.level.getEntitiesOfClass(ServerPlayer.class, box, pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
            double dx = p.getX() - a.pos.x, dz = p.getZ() - a.pos.z;
            if (dx * dx + dz * dz <= a.radius * a.radius) out.add(p);
        }
        return out;
    }

    private static MobEffect effect(String id) {
        if (id == null || id.isBlank()) return null;
        try {
            return BuiltInRegistries.MOB_EFFECT.getOptional(new ResourceLocation(id.trim())).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** Déclenche une salve : moitié sous des joueurs, reste au hasard autour du centre. */
    private static void trigger(MinecraftServer server, AltarRecipes.Anomalies cfg) {
        BlockPos center = AltarDefense.activePos();
        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        players.removeIf(p -> p.isCreative() || p.isSpectator() || !p.isAlive());
        if (players.isEmpty()) return;
        ServerLevel level = players.get(0).serverLevel();
        if (center == null) center = players.get(0).blockPosition();
        int onPlayers = Math.max(1, cfg.count / 2);
        for (int i = 0; i < cfg.count; i++) {
            Vec3 spot;
            if (i < onPlayers) {
                ServerPlayer p = players.get(RNG.nextInt(players.size()));
                if (p.distanceToSqr(Vec3.atCenterOf(center)) > 40 * 40) continue; // loin de l'arène : épargné
                spot = new Vec3(p.getX(), ground(level, p.getBlockX(), p.getBlockZ()), p.getZ());
            } else {
                double ang = RNG.nextDouble() * Math.PI * 2, r = 2 + RNG.nextDouble() * Math.max(2, cfg.zoneRadius - 2);
                int x = center.getX() + (int) Math.round(Math.cos(ang) * r), z = center.getZ() + (int) Math.round(Math.sin(ang) * r);
                spot = new Vec3(x + 0.5, ground(level, x, z), z + 0.5);
            }
            spawnAt(level, spot, cfg.pickType(RNG));
        }
        Component msg = com.wavesurvivor.i18n.WSLang.c("anomaly.alert");
        for (ServerPlayer p : players) p.displayClientMessage(msg, true);
    }

    private static double ground(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1.0f, 0.15f, 0.2f), 2.2f);

    private static void ring(Anomaly a, boolean urgent) {
        int n = (int) Math.max(24, a.radius * 10);
        for (int k = 0; k < n; k++) {
            double ang = Math.PI * 2 * k / n;
            a.level.sendParticles(urgent ? RED : a.dust, a.pos.x + Math.cos(ang) * a.radius, a.pos.y + 0.2,
                    a.pos.z + Math.sin(ang) * a.radius, 1, 0, 0.02, 0, 0);
        }
        int m = Math.max(12, n / 2);
        for (int k = 0; k < m; k++) {
            double ang = Math.PI * 2 * k / m;
            a.level.sendParticles(a.dust, a.pos.x + Math.cos(ang) * a.radius * 0.55, a.pos.y + 0.15,
                    a.pos.z + Math.sin(ang) * a.radius * 0.55, 1, 0, 0.01, 0, 0);
        }
        a.level.sendParticles(ParticleTypes.WITCH, a.pos.x, a.pos.y + 0.2, a.pos.z, 3, a.radius * 0.4, 0, a.radius * 0.4, 0);
    }
}
