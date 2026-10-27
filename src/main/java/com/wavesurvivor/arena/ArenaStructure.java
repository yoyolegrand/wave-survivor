package com.wavesurvivor.arena;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.wavesurvivor.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
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
        if (bp == null || !bp.available()) return Optional.empty(); // arène liée à un mod absent (Cercle des Arcanes) : rien ne se génère, rien ne casse
        // Biome d'abord (une seule mesure) : les candidats hors des biomes de l'arène sont écartés sans échantillonner tout le terrain
        // (sinon /locate et la génération paient une centaine de mesures de relief par chunk candidat, très lourd avec des mondes moddés)
        ChunkPos c0 = ctx.chunkPos();
        int mx0 = c0.getMiddleBlockX(), mz0 = c0.getMiddleBlockZ();
        int my0 = ctx.chunkGenerator().getFirstOccupiedHeight(mx0, mz0, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
        var biome = ctx.biomeSource().getNoiseBiome(QuartPos.fromBlock(mx0), QuartPos.fromBlock(my0), QuartPos.fromBlock(mz0), ctx.randomState().sampler());
        if (!ctx.validBiome().test(biome)) return Optional.empty();
        if (bp.square() && nearOtherStructure(ctx, c0)) return Optional.empty(); // grand terrain : jamais par-dessus une autre structure
        Rotation rot = ArenaSettings.randomRotation() ? Rotation.getRandom(ctx.random()) : Rotation.NONE;
        return plan(ctx, bp, arena, rot);
    }

    /** Demi-largeur d'un terrain complet raccord compris (91 + 12 blocs ≈ 7 chunks) et écart minimal exigé avec toute autre structure de surface. */
    private static final int HALF_CHUNKS = 7, GAP_CHUNKS = 4;
    /** Structures sous terre, sous l'eau ou dans d'autres dimensions : sans risque de chevauchement. */
    private static final java.util.Set<String> IGNORED = java.util.Set.of("mineshafts", "buried_treasures", "ocean_ruins", "shipwrecks", "ancient_cities", "nether_complexes",
            "nether_fossils", "bastion_remnants", "end_cities", "ocean_monuments", "strongholds", "trail_ruins");

    /**
     * Une autre structure de surface peut-elle démarrer à moins de 4 chunks du bord du terrain complet ? On regarde le chunk de départ POSSIBLE de chaque
     * structure (même calcul que le jeu, à partir de la graine), avec sa demi-taille estimée : par prudence, on s'écarte même si le terrain refuse finalement
     * cette structure (seul son biome est vérifié). Concerne toutes les structures réparties au hasard : vanilla, nos arènes et celles des autres mods.
     */
    private static boolean nearOtherStructure(GenerationContext ctx, ChunkPos c) {
        var reg = ctx.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET);
        for (var e : reg.entrySet()) {
            net.minecraft.resources.ResourceLocation id = e.getKey().location();
            String ns = id.getNamespace(), path = id.getPath();
            if (ns.equals("wavesurvivor") && path.equals("arena_val") || IGNORED.contains(path)) continue;
            int size = switch (path) { // demi-taille estimée de la structure, en chunks (valeurs serrées : sinon plus aucun emplacement n'est libre)
                case "villages" -> 3;
                case "woodland_mansions" -> 4;
                case "igloos", "swamp_huts", "ruined_portals" -> 1;
                default -> ns.equals("wavesurvivor") && path.startsWith("arena_") ? 4 : 2;
            };
            int range = HALF_CHUNKS + GAP_CHUNKS + size;
            if (!(e.getValue().placement() instanceof net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement rs)) continue;
            int sp = rs.spacing();
            for (int cx = Math.floorDiv(c.x - range, sp); cx <= Math.floorDiv(c.x + range, sp); cx++) {
                for (int cz = Math.floorDiv(c.z - range, sp); cz <= Math.floorDiv(c.z + range, sp); cz++) {
                    ChunkPos p = rs.getPotentialStructureChunk(ctx.seed(), cx * sp, cz * sp);
                    if (Math.abs(p.x - c.x) > range || Math.abs(p.z - c.z) > range) continue;
                    // seulement si cette structure peut vraiment démarrer là : son biome doit lui convenir
                    var biome = ctx.biomeSource().getNoiseBiome(QuartPos.fromBlock(p.getMiddleBlockX()), QuartPos.fromBlock(70), QuartPos.fromBlock(p.getMiddleBlockZ()), ctx.randomState().sampler());
                    for (var sel : e.getValue().structures()) if (sel.structure().value().biomes().contains(biome)) return true;
                }
            }
        }
        return false;
    }

    /**
     * Emplacement d'une arène centrée sur le milieu du chunk de départ : refuse l'eau et les pentes trop fortes (écart de hauteur toléré :
     * celui du plan s'il en fixe un, sinon le réglage général). Sert aussi à la greffe sur les portails en ruine (BreachGraftStructure).
     */
    public static Optional<GenerationStub> plan(GenerationContext ctx, ArenaBlueprint bp, String arena, Rotation rot) {
        int[] d = bp.size(rot);
        ChunkPos cp = ctx.chunkPos();
        int cx = cp.getMiddleBlockX(), cz = cp.getMiddleBlockZ();
        int ox = cx - d[0] / 2, oz = cz - d[1] / 2;
        double mx = (d[0] - 1) / 2.0, mz = (d[1] - 1) / 2.0, radius = bp.radius();

        // Relief sous le cercle : pas d'eau, pas de pente trop forte (île flottante : on se place au-dessus du relief ; arène sur l'eau : niveau de la mer)
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, count = 0;
        long sum = 0;
        java.util.List<Integer> tops = new java.util.ArrayList<>();
        int step = bp.square() ? 12 : 3; // terrain complet : un échantillon tous les 12 blocs suffit (≈ 225 mesures au lieu de 3 600, /locate bien plus rapide)
        for (int x = 0; x < d[0]; x += step) for (int z = 0; z < d[1]; z += step) {
            if (!bp.square() && Math.hypot(x - mx, z - mz) > radius) continue;
            int wx = ox + x, wz = oz + z;
            if (bp.aquatic()) { // fond de l'océan ou de la plage
                int floor = ctx.chunkGenerator().getFirstOccupiedHeight(wx, wz, Heightmap.Types.OCEAN_FLOOR_WG, ctx.heightAccessor(), ctx.randomState()) - 1;
                min = Math.min(min, floor);
                max = Math.max(max, floor);
                count++;
                continue;
            }
            int top = ctx.chunkGenerator().getFirstOccupiedHeight(wx, wz, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState()) - 1;
            if (!bp.floating() && !bp.square()) { // terrain complet : l'eau ne l'arrête pas (le lac est comblé à la pose)
                NoiseColumn col = ctx.chunkGenerator().getBaseColumn(wx, wz, ctx.heightAccessor(), ctx.randomState());
                if (!col.getBlock(top).getFluidState().isEmpty()) return Optional.empty();
            }
            min = Math.min(min, top);
            max = Math.max(max, top);
            sum += top;
            count++;
            tops.add(top);
        }
        if (count == 0) return Optional.empty();
        int y0;
        if (bp.floating()) {
            // le dessous du plan (cône) doit rester au-dessus de l'île la plus haute de la zone, avec 3 blocs de marge
            y0 = Math.max(bp.floatY(), max + bp.planGroundY() + 3);
        } else if (bp.aquatic()) {
            y0 = ctx.chunkGenerator().getSeaLevel() - 1;
            if (y0 - min > 8) return Optional.empty();       // trop profond : le Récif ne se pose que sur des fonds peu profonds
            if (max > y0 + 6) return Optional.empty();       // et pas contre une falaise
        } else {
            int slope = bp.maxSlope() >= 0 ? bp.maxSlope() : ArenaSettings.maxSlope();
            if (bp.square()) { // terrain complet : posé de force (ni pente ni eau refusées), niveau du sol = médiane des hauteurs, le relief autour est remodelé
                java.util.Collections.sort(tops);
                y0 = tops.get(tops.size() / 2);
            } else {
                if (max - min > slope) return Optional.empty();
                y0 = (int) Math.round(sum / (double) count);
            }
        }
        BlockPos center = new BlockPos(cx, y0, cz);
        return Optional.of(new GenerationStub(center, b -> b.addPiece(new ArenaPiece(arena, rot, ox, oz, y0, d[0], d[1], bp.clearHeight(), bp.floating() ? bp.planGroundY() + 3 : bp.planGroundY() + 48, bp.blendWidth()))));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.ARENA.get();
    }
}
