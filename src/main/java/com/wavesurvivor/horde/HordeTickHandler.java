package com.wavesurvivor.horde;

import com.wavesurvivor.horde.boss.BossConfigTicker;
import com.wavesurvivor.horde.boss.BossManager;
import com.wavesurvivor.horde.boss.BossMinionTracker;
import com.wavesurvivor.horde.chaos.ChaosTracker;
import com.wavesurvivor.horde.skill.DelayedActionScheduler;
import com.wavesurvivor.horde.skill.GatlingScheduler;
import com.wavesurvivor.horde.skill.ProjectileTrailTracker;
import com.wavesurvivor.horde.skill.SkillManager;
import com.wavesurvivor.horde.skill.WebTrapTracker;
import com.wavesurvivor.horde.spawn.MobRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class HordeTickHandler {

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer srv = event.getServer();
        // « La Chute du Monolithe » en cours : la horde, les compétences et le Kingdom sont en pause
        if (com.wavesurvivor.altar.MonolithFall.active()) return;
        HordeManager.get().tick(srv);
        BossMinionTracker.tick(srv);
        BossConfigTicker.tick(srv);
        SkillManager.tick(srv);
        ProjectileTrailTracker.tick(srv);
        GatlingScheduler.tick(srv);
        WebTrapTracker.tick(srv);
        DelayedActionScheduler.tick(srv);
        ChaosTracker.tick(srv);
        BossManager.updateBars(srv);

        if (srv.getTickCount() % 20 == 0 && HordeManager.get().isRunning()) {
            MobRegistry.pruneDead(srv);
        }
    }
}
