package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.skill.BossSkill;
import com.wavesurvivor.horde.skill.DelayedActionScheduler;
import com.wavesurvivor.horde.skill.NecroTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ice_spikes (Pointes de glace) : des lignes de pointes de glace jaillissent du boss vers sa cible, en éventail.
 * La zone clignote d'abord (iceSpikesDelay ticks), puis les pointes frappent : dégâts, léger envol et Lenteur
 * pour les joueurs restés sur une ligne. Se règle comme les autres compétences dans l'éditeur.
 */
public class SkillIceSpikes extends BossSkill {

    private static final DustParticleOptions WARN = new DustParticleOptions(new Vector3f(0.45f, 0.85f, 1.0f), 1.6f);

    public SkillIceSpikes(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(40, config.iceSpikesCooldown);
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        if (target == null || !target.isAlive()) return;
        double range = Math.max(4, config.iceSpikesRange);
        if (boss.distanceToSqr(target) > range * range) return;

        Vec3 from = boss.position();
        Vec3 aim = new Vec3(target.getX() - from.x, 0, target.getZ() - from.z);
        if (aim.length() < 0.01) return;
        aim = aim.normalize();

        int lines = Math.max(1, Math.min(8, config.iceSpikesLines));
        double length = Math.max(3, config.iceSpikesLength);
        double half = Math.max(0.5, config.iceSpikesWidth) / 2.0 + 0.6;
        int delay = Math.max(5, config.iceSpikesDelay);

        // Directions de chaque ligne (éventail d'environ 16° entre deux lignes)
        List<Vec3> dirs = new ArrayList<>();
        for (int i = 0; i < lines; i++) {
            double ang = (i - (lines - 1) / 2.0) * 0.28;
            double cos = Math.cos(ang), sin = Math.sin(ang);
            dirs.add(new Vec3(aim.x * cos - aim.z * sin, 0, aim.x * sin + aim.z * cos));
        }

        NecroTracker.playSound(level, boss.blockPosition(), "minecraft:block.glass.break", 0.6f);
        if (config.iceSpikesMessage != null && !config.iceSpikesMessage.isBlank()) {
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, boss.getBoundingBox().inflate(range))) {
                p.displayClientMessage(Component.literal(config.iceSpikesMessage), true);
            }
        }

        // Avertissement : des dalles de givre marquent chaque ligne, et la ligne clignote
        List<net.minecraft.world.entity.Display.BlockDisplay> tiles = new ArrayList<>();
        for (Vec3 d : dirs) {
            for (double s = 1.5; s <= length; s += 1.0) {
                double x = from.x + d.x * s, z = from.z + d.z * s;
                tiles.add(com.wavesurvivor.horde.skill.IceFx.floorTile(level, x, groundY(level, x, z), z, 0.85f,
                        com.wavesurvivor.horde.skill.IceFx.FROST_GLASS));
            }
        }
        for (int t = 0; t < delay; t += 3) {
            DelayedActionScheduler.schedule(level.getServer(), t, () -> {
                for (Vec3 d : dirs) {
                    for (double s = 1.5; s <= length; s += 1.0) {
                        level.sendParticles(WARN, from.x + d.x * s, groundY(level, from.x + d.x * s, from.z + d.z * s) + 0.15,
                                from.z + d.z * s, 1, 0.1, 0, 0.1, 0);
                    }
                }
            }, "ice_spikes warning");
        }

        // Frappe : une vague de pics de glace jaillit du boss jusqu'au bout de chaque ligne
        DelayedActionScheduler.schedule(level.getServer(), delay, () -> {
            for (net.minecraft.world.entity.Display.BlockDisplay tile : tiles) com.wavesurvivor.horde.skill.IceFx.remove(tile);
            Set<ServerPlayer> hit = new HashSet<>();
            java.util.Random rng = new java.util.Random();
            for (Vec3 d : dirs) {
                float lean = (float) Math.atan2(d.z, d.x);
                int step = 0;
                for (double s = 1.5; s <= length; s += 1.1, step++) {
                    double x = from.x + d.x * s, z = from.z + d.z * s, y = groundY(level, x, z);
                    float h = 1.3f + rng.nextFloat() * 0.9f + (float) (s / length) * 0.4f;   // plus hauts vers le bout
                    com.wavesurvivor.horde.skill.IceFx.spike(level, level.getServer(), x, y, z, h, 0.75f,
                            0.2f + rng.nextFloat() * 0.15f, lean + (rng.nextFloat() - 0.5f) * 0.4f, step, 12);
                    final double fx = x, fy = y, fz = z;
                    DelayedActionScheduler.schedule(level.getServer(), step, () -> {
                        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()),
                                fx, fy + 0.4, fz, 6, 0.25, 0.3, 0.25, 0.08);
                        level.sendParticles(ParticleTypes.SNOWFLAKE, fx, fy + 1.0, fz, 3, 0.2, 0.5, 0.2, 0.02);
                    }, "ice_spikes burst");
                }
            }
            NecroTracker.playSound(level, BlockPos.containing(from), config.iceSpikesSound, 1.0f);
            for (ServerPlayer p : level.players()) {
                if (!p.isAlive() || p.isCreative() || p.isSpectator() || hit.contains(p)) continue;
                for (Vec3 d : dirs) {
                    Vec3 v = new Vec3(p.getX() - from.x, 0, p.getZ() - from.z);
                    double along = v.dot(d);
                    if (along < 0.5 || along > length) continue;
                    double perp = v.subtract(d.scale(along)).length();
                    if (perp > half || Math.abs(p.getY() - from.y) > 4) continue;
                    hit.add(p);
                    p.hurt(boss.isAlive() ? level.damageSources().indirectMagic(boss, boss) : level.damageSources().magic(),
                            (float) Math.max(0, config.iceSpikesDamage));
                    p.setDeltaMovement(p.getDeltaMovement().add(0, 0.55, 0));
                    p.hurtMarked = true;
                    if (config.iceSpikesSlowSeconds > 0) {
                        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, config.iceSpikesSlowSeconds * 20, 1, false, true));
                    }
                    break;
                }
            }
        }, "ice_spikes strike");
    }

    private static double groundY(ServerLevel level, double x, double z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
    }
}
