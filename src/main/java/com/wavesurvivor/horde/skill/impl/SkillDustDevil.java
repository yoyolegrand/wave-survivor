package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.quake.QuakeManager;
import com.wavesurvivor.horde.skill.BossSkill;
import com.wavesurvivor.horde.skill.DelayedActionScheduler;
import com.wavesurvivor.horde.skill.NecroTracker;
import com.wavesurvivor.horde.skill.SandFx;
import com.wavesurvivor.item.RelicEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Random;

/**
 * dust_devil (Tourbillon de sable) : un tourbillon part du boss et traque le joueur le plus proche pendant
 * dustDevilDuration ticks. Les joueurs pris dedans sont attirés vers son centre, soulevés et blessés chaque seconde.
 * La Ceinture de Plomb (relique anti-poussée) les en protège comme pour toute compétence qui attire.
 */
public class SkillDustDevil extends BossSkill {

    private static final Random RNG = new Random();

    public SkillDustDevil(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(60, config.dustDevilCooldown);
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        double range = Math.max(6, config.dustDevilRange);
        List<ServerPlayer> near = level.getEntitiesOfClass(ServerPlayer.class, boss.getBoundingBox().inflate(range),
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator());
        if (near.isEmpty()) return;
        double[] pos = {boss.getX(), boss.getZ()};
        if (config.dustDevilMessage != null && !config.dustDevilMessage.isBlank()) {
            for (ServerPlayer p : near) p.displayClientMessage(Component.literal(config.dustDevilMessage), true);
        }
        NecroTracker.playSound(level, boss.blockPosition(), config.dustDevilSound, 0.7f);
        step(level, level.getServer(), pos, 0, range);
    }

    /** Toutes les 2 ticks : le tourbillon avance vers le joueur le plus proche, tourne, attire, soulève et blesse. */
    private void step(ServerLevel level, MinecraftServer srv, double[] pos, int elapsed, double range) {
        DelayedActionScheduler.schedule(srv, 2, () -> {
            int t = elapsed + 2;
            if (t >= config.dustDevilDuration) return;
            double r = Math.max(1.5, config.dustDevilRadius);
            // Chasse : le joueur vivant le plus proche
            ServerPlayer nearest = null;
            double best = Double.MAX_VALUE;
            for (ServerPlayer p : level.players()) {
                if (!p.isAlive() || p.isSpectator()) continue;
                double dx = p.getX() - pos[0], dz = p.getZ() - pos[1], d = dx * dx + dz * dz;
                if (d < best && d <= (range * 2) * (range * 2)) { best = d; nearest = p; }
            }
            if (nearest != null) {
                double dx = nearest.getX() - pos[0], dz = nearest.getZ() - pos[1], len = Math.sqrt(dx * dx + dz * dz);
                if (len > 0.5) {
                    pos[0] += dx / len * config.dustDevilSpeed * 2;
                    pos[1] += dz / len * config.dustDevilSpeed * 2;
                }
            }
            int gy = QuakeManager.ground(level, pos[0], pos[1]);
            // Spirale de sable
            for (int h = 0; h < 8; h++) {
                double a = t * 0.7 + h * 0.9, rr = 0.5 + h * 0.3;
                level.sendParticles(SandFx.parse(config.dustDevilParticle, SandFx.SAND), pos[0] + Math.cos(a) * rr, gy + h * 0.55, pos[1] + Math.sin(a) * rr, 3, 0.1, 0.1, 0.1, 0.02);
            }
            if (t % 10 == 0) level.sendParticles(SandFx.parse(config.dustDevilParticle, ParticleTypes.CLOUD), pos[0], gy + 2.5, pos[1], 6, 0.8, 1.5, 0.8, 0.02);
            for (ServerPlayer p : level.players()) {
                if (!p.isAlive() || p.isSpectator() || p.getY() < gy - 1 || p.getY() > gy + 9) continue;
                double dx = pos[0] - p.getX(), dz = pos[1] - p.getZ();
                double d2 = dx * dx + dz * dz;
                if (d2 > r * r) continue;
                if (!RelicEffects.immovable(p)) {
                    double len = Math.max(0.3, Math.sqrt(d2));
                    double vy = Math.min(0.6, p.getDeltaMovement().y + config.dustDevilLift);
                    p.setDeltaMovement(p.getDeltaMovement().x + dx / len * config.dustDevilPull, vy, p.getDeltaMovement().z + dz / len * config.dustDevilPull);
                    p.hurtMarked = true;
                    p.fallDistance = 0;
                }
                if (t % 20 == 0 && config.dustDevilDamage > 0) p.hurt(level.damageSources().generic(), (float) config.dustDevilDamage);
            }
            step(level, srv, pos, t, range);
        }, "dust_devil");
    }
}
