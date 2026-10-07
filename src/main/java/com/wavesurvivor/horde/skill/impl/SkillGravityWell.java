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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/**
 * gravity_well (Puits d'éther / gravitationnel) : un vortex s'ouvre SOUS la cible. Pendant gravityWellDuration ticks,
 * les joueurs dans gravityWellRadius sont ATTIRÉS vers le centre ; au cœur (≤ 1,5 bloc) ils subissent
 * gravityWellDamage par seconde. Fin : IMPLOSION (onde de choc) qui projette en l'air ceux qui sont près du centre.
 *
 * Mise en scène : bras en spirale qui se resserrent, traits de lumière réellement aspirés vers le centre,
 * colonne tourbillonnante, bourdonnement qui monte ; implosion = boom sonique + flash + onde de choc au sol.
 */
public class SkillGravityWell extends BossSkill {

    private static final DustParticleOptions PURPLE = new DustParticleOptions(new Vector3f(0.75f, 0.2f, 1.0f), 1.8f);
    private static final DustParticleOptions DEEP = new DustParticleOptions(new Vector3f(0.35f, 0.05f, 0.6f), 2.2f);

    public SkillGravityWell(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(60, config.gravityWellCooldown);
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        if (target == null || !target.isAlive()) return;
        double range = Math.max(4, config.gravityWellRange);
        if (boss.distanceToSqr(target) > range * range) return;

        Vec3 c = target.position();
        BlockPos cp = BlockPos.containing(c);
        double radius = Math.max(2, config.gravityWellRadius);
        int duration = Math.max(20, config.gravityWellDuration);

        // ─── Ouverture ───
        level.playSound(null, cp, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 2.0f, 0.8f);
        level.playSound(null, cp, SoundEvents.PORTAL_TRIGGER, SoundSource.HOSTILE, 1.2f, 1.4f);
        NecroTracker.playSound(level, cp, config.gravityWellSound, 0.8f);
        level.sendParticles(ParticleTypes.FLASH, c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y + 0.5, c.z, 60, radius * 0.4, 0.3, radius * 0.4, 0.2);
        if (config.gravityWellMessage != null && !config.gravityWellMessage.isBlank()) {
            for (ServerPlayer p : players(level, c, radius + 3)) p.displayClientMessage(Component.literal(config.gravityWellMessage), true);
        }

        for (int t = 0; t < duration; t++) {
            final int tick = t;
            DelayedActionScheduler.schedule(level.getServer(), t, () -> {
                double progress = (double) tick / duration;

                // 4 bras en spirale qui tournent et se resserrent
                if (tick % 2 == 0) {
                    for (int arm = 0; arm < 4; arm++) {
                        for (int s = 0; s < 6; s++) {
                            double f = s / 6.0;                                   // 0 = centre, 1 = bord
                            double r = radius * f * (1.0 - progress * 0.35);
                            double a = tick * 0.25 + arm * (Math.PI / 2) + f * 3.2; // enroulement
                            level.sendParticles(s % 2 == 0 ? PURPLE : DEEP, c.x + Math.cos(a) * r, c.y + 0.15, c.z + Math.sin(a) * r,
                                    1, 0, 0, 0, 0);
                        }
                    }
                }
                // Traits de lumière ASPIRÉS vers le centre (particules avec vitesse)
                for (int k = 0; k < 3; k++) {
                    double a = Math.random() * Math.PI * 2;
                    double sx = c.x + Math.cos(a) * radius, sz = c.z + Math.sin(a) * radius, sy = c.y + 0.3 + Math.random() * 1.2;
                    Vec3 v = new Vec3(c.x - sx, (c.y + 0.6) - sy, c.z - sz).normalize();
                    level.sendParticles(ParticleTypes.END_ROD, sx, sy, sz, 0, v.x, v.y, v.z, 0.25);
                    level.sendParticles(ParticleTypes.DRAGON_BREATH, sx, sy, sz, 0, v.x, v.y, v.z, 0.18);
                }
                // Colonne tourbillonnante au centre
                if (tick % 2 == 0) {
                    for (int k = 0; k < 3; k++) {
                        double a = tick * 0.5 + k * (Math.PI * 2 / 3);
                        double h = (tick % 20) / 20.0 * 5 + k * 0.4;
                        level.sendParticles(ParticleTypes.PORTAL, c.x + Math.cos(a) * 0.5, c.y + h, c.z + Math.sin(a) * 0.5, 2, 0.05, 0.1, 0.05, 0.3);
                    }
                }
                // Bord de la zone
                if (tick % 6 == 0) {
                    int n = (int) Math.max(20, radius * 8);
                    for (int k = 0; k < n; k++) {
                        double a = Math.PI * 2 * k / n;
                        level.sendParticles(ParticleTypes.WITCH, c.x + Math.cos(a) * radius, c.y + 0.2, c.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
                    }
                }
                // Bourdonnement qui monte
                if (tick % 10 == 0) {
                    level.playSound(null, cp, SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 1.8f, (float) (0.5 + progress * 1.0));
                    level.playSound(null, cp, SoundEvents.PORTAL_AMBIENT, SoundSource.HOSTILE, 1.0f, (float) (0.7 + progress * 0.6));
                }

                // Attraction + dégâts au cœur
                for (ServerPlayer p : players(level, c, radius)) {
                    Vec3 to = new Vec3(c.x - p.getX(), 0, c.z - p.getZ());
                    double dist = to.length();
                    if (dist > radius) continue;
                    if (dist > 0.4 && !com.wavesurvivor.item.RelicEffects.immovable(p)) {
                        Vec3 pull = to.normalize().scale(Math.max(0.02, config.gravityWellStrength));
                        p.setDeltaMovement(p.getDeltaMovement().add(pull.x, 0, pull.z));
                        p.hurtMarked = true;
                    }
                    if (tick % 10 == 0) p.displayClientMessage(Component.literal(com.wavesurvivor.i18n.WSLang.t("§5§l⚠ Le Puits d'éther t'aspire — cours !")), true);
                    if (dist <= 1.5 && tick % 20 == 0 && config.gravityWellDamage > 0) {
                        p.hurt(boss.isAlive() ? level.damageSources().indirectMagic(boss, boss) : level.damageSources().magic(),
                                (float) config.gravityWellDamage);
                    }
                }
            }, "gravity well");
        }

        // ─── Implosion finale ───
        DelayedActionScheduler.schedule(level.getServer(), duration, () -> {
            level.playSound(null, cp, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 2.5f, 0.9f);
            level.playSound(null, cp, SoundEvents.ENDER_EYE_DEATH, SoundSource.HOSTILE, 1.5f, 0.5f);
            level.sendParticles(ParticleTypes.FLASH, c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.PORTAL, c.x, c.y + 0.5, c.z, 80, 0.4, 0.4, 0.4, 1.4);
            // Onde de choc au sol (traits de lumière projetés vers l'extérieur)
            int n = 48;
            for (int k = 0; k < n; k++) {
                double a = Math.PI * 2 * k / n;
                double dx = Math.cos(a), dz = Math.sin(a);
                level.sendParticles(ParticleTypes.END_ROD, c.x + dx * 0.5, c.y + 0.3, c.z + dz * 0.5, 0, dx, 0.02, dz, 0.6);
                level.sendParticles(PURPLE, c.x + dx * radius * 0.5, c.y + 0.2, c.z + dz * radius * 0.5, 1, 0, 0, 0, 0);
            }
            if (!config.gravityWellImplosion) return;
            for (ServerPlayer p : players(level, c, 2.5)) {
                if (com.wavesurvivor.item.RelicEffects.immovable(p)) continue;
                p.setDeltaMovement(p.getDeltaMovement().x, 1.0, p.getDeltaMovement().z);
                p.hurtMarked = true;
            }
        }, "gravity well implosion");
    }

    private static List<ServerPlayer> players(ServerLevel level, Vec3 c, double r) {
        return level.getEntitiesOfClass(ServerPlayer.class, new AABB(c.x - r, c.y - 2, c.z - r, c.x + r, c.y + 4, c.z + r),
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator());
    }
}
