package com.wavesurvivor.shrine;

import com.wavesurvivor.horde.spawn.MobRegistry;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

/**
 * Événements qui font bouger la foi des statues à moins de ~16 blocs (rayon et valeurs dans shrine_offerings.json) :
 * un monstre de horde tué = grâce ; un animal paisible tué par un joueur = souillure ; un villageois tué par un joueur = grosse souillure.
 */
public class ShrineEvents {

    // HIGHEST : on lit le registre des monstres de horde avant que d'autres gestionnaires de mort ne le nettoient
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDeath(LivingDeathEvent e) {
        LivingEntity v = e.getEntity();
        if (!(v.level() instanceof ServerLevel sl)) return;
        ServerPlayer killer = e.getSource().getEntity() instanceof ServerPlayer p ? p : null;
        int delta;
        String key;
        if (MobRegistry.get(v.getUUID()) != null) {
            delta = ShrineConfig.hordeKill();
            key = null;                                  // trop fréquent pour un message
        } else if (killer != null && v instanceof Villager) {
            delta = ShrineConfig.villagerKill();
            key = "shrine.event_villager";
        } else if (killer != null && v instanceof Animal && !(v instanceof Enemy)) {
            delta = ShrineConfig.animalKill();
            key = "shrine.event_animal";
        } else {
            return;
        }
        if (delta == 0) return;
        List<SeraphShrineBlockEntity> near = ShrineRegistry.nearby(sl, v.position(), ShrineConfig.eventRadius());
        if (near.isEmpty()) return;
        for (SeraphShrineBlockEntity be : near) be.addFaith(delta);
        if (key != null && killer != null) {
            killer.displayClientMessage(Component.literal(WSLang.t(key, (delta > 0 ? "+" : "") + delta)), true);
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent e) {
        ShrineRegistry.clear();
    }
}
