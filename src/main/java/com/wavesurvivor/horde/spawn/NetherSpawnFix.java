package com.wavesurvivor.horde.spawn;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.player.Player;

/**
 * Monstres du Nether dans l'Overworld :
 *   - piglins / piglins brutes / hoglins : IMMUNISÉS à la zombification (sinon ils se transforment au bout de 15 s
 *     et quittent la horde) ;
 *   - mobs neutres (piglins zombies...) : agressifs tout de suite envers le joueur le plus proche.
 * À appeler juste après la création du mob (avant ou après addFreshEntity).
 */
public final class NetherSpawnFix {

    private NetherSpawnFix() {}

    public static void apply(Mob mob) {
        if (mob == null) return;
        HordeAggro.track(mob); // portée d'aggro énorme + verrouillage continu sur les joueurs
        // Jamais de bébé dans une horde (zombies, piglins, hoglins... tirés au hasard par finalizeSpawn)
        if (mob.isBaby()) mob.setBaby(false);
        // Jockey poulet éventuel : on descend et on retire le poulet
        if (mob.getVehicle() instanceof net.minecraft.world.entity.animal.Chicken chicken) {
            mob.stopRiding();
            chicken.discard();
        }
        if (mob instanceof AbstractPiglin p) {
            p.setImmuneToZombification(true);
            HordePiglinControl.track(p); // insensible à l'or, attaque même les joueurs en armure d'or
        }
        if (mob instanceof Hoglin h) h.setImmuneToZombification(true);
        if (mob instanceof net.minecraft.world.entity.monster.EnderMan em) HordeEndermanControl.mark(em); // pas de blocs, pas de fuite
        if (mob instanceof NeutralMob nm) {
            Player target = mob.level().getNearestPlayer(mob, 64);
            if (target != null && !target.isCreative() && !target.isSpectator()) {
                nm.setPersistentAngerTarget(target.getUUID());
                nm.startPersistentAngerTimer();
                mob.setTarget(target);
            }
        }
    }
}
