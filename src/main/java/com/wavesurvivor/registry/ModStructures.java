package com.wavesurvivor.registry;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.shrine.SeraphShrinePiece;
import com.wavesurvivor.shrine.SeraphShrineStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Structures du mod (génération naturelle) : pour l'instant la ruine du Sanctuaire du Séraphin. */
public final class ModStructures {

    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, WaveSurvivorMod.MODID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, WaveSurvivorMod.MODID);

    public static final RegistryObject<StructureType<SeraphShrineStructure>> SERAPH_SHRINE =
            STRUCTURE_TYPES.register("seraph_shrine", () -> () -> SeraphShrineStructure.CODEC);
    public static final RegistryObject<StructurePieceType> SERAPH_SHRINE_PIECE =
            STRUCTURE_PIECES.register("seraph_shrine", () -> (StructurePieceType) SeraphShrinePiece::new);

    private ModStructures() {}

    public static void register(IEventBus bus) {
        STRUCTURE_TYPES.register(bus);
        STRUCTURE_PIECES.register(bus);
    }
}
