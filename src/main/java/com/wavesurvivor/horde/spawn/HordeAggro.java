package com.wavesurvivor.horde.spawn;

import com.wavesurvivor.altar.AltarDefense;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AGGRO DES MONSTRES DE HORDE :
 *   - portée de suivi portée à FOLLOW_RANGE blocs (ne perdent pas le joueur de vue) ;
 *   - chaque seconde, un monstre sans cible valable se verrouille sur le joueur le plus proche (≤ SEARCH blocs) ;
 *   - mobs neutres (endermen, piglins zombies...) : colère permanente, rafraîchie en continu
 *     → les endermen attaquent sans qu'on ait besoin de les regarder.
 * Exclus : piglins (HordePiglinControl) et Profanateurs (leur cible est le Monolithe).
 */
public class HordeAggro {

    public static final double FOLLOW_RANGE = 100;
    private static final double SEARCH = 128;
    private static final Set<UUID> TRACKED = ConcurrentHashMap.newKeySet();

    public static void track(Mob mob) {
        if (mob == null) return;
        AttributeInstance fr = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (fr != null && fr.getBaseValue() < FOLLOW_RANGE) fr.setBaseValue(FOLLOW_RANGE);
        TRACKED.add(mob.getUUID());
    }

    public static void clearAll() {
        TRACKED.clear();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || TRACKED.isEmpty()) return;
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;
        Iterator<UUID> it = TRACKED.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            Mob mob = find(server, id);
            if (mob == null || !mob.isAlive()) { it.remove(); continue; }
            if (mob instanceof AbstractPiglin || AltarDefense.isProfaner(id)) continue;

            // Portée de suivi (au cas où une stat l'aurait réduite après le spawn)
            AttributeInstance fr = mob.getAttribute(Attributes.FOLLOW_RANGE);
            if (fr != null && fr.getBaseValue() < FOLLOW_RANGE) fr.setBaseValue(FOLLOW_RANGE);

            LivingEntity t = mob.getTarget();
            boolean valid = t != null && t.isAlive() && !(t instanceof Player p && (p.isCreative() || p.isSpectator()));
            Player target = valid && t instanceof Player pl ? pl : null;
            if (!valid) {
                Player near = mob.level().getNearestPlayer(mob, SEARCH);
                if (near != null && !near.isCreative() && !near.isSpectator()) {
                    mob.setTarget(near);
                    target = near;
                }
            }
            // Colère permanente des mobs neutres (endermen compris)
            if (target != null && mob instanceof NeutralMob nm) {
                nm.setPersistentAngerTarget(target.getUUID());
                nm.setRemainingPersistentAngerTime(600);
                if (mob instanceof EnderMan && mob.getTarget() != target) mob.setTarget(target);
            }
        }
    }

    private static Mob find(MinecraftServer server, UUID id) {
        for (ServerLevel lvl : server.getAllLevels()) {
            Entity e = lvl.getEntity(id);
            if (e instanceof Mob m) return m;
        }
        return null;
    }
}
