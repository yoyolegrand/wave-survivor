package com.wavesurvivor.shrine;

import com.mojang.serialization.Codec;
import com.wavesurvivor.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * Ruine du Sanctuaire du Séraphin (génération naturelle) : une petite plate-forme de quartz et de calcite brisés, envahie de mousse,
 * au centre d'un chunk de surface. Rareté, biomes et espacement se règlent par les données (datapack) :
 *   data/wavesurvivor/worldgen/structure_set/seraph_shrine.json (spacing / separation, en chunks)
 *   data/wavesurvivor/tags/worldgen/biome/has_structure/seraph_shrine.json (biomes).
 * Se retrouve avec /locate structure wavesurvivor:seraph_shrine.
 */
public class SeraphShrineStructure extends Structure {

    public static final Codec<SeraphShrineStructure> CODEC = simpleCodec(SeraphShrineStructure::new);

    public SeraphShrineStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        ChunkPos cp = ctx.chunkPos();
        int x = cp.getMiddleBlockX(), z = cp.getMiddleBlockZ();
        int y = ctx.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
        // Pas dans l'eau : le sol sous la surface doit être solide
        NoiseColumn col = ctx.chunkGenerator().getBaseColumn(x, z, ctx.heightAccessor(), ctx.randomState());
        BlockState ground = col.getBlock(y - 1);
        if (!ground.getFluidState().isEmpty() || ground.isAir()) return Optional.empty();
        BlockPos center = new BlockPos(x, y, z);
        return Optional.of(new GenerationStub(center, builder -> builder.addPiece(new SeraphShrinePiece(center, ctx.random().nextInt(4)))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.SERAPH_SHRINE.get();
    }
}
