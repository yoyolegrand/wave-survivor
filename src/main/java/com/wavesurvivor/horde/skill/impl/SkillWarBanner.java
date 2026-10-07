package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.entity.TotemEntity;
import com.wavesurvivor.horde.chaos.TotemAuraManager;
import com.wavesurvivor.horde.skill.BossSkill;
import com.wavesurvivor.horde.skill.DelayedActionScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * war_banner — BANNIÈRES DE GUERRE (signature du Seigneur du Bastion).
 * Plante warBannerCount bannières (totems « Fureur » : Force + Vitesse aux monstres dans leur rayon).
 * Tant qu'au moins une bannière tient debout, le boss gagne Résistance. Les joueurs doivent les détruire.
 */
public class SkillWarBanner extends BossSkill {

    public SkillWarBanner(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(100, config.warBannerCooldown);
    }

    @Override
    public boolean requiresTarget() {
        return true;
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        int count = Math.max(1, Math.min(6, config.warBannerCount));
        int life = Math.max(10, config.warBannerLifetime);
        double dist = Math.max(3, config.warBannerDistance);
        List<UUID> banners = new ArrayList<>();
        double offset = Math.random() * Math.PI * 2;
        for (int i = 0; i < count; i++) {
            double a = offset + Math.PI * 2 * i / count;
            BlockPos aim = BlockPos.containing(boss.getX() + Math.cos(a) * dist, boss.getY(), boss.getZ() + Math.sin(a) * dist);
            TotemEntity t = com.wavesurvivor.registry.ModEntities.TOTEM.get().create(level);
            if (t == null) continue;
            BlockPos at = com.wavesurvivor.horde.spawn.SpawnZone.pick(level, aim, 2, t.getType());
            t.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, (float) Math.toDegrees(a), 0);
            t.setup("fureur", "minecraft:yellow_banner", 0xFFAA00);
            t.setCustomName(Component.literal(com.wavesurvivor.i18n.WSLang.t("skill.war_banner.name")));
            t.setCustomNameVisible(true);
            if (!level.addFreshEntity(t)) continue;
            TotemAuraManager.register(t, "fureur", Math.max(3, config.warBannerRadius), life, "minecraft:gold_nugget", 4, "minecraft:piglin");
            banners.add(t.getUUID());
            level.sendParticles(ParticleTypes.FLAME, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 30, 0.3, 0.8, 0.3, 0.05);
        }
        if (banners.isEmpty()) return;
        level.playSound(null, boss.blockPosition(), SoundEvents.PIGLIN_BRUTE_ANGRY, SoundSource.HOSTILE, 2f, 0.7f);
        level.playSound(null, boss.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 3f, 1.1f);
        if (config.warBannerMessage != null && !config.warBannerMessage.isBlank()) {
            Component msg = Component.literal(config.warBannerMessage);
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, boss.getBoundingBox().inflate(48))) p.displayClientMessage(msg, true);
        }
        // Résistance du boss tant qu'une bannière tient
        keepResistance(boss, level, banners, life * 20);
    }

    private void keepResistance(LivingEntity boss, ServerLevel level, List<UUID> banners, int ticksLeft) {
        if (ticksLeft <= 0 || !boss.isAlive()) return;
        banners.removeIf(id -> { Entity e = level.getEntity(id); return e == null || !e.isAlive(); });
        if (banners.isEmpty()) return;
        int amp = Math.max(0, config.warBannerResistance - 1);
        boss.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 50, amp, false, true, true));
        level.sendParticles(ParticleTypes.WAX_ON, boss.getX(), boss.getY() + boss.getBbHeight() * 0.6, boss.getZ(), 6, 0.4, 0.4, 0.4, 0.02);
        DelayedActionScheduler.schedule(level.getServer(), 40, () -> keepResistance(boss, level, banners, ticksLeft - 40), "war banner");
    }
}
