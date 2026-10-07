package com.wavesurvivor.horde.spawn;

import com.wavesurvivor.entity.KingdomSoldier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * PAS DE COMBATS ENTRE MONSTRES (toujours actif, int\u00e9gr\u00e9 au mod \u2014 remplace l'ancien script KubeJS \u00ab Friendlyfire \u00bb) :
 *   - un monstre ne peut pas prendre un autre monstre pour cible ;
 *   - aucun d\u00e9g\u00e2t d'un monstre sur un autre (corps \u00e0 corps, fl\u00e8ches, explosions) \u2192 pas de riposte en cha\u00eene.
 * \u00ab Monstre \u00bb = cr\u00e9ature hostile (Enemy) OU cr\u00e9ature lanc\u00e9e par la horde (registre du mod, montures comprises)
 * OU Gardien / escorte du mode Kingdom. Les soldats du royaume ne sont jamais concern\u00e9s (ils restent attaquables).
 * Ex : squelettes archers qui se touchent entre eux, piglins vs wither squelettes, boules de feu de blaze sur un alli\u00e9\u2026
 */
public class HordeInfightingGuard {

    /** Vrai si l'entit\u00e9 est un monstre (au sens de la r\u00e8gle anti-bagarre). */
    public static boolean isMonster(Entity e) {
        if (!(e instanceof LivingEntity) || e instanceof KingdomSoldier) return false;
        if (e instanceof Enemy) return true;
        if (e instanceof Mob && MobRegistry.get(e.getUUID()) != null) return true;  // unit\u00e9 de horde (m\u00eame non \u00ab hostile \u00bb)
        var tag = e.getPersistentData();
        return tag.getBoolean("ws_kingdom_guardian") || tag.getBoolean("ws_kingdom_escort");
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity newTarget = event.getNewTarget();
        if (newTarget != null && newTarget != event.getEntity() && isMonster(event.getEntity()) && isMonster(newTarget)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onAttack(LivingAttackEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || !isMonster(victim)) return;
        Entity attacker = event.getSource().getEntity(); // tireur / lanceur pour les projectiles
        if (attacker != null && attacker != victim && isMonster(attacker)) {
            event.setCanceled(true);
        }
    }
}
