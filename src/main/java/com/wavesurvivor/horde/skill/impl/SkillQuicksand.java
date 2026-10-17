package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.skill.BossSkill;
import com.wavesurvivor.horde.skill.DelayedActionScheduler;
import com.wavesurvivor.horde.skill.IceFx;
import com.wavesurvivor.horde.skill.NecroTracker;
import com.wavesurvivor.horde.skill.SandFx;
import com.wavesurvivor.item.RelicEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * quicksand (Sables mouvants) : le sol se dérobe sous quelques joueurs. Dans la zone (un disque de sable), ils sont
 * ralentis, attirés vers le centre, ne peuvent plus sauter et subissent des dégâts par seconde, jusqu'à en sortir ou
 * jusqu'à la fin (quicksandDuration ticks). La relique d'immunité (Voile du Désert par défaut) les en protège.
 */
public class SkillQuicksand extends BossSkill {

    public SkillQuicksand(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(60, config.quicksandCooldown);
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        double range = Math.max(4, config.quicksandRange);
        List<ServerPlayer> near = new ArrayList<>(level.getEntitiesOfClass(ServerPlayer.class, boss.getBoundingBox().inflate(range),
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator()));
        if (near.isEmpty()) return;
        Collections.shuffle(near);
        // Le joueur visé en premier
        if (target instanceof ServerPlayer tp && near.remove(tp)) near.add(0, tp);
        MinecraftServer srv = level.getServer();
        double r = Math.max(1.5, config.quicksandRadius);
        int n = Math.max(1, Math.min(near.size(), config.quicksandCount));
        for (int i = 0; i < n; i++) {
            ServerPlayer p = near.get(i);
            if (SandFx.immune(p, config.quicksandImmuneItem)) continue;
            Vec3 c = p.position();
            double y = Math.floor(p.getY());
            Display.BlockDisplay disc = IceFx.spawn(level, c.x, y + 0.01, c.z, Blocks.SAND.defaultBlockState(),
                    new Quaternionf(), (float) (2 * r), 0.04f, (float) (2 * r), 0f);
            if (config.quicksandMessage != null && !config.quicksandMessage.isBlank()) {
                p.displayClientMessage(Component.literal(config.quicksandMessage), true);
            }
            step(level, srv, c.x, y, c.z, r, disc, 0);
        }
        NecroTracker.playSound(level, boss.blockPosition(), config.quicksandSound, 0.8f);
    }

    /** Toutes les 5 ticks : effets sur les joueurs dans la zone, jusqu'à la fin. */
    private void step(ServerLevel level, MinecraftServer srv, double cx, double cy, double cz, double r, Display.BlockDisplay disc, int elapsed) {
        DelayedActionScheduler.schedule(srv, 5, () -> {
            int t = elapsed + 5;
            if (t >= config.quicksandDuration) {
                IceFx.remove(disc);
                return;
            }
            level.sendParticles(SandFx.SAND, cx, cy + 0.2, cz, 10, r * 0.5, 0.1, r * 0.5, 0.05);
            for (ServerPlayer p : level.players()) {
                if (!p.isAlive() || p.isSpectator() || Math.abs(p.getY() - cy) > 2.5) continue;
                double dx = cx - p.getX(), dz = cz - p.getZ();
                if (dx * dx + dz * dz > r * r) continue;
                if (SandFx.immune(p, config.quicksandImmuneItem)) continue;
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, Math.max(0, config.quicksandSlowAmplifier), false, true));
                p.addEffect(new MobEffectInstance(MobEffects.JUMP, 12, 128, false, false)); // plus de saut
                double len = Math.sqrt(dx * dx + dz * dz);
                if (len > 0.4 && config.quicksandPull > 0 && !RelicEffects.immovable(p)) {
                    p.setDeltaMovement(p.getDeltaMovement().add(dx / len * config.quicksandPull, 0, dz / len * config.quicksandPull));
                    p.hurtMarked = true;
                }
                if (t % 20 == 0 && config.quicksandDamage > 0) p.hurt(level.damageSources().generic(), (float) config.quicksandDamage);
            }
            step(level, srv, cx, cy, cz, r, disc, t);
        }, "quicksand");
    }
}
