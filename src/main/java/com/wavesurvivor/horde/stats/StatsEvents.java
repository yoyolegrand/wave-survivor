package com.wavesurvivor.horde.stats;

import com.wavesurvivor.horde.HordeManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Statistiques (1.7) : morts des joueurs et temps de jeu pendant une horde. Enregistré dans WaveSurvivorMod. */
public class StatsEvents {

    @SubscribeEvent
    public void onDeath(LivingDeathEvent e) {
        if (e.getEntity().level().isClientSide || !(e.getEntity() instanceof ServerPlayer p)) return;
        if (!HordeManager.get().isRunning()) return;
        PlayerStats.add(p, "deaths", 1);
    }

    /** Toutes les secondes de horde : +20 ticks de temps de jeu pour chaque joueur présent. */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer().getTickCount() % 20 != 0) return;
        if (!HordeManager.get().isRunning()) return;
        PlayerStats.addForAll(e.getServer(), "playtime_ticks", 20);
    }
}
