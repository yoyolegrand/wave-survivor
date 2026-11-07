package com.wavesurvivor.item;

import com.wavesurvivor.registry.ModEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * FRAÎCHEUR DU DÉSERT (1.9) : tant que l'effet est actif, toute brûlure du joueur dure deux fois moins longtemps
 * (un feu de 4 s ne dure que 2 s). Les dégâts de chaque tic de feu ne changent pas : seule la durée est réduite.
 * On surveille la durée de feu restante : quand elle augmente (nouvelle brûlure, lave, feu de camp…), on ramène l'ajout à sa moitié.
 */
public class FreshnessEvents {

    private static final String LAST = "ws_fresh_fire";

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        CompoundTag d = p.getPersistentData();
        if (!p.hasEffect(ModEffects.DESERT_FRESHNESS.get())) {
            if (d.contains(LAST)) d.remove(LAST);
            return;
        }
        int cur = p.getRemainingFireTicks();
        int last = d.contains(LAST) ? d.getInt(LAST) : 0; // première tick sous l'effet : un feu déjà en cours est lui aussi réduit de moitié
        if (cur > last) {
            cur = last + (cur - last) / 2;
            p.setRemainingFireTicks(cur);
        }
        d.putInt(LAST, Math.max(cur, 0));
    }
}
