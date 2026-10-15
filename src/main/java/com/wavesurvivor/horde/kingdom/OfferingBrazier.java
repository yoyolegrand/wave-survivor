package com.wavesurvivor.horde.kingdom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BRASIER DES OFFRANDES — gestion côté partie Kingdom : registre des Brasiers posés (avec leurs blocs de collision),
 * nettoyage en fin de partie, sauvegarde / reprise, Vague de réparation, Ferveur du royaume.
 * Emprise : 2×2 au sol, 2 blocs de haut (le bloc du Brasier est le coin nord-ouest, en bas).
 */
public final class OfferingBrazier {

    /** Brasier (coin) → ses blocs de collision. */
    private static final Map<BlockPos, List<BlockPos>> BRAZIERS = new HashMap<>();
    /** Bloc de collision → Brasier parent. */
    private static final Map<BlockPos, BlockPos> PARENT = new HashMap<>();
    /** Ferveur du royaume : active pendant l'Assaut n° fervorCycle (+10 % de dégâts des tours et des soldats). */
    private static int fervorCycle = -1;

    public OfferingBrazier() {}

    /** Les 7 autres cases de l'emprise 2×2×2. */
    public static List<BlockPos> footprint(BlockPos c) {
        List<BlockPos> l = new ArrayList<>();
        for (int y = 0; y <= 1; y++)
            for (int x = 0; x <= 1; x++)
                for (int z = 0; z <= 1; z++)
                    if (x != 0 || y != 0 || z != 0) l.add(c.offset(x, y, z));
        return l;
    }

    /** Place libre pour l'emprise ? */
    public static boolean hasRoom(ServerLevel l, BlockPos c) {
        for (BlockPos q : footprint(c)) {
            BlockState s = l.getBlockState(q);
            if (!s.isAir() && !s.canBeReplaced()) return false;
        }
        return true;
    }

    static void register(ServerLevel l, BlockPos c) {
        List<BlockPos> cols = new ArrayList<>();
        for (BlockPos q : footprint(c)) {
            l.setBlock(q, com.wavesurvivor.registry.ModBlocks.KINGDOM_COLLIDER.get().defaultBlockState(), 3);
            cols.add(q.immutable());
            PARENT.put(q.immutable(), c.immutable());
        }
        BRAZIERS.put(c.immutable(), cols);
        KingdomLeftovers.track(l, c.immutable());
        KingdomLeftovers.track(l, cols);
    }

    static void unregister(ServerLevel l, BlockPos c) {
        List<BlockPos> cols = BRAZIERS.remove(c);
        if (cols == null) return;
        for (BlockPos q : cols) {
            PARENT.remove(q);
            if (l.getBlockState(q).getBlock() instanceof ColliderBlock) l.removeBlock(q, false);
        }
    }

    /** Bloc de collision → Brasier (clic transmis au Brasier). */
    public static BlockPos parentOf(BlockPos collider) {
        return PARENT.get(collider);
    }

    /** Une partie du Brasier est cassée : tout le Brasier tombe (objet rendu, contenu au sol). */
    public static void colliderBroken(ServerLevel l, BlockPos collider) {
        BlockPos c = PARENT.get(collider);
        if (c != null && l.getBlockState(c).getBlock() instanceof OfferingBrazierBlock) l.destroyBlock(c, true);
    }

    /** Fin de partie : Brasiers et blocs de collision retirés du monde (le contenu de la grille tombe au sol). */
    static void clear(ServerLevel l) {
        if (l != null) {
            for (BlockPos c : new ArrayList<>(BRAZIERS.keySet())) {
                if (l.getBlockState(c).getBlock() instanceof OfferingBrazierBlock) {
                    l.sendParticles(ParticleTypes.POOF, c.getX() + 1.0, c.getY() + 1.0, c.getZ() + 1.0, 20, 0.6, 0.6, 0.6, 0.02);
                    l.removeBlock(c, false);
                }
                unregister(l, c);
            }
        }
        BRAZIERS.clear();
        PARENT.clear();
        fervorCycle = -1;
    }

    // ─── Sauvegarde / reprise de partie ───

    static CompoundTag saveState(ServerLevel l) {
        CompoundTag t = new CompoundTag();
        ListTag list = new ListTag();
        if (l != null) {
            for (BlockPos c : BRAZIERS.keySet()) {
                if (!(l.getBlockEntity(c) instanceof OfferingBrazierBlockEntity be)) continue;
                CompoundTag e = be.saveWithoutMetadata();
                e.putLong("pos", c.asLong());
                list.add(e);
            }
        }
        t.put("list", list);
        t.putInt("fervor", fervorCycle);
        return t;
    }

    static void restoreState(ServerLevel l, CompoundTag t) {
        fervorCycle = t.contains("fervor") ? t.getInt("fervor") : -1;
        ListTag list = t.getList("list", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            BlockPos c = BlockPos.of(e.getLong("pos"));
            l.setBlock(c, com.wavesurvivor.registry.ModBlocks.OFFERING_BRAZIER.get().defaultBlockState(), 3);
            if (l.getBlockEntity(c) instanceof OfferingBrazierBlockEntity be) be.load(e);
            register(l, c);
        }
    }

    // ─── Récompenses spéciales ───

    /** Vague de réparation : toutes les défenses et remparts du royaume récupèrent 15 % de leurs PV. */
    static void repairWave(ServerLevel l, BlockPos at) {
        KingdomClaim.healArea(at, 160.0, 0.15f); // 160.0 : version « fraction des PV max » (et non PV fixes)
        l.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.getX() + 1.0, at.getY() + 2.0, at.getZ() + 1.0, 40, 1.5, 1.0, 1.5, 0.1);
    }

    /** Ferveur du royaume : +10 % de dégâts des tours et des soldats pendant le prochain Assaut. */
    static void grantFervor() {
        fervorCycle = KingdomManager.cycle() + 1;
    }

    static boolean fervorActive() {
        return KingdomManager.isActive() && !KingdomManager.isCalmPhase() && KingdomManager.cycle() == fervorCycle;
    }

    static void announce(ServerLevel l, String key, Object... args) {
        l.getServer().getPlayerList().broadcastSystemMessage(com.wavesurvivor.i18n.WSLang.c(key, args), false);
    }

    /** Ferveur : flèches des tours et coups des soldats +10 %. */
    @SubscribeEvent
    public void onHurt(LivingHurtEvent e) {
        if (fervorCycle < 0 || e.getEntity().level().isClientSide || !fervorActive()) return;
        Entity direct = e.getSource().getDirectEntity();
        boolean tower = direct != null && direct.getPersistentData().getBoolean("ws_tower_arrow");
        boolean soldier = e.getSource().getEntity() instanceof com.wavesurvivor.entity.KingdomSoldier;
        if (tower || soldier) e.setAmount(e.getAmount() * 1.1f);
    }
}
