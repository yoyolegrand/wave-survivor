package com.wavesurvivor.altar;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Événements du système Altar :
 *   - Placement : si l'item avait un NBT wavesurvivor_bound_horde (via /ws altar give),
 *                 on l'enregistre auto dans le store.
 *   - Break : unregister du store.
 */
public class AltarEventHandler {

    public static final String NBT_BOUND_HORDE = "wavesurvivor_bound_horde";

    @SubscribeEvent
    public void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        Level level = (Level) event.getLevel();
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel sl)) return;
        if (event.getPlacedBlock().getBlock() != ModBlocks.ALTAR_RUNIC.get()) return;

        BlockPos pos = event.getPos();

        // Cherche l'ItemStack source qui a placé le block (via placer entity)
        String boundHorde = null;
        if (event.getEntity() instanceof LivingEntity living) {
            // Vérifie les deux mains
            for (ItemStack held : new ItemStack[]{living.getMainHandItem(), living.getOffhandItem()}) {
                if (held.getItem() == com.wavesurvivor.registry.ModItems.ALTAR_RUNIC_ITEM.get()) {
                    CompoundTag tag = held.getTag();
                    if (tag != null && tag.contains(NBT_BOUND_HORDE)) {
                        boundHorde = tag.getString(NBT_BOUND_HORDE);
                        break;
                    }
                }
            }
        }

        String dim = sl.dimension().location().toString();
        AltarStore.AltarEntry entry = new AltarStore.AltarEntry(
                boundHorde, dim, pos.getX(), pos.getY(), pos.getZ(),
                AltarBlock.AltarType.HORDE_TRIGGER.name()
        );
        // Propriétaire = celui qui pose l'autel
        if (event.getEntity() instanceof net.minecraft.world.entity.player.Player placer) {
            entry.ownerUuid = placer.getUUID().toString();
            entry.ownerName = placer.getGameProfile().getName();
        }
        AltarStore.register(entry);

        WaveSurvivorMod.LOGGER.info("[Altar] Enregistré @ {} (horde={}, dim={})",
                pos, boundHorde != null ? boundHorde : com.wavesurvivor.i18n.WSLang.t("srv.non_lie"), dim);
    }

    /** Les explosions n'emportent pas l'autel d'une arène. */
    @SubscribeEvent
    public void onExplosion(net.minecraftforge.event.level.ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        event.getAffectedBlocks().removeIf(p -> sl.getBlockState(p).getBlock() == ModBlocks.ALTAR_RUNIC.get() && com.wavesurvivor.arena.ArenaAltar.isLocked(sl, p));
    }

    /** Les pistons ne déplacent ni ne détruisent l'autel d'une arène. */
    @SubscribeEvent
    public void onPiston(net.minecraftforge.event.level.PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        var helper = event.getStructureHelper();
        if (helper == null || !helper.resolve()) return;
        for (BlockPos p : helper.getToPush()) if (sl.getBlockState(p).getBlock() == ModBlocks.ALTAR_RUNIC.get() && com.wavesurvivor.arena.ArenaAltar.isLocked(sl, p)) { event.setCanceled(true); return; }
        for (BlockPos p : helper.getToDestroy()) if (sl.getBlockState(p).getBlock() == ModBlocks.ALTAR_RUNIC.get() && com.wavesurvivor.arena.ArenaAltar.isLocked(sl, p)) { event.setCanceled(true); return; }
    }

    @SubscribeEvent
    public void onBlockBroken(BlockEvent.BreakEvent event) {
        Level level = (Level) event.getLevel();
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel sl)) return;
        if (event.getState().getBlock() != ModBlocks.ALTAR_RUNIC.get()) return;

        // Autel d'une arène : incassable en Survie
        if (com.wavesurvivor.arena.ArenaAltar.isLocked(sl, event.getPos()) && !event.getPlayer().getAbilities().instabuild) {
            event.setCanceled(true);
            event.getPlayer().displayClientMessage(net.minecraft.network.chat.Component.literal(com.wavesurvivor.i18n.WSLang.t("arena.altar_unbreakable")), true);
            return;
        }
        String dim = sl.dimension().location().toString();
        // Autel 2 blocs : la référence est toujours la moitié basse
        AltarStore.unregister(dim, AltarBlock.basePos(event.getState(), event.getPos()));
    }
}
