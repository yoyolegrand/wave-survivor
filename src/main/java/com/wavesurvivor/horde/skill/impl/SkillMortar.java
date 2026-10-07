package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.skill.BossSkill;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.phys.Vec3;

/**
 * mortar : le boss tire un projectile arqué (défaut : LargeFireball) sur la cible.
 * mortarArc contrôle l'inclinaison verticale du tir.
 */
public class SkillMortar extends BossSkill {

    public SkillMortar(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(20, config.mortarCooldown);
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        Vec3 bossEye = boss.getEyePosition();
        Vec3 targetPos = target.getEyePosition();
        Vec3 diff = targetPos.subtract(bossEye);
        double horizDist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        if (horizDist < 0.1) return;

        // Direction horizontale normalisée
        Vec3 horizDir = new Vec3(diff.x, 0, diff.z).normalize();

        // Vitesse initiale : mortarSpeed contrôle la vitesse (défaut 0.08 -> multiplié par 15 = 1.2 b/t)
        double horizSpeed = Math.max(0.5, config.mortarSpeed) * 15;

        // Composante verticale (arc) : mortarArc contrôle l'inclinaison initiale (défaut 0.8 -> vy 1.2)
        double vy = Math.max(0.1, config.mortarArc) * 1.5;

        double vx = horizDir.x * horizSpeed;
        double vz = horizDir.z * horizSpeed;

        int explosionPower = Math.max(1, (int) Math.round(config.mortarExplosionPower));
        boolean isSmall = "minecraft:small_fireball".equalsIgnoreCase(config.mortarProjectileType);

        // Le constructeur fireball prend l'ACCELERATION (pas la vitesse) ; on donne une petite accel
        // dans la même direction pour maintenir la trajectoire, puis on set la vitesse INITIALE
        // via setDeltaMovement pour que le projectile parte immédiatement vite.
        if (isSmall) {
            SmallFireball fb = new SmallFireball(level, boss, vx * 0.1, vy * 0.1, vz * 0.1);
            fb.setPos(bossEye.x, bossEye.y, bossEye.z);
            fb.setDeltaMovement(vx, vy, vz);
            fb.addTag(MortarExplosionHandler.MORTAR_TAG);
            level.addFreshEntity(fb);
        } else {
            LargeFireball fb = new LargeFireball(level, boss, vx * 0.1, vy * 0.1, vz * 0.1, explosionPower);
            fb.setPos(bossEye.x, bossEye.y, bossEye.z);
            fb.setDeltaMovement(vx, vy, vz);
            fb.addTag(MortarExplosionHandler.MORTAR_TAG);
            level.addFreshEntity(fb);
        }

        playSound(level, boss, config.mortarSound);
        WaveSurvivorMod.LOGGER.debug("[Skill mortar] '{}' tire vers {} (dist {}, horizSpeed {}, vy {})",
                boss.getCustomName() != null ? boss.getCustomName().getString() : boss.getType(),
                target.getName().getString(), (int) horizDist, String.format("%.2f", horizSpeed), String.format("%.2f", vy));
    }

    private void playSound(ServerLevel level, LivingEntity from, String soundId) {
        if (soundId == null || soundId.isBlank()) return;
        try {
            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation(soundId));
            if (sound != null) {
                level.playSound(null, from.blockPosition(), sound, SoundSource.HOSTILE, 1.0f, 1.0f);
            }
        } catch (Exception ignore) {}
    }
}
