package com.wavesurvivor.horde.skill;

import com.wavesurvivor.WaveSurvivorMod;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracker des cobwebs posées par SkillWebTrap.
 * Chaque cobweb est stockée avec sa position, sa dimension, et son tick d'expiration.
 * Au tick d'expiration, la cobweb est supprimée SEULEMENT SI elle est toujours présente
 * (le joueur a pu la casser entre-temps).
 *
 * Ticked depuis HordeTickHandler.onServerTick.
 */
public class WebTrapTracker {

    private static final List<TrappedBlock> BLOCKS = new ArrayList<>();

    public static class TrappedBlock {
        public final ResourceLocation dimension;
        public final BlockPos pos;
        public final long expiryTick;

        public TrappedBlock(ResourceLocation dim, BlockPos pos, long expiry) {
            this.dimension = dim;
            this.pos = pos;
            this.expiryTick = expiry;
        }
    }

    public static void register(ServerLevel level, BlockPos pos, int durationTicks) {
        long expiry = level.getServer().getTickCount() + Math.max(1, durationTicks);
        BLOCKS.add(new TrappedBlock(level.dimension().location(), pos.immutable(), expiry));
    }

    public static void tick(MinecraftServer server) {
        if (BLOCKS.isEmpty()) return;
        long now = server.getTickCount();

        var it = BLOCKS.iterator();
        while (it.hasNext()) {
            TrappedBlock tb = it.next();
            if (now < tb.expiryTick) continue;

            // Find the level by dimension
            ServerLevel level = null;
            for (ServerLevel l : server.getAllLevels()) {
                if (l.dimension().location().equals(tb.dimension)) { level = l; break; }
            }
            if (level != null) {
                BlockState state = level.getBlockState(tb.pos);
                if (state.is(Blocks.COBWEB)) {
                    level.setBlockAndUpdate(tb.pos, Blocks.AIR.defaultBlockState());
                }
            }
            it.remove();
        }
    }

    public static void clearAll() {
        int n = BLOCKS.size();
        BLOCKS.clear();
        if (n > 0) WaveSurvivorMod.LOGGER.debug("[WebTrapTracker] {} cobwebs purgées", n);
    }

    public static int pendingCount() {
        return BLOCKS.size();
    }
}
