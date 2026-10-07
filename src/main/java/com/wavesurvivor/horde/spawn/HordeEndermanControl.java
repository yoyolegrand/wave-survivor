package com.wavesurvivor.horde.spawn;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * ENDERMEN DE HORDE (marqués au spawn par NetherSpawnFix) :
 *   - ne ramassent / ne posent AUCUN bloc (mobGriefing refusé) ;
 *   - la pluie et l'eau ne les blessent pas (sinon : dégâts + téléportation de fuite) ;
 *   - pas de téléportation aléatoire (plein jour, coup reçu) : ils restent au combat.
 * Les endermen hors horde gardent leur comportement normal.
 */
public class HordeEndermanControl {

    public static final String TAG = "ws_horde_enderman";

    public static void mark(EnderMan e) {
        e.getPersistentData().putBoolean(TAG, true);
    }

    private static boolean isHorde(Entity e) {
        return e instanceof EnderMan && e.getPersistentData().getBoolean(TAG);
    }

    @SubscribeEvent
    public void onGriefing(EntityMobGriefingEvent event) {
        if (isHorde(event.getEntity())) event.setResult(Event.Result.DENY);
    }

    @SubscribeEvent
    public void onAttack(LivingAttackEvent event) {
        if (!isHorde(event.getEntity())) return;
        var src = event.getSource();
        if (src.is(DamageTypes.DROWN) || src.is(DamageTypeTags.IS_DROWNING)) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onTeleport(EntityTeleportEvent.EnderEntity event) {
        if (isHorde(event.getEntityLiving())) event.setCanceled(true);
    }
}
