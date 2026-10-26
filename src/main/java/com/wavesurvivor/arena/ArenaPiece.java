package com.wavesurvivor.arena;

import com.wavesurvivor.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * Pièce d'une arène générée : retient le plan, la rotation, le coin nord-ouest et le niveau du sol (fixés au départ, identiques pour tous les
 * chunks) ; chaque chunk construit seulement la part de l'arène qui tombe dans sa zone d'écriture.
 */
public class ArenaPiece extends StructurePiece {

    private final String arena;
    private final int rot, ox, oz, y0, width, depth;

    public ArenaPiece(String arena, Rotation rotation, int ox, int oz, int y0, int width, int depth, int clear, int below) {
        super(ModStructures.ARENA_PIECE.get(), 0, new BoundingBox(ox - ArenaBlueprint.BLEND_WIDTH - 2, y0 - below, oz - ArenaBlueprint.BLEND_WIDTH - 2,
                ox + width + ArenaBlueprint.BLEND_WIDTH + 1, y0 + clear + 10, oz + depth + ArenaBlueprint.BLEND_WIDTH + 1));
        this.arena = arena;
        this.rot = rotation.ordinal();
        this.ox = ox;
        this.oz = oz;
        this.y0 = y0;
        this.width = width;
        this.depth = depth;
    }

    public ArenaPiece(StructurePieceSerializationContext ctx, CompoundTag tag) {
        super(ModStructures.ARENA_PIECE.get(), tag);
        this.arena = tag.getString("Arena");
        this.rot = tag.getInt("Rot");
        this.ox = tag.getInt("Ox");
        this.oz = tag.getInt("Oz");
        this.y0 = tag.getInt("Y0");
        this.width = tag.getInt("W");
        this.depth = tag.getInt("D");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
        tag.putString("Arena", arena);
        tag.putInt("Rot", rot);
        tag.putInt("Ox", ox);
        tag.putInt("Oz", oz);
        tag.putInt("Y0", y0);
        tag.putInt("W", width);
        tag.putInt("D", depth);
    }

    /** Rotation de la pièce. */
    public Rotation rotation() { return Rotation.values()[Math.max(0, Math.min(3, rot))]; }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator gen, RandomSource rand,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        ArenaBlueprint bp = ArenaBlueprint.load(arena);
        if (bp == null) return;
        bp.build(level, ox, oz, y0, rotation(), box, true);
        ArenaAltar.install(level, bp, ox, oz, y0, rotation(), box);
    }
}
