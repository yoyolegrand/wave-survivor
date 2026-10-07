package com.wavesurvivor.horde.kingdom;

import com.wavesurvivor.entity.GisementEntity;
import com.wavesurvivor.network.KingdomMapPackets;
import com.wavesurvivor.network.KingdomMapPackets.Marker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * CARTE DU ROYAUME — rassemble ce qu'il faut afficher autour du Monolithe (128 blocs de rayon) :
 * Monolithe, défenses, remparts, pièges, Portes ennemies, Catalyseurs, gisements, arbres, joueurs.
 * (Pas le trésor enfoui : il doit se chercher.)
 */
public final class KingdomMap {

    private KingdomMap() {}

    public static final int RANGE = 128;
    private static final int LIMIT = 3900;

    public static KingdomMapPackets.Data build(ServerPlayer viewer) {
        List<Marker> out = new ArrayList<>();
        BlockPos c = KingdomManager.center();
        if (!KingdomManager.isActive() || c == null || !(viewer.level() instanceof ServerLevel lvl)) {
            return new KingdomMapPackets.Data(-1, out);
        }
        out.add(new Marker(KingdomMapPackets.MONOLITH, (short) 0, (short) 0, (byte) 0));
        // Défenses (tours, atelier, baraquement…)
        KingdomDefenses.forEachDefense((pos, kind) -> add(out, c, pos.getX(), pos.getZ(), KingdomMapPackets.DEFENSE, (byte) kind.ordinal()));
        // Remparts (une marque par colonne) et pièges
        for (long col : KingdomClaim.rampartColumns(lvl)) {
            add(out, c, (int) (col >> 32), (int) col, KingdomMapPackets.RAMPART, (byte) 0);
        }
        for (BlockPos t : KingdomDefenses.trapPositions()) add(out, c, t.getX(), t.getZ(), KingdomMapPackets.TRAP, (byte) 0);
        // Portes ennemies encore debout
        for (BlockPos g : KingdomManager.objGates()) add(out, c, g.getX(), g.getZ(), KingdomMapPackets.GATE, (byte) 0);
        // Catalyseurs
        for (Entity e : KingdomManager.catalysts()) add(out, c, e.getBlockX(), e.getBlockZ(), KingdomMapPackets.CATALYST, (byte) 0);
        // Gisements et arbres (pas les foyers de corruption)
        AABB box = new AABB(c).inflate(RANGE, 64, RANGE);
        for (GisementEntity g : lvl.getEntitiesOfClass(GisementEntity.class, box, g -> g.isAlive() && !g.isFoyer())) {
            add(out, c, g.getBlockX(), g.getBlockZ(), g.isTree() ? KingdomMapPackets.TREE : KingdomMapPackets.DEPOSIT, (byte) 0);
        }
        // Joueurs (soi-même à part)
        for (ServerPlayer p : lvl.players()) {
            if (p.isSpectator()) continue;
            add(out, c, p.getBlockX(), p.getBlockZ(), p == viewer ? KingdomMapPackets.SELF : KingdomMapPackets.PLAYER, (byte) 0);
        }
        return new KingdomMapPackets.Data(KingdomClaim.radius(), out);
    }

    /** Boussole du Royaume du joueur : main principale, main secondaire, puis inventaire. */
    private static net.minecraft.world.item.ItemStack findCompass(ServerPlayer p) {
        var item = com.wavesurvivor.registry.ModItems.KINGDOM_COMPASS.get();
        if (p.getMainHandItem().is(item)) return p.getMainHandItem();
        if (p.getOffhandItem().is(item)) return p.getOffhandItem();
        for (var st : p.getInventory().items) if (st.is(item)) return st;
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    /** Règle la Boussole du Royaume du joueur sur un repère de la carte (ou efface sa cible). */
    public static void setCompass(ServerPlayer p, Marker m, boolean clear) {
        var st = findCompass(p);
        if (st.isEmpty()) {
            p.displayClientMessage(com.wavesurvivor.i18n.WSLang.c("compass.none"), true);
            return;
        }
        if (clear) {
            KingdomCompassItem.clearTarget(st);
            p.displayClientMessage(com.wavesurvivor.i18n.WSLang.c("compass.cleared"), true);
            return;
        }
        BlockPos c = KingdomManager.center();
        if (c == null || !(p.level() instanceof ServerLevel lvl)) return;
        int x = c.getX() + m.dx(), z = c.getZ() + m.dz();
        int y = lvl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        String name = m.type() == KingdomMapPackets.DEFENSE
                ? com.wavesurvivor.i18n.WSLang.t("ui.shop.map.def." + switch (m.extra()) {
                    case 0 -> "archer"; case 1 -> "mage"; case 2 -> "shrine"; case 3 -> "barracks"; case 4 -> "collector"; default -> "workshop"; })
                : com.wavesurvivor.i18n.WSLang.t("ui.shop.map.m" + m.type());
        KingdomCompassItem.setTarget(st, lvl, new BlockPos(x, y, z), name, m.type());
        int dist = (int) Math.round(Math.sqrt(p.distanceToSqr(x + 0.5, p.getY(), z + 0.5)));
        p.displayClientMessage(com.wavesurvivor.i18n.WSLang.c("compass.set", name, dist), true);
        lvl.playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.LODESTONE_COMPASS_LOCK, net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1.2f);
    }

    private static void add(List<Marker> out, BlockPos c, int x, int z, byte type, byte extra) {
        if (out.size() >= LIMIT) return;
        int dx = x - c.getX(), dz = z - c.getZ();
        if (Math.abs(dx) > RANGE || Math.abs(dz) > RANGE) return;
        out.add(new Marker(type, (short) dx, (short) dz, extra));
    }
}
