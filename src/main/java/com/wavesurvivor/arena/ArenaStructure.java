package com.wavesurvivor.arena;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.wavesurvivor.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * Structure « arène toute faite » générée dans le monde (1.8) : la même classe sert à toutes les arènes, chacune étant une entrée de
 * data/wavesurvivor/worldgen/structure/arena_&lt;nom&gt;.json avec le champ « arena » (identifiant du plan). Rareté et exclusions (villages)
 * dans le structure_set correspondant, biomes dans le tag has_structure/arena_&lt;nom&gt;, réglages dans config/wavesurvivor/arenas.json.
 * Refuse l'eau et les terrains trop pentus ; rotation aléatoire. Se retrouve avec /locate structure wavesurvivor:arena_&lt;nom&gt;.
 */
public class ArenaStructure extends Structure {

    public static final Codec<ArenaStructure> CODEC = RecordCodecBuilder.<ArenaStructure>mapCodec(i -> i.group(
            settingsCodec(i),
            Codec.STRING.fieldOf("arena").forGetter(a -> a.arena)
    ).apply(i, ArenaStructure::new)).codec();

    private final String arena;

    public ArenaStructure(StructureSettings settings, String arena) {
        super(settings);
        this.arena = arena;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        if (!ArenaSettings.enabled() || ArenaSettings.isDisabled(arena)) return Optional.empty();
        ArenaBlueprint bp = ArenaBlueprint.load(arena);
        if (bp == null) return Optional.empty();
        Rotation rot = ArenaSettings.randomRotation() ? Rotation.getRandom(ctx.random()) : Rotation.NONE;
        int[] d = bp.size(rot);
        ChunkPos cp = ctx.chunkPos();
        int cx = cp.getMiddleBlockX(), cz = cp.getMiddleBlockZ();
        int ox = cx - d[0] / 2, oz = cz - d[1] / 2;
        double mx = (d[0] - 1) / 2.0, mz = (d[1] - 1) / 2.0, radius = bp.radius();

        // Relief sous le cercle : pas d'eau, pas de pente trop forte
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, count = 0;
        long sum = 0;
        for (int x = 0; x < d[0]; x += 3) for (int z = 0; z < d[1]; z += 3) {
            if (Math.hypot(x - mx, z - mz) > radius) continue;
            int wx = ox + x, wz = oz + z;
            int top = ctx.chunkGenerator().getFirstOccupiedHeight(wx, wz, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState()) - 1;
            NoiseColumn col = ctx.chunkGenerator().getBaseColumn(wx, wz, ctx.heightAccessor(), ctx.randomState());
            if (!col.getBlock(top).getFluidState().isEmpty()) return Optional.empty();
            min = Math.min(min, top);
            max = Math.max(max, top);
            sum += top;
            count++;
        }
        if (count == 0 || max - min > ArenaSettings.maxSlope()) return Optional.empty();
        int y0 = (int) Math.round(sum / (double) count);
        BlockPos center = new BlockPos(cx, y0, cz);
        return Optional.of(new GenerationStub(center, b -> b.addPiece(new ArenaPiece(arena, rot, ox, oz, y0, d[0], d[1]))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.ARENA.get();
    }
}
