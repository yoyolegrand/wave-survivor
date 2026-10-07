package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.skill.BossSkill;
import com.wavesurvivor.horde.skill.DelayedActionScheduler;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * telegraph — ATTAQUE TÉLÉGRAPHIÉE (générique, réutilisable par n'importe quel monstre).
 * telegraphCount zones apparaissent (d'abord sous des joueurs, puis au hasard autour du monstre), clignotent pendant
 * telegraphWarning, puis frappent : dégâts, projection et effet sur les joueurs restés dedans.
 * Styles : slam (frappe au sol, orange), geyser (jet d'eau, bleu), fangs (crocs d'évocateur, violet).
 */
public class SkillTelegraph extends BossSkill {

    private static final Random RNG = new Random();

    public SkillTelegraph(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(40, config.telegraphCooldown);
    }

    @Override
    public boolean requiresTarget() {
        return true;
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        String style = config.telegraphStyle == null ? "slam" : config.telegraphStyle;
        double r = Math.max(1, config.telegraphRadius);
        int count = Math.max(1, Math.min(10, config.telegraphCount));
        int warn = Math.max(10, config.telegraphWarning);

        // Zones : sous les joueurs d'abord, puis au hasard autour du monstre
        List<Vec3> zones = new ArrayList<>();
        List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, boss.getBoundingBox().inflate(28),
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator());
        for (ServerPlayer p : players) if (zones.size() < count) zones.add(ground(level, p.getX(), p.getZ()));
        while (zones.size() < count) {
            double a = RNG.nextDouble() * Math.PI * 2, d = 3 + RNG.nextDouble() * 6;
            zones.add(ground(level, boss.getX() + Math.cos(a) * d, boss.getZ() + Math.sin(a) * d));
        }

        DustParticleOptions dust = new DustParticleOptions(switch (style) {
            case "geyser" -> new Vector3f(0.2f, 0.6f, 1.0f);
            case "fangs" -> new Vector3f(0.7f, 0.2f, 0.9f);
            default -> new Vector3f(1.0f, 0.45f, 0.1f);
        }, 1.8f);
        DustParticleOptions red = new DustParticleOptions(new Vector3f(1f, 0.1f, 0.1f), 2.0f);

        // Avertissement : cercles qui clignotent (rouges juste avant la frappe)
        for (int t = 0; t < warn; t += 3) {
            final boolean urgent = warn - t <= 10;
            DelayedActionScheduler.schedule(level.getServer(), t, () -> {
                for (Vec3 z : zones) ring(level, z, r, urgent ? red : dust);
            }, "telegraph warn");
        }
        level.playSound(null, boss.blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 1.5f, 0.8f);
        if (config.telegraphMessage != null && !config.telegraphMessage.isBlank()) {
            Component msg = Component.literal(config.telegraphMessage);
            for (ServerPlayer p : players) p.displayClientMessage(msg, true);
        }

        // Frappe
        DelayedActionScheduler.schedule(level.getServer(), warn, () -> {
            if (!boss.isAlive()) return;
            for (Vec3 z : zones) strike(boss, level, z, r, style);
        }, "telegraph strike");
    }

    private void strike(LivingEntity boss, ServerLevel level, Vec3 z, double r, String style) {
        switch (style) {
            case "geyser" -> {
                level.sendParticles(ParticleTypes.SPLASH, z.x, z.y + 0.2, z.z, 80, r * 0.5, 0.2, r * 0.5, 0.3);
                level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, z.x, z.y + 0.5, z.z, 60, r * 0.4, 1.5, r * 0.4, 0.4);
                level.playSound(null, net.minecraft.core.BlockPos.containing(z), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.HOSTILE, 1.5f, 0.7f);
            }
            case "fangs" -> {
                int n = (int) Math.max(6, r * 5);
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2 * i / n;
                    level.addFreshEntity(new EvokerFangs(level, z.x + Math.cos(a) * r * 0.7, z.y, z.z + Math.sin(a) * r * 0.7,
                            (float) a, 0, boss));
                }
                level.addFreshEntity(new EvokerFangs(level, z.x, z.y, z.z, 0f, 0, boss));
            }
            default -> {
                level.sendParticles(ParticleTypes.EXPLOSION, z.x, z.y + 0.3, z.z, 4, r * 0.4, 0.1, r * 0.4, 0);
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, z.x, z.y + 0.2, z.z, 20, r * 0.5, 0.1, r * 0.5, 0.02);
                level.playSound(null, net.minecraft.core.BlockPos.containing(z), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.2f, 0.8f);
            }
        }
        MobEffect eff = null;
        if (config.telegraphEffect != null && !config.telegraphEffect.isBlank()) {
            try { eff = BuiltInRegistries.MOB_EFFECT.getOptional(new ResourceLocation(config.telegraphEffect.trim())).orElse(null); } catch (Exception ignored) {}
        }
        AABB box = new AABB(z.x - r, z.y - 1, z.z - r, z.x + r, z.y + 3, z.z + r);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, box)) {
            if (p.isCreative() || p.isSpectator()) continue;
            double dx = p.getX() - z.x, dz = p.getZ() - z.z;
            if (dx * dx + dz * dz > r * r) continue;
            // Les crocs infligent eux-mêmes leurs dégâts : pas de double coup
            if (!"fangs".equals(style) && config.telegraphDamage > 0) p.hurt(level.damageSources().mobAttack(boss), (float) config.telegraphDamage);
            if (config.telegraphKnockUp > 0) {
                p.setDeltaMovement(p.getDeltaMovement().add(0, config.telegraphKnockUp, 0));
                p.hurtMarked = true;
            }
            if (eff != null) p.addEffect(new MobEffectInstance(eff, Math.max(10, config.telegraphEffectDuration), Math.max(0, config.telegraphEffectLevel - 1)));
        }
    }

    private static Vec3 ground(ServerLevel level, double x, double z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
        return new Vec3(x, y, z);
    }

    private static void ring(ServerLevel level, Vec3 c, double r, DustParticleOptions dust) {
        int n = (int) Math.max(16, r * 10);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            level.sendParticles(dust, c.x + Math.cos(a) * r, c.y + 0.15, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
        level.sendParticles(dust, c.x, c.y + 0.15, c.z, 3, r * 0.4, 0, r * 0.4, 0);
    }
}
