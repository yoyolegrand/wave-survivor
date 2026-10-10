package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.skill.BossSkill;
import com.wavesurvivor.horde.skill.DelayedActionScheduler;
import com.wavesurvivor.horde.skill.NecroTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;

/**
 * frost_nova (Onde de givre) : un anneau de gel part du boss et s'étend jusqu'à frostNovaRadius. Les joueurs qu'il
 * traverse AU SOL subissent des dégâts et sont immobilisés quelques secondes ; sauter par-dessus l'anneau l'évite.
 */
public class SkillFrostNova extends BossSkill {

    private static final DustParticleOptions ICE = new DustParticleOptions(new Vector3f(0.55f, 0.9f, 1.0f), 1.7f);

    public SkillFrostNova(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(60, config.frostNovaCooldown);
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        Vec3 c = boss.position();
        double maxR = Math.max(4, config.frostNovaRadius);
        double speed = Math.max(0.2, config.frostNovaSpeed);
        int steps = (int) Math.ceil(maxR / speed);
        Set<ServerPlayer> hit = new HashSet<>();

        NecroTracker.playSound(level, BlockPos.containing(c), config.frostNovaSound, 1.2f);
        if (config.frostNovaMessage != null && !config.frostNovaMessage.isBlank()) {
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, boss.getBoundingBox().inflate(maxR))) {
                p.displayClientMessage(Component.literal(config.frostNovaMessage), true);
            }
        }

        for (int step = 1; step <= steps; step++) {
            final int st = step;
            DelayedActionScheduler.schedule(level.getServer(), step, () -> {
                double r = st * speed;
                // Anneau visible
                int n = (int) Math.max(24, r * 7);
                for (int k = 0; k < n; k++) {
                    double a = Math.PI * 2 * k / n;
                    double x = c.x + Math.cos(a) * r, z = c.z + Math.sin(a) * r;
                    level.sendParticles(k % 2 == 0 ? ICE : (net.minecraft.core.particles.ParticleOptions) ParticleTypes.SNOWFLAKE,
                            x, c.y + 0.25, z, 1, 0, 0.05, 0, 0.01);
                }
                // Joueurs au sol traversés par l'anneau
                for (ServerPlayer p : level.players()) {
                    if (!p.isAlive() || p.isCreative() || p.isSpectator() || hit.contains(p)) continue;
                    double d = Math.hypot(p.getX() - c.x, p.getZ() - c.z);
                    if (Math.abs(d - r) > speed + 0.7) continue;
                    if (p.getY() - c.y >= 1.0) continue; // sauté par-dessus : évité
                    hit.add(p);
                    p.hurt(boss.isAlive() ? level.damageSources().indirectMagic(boss, boss) : level.damageSources().magic(),
                            (float) Math.max(0, config.frostNovaDamage));
                    int rootTicks = Math.max(10, config.frostNovaRootSeconds * 20);
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, rootTicks, 6, false, true));
                    p.addEffect(new MobEffectInstance(MobEffects.JUMP, rootTicks, 128, false, false));
                    p.setTicksFrozen(Math.min(p.getTicksRequiredToFreeze() - 1, p.getTicksFrozen() + rootTicks));
                    level.sendParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 1, p.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
                }
            }, "frost_nova");
        }
    }
}
