package com.wavesurvivor.horde.skill.impl;

import com.wavesurvivor.config.model.CustomSkillData;
import com.wavesurvivor.horde.skill.BossSkill;
import com.wavesurvivor.horde.skill.DelayedActionScheduler;
import com.wavesurvivor.horde.skill.NecroTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * glacier_pillars (Piliers de glace) : des colonnes de glace surgissent autour du boss, coupent les lignes de vue et
 * obligent à bouger. Elles disparaissent d'elles-mêmes après pillarsDuration ticks (seulement si le bloc est
 * toujours le nôtre). Jamais posées sur un joueur.
 */
public class SkillGlacierPillars extends BossSkill {

    private static final Random RNG = new Random();

    public SkillGlacierPillars(CustomSkillData config) {
        super(config);
    }

    @Override
    public long getCooldownTicks() {
        return Math.max(60, config.pillarsCooldown);
    }

    @Override
    public void execute(LivingEntity boss, LivingEntity target, ServerLevel level) {
        Block block = Blocks.PACKED_ICE;
        try {
            Block b = BuiltInRegistries.BLOCK.get(new ResourceLocation(config.pillarsBlock.trim()));
            if (b != Blocks.AIR) block = b;
        } catch (Exception ignored) {}
        final Block placed = block;
        int count = Math.max(1, Math.min(20, config.pillarsCount));
        int height = Math.max(2, Math.min(10, config.pillarsHeight));
        double range = Math.max(4, config.pillarsRange);
        int duration = Math.max(20, config.pillarsDuration);

        List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, boss.getBoundingBox().inflate(range + 6),
                p -> p.isAlive() && !p.isSpectator());
        List<BlockPos> bases = new ArrayList<>();
        for (int i = 0; i < count * 4 && bases.size() < count; i++) {
            double ang = RNG.nextDouble() * Math.PI * 2;
            double dist = 3 + RNG.nextDouble() * (range - 3);
            int x = (int) Math.floor(boss.getX() + Math.cos(ang) * dist);
            int z = (int) Math.floor(boss.getZ() + Math.sin(ang) * dist);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos base = new BlockPos(x, y, z);
            if (!level.getBlockState(base.below()).isSolid() || !level.getBlockState(base).isAir()) continue;
            boolean onPlayer = false;
            for (ServerPlayer p : players) {
                if (Math.abs(p.getX() - (x + 0.5)) < 1.8 && Math.abs(p.getZ() - (z + 0.5)) < 1.8) { onPlayer = true; break; }
            }
            if (!onPlayer) bases.add(base);
        }
        if (bases.isEmpty()) return;

        for (BlockPos base : bases) {
            for (int h = 0; h < height; h++) {
                BlockPos bp = base.above(h);
                if (!level.getBlockState(bp).isAir()) break;
                level.setBlockAndUpdate(bp, placed.defaultBlockState());
                // Disparition : seulement si le bloc est toujours celui qu'on a posé
                DelayedActionScheduler.schedule(level.getServer(), duration, () -> {
                    if (level.getBlockState(bp).is(placed)) level.setBlockAndUpdate(bp, Blocks.AIR.defaultBlockState());
                }, "glacier_pillars restore");
            }
            level.sendParticles(ParticleTypes.SNOWFLAKE, base.getX() + 0.5, base.getY() + 1.5, base.getZ() + 0.5, 25, 0.4, 1.0, 0.4, 0.05);
        }
        NecroTracker.playSound(level, boss.blockPosition(), config.pillarsSound, 1.2f);
        if (config.pillarsMessage != null && !config.pillarsMessage.isBlank()) {
            for (ServerPlayer p : players) p.displayClientMessage(Component.literal(config.pillarsMessage), true);
        }
    }
}
