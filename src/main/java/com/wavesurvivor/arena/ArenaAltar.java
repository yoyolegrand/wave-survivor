package com.wavesurvivor.arena;

import com.wavesurvivor.altar.AltarBlock;
import com.wavesurvivor.altar.AltarBlockEntity;
import com.wavesurvivor.altar.AltarManager;
import com.wavesurvivor.altar.AltarRecipes;
import com.wavesurvivor.altar.AltarStore;
import com.wavesurvivor.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;

/**
 * AUTEL D'UNE ARÈNE : pose l'autel de horde du mod au repère « monolith », le marque (identifiant de l'arène, orientation, coin d'origine : de quoi
 * retrouver tous les repères plus tard) puis, à la première utilisation (pas pendant la génération du monde), le LIE à la horde de l'arène et le
 * VERROUILLE : le joueur ne peut ni le délier ni le reconfigurer, et il est incassable (sauf en Créatif).
 */
public final class ArenaAltar {

    private ArenaAltar() {}

    /** Pose l'autel au repère « monolith » (si ce repère est dans {@code clip}, ou toujours si clip est nul) et le marque. */
    public static void install(WorldGenLevel level, ArenaBlueprint bp, int ox, int oz, int y0, Rotation rot, BoundingBox clip) {
        List<BlockPos> m = bp.worldMarkers(ox, oz, y0, rot).get("monolith");
        if (m == null || m.isEmpty()) return;
        BlockPos pos = m.get(0);
        if (clip != null && !clip.isInside(pos)) return;
        level.setBlock(pos, ModBlocks.ALTAR_RUNIC.get().defaultBlockState(), 2);
        if (level.getBlockEntity(pos) instanceof AltarBlockEntity be) be.setArena(bp.id, rot.ordinal(), ox, y0, oz);
    }

    /** L'autel est-il verrouillé par une arène ? */
    public static boolean isLocked(ServerLevel level, BlockPos pos) {
        AltarStore.AltarEntry e = AltarStore.get(level.dimension().location().toString(), pos);
        if (e != null && e.locked) return true;
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof AltarBlockEntity ab && ab.arenaId() != null; // pas encore adopté mais déjà protégé
    }

    /** Portes fixes d'une arène Kingdom (repères « fixed_gates » du plan, en coordonnées du monde) ; null si l'autel n'est pas celui d'une arène Kingdom. */
    public static List<com.wavesurvivor.config.model.HordeConfigMultiData.KingdomSettings.GatePoint> kingdomGates(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof AltarBlockEntity ab) || ab.arenaId() == null) return null;
        ArenaBlueprint bp = ArenaBlueprint.load(ab.arenaId());
        if (bp == null || !bp.kingdom()) return null;
        List<BlockPos> g = bp.worldMarkers(ab.arenaOx(), ab.arenaOz(), ab.arenaY0(), Rotation.values()[Math.max(0, Math.min(3, ab.arenaRot()))]).get("fixed_gates");
        if (g == null || g.isEmpty()) return null;
        List<com.wavesurvivor.config.model.HordeConfigMultiData.KingdomSettings.GatePoint> out = new java.util.ArrayList<>();
        for (BlockPos p : g) {
            var gp = new com.wavesurvivor.config.model.HordeConfigMultiData.KingdomSettings.GatePoint();
            gp.x = p.getX(); gp.y = p.getY(); gp.z = p.getZ();
            gp.facing = "monolith"; // tournée vers le Monolithe, quelle que soit la rotation de l'arène
            out.add(gp);
        }
        return out;
    }

    /** Lie et verrouille l'autel d'une arène sur la horde de son plan (une seule fois). Sans effet sur un autel ordinaire. */
    public static void adopt(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof AltarBlockEntity ab) || ab.arenaId() == null) return;
        String dim = level.dimension().location().toString();
        AltarStore.AltarEntry entry = AltarStore.get(dim, pos);
        if (entry != null && entry.locked) return;
        ArenaBlueprint bp = ArenaBlueprint.load(ab.arenaId());
        if (bp == null || bp.hordeName.isBlank()) return;
        if (entry == null) entry = new AltarStore.AltarEntry(bp.hordeName, dim, pos.getX(), pos.getY(), pos.getZ(), AltarBlock.AltarType.HORDE_TRIGGER.name());
        else entry.hordeName = bp.hordeName;
        entry.locked = true;
        entry.arenaId = ab.arenaId();
        entry.zoneRadius = 0;
        AltarRecipes.Style style = AltarRecipes.getStyle(bp.hordeName);
        if (style != null) AltarManager.applyStyle(level, pos, entry, style.dye(), style.particles);
        AltarStore.register(entry);
    }
}
