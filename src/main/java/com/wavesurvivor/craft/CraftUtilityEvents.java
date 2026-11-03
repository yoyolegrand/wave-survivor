package com.wavesurvivor.craft;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Effets des blocs utilitaires qui passent par des événements : Lanterne de grâce (dégâts réduits) et Bougie votive (pas d'apparition naturelle). */
public class CraftUtilityEvents {

    /** Lanterne de grâce : morts-vivants et Corrompus à proximité infligent moins de dégâts. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onHurt(LivingHurtEvent e) {
        if (!(e.getSource().getEntity() instanceof LivingEntity att) || att.level().isClientSide) return;
        if (!CraftEquipEvents.undeadOrCorrupted(att)) return;
        double r = CraftConfig.blockNum("grace_lantern", "radius", 8);
        double pct = CraftConfig.blockNum("grace_lantern", "damageReductionPercent", 20);
        for (BlockPos p : CraftUtilityBlockEntity.loaded(att.level(), CraftUtilityBlock.Kind.LANTERN)) {
            if (att.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) <= r * r) {
                e.setAmount(e.getAmount() * (float) Math.max(0, 1 - pct / 100.0));
                return;
            }
        }
    }

    /** Bougie votive allumée : aucune apparition NATURELLE de monstre à proximité (les vagues des hordes ne passent pas par ici). */
    @SubscribeEvent
    public void onSpawnCheck(MobSpawnEvent.PositionCheck e) {
        if (e.getSpawnType() != MobSpawnType.NATURAL || !(e.getEntity() instanceof Enemy)) return;
        Level level = e.getLevel().getLevel();
        double r = CraftConfig.blockNum("votive_candle", "radius", 12);
        for (BlockPos p : CraftUtilityBlockEntity.loaded(level, CraftUtilityBlock.Kind.CANDLE)) {
            if (!level.getBlockState(p).getValue(CraftUtilityBlock.LIT)) continue;
            double dx = e.getX() - (p.getX() + 0.5), dy = e.getY() - (p.getY() + 0.5), dz = e.getZ() - (p.getZ() + 0.5);
            if (dx * dx + dy * dy + dz * dz <= r * r) {
                e.setResult(Event.Result.DENY);
                return;
            }
        }
    }
}
