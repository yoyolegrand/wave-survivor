package com.wavesurvivor.horde.roulette;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.horde.skill.DelayedActionScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Événements du système Roulette Chest :
 * - Placement d'un chest → check son CustomName (2 ticks après pour laisser MC copier le nom de l'item)
 * - Break d'un chest → cleanup registry
 * - Right-click sur un chest enregistré avec une clé → délègue à RouletteChestManager
 */
public class RouletteChestEventHandler {

    @SubscribeEvent
    public void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();

        // Le CustomName est copié de l'ItemStack vers le BlockEntity APRÈS le placement.
        // On schedule un check à +2 ticks pour être sûr d'avoir le nom final.
        DelayedActionScheduler.schedule(level.getServer(), 2, () -> {
            String name = readChestCustomName(level, pos);
            if (name == null) return;
            RouletteChestConfig cfg = RouletteChestRegistry.getByChestName(name);
            if (cfg != null) {
                // Match : registre (+ un coffre vanilla nommé devient un Coffre Roulette)
                RouletteChestManager.registerIdle(pos, cfg);
                RouletteChestManager.convertIfVanilla(level, pos, cfg);
            }
        }, "roulette check chest name");
    }

    @SubscribeEvent
    public void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        BlockPos pos = event.getPos();
        // Simplement retirer du registry (idempotent : safe même si pas enregistré)
        RouletteChestManager.unregister(pos);
    }

    @SubscribeEvent
    public void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;

        BlockPos pos = event.getPos();
        RouletteChestConfig cfg = RouletteChestManager.getConfigAt(pos);
        if (cfg == null) {
            // Fallback : peut-être qu'on a raté le placement (chunk reload etc)
            // Check le custom name maintenant
            String name = readChestCustomName(level, pos);
            if (name == null) return;
            cfg = RouletteChestRegistry.getByChestName(name);
            if (cfg == null) return;
            // Re-register pour la prochaine fois (+ conversion d'un ancien coffre vanilla)
            RouletteChestManager.registerIdle(pos, cfg);
            RouletteChestManager.convertIfVanilla(level, pos, cfg);
        }

        // Délègue au manager (vérif clé + animation)
        boolean handled = RouletteChestManager.tryOpenChest(sp, level, pos, cfg, event.getItemStack());
        if (handled) {
            // Cancel l'ouverture vanilla du coffre
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        }
    }

    /** Clic gauche en survie sur un coffre roulette : aperçu du loot (et pas de minage). */
    @SubscribeEvent
    public void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        if (player.isCreative() || player.isSpectator()) return;
        BlockPos pos = event.getPos();

        if (event.getLevel().isClientSide()) return; // le serveur gère (le bloc est incassable en survie)
        if (!(player instanceof ServerPlayer sp) || !(event.getLevel() instanceof ServerLevel level)) return;

        RouletteChestConfig cfg = RouletteChestManager.getConfigAt(pos);
        if (cfg == null) {
            String name = readChestCustomName(level, pos);
            if (name == null) return;
            cfg = RouletteChestRegistry.getByChestName(name);
            if (cfg == null) return;
            RouletteChestManager.registerIdle(pos, cfg);
            RouletteChestManager.convertIfVanilla(level, pos, cfg);
        }
        event.setCanceled(true);
        if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START) {
            RouletteChestManager.showLoot(sp, cfg);
        }
    }

    /** Lit le CustomName d'un chest (ou tout container nommé). */
    private static String readChestCustomName(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof BaseContainerBlockEntity container) {
            var customName = container.getCustomName();
            if (customName != null) return customName.getString();
        }
        return null;
    }
}
