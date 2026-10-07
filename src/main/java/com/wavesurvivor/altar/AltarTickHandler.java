package com.wavesurvivor.altar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Particules d'ambiance sur chaque altar posé, pour signaler visuellement qu'il est actif.
 *
 * Deux modes selon le lien :
 *   - Altar LIÉ à une horde : particules rouges intenses (SOUL_FIRE_FLAME + FLAME + spirale)
 *   - Altar VIERGE (non lié) : particules subtiles (juste étincelles CRIT au-dessus)
 *
 * Pattern actif :
 *   - Cercle de flammes tournant lentement au-dessus
 *   - Colonne montante de SOUL_FIRE_FLAME
 *   - Petites étincelles LAVA autour du block
 */
public class AltarTickHandler {

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null) return;
        long now = server.getTickCount();

        // Update toutes les 2 ticks (10 fps) — allège la charge
        if (now % 2 != 0) return;

        for (AltarStore.AltarEntry entry : AltarStore.all()) {
            ServerLevel level = resolveLevel(server, entry.dimension);
            if (level == null) continue;

            BlockPos pos = entry.toBlockPos();
            if (!level.isLoaded(pos)) continue;

            boolean bound = entry.hordeName != null && !entry.hordeName.isBlank();
            var state = level.getBlockState(pos);
            var color = state.hasProperty(AltarBlock.COLOR) ? state.getValue(AltarBlock.COLOR)
                    : net.minecraft.world.item.DyeColor.RED;
            AltarParticles.byId(entry.particle).spawn(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                    now, bound, color);
        }
    }

    @SuppressWarnings("unused")
    private void spawnIdleParticlesLegacy(ServerLevel level, BlockPos p, boolean bound, long now) {
        double cx = p.getX() + 0.5;
        double cz = p.getZ() + 0.5;
        double baseY = p.getY() + 1.0;

        if (bound) {
            // === ALTAR LIÉ : effets intenses ===

            // Cercle de flammes tournant : 3 particules autour du block
            double angleStep = (Math.PI * 2) / 3;
            double angleOffset = (now % 60) * (Math.PI * 2 / 60);
            for (int i = 0; i < 3; i++) {
                double angle = angleStep * i + angleOffset;
                double dx = Math.cos(angle) * 0.7;
                double dz = Math.sin(angle) * 0.7;
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        cx + dx, baseY + 0.2, cz + dz,
                        1, 0, 0, 0, 0.005);
            }

            // Colonne montante : 1 flamme tous les 6 ticks qui monte
            if (now % 6 == 0) {
                level.sendParticles(ParticleTypes.FLAME,
                        cx, baseY + 0.3, cz,
                        1, 0.05, 0.4, 0.05, 0.02);
            }

            // Étincelles LAVA occasionnelles autour du block
            if (now % 20 == 0) {
                level.sendParticles(ParticleTypes.LAVA,
                        cx + (Math.random() - 0.5) * 1.2,
                        baseY - 0.2,
                        cz + (Math.random() - 0.5) * 1.2,
                        1, 0, 0, 0, 0);
            }

        } else {
            // === ALTAR VIERGE : effets subtils (juste "présent") ===
            if (now % 10 == 0) {
                level.sendParticles(ParticleTypes.CRIT,
                        cx, baseY + 0.5, cz,
                        1, 0.3, 0.2, 0.3, 0.01);
            }
        }
    }

    private static ServerLevel resolveLevel(MinecraftServer server, String dimensionId) {
        String dim = dimensionId != null ? dimensionId : "minecraft:overworld";
        try {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dim));
            ServerLevel lvl = server.getLevel(key);
            if (lvl != null) return lvl;
        } catch (Exception ignore) {}
        return server.overworld();
    }
}
