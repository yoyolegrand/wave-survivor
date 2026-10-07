package com.wavesurvivor.registry;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.altar.AltarBlock;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * DeferredRegister pour les blocks custom du mod.
 * En 1.20.1 Forge, tous les blocks doivent être enregistrés via ce mécanisme sur le mod event bus.
 */
public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, WaveSurvivorMod.MODID);

    /** Autel Runique — bell rouge qui déclenche une horde. */
    public static final RegistryObject<Block> ALTAR_RUNIC = BLOCKS.register("altar_runic",
            () -> new AltarBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_RED)
                            .strength(3.0f, 6.0f)          // hardness + resistance
                            .sound(SoundType.ANVIL)
                            .requiresCorrectToolForDrops()
                            .lightLevel(state -> 7),        // léger halo lumineux
                    AltarBlock.AltarType.HORDE_TRIGGER
            ));

    /** Autel de Renaissance — ouvre l'écran Sacrifice / Boutique. */
    public static final RegistryObject<Block> RENAISSANCE_ALTAR = BLOCKS.register("renaissance_altar",
            () -> new com.wavesurvivor.horde.renaissance.RenaissanceAltarBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_PURPLE)
                            .strength(3.0f, 1200.0f)
                            .sound(SoundType.AMETHYST)
                            .lightLevel(state -> 10)
            ));

    // ─── Mode Kingdom : défenses (étape 4a) ───

    public static final RegistryObject<Block> KINGDOM_ARCHER_TOWER = BLOCKS.register("kingdom_archer_tower",
            () -> new com.wavesurvivor.horde.kingdom.DefenseBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD).strength(3.0f, 1200.0f).sound(SoundType.WOOD).lightLevel(s -> 6),
                    com.wavesurvivor.horde.kingdom.DefenseBlock.Kind.ARCHER));

    public static final RegistryObject<Block> KINGDOM_MAGE_TOWER = BLOCKS.register("kingdom_mage_tower",
            () -> new com.wavesurvivor.horde.kingdom.DefenseBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE).strength(3.0f, 1200.0f).sound(SoundType.AMETHYST).lightLevel(s -> 10),
                    com.wavesurvivor.horde.kingdom.DefenseBlock.Kind.MAGE));

    public static final RegistryObject<Block> KINGDOM_REPAIR_SHRINE = BLOCKS.register("kingdom_repair_shrine",
            () -> new com.wavesurvivor.horde.kingdom.DefenseBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN).strength(3.0f, 1200.0f).sound(SoundType.STONE).lightLevel(s -> 12),
                    com.wavesurvivor.horde.kingdom.DefenseBlock.Kind.SHRINE));

    public static final RegistryObject<Block> KINGDOM_BARRACKS = BLOCKS.register("kingdom_barracks",
            () -> new com.wavesurvivor.horde.kingdom.DefenseBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY).strength(3.5f, 1200.0f).sound(SoundType.WOOD).lightLevel(s -> 4),
                    com.wavesurvivor.horde.kingdom.DefenseBlock.Kind.BARRACKS));

    /** Mode Kingdom : Tour du Glaneur (ramasse les objets au sol dans un stockage de 27 cases). */
    public static final RegistryObject<Block> KINGDOM_COLLECTOR = BLOCKS.register("kingdom_collector",
            () -> new com.wavesurvivor.horde.kingdom.DefenseBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD).strength(3.0f, 1200.0f).sound(SoundType.WOOD).lightLevel(s -> 6),
                    com.wavesurvivor.horde.kingdom.DefenseBlock.Kind.COLLECTOR));

    /** Mode Kingdom : Atelier de Réparation (2×1, répare lentement armures, armes et outils déposés). */
    public static final RegistryObject<Block> KINGDOM_WORKSHOP = BLOCKS.register("kingdom_workshop",
            () -> new com.wavesurvivor.horde.kingdom.DefenseBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).strength(3.0f, 1200.0f).sound(SoundType.ANVIL).noOcclusion().lightLevel(s -> 8),
                    com.wavesurvivor.horde.kingdom.DefenseBlock.Kind.WORKSHOP));

    /** Mode Kingdom : bloc de collision invisible des défenses (corps physique des tours et du baraquement). */
    public static final RegistryObject<Block> KINGDOM_COLLIDER = BLOCKS.register("kingdom_collider",
            () -> new com.wavesurvivor.horde.kingdom.ColliderBlock(BlockBehaviour.Properties.of()
                    .strength(-1.0f, 3600000.0f).noOcclusion().noLootTable()
                    .isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false)));

    /** Cristal de la Mairie : bloc d'affichage uniquement (modèle 3D facetté), jamais posé dans le monde. */
    public static final RegistryObject<Block> TOWN_CRYSTAL = BLOCKS.register("town_crystal",
            () -> new Block(BlockBehaviour.Properties.of().strength(-1.0f, 3600000.0f).noOcclusion().noLootTable()));

    /** Mode Kingdom : rempart — segment de muraille de 3 blocs de haut (base, corps, sommet crénelé), PV partagés. */
    public static final RegistryObject<Block> KINGDOM_RAMPART = BLOCKS.register("kingdom_rampart",
            () -> new com.wavesurvivor.horde.kingdom.RampartBlock(BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.DEEPSLATE_BRICKS)
                    .noOcclusion().noLootTable()));

    /** Mode Kingdom : partie de porte de rempart (cadre 3×3, PV partagés). */
    public static final RegistryObject<Block> KINGDOM_GATE_PART = BLOCKS.register("kingdom_gate_part",
            () -> new com.wavesurvivor.horde.kingdom.GatePartBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD).strength(3.0f, 1200.0f).sound(SoundType.WOOD).noOcclusion()));

    /** Mode Kingdom : pièges à usage unique. */
    public static final RegistryObject<Block> KINGDOM_TRAP_SPIKES = trap("kingdom_trap_spikes", com.wavesurvivor.horde.kingdom.TrapBlock.Kind.SPIKES, SoundType.METAL);
    public static final RegistryObject<Block> KINGDOM_TRAP_FIRE = trap("kingdom_trap_fire", com.wavesurvivor.horde.kingdom.TrapBlock.Kind.FIRE, SoundType.STONE);
    public static final RegistryObject<Block> KINGDOM_TRAP_FROST = trap("kingdom_trap_frost", com.wavesurvivor.horde.kingdom.TrapBlock.Kind.FROST, SoundType.GLASS);
    public static final RegistryObject<Block> KINGDOM_TRAP_EXPLOSIVE = trap("kingdom_trap_explosive", com.wavesurvivor.horde.kingdom.TrapBlock.Kind.EXPLOSIVE, SoundType.GRASS);
    public static final RegistryObject<Block> KINGDOM_TRAP_SNARE = trap("kingdom_trap_snare", com.wavesurvivor.horde.kingdom.TrapBlock.Kind.SNARE, SoundType.WOOL);

    private static RegistryObject<Block> trap(String id, com.wavesurvivor.horde.kingdom.TrapBlock.Kind kind, SoundType sound) {
        return BLOCKS.register(id, () -> new com.wavesurvivor.horde.kingdom.TrapBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE).strength(0.5f).sound(sound).noCollission().noOcclusion(), kind));
    }

    // ─── Sols de zone "sans gravité" : même rendu que le vanilla, ne tombent jamais, droppent le bloc vanilla ───

    public static final RegistryObject<Block> STABLE_SAND = BLOCKS.register("stable_sand",
            () -> new Block(BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.SAND)));

    public static final RegistryObject<Block> STABLE_RED_SAND = BLOCKS.register("stable_red_sand",
            () -> new Block(BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.RED_SAND)));

    public static final RegistryObject<Block> STABLE_GRAVEL = BLOCKS.register("stable_gravel",
            () -> new Block(BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.GRAVEL)));

    // ─── Coffre Roulette (Reliquaire runique) : remplace le coffre vanilla des roulette chests ───

    public static final RegistryObject<Block> ROULETTE_CHEST = BLOCKS.register("roulette_chest",
            () -> new com.wavesurvivor.horde.roulette.RouletteChestBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.DEEPSLATE)
                            .strength(2.5f, 1200.0f)
                            .sound(SoundType.DEEPSLATE_BRICKS)
                            .lightLevel(state -> 5)
                            .noOcclusion()
            ));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
