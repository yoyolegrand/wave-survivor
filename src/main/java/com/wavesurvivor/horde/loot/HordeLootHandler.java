package com.wavesurvivor.horde.loot;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.horde.model.HordeEntity;
import com.wavesurvivor.horde.model.LootEntry;
import com.wavesurvivor.horde.spawn.MobRegistry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drops custom de TOUTES les entités du mod :
 *   - mobs de horde, entités custom, montures, boss CustomEntity → template dans MobRegistry
 *   - boss "vanilla" des bossWaves → table séparée BOSS_LOOT (ne compte pas dans les vagues)
 * Les ids passent par LootItems (clés de roulette chest KubeJS incluses).
 */
public class HordeLootHandler {

    private static final Random RNG = new Random();

    /** Loot des boss de bossWaves (UUID → entrées), en plus de l'éventuel template MobRegistry. */
    private static final Map<UUID, List<LootEntry>> BOSS_LOOT = new ConcurrentHashMap<>();

    public static void registerBossLoot(Entity boss, List<LootEntry> loot) {
        if (boss != null && loot != null && !loot.isEmpty()) BOSS_LOOT.put(boss.getUUID(), loot);
    }

    /**
     * Correctif de plantage (ConcurrentModificationException dans GoalSelector) : un squelette de la horde tué PENDANT son propre tour d'IA
     * (dégâts renvoyés, magie…) lâche son arme → le jeu vide sa main (setItemSlot) → le squelette recalcule ses objectifs d'attaque
     * (reassessWeaponGoal) alors qu'ils sont en cours de parcours. Les monstres de la horde ne lâchent donc pas leur équipement :
     * leur butin vient des tables de la horde.
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public void onHordeMobDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (event.isCanceled() || event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof net.minecraft.world.entity.monster.AbstractSkeleton sk)) return;
        if (MobRegistry.get(sk.getUUID()) == null && !com.wavesurvivor.horde.rift.RiftManager.isActive()) return;
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) sk.setDropChance(slot, 0f);
    }

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        HordeEntity template = MobRegistry.remove(entity.getUUID());
        List<LootEntry> bossLoot = BOSS_LOOT.remove(entity.getUUID());

        int added = 0;
        if (template != null) added += roll(entity, template.lootTable, event);
        if (bossLoot != null) added += roll(entity, bossLoot, event);

        // Marchand 9 : un boss de horde a 20 % de chance de doubler ses Éclats de grâce / de souillure
        if (com.wavesurvivor.horde.boss.BossDeathHandler.BOSSES.remove(entity.getUUID()) && entity.getServer() != null
                && com.wavesurvivor.horde.renaissance.Heritage.anyone(entity.getServer(), com.wavesurvivor.horde.renaissance.Heritage.Branch.MERCHANT, 9)
                && RNG.nextInt(5) == 0) {
            for (ItemEntity d : event.getDrops()) {
                ItemStack st = d.getItem();
                if (st.is(com.wavesurvivor.registry.ModItems.GRACE_SHARD.get()) || st.is(com.wavesurvivor.registry.ModItems.TAINT_SHARD.get()))
                    st.setCount(Math.min(64, st.getCount() * 2));
            }
        }
        if (added > 0) {
            WaveSurvivorMod.LOGGER.debug("[Loot] {} drop(s) custom pour {}", added,
                    template != null ? template.entityType : entity.getType().toString());
        }
    }

    private static int roll(LivingEntity entity, List<LootEntry> table, LivingDropsEvent event) {
        if (table == null || table.isEmpty()) return 0;
        int added = 0;
        for (LootEntry loot : table) {
            if (loot == null || loot.item == null || loot.item.isBlank()) continue;
            if (loot.chance < 100 && RNG.nextInt(100) >= loot.chance) continue;

            int min = Math.max(0, loot.minQty);
            int max = Math.max(min, loot.maxQty);
            int qty = min == max ? min : min + RNG.nextInt(max - min + 1);
            qty = com.wavesurvivor.horde.mutator.MutatorEffects.scaleQty(qty); // bonus des mutateurs (ex. ×1,45)
            if (qty <= 0) continue;

            ItemStack stack = LootItems.resolve(loot.item, qty);
            if (stack.isEmpty()) continue;

            ItemEntity drop = new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(), stack);
            drop.setDefaultPickUpDelay();
            event.getDrops().add(drop);
            added++;
        }
        return added;
    }
}
