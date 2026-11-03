package com.wavesurvivor.craft;

import com.wavesurvivor.registry.ModBlocks;
import com.wavesurvivor.registry.ModItems;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * BLOCS DE DÉCORATION de l'Artisanat sacré et profane : Marbre béni (grâce) et Pierre profanée (souillure),
 * chacun avec escaliers, dalle, muret, colonne, version polie et sculptée. Ils luisent faiblement (lumière 4).
 * Les blocs et objets sont enregistrés dans ModBlocks / ModItems ; {@link #init()} force l'enregistrement avant l'envoi au bus.
 */
public final class CraftDecor {

    /** Tous les objets de décoration, dans l'ordre de l'onglet créatif. */
    public static final List<RegistryObject<Item>> ITEMS = new ArrayList<>();

    /** Luminosité des blocs de décoration. */
    private static final int LIGHT = 4;

    static {
        family("blessed_marble", "polished_blessed_marble", "chiseled_blessed_marble", () -> Blocks.CALCITE);
        family("tainted_stone", "polished_tainted_stone", "chiseled_tainted_stone", () -> Blocks.DEEPSLATE);
    }

    private CraftDecor() {}

    /** Appelé avant l'enregistrement des DeferredRegister : charge la classe, donc enregistre les blocs. */
    public static void init() {}

    private static void family(String base, String polished, String chiseled, Supplier<Block> copy) {
        Supplier<BlockBehaviour.Properties> props = () -> BlockBehaviour.Properties.copy(copy.get()).lightLevel(s -> LIGHT);
        RegistryObject<Block> b = block(base, () -> new Block(props.get()));
        block(base + "_stairs", () -> new StairBlock(() -> b.get().defaultBlockState(), props.get()));
        block(base + "_slab", () -> new SlabBlock(props.get()));
        block(base + "_wall", () -> new WallBlock(props.get()));
        block(base + "_column", () -> new RotatedPillarBlock(props.get()));
        block(polished, () -> new Block(props.get()));
        block(chiseled, () -> new Block(props.get()));
    }

    private static RegistryObject<Block> block(String id, Supplier<Block> sup) {
        RegistryObject<Block> blk = ModBlocks.BLOCKS.register(id, sup);
        ITEMS.add(ModItems.ITEMS.register(id, () -> new BlockItem(blk.get(), new Item.Properties())));
        return blk;
    }
}
