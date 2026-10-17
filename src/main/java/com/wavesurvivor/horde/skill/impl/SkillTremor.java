package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.quake.QuakeManager;
import com.wavesurvivor.horde.skill.BossSkill;
import com.wavesurvivor.horde.skill.DelayedActionScheduler;
import com.wavesurvivor.horde.skill.NecroTracker;
import com.wavesurvivor.horde.skill.SandFx;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * tremor (Fracture en étoile) : le boss frappe le sol. Des tracés de poussière annoncent des fissures qui partent en
 * étoile (tremorLines, tremorLength blocs) ; après tremorWarning ticks elles s'ouvrent et blessent / soulèvent ceux
 * qui s'y trouvent. Entre deux fissures, on est à l'abri.
 */
public class SkillTremor extends BossSkill {

    private static final Random RNG = new Random();

    public SkillTremor(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(60, config.tremorCooldown);
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        Vec3 c = boss.position();
        int lines = Math.max(3, Math.min(24, config.tremorLines));
        int len = Math.max(4, Math.min(40, config.tremorLength));
        int warn = Math.max(10, config.tremorWarning);
        double off = RNG.nextDouble() * Math.PI * 2;
        MinecraftServer srv = level.getServer();

        NecroTracker.playSound(level, boss.blockPosition(), config.tremorSound, 0.7f);
        if (config.tremorMessage != null && !config.tremorMessage.isBlank()) {
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, boss.getBoundingBox().inflate(len + 8))) {
                p.displayClientMessage(Component.literal(config.tremorMessage), true);
            }
        }
        // Avertissement : poussière sur le tracé, deux fois (au début et à mi-chemin)
        for (int pass = 0; pass < 2; pass++) {
            DelayedActionScheduler.schedule(srv, pass * (warn / 2), () -> {
                for (int k = 0; k < lines; k++) {
                    double dir = off + k * Math.PI * 2 / lines;
                    for (int i = 1; i <= len; i++) {
                        double x = c.x + Math.cos(dir) * i, z = c.z + Math.sin(dir) * i;
                        level.sendParticles(SandFx.WARN, x, QuakeManager.ground(level, x, z) + 0.2, z, 1, 0.05, 0, 0.05, 0);
                    }
                }
            }, "tremor warn");
        }
        // Ouverture des fissures
        DelayedActionScheduler.schedule(srv, warn, () -> {
            for (int k = 0; k < lines; k++) {
                QuakeManager.crackLine(level, srv, c.x, c.z, off + k * Math.PI * 2 / lines, len, config.tremorDamage,
                        Blocks.BLACK_CONCRETE.defaultBlockState());
            }
        }, "tremor open");
    }
}
