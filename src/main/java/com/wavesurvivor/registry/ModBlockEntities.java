package com.wavesurvivor.registry;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.altar.AltarBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** DeferredRegister des BlockEntities du mod. */
public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, WaveSurvivorMod.MODID);

    /** Autel Runique : livre flottant animé au sommet du monolithe. */
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<AltarBlockEntity>> ALTAR_RUNIC =
            BLOCK_ENTITIES.register("altar_runic",
                    () -> BlockEntityType.Builder.of(AltarBlockEntity::new, ModBlocks.ALTAR_RUNIC.get()).build(null));

    /** Coffre Roulette : porte le nom (config) du coffre, synchronisé au client pour la teinte. */
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.wavesurvivor.horde.roulette.RouletteChestBlockEntity>> ROULETTE_CHEST =
            BLOCK_ENTITIES.register("roulette_chest",
                    () -> BlockEntityType.Builder.of(com.wavesurvivor.horde.roulette.RouletteChestBlockEntity::new,
                            ModBlocks.ROULETTE_CHEST.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }

    /** Mode Kingdom : défenses (niveau synchronisé + rendu 3D). */
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.wavesurvivor.horde.kingdom.DefenseBlockEntity>> DEFENSE =
            BLOCK_ENTITIES.register("kingdom_defense",
                    () -> BlockEntityType.Builder.of(com.wavesurvivor.horde.kingdom.DefenseBlockEntity::new,
                            ModBlocks.KINGDOM_ARCHER_TOWER.get(), ModBlocks.KINGDOM_MAGE_TOWER.get(),
                            ModBlocks.KINGDOM_REPAIR_SHRINE.get(), ModBlocks.KINGDOM_BARRACKS.get(), ModBlocks.KINGDOM_COLLECTOR.get(),
                            ModBlocks.KINGDOM_WORKSHOP.get()).build(null));
}
