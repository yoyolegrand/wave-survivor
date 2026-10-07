package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.skill.BossSkill;
import com.wavesurvivor.horde.skill.DelayedActionScheduler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * potion_rain : le boss vise le joueur et tire une rafale de N splash potions en lobe.
 * Chaque potion :
 *   - Contient un effet random tiré parmi potionRainEffects (défaut : liste négative sensée)
 *   - Est lancée avec un arc vertical (vy fort) pour retomber en mortier
 *   - A une dispersion horizontale aléatoire autour de la cible
 * Les tirs sont espacés de potionRainBurstInterval ticks pour un effet "vague".
 */
public class SkillPotionRain extends BossSkill {

    private static final Random RNG = new Random();

    /** Effets par défaut si aucun n'est configuré (les plus visuellement drôles). */
    private static final List<MobEffect> DEFAULT_EFFECTS = List.of(
            MobEffects.POISON,
            MobEffects.MOVEMENT_SLOWDOWN,
            MobEffects.WEAKNESS,
            MobEffects.CONFUSION,       // Nausea
            MobEffects.BLINDNESS,
            MobEffects.LEVITATION,
            MobEffects.DIG_SLOWDOWN     // Mining Fatigue
    );

    public SkillPotionRain(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(60, config.potionRainCooldown);
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        int totalPotions = Math.max(1, config.potionRainPotionCount);
        int interval = Math.max(1, config.potionRainBurstInterval);
        final ServerLevel finalLevel = level;
        final LivingEntity finalBoss = boss;
        final LivingEntity finalTarget = target;

        // Résout la liste d'effets à utiliser (config ou fallback)
        List<MobEffect> effects = resolveEffects(config.potionRainEffects);

        // Son initial (rafale annoncée)
        SoundEvent initialSound = resolveSound(config.potionRainSound, SoundEvents.WITCH_CELEBRATE);
        level.playSound(null, boss.blockPosition(), initialSound, SoundSource.HOSTILE, 2.0f, 0.9f);

        // Schedule chaque potion à intervalle fixe. La position du joueur est re-lue à chaque tir
        // (finalTarget.position() est évalué au moment du fire, pas au moment du schedule).
        for (int i = 0; i < totalPotions; i++) {
            final int shotIdx = i;
            DelayedActionScheduler.schedule(level.getServer(), i * interval, () -> {
                if (finalBoss.isRemoved() || !finalBoss.isAlive()) return;
                if (finalTarget == null || finalTarget.isRemoved() || !finalTarget.isAlive()) return;
                fireOnePotion(finalLevel, finalBoss, finalTarget.position(), effects, shotIdx, totalPotions);
            }, "potion_rain shot " + i);
        }

        WaveSurvivorMod.LOGGER.info("[Skill potion_rain] '{}' -> rafale de {} potions vers '{}' (interval {}t, tracking)",
                boss.getCustomName() != null ? boss.getCustomName().getString() : boss.getType().toString(),
                totalPotions, target.getName().getString(), interval);
    }

    private void fireOnePotion(ServerLevel level, LivingEntity boss, Vec3 targetPos,
                                List<MobEffect> effects, int shotIdx, int totalShots) {
        // Position de spawn : depuis les yeux du boss
        Vec3 spawn = boss.getEyePosition();

        // Cible dispersée : autour de targetPos avec ±dispersion
        double disp = Math.max(0.1, config.potionRainDispersion);
        double tx = targetPos.x + (RNG.nextDouble() - 0.5) * 2 * disp;
        double tz = targetPos.z + (RNG.nextDouble() - 0.5) * 2 * disp;

        // Delta horizontal vers la cible
        double dx = tx - spawn.x;
        double dz = tz - spawn.z;
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        if (horizDist < 0.1) return;

        // Trajectoire parabolique propre :
        //  - vy = arcHeight (contrôle la hauteur du lobe)
        //  - flightTicks estimés par la parabole (temps pour revenir à la même altitude)
        //  - vx/vz calculés pour que la potion atterrisse pile à la cible en flightTicks
        double vy = Math.max(0.1, config.potionRainArcHeight);
        double gravity = 0.05; // approximation ThrownPotion en 1.20.1
        double flightTicks = (2.0 * vy) / gravity;

        double speedMult = Math.max(0.1, config.potionRainProjectileSpeed);
        double vx = (dx / flightTicks) * speedMult;
        double vz = (dz / flightTicks) * speedMult;

        // Choisit un effet random
        MobEffect chosen = effects.get(RNG.nextInt(effects.size()));

        // Crée le splash potion avec l'effet custom
        ItemStack potionItem = new ItemStack(Items.SPLASH_POTION);
        MobEffectInstance instance = new MobEffectInstance(chosen,
                Math.max(20, config.potionRainEffectDuration),
                Math.max(0, config.potionRainEffectAmplifier));
        PotionUtils.setCustomEffects(potionItem, List.of(instance));

        // Spawn du ThrownPotion
        ThrownPotion pot = new ThrownPotion(level, boss);
        pot.setPos(spawn.x, spawn.y, spawn.z);
        pot.setItem(potionItem);
        pot.setDeltaMovement(vx, vy, vz);
        level.addFreshEntity(pot);

        // Petit son de tir répété (subtil, pas de spam)
        if (shotIdx % 4 == 0) {
            level.playSound(null, boss.blockPosition(),
                    SoundEvents.WITCH_THROW, SoundSource.HOSTILE, 0.8f, 0.9f + RNG.nextFloat() * 0.3f);
        }
    }

    private static List<MobEffect> resolveEffects(List<String> ids) {
        if (ids == null || ids.isEmpty()) return DEFAULT_EFFECTS;
        List<MobEffect> result = new ArrayList<>();
        for (String id : ids) {
            if (id == null || id.isBlank()) continue;
            try {
                MobEffect e = BuiltInRegistries.MOB_EFFECT.get(new ResourceLocation(id));
                if (e != null) result.add(e);
            } catch (Exception ignore) {}
        }
        return result.isEmpty() ? DEFAULT_EFFECTS : result;
    }

    private static SoundEvent resolveSound(String id, SoundEvent fallback) {
        if (id == null || id.isBlank()) return fallback;
        try {
            SoundEvent snd = BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation(id));
            return snd != null ? snd : fallback;
        } catch (Exception ignore) {}
        return fallback;
    }
}
