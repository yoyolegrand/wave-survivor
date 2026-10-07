package com.wavesurvivor.horde.spawn;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Piglins de HORDE : insensibles à l'or.
 *   - ne ramassent plus rien au sol (pas de troc si on leur jette de l'or) ;
 *   - n'admirent plus l'or (mémoire ADMIRING_DISABLED maintenue, objet admiré retiré) ;
 *   - attaquent le joueur le plus proche MÊME s'il porte de l'armure en or (cible forcée dans leur cerveau).
 * Les piglins hors horde (bastions, Nether) gardent leur comportement normal.
 */
public class HordePiglinControl {

    private static final Set<UUID> TRACKED = ConcurrentHashMap.newKeySet();

    /** Appelé au spawn (NetherSpawnFix) pour chaque piglin / piglin brute de horde. */
    public static void track(AbstractPiglin p) {
        p.setCanPickUpLoot(false);
        TRACKED.add(p.getUUID());
        calm(p);
    }

    public static void clearAll() {
        TRACKED.clear();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || TRACKED.isEmpty()) return;
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 10 != 0) return;
        Iterator<UUID> it = TRACKED.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            AbstractPiglin p = find(server, id);
            if (p == null || !p.isAlive()) { it.remove(); continue; }
            calm(p);
            // Profanateurs (Briseurs) : leur cible est le Monolithe, gérée par AltarDefense
            if (com.wavesurvivor.altar.AltarDefense.isProfaner(id)) continue;
            // Cible forcée : le joueur le plus proche, armure en or ou non
            var brain = p.getBrain();
            var current = brain.getMemory(MemoryModuleType.ATTACK_TARGET);
            boolean valid = current.isPresent() && current.get().isAlive()
                    && !(current.get() instanceof net.minecraft.world.entity.monster.Enemy)
                    && !(current.get() instanceof Player pl && (pl.isCreative() || pl.isSpectator()));
            if (!valid) {
                Player target = p.level().getNearestPlayer(p, 48);
                if (target != null && !target.isCreative() && !target.isSpectator()) {
                    brain.setMemoryWithExpiry(MemoryModuleType.ANGRY_AT, target.getUUID(), 600L);
                    brain.setMemory(MemoryModuleType.ATTACK_TARGET, target);
                    p.setTarget(target);
                }
            }
        }
    }

    /** Coupe l'attrait pour l'or : pas d'admiration, pas d'objet admiré en main secondaire. */
    private static void calm(AbstractPiglin p) {
        var brain = p.getBrain();
        brain.setMemoryWithExpiry(MemoryModuleType.ADMIRING_DISABLED, true, 400L);
        brain.eraseMemory(MemoryModuleType.ADMIRING_ITEM);
        // Oublie son « ennemi juré » (wither squelettes) : pas de combat contre les alliés de horde
        brain.eraseMemory(MemoryModuleType.NEAREST_VISIBLE_NEMESIS);
        brain.getMemory(MemoryModuleType.ATTACK_TARGET).ifPresent(t -> {
            if (t instanceof net.minecraft.world.entity.monster.Enemy) brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
        });
        ItemStack off = p.getItemBySlot(EquipmentSlot.OFFHAND);
        if (!off.isEmpty() && (off.is(Items.GOLD_INGOT) || off.is(Items.GOLD_NUGGET) || off.is(Items.GOLD_BLOCK)
                || off.is(Items.GOLDEN_APPLE) || off.is(Items.GOLDEN_CARROT))) {
            p.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }
    }

    private static AbstractPiglin find(MinecraftServer server, UUID id) {
        for (ServerLevel lvl : server.getAllLevels()) {
            Entity e = lvl.getEntity(id);
            if (e instanceof AbstractPiglin p) return p;
        }
        return null;
    }
}
