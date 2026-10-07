package com.wavesurvivor.client;

import com.wavesurvivor.WaveSurvivorMod;
import com.wavesurvivor.registry.ModItems;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Couleur du liquide des fioles d'alchimiste (même teinte que les potions jetables vanilla). */
@Mod.EventBusSubscriber(modid = WaveSurvivorMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AlchemyFlaskColors {

    private AlchemyFlaskColors() {}

    @SubscribeEvent
    public static void alchemyFlaskColors(RegisterColorHandlersEvent.Item e) {
        e.register((stack, layer) -> layer > 0 ? -1 : PotionUtils.getColor(stack), ModItems.ALCHEMY_FLASK.get());
    }
}
