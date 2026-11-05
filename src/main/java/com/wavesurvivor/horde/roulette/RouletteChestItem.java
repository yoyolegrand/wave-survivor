package com.wavesurvivor.horde.roulette;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/** Objet du coffre roulette : rendu par le même moteur que le bloc (les couleurs viennent de l'objet, voir RouletteChestBewlr). */
public class RouletteChestItem extends BlockItem {

    public RouletteChestItem(Block block, Properties props) { super(block, props); }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return com.wavesurvivor.client.roulette.RouletteChestBewlr.get();
            }
        });
    }
}
