package com.wavesurvivor.shrine;

import com.wavesurvivor.registry.ModBlocks;
import com.wavesurvivor.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * Pièce unique de la ruine : plate-forme ronde (rayon ~3,6) de quartz / calcite / briques moussues, quatre colonnes brisées, mousse
 * alentour, et au centre le Sanctuaire du Séraphin à l'étape Neutre. Tout reste dans le chunk (±4 blocs autour du centre).
 */
public class SeraphShrinePiece extends StructurePiece {

    private final int baseY;
    private final int facing;

    public SeraphShrinePiece(BlockPos center, int facing) {
        super(ModStructures.SERAPH_SHRINE_PIECE.get(), 0, new BoundingBox(center.getX() - 5, center.getY() - 14, center.getZ() - 5,
                center.getX() + 5, center.getY() + 12, center.getZ() + 5));
        this.baseY = center.getY();
        this.facing = facing;
    }

    public SeraphShrinePiece(StructurePieceSerializationContext ctx, CompoundTag tag) {
        super(ModStructures.SERAPH_SHRINE_PIECE.get(), tag);
        this.baseY = tag.getInt("BaseY");
        this.facing = tag.getInt("Facing");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
        tag.putInt("BaseY", baseY);
        tag.putInt("Facing", facing);
    }

    private void put(WorldGenLevel level, BlockState state, int x, int y, int z) {
        BlockPos p = new BlockPos(x, y, z);
        if (boundingBox.isInside(p)) level.setBlock(p, state, 2);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator gen, RandomSource rand,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        int cx = (boundingBox.minX() + boundingBox.maxX()) / 2, cz = (boundingBox.minZ() + boundingBox.maxZ()) / 2, y = baseY;
        BlockState[] floor = {Blocks.SMOOTH_QUARTZ.defaultBlockState(), Blocks.CALCITE.defaultBlockState(), Blocks.QUARTZ_BRICKS.defaultBlockState(),
                Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), Blocks.CRACKED_STONE_BRICKS.defaultBlockState()};

        // Dégagement : tout le volume autour de la statue (arbres et terre compris) pour qu'elle ne soit jamais enfouie dans une colline
        for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
            if (Math.sqrt(dx * dx + dz * dz) > 5.3) continue;
            for (int yy = y; yy <= y + 11; yy++) {
                BlockPos p = new BlockPos(cx + dx, yy, cz + dz);
                if (!level.getBlockState(p).isAir()) put(level, Blocks.AIR.defaultBlockState(), p.getX(), p.getY(), p.getZ());
            }
        }

        // Plate-forme ronde (remplace le sol), avec des trous et de la mousse
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 4.3) continue;
            int x = cx + dx, z = cz + dz;
            // On épouse le relief : sol réel à cet endroit
            int gy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
            if (d <= 3.6) {
                if (rand.nextInt(9) == 0) continue; // trou
                for (int yy = Math.min(gy, y - 1) - 1; yy < y; yy++) put(level, Blocks.STONE_BRICKS.defaultBlockState(), x, yy, z); // fondation (jusqu'au relief)
                put(level, floor[rand.nextInt(floor.length)], x, y - 1, z);
                if (rand.nextInt(4) == 0) put(level, Blocks.MOSS_CARPET.defaultBlockState(), x, y, z);
            } else if (rand.nextInt(3) == 0) {
                put(level, Blocks.MOSS_BLOCK.defaultBlockState(), x, gy, z);
                if (rand.nextInt(2) == 0) put(level, Blocks.MOSS_CARPET.defaultBlockState(), x, gy + 1, z);
            }
        }

        // Quatre colonnes brisées
        int[][] corners = {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}};
        for (int[] c : corners) {
            int h = 1 + rand.nextInt(4);
            for (int i = 0; i < h; i++) {
                BlockState col = rand.nextInt(3) == 0 ? Blocks.CALCITE.defaultBlockState()
                        : Blocks.QUARTZ_PILLAR.defaultBlockState();
                put(level, col, cx + c[0], y + i, cz + c[1]);
            }
            if (rand.nextBoolean()) put(level, Blocks.QUARTZ_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.BOTTOM), cx + c[0], y + h, cz + c[1]);
            if (rand.nextInt(3) == 0) put(level, Blocks.VINE.defaultBlockState(), cx + c[0] + (c[0] > 0 ? 1 : -1), y, cz + c[1]);
        }

        // Le Sanctuaire (étape Neutre par défaut, voir SeraphShrineBlockEntity)
        Direction dir = Direction.from2DDataValue(facing);
        BlockState shrine = ModBlocks.SERAPH_SHRINE.get().defaultBlockState().setValue(com.wavesurvivor.shrine.SeraphShrineBlock.FACING, dir);
        put(level, shrine, cx, y, cz);
        SeraphShrineBlock.placeColliders(level, new BlockPos(cx, y, cz));
    }
}
