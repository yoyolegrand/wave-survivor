package com.wavesurvivor.horde.kingdom;

import com.wavesurvivor.entity.GisementEntity;
import com.wavesurvivor.i18n.WSLang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * CHARGE DE DÉMOLITION (Bâtisseur) : achetée 1 fois par vague, posée près d'une Porte ébréchée dont les gardiens sont
 * morts. Mèche de 2 s (sifflement + fumée), puis explosion : la Porte perd d'un coup tout ce qui reste de sa brèche
 * (jamais plus que le plafond autorisé). Visuel : un amas de TNT, même structure que les gisements.
 */
public final class KingdomDemolition {

    private KingdomDemolition() {}

    /** Durée de la mèche (ticks). */
    public static final int FUSE = 40;
    /** Rayon de recherche de la Porte visée. */
    public static final double RANGE = 12;

    private record Armed(UUID entity, int gate, long boomAt, BlockPos pos) {}

    private static final List<Armed> ARMED = new ArrayList<>();
    /** Vague (cycle) du dernier achat. */
    private static int boughtCycle = -1;

    public static boolean boughtThisWave() {
        return KingdomManager.isActive() && boughtCycle == KingdomManager.cycle();
    }

    public static void markBought() {
        boughtCycle = KingdomManager.cycle();
    }

    /** Pose d'une charge : amas de TNT, mèche allumée. */
    public static boolean arm(ServerLevel lvl, BlockPos at, int gate) {
        GisementEntity g = com.wavesurvivor.registry.ModEntities.GISEMENT.get().create(lvl);
        if (g == null) return false;
        g.setSize(0);
        g.setup("minecraft:tnt,minecraft:tnt,minecraft:redstone_block", 0);
        g.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, lvl.random.nextFloat() * 360f, 0);
        g.setInvulnerable(true);
        g.setCustomName(Component.literal(WSLang.t("kingdom.charge.name")));
        g.setCustomNameVisible(true);
        g.getPersistentData().putBoolean("ws_revenant", true);
        if (!lvl.addFreshEntity(g)) return false;
        ARMED.add(new Armed(g.getUUID(), gate, lvl.getServer().getTickCount() + FUSE, at));
        lvl.playSound(null, at, SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.5f, 1f);
        return true;
    }

    public static void clear(MinecraftServer server) {
        if (server != null) {
            for (Armed a : ARMED) {
                for (ServerLevel l : server.getAllLevels()) {
                    Entity e = l.getEntity(a.entity());
                    if (e != null) e.discard();
                }
            }
        }
        ARMED.clear();
        boughtCycle = -1;
    }

    public static class Events {
        @SubscribeEvent
        public void demolitionTick(TickEvent.ServerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || ARMED.isEmpty()) return;
            MinecraftServer server = e.getServer();
            ServerLevel lvl = KingdomManager.level();
            if (lvl == null) { clear(server); return; }
            long now = server.getTickCount();
            for (int i = ARMED.size() - 1; i >= 0; i--) {
                Armed a = ARMED.get(i);
                BlockPos p = a.pos();
                if (now < a.boomAt()) {
                    // Mèche : fumée et étincelles
                    lvl.sendParticles(ParticleTypes.SMOKE, p.getX() + 0.5, p.getY() + 1.1, p.getZ() + 0.5, 3, 0.1, 0.05, 0.1, 0.01);
                    lvl.sendParticles(ParticleTypes.FLAME, p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5, 1, 0.05, 0.05, 0.05, 0.01);
                    if ((a.boomAt() - now) % 10 == 0) {
                        lvl.playSound(null, p, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, 1f, 1.8f);
                    }
                    continue;
                }
                ARMED.remove(i);
                Entity ent = lvl.getEntity(a.entity());
                if (ent != null) ent.discard();
                // Explosion visuelle (aucun dégât au terrain ni aux joueurs)
                lvl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 1, 0, 0, 0, 0);
                lvl.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, 30, 1, 1, 1, 0.05);
                lvl.playSound(null, p, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4f, 0.8f);
                boolean hit = KingdomManager.detonate(a.gate());
                Component msg = WSLang.c(hit ? "kingdom.charge.boom" : "kingdom.charge.fizzle");
                for (ServerPlayer pl : server.getPlayerList().getPlayers()) pl.sendSystemMessage(msg);
            }
        }
    }
}
