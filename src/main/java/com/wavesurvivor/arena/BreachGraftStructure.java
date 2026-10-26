package com.wavesurvivor.arena;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;
import java.util.Random;

/**
 * GREFFE D'UNE ARÈNE SUR UN PORTAIL EN RUINE (1.8) : ce type de structure REMPLACE les fichiers vanilla minecraft:ruined_portal (standard) et
 * minecraft:ruined_portal_desert. Il génère d'abord le portail vanilla (la structure d'origine, recopiée sous wavesurvivor:ruined_portal_*_original,
 * champ « delegate »), puis, si le portail est posé en SURFACE dans un biome éligible (tag wavesurvivor:has_structure/infernal_breach), tire au
 * sort (déterministe : graine du monde + position) une proportion de portails (arenas.json, « graft », 25 % par défaut) à remplacer par
 * l'arène du champ « arena ». Terrain trop pentu ou eau : le portail vanilla reste tel quel.
 *
 * Pourquoi pas une « structure jumelle + zone d'exclusion » : l'exclusion se calcule sur les emplacements POSSIBLES de l'autre ensemble, que
 * la structure réussisse ou non ; elle aurait fait disparaître des portails vanilla là où la Brèche ne peut pas se générer (océans, jungles,
 * Nether…). Ici rien n'est perdu : le portail vanilla n'est remplacé que lorsque la Brèche se génère vraiment.
 */
public class BreachGraftStructure extends Structure {

    public static final Codec<BreachGraftStructure> CODEC = RecordCodecBuilder.<BreachGraftStructure>mapCodec(i -> i.group(
            settingsCodec(i),
            Codec.STRING.fieldOf("arena").forGetter(a -> a.arena),
            Codec.STRING.fieldOf("delegate").forGetter(a -> a.delegate)
    ).apply(i, BreachGraftStructure::new)).codec();

    private static final TagKey<Biome> ELIGIBLE = TagKey.create(Registries.BIOME, new ResourceLocation("wavesurvivor", "has_structure/infernal_breach"));

    private final String arena, delegate;

    public BreachGraftStructure(StructureSettings settings, String arena, String delegate) {
        super(settings);
        this.arena = arena;
        this.delegate = delegate;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        // 1. Le portail vanilla d'origine
        Structure original = ctx.registryAccess().registryOrThrow(Registries.STRUCTURE).get(new ResourceLocation(delegate));
        if (original == null) return Optional.empty();
        Optional<GenerationStub> vanilla = original.findValidGenerationPoint(ctx);
        if (vanilla.isEmpty()) return vanilla;

        // 2. Remplacé par l'arène ? (génération activée, tirage déterministe, biome éligible, portail en surface, terrain convenable)
        double chance = ArenaSettings.graftChance(arena);
        if (chance <= 0 || !ArenaSettings.enabled() || ArenaSettings.isDisabled(arena)) return vanilla;
        ArenaBlueprint bp = ArenaBlueprint.load(arena);
        if (bp == null) return vanilla;
        ChunkPos cp = ctx.chunkPos();
        Random r = new Random(ctx.seed() ^ (cp.x * 341873128712L) ^ (cp.z * 132897987541L) ^ arena.hashCode());
        if (r.nextDouble() >= chance) return vanilla;
        int cx = cp.getMiddleBlockX(), cz = cp.getMiddleBlockZ();
        // À partir d'ici le tirage a désigné ce portail : on dit dans le journal pourquoi il est remplacé ou non
        int surface = ctx.chunkGenerator().getFirstOccupiedHeight(cx, cz, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
        int py = vanilla.get().position().getY();
        if (py < surface - 16) { // portail souterrain : jamais remplacé (les portails « à moitié enterrés » du désert restent éligibles)
            WaveSurvivorMod.LOGGER.info("[Arènes] Greffe « {} » refusée en {},{} : portail souterrain (y {} pour une surface à {}).", arena, cp.x, cp.z, py, surface);
            return vanilla;
        }
        var biome = ctx.biomeSource().getNoiseBiome(QuartPos.fromBlock(cx), QuartPos.fromBlock(surface), QuartPos.fromBlock(cz), ctx.randomState().sampler());
        if (!biome.is(ELIGIBLE)) {
            WaveSurvivorMod.LOGGER.info("[Arènes] Greffe « {} » refusée en {},{} : biome non éligible ({}).", arena, cp.x, cp.z, biome.unwrapKey().map(k -> k.location().toString()).orElse("?"));
            return vanilla;
        }
        Rotation rot = ArenaSettings.randomRotation() ? Rotation.values()[r.nextInt(4)] : Rotation.NONE;
        Optional<GenerationStub> breach = ArenaStructure.plan(ctx, bp, arena, rot);
        if (breach.isEmpty()) {
            WaveSurvivorMod.LOGGER.info("[Arènes] Greffe « {} » refusée en {},{} : terrain refusé (eau ou pente de plus de {} blocs).", arena, cp.x, cp.z,
                    bp.maxSlope() >= 0 ? bp.maxSlope() : ArenaSettings.maxSlope());
            return vanilla;
        }
        WaveSurvivorMod.LOGGER.info("[Arènes] « {} » greffée à la place du portail en ruine, chunk {},{} (x {} z {}).", arena, cp.x, cp.z, cx, cz);
        return breach;
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.BREACH_GRAFT.get();
    }
}
