package com.wavesurvivor.horde;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * GARDE ANTI-EXPLOSION.
 *  - Pendant une horde : les explosions (creepers, boules de feu, mortiers, mines…) blessent toujours
 *    joueurs et monstres, mais ne cassent AUCUN bloc → l'arène reste intacte.
 *  - En permanence : les blocs du mod (Autel runique / Monolithe, autel de Renaissance, coffres de roulette…)
 *    ne peuvent jamais être détruits par une explosion.
 */
public class ExplosionGuard {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDetonate(ExplosionEvent.Detonate event) {
        if (event.getAffectedBlocks().isEmpty()) return;
        if (HordeManager.get() != null && HordeManager.get().isRunning()) {
            event.getAffectedBlocks().clear();
            return;
        }
        Level level = event.getLevel();
        event.getAffectedBlocks().removeIf(pos -> isModBlock(level, pos));
    }

    private static boolean isModBlock(Level level, BlockPos pos) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        return id != null && "wavesurvivor".equals(id.getNamespace());
    }
}
